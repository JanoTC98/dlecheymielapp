package com.grupo4.dlecheymielapp.ui.admin

import com.grupo4.dlecheymielapp.core.network.InMemoryCookieStorage
import com.grupo4.dlecheymielapp.core.network.NetworkModule
import com.grupo4.dlecheymielapp.data.admin.AdminApi
import com.grupo4.dlecheymielapp.data.admin.AdminRepository
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

/** The detail shows who the order is assigned to without opening the dialog, and never mistakes a failed read for "unassigned". */
class AdminOrderAssignmentTest {
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

    private fun order(mode: String) = json("""{"id":7,"publicCode":"DLM-7","status":"CONFIRMED","fulfillmentSnapshot":{"mode":"$mode"}}""")

    private fun awaitState(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!check()) {
            if (System.currentTimeMillis() > deadline) fail("the expected state was not reached")
            Thread.sleep(10)
        }
    }

    private fun viewModel(mode: String): AdminOrderDetailViewModel {
        server.enqueue(order(mode))
        val vm = AdminOrderDetailViewModel(7, repo) {}
        awaitState { vm.state.value.order != null }
        return vm
    }

    @Test
    fun aDeliveryOrderShowsItsDestination() {
        val vm = viewModel("DELIVERY")
        server.enqueue(json("""{"orderId":7,"destinationRole":"DELIVERY_COORDINATOR"}"""))

        vm.loadAssignment()
        awaitState { vm.state.value.assignment is AssignmentView.Known }

        assertEquals(AssignmentView.Known("Operador"), vm.state.value.assignment)
    }

    @Test
    fun aDeliveryOrderWithAnEmployeeShowsTheEmployeeName() {
        val vm = viewModel("DELIVERY")
        server.enqueue(json("""{"orderId":7,"destinationRole":"DELIVERY","employeeId":15,"employeeDisplayName":"Delivery QA"}"""))

        vm.loadAssignment()
        awaitState { vm.state.value.assignment is AssignmentView.Known }

        assertEquals(AssignmentView.Known("Delivery QA"), vm.state.value.assignment)
    }

    @Test
    fun anInPersonOrderShowsTheNameOfItsHandler() {
        val vm = viewModel("INTERNAL_DELIVERY")
        server.enqueue(json("""{"orderId":7,"handlerId":5}"""))
        server.enqueue(json("""{"items":[{"id":4,"displayName":"Otro"},{"id":5,"displayName":"Repartidor Prueba"}]}"""))

        vm.loadAssignment()
        awaitState { vm.state.value.assignment is AssignmentView.Known }

        assertEquals(AssignmentView.Known("Repartidor Prueba"), vm.state.value.assignment)
    }

    @Test
    fun anOrderWithNobodyAssignedSaysSo() {
        val vm = viewModel("INTERNAL_DELIVERY")
        server.enqueue(json("""{"orderId":7}"""))

        vm.loadAssignment()
        awaitState { vm.state.value.assignment is AssignmentView.Known }

        assertEquals(AssignmentView.Known("Sin encargado"), vm.state.value.assignment)
    }

    @Test
    fun aFailedReadIsNotShownAsUnassigned() {
        val vm = viewModel("DELIVERY")
        server.enqueue(MockResponse().setResponseCode(500))

        vm.loadAssignment()
        awaitState { vm.state.value.assignment != AssignmentView.Loading && vm.state.value.assignment != null }

        assertEquals(AssignmentView.Failed, vm.state.value.assignment)
    }

    @Test
    fun pickupOrdersAreAssignedByGroupSoNothingIsRead() {
        val vm = viewModel("PICKUP")
        val before = server.requestCount

        vm.loadAssignment()

        assertEquals(before, server.requestCount)
    }
}
