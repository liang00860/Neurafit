package com.neurafit.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TrainingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrainingRecord(record: TrainingRecord): Long

    @Update
    suspend fun updateTrainingRecord(record: TrainingRecord)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRepRecord(record: RepRecord): Long

    @Query("SELECT * FROM training_records ORDER BY timestamp DESC")
    suspend fun getTrainingRecords(): List<TrainingRecord>

    @Query("SELECT * FROM training_records WHERE actionName = :actionName ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTrainingRecords(actionName: String, limit: Int): List<TrainingRecord>

    @Query("SELECT * FROM rep_records WHERE trainingId = :trainingId ORDER BY repIndex ASC")
    suspend fun getRepRecords(trainingId: Long): List<RepRecord>

    @Query("SELECT * FROM user_action_thresholds WHERE actionName = :actionName LIMIT 1")
    suspend fun getUserActionThreshold(actionName: String): UserActionThreshold?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserActionThreshold(threshold: UserActionThreshold)
}
