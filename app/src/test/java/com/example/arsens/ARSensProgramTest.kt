package com.example.arsens

import com.example.arsens.data.*
import org.junit.Assert.*
import org.junit.Test

class ARSensProgramTest {
    private fun json(sensors: String = """{"id":"7","name":"Tank temp","sensorTagId":206,"target":{"x":100,"y":0,"z":200}}""") =
        """{"schemaVersion":1,"units":"mm","coordinateFrame":"ARSENS_BOX_XYZ","sensors":[$sensors]}"""
    @Test fun validatesUnitsFrameDuplicatesAndVersion() {
        val program = ARSensProgramImport.parse(json())
        assertEquals(25, program.sensors.single().toleranceMm)
        assertEquals(206, program.sensors.single().sensorTagId)
        for (invalid in listOf(json().replace("\"mm\"", "\"m\""), json().replace("ARSENS_BOX_XYZ", "unknown"),
            json().replace("schemaVersion\":1", "schemaVersion\":2"), json("""{"id":"1","target":{"x":1,"y":2,"z":3}},{"id":"1","target":{"x":4,"y":5,"z":6}}"""))) {
            assertTrue(runCatching { ARSensProgramImport.parse(invalid) }.isFailure)
        }
    }
    @Test fun conflictsRequirePolicyAndRenameDoesNotDuplicateTag() {
        val program = ARSensProgramImport.parse(json())
        val existing = program.sensors.map { it.copy(name = "Original") }
        assertEquals(existing, ARSensProgramImport.merge(existing, program, ProgramConflictPolicy.SKIP))
        val renamed = ARSensProgramImport.merge(existing, program, ProgramConflictPolicy.RENAME)
        assertEquals("7-2", renamed.last().id)
        assertNull(renamed.last().sensorTagId)
        assertEquals("Original", renamed.first().name)
    }
}
