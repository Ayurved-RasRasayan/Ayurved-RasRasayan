package com.example.forexnewsalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Fires every 5 minutes.
 *  - If KeeperService is running: does nothing, reschedules.
 *  - If KeeperService is dead: starts it back up.
 */
class WatchdogReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WatchdogReceiver"
        private const val ACTION = "com.example.forexnewsalarm.WATCHDOG"
        private const val INTERVAL_MS = 5L * 60L * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        if (!KeeperService.isRunning) {
            Log.w(TAG, "KeeperService not running  restarting")
            val svc = Intent(context, KeeperService::class.java).apply {
                action = KeeperService.ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc)
                } else {
                    context.startService(svc)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart KeeperService", e)
            }
        } else {
            Log.d(TAG, "KeeperService is alive")
        }

        // Reschedule the next watchdog
        val next = Intent(context, WatchdogReceiver::class.java).apply {
            action = ACTION
        }
        val pi = PendingIntent.getBroadcast(
            context, 11, next,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + INTERVAL_MS, pi)
    }
}