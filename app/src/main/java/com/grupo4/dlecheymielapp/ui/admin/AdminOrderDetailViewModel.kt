package com.grupo4.dlecheymielapp.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.data.admin.AdminOrderDetail
import com.grupo4.dlecheymielapp.data.admin.AdminRepository
import com.grupo4.dlecheymielapp.data.admin.DeliveryDestination
import com.grupo4.dlecheymielapp.data.admin.IncidentCause
import com.grupo4.dlecheymielapp.data.admin.RescheduleSlot
import com.grupo4.dlecheymielapp.data.admin.excludingSlotOf
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.ui.common.toAdminMessage
import com.grupo4.dlecheymielapp.ui.handler.leavesStateUnknown
import com.grupo4.dlecheymielapp.ui.handler.needsAdvanceConfirmation
import com.grupo4.dlecheymielapp.ui.common.toGenericMessage
import kotlinx.coroutines.CancellationException
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

    /** Settle who failed; the note (1 to 500 characters) is required and audited by the server. */
    data class Cause(val incidentId: Long, val cause: IncidentCause = IncidentCause.NEGOCIO, val note: String = "") : AdminDialog

    /** Full refund of an order the business did not deliver; `lateFee` warns that the sale was before today. */
    data class ConfirmRefund(val incidentId: Long, val lateFee: Boolean) : AdminDialog
    data class ConfirmRefundLostCharge(val lateFee: Boolean) : AdminDialog

    /** The next step is En camino or terminal: the Admin confirms the order code and target status first. */
    data class ConfirmAdvance(val label: String, val target: OrderStatus) : AdminDialog

    data class Assign(
        val title: String,
        val current: String? = null,
        val loading: Boolean = true,
        val choices: List<AssignChoice> = emptyList(),
        val error: String? = null,
    ) : AdminDialog
}

/** Who the order is assigned to, as the detail card shows it. */
sealed interface AssignmentView {
    data object Loading : AssignmentView
    data class Known(val label: String) : AssignmentView
    /** The read failed: never shown as "unassigned", which is a different fact. */
    data object Failed : AssignmentView
}

/** What the "client desisted" dialog says and whether it may offer a refund: only a paid order has a charge to return. */
internal data class DesistPrompt(val text: String, val canRefund: Boolean)

internal fun desistPrompt(paid: Boolean): DesistPrompt =
    if (paid) DesistPrompt("El pedido se cancela. ¿Se debe iniciar el reembolso completo?", canRefund = true)
    else DesistPrompt("El pedido se cancela. No hay ningún cobro que reembolsar.", canRefund = false)

/** Why Guardar is disabled while the note is empty; null once there is text to save. */
internal fun noteHint(text: String): String? = if (text.isBlank()) "Escriba la nota para poder guardarla." else null

/** The label of a delivery destination, shared by the card and the assignment dialog. */
internal fun destinationLabel(destination: DeliveryDestination): String = when (destination.destinationRole) {
    "DELIVERY" -> destination.employeeDisplayName ?: "Delivery interno"
    "DELIVERY_COORDINATOR" -> "Operador"
    else -> "Sin destino (entrega el Administrador)"
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
    /** Null until [AdminOrderDetailViewModel.loadAssignment] runs (pickup orders are assigned by group and never load it). */
    val assignment: AssignmentView? = null,
    /** Bumped after every write so the screen reads the assignment again. */
    val assignmentTick: Int = 0,
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
            } catch (e: CancellationException) {
                throw e
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

    /** The first call opens the confirmation when the step is En camino or terminal; the confirmed call sends it. */
    fun advance() {
        val order = _state.value.order ?: return
        val (target, _) = nextStep(order) ?: return
        if (target.needsAdvanceConfirmation() && _state.value.dialog !is AdminDialog.ConfirmAdvance) {
            val label = order.publicCode ?: "Pedido ${order.id}"
            _state.update { it.copy(dialog = AdminDialog.ConfirmAdvance(label, target), actionError = null) }
            return
        }
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
                val open = repository.rescheduleSlots(id).filter { it.remainingCapacity > 0 }
                // Reassigning to the slot the order already holds would change nothing.
                val current = if (incidentId != null) _state.value.order?.fulfillmentSnapshot else null
                val slots = if (current != null) open.excludingSlotOf(current) else open
                updateSlot { it.copy(loading = false, slots = slots) }
            } catch (e: CancellationException) {
                throw e
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
        } else if (_state.value.order?.fulfillmentSnapshot?.mode == FulfillmentMode.INTERNAL_DELIVERY) {
            // The server drops the Encargado when it moves the order, so the Admin decides who delivers it in the new slot:
            // the same one, another one, or later. The previous Encargado is read first because the reassignment erases it.
            var previous: Long? = null
            act("Pedido reasignado. Se avisó al cliente por correo.", after = { openHandlerAfterReassign(previous) }) {
                previous = runCatching { repository.orderAssignment(id).handlerId }.getOrNull()
                repository.reassign(id, incidentId, slot)
            }
        } else {
            act("Pedido reasignado. Se avisó al cliente por correo.") { repository.reassign(id, incidentId, slot) }
        }
    }

    private fun openHandlerAfterReassign(previous: Long?) {
        _state.update { it.copy(dialog = AdminDialog.Assign("¿Quién entrega el pedido reasignado?"), actionError = null) }
        viewModelScope.launch {
            try {
                val candidates = repository.orderCandidates(id)
                val choices = buildList {
                    candidates.sortedByDescending { it.id == previous }.forEach { c ->
                        add(
                            AssignChoice(
                                key = "h:${c.id}", label = c.displayName,
                                hint = when {
                                    !c.available -> c.conflict?.let { "Ocupado ${it.startTime}–${it.endTime}" } ?: "No disponible"
                                    c.id == previous -> "Tenía este pedido"
                                    else -> null
                                },
                                enabled = c.available,
                            ),
                        )
                    }
                    add(AssignChoice("later", "Dejarlo sin encargado por ahora", "Aparecerá sin asignar hasta que lo elija."))
                }
                val previousName = candidates.firstOrNull { it.id == previous }?.displayName
                updateAssign { it.copy(loading = false, choices = choices, current = previousName?.let { name -> "Antes: $name" } ?: "Sin encargado") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e) { text -> updateAssign { it.copy(loading = false, error = text) } }
            }
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

    // ---- Causa y reembolso (pedidos pagados) ----

    fun askCause(incidentId: Long, current: IncidentCause) {
        val start = if (current == IncidentCause.CLIENTE) IncidentCause.CLIENTE else IncidentCause.NEGOCIO
        showDialog(AdminDialog.Cause(incidentId, start))
    }

    fun onCauseChoice(cause: IncidentCause) = updateCause { it.copy(cause = cause) }

    fun onCauseNote(text: String) = updateCause { it.copy(note = text.take(CAUSE_NOTE_MAX)) }

    private fun updateCause(transform: (AdminDialog.Cause) -> AdminDialog.Cause) = _state.update {
        val dialog = it.dialog as? AdminDialog.Cause ?: return@update it
        it.copy(dialog = transform(dialog))
    }

    fun submitCause() {
        val d = _state.value.dialog as? AdminDialog.Cause ?: return
        if (d.cause != IncidentCause.NEGOCIO && d.cause != IncidentCause.CLIENTE) return
        if (d.note.isBlank()) return
        val message = if (d.cause == IncidentCause.CLIENTE) "Causa fijada: falló el cliente. Corren de nuevo sus 24 horas." else "Causa fijada: falló el negocio."
        act(message) { repository.setCause(id, d.incidentId, d.cause, d.note) }
    }

    /** The commission of the payment provider is lost when the sale was before today, so the dialog says so first. */
    fun askRefund(incidentId: Long) {
        val order = _state.value.order ?: return
        showDialog(AdminDialog.ConfirmRefund(incidentId, lateFee = soldBeforeToday(order)))
    }

    fun confirmRefund() {
        val d = _state.value.dialog as? AdminDialog.ConfirmRefund ?: return
        act("El pedido se canceló y el reembolso total está en curso.") { repository.refund(id, d.incidentId) }
    }

    fun askRefundLostCharge() {
        val order = _state.value.order ?: return
        showDialog(AdminDialog.ConfirmRefundLostCharge(lateFee = soldBeforeToday(order)))
    }

    fun confirmRefundLostCharge() = act("El pedido se canceló y el reembolso total está en curso.") { repository.refundLostCharge(id) }

    private fun soldBeforeToday(order: AdminOrderDetail): Boolean {
        val soldOn = order.consolidatedAt.take(10)
        return soldOn.length == 10 && soldOn < todayInLima()
    }

    // ---- Enlace de pago ----

    fun requestPaymentLink() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, actionError = null, message = null) }
        viewModelScope.launch {
            try {
                val url = repository.paymentLink(id)
                _state.update { it.copy(busy = false, paymentUrl = url) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e) { text -> _state.update { it.copy(busy = false, actionError = text) } }
            }
        }
    }

    /** The link must not outlive the screen that shows it. */
    fun hidePaymentLink() = _state.update { it.copy(paymentUrl = null) }

    // ---- Asignación ----

    /** Reads who the order is assigned to, so the card shows it without opening the dialog. */
    fun loadAssignment() {
        val mode = _state.value.order?.fulfillmentSnapshot?.mode ?: return
        if (mode != FulfillmentMode.INTERNAL_DELIVERY && mode != FulfillmentMode.DELIVERY) return
        _state.update { it.copy(assignment = it.assignment ?: AssignmentView.Loading) }
        viewModelScope.launch {
            try {
                val label = if (mode == FulfillmentMode.DELIVERY) {
                    destinationLabel(repository.deliveryDestination(id))
                } else {
                    val handlerId = repository.orderAssignment(id).handlerId
                    if (handlerId == null) "Sin encargado"
                    else repository.orderCandidates(id).firstOrNull { it.id == handlerId }?.displayName ?: "Encargado #$handlerId"
                }
                _state.update { it.copy(assignment = AssignmentView.Known(label)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(assignment = AssignmentView.Failed) }
            }
        }
    }

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
            } catch (e: CancellationException) {
                throw e
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
                updateAssign { it.copy(loading = false, choices = choices, current = destinationLabel(destination)) }
            } catch (e: CancellationException) {
                throw e
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
            key == "later" -> closeDialog()
        }
    }

    private fun updateAssign(transform: (AdminDialog.Assign) -> AdminDialog.Assign) = _state.update {
        val dialog = it.dialog as? AdminDialog.Assign ?: return@update it
        it.copy(dialog = transform(dialog))
    }

    fun closeDialog() = _state.update { it.copy(dialog = null) }

    fun clearMessage() = _state.update { it.copy(message = null, actionError = null) }

    /**
     * Runs one write at a time (no idempotency keys on these endpoints), then reloads the order. The write stays busy
     * until that reload has finished; after a timeout, a dropped connection or a conflict the order is reloaded first.
     */
    private fun act(success: String, after: (() -> Unit)? = null, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null, actionError = null) }
        viewModelScope.launch {
            try {
                block()
                val order = repository.order(id)
                _state.update { it.copy(busy = false, order = order, dialog = null, message = success, noteText = "", assignmentTick = it.assignmentTick + 1) }
                after?.invoke()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e) { text ->
                    _state.update { it.copy(actionError = text, dialog = if (it.dialog is AdminDialog.Slot || it.dialog is AdminDialog.ConfirmAdvance) null else it.dialog) }
                }
                // The order moved under us or its outcome is unknown: show its real state before the actions unlock.
                if (e.leavesStateUnknown() || (e is ApiException && e.code in RELOAD_CODES)) reloadOrder()
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun reloadOrder() {
        try {
            val order = repository.order(id)
            _state.update { it.copy(order = order) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e) { text -> _state.update { it.copy(actionError = text) } }
        }
    }

    private inline fun fail(e: Exception, apply: (String) -> Unit) {
        if (e is ApiException && e.status == 401) onUnauthorized()
        apply(if (e is ApiException) e.toAdminMessage() else e.toGenericMessage())
    }

    private companion object {
        const val CAUSE_NOTE_MAX = 500

        /** Answers that mean the order is no longer what the screen shows, so it is reloaded before the actions unlock. */
        val RELOAD_CODES = setOf(
            "INCIDENT_NOT_OPEN", "ORDER_ALREADY_TERMINAL", "REFUND_ALREADY_EXISTS", "PAYMENT_NOT_SETTLED",
            "RESCHEDULE_LIMIT_REACHED", "RESCHEDULE_WINDOW_CLOSED", "LOSS_NOT_ALLOWED_PAID_ORDER", "INCIDENT_CAUSE_REQUIRED",
        )
    }
}

/** Today in Lima as yyyy-MM-dd: the day of the sale decides whether a refund costs the card commission. */
internal fun todayInLima(): String =
    java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).toString()
