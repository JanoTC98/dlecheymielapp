package com.grupo4.dlecheymielapp

import android.app.job.JobScheduler
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grupo4.dlecheymielapp.data.auth.SessionState
import com.grupo4.dlecheymielapp.data.fulfillment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in: real demo accounts/orders are supplied as runner arguments, never checked into source. */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class LiveBackendTest

@LiveBackendTest
@RunWith(AndroidJUnit4::class)
class LiveBackendE2ETest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as DLecheApp
    private val container get() = app.container

    private fun waitFor(matcher: SemanticsMatcher) {
        try {
            compose.waitUntil(20000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Waiting for $matcher\n${compose.onAllNodes(isRoot(), useUnmergedTree = true)[0].printToString()}", error)
        }
    }

    private fun login(email: String, password: String) {
        waitFor(hasText("Acceso del personal"))
        compose.onNodeWithText("Correo").performScrollTo().performTextReplacement(email)
        compose.onNodeWithText("Contraseña").performScrollTo().performTextReplacement(password)
        compose.onNodeWithText("Ingresar").performScrollTo().performClick()
        compose.waitUntil(20000) { container.authRepository.session.value is SessionState.SignedIn }
        assertEquals(email, (container.authRepository.session.value as SessionState.SignedIn).user.email)
        waitFor(hasContentDescription("Cerrar sesión"))
    }

    private fun logout() {
        compose.onNodeWithContentDescription("Cerrar sesión").performClick()
        compose.onNode(hasText("Cerrar sesión") and hasClickAction()).performClick()
        waitFor(hasText("Acceso del personal"))
        val scheduler = app.getSystemService(JobScheduler::class.java)
        compose.waitUntil(5000) { scheduler.getPendingJob(4101) == null }
        val prefs = app.getSharedPreferences("dleche_session_cookies", Context.MODE_PRIVATE)
        assertFalse(prefs.contains("cookies"))
        assertFalse(prefs.contains("cookies_aes_gcm_v1"))
    }

    private fun findCard(id: Long) {
        val tag = "order-card-$id"
        waitFor(hasScrollToNodeAction())
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(tag))
    }

    private fun cardAction(id: Long, text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag("order-card-$id")))

    private fun openOrder(order: JSONObject) {
        val id = order.getLong("id")
        findCard(id)
        cardAction(id, "Detalles").performClick()
        waitFor(hasContentDescription("Volver"))
        // Detail loading may keep its header visible before it can offer actions.
        waitFor(hasText("Incidencias"))
    }

    private fun back() { compose.onNodeWithContentDescription("Volver").performClick() }

    private fun gateway(kind: String): FieldGateway = when (kind) {
        "internal", "pickup" -> container.handlerRepository
        "delivery" -> container.deliveryRepository
        else -> container.operatorRepository
    }

    private fun deliver(kind: String, order: JSONObject) {
        val id = order.getLong("id")
        openOrder(order)
        if (kind == "operator") {
            compose.onNodeWithText("Marcar como entregado").performScrollTo().performClick()
            compose.onNodeWithText("Sí, entregado").performClick()
        } else {
            compose.onNodeWithText("Código de entrega").performScrollTo().performTextInput("WRONG1")
            compose.onNodeWithText("Validar entrega").performScrollTo().performClick()
            waitFor(hasText("El código no es válido.", substring = true))
            compose.onNodeWithText("Código de entrega").performScrollTo().performTextReplacement(order.getString("code"))
            compose.onNodeWithText("Código de entrega").performImeAction()
        }
        waitFor(hasText("Pedido entregado.") or hasText("Este pedido ya fue entregado."))
        assertEquals(OrderStatus.DELIVERED, runBlocking(Dispatchers.IO) { gateway(kind).order(id) }.status)
        back()
    }

    private fun notes(kind: String, order: JSONObject) {
        val id = order.getLong("id")
        openOrder(order)
        compose.onNodeWithText("Reportar incidencia").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("Incidencia de prueba $kind")
        compose.onNodeWithText("Enviar").performClick()
        waitFor(hasText("Incidencia registrada. La administración fue avisada."))
        back()
        findCard(id)
        waitFor(hasText("Agregar nota") and hasAnyAncestor(hasTestTag("order-card-$id")))
        cardAction(id, "Agregar nota").performClick()
        waitFor(hasText("El seguimiento se agregará como nota", substring = true))
        compose.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextInput("Seguimiento E2E $kind")
        compose.onNodeWithText("Enviar").performClick()
        waitFor(hasText("Se agregó a la incidencia abierta."))
        val incidents = runBlocking(Dispatchers.IO) { gateway(kind).incidents(id) }
        assertEquals(1, incidents.size)
        assertEquals("Seguimiento E2E $kind", incidents.single().notes.single().text)
        openOrder(order)
        waitFor(hasText("Seguimiento E2E $kind"))
        compose.onNodeWithText("Seguimiento E2E $kind").performScrollTo().assertIsDisplayed()
        back()
    }

    @Test fun demoAccountsCompleteTheirRealAssignedWorkAndKeepSecureSessions() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue("Live E2E needs explicit demo accounts and disposable order fixtures", arguments.containsKey("demoAccounts") && arguments.containsKey("e2eOrders"))
        val accounts = JSONArray(requireNotNull(arguments.getString("demoAccounts")))
        val orders = JSONObject(requireNotNull(arguments.getString("e2eOrders")))
        runBlocking(Dispatchers.IO) { container.authRepository.logout() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                if (android.os.Build.VERSION.SDK_INT >= 29) assertFalse(activity.window.decorView.isForceDarkAllowed)
                assertTrue(androidx.core.view.WindowInsetsControllerCompat(activity.window, activity.window.decorView).isAppearanceLightStatusBars)
            }
            for (index in 0 until accounts.length()) {
                val account = accounts.getJSONObject(index)
                val kind = account.getString("kind")
                login(account.getString("email"), account.getString("password"))
                val scheduler = app.getSystemService(JobScheduler::class.java)
                compose.waitUntil(5000) { scheduler.getPendingJob(4101) != null }
                if (index == 0) {
                    scheduler.cancel(4101)
                    assertNull(scheduler.getPendingJob(4101))
                    scenario.moveToState(Lifecycle.State.STARTED)
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    compose.waitUntil(5000) { scheduler.getPendingJob(4101) != null }
                    scenario.recreate()
                    waitFor(hasContentDescription("Cerrar sesión"))
                    val prefs = app.getSharedPreferences("dleche_session_cookies", Context.MODE_PRIVATE)
                    assertFalse(prefs.contains("cookies"))
                    assertNotNull(prefs.getString("cookies_aes_gcm_v1", null))
                }
                if (kind == "admin") {
                    for (field in listOf("internal", "delivery", "operator")) {
                        val order = orders.getJSONObject(field + "Note")
                        compose.onNodeWithText("Buscar por código").performTextReplacement(order.getString("publicCode"))
                        compose.onNodeWithText("Buscar por código").performImeAction()
                        val resultCard = hasText(order.getString("publicCode")) and hasClickAction() and !hasSetTextAction()
                        waitFor(resultCard)
                        compose.onNode(resultCard).performClick()
                        waitFor(hasContentDescription("Volver"))
                        waitFor(hasText("Incidencias"))
                        waitFor(hasText("Seguimiento E2E $field"))
                        compose.onNodeWithText("Seguimiento E2E $field").performScrollTo().assertIsDisplayed()
                        back()
                    }
                } else {
                    if (kind == "pickup") {
                        val tag = "pickup-group-${orders.getJSONObject(kind).getString("groupKey")}"
                        waitFor(hasTestTag(tag))
                        compose.onNodeWithTag(tag).performClick()
                    }
                    deliver(kind, orders.getJSONObject(kind))
                    if (kind == "pickup") back() else notes(kind, orders.getJSONObject(kind + "Note"))
                }
                logout()
            }
        }
    }

    @Test fun encryptedSessionRestoresAfterProcessRestart() {
        val arguments = InstrumentationRegistry.getArguments()
        val phase = arguments.getString("restoreSession")
        assumeTrue("Explicit prepare/verify phases with a process restart between them", phase in listOf("prepare", "verify"))
        ActivityScenario.launch(MainActivity::class.java).use {
            if (phase == "prepare") {
                val accounts = JSONArray(requireNotNull(arguments.getString("demoAccounts")))
                val admin = (0 until accounts.length()).map { accounts.getJSONObject(it) }.single { it.getString("kind") == "admin" }
                login(admin.getString("email"), admin.getString("password"))
            } else {
                compose.waitUntil(20000) { container.authRepository.session.value is SessionState.SignedIn }
                assertEquals("admin@gmail.com", (container.authRepository.session.value as SessionState.SignedIn).user.email)
                waitFor(hasContentDescription("Cerrar sesión"))
                assertNotNull(app.getSystemService(JobScheduler::class.java).getPendingJob(4101))
                logout()
            }
        }
    }
}
