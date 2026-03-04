package de.flobaer.arbeitszeiterfassung.network

import kotlinx.serialization.Serializable

@Serializable
data class StartWorkTimeResponse(
    val id: Int,
    val message: String
)