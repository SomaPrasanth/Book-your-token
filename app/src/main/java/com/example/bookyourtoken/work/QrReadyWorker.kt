package com.example.bookyourtoken.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bookyourtoken.appContainer
import com.example.bookyourtoken.data.QrReadyCheck

/**
 * The optional morning check (off by default): once a day, one login and one StudentGetToken. If the
 * portal has enabled a QR it posts "Your food QR is ready". Never fetches or stores the QR itself.
 */
class QrReadyWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        if (!container.preferences.qrReadyEnabled || !container.credentials.hasCredentials()) return Result.success()

        if (QrReadyCheck.run(container.credentials)) NotificationHelper.postQrReady(applicationContext)
        container.qr.deleteIfStale()
        ReminderScheduler.scheduleQrReady(applicationContext)
        return Result.success()
    }
}
