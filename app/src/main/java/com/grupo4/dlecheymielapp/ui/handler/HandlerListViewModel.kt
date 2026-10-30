package com.grupo4.dlecheymielapp.ui.handler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.data.fulfillment.FieldGateway
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerOrderWork
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerProfile
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerRepository
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerType
import com.grupo4.dlecheymielapp.data.fulfillment.PickupGroupWork
import com.grupo4.dlecheymielapp.data.reminders.UpcomingReminder
import com.grupo4.dlecheymielapp.ui.common.toGenericMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One list for every field role. The Encargado of in-person delivery fills [orders]; the Encargado of pickup fills [groups];
 * Delivery and Operador fill [orders] with their queue.
 */
data class HandlerListState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val profile: HandlerProfile? = null,
    val orders: List<HandlerOrderWork> = emptyList(),
    val groups: List<PickupGroupWork> = emptyList(),
    val reminders: List<UpcomingReminder> = emptyList(),
    val error: String? = null,
    /** Epoch millis of the last successful load; null until one succeeds. */
    val lastUpdated: Long? = null,
    /** True when the last refresh failed and the lists still show older data. */
    val stale: Boolean = false,
) {
    /** Pickup Encargados work by groups; they must not call `/handler/orders` (403 HANDLER_TYPE_MISMATCH). */
    val isPickupHandler: Boolean get() = profile?.handlerType == HandlerType.PICKUP
}

class HandlerListViewModel(
    private val repository: HandlerRepository,
    private val reminders: suspend () -> List<UpcomingReminder>,
    private val onUnauthorized: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(HandlerListState())
    val state: StateFlow<HandlerListState> = _state.asStateFlow()

    /** Validate codes, report incidents… straight from the cards; the list reloads quietly afterwards. */
    val cardActions = CardActions(viewModelScope, repository, onUnauthorized, onChanged = { reloadAndWait() })

    private var job: Job? = null

    init { load(initial = true) }

    fun refresh() { load(initial = false) }

    /**
     * Restarts the list load and waits until a load started after this call has finished. Another card may restart the load
     * meanwhile and cancel the one awaited here (a cancelled job's join returns at once), so follow the replacement.
     */
    private suspend fun reloadAndWait() {
        var running = load(initial = false, quiet = true, restart = true)
        running.join()
        while (job !== running) {
            running = job ?: return
            running.join()
        }
    }

    /**
     * A refresh while a load runs is dropped: the running load already reads the server. A card action restarts it, so its result
     * is shown. Returns the running load so a card action can wait for its reload before unlocking the card.
     */
    private fun load(initial: Boolean, quiet: Boolean = false, restart: Boolean = false): Job {
        job?.takeIf { it.isActive && !restart }?.let { return it }
        job?.cancel()
        _state.update { it.copy(loading = initial && it.profile == null, refreshing = !initial && !quiet, error = null) }
        return viewModelScope.launch {
            try {
                val profile = _state.value.profile ?: repository.profile()
                val pickup = profile.handlerType == HandlerType.PICKUP
                val orders = if (pickup) emptyList() else repository.orders().sortedForField()
                val groups = if (pickup) repository.pickupGroups().sortedForPickup() else emptyList()
                val upcoming = runCatching { reminders() }.getOrDefault(emptyList())
                ensureActive()
                _state.update {
                    it.copy(
                        loading = false, refreshing = false, profile = profile, orders = orders, groups = groups, reminders = upcoming,
                        lastUpdated = now(), stale = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiException && e.status == 401) onUnauthorized()
                // The previous list, profile and filters stay on screen; the screen shows that they are older.
                _state.update { it.copy(loading = false, refreshing = false, error = e.toGenericMessage(), stale = true) }
            }
        }.also { job = it }
    }
}

/** Delivery and Operador queue: the same list state without a profile. */
class FieldListViewModel(
    gateway: FieldGateway,
    private val loadOrders: suspend () -> List<HandlerOrderWork>,
    private val reminders: suspend () -> List<UpcomingReminder>,
    private val onUnauthorized: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(HandlerListState())
    val state: StateFlow<HandlerListState> = _state.asStateFlow()

    val cardActions = CardActions(viewModelScope, gateway, onUnauthorized, onChanged = { reloadAndWait() })

    private var job: Job? = null

    init { load(initial = true) }

    fun refresh() { load(initial = false) }

    /**
     * Restarts the list load and waits until a load started after this call has finished. Another card may restart the load
     * meanwhile and cancel the one awaited here (a cancelled job's join returns at once), so follow the replacement.
     */
    private suspend fun reloadAndWait() {
        var running = load(initial = false, quiet = true, restart = true)
        running.join()
        while (job !== running) {
            running = job ?: return
            running.join()
        }
    }

    /** Same rule as [HandlerListViewModel]: one load at a time; a card action restarts it and waits for it. */
    private fun load(initial: Boolean, quiet: Boolean = false, restart: Boolean = false): Job {
        job?.takeIf { it.isActive && !restart }?.let { return it }
        job?.cancel()
        _state.update { it.copy(loading = initial, refreshing = !initial && !quiet, error = null) }
        return viewModelScope.launch {
            try {
                val orders = loadOrders().sortedForField()
                val upcoming = runCatching { reminders() }.getOrDefault(emptyList())
                ensureActive()
                _state.update { it.copy(loading = false, refreshing = false, orders = orders, reminders = upcoming, lastUpdated = now(), stale = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiException && e.status == 401) onUnauthorized()
                _state.update { it.copy(loading = false, refreshing = false, error = e.toGenericMessage(), stale = true) }
            }
        }.also { job = it }
    }
}

/** The server already orders by date, time and id; keep it stable and put finished orders last. */
internal fun List<HandlerOrderWork>.sortedForField(): List<HandlerOrderWork> =
    sortedWith(compareBy<HandlerOrderWork> { it.status.isTerminal }.thenBy { it.schedule.slotDate }.thenBy { it.schedule.startTime }.thenBy { it.id })

/** Groups with something still to hand over first, then by date and time. */
internal fun List<PickupGroupWork>.sortedForPickup(): List<PickupGroupWork> =
    sortedWith(compareBy<PickupGroupWork> { it.pendingCount == 0 }.thenBy { it.schedule.slotDate }.thenBy { it.schedule.startTime }.thenBy { it.groupKey })
