package com.grupo4.dlecheymielapp.data.auth

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi {
    /** `captchaToken` is the Cloudflare Turnstile token; the header is omitted when null (captcha disabled). */
    @POST("api/v1/auth/login/local")
    suspend fun login(
        @Body request: LoginRequest,
        @Header("X-Captcha-Token") captchaToken: String? = null,
    ): AuthSessionResponse

    @GET("api/v1/auth/me")
    suspend fun me(): AuthSessionResponse

    @POST("api/v1/auth/logout")
    suspend fun logout()
}
