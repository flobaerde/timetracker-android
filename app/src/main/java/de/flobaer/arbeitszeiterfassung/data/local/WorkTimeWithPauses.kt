package de.flobaer.arbeitszeiterfassung.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class WorkTimeWithPauses(
    @Embedded val workTime: WorkTimeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "workTimeId"
    )
    val pauses: List<PauseEntity>
)
