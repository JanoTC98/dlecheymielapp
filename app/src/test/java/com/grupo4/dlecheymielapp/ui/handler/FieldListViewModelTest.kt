package com.grupo4.dlecheymielapp.ui.handler

import com.grupo4.dlecheymielapp.data.fulfillment.FieldCapabilities
import com.grupo4.dlecheymielapp.data.fulfillment.FieldGateway
import com.grupo4.dlecheymielapp.data.fulfillment.FieldIncident
import com.grupo4.dlecheymielapp.data.fulfillment.FieldSchedule
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerOrderWork
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** Gateway used only for the card actions; the list tests never call it. */
private object UnusedGateway : FieldGateway {
    override val capabilities: FieldCapabilities = FieldCapabilities()
    override suspend fun order(id: Long): HandlerOrderWork = error("not used")
    override suspend fun incidents(id: Long): List<FieldIncident> = emptyList()
    override suspend fun handoff(id: Long, code: String) = error("not used")
    override suspend fun reportIncident(id: Long, description: String): FieldIncident = error("not used")
    override suspend fun paymentLink(id: Long): String = error("not used")
}

private fun order(id: Long) = HandlerOrderWork(id = id, schedule = FieldSchedule("2026-10-30", "10:00", "10:30"), customerName = "Cliente $id")

class FieldListViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun initialLoadFillsTheList() = runTest(dispatcher) {
        val vm = FieldListViewModel(UnusedGateway, { listOf(order(1), order(2)) }, { emptyList() }, {}, now = { 0L })

        assertEquals(listOf(1L, 2L), vm.state.value.orders.map { it.id })
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun aCardStaysLockedUntilTheListReloadAfterItsAdvanceFinishes() = runTest(dispatcher) {
        val advancing = object : FieldGateway by UnusedGateway {
            override val capabilities = FieldCapabilities(canAdvance = true)
            var advanceCalls = 0
            override suspend fun advance(id: Long, expected: com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus) { advanceCalls++ }
        }
        var loads = 0
        val reloadGate = CompletableDeferred<Unit>()
        val vm = FieldListViewModel(advancing, { if (loads++ > 0) reloadGate.await(); listOf(order(3)) }, { emptyList() }, {}, now = { 0L })

        vm.cardActions.advance(3, com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus.CONFIRMED, "DLM-3")
        assertTrue("the list is still reloading after the advance, so the card must stay locked", vm.cardActions.state.value.card(3).busy)
        vm.cardActions.advance(3, com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus.CONFIRMED, "DLM-3")
        assertEquals(1, advancing.advanceCalls)

        reloadGate.complete(Unit)
        assertFalse(vm.cardActions.state.value.card(3).busy)
    }

    @Test
    fun aSecondCardRestartingTheReloadDoesNotUnlockTheFirstCardEarly() = runTest(dispatcher) {
        val gates = mapOf(1L to CompletableDeferred<Unit>(), 2L to CompletableDeferred<Unit>())
        val advancing = object : FieldGateway by UnusedGateway {
            override val capabilities = FieldCapabilities(canAdvance = true)
            override suspend fun advance(id: Long, expected: com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus) { gates.getValue(id).await() }
        }
        val reloads = mutableListOf<CompletableDeferred<Unit>>()
        var loads = 0
        val vm = FieldListViewModel(advancing, {
            if (loads++ > 0) CompletableDeferred<Unit>().also { reloads += it }.await()
            listOf(order(1), order(2))
        }, { emptyList() }, {}, now = { 0L })

        vm.cardActions.advance(1, com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus.CONFIRMED, "DLM-1")
        vm.cardActions.advance(2, com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus.CONFIRMED, "DLM-2")
        gates.getValue(1L).complete(Unit)
        // Card 2's reload restarts the list load and cancels card 1's: card 1 must wait for the load that replaced it.
        gates.getValue(2L).complete(Unit)
        assertTrue("no reload has finished yet, so card 1 must stay locked", vm.cardActions.state.value.card(1).busy)
        assertTrue(vm.cardActions.state.value.card(2).busy)

        reloads.last().complete(Unit)
        assertFalse(vm.cardActions.state.value.card(1).busy)
        assertFalse(vm.cardActions.state.value.card(2).busy)
    }

    @Test
    fun overlappingRefreshesStartOnlyOneLoad() = runTest(dispatcher) {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val vm = FieldListViewModel(UnusedGateway, { calls++; gate.await(); listOf(order(1)) }, { emptyList() }, {}, now = { 0L })

        vm.refresh()
        vm.refresh()
        assertEquals("a load is in flight, so refreshes must not start another", 1, calls)

        gate.complete(Unit)
        vm.refresh()
        assertEquals("once the previous load finished, a refresh starts a new one", 2, calls)
    }

    @Test
    fun failedRefreshKeepsPreviousDataAndSetsStale() = runTest(dispatcher) {
        var fail = false
        val vm = FieldListViewModel(UnusedGateway, { if (fail) throw IOException("sin red") else listOf(order(1)) }, { emptyList() }, {}, now = { 1_000L })
        assertFalse(vm.state.value.stale)

        fail = true
        vm.refresh()

        assertEquals(listOf(1L), vm.state.value.orders.map { it.id })
        assertTrue(vm.state.value.stale)
        assertNotNull(vm.state.value.error)
        assertEquals(1_000L, vm.state.value.lastUpdated)
    }

    @Test
    fun lastUpdatedIsSetOnSuccessAndStaleClearsOnRecovery() = runTest(dispatcher) {
        var now = 1_000L
        var fail = false
        val vm = FieldListViewModel(UnusedGateway, { if (fail) throw IOException("sin red") else listOf(order(1)) }, { emptyList() }, {}, now = { now })
        assertEquals(1_000L, vm.state.value.lastUpdated)

        now = 5_000L
        fail = true
        vm.refresh()
        assertTrue(vm.state.value.stale)
        assertEquals("a failed refresh keeps the last successful time", 1_000L, vm.state.value.lastUpdated)

        fail = false
        vm.refresh()
        assertEquals(5_000L, vm.state.value.lastUpdated)
        assertFalse(vm.state.value.stale)
        assertNull(vm.state.value.error)
    }
}
