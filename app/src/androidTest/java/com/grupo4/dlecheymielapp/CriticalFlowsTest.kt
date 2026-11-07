package com.grupo4.dlecheymielapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grupo4.dlecheymielapp.core.network.*
import com.grupo4.dlecheymielapp.data.auth.*
import com.grupo4.dlecheymielapp.data.fulfillment.*
import com.grupo4.dlecheymielapp.ui.handler.*
import com.grupo4.dlecheymielapp.ui.login.*
import com.grupo4.dlecheymielapp.ui.theme.DLecheTheme
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class ScreenGateway : FieldGateway {
    override var capabilities = FieldCapabilities()
    var work = HandlerOrderWork(1, "DEMO-TEST", OrderStatus.IN_TRANSIT, FulfillmentMode.INTERNAL_DELIVERY,
        FieldSchedule("2026-11-07", "10:00", "11:00"), customerName = "Demo")
    var history = emptyList<FieldIncident>()
    var handoffCalls = 0
    var completeCalls = 0
    var noteCalls = 0
    var rateLimit = false
    override suspend fun order(id: Long) = work
    override suspend fun incidents(id: Long) = history
    override suspend fun handoff(id: Long, code: String): HandoffResult {
        handoffCalls++
        if (rateLimit) throw ApiException(429, "RATE_LIMITED", "Slow down", retryAfterSeconds = 37)
        if (code != "ABC123") throw ApiException(400, "HANDOFF_CODE_INVALID", "Invalid")
        work = work.copy(status = OrderStatus.DELIVERED)
        return HandoffResult(id, OrderStatus.DELIVERED)
    }
    override suspend fun complete(id: Long) { completeCalls++; work = work.copy(status = OrderStatus.DELIVERED) }
    override suspend fun reportIncident(id: Long, description: String): FieldIncident {
        noteCalls++
        return history.single().copy(addedAsNote = true)
    }
    override suspend fun paymentLink(id: Long): String = error("not needed")
}

@RunWith(AndroidJUnit4::class)
class CriticalFlowsTest {
    @get:Rule val compose = createComposeRule()

    private fun detail(gateway: ScreenGateway) {
        compose.setContent {
            val vm: OrderDetailViewModel = viewModel(factory = viewModelFactory {
                initializer { OrderDetailViewModel(1, gateway, {}) }
            })
            val state by vm.state.collectAsState()
            DLecheTheme {
                OrderDetailScreen(state, {}, vm::load, vm::onCodeChange, vm::submitHandoff,
                    vm::openIncidentDialog, vm::closeIncidentDialog, vm::onIncidentTextChange,
                    vm::submitIncident, vm::requestPaymentLink, vm::askAdvance, vm::advance,
                    vm::askComplete, vm::complete)
            }
        }
    }

    @Test fun loginSubmitsCredentialsAndShowsAuthenticatedStaff() {
        MockWebServer().use { server ->
            val network = NetworkModule(server.url("/").toString(), InMemoryCookieStorage(), false)
            val auth = AuthRepository(network, network.retrofit.create(AuthApi::class.java))
            server.enqueue(MockResponse().setResponseCode(204).addHeader("Set-Cookie", "XSRF-TOKEN=test; Path=/"))
            server.enqueue(MockResponse().setBody("""{"user":{"id":1,"firstName":"Admin","lastName":"Demo","email":"admin@example.com","role":"ADMIN"},"expiresAt":"2027-01-01T00:00:00Z"}"""))
            compose.setContent {
                val vm: LoginViewModel = viewModel(factory = viewModelFactory { initializer { LoginViewModel(auth) } })
                val state by vm.state.collectAsState()
                val session by auth.session.collectAsState()
                DLecheTheme {
                    if (session is SessionState.SignedIn) Text("Sesión de personal iniciada")
                    else LoginScreen(state, vm::onEmailChange, vm::onPasswordChange, vm::submit)
                }
            }
            compose.onNodeWithText("Ingresar").assertIsNotEnabled()
            compose.onNodeWithText("Correo").performScrollTo().performTextInput("admin@example.com")
            compose.onNodeWithText("Contraseña").performScrollTo().performTextInput("Test1234!")
            compose.onNodeWithText("Ingresar").performScrollTo().performClick()
            compose.waitUntil(10000) { auth.session.value is SessionState.SignedIn }
            compose.onNodeWithText("Sesión de personal iniciada").assertIsDisplayed()
            assertEquals("/api/v1/auth/csrf", server.takeRequest().path)
            val request = server.takeRequest()
            assertEquals("/api/v1/auth/login/local", request.path)
            assertTrue(request.body.readUtf8().contains("admin@example.com"))
            assertEquals("test", request.getHeader("X-XSRF-TOKEN"))
        }
    }

    @Test fun wrongCodeCanBeCorrectedAndDeliveryShowsConfirmation() {
        val gateway = ScreenGateway()
        detail(gateway)
        compose.onNodeWithText("Código de entrega").performScrollTo().performTextInput("WRONG1")
        compose.onNodeWithText("Validar entrega").performScrollTo().performClick()
        compose.onNodeWithText("El código no es válido.", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Código de entrega").performScrollTo().performTextReplacement("ABC123")
        compose.onNodeWithText("Validar entrega").performScrollTo().performClick()
        compose.onNodeWithText("Este pedido ya fue entregado.").performScrollTo().assertIsDisplayed()
        assertEquals(2, gateway.handoffCalls)
    }

    @Test fun codeRateLimitExplainsServerWait() {
        val gateway = ScreenGateway().apply { rateLimit = true }
        detail(gateway)
        compose.onNodeWithText("Código de entrega").performScrollTo().performTextInput("ABC123")
        compose.onNodeWithText("Validar entrega").performScrollTo().performClick()
        compose.onNodeWithText("37 segundos", substring = true).performScrollTo().assertIsDisplayed()
        assertEquals(1, gateway.handoffCalls)
    }

    @Test fun operatorMustConfirmBeforeClosingDelivery() {
        val gateway = ScreenGateway().apply { capabilities = FieldCapabilities(canComplete = true) }
        detail(gateway)
        compose.onNodeWithText("Marcar como entregado").performScrollTo().performClick()
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(0, gateway.completeCalls)
        compose.onNodeWithText("Marcar como entregado").performScrollTo().performClick()
        compose.onNodeWithText("Sí, entregado").performClick()
        compose.onNodeWithText("Este pedido ya fue entregado.").performScrollTo().assertIsDisplayed()
        assertEquals(1, gateway.completeCalls)
    }

    @Test fun cardWarnsAboutOpenIncidentAndConfirmsAddedNote() {
        val gateway = ScreenGateway().apply {
            history = listOf(FieldIncident(1, 1, description = "Original", status = IncidentStatus.OPEN, recordedAt = "2026-11-07T10:00:00"))
        }
        compose.setContent {
            val scope = rememberCoroutineScope()
            val actions = androidx.compose.runtime.remember { CardActions(scope, gateway, {}, {}) }
            val state by actions.state.collectAsState()
            DLecheTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    FieldOrderCard(gateway.work.toCard().copy(openIncidentCount = 1), state.card(1), actions, {})
                    state.notice?.let { Text(it) }
                }
                CardActionDialogs(state, actions)
            }
        }
        compose.onNodeWithText("Incidencia abierta").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Agregar nota").performScrollTo().performClick()
        compose.onNodeWithText("El seguimiento se agregará como nota", substring = true).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Seguimiento de la demo")
        compose.onNodeWithText("Enviar").performClick()
        compose.onNodeWithText("Se agregó a la incidencia abierta.").performScrollTo().assertIsDisplayed()
        assertEquals(1, gateway.noteCalls)
    }

    @Test fun resolvedIncidentDoesNotBlockDeliveryAndNotesAreVisible() {
        val gateway = ScreenGateway().apply {
            history = listOf(FieldIncident(1, 1, description = "Original", status = IncidentStatus.RESOLVED_REASSIGNED,
                recordedAt = "2026-11-07T10:00:00", notes = listOf(IncidentNote(2, authorName = "Operario Demo",
                    text = "Seguimiento visible", recordedAt = "2026-11-07T10:05:00"))))
        }
        detail(gateway)
        compose.onNodeWithText("Código de entrega").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Seguimiento visible").performScrollTo().assertIsDisplayed()
    }

    @Test fun actionConfirmationRemainsVisibleWhenTheQueueIsScrolled() {
        val actions = androidx.compose.runtime.mutableStateOf(CardActionsState())
        val sample = ScreenGateway().work
        compose.setContent {
            DLecheTheme {
                HandlerOrdersScreen(HandlerListState(loading = false, orders = (1L..20L).map { sample.copy(id = it) }),
                    actions.value, NoCardCallbacks, {}, {}, {})
            }
        }
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("order-card-20"))
        compose.runOnIdle { actions.value = CardActionsState(notice = "Se agregó a la incidencia abierta.") }
        compose.onNodeWithText("Se agregó a la incidencia abierta.").assertIsDisplayed()
    }
}
