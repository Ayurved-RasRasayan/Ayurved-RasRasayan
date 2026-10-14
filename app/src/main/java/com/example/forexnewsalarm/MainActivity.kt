package com.example.forexnewsalarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
    private lateinit var batteryButton: Button
    private lateinit var dismissButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        refreshButton = findViewById(R.id.refreshButton)
        permissionButton = findViewById(R.id.permissionButton)
        batteryButton = findViewById(R.id.batteryButton)
        dismissButton = findViewById(R.id.dismissButton)

        refreshButton.setOnClickListener { refreshEvents() }
        permissionButton.setOnClickListener { requestExactAlarmPermission() }
        batteryButton.setOnClickListener { requestIgnoreBatteryOptimizations() }
        dismissButton.setOnClickListener { dismissAlarm() }

        updateButtons()
        refreshEvents()
    }

    override fun onResume() {
        super.onResume()
        updateButtons()
    }

    private fun refreshEvents() {
        statusText.text = "Fetching high-impact events..."
        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) {
                CalendarScraper().fetchAllHighImpactEvents()
            }
            val scheduler = AlarmScheduler(this@MainActivity)
            val scheduled = scheduler.scheduleAll(events)

            val fmt = DateTimeFormatter.ofPattern("EEE dd MMM HH:mm")
            val sb = StringBuilder()
            sb.appendLine("High-impact events: ${events.size}")
            sb.appendLine("Alarms scheduled:    $scheduled")
            sb.appendLine()
            for (e in events.take(30)) {
                val npt = TimezoneHelper.toNepal(e.eventTimeUtc).format(fmt)
                sb.appendLine("\u2022 ${e.title} (${e.currency})")
                sb.appendLine("  $npt NPT  [${e.source}]")
            }
            if (events.size > 30) sb.appendLine("... and ${events.size - 30} more")
            if (events.isEmpty()) {
                sb.appendLine("No events yet. The GitHub Action runs every 15 min ")
                sb.appendLine("check back shortly. You can also tap Refresh.")
            }
            statusText.text = sb.toString()
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                } catch (e: Exception) {
                    // Fall back to the general battery-optimization settings screen
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }
    }

    private fun dismissAlarm() {
        val intent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
        }
        startService(intent)
    }

    private fun updateButtons() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val granted = am.canScheduleExactAlarms()
            permissionButton.isEnabled = !granted
            permissionButton.text = if (granted) "Exact alarms: GRANTED"
                else "Grant exact alarm permission"
        } else {
            permissionButton.isEnabled = false
            permissionButton.text = "Exact alarms: GRANTED"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val exempt = pm.isIgnoringBatteryOptimizations(packageName)
            batteryButton.isEnabled = !exempt
            batteryButton.text = if (exempt) "Battery: UNRESTRICTED"
                else "Disable battery optimization"
        } else {
            batteryButton.isEnabled = false
            batteryButton.text = "Battery: UNRESTRICTED"
        }
    }
}