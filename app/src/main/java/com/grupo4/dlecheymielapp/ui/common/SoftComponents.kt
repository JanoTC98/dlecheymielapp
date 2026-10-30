package com.grupo4.dlecheymielapp.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.data.fulfillment.PayAtDeliveryInfo
import com.grupo4.dlecheymielapp.data.fulfillment.PaymentStatus
import com.grupo4.dlecheymielapp.ui.theme.Brand
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** White card with the lilac 1.5dp border used across the soft design. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline), content = body,
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline), content = body,
        )
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = container) {
        Text(
            text, color = content, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun StatusPill(status: OrderStatus, modifier: Modifier = Modifier) {
    val (container, content) = when (status) {
        OrderStatus.CONFIRMED, OrderStatus.PREPARING -> Brand.Mist to Brand.Ink
        OrderStatus.READY_FOR_PICKUP, OrderStatus.IN_TRANSIT -> Brand.Lila to Color.White
        OrderStatus.DELIVERED -> Color(0xFFDDF3E4) to Color(0xFF14532D)
        OrderStatus.CANCELLED, OrderStatus.PICKUP_EXPIRED, OrderStatus.LOST -> Brand.ErrorContainer to Brand.Error
        OrderStatus.UNKNOWN -> Brand.Mist to Brand.Violet
    }
    Pill(status.label, container, content, modifier)
}

/** Payment-on-delivery badge; nothing is drawn for orders paid in advance. */
@Composable
fun PayBadge(info: PayAtDeliveryInfo?, modifier: Modifier = Modifier) {
    if (info == null) return
    val (container, content) = when (info.paymentStatus) {
        PaymentStatus.approved -> Color(0xFFDDF3E4) to Color(0xFF14532D)
        PaymentStatus.rejected, PaymentStatus.cancelled -> Brand.ErrorContainer to Brand.Error
        else -> Color(0xFFFFF1CC) to Color(0xFF6B4A00)
    }
    val text = "Contra entrega · " + info.paymentStatus.label + if (info.codeVerified && info.paymentStatus != PaymentStatus.approved) " · código validado" else ""
    Pill(text, container, content, modifier)
}

@Composable
fun LabeledRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val LIMA = Locale.forLanguageTag("es-PE")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", LIMA)

/** "2026-10-26" → "lunes 26 de octubre"; falls back to the raw text if the server sends something else. */
fun formatSlotDate(slotDate: String): String =
    runCatching { LocalDate.parse(slotDate).format(DAY_FORMAT).replaceFirstChar { it.uppercase(LIMA) } }.getOrDefault(slotDate)

private val TIME_FORMAT = DateTimeFormatter.ofPattern("d MMM, HH:mm", LIMA)
private val LIMA_ZONE = java.time.ZoneId.of("America/Lima")

/** Server timestamps (UTC instants or local date-times) → "26 oct, 10:42" in Lima time; unparseable text is shown as it came. */
fun formatTimestamp(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    return runCatching { java.time.Instant.parse(raw).atZone(LIMA_ZONE).format(TIME_FORMAT) }
        .recoverCatching { java.time.OffsetDateTime.parse(raw).atZoneSameInstant(LIMA_ZONE).format(TIME_FORMAT) }
        .recoverCatching { java.time.LocalDateTime.parse(raw).format(TIME_FORMAT) }
        .getOrDefault(raw.take(16).replace('T', ' '))
}

fun formatMoney(amount: Double): String = "S/ " + String.format(Locale.US, "%.2f", amount)
