package com.tored.bridgelauncher.services.quicksettings

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

private const val TAG = "QuickSettingsHolder"

private const val MAX_SCREEN_BRIGHTNESS = 255

@Serializable
data class ScreenBrightness(
    val isAuto: Boolean,
    /** Manual brightness, 0 to 1. */
    val level: Float,
)

/**
 * System toggles that an app can still change itself: flashlight, screen brightness, auto-rotate
 * and master sync. Wi-Fi, Bluetooth, mobile data and location can't be toggled by apps on current
 * Android versions; for those, projects can only open the system panels.
 */
class QuickSettingsHolder(
    private val _context: Context,
)
{
    private val _handler = Handler(Looper.getMainLooper())
    private val _resolver = _context.contentResolver
    private val _cameraManager = _context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /** A back camera with a flash unit, or null if the device has no flashlight. */
    val flashlightCameraId: String? = try
    {
        _cameraManager.cameraIdList.firstOrNull {
            _cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }
    catch (ex: Exception)
    {
        Log.e(TAG, "Could not look for a flashlight", ex)
        null
    }

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn = _isFlashlightOn.asStateFlow()

    private val _screenBrightness = MutableStateFlow(readScreenBrightness())
    val screenBrightness = _screenBrightness.asStateFlow()

    private val _isAutoRotateOn = MutableStateFlow(readIsAutoRotateOn())
    val isAutoRotateOn = _isAutoRotateOn.asStateFlow()

    private val _isMasterSyncOn = MutableStateFlow(ContentResolver.getMasterSyncAutomatically())
    val isMasterSyncOn = _isMasterSyncOn.asStateFlow()


    fun startup()
    {
        if (flashlightCameraId != null)
        {
            _cameraManager.registerTorchCallback(object : CameraManager.TorchCallback()
            {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean)
                {
                    if (cameraId == flashlightCameraId)
                        _isFlashlightOn.value = enabled
                }

                override fun onTorchModeUnavailable(cameraId: String)
                {
                    // e.g. the camera app is using the camera
                    if (cameraId == flashlightCameraId)
                        _isFlashlightOn.value = false
                }
            }, _handler)
        }

        observeSystemSetting(Settings.System.SCREEN_BRIGHTNESS, Settings.System.SCREEN_BRIGHTNESS_MODE) {
            _screenBrightness.value = readScreenBrightness()
        }

        observeSystemSetting(Settings.System.ACCELEROMETER_ROTATION) {
            _isAutoRotateOn.value = readIsAutoRotateOn()
        }

        // called on a background thread; MutableStateFlow is thread safe
        ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS) {
            _isMasterSyncOn.value = ContentResolver.getMasterSyncAutomatically()
        }
    }


    // region changing settings

    fun setFlashlightOn(isOn: Boolean)
    {
        val cameraId = flashlightCameraId ?: throw Exception("This device has no flashlight.")
        _cameraManager.setTorchMode(cameraId, isOn)
    }

    /** Requires [Settings.System.canWrite]. */
    fun setScreenBrightnessAuto(isAuto: Boolean)
    {
        Settings.System.putInt(
            _resolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (isAuto) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
    }

    /** Switches to manual brightness at [level] (0 to 1). Requires [Settings.System.canWrite]. */
    fun setScreenBrightnessLevel(level: Float)
    {
        if (level.isNaN() || level < 0f || level > 1f)
            throw Exception("Brightness level must be between 0 and 1 (got $level).")

        setScreenBrightnessAuto(false)
        Settings.System.putInt(_resolver, Settings.System.SCREEN_BRIGHTNESS, (level * MAX_SCREEN_BRIGHTNESS).toInt().coerceAtLeast(1))
    }

    /** Requires [Settings.System.canWrite]. */
    fun setAutoRotateOn(isOn: Boolean)
    {
        Settings.System.putInt(_resolver, Settings.System.ACCELEROMETER_ROTATION, if (isOn) 1 else 0)
    }

    fun setMasterSyncOn(isOn: Boolean)
    {
        ContentResolver.setMasterSyncAutomatically(isOn)
    }

    // endregion


    private fun readScreenBrightness() = ScreenBrightness(
        isAuto = Settings.System.getInt(_resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        level = Settings.System.getInt(_resolver, Settings.System.SCREEN_BRIGHTNESS, MAX_SCREEN_BRIGHTNESS).toFloat() / MAX_SCREEN_BRIGHTNESS,
    )

    private fun readIsAutoRotateOn() = Settings.System.getInt(_resolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    private fun observeSystemSetting(vararg names: String, onChange: () -> Unit)
    {
        val observer = object : ContentObserver(_handler)
        {
            override fun onChange(selfChange: Boolean) = onChange()
        }

        for (name in names)
            _resolver.registerContentObserver(Settings.System.getUriFor(name), false, observer)
    }
}
