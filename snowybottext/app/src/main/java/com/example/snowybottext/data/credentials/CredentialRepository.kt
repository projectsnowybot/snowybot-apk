package com.example.snowybottext.data.credentials

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Repository providing encrypted persistence for sensitive bot credentials (Username, Password, 2FA code)
 * and site configuration using EncryptedSharedPreferences.
 */
@Suppress("DEPRECATION")
class CredentialRepository(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        SECURE_PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getUsername(): String = sharedPreferences.getString(KEY_USERNAME, "") ?: ""
    fun setUsername(username: String) = sharedPreferences.edit().putString(KEY_USERNAME, username).apply()

    fun getPassword(): String = sharedPreferences.getString(KEY_PASSWORD, "") ?: ""
    fun setPassword(password: String) = sharedPreferences.edit().putString(KEY_PASSWORD, password).apply()

    fun get2FACode(): String = sharedPreferences.getString(KEY_2FA_CODE, "") ?: ""
    fun set2FACode(code: String) = sharedPreferences.edit().putString(KEY_2FA_CODE, code).apply()

    fun getTargetUrl(): String = sharedPreferences.getString(KEY_TARGET_URL, DEFAULT_TARGET_URL) ?: DEFAULT_TARGET_URL
    fun setTargetUrl(url: String) = sharedPreferences.edit().putString(KEY_TARGET_URL, url).apply()

    fun getTargetLimit(): Double {
        val str = sharedPreferences.getString(KEY_TARGET_LIMIT, DEFAULT_TARGET_LIMIT_STR) ?: DEFAULT_TARGET_LIMIT_STR
        return str.toDoubleOrNull() ?: 144000.0
    }
    fun setTargetLimit(value: Double) {
        sharedPreferences.edit().putString(KEY_TARGET_LIMIT, value.toString()).apply()
    }

    fun getProxyEnabled(): Boolean = sharedPreferences.getBoolean(KEY_PROXY_ENABLED, false)
    fun setProxyEnabled(enabled: Boolean) = sharedPreferences.edit().putBoolean(KEY_PROXY_ENABLED, enabled).apply()

    fun getProxyHost(): String = sharedPreferences.getString(KEY_PROXY_HOST, "") ?: ""
    fun setProxyHost(host: String) = sharedPreferences.edit().putString(KEY_PROXY_HOST, host).apply()

    fun getProxyPort(): Int {
        val str = sharedPreferences.getString(KEY_PROXY_PORT, "8080") ?: "8080"
        return str.toIntOrNull() ?: 8080
    }
    fun setProxyPort(port: Int) {
        sharedPreferences.edit().putString(KEY_PROXY_PORT, port.toString()).apply()
    }

    fun saveCredentials(username: String, password: String, code2FA: String) {
        sharedPreferences.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .putString(KEY_2FA_CODE, code2FA)
            .apply()
    }

    fun clearCredentials() {
        sharedPreferences.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .remove(KEY_2FA_CODE)
            .apply()
    }

    fun hasCredentials(): Boolean {
        return getUsername().isNotBlank() && getPassword().isNotBlank()
    }

    companion object {
        private const val SECURE_PREFS_NAME = "snowybot_secure_prefs"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_PASSWORD = "key_password"
        private const val KEY_2FA_CODE = "key_2fa_code"
        private const val KEY_TARGET_URL = "key_target_url"
        private const val KEY_TARGET_LIMIT = "key_target_limit"
        private const val KEY_PROXY_ENABLED = "key_proxy_enabled"
        private const val KEY_PROXY_HOST = "key_proxy_host"
        private const val KEY_PROXY_PORT = "key_proxy_port"

        const val DEFAULT_TARGET_URL = "https://just-dice.com"
        const val DEFAULT_TARGET_LIMIT_STR = "144000.0"
    }
}
