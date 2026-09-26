package com.tored.bridgelauncher.services.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.annotation.RequiresApi
import com.tored.bridgelauncher.utils.CurrentAndroidVersion
import kotlinx.serialization.Serializable

private const val TAG = "AppShortcutsHolder"

@Serializable
data class SerializableAppShortcut(
    val id: String,
    val shortLabel: String,
    val longLabel: String?,
)

/**
 * Apps' shortcuts ("New message", "Navigate home"...), the ones launchers show when long-pressing an app.
 * Android only gives them to the default launcher, which Bridge is.
 */
class AppShortcutsHolder(
    private val _context: Context,
)
{
    private val _launcherApps = _context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    val canAccess: Boolean
        get() = CurrentAndroidVersion.supportsAppShortcuts() && try
        {
            _launcherApps.hasShortcutHostPermission()
        }
        catch (ex: Exception)
        {
            false
        }

    /** The app's shortcuts in the order launchers show them: the ones from its manifest first, then dynamic ones, each by rank. */
    fun getShortcuts(packageName: String): List<ShortcutInfo>
    {
        if (!canAccess) return emptyList()
        return try
        {
            queryShortcuts(packageName)
        }
        catch (ex: Exception)
        {
            Log.w(TAG, "Could not get the shortcuts of $packageName", ex)
            emptyList()
        }
    }

    fun getShortcut(packageName: String, shortcutId: String) = getShortcuts(packageName).firstOrNull { it.id == shortcutId }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun getIcon(shortcut: ShortcutInfo): Drawable? = _launcherApps.getShortcutIconDrawable(shortcut, _context.resources.displayMetrics.densityDpi)

    fun start(packageName: String, shortcutId: String)
    {
        if (!canAccess)
            throw Exception("Bridge can't use app shortcuts (it has to be the default launcher, on Android 7.1 or newer).")
        _launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun queryShortcuts(packageName: String): List<ShortcutInfo>
    {
        val query = LauncherApps.ShortcutQuery()
            .setPackage(packageName)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)

        return _launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
    }
}

@RequiresApi(Build.VERSION_CODES.N_MR1)
fun ShortcutInfo.toSerializable() = SerializableAppShortcut(
    id = id,
    shortLabel = shortLabel?.toString() ?: id,
    longLabel = longLabel?.toString(),
)
