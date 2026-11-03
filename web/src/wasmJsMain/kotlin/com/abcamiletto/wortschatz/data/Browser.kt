package com.abcamiletto.wortschatz.data

import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import org.w3c.dom.url.URL
import org.w3c.fetch.Response

private const val STORAGE_KEY = "wortschatz.progress"

/** /content is published next to the page, so these paths also work when the site lives in a subfolder. */
object HostedContent : WordContent {
    override suspend fun wordsJson(): String {
        val response = window.fetch("words.json").await<Response>()
        check(response.ok) { "words.json returned ${response.status}" }
        return response.text().await<JsString>().toString()
    }

    override fun imageUri(wordId: String): String = URL("images/$wordId.webp", document.baseURI).href
}

object BrowserSettings : Settings {
    override fun get(key: String): String? = localStorage.getItem(key)

    override fun set(key: String, value: String?) {
        if (value == null) localStorage.removeItem(key) else localStorage.setItem(key, value)
    }
}

/** Keeps the schedule in this browser's local storage. About 3,000 small rows fit well within its quota. */
class BrowserProgressStore : ProgressStore {
    private val rows = MutableStateFlow(load())

    private fun load(): Map<String, Progress> {
        val saved = localStorage.getItem(STORAGE_KEY) ?: return emptyMap()
        return Json.decodeFromString<List<Progress>>(saved).associateBy { it.wordId }
    }

    private fun save(next: Map<String, Progress>) {
        localStorage.setItem(STORAGE_KEY, Json.encodeToString(next.values.toList()))
        rows.value = next
    }

    override fun observeAll(): Flow<List<Progress>> = rows.map { it.values.toList() }
    override suspend fun all(): List<Progress> = rows.value.values.toList()
    override suspend fun get(wordId: String): Progress? = rows.value[wordId]
    override suspend fun upsert(progress: Progress) = save(rows.value + (progress.wordId to progress))
    override suspend fun upsertAll(rows: List<Progress>) = save(this.rows.value + rows.associateBy { it.wordId })
    override suspend fun clear() = save(emptyMap())
}
