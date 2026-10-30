package com.grupo4.dlecheymielapp.data.admin

import com.grupo4.dlecheymielapp.data.fulfillment.FieldSchedule
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

enum class AssignmentKind { HANDLER, DELIVERY_EMPLOYEE, OPERATOR_QUEUE, UNKNOWN }

enum class IncidentType(val label: String) {
    MANUAL("Reporte del personal"),
    SIN_CAMBIO("Sin entrega ni reporte al terminar la franja"),
    IMPAGO("Entregado y no pagado"),
    UNKNOWN("Incidencia"),
}

enum class ReporterRole(val label: String) {
    DELIVERY_HANDLER("Encargado"),
    DELIVERY("Delivery"),
    DELIVERY_COORDINATOR("Operador"),
    UNKNOWN("Personal"),
}

enum class AdminIncidentStatus(val label: String, val isOpen: Boolean = false) {
    OPEN("Abierta", isOpen = true),
    RESOLVED_REASSIGNED("Reasignada"),
    RESOLVED_LOST("Dada por perdida"),
    AUTO_LOST("Perdida automáticamente (24 h)"),
    ARCHIVED("Archivada"),
    RESOLVED_PAID("Cobrada"),
    RESOLVED_UNCOLLECTIBLE("Dada por no cobrable"),
    RESOLVED_DESISTED("El cliente desistió"),
    UNKNOWN("Otro estado"),
}

enum class AdminPaymentStatus(val label: String) {
    pending("Pendiente"),
    approved("Pagado"),
    rejected("Rechazado"),
    cancelled("Cancelado"),
    unknown("En verificación"),
}

enum class PaymentTiming { PAY_FIRST, ON_DELIVERY, UNKNOWN }

@Serializable
data class AdminOrderSummary(
    val id: Long,
    val publicCode: String? = null,
    val status: OrderStatus = OrderStatus.UNKNOWN,
    val consolidatedAt: String = "",
    val amount: Double = 0.0,
    val customerDisplayName: String = "",
    val customerEmail: String = "",
    val fulfillmentMode: FulfillmentMode = FulfillmentMode.UNKNOWN,
    val schedule: FieldSchedule,
    val paymentStatus: AdminPaymentStatus = AdminPaymentStatus.unknown,
    val hasOpenPaymentIncident: Boolean = false,
    val incidentCount: Int = 0,
    val hasIncident: Boolean = false,
    val incidentStatus: String? = null,
    val incidentDeadlineAt: String? = null,
    val assignmentKind: AssignmentKind? = null,
    val assignedUserId: Long? = null,
    val pickupGroupKey: String? = null,
) {
    val hasOpenIncident: Boolean get() = incidentStatus == "OPEN"
}

@Serializable
data class NewOrderCount(val count: Long = 0)

@Serializable
data class AdminIncident(
    val id: Long,
    val orderId: Long,
    val reporterRole: ReporterRole? = null,
    val reporterName: String? = null,
    val type: IncidentType = IncidentType.MANUAL,
    val description: String = "",
    val status: AdminIncidentStatus = AdminIncidentStatus.UNKNOWN,
    val recordedAt: String = "",
    val deadlineAt: String? = null,
    val resolvedAt: String? = null,
    val resolvedBy: String? = null,
)

/** Only the fields the app shows; the snapshot carries many more. */
@Serializable
data class AdminSnapshot(
    val mode: FulfillmentMode = FulfillmentMode.UNKNOWN,
    val slotDate: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val universityName: String? = null,
    val pickupPointName: String? = null,
    val pickupPointLocationText: String? = null,
    val recipientName: String? = null,
    val recipientPhone: String? = null,
    val universityPlace: String? = null,
    val reference: String? = null,
    val deliveryDistrictName: String? = null,
    val deliveryPhone: String? = null,
    val deliveryAddress: String? = null,
    val deliveryReference: String? = null,
) {
    val schedule: FieldSchedule get() = FieldSchedule(slotDate, startTime, endTime)
}

@Serializable
data class AdminOrderItem(
    val lineNumber: Int = 0,
    val name: String = "",
    val quantity: Int = 0,
    val subtotal: Double = 0.0,
    /** Captured at checkout; null for orders created before the backend stored it. */
    val commercialSnapshot: AdminCommercialSnapshot? = null,
)

/** A PACK or COMBO component, as the backend captured it (CartDtos.ComponentSnapshot). */
@Serializable
data class AdminCommercialComponent(
    val productName: String? = null,
    val requestedQuantity: Int = 0,
    /** The backend's readable customization text, "Grupo: opción; ..."; empty when the component is not customized. */
    val summary: String? = null,
)

/** The commercial identity of an order line (CommercialSnapshot.java). Only the fields the kitchen needs are read. */
@Serializable
data class AdminCommercialSnapshot(
    val itemType: String? = null,
    val presentationName: String? = null,
    val components: List<AdminCommercialComponent> = emptyList(),
    /** Holds group and option ids only, so it is never shown; read as a tree so any shape is tolerated. */
    val configuration: JsonElement? = null,
)

/**
 * What the kitchen needs to prepare this line. Empty for a plain product, so it shows exactly as before. Components are listed
 * only for PACK and COMBO lines; a customized component shows the backend's own "Grupo: opción" text, one line per group.
 */
fun AdminOrderItem.preparationLines(): List<String> {
    val snapshot = commercialSnapshot ?: return emptyList()
    val lines = mutableListOf<String>()
    snapshot.presentationName?.takeIf { it.isNotBlank() }?.let { lines += "Presentación: $it" }
    if (snapshot.itemType == "PACK" || snapshot.itemType == "COMBO") {
        snapshot.components.forEach { component ->
            val name = component.productName?.takeIf { it.isNotBlank() } ?: return@forEach
            lines += "${component.requestedQuantity} × $name"
            component.summary.orEmpty().split("; ").filter { it.isNotBlank() }.forEach { lines += it }
        }
    }
    return lines
}

@Serializable
data class AdminMoney(val amount: Double = 0.0, val currency: String? = null)

/** Prices stored with the order at sale time. Read as they were; never recomputed from the current tariff. */
@Serializable
data class AdminPricingSnapshot(
    val total: Double? = null,
    val currency: String? = null,
    val internalDeliveryFee: AdminMoney? = null,
)

data class PriceLine(val quantity: Int, val name: String, val amount: Double)

data class PriceBreakdown(val items: List<PriceLine>, val deliveryFee: Double?, val total: Double?)

/**
 * The breakdown the order was sold with, or null when the backend sent no snapshot. Each line comes from the order's own
 * persisted items (name, quantity and subtotal together), never paired by position with the unnamed snapshot lines; the fee
 * and the total come from the snapshot. Amounts are never subtracted to derive a fee.
 */
fun AdminOrderDetail.priceBreakdown(): PriceBreakdown? {
    val pricing = pricingSnapshot ?: return null
    return PriceBreakdown(
        items = items.sortedBy { it.lineNumber }.map { PriceLine(it.quantity, it.name, it.subtotal) },
        deliveryFee = pricing.internalDeliveryFee?.amount,
        total = pricing.total,
    )
}

/** Labels for the backend's RefundStatus values. An unknown value is shown as it arrives. */
fun refundStatusLabel(raw: String): String = when (raw) {
    "refund_pending" -> "Solicitado o en curso"
    "refunded" -> "Completado"
    "refund_rejected" -> "Rechazado"
    else -> raw
}

@Serializable
data class AdminActor(val id: Long, val displayName: String)

@Serializable
data class AdminNote(val id: Long, val author: AdminActor? = null, val createdAt: String = "", val content: String = "")

@Serializable
data class AdminHistoryEvent(
    val id: String,
    val type: String = "",
    val occurredAt: String = "",
    val actor: AdminActor? = null,
    val fromStatus: OrderStatus? = null,
    val toStatus: OrderStatus? = null,
)

@Serializable
data class AdminOrderDetail(
    val id: Long,
    val publicCode: String? = null,
    val status: OrderStatus = OrderStatus.UNKNOWN,
    val amount: Double = 0.0,
    val consolidatedAt: String = "",
    val fulfillmentSnapshot: AdminSnapshot = AdminSnapshot(),
    val items: List<AdminOrderItem> = emptyList(),
    val paymentStatus: AdminPaymentStatus = AdminPaymentStatus.unknown,
    /** Null for orders created before the backend stored the price snapshot. */
    val pricingSnapshot: AdminPricingSnapshot? = null,
    /** The backend's RefundStatus value (for example "refund_pending"); null when there is no refund. */
    val refundStatus: String? = null,
    val notes: List<AdminNote> = emptyList(),
    val history: List<AdminHistoryEvent> = emptyList(),
    val incidents: List<AdminIncident> = emptyList(),
    val incidentStatus: String? = null,
    val incidentDeadlineAt: String? = null,
    val paymentTiming: PaymentTiming? = null,
    val stockRestored: Boolean = false,
    val stockRestoreAvailable: Boolean = false,
) {
    val isPayOnDelivery: Boolean get() = paymentTiming == PaymentTiming.ON_DELIVERY
    val openIncidents: List<AdminIncident> get() = incidents.filter { it.status.isOpen }
}

@Serializable
data class RescheduleSlot(
    val slotDate: String,
    val startTime: String,
    val endTime: String,
    val timezone: String? = null,
    val remainingCapacity: Int = 0,
)

/** The full slot identity: same day, same start and same end. */
fun RescheduleSlot.isSlotOf(snapshot: AdminSnapshot): Boolean =
    slotDate == snapshot.slotDate && startTime == snapshot.startTime && endTime == snapshot.endTime

/** Drops the slot the order is already in: moving the order there changes nothing. */
fun List<RescheduleSlot>.excludingSlotOf(snapshot: AdminSnapshot): List<RescheduleSlot> = filterNot { it.isSlotOf(snapshot) }

@Serializable
data class SlotBody(val slotDate: String, val startTime: String, val endTime: String)

@Serializable
data class Rescheduled(val orderId: Long, val status: OrderStatus = OrderStatus.UNKNOWN, val schedule: FieldSchedule? = null)

@Serializable
data class AdvanceBody(val expectedStatus: OrderStatus, val targetStatus: OrderStatus)

@Serializable
data class NoteBody(val content: String)

@Serializable
data class DesistBody(val refund: Boolean)

@Serializable
data class DesistResult(val orderId: Long, val refundStarted: Boolean = false)

@Serializable
data class RestoreResult(val orderId: Long, val restored: Boolean = false)

@Serializable
data class IncidentNotification(
    val id: Long,
    val kind: String = "",
    val orderId: Long,
    val orderPublicCode: String? = null,
    val mode: FulfillmentMode? = null,
    val reporterRole: ReporterRole? = null,
    val reporterName: String? = null,
    val summary: String = "",
    val recordedAt: String = "",
    val read: Boolean = false,
)

@Serializable
data class NotificationPage(val items: List<IncidentNotification>, val page: Int = 0, val size: Int = 20, val totalElements: Long = 0, val totalPages: Int = 0)

@Serializable
data class UnreadCount(val count: Long = 0)

// ---- Asignaciones ----

@Serializable
data class HandlerCandidate(
    val id: Long,
    val displayName: String,
    val available: Boolean = true,
    val conflict: SlotConflict? = null,
)

@Serializable
data class SlotConflict(val kind: String = "", val id: String = "", val slotDate: String = "", val startTime: String = "", val endTime: String = "")

@Serializable
data class CandidatePage(val items: List<HandlerCandidate>, val page: Int = 0, val hasNext: Boolean = false)

@Serializable
data class HandlerBody(val handlerId: Long)

@Serializable
data class OrderHandlerAssignment(val orderId: Long, val handlerId: Long? = null, val assignedAt: String? = null)

@Serializable
data class PickupGroupSummary(
    val groupKey: String,
    val university: NamedRef = NamedRef(),
    val pickupPoint: PickupPointRef = PickupPointRef(),
    val schedule: FieldSchedule,
    val orderCount: Int = 0,
    val deliveredCount: Int = 0,
    val pendingCount: Int = 0,
    val statusCounts: Map<String, Int> = emptyMap(),
    val incidentCount: Int = 0,
    val assignedHandler: AdminActor? = null,
)

@Serializable
data class NamedRef(val id: Long? = null, val name: String? = null)

@Serializable
data class PickupPointRef(val id: Long? = null, val name: String? = null, val locationText: String? = null)

@Serializable
data class PickupGroupPage(val items: List<PickupGroupSummary>, val page: Int = 0, val size: Int = 20, val totalElements: Long = 0, val totalPages: Int = 0)

@Serializable
data class PickupGroupOrder(
    val id: Long,
    val publicCode: String? = null,
    val status: OrderStatus = OrderStatus.UNKNOWN,
    val customerName: String? = null,
    val incidentCount: Int = 0,
    val delivered: Boolean = false,
)

@Serializable
data class PickupGroupDetail(
    val groupKey: String,
    val university: NamedRef = NamedRef(),
    val pickupPoint: PickupPointRef = PickupPointRef(),
    val schedule: FieldSchedule,
    val assignedHandler: AdminActor? = null,
    val orders: List<PickupGroupOrder> = emptyList(),
)

@Serializable
data class GroupAdvanceResult(val advanced: List<Long> = emptyList(), val skipped: List<SkippedOrder> = emptyList())

@Serializable
data class SkippedOrder(val id: Long, val reason: String = "")

@Serializable
data class DeliveryDestination(
    val orderId: Long,
    val destinationRole: String? = null,
    val employeeId: Long? = null,
    val employeeDisplayName: String? = null,
)

@Serializable
data class DestinationBody(val destinationRole: String, val employeeId: Long? = null)

@Serializable
data class StaffMember(
    val id: Long,
    val displayName: String,
    val email: String = "",
    val role: String = "",
    val handlerType: String? = null,
)

@Serializable
data class StaffPage(val items: List<StaffMember>, val page: Int = 0, val size: Int = 20, val hasNext: Boolean = false)
