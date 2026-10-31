package com.grupo4.dlecheymielapp.data.fulfillment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "Aún no hay pedidos listos" only makes sense while something is still waiting to be delivered. */
class PickupWaitNoticeTest {
    private val slot = FieldSchedule("2026-11-04", "11:00", "12:00")

    private fun group(vararg orders: PickupGroupOrderWork) = PickupGroupWorkDetail("k", schedule = slot, orders = orders.toList())

    private fun order(status: OrderStatus, delivered: Boolean = false) =
        PickupGroupOrderWork(id = 1, status = status, delivered = delivered)

    @Test
    fun theNoticeShowsWhileOrdersArePendingButNoneIsReady() {
        assertTrue(group(order(OrderStatus.PREPARING)).awaitsReadyOrders)
        assertTrue(group(order(OrderStatus.CONFIRMED)).awaitsReadyOrders)
    }

    @Test
    fun aGroupWithEveryOrderDeliveredHasNothingToWaitFor() {
        assertFalse(group(order(OrderStatus.DELIVERED, delivered = true)).awaitsReadyOrders)
    }

    @Test
    fun aReadyOrderMeansNothingIsAwaited() {
        assertFalse(group(order(OrderStatus.READY_FOR_PICKUP), order(OrderStatus.PREPARING)).awaitsReadyOrders)
    }

    @Test
    fun cancelledOrdersAreNotWaitedFor() {
        assertFalse(group(order(OrderStatus.CANCELLED)).awaitsReadyOrders)
    }
}
