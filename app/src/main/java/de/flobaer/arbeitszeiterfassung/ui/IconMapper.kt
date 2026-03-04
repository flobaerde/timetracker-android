package de.flobaer.arbeitszeiterfassung.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

fun mapIcon(iconName: String?): ImageVector {
    return when (iconName?.lowercase()) {
        "business" -> Icons.Default.Business
        "home" -> Icons.Default.Home
        "flight" -> Icons.Default.Flight
        "work" -> Icons.Default.Work
        "location_city" -> Icons.Default.LocationCity
        "commute" -> Icons.Default.Commute
        "computer" -> Icons.Default.Computer
        "laptop" -> Icons.Default.Laptop
        "card_travel" -> Icons.Default.CardTravel
        else -> Icons.AutoMirrored.Filled.HelpOutline // Default icon if name is unknown or null
    }
}
