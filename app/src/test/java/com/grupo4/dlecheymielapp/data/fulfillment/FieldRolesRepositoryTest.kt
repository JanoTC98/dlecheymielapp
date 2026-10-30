package com.grupo4.dlecheymielapp.data.fulfillment

import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.core.network.InMemoryCookieStorage
import com.grupo4.dlecheymielapp.core.network.NetworkModule
import com.grupo4.dlecheymielapp.data.reminders.ReminderScope
import com.grupo4.dlecheymielapp.data.reminders.RemindersApi
import com.grupo4.dlecheymielapp.data.reminders.RemindersRepository
import com.grupo4.dlecheymielapp.ui.common.timeUntil
import com.grupo4.dlecheymielapp.ui.common.toActionMessage
import com.grupo4.dlecheymielapp.ui.common.toHandoffMessage
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
import java.time.OffsetDateTime

class FieldRolesRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var network: NetworkModule
    private lateinit var delivery: DeliveryRepository
    private lateinit var operator: DeliveryRepository
    private lateinit var handler: HandlerRepository
    private lateinit var reminders: RemindersRepository

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        network = NetworkModule(server.url("/").toString(), InMemoryCookieStorage(), debugLogging = false)
        val api = network.retrofit.create(DeliveryApi::class.java)
        delivery = DeliveryRepository(api, network.json, DeliveryScope.DELIVERY)
        operator = DeliveryRepository(api, network.json, DeliveryScope.OPERATOR)
        handler = HandlerRepository(network.retrofit.create(HandlerApi::class.java), network.json)
        reminders = RemindersRepository(network.retrofit.create(RemindersApi::class.java), network.json)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(body)

    private fun csrfSeed() = MockResponse().addHeader("Set-Cookie", "XSRF-TOKEN=t1; Path=/")

    private val slot = """{"slotDate":"2026-10-26","startTime":"10:00","endTime":"10:30","timezone":"America/Lima"}"""

    private fun row(id: Int, status: String) =
        """{"id":$id,"publicCode":"DLM-$id","status":"$status","schedule":$slot,"districtName":"Miraflores","addressPreview":"Av. Larco 123","incidentCount":0}"""

    @Test
    fun deliveryQueueUsesItsOwnPathAndDropsFinishedOrders() = runTest {
        server.enqueue(json("""{"items":[${row(1, "IN_TRANSIT")},${row(2, "DELIVERED")},${row(3, "LOST")}],"page":0,"size":50}"""))

        val orders = delivery.orders()

        assertTrue(server.takeRequest().path!!.startsWith("/api/v1/delivery/orders"))
        assertEquals(listOf(1L), orders.map { it.id })
        assertEquals(FulfillmentMode.DELIVERY, orders.single().mode)
        assertEquals("Av. Larco 123", orders.single().recipient?.place)
    }

    @Test
    fun operatorQueueUsesTheOperatorPath() = runTest {
        server.enqueue(json("""{"items":[${row(1, "PREPARING")}],"page":0,"size":50}"""))

        operator.orders()

        assertTrue(server.takeRequest().path!!.startsWith("/api/v1/operator/orders"))
    }

    @Test
    fun deliveryDetailBecomesTheCommonOrderWithAddressAndPhone() = runTest {
        server.enqueue(
            json(
                """{"id":5,"publicCode":"DLM-5","status":"IN_TRANSIT","schedule":$slot,"customerName":"Rosa Vega",
                   "delivery":{"districtName":"Surco","address":"Jr. Sol 45","reference":"Puerta verde","phone":"987654321"},"incidentCount":1,
                   "payAtDelivery":{"paymentStatus":"pending","codeVerified":false,"linkAvailable":true}}""",
            ),
        )

        val order = delivery.order(5)

        assertEquals("Rosa Vega", order.customerName)
        assertEquals("Jr. Sol 45, Surco", order.recipient?.place)
        assertEquals("987654321", order.recipient?.phone)
        assertEquals("Puerta verde", order.recipient?.reference)
        assertEquals(PaymentStatus.pending, order.payAtDelivery?.paymentStatus)
    }

    @Test
    fun advanceMapsEachStatusToItsNextStepAndSendsCsrf() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))

        delivery.advance(5, OrderStatus.CONFIRMED)
        delivery.advance(5, OrderStatus.PREPARING)

        server.takeRequest()
        val first = server.takeRequest()
        assertEquals("/api/v1/delivery/orders/5/advance", first.path)
        assertEquals("t1", first.getHeader("X-XSRF-TOKEN"))
        assertTrue(first.body.readUtf8().contains("\"targetStatus\":\"PREPARING\""))
        assertTrue(server.takeRequest().body.readUtf8().contains("\"targetStatus\":\"IN_TRANSIT\""))
    }

    @Test
    fun noStepExistsAfterInTransit() = runTest {
        val error = runCatching { delivery.advance(5, OrderStatus.IN_TRANSIT) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun operatorCompleteSendsNoBody() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(MockResponse().setResponseCode(204))

        operator.complete(8)

        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/api/v1/operator/orders/8/complete", request.path)
        assertEquals(0L, request.bodySize)
    }

    @Test
    fun payOnDeliveryCompleteExplainsTheCodeIsRequired() {
        val error = ApiException(409, "HANDOFF_CODE_REQUIRED", "x")

        assertTrue(error.toHandoffMessage().contains("código"))
    }

    @Test
    fun operatorReadsIncidentsAndProviderFromCoordination() = runTest {
        val body = """{"provider":{"name":"InDrive Express","phone":"999111222"},
            "incidents":[{"id":3,"orderId":8,"description":"No contesta","status":"OPEN","recordedAt":"2026-10-26T15:00:00Z"}]}"""
        server.enqueue(json(body))
        server.enqueue(json(body))

        val incidents = operator.incidents(8)
        val provider = operator.provider(8)

        assertEquals("No contesta", incidents.single().description)
        assertEquals(Provider("InDrive Express", "999111222"), provider)
        assertTrue(server.takeRequest().path!!.endsWith("/operator/orders/8/coordination"))
    }

    @Test
    fun deliveryEmployeeReadsIncidentsFromItsOwnList() = runTest {
        server.enqueue(json("""[{"id":4,"orderId":5,"description":"Dirección incorrecta","status":"OPEN","recordedAt":"2026-10-26T15:00:00Z"}]"""))

        val incidents = delivery.incidents(5)

        assertTrue(server.takeRequest().path!!.endsWith("/delivery/orders/5/incidents"))
        assertEquals(1, incidents.size)
        assertNull(delivery.provider(5))
    }

    @Test
    fun saveProviderTrimsAndSendsBothFields() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(MockResponse().setResponseCode(204))

        operator.saveProvider(8, Provider("  InDrive  ", " 999 "))

        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/operator/orders/8/provider", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"name\":\"InDrive\"") && body.contains("\"phone\":\"999\""))
    }

    @Test
    fun onlyTheOperatorCanCompleteAndRegisterAProvider() {
        assertTrue(operator.capabilities.canComplete && operator.capabilities.canRegisterProvider)
        assertFalse(delivery.capabilities.canComplete || delivery.capabilities.canRegisterProvider)
        assertTrue(delivery.capabilities.canAdvance)
        assertFalse(handler.capabilities.canAdvance)
    }

    @Test
    fun staleStepIsExplained() {
        assertTrue(ApiException(409, "ORDER_STATE_CHANGED", "x").toActionMessage().contains("cambió de estado"))
    }

    @Test
    fun pickupGroupsAskForThePendingStateAndDecode() = runTest {
        server.enqueue(
            json(
                """{"items":[{"groupKey":"pickup-1-20261026-1000","university":{"id":1,"name":"UNI"},
                   "pickupPoint":{"id":1,"name":"Kiosco","locationText":"Patio"},"schedule":$slot,
                   "orderCount":4,"deliveredCount":1,"pendingCount":3,"incidentCount":0}],"page":0,"size":50}""",
            ),
        )

        val groups = handler.pickupGroups()

        assertTrue(server.takeRequest().path!!.contains("state=pending"))
        assertEquals(3, groups.single().pendingCount)
        assertEquals("Kiosco", groups.single().pickupPoint.name)
    }

    @Test
    fun pickupGroupDetailKnowsWhenNothingIsReady() = runTest {
        server.enqueue(
            json(
                """{"groupKey":"pickup-1-20261026-1000","schedule":$slot,"orders":[
                   {"id":1,"publicCode":"DLM-1","status":"PREPARING","customerName":"Ana","incidentCount":0,"delivered":false},
                   {"id":2,"publicCode":"DLM-2","status":"DELIVERED","customerName":"Luis","incidentCount":0,"delivered":true}]}""",
            ),
        )
        server.enqueue(
            json(
                """{"groupKey":"k","schedule":$slot,"orders":[{"id":1,"status":"READY_FOR_PICKUP","customerName":"Ana","delivered":false,
                   "payAtDelivery":{"paymentStatus":"pending","codeVerified":false,"linkAvailable":true}}]}""",
            ),
        )

        assertFalse(handler.pickupGroup("pickup-1-20261026-1000").hasReadyOrders)
        val ready = handler.pickupGroup("k")
        assertTrue(ready.hasReadyOrders)
        assertEquals(PaymentStatus.pending, ready.orders.single().payAtDelivery?.paymentStatus)
    }

    @Test
    fun remindersUseTheRoleScopeAndThreeItems() = runTest {
        server.enqueue(
            json(
                """{"items":[{"orderId":5,"publicCode":"DLM-5","mode":"DELIVERY","status":"PREPARING",
                   "slotStart":"2026-10-26T10:00:00-05:00","timezone":"America/Lima","reachedOffsetsMinutes":[720,180]}],"page":0,"size":3,"totalElements":1}""",
            ),
        )

        val page = reminders.upcoming(ReminderScope.OPERATOR)

        val path = server.takeRequest().path!!
        assertTrue(path, path.startsWith("/api/v1/operator/upcoming-reminders") && path.contains("size=3"))
        assertEquals(listOf(720, 180), page.items.single().reachedOffsetsMinutes)
    }

    @Test
    fun timeUntilIsReadable() {
        val now = OffsetDateTime.parse("2026-10-26T09:00:00-05:00")

        assertEquals("en 45 min", timeUntil("2026-10-26T09:45:00-05:00", now))
        assertEquals("en 2 h 15 min", timeUntil("2026-10-26T11:15:00-05:00", now))
        assertEquals("en 3 h", timeUntil("2026-10-26T12:00:00-05:00", now))
        assertEquals("ya empezó", timeUntil("2026-10-26T08:30:00-05:00", now))
        assertNull(timeUntil("no es una fecha", now))
    }
}
