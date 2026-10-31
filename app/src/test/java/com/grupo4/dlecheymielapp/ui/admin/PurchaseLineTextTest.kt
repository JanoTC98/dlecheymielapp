package com.grupo4.dlecheymielapp.ui.admin

import com.grupo4.dlecheymielapp.data.admin.AdminOrderItem
import org.junit.Assert.assertEquals
import org.junit.Test

/** The amount beside an order line is the subtotal of the whole line, and the text must say so. */
class PurchaseLineTextTest {
    @Test
    fun theAmountIsLabelledAsTheSubtotalOfTheLine() {
        val line = AdminOrderItem(name = "Rol de canela", quantity = 2, subtotal = 12.0)
        assertEquals("Rol de canela · Subtotal: S/ 12.00", purchaseLineValue(line))
    }

    @Test
    fun aSingleUnitIsLabelledTheSameWay() {
        val line = AdminOrderItem(name = "Galleta con chispas de chocolate", quantity = 1, subtotal = 4.5)
        assertEquals("Galleta con chispas de chocolate · Subtotal: S/ 4.50", purchaseLineValue(line))
    }
}
