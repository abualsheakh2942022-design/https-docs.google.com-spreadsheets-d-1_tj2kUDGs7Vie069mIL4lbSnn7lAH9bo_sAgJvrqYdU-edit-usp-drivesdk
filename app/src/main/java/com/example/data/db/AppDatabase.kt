package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProjectEntity::class, WorkPlanEntity::class, WorkPlanPhaseEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun workPlanDao(): WorkPlanDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `work_plans` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `location` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `linkedProjectId` INTEGER,
                        `paceName` TEXT NOT NULL,
                        `startDateMillis` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `createdAtTimestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `work_plan_phases` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `planId` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `categoryName` TEXT NOT NULL,
                        `startDay` INTEGER NOT NULL,
                        `durationDays` INTEGER NOT NULL,
                        `progressPercent` INTEGER NOT NULL,
                        `statusName` TEXT NOT NULL,
                        `quantityHint` TEXT NOT NULL,
                        `resourcesNote` TEXT NOT NULL,
                        FOREIGN KEY(`planId`) REFERENCES `work_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_work_plan_phases_planId` ON `work_plan_phases` (`planId`)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "civil_quantities_database"
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
