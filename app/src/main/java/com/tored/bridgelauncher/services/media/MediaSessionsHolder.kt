package com.tored.bridgelauncher.services.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.tored.bridgelauncher.services.system.BridgeNotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MediaSessionsHolder"

/**
 * Follows the media session that's playing (or was last playing), for music controls.
 * Android only lists other apps' sessions to notification listeners, so this starts and stops
 * together with [BridgeNotificationListenerService].
 */
class MediaSessionsHolder(
    private val _context: Context,
)
{
    private val _handler = Handler(Looper.getMainLooper())
    private val _sessionManager = _context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val _listenerComponent = ComponentName(_context, BridgeNotificationListenerService::class.java)

    // main thread only
    private var _isListening = false
    private var _artVersion = 0

    var controller: MediaController? = null
        private set

    /** The current track's art, read by the server endpoint from WebView threads. */
    @Volatile
    var art: Bitmap? = null
        private set

    private val _session = MutableStateFlow<SerializableMediaSession?>(null)
    val session = _session.asStateFlow()

    private val _sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        pickController(controllers.orEmpty())
    }

    private val _controllerCallback = object : MediaController.Callback()
    {
        override fun onMetadataChanged(metadata: MediaMetadata?) = update(metadataChanged = true)
        override fun onPlaybackStateChanged(state: PlaybackState?) = update(metadataChanged = false)
        override fun onSessionDestroyed() = pickController(getActiveSessions())
    }


    // region called by the notification listener service (main thread)

    fun startListening()
    {
        if (_isListening) return
        try
        {
            _sessionManager.addOnActiveSessionsChangedListener(_sessionsListener, _listenerComponent, _handler)
            _isListening = true
            pickController(getActiveSessions())
        }
        catch (ex: SecurityException)
        {
            Log.w(TAG, "startListening: no notification access", ex)
        }
    }

    fun stopListening()
    {
        if (!_isListening) return
        _sessionManager.removeOnActiveSessionsChangedListener(_sessionsListener)
        _isListening = false
        setController(null)
    }

    // endregion


    private fun getActiveSessions(): List<MediaController> = try
    {
        _sessionManager.getActiveSessions(_listenerComponent)
    }
    catch (ex: SecurityException)
    {
        emptyList()
    }

    // Android lists sessions by priority; prefer one that's actually playing
    private fun pickController(controllers: List<MediaController>)
    {
        val best = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()

        if (best?.sessionToken != controller?.sessionToken)
            setController(best)
    }

    private fun setController(newController: MediaController?)
    {
        controller?.unregisterCallback(_controllerCallback)
        controller = newController
        newController?.registerCallback(_controllerCallback, _handler)
        update(metadataChanged = true)
    }

    private fun update(metadataChanged: Boolean)
    {
        val c = controller
        if (c == null)
        {
            art = null
            _session.value = null
            return
        }

        val metadata = c.metadata
        if (metadataChanged)
        {
            art = metadata?.let {
                it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: it.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            }
            _artVersion++
        }

        val playback = c.playbackState
        val isPlaying = playback?.state == PlaybackState.STATE_PLAYING

        // the position Android reports is from its last update; bring it up to now
        val positionMs = playback?.let {
            if (isPlaying && it.lastPositionUpdateTime > 0)
                it.position + ((SystemClock.elapsedRealtime() - it.lastPositionUpdateTime) * it.playbackSpeed).toLong()
            else
                it.position
        }?.takeIf { it >= 0 }

        val actions = playback?.actions ?: 0L

        _session.value = SerializableMediaSession(
            packageName = c.packageName,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            state = MediaPlaybackStateStringOptions.fromPlaybackState(playback?.state),
            durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 },
            positionMs = positionMs,
            positionUpdatedAt = System.currentTimeMillis(),
            playbackSpeed = playback?.playbackSpeed ?: 1f,
            hasArt = art != null,
            artVersion = _artVersion,
            canSkipToNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSkipToPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
        )
    }
}
