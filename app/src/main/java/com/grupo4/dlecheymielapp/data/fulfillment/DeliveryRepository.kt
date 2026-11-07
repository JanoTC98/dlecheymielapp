package com.grupo4.dlecheymielapp.data.fulfillment

import com.grupo4.dlecheymielapp.core.network.apiCall
import com.grupo4.dlecheymielapp.data.admin.AdvanceBody
import kotlinx.serialization.json.Json

/** Which role's endpoints a [DeliveryRepository] talks to. */
enum class DeliveryScope(val path: String) { DELIVERY("delivery"), OPERATOR("operator") }

class DeliveryRepository(
    private val api: DeliveryApi,
    private val json: Json,
    val scope: DeliveryScope,
) : FieldGateway {
    private val operator get() = scope == DeliveryScope.OPERATOR

    override val capabilities = FieldCapabilities(canAdvance = true, canComplete = operator, canRegisterProvider = operator)

    /** The list keeps every status while the order stays assigned; finished orders are dropped here, like the web. */
    suspend fun orders(): List<HandlerOrderWork> =
        apiCall(json) { api.orders(scope.path) }.items.map { it.toWork() }.filterNot { it.status.isTerminal }

    override suspend fun order(id: Long) = apiCall(json) { api.order(scope.path, id) }.toWork()

    override suspend fun incidents(id: Long): List<FieldIncident> =
        if (operator) apiCall(json) { api.coordination(id) }.incidents else apiCall(json) { api.incidents(scope.path, id) }

    override suspend fun handoff(id: Long, code: String) =
        apiCall(json) { api.handoff(scope.path, id, HandoffRequest(code.trim().uppercase())) }

    override suspend fun reportIncident(id: Long, description: String) =
        reportIncidentCall(json) { api.reportIncident(scope.path, id, IncidentRequest(description.trim())) }

    override suspend fun paymentLink(id: Long) = apiCall(json) { api.paymentLink(id) }.url

    /** Only the two preparation steps are allowed to these roles; closing is a separate action. */
    override suspend fun advance(id: Long, expected: OrderStatus) {
        val target = when (expected) {
            OrderStatus.CONFIRMED -> OrderStatus.PREPARING
            OrderStatus.PREPARING -> OrderStatus.IN_TRANSIT
            else -> throw IllegalArgumentException("No hay un siguiente paso desde $expected")
        }
        apiCall(json) { api.advance(scope.path, id, AdvanceBody(expected, target)) }
    }

    override suspend fun complete(id: Long) = apiCall(json) { api.complete(id) }

    override suspend fun provider(id: Long): Provider? =
        if (operator) apiCall(json) { api.coordination(id) }.provider else null

    override suspend fun saveProvider(id: Long, provider: Provider) =
        apiCall(json) { api.saveProvider(id, Provider(provider.name.trim(), provider.phone.trim())) }
}

/** The list row has no customer name or phone, so the district stands in as the headline and the address preview as the place. */
private fun DeliverySummary.toWork() = HandlerOrderWork(
    id = id, publicCode = publicCode, status = status, mode = FulfillmentMode.DELIVERY, schedule = schedule,
    recipient = Recipient(name = districtName ?: "Delivery", phone = "", place = addressPreview.orEmpty()),
    customerName = districtName ?: "Delivery", incidentCount = incidentCount, payAtDelivery = payAtDelivery, openIncidentCount = openIncidentCount,
)

/**
 * The address plus the district, without repeating the district when the address already ends with it ("..., Los Olivos").
 * Only a trailing ", district" counts as a repeat, so a street or avenue that merely contains the name keeps every part.
 */
internal fun deliveryPlace(address: String?, district: String?): String {
    val street = address?.trim().orEmpty()
    val name = district?.trim().orEmpty()
    if (name.isEmpty()) return street
    if (street.isBlank()) return name
    val withoutTail = street.trimEnd('.', ',', ';', ' ')
    val repeated = withoutTail.equals(name, ignoreCase = true) || withoutTail.endsWith(", $name", ignoreCase = true)
    return if (repeated) street else "$street, $name"
}

private fun DeliveryDetail.toWork() = HandlerOrderWork(
    id = id, publicCode = publicCode, status = status, mode = FulfillmentMode.DELIVERY, schedule = schedule,
    recipient = Recipient(
        name = customerName, phone = delivery.phone.orEmpty(),
        place = deliveryPlace(delivery.address, delivery.districtName),
        reference = delivery.reference,
    ),
    customerName = customerName, incidentCount = incidentCount, payAtDelivery = payAtDelivery, openIncidentCount = openIncidentCount,
)
