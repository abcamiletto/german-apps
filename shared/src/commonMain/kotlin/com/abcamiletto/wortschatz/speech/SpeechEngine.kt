package com.abcamiletto.wortschatz.speech

enum class SpeechFailure { NotUnderstood, Offline, Unavailable, Busy, NoPermission }

interface SpeechListener {
    fun onReady()
    /** What has been heard so far, as recogniser alternatives, best first. */
    fun onPartial(candidates: List<String>)
    fun onLevel(level: Float)
    fun onResults(candidates: List<String>)
    fun onFailure(failure: SpeechFailure)
}

interface SpeechEngine {
    val isAvailable: Boolean
    fun start(listener: SpeechListener)

    /** Ends listening and asks for the result. */
    fun stop()

    /** Ends listening and drops the result. The engine stays ready for the next [start]. */
    fun cancel()

    /** Releases the engine at the end of a session. */
    fun destroy()
}
