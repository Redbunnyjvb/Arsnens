package com.example.arsens

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arsens.ar.*
import com.example.arsens.data.*
import com.example.arsens.ui.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real workflow + JSON store in an isolated cache directory, with controlled AR packets.
 * Does not open a camera, use a user's project, or establish real-world AR accuracy. */
class SensorWorkflowStateTest {
    private lateinit var context: Context
    private lateinit var folder: File
    private lateinit var state: WorkflowAppState
    private val preferenceNames = mutableSetOf<String>()
    @Before fun setup() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "workflow-test-${UUID.randomUUID()}"
        folder = File(base.cacheDir, prefix).also { it.mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = folder
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val testName = "$prefix-$name"
                preferenceNames += testName
                return base.getSharedPreferences(testName, mode)
            }
        }
        state = WorkflowAppState(context)
        state.project = Project("Workflow test", "", dimensionsMm = MmPosition(1000, 1000, 1000), markers = emptyList(), sensors = emptyList())
        state.log = InstallationLog("Workflow test", "t", "test", emptyList())
        state.activeMapView = TransformerMapView.Front
        state.sensorId = "TEMP-A"
        state.sensorName = "Temperatuur"
    }
    @After fun cleanup() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        check(folder.canonicalFile.parentFile == base.cacheDir.canonicalFile)
        folder.deleteRecursively()
        preferenceNames.forEach { base.deleteSharedPreferences(it) }
    }
    private fun readyCursor(position: MmPosition) {
        state.updateAprilTagResult(AprilTagFrameResult(
            transformerPose = TransformerPose(floatArrayOf(0f, 0f, 1500f), floatArrayOf(0f, 0f, 0f), 1f),
            arTracking = true, anchorSettled = true, trackingStatus = ArTrackingStatus.TagCalibration,
            trackingQualityPercent = 95, detectionAgeMillis = 0,
            calibrationRevision = state.arCalibrationRevision,
            displayProjection = ArDisplayProjection(100, 100, doubleArrayOf(
                1.0,0.0,0.0,0.0, 0.0,1.0,0.0,0.0, 0.0,0.0,1.0,0.0, 0.0,0.0,0.0,1.0))))
        state.arCursorPosition = position
        state.arCursorSource = "surface"
        state.arCursorInsideTransformer = true
        assertTrue(state.sensorPlacementBlockReason, state.sensorPlacementReady)
    }
    private fun startSession() {
        state.project = state.project.copy(markers = listOf(Marker(0, "apriltag", 100, MmPosition(100, 0, 500), FloatVector(90f, 0f, 0f))))
        state.sessionOperator = "test"
        state.startSession()
        assertNotNull(state.message, state.project.activeSession)
    }
    private fun capture(position: MmPosition, action: MeasurementAction = MeasurementAction.PLACE) {
        state.armMeasurement(state.sensorId, action)
        readyCursor(position)
        state.saveSensorAtCursor()
        assertNotNull(state.message, state.project.measurementDraft)
        state.acceptDraft()
        assertNull(state.message, state.project.measurementDraft)
    }
    @Test fun planIn2DPlaceElsewhereThenUndoAndReloadPreservesHistory() {
        val planned = MmPosition(300, 0, 400)
        val measured = MmPosition(600, 0, 400)
        state.saveSensorAtBoxPosition(planned)
        val sensor = state.project.sensors.single()
        assertEquals(SensorStatus.Pending, sensor.status)
        assertEquals(PlacementOrigin.Prepared, sensor.origin)
        assertNull(sensor.placement)
        startSession()
        state.selectCameraSensor(sensor)
        state.armMeasurement(sensor.id, MeasurementAction.PLACE)
        readyCursor(measured)
        state.saveSensorAtCursor()
        assertTrue(state.log.results.isEmpty())
        assertNotNull(state.project.measurementDraft)
        state.acceptDraft()
        val installed = state.project.sensors.single()
        assertEquals("TEMP-A", installed.id)
        assertEquals("Temperatuur", installed.name)
        assertEquals(planned, installed.positionMm)
        assertEquals(SensorStatus.Fail, installed.status)
        assertNotNull(installed.placement)
        assertEquals(measured, state.log.results.single().measuredPositionMm)
        assertEquals("TEMP-A", state.sensorId)
        state.undoLastPlacedSensor()
        val reloaded = WorkflowAppState(context)
        assertEquals(sensor, reloaded.project.sensors.single())
        assertTrue(reloaded.log.results.isEmpty())
        assertEquals(1, reloaded.project.activeSession!!.measurements.size)
        assertEquals("ACTION_REVERTED", reloaded.project.activeSession!!.actions.last().type)
    }
    @Test fun cameraPlanIsPendingWithRadiusAndWithoutPhysicalAudit() {
        state.planSensorAtCursor = true
        readyCursor(MmPosition(300, 0, 400))
        state.saveSensorAtCursor()
        val sensor = state.project.sensors.single()
        assertEquals(PlacementOrigin.Prepared, sensor.origin)
        assertEquals(SensorStatus.Pending, sensor.status)
        assertEquals(50, sensor.toleranceMm)
        assertNull(sensor.placement)
        assertTrue(state.log.results.isEmpty())
    }
    @Test fun freeCameraPlacementCreatesPhysicalMeasurement() {
        startSession()
        capture(MmPosition(300, 0, 400))
        val sensor = state.project.sensors.single()
        assertEquals(PlacementOrigin.OnTheFly, sensor.origin)
        assertEquals(SensorStatus.Ok, sensor.status)
        assertNotNull(sensor.placement)
        assertEquals(sensor.positionMm, state.log.results.single().measuredPositionMm)
        assertNull(sensor.plannedTarget)
        assertEquals(sensor.id, state.sensorId)
    }

    @Test fun selectionDoesNotMeasureAndMoveKeepsOtherSensorsAndEarlierMeasurement() {
        val target = MmPosition(300, 0, 400)
        val sensors = listOf("A-19", "B-2", "C-7").mapIndexed { index, id ->
            Sensor(index + 1, id, "Temperatuur $id", "Front", target, toleranceMm = 50, instruction = "")
        }
        state.project = state.project.copy(sensors = sensors)
        startSession()
        state.selectCameraSensor(sensors[1])
        capture(target)
        assertEquals("B-2", state.sensorId)
        state.stepCameraSensor(1)
        assertEquals("C-7", state.sensorId)
        capture(target)
        val before = state.project.sensors
        val logBefore = state.log
        state.selectCameraSensor(state.project.sensors.first { it.id == "B-2" })
        assertEquals("B-2", state.sensorId)
        assertEquals(before, state.project.sensors)
        assertEquals(logBefore, state.log)
        capture(MmPosition(600, 0, 400), MeasurementAction.MOVE)
        assertEquals("B-2", state.sensorId)
        assertEquals(3, state.project.sensors.size)
        assertEquals(target, state.project.sensors.first { it.id == "B-2" }.positionMm)
        assertEquals(SensorStatus.Fail, state.project.sensors.first { it.id == "B-2" }.status)
        assertEquals(logBefore.results.first { it.sensorId == "C-7" }, state.log.results.first { it.sensorId == "C-7" })
        assertEquals("2 / 3 geplaatst", state.project.placementCountLabel)
        assertEquals(3, state.project.activeSession!!.measurements.size)
    }

    @Test fun sensorNavigationHasBoundsAndNeverCarriesThePreviousTagOrPlanModeToANewSensor() {
        state.project = state.project.copy(sensors = listOf(
            Sensor(1, "TEMP-A", "Temperatuur", "Front", MmPosition(300, 0, 400), toleranceMm = 50, instruction = "", sensorTagId = 200)))
        state.selectCameraSensor(state.project.sensors.single())
        assertFalse(state.canSelectPreviousCameraSensor)
        state.stepCameraSensor(-1)
        assertEquals("TEMP-A", state.sensorId)
        state.stepCameraSensor(1)
        assertTrue(state.canSelectPreviousCameraSensor)
        assertFalse(state.canSelectNextCameraSensor)
        val newId = state.sensorId
        state.stepCameraSensor(1)
        assertEquals(newId, state.sensorId)
        assertEquals("", state.sensorTagId)
        state.planSensorAtCursor = true
        state.beginNewCameraSensor()
        assertFalse(state.planSensorAtCursor)
        assertEquals(1, state.project.sensors.size)
        assertTrue(state.log.results.isEmpty())
    }

    @Test fun selectingSensorOnAnotherFaceInvalidatesTheOldCursorUntilANewFrame() {
        state.selectedTagPlane = TagPlane.Front
        readyCursor(MmPosition(300, 0, 400))
        val top = Sensor(1, "TOP", "Boven", "Top", MmPosition(300, 400, 1000), toleranceMm = 50, instruction = "")
        state.project = state.project.copy(sensors = listOf(top))
        state.selectCameraSensor(top)
        assertNull(state.arCursorPosition)
        state.saveSensorAtCursor()
        assertEquals(SensorStatus.Pending, state.project.sensors.single().status)
        assertTrue(state.log.results.isEmpty())
    }
    @Test fun preparationNeverOverwritesAnExistingIdAndEditorCanUnlinkTag() {
        state.sensorTagId = "200"
        state.saveSensorAtBoxPosition(MmPosition(300, 0, 400))
        val original = state.project.sensors.single()
        state.sensorId = original.id
        state.saveSensorAtBoxPosition(MmPosition(700, 0, 400))
        assertEquals(original, state.project.sensors.single())
        assertNull(state.editSensorDetails(original.id, "Nieuwe naam", 75, null, "Instructie"))
        val edited = WorkflowAppState(context).project.sensors.single()
        assertEquals(original.id, edited.id)
        assertEquals(original.positionMm, edited.positionMm)
        assertEquals("Nieuwe naam", edited.name)
        assertEquals(75, edited.toleranceMm)
        assertNull(edited.sensorTagId)
    }
}
