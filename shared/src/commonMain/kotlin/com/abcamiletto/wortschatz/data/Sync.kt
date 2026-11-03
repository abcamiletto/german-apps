package com.abcamiletto.wortschatz.data

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
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

const val SERVER_URL = "https://german.abcamiletto.com"

private const val KEY_TOKEN = "sync.token"
private const val KEY_CURSOR = "sync.position"
private const val KEY_DIRTY = "sync.dirty"
private const val PUSH_DELAY_MILLIS = 1_500L
private const val POLL_MILLIS = 60_000L

/** Small values a platform keeps between launches. */
interface Settings {
    operator fun get(key: String): String?
    operator fun set(key: String, value: String?)
}

@Serializable
data class SyncRequest(val since: Long, val rows: List<Progress>)

@Serializable
data class SyncResponse(val cursor: Long, val rows: List<Progress>)

@Serializable
private data class SignInRequest(val password: String)

@Serializable
private data class SignInResponse(val token: String)

sealed interface SignInResult {
    data class Success(val token: String) : SignInResult
    data object WrongPassword : SignInResult
    data object TooManyAttempts : SignInResult
    data object Offline : SignInResult
}

/** The session has ended, or never began. */
class NotSignedIn : Exception()

interface SyncApi {
    /** [token] is null where the session lives elsewhere, as it does in the browser's cookie. */
    suspend fun exchange(token: String?, request: SyncRequest): SyncResponse
    suspend fun signIn(password: String): SignInResult
}

class HttpSyncApi(private val client: HttpClient, private val server: String = SERVER_URL) : SyncApi {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun exchange(token: String?, request: SyncRequest): SyncResponse {
        val response = client.post("$server/v1/sync") {
            token?.let(::bearerAuth)
            setBody(TextContent(json.encodeToString(request), ContentType.Application.Json))
        }
        if (response.status == HttpStatusCode.Unauthorized) throw NotSignedIn()
        check(response.status.isSuccess()) { "Sync returned ${response.status}" }
        return json.decodeFromString(response.bodyAsText())
    }

    override suspend fun signIn(password: String): SignInResult {
        val response = try {
            client.post("$server/v1/login") {
                setBody(TextContent(json.encodeToString(SignInRequest(password)), ContentType.Application.Json))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return SignInResult.Offline
        }
        return when {
            response.status.isSuccess() -> SignInResult.Success(json.decodeFromString<SignInResponse>(response.bodyAsText()).token)
            response.status == HttpStatusCode.Unauthorized -> SignInResult.WrongPassword
            response.status == HttpStatusCode.TooManyRequests -> SignInResult.TooManyAttempts
            else -> SignInResult.Offline
        }
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
    /** True where this class holds the session itself. The browser's cookie needs no token. */
    private val usesToken: Boolean = true,
) : ProgressStore by local {
    private val exchange = Mutex()
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val dirty = MutableStateFlow(settings[KEY_DIRTY]?.let { Json.decodeFromString<Set<String>>(it) }.orEmpty())
    private val mutableStatus = MutableStateFlow(SyncStatus.Waiting)
    val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()

    private val mutableNeedsSignIn = MutableStateFlow(usesToken && settings[KEY_TOKEN] == null)
    val needsSignIn: StateFlow<Boolean> = mutableNeedsSignIn.asStateFlow()

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

    suspend fun signIn(password: String): SignInResult {
        val result = api.signIn(password)
        if (result is SignInResult.Success) {
            settings[KEY_TOKEN] = result.token
            // Exchange everything, so a server restored from an older copy gets back what this device knows.
            settings[KEY_CURSOR] = null
            // The sign-in screen runs this call and goes away with the flag, so the first sync has to finish first.
            syncNow()
            if (settings[KEY_TOKEN] != null) mutableNeedsSignIn.value = false
        }
        return result
    }

    suspend fun syncNow() = exchange.withLock {
        if (mutableNeedsSignIn.value && settings[KEY_TOKEN] == null) return@withLock
        val cursor = settings[KEY_CURSOR]?.toLongOrNull()
        val rows = local.all()
        val pending = if (cursor == null) rows else dirty.value.let { ids -> rows.filter { it.wordId in ids } }
        val response = try {
            api.exchange(settings[KEY_TOKEN], SyncRequest(cursor ?: 0, pending))
        } catch (error: CancellationException) {
            throw error
        } catch (_: NotSignedIn) {
            settings[KEY_TOKEN] = null
            mutableNeedsSignIn.value = true
            return@withLock
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
