package com.grupo4.dlecheymielapp.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Builds the HTTP stack for one backend. [baseUrl] must end with `/`; endpoint paths are `api/v1/...`. */
class NetworkModule(
    val baseUrl: String,
    storage: CookieStorage,
    debugLogging: Boolean,
) {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    val cookieJar = PersistentCookieJar(storage)

    private val seedClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    val okHttp: OkHttpClient = seedClient.newBuilder()
        .addInterceptor(CsrfInterceptor(cookieJar, seedClient))
        .apply {
            if (debugLogging) {
                // BASIC: method, URL and status only. Bodies would put passwords in logcat.
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private companion object { const val TIMEOUT_SECONDS = 20L }
}
