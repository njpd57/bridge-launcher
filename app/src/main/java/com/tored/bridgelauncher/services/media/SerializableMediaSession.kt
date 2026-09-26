package com.tored.bridgelauncher.services.media

import android.media.session.PlaybackState
import com.tored.bridgelauncher.utils.RawRepresentable
import com.tored.bridgelauncher.utils.serialization.StringEnumWriteOnlySerializer
import kotlinx.serialization.Serializable

@Serializable
data class SerializableMediaSession(
    val packageName: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val state: MediaPlaybackStateStringOptions,
    val durationMs: Long?,
    /** The position at [positionUpdatedAt]; while playing, it advances at [playbackSpeed]. */
    val positionMs: Long?,
    /** Milliseconds since the epoch. */
    val positionUpdatedAt: Long,
    val playbackSpeed: Float,
    val hasArt: Boolean,
    /** Changes with every track, so the art URL changes too. */
    val artVersion: Int,
    val canSkipToNext: Boolean,
    val canSkipToPrevious: Boolean,
)

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = StringEnumWriteOnlySerializer::class)
enum class MediaPlaybackStateStringOptions(override val rawValue: String) : RawRepresentable<String>
{
    Playing("playing"),
    Paused("paused"),
    Buffering("buffering"),
    Stopped("stopped"),
    None("none"),
    ;

    companion object
    {
        fun fromPlaybackState(state: Int?) = when (state)
        {
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING -> Playing

            PlaybackState.STATE_PAUSED -> Paused

            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING,
            PlaybackState.STATE_SKIPPING_TO_NEXT,
            PlaybackState.STATE_SKIPPING_TO_PREVIOUS,
            PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM -> Buffering

            PlaybackState.STATE_STOPPED,
            PlaybackState.STATE_ERROR -> Stopped

            else -> None
        }
    }
}
