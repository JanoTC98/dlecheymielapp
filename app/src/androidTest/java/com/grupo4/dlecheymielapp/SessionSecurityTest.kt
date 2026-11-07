package com.grupo4.dlecheymielapp

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grupo4.dlecheymielapp.core.network.SharedPreferencesCookieStorage
import okhttp3.Cookie
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionSecurityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test_session_security"
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun storage() = SharedPreferencesCookieStorage(context, name)
    private val cookie get() = Cookie.Builder().name("DLECHE_SESSION").value("secret-session-value")
        .hostOnlyDomain("api.example.com").path("/").secure().httpOnly().expiresAt(System.currentTimeMillis() + 3600000).build()

    @After fun clear() { prefs.edit().clear().commit() }

    @Test fun sessionSurvivesNewStorageInstanceWithoutPlaintextOnDisk() {
        storage().save(listOf(cookie))
        assertFalse(prefs.all.toString().contains("secret-session-value"))
        assertFalse(prefs.contains("cookies"))
        val loaded = storage().load().single()
        assertEquals(cookie.value, loaded.value)
        assertTrue(loaded.secure && loaded.httpOnly && loaded.hostOnly)
    }

    @Test fun legacySessionMigratesToCiphertextAndRemovesPlaintextAtomically() {
        val legacy = """[{"name":"DLECHE_SESSION","value":"legacy-secret","domain":"api.example.com","path":"/","expiresAt":9999999999999,"secure":true,"httpOnly":true,"hostOnly":true}]"""
        prefs.edit().putString("cookies", legacy).commit()
        assertEquals("legacy-secret", storage().load().single().value)
        assertFalse(prefs.contains("cookies"))
        assertFalse(prefs.all.toString().contains("legacy-secret"))
        assertEquals("legacy-secret", storage().load().single().value)
    }

    @Test fun tamperedCiphertextSignsOutWithoutUsingLegacyFallback() {
        storage().save(listOf(cookie))
        prefs.edit().putString("cookies_aes_gcm_v1", "corrupted")
            .putString("cookies", "plaintext-must-not-be-used").commit()
        assertTrue(storage().load().isEmpty())
        assertTrue(prefs.all.isEmpty())
    }

    @Test fun everyWriteUsesFreshCiphertextAndLogoutClearsIt() {
        storage().save(listOf(cookie))
        val first = prefs.getString("cookies_aes_gcm_v1", null)
        storage().save(listOf(cookie))
        assertNotEquals(first, prefs.getString("cookies_aes_gcm_v1", null))
        storage().save(emptyList())
        assertTrue(storage().load().isEmpty())
        assertTrue(prefs.all.isEmpty())
    }
}
