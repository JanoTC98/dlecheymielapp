package com.grupo4.dlecheymielapp.ui.handler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.data.fulfillment.FieldCapabilities
import com.grupo4.dlecheymielapp.data.fulfillment.FieldGateway
import com.grupo4.dlecheymielapp.data.fulfillment.FieldIncident
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerOrderWork
import com.grupo4.dlecheymielapp.data.fulfillment.IncidentStatus
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.data.fulfillment.PaymentStatus
import com.grupo4.dlecheymielapp.data.fulfillment.Provider
import com.grupo4.dlecheymielapp.ui.common.PROVIDER_PHONE_FORMAT_MESSAGE
import com.grupo4.dlecheymielapp.ui.common.isValidProviderPhone
import com.grupo4.dlecheymielapp.ui.common.toActionMessage
import com.grupo4.dlecheymielapp.ui.common.toGenericMessage
import com.grupo4.dlecheymielapp.ui.common.toHandoffMessage
import com.grupo4.dlecheymielapp.ui.common.toIncidentMessage
import com.grupo4.dlecheymielapp.ui.common.toPaymentLinkMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MAX_INCIDENT_LENGTH = 2000

data class OrderDetailState(
    val loading: Boolean = true,
    val order: HandlerOrderWork? = null,
    val loadError: String? = null,
    val incidents: List<FieldIncident> = emptyList(),
    val capabilities: FieldCapabilities = FieldCapabilities(),

    val code: String = "",
    val handoffBusy: Boolean = false,
    val handoffError: String? = null,
    /** Shown after a valid code: either delivered or waiting for the on-delivery payment. */
    val handoffSuccess: String? = null,

    val incidentDialogOpen: Boolean = false,
    val incidentText: String = "",
    val incidentBusy: Boolean = false,
    val incidentError: String? = null,
    val incidentSaved: Boolean = false,
    /** The incident list could not be read: the screen must not claim there are none. */
    val incidentsLoadFailed: Boolean = false,

    val linkBusy: Boolean = false,
    val linkError: String? = null,
    /** In memory only, while the QR panel is open: the URL is a credential, never log or persist it. */
    val paymentUrl: String? = null,

    /** Delivery / Operador: moving the order through preparation. */
    val actionBusy: Boolean = false,
    val actionError: String? = null,
    val actionMessage: String? = null,
    val confirmComplete: Boolean = false,
    /** En camino or a terminal step waiting for its confirmation dialog. */
    val confirmAdvance: Boolean = false,

    val providerName: String = "",
    val providerPhone: String = "",
    val providerSaved: Boolean = false,
    /** Shown under the phone field; set when a save is refused, cleared when the phone is edited. */
    val providerPhoneError: String? = null,
) {
    /** Pickup orders are handed over when ready; every other modality while on its way. */
    val handoffStatus: OrderStatus
        get() = if (order?.mode == FulfillmentMode.PICKUP) OrderStatus.READY_FOR_PICKUP else OrderStatus.IN_TRANSIT

    /** The Operador closes an order paid in advance with a button, not a code. */
    val closesWithoutCode: Boolean get() = capabilities.canComplete && order?.payAtDelivery == null

    /** The Administrator decides an open incident (reassign, refund, write off); until then the order cannot be handed over. */
    val hasOpenIncident: Boolean get() = incidents.any { it.status == IncidentStatus.OPEN }

    /** An on-delivery order whose code was already accepted only waits for its payment; the code is not asked for again. */
    val codeAlreadyValidated: Boolean
        get() = order?.payAtDelivery?.let { it.codeVerified && it.paymentStatus != PaymentStatus.approved } == true

    val canHandoff: Boolean get() = order?.status == handoffStatus && !handoffBusy && !hasOpenIncident && !codeAlreadyValidated
    val codeReady: Boolean get() = code.trim().isNotEmpty()
    val canReportIncident: Boolean get() = order?.status?.let { !it.isTerminal } == true

    /** The step an employee can take now, with its button label; null when closing or waiting on someone else. */
    val nextStep: Pair<OrderStatus, String>?
        get() = if (capabilities.canAdvance) order?.status?.let(::nextStepOf) else null

    val canEditProvider: Boolean
        get() = capabilities.canRegisterProvider &&
            order?.status in setOf(OrderStatus.CONFIRMED, OrderStatus.PREPARING, OrderStatus.IN_TRANSIT)
    val providerReady: Boolean get() = providerName.isNotBlank() && providerPhone.isNotBlank()
}

class OrderDetailViewModel(
    private val orderId: Long,
    private val gateway: FieldGateway,
    private val onUnauthorized: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(OrderDetailState(capabilities = gateway.capabilities))
    val state: StateFlow<OrderDetailState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch { fetch() }
    }

    /** Suspends until the order is reloaded: the write locks are released only after this has finished. */
    private suspend fun fetch() {
        _state.update { it.copy(loading = it.order == null, loadError = null) }
        try {
            val order = gateway.order(orderId)
            var incidentsFailed = false
            // A failed read keeps the last list and says so; it never turns into "no incidents".
            val incidents = try {
                gateway.incidents(orderId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                incidentsFailed = true
                _state.value.incidents
            }
            val provider = if (gateway.capabilities.canRegisterProvider) runCatching { gateway.provider(orderId) }.getOrNull() else null
            _state.update {
                it.copy(
                    loading = false, order = order, incidents = incidents, incidentsLoadFailed = incidentsFailed,
                    // Once the payment is approved or the order is closed the link is of no use: forget it.
                    paymentUrl = it.paymentUrl.takeUnless { _ -> order.status.isTerminal || order.payAtDelivery?.paymentStatus == PaymentStatus.approved },
                    // Keep what the user is typing; fill the fields only the first time.
                    providerName = if (it.providerName.isEmpty()) provider?.name.orEmpty() else it.providerName,
                    providerPhone = if (it.providerPhone.isEmpty()) provider?.phone.orEmpty() else it.providerPhone,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleAuth(e)
            _state.update { it.copy(loading = false, loadError = e.toGenericMessage()) }
        }
    }

    fun onCodeChange(value: String) =
        _state.update { it.copy(code = value.filter { c -> c.isLetterOrDigit() }.take(12).uppercase(), handoffError = null) }

    fun submitHandoff() {
        val current = _state.value
        if (!current.canHandoff || !current.codeReady) return
        _state.update { it.copy(handoffBusy = true, handoffError = null, handoffSuccess = null) }
        viewModelScope.launch {
            var reload = true
            try {
                val result = gateway.handoff(orderId, current.code)
                val message = if (result.awaitingPayment) {
                    "Código validado. El pedido se cerrará cuando el pago contra entrega esté aprobado."
                } else {
                    "Pedido entregado."
                }
                _state.update { it.copy(code = "", handoffSuccess = message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleAuth(e)
                _state.update { it.copy(handoffError = e.toHandoffMessage()) }
                // The order may have changed under us (delivered by someone else, state moved): show the truth.
                reload = e.leavesStateUnknown() || (e is ApiException && (e.code == "HANDOFF_ALREADY_COMPLETED" || e.code == "ORDER_STATE_CHANGED"))
            }
            if (reload) fetch()
            _state.update { it.copy(handoffBusy = false) }
        }
    }

    // ---- Delivery / Operador: avanzar y cerrar ----

    /**
     * The first call asks for confirmation when the step is En camino or terminal; the confirmed call sends it.
     * The actions stay busy until the order has been reloaded, so a second tap cannot act on the old status.
     */
    fun advance() {
        val current = _state.value
        val step = current.nextStep ?: return
        if (current.actionBusy) return
        val expected = current.order?.status ?: return
        if (step.first.needsAdvanceConfirmation() && !current.confirmAdvance) {
            _state.update { it.copy(confirmAdvance = true, actionError = null) }
            return
        }
        _state.update { it.copy(actionBusy = true, actionError = null, actionMessage = null, confirmAdvance = false) }
        viewModelScope.launch {
            var reload = true
            try {
                gateway.advance(orderId, expected)
                _state.update { it.copy(actionMessage = "El pedido pasó a ${step.first.label}.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleAuth(e)
                _state.update { it.copy(actionError = e.toActionMessage()) }
                // A stale or unknown step must not be retried blindly: show what the order really is first.
                reload = e.leavesStateUnknown()
            }
            if (reload) fetch()
            _state.update { it.copy(actionBusy = false) }
        }
    }

    /** Shows or closes the En camino confirmation; closing sends nothing. */
    fun askAdvance(show: Boolean) = _state.update { it.copy(confirmAdvance = show) }

    fun askComplete(show: Boolean) = _state.update { it.copy(confirmComplete = show) }

    fun complete() {
        val current = _state.value
        if (current.actionBusy || !current.closesWithoutCode || current.order?.status != OrderStatus.IN_TRANSIT || current.hasOpenIncident) return
        _state.update { it.copy(actionBusy = true, actionError = null, actionMessage = null, confirmComplete = false) }
        viewModelScope.launch {
            var reload = true
            try {
                gateway.complete(orderId)
                _state.update { it.copy(actionMessage = "Pedido entregado.") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleAuth(e)
                _state.update { it.copy(actionError = e.toHandoffMessage()) }
                reload = e.leavesStateUnknown() || (e is ApiException && (e.code == "HANDOFF_ALREADY_COMPLETED" || e.code == "ORDER_STATE_CHANGED"))
            }
            if (reload) fetch()
            _state.update { it.copy(actionBusy = false) }
        }
    }

    // ---- Operador: proveedor externo ----

    fun onProviderName(value: String) = _state.update { it.copy(providerName = value.take(120), providerSaved = false) }

    fun onProviderPhone(value: String) =
        _state.update { it.copy(providerPhone = value.take(40), providerSaved = false, providerPhoneError = null) }

    fun saveProvider() {
        val current = _state.value
        if (current.actionBusy || !current.providerReady) return
        // An invalid phone never reaches the repository: the field explains the format instead.
        if (!isValidProviderPhone(current.providerPhone)) {
            _state.update { it.copy(providerPhoneError = PROVIDER_PHONE_FORMAT_MESSAGE, providerSaved = false) }
            return
        }
        _state.update { it.copy(actionBusy = true, actionError = null, providerSaved = false) }
        viewModelScope.launch {
            try {
                gateway.saveProvider(orderId, Provider(current.providerName, current.providerPhone.trim()))
                _state.update { it.copy(actionBusy = false, providerSaved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleAuth(e)
                _state.update { it.copy(actionBusy = false, actionError = e.toActionMessage()) }
            }
        }
    }

    // ---- Incidencias ----

    fun openIncidentDialog() = _state.update { it.copy(incidentDialogOpen = true, incidentError = null, incidentSaved = false) }
    fun closeIncidentDialog() = _state.update { it.copy(incidentDialogOpen = false, incidentText = "", incidentError = null) }
    fun onIncidentTextChange(value: String) = _state.update { it.copy(incidentText = value.take(MAX_INCIDENT_LENGTH), incidentError = null) }

    fun submitIncident() {
        val current = _state.value
        val text = current.incidentText.trim()
        // Every POST creates a new incident (no idempotency key): ignore taps while one is in flight.
        if (current.incidentBusy || text.isEmpty()) return
        _state.update { it.copy(incidentBusy = true, incidentError = null) }
        viewModelScope.launch {
            try {
                gateway.reportIncident(orderId, text)
                _state.update { it.copy(incidentBusy = false, incidentDialogOpen = false, incidentText = "", incidentSaved = true) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleAuth(e)
                _state.update { it.copy(incidentBusy = false, incidentError = e.toIncidentMessage()) }
            }
        }
    }

    fun requestPaymentLink() {
        if (_state.value.linkBusy) return
        _state.update { it.copy(linkBusy = true, linkError = null) }
        viewModelScope.launch {
            try {
                val url = gateway.paymentLink(orderId)
                _state.update { it.copy(linkBusy = false, paymentUrl = url) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleAuth(e)
                _state.update { it.copy(linkBusy = false, linkError = e.toPaymentLinkMessage()) }
            }
        }
    }

    /** Called when the screen closes: the link must not outlive the panel. */
    fun hidePaymentLink() = _state.update { it.copy(paymentUrl = null, linkError = null) }

    private fun handleAuth(e: Exception) {
        if (e is ApiException && e.status == 401) onUnauthorized()
    }
}
