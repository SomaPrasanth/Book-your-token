package com.example.bookyourtoken.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bookyourtoken.appContainer
import com.example.bookyourtoken.data.ReminderCheck
import com.example.bookyourtoken.data.ReminderOutcome

/**
 * Runs once a day (or on demand via "Check now"). Never books anything — the shared
 * [ReminderCheck] only reads state; this posts the notification and plans the next run.
 */
class ReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        when (val outcome = ReminderCheck.run(container.credentials, container.preferences)) {
            ReminderOutcome.NotSignedIn -> return Result.success()
            ReminderOutcome.AlreadyBooked -> Unit
            is ReminderOutcome.Notify -> NotificationHelper.postReminder(applicationContext, outcome.body)
        }
        ReminderScheduler.schedule(applicationContext)
        return Result.success()
    }
}
