package com.example.forexnewsalarm

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Fetches the news_events.json file published to GitHub by the
 * scrape-news GitHub Action. This is far more reliable than scraping
 * Forex Factory directly from the phone.
 */
class CalendarScraper {

    companion object {
        private const val TAG = "CalendarScraper"
        private const val EVENTS_URL =
            "https://raw.githubusercontent.com/Ayurved-RasRasayan/Ayurved-RasRasayan/main/data/news_events.json"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private data class Response(
        @SerializedName("lastUpdated") val lastUpdated: String?,
        @SerializedName("events") val events: List<Dto>?
    )

    private data class Dto(
        @SerializedName("title") val title: String?,
        @SerializedName("currency") val currency: String?,
        @SerializedName("eventTimeUtc") val eventTimeUtc: String?,
        @SerializedName("impact") val impact: String?,
        @SerializedName("source") val source: String?
    )

    suspend fun fetchAllHighImpactEvents(): List<NewsEvent> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(EVENTS_URL)
                .header("User-Agent", "ForexNewsAlarm/1.0")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.e(TAG, "HTTP ${resp.code} fetching $EVENTS_URL")
                    return@withContext emptyList()
                }
                val body = resp.body?.string().orEmpty()
                val parsed = gson.fromJson(body, Response::class.java) ?: return@withContext emptyList()

                val list = mutableListOf<NewsEvent>()
                for (d in parsed.events.orEmpty()) {
                    val title = d.title?.trim().orEmpty()
                    val timeStr = d.eventTimeUtc?.trim().orEmpty()
                    if (title.isEmpty() || timeStr.isEmpty()) continue
                    val instant = try {
                        Instant.parse(timeStr)
                    } catch (e: Exception) {
                        Log.w(TAG, "Bad timestamp: $timeStr")
                        continue
                    }
                    list.add(
                        NewsEvent(
                            title = title,
                            currency = d.currency.orEmpty(),
                            impact = d.impact ?: "High",
                            eventTimeUtc = instant,
                            source = d.source ?: "ForexFactory"
                        )
                    )
                }
                Log.d(TAG, "Loaded ${list.size} events (lastUpdated=${parsed.lastUpdated})")
                list.sortedBy { it.eventTimeUtc }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch events", e)
            emptyList()
        }
    }
}