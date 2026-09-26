package com.tored.bridgelauncher.services.connectivity

import android.telephony.TelephonyManager
import com.tored.bridgelauncher.utils.RawRepresentable
import com.tored.bridgelauncher.utils.serialization.StringEnumWriteOnlySerializer
import kotlinx.serialization.Serializable

@Serializable
data class SerializableConnectivity(
    /** The network in use. */
    val type: ConnectionTypeStringOptions,
    /** 0 to 4 while connected to Wi-Fi, otherwise null. */
    val wifiLevel: Int?,
    /** 0 to 4, or null without a SIM or service. Reported on Wi-Fi too. */
    val cellularLevel: Int?,
    /** As reported by Android; regular apps may never get it (One UI only reports "dormant"). */
    val cellularDataActivity: DataActivityStringOptions,
    /** Measured from the device's traffic on any network, while the home screen is visible. */
    val dataActivity: DataActivityStringOptions,
)

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = StringEnumWriteOnlySerializer::class)
enum class ConnectionTypeStringOptions(override val rawValue: String) : RawRepresentable<String>
{
    Wifi("wifi"),
    Cellular("cellular"),
    Ethernet("ethernet"),
    Other("other"),
    None("none"),
}

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = StringEnumWriteOnlySerializer::class)
enum class DataActivityStringOptions(override val rawValue: String) : RawRepresentable<String>
{
    None("none"),
    In("in"),
    Out("out"),
    InOut("inout"),
    Dormant("dormant"),
    ;

    companion object
    {
        fun fromTelephonyDataActivity(direction: Int) = when (direction)
        {
            TelephonyManager.DATA_ACTIVITY_IN -> In
            TelephonyManager.DATA_ACTIVITY_OUT -> Out
            TelephonyManager.DATA_ACTIVITY_INOUT -> InOut
            TelephonyManager.DATA_ACTIVITY_DORMANT -> Dormant
            else -> None
        }
    }
}
