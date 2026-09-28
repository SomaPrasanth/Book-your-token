package com.example.bookyourtoken.data

import com.russhwolf.settings.Settings

/** Non-secret app settings: reminder time and the skip-if-already-booked toggle. */
class AppPreferences(private val settings: Settings) {

    var reminderHour: Int
        get() = settings.getInt(KEY_HOUR, DEFAULT_HOUR)
        set(value) = settings.putInt(KEY_HOUR, value)

    var reminderMinute: Int
        get() = settings.getInt(KEY_MINUTE, DEFAULT_MINUTE)
        set(value) = settings.putInt(KEY_MINUTE, value)

    var skipIfAlreadyBooked: Boolean
        get() = settings.getBoolean(KEY_SKIP, true)
        set(value) = settings.putBoolean(KEY_SKIP, value)

    companion object {
        /** Android keeps these in the "app_prefs" SharedPreferences file. */
        const val ANDROID_PREFS_NAME = "app_prefs"
        private const val KEY_HOUR = "reminder_hour"
        private const val KEY_MINUTE = "reminder_minute"
        private const val KEY_SKIP = "skip_if_booked"
        const val DEFAULT_HOUR = 16
        const val DEFAULT_MINUTE = 0
    }
}
