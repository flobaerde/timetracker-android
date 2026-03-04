package de.flobaer.arbeitszeiterfassung.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface WorkTimeDao {
    @Insert
    suspend fun insertWorkTime(workTime: WorkTimeEntity): Long

    @Update
    suspend fun updateWorkTime(workTime: WorkTimeEntity): Int

    @Delete
    suspend fun deleteWorkTime(workTime: WorkTimeEntity): Int

    @Query("SELECT * FROM work_times WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedWorkTimes(): List<WorkTimeEntity>

    @Transaction
    @Query("SELECT * FROM work_times WHERE syncStatus != 'DELETED' ORDER BY startTime DESC")
    fun getAllWorkTimesWithPauses(): Flow<List<WorkTimeWithPauses>>

    @Query("SELECT * FROM work_times WHERE syncStatus != 'DELETED' ORDER BY startTime DESC")
    fun getAllWorkTimes(): Flow<List<WorkTimeEntity>>

    @Query("SELECT * FROM work_times WHERE endTime IS NULL LIMIT 1")
    suspend fun getRunningWorkTime(): WorkTimeEntity?
    
    @Query("SELECT * FROM work_times WHERE syncStatus != 'DELETED' ORDER BY startTime DESC")
    suspend fun getAllActiveWorkTimes(): List<WorkTimeEntity>

    @Query("SELECT * FROM work_times WHERE id = :id")
    suspend fun getWorkTimeById(id: Long): WorkTimeEntity?

    @Query("SELECT * FROM work_times WHERE remoteId = :remoteId")
    suspend fun getWorkTimeByRemoteId(remoteId: Int): WorkTimeEntity?

    @Query("DELETE FROM work_times WHERE syncStatus = 'SYNCED' AND startTime < :threshold")
    suspend fun deleteOldSyncedWorkTimes(threshold: Instant)
}