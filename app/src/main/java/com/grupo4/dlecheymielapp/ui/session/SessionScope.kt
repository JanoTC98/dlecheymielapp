package com.grupo4.dlecheymielapp.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Keeps the screens' ViewModels of ONE signed-in user. It lives in the Activity so a rotation keeps the lists, but it is
 * emptied the moment the session ends or a different user signs in: nobody may ever see the previous user's data.
 */
class SessionStores : ViewModel() {
    private var current: Pair<Long, ViewModelStore>? = null

    fun storeFor(userId: Long): ViewModelStore {
        current?.let { (id, store) ->
            if (id == userId) return store
            store.clear()
        }
        return ViewModelStore().also { current = userId to it }
    }

    fun clear() {
        current?.second?.clear()
        current = null
    }

    override fun onCleared() = clear()
}

/** Everything inside gets its own ViewModel store and its own remembered state, both tied to [userId]. */
@Composable
fun SessionScope(userId: Long, stores: SessionStores, content: @Composable () -> Unit) {
    val owner = remember(userId) {
        val store = stores.storeFor(userId)
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = store
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
        key(userId) { content() }
    }
}
