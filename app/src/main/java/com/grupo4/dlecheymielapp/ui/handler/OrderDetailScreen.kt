package com.grupo4.dlecheymielapp.ui.handler

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.grupo4.dlecheymielapp.data.fulfillment.FieldIncident
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerOrderWork
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.data.fulfillment.PaymentStatus
import com.grupo4.dlecheymielapp.ui.common.LabeledRow
import com.grupo4.dlecheymielapp.ui.common.PayBadge
import com.grupo4.dlecheymielapp.ui.common.SoftCard
import com.grupo4.dlecheymielapp.ui.common.StatusPill
import com.grupo4.dlecheymielapp.ui.common.formatSlotDate
import com.grupo4.dlecheymielapp.ui.common.formatTimestamp
import com.grupo4.dlecheymielapp.ui.common.whatsappUrl

@Composable
fun OrderDetailScreen(
    state: OrderDetailState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmitHandoff: () -> Unit,
    onOpenIncident: () -> Unit,
    onCloseIncident: () -> Unit,
    onIncidentTextChange: (String) -> Unit,
    onSubmitIncident: () -> Unit,
    onRequestPaymentLink: () -> Unit,
    onAskAdvance: (Boolean) -> Unit,
    onAdvance: () -> Unit = {},
    onAskComplete: (Boolean) -> Unit = {},
    onComplete: () -> Unit = {},
    onProviderName: (String) -> Unit = {},
    onProviderPhone: (String) -> Unit = {},
    onSaveProvider: () -> Unit = {},
) {
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            Text(state.order?.publicCode ?: "Pedido", style = MaterialTheme.typography.titleLarge)
        }

        val order = state.order
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            order == null -> CenteredMessage(state.loadError ?: "No se pudo cargar el pedido.", "Reintentar", onRetry)
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.loadError != null) Text(state.loadError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                SummaryCard(order, context)
                if (state.nextStep != null || state.actionMessage != null || state.actionError != null) AdvanceCard(state, onAdvance)
                if (order.payAtDelivery != null && !order.status.isTerminal) PaymentCard(order, state, onRequestPaymentLink, onRetry)
                HandoffCard(order, state, onCodeChange, onSubmitHandoff, onAskComplete)
                if (state.canEditProvider) ProviderCard(state, onProviderName, onProviderPhone, onSaveProvider)
                IncidentsCard(state, onOpenIncident)
                Box(Modifier.height(24.dp))
            }
        }
    }

    if (state.incidentDialogOpen) IncidentDialog(state, onCloseIncident, onIncidentTextChange, onSubmitIncident)
    if (state.confirmAdvance) {
        AlertDialog(
            onDismissRequest = { onAskAdvance(false) },
            title = { Text("Confirmar cambio de estado") },
            text = { Text("¿Marcar ${state.order?.publicCode ?: "este pedido"} como ${state.nextStep?.first?.label.orEmpty()}?") },
            confirmButton = { Button(onClick = onAdvance, enabled = !state.actionBusy) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { onAskAdvance(false) }, enabled = !state.actionBusy) { Text("Cancelar") } },
        )
    }
    if (state.confirmComplete) {
        AlertDialog(
            onDismissRequest = { onAskComplete(false) },
            title = { Text("Marcar como entregado") },
            text = { Text("Confirme que el pedido ya se entregó al cliente. Este paso no se puede deshacer.") },
            confirmButton = { Button(onClick = onComplete, enabled = !state.actionBusy) { Text("Sí, entregado") } },
            dismissButton = { TextButton(onClick = { onAskComplete(false) }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun AdvanceCard(state: OrderDetailState, onAdvance: () -> Unit) {
    SoftCard {
        Text("Preparación", style = MaterialTheme.typography.titleMedium)
        state.actionMessage?.let { SuccessText(it) }
        state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        state.nextStep?.let { (_, label) ->
            Button(onClick = onAdvance, enabled = !state.actionBusy, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                if (state.actionBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ProviderCard(state: OrderDetailState, onName: (String) -> Unit, onPhone: (String) -> Unit, onSave: () -> Unit) {
    SoftCard {
        Text("Proveedor de delivery", style = MaterialTheme.typography.titleMedium)
        Text("Servicio externo que lleva el pedido. Es un dato interno: el cliente nunca lo ve.", style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = state.providerName, onValueChange = onName, label = { Text("Nombre del proveedor") }, singleLine = true,
            enabled = !state.actionBusy, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
        )
        val phoneError = state.providerPhoneError
        OutlinedTextField(
            value = state.providerPhone, onValueChange = onPhone, label = { Text("Teléfono del proveedor") }, singleLine = true,
            enabled = !state.actionBusy, isError = phoneError != null, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone),
            supportingText = { phoneError?.let { Text(it) } },
        )
        if (state.providerSaved) SuccessText("Proveedor guardado.")
        OutlinedButton(onClick = onSave, enabled = state.providerReady && !state.actionBusy, modifier = Modifier.fillMaxWidth()) { Text("Guardar proveedor") }
    }
}

@Composable
private fun SummaryCard(order: HandlerOrderWork, context: Context) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(order.mode.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(order.status)
        }
        LabeledRow("Fecha:", formatSlotDate(order.schedule.slotDate))
        LabeledRow("Horario:", order.schedule.timeRange)
        LabeledRow("Cliente:", order.customerName)
        order.pickup?.let { p ->
            LabeledRow("Universidad:", p.universityName)
            LabeledRow("Punto:", p.pickupPointName)
            LabeledRow("Lugar:", p.locationText)
        }
        order.recipient?.let { r ->
            if (order.mode != FulfillmentMode.DELIVERY) LabeledRow("Recibe:", r.name)
            LabeledRow(if (order.mode == FulfillmentMode.DELIVERY) "Dirección:" else "Lugar:", r.place)
            LabeledRow("Referencia:", r.reference)
            LabeledRow("Teléfono:", r.phone)
            if (order.mode == FulfillmentMode.DELIVERY) {
                OutlinedButton(
                    onClick = {
                        val text = listOfNotNull(r.place.takeIf { it.isNotBlank() }, r.reference?.takeIf { it.isNotBlank() }?.let { "Ref.: $it" }).joinToString(". ")
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Dirección", text))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Copiar dirección") }
            }
            val dialable = r.phone.filter { it.isDigit() || it == '+' }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (dialable.isNotEmpty()) OutlinedButton(
                    onClick = { context.startSafely(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$dialable"))) },
                    modifier = Modifier.weight(1f),
                ) { Text("Llamar") }
                whatsappUrl(r.phone)?.let { url ->
                    // Opening WhatsApp never changes the order state.
                    OutlinedButton(onClick = { context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }, modifier = Modifier.weight(1f)) {
                        Text("WhatsApp")
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentCard(order: HandlerOrderWork, state: OrderDetailState, onRequestPaymentLink: () -> Unit, onRefresh: () -> Unit) {
    val info = order.payAtDelivery ?: return
    SoftCard {
        PaymentQrPanel(
            info = info, phone = order.recipient?.phone, url = state.paymentUrl, busy = state.linkBusy, error = state.linkError,
            onGenerate = onRequestPaymentLink, onRefresh = onRefresh,
        )
    }
}

@Composable
private fun HandoffCard(
    order: HandlerOrderWork,
    state: OrderDetailState,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onAskComplete: (Boolean) -> Unit,
) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    SoftCard {
        Text("Entrega", style = MaterialTheme.typography.titleMedium)
        state.handoffSuccess?.let { SuccessText(it) }
        when {
            order.status == OrderStatus.DELIVERED -> Text("Este pedido ya fue entregado.", style = MaterialTheme.typography.bodyMedium)

            order.status.isTerminal -> Text("Este pedido ya no admite entrega (${order.status.label.lowercase()}).", style = MaterialTheme.typography.bodyMedium)

            order.status != state.handoffStatus -> Text(
                when {
                    order.mode == FulfillmentMode.PICKUP -> "Aún no está listo para recoger. La entrega se habilita cuando la administración lo marque como listo."
                    state.capabilities.canAdvance -> "Aún no está en camino. Márquelo En camino para poder entregarlo."
                    else -> "Aún no está en camino. La entrega se habilita cuando la administración lo marque como En camino."
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            state.codeAlreadyValidated -> Text(
                "El código ya fue validado. El pedido se cerrará cuando el pago contra entrega esté aprobado.",
                style = MaterialTheme.typography.bodyMedium,
            )

            state.hasOpenIncident -> Text(
                "Este pedido tiene una incidencia abierta. La administración la resuelve (reasigna, reembolsa o la da por perdida); " +
                    "mientras tanto no se puede registrar la entrega.",
                style = MaterialTheme.typography.bodyMedium,
            )

            state.closesWithoutCode -> {
                Text("Cuando el cliente reciba el pedido, márquelo como entregado.", style = MaterialTheme.typography.bodyMedium)
                state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Button(
                    onClick = { onAskComplete(true) },
                    enabled = !state.actionBusy,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text("Marcar como entregado", style = MaterialTheme.typography.labelLarge) }
            }

            else -> {
                Text("Pida al cliente el código de 6 caracteres que le llegó por correo.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = state.code,
                    onValueChange = onCodeChange,
                    label = { Text("Código de entrega") },
                    singleLine = true,
                    enabled = !state.handoffBusy,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onSubmit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.handoffError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Button(
                    onClick = { focus.clearFocus(); onSubmit() },
                    enabled = state.canHandoff && state.codeReady,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    if (state.handoffBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Validar entrega", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun IncidentsCard(state: OrderDetailState, onOpen: () -> Unit) {
    SoftCard {
        Text("Incidencias", style = MaterialTheme.typography.titleMedium)
        if (state.incidentSaved) SuccessText(if (state.incidentAddedAsNote) "Se agregó a la incidencia abierta." else "Incidencia registrada. La administración fue avisada.")
        if (state.canReportIncident) {
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(if (state.hasOpenIncident) "Agregar nota" else "Reportar incidencia") }
        }
        if (state.incidentsLoadFailed) {
            Text("No se pudo actualizar la lista de incidencias. Se muestra la última que se cargó.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (state.incidents.isEmpty() && !state.incidentsLoadFailed) {
            Text("No hay incidencias registradas.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.incidents.forEach { IncidentRow(it) }
        }
    }
}

@Composable
private fun IncidentRow(incident: FieldIncident) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(incident.status.label, style = MaterialTheme.typography.titleMedium)
            Text(incident.description, style = MaterialTheme.typography.bodyMedium)
            com.grupo4.dlecheymielapp.ui.common.IncidentNotes(incident.notes)
            Text(
                listOfNotNull(incident.reporterName, formatTimestamp(incident.recordedAt)).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IncidentDialog(state: OrderDetailState, onClose: () -> Unit, onTextChange: (String) -> Unit, onSubmit: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!state.incidentBusy) onClose() },
        title = { Text(if (state.hasOpenIncident) "Agregar nota" else "Reportar incidencia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(incidentIntro(state.hasOpenIncident), style = MaterialTheme.typography.bodyMedium)
                val counter = "${state.incidentText.length}/$MAX_INCIDENT_LENGTH"
                OutlinedTextField(
                    value = state.incidentText,
                    onValueChange = onTextChange,
                    enabled = !state.incidentBusy,
                    minLines = 4,
                    supportingText = { Text(if (state.incidentText.isBlank()) "Describa la incidencia · $counter" else counter) },
                    modifier = Modifier.fillMaxWidth(),
                )
                state.incidentError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            Button(onClick = onSubmit, enabled = !state.incidentBusy && state.incidentText.isNotBlank()) {
                Text(if (state.incidentBusy) "Enviando…" else "Enviar")
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !state.incidentBusy) { Text("Cancelar") } },
    )
}

@Composable
private fun SuccessText(text: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = androidx.compose.ui.graphics.Color(0xFFDDF3E4), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = androidx.compose.ui.graphics.Color(0xFF14532D), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

internal fun Context.startSafely(intent: Intent) {
    try { startActivity(intent) } catch (_: ActivityNotFoundException) { /* no app can handle it; nothing to do */ }
}
