package com.grupo4.dlecheymielapp.ui.handler

import com.grupo4.dlecheymielapp.data.fulfillment.FieldCapabilities
import com.grupo4.dlecheymielapp.data.fulfillment.FieldGateway
import com.grupo4.dlecheymielapp.data.fulfillment.FieldIncident
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerOrderWork
import com.grupo4.dlecheymielapp.data.fulfillment.IncidentStatus
import com.grupo4.dlecheymielapp.data.fulfillment.Provider
import com.grupo4.dlecheymielapp.ui.common.PROVIDER_PHONE_FORMAT_MESSAGE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FormGateway(override val capabilities: FieldCapabilities = FieldCapabilities(canRegisterProvider = true)) : FieldGateway {
    var saveProviderCalls = 0
    var savedProvider: Provider? = null
    var reportIncidentCalls = 0
    var incidentError: Exception? = null

    override suspend fun order(id: Long): HandlerOrderWork = error("not used")
    override suspend fun incidents(id: Long): List<FieldIncident> = emptyList()
    override suspend fun handoff(id: Long, code: String) = error("not used")
    override suspend fun paymentLink(id: Long): String = error("not used")
    override suspend fun saveProvider(id: Long, provider: Provider) { saveProviderCalls++; savedProvider = provider }
    override suspend fun reportIncident(id: Long, description: String): FieldIncident {
        reportIncidentCalls++
        incidentError?.let { throw it }
        return FieldIncident(1, id, null, description, IncidentStatus.OPEN, "2026-10-26T10:00:00Z")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OrderDetailFormsTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(gateway: FormGateway) = OrderDetailViewModel(orderId = 7, gateway = gateway, onUnauthorized = {})

    @Test
    fun invalidProviderPhoneShowsTheErrorAndSendsNothing() {
        val gateway = FormGateway()
        val vm = viewModel(gateway)

        vm.onProviderName("Rápido SAC")
        vm.onProviderPhone("abc")
        vm.saveProvider()

        assertEquals(0, gateway.saveProviderCalls)
        assertEquals(PROVIDER_PHONE_FORMAT_MESSAGE, vm.state.value.providerPhoneError)
        assertFalse(vm.state.value.providerSaved)
    }

    @Test
    fun editingThePhoneClearsItsError() {
        val vm = viewModel(FormGateway())
        vm.onProviderName("Rápido SAC")
        vm.onProviderPhone("abc")
        vm.saveProvider()

        vm.onProviderPhone("+51987654321")

        assertNull(vm.state.value.providerPhoneError)
    }

    @Test
    fun validProviderIsSentAndValidIncidentIsStillReported() {
        val gateway = FormGateway()
        val vm = viewModel(gateway)

        vm.onProviderName("Rápido SAC")
        vm.onProviderPhone(" +51987654321 ")
        vm.saveProvider()
        vm.openIncidentDialog()
        vm.onIncidentTextChange("  Cliente ausente  ")
        vm.submitIncident()

        assertEquals(1, gateway.saveProviderCalls)
        assertEquals("the phone that passed validation is the one sent", "+51987654321", gateway.savedProvider?.phone)
        assertTrue(vm.state.value.providerSaved)
        assertNull(vm.state.value.providerPhoneError)
        assertEquals(1, gateway.reportIncidentCalls)
        assertFalse(vm.state.value.incidentDialogOpen)
    }

    @Test
    fun blankIncidentIsNotSent() {
        val gateway = FormGateway()
        val vm = viewModel(gateway)

        vm.openIncidentDialog()
        vm.onIncidentTextChange("   ")
        vm.submitIncident()

        assertEquals(0, gateway.reportIncidentCalls)
        assertTrue(vm.state.value.incidentDialogOpen)
    }

    @Test
    fun failedIncidentKeepsTheDraftText() {
        val gateway = FormGateway().apply { incidentError = IllegalStateException("sin red") }
        val vm = viewModel(gateway)

        vm.openIncidentDialog()
        vm.onIncidentTextChange("Cliente no contesta")
        vm.submitIncident()

        assertEquals(1, gateway.reportIncidentCalls)
        assertEquals("Cliente no contesta", vm.state.value.incidentText)
        assertTrue(vm.state.value.incidentDialogOpen)
        assertNotNull(vm.state.value.incidentError)
        assertFalse(vm.state.value.incidentBusy)
    }

    @Test
    fun incidentTextIsCappedAtTheBackendLimit() {
        val vm = viewModel(FormGateway())

        vm.onIncidentTextChange("a".repeat(MAX_INCIDENT_LENGTH + 500))

        assertEquals(MAX_INCIDENT_LENGTH, vm.state.value.incidentText.length)
    }
}
