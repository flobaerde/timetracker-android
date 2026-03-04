package de.flobaer.arbeitszeiterfassung.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import java.util.concurrent.TimeUnit
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.flobaer.arbeitszeiterfassung.data.WorkTimeRepository
import de.flobaer.arbeitszeiterfassung.network.LocationsRepository

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val workTimeRepository: WorkTimeRepository,
    private val locationsRepository: LocationsRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            locationsRepository.refreshLocations()
            workTimeRepository.syncUnsyncedWorkTimes()
            Result.success()
        } catch (e: Exception) {
            // Bei Netzwerkfehlern oder Serverfehlern lohnt sich ein Retry
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "PeriodicSyncWork"
        const val UNIQUE_ON_DEMAND_NAME = "OnDemandSyncWork"

        fun createPeriodicRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return PeriodicWorkRequest.Builder(
                SyncWorker::class.java as Class<out ListenableWorker>,
                15,
                TimeUnit.MINUTES
            )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .build()
        }
    }
}
