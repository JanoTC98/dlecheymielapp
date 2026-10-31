package com.grupo4.dlecheymielapp.ui.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "El cliente desistió" only offers a refund when there is a charge to refund. */
class DesistPromptTest {
    @Test
    fun aPaidOrderAsksWhetherToRefund() {
        val prompt = desistPrompt(paid = true)
        assertTrue(prompt.canRefund)
        assertEquals("El pedido se cancela. ¿Se debe iniciar el reembolso completo?", prompt.text)
    }

    @Test
    fun anUnpaidOrderOffersNoRefund() {
        val prompt = desistPrompt(paid = false)
        assertFalse(prompt.canRefund)
        assertEquals("El pedido se cancela. No hay ningún cobro que reembolsar.", prompt.text)
    }
}
