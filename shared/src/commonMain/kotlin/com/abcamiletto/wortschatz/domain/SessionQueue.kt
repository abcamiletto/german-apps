package com.abcamiletto.wortschatz.domain

/** Order of words in a session. A failed word returns a few cards later until it is answered correctly. */
class SessionQueue<T>(items: List<T>, private val retryGap: Int = 3) {
    private val queue = ArrayDeque(items)
    private val total = items.size
    private var passed = 0

    val current: T? get() = queue.firstOrNull()
    val completed: Int get() = passed
    val size: Int get() = total

    fun pass() {
        queue.removeFirstOrNull() ?: return
        passed++
    }

    fun retryLater() {
        val item = queue.removeFirstOrNull() ?: return
        queue.add(minOf(retryGap, queue.size), item)
    }
}
