package de.flobaer.arbeitszeiterfassung

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.flobaer.arbeitszeiterfassung.data.WorkTimeRepository
import de.flobaer.arbeitszeiterfassung.data.local.LocationEntity
import de.flobaer.arbeitszeiterfassung.data.local.PauseEntity
import de.flobaer.arbeitszeiterfassung.data.local.SyncStatus
import de.flobaer.arbeitszeiterfassung.data.local.WorkTimeEntity
import de.flobaer.arbeitszeiterfassung.network.LocationsRepository
import de.flobaer.arbeitszeiterfassung.ui.mapIcon
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import javax.inject.Inject

data class WorkTimeItem(
    val id: Long,
    val startTime: Instant,
    val endTime: Instant?,
    val locationId: Int,
    val syncStatus: SyncStatus,
    val pauseCount: Int = 0,
    val totalDurationMillis: Long = 0,
    val netDurationMillis: Long = 0
)

data class PauseItem(
    val id: Long,
    val startTime: Instant,
    val endTime: Instant?,
    val syncStatus: SyncStatus
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val locationsRepository: LocationsRepository,
    private val workTimeRepository: WorkTimeRepository
) : ViewModel() {

    private val _updateFailed = MutableStateFlow(false)
    val updateFailed = _updateFailed.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Success)
    val uiState = _uiState.asStateFlow()

    val locations: StateFlow<List<LocationOption>> = locationsRepository.locations
        .map { entities ->
            entities.map { it.toLocationOption() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val workTimes: StateFlow<List<WorkTimeItem>> = workTimeRepository.workTimesWithPauses
        .map { list ->
            list.map { wtp ->
                val entity = wtp.workTime
                val pauses = wtp.pauses
                val totalDuration = if (entity.endTime != null) {
                    entity.endTime.toEpochMilliseconds() - entity.startTime.toEpochMilliseconds()
                } else {
                    0L
                }
                val pauseDuration = pauses.sumOf { pause ->
                    if (pause.endTime != null) {
                        pause.endTime.toEpochMilliseconds() - pause.startTime.toEpochMilliseconds()
                    } else {
                        0L
                    }
                }
                
                // Aggregated sync status: if any pause is not SYNCED, the whole item is not SYNCED
                val aggregatedSyncStatus = when {
                    entity.syncStatus != SyncStatus.SYNCED -> entity.syncStatus
                    pauses.any { it.syncStatus != SyncStatus.SYNCED } -> {
                        // If any pause is CREATED, UPDATED or DELETED, we show it as UPDATED (unsynced)
                        // This ensures the sync icon on the main screen shows the correct state.
                        SyncStatus.UPDATED
                    }
                    else -> SyncStatus.SYNCED
                }

                WorkTimeItem(
                    id = entity.id,
                    startTime = entity.startTime,
                    endTime = entity.endTime,
                    locationId = entity.locationId,
                    syncStatus = aggregatedSyncStatus,
                    pauseCount = pauses.size,
                    totalDurationMillis = totalDuration,
                    netDurationMillis = totalDuration - pauseDuration
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isRunning: StateFlow<Boolean> = workTimes.map { it.any { item -> item.endTime == null } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val runningWorkTime: StateFlow<WorkTimeItem?> = workTimes.map { it.find { item -> item.endTime == null } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val currentPauses: StateFlow<List<PauseItem>> = runningWorkTime
        .flatMapLatest { running ->
            if (running != null) {
                workTimeRepository.getPausesForWorkTime(running.id)
            } else {
                flowOf(emptyList())
            }
        }
        .map { entities -> entities.map { it.toPauseItem() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isInPause: StateFlow<Boolean> = currentPauses.map { it.any { p -> p.endTime == null } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val activePause: StateFlow<PauseItem?> = currentPauses.map { it.find { p -> p.endTime == null } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun refreshLocations() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _uiState.value = MainUiState.Loading
            val success = locationsRepository.refreshLocations()
            _updateFailed.value = !success
            _isRefreshing.value = false
            _uiState.value = if (success) MainUiState.Success else MainUiState.Error("Fehler beim Laden der Orte")
        }
    }

    fun startWorkTime(locationId: Int) {
        viewModelScope.launch {
            workTimeRepository.startWorkTime(locationId)
        }
    }

    fun stopWorkTime() {
        viewModelScope.launch {
            workTimeRepository.stopWorkTime()
        }
    }

    fun startPause() {
        val running = runningWorkTime.value ?: return
        viewModelScope.launch {
            workTimeRepository.startPause(running.id)
        }
    }

    fun stopPause() {
        val running = runningWorkTime.value ?: return
        viewModelScope.launch {
            workTimeRepository.stopPause(running.id)
        }
    }

    fun editWorkTime(workTimeItem: WorkTimeItem, newLocationId: Int, newStartTime: Instant, newEndTime: Instant) {
        viewModelScope.launch {
            workTimeRepository.editWorkTime(workTimeItem.id, newLocationId, newStartTime, newEndTime)
        }
    }

    fun deleteWorkTime(workTimeItem: WorkTimeItem) {
        viewModelScope.launch {
            workTimeRepository.deleteWorkTime(workTimeItem.id)
        }
    }

    fun syncWorkTimes() {
        viewModelScope.launch {
            workTimeRepository.syncUnsyncedWorkTimes()
        }
    }
}

private fun LocationEntity.toLocationOption() = LocationOption(
    id = this.id,
    name = this.name,
    icon = mapIcon(this.iconName)
)

private fun WorkTimeEntity.toWorkTimeItem() = WorkTimeItem(
    id = this.id,
    startTime = this.startTime,
    endTime = this.endTime,
    locationId = this.locationId,
    syncStatus = this.syncStatus
)

private fun PauseEntity.toPauseItem() = PauseItem(
    id = this.id,
    startTime = this.startTime,
    endTime = this.endTime,
    syncStatus = this.syncStatus
)

sealed interface MainUiState {
    object Loading : MainUiState
    object Success : MainUiState
    data class Error(val message: String) : MainUiState
}