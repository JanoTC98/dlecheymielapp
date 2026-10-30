package com.grupo4.dlecheymielapp.data.admin

import com.grupo4.dlecheymielapp.data.fulfillment.Page
import com.grupo4.dlecheymielapp.data.fulfillment.PaymentLink
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Admin endpoints (everything under `/admin`). Paths and bodies follow section 5 of docs/MAPA_DE_PROCESOS.md. */
interface AdminApi {
    // ---- Pedidos ----
    @GET("api/v1/admin/orders")
    suspend fun orders(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("code") code: String? = null,
        @Query("customer") customer: String? = null,
        @Query("status") status: String? = null,
        @Query("fulfillmentMode") mode: String? = null,
        @Query("fulfillmentDateFrom") dateFrom: String? = null,
        @Query("fulfillmentDateTo") dateTo: String? = null,
        @Query("incident") incident: String? = null,
    ): Page<AdminOrderSummary>

    @GET("api/v1/admin/orders/new-count")
    suspend fun newCount(): NewOrderCount

    @GET("api/v1/admin/orders/{id}")
    suspend fun order(@Path("id") id: Long): AdminOrderDetail

    @POST("api/v1/admin/orders/{id}/notes")
    suspend fun addNote(@Path("id") id: Long, @Body body: NoteBody): AdminNote

    @POST("api/v1/admin/orders/{id}/advance")
    suspend fun advance(@Path("id") id: Long, @Body body: AdvanceBody)

    // ---- Incidencias ----
    @GET("api/v1/admin/orders/{id}/reschedule-slots")
    suspend fun rescheduleSlots(@Path("id") id: Long, @Query("date") date: String? = null): List<RescheduleSlot>

    @POST("api/v1/admin/orders/{id}/incidents/{incidentId}/reassign")
    suspend fun reassign(@Path("id") id: Long, @Path("incidentId") incidentId: Long, @Body slot: SlotBody): Rescheduled

    @POST("api/v1/admin/orders/{id}/incidents/{incidentId}/mark-lost")
    suspend fun markLost(@Path("id") id: Long, @Path("incidentId") incidentId: Long)

    @POST("api/v1/admin/orders/{id}/incidents/{incidentId}/uncollectible")
    suspend fun uncollectible(@Path("id") id: Long, @Path("incidentId") incidentId: Long)

    @POST("api/v1/admin/orders/{id}/incidents/{incidentId}/desist")
    suspend fun desist(@Path("id") id: Long, @Path("incidentId") incidentId: Long, @Body body: DesistBody): DesistResult

    @POST("api/v1/admin/orders/{id}/restore-stock")
    suspend fun restoreStock(@Path("id") id: Long): RestoreResult

    @POST("api/v1/admin/orders/{id}/reopen")
    suspend fun reopen(@Path("id") id: Long, @Body slot: SlotBody): Rescheduled

    @GET("api/v1/admin/incidents/notifications")
    suspend fun notifications(@Query("unread") unread: Boolean, @Query("page") page: Int, @Query("size") size: Int): NotificationPage

    @GET("api/v1/admin/incidents/notifications/unread-count")
    suspend fun unreadCount(): UnreadCount

    @PATCH("api/v1/admin/incidents/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: Long)

    // ---- Asignaciones: recojo por grupo ----
    @GET("api/v1/admin/fulfillment/pickup-groups")
    suspend fun pickupGroups(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("assigned") assigned: Boolean? = null,
    ): PickupGroupPage

    @GET("api/v1/admin/fulfillment/pickup-groups/{key}")
    suspend fun pickupGroup(@Path("key") key: String): PickupGroupDetail

    @GET("api/v1/admin/fulfillment/pickup-groups/{key}/handler-candidates")
    suspend fun groupCandidates(@Path("key") key: String, @Query("page") page: Int): CandidatePage

    @PUT("api/v1/admin/fulfillment/pickup-groups/{key}/handler-assignment")
    suspend fun assignGroup(@Path("key") key: String, @Body body: HandlerBody)

    @DELETE("api/v1/admin/fulfillment/pickup-groups/{key}/handler-assignment")
    suspend fun unassignGroup(@Path("key") key: String)

    @POST("api/v1/admin/fulfillment/pickup-groups/{key}/advance")
    suspend fun advanceGroup(@Path("key") key: String, @Body body: AdvanceBody): GroupAdvanceResult

    // ---- Asignaciones: entrega en persona y Delivery, por pedido ----
    @GET("api/v1/admin/orders/{id}/handler-candidates")
    suspend fun orderCandidates(@Path("id") id: Long, @Query("page") page: Int): CandidatePage

    @GET("api/v1/admin/orders/{id}/handler-assignment")
    suspend fun orderAssignment(@Path("id") id: Long): OrderHandlerAssignment

    @PUT("api/v1/admin/orders/{id}/handler-assignment")
    suspend fun assignOrder(@Path("id") id: Long, @Body body: HandlerBody)

    @GET("api/v1/admin/orders/{id}/delivery-destination")
    suspend fun deliveryDestination(@Path("id") id: Long): DeliveryDestination

    @PUT("api/v1/admin/orders/{id}/delivery-destination")
    suspend fun setDeliveryDestination(@Path("id") id: Long, @Body body: DestinationBody)

    @DELETE("api/v1/admin/orders/{id}/delivery-destination")
    suspend fun clearDeliveryDestination(@Path("id") id: Long)

    /** The URL is a credential: never log or cache it. */
    @GET("api/v1/fulfillment/orders/{id}/payment-link")
    suspend fun paymentLink(@Path("id") id: Long): PaymentLink

    @GET("api/v1/admin/users/operational-roles")
    suspend fun staff(@Query("page") page: Int, @Query("size") size: Int): StaffPage
}
