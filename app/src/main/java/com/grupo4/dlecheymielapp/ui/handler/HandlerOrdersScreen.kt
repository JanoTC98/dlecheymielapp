package com.grupo4.dlecheymielapp.ui.handler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grupo4.dlecheymielapp.ui.common.BrandHeader
import com.grupo4.dlecheymielapp.ui.common.DayTitle
import com.grupo4.dlecheymielapp.ui.common.EmptyState
import com.grupo4.dlecheymielapp.ui.common.ErrorText
import com.grupo4.dlecheymielapp.ui.common.RemindersCard
import com.grupo4.dlecheymielapp.ui.common.Stat
import com.grupo4.dlecheymielapp.ui.common.StatRow
import com.grupo4.dlecheymielapp.ui.common.SuccessBanner
import com.grupo4.dlecheymielapp.ui.common.TimeBlockHeader

/**
 * A field role's work list (Encargado de entrega en persona, Delivery, Operador): its orders by day and time block,
 * each card able to close the order on its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandlerOrdersScreen(
    state: HandlerListState,
    actions: CardActionsState,
    callbacks: CardCallbacks,
    onRefresh: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    onLogout: () -> Unit,
    eyebrow: String = "OPERACIONES",
    title: String = "Entrega en persona",
    subtitle: String? = null,
    emptyText: String = "No tiene pedidos asignados por ahora. Deslice hacia abajo para actualizar.",
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        BrandHeader(eyebrow, title, subtitle ?: state.profile?.displayName, onLogout)

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            state.error != null && state.orders.isEmpty() -> CenteredMessage(state.error, actionLabel = "Reintentar", onAction = onRefresh)

            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                if (state.orders.isEmpty()) {
                    Column(Modifier.fillMaxSize()) {
                        actions.notice?.let { SuccessBanner(it, Modifier.padding(16.dp), onDismiss = callbacks::dismissNotice) }
                        RemindersCard(state.reminders, Modifier.padding(16.dp))
                        EmptyState(emptyText)
                    }
                } else {
                    OrderBlocks(state, actions, callbacks, onOpenOrder)
                }
            }
        }
    }
    CardActionDialogs(actions, callbacks)
}

@Composable
private fun OrderBlocks(state: HandlerListState, actions: CardActionsState, callbacks: CardCallbacks, onOpenOrder: (Long) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    val cards = state.orders.map { it to it.toCard() }
    val readyNow = cards.count { (_, c) -> c.status == c.handoffStatus }
    val shown = cards.filter { (_, c) ->
        filter.isBlank() || (c.code ?: "").contains(filter.trim().removePrefix("#"), ignoreCase = true) || c.name.contains(filter.trim(), ignoreCase = true)
    }
    val byDay = shown.groupBy { (work, _) -> work.schedule.slotDate }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        actions.notice?.let { notice -> item(key = "notice") { SuccessBanner(notice, onDismiss = callbacks::dismissNotice) } }
        state.error?.let { item(key = "error") { ErrorText(it) } }
        item(key = "stats") {
            StatRow(
                listOf(
                    Stat("Pedidos", state.orders.size),
                    Stat("Para entregar ahora", readyNow),
                    Stat("Con incidencia", state.orders.count { it.incidentCount > 0 }),
                ),
            )
        }
        if (state.orders.size > 3) item(key = "search") {
            OutlinedTextField(
                value = filter, onValueChange = { filter = it }, singleLine = true, label = { Text("Buscar por código o cliente") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.reminders.isNotEmpty()) item(key = "reminders") { RemindersCard(state.reminders) }
        if (shown.isEmpty()) item(key = "none") { Text("Ningún pedido coincide.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) }

        byDay.forEach { (day, dayOrders) ->
            item(key = "day-$day") { DayTitle(day) }
            dayOrders.groupBy { (work, _) -> work.schedule.timeRange }.forEach { (range, block) ->
                item(key = "block-$day-$range") { TimeBlockHeader(range, block.size) }
                items(block, key = { (work, _) -> work.id }) { (work, card) ->
                    FieldOrderCard(card, actions.card(work.id), callbacks, onOpen = { onOpenOrder(work.id) })
                }
            }
        }
        item(key = "end") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
internal fun CenteredMessage(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
