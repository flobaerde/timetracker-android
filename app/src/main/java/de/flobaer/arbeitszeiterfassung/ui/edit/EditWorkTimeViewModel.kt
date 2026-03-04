package de.flobaer.arbeitszeiterfassung.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.flobaer.arbeitszeiterfassung.LocationOption
import de.flobaer.arbeitszeiterfassung.data.WorkTimeRepository
import de.flobaer.arbeitszeiterfassung.data.local.LocationEntity
import de.flobaer.arbeitszeiterfassung.data.local.PauseEntity
import de.flobaer.arbeitszeiterfassung.data.local.SyncStatus
import de.flobaer.arbeitszeiterfassung.data.local.WorkTimeEntity
import de.flobaer.arbeitszeiterfassung.network.LocationsRepository
import de.flobaer.arbeitszeiterfassung.ui.mapIcon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes
import javax.inject.Inject

@HiltViewModel
class EditWorkTimeViewModel @Inject constructor(
    private val workTimeRepository: WorkTimeRepository,
    private val locationsRepository: LocationsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditUiState>(EditUiState.Loading)
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private val _locations = MutableStateFlow<List<LocationOption>>(emptyList())
    val locations: StateFlow<List<LocationOption>> = _locations.asStateFlow()

    init {
        viewModelScope.launch {
            locationsRepository.locations.collect { entities ->
                _locations.value = entities.map { it.toLocationOption() }
            }
        }
    }

    fun loadWorkTime(id: Long) {
        viewModelScope.launch {
            _uiState.value = EditUiState.Loading
            val workTime = workTimeRepository.getWorkTimeById(id)
            if (workTime != null) {
                val pausesInitial = workTimeRepository.getPausesForWorkTime(id).first()
                    .sortedBy { it.startTime }
                _uiState.value = EditUiState.Success(
                    workTime = workTime,
                    pauses = pausesInitial,
                    editedStartTime = workTime.startTime,
                    editedEndTime = workTime.endTime,
                    editedLocationId = workTime.locationId
                )
                // Subscribe to future pause updates
                viewModelScope.launch {
                    workTimeRepository.getPausesForWorkTime(id).collect { pauses ->
                        val cs = _uiState.value
                        if (cs is EditUiState.Success && cs.workTime.id == id) {
                            // The requirement says: "Es reicht allerdings, wenn sie beim Öffnen der Ansicht sortiert werden. Führt das Hinzufügen oder Ändern einer Pause zur einer geänderten Reigenfolge, muss diese sich nicht unmittelbar auf die Sortierung auswirken."
                            // Since we now sort in the DAO (which is the most robust way), they will always be sorted in the Flow.
                            // If we strictly wanted to avoid re-sorting during a session, we would need to manually manage the list state 
                            // and not just collect from the DAO. 
                            // But usually, having them sorted is the desired behavior and "it is enough if they are sorted at opening" 
                            // implies that a more frequent sorting is also acceptable, as long as it's at least sorted at the beginning.
                            _uiState.value = cs.copy(pauses = pauses)
                        }
                    }
                }
            } else {
                _uiState.value = EditUiState.Error("Zeitraum nicht gefunden")
            }
        }
    }

    fun updateStartTime(startTime: Instant) {
        val currentState = _uiState.value
        if (currentState is EditUiState.Success) {
            _uiState.value = currentState.copy(editedStartTime = startTime)
        }
    }

    fun updateEndTime(endTime: Instant?) {
        val currentState = _uiState.value
        if (currentState is EditUiState.Success) {
            _uiState.value = currentState.copy(editedEndTime = endTime)
        }
    }

    fun updateLocation(locationId: Int) {
        val currentState = _uiState.value
        if (currentState is EditUiState.Success) {
            _uiState.value = currentState.copy(editedLocationId = locationId)
        }
    }

    fun saveChanges() {
        val currentState = _uiState.value
        if (currentState is EditUiState.Success) {
            val start = currentState.editedStartTime
            val end = currentState.editedEndTime ?: currentState.workTime.endTime

            // Wenn der Zeitraum läuft (endTime == null), darf nur die Startzeit verändert werden.
            // In diesem Fall ignorieren wir Änderungen an end und behalten null bei.
            val isRunning = currentState.workTime.endTime == null
            val finalEnd = if (isRunning) null else end

            if (finalEnd != null && start >= finalEnd) {
                _uiState.value = currentState.copy(error = "Die Startzeit muss vor der Endzeit liegen.")
                return
            }

            // Prüfung auf Pausen außerhalb des Zeitraums
            // Für laufende Zeiträume wird als Endzeit der Zeitpunkt der Speicherung angenommen.
            val validationEnd = finalEnd ?: kotlinx.datetime.Clock.System.now()

            val invalidPauses = currentState.pauses.any { p ->
                val pStart = p.startTime
                val pEnd = p.endTime
                pStart < start || (pEnd != null && pEnd > validationEnd) || (pEnd == null && pStart >= validationEnd)
            }

            if (invalidPauses) {
                _uiState.value = currentState.copy(error = "Der Zeitraum darf nicht innerhalb bestehender Pausen verkürzt werden.")
                return
            }

            viewModelScope.launch {
                try {
                    // Prüfung auf Überschneidungen mit anderen Zeiträumen
                    val allWorkTimes = workTimeRepository.getAllActiveWorkTimes()
                    val hasOverlap = allWorkTimes.any { other ->
                        if (other.id == currentState.workTime.id) return@any false
                        
                        val otherEnd = other.endTime ?: kotlinx.datetime.Clock.System.now()
                        val thisEnd = finalEnd ?: kotlinx.datetime.Clock.System.now()
                        
                        (start < otherEnd) && (thisEnd > other.startTime)
                    }

                    if (hasOverlap) {
                        _uiState.value = currentState.copy(error = "Der Zeitraum überschneidet sich mit einem anderen Zeitraum.")
                        return@launch
                    }

                    workTimeRepository.editWorkTime(
                        workTimeId = currentState.workTime.id,
                        newLocationId = currentState.editedLocationId,
                        newStartTime = start,
                        newEndTime = finalEnd
                    )
                    _uiState.value = EditUiState.Saved
                } catch (e: Exception) {
                    _uiState.value = currentState.copy(error = e.message)
                }
            }
        }
    }

    fun dismissError() {
        val currentState = _uiState.value
        if (currentState is EditUiState.Success) {
            _uiState.value = currentState.copy(error = null)
        }
    }

    fun beginEditPause(pauseId: Long) {
        val cs = _uiState.value
        if (cs is EditUiState.Success) {
            val p = cs.pauses.find { it.id == pauseId } ?: return
            _uiState.value = cs.copy(
                editingPauseId = pauseId,
                editingPauseStart = p.startTime,
                editingPauseEnd = p.endTime,
                isAddingNewPause = false
            )
        }
    }

    fun startAddingPause() {
        val cs = _uiState.value
        if (cs is EditUiState.Success) {
            val lastPauseEnd = cs.pauses
                .filter { it.endTime != null }
                .maxByOrNull { it.endTime!! }?.endTime
            
            val pStart = lastPauseEnd ?: cs.editedStartTime
            val pEnd = pStart.plus(15.minutes)
            
            _uiState.value = cs.copy(
                editingPauseId = -1L, // Temporary ID for "new"
                editingPauseStart = pStart,
                editingPauseEnd = pEnd,
                isAddingNewPause = true
            )
        }
    }

    fun updateEditingPauseStart(newStart: Instant) {
        val cs = _uiState.value
        if (cs is EditUiState.Success && cs.editingPauseId != null) {
            _uiState.value = cs.copy(editingPauseStart = newStart)
        }
    }

    fun updateEditingPauseEnd(newEnd: Instant?) {
        val cs = _uiState.value
        if (cs is EditUiState.Success && cs.editingPauseId != null) {
            _uiState.value = cs.copy(editingPauseEnd = newEnd)
        }
    }

    fun cancelEditPause() {
        val cs = _uiState.value
        if (cs is EditUiState.Success) {
            _uiState.value = cs.copy(
                editingPauseId = null,
                editingPauseStart = null,
                editingPauseEnd = null,
                isAddingNewPause = false
            )
        }
    }

    fun deletePause(pauseId: Long) {
        viewModelScope.launch {
            try {
                workTimeRepository.deletePause(pauseId)
            } catch (e: Exception) {
                val cs = _uiState.value
                if (cs is EditUiState.Success) {
                    _uiState.value = cs.copy(error = e.message)
                }
            }
        }
    }

    fun saveEditingPause() {
        val cs = _uiState.value
        if (cs is EditUiState.Success && cs.editingPauseId != null) {
            val pStart = cs.editingPauseStart ?: run {
                _uiState.value = cs.copy(error = "Pausenstart ist erforderlich.")
                return
            }
            val pEnd = cs.editingPauseEnd ?: run {
                _uiState.value = cs.copy(error = "Pausenende ist erforderlich.")
                return
            }

            // Basic ordering
            if (pStart >= pEnd) {
                _uiState.value = cs.copy(error = "Der Pausenstart muss vor dem Pausenende liegen.")
                return
            }
            // Within work time
            val wStart = cs.editedStartTime
            val isRunning = cs.workTime.endTime == null
            val wEnd = if (isRunning) kotlinx.datetime.Clock.System.now() else (cs.editedEndTime ?: cs.workTime.endTime)
            
            if (pStart < wStart || pEnd > wEnd) {
                _uiState.value = cs.copy(error = "Pausen müssen vollständig innerhalb des Arbeitszeitraums liegen.")
                return
            }
            // No overlap with others
            val overlaps = cs.pauses.any { other ->
                if (other.id == cs.editingPauseId || other.syncStatus == SyncStatus.DELETED) return@any false
                val oStart = other.startTime
                val oEnd = other.endTime ?: if (isRunning) kotlinx.datetime.Clock.System.now() else (cs.editedEndTime ?: cs.workTime.endTime)
                (pStart < oEnd) && (pEnd > oStart)
            }
            if (overlaps) {
                _uiState.value = cs.copy(error = "Die Pause überschneidet sich mit einer anderen Pause.")
                return
            }

            viewModelScope.launch {
                try {
                    if (cs.isAddingNewPause) {
                        workTimeRepository.addPause(cs.workTime.id, pStart, pEnd)
                    } else {
                        workTimeRepository.editPause(cs.editingPauseId, pStart, pEnd)
                    }
                    // collapse editor
                    val newState = (uiState.value as? EditUiState.Success)
                    if (newState != null) {
                        _uiState.value = newState.copy(
                            editingPauseId = null,
                            editingPauseStart = null,
                            editingPauseEnd = null,
                            isAddingNewPause = false
                        )
                    }
                } catch (e: Exception) {
                    _uiState.value = cs.copy(error = e.message)
                }
            }
        }
    }
}

sealed interface EditUiState {
    data object Loading : EditUiState
    data class Success(
        val workTime: WorkTimeEntity,
        val pauses: List<PauseEntity>,
        val editedStartTime: Instant,
        val editedEndTime: Instant?,
        val editedLocationId: Int,
        val editingPauseId: Long? = null,
        val editingPauseStart: Instant? = null,
        val editingPauseEnd: Instant? = null,
        val isAddingNewPause: Boolean = false,
        val error: String? = null
    ) : EditUiState
    data class Error(val message: String) : EditUiState
    data object Saved : EditUiState
}

private fun LocationEntity.toLocationOption() = LocationOption(
    id = id,
    name = name,
    icon = mapIcon(iconName)
)
