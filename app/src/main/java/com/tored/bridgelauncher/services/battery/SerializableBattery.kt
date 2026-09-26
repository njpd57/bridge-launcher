package com.tored.bridgelauncher.services.battery

import android.os.BatteryManager
import com.tored.bridgelauncher.utils.RawRepresentable
import com.tored.bridgelauncher.utils.serialization.StringEnumWriteOnlySerializer
import kotlinx.serialization.Serializable

@Serializable
data class SerializableBattery(
    /** 0 to 100. */
    val level: Int,
    /** Whether a charger of any kind is connected. */
    val isCharging: Boolean,
    /** null when not plugged in. */
    val pluggedType: BatteryPluggedStringOptions?,
)

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = StringEnumWriteOnlySerializer::class)
enum class BatteryPluggedStringOptions(override val rawValue: String) : RawRepresentable<String>
{
    Ac("ac"),
    Usb("usb"),
    Wireless("wireless"),
    Other("other"),
    ;

    companion object
    {
        fun fromPlugExtra(plugged: Int): BatteryPluggedStringOptions? = when (plugged)
        {
            0 -> null
            BatteryManager.BATTERY_PLUGGED_AC -> Ac
            BatteryManager.BATTERY_PLUGGED_USB -> Usb
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> Wireless
            else -> Other
        }
    }
}
