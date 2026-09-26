package com.tored.bridgelauncher.api2.webview

/** Shows Android's permission dialog. Implemented by the activity hosting the WebView. */
fun interface BridgeRuntimePermissionRequester
{
    /**
     * Asks for [permission] and calls [onResult] with whether it's granted. When the user already refused
     * it for good, Android shows no dialog; the implementation then opens Bridge's app settings instead.
     */
    fun request(permission: String, onResult: (isGranted: Boolean) -> Unit)
}
