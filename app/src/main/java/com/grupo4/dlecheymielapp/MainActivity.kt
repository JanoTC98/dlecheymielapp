package com.grupo4.dlecheymielapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grupo4.dlecheymielapp.data.auth.AuthRole
import com.grupo4.dlecheymielapp.data.auth.SessionState
import com.grupo4.dlecheymielapp.ui.admin.AdminHost
import com.grupo4.dlecheymielapp.data.reminders.ReminderScope
import com.grupo4.dlecheymielapp.ui.handler.FieldQueueHost
import com.grupo4.dlecheymielapp.ui.handler.HandlerHost
import com.grupo4.dlecheymielapp.ui.home.RoleHomeScreen
import com.grupo4.dlecheymielapp.ui.login.LoginScreen
import com.grupo4.dlecheymielapp.ui.login.LoginViewModel
import com.grupo4.dlecheymielapp.ui.session.SessionScope
import com.grupo4.dlecheymielapp.ui.session.SessionStores
import com.grupo4.dlecheymielapp.ui.theme.DLecheTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DLecheApp).container
        setContent {
            DLecheTheme {
                Surface(Modifier.fillMaxSize()) { AppRoot(container) }
            }
        }
    }
}

@Composable
private fun AppRoot(container: AppContainer) {
    val auth = container.authRepository
    val session by auth.session.collectAsState()
    val scope = rememberCoroutineScope()
    val stores: SessionStores = viewModel()

    LaunchedEffect(Unit) { if (session is SessionState.Restoring) auth.restore() }
    // No ViewModel of a finished session may survive into the next one.
    LaunchedEffect(session is SessionState.SignedIn) { if (session !is SessionState.SignedIn) stores.clear() }

    when (val current = session) {
        SessionState.Restoring -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.isologo), contentDescription = "D'Leche y Miel", modifier = Modifier.size(120.dp))
        }

        SessionState.SignedOut -> {
            val vm: LoginViewModel = viewModel(factory = viewModelFactory { initializer { LoginViewModel(auth) } })
            val state by vm.state.collectAsState()
            LoginScreen(state, vm::onEmailChange, vm::onPasswordChange, vm::submit)
        }

        is SessionState.SignedIn -> SessionScope(current.user.id, stores) {
            val logout = { scope.launch { auth.logout() }; Unit }
            val userName = current.user.fullName
            when (current.user.role) {
                AuthRole.ADMIN -> AdminHost(
                    container.adminRepository, container.remindersRepository, userName = userName,
                    onUnauthorized = auth::onSessionExpired, onLogout = logout,
                )
                AuthRole.DELIVERY_HANDLER ->
                    HandlerHost(container.handlerRepository, container.remindersRepository, onUnauthorized = auth::onSessionExpired, onLogout = logout)
                AuthRole.DELIVERY -> FieldQueueHost(
                    gateway = container.deliveryRepository,
                    loadOrders = container.deliveryRepository::orders,
                    reminders = { container.remindersRepository.upcoming(ReminderScope.DELIVERY).items },
                    eyebrow = "DELIVERY", title = "Pedidos asignados", subtitle = userName,
                    emptyText = "No tiene pedidos de delivery asignados por ahora. Deslice hacia abajo para actualizar.",
                    onUnauthorized = auth::onSessionExpired, onLogout = logout,
                )
                AuthRole.DELIVERY_COORDINATOR -> FieldQueueHost(
                    gateway = container.operatorRepository,
                    loadOrders = container.operatorRepository::orders,
                    reminders = { container.remindersRepository.upcoming(ReminderScope.OPERATOR).items },
                    eyebrow = "OPERACIONES EXTERNAS", title = "Coordinación externa", subtitle = userName,
                    emptyText = "La cola está vacía por ahora. Deslice hacia abajo para actualizar.",
                    onUnauthorized = auth::onSessionExpired, onLogout = logout,
                )
                else -> RoleHomeScreen(current.user, onLogout = logout)
            }
        }
    }
}
