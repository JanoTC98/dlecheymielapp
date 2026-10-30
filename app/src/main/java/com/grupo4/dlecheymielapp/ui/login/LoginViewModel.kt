package com.grupo4.dlecheymielapp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.core.network.NetworkUnavailableException
import com.grupo4.dlecheymielapp.data.auth.AuthRepository
import com.grupo4.dlecheymielapp.data.auth.NotStaffAccountException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** Neutral information, e.g. the previous session expired. */
    val notice: String? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotEmpty() && !loading
}

const val SESSION_EXPIRED_NOTICE = "Su sesión venció. Inicie sesión de nuevo para continuar."

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        // The login can outlive several sessions: say why the user is back here whenever the server ended one.
        viewModelScope.launch {
            auth.sessionExpired.collect { expired -> _state.update { it.copy(notice = if (expired) SESSION_EXPIRED_NOTICE else null) } }
        }
    }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(loading = true, error = null, notice = null) }
        auth.clearExpiredNotice()
        viewModelScope.launch {
            try {
                // Success flips AuthRepository.session to SignedIn and the root composable swaps screens.
                auth.login(current.email, current.password)
                // Nothing of this session may remain on the form for the next person using the phone.
                _state.value = LoginUiState()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.toMessage()) }
            }
        }
    }
}

/** Spanish message for a failed login. Codes are the backend's `AUTH_*` / `RATE_LIMITED` / `CAPTCHA_*`. */
internal fun Throwable.toMessage(): String = when (this) {
    is NotStaffAccountException -> "Esta app es solo para el personal de D'Leche y Miel."
    is NetworkUnavailableException -> "No hay conexión con el servidor. Revisa tu red e inténtalo otra vez."
    is ApiException -> when {
        code == "AUTH_INVALID_CREDENTIALS" -> "Correo o contraseña incorrectos."
        code == "AUTH_TOO_MANY_ATTEMPTS" || code == "RATE_LIMITED" || status == 429 ->
            "Demasiados intentos. Espera ${retryAfterSeconds?.let { "${(it + 59) / 60} min" } ?: "unos minutos"} e inténtalo de nuevo."
        code == "CAPTCHA_FAILED" -> "No se pudo verificar que eres una persona. Inténtalo otra vez."
        code == "CAPTCHA_UNAVAILABLE" -> "La verificación no está disponible ahora. Inténtalo en unos minutos."
        code == "AUTH_VALIDATION_ERROR" -> "Revisa el correo y la contraseña."
        status in 500..599 -> "El servidor tuvo un problema. Inténtalo de nuevo en un momento."
        else -> message ?: "No se pudo iniciar sesión."
    }
    else -> "No se pudo iniciar sesión."
}
