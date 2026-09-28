package com.example.bookyourtoken.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.bookyourtoken.data.AppPreferences
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Self-rescheduling one-shot worker instead of a PeriodicWorkRequest, so the reminder time can be
 * changed on the fly and lands exactly on the configured minute each day.
 */
object ReminderScheduler {
    private const val DAILY_WORK_NAME = "daily_token_reminder"
    private const val CHECK_NOW_WORK_NAME = "daily_token_reminder_now"

    fun schedule(context: Context) {
        val prefs = AppPreferences(context)
        val delayMillis = computeInitialDelayMillis(prefs.reminderHour, prefs.reminderMinute)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DAILY_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_WORK_NAME)
    }

    /** Runs the reminder logic immediately, without disturbing the regular daily schedule. */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(CHECK_NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun computeInitialDelayMillis(hour: Int, minute: Int): Long {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = ZonedDateTime.now(zone)
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return Duration.between(now, next).toMillis()
    }
}
