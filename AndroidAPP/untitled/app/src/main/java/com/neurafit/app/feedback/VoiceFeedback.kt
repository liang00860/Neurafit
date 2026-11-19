package com.neurafit.app.feedback

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import com.neurafit.app.analysis.ActionErrorType
import com.neurafit.app.analysis.CorrectionResult
import java.util.Locale

class VoiceFeedback(
    context: Context,
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    private val locale: Locale = Locale.CHINA,
) : TextToSpeech.OnInitListener {
    private val textToSpeech = TextToSpeech(context.applicationContext, this)
    private var isReady = false
    private var lastSpokenAtMillis = -cooldownMillis
    private var lastMessage: String? = null

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            isReady = false
            return
        }

        val languageResult = textToSpeech.setLanguage(locale)
        isReady = languageResult != TextToSpeech.LANG_MISSING_DATA &&
            languageResult != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun speak(result: CorrectionResult) {
        if (!isReady || !result.hasActionError()) {
            return
        }

        val message = result.message.trim()
        if (message.isEmpty()) {
            return
        }

        val now = SystemClock.elapsedRealtime()
        val isCoolingDown = now - lastSpokenAtMillis < cooldownMillis
        if (isCoolingDown && message == lastMessage) {
            return
        }
        if (isCoolingDown) {
            return
        }

        lastMessage = message
        lastSpokenAtMillis = now
        textToSpeech.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "correction-$now",
        )
    }

    fun shutdown() {
        isReady = false
        textToSpeech.stop()
        textToSpeech.shutdown()
    }

    private fun CorrectionResult.hasActionError(): Boolean =
        errors.any { it != ActionErrorType.NONE }

    private companion object {
        const val DEFAULT_COOLDOWN_MILLIS = 4_000L
    }
}
