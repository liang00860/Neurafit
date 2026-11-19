package com.neurafit.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TrainingRecord::class,
        RepRecord::class,
        UserActionThreshold::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trainingDao(): TrainingDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DatabaseConfig.DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_action_thresholds` (
                        `actionName` TEXT NOT NULL,
                        `maxKneeAngleAtBottom` REAL NOT NULL,
                        `maxBackLeanAngle` REAL NOT NULL,
                        `minKneeDistance` REAL NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`actionName`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `training_records` ADD COLUMN `pushUpTooShallowCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `training_records` ADD COLUMN `waistCollapseCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `training_records` ADD COLUMN `hipsTooHighCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `training_records` ADD COLUMN `pushUpTooFastCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `training_records` ADD COLUMN `avgElbowAngle` REAL")
                db.execSQL("ALTER TABLE `training_records` ADD COLUMN `avgBodyLineAngle` REAL")
                db.execSQL("ALTER TABLE `training_records` ADD COLUMN `avgBodyDescent` REAL")
                db.execSQL("ALTER TABLE `rep_records` ADD COLUMN `minElbowAngle` REAL")
                db.execSQL("ALTER TABLE `rep_records` ADD COLUMN `maxBodyDescent` REAL")
                db.execSQL("ALTER TABLE `rep_records` ADD COLUMN `minBodyLineAngle` REAL")
                db.execSQL("ALTER TABLE `rep_records` ADD COLUMN `durationMs` INTEGER")
                db.execSQL("ALTER TABLE `user_action_thresholds` ADD COLUMN `pushUpMaxElbowAngleAtBottom` REAL NOT NULL DEFAULT 105.0")
                db.execSQL("ALTER TABLE `user_action_thresholds` ADD COLUMN `pushUpMinBodyLineAngle` REAL NOT NULL DEFAULT 160.0")
                db.execSQL("ALTER TABLE `user_action_thresholds` ADD COLUMN `pushUpMinDescent` REAL NOT NULL DEFAULT 0.06")
                db.execSQL("ALTER TABLE `user_action_thresholds` ADD COLUMN `pushUpMinRepDurationMs` INTEGER NOT NULL DEFAULT 900")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `training_records` ADD COLUMN `durationMs` INTEGER")
                db.execSQL(
                    """
                    UPDATE `training_records`
                    SET `durationMs` = (
                        SELECT COALESCE(SUM(`durationMs`), 0)
                        FROM `rep_records`
                        WHERE `trainingId` = `training_records`.`id`
                    )
                    WHERE `durationMs` IS NULL
                    """.trimIndent(),
                )
            }
        }
    }
}
