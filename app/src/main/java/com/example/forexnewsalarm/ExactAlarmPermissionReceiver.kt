package com.example.forexnewsalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Listens for the user granting SCHEDULE_EXACT_ALARM permission
 * and immediately reschedules all alarms with exact timing.
 */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED") return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val events = CalendarScraper().fetchAllHighImpactEvents()
                val scheduler = AlarmScheduler(context)
                val count = scheduler.scheduleAll(events)
                android.util.Log.d("ExactAlarmPerm", "Re-scheduled $count exact alarms")
            } catch (e: Exception) {
                android.util.Log.e("ExactAlarmPerm", "Failed to reschedule", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
