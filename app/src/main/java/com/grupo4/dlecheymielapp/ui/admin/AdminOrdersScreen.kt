package com.grupo4.dlecheymielapp.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.grupo4.dlecheymielapp.data.admin.AdminOrderSummary
import com.grupo4.dlecheymielapp.data.admin.AdminPaymentStatus
import com.grupo4.dlecheymielapp.data.admin.AssignmentKind
import com.grupo4.dlecheymielapp.data.admin.IncidentCause
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.ui.common.BrandHeader
import com.grupo4.dlecheymielapp.ui.common.FreshnessNotice
import com.grupo4.dlecheymielapp.ui.common.LabeledRow
import com.grupo4.dlecheymielapp.ui.common.Pill
import com.grupo4.dlecheymielapp.ui.common.RemindersCard
import com.grupo4.dlecheymielapp.ui.common.SoftCard
import com.grupo4.dlecheymielapp.ui.common.StatusPill
import com.grupo4.dlecheymielapp.ui.common.formatMoney
import com.grupo4.dlecheymielapp.ui.common.formatSlotDate
import com.grupo4.dlecheymielapp.ui.handler.CenteredMessage
import com.grupo4.dlecheymielapp.ui.theme.Brand

private val statusFilters = listOf(
    null to "Todos",
    OrderStatus.CONFIRMED to "Confirmados",
    OrderStatus.PREPARING to "En preparación",
    OrderStatus.READY_FOR_PICKUP to "Listos",
    OrderStatus.IN_TRANSIT to "En camino",
    OrderStatus.DELIVERED to "Entregados",
    OrderStatus.LOST to "Perdidos",
)

/** Admin queue: newest first, with a status filter, an "incidencias abiertas" switch and a code search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminOrdersScreen(
    state: AdminOrdersState,
    onRefresh: () -> Unit,
    onStatus: (OrderStatus?) -> Unit,
    onOpenIncidentsOnly: (Boolean) -> Unit,
    onSearch: (String) -> Unit,
    onOpenOrder: (Long) -> Unit,
    onLogout: () -> Unit,
    userName: String? = null,
) {
    Column(Modifier.fillMaxSize()) {
        val news = when {
            state.newCount <= 0 -> null
            state.newCount == 1L -> "1 pedido nuevo por preparar"
            else -> "${state.newCount} pedidos nuevos por preparar"
        }
        BrandHeader("ADMINISTRACIÓN", "Pedidos", listOfNotNull(userName, news).joinToString(" · ").ifBlank { null }, onLogout)

        SearchField(state.filters.code, onSearch)

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(statusFilters) { (status, label) ->
                FilterChip(selected = state.filters.status == status, onClick = { onStatus(status) }, label = { Text(label) })
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                val on = state.filters.incident == "open"
                FilterChip(selected = on, onClick = { onOpenIncidentsOnly(!on) }, label = { Text("Con incidencia abierta") })
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                when {
                    state.error != null && state.orders.isEmpty() -> CenteredMessage(state.error, "Actualizar", onRefresh)
                    state.orders.isEmpty() -> CenteredMessage("No hay pedidos con esos filtros. Deslice hacia abajo para actualizar.", "Actualizar", onRefresh)
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "freshness") { FreshnessNotice(state.lastUpdated, state.stale) }
                        if (state.reminders.isNotEmpty()) item(key = "reminders") { RemindersCard(state.reminders) }
                        items(state.orders, key = { it.id }) { OrderCard(it) { onOpenOrder(it.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(code: String, onSearch: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(code) }
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    OutlinedTextField(
        value = text, onValueChange = { text = it }, singleLine = true,
        label = { Text("Buscar por código") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); onSearch(text) }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/** Same marks as the web queue: a paid order nobody resolved is "Vencida" after 24 h and "Reembolso recomendado" after 72 h (business fault). */
internal fun incidentPillText(order: AdminOrderSummary): String {
    val paid = order.paymentStatus == AdminPaymentStatus.approved
    val level = order.incidentEscalationLevel
    return when {
        paid && order.incidentCause == IncidentCause.NEGOCIO && level >= 2 -> "Reembolso recomendado"
        paid && order.incidentCause != IncidentCause.CLIENTE && level >= 1 -> "Incidencia vencida"
        else -> "Incidencia abierta"
    }
}

@Composable
private fun OrderCard(order: AdminOrderSummary, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(order.publicCode ?: "Pedido #${order.id}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(order.status)
        }
        Text(order.customerDisplayName, style = MaterialTheme.typography.bodyLarge)
        LabeledRow("Entrega:", "${formatSlotDate(order.schedule.slotDate)} · ${order.schedule.timeRange}")
        LabeledRow("Modalidad:", order.fulfillmentMode.label)
        LabeledRow("Total:", formatMoney(order.amount))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (order.hasOpenIncident) Pill(incidentPillText(order), Brand.ErrorContainer, Brand.Error)
            else if (order.hasIncident) Pill("Incidencia resuelta", Brand.Mist, Brand.Violet)
            assignmentPill(order)?.let { (text, assigned) ->
                Pill(text, if (assigned) Brand.Mist else androidx.compose.ui.graphics.Color(0xFFFFF1CC), if (assigned) Brand.Ink else androidx.compose.ui.graphics.Color(0xFF6B4A00))
            }
        }
    }
}

/** What the card says about who holds the order; nothing for pickup orders (they are assigned by group). */
private fun assignmentPill(order: AdminOrderSummary): Pair<String, Boolean>? = when {
    order.status.isTerminal -> null
    order.assignmentKind == AssignmentKind.HANDLER -> "Con encargado" to true
    order.assignmentKind == AssignmentKind.DELIVERY_EMPLOYEE -> "Con Delivery" to true
    order.assignmentKind == AssignmentKind.OPERATOR_QUEUE -> "En cola del Operador" to true
    order.fulfillmentMode == FulfillmentMode.PICKUP && order.pickupGroupKey != null -> null
    order.fulfillmentMode == FulfillmentMode.UNKNOWN -> null
    else -> "Sin asignar" to false
}
