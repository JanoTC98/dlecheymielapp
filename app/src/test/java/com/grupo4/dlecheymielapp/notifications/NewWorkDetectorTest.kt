package com.grupo4.dlecheymielapp.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewWorkDetectorTest {
    private class MemoryStore : SeenStore {
        val data = mutableMapOf<Long, Set<String>>()
        override fun load(userId: Long) = data[userId]
        override fun save(userId: Long, keys: Set<String>) { data[userId] = keys }
        override fun clear() = data.clear()
    }

    private fun item(key: String) = AlertItem(key, "Nuevo pedido", key)

    @Test
    fun theFirstCheckOnlyRecordsTheQueueAndAnnouncesNothing() {
        val detector = NewWorkDetector(MemoryStore())
        assertTrue(detector.newItems(1, listOf(item("order:1"), item("order:2"))).isEmpty())
    }

    @Test
    fun onlyWhatWasNotThereAtThePreviousCheckIsAnnounced() {
        val detector = NewWorkDetector(MemoryStore())
        detector.newItems(1, listOf(item("order:1")))
        val fresh = detector.newItems(1, listOf(item("order:1"), item("order:2")))
        assertEquals(listOf("order:2"), fresh.map { it.key })
        assertTrue(detector.newItems(1, listOf(item("order:1"), item("order:2"))).isEmpty())
    }

    @Test
    fun anOrderThatLeftAndReturnedIsAnnouncedAgain() {
        val detector = NewWorkDetector(MemoryStore())
        detector.newItems(1, listOf(item("order:1")))
        detector.newItems(1, emptyList())
        assertEquals(listOf("order:1"), detector.newItems(1, listOf(item("order:1"))).map { it.key })
    }

    @Test
    fun eachUserHasTheirOwnBaseline() {
        val detector = NewWorkDetector(MemoryStore())
        detector.newItems(1, listOf(item("order:1")))
        assertTrue(detector.newItems(2, listOf(item("order:1"))).isEmpty())
    }

    @Test
    fun clearingTheStoreMakesTheNextCheckABaselineAgain() {
        val store = MemoryStore()
        val detector = NewWorkDetector(store)
        detector.newItems(1, listOf(item("order:1")))
        store.clear()
        assertTrue(detector.newItems(1, listOf(item("order:1"), item("order:2"))).isEmpty())
    }
}
