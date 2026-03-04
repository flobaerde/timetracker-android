package de.flobaer.arbeitszeiterfassung.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.ListenableWorker
import java.util.concurrent.TimeUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.flobaer.arbeitszeiterfassung.PrefKeys
import de.flobaer.arbeitszeiterfassung.network.ApiService
import de.flobaer.arbeitszeiterfassung.network.LoginRequest
import de.flobaer.arbeitszeiterfassung.network.RefreshRequest
import de.flobaer.arbeitszeiterfassung.network.TokenManager
import de.flobaer.arbeitszeiterfassung.network.model.User
import de.flobaer.arbeitszeiterfassung.sync.SyncWorker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ApiStatus {
    object Idle : ApiStatus()
    object Loading : ApiStatus()
    object Success : ApiStatus()
    object Warning : ApiStatus()
    object Error : ApiStatus()
}

data class LoggedInUser(val firstName: String?, val lastName: String?, val username: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val tokenManager: TokenManager,
    private val apiService: ApiService,
) : ViewModel() {

    // React to token changes (e.g., when AuthAuthenticator clears invalid tokens)
    private val tokenListener: () -> Unit = {
        val refresh = tokenManager.getRefreshToken()
        if (refresh == null) {
            // Tokens were cleared → reflect logout in UI and DataStore
            viewModelScope.launch {
                clearUser()
                _loggedInUser.value = null
            }
        }
    }

    private val _apiUrl = MutableStateFlow("")
    val apiUrl = _apiUrl.asStateFlow()

    private val _apiStatus = MutableStateFlow<ApiStatus>(ApiStatus.Idle)
    val apiStatus = _apiStatus.asStateFlow()

    private val _showSaveConfirmation = Channel<Unit>(Channel.BUFFERED)
    val showSaveConfirmation = _showSaveConfirmation.receiveAsFlow()

    private val _loggedInUser = MutableStateFlow<LoggedInUser?>(null)
    val loggedInUser = _loggedInUser.asStateFlow()

    private val _nextPeriodicSyncTimeMillis = MutableStateFlow<Long?>(null)
    val nextPeriodicSyncTimeMillis = _nextPeriodicSyncTimeMillis.asStateFlow()

    init {
        tokenManager.addOnTokenChangedListener(tokenListener)
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _apiUrl.value = prefs[PrefKeys.API_URL] ?: ""

            val firstName = prefs[PrefKeys.USER_FIRST_NAME]
            val lastName = prefs[PrefKeys.USER_LAST_NAME]
            val username = prefs[PrefKeys.USER_USERNAME]
            val refreshToken = tokenManager.getRefreshToken()

            if (refreshToken != null && username != null) {
                _loggedInUser.value = LoggedInUser(firstName, lastName, username)
            }
        }
    }

    fun refreshNextPeriodicSync() {
        viewModelScope.launch {
            try {
                val wm = WorkManager.getInstance(context)
                val infos = wm.getWorkInfosForUniqueWork(SyncWorker.UNIQUE_PERIODIC_NAME).get()
                // nextScheduleTimeMillis ist erst ab WorkManager 2.9.0 verfügbar.
                // Wir nutzen hier Reflection oder prüfen das Info-Objekt vorsichtig.
                val next = try {
                    val method = infos.firstOrNull()?.javaClass?.getMethod("getNextScheduleTimeMillis")
                    method?.invoke(infos.firstOrNull()) as? Long
                } catch (e: Exception) {
                    null
                }
                _nextPeriodicSyncTimeMillis.value = next
            } catch (_: Exception) {
                _nextPeriodicSyncTimeMillis.value = null
            }
        }
    }

    fun triggerSyncNow() {
        viewModelScope.launch {
            val wm = WorkManager.getInstance(context)
            val request = SyncWorker.createPeriodicRequest()
            
            // Startet den periodischen Sync sofort neu und setzt das Intervall zurück
            wm.enqueueUniquePeriodicWork(
                SyncWorker.UNIQUE_PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request
            ).await()
            
            // Nach dem Trigger die nächste Zeit erneut abrufen
            refreshNextPeriodicSync()
        }
    }

    fun saveApiUrl(url: String) {
        viewModelScope.launch {
            dataStore.edit { it[PrefKeys.API_URL] = url }
            _apiUrl.value = url
            _showSaveConfirmation.send(Unit)
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _apiStatus.value = ApiStatus.Loading
            val url = _apiUrl.value
            if (url.isBlank()) {
                _apiStatus.value = ApiStatus.Error
                return@launch
            }

            try {
                val response = apiService.login(LoginRequest(username, password))
                if (response.isSuccessful && response.body() != null) {
                    val authResponse = response.body()!!
                    tokenManager.saveTokens(authResponse.accessToken, authResponse.refreshToken)
                    saveUser(authResponse.user)
                    _loggedInUser.value = LoggedInUser(authResponse.user.firstName, authResponse.user.lastName, authResponse.user.username)
                    _apiStatus.value = ApiStatus.Success
                } else {
                    _apiStatus.value = ApiStatus.Error
                }
            } catch (e: Exception) {
                _apiStatus.value = ApiStatus.Error
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val refreshToken = tokenManager.getRefreshToken() ?: return@launch

            try {
                apiService.logout(RefreshRequest(refreshToken))
            } finally {
                tokenManager.clearTokens()
                clearUser()
                _loggedInUser.value = null
            }
        }
    }

    private suspend fun saveUser(user: User) {
        dataStore.edit {
            it[PrefKeys.USER_FIRST_NAME] = user.firstName ?: ""
            it[PrefKeys.USER_LAST_NAME] = user.lastName ?: ""
            it[PrefKeys.USER_USERNAME] = user.username
        }
    }

    private suspend fun clearUser() {
        dataStore.edit {
            it.remove(PrefKeys.USER_FIRST_NAME)
            it.remove(PrefKeys.USER_LAST_NAME)
            it.remove(PrefKeys.USER_USERNAME)
        }
    }
    fun checkApiStatus() {
        viewModelScope.launch {
            _apiStatus.value = ApiStatus.Loading

            try {
                val response = apiService.getHealth()

                if (response.isSuccessful) {
                    val health = response.body()
                    if (health?.status == "ok" && health.database == "ok") {
                        _apiStatus.value = ApiStatus.Success
                    } else {
                        _apiStatus.value = ApiStatus.Warning
                    }
                } else {
                    _apiStatus.value = ApiStatus.Error
                }
            } catch (e: Exception) {
                _apiStatus.value = ApiStatus.Error
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tokenManager.removeOnTokenChangedListener(tokenListener)
    }
}