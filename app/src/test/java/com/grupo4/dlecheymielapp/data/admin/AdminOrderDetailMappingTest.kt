package com.grupo4.dlecheymielapp.data.admin

import com.grupo4.dlecheymielapp.core.network.InMemoryCookieStorage
import com.grupo4.dlecheymielapp.core.network.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Trimmed from the backend's admin order detail for an in-person delivery (order 12). */
private const val ORDER_WITH_PRICING = """
{
 "id": 12, "publicCode": "DLM-77YQS2P2", "status": "PREPARING", "amount": 29.0, "currency": "PEN",
 "fulfillmentSnapshot": {
  "mode": "INTERNAL_DELIVERY", "slotId": 207592160, "slotDate": "2026-11-02", "startTime": "12:00", "endTime": "13:00",
  "timezone": "America/Lima", "recipientName": "Valeria Huamán", "universityPlace": "Patio central",
  "internalDeliveryFee": {"amount": 5.0, "currency": "PEN"},
  "rescheduledFrom": {"slotId": 207602220, "slotDate": "2026-11-03", "startTime": "13:00", "endTime": "14:00"}
 },
 "pricingSnapshot": {
  "items": [{"currency": "PEN", "itemType": "PRODUCT", "quantity": 4, "subtotal": 24.0, "cartItemId": 32, "finalPrice": 6.0}],
  "total": 29.0, "currency": "PEN", "internalDeliveryFee": {"amount": 5.0, "currency": "PEN"}
 },
 "items": [{"lineNumber": 1, "itemType": "PRODUCT", "name": "Turrón de Doña Pepa (porción)", "sku": "BOC-TURR-01", "quantity": 4, "subtotal": 24.0, "currency": "PEN"}],
 "paymentStatus": "pending", "refundStatus": null
}
"""

/** Same kind of order without the pricing and refund fields, as one created before the snapshot existed. */
private const val ORDER_WITHOUT_PRICING = """
{
 "id": 1, "publicCode": "DLM-OLD", "status": "DELIVERED", "amount": 18.0,
 "fulfillmentSnapshot": {"mode": "PICKUP", "slotDate": "2026-10-20", "startTime": "11:00", "endTime": "12:00"},
 "items": [{"lineNumber": 1, "name": "Alfajor", "quantity": 3, "subtotal": 18.0}],
 "paymentStatus": "approved"
}
"""

class AdminOrderDetailMappingTest {
    private val json = NetworkModule("https://example.test/", InMemoryCookieStorage(), debugLogging = false).json

    private fun parse(body: String) = json.decodeFromString(AdminOrderDetail.serializer(), body)

    @Test
    fun thePriceBreakdownIsReadAsSoldWithItsSubtotalsFeeAndTotal() {
        val breakdown = parse(ORDER_WITH_PRICING).priceBreakdown()!!

        assertEquals(listOf(24.0), breakdown.items.map { it.amount })
        assertEquals(listOf(4), breakdown.items.map { it.quantity })
        assertEquals("Turrón de Doña Pepa (porción)", breakdown.items.single().name)
        assertEquals(5.0, breakdown.deliveryFee!!, 0.0)
        assertEquals(29.0, breakdown.total!!, 0.0)
    }

    @Test
    fun theDeliveryFeeIsTakenFromTheSnapshotNotDerivedFromTotalMinusItems() {
        // Subtracting the items from this total would give 11.00; the snapshot says the fee was 5.00.
        val breakdown = parse(ORDER_WITH_PRICING.replace("\"total\": 29.0", "\"total\": 35.0")).priceBreakdown()!!

        assertEquals(5.0, breakdown.deliveryFee!!, 0.0)
        assertEquals(35.0, breakdown.total!!, 0.0)
    }

    @Test
    fun eachLineKeepsItsOwnNameAndSubtotalEvenWhenTheSnapshotListsItemsInAnotherOrder() {
        // The snapshot lines carry no name; pairing them with the order items by position would put a price next to the wrong product.
        val twoLines = ORDER_WITH_PRICING
            .replace(
                """"items": [{"currency": "PEN", "itemType": "PRODUCT", "quantity": 4, "subtotal": 24.0, "cartItemId": 32, "finalPrice": 6.0}]""",
                """"items": [{"quantity": 2, "subtotal": 13.0}, {"quantity": 4, "subtotal": 24.0}]""",
            )
            .replace(
                """"items": [{"lineNumber": 1, "itemType": "PRODUCT", "name": "Turrón de Doña Pepa (porción)", "sku": "BOC-TURR-01", "quantity": 4, "subtotal": 24.0, "currency": "PEN"}]""",
                """"items": [{"lineNumber": 1, "name": "Turrón de Doña Pepa (porción)", "quantity": 4, "subtotal": 24.0}, {"lineNumber": 2, "name": "Cupcake de maracuyá", "quantity": 2, "subtotal": 13.0}]""",
            )

        val breakdown = parse(twoLines).priceBreakdown()!!

        assertEquals(
            listOf(PriceLine(4, "Turrón de Doña Pepa (porción)", 24.0), PriceLine(2, "Cupcake de maracuyá", 13.0)),
            breakdown.items,
        )
    }

    @Test
    fun anOrderWithoutPricingSnapshotStillParsesAndHasNoBreakdown() {
        val order = parse(ORDER_WITHOUT_PRICING)

        assertNull(order.pricingSnapshot)
        assertNull(order.priceBreakdown())
        assertNull(order.refundStatus)
        assertEquals("Alfajor", order.items.single().name)
        assertEquals(18.0, order.amount, 0.0)
    }

    @Test
    fun theBackendRefundValuesHaveSpanishLabelsAndUnknownValuesAreShownRaw() {
        assertEquals("Solicitado o en curso", refundStatusLabel("refund_pending"))
        assertEquals("Completado", refundStatusLabel("refunded"))
        assertEquals("Rechazado", refundStatusLabel("refund_rejected"))
        assertEquals("refund_queued_later", refundStatusLabel("refund_queued_later"))
    }

    @Test
    fun theRefundStatusIsReadFromTheOrder() {
        val order = parse(ORDER_WITH_PRICING.replace("\"refundStatus\": null", "\"refundStatus\": \"refunded\""))

        assertEquals("refunded", order.refundStatus)
    }

    @Test
    fun reassignmentOffersEverySlotExceptTheExactCurrentOne() {
        val current = parse(ORDER_WITH_PRICING).fulfillmentSnapshot
        val slots = listOf(
            RescheduleSlot("2026-11-02", "12:00", "13:00", "America/Lima", 5), // current: removed
            RescheduleSlot("2026-11-02", "13:00", "14:00", "America/Lima", 6), // same day, other hour
            RescheduleSlot("2026-11-03", "12:00", "13:00", "America/Lima", 5), // same hour, other day
            RescheduleSlot("2026-11-02", "12:00", "13:30", "America/Lima", 5), // same start, other end
        )

        val offered = slots.excludingSlotOf(current)

        assertEquals(3, offered.size)
        assertFalse(offered.any { it.slotDate == "2026-11-02" && it.startTime == "12:00" && it.endTime == "13:00" })
        assertTrue(offered.contains(slots[1]))
        assertTrue(offered.contains(slots[2]))
        assertTrue(offered.contains(slots[3]))
    }
}
