package com.grupo4.dlecheymielapp.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Roles the backend knows. Only the staff roles may use this app; `CLIENT` is refused at login. */
enum class AuthRole(val label: String) {
    ADMIN("Administrador"),
    DELIVERY_HANDLER("Encargado"),
    DELIVERY_COORDINATOR("Operador"),
    DELIVERY("Delivery interno"),
    CLIENT("Cliente"),
    ;

    val isStaff: Boolean get() = this != CLIENT
}

@Serializable
data class AuthUser(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val phone: String? = null,
    val email: String,
    val role: AuthRole,
    val authProvider: String? = null,
) {
    val fullName: String get() = "$firstName $lastName".trim()
}

@Serializable
data class AuthSessionResponse(
    val user: AuthUser,
    val expiresAt: String,
)

@Serializable
data class LoginRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
)

/** Session state the UI reacts to. */
sealed interface SessionState {
    /** Checking whether a stored session cookie is still valid. */
    data object Restoring : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: AuthUser) : SessionState
}
