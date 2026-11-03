package com.abcamiletto.wortschatz.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class MemoryStore : ProgressStore {
    val rows = MutableStateFlow(emptyMap<String, Progress>())
    override fun observeAll(): Flow<List<Progress>> = throw NotImplementedError()
    override suspend fun all() = rows.value.values.toList()
    override suspend fun get(wordId: String) = rows.value[wordId]
    override suspend fun upsert(progress: Progress) { rows.value += progress.wordId to progress }
    override suspend fun upsertAll(rows: List<Progress>) { this.rows.value += rows.associateBy { it.wordId } }
    override suspend fun clear() { rows.value = emptyMap() }
}

private class MemorySettings : Settings {
    val values = HashMap<String, String>()
    override fun get(key: String) = values[key]
    override fun set(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

private const val PASSWORD = "the password"

/** Behaves like the real server, including last-review-wins merging and cursors. */
private class FakeServer : SyncApi {
    val held = HashMap<String, Pair<Long, Progress>>()
    val tokens = HashSet<String>()
    var offline = false
    var acceptsCookie = false
    val requests = ArrayList<SyncRequest>()
    var duringExchange: suspend () -> Unit = {}

    override suspend fun exchange(token: String?, request: SyncRequest): SyncResponse {
        if (offline) error("offline")
        if (token !in tokens && !acceptsCookie) throw NotSignedIn()
        requests += request
        duringExchange()
        val seq = (held.values.maxOfOrNull { it.first } ?: 0) + 1
        request.rows.forEach { row ->
            val existing = held[row.wordId]?.second
            if (existing == null || row.isNewerThan(existing)) held[row.wordId] = seq to row
        }
        return SyncResponse(
            cursor = held.values.maxOfOrNull { it.first } ?: 0,
            rows = held.values.filter { it.first > request.since }.map { it.second },
        )
    }

    override suspend fun signIn(password: String): SignInResult = when {
        offline -> SignInResult.Offline
        password != PASSWORD -> SignInResult.WrongPassword
        else -> SignInResult.Success("token${tokens.size}".also(tokens::add))
    }
}

private fun progress(id: String, reviewedAt: Long = 100, reviews: Int = 1) =
    Progress(id, 3.0, 5.0, reviewedAt + 10, reviewedAt, reviews, 0)

private class Device(val server: FakeServer) {
    val store = MemoryStore()
    val settings = MemorySettings()
    val sync = ProgressSync(store, settings, server)
}

private suspend fun signedIn(server: FakeServer) = Device(server).also { it.sync.signIn(PASSWORD) }

class ProgressSyncTest {
    @Test fun aReviewReachesTheOtherDevice() = runTest {
        val server = FakeServer()
        val phone = signedIn(server)
        val tablet = signedIn(server)
        phone.sync.upsert(progress("die_zeit"))
        phone.sync.syncNow()
        tablet.sync.syncNow()
        assertEquals(progress("die_zeit"), tablet.store.get("die_zeit"))
        assertEquals(SyncStatus.Synced, tablet.sync.status.value)
    }

    @Test fun nothingSyncsBeforeSigningIn() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        assertTrue(phone.sync.needsSignIn.value)
        phone.sync.upsert(progress("die_zeit"))
        phone.sync.syncNow()
        assertTrue(server.requests.isEmpty())
    }

    @Test fun aWrongPasswordKeepsAsking() = runTest {
        val phone = Device(FakeServer())
        assertEquals(SignInResult.WrongPassword, phone.sync.signIn("nope"))
        assertTrue(phone.sync.needsSignIn.value)
    }

    @Test fun signingInSendsProgressAlreadyOnTheDevice() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        phone.store.upsert(progress("die_zeit"))
        assertTrue(phone.sync.signIn(PASSWORD) is SignInResult.Success)
        assertFalse(phone.sync.needsSignIn.value)
        assertEquals(listOf("die_zeit"), server.requests.single().rows.map { it.wordId })
    }

    @Test fun theSignInScreenStaysUntilTheFirstSyncIsDone() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        phone.store.upsert(progress("die_zeit"))
        var askingDuringSync = false
        server.duringExchange = { askingDuringSync = phone.sync.needsSignIn.value }
        phone.sync.signIn(PASSWORD)
        assertTrue(askingDuringSync)
        assertFalse(phone.sync.needsSignIn.value)
    }

    @Test fun signingInStaysAcrossRestarts() = runTest {
        val server = FakeServer()
        val phone = signedIn(server)
        assertFalse(ProgressSync(phone.store, phone.settings, server).needsSignIn.value)
    }

    @Test fun anEndedSessionAsksAgainAndKeepsUnsentWork() = runTest {
        val server = FakeServer()
        val phone = signedIn(server)
        server.tokens.clear()
        phone.sync.upsert(progress("die_zeit"))
        phone.sync.syncNow()
        assertTrue(phone.sync.needsSignIn.value)
        phone.sync.signIn(PASSWORD)
        assertEquals(listOf("die_zeit"), server.requests.last().rows.map { it.wordId })
    }

    @Test fun aBrowserNeverAsksBecauseItsCookieIsTheSession() = runTest {
        val server = FakeServer().apply { acceptsCookie = true }
        val browser = ProgressSync(MemoryStore(), MemorySettings(), server, usesToken = false)
        assertFalse(browser.needsSignIn.value)
        browser.upsert(progress("die_zeit"))
        browser.syncNow()
        assertEquals(SyncStatus.Synced, browser.status.value)
    }

    @Test fun anExpiredBrowserSessionIsReported() = runTest {
        val browser = ProgressSync(MemoryStore(), MemorySettings(), FakeServer(), usesToken = false)
        browser.syncNow()
        assertTrue(browser.needsSignIn.value)
    }

    @Test fun theLaterReviewWinsOnBothDevices() = runTest {
        val server = FakeServer()
        val phone = signedIn(server)
        val tablet = signedIn(server)
        phone.sync.upsert(progress("die_zeit", reviewedAt = 200, reviews = 2))
        tablet.sync.upsert(progress("die_zeit", reviewedAt = 100, reviews = 1))
        phone.sync.syncNow()
        tablet.sync.syncNow()
        phone.sync.syncNow()
        assertEquals(200, phone.store.get("die_zeit")?.lastReviewAt)
        assertEquals(200, tablet.store.get("die_zeit")?.lastReviewAt)
    }

    @Test fun onlyChangedWordsAreSentAfterThat() = runTest {
        val server = FakeServer()
        val phone = signedIn(server)
        phone.sync.upsert(progress("die_zeit"))
        phone.sync.syncNow()
        phone.sync.upsert(progress("der_tag"))
        phone.sync.syncNow()
        phone.sync.syncNow()
        assertEquals(
            listOf(emptyList(), listOf("die_zeit"), listOf("der_tag"), emptyList()),
            server.requests.map { r -> r.rows.map { it.wordId } },
        )
    }

    @Test fun reviewsMadeOfflineAreKeptAndSentLater() = runTest {
        val server = FakeServer()
        val phone = signedIn(server)
        server.offline = true
        phone.sync.upsert(progress("die_zeit"))
        phone.sync.syncNow()
        assertEquals(SyncStatus.Offline, phone.sync.status.value)
        // A restart must not lose the list of unsent words.
        val restarted = ProgressSync(phone.store, phone.settings, server)
        server.offline = false
        restarted.syncNow()
        assertEquals(listOf("die_zeit"), server.requests.last().rows.map { it.wordId })
        assertEquals(SyncStatus.Synced, restarted.status.value)
    }

    @Test fun aReviewDuringAnExchangeIsSentNextTime() = runTest {
        val server = FakeServer()
        val phone = signedIn(server)
        phone.sync.upsert(progress("die_zeit", reviewedAt = 100))
        phone.sync.syncNow()
        phone.sync.upsert(progress("die_zeit", reviewedAt = 200, reviews = 2))
        server.duringExchange = {
            server.duringExchange = {}
            phone.sync.upsert(progress("die_zeit", reviewedAt = 300, reviews = 3))
        }
        phone.sync.syncNow()
        assertEquals(300, phone.store.get("die_zeit")?.lastReviewAt)
        phone.sync.syncNow()
        assertEquals(300, server.requests.last().rows.single().lastReviewAt)
    }
}
