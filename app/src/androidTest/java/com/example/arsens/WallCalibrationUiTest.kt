package com.example.arsens

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arsens.ar.*
import com.example.arsens.ar.calibration.*
import com.example.arsens.data.*
import com.example.arsens.ui.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class WallCalibrationUiTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var store: WallTestStore
    @Before fun setup() { store=WallTestStore() }
    @After fun cleanup() { store.close() }
    @Test fun projectChoiceKeepsExistingMethodAndOffersBothDimensionSources() {
        val mode=mutableStateOf(ReferenceGeometryMode.KnownTagPositions)
        val source=mutableStateOf(WallDimensionSource.Entered)
        compose.setContent { MaterialTheme { Surface { Column(Modifier.safeDrawingPadding().padding(16.dp)) {
            WorkflowReferenceMethodChoice(mode.value,source.value,{mode.value=it},{source.value=it})
        } } } }
        compose.onNodeWithText("Afmetingen bepalen").assertDoesNotExist()
        compose.onNodeWithText("Wanden scannen").performClick()
        compose.onNodeWithText("Afmetingen bekend").assertExists()
        compose.onNodeWithText("Afmetingen bepalen").performClick()
        compose.runOnIdle { assertEquals(WallDimensionSource.Scanned,source.value) }
        compose.onNodeWithText("Scan alle vier",substring=true).assertExists()
        screenshot("wall-method-choice")
        compose.onNodeWithText("Bekende tagposities").performClick()
        compose.onNodeWithText("Afmetingen bepalen").assertDoesNotExist()
    }
    @Test fun visibleTagIsAssignedToExplicitWallAndHeightInstructionsAreAccessible() {
        val state=store.state()
        state.project=state.project.copy(referenceGeometryMode=ReferenceGeometryMode.ScannedWalls,wallDimensionSource=WallDimensionSource.Scanned)
        state.beginWallScan();state.wallScanSize="100";state.wallSelectedTagId=0
        state.updateWallScanFrame(AprilTagFrameResult(calibrationRevision=state.arCalibrationRevision,
            wallScanFrame=WallScanFrame(state.wallScanId,1,true,WallTestGeometry.observations(SystemClock.elapsedRealtime(),1),null)))
        compose.setContent { WorkflowWallCalibrationScreen(state) { Box(Modifier.fillMaxSize().background(Color(0xFF53626B))) } }
        compose.onNodeWithText("Achter").performClick()
        compose.onNodeWithText("Tag 0 op Achter vastleggen").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) { click ->
            state.updateWallScanFrame(AprilTagFrameResult(calibrationRevision=state.arCalibrationRevision,
                wallScanFrame=WallScanFrame(state.wallScanId,1,true,WallTestGeometry.observations(SystemClock.elapsedRealtime(),2),null)))
            assertTrue(click())
        }
        compose.runOnIdle { assertEquals(CalibrationWall.Back,state.wallAssignments.single().wall) }
        screenshot("wall-side-capture")
        compose.onNodeWithText("Deksel / hoogte").performClick()
        compose.onNodeWithText("Zijwandhoogte onder deksel (mm)").assertIsDisplayed()
        compose.onNodeWithText("Gereed").performClick()
        compose.onNodeWithText("Onderreferentie").assertDoesNotExist()
        compose.onNodeWithText("Flits").assertDoesNotExist()
        compose.onNodeWithText("Focus").assertDoesNotExist()
    }
    @Test fun contourCanBeInspectedFullScreenAndDetailsCanBeReopened() {
        val state=store.state();state.beginWallScan()
        state.wallScanSolution=WallCalibrationSolver.solve(WallTestGeometry.dimensions,WallDimensionSource.Entered,
            WallTestGeometry.tags,WallTestGeometry.datum).solution
        val camera=Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(-400f,450f,2400f),
            floatArrayOf((Math.PI/2-0.3).toFloat(),0.10f,0f),0f))
        val view=Transform3D.cameraGlFromCameraCv()*camera*WallTestGeometry.frame.inverseRigid()
        val projection=floatArrayOf(2f,0f,0f,0f, 0f,1.2f,0f,0f, 0f,0f,-1.0002f,-1f, 0f,0f,-20.002f,0f)
        val metrics=InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        state.wallScanFrame=WallScanFrame(state.wallScanId,1,true,emptyList(),
            ArDisplayProjection.fromOpenGlCamera(metrics.widthPixels,metrics.heightPixels,projection,view))
        compose.setContent { WorkflowWallCalibrationScreen(state) { Box(Modifier.fillMaxSize().background(Color(0xFF53626B))) } }
        compose.onNodeWithText("Controleer de tankcontour").assertIsDisplayed()
        compose.onNodeWithTag("accept-wall-calibration").assertIsDisplayed().assertIsEnabled()
        screenshot("wall-contour-preview")
        compose.onNodeWithText("Hele beeld").performClick()
        compose.onNodeWithTag("accept-wall-calibration").assertDoesNotExist()
        screenshot("wall-contour-full")
        compose.onNodeWithText("Details").performClick()
        compose.onNodeWithTag("accept-wall-calibration").assertExists()
        compose.onNodeWithText("Sluiten").performClick()
        compose.runOnIdle { assertFalse(state.wallScanActive) }
    }
    @Test fun footprintPreviewLeadsToTopLinkingAndFreeExtraTagCollection() {
        val state=store.state();state.beginWallScan()
        state.wallScanFootprint=WallCalibrationSolver.solveFootprint(WallTestGeometry.dimensions,WallDimensionSource.Entered,
            WallTestGeometry.tags,WallTestGeometry.datum).solution
        compose.setContent { WorkflowWallCalibrationScreen(state) { Box(Modifier.fillMaxSize().background(Color(0xFF53626B))) } }
        compose.onNodeWithText("Contour 1000 × 800 mm").assertIsDisplayed()
        compose.onNodeWithText("Verder: bovenkant koppelen").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(state.wallTopStage);assertEquals(CalibrationWall.Top,state.wallScanWall) }
        compose.onNodeWithText("Kies Boven en leg",substring=true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bovenkant koppelen").assertIsDisplayed()
        screenshot("wall-top-linking")
    }

    @Test fun horizontalTagStillHasAnExplicitCaptureActionForTheChosenFace() {
        val state=store.state();state.beginWallScan();state.wallScanSize="100"
        state.updateWallScanFrame(AprilTagFrameResult(calibrationRevision=state.arCalibrationRevision,
            wallScanFrame=WallScanFrame(state.wallScanId,1,true,
                WallTestGeometry.observations(SystemClock.elapsedRealtime(),1).filter { it.tagId==8 },null)))
        compose.setContent { WorkflowWallCalibrationScreen(state) { Box(Modifier.fillMaxSize().background(Color(0xFF53626B))) } }
        compose.onNodeWithText("Tag 8 op Voor vastleggen").assertIsDisplayed()
        compose.onNodeWithText("Deze tag lijkt horizontaal",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Boven",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Tag 8 op Boven vastleggen").performSemanticsAction(SemanticsActions.OnClick) { click ->
            state.updateWallScanFrame(AprilTagFrameResult(calibrationRevision=state.arCalibrationRevision,
                wallScanFrame=WallScanFrame(state.wallScanId,1,true,
                    WallTestGeometry.observations(SystemClock.elapsedRealtime(),2).filter { it.tagId==8 },null)))
            assertTrue(click())
        }
        compose.runOnIdle { assertEquals(CalibrationWall.Top,state.wallAssignments.single().wall) }
        screenshot("wall-top-capture")
    }

    private fun screenshot(name: String) {
        val i=InstrumentationRegistry.getInstrumentation()
        // Capture this app's rendered Compose view; emulator System UI is outside this test.
        val bitmap=compose.onRoot().captureToImage().asAndroidBitmap()
        File(i.targetContext.cacheDir,"$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
}
