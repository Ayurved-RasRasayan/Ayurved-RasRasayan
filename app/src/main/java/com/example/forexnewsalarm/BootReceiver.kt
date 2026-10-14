package com.example.forexnewsalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * On device boot, start KeeperService so alarms are scheduled again.
 * Android clears all setAlarmClock() alarms when the phone reboots.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.action ?: return
        if (a != Intent.ACTION_BOOT_COMPLETED &&
            a != "android.intent.action.QUICKBOOT_POWERON" &&
            a != "com.htc.intent.action.QUICKBOOT_POWERON") return

        Log.d("BootReceiver", "Boot detected  starting KeeperService")

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
            Log.e("BootReceiver", "Failed to start KeeperService", e)
        }
    }
}