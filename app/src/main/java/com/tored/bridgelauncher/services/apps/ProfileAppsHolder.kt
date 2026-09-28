package com.tored.bridgelauncher.services.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.tored.bridgelauncher.utils.q
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ProfileApps"

// used when a drawable has no intrinsic size
private const val FALLBACK_ICON_SIZE_PX = 96

@Serializable
data class SerializableProfileApp(
    val packageName: String,
    val label: String,
    /** Identifies the profile (user) the app belongs to; stable across reboots. */
    val userSerial: Long,
    /** `'personal'` for Bridge's own profile, `'work'` for any other profile (the work profile). */
    val profile: String,
    /** Whether the app's profile is paused ("work apps" turned off). */
    val isPaused: Boolean,
)

data class ProfileApp(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val userSerial: Long,
    val isPersonal: Boolean,
    val isPaused: Boolean,
    val activityInfo: LauncherActivityInfo,
)
{
    val key = "$packageName@$userSerial"

    fun toSerializable() = SerializableProfileApp(
        packageName = packageName,
        label = label,
        userSerial = userSerial,
        profile = if (isPersonal) PROFILE_PERSONAL else PROFILE_WORK,
        isPaused = isPaused,
    )

    companion object
    {
        const val PROFILE_PERSONAL = "personal"
        const val PROFILE_WORK = "work"
    }
}

/**
 * Launchable apps of every profile of the user (the personal one and the work profile), read through
 * [LauncherApps], like the system launcher does. [InstalledAppsHolder] keeps serving the personal
 * profile only; this holder is what lets projects tell personal Teams and work Teams apart (same package).
 */
class ProfileAppsHolder(
    private val _context: Context,
)
{
    private val _scope = CoroutineScope(Dispatchers.Default) + SupervisorJob()

    private val _launcherApps = _context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val _userManager = _context.getSystemService(Context.USER_SERVICE) as UserManager

    @Volatile
    private var _apps: List<ProfileApp> = emptyList()

    private val _iconPngCache = ConcurrentHashMap<String, ByteArray>()

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes = _changes.asSharedFlow()

    private val _launcherAppsCallback = object : LauncherApps.Callback()
    {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = launchReload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = launchReload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = launchReload()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = launchReload()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = launchReload()
        override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) = launchReload()
        override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle?) = launchReload()
    }

    // profile added/removed, paused/resumed ("work apps" toggle) and unlocked
    private val _profileReceiver = object : BroadcastReceiver()
    {
        override fun onReceive(context: Context, intent: Intent)
        {
            Log.d(TAG, "onReceive: ${intent.action}")
            launchReload()
        }
    }

    fun getApps() = _apps

    fun find(packageName: String, userSerial: Long) = _apps.firstOrNull { it.packageName == packageName && it.userSerial == userSerial }

    fun findOrThrow(packageName: String, userSerial: Long) = find(packageName, userSerial)
        ?: throw Exception("No app ${q(packageName)} in profile $userSerial.")

    /** The app's icon as a PNG, with the profile's badge (the work briefcase) if it has one. */
    fun getIconPng(app: ProfileApp): ByteArray
    {
        return _iconPngCache.getOrPut(app.key) {
            val drawable = app.activityInfo.getBadgedIcon(0)
            val bmp = drawable.toBitmap(
                width = drawable.intrinsicWidth.takeIf { it > 0 } ?: FALLBACK_ICON_SIZE_PX,
                height = drawable.intrinsicHeight.takeIf { it > 0 } ?: FALLBACK_ICON_SIZE_PX,
            )
            ByteArrayOutputStream().use { stream ->
                bmp.compress(Bitmap.CompressFormat.PNG, 90, stream)
                stream.toByteArray()
            }
        }
    }

    /** Starts the app in its profile. If the profile is paused, Android asks whether to turn work apps on. */
    fun launch(packageName: String, userSerial: Long)
    {
        val app = findOrThrow(packageName, userSerial)
        _launcherApps.startMainActivity(app.component, app.user, null, null)
    }

    fun openAppInfo(packageName: String, userSerial: Long)
    {
        val app = findOrThrow(packageName, userSerial)
        _launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }

    private fun reload()
    {
        val myUser = Process.myUserHandle()

        val apps = _userManager.userProfiles.flatMap { user ->
            try
            {
                val userSerial = _userManager.getSerialNumberForUser(user)
                val isPaused = _userManager.isQuietModeEnabled(user)

                _launcherApps.getActivityList(null, user)
                    .distinctBy { it.componentName.packageName }
                    .map { info ->
                        ProfileApp(
                            packageName = info.componentName.packageName,
                            label = info.label.toString(),
                            component = info.componentName,
                            user = user,
                            userSerial = userSerial,
                            isPersonal = user == myUser,
                            isPaused = isPaused,
                            activityInfo = info,
                        )
                    }
            }
            catch (ex: Exception)
            {
                Log.w(TAG, "reload: could not list the apps of profile $user", ex)
                emptyList()
            }
        }

        _iconPngCache.clear()
        _apps = apps
        _changes.tryEmit(Unit)

        Log.d(TAG, "reload: ${apps.size} apps, ${apps.count { !it.isPersonal }} in other profiles")
    }

    @Synchronized
    private fun reloadSynchronized() = reload()

    private fun launchReload()
    {
        _scope.launch { reloadSynchronized() }
    }

    fun startup()
    {
        _launcherApps.registerCallback(_launcherAppsCallback, Handler(Looper.getMainLooper()))

        ContextCompat.registerReceiver(
            _context,
            _profileReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_MANAGED_PROFILE_ADDED)
                addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED)
                addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        launchReload()
    }
}
