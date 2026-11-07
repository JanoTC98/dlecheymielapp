package com.grupo4.dlecheymielapp.data.fulfillment

import com.grupo4.dlecheymielapp.data.admin.AdvanceBody
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class DeliverySummary(
    val id: Long,
    val publicCode: String? = null,
    val status: OrderStatus = OrderStatus.UNKNOWN,
    val schedule: FieldSchedule,
    val districtName: String? = null,
    val addressPreview: String? = null,
    val incidentCount: Int = 0,
    val payAtDelivery: PayAtDeliveryInfo? = null,
    val openIncidentCount: Int = incidentCount,
)

@Serializable
data class DeliveryInfo(
    val districtName: String? = null,
    val address: String? = null,
    val reference: String? = null,
    val phone: String? = null,
)

@Serializable
data class DeliveryDetail(
    val id: Long,
    val publicCode: String? = null,
    val status: OrderStatus = OrderStatus.UNKNOWN,
    val schedule: FieldSchedule,
    val customerName: String = "",
    val delivery: DeliveryInfo = DeliveryInfo(),
    val incidentCount: Int = 0,
    val provider: Provider? = null,
    val payAtDelivery: PayAtDeliveryInfo? = null,
    val openIncidentCount: Int = incidentCount,
)

/** The Operador has no incident list of its own: incidents and the provider arrive together. */
@Serializable
data class Coordination(val provider: Provider? = null, val incidents: List<FieldIncident> = emptyList())

/**
 * Delivery (employee) and Operador endpoints. Both roles share the same shapes under `/delivery` and `/operator`,
 * so the role goes in as the first path segment ([scope]).
 */
interface DeliveryApi {
    @GET("api/v1/{scope}/orders")
    suspend fun orders(@Path("scope") scope: String, @Query("page") page: Int = 0, @Query("size") size: Int = 50): Page<DeliverySummary>

    @GET("api/v1/{scope}/orders/{id}")
    suspend fun order(@Path("scope") scope: String, @Path("id") id: Long): DeliveryDetail

    @POST("api/v1/{scope}/orders/{id}/advance")
    suspend fun advance(@Path("scope") scope: String, @Path("id") id: Long, @Body body: AdvanceBody)

    @POST("api/v1/{scope}/orders/{id}/handoff")
    suspend fun handoff(@Path("scope") scope: String, @Path("id") id: Long, @Body body: HandoffRequest): HandoffResult

    @GET("api/v1/{scope}/orders/{id}/incidents")
    suspend fun incidents(@Path("scope") scope: String, @Path("id") id: Long): List<FieldIncident>

    @POST("api/v1/{scope}/orders/{id}/incidents")
    suspend fun reportIncident(@Path("scope") scope: String, @Path("id") id: Long, @Body body: IncidentRequest): retrofit2.Response<FieldIncident>

    /** Operador only. Takes no body (the server answers 400 if one is sent). */
    @POST("api/v1/operator/orders/{id}/complete")
    suspend fun complete(@Path("id") id: Long)

    @GET("api/v1/operator/orders/{id}/coordination")
    suspend fun coordination(@Path("id") id: Long): Coordination

    @PUT("api/v1/operator/orders/{id}/provider")
    suspend fun saveProvider(@Path("id") id: Long, @Body body: Provider)

    /** The URL is a credential: never log or cache it. */
    @GET("api/v1/fulfillment/orders/{id}/payment-link")
    suspend fun paymentLink(@Path("id") id: Long): PaymentLink
}
