package com.abcamiletto.wortschatz.data

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

const val SYNC_URL = "https://german.abcamiletto.com/v1/sync"

private const val KEY_CODE = "sync.code"
private const val KEY_CURSOR = "sync.cursor"
private const val KEY_DIRTY = "sync.dirty"
private const val PUSH_DELAY_MILLIS = 1_500L
private const val POLL_MILLIS = 60_000L

/** Small values a platform keeps between launches. */
interface Settings {
    operator fun get(key: String): String?
    operator fun set(key: String, value: String?)
}

/** A sync code links devices: 80 random bits, in an alphabet with no look-alike letters so it can be typed by hand. */
object SyncCode {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val LENGTH = 16

    fun generate(): String {
        // Bytes 6 and 8 of a UUID hold its version and variant. Every other byte is random.
        val random = List(2) { Uuid.random().toByteArray().filterIndexed { index, _ -> index != 6 && index != 8 } }.flatten()
        return random.take(LENGTH).joinToString("") { ALPHABET[it.toInt() and 31].toString() }
    }

    /** Accepts what a person might type: any case, any separators, and O, I or L for 0, 1 and 1. */
    fun parse(text: String): String? {
        val code = text.uppercase().filter { it.isLetterOrDigit() }
            .replace('O', '0').replace('I', '1').replace('L', '1')
        return code.takeIf { it.length == LENGTH && it.all(ALPHABET::contains) }
    }

    fun display(code: String): String = code.chunked(4).joinToString("-")
}

@Serializable
data class SyncRequest(val since: Long, val rows: List<Progress>)

@Serializable
data class SyncResponse(val cursor: Long, val rows: List<Progress>)

fun interface SyncApi {
    suspend fun exchange(code: String, request: SyncRequest): SyncResponse
}

class HttpSyncApi(private val client: HttpClient, private val url: String = SYNC_URL) : SyncApi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun exchange(code: String, request: SyncRequest): SyncResponse {
        val response = client.post(url) {
            bearerAuth(code)
            setBody(TextContent(json.encodeToString(request), ContentType.Application.Json))
        }
        check(response.status.isSuccess()) { "Sync returned ${response.status}" }
        return json.decodeFromString(response.bodyAsText())
    }
}

enum class SyncStatus { Waiting, Synced, Offline }

/** The later review wins. That makes merging safe in any order, and safe to repeat. */
fun Progress.isNewerThan(other: Progress): Boolean =
    lastReviewAt > other.lastReviewAt || (lastReviewAt == other.lastReviewAt && reviews > other.reviews)

/**
 * The progress store the app uses: everything stays on the device, and reviews also reach the server when it can.
 * Words changed since the last successful exchange are remembered, so nothing is lost while offline.
 */
class ProgressSync(
    private val local: ProgressStore,
    private val settings: Settings,
    private val api: SyncApi,
) : ProgressStore by local {
    private val exchange = Mutex()
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val dirty = MutableStateFlow(settings[KEY_DIRTY]?.let { Json.decodeFromString<Set<String>>(it) }.orEmpty())
    private val mutableStatus = MutableStateFlow(SyncStatus.Waiting)
    val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()

    val code: String
        get() = settings[KEY_CODE] ?: SyncCode.generate().also { settings[KEY_CODE] = it }

    override suspend fun upsert(progress: Progress) {
        local.upsert(progress)
        markDirty(setOf(progress.wordId))
    }

    override suspend fun upsertAll(rows: List<Progress>) {
        local.upsertAll(rows)
        markDirty(rows.mapTo(HashSet()) { it.wordId })
    }

    override suspend fun clear() {
        local.clear()
        dirty.value = emptySet()
        settings[KEY_DIRTY] = null
        settings[KEY_CURSOR] = null
    }

    private fun markDirty(ids: Set<String>) {
        dirty.update { it + ids }
        settings[KEY_DIRTY] = Json.encodeToString(dirty.value)
        changes.trySend(Unit)
    }

    /** Joins the progress behind [input] to this device's. Returns false if it isn't a valid code. */
    suspend fun link(input: String): Boolean {
        val parsed = SyncCode.parse(input) ?: return false
        exchange.withLock {
            settings[KEY_CODE] = parsed
            // Without a cursor, the next exchange sends all of this device's words and receives all of the other's.
            settings[KEY_CURSOR] = null
            mutableStatus.value = SyncStatus.Waiting
        }
        syncNow()
        return true
    }

    suspend fun syncNow() = exchange.withLock {
        val cursor = settings[KEY_CURSOR]?.toLongOrNull()
        val rows = local.all()
        val pending = if (cursor == null) rows else dirty.value.let { ids -> rows.filter { it.wordId in ids } }
        val response = try {
            api.exchange(code, SyncRequest(cursor ?: 0, pending))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutableStatus.value = SyncStatus.Offline
            return@withLock
        }
        // A review may have landed during the exchange, so compare with the store as it is now.
        val current = local.all().associateBy { it.wordId }
        val newer = response.rows.filter { row -> current[row.wordId]?.let(row::isNewerThan) ?: true }
        if (newer.isNotEmpty()) local.upsertAll(newer)
        val sent = pending.filter { current[it.wordId] == it }.mapTo(HashSet()) { it.wordId }
        dirty.update { it - sent }
        settings[KEY_DIRTY] = Json.encodeToString(dirty.value)
        settings[KEY_CURSOR] = response.cursor.toString()
        mutableStatus.value = SyncStatus.Synced
    }

    /** Syncs for as long as the app is on screen: soon after each review, and every so often for other devices' reviews. */
    suspend fun run() = coroutineScope {
        launch {
            for (change in changes) {
                delay(PUSH_DELAY_MILLIS)
                syncNow()
            }
        }
        while (isActive) {
            syncNow()
            delay(POLL_MILLIS)
        }
    }
}
