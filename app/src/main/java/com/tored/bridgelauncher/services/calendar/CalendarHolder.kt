package com.tored.bridgelauncher.services.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable

private const val TAG = "CalendarHolder"

// the provider can report many changes in a row while syncing
private const val CHANGE_DEBOUNCE_MS = 1000L

@Serializable
data class SerializableCalendarEvent(
    val eventId: Long,
    val title: String?,
    val location: String?,
    /** Milliseconds since the epoch. For all-day events, midnight UTC of the first day. */
    val begin: Long,
    /** Milliseconds since the epoch. For all-day events, midnight UTC of the day after the last one. */
    val end: Long,
    val allDay: Boolean,
    /** `#rrggbb`, the color the calendar app shows the event in. */
    val color: String?,
    val calendarName: String?,
)

/** The user's calendar events (needs READ_CALENDAR), with a debounced change signal. */
class CalendarHolder(
    private val _context: Context,
)
{
    private val _handler = Handler(Looper.getMainLooper())
    private var _isObserving = false

    // emits Unit whenever events may have changed
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes = _changes.asSharedFlow()

    private val _emitChange = Runnable { _changes.tryEmit(Unit) }

    val canRead: Boolean
        get() = ContextCompat.checkSelfPermission(_context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Starts watching the calendar provider; only works once READ_CALENDAR is granted, so it's retried when it may have been. */
    fun startObservingIfPossible()
    {
        if (_isObserving || !canRead) return
        try
        {
            _context.contentResolver.registerContentObserver(
                CalendarContract.CONTENT_URI,
                true,
                object : ContentObserver(_handler)
                {
                    override fun onChange(selfChange: Boolean)
                    {
                        _handler.removeCallbacks(_emitChange)
                        _handler.postDelayed(_emitChange, CHANGE_DEBOUNCE_MS)
                    }
                },
            )
            _isObserving = true
        }
        catch (ex: Exception)
        {
            Log.w(TAG, "Could not observe the calendar", ex)
        }
    }

    /** Event instances (recurring events expanded) overlapping [fromMs, toMs), from visible calendars, by start time. */
    fun getEvents(fromMs: Long, toMs: Long): List<SerializableCalendarEvent>
    {
        if (!canRead) throw SecurityException("Bridge doesn't have the calendar permission.")

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMs)
            ContentUris.appendId(it, toMs)
        }.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )

        val events = mutableListOf<SerializableCalendarEvent>()
        _context.contentResolver.query(
            uri,
            projection,
            "${CalendarContract.Instances.VISIBLE} = 1",
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext())
            {
                events.add(
                    SerializableCalendarEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1),
                        location = c.getString(2)?.takeIf { it.isNotBlank() },
                        begin = c.getLong(3),
                        end = c.getLong(4),
                        allDay = c.getInt(5) != 0,
                        color = if (c.isNull(6)) null else String.format("#%06x", c.getInt(6) and 0xffffff),
                        calendarName = c.getString(7),
                    )
                )
            }
        }
        return events
    }
}
