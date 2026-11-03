package com.abcamiletto.wortschatz.audio

import org.w3c.dom.Audio

// Browsers reject play() when no gesture has unlocked audio yet. The learner can still tap Listen, so ignore that.
private fun playQuietly(audio: Audio): Unit = js("{ audio.play().catch(function () {}); }")

class BrowserPronunciationPlayer : PronunciationPlayer {
    private var current: Audio? = null

    override fun play(wordId: String) {
        stop()
        current = Audio("audio/$wordId.ogg").also(::playQuietly)
    }

    override fun stop() {
        current?.pause()
        current = null
    }
}
