package com.example.bookyourtoken

import android.app.Application
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.bookyourtoken.data.AppPreferences
import com.example.bookyourtoken.data.CredentialStore
import com.example.bookyourtoken.work.NotificationHelper
import com.russhwolf.settings.SharedPreferencesSettings

class HostelApp : Application() {

    /** Built lazily: creating the EncryptedSharedPreferences master key is slow. */
    val container: AppContainer by lazy {
        AppContainer(
            credentials = CredentialStore(SharedPreferencesSettings(encryptedCredentialPrefs(this))),
            preferences = AppPreferences(
                SharedPreferencesSettings(getSharedPreferences(AppPreferences.ANDROID_PREFS_NAME, MODE_PRIVATE))
            ),
            reminders = AndroidReminders(this),
            platform = AndroidPlatformActions(this)
        )
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }

    private fun encryptedCredentialPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        // Excluded from backups in res/xml: the Keystore key isn't backed up, so a restore couldn't decrypt it.
        "secure_credentials",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

val Context.appContainer: AppContainer
    get() = (applicationContext as HostelApp).container
