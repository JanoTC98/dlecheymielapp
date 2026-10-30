package com.grupo4.dlecheymielapp.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.grupo4.dlecheymielapp.R
import com.grupo4.dlecheymielapp.data.auth.AuthRole
import com.grupo4.dlecheymielapp.data.auth.AuthUser

/** Landing for every staff role until each role's work screens exist. */
@Composable
fun RoleHomeScreen(user: AuthUser, onLogout: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painter = painterResource(R.drawable.isologo), contentDescription = null, modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Hola, ${user.firstName}", style = MaterialTheme.typography.headlineMedium)
                Text(user.role.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(user.email, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Text(nextStepFor(user.role), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Cerrar sesión", style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun nextStepFor(role: AuthRole) = when (role) {
    AuthRole.DELIVERY_HANDLER -> "Próximamente: tus pedidos y grupos de recojo."
    AuthRole.DELIVERY_COORDINATOR -> "Próximamente: la cola de entregas externas."
    AuthRole.DELIVERY -> "Próximamente: tus pedidos asignados."
    AuthRole.ADMIN -> "Próximamente: pedidos, asignaciones e incidencias."
    AuthRole.CLIENT -> ""
}
