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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** The ViewModel runs on Unconfined, so the real HTTP calls resume it; the tests wait for the state they expect. */
class AdminGroupDetailViewModelTest {
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

    private fun group(status: String) =
        """{"groupKey":"G1","schedule":{"slotDate":"2026-10-30","startTime":"10:00","endTime":"11:00"},"orders":[{"id":7,"publicCode":"DLM-7","status":"$status"}]}"""

    private fun awaitState(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!check()) {
            if (System.currentTimeMillis() > deadline) fail("the expected state was not reached")
            Thread.sleep(10)
        }
    }

    @Test
    fun aNormalGroupAdvanceStillWorks() {
        server.enqueue(json(group("CONFIRMED")))
        server.enqueue(MockResponse().addHeader("Set-Cookie", "XSRF-TOKEN=t1; Path=/"))
        server.enqueue(json("""{"advanced":[],"skipped":[]}"""))
        server.enqueue(json(group("PREPARING")))
        val vm = AdminGroupDetailViewModel("G1", repo) {}
        awaitState { vm.state.value.group != null }

        vm.advanceGroup()
        awaitState { vm.state.value.message != null && !vm.state.value.busy }

        assertNull(vm.state.value.actionError)
        assertEquals(OrderStatus.PREPARING, vm.state.value.group?.orders?.first()?.status)
        assertFalse(vm.state.value.busy)
    }
}
