package com.grupo4.dlecheymielapp.notifications

import com.grupo4.dlecheymielapp.data.fulfillment.FieldSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AlertSlotTextTest {
    @Test
    fun theSlotReadsLikeTheRestOfTheApp() {
        val text = alertSlotText(FieldSchedule("2026-11-06", "10:00", "11:00", "America/Lima"))
        assertEquals("Viernes 6 de noviembre · 10:00 – 11:00", text)
    }

    @Test
    fun aDateThatCannotBeParsedIsShownAsItCame() {
        val text = alertSlotText(FieldSchedule("pronto", "10:00", "11:00", "America/Lima"))
        assertFalse(text.isBlank())
        assertEquals("pronto · 10:00 – 11:00", text)
    }
}
