package com.tored.bridgelauncher.services.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The device's battery level and charging state, for the status bar and a battery widget. Read
 * from the sticky `ACTION_BATTERY_CHANGED` broadcast; no runtime permission needed.
 */
class BatteryHolder(
    private val _context: Context,
)
{
    private val _battery = MutableStateFlow(readStickyBattery(_context))
    val battery = _battery.asStateFlow()

    private val _receiver = object : BroadcastReceiver()
    {
        override fun onReceive(context: Context, intent: Intent)
        {
            _battery.value = parseBatteryIntent(intent)
        }
    }

    fun startup()
    {
        ContextCompat.registerReceiver(
            _context,
            _receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}

// registering with a null receiver just reads the last sticky broadcast, synchronously
private fun readStickyBattery(context: Context): SerializableBattery
{
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    return intent?.let { parseBatteryIntent(it) } ?: SerializableBattery(level = 0, isCharging = false, pluggedType = null)
}

private fun parseBatteryIntent(intent: Intent): SerializableBattery
{
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val percent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else 0

    val pluggedType = BatteryPluggedStringOptions.fromPlugExtra(intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0))

    return SerializableBattery(
        level = percent,
        isCharging = pluggedType != null,
        pluggedType = pluggedType,
    )
}
