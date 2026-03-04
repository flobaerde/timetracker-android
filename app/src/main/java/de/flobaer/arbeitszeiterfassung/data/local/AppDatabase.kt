package de.flobaer.arbeitszeiterfassung.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [LocationEntity::class, WorkTimeEntity::class, PauseEntity::class], version = 7, exportSchema = false)
@TypeConverters(SyncStatusConverter::class, InstantConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao
    abstract fun workTimeDao(): WorkTimeDao
    abstract fun pauseDao(): PauseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database"
                )
                // If migrations fail, Room will clear the database and rebuild it.
                // This is acceptable since the data is synced from the server.
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}