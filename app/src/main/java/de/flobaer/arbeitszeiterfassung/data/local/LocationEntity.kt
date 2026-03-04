package de.flobaer.arbeitszeiterfassung.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey
    val id: Int,
    val name: String,
    val iconName: String?
)