package com.example.forexnewsalarm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Always-on foreground service.
 *
 *  - Runs 24/7 with a low-priority persistent notification.
 *  - Every 15 minutes: fetches events and reschedules alarms.
 *  - START_STICKY: Android restarts the service if the OS kills it.
 *  - onTaskRemoved: if the user swipes the app away, restart via AlarmManager.
 *  - Schedules a WatchdogReceiver every 5 minutes as a backup.
 */
class KeeperService : Service() {

    companion object {
        const val TAG = "KeeperService"
        const val CHANNEL_ID = "keeper_channel"
        const val NOTIFICATION_ID = 9002
        const val ACTION_START = "com.example.forexnewsalarm.KEEPER_START"
        const val ACTION_REFRESH_NOW = "com.example.forexnewsalarm.KEEPER_REFRESH"
        const val REFRESH_INTERVAL_MS = 15L * 60L * 1000L
        const val WATCHDOG_INTERVAL_MS = 5L * 60L * 1000L

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastCount: Int = 0

    private val refreshTask = object : Runnable {
        override fun run() {
            refreshAndSchedule()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannel()
        startForegroundCompat(buildNotification(0))
        Log.d(TAG, "KeeperService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Immediate refresh whenever we're started (boot, app launch, watchdog)
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
        scheduleWatchdog()
        Log.d(TAG, "KeeperService started (action=${intent?.action})")
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(refreshTask)
        Log.d(TAG, "KeeperService destroyed  scheduling restart")
        scheduleRestart(5000L)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "Task removed  scheduling fast restart")
        scheduleRestart(1000L)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ----------------------------------------------------------------
    // Core work
    // ----------------------------------------------------------------
    private fun refreshAndSchedule() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val events = CalendarScraper().fetchAllHighImpactEvents()
                val scheduler = AlarmScheduler(applicationContext)
                val count = scheduler.scheduleAll(events)
                lastCount = count
                withContext(Dispatchers.Main) {
                    updateNotification(count)
                }
                Log.d(TAG, "Refreshed: ${events.size} events, $count alarms")
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
            }
        }
    }

    // ----------------------------------------------------------------
    // Scheduling
    // ----------------------------------------------------------------
    private fun scheduleWatchdog() {
        val intent = Intent(this, WatchdogReceiver::class.java).apply {
            action = "com.example.forexnewsalarm.WATCHDOG"
        }
        val pi = PendingIntent.getBroadcast(
            this, 11, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + WATCHDOG_INTERVAL_MS, pi)
    }

    private fun scheduleRestart(delayMs: Long) {
        val restartIntent = Intent(applicationContext, KeeperService::class.java).apply {
            action = ACTION_START
        }
        val pi = PendingIntent.getService(
            this, 10, restartIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pi)
    }

    // ----------------------------------------------------------------
    // Notification
    // ----------------------------------------------------------------
    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, n,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun buildNotification(alarmCount: Int): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Forex News Alarm active")
            .setContentText("$alarmCount high-impact alarms scheduled")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openPi)
            .build()
    }

    private fun updateNotification(count: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(count))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Background Keeper",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the alarm schedule running 24/7"
                setShowBadge(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }
}