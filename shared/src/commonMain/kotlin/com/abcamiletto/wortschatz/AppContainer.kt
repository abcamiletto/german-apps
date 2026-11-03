package com.abcamiletto.wortschatz

import androidx.compose.runtime.Composable
import com.abcamiletto.wortschatz.audio.PronunciationPlayer
import com.abcamiletto.wortschatz.data.Dictionary
import com.abcamiletto.wortschatz.data.ProgressStore
import com.abcamiletto.wortschatz.speech.SpeechEngine

/** Asks for the microphone in whatever way the platform requires. */
interface MicrophoneAccess {
    /** Returns a function that requests access and reports the outcome to [onResult]. */
    @Composable
    fun rememberRequest(onResult: (granted: Boolean) -> Unit): () -> Unit
}

/** Everything the shared UI needs from the platform it runs on. */
class AppContainer(
    val dictionary: Dictionary,
    val progress: ProgressStore,
    val player: PronunciationPlayer,
    val microphone: MicrophoneAccess,
    /** Replaced in instrumentation tests, because an emulator has no reliable speech service. */
    var speechFactory: () -> SpeechEngine,
)
