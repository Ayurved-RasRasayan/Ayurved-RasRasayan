package com.example.forexnewsalarm

import java.time.Instant

/**
 * Normalized economic news event from any source.
 * All times are stored as UTC Instant internally.
 */
data class NewsEvent(
    val title: String,
    val currency: String,
    val impact: String,          // "High"
    val eventTimeUtc: Instant,   // Always UTC
    val source: String,          // "ForexFactory" or "Investing.com"
    val country: String = ""
) {
    /** Unique ID for alarm scheduling and deduplication. */
    val uniqueId: String
        get() = "${title}_${currency}_${eventTimeUtc.epochSecond}".hashCode().toString()
}
