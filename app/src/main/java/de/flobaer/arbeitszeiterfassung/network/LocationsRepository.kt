package de.flobaer.arbeitszeiterfassung.network

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import de.flobaer.arbeitszeiterfassung.PrefKeys
import de.flobaer.arbeitszeiterfassung.data.local.LocationDao
import de.flobaer.arbeitszeiterfassung.data.local.LocationEntity
import de.flobaer.arbeitszeiterfassung.network.model.ApiLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class LocationsRepository(
    private val locationDao: LocationDao,
    private val apiService: ApiService,
    private val dataStore: DataStore<Preferences>
) {

    val locations: Flow<List<LocationEntity>> = locationDao.getLocations()

    suspend fun refreshLocations(): Boolean {
        val prefs = dataStore.data.first()
        val baseUrl = prefs[PrefKeys.API_URL]

        if (baseUrl.isNullOrEmpty()) return false

        return try {
            val apiLocations = apiService.getLocations()
            val activeLocations = apiLocations.filter { !it.deleted }
            val locationEntities = activeLocations.map { it.toEntity() }
            locationDao.replaceAll(locationEntities)
            true
        } catch (e: Exception) {
            Log.e("LocationsRepository", "Failed to refresh locations", e)
            false
        }
    }
}

private fun ApiLocation.toEntity() = LocationEntity(
    id = this.id,
    name = this.name,
    iconName = this.iconName
)
