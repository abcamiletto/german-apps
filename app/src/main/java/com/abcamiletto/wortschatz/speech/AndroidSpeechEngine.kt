package com.abcamiletto.wortschatz.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * German speech recognition through the phone's recognition service. Google's offline German model is used when the
 * phone has it: it answers faster and catches far more short words said on their own.
 */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {
    private var recognizer: SpeechRecognizer? = null
    private var offline = false

    init {
        if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            val probe = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            probe.checkRecognitionSupport(
                intent(),
                context.mainExecutor,
                object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        offline = GERMAN in support.installedOnDeviceLanguages
                        probe.destroy()
                    }
                    override fun onError(error: Int) = probe.destroy()
                },
            )
        }
    }

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun start(listener: SpeechListener) {
        // One recogniser serves the whole session: starting a new one right after destroying the last makes it
        // report ERROR_SERVER_DISCONNECTED within milliseconds.
        val current = recognizer ?: newRecognizer().also { recognizer = it }
        current.apply {
            cancel()
            setRecognitionListener(Bridge(listener))
            startListening(intent())
        }
    }

    private fun newRecognizer(): SpeechRecognizer =
        if (offline) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)

    // The web search model expects a few words rather than dictation, which is what an answer is.
    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, GERMAN)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, GERMAN)
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, GERMAN)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.apply {
            setRecognitionListener(Bridge(Ignored))
            cancel()
        }
    }

    override fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private object Ignored : SpeechListener {
        override fun onReady() = Unit
        override fun onPartial(candidates: List<String>) = Unit
        override fun onLevel(level: Float) = Unit
        override fun onResults(candidates: List<String>) = Unit
        override fun onFailure(failure: SpeechFailure) = Unit
    }

    private inner class Bridge(private val listener: SpeechListener) : RecognitionListener {
        /** The latest partial alternatives, since a recogniser sometimes ends with an empty final result. */
        private var heard = emptyList<String>()

        override fun onReadyForSpeech(params: Bundle?) = listener.onReady()
        override fun onRmsChanged(rmsdB: Float) = listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        override fun onPartialResults(partialResults: Bundle?) {
            val candidates = partialResults.candidates()
            if (candidates.isEmpty()) return
            heard = candidates
            listener.onPartial(candidates)
        }
        override fun onResults(results: Bundle?) = listener.onResults(results.candidates().ifEmpty { heard })
        override fun onError(error: Int) {
            Log.w("WortschatzSpeech", "Recogniser error $error")
            // The connection to the service is gone, so the next attempt needs a new recogniser.
            if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED) destroy()
            listener.onFailure(failureFor(error))
        }
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun Bundle?.candidates(): List<String> =
            this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
    }

    private companion object {
        const val GERMAN = "de-DE"
    }
}

/** Only lasting problems send the learner to the keyboard. A momentary one just asks them to try again. */
internal fun failureFor(error: Int): SpeechFailure = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechFailure.NotUnderstood
    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> SpeechFailure.Offline
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_SERVER_DISCONNECTED, SpeechRecognizer.ERROR_CLIENT -> SpeechFailure.Busy
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechFailure.NoPermission
    else -> SpeechFailure.Unavailable
}
