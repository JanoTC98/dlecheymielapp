package com.grupo4.dlecheymielapp.data.fulfillment

import kotlinx.serialization.Serializable

/** External service registered by the Operador for a delivery; internal data, never shown to the customer. */
@Serializable
data class Provider(val name: String, val phone: String)

/** What a role can do on an order beyond the common handoff / incident / payment-link actions. */
data class FieldCapabilities(
    /** Delivery and Operador move their own orders CONFIRMED → PREPARING → IN_TRANSIT. */
    val canAdvance: Boolean = false,
    /** Operador closes an order paid in advance without a code. */
    val canComplete: Boolean = false,
    val canRegisterProvider: Boolean = false,
)

/**
 * The calls one field role's order screen needs. The Encargado, Delivery and Operador have different URLs but the same
 * screen, so [HandlerRepository] and [DeliveryRepository] both implement it and the order ViewModel does not care which.
 */
interface FieldGateway {
    val capabilities: FieldCapabilities get() = FieldCapabilities()

    suspend fun order(id: Long): HandlerOrderWork

    suspend fun incidents(id: Long): List<FieldIncident>

    suspend fun handoff(id: Long, code: String): HandoffResult

    suspend fun reportIncident(id: Long, description: String): FieldIncident

    suspend fun paymentLink(id: Long): String

    suspend fun advance(id: Long, expected: OrderStatus): Unit = throw UnsupportedOperationException("advance")

    suspend fun complete(id: Long): Unit = throw UnsupportedOperationException("complete")

    suspend fun provider(id: Long): Provider? = null

    suspend fun saveProvider(id: Long, provider: Provider): Unit = throw UnsupportedOperationException("provider")
}
