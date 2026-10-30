package com.grupo4.dlecheymielapp.core.network

import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** Runs a Retrofit call and turns failures into [ApiException] / [NetworkUnavailableException]. */
suspend fun <T> apiCall(json: Json, block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    throw e.toApiException(json)
} catch (e: IOException) {
    throw NetworkUnavailableException(e)
}

private fun HttpException.toApiException(json: Json): ApiException {
    val body = runCatching {
        response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }?.let { json.decodeFromString<ApiErrorBody>(it) }
    }.getOrNull()
    val retryAfter = response()?.headers()?.get("Retry-After")?.toLongOrNull()
    return ApiException(
        status = code(),
        code = body?.code,
        message = body?.message ?: message(),
        fieldErrors = body?.fieldErrors.orEmpty(),
        correlationId = body?.correlationId,
        retryAfterSeconds = retryAfter,
    )
}
