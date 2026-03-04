package de.flobaer.arbeitszeiterfassung.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.concurrent.CopyOnWriteArraySet

@Suppress("DEPRECATION")
class TokenManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_shared_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // Simple listener mechanism to notify when tokens change
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    private val spListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "access_token" || key == "refresh_token") {
            listeners.forEach { it.invoke() }
        }
    }

    init {
        sharedPreferences.registerOnSharedPreferenceChangeListener(spListener)
    }

    fun addOnTokenChangedListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeOnTokenChangedListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    fun getAccessToken(): String? {
        return sharedPreferences.getString("access_token", null)
    }

    fun getRefreshToken(): String? {
        return sharedPreferences.getString("refresh_token", null)
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPreferences.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()
    }

    fun clearTokens() {
        sharedPreferences.edit()
            .remove("access_token")
            .remove("refresh_token")
            .apply()
    }
}