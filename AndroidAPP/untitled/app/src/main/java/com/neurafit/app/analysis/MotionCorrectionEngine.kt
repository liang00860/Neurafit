package com.neurafit.app.analysis

import android.content.Context
import com.neurafit.app.data.AppDatabase
import com.neurafit.app.data.PushUpTrainingSessionStats
import com.neurafit.app.data.TrainingDao
import com.neurafit.app.data.TrainingRecord
import com.neurafit.app.data.TrainingSessionStats
import com.neurafit.app.data.UserActionThreshold
import com.neurafit.app.feedback.VoiceFeedback
import com.neurafit.app.learning.ThresholdUpdater
import com.neurafit.app.learning.TrainingAdviceGenerator
import com.neurafit.app.learning.UserActionProfile
import com.neurafit.app.learning.UserProfileBuilder
import com.neurafit.app.pose.PoseOverlayResult
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MotionCorrectionState(
    val poseOverlayResult: PoseOverlayResult? = null,
    val bodyFeatures: BodyFeatures? = null,
    val pushUpFeatures: PushUpFeatures? = null,
    val actionName: String = WorkoutActions.SQUAT,
    val squatState: SquatCounterSnapshot = SquatCounter().snapshot,
    val pushUpState: PushUpCounterSnapshot = PushUpCounter().snapshot,
    val correctionResult: CorrectionResult = SquatAnalyzer.analyze(features = null),
    val squatThreshold: SquatThreshold = SquatThreshold(),
    val pushUpThreshold: PushUpThreshold = PushUpThreshold(),
    val trainingId: Long? = null,
    val actionProfile: UserActionProfile? = null,
    val nextTrainingAdvice: String? = null,
    val sessionStartedAtMs: Long? = null,
    val isTrainingStarted: Boolean = false,
    val isFinishing: Boolean = false,
    val errorMessage: String? = null,
)

class MotionCorrectionEngine(
    context: Context,
    private val trainingDao: TrainingDao,
    private val actionName: String = DEFAULT_ACTION_NAME,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val voiceFeedback = VoiceFeedback(appContext)
    private val squatCounter = SquatCounter()
    private val pushUpCounter = PushUpCounter()
    private val trainingStats = TrainingSessionStats(actionName = actionName)
    private val pushUpTrainingStats = PushUpTrainingSessionStats(actionName = actionName)
    private val trainingIdDeferred = CompletableDeferred<Long>()
    private val pendingRepSaveJobs = Collections.synchronizedSet(mutableSetOf<Job>())

    private val _state = MutableStateFlow(
        MotionCorrectionState(
            actionName = actionName,
            squatState = squatCounter.snapshot,
            pushUpState = pushUpCounter.snapshot,
        ),
    )
    val state: StateFlow<MotionCorrectionState> = _state.asStateFlow()

    private var startRequested = false

    fun startTraining() {
        if (startRequested) return
        startRequested = true

        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val thresholdRecord = (
                        trainingDao.getUserActionThreshold(actionName)
                            ?: UserActionThreshold.defaultFor(actionName)
                        )
                    val trainingId = trainingDao.insertTrainingRecord(
                        TrainingRecord(
                            actionName = actionName,
                            timestamp = System.currentTimeMillis(),
                            count = 0,
                            score = 0,
                            squatTooShallowCount = 0,
                            backLeanTooMuchCount = 0,
                            kneeValgusCount = 0,
                            avgKneeAngle = null,
                            avgBackLeanAngle = null,
                        ),
                    )
                    StartResult(
                        squatThreshold = thresholdRecord.toSquatThreshold(),
                        pushUpThreshold = thresholdRecord.toPushUpThreshold(),
                        trainingId = trainingId,
                    )
                }
            }.onSuccess { startResult ->
                if (!trainingIdDeferred.isCompleted) {
                    trainingIdDeferred.complete(startResult.trainingId)
                }
                _state.update {
                    it.copy(
                        squatThreshold = startResult.squatThreshold,
                        pushUpThreshold = startResult.pushUpThreshold,
                        trainingId = startResult.trainingId,
                        sessionStartedAtMs = System.currentTimeMillis(),
                        isTrainingStarted = true,
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                if (!trainingIdDeferred.isCompleted) {
                    trainingIdDeferred.completeExceptionally(throwable)
                }
                _state.update {
                    it.copy(
                        isTrainingStarted = false,
                        errorMessage = throwable.localizedMessage ?: "Unable to start training session.",
                    )
                }
            }
        }
    }

    fun processPoseResult(result: PoseOverlayResult?) {
        if (_state.value.isFinishing) return

        val features = result?.let { PoseFeatureExtractor.extract(it.landmarks) }
        if (actionName == WorkoutActions.PUSH_UP) {
            processPushUpPoseResult(result, features)
            return
        }

        processSquatPoseResult(result, features)
    }

    private fun processSquatPoseResult(
        result: PoseOverlayResult?,
        features: BodyFeatures?,
    ) {
        val nextSquatState = squatCounter.update(features)
        val correction = SquatAnalyzer.analyze(
            features = features,
            squatState = nextSquatState,
            threshold = _state.value.squatThreshold,
        )

        trainingStats.onFrame(
            features = features,
            correctionResult = correction,
            squatState = nextSquatState,
        )
        saveRepIfCompleted(nextSquatState, correction)
        voiceFeedback.speak(correction)

        _state.update {
            it.copy(
                poseOverlayResult = result,
                bodyFeatures = features,
                pushUpFeatures = null,
                squatState = nextSquatState,
                correctionResult = correction,
                errorMessage = null,
            )
        }
    }

    private fun processPushUpPoseResult(
        result: PoseOverlayResult?,
        features: BodyFeatures?,
    ) {
        val pushUpFeatures = PushUpFeatureExtractor.extract(
            features = features,
            referenceShoulderY = pushUpCounter.referenceShoulderY,
        )
        val nextPushUpState = pushUpCounter.update(pushUpFeatures)
        val correction = PushUpAnalyzer.analyze(
            features = pushUpFeatures,
            pushUpState = nextPushUpState,
            threshold = _state.value.pushUpThreshold,
        )

        pushUpTrainingStats.onFrame(
            features = pushUpFeatures,
            correctionResult = correction,
            pushUpState = nextPushUpState,
        )
        savePushUpRepIfCompleted(nextPushUpState, correction)
        voiceFeedback.speak(correction)

        _state.update {
            it.copy(
                poseOverlayResult = result,
                bodyFeatures = features,
                pushUpFeatures = pushUpFeatures,
                pushUpState = nextPushUpState,
                correctionResult = correction,
                errorMessage = null,
            )
        }
    }

    fun finishTraining(onFinished: () -> Unit) {
        if (_state.value.isFinishing) return

        _state.update { it.copy(isFinishing = true) }
        scope.launch {
            try {
                finishTrainingInternal()
            } finally {
                onFinished()
            }
        }
    }

    fun close() {
        voiceFeedback.shutdown()
        scope.cancel()
    }

    private fun saveRepIfCompleted(
        squatState: SquatCounterSnapshot,
        correction: CorrectionResult,
    ) {
        val trainingId = _state.value.trainingId ?: return
        val repRecord = trainingStats.createRepRecordIfCompleted(
            trainingId = trainingId,
            squatState = squatState,
            correctionResult = correction,
        ) ?: return

        val saveJob = scope.launch(Dispatchers.IO) {
            trainingDao.insertRepRecord(repRecord)
        }
        pendingRepSaveJobs += saveJob
        saveJob.invokeOnCompletion {
            pendingRepSaveJobs -= saveJob
        }
    }

    private fun savePushUpRepIfCompleted(
        pushUpState: PushUpCounterSnapshot,
        correction: CorrectionResult,
    ) {
        val trainingId = _state.value.trainingId ?: return
        val repRecord = pushUpTrainingStats.createRepRecordIfCompleted(
            trainingId = trainingId,
            pushUpState = pushUpState,
            correctionResult = correction,
        ) ?: return

        val saveJob = scope.launch(Dispatchers.IO) {
            trainingDao.insertRepRecord(repRecord)
        }
        pendingRepSaveJobs += saveJob
        saveJob.invokeOnCompletion {
            pendingRepSaveJobs -= saveJob
        }
    }

    private suspend fun finishTrainingInternal() {
        val trainingId = _state.value.trainingId
            ?: runCatching { trainingIdDeferred.await() }.getOrNull()
            ?: return
        val currentState = _state.value
        val finishedAt = System.currentTimeMillis()
        val sessionStartedAt = if (actionName == WorkoutActions.PUSH_UP) {
            pushUpTrainingStats.startedAtMs
        } else {
            trainingStats.startedAtMs
        }
        val sessionDurationMs = (finishedAt - sessionStartedAt).coerceAtLeast(0L)
        val trainingRecord = if (actionName == WorkoutActions.PUSH_UP) {
            pushUpTrainingStats.buildTrainingRecord(
                trainingId = trainingId,
                pushUpState = currentState.pushUpState,
                fallbackScore = currentState.correctionResult.score,
                durationMs = sessionDurationMs,
            )
        } else {
            trainingStats.buildTrainingRecord(
                trainingId = trainingId,
                squatState = currentState.squatState,
                fallbackScore = currentState.correctionResult.score,
                durationMs = sessionDurationMs,
            )
        }

        pendingRepSaveJobs.toList().joinAll()

        val finishResult = withContext(Dispatchers.IO) {
            trainingDao.updateTrainingRecord(trainingRecord)

            val recentRecords = trainingDao.getRecentTrainingRecords(actionName, RECENT_RECORD_LIMIT)
            val currentThreshold = trainingDao.getUserActionThreshold(actionName)
            val updatedThreshold = ThresholdUpdater.update(
                actionName = actionName,
                records = recentRecords,
                currentThreshold = currentThreshold,
            )
            trainingDao.upsertUserActionThreshold(updatedThreshold)

            val profile = UserProfileBuilder.build(
                records = trainingDao.getTrainingRecords(),
                actionName = actionName,
            )
            FinishResult(
                squatThreshold = updatedThreshold.toSquatThreshold(),
                pushUpThreshold = updatedThreshold.toPushUpThreshold(),
                profile = profile,
                advice = profile?.let(TrainingAdviceGenerator::generate),
            )
        }

        _state.update {
            it.copy(
                squatThreshold = finishResult.squatThreshold,
                pushUpThreshold = finishResult.pushUpThreshold,
                actionProfile = finishResult.profile,
                nextTrainingAdvice = finishResult.advice,
                sessionStartedAtMs = null,
                isTrainingStarted = false,
                errorMessage = null,
            )
        }
    }

    private data class StartResult(
        val squatThreshold: SquatThreshold,
        val pushUpThreshold: PushUpThreshold,
        val trainingId: Long,
    )

    private data class FinishResult(
        val squatThreshold: SquatThreshold,
        val pushUpThreshold: PushUpThreshold,
        val profile: UserActionProfile?,
        val advice: String?,
    )

    companion object {
        const val DEFAULT_ACTION_NAME = WorkoutActions.SQUAT
        private const val RECENT_RECORD_LIMIT = 7

        fun create(
            context: Context,
            actionName: String = DEFAULT_ACTION_NAME,
        ): MotionCorrectionEngine =
            MotionCorrectionEngine(
                context = context.applicationContext,
                trainingDao = AppDatabase.getInstance(context).trainingDao(),
                actionName = actionName,
            )
    }

}
