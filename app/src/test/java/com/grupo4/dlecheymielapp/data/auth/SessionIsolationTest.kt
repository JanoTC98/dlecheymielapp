package com.grupo4.dlecheymielapp.data.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.grupo4.dlecheymielapp.core.network.InMemoryCookieStorage
import com.grupo4.dlecheymielapp.core.network.NetworkModule
import com.grupo4.dlecheymielapp.ui.session.SessionStores
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Nothing of one session may leak into the next: the ViewModels of the screens and the "session expired" notice. */
class SessionIsolationTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val network = NetworkModule(server.url("/").toString(), InMemoryCookieStorage(), debugLogging = false)
        repo = AuthRepository(network, network.retrofit.create(AuthApi::class.java))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun session(id: Int, role: String) =
        """{"user":{"id":$id,"firstName":"Ana","lastName":"Ruiz","phone":null,"email":"ana@x.pe","role":"$role","authProvider":"LOCAL"},"expiresAt":"2026-10-26T02:00:00Z"}"""

    private fun signIn(id: Int) = runTest {
        server.enqueue(MockResponse().addHeader("Set-Cookie", "XSRF-TOKEN=t; Path=/"))
        server.enqueue(MockResponse().setBody(session(id, "DELIVERY_HANDLER")).addHeader("Content-Type", "application/json"))
        repo.login("ana@x.pe", "secreto")
    }

    private class Marker : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    private val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = Marker() as T
    }

    @Test
    fun sameUserKeepsItsScreensAndAnotherUserGetsNewOnes() {
        val stores = SessionStores()
        val first = stores.storeFor(13)
        val marker = ViewModelProvider.create(first, factory)[Marker::class.java]

        assertSame(first, stores.storeFor(13))
        assertFalse(marker.cleared)

        val second = stores.storeFor(11)
        assertNotSame(first, second)
        assertTrue("the previous user's ViewModels must be cleared", marker.cleared)
        assertNotSame(marker, ViewModelProvider.create(second, factory)[Marker::class.java])
    }

    @Test
    fun endingTheSessionClearsEverything() {
        val stores = SessionStores()
        val marker = ViewModelProvider.create(stores.storeFor(13), factory)[Marker::class.java]

        stores.clear()

        assertTrue(marker.cleared)
    }

    @Test
    fun serverEndingTheSessionRaisesTheNoticeButLoggingOutDoesNot() = runTest {
        signIn(13)
        repo.onSessionExpired()
        assertTrue(repo.sessionExpired.value)
        assertEquals(SessionState.SignedOut, repo.session.value)

        repo.clearExpiredNotice()
        signIn(13)
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        repo.logout()
        assertFalse(repo.sessionExpired.value)
    }

    @Test
    fun severalFailingRequestsRaiseTheNoticeOnlyOnceAndNeverWhenSignedOut() = runTest {
        repo.onSessionExpired()

        assertFalse("nobody was signed in, so nothing expired", repo.sessionExpired.value)
    }
}
