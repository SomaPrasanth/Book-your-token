package com.example.bookyourtoken.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bookyourtoken.data.AppPreferences
import com.example.bookyourtoken.data.CredentialStore
import com.example.bookyourtoken.data.DateUtils
import com.example.bookyourtoken.data.HostelClient
import com.example.bookyourtoken.data.TokenPageParser
import com.example.bookyourtoken.data.models.ApiResult

/**
 * Runs once a day (or on demand via "Check now"). Never books anything — it only reads state and
 * posts a notification. Logs in fresh every run since the session/JWT are short-lived.
 */
class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val credentials = CredentialStore(applicationContext)
        val rollNo = credentials.rollNo()
        val password = credentials.password()

        if (rollNo.isNullOrBlank() || password.isNullOrBlank()) {
            return Result.success()
        }

        val client = HostelClient()
        val tomorrow = DateUtils.tomorrowString()

        when (val login = client.login(rollNo, password)) {
            is ApiResult.Failure -> {
                NotificationHelper.postReminder(applicationContext, "Couldn't check the portal — tap to open it.")
                ReminderScheduler.schedule(applicationContext)
                return Result.success()
            }
            is ApiResult.Success -> Unit
        }

        val prefs = AppPreferences(applicationContext)
        if (prefs.skipIfAlreadyBooked) {
            val booked = client.fetchBookedTokens(rollNo)
            if (booked is ApiResult.Success && booked.data.any { it.expireDate == tomorrow }) {
                ReminderScheduler.schedule(applicationContext)
                return Result.success()
            }
        }

        val body = when (val page = client.fetchBookingPageHtml()) {
            is ApiResult.Success -> {
                val items = TokenPageParser.parse(page.data)
                if (items.isEmpty()) {
                    "Couldn't check the portal — tap to open it."
                } else {
                    val available = items.count { tomorrow in it.dates }
                    "$available items available for $tomorrow"
                }
            }
            is ApiResult.Failure -> "Couldn't check the portal — tap to open it."
        }

        NotificationHelper.postReminder(applicationContext, body)
        ReminderScheduler.schedule(applicationContext)
        return Result.success()
    }
}
