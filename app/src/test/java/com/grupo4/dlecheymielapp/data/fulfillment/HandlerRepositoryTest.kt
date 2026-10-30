package com.grupo4.dlecheymielapp.data.fulfillment

import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.core.network.InMemoryCookieStorage
import com.grupo4.dlecheymielapp.core.network.NetworkModule
import com.grupo4.dlecheymielapp.ui.common.toHandoffMessage
import com.grupo4.dlecheymielapp.ui.common.toIncidentMessage
import com.grupo4.dlecheymielapp.ui.common.whatsappUrl
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HandlerRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: HandlerRepository

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val network = NetworkModule(server.url("/").toString(), InMemoryCookieStorage(), debugLogging = false)
        repo = HandlerRepository(network.retrofit.create(HandlerApi::class.java), network.json)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(body)

    private fun csrfSeed() = MockResponse().addHeader("Set-Cookie", "XSRF-TOKEN=t1; Path=/")

    private val orderJson = """
        {"id":41,"publicCode":"DLM-0041","status":"IN_TRANSIT","mode":"INTERNAL_DELIVERY",
         "schedule":{"slotDate":"2026-10-26","startTime":"10:00","endTime":"10:30","timezone":"America/Lima"},
         "recipient":{"name":"Luis","phone":"987654321","place":"Pabellón B","reference":null},
         "customerName":"Luis Pérez","incidentCount":1,
         "payAtDelivery":{"paymentStatus":"pending","codeVerified":false,"linkAvailable":true}}
    """.trimIndent()

    @Test
    fun ordersDecodeIncludingPayAtDelivery() = runTest {
        server.enqueue(json("""{"items":[$orderJson],"page":0,"size":50,"totalElements":1,"totalPages":1}"""))

        val order = repo.orders().single()

        assertEquals(OrderStatus.IN_TRANSIT, order.status)
        assertEquals(FulfillmentMode.INTERNAL_DELIVERY, order.mode)
        assertEquals("10:00 – 10:30", order.schedule.timeRange)
        assertEquals(PaymentStatus.pending, order.payAtDelivery?.paymentStatus)
        assertTrue(order.payAtDelivery!!.linkAvailable)
        assertNull(order.recipient?.reference)
    }

    @Test
    fun unknownStatusFromNewerBackendDoesNotCrash() = runTest {
        server.enqueue(json(orderJson.replace("IN_TRANSIT", "SOME_FUTURE_STATUS")))

        assertEquals(OrderStatus.UNKNOWN, repo.order(41).status)
    }

    @Test
    fun handoffSendsUppercasedTrimmedCodeWithCsrf() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(json("""{"orderId":41,"status":"DELIVERED","completedAt":"2026-10-26T15:10:00Z"}"""))

        val result = repo.handoff(41, " k7m2xq ")

        assertEquals("/api/v1/auth/csrf", server.takeRequest().path)
        val request = server.takeRequest()
        assertEquals("/api/v1/handler/orders/41/handoff", request.path)
        assertEquals("t1", request.getHeader("X-XSRF-TOKEN"))
        assertTrue(request.body.readUtf8().contains("\"code\":\"K7M2XQ\""))
        assertEquals(OrderStatus.DELIVERED, result.status)
        assertFalse(result.awaitingPayment)
    }

    @Test
    fun handoffAwaitingPaymentKeepsOrderOpen() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(json("""{"orderId":41,"status":"IN_TRANSIT","completedAt":null,"awaitingPayment":true}"""))

        val result = repo.handoff(41, "K7M2XQ")

        assertTrue(result.awaitingPayment)
        assertEquals(OrderStatus.IN_TRANSIT, result.status)
        assertNull(result.completedAt)
    }

    @Test
    fun invalidCodeMapsToTheSpanishInstruction() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(json("""{"code":"HANDOFF_CODE_INVALID","message":"x","correlationId":"c"}""", 400))

        val error = runCatching { repo.handoff(41, "ZZZZZZ") }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertTrue(error!!.toHandoffMessage().startsWith("El código no es válido"))
    }

    @Test
    fun incidentErrorsAreExplained() {
        val finished = ApiException(409, "ORDER_NOT_OPERATIONAL", "x")
        val tooLong = ApiException(400, "INCIDENT_TEXT_INVALID", "x")

        assertTrue(finished.toIncidentMessage().contains("ya terminó"))
        assertTrue(tooLong.toIncidentMessage().contains("2000"))
    }

    @Test
    fun reportIncidentTrimsTheText() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(
            json("""{"id":9,"orderId":41,"description":"Cliente ausente","status":"OPEN","recordedAt":"2026-10-26T15:00:00Z"}""", 201),
        )

        val incident = repo.reportIncident(41, "  Cliente ausente  ")

        server.takeRequest()
        assertTrue(server.takeRequest().body.readUtf8().contains("\"description\":\"Cliente ausente\""))
        assertEquals(IncidentStatus.OPEN, incident.status)
    }

    @Test
    fun whatsappAddsPeruPrefixToNineDigitNumbers() {
        assertEquals("https://wa.me/51987654321", whatsappUrl("987 654 321"))
        assertEquals("https://wa.me/51987654321", whatsappUrl("+51 987654321"))
        assertNull(whatsappUrl("123"))
        assertNull(whatsappUrl(null))
    }
}
