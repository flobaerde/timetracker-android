package de.flobaer.arbeitszeiterfassung.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface PauseDao {
    @Insert
    suspend fun insertPause(pause: PauseEntity): Long

    @Update
    suspend fun updatePause(pause: PauseEntity): Int

    @Query("SELECT * FROM pauses WHERE workTimeId = :workTimeId ORDER BY startTime ASC")
    fun getPausesForWorkTime(workTimeId: Long): Flow<List<PauseEntity>>

    @Query("SELECT * FROM pauses WHERE workTimeId = :workTimeId AND endTime IS NULL LIMIT 1")
    suspend fun getRunningPause(workTimeId: Long): PauseEntity?

    @Query("SELECT * FROM pauses WHERE syncStatus != 'SYNCED'")
    suspend fun getUnsyncedPauses(): List<PauseEntity>
    
    @Query("SELECT * FROM pauses WHERE id = :id")
    suspend fun getPauseById(id: Long): PauseEntity?

    @Query("DELETE FROM pauses WHERE id = :id")
    suspend fun deletePauseImmediate(id: Long): Int

    @Query("SELECT * FROM pauses WHERE remoteId = :remoteId")
    suspend fun getPauseByRemoteId(remoteId: Int): PauseEntity?
}
