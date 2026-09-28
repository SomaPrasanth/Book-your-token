package com.example.bookyourtoken.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.bookyourtoken.data.CredentialStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (CredentialStore(context).hasCredentials()) {
                ReminderScheduler.schedule(context)
            }
        }
    }
}
