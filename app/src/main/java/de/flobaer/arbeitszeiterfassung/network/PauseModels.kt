package de.flobaer.arbeitszeiterfassung.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PauseCreateRequest(
    @SerialName("worktime_id") val workTimeId: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null
)

@Serializable
data class PauseUpdateRequest(
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null
)

@Serializable
data class PauseCreateResponse(
    val id: Int,
    val message: String
)
