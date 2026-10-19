package com.tored.bridgelauncher.services.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

private const val TAG = "TtsSpeaker"

/**
 * Speaks text through the Android text-to-speech engine. The engine starts when the first instance is created;
 * [isReady] turns true once it has bound. Needs no runtime permission, only an installed engine.
 */
class TtsSpeaker(context: Context)
{
    private var _tts: TextToSpeech? = null

    @Volatile
    var isReady = false
        private set

    init
    {
        _tts = TextToSpeech(context.applicationContext) { status ->
            isReady = status == TextToSpeech.SUCCESS
            if (!isReady) Log.w(TAG, "Text-to-speech engine failed to start (status $status)")
        }
    }

    /** Replaces whatever is being said. False when the engine isn't ready or the language has no voice data. */
    fun speak(text: String, languageTag: String, pitch: Float, rate: Float): Boolean
    {
        val engine = _tts ?: return false
        if (!isReady || text.isBlank()) return false

        val language = engine.setLanguage(Locale.forLanguageTag(languageTag))
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) return false

        engine.setPitch(pitch)
        engine.setSpeechRate(rate)
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bridge-tts") == TextToSpeech.SUCCESS
    }

    fun stop()
    {
        _tts?.stop()
    }
}
