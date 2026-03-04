package de.flobaer.arbeitszeiterfassung.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.datetime.Instant

class SyncStatusConverter {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(status: String): SyncStatus = SyncStatus.valueOf(status)
}

class InstantConverter {
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilliseconds()

    @TypeConverter
    fun toInstant(millis: Long?): Instant? = millis?.let { Instant.fromEpochMilliseconds(it) }
}

@Entity(tableName = "work_times")
data class WorkTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: Int? = null,
    val locationId: Int,
    val startTime: Instant,
    val endTime: Instant? = null,
    val syncStatus: SyncStatus = SyncStatus.CREATED
)