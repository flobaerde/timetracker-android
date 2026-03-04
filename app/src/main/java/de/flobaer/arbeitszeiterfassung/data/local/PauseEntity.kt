package de.flobaer.arbeitszeiterfassung.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

@Entity(
    tableName = "pauses",
    foreignKeys = [
        ForeignKey(
            entity = WorkTimeEntity::class,
            parentColumns = ["id"],
            childColumns = ["workTimeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("workTimeId")]
)
data class PauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: Int? = null,
    val workTimeId: Long,
    val startTime: Instant,
    val endTime: Instant? = null,
    val syncStatus: SyncStatus = SyncStatus.CREATED
)
