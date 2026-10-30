package com.grupo4.dlecheymielapp.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.data.admin.AdminOrderDetail
import com.grupo4.dlecheymielapp.data.admin.AdminRepository
import com.grupo4.dlecheymielapp.data.admin.RescheduleSlot
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.ui.common.toAdminMessage
import com.grupo4.dlecheymielapp.ui.common.toGenericMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One thing the Admin can choose in the assignment dialog. `key` tells the ViewModel what to call. */
data class AssignChoice(val key: String, val label: String, val hint: String? = null, val enabled: Boolean = true)

/** What the open dialog is for. Only one dialog is open at a time. */
sealed interface AdminDialog {
    data object Note : AdminDialog

    /** Pick a new slot to move the order to (reassign an open incident, or reopen a lost order). */
    data class Slot(
        val incidentId: Long?,
        val loading: Boolean = true,
        val slots: List<RescheduleSlot> = emptyList(),
        val selected: RescheduleSlot? = null,
        val error: String? = null,
    ) : AdminDialog

    data class ConfirmLost(val incidentId: Long) : AdminDialog
    data class ConfirmUncollectible(val incidentId: Long) : AdminDialog
    data class Desist(val incidentId: Long) : AdminDialog
    data object ConfirmRestoreStock : AdminDialog

    data class Assign(
        val title: String,
        val current: String? = null,
        val loading: Boolean = true,
        val choices: List<AssignChoice> = emptyList(),
        val error: String? = null,
    ) : AdminDialog
}

data class AdminOrderDetailState(
    val loading: Boolean = true,
    val order: AdminOrderDetail? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val actionError: String? = null,
    val noteText: String = "",
    val dialog: AdminDialog? = null,
    /** Payment link waiting to be shared; the host clears it right away. It is a credential, never logged. */
    val paymentUrl: String? = null,
)

class AdminOrderDetailViewModel(
    private val id: Long,
    private val repository: AdminRepository,
    private val onUnauthorized: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminOrderDetailState())
    val state: StateFlow<AdminOrderDetailState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.order == null, error = null) }
        viewModelScope.launch {
            try {
                val order = repository.order(id)
                _state.update { it.copy(loading = false, order = order) }
            } catch (e: Exception) {
                fail(e) { text -> _state.update { it.copy(loading = false, error = text) } }
            }
        }
    }

    // ---- Avanzar estado ----

    /** The next preparation step for the order, or null when the Admin has nothing to move (closing is done by field staff). */
    fun nextStep(order: AdminOrderDetail): Pair<OrderStatus, String>? = when (order.status) {
        OrderStatus.CONFIRMED -> OrderStatus.PREPARING to "Pasar a En preparación"
        OrderStatus.PREPARING ->
            if (order.fulfillmentSnapshot.mode == FulfillmentMode.PICKUP) OrderStatus.READY_FOR_PICKUP to "Marcar Listo para recoger"
            else OrderStatus.IN_TRANSIT to "Marcar En camino"
        else -> null
    }

    fun advance() {
        val order = _state.value.order ?: return
        val (target, _) = nextStep(order) ?: return
        act("El pedido pasó a ${target.label}.") { repository.advance(id, order.status, target) }
    }

    // ---- Notas ----

    fun openNote() = _state.update { it.copy(dialog = AdminDialog.Note, noteText = "", actionError = null) }

    fun onNoteChange(text: String) = _state.update { it.copy(noteText = text.take(2000)) }

    fun submitNote() {
        val text = _state.value.noteText.trim()
        if (text.isEmpty()) return
        act("Nota guardada.") { repository.addNote(id, text) }
    }

    // ---- Incidencias ----

    fun openReassign(incidentId: Long) = openSlots(incidentId)

    fun openReopen() = openSlots(null)

    private fun openSlots(incidentId: Long?) {
        _state.update { it.copy(dialog = AdminDialog.Slot(incidentId), actionError = null) }
        viewModelScope.launch {
            try {
                val slots = repository.rescheduleSlots(id).filter { it.remainingCapacity > 0 }
                updateSlot { it.copy(loading = false, slots = slots) }
            } catch (e: Exception) {
                fail(e) { text -> updateSlot { it.copy(loading = false, error = text) } }
            }
        }
    }

    fun selectSlot(slot: RescheduleSlot) = updateSlot { it.copy(selected = slot) }

    fun submitSlot() {
        val dialog = _state.value.dialog as? AdminDialog.Slot ?: return
        val slot = dialog.selected ?: return
        val incidentId = dialog.incidentId
        if (incidentId == null) {
            act("El pedido se reabrió en la nueva franja.") { repository.reopen(id, slot) }
        } else {
            act("Pedido reasignado. Se avisó al cliente por correo.") { repository.reassign(id, incidentId, slot) }
        }
    }

    private fun updateSlot(transform: (AdminDialog.Slot) -> AdminDialog.Slot) = _state.update {
        val dialog = it.dialog as? AdminDialog.Slot ?: return@update it
        it.copy(dialog = transform(dialog))
    }

    fun askLost(incidentId: Long) = showDialog(AdminDialog.ConfirmLost(incidentId))

    fun askUncollectible(incidentId: Long) = showDialog(AdminDialog.ConfirmUncollectible(incidentId))

    fun askDesist(incidentId: Long) = showDialog(AdminDialog.Desist(incidentId))

    fun askRestoreStock() = showDialog(AdminDialog.ConfirmRestoreStock)

    private fun showDialog(dialog: AdminDialog) = _state.update { it.copy(dialog = dialog, actionError = null) }

    fun confirmLost() {
        val d = _state.value.dialog as? AdminDialog.ConfirmLost ?: return
        act("Pedido dado por perdido.") { repository.markLost(id, d.incidentId) }
    }

    fun confirmUncollectible() {
        val d = _state.value.dialog as? AdminDialog.ConfirmUncollectible ?: return
        act("Marcado como no cobrable.") { repository.uncollectible(id, d.incidentId) }
    }

    fun confirmDesist(refund: Boolean) {
        val d = _state.value.dialog as? AdminDialog.Desist ?: return
        act("El pedido quedó cancelado por desistimiento.") { repository.desist(id, d.incidentId, refund) }
    }

    fun confirmRestoreStock() = act("Stock repuesto.") { repository.restoreStock(id) }

    // ---- Enlace de pago ----

    fun requestPaymentLink() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, actionError = null, message = null) }
        viewModelScope.launch {
            try {
                val url = repository.paymentLink(id)
                _state.update { it.copy(busy = false, paymentUrl = url) }
            } catch (e: Exception) {
                fail(e) { text -> _state.update { it.copy(busy = false, actionError = text) } }
            }
        }
    }

    /** The link must not outlive the screen that shows it. */
    fun hidePaymentLink() = _state.update { it.copy(paymentUrl = null) }

    // ---- Asignación ----

    /** Opens the assignment dialog that fits the order's modality; pickup orders are assigned by group. */
    fun openAssign() {
        val order = _state.value.order ?: return
        when (order.fulfillmentSnapshot.mode) {
            FulfillmentMode.INTERNAL_DELIVERY -> openHandlerAssign()
            FulfillmentMode.DELIVERY -> openDestinationAssign()
            else -> _state.update { it.copy(actionError = "Los pedidos de recojo se asignan por grupo, en la pestaña Asignaciones.") }
        }
    }

    private fun openHandlerAssign() {
        _state.update { it.copy(dialog = AdminDialog.Assign("Asignar encargado"), actionError = null) }
        viewModelScope.launch {
            try {
                val current = repository.orderAssignment(id).handlerId
                val candidates = repository.orderCandidates(id)
                val choices = candidates.map { c ->
                    AssignChoice(
                        key = "h:${c.id}", label = c.displayName,
                        hint = when {
                            c.id == current -> "Asignado actualmente"
                            !c.available -> c.conflict?.let { "Ocupado ${it.startTime}–${it.endTime}" } ?: "No disponible"
                            else -> null
                        },
                        enabled = c.available && c.id != current,
                    )
                }
                val currentName = candidates.firstOrNull { it.id == current }?.displayName
                updateAssign { it.copy(loading = false, choices = choices, current = currentName ?: current?.let { "Encargado #$it" }) }
            } catch (e: Exception) {
                fail(e) { text -> updateAssign { it.copy(loading = false, error = text) } }
            }
        }
    }

    private fun openDestinationAssign() {
        _state.update { it.copy(dialog = AdminDialog.Assign("Destino del delivery"), actionError = null) }
        viewModelScope.launch {
            try {
                val destination = repository.deliveryDestination(id)
                val employees = repository.deliveryEmployees()
                val choices = buildList {
                    add(AssignChoice("op", "Operador (cola de delivery externo)", enabled = destination.destinationRole != "DELIVERY_COORDINATOR"))
                    employees.forEach {
                        add(AssignChoice("e:${it.id}", it.displayName, "Delivery interno", enabled = destination.employeeId != it.id))
                    }
                    add(AssignChoice("self", "Lo entrego yo (sin destino)", enabled = destination.destinationRole != null))
                }
                val currentLabel = when (destination.destinationRole) {
                    "DELIVERY" -> destination.employeeDisplayName ?: "Delivery interno"
                    "DELIVERY_COORDINATOR" -> "Operador"
                    else -> "Sin destino (entrega el Administrador)"
                }
                updateAssign { it.copy(loading = false, choices = choices, current = currentLabel) }
            } catch (e: Exception) {
                fail(e) { text -> updateAssign { it.copy(loading = false, error = text) } }
            }
        }
    }

    fun choose(choice: AssignChoice) {
        val key = choice.key
        when {
            key.startsWith("h:") -> act("Encargado asignado.") { repository.assignOrder(id, key.removePrefix("h:").toLong()) }
            key.startsWith("e:") -> act("Pedido enviado al Delivery.") { repository.sendToDeliveryEmployee(id, key.removePrefix("e:").toLong()) }
            key == "op" -> act("Pedido enviado al Operador.") { repository.sendToOperator(id) }
            key == "self" -> act("El pedido queda para entrega del Administrador.") { repository.clearDeliveryDestination(id) }
        }
    }

    private fun updateAssign(transform: (AdminDialog.Assign) -> AdminDialog.Assign) = _state.update {
        val dialog = it.dialog as? AdminDialog.Assign ?: return@update it
        it.copy(dialog = transform(dialog))
    }

    fun closeDialog() = _state.update { it.copy(dialog = null) }

    fun clearMessage() = _state.update { it.copy(message = null, actionError = null) }

    /** Runs one write at a time (no idempotency keys on these endpoints), then reloads the order. */
    private fun act(success: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null, actionError = null) }
        viewModelScope.launch {
            try {
                block()
                val order = repository.order(id)
                _state.update { it.copy(busy = false, order = order, dialog = null, message = success, noteText = "") }
            } catch (e: Exception) {
                fail(e) { text ->
                    _state.update { it.copy(busy = false, actionError = text, dialog = if (it.dialog is AdminDialog.Slot) it.dialog else null) }
                }
                // The order moved under us: show its real state instead of leaving a stale screen.
                if (e is ApiException && e.code in setOf("ORDER_STATE_CHANGED", "INCIDENT_NOT_OPEN", "ORDER_ALREADY_TERMINAL")) load()
            }
        }
    }

    private inline fun fail(e: Exception, apply: (String) -> Unit) {
        if (e is ApiException && e.status == 401) onUnauthorized()
        apply(if (e is ApiException) e.toAdminMessage() else e.toGenericMessage())
    }
}
