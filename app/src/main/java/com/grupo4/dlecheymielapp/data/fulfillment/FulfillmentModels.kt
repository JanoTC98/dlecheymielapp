package com.grupo4.dlecheymielapp.data.fulfillment

import kotlinx.serialization.Serializable

/** Unknown values from a newer backend decode as [UNKNOWN] (the Json has coerceInputValues on) instead of crashing. */
enum class OrderStatus(val label: String) {
    CONFIRMED("Confirmado"),
    PREPARING("En preparación"),
    READY_FOR_PICKUP("Listo para recoger"),
    IN_TRANSIT("En camino"),
    DELIVERED("Entregado"),
    CANCELLED("Cancelado"),
    PICKUP_EXPIRED("Plazo de recojo vencido"),
    LOST("Perdido"),
    UNKNOWN("Otro estado"),
    ;

    val isTerminal: Boolean get() = this == DELIVERED || this == CANCELLED || this == PICKUP_EXPIRED || this == LOST
}

enum class FulfillmentMode(val label: String) {
    PICKUP("Recojo en un punto"),
    INTERNAL_DELIVERY("Entrega en persona"),
    DELIVERY("Delivery"),
    UNKNOWN("Otra modalidad"),
}

enum class HandlerType { PICKUP, INTERNAL_DELIVERY, UNKNOWN }

enum class PaymentStatus(val label: String) {
    pending("Pago pendiente"),
    approved("Pagado"),
    rejected("Pago rechazado"),
    cancelled("Pago cancelado"),
    unknown("Pago en verificación"),
}

enum class IncidentStatus(val label: String) {
    OPEN("Abierta"),
    RESOLVED_REASSIGNED("Reasignada por el Administrador"),
    RESOLVED_LOST("Dada por perdida"),
    AUTO_LOST("Perdida automáticamente (24 h)"),
    ARCHIVED("Archivada"),
    RESOLVED_PAID("Cobrada"),
    RESOLVED_UNCOLLECTIBLE("Dada por no cobrable"),
    RESOLVED_DESISTED("El cliente desistió"),
    RESOLVED_REFUNDED("Reembolsada"),
    UNKNOWN("Otro estado"),
}

/** Times are already in America/Lima; show them as they come. */
@Serializable
data class FieldSchedule(val slotDate: String, val startTime: String, val endTime: String, val timezone: String? = null) {
    val timeRange: String get() = "$startTime – $endTime"
}

/** Present only for an order paid at delivery. */
@Serializable
data class PayAtDeliveryInfo(
    val paymentStatus: PaymentStatus = PaymentStatus.unknown,
    val codeVerified: Boolean = false,
    val linkAvailable: Boolean = false,
)

@Serializable
data class HandlerProfile(val id: Long, val displayName: String, val handlerType: HandlerType? = null)

@Serializable
data class Recipient(val name: String = "", val phone: String = "", val place: String = "", val reference: String? = null)

/** The contract allows null for each field (historical orders carry only some of them), so each one defaults to empty. */
@Serializable
data class PickupInfo(val universityName: String = "", val pickupPointName: String = "", val locationText: String = "")

@Serializable
data class HandlerOrderWork(
    val id: Long,
    val publicCode: String? = null,
    val status: OrderStatus = OrderStatus.UNKNOWN,
    val mode: FulfillmentMode = FulfillmentMode.UNKNOWN,
    val schedule: FieldSchedule,
    val pickup: PickupInfo? = null,
    val recipient: Recipient? = null,
    val groupKey: String? = null,
    val customerName: String = "",
    val incidentCount: Int = 0,
    val payAtDelivery: PayAtDeliveryInfo? = null,
    /** Older deployed endpoints use incidentCount for OPEN incidents too. */
    val openIncidentCount: Int = incidentCount,
)

@Serializable
data class Page<T>(val items: List<T>, val page: Int, val size: Int, val totalElements: Long = 0, val totalPages: Int = 0)

@Serializable
data class FieldIncident(
    val id: Long,
    val orderId: Long,
    val reporterName: String? = null,
    val description: String,
    val status: IncidentStatus = IncidentStatus.UNKNOWN,
    val recordedAt: String,
    val resolvedAt: String? = null,
    val notes: List<IncidentNote> = emptyList(),
    @kotlinx.serialization.Transient val addedAsNote: Boolean = false,
)

@Serializable
data class IncidentNote(
    val id: Long,
    val authorRole: String? = null,
    val authorName: String? = null,
    val text: String,
    val recordedAt: String,
)

@Serializable
data class HandoffRequest(val code: String)

@Serializable
data class IncidentRequest(val description: String)

/** `awaitingPayment`: the code is valid but the order is paid at delivery and stays open until the payment is approved. */
@Serializable
data class HandoffResult(
    val orderId: Long,
    val status: OrderStatus = OrderStatus.UNKNOWN,
    val completedAt: String? = null,
    val awaitingPayment: Boolean = false,
)

@Serializable
data class PaymentLink(val url: String)
