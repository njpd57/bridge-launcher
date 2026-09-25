package com.tored.bridgelauncher.api2.webview

import android.content.Intent
import android.net.Uri

/** Shows the system file picker for an `<input type="file">`. Implemented by the activity hosting the WebView. */
fun interface BridgeFileChooser
{
    /** Launches [intent] and calls [onResult] exactly once, with the picked files or null if cancelled. */
    fun show(intent: Intent, onResult: (uris: Array<Uri>?) -> Unit)
}
