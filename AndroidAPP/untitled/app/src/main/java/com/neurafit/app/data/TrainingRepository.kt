package com.neurafit.app.data

import android.content.Context
import com.neurafit.app.analysis.PushUpThreshold
import com.neurafit.app.analysis.SquatThreshold
import com.neurafit.app.learning.ThresholdUpdater

class TrainingRepository(
    private val trainingDao: TrainingDao,
) {
    suspend fun createTrainingSession(
        actionName: String,
        timestamp: Long = System.currentTimeMillis(),
    ): Long =
        trainingDao.insertTrainingRecord(
            TrainingRecord(
                actionName = actionName,
                timestamp = timestamp,
                count = 0,
                score = 0,
                squatTooShallowCount = 0,
                backLeanTooMuchCount = 0,
                kneeValgusCount = 0,
                avgKneeAngle = null,
                avgBackLeanAngle = null,
            ),
        )

    suspend fun saveRepRecord(record: RepRecord): Long =
        trainingDao.insertRepRecord(record)

    suspend fun getTrainingRecords(): List<TrainingRecord> =
        trainingDao.getTrainingRecords()

    suspend fun getRecentTrainingRecords(actionName: String, limit: Int = 7): List<TrainingRecord> =
        trainingDao.getRecentTrainingRecords(actionName, limit)

    suspend fun getRepRecords(trainingId: Long): List<RepRecord> =
        trainingDao.getRepRecords(trainingId)

    suspend fun getSquatThreshold(actionName: String): SquatThreshold =
        (trainingDao.getUserActionThreshold(actionName) ?: UserActionThreshold.defaultFor(actionName))
            .toSquatThreshold()

    suspend fun getPushUpThreshold(actionName: String): PushUpThreshold =
        (trainingDao.getUserActionThreshold(actionName) ?: UserActionThreshold.defaultFor(actionName))
            .toPushUpThreshold()

    suspend fun updateThresholdAfterTraining(actionName: String): UserActionThreshold {
        val currentThreshold = trainingDao.getUserActionThreshold(actionName)
        val recentRecords = trainingDao.getRecentTrainingRecords(actionName, limit = 7)
        val updatedThreshold = ThresholdUpdater.update(
            actionName = actionName,
            records = recentRecords,
            currentThreshold = currentThreshold,
        )
        trainingDao.upsertUserActionThreshold(updatedThreshold)
        return updatedThreshold
    }

    suspend fun finishTrainingSession(record: TrainingRecord) {
        trainingDao.updateTrainingRecord(record)
    }

    companion object {
        fun create(context: Context): TrainingRepository =
            TrainingRepository(AppDatabase.getInstance(context).trainingDao())
    }
}
