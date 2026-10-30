package com.grupo4.dlecheymielapp.data.fulfillment

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Encargado endpoints (everything under `/handler`). Pickup groups come later; this covers "entrega en persona". */
interface HandlerApi {
    @GET("api/v1/handler/profile")
    suspend fun profile(): HandlerProfile

    @GET("api/v1/handler/pickup-groups")
    suspend fun pickupGroups(@Query("state") state: String, @Query("page") page: Int = 0, @Query("size") size: Int = 50): Page<PickupGroupWork>

    @GET("api/v1/handler/pickup-groups/{key}")
    suspend fun pickupGroup(@Path("key") key: String): PickupGroupWorkDetail

    @GET("api/v1/handler/orders")
    suspend fun orders(@Query("page") page: Int = 0, @Query("size") size: Int = 50): Page<HandlerOrderWork>

    @GET("api/v1/handler/orders/{id}")
    suspend fun order(@Path("id") id: Long): HandlerOrderWork

    @POST("api/v1/handler/orders/{id}/handoff")
    suspend fun handoff(@Path("id") id: Long, @Body body: HandoffRequest): HandoffResult

    @GET("api/v1/handler/orders/{id}/incidents")
    suspend fun incidents(@Path("id") id: Long): List<FieldIncident>

    @POST("api/v1/handler/orders/{id}/incidents")
    suspend fun reportIncident(@Path("id") id: Long, @Body body: IncidentRequest): FieldIncident

    /** Any signed-in staff role; the server checks role and modality. The URL is a credential: never log or cache it. */
    @GET("api/v1/fulfillment/orders/{id}/payment-link")
    suspend fun paymentLink(@Path("id") id: Long): PaymentLink
}
