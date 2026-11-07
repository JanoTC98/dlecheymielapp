package com.grupo4.dlecheymielapp.core.network

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.IOException

/** Where cookies survive between app launches. */
interface CookieStorage {
    fun load(): List<Cookie>
    fun save(cookies: List<Cookie>)
}

/** Keeps the session in memory only; used by unit tests. */
class InMemoryCookieStorage : CookieStorage {
    private var stored: List<Cookie> = emptyList()
    override fun load() = stored
    override fun save(cookies: List<Cookie>) { stored = cookies }
}

/** AES-GCM ciphertext only; the non-exportable key lives in Android Keystore. */
class SharedPreferencesCookieStorage(context: Context, preferencesName: String = "dleche_session_cookies") : CookieStorage {
    private val prefs = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val json = Json
    private val cipher = KeystoreCookieCipher()

    @Serializable
    private class Stored(
        val name: String, val value: String, val domain: String, val path: String,
        val expiresAt: Long, val secure: Boolean, val httpOnly: Boolean, val hostOnly: Boolean,
    )

    @Synchronized
    override fun load(): List<Cookie> {
        val encrypted = prefs.getString(ENCRYPTED_KEY, null)
        val legacy = prefs.getString(KEY, null)
        if (encrypted == null && legacy == null) return emptyList()
        return try {
            val cookies = decode(encrypted?.let(cipher::decrypt) ?: requireNotNull(legacy))
            // Commit ciphertext and remove plaintext in the same preferences transaction.
            if (legacy != null) save(cookies)
            cookies
        } catch (_: java.security.GeneralSecurityException) {
            discardUnreadableSession()
        } catch (_: IllegalArgumentException) {
            discardUnreadableSession()
        }
    }

    private fun discardUnreadableSession(): List<Cookie> {
        check(prefs.edit().remove(KEY).remove(ENCRYPTED_KEY).commit()) { "Cannot clear unreadable session" }
        return emptyList()
    }

    private fun decode(raw: String): List<Cookie> = json.decodeFromString<List<Stored>>(raw).map { s ->
        Cookie.Builder().name(s.name).value(s.value).path(s.path).expiresAt(s.expiresAt)
            .apply {
                if (s.hostOnly) hostOnlyDomain(s.domain) else domain(s.domain)
                if (s.secure) secure()
                if (s.httpOnly) httpOnly()
            }.build()
    }

    @Synchronized
    override fun save(cookies: List<Cookie>) {
        val stored = cookies.map {
            Stored(it.name, it.value, it.domain, it.path, it.expiresAt, it.secure, it.httpOnly, it.hostOnly)
        }
        val editor = prefs.edit().remove(KEY)
        if (stored.isEmpty()) editor.remove(ENCRYPTED_KEY)
        else editor.putString(ENCRYPTED_KEY, cipher.encrypt(json.encodeToString(stored)))
        if (!editor.commit()) throw IOException("Cannot persist encrypted session")
    }

    private companion object {
        const val KEY = "cookies"
        const val ENCRYPTED_KEY = "cookies_aes_gcm_v1"
    }
}

/**
 * The backend authenticates with the HttpOnly `DLECHE_SESSION` cookie and protects state-changing calls with the
 * `XSRF-TOKEN` cookie (echoed in `X-XSRF-TOKEN`). A browser keeps both automatically; this jar does the same.
 */
class PersistentCookieJar(private val storage: CookieStorage, private val apiOrigin: HttpUrl? = null) : CookieJar {
    private val lock = Any()
    private var cookies: MutableList<Cookie> = storage.load().toMutableList()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(lock) {
        if (!allows(url)) return@synchronized
        for (incoming in cookies) {
            val validDomain = incoming.domain == url.host || (!incoming.hostOnly && url.host.endsWith(".${incoming.domain}"))
            if (!validDomain || (incoming.secure && !url.isHttps)) continue
            this.cookies.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
            // A cookie the server expires (Max-Age=0 on logout) is a deletion.
            if (incoming.expiresAt > System.currentTimeMillis()) this.cookies.add(incoming)
        }
        storage.save(this.cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        if (!allows(url)) return@synchronized emptyList()
        val now = System.currentTimeMillis()
        val expired = cookies.removeAll { it.expiresAt <= now }
        if (expired) storage.save(cookies)
        cookies.filter { it.matches(url) }
    }

    // SameSite is a browser navigation policy, unavailable to OkHttp's native CookieJar.
    // Instead, session and CSRF cookies are confined to the configured API origin, including redirects.
    private fun allows(url: HttpUrl): Boolean = apiOrigin?.let {
        it.scheme == url.scheme && it.host == url.host && it.port == url.port
    } ?: true

    /** Current value of [name] for [url], if the jar holds one. */
    fun valueOf(url: HttpUrl, name: String): String? = loadForRequest(url).firstOrNull { it.name == name }?.value

    fun clear() = synchronized(lock) {
        cookies.clear()
        storage.save(cookies)
    }
}
