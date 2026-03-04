package de.flobaer.arbeitszeiterfassung.network.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthStatus(
    val status: String? = null,
    val timestamp: String? = null,
    val database: String? = null
)