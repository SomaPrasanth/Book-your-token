package com.example.bookyourtoken.data

import android.content.Context

/** Non-secret app settings: reminder time and the skip-if-already-booked toggle. */
class AppPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var reminderHour: Int
        get() = prefs.getInt(KEY_HOUR, DEFAULT_HOUR)
        set(value) = prefs.edit().putInt(KEY_HOUR, value).apply()

    var reminderMinute: Int
        get() = prefs.getInt(KEY_MINUTE, DEFAULT_MINUTE)
        set(value) = prefs.edit().putInt(KEY_MINUTE, value).apply()

    var skipIfAlreadyBooked: Boolean
        get() = prefs.getBoolean(KEY_SKIP, true)
        set(value) = prefs.edit().putBoolean(KEY_SKIP, value).apply()

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_HOUR = "reminder_hour"
        private const val KEY_MINUTE = "reminder_minute"
        private const val KEY_SKIP = "skip_if_booked"
        const val DEFAULT_HOUR = 16
        const val DEFAULT_MINUTE = 0
    }
}
