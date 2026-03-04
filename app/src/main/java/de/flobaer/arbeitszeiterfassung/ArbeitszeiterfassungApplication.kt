package de.flobaer.arbeitszeiterfassung

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import dagger.hilt.android.HiltAndroidApp
import de.flobaer.arbeitszeiterfassung.sync.SyncWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class ArbeitszeiterfassungApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Da wir den WorkManagerInitializer im Manifest deaktiviert haben,
        // müssen wir den WorkManager hier manuell mit der HiltWorkerFactory initialisieren.
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(
                this,
                Configuration.Builder()
                    .setWorkerFactory(workerFactory)
                    .build()
            )
        }
        
        schedulePeriodicSync()
    }

    private fun schedulePeriodicSync() {
        val request = SyncWorker.createPeriodicRequest()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.UNIQUE_PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}