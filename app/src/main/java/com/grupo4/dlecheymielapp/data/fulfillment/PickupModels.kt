package com.grupo4.dlecheymielapp.data.fulfillment

import kotlinx.serialization.Serializable

@Serializable
data class UniversityInfo(val id: Long? = null, val name: String? = null)

@Serializable
data class PickupPointInfo(val id: Long? = null, val name: String? = null, val locationText: String? = null)

/** A pickup group of the signed-in Encargado: one pickup point and one time slot. The key is opaque; it comes from the server. */
@Serializable
data class PickupGroupWork(
    val groupKey: String,
    val university: UniversityInfo = UniversityInfo(),
    val pickupPoint: PickupPointInfo = PickupPointInfo(),
    val schedule: FieldSchedule,
    val orderCount: Int = 0,
    val deliveredCount: Int = 0,
    val pendingCount: Int = 0,
    val incidentCount: Int = 0,
    val openIncidentCount: Int = incidentCount,
)

@Serializable
data class PickupGroupOrderWork(
    val id: Long,
    val publicCode: String? = null,
    val status: OrderStatus = OrderStatus.UNKNOWN,
    val customerName: String = "",
    val incidentCount: Int = 0,
    val delivered: Boolean = false,
    val payAtDelivery: PayAtDeliveryInfo? = null,
    val openIncidentCount: Int = incidentCount,
)

@Serializable
data class PickupGroupWorkDetail(
    val groupKey: String,
    val university: UniversityInfo = UniversityInfo(),
    val pickupPoint: PickupPointInfo = PickupPointInfo(),
    val schedule: FieldSchedule,
    val orders: List<PickupGroupOrderWork> = emptyList(),
) {
    /** Handing over is only possible while at least one order is ready. */
    val hasReadyOrders: Boolean get() = orders.any { it.status == OrderStatus.READY_FOR_PICKUP }

    /** True while something is still waiting to become ready; a group with everything delivered or closed waits for nothing. */
    val awaitsReadyOrders: Boolean get() = !hasReadyOrders && orders.any { !it.delivered && !it.status.isTerminal }
}
