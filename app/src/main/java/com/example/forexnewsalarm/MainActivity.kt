package com.example.forexnewsalarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var refreshButton: Button
    private lateinit var permissionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        refreshButton = findViewById(R.id.refreshButton)
        permissionButton = findViewById(R.id.permissionButton)

        refreshButton.setOnClickListener { refreshEvents() }
        permissionButton.setOnClickListener { requestExactAlarmPermission() }

        updatePermissionButton()
        refreshEvents()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButton()
    }

    private fun refreshEvents() {
        statusText.text = "Fetching high-impact events..."
        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) {
                CalendarScraper().fetchAllHighImpactEvents()
            }
            val scheduler = AlarmScheduler(this@MainActivity)
            val scheduled = scheduler.scheduleAll(events)

            val sb = StringBuilder()
            sb.appendLine("High-impact events found: ${events.size}")
            sb.appendLine("Alarms scheduled: $scheduled")
            sb.appendLine()
            val formatter = DateTimeFormatter.ofPattern("EEE dd MMM HH:mm")
            for (e in events.take(20)) {
                val nepalTime = TimezoneHelper.toNepal(e.eventTimeUtc).format(formatter)
                sb.appendLine(" ${e.title} (${e.currency})")
                sb.appendLine("  ${nepalTime} NPT  [${e.source}]")
            }
            if (events.size > 20) sb.appendLine("... and ${events.size - 20} more")
            statusText.text = sb.toString()
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        }
    }

    private fun updatePermissionButton() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            permissionButton.isEnabled = !am.canScheduleExactAlarms()
            permissionButton.text = if (am.canScheduleExactAlarms())
                "Exact alarms granted "
            else
                "Grant exact alarm permission"
        } else {
            permissionButton.isEnabled = false
            permissionButton.text = "Exact alarms granted "
        }
    }
}
