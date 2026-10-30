package com.grupo4.dlecheymielapp.ui.handler

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.grupo4.dlecheymielapp.data.fulfillment.FieldCapabilities
import com.grupo4.dlecheymielapp.data.fulfillment.FieldIncident
import com.grupo4.dlecheymielapp.data.fulfillment.PickupGroupOrderWork
import com.grupo4.dlecheymielapp.data.fulfillment.PickupGroupWork
import com.grupo4.dlecheymielapp.data.fulfillment.PickupGroupWorkDetail
import com.grupo4.dlecheymielapp.data.fulfillment.PickupPointInfo
import com.grupo4.dlecheymielapp.data.fulfillment.UniversityInfo
import com.grupo4.dlecheymielapp.data.reminders.UpcomingReminder
import com.grupo4.dlecheymielapp.data.fulfillment.FieldSchedule
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerOrderWork
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerProfile
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerType
import com.grupo4.dlecheymielapp.data.fulfillment.IncidentStatus
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.data.fulfillment.PayAtDeliveryInfo
import com.grupo4.dlecheymielapp.data.fulfillment.PaymentStatus
import com.grupo4.dlecheymielapp.data.fulfillment.Recipient
import com.grupo4.dlecheymielapp.ui.theme.DLecheTheme

private val slot = FieldSchedule("2026-10-26", "10:00", "10:30", "America/Lima")

private val sample = listOf(
    HandlerOrderWork(
        id = 41, publicCode = "DLM-0041", status = OrderStatus.IN_TRANSIT, mode = FulfillmentMode.INTERNAL_DELIVERY, schedule = slot,
        recipient = Recipient("Luis Pérez", "987654321", "Pabellón B, aula 204", "Frente a la biblioteca"), customerName = "Luis Pérez",
        incidentCount = 1, payAtDelivery = PayAtDeliveryInfo(PaymentStatus.pending, codeVerified = false, linkAvailable = true),
    ),
    HandlerOrderWork(
        id = 42, publicCode = "DLM-0042", status = OrderStatus.PREPARING, mode = FulfillmentMode.INTERNAL_DELIVERY, schedule = slot,
        recipient = Recipient("Marta Díaz", "912345678", "Cafetería central", null), customerName = "Marta Díaz",
    ),
    HandlerOrderWork(
        id = 43, publicCode = "DLM-0043", status = OrderStatus.DELIVERED, mode = FulfillmentMode.INTERNAL_DELIVERY,
        schedule = FieldSchedule("2026-10-26", "11:30", "12:00", "America/Lima"),
        recipient = Recipient("Ana Ruiz", "955443322", "Biblioteca", null), customerName = "Ana Ruiz",
    ),
)

@Composable
private fun Framed(content: @Composable () -> Unit) = DLecheTheme { Surface(Modifier.fillMaxSize()) { content() } }

@Preview(name = "Encargado: lista", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun OrdersPreview() = Framed {
    HandlerOrdersScreen(
        HandlerListState(loading = false, profile = HandlerProfile(3, "Carlos Quispe", HandlerType.INTERNAL_DELIVERY), orders = sample),
        CardActionsState(cards = mapOf(41L to CardState(code = "K7M2"))), NoCardCallbacks,
        onRefresh = {}, onOpenOrder = {}, onLogout = {},
    )
}

@Preview(name = "Encargado: lista vacía", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun OrdersEmptyPreview() = Framed {
    HandlerOrdersScreen(
        HandlerListState(loading = false, profile = HandlerProfile(3, "Carlos Quispe", HandlerType.INTERNAL_DELIVERY)),
        CardActionsState(), NoCardCallbacks,
        onRefresh = {}, onOpenOrder = {}, onLogout = {},
    )
}

@Preview(name = "Detalle: en camino, contra entrega", showBackground = true, widthDp = 360, heightDp = 1100)
@Composable
private fun DetailPreview() = Framed {
    DetailWith(
        OrderDetailState(
            loading = false, order = sample[0], code = "K7M2",
            incidents = listOf(FieldIncident(9, 41, "Carlos Quispe", "Cliente no contesta el teléfono", IncidentStatus.OPEN, "2026-10-26T09:42:00Z")),
        ),
    )
}

@Preview(name = "Detalle: en preparación", showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun DetailPreparingPreview() = Framed { DetailWith(OrderDetailState(loading = false, order = sample[1])) }

@Composable
private fun DetailWith(state: OrderDetailState) = OrderDetailScreen(
    state = state, onBack = {}, onRetry = {}, onCodeChange = {}, onSubmitHandoff = {}, onOpenIncident = {}, onCloseIncident = {},
    onIncidentTextChange = {}, onSubmitIncident = {}, onRequestPaymentLink = {},
)

private val deliveryOrder = HandlerOrderWork(
    id = 51, publicCode = "DLM-0051", status = OrderStatus.PREPARING, mode = FulfillmentMode.DELIVERY, schedule = slot,
    recipient = Recipient("Rosa Vega", "987654321", "Jr. Sol 45, Surco", "Puerta verde"), customerName = "Rosa Vega",
)

@Preview(name = "Delivery: preparando", showBackground = true, widthDp = 360, heightDp = 1000)
@Composable
private fun DeliveryDetailPreview() = Framed {
    DetailWith(OrderDetailState(loading = false, order = deliveryOrder, capabilities = FieldCapabilities(canAdvance = true)))
}

@Preview(name = "Operador: en camino, con proveedor", showBackground = true, widthDp = 360, heightDp = 1300)
@Composable
private fun OperatorDetailPreview() = Framed {
    DetailWith(
        OrderDetailState(
            loading = false, order = deliveryOrder.copy(status = OrderStatus.IN_TRANSIT),
            capabilities = FieldCapabilities(canAdvance = true, canComplete = true, canRegisterProvider = true),
            providerName = "InDrive Express", providerPhone = "999111222",
        ),
    )
}

@Preview(name = "Operador: cola con recordatorios", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun OperatorQueuePreview() = Framed {
    HandlerOrdersScreen(
        HandlerListState(
            loading = false, orders = listOf(deliveryOrder),
            reminders = listOf(UpcomingReminder(51, "DLM-0051", FulfillmentMode.DELIVERY, OrderStatus.PREPARING, "2026-10-26T10:00:00-05:00", "America/Lima", listOf(720))),
        ),
        CardActionsState(), object : CardCallbacks by NoCardCallbacks {
            override val capabilities = FieldCapabilities(canAdvance = true, canComplete = true, canRegisterProvider = true)
        },
        onRefresh = {}, onOpenOrder = {}, onLogout = {},
        eyebrow = "OPERACIONES EXTERNAS", title = "Coordinación externa", subtitle = "Operador de prueba",
    )
}

private val pickupGroup = PickupGroupWork(
    "pickup-1-20261026-1000", UniversityInfo(1, "Universidad Nacional"), PickupPointInfo(1, "Kiosco central", "Patio principal"),
    slot, orderCount = 4, deliveredCount = 1, pendingCount = 3,
)

@Preview(name = "Recojo: grupos", showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun PickupGroupsPreview() = Framed {
    PickupGroupsScreen(
        HandlerListState(loading = false, profile = HandlerProfile(4, "Lucía Ramos", HandlerType.PICKUP), groups = listOf(pickupGroup, pickupGroup.copy(groupKey = "k2", pendingCount = 0, deliveredCount = 4))),
        onRefresh = {}, onOpenGroup = {}, onLogout = {},
    )
}

@Preview(name = "Recojo: un grupo", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun PickupGroupPreview() = Framed {
    PickupGroupScreen(
        PickupGroupState(
            loading = false,
            group = PickupGroupWorkDetail(
                pickupGroup.groupKey, pickupGroup.university, pickupGroup.pickupPoint, slot,
                listOf(
                    PickupGroupOrderWork(1, "DLM-0001", OrderStatus.READY_FOR_PICKUP, "Ana Ruiz", payAtDelivery = PayAtDeliveryInfo(PaymentStatus.pending, false, true)),
                    PickupGroupOrderWork(2, "DLM-0002", OrderStatus.PREPARING, "Luis Pérez"),
                    PickupGroupOrderWork(3, "DLM-0003", OrderStatus.DELIVERED, "Marta Díaz", delivered = true),
                ),
            ),
        ),
        CardActionsState(cards = mapOf(1L to CardState(code = "K7M2Q9"))), NoCardCallbacks,
        onBack = {}, onRetry = {}, onOpenOrder = {},
    )
}
