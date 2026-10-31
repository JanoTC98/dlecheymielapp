package com.grupo4.dlecheymielapp.ui.admin

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.grupo4.dlecheymielapp.data.admin.AdminPaymentStatus
import com.grupo4.dlecheymielapp.data.fulfillment.PayAtDeliveryInfo
import com.grupo4.dlecheymielapp.data.fulfillment.PaymentStatus
import com.grupo4.dlecheymielapp.ui.handler.PaymentQrPanel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.grupo4.dlecheymielapp.data.admin.AdminIncident
import com.grupo4.dlecheymielapp.data.admin.AdminOrderDetail
import com.grupo4.dlecheymielapp.data.admin.AdminOrderItem
import com.grupo4.dlecheymielapp.data.admin.IncidentCause
import com.grupo4.dlecheymielapp.data.admin.IncidentRules
import com.grupo4.dlecheymielapp.data.admin.IncidentType
import com.grupo4.dlecheymielapp.data.admin.RescheduleBlock
import com.grupo4.dlecheymielapp.data.admin.RescheduleSlot
import com.grupo4.dlecheymielapp.data.admin.incidentRules
import com.grupo4.dlecheymielapp.ui.common.whatsappUrl
import com.grupo4.dlecheymielapp.data.admin.preparationLines
import com.grupo4.dlecheymielapp.data.admin.priceBreakdown
import com.grupo4.dlecheymielapp.data.admin.refundStatusLabel
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.ui.common.LabeledRow
import com.grupo4.dlecheymielapp.ui.common.Pill
import com.grupo4.dlecheymielapp.ui.common.SoftCard
import com.grupo4.dlecheymielapp.ui.common.StatusPill
import com.grupo4.dlecheymielapp.ui.common.formatMoney
import com.grupo4.dlecheymielapp.ui.common.formatSlotDate
import com.grupo4.dlecheymielapp.ui.common.formatTimestamp
import com.grupo4.dlecheymielapp.ui.handler.CenteredMessage
import com.grupo4.dlecheymielapp.ui.theme.Brand

/** Callbacks of the Admin order screen, bundled so previews and the host stay short. */
class AdminOrderActions(
    val onBack: () -> Unit,
    val onRetry: () -> Unit,
    val onAdvance: () -> Unit,
    val onOpenNote: () -> Unit,
    val onNoteChange: (String) -> Unit,
    val onSubmitNote: () -> Unit,
    val onReassign: (Long) -> Unit,
    val onReopen: () -> Unit,
    val onSelectSlot: (RescheduleSlot) -> Unit,
    val onSubmitSlot: () -> Unit,
    val onAskLost: (Long) -> Unit,
    val onConfirmLost: () -> Unit,
    val onAskUncollectible: (Long) -> Unit,
    val onConfirmUncollectible: () -> Unit,
    val onAskDesist: (Long) -> Unit,
    val onConfirmDesist: (Boolean) -> Unit,
    val onAskRestoreStock: () -> Unit,
    val onConfirmRestoreStock: () -> Unit,
    val onAskCause: (Long, IncidentCause) -> Unit,
    val onCauseChoice: (IncidentCause) -> Unit,
    val onCauseNote: (String) -> Unit,
    val onSubmitCause: () -> Unit,
    val onAskRefund: (Long) -> Unit,
    val onConfirmRefund: () -> Unit,
    val onAskRefundLostCharge: () -> Unit,
    val onConfirmRefundLostCharge: () -> Unit,
    val onRequestPaymentLink: () -> Unit,
    val onHidePaymentLink: () -> Unit,
    val onOpenAssign: () -> Unit,
    val onLoadAssignment: () -> Unit,
    val onChoose: (AssignChoice) -> Unit,
    val onCloseDialog: () -> Unit,
)

@Composable
fun AdminOrderDetailScreen(state: AdminOrderDetailState, nextStep: (AdminOrderDetail) -> Pair<OrderStatus, String>?, actions: AdminOrderActions) {
    // The payment link must not outlive the screen that shows it.
    DisposableEffect(Unit) { onDispose { actions.onHidePaymentLink() } }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            Text(state.order?.publicCode ?: "Pedido", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            state.order?.let { StatusPill(it.status) }
        }
        val order = state.order
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            order == null -> CenteredMessage(state.error ?: "No se pudo cargar el pedido.", "Reintentar", actions.onRetry)
            else -> Body(order, state, nextStep(order), actions)
        }
    }
    state.dialog?.let { Dialogs(it, state, actions) }
}

@Composable
private fun Body(order: AdminOrderDetail, state: AdminOrderDetailState, next: Pair<OrderStatus, String>?, actions: AdminOrderActions) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.message?.let { Notice(it, ok = true) }
        // Errors that belong to an open dialog are shown inside it.
        if (state.dialog == null) state.actionError?.let { Notice(it, ok = false) }

        SummaryCard(order)
        StateCard(order, next, state.busy, actions)
        if (order.isPayOnDelivery && !order.status.isTerminal) PaymentCard(order, state, actions)
        // Read again after every write (the tick) so the card never shows a stale assignee.
        LaunchedEffect(order.id, state.assignmentTick) { actions.onLoadAssignment() }
        AssignmentCard(order, state.assignment, state.busy, actions)
        IncidentsCard(order, state.busy, actions)
        NotesCard(order, actions)
        HistoryCard(order)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SummaryCard(order: AdminOrderDetail) {
    val s = order.fulfillmentSnapshot
    SoftCard {
        Text("Entrega", style = MaterialTheme.typography.titleMedium)
        LabeledRow("Modalidad:", s.mode.label)
        LabeledRow("Fecha:", if (s.slotDate.isNotEmpty()) formatSlotDate(s.slotDate) else null)
        LabeledRow("Franja:", if (s.startTime.isNotEmpty()) s.schedule.timeRange else null)
        when (s.mode) {
            FulfillmentMode.PICKUP -> {
                LabeledRow("Universidad:", s.universityName)
                LabeledRow("Punto:", s.pickupPointName)
                LabeledRow("Lugar:", s.pickupPointLocationText)
            }
            FulfillmentMode.INTERNAL_DELIVERY -> {
                LabeledRow("Recibe:", s.recipientName)
                LabeledRow("Teléfono:", s.recipientPhone)
                LabeledRow("Lugar:", s.universityPlace)
                LabeledRow("Referencia:", s.reference)
            }
            FulfillmentMode.DELIVERY -> {
                LabeledRow("Distrito:", s.deliveryDistrictName)
                LabeledRow("Dirección:", s.deliveryAddress)
                LabeledRow("Referencia:", s.deliveryReference)
                LabeledRow("Teléfono:", s.deliveryPhone)
            }
            FulfillmentMode.UNKNOWN -> Unit
        }
        Spacer(Modifier.height(4.dp))
        Text("Compra", style = MaterialTheme.typography.titleMedium)
        val breakdown = order.priceBreakdown()
        if (breakdown != null) {
            // The prices the order was sold with: no fee is derived from the tariff or from subtractions. Each row is drawn
            // from its own item, in line order, so its preparation details sit under it; the amount is that item's subtotal.
            order.items.sortedBy { it.lineNumber }.forEach { PurchaseLine(it) }
            breakdown.deliveryFee?.let { LabeledRow("Costo de entrega:", formatMoney(it)) }
            LabeledRow("Total:", formatMoney(breakdown.total ?: order.amount))
        } else {
            order.items.forEach { PurchaseLine(it) }
            LabeledRow("Total:", formatMoney(order.amount))
        }
        LabeledRow("Pago:", order.paymentStatus.label + if (order.isPayOnDelivery) " (contra entrega)" else "")
        order.refundStatus?.let { LabeledRow("Reembolso:", refundStatusLabel(it)) }
        LabeledRow("Confirmado:", formatTimestamp(order.consolidatedAt))
    }
}

/** The amount of a line is its subtotal (quantity included), not a unit price; the text says so. */
internal fun purchaseLineValue(item: AdminOrderItem): String = "${item.name} · Subtotal: ${formatMoney(item.subtotal)}"

@Composable
private fun PurchaseLine(item: AdminOrderItem) {
    LabeledRow("${item.quantity} ×", purchaseLineValue(item))
    item.preparationLines().forEach {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 24.dp))
    }
}

@Composable
private fun StateCard(order: AdminOrderDetail, next: Pair<OrderStatus, String>?, busy: Boolean, actions: AdminOrderActions) {
    SoftCard {
        Text("Estado", style = MaterialTheme.typography.titleMedium)
        if (next != null) {
            Button(onClick = actions.onAdvance, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Guardando…" else next.second) }
        } else {
            Text(
                when (order.status) {
                    OrderStatus.READY_FOR_PICKUP -> "Esperando el recojo: el Encargado lo cierra con el código del cliente."
                    OrderStatus.IN_TRANSIT -> "En camino: se cierra con el código de entrega o por el Operador."
                    OrderStatus.DELIVERED -> "Entregado."
                    OrderStatus.CANCELLED -> "Cancelado."
                    OrderStatus.LOST -> "Perdido: no se repuso stock ni se reembolsó."
                    OrderStatus.PICKUP_EXPIRED -> "El plazo de recojo venció."
                    else -> "Sin acciones disponibles."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (order.status == OrderStatus.LOST) LostActions(order, busy, actions)
    }
}

@Composable
private fun LostActions(order: AdminOrderDetail, busy: Boolean, actions: AdminOrderActions) {
    // Reopening and restoring stock apply to orders paid at delivery only; the server enforces it too.
    if (order.isPayOnDelivery && !order.stockRestored) {
        OutlinedButton(onClick = actions.onReopen, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Reabrir en otra franja") }
    }
    // A charge approved after the loss: the server answers 409 if it was paid before, and the dialog explains it.
    if (order.isPaid && order.refundStatus == null) {
        OutlinedButton(onClick = actions.onAskRefundLostCharge, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Reembolsar cobro tras la pérdida") }
    }
    if (order.stockRestoreAvailable) {
        OutlinedButton(onClick = actions.onAskRestoreStock, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Reponer stock") }
    }
    if (order.stockRestored) Text("El stock de este pedido ya fue repuesto.", style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun PaymentCard(order: AdminOrderDetail, state: AdminOrderDetailState, actions: AdminOrderActions) {
    val snapshot = order.fulfillmentSnapshot
    val info = PayAtDeliveryInfo(
        paymentStatus = when (order.paymentStatus) {
            AdminPaymentStatus.approved -> PaymentStatus.approved
            AdminPaymentStatus.rejected -> PaymentStatus.rejected
            AdminPaymentStatus.cancelled -> PaymentStatus.cancelled
            AdminPaymentStatus.pending -> PaymentStatus.pending
            AdminPaymentStatus.unknown -> PaymentStatus.unknown
        },
        // The server answers "not yet" itself if the slot has not started, so the button is always offered.
        codeVerified = false, linkAvailable = true,
    )
    SoftCard {
        PaymentQrPanel(
            info = info, phone = snapshot.deliveryPhone ?: snapshot.recipientPhone, url = state.paymentUrl, busy = state.busy,
            error = state.actionError, onGenerate = actions.onRequestPaymentLink, onRefresh = actions.onRetry,
        )
    }
}

@Composable
private fun AssignmentCard(order: AdminOrderDetail, assignment: AssignmentView?, busy: Boolean, actions: AdminOrderActions) {
    if (order.status.isTerminal) return
    val mode = order.fulfillmentSnapshot.mode
    if (mode != FulfillmentMode.INTERNAL_DELIVERY && mode != FulfillmentMode.DELIVERY) return
    SoftCard {
        Text(if (mode == FulfillmentMode.DELIVERY) "Destino del delivery" else "Encargado de entrega", style = MaterialTheme.typography.titleMedium)
        when (assignment) {
            is AssignmentView.Known -> Text("Actual: ${assignment.label}", style = MaterialTheme.typography.bodyMedium)
            AssignmentView.Failed -> Text("No se pudo leer la asignación actual.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            AssignmentView.Loading, null -> Text("Cargando asignación…", style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = actions.onOpenAssign, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (mode == FulfillmentMode.DELIVERY) "Elegir destino" else "Asignar o cambiar encargado")
        }
    }
}

@Composable
private fun IncidentsCard(order: AdminOrderDetail, busy: Boolean, actions: AdminOrderActions) {
    if (order.incidents.isEmpty()) return
    val context = LocalContext.current
    val whatsapp = whatsappUrl(order.customerPhone ?: order.fulfillmentSnapshot.run { deliveryPhone ?: recipientPhone })
    SoftCard {
        Text("Incidencias", style = MaterialTheme.typography.titleMedium)
        order.incidents.forEach { incident ->
            IncidentBlock(incident, order, busy, actions)
        }
        // Rescheduling is agreed with the customer by WhatsApp, outside the app.
        if (whatsapp != null && order.openIncidents.isNotEmpty()) {
            OutlinedButton(
                onClick = { context.startSafely(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(whatsapp))) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Contactar al cliente por WhatsApp") }
        }
    }
}

@Composable
private fun IncidentBlock(incident: AdminIncident, order: AdminOrderDetail, busy: Boolean, actions: AdminOrderActions) {
    // Cause and reminders matter only while a non-IMPAGO incident is open; the cause drives what money moves are allowed.
    val rules = if (incident.status.isOpen && incident.type != IncidentType.IMPAGO) incidentRules(incident, order.incidents, order.isPaid) else null
    Surface(shape = RoundedCornerShape(14.dp), color = if (incident.status.isOpen) Brand.ErrorContainer else Brand.Mist.copy(alpha = 0.4f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(incident.status.label, if (incident.status.isOpen) Brand.Error else Brand.Violet, Color.White)
                Text(incident.type.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (rules != null && order.isPaid) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pill(rules.cause.label, Brand.Violet, Color.White)
                    if (rules.refundRecommended) Pill("Reembolso recomendado", Brand.Error, Color.White)
                    else if (rules.overdue) Pill("Vencida", Brand.Error, Color.White)
                }
            }
            if (incident.description.isNotBlank()) Text(incident.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(incident.reporterRole?.label, incident.reporterName, formatTimestamp(incident.recordedAt)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (incident.status.isOpen && incident.deadlineAt != null) {
                Text("Se da por perdido solo el ${formatTimestamp(incident.deadlineAt)} si nadie decide.", style = MaterialTheme.typography.bodyMedium)
            }
            if (incident.status.isOpen) IncidentButtons(incident, rules, order, busy, actions)
        }
    }
}

@Composable
private fun IncidentButtons(incident: AdminIncident, rules: IncidentRules?, order: AdminOrderDetail, busy: Boolean, actions: AdminOrderActions) {
    if (incident.type == IncidentType.IMPAGO || rules == null) {
        OutlinedButton(onClick = { actions.onAskUncollectible(incident.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Dar por no cobrable") }
        return
    }
    // A paid order the business failed ends in a refund, never in a write-off; the buttons follow the same table as the web.
    if (rules.canRefund) {
        if (rules.refundRecommended) {
            Button(onClick = { actions.onAskRefund(incident.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Reembolsar total") }
        } else {
            OutlinedButton(onClick = { actions.onAskRefund(incident.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Reembolsar total") }
        }
    }
    if (rules.rescheduleBlocked) {
        Text(
            when (rules.rescheduleBlockedBy) {
                RescheduleBlock.LIMIT -> "Este pedido pagado ya se reprogramó una vez: solo queda reembolsarlo."
                else -> "Pasaron más de 7 días desde la franja: ya no se reprograma, solo se reembolsa."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    } else {
        Button(onClick = { actions.onReassign(incident.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Reasignar a otra franja") }
    }
    // The cause only moves money on a paid order, so it is only settled there.
    if (order.isPaid) {
        OutlinedButton(onClick = { actions.onAskCause(incident.id, rules.cause) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Fijar quién falló") }
    }
    if (order.isPayOnDelivery) {
        OutlinedButton(onClick = { actions.onAskDesist(incident.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("El cliente desistió") }
    }
    if (rules.lossBlocked) {
        Text("Un pedido pagado solo se da por perdido cuando falló el cliente.", style = MaterialTheme.typography.bodyMedium)
    } else {
        OutlinedButton(onClick = { actions.onAskLost(incident.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Dar por perdido") }
    }
}

@Composable
private fun NotesCard(order: AdminOrderDetail, actions: AdminOrderActions) {
    SoftCard {
        Text("Notas internas", style = MaterialTheme.typography.titleMedium)
        if (order.notes.isEmpty()) Text("Todavía no hay notas.", style = MaterialTheme.typography.bodyMedium)
        order.notes.forEach { note ->
            Column {
                Text(note.content, style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull(note.author?.displayName, formatTimestamp(note.createdAt)).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(onClick = actions.onOpenNote, modifier = Modifier.fillMaxWidth()) { Text("Agregar nota") }
    }
}

@Composable
private fun HistoryCard(order: AdminOrderDetail) {
    val events = order.history.filter { it.type != "ORDER_NOTE_ADDED" }
    if (events.isEmpty()) return
    SoftCard {
        Text("Historial", style = MaterialTheme.typography.titleMedium)
        events.forEach { e ->
            val what = when {
                e.type == "ORDER_CONFIRMED" -> "Pedido confirmado"
                e.toStatus != null -> "Pasó a ${e.toStatus.label}"
                else -> "Cambio de estado"
            }
            Text(
                listOfNotNull(what, e.actor?.displayName, formatTimestamp(e.occurredAt)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Notice(text: String, ok: Boolean) {
    Surface(shape = RoundedCornerShape(14.dp), color = if (ok) Color(0xFFDDF3E4) else Brand.ErrorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            text, color = if (ok) Color(0xFF14532D) else Brand.Error,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp),
        )
    }
}

// ---------------------------------------------------------------- Diálogos

@Composable
private fun Dialogs(dialog: AdminDialog, state: AdminOrderDetailState, actions: AdminOrderActions) {
    val busy = state.busy
    when (dialog) {
        AdminDialog.Note -> AlertDialog(
            onDismissRequest = actions.onCloseDialog,
            title = { Text("Nota interna") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.noteText, onValueChange = actions.onNoteChange, minLines = 3,
                        label = { Text("Nota (solo la ven los administradores)") }, modifier = Modifier.fillMaxWidth(),
                        supportingText = { noteHint(state.noteText)?.let { Text(it) } },
                    )
                    Text("${state.noteText.length}/2000", style = MaterialTheme.typography.labelLarge)
                    state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = actions.onSubmitNote, enabled = !busy && state.noteText.isNotBlank()) { Text(if (busy) "Guardando…" else "Guardar") } },
            dismissButton = { TextButton(onClick = actions.onCloseDialog, enabled = !busy) { Text("Cancelar") } },
        )

        is AdminDialog.Slot -> SlotDialog(dialog, state, actions)

        is AdminDialog.ConfirmLost -> ConfirmDialog(
            title = "Dar por perdido", busy = busy, confirmLabel = "Dar por perdido", error = state.actionError,
            text = "El pedido pasa a Perdido. No se reembolsa ni se repone el stock automáticamente.",
            onConfirm = actions.onConfirmLost, onDismiss = actions.onCloseDialog,
        )

        is AdminDialog.ConfirmUncollectible -> ConfirmDialog(
            title = "Dar por no cobrable", busy = busy, confirmLabel = "Confirmar", error = state.actionError,
            text = "El pedido sigue como entregado y no se reembolsa nada. La incidencia se cierra.",
            onConfirm = actions.onConfirmUncollectible, onDismiss = actions.onCloseDialog,
        )

        is AdminDialog.Cause -> AlertDialog(
            onDismissRequest = actions.onCloseDialog,
            title = { Text("¿Quién falló?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(IncidentCause.NEGOCIO, IncidentCause.CLIENTE).forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = dialog.cause == option, role = Role.RadioButton, onClick = { actions.onCauseChoice(option) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = dialog.cause == option, onClick = null)
                            Text(option.label, modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp))
                        }
                    }
                    Text(
                        if (dialog.cause == IncidentCause.NEGOCIO) "Si falló el negocio, el pedido pagado solo se reprograma una vez o se reembolsa."
                        else "Si falló el cliente, se puede reprogramar y, si no responde, se pierde sin reembolso. Corren de nuevo sus 24 horas.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = dialog.note, onValueChange = actions.onCauseNote, minLines = 2,
                        label = { Text("Nota (obligatoria, queda auditada)") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${dialog.note.length}/500", style = MaterialTheme.typography.labelLarge)
                    state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = actions.onSubmitCause, enabled = !busy && dialog.note.isNotBlank()) { Text(if (busy) "Guardando…" else "Guardar") } },
            dismissButton = { TextButton(onClick = actions.onCloseDialog, enabled = !busy) { Text("Cancelar") } },
        )

        is AdminDialog.ConfirmRefund -> ConfirmDialog(
            title = "Reembolsar total", busy = busy, confirmLabel = "Reembolsar", error = state.actionError,
            text = refundText(dialog.lateFee, lost = false),
            onConfirm = actions.onConfirmRefund, onDismiss = actions.onCloseDialog,
        )

        is AdminDialog.ConfirmRefundLostCharge -> ConfirmDialog(
            title = "Reembolsar cobro tras la pérdida", busy = busy, confirmLabel = "Reembolsar", error = state.actionError,
            text = refundText(dialog.lateFee, lost = true),
            onConfirm = actions.onConfirmRefundLostCharge, onDismiss = actions.onCloseDialog,
        )

        AdminDialog.ConfirmRestoreStock -> ConfirmDialog(
            title = "Reponer stock", busy = busy, confirmLabel = "Reponer", error = state.actionError,
            text = "Se devuelven las unidades al inventario. Solo se puede hacer una vez y el pedido ya no podrá reabrirse.",
            onConfirm = actions.onConfirmRestoreStock, onDismiss = actions.onCloseDialog,
        )

        is AdminDialog.ConfirmAdvance -> ConfirmDialog(
            title = "Confirmar cambio de estado", busy = busy, confirmLabel = "Confirmar", error = state.actionError,
            text = "¿Marcar ${dialog.label} como ${dialog.target.label}?",
            onConfirm = actions.onAdvance, onDismiss = actions.onCloseDialog,
        )

        is AdminDialog.Desist -> AlertDialog(
            onDismissRequest = actions.onCloseDialog,
            title = { Text("El cliente desistió") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("El pedido se cancela. ¿Se debe iniciar el reembolso completo?")
                    state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = { actions.onConfirmDesist(true) }, enabled = !busy) { Text("Cancelar y reembolsar") }
                    TextButton(onClick = { actions.onConfirmDesist(false) }, enabled = !busy) { Text("Cancelar sin reembolso") }
                }
            },
            dismissButton = { TextButton(onClick = actions.onCloseDialog, enabled = !busy) { Text("Volver") } },
        )

        is AdminDialog.Assign -> AlertDialog(
            onDismissRequest = actions.onCloseDialog,
            title = { Text(dialog.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dialog.current?.let { Text("Actual: $it", style = MaterialTheme.typography.bodyMedium) }
                    when {
                        dialog.loading -> CircularProgressIndicator()
                        dialog.error != null -> Text(dialog.error, color = MaterialTheme.colorScheme.error)
                        dialog.choices.isEmpty() -> Text("No hay personal disponible para elegir.")
                        else -> dialog.choices.forEach { ChoiceRow(it, busy) { actions.onChoose(it) } }
                    }
                    state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = actions.onCloseDialog, enabled = !busy) { Text("Cerrar") } },
        )
    }
}

private fun refundText(lateFee: Boolean, lost: Boolean): String = buildString {
    append("El pedido se cancela, se avisa al cliente y se inicia el reembolso total. ")
    append(if (lost) "Solo se devuelve el cobro recibido después de la pérdida. " else "El stock no cambia: lo decide con «Reponer stock». ")
    if (lateFee) append("La venta fue antes de hoy: el negocio pierde la comisión de la pasarela.")
}.trim()

@Composable
private fun ChoiceRow(choice: AssignChoice, busy: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = choice.enabled && !busy, modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Text(choice.label)
            choice.hint?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirmLabel: String, busy: Boolean, error: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy) { Text(if (busy) "Guardando…" else confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}

@Composable
private fun SlotDialog(dialog: AdminDialog.Slot, state: AdminOrderDetailState, actions: AdminOrderActions) {
    val reopening = dialog.incidentId == null
    AlertDialog(
        onDismissRequest = actions.onCloseDialog,
        title = { Text(if (reopening) "Reabrir en otra franja" else "Reasignar a otra franja") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    dialog.loading -> CircularProgressIndicator()
                    dialog.error != null -> Text(dialog.error, color = MaterialTheme.colorScheme.error)
                    dialog.slots.isEmpty() -> Text("No hay franjas con cupo en los próximos días.")
                    else -> dialog.slots.forEach { slot ->
                        val selected = dialog.selected == slot
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = { actions.onSelectSlot(slot) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = null)
                            Column(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp)) {
                                Text("${formatSlotDate(slot.slotDate)} · ${slot.startTime} – ${slot.endTime}")
                                Text("Cupos: ${slot.remainingCapacity}", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                if (!reopening) Text("Se avisará al cliente por correo.", style = MaterialTheme.typography.bodyMedium)
                state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onSubmitSlot, enabled = !state.busy && dialog.selected != null) {
                Text(if (state.busy) "Guardando…" else "Confirmar")
            }
        },
        dismissButton = { TextButton(onClick = actions.onCloseDialog, enabled = !state.busy) { Text("Cancelar") } },
    )
}

private fun Context.startSafely(intent: Intent) {
    try { startActivity(intent) } catch (_: ActivityNotFoundException) { /* no app can handle it; nothing to do */ }
}
