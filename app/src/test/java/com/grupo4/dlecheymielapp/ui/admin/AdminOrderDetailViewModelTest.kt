package com.grupo4.dlecheymielapp.ui.admin

import com.grupo4.dlecheymielapp.core.network.InMemoryCookieStorage
import com.grupo4.dlecheymielapp.core.network.NetworkModule
import com.grupo4.dlecheymielapp.data.admin.AdminApi
import com.grupo4.dlecheymielapp.data.admin.AdminRepository
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The ViewModel runs on Unconfined, so the real HTTP calls resume it; the tests wait for the state they expect. */
class AdminOrderDetailViewModelTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: AdminRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        server = MockWebServer().also { it.start() }
        val network = NetworkModule(server.url("/").toString(), InMemoryCookieStorage(), debugLogging = false)
        repo = AdminRepository(network.retrofit.create(AdminApi::class.java), network.json)
    }

    @After
    fun tearDown() {
        server.shutdown()
        Dispatchers.resetMain()
    }

    private fun json(body: String) = MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json").setBody(body)

    private fun order(status: String) =
        """{"id":7,"publicCode":"DLM-7","status":"$status","fulfillmentSnapshot":{"mode":"DELIVERY"}}"""

    private fun awaitState(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!check()) {
            if (System.currentTimeMillis() > deadline) fail("the expected state was not reached")
            Thread.sleep(10)
        }
    }

    @Test
    fun aNormalSingleAdvanceStillWorks() {
        server.enqueue(json(order("CONFIRMED")))
        server.enqueue(MockResponse().addHeader("Set-Cookie", "XSRF-TOKEN=t1; Path=/"))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(json(order("PREPARING")))
        val vm = AdminOrderDetailViewModel(7, repo) {}
        awaitState { vm.state.value.order != null }

        vm.advance()
        awaitState { vm.state.value.message != null && !vm.state.value.busy }

        assertEquals("El pedido pasó a En preparación.", vm.state.value.message)
        assertEquals(OrderStatus.PREPARING, vm.state.value.order?.status)
    }

    @Test
    fun enCaminoSendsNothingUntilTheAdminConfirms() {
        server.enqueue(json(order("PREPARING")))
        server.enqueue(MockResponse().addHeader("Set-Cookie", "XSRF-TOKEN=t1; Path=/"))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(json(order("IN_TRANSIT")))
        val vm = AdminOrderDetailViewModel(7, repo) {}
        awaitState { vm.state.value.order != null }
        val requestsBefore = server.requestCount

        vm.advance()

        assertEquals(AdminDialog.ConfirmAdvance("DLM-7", OrderStatus.IN_TRANSIT), vm.state.value.dialog)
        assertEquals("the request alone must not reach the server", requestsBefore, server.requestCount)

        vm.advance()
        awaitState { vm.state.value.message != null && !vm.state.value.busy }
        assertEquals("El pedido pasó a En camino.", vm.state.value.message)
    }

    @Test
    fun reassignOffersEveryOpenSlotButNotTheOrdersCurrentOne() {
        server.enqueue(json("""{"id":7,"status":"PREPARING","fulfillmentSnapshot":{"mode":"INTERNAL_DELIVERY","slotDate":"2026-11-02","startTime":"12:00","endTime":"13:00"}}"""))
        server.enqueue(json("""[
            {"slotDate":"2026-11-02","startTime":"12:00","endTime":"13:00","timezone":"America/Lima","remainingCapacity":5},
            {"slotDate":"2026-11-02","startTime":"13:00","endTime":"14:00","timezone":"America/Lima","remainingCapacity":6},
            {"slotDate":"2026-11-03","startTime":"12:00","endTime":"13:00","timezone":"America/Lima","remainingCapacity":5},
            {"slotDate":"2026-11-04","startTime":"11:00","endTime":"12:00","timezone":"America/Lima","remainingCapacity":0}
        ]"""))
        val vm = AdminOrderDetailViewModel(7, repo) {}
        awaitState { vm.state.value.order != null }

        vm.openReassign(incidentId = 3)
        awaitState { vm.state.value.dialog.let { it is AdminDialog.Slot && !it.loading } }

        val offered = (vm.state.value.dialog as AdminDialog.Slot).slots.map { "${it.slotDate} ${it.startTime}" }
        assertEquals(listOf("2026-11-02 13:00", "2026-11-03 12:00"), offered)
    }
}
