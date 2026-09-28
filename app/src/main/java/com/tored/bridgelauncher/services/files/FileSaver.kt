package com.tored.bridgelauncher.services.files

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tored.bridgelauncher.api2.webview.BridgeFileChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.serialization.Serializable

private const val TAG = "FileSaver"

@Serializable
data class SerializableFileSaveResult(
    val fileName: String,
    /** `'saved'`, `'cancelled'` (the user closed Android's dialog) or `'failed'`. */
    val result: String,
)

/**
 * Saves text that a project generated (e.g. a backup) to a file the user picks with Android's "Save as"
 * dialog (`ACTION_CREATE_DOCUMENT`, no storage permission needed). The WebView can't download files,
 * so this is the only way to get them out.
 */
class FileSaver(
    private val _context: Context,
)
{
    private val _scope = CoroutineScope(Dispatchers.IO) + SupervisorJob()

    private val _results = MutableSharedFlow<SerializableFileSaveResult>(extraBufferCapacity = 4)
    val results = _results.asSharedFlow()

    /** Opens Android's dialog to pick where to save [content]; the outcome arrives through [results]. */
    fun save(chooser: BridgeFileChooser, fileName: String, mimeType: String, content: String)
    {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_TITLE, fileName)

        Handler(Looper.getMainLooper()).post {
            chooser.show(intent) { uris ->
                val uri = uris?.firstOrNull()
                if (uri == null)
                {
                    _results.tryEmit(SerializableFileSaveResult(fileName, RESULT_CANCELLED))
                    return@show
                }

                _scope.launch {
                    val result = try
                    {
                        _context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                            stream.write(content.toByteArray(Charsets.UTF_8))
                        } ?: throw Exception("Could not open $uri for writing.")
                        RESULT_SAVED
                    }
                    catch (ex: Exception)
                    {
                        Log.e(TAG, "Could not save $fileName", ex)
                        RESULT_FAILED
                    }
                    _results.tryEmit(SerializableFileSaveResult(fileName, result))
                }
            }
        }
    }

    companion object
    {
        const val RESULT_SAVED = "saved"
        const val RESULT_CANCELLED = "cancelled"
        const val RESULT_FAILED = "failed"
    }
}
