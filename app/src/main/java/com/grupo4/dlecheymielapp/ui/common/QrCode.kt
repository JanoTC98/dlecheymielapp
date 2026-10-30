package com.grupo4.dlecheymielapp.ui.common

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * The payment QR, built on the phone like the web builds it in the browser: same link, error correction level M and a
 * quiet zone of 2 modules, so the customer scans the same code from either place. The link never leaves the device.
 */
fun qrMatrix(content: String): BitMatrix = QRCodeWriter().encode(
    content, BarcodeFormat.QR_CODE, 0, 0,
    mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8"),
)

/** One pixel per module (quiet zone included); draw it scaled up without smoothing so it stays sharp. */
fun qrBitmap(content: String): Bitmap {
    val matrix = qrMatrix(content)
    val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix[i % matrix.width, i / matrix.width]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

/** Message the staff sends with the link; identical to the web's. */
fun paymentMessage(url: String): String = "Hola, este es el link para pagar tu pedido de D'Leche y Miel: $url"

/** WhatsApp address: the customer's chat when the phone is known, otherwise the person picks the chat. */
fun paymentShareUrl(url: String, phone: String?): String {
    val base = whatsappUrl(phone) ?: "https://wa.me/"
    return "$base?text=" + java.net.URLEncoder.encode(paymentMessage(url), "UTF-8").replace("+", "%20")
}
