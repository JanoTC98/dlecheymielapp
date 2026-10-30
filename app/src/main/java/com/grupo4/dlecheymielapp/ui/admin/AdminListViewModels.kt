package com.grupo4.dlecheymielapp.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.data.admin.AdminRepository
import com.grupo4.dlecheymielapp.data.admin.AdminOrderSummary
import com.grupo4.dlecheymielapp.data.admin.HandlerCandidate
import com.grupo4.dlecheymielapp.data.admin.IncidentNotification
import com.grupo4.dlecheymielapp.data.admin.OrderFilters
import com.grupo4.dlecheymielapp.data.admin.PickupGroupDetail
import com.grupo4.dlecheymielapp.data.admin.PickupGroupSummary
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import com.grupo4.dlecheymielapp.data.reminders.UpcomingReminder
import com.grupo4.dlecheymielapp.ui.common.toAdminMessage
import com.grupo4.dlecheymielapp.ui.common.toGenericMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun Throwable.adminText(onUnauthorized: () -> Unit): String {
    if (this is ApiException && status == 401) onUnauthorized()
    return if (this is ApiException) toAdminMessage() else toGenericMessage()
}

// ---------------------------------------------------------------- Pedidos (cola)

data class AdminOrdersState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val orders: List<AdminOrderSummary> = emptyList(),
    val newCount: Long = 0,
    val reminders: List<UpcomingReminder> = emptyList(),
    val filters: OrderFilters = OrderFilters(),
    val error: String? = null,
)

class AdminOrdersViewModel(
    private val repository: AdminRepository,
    private val reminders: suspend () -> List<UpcomingReminder>,
    private val onUnauthorized: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminOrdersState())
    val state: StateFlow<AdminOrdersState> = _state.asStateFlow()
    private var job: Job? = null

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    fun setStatus(status: OrderStatus?) = changeFilters { it.copy(status = status) }

    fun setOpenIncidents(only: Boolean) = changeFilters { it.copy(incident = if (only) "open" else null) }

    fun search(code: String) = changeFilters { it.copy(code = code) }

    private fun changeFilters(change: (OrderFilters) -> OrderFilters) {
        _state.update { it.copy(filters = change(it.filters)) }
        load(initial = true)
    }

    private fun load(initial: Boolean) {
        job?.cancel()
        _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
        job = viewModelScope.launch {
            try {
                val filters = _state.value.filters
                val page = repository.orders(filters)
                val count = runCatching { repository.newCount() }.getOrDefault(_state.value.newCount)
                val upcoming = runCatching { reminders() }.getOrDefault(_state.value.reminders)
                _state.update { it.copy(loading = false, refreshing = false, orders = page.items, newCount = count, reminders = upcoming) }
            } catch (e: Exception) {
                val text = e.adminText(onUnauthorized)
                _state.update { it.copy(loading = false, refreshing = false, error = text) }
            }
        }
    }
}

// ---------------------------------------------------------------- Asignaciones: grupos de recojo

data class AdminGroupsState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val groups: List<PickupGroupSummary> = emptyList(),
    val onlyUnassigned: Boolean = false,
    val error: String? = null,
)

class AdminGroupsViewModel(
    private val repository: AdminRepository,
    private val onUnauthorized: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminGroupsState())
    val state: StateFlow<AdminGroupsState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    fun setOnlyUnassigned(only: Boolean) {
        _state.update { it.copy(onlyUnassigned = only) }
        load(initial = true)
    }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
        viewModelScope.launch {
            try {
                val groups = repository.pickupGroups(assigned = if (_state.value.onlyUnassigned) false else null)
                _state.update { it.copy(loading = false, refreshing = false, groups = groups) }
            } catch (e: Exception) {
                val text = e.adminText(onUnauthorized)
                _state.update { it.copy(loading = false, refreshing = false, error = text) }
            }
        }
    }
}

// ---------------------------------------------------------------- Asignaciones: un grupo

data class AdminGroupDetailState(
    val loading: Boolean = true,
    val group: PickupGroupDetail? = null,
    val error: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val actionError: String? = null,
    /** Non-null while the "choose Encargado" dialog is open. */
    val candidates: CandidatesState? = null,
    val confirmUnassign: Boolean = false,
)

data class CandidatesState(val loading: Boolean = true, val items: List<HandlerCandidate> = emptyList(), val error: String? = null)

class AdminGroupDetailViewModel(
    private val key: String,
    private val repository: AdminRepository,
    private val onUnauthorized: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminGroupDetailState())
    val state: StateFlow<AdminGroupDetailState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = it.group == null, error = null) }
        viewModelScope.launch {
            try {
                val group = repository.pickupGroup(key)
                _state.update { it.copy(loading = false, group = group) }
            } catch (e: Exception) {
                val text = e.adminText(onUnauthorized)
                _state.update { it.copy(loading = false, error = text) }
            }
        }
    }

    /** The group moves one step together; orders that cannot move are reported back as skipped. */
    fun nextStep(group: PickupGroupDetail): Triple<OrderStatus, OrderStatus, String>? {
        val statuses = group.orders.map { it.status }
        return when {
            statuses.any { it == OrderStatus.CONFIRMED } -> Triple(OrderStatus.CONFIRMED, OrderStatus.PREPARING, "Pasar el grupo a En preparación")
            statuses.any { it == OrderStatus.PREPARING } -> Triple(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, "Marcar el grupo Listo para recoger")
            else -> null
        }
    }

    fun advanceGroup() {
        val group = _state.value.group ?: return
        val (from, to, _) = nextStep(group) ?: return
        act(null) {
            val result = repository.advanceGroup(key, from, to)
            val skipped = if (result.skipped.isEmpty()) "" else " ${result.skipped.size} no se pudieron mover."
            "Avanzaron ${result.advanced.size} pedidos.$skipped"
        }
    }

    fun openCandidates() {
        _state.update { it.copy(candidates = CandidatesState(), actionError = null) }
        viewModelScope.launch {
            try {
                val items = repository.groupCandidates(key)
                _state.update { it.copy(candidates = CandidatesState(loading = false, items = items)) }
            } catch (e: Exception) {
                val text = e.adminText(onUnauthorized)
                _state.update { it.copy(candidates = CandidatesState(loading = false, error = text)) }
            }
        }
    }

    fun closeCandidates() = _state.update { it.copy(candidates = null) }

    fun assign(handlerId: Long) = act("Encargado asignado al grupo.") { repository.assignGroup(key, handlerId); null }

    fun askUnassign(show: Boolean) = _state.update { it.copy(confirmUnassign = show) }

    fun unassign() = act("Se quitó la asignación del grupo.") { repository.unassignGroup(key); null }

    fun clearMessage() = _state.update { it.copy(message = null, actionError = null) }

    private fun act(success: String?, block: suspend () -> String?) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null, actionError = null) }
        viewModelScope.launch {
            try {
                val custom = block()
                val group = repository.pickupGroup(key)
                _state.update { it.copy(busy = false, group = group, candidates = null, confirmUnassign = false, message = custom ?: success) }
            } catch (e: Exception) {
                val text = e.adminText(onUnauthorized)
                _state.update { it.copy(busy = false, actionError = text, candidates = null, confirmUnassign = false) }
                if (e is ApiException && e.code == "ORDER_STATE_CHANGED") load()
            }
        }
    }
}

// ---------------------------------------------------------------- Incidencias (campana)

data class AdminNotificationsState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val items: List<IncidentNotification> = emptyList(),
    val unreadOnly: Boolean = false,
    val error: String? = null,
) {
    val unread: Int get() = items.count { !it.read }
}

class AdminNotificationsViewModel(
    private val repository: AdminRepository,
    private val onUnauthorized: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(AdminNotificationsState())
    val state: StateFlow<AdminNotificationsState> = _state.asStateFlow()

    init { load(initial = true) }

    fun refresh() = load(initial = false)

    fun setUnreadOnly(only: Boolean) {
        _state.update { it.copy(unreadOnly = only) }
        load(initial = true)
    }

    /** Opening a notification marks it read; a failure here never blocks opening the order. */
    fun markRead(id: Long) {
        _state.update { s -> s.copy(items = s.items.map { if (it.id == id) it.copy(read = true) else it }) }
        viewModelScope.launch { runCatching { repository.markRead(id) } }
    }

    private fun load(initial: Boolean) {
        _state.update { it.copy(loading = initial, refreshing = !initial, error = null) }
        viewModelScope.launch {
            try {
                val items = repository.notifications(_state.value.unreadOnly)
                _state.update { it.copy(loading = false, refreshing = false, items = items) }
            } catch (e: Exception) {
                val text = e.adminText(onUnauthorized)
                _state.update { it.copy(loading = false, refreshing = false, error = text) }
            }
        }
    }
}
