package com.tored.bridgelauncher.services.quicksettings

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.tored.bridgelauncher.api2.shared.RingerModeStringOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

private const val TAG = "QuickSettingsHolder"

// hidden from the public SDK (no AudioManager constant), but the standard way apps observe volume changes
private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

private const val MAX_SCREEN_BRIGHTNESS = 255

@Serializable
data class ScreenBrightness(
    val isAuto: Boolean,
    /** Manual brightness, 0 to 1. */
    val level: Float,
)

/**
 * System toggles that an app can still change itself: flashlight, screen brightness, auto-rotate,
 * master sync, ringer mode and media volume. Wi-Fi, Bluetooth, mobile data and location can't be
 * toggled by apps on current Android versions; for those, projects can only read whether they're
 * on and open the system panels.
 */
class QuickSettingsHolder(
    private val _context: Context,
)
{
    private val _handler = Handler(Looper.getMainLooper())
    private val _resolver = _context.contentResolver
    private val _cameraManager = _context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val _wifiManager = _context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val _bluetoothAdapter: BluetoothAdapter? = (_context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val _locationManager = _context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _audioManager = _context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** A back camera with a flash unit, or null if the device has no flashlight. */
    val flashlightCameraId: String? = try
    {
        _cameraManager.cameraIdList.firstOrNull {
            _cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }
    catch (ex: Exception)
    {
        Log.e(TAG, "Could not look for a flashlight", ex)
        null
    }

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn = _isFlashlightOn.asStateFlow()

    private val _screenBrightness = MutableStateFlow(readScreenBrightness())
    val screenBrightness = _screenBrightness.asStateFlow()

    private val _isAutoRotateOn = MutableStateFlow(readIsAutoRotateOn())
    val isAutoRotateOn = _isAutoRotateOn.asStateFlow()

    private val _isMasterSyncOn = MutableStateFlow(ContentResolver.getMasterSyncAutomatically())
    val isMasterSyncOn = _isMasterSyncOn.asStateFlow()

    private val _isWifiOn = MutableStateFlow(_wifiManager.isWifiEnabled)
    val isWifiOn = _isWifiOn.asStateFlow()

    val isBluetoothAvailable = _bluetoothAdapter != null
    private val _isBluetoothOn = MutableStateFlow(readIsBluetoothOn())
    val isBluetoothOn = _isBluetoothOn.asStateFlow()

    private val _isLocationOn = MutableStateFlow(LocationManagerCompat.isLocationEnabled(_locationManager))
    val isLocationOn = _isLocationOn.asStateFlow()

    private val _ringerMode = MutableStateFlow(readRingerMode())
    val ringerMode = _ringerMode.asStateFlow()

    private val _musicVolume = MutableStateFlow(readMusicVolume())
    val musicVolume = _musicVolume.asStateFlow()


    fun startup()
    {
        if (flashlightCameraId != null)
        {
            _cameraManager.registerTorchCallback(object : CameraManager.TorchCallback()
            {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean)
                {
                    if (cameraId == flashlightCameraId)
                        _isFlashlightOn.value = enabled
                }

                override fun onTorchModeUnavailable(cameraId: String)
                {
                    // e.g. the camera app is using the camera
                    if (cameraId == flashlightCameraId)
                        _isFlashlightOn.value = false
                }
            }, _handler)
        }

        observeSystemSetting(Settings.System.SCREEN_BRIGHTNESS, Settings.System.SCREEN_BRIGHTNESS_MODE) {
            _screenBrightness.value = readScreenBrightness()
        }

        observeSystemSetting(Settings.System.ACCELEROMETER_ROTATION) {
            _isAutoRotateOn.value = readIsAutoRotateOn()
        }

        // called on a background thread; MutableStateFlow is thread safe
        ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS) {
            _isMasterSyncOn.value = ContentResolver.getMasterSyncAutomatically()
        }

        // Wi-Fi and location changes are broadcast to everyone. Bluetooth's broadcast needs the runtime
        // permission BLUETOOTH_CONNECT from Android 12, so it's re-read in refresh() instead (see there).
        registerReceiver(WifiManager.WIFI_STATE_CHANGED_ACTION) { _isWifiOn.value = _wifiManager.isWifiEnabled }
        registerReceiver(LocationManager.MODE_CHANGED_ACTION, LocationManager.PROVIDERS_CHANGED_ACTION) {
            _isLocationOn.value = LocationManagerCompat.isLocationEnabled(_locationManager)
        }
        registerReceiver(BluetoothAdapter.ACTION_STATE_CHANGED) { _isBluetoothOn.value = readIsBluetoothOn() }
        registerReceiver(AudioManager.RINGER_MODE_CHANGED_ACTION) { _ringerMode.value = readRingerMode() }
        // fired for any stream's volume; not part of the public SDK (no AudioManager constant), but
        // it's the standard, widely used way apps observe volume changes
        registerReceiver(VOLUME_CHANGED_ACTION) { _musicVolume.value = readMusicVolume() }
    }

    /**
     * Reads the states that can change without Bridge hearing about it (Bluetooth from Android 12).
     * Called when the home screen gets the focus back, e.g. after closing the notification shade.
     */
    fun refresh()
    {
        _isWifiOn.value = _wifiManager.isWifiEnabled
        _isBluetoothOn.value = readIsBluetoothOn()
        _isLocationOn.value = LocationManagerCompat.isLocationEnabled(_locationManager)
    }


    // region changing settings

    fun setFlashlightOn(isOn: Boolean)
    {
        val cameraId = flashlightCameraId ?: throw Exception("This device has no flashlight.")
        _cameraManager.setTorchMode(cameraId, isOn)
    }

    /** Requires [Settings.System.canWrite]. */
    fun setScreenBrightnessAuto(isAuto: Boolean)
    {
        Settings.System.putInt(
            _resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (isAuto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
    }

    /** Switches to manual brightness at [level] (0 to 1). Requires [Settings.System.canWrite]. */
    fun setScreenBrightnessLevel(level: Float)
    {
        if (level.isNaN() || level < 0f || level > 1f)
            throw Exception("Brightness level must be between 0 and 1 (got $level).")

        setScreenBrightnessAuto(false)
        Settings.System.putInt(_resolver, Settings.System.SCREEN_BRIGHTNESS, (level * MAX_SCREEN_BRIGHTNESS).toInt().coerceAtLeast(1))
    }

    /** Requires [Settings.System.canWrite]. */
    fun setAutoRotateOn(isOn: Boolean)
    {
        Settings.System.putInt(_resolver, Settings.System.ACCELEROMETER_ROTATION, if (isOn) 1 else 0)
    }

    fun setMasterSyncOn(isOn: Boolean)
    {
        ContentResolver.setMasterSyncAutomatically(isOn)
    }

    /** Requires "Do Not Disturb access" ([android.app.NotificationManager.isNotificationPolicyAccessGranted]). */
    fun setRingerMode(androidRingerMode: Int)
    {
        _audioManager.ringerMode = androidRingerMode
    }

    /** Sets the media volume (0 to 1). No special permission needed. */
    fun setMusicVolume(level: Float)
    {
        if (level.isNaN() || level < 0f || level > 1f)
            throw Exception("Volume level must be between 0 and 1 (got $level).")

        val max = _audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        _audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (level * max).toInt().coerceIn(0, max), 0)
    }

    // endregion


    private fun readScreenBrightness() = ScreenBrightness(
        isAuto = Settings.System.getInt(_resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        level = Settings.System.getInt(_resolver, Settings.System.SCREEN_BRIGHTNESS, MAX_SCREEN_BRIGHTNESS).toFloat() / MAX_SCREEN_BRIGHTNESS,
    )

    // isEnabled() needs no permission from Android 12, and the legacy BLUETOOTH permission before
    private fun readIsBluetoothOn() = try
    {
        _bluetoothAdapter?.isEnabled == true
    }
    catch (ex: SecurityException)
    {
        false
    }

    private fun registerReceiver(vararg actions: String, onReceive: () -> Unit)
    {
        val filter = IntentFilter().apply { actions.forEach { addAction(it) } }
        ContextCompat.registerReceiver(
            _context,
            object : BroadcastReceiver()
            {
                override fun onReceive(context: Context?, intent: Intent?) = onReceive()
            },
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun readIsAutoRotateOn() = Settings.System.getInt(_resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun readRingerMode() = RingerModeStringOptions.fromAudioManagerRingerMode(_audioManager.ringerMode)

    private fun readMusicVolume(): Float
    {
        val max = _audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return _audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    private fun observeSystemSetting(vararg names: String, onChange: () -> Unit)
    {
        val observer = object : ContentObserver(_handler)
        {
            override fun onChange(selfChange: Boolean) = onChange()
        }

        for (name in names)
            _resolver.registerContentObserver(Settings.System.getUriFor(name), false, observer)
    }
}
