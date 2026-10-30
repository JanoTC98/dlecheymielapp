package com.grupo4.dlecheymielapp.data.auth

import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.core.network.NetworkModule
import com.grupo4.dlecheymielapp.core.network.NetworkUnavailableException
import com.grupo4.dlecheymielapp.core.network.apiCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The signed-in customer account tried to use the staff app. */
class NotStaffAccountException : Exception("not a staff account")

class AuthRepository(private val network: NetworkModule, private val api: AuthApi) {
    private val _session = MutableStateFlow<SessionState>(SessionState.Restoring)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    /** True when the last sign-out was not the user's choice (the server ended the session), so the login can say why. */
    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    fun clearExpiredNotice() { _sessionExpired.value = false }

    /**
     * The staff user the server recognises right now, or null. Unlike [restore] it never changes [session] or the stored
     * cookie, so a background check that fails (offline, expired) cannot sign the user out behind the screens' back.
     */
    suspend fun currentStaffUser(): AuthUser? {
        (_session.value as? SessionState.SignedIn)?.let { return it.user }
        return try {
            apiCall(network.json) { api.me() }.user.takeIf { it.role.isStaff }
        } catch (e: ApiException) {
            null
        } catch (e: NetworkUnavailableException) {
            null
        }
    }

    /** Called once at launch: reuses the stored session cookie if the server still accepts it. */
    suspend fun restore() {
        _session.value = try {
            val user = apiCall(network.json) { api.me() }.user
            if (user.role.isStaff) SessionState.SignedIn(user) else SessionState.SignedOut
        } catch (e: ApiException) {
            // 401/403: the cookie expired (internal sessions last 8 h). Drop it so it cannot poison the next login.
            network.cookieJar.clear()
            SessionState.SignedOut
        } catch (e: NetworkUnavailableException) {
            // Keep the cookie: the session may still be valid once the connection is back.
            SessionState.SignedOut
        }
    }

    /** @throws ApiException wrong credentials, rate limit, captcha... @throws NotStaffAccountException customer account. */
    suspend fun login(email: String, password: String, captchaToken: String? = null) {
        val user = apiCall(network.json) { api.login(LoginRequest(email.trim(), password), captchaToken) }.user
        if (!user.role.isStaff) {
            // Do not leave a customer session open inside the staff app.
            runCatching { apiCall(network.json) { api.logout() } }
            network.cookieJar.clear()
            throw NotStaffAccountException()
        }
        _session.value = SessionState.SignedIn(user)
    }

    suspend fun logout() {
        _sessionExpired.value = false
        runCatching { apiCall(network.json) { api.logout() } }
        network.cookieJar.clear()
        _session.value = SessionState.SignedOut
    }

    /** Call when any request answers 401: the session is gone, send the user back to the login. */
    fun onSessionExpired() {
        // Several requests can fail together; only the first one ends the session and raises the notice.
        if (_session.value is SessionState.SignedIn) _sessionExpired.value = true
        network.cookieJar.clear()
        _session.value = SessionState.SignedOut
    }
}
