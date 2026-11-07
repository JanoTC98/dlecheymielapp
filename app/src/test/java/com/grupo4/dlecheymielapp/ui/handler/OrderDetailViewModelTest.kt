package com.grupo4.dlecheymielapp.ui.handler

import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.data.fulfillment.FieldCapabilities
import com.grupo4.dlecheymielapp.data.fulfillment.FieldGateway
import com.grupo4.dlecheymielapp.data.fulfillment.FieldIncident
import com.grupo4.dlecheymielapp.data.fulfillment.FieldSchedule
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerOrderWork
import com.grupo4.dlecheymielapp.data.fulfillment.HandoffResult
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class DetailGateway : FieldGateway {
    override val capabilities = FieldCapabilities(canAdvance = true)
    var status = OrderStatus.CONFIRMED
    var advanceCalls = 0
    var advanceError: Exception? = null
    /** Slows the POST, and the reload that follows it, to simulate a slow network. */
    var advanceGate: CompletableDeferred<Unit>? = null
    var orderGate: CompletableDeferred<Unit>? = null

    override suspend fun order(id: Long): HandlerOrderWork {
        orderGate?.await()
        return HandlerOrderWork(
            id = id, publicCode = "DLM-1", status = status, mode = FulfillmentMode.DELIVERY,
            schedule = FieldSchedule("2026-10-30", "10:00", "11:00"), customerName = "Rosa",
        )
    }

    var incidentList: List<FieldIncident> = emptyList()
    var incidentsError: Exception? = null

    override suspend fun incidents(id: Long): List<FieldIncident> {
        incidentsError?.let { throw it }
        return incidentList
    }
    override suspend fun handoff(id: Long, code: String): HandoffResult = HandoffResult(1, OrderStatus.DELIVERED)
    override suspend fun reportIncident(id: Long, description: String): FieldIncident = error("not used")
    override suspend fun paymentLink(id: Long): String = error("not used")

    override suspend fun advance(id: Long, expected: OrderStatus) {
        advanceCalls++
        advanceGate?.await()
        advanceError?.let { throw it }
        status = if (expected == OrderStatus.CONFIRMED) OrderStatus.PREPARING else OrderStatus.IN_TRANSIT
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val incident = FieldIncident(id = 7, orderId = 1, description = "Cliente ausente", recordedAt = "2026-11-06T00:42:00")

    @Test
    fun reopeningDetailReloadsNotesAddedFromTheQueue() = runTest(dispatcher) {
        val gateway = DetailGateway().apply { incidentList = listOf(incident) }
        val vm = OrderDetailViewModel(1, gateway) {}
        vm.refreshOnOpen()
        advanceUntilIdle()
        assertTrue(vm.state.value.incidents.single().notes.isEmpty())
        val updated = incident.copy(notes = listOf(com.grupo4.dlecheymielapp.data.fulfillment.IncidentNote(
            1, authorName = "Demo", text = "Seguimiento", recordedAt = "2026-11-06T01:00:00")))
        gateway.incidentList = listOf(updated)
        vm.refreshOnOpen()
        advanceUntilIdle()
        assertEquals("Seguimiento", vm.state.value.incidents.single().notes.single().text)
    }

    @Test
    fun anIncidentListThatCannotBeReadIsReportedInsteadOfShownAsEmpty() = runTest(dispatcher) {
        val gateway = DetailGateway().apply { incidentList = listOf(incident) }
        val vm = OrderDetailViewModel(1, gateway) {}
        advanceUntilIdle()
        assertEquals(listOf(incident), vm.state.value.incidents)
        assertFalse(vm.state.value.incidentsLoadFailed)

        gateway.incidentsError = java.io.IOException("offline")
        vm.load()
        advanceUntilIdle()

        // The last list that was read stays on screen and the failure is flagged; the order itself still loads.
        assertEquals(listOf(incident), vm.state.value.incidents)
        assertTrue(vm.state.value.incidentsLoadFailed)
        assertEquals(null, vm.state.value.loadError)

        gateway.incidentsError = null
        vm.load()
        advanceUntilIdle()
        assertFalse(vm.state.value.incidentsLoadFailed)
    }

    @Test
    fun aSuccessfullyReadEmptyIncidentListIsNotAFailure() = runTest(dispatcher) {
        val vm = OrderDetailViewModel(1, DetailGateway()) {}
        advanceUntilIdle()
        assertTrue(vm.state.value.incidents.isEmpty())
        assertFalse(vm.state.value.incidentsLoadFailed)
    }

    @Test
    fun aNormalSingleAdvanceStillWorks() = runTest(dispatcher) {
        val gateway = DetailGateway()
        val vm = OrderDetailViewModel(1, gateway) {}
        advanceUntilIdle()

        vm.advance()
        advanceUntilIdle()

        assertEquals(1, gateway.advanceCalls)
        assertEquals("El pedido pasó a En preparación.", vm.state.value.actionMessage)
        assertEquals(OrderStatus.PREPARING, vm.state.value.order?.status)
        assertFalse(vm.state.value.actionBusy)
    }

    @Test
    fun aSecondTapWhileThePostOrTheReloadIsInFlightSendsNothing() = runTest(dispatcher) {
        val gateway = DetailGateway()
        val vm = OrderDetailViewModel(1, gateway) {}
        advanceUntilIdle()
        gateway.advanceGate = CompletableDeferred()
        gateway.orderGate = CompletableDeferred()

        vm.advance()
        runCurrent()
        vm.advance()
        runCurrent()
        assertEquals(1, gateway.advanceCalls)

        gateway.advanceGate!!.complete(Unit)
        runCurrent()
        // The POST has returned but the order is still reloading: the action must stay locked.
        assertTrue(vm.state.value.actionBusy)
        vm.advance()
        runCurrent()
        assertEquals(1, gateway.advanceCalls)

        gateway.orderGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.actionBusy)
        assertEquals(1, gateway.advanceCalls)
    }

    @Test
    fun aConflictReloadsTheOrderBeforeTheActionUnlocks() = runTest(dispatcher) {
        val gateway = DetailGateway().apply { advanceError = ApiException(409, "ORDER_STATE_CHANGED", "x") }
        val vm = OrderDetailViewModel(1, gateway) {}
        advanceUntilIdle()
        gateway.orderGate = CompletableDeferred()

        vm.advance()
        runCurrent()
        assertTrue("the reload must finish before another attempt", vm.state.value.actionBusy)

        gateway.orderGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.actionBusy)
        assertEquals(1, gateway.advanceCalls)
    }

    @Test
    fun enCaminoNeedsConfirmationBeforeTheRequestIsSent() = runTest(dispatcher) {
        val gateway = DetailGateway().apply { status = OrderStatus.PREPARING }
        val vm = OrderDetailViewModel(1, gateway) {}
        advanceUntilIdle()

        vm.advance()
        advanceUntilIdle()
        assertEquals(0, gateway.advanceCalls)
        assertTrue(vm.state.value.confirmAdvance)

        vm.askAdvance(false)
        assertFalse(vm.state.value.confirmAdvance)
        assertEquals(0, gateway.advanceCalls)

        vm.advance()
        vm.advance()
        advanceUntilIdle()
        assertEquals(1, gateway.advanceCalls)
        assertEquals("El pedido pasó a En camino.", vm.state.value.actionMessage)
    }
}
