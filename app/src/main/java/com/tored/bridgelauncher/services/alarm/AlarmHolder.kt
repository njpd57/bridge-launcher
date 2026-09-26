package com.tored.bridgelauncher.services.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The next alarm clock set on the device (from any clock app), for the status bar's alarm icon and the
 * clock widgets. Read from `AlarmManager.getNextAlarmClock()` and refreshed on
 * `ACTION_NEXT_ALARM_CLOCK_CHANGED`; no permission needed.
 */
class AlarmHolder(
    private val _context: Context,
)
{
    private val _alarmManager = _context.getSystemService(AlarmManager::class.java)

    private val _nextAlarm = MutableStateFlow(readNextAlarm())
    val nextAlarm = _nextAlarm.asStateFlow()

    private val _receiver = object : BroadcastReceiver()
    {
        override fun onReceive(context: Context, intent: Intent)
        {
            _nextAlarm.value = readNextAlarm()
        }
    }

    fun startup()
    {
        ContextCompat.registerReceiver(
            _context,
            _receiver,
            IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun readNextAlarm(): SerializableNextAlarm?
    {
        val info = _alarmManager?.nextAlarmClock ?: return null
        return SerializableNextAlarm(
            triggerTime = info.triggerTime,
            packageName = info.showIntent?.creatorPackage,
        )
    }
}
