package com.abcamiletto.wortschatz.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** German speech recognition through the recognition service installed on the phone. */
class AndroidSpeechEngine(private val context: Context) : SpeechEngine {
    private var recognizer: SpeechRecognizer? = null

    override val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    override fun start(listener: SpeechListener) {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
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

    override fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private class Bridge(private val listener: SpeechListener) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = listener.onReady()
        override fun onRmsChanged(rmsdB: Float) = listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults.candidates().firstOrNull()?.let(listener::onPartial)
        }
        override fun onResults(results: Bundle?) = listener.onResults(results.candidates())
        override fun onError(error: Int) = listener.onFailure(failureFor(error))
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun Bundle?.candidates(): List<String> =
            this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }

        private fun failureFor(error: Int): SpeechFailure = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechFailure.NotUnderstood
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> SpeechFailure.Offline
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechFailure.Busy
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechFailure.NoPermission
            else -> SpeechFailure.Unavailable
        }
    }

    private companion object {
        const val GERMAN = "de-DE"
    }
}
