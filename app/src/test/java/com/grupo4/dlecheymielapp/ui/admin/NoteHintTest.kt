package com.grupo4.dlecheymielapp.ui.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Guardar button stays disabled for an empty note; the dialog must say why. */
class NoteHintTest {
    @Test
    fun anEmptyNoteExplainsWhyItCannotBeSaved() {
        assertEquals("Escriba la nota para poder guardarla.", noteHint(""))
    }

    @Test
    fun aNoteOfOnlySpacesIsTreatedAsEmpty() {
        assertEquals("Escriba la nota para poder guardarla.", noteHint("   \n  "))
    }

    @Test
    fun aNoteWithTextNeedsNoHint() {
        assertNull(noteHint("Cliente pidió llamar antes"))
    }
}
