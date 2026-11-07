package com.grupo4.dlecheymielapp.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grupo4.dlecheymielapp.data.fulfillment.IncidentNote

/** Shared by the field roles and the Admin: the original report stays separate from follow-up notes. */
@Composable
fun IncidentNotes(notes: List<IncidentNote>) {
    notes.forEach { note ->
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Nota · ${note.authorName ?: "Personal"} · ${formatTimestamp(note.recordedAt)}", style = MaterialTheme.typography.labelLarge)
            Text(note.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
