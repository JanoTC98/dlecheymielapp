package com.grupo4.dlecheymielapp.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grupo4.dlecheymielapp.data.admin.IncidentNotification
import com.grupo4.dlecheymielapp.ui.common.BrandHeader
import com.grupo4.dlecheymielapp.ui.common.FreshnessNotice
import com.grupo4.dlecheymielapp.ui.common.Pill
import com.grupo4.dlecheymielapp.ui.common.SoftCard
import com.grupo4.dlecheymielapp.ui.common.formatTimestamp
import com.grupo4.dlecheymielapp.ui.handler.CenteredMessage
import com.grupo4.dlecheymielapp.ui.theme.Brand

/** The Admin bell: incidents reported from the field or lost automatically. Opening one jumps to its order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminNotificationsScreen(
    state: AdminNotificationsState,
    onRefresh: () -> Unit,
    onUnreadOnly: (Boolean) -> Unit,
    onOpen: (IncidentNotification) -> Unit,
    onLogout: (() -> Unit)? = null,
    userName: String? = null,
) {
    Column(Modifier.fillMaxSize()) {
        BrandHeader("ADMINISTRACIÓN", "Incidencias", userName, onLogout)
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 4.dp)) {
            Text(
                "Reportes del personal de campo. Toque uno para resolverlo en el pedido.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            FilterChip(selected = state.unreadOnly, onClick = { onUnreadOnly(!state.unreadOnly) }, label = { Text("Solo sin leer") })
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
                when {
                    state.error != null && state.items.isEmpty() -> CenteredMessage(state.error, "Actualizar", onRefresh)
                    state.items.isEmpty() -> CenteredMessage("No hay incidencias. Deslice hacia abajo para actualizar.", "Actualizar", onRefresh)
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item(key = "freshness") { FreshnessNotice(state.lastUpdated, state.stale) }
                        items(state.items, key = { it.id }) { NotificationCard(it) { onOpen(it) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: IncidentNotification, onClick: () -> Unit) {
    SoftCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.orderPublicCode ?: "Pedido #${item.orderId}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!item.read) Pill("Nueva", Brand.Error, androidx.compose.ui.graphics.Color.White)
        }
        if (item.summary.isNotBlank()) Text(item.summary, style = MaterialTheme.typography.bodyLarge)
        Text(
            listOfNotNull(
                if (item.kind == "INCIDENT_AUTO_LOST") "Perdido automáticamente" else item.reporterRole?.label,
                item.reporterName, formatTimestamp(item.recordedAt),
            ).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
