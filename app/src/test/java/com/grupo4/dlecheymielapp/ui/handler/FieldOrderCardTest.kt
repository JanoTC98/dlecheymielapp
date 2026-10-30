package com.grupo4.dlecheymielapp.ui.handler

import com.grupo4.dlecheymielapp.data.fulfillment.PayAtDeliveryInfo
import com.grupo4.dlecheymielapp.data.fulfillment.PaymentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldOrderCardTest {
    @Test
    fun thePaymentLinkIsOfferedOnlyWhenTheBackendSaysTheSlotHasStarted() {
        assertEquals(
            "El cobro se habilita cuando empiece la franja de entrega.",
            paymentLinkNotice(PayAtDeliveryInfo(PaymentStatus.pending, codeVerified = true, linkAvailable = false)),
        )
        assertNull(paymentLinkNotice(PayAtDeliveryInfo(PaymentStatus.pending, codeVerified = false, linkAvailable = true)))
    }

    @Test
    fun aValidatedCodeWithPaymentPendingShowsExactlyOneLine() {
        // Right after the validation the banner carries the message; the card must not repeat it.
        val afterValidation = CardState(message = AWAITING_PAYMENT_MESSAGE)
        // After a reload the banner is gone and the card states the same thing once.
        val afterReload = CardState()

        assertEquals(listOf(AWAITING_PAYMENT_MESSAGE), listOfNotNull(afterValidation.message, codeValidatedLine(afterValidation, codeVerified = true)))
        assertEquals(listOf(AWAITING_PAYMENT_MESSAGE), listOfNotNull(afterReload.message, codeValidatedLine(afterReload, codeVerified = true)))
    }
}
