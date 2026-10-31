package com.grupo4.dlecheymielapp.ui.handler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grupo4.dlecheymielapp.R
import com.grupo4.dlecheymielapp.data.fulfillment.PickupGroupWork
import com.grupo4.dlecheymielapp.data.fulfillment.PickupGroupWorkDetail
import com.grupo4.dlecheymielapp.ui.common.BrandHeader
import com.grupo4.dlecheymielapp.ui.common.DayTitle
import com.grupo4.dlecheymielapp.ui.common.EmptyState
import com.grupo4.dlecheymielapp.ui.common.ErrorText
import com.grupo4.dlecheymielapp.ui.common.FreshnessNotice
import com.grupo4.dlecheymielapp.ui.common.RefreshButton
import com.grupo4.dlecheymielapp.ui.common.Pill
import com.grupo4.dlecheymielapp.ui.common.RemindersCard
import com.grupo4.dlecheymielapp.ui.common.SoftCard
import com.grupo4.dlecheymielapp.ui.common.Stat
import com.grupo4.dlecheymielapp.ui.common.StatRow
import com.grupo4.dlecheymielapp.ui.common.SuccessBanner
import com.grupo4.dlecheymielapp.ui.common.formatSlotDate
import com.grupo4.dlecheymielapp.ui.theme.Brand

/** Encargado de recojo: the groups (pickup point + time slot) assigned to them, by day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupGroupsScreen(state: HandlerListState, onRefresh: () -> Unit, onOpenGroup: (String) -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BrandHeader("RECOJO EN PUNTO", "Mis recojos", state.profile?.displayName, onLogout)
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                when {
                    state.error != null && state.groups.isEmpty() -> CenteredMessage(state.error, "Actualizar", onRefresh)

                    state.groups.isEmpty() -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        RemindersCard(state.reminders, Modifier.padding(16.dp))
                        EmptyState("No tiene grupos de recojo asignados por ahora. Deslice hacia abajo para actualizar.")
                        RefreshButton(onRefresh)
                    }

                    else -> {
                        val byDay = state.groups.groupBy { it.schedule.slotDate }
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            state.error?.let { item { ErrorText(it) } }
                            item(key = "freshness") { FreshnessNotice(state.lastUpdated, state.stale) }
                            item(key = "stats") {
                                StatRow(
                                    listOf(
                                        Stat("Grupos", state.groups.size),
                                        Stat("Pedidos pendientes", state.groups.sumOf { it.pendingCount }),
                                        Stat("Con incidencia", state.groups.count { it.incidentCount > 0 }),
                                    ),
                                )
                            }
                            if (state.reminders.isNotEmpty()) item(key = "reminders") { RemindersCard(state.reminders) }
                            byDay.forEach { (day, groups) ->
                                item(key = "day-$day") { DayTitle(day) }
                                items(groups, key = { it.groupKey }) { GroupCard(it) { onOpenGroup(it.groupKey) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Time chip + pickup point, the way the web draws a group's bar. */
@Composable
private fun GroupBar(timeRange: String, point: String?, detail: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(10.dp), color = Brand.Ink) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(timeRange, color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Storefront, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(18.dp))
                Text(point ?: "Punto de recojo", style = MaterialTheme.typography.titleMedium)
            }
            if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GroupCard(group: PickupGroupWork, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        GroupBar(group.schedule.timeRange, group.pickupPoint.name, listOfNotNull(group.university.name, group.pickupPoint.locationText).joinToString(" · "))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.pickup_pending_of_orders, group.orderCount, group.pendingCount, group.orderCount),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            if (group.pendingCount == 0) Pill("Todo entregado", Color(0xFFDDF3E4), Color(0xFF14532D))
            if (group.incidentCount > 0) Pill("${group.incidentCount} incid.", Brand.ErrorContainer, Brand.Error)
        }
    }
}

/** One group: its orders, each with the code field and the incident button, as at the counter of the web page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupGroupScreen(
    state: PickupGroupState,
    actions: CardActionsState,
    callbacks: CardCallbacks,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenOrder: (Long) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            Text(state.group?.pickupPoint?.name ?: "Grupo de recojo", style = MaterialTheme.typography.titleLarge)
        }
        val group = state.group
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            group == null -> CenteredMessage(state.error ?: "No se pudo cargar el grupo.", "Reintentar", onRetry)
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRetry, modifier = Modifier.fillMaxSize()) {
                GroupOrders(group, state.error, actions, callbacks, filter, { filter = it }, onOpenOrder)
            }
        }
    }
    CardActionDialogs(actions, callbacks)
}

@Composable
private fun GroupOrders(
    group: PickupGroupWorkDetail,
    error: String?,
    actions: CardActionsState,
    callbacks: CardCallbacks,
    filter: String,
    onFilter: (String) -> Unit,
    onOpenOrder: (Long) -> Unit,
) {
    val shown = group.orders.filter {
        filter.isBlank() || (it.publicCode ?: "").contains(filter.trim().removePrefix("#"), ignoreCase = true) || it.customerName.contains(filter.trim(), ignoreCase = true)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actions.notice?.let { notice -> item(key = "notice") { SuccessBanner(notice, onDismiss = callbacks::dismissNotice) } }
        error?.let { item(key = "error") { ErrorText(it) } }
        item(key = "bar") {
            SoftCard {
                GroupBar(group.schedule.timeRange, group.pickupPoint.name, listOfNotNull(group.university.name, group.pickupPoint.locationText).joinToString(" · "))
                Text(formatSlotDate(group.schedule.slotDate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val pending = group.orders.count { !it.delivered && !it.status.isTerminal }
                Text(pluralStringResource(R.plurals.pickup_pending_of_orders, group.orders.size, pending, group.orders.size), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (group.awaitsReadyOrders) {
            item(key = "wait") {
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF1CC), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Aún no hay pedidos listos. La entrega se habilita cuando el Administrador los marque como listos para recoger. Deslice hacia abajo para actualizar.",
                        color = Color(0xFF6B4A00), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
        if (group.orders.size > 3) item(key = "search") {
            OutlinedTextField(
                value = filter, onValueChange = onFilter, singleLine = true, label = { Text("Buscar por código o cliente") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (shown.isEmpty()) item(key = "none") { Text("Ningún pedido coincide.", style = MaterialTheme.typography.bodyMedium) }
        items(shown, key = { it.id }) { order ->
            FieldOrderCard(order.toCard(group), actions.card(order.id), callbacks, onOpen = { onOpenOrder(order.id) })
        }
    }
}
