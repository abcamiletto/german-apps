package com.abcamiletto.wortschatz.speech

private external interface Recognition : JsAny {
    var lang: String
    var interimResults: Boolean
    var maxAlternatives: Int
    var onresult: ((JsAny) -> Unit)?
    var onerror: ((JsAny) -> Unit)?
    var onend: ((JsAny) -> Unit)?
    fun start()
    fun stop()
    fun abort()
}

private fun recognitionSupported(): Boolean = js("!!(window.SpeechRecognition || window.webkitSpeechRecognition)")

private fun newRecognition(): Recognition = js("new (window.SpeechRecognition || window.webkitSpeechRecognition)()")

private fun latestIsFinal(event: JsAny): Boolean = js("event.results[event.results.length - 1].isFinal")

/** The latest result's alternatives, best first, one per line. */
private fun latestTranscripts(event: JsAny): String =
    js("Array.from(event.results[event.results.length - 1]).map(function (a) { return a.transcript; }).join('\\n')")

private fun errorCode(event: JsAny): String = js("event.error")

/** German speech recognition through the Web Speech API, which Chrome, Edge and Safari provide. */
class BrowserSpeechEngine : SpeechEngine {
    private var recognition: Recognition? = null

    override val isAvailable: Boolean get() = recognitionSupported()

    override fun start(listener: SpeechListener) {
        destroy()
        var finished = false
        fun finish(report: () -> Unit) {
            if (!finished) report()
            finished = true
        }
        recognition = newRecognition().apply {
            lang = "de-DE"
            interimResults = true
            maxAlternatives = 5
            onresult = { event ->
                val candidates = latestTranscripts(event).split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                if (latestIsFinal(event)) {
                    finish { listener.onResults(candidates) }
                } else {
                    candidates.firstOrNull()?.let(listener::onPartial)
                }
            }
            onerror = { event ->
                val code = errorCode(event)
                if (code != "aborted") finish { listener.onFailure(failureFor(code)) }
            }
            // A session can end without a final result, for example when the learner stops before speaking.
            onend = { finish { listener.onFailure(SpeechFailure.NotUnderstood) } }
            start()
        }
        listener.onReady()
    }

    override fun stop() {
        recognition?.stop()
    }

    override fun destroy() {
        recognition?.run {
            onresult = null
            onerror = null
            onend = null
            abort()
        }
        recognition = null
    }

    private fun failureFor(code: String): SpeechFailure = when (code) {
        "no-speech" -> SpeechFailure.NotUnderstood
        "network" -> SpeechFailure.Offline
        "not-allowed", "service-not-allowed" -> SpeechFailure.NoPermission
        else -> SpeechFailure.Unavailable
    }
}
