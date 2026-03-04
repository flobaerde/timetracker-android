package de.flobaer.arbeitszeiterfassung.data

import android.util.Log
import de.flobaer.arbeitszeiterfassung.data.local.PauseDao
import de.flobaer.arbeitszeiterfassung.data.local.PauseEntity
import de.flobaer.arbeitszeiterfassung.data.local.SyncStatus
import de.flobaer.arbeitszeiterfassung.data.local.WorkTimeDao
import de.flobaer.arbeitszeiterfassung.data.local.WorkTimeWithPauses
import de.flobaer.arbeitszeiterfassung.data.local.WorkTimeEntity
import de.flobaer.arbeitszeiterfassung.network.*
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.ListenableWorker
import java.util.concurrent.TimeUnit

class WorkTimeRepository(
    private val appContext: Context,
    private val workTimeDao: WorkTimeDao,
    private val pauseDao: PauseDao,
    private val workTimeApiService: WorkTimeApiService
) {
    val workTimes: Flow<List<WorkTimeEntity>> = workTimeDao.getAllWorkTimes()
    val workTimesWithPauses: Flow<List<WorkTimeWithPauses>> = workTimeDao.getAllWorkTimesWithPauses()

    suspend fun startWorkTime(locationId: Int) {
        val startTime = Clock.System.now()

        val workTimeEntity = WorkTimeEntity(
            locationId = locationId,
            startTime = startTime,
            syncStatus = SyncStatus.CREATED
        )
        workTimeDao.insertWorkTime(workTimeEntity)
        enqueueSync()
    }

    suspend fun stopWorkTime() {
        val runningWorkTime = workTimeDao.getRunningWorkTime() ?: return

        // If there is a running pause, stop it first.
        stopPause(runningWorkTime.id)

        val endTime = Clock.System.now()

        val updatedEntity = runningWorkTime.copy(
            endTime = endTime,
            syncStatus = if (runningWorkTime.syncStatus == SyncStatus.CREATED) SyncStatus.CREATED else SyncStatus.UPDATED
        )
        workTimeDao.updateWorkTime(updatedEntity)
        enqueueSync()
    }

    suspend fun editWorkTime(workTimeId: Long, newLocationId: Int, newStartTime: Instant, newEndTime: Instant?) {
        val workTimeEntity = workTimeDao.getWorkTimeById(workTimeId) ?: return
        val updatedEntity = workTimeEntity.copy(
            locationId = newLocationId,
            startTime = newStartTime,
            endTime = newEndTime,
            syncStatus = if (workTimeEntity.syncStatus == SyncStatus.CREATED) SyncStatus.CREATED else SyncStatus.UPDATED
        )
        workTimeDao.updateWorkTime(updatedEntity)
        enqueueSync()
    }

    suspend fun deleteWorkTime(workTimeId: Long) {
        val workTimeEntity = workTimeDao.getWorkTimeById(workTimeId) ?: return
        // If the entry was created offline and never synced, just delete it locally.
        if (workTimeEntity.syncStatus == SyncStatus.CREATED) {
            workTimeDao.deleteWorkTime(workTimeEntity)
        } else {
            // Otherwise, mark it for deletion.
            val updatedEntity = workTimeEntity.copy(syncStatus = SyncStatus.DELETED)
            workTimeDao.updateWorkTime(updatedEntity)
            syncUnsyncedWorkTimes()
        }
    }

    suspend fun startPause(workTimeId: Long) {
        val startTime = Clock.System.now()
        val pauseEntity = PauseEntity(
            workTimeId = workTimeId,
            startTime = startTime,
            syncStatus = SyncStatus.CREATED
        )
        pauseDao.insertPause(pauseEntity)
        enqueueSync()
    }

    suspend fun stopPause(workTimeId: Long) {
        val runningPause = pauseDao.getRunningPause(workTimeId) ?: return
        val endTime = Clock.System.now()
        val updatedEntity = runningPause.copy(
            endTime = endTime,
            syncStatus = if (runningPause.syncStatus == SyncStatus.CREATED) SyncStatus.CREATED else SyncStatus.UPDATED
        )
        pauseDao.updatePause(updatedEntity)
        enqueueSync()
    }

    fun getPausesForWorkTime(workTimeId: Long): Flow<List<PauseEntity>> {
        return pauseDao.getPausesForWorkTime(workTimeId)
    }

    suspend fun getRunningPause(workTimeId: Long): PauseEntity? {
        return pauseDao.getRunningPause(workTimeId)
    }

    suspend fun addPause(workTimeId: Long, startTime: Instant, endTime: Instant) {
        val pauseEntity = PauseEntity(
            workTimeId = workTimeId,
            startTime = startTime,
            endTime = endTime,
            syncStatus = SyncStatus.CREATED
        )
        pauseDao.insertPause(pauseEntity)
        enqueueSync()
    }

    suspend fun editPause(pauseId: Long, newStartTime: Instant, newEndTime: Instant) {
        val pause = pauseDao.getPauseById(pauseId) ?: return
        val updated = pause.copy(
            startTime = newStartTime,
            endTime = newEndTime,
            syncStatus = if (pause.syncStatus == SyncStatus.CREATED) SyncStatus.CREATED else SyncStatus.UPDATED
        )
        pauseDao.updatePause(updated)
        enqueueSync()
    }

    suspend fun deletePause(pauseId: Long) {
        val pause = pauseDao.getPauseById(pauseId) ?: return
        if (pause.remoteId == null) {
            // Not synced yet, just delete locally.
            pauseDao.deletePauseImmediate(pauseId)
        } else {
            // Mark for deletion on the server
            val updated = pause.copy(syncStatus = SyncStatus.DELETED)
            pauseDao.updatePause(updated)
            enqueueSync()
        }
    }

    suspend fun syncUnsyncedWorkTimes() {
        syncUnsyncedPauses()
        cleanupOldSyncedData()
        fetchMissingRemoteData()

        val unsyncedWorkTimes = workTimeDao.getUnsyncedWorkTimes()
        for (workTime in unsyncedWorkTimes) {
            try {
                when (workTime.syncStatus) {
                    SyncStatus.CREATED -> {
                        val request = StartWorkTimeRequest(locationId = workTime.locationId, startTime = workTime.startTime.toString())
                        val response = workTimeApiService.startWorkTime(request)
                        if (response.isSuccessful) {
                            val body = response.body()!!
                            val finalEntity = workTime.copy(remoteId = body.id, syncStatus = SyncStatus.SYNCED)
                            if (workTime.endTime != null) {
                                // The entry was stopped before it was even created on the server.
                                // We need to update the endTime now.
                                val stopRequest = StopWorkTimeRequest(endTime = workTime.endTime.toString())
                                workTimeApiService.stopWorkTime(body.id, stopRequest)
                            }
                            workTimeDao.updateWorkTime(finalEntity)
                        } else if (response.code() == 401) {
                            Log.e("WorkTimeRepository", "Sync failed: 401 Unauthorized")
                        }
                    }
                    SyncStatus.UPDATED -> {
                        val remoteId = workTime.remoteId
                        if (remoteId == null) {
                            // Fallback: Wenn kein Remote-Objekt existiert, wie CREATED behandeln
                            val createReq = StartWorkTimeRequest(locationId = workTime.locationId, startTime = workTime.startTime.toString())
                            val createResp = workTimeApiService.startWorkTime(createReq)
                            if (createResp.isSuccessful) {
                                val body = createResp.body()!!
                                if (workTime.endTime != null) {
                                    val stopReq = StopWorkTimeRequest(endTime = workTime.endTime.toString())
                                    workTimeApiService.stopWorkTime(body.id, stopReq)
                                }
                                workTimeDao.updateWorkTime(workTime.copy(remoteId = body.id, syncStatus = SyncStatus.SYNCED))
                            } else if (createResp.code() == 401) {
                                Log.e("WorkTimeRepository", "Sync failed: 401 Unauthorized")
                            }
                        } else {
                            val updateReq = UpdateWorkTimeRequest(
                                locationId = workTime.locationId,
                                startTime = workTime.startTime.toString(),
                                endTime = workTime.endTime?.toString()
                            )
                            val updateResp = workTimeApiService.updateWorkTime(remoteId, updateReq)
                            if (updateResp.isSuccessful) {
                                workTimeDao.updateWorkTime(workTime.copy(syncStatus = SyncStatus.SYNCED))
                            } else if (updateResp.code() == 401) {
                                Log.e("WorkTimeRepository", "Sync failed: 401 Unauthorized")
                            }
                        }
                    }
                    SyncStatus.DELETED -> {
                        if (workTime.remoteId != null) {
                            val response = workTimeApiService.deleteWorkTime(workTime.remoteId)
                            if (response.isSuccessful || response.code() == 404) {
                                // Once the remote entry is deleted (or already gone), delete it locally as well.
                                workTimeDao.deleteWorkTime(workTime)
                            } else if (response.code() == 401) {
                                Log.e("WorkTimeRepository", "Sync failed: 401 Unauthorized")
                            }
                        } else {
                            workTimeDao.deleteWorkTime(workTime)
                        }
                    }
                    SyncStatus.SYNCED -> {
                        // Should not happen, but good to handle.
                    }
                }
            } catch (e: Exception) {
                Log.e("WorkTimeRepository", "Sync failed with exception", e)
            }
        }
    }

    suspend fun syncUnsyncedPauses() {
        val unsyncedPauses = pauseDao.getUnsyncedPauses()
        for (pause in unsyncedPauses) {
            try {
                val workTime = workTimeDao.getWorkTimeById(pause.workTimeId)
                if (workTime?.remoteId == null) {
                    // WorkTime not yet synced, wait for next sync cycle
                    continue
                }

                when (pause.syncStatus) {
                    SyncStatus.CREATED -> {
                        val request = PauseCreateRequest(
                            workTimeId = workTime.remoteId,
                            startTime = pause.startTime.toString(),
                            endTime = pause.endTime?.toString()
                        )
                        val response = workTimeApiService.startPause(request)
                        if (response.isSuccessful) {
                            val body = response.body()!!
                            pauseDao.updatePause(pause.copy(remoteId = body.id, syncStatus = SyncStatus.SYNCED))
                        } else if (response.code() == 401) {
                            Log.e("WorkTimeRepository", "Pause sync failed: 401 Unauthorized")
                        }
                    }
                    SyncStatus.UPDATED -> {
                        val remoteId = pause.remoteId
                        if (remoteId != null) {
                            val request = PauseUpdateRequest(
                                startTime = pause.startTime.toString(),
                                endTime = pause.endTime?.toString()
                            )
                            val response = workTimeApiService.updatePause(remoteId, request)
                            if (response.isSuccessful) {
                                pauseDao.updatePause(pause.copy(syncStatus = SyncStatus.SYNCED))
                            } else if (response.code() == 401) {
                                Log.e("WorkTimeRepository", "Pause sync failed: 401 Unauthorized")
                            }
                        }
                    }
                    SyncStatus.DELETED -> {
                        if (pause.remoteId != null) {
                            val response = workTimeApiService.deletePause(pause.remoteId)
                            if (response.isSuccessful || response.code() == 404) {
                                pauseDao.deletePauseImmediate(pause.id)
                            } else if (response.code() == 401) {
                                Log.e("WorkTimeRepository", "Pause sync failed: 401 Unauthorized")
                            }
                        } else {
                            pauseDao.deletePauseImmediate(pause.id)
                        }
                    }
                    SyncStatus.SYNCED -> {}
                }
            } catch (e: Exception) {
                Log.e("WorkTimeRepository", "Pause sync failed with exception", e)
            }
        }
    }

    suspend fun fetchMissingRemoteData() {
        try {
            val sinceHours = 240 // 10 days
            val response = workTimeApiService.getWorkTimes(sinceHours)
            if (response.isSuccessful) {
                val remoteWorkTimes = response.body() ?: emptyList()
                for (remoteWorkTime in remoteWorkTimes) {
                    var localWorkTime = workTimeDao.getWorkTimeByRemoteId(remoteWorkTime.id)
                    
                    if (localWorkTime == null) {
                        // Create new local entry
                        val startTime = parseApiDateTime(remoteWorkTime.startTime)
                        val endTime = remoteWorkTime.endTime?.let { parseApiDateTime(it) }
                        
                        val newEntity = WorkTimeEntity(
                            remoteId = remoteWorkTime.id,
                            locationId = remoteWorkTime.locationId,
                            startTime = startTime,
                            endTime = endTime,
                            syncStatus = SyncStatus.SYNCED
                        )
                        val newId = workTimeDao.insertWorkTime(newEntity)
                        localWorkTime = newEntity.copy(id = newId)
                    }

                    // Sync pauses for this workTime
                    val pausesResponse = workTimeApiService.getPauses(remoteWorkTime.id)
                    val remotePauses = if (pausesResponse.isSuccessful) {
                        pausesResponse.body() ?: emptyList()
                    } else {
                        remoteWorkTime.pauses
                    }

                    for (remotePause in remotePauses) {
                        val localPause = pauseDao.getPauseByRemoteId(remotePause.id)
                        if (localPause == null) {
                            val pauseStartTime = parseApiDateTime(remotePause.startTime)
                            val pauseEndTime = remotePause.endTime?.let { parseApiDateTime(it) }
                            
                            val newPause = PauseEntity(
                                remoteId = remotePause.id,
                                workTimeId = localWorkTime.id,
                                startTime = pauseStartTime,
                                endTime = pauseEndTime,
                                syncStatus = SyncStatus.SYNCED
                            )
                            pauseDao.insertPause(newPause)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("WorkTimeRepository", "Failed to fetch missing remote data", e)
        }
    }

    private fun parseApiDateTime(dateTimeString: String): Instant {
        return try {
            // Die API liefert das Format "YYYY-MM-DD HH:MM:SS"
            // Wir wandeln das Leerzeichen in ein 'T' um, damit LocalDateTime.parse es verarbeiten kann
            val isoString = dateTimeString.replace(" ", "T")
            LocalDateTime.parse(isoString).toInstant(TimeZone.currentSystemDefault())
        } catch (e: Exception) {
            // Fallback auf Instant.parse falls es doch ISO ist
            Instant.parse(dateTimeString)
        }
    }

    private suspend fun cleanupOldSyncedData() {
        try {
            val tenDaysAgo = Clock.System.now().minus(240.hours)
            workTimeDao.deleteOldSyncedWorkTimes(tenDaysAgo)
        } catch (e: Exception) {
            Log.e("WorkTimeRepository", "Failed to cleanup old data", e)
        }
    }

    suspend fun getWorkTimeById(id: Long): WorkTimeEntity? {
        return workTimeDao.getWorkTimeById(id)
    }

    suspend fun getAllActiveWorkTimes(): List<WorkTimeEntity> {
        return workTimeDao.getAllActiveWorkTimes()
    }

    private fun enqueueSync() {
        val request = OneTimeWorkRequest.Builder(de.flobaer.arbeitszeiterfassung.sync.SyncWorker::class.java as Class<out ListenableWorker>)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            de.flobaer.arbeitszeiterfassung.sync.SyncWorker.UNIQUE_ON_DEMAND_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}