package com.grupo4.dlecheymielapp.ui.admin

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.grupo4.dlecheymielapp.data.admin.AdminActor
import com.grupo4.dlecheymielapp.data.admin.AdminHistoryEvent
import com.grupo4.dlecheymielapp.data.admin.AdminIncident
import com.grupo4.dlecheymielapp.data.admin.AdminIncidentStatus
import com.grupo4.dlecheymielapp.data.admin.AdminNote
import com.grupo4.dlecheymielapp.data.admin.AdminOrderDetail
import com.grupo4.dlecheymielapp.data.admin.AdminOrderItem
import com.grupo4.dlecheymielapp.data.admin.AdminOrderSummary
import com.grupo4.dlecheymielapp.data.admin.AdminPaymentStatus
import com.grupo4.dlecheymielapp.data.admin.AdminSnapshot
import com.grupo4.dlecheymielapp.data.admin.AssignmentKind
import com.grupo4.dlecheymielapp.data.admin.IncidentNotification
import com.grupo4.dlecheymielapp.data.admin.IncidentType
import com.grupo4.dlecheymielapp.data.admin.NamedRef
import com.grupo4.dlecheymielapp.data.admin.PaymentTiming
import com.grupo4.dlecheymielapp.data.admin.PickupGroupDetail
import com.grupo4.dlecheymielapp.data.admin.PickupGroupOrder
import com.grupo4.dlecheymielapp.data.admin.PickupGroupSummary
import com.grupo4.dlecheymielapp.data.admin.PickupPointRef
import com.grupo4.dlecheymielapp.data.admin.ReporterRole
import com.grupo4.dlecheymielapp.data.fulfillment.FieldSchedule
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.ui.theme.DLecheTheme

private val slot = FieldSchedule("2026-10-26", "10:00", "10:30", "America/Lima")

private val orders = listOf(
    AdminOrderSummary(
        id = 7, publicCode = "DLM-0007", status = OrderStatus.IN_TRANSIT, consolidatedAt = "2026-10-25T14:00:00Z", amount = 45.5,
        customerDisplayName = "Rosa Vega", fulfillmentMode = FulfillmentMode.INTERNAL_DELIVERY, schedule = slot,
        paymentStatus = AdminPaymentStatus.approved, incidentCount = 1, hasIncident = true, incidentStatus = "OPEN",
        assignmentKind = AssignmentKind.HANDLER, assignedUserId = 3,
    ),
    AdminOrderSummary(
        id = 8, publicCode = "DLM-0008", status = OrderStatus.CONFIRMED, consolidatedAt = "2026-10-25T15:10:00Z", amount = 120.0,
        customerDisplayName = "Mario Paredes", fulfillmentMode = FulfillmentMode.DELIVERY, schedule = slot,
        paymentStatus = AdminPaymentStatus.approved,
    ),
    AdminOrderSummary(
        id = 9, publicCode = "DLM-0009", status = OrderStatus.PREPARING, consolidatedAt = "2026-10-25T15:40:00Z", amount = 32.0,
        customerDisplayName = "Elena Soto", fulfillmentMode = FulfillmentMode.PICKUP, schedule = slot,
        paymentStatus = AdminPaymentStatus.approved, pickupGroupKey = "pickup-1-20261026-1000",
    ),
)

private val detail = AdminOrderDetail(
    id = 7, publicCode = "DLM-0007", status = OrderStatus.IN_TRANSIT, amount = 45.5, consolidatedAt = "2026-10-25T14:00:00Z",
    fulfillmentSnapshot = AdminSnapshot(
        mode = FulfillmentMode.INTERNAL_DELIVERY, slotDate = "2026-10-26", startTime = "10:00", endTime = "10:30",
        recipientName = "Luis Pérez", recipientPhone = "987654321", universityPlace = "Pabellón B, aula 204", reference = "Frente a la biblioteca",
    ),
    items = listOf(AdminOrderItem(1, "Torta de chocolate", 1, 35.5), AdminOrderItem(2, "Alfajores x6", 1, 10.0)),
    paymentStatus = AdminPaymentStatus.pending, paymentTiming = PaymentTiming.ON_DELIVERY,
    notes = listOf(AdminNote(1, AdminActor(1, "Admin"), "2026-10-25T15:00:00Z", "Cliente pidió llamar antes de llegar")),
    history = listOf(
        AdminHistoryEvent("e1", "ORDER_CONFIRMED", "2026-10-25T14:00:00Z", null),
        AdminHistoryEvent("e2", "ORDER_STATUS_CHANGED", "2026-10-26T08:00:00Z", AdminActor(1, "Admin"), OrderStatus.PREPARING, OrderStatus.IN_TRANSIT),
    ),
    incidents = listOf(
        AdminIncident(
            9, 7, ReporterRole.DELIVERY_HANDLER, "Carlos Quispe", IncidentType.MANUAL, "Cliente no contesta el teléfono",
            AdminIncidentStatus.OPEN, "2026-10-26T14:42:00Z", deadlineAt = "2026-10-27T14:42:00Z",
        ),
    ),
    incidentStatus = "OPEN",
)

@Composable
private fun Framed(content: @Composable () -> Unit) = DLecheTheme { Surface(Modifier.fillMaxSize()) { content() } }

private val noActions = AdminOrderActions(
    {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
    { _, _ -> }, {}, {}, {}, {}, {}, {}, {},
    {}, {}, {}, {}, {},
)

@Preview(name = "Admin: pedidos", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun OrdersPreview() = Framed {
    AdminOrdersScreen(
        AdminOrdersState(loading = false, orders = orders, newCount = 1), onRefresh = {}, onStatus = {},
        onOpenIncidentsOnly = {}, onSearch = {}, onOpenOrder = {}, onLogout = {},
    )
}

@Preview(name = "Admin: detalle con incidencia", showBackground = true, widthDp = 360, heightDp = 1500)
@Composable
private fun DetailPreview() = Framed {
    AdminOrderDetailScreen(AdminOrderDetailState(loading = false, order = detail), nextStep = { null }, actions = noActions)
}

@Preview(name = "Admin: confirmado, por preparar", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun DetailConfirmedPreview() = Framed {
    AdminOrderDetailScreen(
        AdminOrderDetailState(loading = false, order = detail.copy(status = OrderStatus.CONFIRMED, incidents = emptyList(), paymentTiming = null, paymentStatus = AdminPaymentStatus.approved)),
        nextStep = { OrderStatus.PREPARING to "Pasar a En preparación" }, actions = noActions,
    )
}

@Preview(name = "Admin: grupos de recojo", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun GroupsPreview() = Framed {
    AdminGroupsScreen(
        AdminGroupsState(
            loading = false,
            groups = listOf(
                PickupGroupSummary(
                    "pickup-1-20261026-1000", NamedRef(1, "Universidad Nacional"), PickupPointRef(1, "Kiosco central", "Patio principal"),
                    slot, orderCount = 5, deliveredCount = 2, pendingCount = 3, assignedHandler = AdminActor(4, "Lucía Ramos"),
                ),
                PickupGroupSummary("pickup-2-20261026-1130", NamedRef(1, "Universidad Nacional"), PickupPointRef(2, "Biblioteca", null), slot, orderCount = 2, pendingCount = 2, incidentCount = 1),
            ),
        ),
        onRefresh = {}, onOnlyUnassigned = {}, onOpenGroup = {},
    )
}

@Preview(name = "Admin: detalle de grupo", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun GroupDetailPreview() = Framed {
    AdminGroupDetailScreen(
        AdminGroupDetailState(
            loading = false,
            group = PickupGroupDetail(
                "pickup-1-20261026-1000", NamedRef(1, "Universidad Nacional"), PickupPointRef(1, "Kiosco central", "Patio principal"), slot,
                orders = listOf(PickupGroupOrder(9, "DLM-0009", OrderStatus.PREPARING, "Elena Soto"), PickupGroupOrder(10, "DLM-0010", OrderStatus.CONFIRMED, "Hugo Ríos", 1)),
            ),
        ),
        nextStep = { Triple(OrderStatus.CONFIRMED, OrderStatus.PREPARING, "Pasar el grupo a En preparación") },
        onBack = {}, onRetry = {}, onAdvance = {}, onOpenCandidates = {}, onCloseCandidates = {}, onAssign = {},
        onAskUnassign = {}, onUnassign = {}, onOpenOrder = {},
    )
}

@Preview(name = "Admin: incidencias", showBackground = true, widthDp = 360, heightDp = 600)
@Composable
private fun NotificationsPreview() = Framed {
    AdminNotificationsScreen(
        AdminNotificationsState(
            loading = false,
            items = listOf(
                IncidentNotification(1, "INCIDENT_REPORTED", 7, "DLM-0007", FulfillmentMode.INTERNAL_DELIVERY, ReporterRole.DELIVERY_HANDLER, "Carlos Quispe", "Cliente no contesta el teléfono", "2026-10-26T14:42:00Z"),
                IncidentNotification(2, "INCIDENT_AUTO_LOST", 5, "DLM-0005", null, null, null, "Pasaron 24 h sin decisión", "2026-10-25T09:00:00Z", read = true),
            ),
        ),
        onRefresh = {}, onUnreadOnly = {}, onOpen = {},
    )
}
