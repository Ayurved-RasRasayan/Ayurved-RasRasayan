package com.example.forexnewsalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Schedules "alarm clock" style alarms for high-impact news events.
 * setAlarmClock() is the strongest alarm API on Android: it wakes the
 * device fully, bypasses Doze, and shows the system alarm icon.
 */
class AlarmScheduler(private val context: Context) {

    companion object {
        private const val TAG = "AlarmScheduler"
        const val MINUTES_BEFORE = 9L
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(event: NewsEvent): Boolean {
        val alertTime: ZonedDateTime =
            TimezoneHelper.alertTimeInNepal(event.eventTimeUtc, MINUTES_BEFORE)

        if (alertTime.toInstant().isBefore(Instant.now())) {
            Log.d(TAG, "Skipping past event: ${event.title}")
            return false
        }

        val triggerAtMillis = alertTime.toInstant().toEpochMilli()
        val operation = buildOperationPendingIntent(event)
        val showIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return try {
            if (canScheduleExactAlarms()) {
                val info = AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent)
                alarmManager.setAlarmClock(info, operation)
                Log.d(TAG, "AlarmClock scheduled: ${event.title} @ $alertTime")
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, operation
                )
                Log.w(TAG, "Inexact fallback scheduled: ${event.title} @ $alertTime")
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException", e)
            false
        }
    }

    fun scheduleAll(events: List<NewsEvent>): Int {
        var n = 0
        for (e in events) if (scheduleAlarm(e)) n++
        return n
    }

    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            alarmManager.canScheduleExactAlarms()
        else true

    private fun buildOperationPendingIntent(event: NewsEvent): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.forexnewsalarm.NEWS_ALARM"
            putExtra("event_title", event.title)
            putExtra("event_currency", event.currency)
            putExtra("event_source", event.source)
            putExtra("event_time_utc", event.eventTimeUtc.epochSecond)
        }
        return PendingIntent.getBroadcast(
            context,
            event.uniqueId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}