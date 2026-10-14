package com.example.forexnewsalarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {

    private lateinit var clockText: TextView
    private lateinit var statusText: TextView
    private lateinit var leadTimeText: TextView
    private lateinit var leadTimeSeekBar: SeekBar
    private lateinit var refreshButton: Button
    private lateinit var permissionButton: Button
    private lateinit var batteryButton: Button
    private lateinit var dismissButton: Button

    private var currentEvents: List<NewsEvent> = emptyList()
    private var scheduledCount: Int = 0

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTick = object : Runnable {
        override fun run() {
            updateClock()
            clockHandler.postDelayed(this, 1000L)
        }
    }

    private val clockFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy  \u00B7  hh:mm:ss a 'NPT'")

    private val dayHeaderFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, dd MMM yyyy")

    private val dayKeyFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val time12Fmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("hh:mm a")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clockText = findViewById(R.id.clockText)
        statusText = findViewById(R.id.statusText)
        leadTimeText = findViewById(R.id.leadTimeText)
        leadTimeSeekBar = findViewById(R.id.leadTimeSeekBar)
        refreshButton = findViewById(R.id.refreshButton)
        permissionButton = findViewById(R.id.permissionButton)
        batteryButton = findViewById(R.id.batteryButton)
        dismissButton = findViewById(R.id.dismissButton)

        setupLeadTimeSlider()

        refreshButton.setOnClickListener { refreshEvents() }
        permissionButton.setOnClickListener { requestExactAlarmPermission() }
        batteryButton.setOnClickListener { requestIgnoreBatteryOptimizations() }
        dismissButton.setOnClickListener { dismissAlarm() }

        updateButtons()
        startKeeperService()
        refreshEvents()
    }

    private fun startKeeperService() {
        val svc = Intent(this, KeeperService::class.java).apply {
            action = KeeperService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc)
        } else {
            startService(svc)
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtons()
        clockHandler.post(clockTick)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTick)
    }

    private fun updateClock() {
        val now: ZonedDateTime = TimezoneHelper.nowInNepal()
        clockText.text = now.format(clockFmt)
    }

    // ------------------------------------------------------------------
    // Lead-time slider
    // ------------------------------------------------------------------
    private fun setupLeadTimeSlider() {
        val minutes = Prefs.getMinutesBefore(this)
        leadTimeSeekBar.max = Prefs.MAX_MINUTES - Prefs.MIN_MINUTES
        leadTimeSeekBar.progress = minutes - Prefs.MIN_MINUTES
        leadTimeText.text = "Alarm leads by: $minutes min"

        leadTimeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val m = progress + Prefs.MIN_MINUTES
                leadTimeText.text = "Alarm leads by: $m min"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                val m = (sb?.progress ?: 0) + Prefs.MIN_MINUTES
                Prefs.setMinutesBefore(this@MainActivity, m)
                if (currentEvents.isNotEmpty()) {
                    rescheduleCurrent()
                } else {
                    updateStatusText()
                }
            }
        })
    }

    // ------------------------------------------------------------------
    // Fetch + schedule
    // ------------------------------------------------------------------
    private fun refreshEvents() {
        statusText.text = "Fetching high-impact events..."
        lifecycleScope.launch {
            val events = withContext(Dispatchers.IO) {
                CalendarScraper().fetchAllHighImpactEvents()
            }
            currentEvents = events
            val scheduler = AlarmScheduler(this@MainActivity)
            scheduledCount = scheduler.scheduleAll(events)
            updateStatusText()
        }
    }

    private fun rescheduleCurrent() {
        val scheduler = AlarmScheduler(this)
        scheduledCount = scheduler.scheduleAll(currentEvents)
        updateStatusText()
    }

    // ------------------------------------------------------------------
    // Render: grouped by day, 12-hour times, bold day headers
    // ------------------------------------------------------------------
    private fun updateStatusText() {
        val lead = Prefs.getMinutesBefore(this)
        val sb = SpannableStringBuilder()

        sb.append("Lead time:  ").append(lead.toString()).append(" min before event\n")
        sb.append("Events:     ").append(currentEvents.size.toString()).append("\n")
        sb.append("Scheduled:  ").append(scheduledCount.toString()).append(" alarms\n\n")

        if (currentEvents.isEmpty()) {
            sb.append("No high-impact events yet.\n")
            sb.append("The scraper runs every 15 minutes.\n")
            sb.append("Tap Refresh & Schedule in a moment.\n")
            statusText.text = sb
            return
        }

        // Group events by Nepal-time day
        val grouped: Map<String, List<NewsEvent>> = currentEvents.groupBy {
            TimezoneHelper.toNepal(it.eventTimeUtc).format(dayKeyFmt)
        }

        for ((_, dayEvents) in grouped) {
            val first = dayEvents.first()
            val dayHeaderText = TimezoneHelper.toNepal(first.eventTimeUtc).format(dayHeaderFmt)

            // Day header block
            sb.append("\u2501".repeat(34)).append("\n")
            val headerStart = sb.length
            sb.append("  ").append(dayHeaderText).append("\n")
            sb.setSpan(
                StyleSpan(Typeface.BOLD),
                headerStart, sb.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.setSpan(
                ForegroundColorSpan(Color.parseColor("#0D47A1")),
                headerStart, sb.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.append("\u2501".repeat(34)).append("\n")

            // Column header
            sb.append(String.format("%-10s%-5s%s%n", "TIME (NPT)", "CUR", "EVENT"))
            sb.append("\u2500".repeat(34)).append("\n")

            // Event rows
            for (e in dayEvents) {
                val timeStr = TimezoneHelper.toNepal(e.eventTimeUtc).format(time12Fmt)
                val cur = e.currency.take(3).padEnd(5)
                sb.append(timeStr.padEnd(10))
                sb.append(cur)

                val dotStart = sb.length
                sb.append("\u25CF ")
                sb.setSpan(
                    ForegroundColorSpan(Color.parseColor("#E53935")),
                    dotStart, sb.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                sb.append(e.title).append("\n")
            }

            sb.append("\n")
        }

        statusText.text = sb
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------
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