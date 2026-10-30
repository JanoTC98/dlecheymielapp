package com.grupo4.dlecheymielapp.ui.handler

import com.grupo4.dlecheymielapp.data.fulfillment.FieldIncident
import com.grupo4.dlecheymielapp.data.fulfillment.FieldSchedule
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerOrderWork
import com.grupo4.dlecheymielapp.data.fulfillment.IncidentStatus
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The delivery code can be entered at any time the order is ready, except while an incident is open (the Admin decides it). */
class OpenIncidentHandoffTest {
    private val order = HandlerOrderWork(
        id = 1, status = OrderStatus.IN_TRANSIT, mode = FulfillmentMode.INTERNAL_DELIVERY,
        schedule = FieldSchedule("2026-10-30", "10:00", "11:00"), customerName = "Ana G.",
    )

    private fun incident(status: IncidentStatus) = FieldIncident(1, 1, "Carlos", "No contesta", status, "2026-10-30T09:00:00")

    @Test
    fun theCodeIsAcceptedWhenThereIsNoIncident() {
        assertTrue(OrderDetailState(order = order).canHandoff)
    }

    @Test
    fun anOpenIncidentHoldsTheDelivery() {
        val state = OrderDetailState(order = order, incidents = listOf(incident(IncidentStatus.OPEN)))
        assertTrue(state.hasOpenIncident)
        assertFalse(state.canHandoff)
    }

    @Test
    fun aResolvedIncidentDoesNotHoldTheDelivery() {
        val state = OrderDetailState(order = order, incidents = listOf(incident(IncidentStatus.RESOLVED_REASSIGNED)))
        assertFalse(state.hasOpenIncident)
        assertTrue(state.canHandoff)
    }
}
