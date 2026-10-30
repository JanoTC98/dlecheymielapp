package com.grupo4.dlecheymielapp.ui.common

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ResultMetadataType
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCodeTest {
    private val url = "https://checkout.example.pe/pagar/abc123?token=Zx9_-k&orden=77"

    private fun decode(content: String) = qrMatrix(content).let { m ->
        val scale = 8
        val w = m.width * scale
        val h = m.height * scale
        val pixels = IntArray(w * h) { i -> if (m[(i % w) / scale, (i / w) / scale]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, pixels))))
    }

    @Test
    fun scanningTheQrGivesBackTheExactLink() {
        assertEquals(url, decode(url).text)
    }

    @Test
    fun usesTheSameErrorCorrectionLevelAsTheWeb() {
        assertEquals("M", decode(url).resultMetadata[ResultMetadataType.ERROR_CORRECTION_LEVEL])
    }

    @Test
    fun keepsAQuietZoneOfTwoModulesLikeTheWeb() {
        val m = qrMatrix(url)
        for (i in 0 until m.width) {
            assertFalse(m[i, 0]); assertFalse(m[i, 1]); assertFalse(m[i, m.height - 1]); assertFalse(m[i, m.height - 2])
        }
        for (j in 0 until m.height) {
            assertFalse(m[0, j]); assertFalse(m[1, j]); assertFalse(m[m.width - 1, j]); assertFalse(m[m.width - 2, j])
        }
    }

    @Test
    fun whatsappMessageMatchesTheWebAndEncodesTheLink() {
        assertEquals("Hola, este es el link para pagar tu pedido de D'Leche y Miel: $url", paymentMessage(url))
        val shared = paymentShareUrl(url, "987654321")
        assertTrue(shared, shared.startsWith("https://wa.me/51987654321?text="))
        assertTrue(shared, shared.contains("https%3A%2F%2Fcheckout.example.pe"))
        assertFalse("spaces must be %20", shared.contains("+"))
    }

    @Test
    fun withoutPhoneThePersonPicksTheChat() {
        assertTrue(paymentShareUrl(url, null).startsWith("https://wa.me/?text="))
    }
}
