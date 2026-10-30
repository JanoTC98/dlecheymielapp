package com.grupo4.dlecheymielapp.data.admin

import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.core.network.InMemoryCookieStorage
import com.grupo4.dlecheymielapp.core.network.NetworkModule
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.ui.common.toAdminMessage
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

class AdminRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: AdminRepository

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        val network = NetworkModule(server.url("/").toString(), InMemoryCookieStorage(), debugLogging = false)
        repo = AdminRepository(network.retrofit.create(AdminApi::class.java), network.json)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).addHeader("Content-Type", "application/json").setBody(body)

    private fun csrfSeed() = MockResponse().addHeader("Set-Cookie", "XSRF-TOKEN=t1; Path=/")

    private val summary = """
        {"id":7,"publicCode":"DLM-0007","status":"CONFIRMED","consolidatedAt":"2026-10-25T14:00:00Z","amount":45.5,"currency":"PEN",
         "customerDisplayName":"Rosa Vega","customerEmail":"r@x.pe","fulfillmentMode":"INTERNAL_DELIVERY",
         "schedule":{"slotDate":"2026-10-26","startTime":"10:00","endTime":"10:30","timezone":"America/Lima"},
         "paymentStatus":"approved","refundStatus":null,"hasOpenPaymentIncident":false,"incidentCount":1,"hasIncident":true,
         "incidentStatus":"OPEN","incidentDeadlineAt":"2026-10-26T12:00:00Z","assignmentKind":"HANDLER","assignedUserId":3,"pickupGroupKey":null}
    """.trimIndent()

    @Test
    fun queueSendsOnlyTheActiveFiltersAndDecodes() = runTest {
        server.enqueue(json("""{"items":[$summary],"page":0,"size":30,"totalElements":1,"totalPages":1}"""))

        val page = repo.orders(OrderFilters(code = " DLM-7 ", status = OrderStatus.CONFIRMED, incident = "open"))

        val path = server.takeRequest().path!!
        assertTrue(path, path.contains("code=DLM-7"))
        assertTrue(path, path.contains("status=CONFIRMED"))
        assertTrue(path, path.contains("incident=open"))
        assertFalse(path, path.contains("fulfillmentMode"))
        val order = page.items.single()
        assertEquals(FulfillmentMode.INTERNAL_DELIVERY, order.fulfillmentMode)
        assertEquals(AssignmentKind.HANDLER, order.assignmentKind)
        assertTrue(order.hasOpenIncident)
        assertEquals(45.5, order.amount, 0.0)
    }

    @Test
    fun unknownEnumValuesFromANewerBackendDoNotCrash() = runTest {
        server.enqueue(json("""{"items":[${summary.replace("HANDLER", "SOMETHING_NEW").replace("CONFIRMED", "FUTURE")}],"page":0,"size":30}"""))

        val order = repo.orders(OrderFilters()).items.single()

        assertEquals(OrderStatus.UNKNOWN, order.status)
    }

    @Test
    fun advanceSendsExpectedAndTargetStatusWithCsrf() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(MockResponse().setResponseCode(204))

        repo.advance(7, OrderStatus.CONFIRMED, OrderStatus.PREPARING)

        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/api/v1/admin/orders/7/advance", request.path)
        assertEquals("t1", request.getHeader("X-XSRF-TOKEN"))
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"expectedStatus\":\"CONFIRMED\"") && body.contains("\"targetStatus\":\"PREPARING\""))
    }

    @Test
    fun staleAdvanceExplainsTheOrderChanged() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(json("""{"code":"ORDER_STATE_CHANGED","message":"x","correlationId":"c"}""", 409))

        val error = runCatching { repo.advance(7, OrderStatus.CONFIRMED, OrderStatus.PREPARING) }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertTrue(error!!.toAdminMessage().contains("cambió de estado"))
    }

    @Test
    fun reassignSendsTheChosenSlot() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(json("""{"orderId":7,"status":"IN_TRANSIT","schedule":{"slotDate":"2026-10-28","startTime":"11:00","endTime":"11:30","timezone":"America/Lima"}}"""))

        val result = repo.reassign(7, 9, RescheduleSlot("2026-10-28", "11:00", "11:30", "America/Lima", 4))

        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/api/v1/admin/orders/7/incidents/9/reassign", request.path)
        assertTrue(request.body.readUtf8().contains("\"slotDate\":\"2026-10-28\""))
        assertEquals(OrderStatus.IN_TRANSIT, result.status)
    }

    @Test
    fun fullSlotIsExplained() {
        val error = ApiException(409, "RESCHEDULE_SLOT_UNAVAILABLE", "x")

        assertTrue(error.toAdminMessage().contains("cupo"))
    }

    @Test
    fun desistAlwaysSendsTheRefundChoice() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(json("""{"orderId":7,"status":"CANCELLED","refundStarted":false}"""))

        val result = repo.desist(7, 9, refund = false)

        server.takeRequest()
        assertTrue(server.takeRequest().body.readUtf8().contains("\"refund\":false"))
        assertFalse(result.refundStarted)
    }

    @Test
    fun detailDecodesIncidentsNotesAndPayOnDelivery() = runTest {
        server.enqueue(
            json(
                """{"id":7,"publicCode":"DLM-0007","status":"LOST","amount":45.5,"consolidatedAt":"2026-10-25T14:00:00Z",
                   "fulfillmentSnapshot":{"mode":"DELIVERY","slotDate":"2026-10-26","startTime":"10:00","endTime":"10:30","deliveryAddress":"Av. Sol 123"},
                   "items":[{"lineNumber":1,"itemType":"PRODUCT","name":"Torta","quantity":2,"subtotal":45.5}],
                   "paymentStatus":"pending","notes":[{"id":1,"author":{"id":1,"displayName":"Admin"},"createdAt":"2026-10-25T15:00:00Z","content":"Llamar"}],
                   "history":[],"incidents":[{"id":9,"orderId":7,"type":"SIN_CAMBIO","description":"","status":"RESOLVED_LOST","recordedAt":"2026-10-26T15:00:00Z"}],
                   "paymentTiming":"ON_DELIVERY","stockRestored":false,"stockRestoreAvailable":true}""",
            ),
        )

        val order = repo.order(7)

        assertTrue(order.isPayOnDelivery)
        assertTrue(order.stockRestoreAvailable)
        assertEquals("Av. Sol 123", order.fulfillmentSnapshot.deliveryAddress)
        assertEquals(IncidentType.SIN_CAMBIO, order.incidents.single().type)
        assertTrue(order.openIncidents.isEmpty())
    }

    @Test
    fun groupCandidatesShowBusyOnesAsUnavailable() = runTest {
        server.enqueue(
            json(
                """{"items":[{"id":3,"displayName":"Carlos","available":true,"conflict":null},
                   {"id":4,"displayName":"Lucía","available":false,"conflict":{"kind":"PICKUP_GROUP","id":"pickup-1-20261026-1000","slotDate":"2026-10-26","startTime":"10:00","endTime":"10:30"}}],
                   "page":0,"hasNext":false}""",
            ),
        )

        val candidates = repo.groupCandidates("pickup-1-20261026-1000")

        assertTrue(candidates[0].available)
        assertFalse(candidates[1].available)
        assertEquals("10:00", candidates[1].conflict?.startTime)
    }

    @Test
    fun clearingTheDestinationLeavesDeliveryToTheAdmin() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(MockResponse().setResponseCode(204))

        repo.clearDeliveryDestination(7)

        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/admin/orders/7/delivery-destination", request.path)
    }

    @Test
    fun sendingToTheOperatorOmitsTheEmployee() = runTest {
        server.enqueue(csrfSeed())
        server.enqueue(MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json").setBody("""{"orderId":7}"""))

        repo.sendToOperator(7)

        server.takeRequest()
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("DELIVERY_COORDINATOR"))
        assertNull(Regex("employeeId").find(body)?.value)
    }
}
