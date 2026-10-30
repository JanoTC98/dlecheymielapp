package com.grupo4.dlecheymielapp.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.grupo4.dlecheymielapp.data.admin.PickupGroupDetail
import com.grupo4.dlecheymielapp.data.admin.PickupGroupOrder
import com.grupo4.dlecheymielapp.data.admin.PickupGroupSummary
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.ui.common.BrandHeader
import com.grupo4.dlecheymielapp.ui.common.FreshnessNotice
import com.grupo4.dlecheymielapp.ui.common.LabeledRow
import com.grupo4.dlecheymielapp.ui.common.Pill
import com.grupo4.dlecheymielapp.ui.common.SoftCard
import com.grupo4.dlecheymielapp.ui.common.StatusPill
import com.grupo4.dlecheymielapp.ui.common.formatSlotDate
import com.grupo4.dlecheymielapp.ui.handler.CenteredMessage
import com.grupo4.dlecheymielapp.ui.theme.Brand

/** Recojo por grupo: lista de grupos (punto + franja) con su encargado. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminGroupsScreen(
    state: AdminGroupsState,
    onRefresh: () -> Unit,
    onOnlyUnassigned: (Boolean) -> Unit,
    onOpenGroup: (String) -> Unit,
    onLogout: (() -> Unit)? = null,
    userName: String? = null,
) {
    Column(Modifier.fillMaxSize()) {
        BrandHeader("ADMINISTRACIÓN", "Asignaciones", userName, onLogout)
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)) {
            Text(
                "Grupos de recojo. La entrega en persona y el delivery se asignan desde el detalle de cada pedido.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            FilterChip(selected = state.onlyUnassigned, onClick = { onOnlyUnassigned(!state.onlyUnassigned) }, label = { Text("Solo sin encargado") })
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                when {
                    state.error != null && state.groups.isEmpty() -> CenteredMessage(state.error, "Actualizar", onRefresh)
                    state.groups.isEmpty() -> CenteredMessage("No hay grupos de recojo en los próximos días. Deslice hacia abajo para actualizar.", "Actualizar", onRefresh)
                    else -> {
                        val byDay = state.groups.groupBy { it.schedule.slotDate }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            item(key = "freshness") { FreshnessNotice(state.lastUpdated, state.stale) }
                            byDay.forEach { (day, groups) ->
                                item(key = "day-$day") { Text(formatSlotDate(day), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
                                items(groups, key = { it.groupKey }) { GroupCard(it) { onOpenGroup(it.groupKey) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCard(group: PickupGroupSummary, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Text(group.pickupPoint.name ?: "Recojo", style = MaterialTheme.typography.titleMedium)
        Text("${group.schedule.timeRange}${group.university.name?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium)
        LabeledRow("Pedidos:", "${group.orderCount} (${group.deliveredCount} entregados, ${group.pendingCount} pendientes)")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val handler = group.assignedHandler
            if (handler != null) Pill(handler.displayName, Brand.Mist, Brand.Ink)
            else Pill("Sin encargado", Color(0xFFFFF1CC), Color(0xFF6B4A00))
            if (group.incidentCount > 0) Pill("${group.incidentCount} incidencia(s)", Brand.ErrorContainer, Brand.Error)
        }
    }
}

/** Un grupo: encargado, avanzar todo el grupo y sus pedidos. */
@Composable
fun AdminGroupDetailScreen(
    state: AdminGroupDetailState,
    nextStep: (PickupGroupDetail) -> Triple<OrderStatus, OrderStatus, String>?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAdvance: () -> Unit,
    onOpenCandidates: () -> Unit,
    onCloseCandidates: () -> Unit,
    onAssign: (Long) -> Unit,
    onAskUnassign: (Boolean) -> Unit,
    onUnassign: () -> Unit,
    onOpenOrder: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            Text(state.group?.pickupPoint?.name ?: "Grupo de recojo", style = MaterialTheme.typography.titleLarge)
        }
        val group = state.group
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            group == null -> CenteredMessage(state.error ?: "No se pudo cargar el grupo.", "Reintentar", onRetry)
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.message?.let { Banner(it, ok = true) }
                if (state.candidates == null && !state.confirmUnassign) state.actionError?.let { Banner(it, ok = false) }

                SoftCard {
                    Text("Franja", style = MaterialTheme.typography.titleMedium)
                    LabeledRow("Fecha:", formatSlotDate(group.schedule.slotDate))
                    LabeledRow("Hora:", group.schedule.timeRange)
                    LabeledRow("Universidad:", group.university.name)
                    LabeledRow("Lugar:", group.pickupPoint.locationText)
                }

                SoftCard {
                    Text("Encargado", style = MaterialTheme.typography.titleMedium)
                    Text(group.assignedHandler?.displayName ?: "Sin encargado asignado", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onOpenCandidates, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (group.assignedHandler == null) "Asignar encargado" else "Cambiar encargado")
                    }
                    if (group.assignedHandler != null) {
                        OutlinedButton(onClick = { onAskUnassign(true) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Quitar asignación") }
                    }
                }

                nextStep(group)?.let { step ->
                    SoftCard {
                        Text("Avanzar el grupo", style = MaterialTheme.typography.titleMedium)
                        Text("Mueve a la vez todos los pedidos que estén en ${step.first.label}.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onAdvance, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Guardando…" else step.third) }
                    }
                }

                SoftCard {
                    Text("Pedidos (${group.orders.size})", style = MaterialTheme.typography.titleMedium)
                    group.orders.forEach { OrderLine(it) { onOpenOrder(it.id) } }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    state.candidates?.let { candidates ->
        AlertDialog(
            onDismissRequest = onCloseCandidates,
            title = { Text("Elegir encargado de recojo") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        candidates.loading -> CircularProgressIndicator()
                        candidates.error != null -> Text(candidates.error, color = MaterialTheme.colorScheme.error)
                        candidates.items.isEmpty() -> Text("No hay encargados de recojo registrados.")
                        else -> candidates.items.forEach { c ->
                            val current = state.group?.assignedHandler?.id == c.id
                            OutlinedButton(onClick = { onAssign(c.id) }, enabled = c.available && !current && !state.busy, modifier = Modifier.fillMaxWidth()) {
                                Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
                                    Text(c.displayName)
                                    val hint = when {
                                        current -> "Asignado actualmente"
                                        !c.available -> c.conflict?.let { "Ocupado ${it.startTime}–${it.endTime}" } ?: "No disponible"
                                        else -> null
                                    }
                                    hint?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                                }
                            }
                        }
                    }
                    state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCloseCandidates, enabled = !state.busy) { Text("Cerrar") } },
        )
    }

    if (state.confirmUnassign) {
        AlertDialog(
            onDismissRequest = { onAskUnassign(false) },
            title = { Text("Quitar asignación") },
            text = { Text("Los pedidos del grupo quedan sin encargado hasta que asigne a otro.") },
            confirmButton = { TextButton(onClick = onUnassign, enabled = !state.busy) { Text(if (state.busy) "Guardando…" else "Quitar") } },
            dismissButton = { TextButton(onClick = { onAskUnassign(false) }, enabled = !state.busy) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun OrderLine(order: PickupGroupOrder, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = Brand.Mist.copy(alpha = 0.35f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(order.publicCode ?: "Pedido #${order.id}", style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(order.customerName, if (order.incidentCount > 0) "${order.incidentCount} incidencia(s)" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            StatusPill(order.status)
        }
    }
}

@Composable
private fun Banner(text: String, ok: Boolean) {
    Surface(shape = RoundedCornerShape(14.dp), color = if (ok) Color(0xFFDDF3E4) else Brand.ErrorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(text, color = if (ok) Color(0xFF14532D) else Brand.Error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}
