package com.example.forexnewsalarm

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log

/**
 * Daily background worker that re-fetches events and reschedules alarms.
 * This handles the weekly export limitation and event rescheduling.
 */
class RefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val events = CalendarScraper().fetchAllHighImpactEvents()
            val scheduler = AlarmScheduler(applicationContext)
            val count = scheduler.scheduleAll(events)
            Log.d("RefreshWorker", "Daily refresh: scheduled $count alarms")
            Result.success()
        } catch (e: Exception) {
            Log.e("RefreshWorker", "Daily refresh failed", e)
            Result.retry()
        }
    }
}
