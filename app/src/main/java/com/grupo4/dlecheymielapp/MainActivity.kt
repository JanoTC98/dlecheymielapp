package com.grupo4.dlecheymielapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grupo4.dlecheymielapp.data.auth.AuthRole
import com.grupo4.dlecheymielapp.data.auth.SessionState
import com.grupo4.dlecheymielapp.ui.admin.AdminHost
import com.grupo4.dlecheymielapp.data.reminders.ReminderScope
import com.grupo4.dlecheymielapp.notifications.OrderAlertNotifier
import com.grupo4.dlecheymielapp.notifications.OrderAlertScheduler
import com.grupo4.dlecheymielapp.ui.handler.FieldQueueHost
import com.grupo4.dlecheymielapp.ui.handler.HandlerHost
import com.grupo4.dlecheymielapp.ui.home.RoleHomeScreen
import com.grupo4.dlecheymielapp.ui.login.LoginScreen
import com.grupo4.dlecheymielapp.ui.login.LoginViewModel
import com.grupo4.dlecheymielapp.ui.session.SessionScope
import com.grupo4.dlecheymielapp.ui.session.SessionStores
import com.grupo4.dlecheymielapp.ui.theme.DLecheTheme
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.decorView.isForceDarkAllowed = false
        // The demo deliberately uses a light theme, even when Android is in dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
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

    // New-order notifications run only while someone is signed in; signing out forgets what was announced to that device.
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val signedIn = session is SessionState.SignedIn
    val signedOut = session is SessionState.SignedOut
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, signedIn) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && signedIn && BuildConfig.ORDER_ALERTS_ENABLED) {
                OrderAlertScheduler.schedule(context)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(signedIn, signedOut) {
        when {
            signedIn && BuildConfig.ORDER_ALERTS_ENABLED -> {
                OrderAlertScheduler.schedule(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !OrderAlertNotifier.canNotify(context)) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            signedOut -> {
                OrderAlertScheduler.cancel(context)
                container.alertStore.clear()
            }
        }
    }

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
