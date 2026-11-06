package com.abcamiletto.wortschatz.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/** German speech recognition through the recognition service installed on the phone. */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {
    private var recognizer: SpeechRecognizer? = null

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun start(listener: SpeechListener) {
        // One recogniser serves the whole session: starting a new one right after destroying the last makes it
        // report ERROR_SERVER_DISCONNECTED within milliseconds.
        val current = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also { recognizer = it }
        current.apply {
            cancel()
            setRecognitionListener(Bridge(listener))
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, GERMAN)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, GERMAN)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, GERMAN)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                },
            )
        }
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
        override fun onPartial(text: String) = Unit
        override fun onLevel(level: Float) = Unit
        override fun onResults(candidates: List<String>) = Unit
        override fun onFailure(failure: SpeechFailure) = Unit
    }

    private inner class Bridge(private val listener: SpeechListener) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = listener.onReady()
        override fun onRmsChanged(rmsdB: Float) = listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults.candidates().firstOrNull()?.let(listener::onPartial)
        }
        override fun onResults(results: Bundle?) = listener.onResults(results.candidates())
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
