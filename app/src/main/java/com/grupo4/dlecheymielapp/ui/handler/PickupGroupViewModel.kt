package com.grupo4.dlecheymielapp.ui.handler

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerRepository
import com.grupo4.dlecheymielapp.data.fulfillment.PickupGroupWorkDetail
import com.grupo4.dlecheymielapp.ui.common.toGenericMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PickupGroupState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val group: PickupGroupWorkDetail? = null,
    val error: String? = null,
)

class PickupGroupViewModel(
    private val key: String,
    private val repository: HandlerRepository,
    private val onUnauthorized: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(PickupGroupState())
    val state: StateFlow<PickupGroupState> = _state.asStateFlow()

    /** The customer's code is typed on each order's card, as at the counter of the web page. */
    val cardActions = CardActions(viewModelScope, repository, onUnauthorized, onChanged = { load(quiet = true) })

    init { load() }

    fun load(quiet: Boolean = false) {
        _state.update { it.copy(loading = it.group == null, refreshing = it.group != null && !quiet, error = null) }
        viewModelScope.launch {
            try {
                val group = repository.pickupGroup(key)
                _state.update { it.copy(loading = false, refreshing = false, group = group) }
            } catch (e: Exception) {
                if (e is ApiException && e.status == 401) onUnauthorized()
                val text = if (e is ApiException && e.code == "GROUP_NOT_FOUND") "Este grupo ya no está asignado a su cuenta." else e.toGenericMessage()
                _state.update { it.copy(loading = false, refreshing = false, error = text) }
            }
        }
    }
}
