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
 * Schedules exact alarms for high-impact news events.
 * Alarms fire 9 minutes before each event, in Asia/Kathmandu time.
 */
class AlarmScheduler(private val context: Context) {

    companion object {
        private const val TAG = "AlarmScheduler"
        const val MINUTES_BEFORE = 9L
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule an alarm for a single news event.
     * Returns true if scheduled successfully.
     */
    fun scheduleAlarm(event: NewsEvent): Boolean {
        val alertTime: ZonedDateTime = TimezoneHelper.alertTimeInNepal(
            event.eventTimeUtc, MINUTES_BEFORE
        )

        // Don't schedule alarms in the past.
        if (alertTime.toInstant().isBefore(Instant.now())) {
            Log.d(TAG, "Skipping past event: ${event.title}")
            return false
        }

        val triggerAtMillis = alertTime.toInstant().toEpochMilli()
        val pendingIntent = buildPendingIntent(event)

        return try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.d(TAG, "Exact alarm scheduled for ${event.title} at $alertTime")
            } else {
                // Fallback: inexact alarm (may be delayed up to 15 min).
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                Log.w(TAG, "Inexact alarm scheduled for ${event.title} at $alertTime")
            }
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException scheduling alarm", e)
            false
        }
    }

    /**
     * Schedule alarms for a list of events.
     */
    fun scheduleAll(events: List<NewsEvent>): Int {
        var count = 0
        for (event in events) {
            if (scheduleAlarm(event)) count++
        }
        return count
    }

    /**
     * Cancel the alarm for a specific event.
     */
    fun cancelAlarm(event: NewsEvent) {
        val pendingIntent = buildPendingIntent(event, cancel = true)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * Check whether the app has permission to schedule exact alarms.
     * On Android 12+ this requires SCHEDULE_EXACT_ALARM; on Android 14+
     * it is denied by default and must be granted by the user.
     */
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private fun buildPendingIntent(event: NewsEvent, cancel: Boolean = false): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.forexnewsalarm.NEWS_ALARM"
            putExtra("event_title", event.title)
            putExtra("event_currency", event.currency)
            putExtra("event_source", event.source)
            putExtra("event_time_utc", event.eventTimeUtc.epochSecond)
        }

        val flags = if (cancel) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        }

        return PendingIntent.getBroadcast(
            context,
            event.uniqueId.hashCode(),
            intent,
            flags
        ) ?: PendingIntent.getBroadcast(
            context,
            event.uniqueId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
