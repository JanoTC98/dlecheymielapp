package com.grupo4.dlecheymielapp.ui.handler

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.grupo4.dlecheymielapp.data.fulfillment.FieldSchedule
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerOrderWork
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.data.fulfillment.PayAtDeliveryInfo
import com.grupo4.dlecheymielapp.data.fulfillment.PaymentStatus
import com.grupo4.dlecheymielapp.data.fulfillment.PickupGroupOrderWork
import com.grupo4.dlecheymielapp.data.fulfillment.PickupGroupWorkDetail
import com.grupo4.dlecheymielapp.ui.common.ErrorText
import com.grupo4.dlecheymielapp.ui.common.PayBadge
import com.grupo4.dlecheymielapp.ui.common.StatusPill
import com.grupo4.dlecheymielapp.ui.common.SuccessBanner
import com.grupo4.dlecheymielapp.ui.common.formatSlotDate
import com.grupo4.dlecheymielapp.ui.common.whatsappUrl
import com.grupo4.dlecheymielapp.ui.theme.Brand

/** What a list card needs to know about an order, whichever role or endpoint it came from. */
data class CardOrder(
    val id: Long,
    val code: String?,
    val status: OrderStatus,
    val mode: FulfillmentMode,
    val name: String,
    val place: String?,
    val reference: String? = null,
    val phone: String? = null,
    val schedule: FieldSchedule? = null,
    val payAtDelivery: PayAtDeliveryInfo? = null,
    val incidentCount: Int = 0,
) {
    /** The status in which the customer's code is asked: ready for pickup, or on its way for any delivery. */
    val handoffStatus: OrderStatus get() = if (mode == FulfillmentMode.PICKUP) OrderStatus.READY_FOR_PICKUP else OrderStatus.IN_TRANSIT
}

fun HandlerOrderWork.toCard() = CardOrder(
    id = id, code = publicCode, status = status, mode = mode,
    name = (recipient?.name ?: customerName).ifBlank { "Cliente sin nombre" },
    place = recipient?.place?.takeIf { it.isNotBlank() }
        ?: pickup?.let { listOf(it.pickupPointName, it.locationText).filter { part -> part.isNotBlank() }.joinToString(" · ") },
    reference = recipient?.reference?.takeIf { it.isNotBlank() },
    phone = recipient?.phone?.takeIf { it.isNotBlank() },
    schedule = schedule, payAtDelivery = payAtDelivery, incidentCount = incidentCount,
)

fun PickupGroupOrderWork.toCard(group: PickupGroupWorkDetail) = CardOrder(
    id = id, code = publicCode, status = if (delivered) OrderStatus.DELIVERED else status, mode = FulfillmentMode.PICKUP,
    name = customerName.ifBlank { "Cliente sin nombre" },
    place = listOfNotNull(group.pickupPoint.name, group.pickupPoint.locationText).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
    payAtDelivery = payAtDelivery, incidentCount = incidentCount,
)

private fun accentFor(status: OrderStatus): Color = when (status) {
    OrderStatus.IN_TRANSIT -> Color(0xFF059669)
    OrderStatus.READY_FOR_PICKUP -> Color(0xFFF97316)
    OrderStatus.PREPARING -> Color(0xFFEAB308)
    OrderStatus.DELIVERED -> Color(0xFF6D28D9)
    OrderStatus.CANCELLED, OrderStatus.PICKUP_EXPIRED, OrderStatus.LOST -> Color(0xFFDC2626)
    OrderStatus.CONFIRMED, OrderStatus.UNKNOWN -> Brand.Lilac
}

/**
 * One order in a list, with everything the web card offers: who and where, contact buttons, the customer's code right
 * on the card, the next step for Delivery and Operador, the order's detail and an incident report.
 */
@Composable
fun FieldOrderCard(order: CardOrder, card: CardState, callbacks: CardCallbacks, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(accentFor(order.status)))
            Column(Modifier.weight(1f).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(order.code ?: "Pedido ${order.id}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    StatusPill(order.status)
                }
                PayBadge(order.payAtDelivery)

                IconLine(Icons.Filled.Person, order.name, strong = true)
                order.place?.let { IconLine(Icons.Filled.Place, it + (order.reference?.let { ref -> " • $ref" } ?: "")) }
                order.schedule?.let { IconLine(Icons.Filled.Schedule, "${formatSlotDate(it.slotDate)} · ${it.timeRange}") }
                if (order.incidentCount > 0) {
                    IconLine(
                        Icons.Filled.WarningAmber,
                        if (order.incidentCount == 1) "1 incidencia registrada" else "${order.incidentCount} incidencias registradas",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }

                ContactButtons(order.phone, context)
                if (order.payAtDelivery != null && order.payAtDelivery.paymentStatus != PaymentStatus.approved && !order.status.isTerminal) {
                    OutlinedButton(onClick = { callbacks.openPayment(order) }, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(18.dp))
                        Text("Cobrar con QR o link")
                    }
                }
                HandoffArea(order, card, callbacks)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(50)) {
                        Text("Detalles")
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    }
                    Button(
                        onClick = { callbacks.openIncident(order.id, order.code ?: "Pedido ${order.id}") },
                        enabled = !order.status.isTerminal,
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(containerColor = Brand.ErrorContainer, contentColor = Brand.Error),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(16.dp))
                        Text("Incidencia")
                    }
                }
            }
        }
    }
}

@Composable
private fun IconLine(icon: ImageVector, text: String, strong: Boolean = false, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(18.dp))
        Text(
            text, modifier = Modifier.weight(1f),
            style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ContactButtons(phone: String?, context: android.content.Context) {
    val dialable = phone?.filter { it.isDigit() || it == '+' }.orEmpty()
    val whatsapp = whatsappUrl(phone.orEmpty())
    if (dialable.isEmpty() && whatsapp == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (dialable.isNotEmpty()) OutlinedButton(
            onClick = { context.startSafely(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialable"))) },
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(50),
        ) {
            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(16.dp))
            Text("Llamar")
        }
        // Opening WhatsApp never changes the order state.
        if (whatsapp != null) OutlinedButton(
            onClick = { context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(whatsapp))) },
            modifier = Modifier.weight(1f), shape = RoundedCornerShape(50),
        ) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(16.dp))
            Text("WhatsApp")
        }
    }
}

/** The part of the card that closes the order: depends on the status, on the role and on how it was paid. */
@Composable
private fun HandoffArea(order: CardOrder, card: CardState, callbacks: CardCallbacks) {
    card.message?.let { SuccessBanner(it) }
    card.error?.let { ErrorText(it) }

    val caps = callbacks.capabilities
    val closesWithoutCode = caps.canComplete && order.payAtDelivery == null
    when {
        order.status == OrderStatus.DELIVERED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF14532D), modifier = Modifier.size(18.dp))
            Text("Entregado", style = MaterialTheme.typography.titleMedium, color = Color(0xFF14532D))
        }

        order.status.isTerminal -> Unit

        order.status == order.handoffStatus && closesWithoutCode -> Button(
            onClick = { callbacks.askComplete(order.id) }, enabled = !card.busy, shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (card.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text("Marcar como entregado", style = MaterialTheme.typography.labelLarge)
        }

        order.status == order.handoffStatus && order.payAtDelivery?.codeVerified == true ->
            Text("Código validado. El pedido se cerrará cuando el cliente pague.", style = MaterialTheme.typography.bodyMedium)

        order.status == order.handoffStatus -> {
            OutlinedTextField(
                value = card.code, onValueChange = { callbacks.onCode(order.id, it) },
                label = { Text("Código de entrega del cliente") }, singleLine = true, enabled = !card.busy,
                shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { callbacks.validate(order.id) }),
            )
            Button(
                onClick = { callbacks.validate(order.id) }, enabled = card.code.isNotBlank() && !card.busy, shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (card.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("Validar entrega", style = MaterialTheme.typography.labelLarge)
            }
        }

        caps.canAdvance && nextStepOf(order.status) != null -> Button(
            onClick = { callbacks.advance(order.id, order.status, order.code ?: "Pedido ${order.id}") }, enabled = !card.busy, shape = RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (card.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(nextStepOf(order.status)!!.second, style = MaterialTheme.typography.labelLarge)
        }

        else -> Text(
            if (order.mode == FulfillmentMode.PICKUP) "La entrega se habilita cuando el Administrador lo marque como listo para recoger."
            else "La entrega se habilita cuando el pedido esté En camino.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The incident and "mark as delivered" dialogs the cards open; draw once per screen. */
@Composable
fun CardActionDialogs(state: CardActionsState, callbacks: CardCallbacks) {
    state.incident?.let { draft ->
        AlertDialog(
            onDismissRequest = { if (!draft.busy) callbacks.closeIncident() },
            title = { Text("Incidencia en ${draft.label}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cuente qué pasó (cliente ausente, dirección incorrecta, no contesta…).", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = draft.text, onValueChange = callbacks::onIncidentText, enabled = !draft.busy, minLines = 4,
                        supportingText = { Text("${draft.text.length}/$MAX_INCIDENT_LENGTH") }, modifier = Modifier.fillMaxWidth(),
                    )
                    draft.error?.let { ErrorText(it) }
                }
            },
            confirmButton = {
                Button(onClick = callbacks::submitIncident, enabled = !draft.busy && draft.text.isNotBlank()) { Text(if (draft.busy) "Enviando…" else "Enviar") }
            },
            dismissButton = { TextButton(onClick = callbacks::closeIncident, enabled = !draft.busy) { Text("Cancelar") } },
        )
    }
    state.payment?.let { draft ->
        AlertDialog(
            onDismissRequest = callbacks::closePayment,
            title = { Text("Cobro de ${draft.label}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PaymentQrPanel(
                        info = draft.info, phone = draft.phone, url = draft.url, busy = draft.busy, error = draft.error,
                        onGenerate = callbacks::generatePayment, onRefresh = callbacks::refreshPayment,
                    )
                }
            },
            confirmButton = { TextButton(onClick = callbacks::closePayment) { Text("Cerrar") } },
        )
    }
    state.advanceFor?.let { pending ->
        AlertDialog(
            onDismissRequest = callbacks::cancelAdvance,
            title = { Text("Confirmar cambio de estado") },
            text = { Text("¿Marcar ${pending.label} como ${pending.target.label}?") },
            confirmButton = { Button(onClick = callbacks::confirmAdvance) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = callbacks::cancelAdvance) { Text("Cancelar") } },
        )
    }
    if (state.completeFor != null) {
        AlertDialog(
            onDismissRequest = { callbacks.askComplete(null) },
            title = { Text("Marcar como entregado") },
            text = { Text("Confirme que el pedido ya se entregó al cliente. Este paso no se puede deshacer.") },
            confirmButton = { Button(onClick = callbacks::complete) { Text("Sí, entregado") } },
            dismissButton = { TextButton(onClick = { callbacks.askComplete(null) }) { Text("Cancelar") } },
        )
    }
}
