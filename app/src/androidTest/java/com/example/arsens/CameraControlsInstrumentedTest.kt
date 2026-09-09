package com.example.arsens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arsens.data.*
import com.example.arsens.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual camera chrome with inert camera and in-memory selection. */
class CameraControlsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val diagnostic = mutableStateOf("Anker bevestigd")
    private val selected = mutableStateOf("19")
    private var captures = 0
    private var previous = 0
    private var next = 0
    private val sensors = listOf(
        Sensor(1, "19", "Temperatuur tank", "Front", MmPosition(0, 0, 0), toleranceMm = 50, instruction = ""),
        Sensor(2, "20", "Koeling", "Front", MmPosition(0, 0, 0), toleranceMm = 50, instruction = "", status = SensorStatus.Ok))

    private fun open(menu: String? = null) {
        compose.setContent { MaterialTheme {
            FullScreenCameraWorkflowShell("Testproject", "", topActions = {
                WorkflowCameraSensorPicker(sensors, selected.value, "1 / 2 geplaatst",
                    { selected.value = it.id }, { selected.value = "21" })
            }, message = "Melding achter menu mag niet zichtbaar zijn", primaryActionText = "Sensor ${selected.value}: Vastleggen",
                onPrimaryAction = { captures++ }, onPreviousSensor = { previous++ }, onNextSensor = { next++ },
                previousSensorEnabled = true, nextSensorEnabled = false,
                requestedMenuKey = menu, onMenuRequestConsumed = {},
                menus = listOf(WorkflowCameraMenu("ar", "AR model") { Text(diagnostic.value) }),
                camera = {
                    Box(Modifier.fillMaxSize().background(Color(0xFF53626B)))
                    WorkflowCursorOverlay(MmPosition(300, 0, 400), true,
                        "Sensor ${selected.value} · Temperatuur tank · Vastleggen", "X=300 mm, Y=0 mm, Z=400 mm")
                })
        } }
    }

    @Test fun closeButtonDoesNotMoveWhenLiveDiagnosticsChangeLength() {
        open("ar")
        val before = compose.onNodeWithTag("camera-menu-close").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { diagnostic.value = "Referentie blijft afwijken. Scan twee gecontroleerde referenties. ".repeat(30) }
        val after = compose.onNodeWithTag("camera-menu-close").fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        compose.onNodeWithText("Melding achter menu mag niet zichtbaar zijn").assertDoesNotExist()
        screenshot("camera-menu")
        compose.onNodeWithContentDescription("Menu sluiten").performClick()
        compose.onNodeWithTag("camera-menu-close").assertDoesNotExist()
        compose.onNodeWithTag("camera-capture").assertIsDisplayed()
    }

    @Test fun topCounterSelectsSensorByNameAndStatusAndUpdatesCaptureIdentity() {
        open()
        compose.onNodeWithTag("camera-sensor-picker").performClick()
        compose.onNodeWithText("Te plaatsen").assertExists()
        compose.onNodeWithText("Geplaatst").assertExists()
        screenshot("camera-sensors")
        compose.onNodeWithText("Zoek naam of ID").performTextInput("Koeling")
        compose.onNodeWithTag("camera-sensor-19").assertDoesNotExist()
        compose.onNodeWithTag("camera-sensor-20").performClick()
        compose.onNodeWithContentDescription("Sensor 20: Vastleggen").performClick()
        compose.runOnIdle { assertEquals("20", selected.value); assertEquals(1, captures) }
    }

    @Test fun bottomHasOneCaptureAndExplicitPreviousNextWithoutOldModeButtons() {
        open()
        compose.onNodeWithText("Sensor zit hier").assertDoesNotExist()
        compose.onNodeWithText("Sensor").assertDoesNotExist()
        compose.onNodeWithText("Tag").assertDoesNotExist()
        compose.onNodeWithContentDescription("Vorige sensor").performClick()
        compose.onNodeWithContentDescription("Volgende sensor").assertIsNotEnabled()
        compose.onNodeWithTag("camera-capture").performClick()
        compose.runOnIdle { assertEquals(1, previous); assertEquals(0, next); assertEquals(1, captures) }
        screenshot("camera-controls")
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        File(instrumentation.targetContext.cacheDir, "$name.png").outputStream().use {
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
