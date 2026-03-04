package de.flobaer.arbeitszeiterfassung.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Query("SELECT * FROM locations ORDER BY name ASC")
    fun getLocations(): Flow<List<LocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocations(locations: List<LocationEntity>): List<Long>

    @Query("DELETE FROM locations")
    suspend fun deleteAllLocations(): Int

    @Transaction
    suspend fun replaceAll(locations: List<LocationEntity>): Boolean {
        deleteAllLocations()
        insertLocations(locations)
        return true
    }
}