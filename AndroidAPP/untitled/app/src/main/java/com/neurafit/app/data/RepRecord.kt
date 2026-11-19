package com.neurafit.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rep_records",
    foreignKeys = [
        ForeignKey(
            entity = TrainingRecord::class,
            parentColumns = ["id"],
            childColumns = ["trainingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["trainingId"])],
)
data class RepRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val trainingId: Long,
    val actionName: String,
    val repIndex: Int,
    val score: Int,
    val minKneeAngle: Double?,
    val maxBackLeanAngle: Double?,
    val errorTypes: String,
    val timestamp: Long,
    val minElbowAngle: Double? = null,
    val maxBodyDescent: Double? = null,
    val minBodyLineAngle: Double? = null,
    val durationMs: Long? = null,
)
