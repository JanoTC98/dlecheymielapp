package com.grupo4.dlecheymielapp.core.network

import kotlinx.serialization.Serializable
import java.io.IOException

/** Error envelope the backend returns on every failure: `{code, message, fieldErrors?, correlationId}`. */
@Serializable
data class ApiErrorBody(
    val code: String? = null,
    val message: String? = null,
    val fieldErrors: List<FieldError> = emptyList(),
    val correlationId: String? = null,
)

@Serializable
data class FieldError(val field: String, val code: String, val message: String)

/** The server answered with an error status. */
class ApiException(
    val status: Int,
    val code: String?,
    message: String,
    val fieldErrors: List<FieldError> = emptyList(),
    val correlationId: String? = null,
    /** Seconds from the `Retry-After` header, present on 429. */
    val retryAfterSeconds: Long? = null,
) : Exception(message)

/** The request never got an answer (no network, wrong IP, server down, timeout). */
class NetworkUnavailableException(cause: IOException) : Exception(cause.message, cause)
