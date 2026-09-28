package com.example.bookyourtoken.data

import com.example.bookyourtoken.data.models.ApiResult

sealed interface ReminderOutcome {
    data object NotSignedIn : ReminderOutcome
    data object AlreadyBooked : ReminderOutcome
    data class Notify(val body: String) : ReminderOutcome
}

/**
 * The daily check behind the reminder notification: Android's WorkManager job and "Check now" on
 * both platforms. Never books anything — it only reads state. Logs in fresh every run.
 */
object ReminderCheck {
    private const val COULD_NOT_CHECK = "Couldn't check the portal — tap to open it."

    suspend fun run(credentials: CredentialStore, preferences: AppPreferences): ReminderOutcome {
        val rollNo = credentials.rollNo()
        val password = credentials.password()
        if (rollNo.isNullOrBlank() || password.isNullOrBlank()) return ReminderOutcome.NotSignedIn

        val tomorrow = DateUtils.tomorrowString()
        return HostelClient().use { client ->
            if (client.login(rollNo, password) is ApiResult.Failure) {
                return@use ReminderOutcome.Notify(COULD_NOT_CHECK)
            }

            if (preferences.skipIfAlreadyBooked) {
                val booked = client.fetchBookedTokens(rollNo)
                if (booked is ApiResult.Success && booked.data.any { it.expireDate == tomorrow }) {
                    return@use ReminderOutcome.AlreadyBooked
                }
            }

            when (val page = client.fetchBookingPageHtml()) {
                is ApiResult.Failure -> ReminderOutcome.Notify(COULD_NOT_CHECK)
                is ApiResult.Success -> {
                    val items = TokenPageParser.parse(page.data)
                    if (items.isEmpty()) {
                        ReminderOutcome.Notify(COULD_NOT_CHECK)
                    } else {
                        ReminderOutcome.Notify("${items.count { tomorrow in it.dates }} items available for $tomorrow")
                    }
                }
            }
        }
    }

    const val NOTIFICATION_TITLE = "Book tomorrow's food token"
}
