package com.grupo4.dlecheymielapp.ui.admin

import com.grupo4.dlecheymielapp.data.admin.PickupGroupSummary
import com.grupo4.dlecheymielapp.data.fulfillment.FieldSchedule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

private fun group(key: String) = PickupGroupSummary(groupKey = key, schedule = FieldSchedule("2026-10-30", "10:00", "10:30"))

class AdminGroupsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun initialLoadFillsTheList() = runTest(dispatcher) {
        val vm = AdminGroupsViewModel({ listOf(group("a"), group("b")) }, {}, now = { 0L })

        assertEquals(listOf("a", "b"), vm.state.value.groups.map { it.groupKey })
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun overlappingRefreshesStartOnlyOneLoad() = runTest(dispatcher) {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val vm = AdminGroupsViewModel({ calls++; gate.await(); listOf(group("a")) }, {}, now = { 0L })

        vm.refresh()
        vm.refresh()
        assertEquals(1, calls)

        gate.complete(Unit)
        vm.refresh()
        assertEquals(2, calls)
    }

    @Test
    fun failedRefreshKeepsPreviousGroupsAndSetsStale() = runTest(dispatcher) {
        var fail = false
        var now = 1_000L
        val vm = AdminGroupsViewModel({ if (fail) throw IOException("sin red") else listOf(group("a")) }, {}, now = { now })

        now = 2_000L
        fail = true
        vm.refresh()

        assertEquals(listOf("a"), vm.state.value.groups.map { it.groupKey })
        assertTrue(vm.state.value.stale)
        assertNotNull(vm.state.value.error)
        assertEquals(1_000L, vm.state.value.lastUpdated)
    }

    @Test
    fun lastUpdatedIsSetOnSuccessAndStaleClearsOnRecovery() = runTest(dispatcher) {
        var fail = true
        var now = 3_000L
        val vm = AdminGroupsViewModel({ if (fail) throw IOException("sin red") else listOf(group("a")) }, {}, now = { now })
        assertNull(vm.state.value.lastUpdated)
        assertTrue(vm.state.value.stale)

        fail = false
        now = 4_000L
        vm.refresh()

        assertEquals(4_000L, vm.state.value.lastUpdated)
        assertFalse(vm.state.value.stale)
        assertNull(vm.state.value.error)
    }
}
