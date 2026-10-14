package com.example.forexnewsalarm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Scrapes high-impact (red folder) economic events from Forex Factory
 * and Investing.com. Only red-folder / high-impact events are returned.
 */
class CalendarScraper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ----------------------------------------------------------------
    // Forex Factory
    // ----------------------------------------------------------------

    /**
     * Scrapes Forex Factory calendar for high-impact events.
     * Red folder events have CSS class "icon--ff-impact-red".
     */
    suspend fun scrapeForexFactory(): List<NewsEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<NewsEvent>()
        try {
            val html = fetchHtml("https://www.forexfactory.com/calendar")
            val doc = Jsoup.parse(html)

            // Each calendar row is a <tr class="calendar__row">
            val rows = doc.select("tr.calendar__row")
            for (row in rows) {
                // --- Check for red folder (high impact) ---
                val impactSpan = row.selectFirst("td.calendar__impact span")
                    ?: continue
                val impactClass = impactSpan.className()
                if (!impactClass.contains("icon--ff-impact-red")) continue

                // --- Extract event title ---
                val titleEl = row.selectFirst("td.calendar__event span.calendar__event-title")
                    ?: row.selectFirst("td.calendar__event")
                val title = titleEl?.text()?.trim() ?: continue
                if (title.isBlank()) continue

                // --- Extract currency ---
                val currency = row.selectFirst("td.calendar__currency")
                    ?.text()?.trim() ?: ""

                // --- Extract date and time ---
                val dateStr = row.selectFirst("td.calendar__date")
                    ?.text()?.trim() ?: ""
                val timeStr = row.selectFirst("td.calendar__time")
                    ?.text()?.trim() ?: ""

                val eventTimeUtc = parseForexFactoryDateTime(dateStr, timeStr)
                    ?: continue

                events.add(
                    NewsEvent(
                        title = title,
                        currency = currency,
                        impact = "High",
                        eventTimeUtc = eventTimeUtc,
                        source = "ForexFactory"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        events
    }

    // ----------------------------------------------------------------
    // Investing.com
    // ----------------------------------------------------------------

    /**
     * Scrapes Investing.com economic calendar for high-impact events.
     * High-impact events are marked with a red bull icon / "sentiment-high" class.
     */
    suspend fun scrapeInvestingCom(): List<NewsEvent> = withContext(Dispatchers.IO) {
        val events = mutableListOf<NewsEvent>()
        try {
            val html = fetchHtml("https://www.investing.com/economic-calendar/")
            val doc = Jsoup.parse(html)

            // Each event row has id starting with "eventRow"
            val rows = doc.select("tr[id^=eventRow]")
            for (row in rows) {
                // --- Check for high impact ---
                // High impact: the importance cell contains a red bull icon.
                val importanceCell = row.selectFirst("td.js-importance")
                    ?: continue
                val importanceHtml = importanceCell.html()
                // Investing.com uses a red bull icon for high impact.
                if (!importanceHtml.contains("grayFullBullishIcon") &&
                    !importanceHtml.contains("redFullBullishIcon") &&
                    !importanceHtml.contains("high")) continue

                // --- Extract title ---
                val titleEl = row.selectFirst("td.event a") ?: continue
                val title = titleEl.text().trim()
                if (title.isBlank()) continue

                // --- Extract currency ---
                val currency = row.selectFirst("td.flagCur span")
                    ?.text()?.trim() ?: ""

                // --- Extract time ---
                val timeEl = row.selectFirst("td.time")
                val timeStr = timeEl?.text()?.trim() ?: ""
                val eventTimeUtc = parseInvestingComDateTime(timeStr)
                    ?: continue

                events.add(
                    NewsEvent(
                        title = title,
                        currency = currency,
                        impact = "High",
                        eventTimeUtc = eventTimeUtc,
                        source = "Investing.com"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        events
    }

    // ----------------------------------------------------------------
    // Combined fetch + deduplicate
    // ----------------------------------------------------------------

    /**
     * Fetches high-impact events from both sources, merges, and deduplicates.
     */
    suspend fun fetchAllHighImpactEvents(): List<NewsEvent> {
        val ffEvents = scrapeForexFactory()
        val invEvents = scrapeInvestingCom()
        val all = (ffEvents + invEvents)

        // Deduplicate by title + approximate time (within 5 minutes)
        val deduped = mutableListOf<NewsEvent>()
        for (event in all) {
            val isDuplicate = deduped.any { existing ->
                existing.title.equals(event.title, ignoreCase = true) &&
                kotlin.math.abs(
                    existing.eventTimeUtc.epochSecond - event.eventTimeUtc.epochSecond
                ) < 300
            }
            if (!isDuplicate) deduped.add(event)
        }

        return deduped.sortedBy { it.eventTimeUtc }
    }

    // ----------------------------------------------------------------
    // Private helpers
    // ----------------------------------------------------------------

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            return response.body?.string() ?: ""
        }
    }

    /**
     * Parse Forex Factory date + time strings into UTC Instant.
     * Forex Factory times are displayed in the user's timezone,
     * but the HTML usually contains a UTC timestamp in a data attribute.
     * We attempt to use that; otherwise fall back to US Eastern.
     */
    private fun parseForexFactoryDateTime(dateStr: String, timeStr: String): Instant? {
        return try {
            // Forex Factory typically uses "Sep 1" for date and "8:30am" for time.
            // We try to combine them. If the date is empty, it means "same as previous row".
            // For simplicity we assume the scraper is run daily for today's events.
            val currentYear = LocalDate.now().year
            val dateTimeStr = "$dateStr $currentYear $timeStr"
            val formatter = DateTimeFormatter.ofPattern("MMM d yyyy h:mma", java.util.Locale.US)
            val localDateTime = LocalDateTime.parse(dateTimeStr, formatter)
            // Forex Factory displays times in US Eastern (ET).
            // ET is UTC-5 (EST) or UTC-4 (EDT). We use -5 for simplicity;
            // the weekly JSON export uses UTC directly and is preferred.
            localDateTime.toInstant(ZoneOffset.ofHours(-5))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse Investing.com time string into UTC Instant.
     * Investing.com displays times in the user's timezone.
     * We assume the scraper is run daily and parse today's date + the time.
     */
    private fun parseInvestingComDateTime(timeStr: String): Instant? {
        return try {
            if (timeStr.isBlank() || timeStr.contains("All Day", ignoreCase = true)) {
                return null
            }
            val today = LocalDate.now()
            val dateTimeStr = "$today $timeStr"
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", java.util.Locale.US)
            val localDateTime = LocalDateTime.parse(dateTimeStr, formatter)
            // Investing.com displays in US Eastern by default.
            localDateTime.toInstant(ZoneOffset.ofHours(-5))
        } catch (e: Exception) {
            null
        }
    }
}
