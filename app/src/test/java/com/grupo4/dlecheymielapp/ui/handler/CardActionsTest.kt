package com.grupo4.dlecheymielapp.ui.handler

import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.data.fulfillment.FieldCapabilities
import com.grupo4.dlecheymielapp.data.fulfillment.FieldGateway
import com.grupo4.dlecheymielapp.data.fulfillment.FieldIncident
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerOrderWork
import com.grupo4.dlecheymielapp.data.fulfillment.HandoffResult
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.PayAtDeliveryInfo
import com.grupo4.dlecheymielapp.data.fulfillment.PaymentStatus
import com.grupo4.dlecheymielapp.data.fulfillment.IncidentStatus
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeGateway(override val capabilities: FieldCapabilities = FieldCapabilities()) : FieldGateway {
    var handoffCalls = 0
    var incidentCalls = 0
    var advanceCalls = 0
    var completeCalls = 0
    var handoff: suspend (String) -> HandoffResult = { HandoffResult(1, OrderStatus.DELIVERED) }
    var advanceError: Exception? = null
    /** When set, the POST suspends until this completes, to simulate a slow request. */
    var advanceGate: CompletableDeferred<Unit>? = null

    override suspend fun order(id: Long): HandlerOrderWork = error("not used")
    override suspend fun incidents(id: Long): List<FieldIncident> = emptyList()
    override suspend fun handoff(id: Long, code: String): HandoffResult { handoffCalls++; return handoff(code) }
    override suspend fun reportIncident(id: Long, description: String): FieldIncident {
        incidentCalls++
        return FieldIncident(1, id, null, description, IncidentStatus.OPEN, "2026-10-26T10:00:00Z")
    }
    var linkCalls = 0
    var link: suspend () -> String = { "https://pago.example/abc" }
    override suspend fun paymentLink(id: Long): String { linkCalls++; return link() }
    override suspend fun advance(id: Long, expected: OrderStatus) { advanceCalls++; advanceGate?.await(); advanceError?.let { throw it } }
    override suspend fun complete(id: Long) { completeCalls++ }
}

class CardActionsTest {
    private var changes = 0
    private var unauthorized = 0
    /** When set, the list reload suspends until this completes, to simulate a slow reload. */
    private var reloadGate: CompletableDeferred<Unit>? = null

    private fun TestScope.actions(gateway: FieldGateway) = CardActions(this, gateway, { unauthorized++ }, { changes++; reloadGate?.await() })

    @Test
    fun aReloadThatThrowsStillUnlocksTheCard() = runTest {
        val gateway = FakeGateway(FieldCapabilities(canAdvance = true))
        val actions = CardActions(this, gateway, { unauthorized++ }, { throw IllegalStateException("reload failed") })

        actions.advance(3, OrderStatus.CONFIRMED, "DLM-3")
        advanceUntilIdle()

        assertEquals(1, gateway.advanceCalls)
        assertFalse("a failed reload must not leave the card locked", actions.state.value.card(3).busy)
    }

    @Test
    fun validCodeDeliversClearsTheFieldAndReloadsTheList() = runTest {
        val gateway = FakeGateway()
        val actions = actions(gateway)

        actions.onCode(1, "k7m2q9")
        actions.validate(1)
        advanceUntilIdle()

        val card = actions.state.value.card(1)
        // The card may leave the queue once the order closes, so the confirmation lives in the list's notice.
        assertEquals("Pedido entregado.", actions.state.value.notice)
        assertNull(card.message)
        assertEquals("", card.code)
        assertFalse(card.busy)
        assertEquals(1, changes)
    }

    @Test
    fun codeIsCleanedAndCapitalised() = runTest {
        val actions = actions(FakeGateway())

        actions.onCode(1, " k7-m2 q9 ")

        assertEquals("K7M2Q9", actions.state.value.card(1).code)
    }

    @Test
    fun wrongCodeShowsTheErrorAndKeepsWhatWasTyped() = runTest {
        val gateway = FakeGateway().apply { handoff = { throw ApiException(400, "HANDOFF_CODE_INVALID", "x") } }
        val actions = actions(gateway)

        actions.onCode(1, "AAAAAA")
        actions.validate(1)
        advanceUntilIdle()

        val card = actions.state.value.card(1)
        assertNotNull(card.error)
        assertEquals("AAAAAA", card.code)
        assertFalse(card.busy)
        assertEquals(0, changes)
    }

    @Test
    fun codeAcceptedButPaymentPendingSaysSo() = runTest {
        val gateway = FakeGateway().apply { handoff = { HandoffResult(1, OrderStatus.IN_TRANSIT, awaitingPayment = true) } }
        val actions = actions(gateway)

        actions.onCode(1, "K7M2Q9")
        actions.validate(1)
        advanceUntilIdle()

        assertTrue(actions.state.value.card(1).message!!.contains("pago"))
    }

    @Test
    fun emptyCodeAndSecondTapWhileSendingDoNothing() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gateway = FakeGateway().apply { handoff = { gate.await(); HandoffResult(1, OrderStatus.DELIVERED) } }
        val actions = actions(gateway)

        actions.validate(1)
        runCurrent()
        assertEquals(0, gateway.handoffCalls)

        actions.onCode(1, "K7M2Q9")
        actions.validate(1)
        actions.validate(1)
        runCurrent()
        assertEquals(1, gateway.handoffCalls)
        assertTrue(actions.state.value.card(1).busy)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(actions.state.value.card(1).busy)
    }

    @Test
    fun cardsDoNotShareTheirState() = runTest {
        val actions = actions(FakeGateway())

        actions.onCode(1, "AAAAAA")
        actions.onCode(2, "BBBBBB")

        assertEquals("AAAAAA", actions.state.value.card(1).code)
        assertEquals("BBBBBB", actions.state.value.card(2).code)
    }

    @Test
    fun anExpiredSessionIsReported() = runTest {
        val gateway = FakeGateway().apply { handoff = { throw ApiException(401, "AUTH_REQUIRED", "x") } }
        val actions = actions(gateway)

        actions.onCode(1, "K7M2Q9")
        actions.validate(1)
        advanceUntilIdle()

        assertEquals(1, unauthorized)
    }

    @Test
    fun incidentIsSentOnceEvenIfTheButtonIsTappedTwice() = runTest {
        val gateway = FakeGateway()
        val actions = actions(gateway)

        actions.openIncident(5, "DLM-0005")
        actions.onIncidentText("  Cliente no contesta  ")
        actions.submitIncident()
        actions.submitIncident()
        advanceUntilIdle()

        assertEquals(1, gateway.incidentCalls)
        assertNull(actions.state.value.incident)
        assertNotNull(actions.state.value.notice)
        assertEquals(1, changes)
    }

    @Test
    fun aRegisteredIncidentClearsTheWrongCodeErrorOfThatCard() = runTest {
        val gateway = FakeGateway().apply { handoff = { throw ApiException(400, "HANDOFF_CODE_INVALID", "x") } }
        val actions = actions(gateway)
        actions.onCode(7, "AAAAAA")
        actions.validate(7)
        advanceUntilIdle()
        assertNotNull(actions.state.value.card(7).error)

        actions.openIncident(7, "DLM-7")
        actions.onIncidentText("Cliente ausente")
        actions.submitIncident()
        advanceUntilIdle()

        assertNull("the order is on hold now: the old code error no longer applies", actions.state.value.card(7).error)
    }

    @Test
    fun emptyIncidentIsNotSent() = runTest {
        val gateway = FakeGateway()
        val actions = actions(gateway)

        actions.openIncident(5, "DLM-0005")
        actions.onIncidentText("   ")
        actions.submitIncident()
        advanceUntilIdle()

        assertEquals(0, gateway.incidentCalls)
        assertNotNull(actions.state.value.incident)
    }

    @Test
    fun staleStepReloadsInsteadOfRetrying() = runTest {
        val gateway = FakeGateway(FieldCapabilities(canAdvance = true)).apply { advanceError = ApiException(409, "ORDER_STATE_CHANGED", "x") }
        val actions = actions(gateway)

        actions.advance(3, OrderStatus.CONFIRMED, "DLM-3")
        advanceUntilIdle()

        assertEquals(1, gateway.advanceCalls)
        assertNotNull(actions.state.value.card(3).error)
        assertEquals(1, changes)
    }

    @Test
    fun aSecondTapWhileThePostAndTheReloadAreInFlightSendsNothing() = runTest {
        val gateway = FakeGateway(FieldCapabilities(canAdvance = true)).apply { advanceGate = CompletableDeferred() }
        reloadGate = CompletableDeferred()
        val actions = actions(gateway)

        actions.advance(3, OrderStatus.CONFIRMED, "DLM-3")
        runCurrent()
        actions.advance(3, OrderStatus.CONFIRMED, "DLM-3")
        runCurrent()
        assertEquals(1, gateway.advanceCalls)

        gateway.advanceGate!!.complete(Unit)
        runCurrent()
        // The POST has returned but the list is still reloading: the card must stay locked.
        assertTrue(actions.state.value.card(3).busy)
        actions.advance(3, OrderStatus.CONFIRMED, "DLM-3")
        runCurrent()
        assertEquals(1, gateway.advanceCalls)

        reloadGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(actions.state.value.card(3).busy)
        assertEquals(1, gateway.advanceCalls)
        assertEquals(1, changes)
    }

    @Test
    fun aConflictOrTimeoutReloadsBeforeTheCardUnlocks() = runTest {
        val gateway = FakeGateway(FieldCapabilities(canAdvance = true)).apply { advanceError = ApiException(409, "ORDER_STATE_CHANGED", "x") }
        reloadGate = CompletableDeferred()
        val actions = actions(gateway)

        actions.advance(3, OrderStatus.CONFIRMED, "DLM-3")
        runCurrent()
        assertEquals(1, changes)
        assertTrue("the reload must finish before another attempt", actions.state.value.card(3).busy)

        reloadGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(actions.state.value.card(3).busy)
    }

    @Test
    fun anIoExceptionAlsoReloadsBeforeTheCardUnlocks() = runTest {
        val gateway = FakeGateway(FieldCapabilities(canAdvance = true)).apply { advanceError = IOException("timeout") }
        reloadGate = CompletableDeferred()
        val actions = actions(gateway)

        actions.advance(3, OrderStatus.CONFIRMED, "DLM-3")
        runCurrent()
        assertEquals(1, changes)
        assertTrue(actions.state.value.card(3).busy)

        reloadGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(actions.state.value.card(3).busy)
    }

    @Test
    fun aSingleAdvanceFromConfirmedStillWorks() = runTest {
        val gateway = FakeGateway(FieldCapabilities(canAdvance = true))
        val actions = actions(gateway)

        actions.advance(3, OrderStatus.CONFIRMED, "DLM-3")
        advanceUntilIdle()

        assertEquals(1, gateway.advanceCalls)
        assertEquals("El pedido pasó a En preparación.", actions.state.value.card(3).message)
        assertFalse(actions.state.value.card(3).busy)
        assertEquals(1, changes)
    }

    @Test
    fun enCaminoNeedsConfirmationBeforeTheRequestIsSent() = runTest {
        val gateway = FakeGateway(FieldCapabilities(canAdvance = true))
        val actions = actions(gateway)

        actions.advance(3, OrderStatus.PREPARING, "DLM-3")
        advanceUntilIdle()
        assertEquals(0, gateway.advanceCalls)
        assertEquals(PendingAdvance(3, "DLM-3", OrderStatus.PREPARING, OrderStatus.IN_TRANSIT), actions.state.value.advanceFor)

        actions.cancelAdvance()
        assertNull(actions.state.value.advanceFor)
        assertEquals(0, gateway.advanceCalls)

        actions.advance(3, OrderStatus.PREPARING, "DLM-3")
        actions.confirmAdvance()
        advanceUntilIdle()
        assertEquals(1, gateway.advanceCalls)
        assertNull(actions.state.value.advanceFor)
        assertEquals("El pedido pasó a En camino.", actions.state.value.card(3).message)
    }

    @Test
    fun terminalStatusesAreTheOnesThatNeedConfirmation() {
        assertTrue(OrderStatus.IN_TRANSIT.needsAdvanceConfirmation())
        assertTrue(OrderStatus.DELIVERED.needsAdvanceConfirmation())
        assertTrue(OrderStatus.CANCELLED.needsAdvanceConfirmation())
        assertFalse(OrderStatus.PREPARING.needsAdvanceConfirmation())
        assertFalse(OrderStatus.READY_FOR_PICKUP.needsAdvanceConfirmation())
    }

    @Test
    fun onlyTheRolesThatCanAdvanceOrCompleteDo() = runTest {
        val gateway = FakeGateway()
        val actions = actions(gateway)

        actions.advance(3, OrderStatus.CONFIRMED, "DLM-3")
        actions.askComplete(3)
        actions.complete()
        advanceUntilIdle()

        assertEquals(0, gateway.advanceCalls)
        assertEquals(0, gateway.completeCalls)
    }

    @Test
    fun operatorCompletesAfterConfirming() = runTest {
        val gateway = FakeGateway(FieldCapabilities(canComplete = true))
        val actions = actions(gateway)

        actions.askComplete(8)
        assertEquals(8L, actions.state.value.completeFor)
        actions.complete()
        advanceUntilIdle()

        assertEquals(1, gateway.completeCalls)
        assertNull(actions.state.value.completeFor)
        assertEquals("Pedido entregado.", actions.state.value.notice)
        assertNull(actions.state.value.card(8).message)
    }

    private fun payable(linkAvailable: Boolean = true) = CardOrder(
        id = 4, code = "DLM-4", status = OrderStatus.IN_TRANSIT, mode = FulfillmentMode.DELIVERY, name = "Rosa", place = null, phone = "987654321",
        payAtDelivery = PayAtDeliveryInfo(PaymentStatus.pending, codeVerified = false, linkAvailable = linkAvailable),
    )

    @Test
    fun openingTheCobroPanelRequestsTheLinkAndKeepsItOnlyWhileOpen() = runTest {
        val gateway = FakeGateway()
        val actions = actions(gateway)

        actions.openPayment(payable())
        advanceUntilIdle()
        assertEquals("https://pago.example/abc", actions.state.value.payment?.url)
        assertEquals("987654321", actions.state.value.payment?.phone)

        actions.closePayment()
        assertNull("the link must be forgotten when the panel closes", actions.state.value.payment)
    }

    @Test
    fun beforeTheSlotStartsNoLinkIsRequested() = runTest {
        val gateway = FakeGateway()
        val actions = actions(gateway)

        actions.openPayment(payable(linkAvailable = false))
        advanceUntilIdle()

        assertEquals(0, gateway.linkCalls)
        assertNull(actions.state.value.payment?.url)
    }

    @Test
    fun aLinkArrivingAfterTheFlapWasClosedIsDropped() = runTest {
        val gate = CompletableDeferred<Unit>()
        val gateway = FakeGateway().apply { link = { gate.await(); "https://pago.example/late" } }
        val actions = actions(gateway)

        actions.openPayment(payable())
        runCurrent()
        actions.closePayment()
        gate.complete(Unit)
        advanceUntilIdle()

        assertNull(actions.state.value.payment)
    }

    @Test
    fun linkErrorsUseTheWebWording() = runTest {
        val gateway = FakeGateway().apply { link = { throw ApiException(409, "PAYMENT_ALREADY_PAID", "x") } }
        val actions = actions(gateway)

        actions.openPayment(payable())
        advanceUntilIdle()

        assertEquals("El pedido ya está pagado.", actions.state.value.payment?.error)
    }

    @Test
    fun alreadyPaidRefreshClosesThePanelAndReloadsTheList() = runTest {
        val actions = actions(FakeGateway())

        actions.openPayment(payable())
        advanceUntilIdle()
        actions.refreshPayment()
        advanceUntilIdle()

        assertNull(actions.state.value.payment)
        assertEquals(1, changes)
    }

    @Test
    fun nextStepFollowsThePreparationFlow() {
        assertEquals(OrderStatus.PREPARING, nextStepOf(OrderStatus.CONFIRMED)?.first)
        assertEquals(OrderStatus.IN_TRANSIT, nextStepOf(OrderStatus.PREPARING)?.first)
        assertNull(nextStepOf(OrderStatus.IN_TRANSIT))
        assertNull(nextStepOf(OrderStatus.DELIVERED))
    }
}
