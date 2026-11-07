package com.grupo4.dlecheymielapp.data.fulfillment

import com.grupo4.dlecheymielapp.core.network.*
import com.grupo4.dlecheymielapp.ui.common.toHandoffMessage
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class IncidentContractTest {
    @Test fun serverStatusDistinguishesNewIncidentFromNoteForAllFieldRoles() = runTest {
        MockWebServer().use { server ->
            val network = NetworkModule(server.url("/").toString(), InMemoryCookieStorage(), false)
            network.cookieJar.saveFromResponse(server.url("/"), listOf(okhttp3.Cookie.parse(server.url("/"), "XSRF-TOKEN=test; Path=/")!!))
            val handler = HandlerRepository(network.retrofit.create(HandlerApi::class.java), network.json)
            val deliveryApi = network.retrofit.create(DeliveryApi::class.java)
            val gateways = listOf<FieldGateway>(handler, DeliveryRepository(deliveryApi, network.json, DeliveryScope.DELIVERY),
                DeliveryRepository(deliveryApi, network.json, DeliveryScope.OPERATOR))
            val body = """{"id":1,"orderId":7,"description":"Original","status":"OPEN","recordedAt":"2026-11-07T10:00:00","notes":[{"id":2,"authorName":"Demo","text":"Seguimiento","recordedAt":"2026-11-07T10:05:00"}]}"""
            for (gateway in gateways) {
                server.enqueue(MockResponse().setResponseCode(201).setBody(body))
                assertFalse(gateway.reportIncident(7, "Original").addedAsNote)
                server.enqueue(MockResponse().setResponseCode(200).setBody(body))
                val note = gateway.reportIncident(7, "Seguimiento")
                assertTrue(note.addedAsNote)
                assertEquals("Seguimiento", note.notes.single().text)
                server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "37")
                    .setBody("""{"code":"RATE_LIMITED","message":"Slow down"}"""))
                try {
                    gateway.reportIncident(7, "Blocked")
                    fail("Expected rate limit")
                } catch (error: ApiException) {
                    assertEquals(37L, error.retryAfterSeconds)
                    assertTrue(error.toHandoffMessage().contains("37 segundos"))
                }
            }
        }
    }

    @Test fun explicitOpenCountWinsOverHistoryAndLegacyOpenCountStillDecodes() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val base = """"id":7,"status":"IN_TRANSIT","mode":"DELIVERY","incidentCount":3,"schedule":{"slotDate":"2026-11-07","startTime":"10:00","endTime":"11:00"}"""
        assertEquals(0, json.decodeFromString<HandlerOrderWork>("{$base,\"openIncidentCount\":0}").openIncidentCount)
        assertEquals(3, json.decodeFromString<HandlerOrderWork>("{$base}").openIncidentCount)
    }
}
