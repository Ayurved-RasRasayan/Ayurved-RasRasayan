package com.example.forexnewsalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.Instant

/**
 * Receives alarm broadcasts and posts a high-priority notification
 * 9 minutes before a high-impact news event.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "news_alerts"
        const val CHANNEL_NAME = "High Impact News Alerts"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("event_title") ?: "High Impact News"
        val currency = intent.getStringExtra("event_currency") ?: ""
        val source = intent.getStringExtra("event_source") ?: ""
        val eventTimeEpoch = intent.getLongExtra("event_time_utc", 0L)

        createNotificationChannel(context)

        val eventTime = if (eventTimeEpoch > 0) {
            TimezoneHelper.toNepal(Instant.ofEpochSecond(eventTimeEpoch))
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        } else ""

        val contentText = buildString {
            append(if (currency.isNotBlank()) "$currency " else "")
            append("in 9 minutes")
            if (eventTime.isNotBlank()) append(" (event at $eventTime NPT)")
            if (source.isNotBlank()) append("  $source")
        }

        val notificationIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(" $title")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(title.hashCode(), notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts 9 minutes before high-impact economic news"
                enableVibration(true)
                enableLights(true)
            }
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
