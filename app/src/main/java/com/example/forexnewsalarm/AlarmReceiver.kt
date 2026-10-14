package com.example.forexnewsalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Receives the exact alarm broadcast and launches AlarmService, which
 * plays a looping alarm until the user dismisses it.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("event_title") ?: "High Impact News"
        val currency = intent.getStringExtra("event_currency") ?: ""
        val source = intent.getStringExtra("event_source") ?: ""
        val eventTimeEpoch = intent.getLongExtra("event_time_utc", 0L)

        val timeStr = if (eventTimeEpoch > 0) {
            TimezoneHelper.toNepal(Instant.ofEpochSecond(eventTimeEpoch))
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        } else ""

        Log.d("AlarmReceiver", "Alarm fired: $title ($currency) at $timeStr NPT from $source")

        val svc = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_TITLE, title)
            putExtra(AlarmService.EXTRA_CURRENCY, currency)
            putExtra(AlarmService.EXTRA_TIME, timeStr)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}