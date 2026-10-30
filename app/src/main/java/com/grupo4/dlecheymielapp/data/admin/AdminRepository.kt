package com.grupo4.dlecheymielapp.data.admin

import com.grupo4.dlecheymielapp.core.network.apiCall
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import kotlinx.serialization.json.Json

/** Filters of the Admin queue; blank text is not sent. */
data class OrderFilters(
    val code: String = "",
    val status: OrderStatus? = null,
    val mode: FulfillmentMode? = null,
    /** `open`, `resolved` or `none`. */
    val incident: String? = null,
)

class AdminRepository(private val api: AdminApi, private val json: Json) {
    // ---- Pedidos ----
    suspend fun orders(filters: OrderFilters, page: Int = 0, size: Int = 30) = apiCall(json) {
        api.orders(
            page = page, size = size,
            code = filters.code.trim().ifEmpty { null },
            status = filters.status?.name,
            mode = filters.mode?.name,
            incident = filters.incident,
        )
    }

    suspend fun newCount() = apiCall(json) { api.newCount() }.count

    suspend fun order(id: Long) = apiCall(json) { api.order(id) }

    suspend fun addNote(id: Long, content: String) = apiCall(json) { api.addNote(id, NoteBody(content.trim())) }

    /** `expected` is what the screen believes the status is; a stale view gets `409 ORDER_STATE_CHANGED` instead of a silent jump. */
    suspend fun advance(id: Long, expected: OrderStatus, target: OrderStatus) =
        apiCall(json) { api.advance(id, AdvanceBody(expected, target)) }

    suspend fun paymentLink(id: Long) = apiCall(json) { api.paymentLink(id) }.url

    // ---- Incidencias ----
    suspend fun rescheduleSlots(id: Long, date: String? = null) = apiCall(json) { api.rescheduleSlots(id, date) }

    suspend fun reassign(orderId: Long, incidentId: Long, slot: RescheduleSlot) =
        apiCall(json) { api.reassign(orderId, incidentId, SlotBody(slot.slotDate, slot.startTime, slot.endTime)) }

    suspend fun markLost(orderId: Long, incidentId: Long) = apiCall(json) { api.markLost(orderId, incidentId) }

    suspend fun uncollectible(orderId: Long, incidentId: Long) = apiCall(json) { api.uncollectible(orderId, incidentId) }

    suspend fun desist(orderId: Long, incidentId: Long, refund: Boolean) =
        apiCall(json) { api.desist(orderId, incidentId, DesistBody(refund)) }

    suspend fun restoreStock(orderId: Long) = apiCall(json) { api.restoreStock(orderId) }

    suspend fun reopen(orderId: Long, slot: RescheduleSlot) =
        apiCall(json) { api.reopen(orderId, SlotBody(slot.slotDate, slot.startTime, slot.endTime)) }

    suspend fun notifications(unreadOnly: Boolean) = apiCall(json) { api.notifications(unreadOnly, 0, 30) }.items

    suspend fun unreadCount() = apiCall(json) { api.unreadCount() }.count

    suspend fun markRead(id: Long) = apiCall(json) { api.markRead(id) }

    // ---- Asignaciones ----
    suspend fun pickupGroups(assigned: Boolean? = null) = apiCall(json) { api.pickupGroups(0, 50, assigned = assigned) }.items

    suspend fun pickupGroup(key: String) = apiCall(json) { api.pickupGroup(key) }

    suspend fun groupCandidates(key: String) = apiCall(json) { api.groupCandidates(key, 0) }.items

    suspend fun assignGroup(key: String, handlerId: Long) = apiCall(json) { api.assignGroup(key, HandlerBody(handlerId)) }

    suspend fun unassignGroup(key: String) = apiCall(json) { api.unassignGroup(key) }

    suspend fun advanceGroup(key: String, expected: OrderStatus, target: OrderStatus) =
        apiCall(json) { api.advanceGroup(key, AdvanceBody(expected, target)) }

    suspend fun orderCandidates(id: Long) = apiCall(json) { api.orderCandidates(id, 0) }.items

    suspend fun orderAssignment(id: Long) = apiCall(json) { api.orderAssignment(id) }

    suspend fun assignOrder(id: Long, handlerId: Long) = apiCall(json) { api.assignOrder(id, HandlerBody(handlerId)) }

    suspend fun deliveryDestination(id: Long) = apiCall(json) { api.deliveryDestination(id) }

    suspend fun sendToDeliveryEmployee(id: Long, employeeId: Long) =
        apiCall(json) { api.setDeliveryDestination(id, DestinationBody("DELIVERY", employeeId)) }

    suspend fun sendToOperator(id: Long) =
        apiCall(json) { api.setDeliveryDestination(id, DestinationBody("DELIVERY_COORDINATOR")) }

    /** No destination means the Admin delivers it personally. */
    suspend fun clearDeliveryDestination(id: Long) = apiCall(json) { api.clearDeliveryDestination(id) }

    /** Delivery employees, for choosing a destination. The first 100 are enough for this business. */
    suspend fun deliveryEmployees() = apiCall(json) { api.staff(0, 100) }.items.filter { it.role == "DELIVERY" }
}
