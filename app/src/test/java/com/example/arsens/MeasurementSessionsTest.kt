package com.example.arsens

import com.example.arsens.data.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class MeasurementSessionsTest {
    private val sensor = Sensor(1, "1", "Temperature", "Front", MmPosition(100, 0, 200), toleranceMm = 25, instruction = "", sensorTagId = 200)
    private val project = Project("Tank", "", listOf(sensor), listOf(Marker(1, "apriltag", 100, MmPosition(0, 0, 0), FloatVector(0f, 0f, 0f))))
    private fun Project.capture(x: Int, action: MeasurementAction = MeasurementAction.PLACE): Project {
        val position = MmPosition(x, 0, 200)
        val offset = position - sensor.positionMm
        return copy(measurementDraft = MeasurementDraft(activeSession!!.id, sensor.id, action,
            InstallationResult(sensor.id, sensor.positionMm, position, offset, distanceMm(offset), SensorStatus.Ok, null, "2026-09-13T12:01:00"),
            MeasurementMethod.DIRECT_SINGLE_REF, 200, null, "2026-09-13T12:01:00"))
    }
    @Test fun secondSessionAndUndoPreserveFirstSessionAndPreparedTarget() {
        val first = project.startMeasurementSession("1", "A", "", "2026-09-13T12:00:00").capture(110).acceptMeasurementDraft("t1").closeMeasurementSession("t2")
        val second = first.startMeasurementSession("2", "B", "", "t3").capture(300, MeasurementAction.MOVE).acceptMeasurementDraft("t4")
        assertEquals(first.sessions.single(), second.sessions.first())
        assertEquals(300, second.activeSession!!.currentResults().single().measuredPositionMm!!.x)
        assertEquals(sensor.positionMm, second.sensors.single().positionMm)
        val undone = second.undoSessionMeasurement("t5")
        assertEquals(110, undone.activeSession!!.currentResults().single().measuredPositionMm!!.x)
        assertEquals(1, undone.activeSession!!.measurements.size)
        assertEquals("ACTION_REVERTED", undone.activeSession!!.actions.last().type)
        assertEquals(first.sessions.single(), undone.sessions.first())
    }
    @Test fun verifyRecordsEvidenceWithoutMovingCurrentPosition() {
        val started = project.startMeasurementSession("1", "A", "", "t").capture(110).acceptMeasurementDraft("t1")
        val checked = started.capture(160, MeasurementAction.VERIFY).acceptMeasurementDraft("t2")
        assertEquals(110, checked.activeSession!!.currentResults().single().measuredPositionMm!!.x)
        assertEquals(2, checked.activeSession!!.measurements.size)
    }
    @Test fun draftRoundTripDoesNotCreateAnAcceptedMeasurement() {
        val draft = project.startMeasurementSession("1", "A", "", "t").capture(130)
        val restored = JsonProjectStore.projectFromJson(JsonProjectStore.projectToJson(draft))
        assertEquals(draft, restored)
        assertTrue(restored.activeSession!!.measurements.isEmpty())
        assertNotNull(restored.measurementDraft)
        assertNull(restored.undoSessionMeasurement("t").measurementDraft)
    }
    @Test fun closedSessionsRejectAcceptAndUndo() {
        val closed = project.startMeasurementSession("1", "A", "", "t").capture(110).acceptMeasurementDraft("t1").closeMeasurementSession("t2")
        val selected = closed.copy(activeSessionId = closed.sessions.single().id)
        assertTrue(runCatching { selected.undoSessionMeasurement("t3") }.isFailure)
        assertTrue(runCatching { selected.capture(300).acceptMeasurementDraft("t3") }.isFailure)
    }
    @Test fun startingDoesNotCopyMeasurementsOrMutateOldGeometry() {
        val first = project.startMeasurementSession("1", "A", "", "t").closeMeasurementSession("t2")
        val changed = first.copy(dimensionsMm = MmPosition(8000, 4000, 2500)).startMeasurementSession("2", "A", "", "t3")
        assertEquals(project.dimensionsMm, changed.geometryRevisions.first().dimensionsMm)
        assertEquals(8000, changed.geometryRevisions.last().dimensionsMm.x)
        assertTrue(changed.activeSession!!.measurements.isEmpty())
        assertNull(changed.measurementDraft)
    }
    @Test fun migrationIsIdempotentAndKeepsActualPositionAndTimestamp() {
        val result = InstallationResult("1", sensor.positionMm, MmPosition(105, 0, 200), MmPosition(5, 0, 0), 5, SensorStatus.Ok, "photo.jpg", "old-time")
        val log = InstallationLog("Tank", "old-start", "A", listOf(result))
        val migrated = project.migrateLegacyMeasurements(log)
        assertEquals(migrated, migrated.migrateLegacyMeasurements(log))
        assertEquals(result, migrated.sessions.single().measurements.single().result)
        assertEquals(SessionStatus.CLOSED, migrated.sessions.single().status)
        assertEquals(migrated, JsonProjectStore.projectFromJson(JsonProjectStore.projectToJson(migrated)))
    }
    @Test fun atomicSaveReplacesCompleteProjectAndLeavesNoPendingFiles() {
        val dir = Files.createTempDirectory("arsens-project").toFile()
        try {
            JsonProjectStore.saveProjectToJson(dir, project)
            val updated = project.startMeasurementSession("1", "A", "", "t").capture(135)
            JsonProjectStore.saveProjectToJson(dir, updated)
            assertEquals(updated, JsonProjectStore.projectFromJson(dir.resolve("project.json").readText()))
            assertFalse(dir.listFiles()!!.any { it.extension == "pending" })
        } finally { dir.deleteRecursively() }
    }
    @Test fun unsupportedSchemaFailsWithoutDowngrading() {
        assertTrue(runCatching { JsonProjectStore.projectFromJson("""{"schema_version":99,"sensors":[],"markers":[]}""") }.isFailure)
    }
    @Test fun acceptedGeometryIsReusedAndDimensionComparisonSurvivesNewRevisionAndJson() {
        val comparison=DimensionComparison("x",10000,10014,"tank","t0")
        val accepted=project.copy(dimensionComparisons=listOf(comparison)).ensureGeometryRevision("t0")
        val session=accepted.startMeasurementSession("1","A","","t1")
        assertEquals(1,session.geometryRevisions.size)
        assertEquals(accepted.geometryRevisions.single().id,session.activeSession!!.geometryRevisionId)
        val revised=session.closeMeasurementSession("t2").copy(dimensionsMm=MmPosition(11000,5000,3200)).ensureGeometryRevision("t3")
        assertEquals(2,revised.geometryRevisions.size)
        assertEquals(listOf(comparison),revised.geometryRevisions.first().comparisons)
        assertEquals(revised,JsonProjectStore.projectFromJson(JsonProjectStore.projectToJson(revised)))
    }
    @Test fun sensorFrameEvidenceSurvivesDraftAcceptanceAndReload() {
        val draft=project.startMeasurementSession("1","A","","t0").capture(110)
        val evidence=SensorCaptureEvidence(12,1400,2.4,150)
        val accepted=draft.copy(measurementDraft=draft.measurementDraft!!.copy(captureEvidence=evidence)).acceptMeasurementDraft("t1")
        val restored=JsonProjectStore.projectFromJson(JsonProjectStore.projectToJson(accepted))
        assertEquals(evidence,restored.activeSession!!.measurements.single().captureEvidence)
    }
}
