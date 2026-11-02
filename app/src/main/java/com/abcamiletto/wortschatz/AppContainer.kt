package com.abcamiletto.wortschatz

import android.app.Application
import android.content.Context
import com.abcamiletto.wortschatz.audio.PronunciationPlayer
import com.abcamiletto.wortschatz.data.AppDatabase
import com.abcamiletto.wortschatz.data.Dictionary
import com.abcamiletto.wortschatz.speech.AndroidSpeechEngine
import com.abcamiletto.wortschatz.speech.SpeechEngine

class AppContainer(context: Context) {
    val dictionary = Dictionary(context.assets)
    val database = AppDatabase.create(context)
    val player = PronunciationPlayer(context)

    /** Replaced in instrumentation tests, because an emulator has no reliable speech service. */
    var speechFactory: () -> SpeechEngine = { AndroidSpeechEngine(context) }
}

class WortschatzApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
