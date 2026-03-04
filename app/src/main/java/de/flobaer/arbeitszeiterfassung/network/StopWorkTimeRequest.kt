package de.flobaer.arbeitszeiterfassung.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StopWorkTimeRequest(
    @SerialName("end_time") val endTime: String
)