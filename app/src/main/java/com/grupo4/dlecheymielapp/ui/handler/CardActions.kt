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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

data class CardActionsState(
    val cards: Map<Long, CardState> = emptyMap(),
    val incident: IncidentDraft? = null,
    val payment: PaymentDraft? = null,
    /** Order waiting for the "mark as delivered" confirmation (Operador, paid in advance). */
    val completeFor: Long? = null,
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

/** What the screens can ask of the cards. Previews and tests use [NoCardCallbacks]. */
interface CardCallbacks {
    val capabilities: FieldCapabilities

    fun onCode(id: Long, value: String)
    fun validate(id: Long)
    fun advance(id: Long, from: OrderStatus)
    fun askComplete(id: Long?)
    fun complete()
    fun openIncident(id: Long, label: String)
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
    override fun advance(id: Long, from: OrderStatus) = Unit
    override fun askComplete(id: Long?) = Unit
    override fun complete() = Unit
    override fun openIncident(id: Long, label: String) = Unit
    override fun onIncidentText(value: String) = Unit
    override fun closeIncident() = Unit
    override fun submitIncident() = Unit
    override fun dismissNotice() = Unit
    override fun openPayment(order: CardOrder) = Unit
    override fun generatePayment() = Unit
    override fun closePayment() = Unit
    override fun refreshPayment() = Unit
}

/**
 * Acts on orders straight from a list card, with the same rules as the order's own screen: one request at a time per
 * order, a stale state is reloaded instead of retried, and an incident is sent once (every POST creates a new one).
 * [onChanged] reloads the list so the cards show what the server now says.
 */
class CardActions(
    private val scope: CoroutineScope,
    private val gateway: FieldGateway,
    private val onUnauthorized: () -> Unit,
    private val onChanged: () -> Unit,
) : CardCallbacks {
    private val _state = MutableStateFlow(CardActionsState())
    val state: StateFlow<CardActionsState> = _state.asStateFlow()

    override val capabilities: FieldCapabilities get() = gateway.capabilities

    private fun edit(id: Long, change: (CardState) -> CardState) =
        _state.update { it.copy(cards = it.cards + (id to change(it.card(id)))) }

    override fun onCode(id: Long, value: String) =
        edit(id) { it.copy(code = value.filter { c -> c.isLetterOrDigit() }.take(12).uppercase(), error = null) }

    override fun validate(id: Long) {
        val card = _state.value.card(id)
        val code = card.code.trim()
        if (card.busy || code.isEmpty()) return
        _state.update { it.copy(notice = null) }
        edit(id) { it.copy(busy = true, error = null, message = null) }
        scope.launch {
            try {
                val result = gateway.handoff(id, code)
                val message = if (result.awaitingPayment) {
                    "Código validado. El pedido se cerrará cuando el pago contra entrega esté aprobado."
                } else {
                    "Pedido entregado."
                }
                edit(id) { it.copy(busy = false, code = "", message = message) }
                onChanged()
            } catch (e: Exception) {
                handleAuth(e)
                edit(id) { it.copy(busy = false, error = e.toHandoffMessage()) }
                // Delivered by someone else or moved under us: show what the order really is.
                if (e is ApiException && (e.code == "HANDOFF_ALREADY_COMPLETED" || e.code == "ORDER_STATE_CHANGED")) onChanged()
            }
        }
    }

    override fun advance(id: Long, from: OrderStatus) {
        val step = nextStepOf(from) ?: return
        if (!capabilities.canAdvance || _state.value.card(id).busy) return
        _state.update { it.copy(notice = null) }
        edit(id) { it.copy(busy = true, error = null, message = null) }
        scope.launch {
            try {
                gateway.advance(id, from)
                edit(id) { it.copy(busy = false, message = "El pedido pasó a ${step.first.label}.") }
                onChanged()
            } catch (e: Exception) {
                handleAuth(e)
                edit(id) { it.copy(busy = false, error = e.toActionMessage()) }
                // A stale step must not be retried blindly.
                if (e is ApiException && e.code == "ORDER_STATE_CHANGED") onChanged()
            }
        }
    }

    override fun askComplete(id: Long?) = _state.update { it.copy(completeFor = id) }

    override fun complete() {
        val id = _state.value.completeFor ?: return
        _state.update { it.copy(completeFor = null) }
        if (!capabilities.canComplete || _state.value.card(id).busy) return
        edit(id) { it.copy(busy = true, error = null, message = null) }
        scope.launch {
            try {
                gateway.complete(id)
                edit(id) { it.copy(busy = false, message = "Pedido entregado.") }
                onChanged()
            } catch (e: Exception) {
                handleAuth(e)
                edit(id) { it.copy(busy = false, error = e.toHandoffMessage()) }
                if (e is ApiException && (e.code == "HANDOFF_ALREADY_COMPLETED" || e.code == "ORDER_STATE_CHANGED")) onChanged()
            }
        }
    }

    override fun openIncident(id: Long, label: String) = _state.update { it.copy(incident = IncidentDraft(id, label), notice = null) }

    override fun onIncidentText(value: String) =
        _state.update { s -> s.copy(incident = s.incident?.copy(text = value.take(MAX_INCIDENT_LENGTH), error = null)) }

    override fun closeIncident() = _state.update { s -> if (s.incident?.busy == true) s else s.copy(incident = null) }

    override fun submitIncident() {
        val draft = _state.value.incident ?: return
        val text = draft.text.trim()
        if (draft.busy || text.isEmpty()) return
        _state.update { it.copy(incident = draft.copy(busy = true, error = null)) }
        scope.launch {
            try {
                gateway.reportIncident(draft.orderId, text)
                _state.update { it.copy(incident = null, notice = "Incidencia registrada. La administración fue avisada.") }
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
        onChanged()
    }

    private fun handleAuth(e: Exception) {
        if (e is ApiException && e.status == 401) onUnauthorized()
    }
}
