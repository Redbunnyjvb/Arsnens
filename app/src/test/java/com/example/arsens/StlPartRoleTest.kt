package com.example.arsens

import com.example.arsens.data.StlPartRole
import org.junit.Assert.assertEquals
import org.junit.Test

class StlPartRoleTest {
    /** De deksel-keywords moeten vóór "tank" getest worden, anders wordt een "Tank cover"/
     *  "Tankdeksel" verkeerd als Tank herkend (en kan een deksel de tank-referentie kapen). */
    @Test
    fun coverIsDetectedBeforeTank() {
        assertEquals(StlPartRole.Cover, StlPartRole.detectFromName("Tank cover.stl"))
        assertEquals(StlPartRole.Cover, StlPartRole.detectFromName("Tankdeksel.stl"))
        assertEquals(StlPartRole.Cover, StlPartRole.detectFromName("Deksel.stl"))
        assertEquals(StlPartRole.Cover, StlPartRole.detectFromName("Lid.stl"))
    }

    @Test
    fun plainTankAndOthersStillDetected() {
        assertEquals(StlPartRole.Tank, StlPartRole.detectFromName("Tank.stl"))
        assertEquals(StlPartRole.ActivePart, StlPartRole.detectFromName("Active Part.stl"))
        assertEquals(StlPartRole.Core, StlPartRole.detectFromName("Core.stl"))
        assertEquals(StlPartRole.Other, StlPartRole.detectFromName("randompart.stl"))
    }
}
