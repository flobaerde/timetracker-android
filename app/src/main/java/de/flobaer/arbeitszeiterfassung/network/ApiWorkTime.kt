package de.flobaer.arbeitszeiterfassung.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiWorkTime(
    val id: Int,
    @SerialName("location_id") val locationId: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null,
    val pauses: List<ApiPause> = emptyList()
)

@Serializable
data class ApiPause(
    val id: Int,
    @SerialName("worktime_id") val workTimeId: Int? = null,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null
)
