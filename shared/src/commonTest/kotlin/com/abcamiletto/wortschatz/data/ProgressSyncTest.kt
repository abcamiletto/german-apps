package com.abcamiletto.wortschatz.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

/** Behaves like the real server, including last-review-wins merging and cursors. */
private class FakeServer : SyncApi {
    val byCode = HashMap<String, MutableMap<String, Pair<Long, Progress>>>()
    var offline = false
    val requests = ArrayList<SyncRequest>()
    var duringExchange: suspend () -> Unit = {}

    override suspend fun exchange(code: String, request: SyncRequest): SyncResponse {
        if (offline) error("offline")
        requests += request
        duringExchange()
        val held = byCode.getOrPut(code) { HashMap() }
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
}

private fun progress(id: String, reviewedAt: Long = 100, reviews: Int = 1) =
    Progress(id, 3.0, 5.0, reviewedAt + 10, reviewedAt, reviews, 0)

private class Device(val server: FakeServer) {
    val store = MemoryStore()
    val settings = MemorySettings()
    val sync = ProgressSync(store, settings, server)
}

class ProgressSyncTest {
    @Test fun aReviewReachesTheOtherDevice() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        val laptop = Device(server)
        phone.sync.upsert(progress("die_zeit"))
        phone.sync.syncNow()
        assertTrue(laptop.sync.link(phone.sync.code))
        assertEquals(progress("die_zeit"), laptop.store.get("die_zeit"))
        assertEquals(SyncStatus.Synced, laptop.sync.status.value)
    }

    @Test fun linkingKeepsTheProgressOfBothDevices() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        val laptop = Device(server)
        phone.sync.upsert(progress("die_zeit"))
        phone.sync.syncNow()
        laptop.sync.upsert(progress("der_tag"))
        laptop.sync.syncNow()
        laptop.sync.link(phone.sync.code)
        phone.sync.syncNow()
        assertEquals(setOf("die_zeit", "der_tag"), phone.store.rows.value.keys)
        assertEquals(setOf("die_zeit", "der_tag"), laptop.store.rows.value.keys)
    }

    @Test fun theLaterReviewWinsOnBothDevices() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        val laptop = Device(server)
        laptop.sync.link(phone.sync.code)
        phone.sync.upsert(progress("die_zeit", reviewedAt = 200, reviews = 2))
        laptop.sync.upsert(progress("die_zeit", reviewedAt = 100, reviews = 1))
        phone.sync.syncNow()
        laptop.sync.syncNow()
        phone.sync.syncNow()
        assertEquals(200, phone.store.get("die_zeit")?.lastReviewAt)
        assertEquals(200, laptop.store.get("die_zeit")?.lastReviewAt)
    }

    @Test fun progressMadeBeforeTheUpdateIsSentOnTheFirstSync() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        phone.store.upsert(progress("die_zeit"))
        phone.sync.syncNow()
        assertEquals(listOf("die_zeit"), server.requests.single().rows.map { it.wordId })
    }

    @Test fun onlyChangedWordsAreSentAfterThat() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        phone.sync.upsert(progress("die_zeit"))
        phone.sync.syncNow()
        phone.sync.upsert(progress("der_tag"))
        phone.sync.syncNow()
        phone.sync.syncNow()
        assertEquals(listOf(listOf("die_zeit"), listOf("der_tag"), emptyList()), server.requests.map { r -> r.rows.map { it.wordId } })
    }

    @Test fun reviewsMadeOfflineAreKeptAndSentLater() = runTest {
        val server = FakeServer()
        val phone = Device(server)
        phone.sync.syncNow()
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
        val phone = Device(server)
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

    @Test fun anInvalidCodeChangesNothing() = runTest {
        val phone = Device(FakeServer())
        val code = phone.sync.code
        assertFalse(phone.sync.link("too short"))
        assertEquals(code, phone.sync.code)
    }

    @Test fun generatedCodesAreValidAndDifferent() {
        val codes = List(50) { SyncCode.generate() }
        assertEquals(50, codes.toSet().size)
        codes.forEach { assertEquals(it, SyncCode.parse(it)) }
    }

    @Test fun typedCodesAreForgiving() {
        assertEquals("7KQ2M9XD3TFA8WPN", SyncCode.parse(" 7kq2-m9xd 3tfa-8wpn "))
        assertEquals("0110ABCD3TFA8WPN", SyncCode.parse("OIl0-abcd-3tfa-8wpn"))
        assertNull(SyncCode.parse("7KQ2-M9XD-3TFA-8WP"))
        assertNull(SyncCode.parse("UUUU-UUUU-UUUU-UUUU"))
        assertNotNull(SyncCode.display("7KQ2M9XD3TFA8WPN").takeIf { it == "7KQ2-M9XD-3TFA-8WPN" })
    }
}
