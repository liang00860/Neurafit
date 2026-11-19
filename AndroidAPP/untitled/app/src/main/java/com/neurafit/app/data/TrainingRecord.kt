package com.neurafit.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "training_records")
data class TrainingRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val actionName: String,
    val timestamp: Long,
    val durationMs: Long? = null,
    val count: Int,
    val score: Int,
    val squatTooShallowCount: Int,
    val backLeanTooMuchCount: Int,
    val kneeValgusCount: Int,
    val avgKneeAngle: Double?,
    val avgBackLeanAngle: Double?,
    val pushUpTooShallowCount: Int = 0,
    val waistCollapseCount: Int = 0,
    val hipsTooHighCount: Int = 0,
    val pushUpTooFastCount: Int = 0,
    val avgElbowAngle: Double? = null,
    val avgBodyLineAngle: Double? = null,
    val avgBodyDescent: Double? = null,
)
