package com.grupo4.dlecheymielapp.ui.handler

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grupo4.dlecheymielapp.data.fulfillment.PayAtDeliveryInfo
import com.grupo4.dlecheymielapp.data.fulfillment.PaymentStatus
import com.grupo4.dlecheymielapp.ui.common.ErrorText
import com.grupo4.dlecheymielapp.ui.common.PayBadge
import com.grupo4.dlecheymielapp.ui.common.paymentShareUrl
import com.grupo4.dlecheymielapp.ui.common.qrBitmap

/**
 * How staff collect an order paid at delivery, as in the web's "Pago al recibir" panel: show the QR (or send the link) and
 * see whether the customer paid. The link is a credential: it lives only in [url] while the panel is open, is never logged
 * and the QR is drawn on this phone.
 */
@Composable
fun PaymentQrPanel(
    info: PayAtDeliveryInfo,
    phone: String?,
    url: String?,
    busy: Boolean,
    error: String?,
    onGenerate: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var copied by remember(url) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pago al recibir:", style = MaterialTheme.typography.titleMedium)
            PayBadge(info)
        }
        if (info.paymentStatus == PaymentStatus.approved) {
            Text(
                if (info.codeVerified) "El pedido se cierra automáticamente." else "El pago está confirmado. Ingrese el código de entrega para cerrar el pedido.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }
        if (info.codeVerified) Text("El código ya fue validado. El pedido se cerrará solo cuando el pago se confirme.", style = MaterialTheme.typography.bodyMedium)

        if (!info.linkAvailable && url == null) {
            Text(
                "El link o QR de pago estará disponible cuando empiece la franja de entrega.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Button(onClick = onGenerate, enabled = !busy, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().height(48.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(if (url != null) "Volver a generar" else "Link o QR de pago", style = MaterialTheme.typography.labelLarge)
            }
        }

        if (url != null) {
            val image = remember(url) { qrBitmap(url).asImageBitmap() }
            Surface(
                shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                // Scaled without smoothing so the modules stay crisp for the customer's camera.
                Image(image, contentDescription = "Código QR para pagar el pedido", filterQuality = FilterQuality.None, modifier = Modifier.padding(8.dp).size(250.dp))
            }
            Text(
                "Muestre el QR al cliente o envíele el link para que pague desde su celular.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { context.copySensitive(url); copied = true }, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) {
                    Text(if (copied) "Link copiado" else "Copiar link")
                }
                OutlinedButton(
                    onClick = { context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(paymentShareUrl(url, phone)))) },
                    shape = RoundedCornerShape(50), modifier = Modifier.weight(1f),
                ) { Text("WhatsApp") }
            }
            OutlinedButton(onClick = onRefresh, shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth()) { Text("Ya pagó: actualizar") }
        }
        error?.let { ErrorText(it) }
    }
}

private fun Context.copySensitive(text: String) {
    val clip = ClipData.newPlainText("Link de pago", text)
    // Keep the link out of keyboard suggestions and clipboard previews where the system supports it.
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    }
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}
