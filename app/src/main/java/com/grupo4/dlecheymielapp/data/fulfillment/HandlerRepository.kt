package com.grupo4.dlecheymielapp.data.fulfillment

import com.grupo4.dlecheymielapp.core.network.apiCall
import kotlinx.serialization.json.Json

class HandlerRepository(private val api: HandlerApi, private val json: Json) : FieldGateway {
    suspend fun profile() = apiCall(json) { api.profile() }

    /** Pickup Encargados only. `pending` lists groups with a ready order plus assigned ones still in preparation, like the web. */
    suspend fun pickupGroups(): List<PickupGroupWork> = apiCall(json) { api.pickupGroups("pending") }.items

    suspend fun pickupGroup(key: String) = apiCall(json) { api.pickupGroup(key) }

    suspend fun orders(): List<HandlerOrderWork> = apiCall(json) { api.orders() }.items

    override suspend fun order(id: Long) = apiCall(json) { api.order(id) }

    override suspend fun incidents(id: Long) = apiCall(json) { api.incidents(id) }

    /** The code is trimmed and upper-cased like the web does; the server ignores case and surrounding spaces anyway. */
    override suspend fun handoff(id: Long, code: String) =
        apiCall(json) { api.handoff(id, HandoffRequest(code.trim().uppercase())) }

    override suspend fun reportIncident(id: Long, description: String) =
        apiCall(json) { api.reportIncident(id, IncidentRequest(description.trim())) }

    override suspend fun paymentLink(id: Long) = apiCall(json) { api.paymentLink(id) }.url
}
