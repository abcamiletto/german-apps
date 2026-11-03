package com.abcamiletto.wortschatz.speech

enum class SpeechFailure { NotUnderstood, Offline, Unavailable, Busy, NoPermission }

interface SpeechListener {
    fun onReady()
    fun onPartial(text: String)
    fun onLevel(level: Float)
    fun onResults(candidates: List<String>)
    fun onFailure(failure: SpeechFailure)
}

interface SpeechEngine {
    val isAvailable: Boolean
    fun start(listener: SpeechListener)
    fun stop()
    fun destroy()
}
