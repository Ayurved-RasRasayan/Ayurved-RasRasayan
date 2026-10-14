package com.example.forexnewsalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-schedules alarms after device reboot.
 * Android clears all alarms on reboot, so we must re-fetch and re-schedule.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val events = CalendarScraper().fetchAllHighImpactEvents()
                val scheduler = AlarmScheduler(context)
                val count = scheduler.scheduleAll(events)
                android.util.Log.d("BootReceiver", "Re-scheduled $count alarms after boot")
            } catch (e: Exception) {
                android.util.Log.e("BootReceiver", "Failed to reschedule after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
