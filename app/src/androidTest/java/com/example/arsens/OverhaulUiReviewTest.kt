package com.example.arsens

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arsens.ar.*
import com.example.arsens.ar.calibration.*
import com.example.arsens.data.*
import com.example.arsens.ui.*
import com.example.arsens.ui.theme.ARSensTheme
import org.junit.*
import org.junit.Assert.*
import java.io.File

/** Real Compose screens with isolated project data; the camera background is a labelled test surface. */
class OverhaulUiReviewTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var store: WallTestStore
    @Before fun setup() { store=WallTestStore() }
    @After fun cleanup() { store.close() }
    @Test fun projectMapAndScanScreenshots() {
        val state=store.state()
        state.project=Project("Trafo T-240", "", listOf(
            Sensor(1,"T01","Tanktemperatuur","Top",MmPosition(300,250,1000),toleranceMm=25,instruction=""),
            Sensor(2,"T02","Dekseltemperatuur","Top",MmPosition(700,600,1000),toleranceMm=25,instruction="")),
            emptyList(),dimensionsMm=WallTestGeometry.dimensions)
        val page=mutableStateOf("project")
        compose.setContent { ARSensTheme {
            when(page.value) {
                "project" -> WorkflowProjectOverview(state)
                "map" -> WorkflowReportMap2DScreen(state)
                else -> WorkflowWallCalibrationScreen(state) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF53626B)), contentAlignment=Alignment.Center) {
                        Text("Camerabeeld · test",color=Color.White.copy(alpha=0.5f))
                    }
                }
            }
        } }
        screenshot("project")
        compose.runOnIdle { page.value="map";state.activeMapView=TransformerMapView.Top }
        screenshot("map")
        compose.onNodeWithText("Lijst").performClick()
        screenshot("map-list")
        compose.onNodeWithText("T01 · Tanktemperatuur · Nog te plaatsen").performClick()
        screenshot("map-selected")
        compose.runOnIdle {
            state.beginWallScan();state.wallScanSize="100";state.wallSelectedTagId=0
            state.updateWallScanFrame(AprilTagFrameResult(calibrationRevision=state.arCalibrationRevision,
                wallScanFrame=WallScanFrame(state.wallScanId,1,true,WallTestGeometry.observations(SystemClock.elapsedRealtime(),1).filter { it.tagId==0 },null)))
            page.value="scan"
        }
        screenshot("scan")
        compose.onNodeWithTag("wall-scan-mode").performClick()
        compose.onNodeWithText("Handmatig").performClick()
        compose.runOnIdle { assertFalse(state.wallAutoCapture) }
        compose.onNodeWithTag("wall-scan-mode").performClick()
        compose.onNodeWithText("Automatisch").performClick()
        compose.runOnIdle { assertTrue(state.wallAutoCapture) }
        screenshot("scan-auto")
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        // Allow the emulator compositor to present the frame after Compose becomes idle.
        SystemClock.sleep(500)
        val stage=InstrumentationRegistry.getArguments().getString("reviewStage") ?: "after"
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val folder=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"ui-review").also { it.mkdirs() }
        File(folder,"$stage-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
}
