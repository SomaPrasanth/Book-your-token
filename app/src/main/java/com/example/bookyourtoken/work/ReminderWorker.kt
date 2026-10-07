package com.example.bookyourtoken.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bookyourtoken.appContainer
import com.example.bookyourtoken.appUpdates
import com.example.bookyourtoken.data.ReminderCheck
import com.example.bookyourtoken.data.ReminderOutcome

/**
 * Runs once a day (or on demand via "Check now"). Never books anything — the shared
 * [ReminderCheck] only reads state; this posts the notification and plans the next run. Afterwards
 * it looks for an app update (at most every 6 hours) and notifies once per new version.
 */
class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        when (val outcome = ReminderCheck.run(container.credentials, container.preferences)) {
            ReminderOutcome.NotSignedIn -> Unit
            ReminderOutcome.AlreadyBooked -> ReminderScheduler.schedule(applicationContext)
            is ReminderOutcome.Notify -> {
                NotificationHelper.postReminder(applicationContext, outcome.body)
                ReminderScheduler.schedule(applicationContext)
            }
        }
        applicationContext.appUpdates.releaseToNotify()?.let {
            NotificationHelper.postUpdateAvailable(applicationContext, it.versionName)
        }
        return Result.success()
    }
}
