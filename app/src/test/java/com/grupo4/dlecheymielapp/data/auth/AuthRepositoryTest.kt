package com.grupo4.dlecheymielapp.data.auth

import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.core.network.InMemoryCookieStorage
import com.grupo4.dlecheymielapp.core.network.NetworkModule
import com.grupo4.dlecheymielapp.core.network.NetworkUnavailableException
import com.grupo4.dlecheymielapp.ui.login.toMessage
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var network: NetworkModule
    private lateinit var repo: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        network = NetworkModule(server.url("/").toString(), InMemoryCookieStorage(), debugLogging = false)
        repo = AuthRepository(network, network.retrofit.create(AuthApi::class.java))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun session(role: String) =
        """{"user":{"id":7,"firstName":"Ana","lastName":"Ruiz","phone":null,"email":"ana@x.pe","role":"$role","authProvider":"LOCAL"},"expiresAt":"2026-10-26T02:00:00Z"}"""

    private fun csrfSeed() = MockResponse().setResponseCode(200).addHeader("Set-Cookie", "XSRF-TOKEN=tok123; Path=/")

    @Test
    fun loginSeedsCsrfThenSendsTokenAndSignsIn() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(MockResponse().setBody(session("DELIVERY_HANDLER")).addHeader("Content-Type", "application/json"))

        repo.login(" ana@x.pe ", "secreto")

        assertEquals("/api/v1/auth/csrf", server.takeRequest().path)
        val login = server.takeRequest()
        assertEquals("/api/v1/auth/login/local", login.path)
        assertEquals("tok123", login.getHeader("X-XSRF-TOKEN"))
        assertNull(login.getHeader("X-Captcha-Token"))
        assertTrue(login.body.readUtf8().contains("\"email\":\"ana@x.pe\""))
        val state = repo.session.value as SessionState.SignedIn
        assertEquals(AuthRole.DELIVERY_HANDLER, state.user.role)
    }

    @Test
    fun customerAccountIsRefusedAndLoggedOut() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(MockResponse().setBody(session("CLIENT")).addHeader("Content-Type", "application/json"))
        server.enqueue(MockResponse().setResponseCode(204))

        try {
            repo.login("ana@x.pe", "secreto")
            fail("a customer must not get into the staff app")
        } catch (e: NotStaffAccountException) {
            // expected
        }
        server.takeRequest(); server.takeRequest()
        assertEquals("/api/v1/auth/logout", server.takeRequest().path)
        assertTrue(repo.session.value !is SessionState.SignedIn)
    }

    @Test
    fun wrongCredentialsKeepEnvelopeCodeAndShowSpanishMessage() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(
            MockResponse().setResponseCode(401).addHeader("Content-Type", "application/json")
                .setBody("""{"code":"AUTH_INVALID_CREDENTIALS","message":"Invalid credentials","correlationId":"c-1"}"""),
        )

        val error = runCatching { repo.login("ana@x.pe", "mala") }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals("AUTH_INVALID_CREDENTIALS", (error as ApiException).code)
        assertEquals("Correo o contraseña incorrectos.", error.toMessage())
    }

    @Test
    fun rateLimitReportsRetryAfter() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(
            MockResponse().setResponseCode(429).addHeader("Retry-After", "240").addHeader("Content-Type", "application/json")
                .setBody("""{"code":"RATE_LIMITED","message":"Too many requests","correlationId":"c-2"}"""),
        )

        val error = runCatching { repo.login("ana@x.pe", "x") }.exceptionOrNull() as ApiException

        assertEquals(240L, error.retryAfterSeconds)
        assertTrue(error.toMessage().contains("4 min"))
    }

    @Test
    fun restoreWithExpiredSessionEndsSignedOutAndClearsCookies() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":"AUTH_SESSION_INVALID","message":"x","correlationId":"c"}"""))

        repo.restore()

        assertEquals(SessionState.SignedOut, repo.session.value)
    }

    @Test
    fun restoreWithValidSessionSignsIn() = runTest {
        server.enqueue(MockResponse().setBody(session("ADMIN")).addHeader("Content-Type", "application/json"))

        repo.restore()

        assertNotNull((repo.session.value as? SessionState.SignedIn)?.user)
    }

    @Test
    fun unreachableServerIsReportedAsNetworkProblem() = runTest {
        server.shutdown()

        val error = runCatching { repo.login("ana@x.pe", "x") }.exceptionOrNull()

        assertTrue(error is NetworkUnavailableException || error is java.io.IOException)
        assertTrue(error!!.toMessage().isNotBlank())
    }
}
