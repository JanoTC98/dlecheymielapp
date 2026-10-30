package com.grupo4.dlecheymielapp.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grupo4.dlecheymielapp.data.reminders.UpcomingReminder
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LIMA_ZONE = ZoneId.of("America/Lima")
private val HOUR = DateTimeFormatter.ofPattern("HH:mm")

/** "en 45 min", "en 2 h 15 min" or "ya empezó"; null when the time cannot be read. */
fun timeUntil(slotStart: String, now: OffsetDateTime = OffsetDateTime.now(LIMA_ZONE)): String? {
    val start = runCatching { OffsetDateTime.parse(slotStart) }.getOrNull() ?: return null
    val minutes = Duration.between(now, start).toMinutes()
    return when {
        minutes <= 0 -> "ya empezó"
        minutes < 60 -> "en $minutes min"
        else -> "en ${minutes / 60} h" + if (minutes % 60 > 0) " ${minutes % 60} min" else ""
    }
}

fun reminderHour(slotStart: String): String? =
    runCatching { OffsetDateTime.parse(slotStart).atZoneSameInstant(LIMA_ZONE).format(HOUR) }.getOrNull()

/** The next jobs starting within 12 hours. Nothing is drawn when there are none. */
@Composable
fun RemindersCard(items: List<UpcomingReminder>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    SoftCard(modifier) {
        Text("Próximos trabajos (12 h)", style = MaterialTheme.typography.titleMedium)
        items.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(r.publicCode ?: "Pedido ${r.orderId}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    listOfNotNull(reminderHour(r.slotStart), timeUntil(r.slotStart)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(r.mode.label + " · " + r.status.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
