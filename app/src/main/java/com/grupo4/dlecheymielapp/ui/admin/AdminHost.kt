package com.grupo4.dlecheymielapp.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grupo4.dlecheymielapp.data.admin.AdminRepository
import com.grupo4.dlecheymielapp.data.reminders.ReminderScope
import com.grupo4.dlecheymielapp.data.reminders.RemindersRepository

private const val TAB_ORDERS = 0
private const val TAB_ASSIGNMENTS = 1
private const val TAB_INCIDENTS = 2

/** Admin entry point: three tabs (Pedidos, Asignaciones, Incidencias) and a detail screen on top of any of them. */
@Composable
fun AdminHost(repository: AdminRepository, reminders: RemindersRepository, userName: String, onUnauthorized: () -> Unit, onLogout: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(TAB_ORDERS) }
    var orderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var groupKey by rememberSaveable { mutableStateOf<String?>(null) }

    // Tab ViewModels live here so switching tabs keeps their lists and filters.
    val ordersVm: AdminOrdersViewModel = viewModel(factory = viewModelFactory { initializer { AdminOrdersViewModel(repository, { reminders.upcoming(ReminderScope.ADMIN).items }, onUnauthorized) } })
    val groupsVm: AdminGroupsViewModel = viewModel(factory = viewModelFactory { initializer { AdminGroupsViewModel(repository, onUnauthorized) } })
    val notificationsVm: AdminNotificationsViewModel = viewModel(factory = viewModelFactory { initializer { AdminNotificationsViewModel(repository, onUnauthorized) } })
    val notifications by notificationsVm.state.collectAsState()

    val openOrder = orderId
    val openGroup = groupKey

    when {
        openOrder != null -> {
            BackHandler { orderId = null }
            OrderDetail(openOrder, repository, onUnauthorized, onBack = {
                orderId = null
                // The order may have moved, been reassigned or had its incident resolved.
                ordersVm.refresh(); groupsVm.refresh(); notificationsVm.refresh()
            })
        }

        openGroup != null -> {
            BackHandler { groupKey = null }
            GroupDetail(openGroup, repository, onUnauthorized, onBack = { groupKey = null; groupsVm.refresh() }, onOpenOrder = { orderId = it })
        }

        else -> Scaffold(
            modifier = Modifier.safeDrawingPadding(),
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == TAB_ORDERS, onClick = { tab = TAB_ORDERS },
                        icon = { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null) }, label = { Text("Pedidos") },
                    )
                    NavigationBarItem(
                        selected = tab == TAB_ASSIGNMENTS, onClick = { tab = TAB_ASSIGNMENTS },
                        icon = { Icon(Icons.Filled.Groups, contentDescription = null) }, label = { Text("Asignaciones") },
                    )
                    NavigationBarItem(
                        selected = tab == TAB_INCIDENTS, onClick = { tab = TAB_INCIDENTS },
                        icon = {
                            BadgedBox(badge = { if (notifications.unread > 0) Badge { Text(notifications.unread.toString()) } }) {
                                Icon(Icons.Filled.Notifications, contentDescription = null)
                            }
                        },
                        label = { Text("Incidencias") },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    TAB_ORDERS -> {
                        val state by ordersVm.state.collectAsState()
                        AdminOrdersScreen(
                            state, onRefresh = ordersVm::refresh, onStatus = ordersVm::setStatus,
                            onOpenIncidentsOnly = ordersVm::setOpenIncidents, onSearch = ordersVm::search,
                            onOpenOrder = { orderId = it }, onLogout = onLogout, userName = userName,
                        )
                    }

                    TAB_ASSIGNMENTS -> {
                        val state by groupsVm.state.collectAsState()
                        AdminGroupsScreen(state, onRefresh = groupsVm::refresh, onOnlyUnassigned = groupsVm::setOnlyUnassigned, onOpenGroup = { groupKey = it })
                    }

                    else -> AdminNotificationsScreen(
                        notifications, onRefresh = notificationsVm::refresh, onUnreadOnly = notificationsVm::setUnreadOnly,
                        onOpen = { notificationsVm.markRead(it.id); orderId = it.orderId },
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderDetail(id: Long, repository: AdminRepository, onUnauthorized: () -> Unit, onBack: () -> Unit) {
    // One ViewModel per order so a different order never shows the previous one's state.
    val vm: AdminOrderDetailViewModel = viewModel(
        key = "admin-order-$id",
        factory = viewModelFactory { initializer { AdminOrderDetailViewModel(id, repository, onUnauthorized) } },
    )
    val state by vm.state.collectAsState()
    AdminOrderDetailScreen(
        state = state, nextStep = vm::nextStep,
        actions = AdminOrderActions(
            onBack = onBack, onRetry = vm::load, onAdvance = vm::advance,
            onOpenNote = vm::openNote, onNoteChange = vm::onNoteChange, onSubmitNote = vm::submitNote,
            onReassign = vm::openReassign, onReopen = vm::openReopen, onSelectSlot = vm::selectSlot, onSubmitSlot = vm::submitSlot,
            onAskLost = vm::askLost, onConfirmLost = vm::confirmLost,
            onAskUncollectible = vm::askUncollectible, onConfirmUncollectible = vm::confirmUncollectible,
            onAskDesist = vm::askDesist, onConfirmDesist = vm::confirmDesist,
            onAskRestoreStock = vm::askRestoreStock, onConfirmRestoreStock = vm::confirmRestoreStock,
            onRequestPaymentLink = vm::requestPaymentLink, onHidePaymentLink = vm::hidePaymentLink,
            onOpenAssign = vm::openAssign, onChoose = vm::choose, onCloseDialog = vm::closeDialog,
        ),
    )
}

@Composable
private fun GroupDetail(key: String, repository: AdminRepository, onUnauthorized: () -> Unit, onBack: () -> Unit, onOpenOrder: (Long) -> Unit) {
    val vm: AdminGroupDetailViewModel = viewModel(
        key = "admin-group-$key",
        factory = viewModelFactory { initializer { AdminGroupDetailViewModel(key, repository, onUnauthorized) } },
    )
    val state by vm.state.collectAsState()
    AdminGroupDetailScreen(
        state = state, nextStep = vm::nextStep, onBack = onBack, onRetry = vm::load, onAdvance = vm::advanceGroup,
        onOpenCandidates = vm::openCandidates, onCloseCandidates = vm::closeCandidates, onAssign = vm::assign,
        onAskUnassign = vm::askUnassign, onUnassign = vm::unassign, onOpenOrder = onOpenOrder,
    )
}
