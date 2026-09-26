package com.tored.bridgelauncher.api2.shared

import android.content.Intent
import android.provider.Settings
import com.tored.bridgelauncher.utils.CurrentAndroidVersion
import com.tored.bridgelauncher.utils.RawRepresentable
import com.tored.bridgelauncher.utils.q

/** System panels and settings screens for things projects can't toggle themselves. */
enum class SystemPanelStringOptions(override val rawValue: String) : RawRepresentable<String>
{
    Wifi("wifi"),
    Internet("internet"),
    Bluetooth("bluetooth"),
    NFC("nfc"),
    Volume("volume"),
    Location("location"),
    ;

    /** The floating settings panel (Android 10+) where there is one, otherwise the settings screen. */
    fun createIntent(): Intent
    {
        val hasPanels = CurrentAndroidVersion.supportsSettingsPanels()
        return Intent(
            when (this)
            {
                Wifi -> if (hasPanels) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS
                Internet -> if (hasPanels) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS
                NFC -> if (hasPanels) Settings.Panel.ACTION_NFC else Settings.ACTION_NFC_SETTINGS
                Volume -> if (hasPanels) Settings.Panel.ACTION_VOLUME else Settings.ACTION_SOUND_SETTINGS
                Bluetooth -> Settings.ACTION_BLUETOOTH_SETTINGS
                Location -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            }
        )
    }

    companion object
    {
        fun fromStringOrThrow(panel: String): SystemPanelStringOptions
        {
            return entries.firstOrNull { it.rawValue == panel }
                ?: throw Exception("Argument \"panel\" must be one of ${entries.joinToString { q(it) }} (got ${q(panel)}).")
        }
    }
}
