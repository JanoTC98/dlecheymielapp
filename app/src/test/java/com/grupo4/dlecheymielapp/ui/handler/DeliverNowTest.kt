package com.grupo4.dlecheymielapp.ui.handler

import com.grupo4.dlecheymielapp.data.fulfillment.FieldSchedule
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Para entregar ahora": the order is on its way AND its slot has already started, in the business time zone (Lima, UTC-5). */
class DeliverNowTest {
    private fun order(
        slot: FieldSchedule? = FieldSchedule("2026-10-31", "18:00", "19:00"),
        status: OrderStatus = OrderStatus.IN_TRANSIT,
        mode: FulfillmentMode = FulfillmentMode.DELIVERY,
    ) = CardOrder(id = 1, code = "DLM-1", status = status, mode = mode, name = "Rosa", place = null, schedule = slot)

    /** [hhmm] is the Lima wall clock on 2026-10-31. */
    private fun limaOn31(hhmm: String): Instant = Instant.parse("2026-10-31T${hhmm}:00-05:00")

    @Test
    fun aSlotLaterTodayHasNotStarted() {
        assertFalse(order().isForDeliveryAt(limaOn31("00:50")))
        assertFalse(order().isForDeliveryAt(limaOn31("17:59")))
    }

    @Test
    fun theSlotCountsFromItsFirstInstantAndWhileItRuns() {
        assertTrue(order().isForDeliveryAt(limaOn31("18:00")))
        assertTrue(order().isForDeliveryAt(limaOn31("18:30")))
    }

    @Test
    fun anOrderStillOpenAfterItsSlotIsOverdueAndKeepsCounting() {
        assertTrue(order().isForDeliveryAt(limaOn31("19:00")))
        assertTrue(order(FieldSchedule("2026-10-30", "10:00", "11:00")).isForDeliveryAt(limaOn31("00:50")))
    }

    @Test
    fun aFutureDayDoesNotCount() {
        assertFalse(order(FieldSchedule("2026-11-04", "15:00", "16:00")).isForDeliveryAt(limaOn31("23:59")))
    }

    @Test
    fun theOrderMustBeInTheHandoffStatusOfItsModality() {
        assertFalse(order(status = OrderStatus.CONFIRMED).isForDeliveryAt(limaOn31("18:30")))
        assertFalse(order(status = OrderStatus.DELIVERED).isForDeliveryAt(limaOn31("18:30")))
        assertTrue(order(status = OrderStatus.READY_FOR_PICKUP, mode = FulfillmentMode.PICKUP).isForDeliveryAt(limaOn31("18:30")))
    }

    @Test
    fun theBusinessZoneDecidesNotTheInstantsOffset() {
        // 18:00 in Lima is 23:00 UTC: the same instant written in UTC must give the same answer.
        assertFalse(order().isForDeliveryAt(Instant.parse("2026-10-31T22:59:00Z")))
        assertTrue(order().isForDeliveryAt(Instant.parse("2026-10-31T23:00:00Z")))
    }

    @Test
    fun aMissingOrUnreadableSlotFallsBackToTheStatusAlone() {
        assertTrue(order(slot = null).isForDeliveryAt(limaOn31("00:50")))
        assertTrue(order(FieldSchedule("sin fecha", "??", "??")).isForDeliveryAt(limaOn31("00:50")))
    }
}
