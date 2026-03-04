package de.flobaer.arbeitszeiterfassung.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UpdateWorkTimeRequest(
    @SerialName("location_id") val locationId: Int,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String?
)
