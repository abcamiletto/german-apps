package com.abcamiletto.wortschatz.audio

/** Plays a word's pronunciation clip, replacing any clip that is still playing. */
interface PronunciationPlayer {
    fun play(wordId: String)
    fun stop()
}
