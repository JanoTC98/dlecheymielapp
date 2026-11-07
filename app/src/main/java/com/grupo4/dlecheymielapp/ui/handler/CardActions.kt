package com.grupo4.dlecheymielapp.ui.handler

import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.data.fulfillment.FieldCapabilities
import com.grupo4.dlecheymielapp.data.fulfillment.FieldGateway
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.data.fulfillment.PayAtDeliveryInfo
import com.grupo4.dlecheymielapp.ui.common.toActionMessage
import com.grupo4.dlecheymielapp.ui.common.toHandoffMessage
import com.grupo4.dlecheymielapp.ui.common.toIncidentMessage
import com.grupo4.dlecheymielapp.ui.common.toPaymentLinkMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** What one order card is doing: the code being typed and the result of the last action on it. */
data class CardState(
    val code: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

/** The incident being written from a card; one at a time. */
data class IncidentDraft(
    val orderId: Long,
    val label: String,
    val text: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val hasOpenIncident: Boolean = false,
    val checking: Boolean = false,
)

/** The payment QR panel opened from a card. [url] is a credential: memory only, dropped when the panel closes. */
data class PaymentDraft(
    val orderId: Long,
    val label: String,
    val phone: String?,
    val info: PayAtDeliveryInfo,
    val url: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

/** A status change waiting for the confirmation dialog. [label] is the order code the employee sees. */
data class PendingAdvance(val id: Long, val label: String, val from: OrderStatus, val target: OrderStatus)

data class CardActionsState(
    val cards: Map<Long, CardState> = emptyMap(),
    val incident: IncidentDraft? = null,
    val payment: PaymentDraft? = null,
    /** Order waiting for the "mark as delivered" confirmation (Operador, paid in advance). */
    val completeFor: Long? = null,
    /** Status change waiting for its confirmation (En camino or a terminal status). */
    val advanceFor: PendingAdvance? = null,
    val notice: String? = null,
) {
    fun card(id: Long): CardState = cards[id] ?: CardState()
}

/** The step an employee can take now and the status it leads to; null when closing or waiting on someone else. */
fun nextStepOf(status: OrderStatus): Pair<OrderStatus, String>? = when (status) {
    OrderStatus.CONFIRMED -> OrderStatus.PREPARING to "Marcar En preparación"
    OrderStatus.PREPARING -> OrderStatus.IN_TRANSIT to "Marcar En camino"
    else -> null
}

/** Statuses that need an explicit confirmation before the request is sent: En camino and every terminal status. */
fun OrderStatus.needsAdvanceConfirmation(): Boolean = this == OrderStatus.IN_TRANSIT || isTerminal

/**
 * Timeouts, dropped connections and conflicts leave the real state unknown: the order must be reloaded
 * before anybody tries again.
 */
fun Exception.leavesStateUnknown(): Boolean =
    this is IOException || (this is ApiException && (status == 409 || code == "ORDER_STATE_CHANGED"))

/** What the screens can ask of the cards. Previews and tests use [NoCardCallbacks]. */
interface CardCallbacks {
    val capabilities: FieldCapabilities

    fun onCode(id: Long, value: String)
    fun validate(id: Long)
    fun advance(id: Long, from: OrderStatus, label: String)
    fun confirmAdvance()
    fun cancelAdvance()
    fun askComplete(id: Long?)
    fun complete()
    fun openIncident(id: Long, label: String, hasOpenIncident: Boolean = false)
    fun onIncidentText(value: String)
    fun closeIncident()
    fun submitIncident()
    fun dismissNotice()
    fun openPayment(order: CardOrder)
    fun generatePayment()
    fun closePayment()
    /** "Ya pagó: actualizar": close the panel and reload the list so the card shows the new payment state. */
    fun refreshPayment()
}

object NoCardCallbacks : CardCallbacks {
    override val capabilities = FieldCapabilities()
    override fun onCode(id: Long, value: String) = Unit
    override fun validate(id: Long) = Unit
    override fun advance(id: Long, from: OrderStatus, label: String) = Unit
    override fun confirmAdvance() = Unit
    override fun cancelAdvance() = Unit
    override fun askComplete(id: Long?) = Unit
    override fun complete() = Unit
    override fun openIncident(id: Long, label: String, hasOpenIncident: Boolean) = Unit
    override fun onIncidentText(value: String) = Unit
    override fun closeIncident() = Unit
    override fun submitIncident() = Unit
    override fun dismissNotice() = Unit
    override fun openPayment(order: CardOrder) = Unit
    override fun generatePayment() = Unit
    override fun closePayment() = Unit
    override fun refreshPayment() = Unit
}

/** Said when an order closes from a list; it is the list's notice because the closed card may leave the queue. */
const val DELIVERED_NOTICE = "Pedido entregado."

/** Said once after a valid code on an order paid at delivery: the order closes only when the payment is approved. */
const val AWAITING_PAYMENT_MESSAGE = "Código validado. El pedido se cerrará cuando el pago contra entrega esté aprobado."

/**
 * Acts on orders straight from a list card, with the same rules as the order's own screen: one request at a time per
 * order, a stale state is reloaded instead of retried, and each incident or follow-up note is sent once.
 * A card stays locked from the first tap until [onChanged] has finished reloading the list, so a second tap cannot
 * act on the status the server is about to replace. [onChanged] must suspend until the reload is done.
 */
class CardActions(
    private val scope: CoroutineScope,
    private val gateway: FieldGateway,
    private val onUnauthorized: () -> Unit,
    private val onChanged: suspend () -> Unit,
) : CardCallbacks {
    private val _state = MutableStateFlow(CardActionsState())
    val state: StateFlow<CardActionsState> = _state.asStateFlow()

    override val capabilities: FieldCapabilities get() = gateway.capabilities

    private fun edit(id: Long, change: (CardState) -> CardState) =
        _state.update { it.copy(cards = it.cards + (id to change(it.card(id)))) }

    /** Releases the card only after the list has been reloaded when [reload] is set; a reload that fails never keeps it locked. */
    private suspend fun settle(id: Long, reload: Boolean) {
        try {
            if (reload) onChanged()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The list reports its own load error; the card only has to become usable again.
        } finally {
            edit(id) { it.copy(busy = false) }
        }
    }

    override fun onCode(id: Long, value: String) =
        edit(id) { it.copy(code = value.filter { c -> c.isLetterOrDigit() }.take(12).uppercase(), error = null) }

    override fun validate(id: Long) {
        val card = _state.value.card(id)
        val code = card.code.trim()
        if (card.busy || code.isEmpty()) return
        _state.update { it.copy(notice = null) }
        edit(id) { it.copy(busy = true, error = null, message = null) }
        scope.launch {
            var reload = true
            try {
                val result = gateway.handoff(id, code)
                if (result.awaitingPayment) {
                    edit(id) { it.copy(code = "", message = AWAITING_PAYMENT_MESSAGE) }
                } else {
                    // The order is closed and may leave the queue: its card would take the message away with it.
                    edit(id) { it.copy(code = "") }
                    _state.update { it.copy(notice = DELIVERED_NOTICE) }
                }
            } catch (e: Exception) {
                handleAuth(e)
                edit(id) { it.copy(error = e.toHandoffMessage()) }
                // Delivered by someone else or moved under us: show what the order really is.
                reload = e.leavesStateUnknown() || (e is ApiException && (e.code == "HANDOFF_ALREADY_COMPLETED" || e.code == "ORDER_STATE_CHANGED"))
            }
            settle(id, reload)
        }
    }

    override fun advance(id: Long, from: OrderStatus, label: String) {
        val step = nextStepOf(from) ?: return
        if (!capabilities.canAdvance || _state.value.card(id).busy) return
        val pending = PendingAdvance(id, label, from, step.first)
        if (step.first.needsAdvanceConfirmation()) {
            _state.update { it.copy(advanceFor = pending, notice = null) }
        } else {
            send(pending)
        }
    }

    override fun confirmAdvance() {
        val pending = _state.value.advanceFor ?: return
        _state.update { it.copy(advanceFor = null) }
        if (capabilities.canAdvance && !_state.value.card(pending.id).busy) send(pending)
    }

    override fun cancelAdvance() = _state.update { it.copy(advanceFor = null) }

    private fun send(pending: PendingAdvance) {
        val id = pending.id
        _state.update { it.copy(notice = null) }
        edit(id) { it.copy(busy = true, error = null, message = null) }
        scope.launch {
            var reload = true
            try {
                gateway.advance(id, pending.from)
                edit(id) { it.copy(message = "El pedido pasó a ${pending.target.label}.") }
            } catch (e: Exception) {
                handleAuth(e)
                edit(id) { it.copy(error = e.toActionMessage()) }
                // A stale or unknown step must not be retried blindly: reload first.
                reload = e.leavesStateUnknown()
            }
            settle(id, reload)
        }
    }

    override fun askComplete(id: Long?) = _state.update { it.copy(completeFor = id) }

    override fun complete() {
        val id = _state.value.completeFor ?: return
        _state.update { it.copy(completeFor = null) }
        if (!capabilities.canComplete || _state.value.card(id).busy) return
        edit(id) { it.copy(busy = true, error = null, message = null) }
        scope.launch {
            var reload = true
            try {
                gateway.complete(id)
                _state.update { it.copy(notice = DELIVERED_NOTICE) }
            } catch (e: Exception) {
                handleAuth(e)
                edit(id) { it.copy(error = e.toHandoffMessage()) }
                reload = e.leavesStateUnknown() || (e is ApiException && (e.code == "HANDOFF_ALREADY_COMPLETED" || e.code == "ORDER_STATE_CHANGED"))
            }
            settle(id, reload)
        }
    }

    override fun openIncident(id: Long, label: String, hasOpenIncident: Boolean) {
        _state.update { it.copy(incident = IncidentDraft(id, label, hasOpenIncident = hasOpenIncident, checking = true), notice = null) }
        scope.launch {
            try {
                val open = gateway.incidents(id).any { it.status == com.grupo4.dlecheymielapp.data.fulfillment.IncidentStatus.OPEN }
                _state.update { s -> if (s.incident?.orderId == id) s.copy(incident = s.incident.copy(hasOpenIncident = open, checking = false)) else s }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleAuth(e)
                _state.update { s -> if (s.incident?.orderId == id) s.copy(incident = s.incident.copy(checking = false, error = "No se pudo comprobar si hay incidencias abiertas. ${e.toIncidentMessage()}")) else s }
            }
        }
    }

    override fun onIncidentText(value: String) =
        _state.update { s -> s.copy(incident = s.incident?.copy(text = value.take(MAX_INCIDENT_LENGTH), error = null)) }

    override fun closeIncident() = _state.update { s -> if (s.incident?.busy == true) s else s.copy(incident = null) }

    override fun submitIncident() {
        val draft = _state.value.incident ?: return
        val text = draft.text.trim()
        if (draft.busy || draft.checking || text.isEmpty()) return
        _state.update { it.copy(incident = draft.copy(busy = true, error = null)) }
        scope.launch {
            try {
                val result = gateway.reportIncident(draft.orderId, text)
                _state.update { it.copy(incident = null, notice = if (result.addedAsNote) "Se agregó a la incidencia abierta." else "Incidencia registrada. La administración fue avisada.") }
                // The order is on hold now: a code error or message from before no longer describes it.
                edit(draft.orderId) { it.copy(error = null, message = null) }
                onChanged()
            } catch (e: Exception) {
                handleAuth(e)
                _state.update { s -> s.copy(incident = s.incident?.copy(busy = false, error = e.toIncidentMessage())) }
            }
        }
    }

    override fun dismissNotice() = _state.update { it.copy(notice = null) }

    override fun openPayment(order: CardOrder) {
        val info = order.payAtDelivery ?: return
        _state.update { it.copy(payment = PaymentDraft(order.id, order.code ?: "Pedido ${order.id}", order.phone, info), notice = null) }
        // Opening the panel is the request for the QR; before the slot starts the panel only explains when it will be available.
        if (info.linkAvailable) generatePayment()
    }

    override fun generatePayment() {
        val draft = _state.value.payment ?: return
        if (draft.busy) return
        _state.update { it.copy(payment = draft.copy(busy = true, error = null)) }
        scope.launch {
            try {
                val url = gateway.paymentLink(draft.orderId)
                // The panel may have been closed while the request was in flight: then the link is simply dropped.
                _state.update { s -> if (s.payment?.orderId == draft.orderId) s.copy(payment = s.payment.copy(busy = false, url = url)) else s }
            } catch (e: Exception) {
                handleAuth(e)
                _state.update { s -> if (s.payment?.orderId == draft.orderId) s.copy(payment = s.payment.copy(busy = false, error = e.toPaymentLinkMessage())) else s }
            }
        }
    }

    override fun closePayment() = _state.update { it.copy(payment = null) }

    override fun refreshPayment() {
        closePayment()
        scope.launch { onChanged() }
    }

    private fun handleAuth(e: Exception) {
        if (e is ApiException && e.status == 401) onUnauthorized()
    }
}
