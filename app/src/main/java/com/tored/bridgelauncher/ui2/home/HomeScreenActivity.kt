package com.tored.bridgelauncher.ui2.home

import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.tored.bridgelauncher.api2.webview.BridgeFileChooser
import com.tored.bridgelauncher.ui2.home.composables.HomeScreen2
import com.tored.bridgelauncher.ui2.theme.BridgeLauncherTheme

private val TAG = HomeScreenActivity::class.simpleName

class HomeScreenActivity : ComponentActivity()
{
    private lateinit var _modeman: UiModeManager

    private val _homeScreenVM: HomeScreen2VM by viewModels { HomeScreen2VM.Factory }

    // the WebView waits for exactly one result per file chooser it opens, so the callback is kept until then
    private var _pendingFileChooserResult: ((Array<Uri>?) -> Unit)? = null

    private val _fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult())
    { result ->
        val uris = when (result.resultCode)
        {
            RESULT_OK -> result.data?.getPickedUris()
            else -> null
        }
        _pendingFileChooserResult?.invoke(uris)
        _pendingFileChooserResult = null
    }

    private val _fileChooser = BridgeFileChooser { intent, onResult ->
        _pendingFileChooserResult?.invoke(null)
        _pendingFileChooserResult = onResult
        try
        {
            _fileChooserLauncher.launch(intent)
        }
        catch (ex: ActivityNotFoundException)
        {
            Log.e(TAG, "No activity to pick files with", ex)
            _pendingFileChooserResult = null
            onResult(null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        _modeman = getSystemService(UI_MODE_SERVICE) as UiModeManager

        _homeScreenVM.afterCreate(this, _fileChooser)

        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        // immediately start another activity for debugging
//        tryStartBridgeAppDrawerActivity()
//        tryStartBridgeSettingsActivity()
//        tryStartDevConsoleActivity()

        setContent {
            BridgeLauncherTheme {
                HomeScreen2(_homeScreenVM)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration)
    {
        super.onConfigurationChanged(newConfig)
        _homeScreenVM.onConfigurationChanged()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean)
    {
        super.onWindowFocusChanged(hasFocus)
        // e.g. the notification shade was closed; it doesn't pause the activity
        if (hasFocus)
            _homeScreenVM.afterFocusGained()
    }

    override fun onPause()
    {
        _homeScreenVM.beforePause()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent)
    {
        super.onNewIntent(intent)
        _homeScreenVM.onNewIntent()
    }

    override fun onResume()
    {
        super.onResume()
        _homeScreenVM.afterResume()
    }

    override fun onDestroy()
    {
        _pendingFileChooserResult?.invoke(null)
        _pendingFileChooserResult = null
        _homeScreenVM.beforeDestroy()
        super.onDestroy()
    }
}

/** Multi-select pickers return their files in clipData; single-select ones in data. */
private fun Intent.getPickedUris(): Array<Uri>?
{
    val clip = clipData
    return when
    {
        clip != null && clip.itemCount > 0 -> Array(clip.itemCount) { clip.getItemAt(it).uri }
        data != null -> arrayOf(data!!)
        else -> null
    }
}
