package com.example.bookyourtoken.data

import com.russhwolf.settings.Settings

/** Non-secret app settings: reminder time, the skip-if-already-booked toggle and the QR-ready check. */
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

    /** Optional morning "Your food QR is ready" check. Off unless the user turns it on. */
    var qrReadyEnabled: Boolean
        get() = settings.getBoolean(KEY_QR_ENABLED, false)
        set(value) = settings.putBoolean(KEY_QR_ENABLED, value)

    var qrReadyHour: Int
        get() = settings.getInt(KEY_QR_HOUR, DEFAULT_QR_HOUR)
        set(value) = settings.putInt(KEY_QR_HOUR, value)

    var qrReadyMinute: Int
        get() = settings.getInt(KEY_QR_MINUTE, DEFAULT_QR_MINUTE)
        set(value) = settings.putInt(KEY_QR_MINUTE, value)

    companion object {
        /** Android keeps these in the "app_prefs" SharedPreferences file. */
        const val ANDROID_PREFS_NAME = "app_prefs"
        private const val KEY_HOUR = "reminder_hour"
        private const val KEY_MINUTE = "reminder_minute"
        private const val KEY_SKIP = "skip_if_booked"
        private const val KEY_QR_ENABLED = "qr_ready_enabled"
        private const val KEY_QR_HOUR = "qr_ready_hour"
        private const val KEY_QR_MINUTE = "qr_ready_minute"
        const val DEFAULT_HOUR = 16
        const val DEFAULT_MINUTE = 0
        const val DEFAULT_QR_HOUR = 7
        const val DEFAULT_QR_MINUTE = 0
    }
}
