package com.tored.bridgelauncher.services.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import com.tored.bridgelauncher.utils.CurrentAndroidVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "ConnectivityHolder"

// Gingerbread's status bar had 4 signal bars (0 to 4) and 3 Wi-Fi arcs; levels are 0 to 4 like Android's
private const val MAX_LEVEL = 4

// traffic is sampled this often while the home screen is visible; less than the threshold counts as idle
private const val TRAFFIC_POLL_MS = 1000L
private const val TRAFFIC_THRESHOLD_BYTES = 512L

/**
 * The real network state for status bars: which network is in use, the Wi-Fi and mobile signal
 * levels and data activity. None of it needs a runtime permission.
 *
 * Data activity comes from sampling TrafficStats while the home screen is visible: Android's own data
 * activity callback isn't delivered to regular apps on One UI (it only reported "dormant" once).
 */
class ConnectivityHolder(
    private val _context: Context,
)
{
    private val _handler = Handler(Looper.getMainLooper())
    private val _connectivityManager = _context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _wifiManager = _context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val _telephonyManager = _context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    private val _connectivity = MutableStateFlow(
        SerializableConnectivity(
            type = ConnectionTypeStringOptions.None,
            wifiLevel = null,
            cellularLevel = readCellularLevel(),
            cellularDataActivity = DataActivityStringOptions.None,
            dataActivity = DataActivityStringOptions.None,
        )
    )
    val connectivity = _connectivity.asStateFlow()

    // main thread only
    private var _isPollingTraffic = false
    private var _lastRxBytes = 0L
    private var _lastTxBytes = 0L
    private val _pollTraffic = object : Runnable
    {
        override fun run()
        {
            sampleTraffic()
            _handler.postDelayed(this, TRAFFIC_POLL_MS)
        }
    }

    // a status bar nicety must never take the whole launcher down, so failures here are only logged
    fun startup()
    {
        try
        {
            listenToNetworks()
        }
        catch (ex: Exception)
        {
            Log.e(TAG, "Could not listen to network changes", ex)
        }

        listenToTelephony()
    }

    // region data activity

    /** Starts sampling traffic; called when the home screen becomes visible. */
    fun startTrafficPolling()
    {
        if (_isPollingTraffic) return
        _isPollingTraffic = true
        _lastRxBytes = TrafficStats.getTotalRxBytes()
        _lastTxBytes = TrafficStats.getTotalTxBytes()
        _handler.postDelayed(_pollTraffic, TRAFFIC_POLL_MS)
    }

    fun stopTrafficPolling()
    {
        _isPollingTraffic = false
        _handler.removeCallbacks(_pollTraffic)
        _connectivity.update { it.copy(dataActivity = DataActivityStringOptions.None) }
    }

    private fun sampleTraffic()
    {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        // UNSUPPORTED (-1) on devices without these counters
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong())
            return

        val isIn = rx - _lastRxBytes > TRAFFIC_THRESHOLD_BYTES
        val isOut = tx - _lastTxBytes > TRAFFIC_THRESHOLD_BYTES
        _lastRxBytes = rx
        _lastTxBytes = tx

        val activity = when
        {
            isIn && isOut -> DataActivityStringOptions.InOut
            isIn -> DataActivityStringOptions.In
            isOut -> DataActivityStringOptions.Out
            else -> DataActivityStringOptions.None
        }
        _connectivity.update { it.copy(dataActivity = activity) }
    }

    // endregion


    private fun listenToNetworks()
    {
        _connectivityManager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback()
        {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities)
            {
                val type = when
                {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionTypeStringOptions.Wifi
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionTypeStringOptions.Cellular
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionTypeStringOptions.Ethernet
                    else -> ConnectionTypeStringOptions.Other
                }
                val wifiLevel = if (type == ConnectionTypeStringOptions.Wifi) readWifiLevel(caps) else null
                _connectivity.update { it.copy(type = type, wifiLevel = wifiLevel) }
            }

            override fun onLost(network: Network)
            {
                _connectivity.update { it.copy(type = ConnectionTypeStringOptions.None, wifiLevel = null) }
            }
        }, _handler)
    }


    // region telephony

    private fun listenToTelephony()
    {
        try
        {
            if (CurrentAndroidVersion.supportsTelephonyCallback())
                listenWithTelephonyCallback()
            else
                listenWithPhoneStateListener()
        }
        catch (ex: Exception)
        {
            // e.g. no telephony on this device
            Log.w(TAG, "Could not listen to the mobile signal", ex)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun listenWithTelephonyCallback()
    {
        val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener, TelephonyCallback.DataActivityListener
        {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = onSignalStrength(signalStrength)
            override fun onDataActivity(direction: Int) = onDataActivityChanged(direction)
        }
        _telephonyManager.registerTelephonyCallback(_context.mainExecutor, callback)
        Log.d(TAG, "listening to the mobile signal and data activity (TelephonyCallback)")
    }

    @Suppress("DEPRECATION")
    private fun listenWithPhoneStateListener()
    {
        val listener = object : PhoneStateListener()
        {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength?)
            {
                signalStrength?.let { onSignalStrength(it) }
            }

            override fun onDataActivity(direction: Int) = onDataActivityChanged(direction)
        }
        _telephonyManager.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_DATA_ACTIVITY)
        Log.d(TAG, "listening to the mobile signal and data activity (PhoneStateListener)")
    }

    private fun onSignalStrength(signalStrength: SignalStrength)
    {
        _connectivity.update { it.copy(cellularLevel = cellularLevelOrNull(signalStrength)) }
    }

    private fun onDataActivityChanged(direction: Int)
    {
        _connectivity.update { it.copy(cellularDataActivity = DataActivityStringOptions.fromTelephonyDataActivity(direction)) }
    }

    private fun readCellularLevel(): Int? = if (CurrentAndroidVersion.supportsReadingSignalStrength())
        _telephonyManager.signalStrength?.let { cellularLevelOrNull(it) }
    else
        null

    // no SIM or no service: SignalStrength reports level 0 with no cell signal at all
    private fun cellularLevelOrNull(signalStrength: SignalStrength): Int? =
        if (CurrentAndroidVersion.supportsReadingSignalStrength() && signalStrength.cellSignalStrengths.isEmpty())
            null
        else
            signalStrength.level.coerceIn(0, MAX_LEVEL)

    // endregion


    @Suppress("DEPRECATION")
    private fun readWifiLevel(caps: NetworkCapabilities): Int?
    {
        val rssi = if (CurrentAndroidVersion.supportsNetworkCapabilitiesSignalStrength())
            caps.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
        else
            _wifiManager.connectionInfo?.rssi
        rssi ?: return null

        return if (CurrentAndroidVersion.supportsWifiManagerSignalLevel())
        {
            val max = _wifiManager.maxSignalLevel.coerceAtLeast(1)
            (_wifiManager.calculateSignalLevel(rssi) * MAX_LEVEL + max / 2) / max
        }
        else
            WifiManager.calculateSignalLevel(rssi, MAX_LEVEL + 1)
    }
}
