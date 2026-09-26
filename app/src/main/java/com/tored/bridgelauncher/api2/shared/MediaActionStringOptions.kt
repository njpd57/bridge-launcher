package com.tored.bridgelauncher.api2.shared

import com.tored.bridgelauncher.utils.RawRepresentable
import com.tored.bridgelauncher.utils.q

enum class MediaActionStringOptions(override val rawValue: String) : RawRepresentable<String>
{
    Play("play"),
    Pause("pause"),
    PlayPause("playPause"),
    Next("next"),
    Previous("previous"),
    ;

    companion object
    {
        fun fromStringOrThrow(action: String): MediaActionStringOptions
        {
            return entries.firstOrNull { it.rawValue == action }
                ?: throw Exception("Argument \"action\" must be one of ${entries.joinToString { q(it) }} (got ${q(action)}).")
        }
    }
}
