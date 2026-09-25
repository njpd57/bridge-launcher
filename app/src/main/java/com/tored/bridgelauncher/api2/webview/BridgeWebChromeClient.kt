package com.tored.bridgelauncher.api2.webview

import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebView
import com.tored.bridgelauncher.services.devconsole.DevConsoleMessagesHolder
import com.tored.bridgelauncher.webview.AccompanistWebChromeClient

private const val TAG = "BridgeWebChromeClient"

class BridgeWebChromeClient(
    private val _consoleMessageHolder: DevConsoleMessagesHolder,
) : AccompanistWebChromeClient()
{
    /** Set while the home screen activity exists; without it, file inputs are cancelled. */
    var fileChooser: BridgeFileChooser? = null

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean
    {
        return when (consoleMessage)
        {
            null -> super.onConsoleMessage(null)
            else ->
            {
                _consoleMessageHolder.addMessage(consoleMessage)
                true
            }
        }
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?,
    ): Boolean
    {
        val chooser = fileChooser
        if (chooser == null || filePathCallback == null || fileChooserParams == null)
        {
            Log.w(TAG, "onShowFileChooser: no file chooser available, cancelling")
            return false
        }

        chooser.show(fileChooserParams.createIntent()) { uris ->
            filePathCallback.onReceiveValue(uris)
        }
        return true
    }
}
