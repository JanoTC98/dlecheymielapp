package com.grupo4.dlecheymielapp.core.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class CookiePolicyTest {
    private val origin = "https://api.example.com/".toHttpUrl()

    @Test fun secureCookieIsNeverSentOverHttpOrToAnotherOrigin() {
        val jar = PersistentCookieJar(InMemoryCookieStorage(), origin)
        val cookie = Cookie.parse(origin, "DLECHE_SESSION=secret; Path=/api; Secure; HttpOnly; SameSite=Strict; Max-Age=3600")!!
        jar.saveFromResponse(origin.newBuilder().encodedPath("/api/login").build(), listOf(cookie))
        assertEquals("secret", jar.valueOf("https://api.example.com/api/orders".toHttpUrl(), "DLECHE_SESSION"))
        for (url in listOf("http://api.example.com/api/orders", "https://evil.example.com/api/orders",
            "https://api.example.com:8443/api/orders", "https://api.example.com/other")) {
            assertTrue(jar.loadForRequest(url.toHttpUrl()).isEmpty())
        }
    }

    @Test fun aBroadDomainCookieCannotLeakToASiblingThroughARedirect() {
        val jar = PersistentCookieJar(InMemoryCookieStorage(), origin)
        jar.saveFromResponse(origin, listOf(Cookie.parse(origin, "DLECHE_SESSION=secret; Domain=example.com; Secure; Path=/")!!))
        assertTrue(jar.loadForRequest("https://shop.example.com/".toHttpUrl()).isEmpty())
        assertEquals(1, jar.loadForRequest(origin).size)
    }

    @Test fun logoutExpiryClearsPersistedCookies() {
        val store = InMemoryCookieStorage()
        val jar = PersistentCookieJar(store, origin)
        jar.saveFromResponse(origin, listOf(Cookie.parse(origin, "DLECHE_SESSION=secret; Path=/; Max-Age=3600; Secure")!!))
        jar.saveFromResponse(origin, listOf(Cookie.parse(origin, "DLECHE_SESSION=; Path=/; Max-Age=0; Secure")!!))
        assertTrue(PersistentCookieJar(store, origin).loadForRequest(origin).isEmpty())
    }

    @Test fun loginCanSetACookieForADifferentPathButOnlyThatPathReceivesIt() {
        val jar = PersistentCookieJar(InMemoryCookieStorage(), origin)
        jar.saveFromResponse("https://api.example.com/auth/login".toHttpUrl(),
            listOf(Cookie.parse(origin, "DLECHE_SESSION=secret; Path=/orders; Secure")!!))
        assertEquals(1, jar.loadForRequest("https://api.example.com/orders/1".toHttpUrl()).size)
        assertTrue(jar.loadForRequest("https://api.example.com/auth/me".toHttpUrl()).isEmpty())
    }
}
