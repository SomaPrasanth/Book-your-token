package com.example.bookyourtoken.data

import com.russhwolf.settings.Settings

/**
 * Credentials never touch plain storage, logs, or crash reports — only this store and the single
 * login POST to the portal. [settings] must be an encrypted backend: EncryptedSharedPreferences
 * on Android, the Keychain on iOS.
 */
class CredentialStore(private val settings: Settings) {

    fun save(rollNo: String, password: String) {
        settings.putString(KEY_ROLL, rollNo.uppercase())
        settings.putString(KEY_PASSWORD, password)
    }

    fun rollNo(): String? = settings.getStringOrNull(KEY_ROLL)

    fun password(): String? = settings.getStringOrNull(KEY_PASSWORD)

    fun hasCredentials(): Boolean = !rollNo().isNullOrBlank() && !password().isNullOrBlank()

    fun clear() {
        settings.clear()
    }

    private companion object {
        // Same keys as the original Android-only store, so existing sign-ins keep working.
        const val KEY_ROLL = "roll_no"
        const val KEY_PASSWORD = "password"
    }
}
