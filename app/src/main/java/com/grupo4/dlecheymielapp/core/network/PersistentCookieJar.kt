package com.grupo4.dlecheymielapp.core.network

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

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

/** Persists cookies in app-private SharedPreferences (not backed up, see `allowBackup=false`). */
class SharedPreferencesCookieStorage(context: Context) : CookieStorage {
    private val prefs = context.getSharedPreferences("dleche_session_cookies", Context.MODE_PRIVATE)
    private val json = Json

    @Serializable
    private class Stored(
        val name: String, val value: String, val domain: String, val path: String,
        val expiresAt: Long, val secure: Boolean, val httpOnly: Boolean, val hostOnly: Boolean,
    )

    override fun load(): List<Cookie> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Stored>>(raw) }.getOrDefault(emptyList()).map { s ->
            Cookie.Builder().name(s.name).value(s.value).path(s.path).expiresAt(s.expiresAt)
                .apply {
                    if (s.hostOnly) hostOnlyDomain(s.domain) else domain(s.domain)
                    if (s.secure) secure()
                    if (s.httpOnly) httpOnly()
                }.build()
        }
    }

    override fun save(cookies: List<Cookie>) {
        val stored = cookies.map {
            Stored(it.name, it.value, it.domain, it.path, it.expiresAt, it.secure, it.httpOnly, it.hostOnly)
        }
        prefs.edit().putString(KEY, json.encodeToString(stored)).apply()
    }

    private companion object { const val KEY = "cookies" }
}

/**
 * The backend authenticates with the HttpOnly `DLECHE_SESSION` cookie and protects state-changing calls with the
 * `XSRF-TOKEN` cookie (echoed in `X-XSRF-TOKEN`). A browser keeps both automatically; this jar does the same.
 */
class PersistentCookieJar(private val storage: CookieStorage) : CookieJar {
    private val lock = Any()
    private var cookies: MutableList<Cookie> = storage.load().toMutableList()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(lock) {
        for (incoming in cookies) {
            this.cookies.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
            // A cookie the server expires (Max-Age=0 on logout) is a deletion.
            if (incoming.expiresAt > System.currentTimeMillis()) this.cookies.add(incoming)
        }
        storage.save(this.cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        val expired = cookies.removeAll { it.expiresAt <= now }
        if (expired) storage.save(cookies)
        cookies.filter { it.matches(url) }
    }

    /** Current value of [name] for [url], if the jar holds one. */
    fun valueOf(url: HttpUrl, name: String): String? = loadForRequest(url).firstOrNull { it.name == name }?.value

    fun clear() = synchronized(lock) {
        cookies.clear()
        storage.save(cookies)
    }
}
