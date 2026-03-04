package de.flobaer.arbeitszeiterfassung

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "timer_prefs")

object PrefKeys {
    val KEY_INTERVALS = stringPreferencesKey("intervals_json")
    val KEY_IS_RUNNING = longPreferencesKey("is_running_timestamp")
    val KEY_RUNNING_LOCATION = stringPreferencesKey("running_location")
    val API_URL = stringPreferencesKey("api_url")

    // User Info
    val USER_FIRST_NAME = stringPreferencesKey("user_first_name")
    val USER_LAST_NAME = stringPreferencesKey("user_last_name")
    val USER_USERNAME = stringPreferencesKey("user_username")
}