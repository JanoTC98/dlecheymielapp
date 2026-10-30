package com.grupo4.dlecheymielapp.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grupo4.dlecheymielapp.R
import com.grupo4.dlecheymielapp.ui.theme.Brand

/**
 * Top of every main screen, the mobile version of the web page header: brand mark, a small caps eyebrow, the title and
 * who is signed in. Signing out asks first, so a stray tap never throws away the session.
 */
@Composable
fun BrandHeader(eyebrow: String, title: String, subtitle: String?, onLogout: (() -> Unit)?, modifier: Modifier = Modifier) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = Color.White,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.background(Brush.horizontalGradient(listOf(Color.White, Brand.Mist.copy(alpha = 0.55f)))).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = RoundedCornerShape(14.dp), color = Brand.Mist, modifier = Modifier.size(46.dp)) {
                Image(painterResource(R.drawable.isologo), contentDescription = "D'Leche y Miel", modifier = Modifier.padding(6.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, letterSpacing = 1.4.sp), color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onLogout != null) IconButton(onClick = { confirm = true }) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Cerrar sesión") }
        }
    }
    if (confirm && onLogout != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Desea cerrar la sesión en este dispositivo?") },
            confirmButton = { Button(onClick = { confirm = false; onLogout() }) { Text("Cerrar sesión") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancelar") } },
        )
    }
}

class Stat(val label: String, val value: Int)

/** Three small counters under the header, like the summary strip of the web lists. */
@Composable
fun StatRow(stats: List<Stat>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { stat ->
            Surface(
                modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small, color = Brand.Mist.copy(alpha = 0.55f),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stat.value.toString(), style = MaterialTheme.typography.titleLarge, color = Brand.Ink)
                    Text(stat.label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 15.sp), color = Brand.Violet, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** "Bloque 10:00 – 10:30 (3 pedidos en esta franja)" as in the web list. */
@Composable
fun TimeBlockHeader(range: String, count: Int, noun: String = "pedido", modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = Brand.Ink) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(range, color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp))
            }
        }
        Text(
            "($count ${if (count == 1) noun else noun + "s"} en esta franja)",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Day title above its blocks. */
@Composable
fun DayTitle(slotDate: String, modifier: Modifier = Modifier) {
    Text(formatSlotDate(slotDate), style = MaterialTheme.typography.titleMedium, color = Brand.Violet, modifier = modifier.padding(top = 10.dp))
}

/** Friendly empty state: the brand mark and a sentence, instead of a bare line of text on a blank screen. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.isologo), contentDescription = null, modifier = Modifier.size(84.dp).alpha(0.55f))
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    }
}

/** Green confirmation strip; tap it to dismiss when a handler is given. */
@Composable
fun SuccessBanner(text: String, modifier: Modifier = Modifier, onDismiss: (() -> Unit)? = null) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = Color(0xFFDDF3E4),
        modifier = modifier.fillMaxWidth(),
        onClick = { onDismiss?.invoke() },
        enabled = onDismiss != null,
    ) {
        Text(text, color = Color(0xFF14532D), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}

@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}
