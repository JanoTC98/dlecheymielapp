package com.grupo4.dlecheymielapp.data.reminders

import com.grupo4.dlecheymielapp.core.network.apiCall
import com.grupo4.dlecheymielapp.data.fulfillment.FulfillmentMode
import com.grupo4.dlecheymielapp.data.fulfillment.OrderStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** An order whose slot starts within the next 12 hours. [reachedOffsetsMinutes] are the thresholds (720, 180, 60, 30) already passed. */
@Serializable
data class UpcomingReminder(
    val orderId: Long,
    val publicCode: String? = null,
    val mode: FulfillmentMode = FulfillmentMode.UNKNOWN,
    val status: OrderStatus = OrderStatus.UNKNOWN,
    val slotStart: String,
    val timezone: String? = null,
    val reachedOffsetsMinutes: List<Int> = emptyList(),
)

@Serializable
data class ReminderPage(val items: List<UpcomingReminder> = emptyList(), val page: Int = 0, val size: Int = 3, val totalElements: Long = 0)

/** Which role's reminders to read; each role sees only its own work. */
enum class ReminderScope(val path: String) { ADMIN("admin"), HANDLER("handler"), DELIVERY("delivery"), OPERATOR("operator") }

interface RemindersApi {
    @GET("api/v1/{scope}/upcoming-reminders")
    suspend fun upcoming(@Path("scope") scope: String, @Query("page") page: Int = 0, @Query("size") size: Int = 3): ReminderPage
}

class RemindersRepository(private val api: RemindersApi, private val json: Json) {
    suspend fun upcoming(scope: ReminderScope): ReminderPage = apiCall(json) { api.upcoming(scope.path) }
}
