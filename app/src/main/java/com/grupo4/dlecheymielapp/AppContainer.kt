package com.grupo4.dlecheymielapp

import android.content.Context
import com.grupo4.dlecheymielapp.core.network.NetworkModule
import com.grupo4.dlecheymielapp.core.network.SharedPreferencesCookieStorage
import com.grupo4.dlecheymielapp.data.admin.AdminApi
import com.grupo4.dlecheymielapp.data.admin.AdminRepository
import com.grupo4.dlecheymielapp.data.auth.AuthApi
import com.grupo4.dlecheymielapp.data.auth.AuthRepository
import com.grupo4.dlecheymielapp.data.fulfillment.DeliveryApi
import com.grupo4.dlecheymielapp.data.fulfillment.DeliveryRepository
import com.grupo4.dlecheymielapp.data.fulfillment.DeliveryScope
import com.grupo4.dlecheymielapp.data.reminders.RemindersApi
import com.grupo4.dlecheymielapp.data.reminders.RemindersRepository
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerApi
import com.grupo4.dlecheymielapp.data.fulfillment.HandlerRepository

/** Hand-wired dependencies; one instance lives in [DLecheApp]. The API address comes from the build variant. */
class AppContainer(context: Context) {
    val network = NetworkModule(
        baseUrl = BuildConfig.API_BASE_URL,
        storage = SharedPreferencesCookieStorage(context),
        debugLogging = BuildConfig.DEBUG,
    )

    val authRepository = AuthRepository(network, network.retrofit.create(AuthApi::class.java))

    val handlerRepository = HandlerRepository(network.retrofit.create(HandlerApi::class.java), network.json)

    val adminRepository = AdminRepository(network.retrofit.create(AdminApi::class.java), network.json)

    private val deliveryApi = network.retrofit.create(DeliveryApi::class.java)

    val deliveryRepository = DeliveryRepository(deliveryApi, network.json, DeliveryScope.DELIVERY)

    val operatorRepository = DeliveryRepository(deliveryApi, network.json, DeliveryScope.OPERATOR)

    val remindersRepository = RemindersRepository(network.retrofit.create(RemindersApi::class.java), network.json)
}
