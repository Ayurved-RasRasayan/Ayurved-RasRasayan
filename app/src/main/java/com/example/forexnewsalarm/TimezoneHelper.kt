package com.example.forexnewsalarm

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Handles timezone conversion for Nepal (Asia/Kathmandu, UTC+5:45).
 * Nepal does not observe DST, but using the IANA zone ID ensures
 * correctness if that ever changes.
 */
object TimezoneHelper {

    val NEPAL_ZONE: ZoneId = ZoneId.of("Asia/Kathmandu")

    /**
     * Calculate the alert time (event time minus [minutesBefore] minutes)
     * in Nepal time.
     */
    fun alertTimeInNepal(eventTimeUtc: Instant, minutesBefore: Long = 9): ZonedDateTime {
        return eventTimeUtc
            .minusSeconds(minutesBefore * 60)
            .atZone(NEPAL_ZONE)
    }

    /** Convert a UTC Instant to Nepal local time for display. */
    fun toNepal(instant: Instant): ZonedDateTime = instant.atZone(NEPAL_ZONE)

    /** Get current time in Nepal. */
    fun nowInNepal(): ZonedDateTime = ZonedDateTime.now(NEPAL_ZONE)
}
