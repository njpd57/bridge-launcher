package com.tored.bridgelauncher.services.usage

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.tored.bridgelauncher.utils.CurrentAndroidVersion
import kotlinx.serialization.Serializable

@Serializable
data class SerializableAppUsage(
    val packageName: String,
    /** Time in the foreground within the range, in milliseconds. */
    val totalTimeMs: Long,
    /** How many times the user switched to the app within the range. */
    val openCount: Int,
    /** Milliseconds since the epoch, or null if it wasn't used within the range. */
    val lastTimeUsed: Long?,
)

/**
 * App usage from Android's own records (needs the special "Usage access", granted in Android's settings).
 * Computed from the activity events rather than UsageStats' daily buckets, which spill outside the range.
 */
class UsageStatsHolder(
    private val _context: Context,
)
{
    private val _usageStatsManager = _context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val _appOps = _context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

    val canRead: Boolean
        get()
        {
            @Suppress("DEPRECATION")
            val mode = if (CurrentAndroidVersion.supportsUnsafeCheckOp())
                _appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), _context.packageName)
            else
                _appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), _context.packageName)

            return if (mode == AppOpsManager.MODE_DEFAULT)
                _context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
            else
                mode == AppOpsManager.MODE_ALLOWED
        }

    /** Usage per app within [fromMs, toMs), most used first. Bridge itself is left out. */
    fun getUsage(fromMs: Long, toMs: Long): List<SerializableAppUsage>
    {
        if (!canRead) throw SecurityException("Bridge doesn't have usage access.")

        val totalTime = HashMap<String, Long>()
        val opens = HashMap<String, Int>()
        val lastUsed = HashMap<String, Long>()
        // the package in the foreground and since when
        var foreground: String? = null
        var foregroundSince = fromMs
        val seenAnyEvent = HashSet<String>()

        val events = _usageStatsManager.queryEvents(fromMs, toMs)
        val e = UsageEvents.Event()
        while (events.hasNextEvent())
        {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType)
            {
                UsageEvents.Event.ACTIVITY_RESUMED ->
                {
                    if (pkg != foreground)
                    {
                        opens[pkg] = (opens[pkg] ?: 0) + 1
                        // another app was still counted as in the foreground; it lost it now
                        foreground?.let { add(totalTime, it, e.timeStamp - foregroundSince) }
                        foreground = pkg
                        foregroundSince = e.timeStamp
                    }
                    lastUsed[pkg] = e.timeStamp
                }

                UsageEvents.Event.ACTIVITY_PAUSED ->
                {
                    if (pkg == foreground)
                    {
                        add(totalTime, pkg, e.timeStamp - foregroundSince)
                        foreground = null
                    }
                    // resumed before the range started: count from its start
                    else if (pkg !in seenAnyEvent)
                        add(totalTime, pkg, e.timeStamp - fromMs)
                }
            }
            seenAnyEvent.add(pkg)
        }
        // still in the foreground when the range ends
        foreground?.let { add(totalTime, it, minOf(toMs, System.currentTimeMillis()) - foregroundSince) }

        return (totalTime.keys + opens.keys)
            .filter { it != _context.packageName }
            .map { pkg ->
                SerializableAppUsage(
                    packageName = pkg,
                    totalTimeMs = totalTime[pkg] ?: 0L,
                    openCount = opens[pkg] ?: 0,
                    lastTimeUsed = lastUsed[pkg],
                )
            }
            .sortedByDescending { it.totalTimeMs }
    }

    private fun add(map: HashMap<String, Long>, pkg: String, ms: Long)
    {
        if (ms > 0) map[pkg] = (map[pkg] ?: 0L) + ms
    }
}
