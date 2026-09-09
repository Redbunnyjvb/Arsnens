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
    @Test fun planIn2DPlaceElsewhereThenResetAndReload() {
        val planned = MmPosition(300, 0, 400)
        val measured = MmPosition(600, 0, 400)
        state.saveSensorAtBoxPosition(planned)
        val sensor = state.project.sensors.single()
        assertEquals(SensorStatus.Pending, sensor.status)
        assertEquals(PlacementOrigin.Prepared, sensor.origin)
        assertNull(sensor.placement)
        state.selectCameraSensor(sensor)
        readyCursor(measured)
        state.saveSensorAtCursor()
        val installed = state.project.sensors.single()
        assertEquals("TEMP-A", installed.id)
        assertEquals("Temperatuur", installed.name)
        assertEquals(planned, installed.positionMm)
        assertEquals(SensorStatus.Fail, installed.status)
        assertNotNull(installed.placement)
        assertEquals(measured, state.log.results.single().measuredPositionMm)
        assertEquals("TEMP-A", state.sensorId) // A second tap cannot silently create a new sensor.
        state.resetPlacement(installed.id)
        val reloaded = WorkflowAppState(context)
        assertEquals(sensor, reloaded.project.sensors.single())
        assertTrue(reloaded.log.results.isEmpty())
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
        readyCursor(MmPosition(300, 0, 400))
        state.saveSensorAtCursor()
        val sensor = state.project.sensors.single()
        assertEquals(PlacementOrigin.OnTheFly, sensor.origin)
        assertEquals(SensorStatus.Ok, sensor.status)
        assertNotNull(sensor.placement)
        assertEquals(sensor.positionMm, state.log.results.single().measuredPositionMm)
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
