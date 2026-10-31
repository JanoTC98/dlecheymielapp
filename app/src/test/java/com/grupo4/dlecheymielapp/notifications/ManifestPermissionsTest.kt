package com.grupo4.dlecheymielapp.notifications

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The periodic alert job has a connectivity constraint; without this permission the app crashed right after login. */
class ManifestPermissionsTest {
    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val alertSource = File("src/main/java/com/grupo4/dlecheymielapp/notifications/OrderAlertService.kt").readText()

    @Test
    fun declaresNetworkStateOnceWhileTheAlertJobRequiresTheNetwork() {
        assertTrue(alertSource.contains("setRequiredNetworkType"))
        assertEquals(1, Regex("android.permission.ACCESS_NETWORK_STATE").findAll(manifest).count())
    }
}
