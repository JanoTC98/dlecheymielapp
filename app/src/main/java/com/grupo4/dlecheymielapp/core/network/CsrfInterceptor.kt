package com.grupo4.dlecheymielapp.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.URLDecoder

/**
 * Adds `X-XSRF-TOKEN` to every state-changing request, like the web client does. If the XSRF cookie is missing
 * it is seeded first with `GET /api/v1/auth/csrf`. The backend also demands it on login whenever a stale session
 * cookie is present, so login is covered too.
 */
class CsrfInterceptor(
    private val cookieJar: PersistentCookieJar,
    /** Plain client (same cookie jar, no interceptors) used only to seed the cookie. */
    private val seedClient: OkHttpClient,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method !in MUTATING) return chain.proceed(request)

        val token = cookieJar.valueOf(request.url, COOKIE) ?: seed(request)
        return chain.proceed(request.newBuilder().header(HEADER, decode(token)).build())
    }

    private fun seed(request: Request): String {
        val seedUrl = request.url.newBuilder().encodedPath(CSRF_PATH).query(null).build()
        seedClient.newCall(Request.Builder().url(seedUrl).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("No se pudo iniciar la protección CSRF (HTTP ${response.code}).")
        }
        return cookieJar.valueOf(request.url, COOKIE) ?: throw IOException("El servidor no entregó el token CSRF.")
    }

    // The cookie value is percent-encoded; '+' is a literal there, not a space.
    private fun decode(value: String) = URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")

    companion object {
        const val COOKIE = "XSRF-TOKEN"
        const val HEADER = "X-XSRF-TOKEN"
        const val CSRF_PATH = "/api/v1/auth/csrf"
        private val MUTATING = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}
