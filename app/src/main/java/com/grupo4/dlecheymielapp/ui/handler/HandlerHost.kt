package com.grupo4.dlecheymielapp.ui.handler

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.grupo4.dlecheymielapp.data.fulfillment.FieldGateway
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerOrderWork
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerRepository
import com.grupo4.dlecheymielapp.data.reminders.ReminderScope
import com.grupo4.dlecheymielapp.data.reminders.RemindersRepository
import com.grupo4.dlecheymielapp.data.reminders.UpcomingReminder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Encargado entry point. The profile decides the view: in-person delivery lists orders; pickup lists groups,
 * then a group's orders. An order's detail opens on top of either.
 *
 * The ViewModels belong to the signed-in user (see SessionScope), so a different account never inherits this one's profile.
 */
@Composable
fun HandlerHost(repository: HandlerRepository, reminders: RemindersRepository, onUnauthorized: () -> Unit, onLogout: () -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }

    val listVm: HandlerListViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HandlerListViewModel(repository, { reminders.upcoming(ReminderScope.HANDLER).items }, onUnauthorized) }
        },
    )
    val listState by listVm.state.collectAsState()
    val listActions by listVm.cardActions.state.collectAsState()

    val id = selectedId
    val group = selectedGroup
    // The open group's ViewModel outlives an order's detail, so coming back can refresh it.
    val groupVm: PickupGroupViewModel? = if (listState.isPickupHandler && group != null) {
        viewModel(
            key = "group-$group",
            factory = viewModelFactory { initializer { PickupGroupViewModel(group, repository, onUnauthorized) } },
        )
    } else {
        null
    }
    // Coming back from the background refreshes the list (and the open group). The first resume opened the screen already.
    val firstResume = remember { AtomicBoolean(true) }
    LifecycleResumeEffect(Unit) {
        if (!firstResume.getAndSet(false)) { listVm.refresh(); groupVm?.load(quiet = true) }
        onPauseOrDispose {}
    }

    // The system Back and the in-app back share these, so both refresh the same way.
    // The list shows status and incident counts, so coming back refreshes it.
    val closeOrder: () -> Unit = { selectedId = null; listVm.refresh(); groupVm?.load(quiet = true) }
    val closeGroup: () -> Unit = { selectedGroup = null; listVm.refresh() }
    when {
        id != null -> {
            BackHandler { closeOrder() }
            FieldOrderDetail(id, repository, onUnauthorized, onBack = closeOrder)
        }

        groupVm != null -> {
            BackHandler { closeGroup() }
            val groupState by groupVm.state.collectAsState()
            val groupActions by groupVm.cardActions.state.collectAsState()
            PickupGroupScreen(
                groupState, groupActions, groupVm.cardActions,
                onBack = closeGroup, onRetry = { groupVm.load() }, onOpenOrder = { selectedId = it },
            )
        }

        listState.isPickupHandler ->
            PickupGroupsScreen(listState, onRefresh = listVm::refresh, onOpenGroup = { selectedGroup = it }, onLogout = onLogout)

        else -> HandlerOrdersScreen(
            listState, listActions, listVm.cardActions, onRefresh = listVm::refresh, onOpenOrder = { selectedId = it }, onLogout = onLogout,
            eyebrow = "OPERACIONES", title = "Entrega en persona", subtitle = listState.profile?.displayName,
        )
    }
}

/**
 * Delivery (employee) and Operador entry point: their queue, with an order's detail on top.
 * Both use the same screens; only the gateway (URLs, abilities) and the texts differ.
 */
@Composable
fun FieldQueueHost(
    gateway: FieldGateway,
    loadOrders: suspend () -> List<HandlerOrderWork>,
    reminders: suspend () -> List<UpcomingReminder>,
    eyebrow: String,
    title: String,
    subtitle: String?,
    emptyText: String,
    onUnauthorized: () -> Unit,
    onLogout: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val listVm: FieldListViewModel = viewModel(
        key = "queue-$title",
        factory = viewModelFactory { initializer { FieldListViewModel(gateway, loadOrders, reminders, onUnauthorized) } },
    )
    val listState by listVm.state.collectAsState()
    val listActions by listVm.cardActions.state.collectAsState()

    val firstResume = remember { AtomicBoolean(true) }
    LifecycleResumeEffect(Unit) {
        if (!firstResume.getAndSet(false)) listVm.refresh()
        onPauseOrDispose {}
    }

    val id = selectedId
    val closeOrder: () -> Unit = { selectedId = null; listVm.refresh() }
    if (id == null) {
        HandlerOrdersScreen(
            listState, listActions, listVm.cardActions, onRefresh = listVm::refresh, onOpenOrder = { selectedId = it }, onLogout = onLogout,
            eyebrow = eyebrow, title = title, subtitle = subtitle, emptyText = emptyText,
        )
    } else {
        BackHandler { closeOrder() }
        FieldOrderDetail(id, gateway, onUnauthorized, onBack = closeOrder)
    }
}

/** One order's screen for any field role. One ViewModel per order so a different order never shows the previous one's state. */
@Composable
fun FieldOrderDetail(id: Long, gateway: FieldGateway, onUnauthorized: () -> Unit, onBack: () -> Unit) {
    val vm: OrderDetailViewModel = viewModel(
        key = "order-$id",
        factory = viewModelFactory { initializer { OrderDetailViewModel(id, gateway, onUnauthorized) } },
    )
    val state by vm.state.collectAsState()
    // The payment link must not outlive the screen that shows it.
    DisposableEffect(vm) { onDispose { vm.hidePaymentLink() } }
    OrderDetailScreen(
        state = state,
        onBack = onBack,
        onRetry = vm::load,
        onCodeChange = vm::onCodeChange,
        onSubmitHandoff = vm::submitHandoff,
        onOpenIncident = vm::openIncidentDialog,
        onCloseIncident = vm::closeIncidentDialog,
        onIncidentTextChange = vm::onIncidentTextChange,
        onSubmitIncident = vm::submitIncident,
        onRequestPaymentLink = vm::requestPaymentLink,
        onAdvance = vm::advance,
        onAskComplete = vm::askComplete,
        onComplete = vm::complete,
        onProviderName = vm::onProviderName,
        onProviderPhone = vm::onProviderPhone,
        onSaveProvider = vm::saveProvider,
    )
}
