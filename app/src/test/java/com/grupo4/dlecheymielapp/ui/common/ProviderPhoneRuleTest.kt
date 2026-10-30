package com.grupo4.dlecheymielapp.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderPhoneRuleTest {
    @Test
    fun rejectsNumbersThatAreNotE164() {
        assertFalse(isValidProviderPhone("abc"))
        assertFalse(isValidProviderPhone("999000001"))
        assertFalse(isValidProviderPhone("+0123"))
        assertFalse(isValidProviderPhone("+1234567"))
        assertFalse(isValidProviderPhone(""))
    }

    @Test
    fun acceptsAnE164NumberAndIgnoresSurroundingSpaces() {
        assertTrue(isValidProviderPhone("+51987654321"))
        assertTrue(isValidProviderPhone("  +51987654321  "))
    }
}
