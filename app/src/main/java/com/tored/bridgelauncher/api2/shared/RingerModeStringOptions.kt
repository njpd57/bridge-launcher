package com.tored.bridgelauncher.api2.shared

import android.media.AudioManager
import com.tored.bridgelauncher.utils.RawRepresentable
import com.tored.bridgelauncher.utils.q
import com.tored.bridgelauncher.utils.serialization.StringEnumWriteOnlySerializer
import kotlinx.serialization.Serializable

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = StringEnumWriteOnlySerializer::class)
enum class RingerModeStringOptions(override val rawValue: String) : RawRepresentable<String>
{
    Normal("normal"),
    Vibrate("vibrate"),
    Silent("silent"),
    ;

    companion object
    {
        fun fromAudioManagerRingerMode(mode: Int) = when (mode)
        {
            AudioManager.RINGER_MODE_SILENT -> Silent
            AudioManager.RINGER_MODE_VIBRATE -> Vibrate
            else -> Normal
        }

        fun toAudioManagerRingerModeOrThrow(mode: String): Int
        {
            return when (mode)
            {
                Normal.rawValue -> AudioManager.RINGER_MODE_NORMAL
                Vibrate.rawValue -> AudioManager.RINGER_MODE_VIBRATE
                Silent.rawValue -> AudioManager.RINGER_MODE_SILENT
                else -> throw Exception("Argument \"mode\" must be one of ${entries.joinToString { q(it) }} (got ${q(mode)}).")
            }
        }
    }
}
