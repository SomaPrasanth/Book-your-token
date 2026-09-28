package com.example.bookyourtoken.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Credentials never touch plain SharedPreferences, logs, or crash reports — only this
 * EncryptedSharedPreferences-backed store and the single login POST to the portal.
 */
class CredentialStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "secure_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(rollNo: String, password: String) {
        prefs.edit()
            .putString(KEY_ROLL, rollNo.uppercase())
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun rollNo(): String? = prefs.getString(KEY_ROLL, null)

    fun password(): String? = prefs.getString(KEY_PASSWORD, null)

    fun hasCredentials(): Boolean = !rollNo().isNullOrBlank() && !password().isNullOrBlank()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ROLL = "roll_no"
        private const val KEY_PASSWORD = "password"
    }
}
