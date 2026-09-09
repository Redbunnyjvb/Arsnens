package com.example.arsens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.arsens.data.*
import com.example.arsens.ui.*
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A standalone Compose workspace with in-memory callbacks. No app project or camera is opened. */
@RunWith(AndroidJUnit4::class)
class MapWorkflowInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var creations = 0
    private var moves = 0
    private val sensor = Sensor(1, "T0", "Temperatuur tank", "Top", MmPosition(500, 500, 1000),
        toleranceMm = 50, instruction = "")

    private fun open() {
        val project = Project("Workflow test", "", markers = emptyList(), dimensionsMm = MmPosition(1000, 1000, 1000), sensors = listOf(sensor))
        compose.setContent {
            MaterialTheme {
                TransformerMapWorkspace(project, InstallationLog("Test", "", "", emptyList()), onBack = {},
                    onPlaceSensorPoint = { creations++ }, onMoveSensorPoint = { _, _, _ -> moves++ },
                    onPlaceTagPoint = { _, _ -> creations++ }, onMoveTagPoint = { _, _, _ -> moves++ },
                    sensorControls = {}, tagControls = {}, onBeginPrepare = {},
                    nextSensorLabel = "Sensor 2", nextTagLabel = "Tag 0", onEditSensor = { _, _, _, _, _ -> null },
                    onEditTag = {}, onDeleteSensor = {}, onDeleteTag = {}, onResetPlacement = {}, onCamera = {})
            }
        }
    }

    @Test fun defaultTapDoesNotCreateAndPreparingTapOnExistingSensorOnlySelects() {
        open()
        compose.onNodeWithTag("transformer-map").performTouchInput { click(center - Offset(90f, 40f)) }
        compose.runOnIdle { assertEquals(0, creations); assertEquals(0, moves) }
        compose.onNodeWithText("Selecteren", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Voorbereiden").performClick()
        screenshot("prepare")
        compose.onNodeWithTag("transformer-map").performTouchInput { click(center) }
        compose.onNodeWithText("T0 · Temperatuur tank").assertExists()
        compose.runOnIdle { assertEquals(0, creations); assertEquals(0, moves) }
    }

    @Test fun modeSwitchUpdatesTapHandlerAndFreePreparationTapCreatesOnlyOnce() {
        open()
        compose.onNodeWithText("Selecteren", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Voorbereiden").performClick()
        compose.onNodeWithTag("transformer-map").performTouchInput { click(center - Offset(90f, 40f)) }
        compose.runOnIdle { assertEquals(1, creations) }
    }

    @Test fun draggingIsADraftUntilSavedAndCancelHasNoSideEffects() {
        open()
        compose.onNodeWithTag("transformer-map").performTouchInput { click(center) }
        compose.onNodeWithText("Verplaatsen").performScrollTo().performClick()
        compose.onNodeWithTag("transformer-map").performTouchInput { swipe(center, center + Offset(80f, 0f), 600) }
        compose.runOnIdle { assertEquals(0, moves) }
        compose.onNodeWithText("Annuleren").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, moves) }
        compose.onNodeWithText("Verplaatsen").performScrollTo().performClick()
        compose.onNodeWithTag("transformer-map").performTouchInput { swipe(center, center + Offset(80f, 0f), 600) }
        compose.onNodeWithText("Opslaan").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, moves); assertEquals(0, creations) }
    }

    @Test fun listSearchFindsNamesAndSelectionDoesNotPlaceAnything() {
        open()
        compose.onNodeWithText("Lijst").performClick()
        compose.onNodeWithText("Zoek naam of ID").performTextInput("Temperatuur")
        compose.onNodeWithText("T0 · Temperatuur tank · Nog te plaatsen").performClick()
        compose.onNodeWithText("T0 · Temperatuur tank").assertExists()
        compose.onNodeWithText("0 / 1 geplaatst").assertExists()
        screenshot("selected")
        compose.runOnIdle { assertEquals(0, moves); assertEquals(0, creations) }
    }
    @Test fun measuringRequiresTwoTapsAndDoesNotCreateOrMoveSensors() {
        open()
        compose.onNodeWithText("Selecteren", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Meten").performClick()
        compose.onNodeWithTag("transformer-map").performTouchInput { click(center - Offset(90f, 40f)) }
        compose.onNodeWithText("Tik het tweede punt.").assertExists()
        compose.onNodeWithTag("transformer-map").performTouchInput { click(center - Offset(90f, -40f)) }
        compose.onNode(hasText("mm · ΔX", substring = true)).assertExists()
        compose.runOnIdle { assertEquals(0, creations); assertEquals(0, moves) }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.cacheDir, "workflow-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

}
