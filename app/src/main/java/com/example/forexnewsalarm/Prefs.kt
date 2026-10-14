package com.example.forexnewsalarm

import android.content.Context

/**
 * Tiny SharedPreferences wrapper for user-configurable settings.
 */
object Prefs {
    private const val NAME = "forex_alarm_prefs"
    private const val KEY_MINUTES_BEFORE = "minutes_before"

    const val DEFAULT_MINUTES_BEFORE = 9
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 30

    fun getMinutesBefore(ctx: Context): Int =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getInt(KEY_MINUTES_BEFORE, DEFAULT_MINUTES_BEFORE)
            .coerceIn(MIN_MINUTES, MAX_MINUTES)

    fun setMinutesBefore(ctx: Context, minutes: Int) {
        val clamped = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_MINUTES_BEFORE, clamped).apply()
    }
}