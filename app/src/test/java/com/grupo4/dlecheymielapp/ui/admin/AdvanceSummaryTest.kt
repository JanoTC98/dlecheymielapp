package com.grupo4.dlecheymielapp.ui.admin

import org.junit.Assert.assertEquals
import org.junit.Test

/** The message after moving a whole pickup group agrees in number with what happened. */
class AdvanceSummaryTest {
    @Test
    fun oneOrderAdvancedUsesTheSingular() {
        assertEquals("Avanzó 1 pedido.", advanceSummary(advanced = 1, skipped = 0))
    }

    @Test
    fun severalOrdersUseThePlural() {
        assertEquals("Avanzaron 3 pedidos.", advanceSummary(advanced = 3, skipped = 0))
    }

    @Test
    fun skippedOrdersAreReportedWithTheirOwnNumber() {
        assertEquals("Avanzó 1 pedido. 1 no se pudo mover.", advanceSummary(advanced = 1, skipped = 1))
        assertEquals("Avanzaron 2 pedidos. 3 no se pudieron mover.", advanceSummary(advanced = 2, skipped = 3))
    }

    @Test
    fun nothingAdvancedStillReadsCorrectly() {
        assertEquals("No avanzó ningún pedido. 2 no se pudieron mover.", advanceSummary(advanced = 0, skipped = 2))
    }
}
