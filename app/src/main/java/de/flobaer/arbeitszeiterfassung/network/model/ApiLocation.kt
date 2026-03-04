package de.flobaer.arbeitszeiterfassung.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiLocation(
    val id: Int,
    @SerialName("location_name")
    val name: String,
    @SerialName("location_icon")
    val iconName: String? = null,
    val deleted: Boolean = false
)