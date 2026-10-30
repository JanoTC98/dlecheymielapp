package com.grupo4.dlecheymielapp.notifications

import android.content.Context
import com.grupo4.dlecheymielapp.data.admin.AdminRepository
import com.grupo4.dlecheymielapp.data.admin.OrderFilters
import com.grupo4.dlecheymielapp.data.admin.title
import com.grupo4.dlecheymielapp.data.auth.AuthRole
import com.grupo4.dlecheymielapp.data.auth.AuthUser
import com.grupo4.dlecheymielapp.data.fulfillment.DeliveryRepository
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerOrderWork
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerRepository
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerType
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus

/** One thing worth telling the user about. [key] identifies it across checks, so the same work is never announced twice. */
data class AlertItem(val key: String, val title: String, val text: String)

/** Remembers which work each user has already been told about. Only keys are stored, never customer data. */
interface SeenStore {
    /** The keys seen at the last check, or null when this user has never been checked. */
    fun load(userId: Long): Set<String>?

    fun save(userId: Long, keys: Set<String>)

    fun clear()
}

/**
 * Compares the work the server lists now with what the user was last told. The first check of a user only records the
 * baseline: opening the app with a full queue must not raise a notification for every order already there.
 */
class NewWorkDetector(private val store: SeenStore) {
    fun newItems(userId: Long, current: List<AlertItem>): List<AlertItem> {
        val seen = store.load(userId)
        store.save(userId, current.mapTo(HashSet()) { it.key })
        return if (seen == null) emptyList() else current.filter { it.key !in seen }
    }
}

class SharedPreferencesSeenStore(context: Context) : SeenStore {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(userId: Long): Set<String>? = prefs.getStringSet(key(userId), null)?.toSet()

    override fun save(userId: Long, keys: Set<String>) {
        prefs.edit().putStringSet(key(userId), keys).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(userId: Long) = "seen_$userId"

    companion object {
        /** Kept out of backups and device transfers, like the session cookie (see res/xml/data_extraction_rules.xml). */
        const val FILE = "dleche_order_alerts"
    }
}

/**
 * Lists, for each role, the work that should raise a notification when it appears. It reads the same queues the screens
 * read, so what the notification announces is what the user finds when opening the app.
 */
class OrderAlertSource(
    private val admin: AdminRepository,
    private val handler: HandlerRepository,
    private val delivery: DeliveryRepository,
    private val operator: DeliveryRepository,
) {
    suspend fun items(user: AuthUser): List<AlertItem> = when (user.role) {
        AuthRole.ADMIN -> adminItems()
        AuthRole.DELIVERY_HANDLER -> handlerItems()
        AuthRole.DELIVERY -> queueItems(delivery.orders(), "Nuevo pedido de delivery")
        AuthRole.DELIVERY_COORDINATOR -> queueItems(operator.orders(), "Nuevo pedido para coordinar")
        AuthRole.CLIENT -> emptyList()
    }

    /** New paid orders waiting to be prepared, plus the incident notices the bell shows as unread. */
    private suspend fun adminItems(): List<AlertItem> {
        val orders = admin.orders(OrderFilters(status = OrderStatus.CONFIRMED)).items.map {
            AlertItem("order:${it.id}", "Nuevo pedido", "${it.publicCode ?: "Pedido #${it.id}"} · ${it.schedule.slotDate} ${it.schedule.timeRange}")
        }
        val notices = admin.notifications(unreadOnly = true).map {
            AlertItem("notice:${it.id}", it.title(), listOfNotNull(it.orderPublicCode ?: "Pedido #${it.orderId}", it.summary.takeIf(String::isNotBlank)).joinToString(" · "))
        }
        return orders + notices
    }

    /** The pickup Encargado is assigned whole groups; the in-person Encargado is assigned single orders. */
    private suspend fun handlerItems(): List<AlertItem> =
        if (handler.profile().handlerType == HandlerType.PICKUP) {
            // The count is part of the key: orders added to a group already assigned are news too.
            handler.pickupGroups().map {
                val place = listOfNotNull(it.pickupPoint.name, it.university.name).joinToString(" · ")
                AlertItem("group:${it.groupKey}:${it.orderCount}", "Pedidos de recojo asignados", "$place · ${it.schedule.slotDate} ${it.schedule.timeRange} · ${it.orderCount} pedido(s)")
            }
        } else {
            queueItems(handler.orders(), "Nuevo pedido asignado")
        }

    private fun queueItems(orders: List<HandlerOrderWork>, title: String): List<AlertItem> =
        orders.filterNot { it.status.isTerminal }.map {
            AlertItem("order:${it.id}", title, "${it.publicCode ?: "Pedido #${it.id}"} · ${it.schedule.slotDate} ${it.schedule.timeRange}")
        }
}
