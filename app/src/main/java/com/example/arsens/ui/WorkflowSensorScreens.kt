@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.arsens.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arsens.ar.AprilTagCorner
import com.example.arsens.ar.AprilTagFrameResult
import com.example.arsens.ar.ArCoreCameraPanel
import com.example.arsens.ar.ArTrackingStatus
import com.example.arsens.ar.PlaneHit
import com.example.arsens.ar.TagAnchor
import com.example.arsens.ar.TagPlane
import com.example.arsens.ar.estimateCursorOnReferenceSurface
import com.example.arsens.ar.estimateSurfaceAtPixel
import com.example.arsens.ar.markerCornersInProjectFrame
import com.example.arsens.ar.projectPointToScreen
import com.example.arsens.ar.ProjectPointMm
import com.example.arsens.ar.projectPointToImage
import com.example.arsens.ar.projectPositionToScreen
import com.example.arsens.ar.ScreenPointPx
import com.example.arsens.ar.tagPlacementFor
import com.example.arsens.ar.tagRotationFor
import com.example.arsens.data.CoordinateFrameSettings
import com.example.arsens.data.FloatVector
import com.example.arsens.data.InstallationResult
import com.example.arsens.data.LocalProjectRepository
import com.example.arsens.data.Marker
import com.example.arsens.data.MmPosition
import com.example.arsens.data.OriginCorner
import com.example.arsens.data.Project
import com.example.arsens.data.ProjectSummary
import com.example.arsens.data.Sensor
import com.example.arsens.data.SensorStatus
import com.example.arsens.data.StlMesh
import com.example.arsens.data.StlModel
import com.example.arsens.data.StlParser
import com.example.arsens.data.asAprilTagCalibrationMarker
import com.example.arsens.data.confirmSensorAtMeasuredPosition
import com.example.arsens.data.coordinateMapper
import com.example.arsens.data.defaultFrameForOrigin
import com.example.arsens.data.distanceMm
import com.example.arsens.data.isAprilTagCalibrationMarker
import com.example.arsens.data.rotatedExtents
import com.example.arsens.data.toReadableMm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun WorkflowSensorSheet(state: WorkflowAppState) {
    // Sensoren-paneel zoals de afbeelding: lijst van geplaatste sensoren met vlak + coördinaten en
    // bewerk/verwijder-knoppen, daaronder de teal "Plaats sensor"-knop.
    val cursorReady = state.sensorPlacementReady && state.arCursorPosition != null &&
        (state.arCursorSource == "surface" || state.arCursorSource == "depth")
    val sensors = state.project.sensors.sortedBy { it.order }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Hier komt een sensor", modifier = Modifier.weight(1f), color = Color.White)
            Switch(checked = state.planSensorAtCursor, onCheckedChange = { state.planSensorAtCursor = it })
        }
        Text(
            if (state.planSensorAtCursor) "Geplande plek: cirkel met straal ${state.sensorTolerance} mm."
            else "Leg vast waar de sensor zit; een zichtbare sensor-tag wordt gekoppeld.",
            color = Color.White.copy(alpha = 0.66f), fontSize = 13.sp
        )
        if (sensors.isEmpty()) {
            Text(
                "Nog geen sensoren geplaatst. Richt de cursor op het vlak en tik 'Plaats sensor'.",
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 13.sp
            )
        } else {
            sensors.forEach { sensor ->
                WorkflowSheetEntityRow(
                    name = sensor.id.ifBlank { "Sensor ${sensor.order.toString().padStart(3, '0')}" },
                    plane = sensor.side.ifBlank { state.selectedTagPlane.cameraPlaneLabel() },
                    coords = workflowSheetCoords(sensor.positionMm),
                    dotColor = workflowStatusColor(sensor.status),
                    onEdit = { state.selectSensorForEdit(sensor) },
                    onDelete = { state.requestRemoveSensor(sensor) }
                )
            }
        }
        WorkflowSheetPlaceButton(
            label = if (state.planSensorAtCursor) "Hier komt de sensor" else "Sensor zit hier",
            color = ArSensTeal,
            enabled = cursorReady,
            onClick = {
                state.selectCameraPlacementTarget(CameraPlacementTarget.Sensor)
                state.saveSensorAtCursor()
            }
        )
        if (!cursorReady) Text(
            state.sensorPlacementBlockReason ?: "Richt de cursor op het gekozen trafovlak.",
            color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp
        )
    }
}

@Composable
internal fun WorkflowSensorsScreen(state: WorkflowAppState) {
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(state::importSensors)
    }
    WorkflowShell(
        title = "Sensorlocaties",
        subtitle = "Voorbereid of live vastleggen",
        onBack = { state.go(WorkflowScreen.Start) },
        topActions = {
            ArSensCounterPill("${state.project.sensors.size} sensoren geplaatst", dark = false)
        },
        overflowItems = workflowTopBarMenuItems(state)
    ) {
        item { WorkflowMessage(state.message) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { csvPicker.launch("text/*") }) { Text("CSV importeren") }
                OutlinedButton(onClick = { state.open2DModel(WorkflowScreen.Sensors) }) { Text("2D model") }
                OutlinedButton(onClick = { state.go(WorkflowScreen.Install) }) { Text("Installeren") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sensorpunt toevoegen", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(state.sensorId, { state.sensorId = it }, label = { Text("ID") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(state.sensorName, { state.sensorName = it }, label = { Text("Naam") }, singleLine = true, modifier = Modifier.weight(2f))
                    }
                    WorkflowNumberField(
                        "ID-tag (AprilTag op sensor, optioneel)",
                        state.sensorTagId,
                        { state.sensorTagId = it },
                        Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        WorkflowNumberField("Meet X", state.sensorX, { state.sensorX = it }, Modifier.weight(1f))
                        WorkflowNumberField("Meet Y", state.sensorY, { state.sensorY = it }, Modifier.weight(1f))
                        WorkflowNumberField("Meet Z", state.sensorZ, { state.sensorZ = it }, Modifier.weight(1f))
                    }
                    WorkflowNumberField("Tolerantie mm", state.sensorTolerance, { state.sensorTolerance = it }, Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = state.sensorInstruction,
                        onValueChange = { state.sensorInstruction = it },
                        label = { Text("Instructie") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = state::saveSensorPoint, modifier = Modifier.fillMaxWidth()) {
                        Text("Sensorpunt opslaan")
                    }
                }
            }
        }
        item { WorkflowSection("Sensoren (${state.project.sensors.size})") }
        items(state.project.sensors.sortedBy { it.order }, key = { it.id }) { sensor ->
            val result = state.resultFor(sensor.id)
            val status = result?.status ?: sensor.status
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(9.dp).background(workflowStatusColor(status), RoundedCornerShape(50)))
                        Text("${sensor.order}. ${sensor.id} · ${sensor.name}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    }
                    WorkflowInfoRow("Meet-XYZ", state.operatorText(sensor.positionMm))
                    WorkflowInfoRow("Tolerantie", "${sensor.toleranceMm} mm")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { state.selectSensorForEdit(sensor) }, modifier = Modifier.height(44.dp)) { Text("Bewerk") }
                        OutlinedButton(onClick = { state.requestRemoveSensor(sensor) }, modifier = Modifier.height(44.dp)) { Text("Verwijder") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkflowInstallScreen(state: WorkflowAppState) {
    val sensor = state.currentSensor
    if (sensor == null) {
        WorkflowShell(
            title = "Installatie",
            subtitle = "Geen sensoren",
            onBack = { if (state.mode == WorkMode.Prepared) state.returnToPreparedSetup() else state.go(WorkflowScreen.Sensors) }
        ) {
            item {
                WorkflowStatusChip(
                    text = if (state.mode == WorkMode.Prepared) {
                        "Geen sensoren — plaats eerst sensorpunten in het plan"
                    } else {
                        "Geen sensoren — maak of importeer eerst sensorpunten"
                    },
                    status = SensorStatus.Fail
                )
            }
        }
        return
    }

    val menus = workflowInstallCameraMenus(state, sensor)
    FullScreenCameraWorkflowShell(
        title = state.project.projectName,
        subtitle = "",
        onBack = { if (state.mode == WorkMode.Prepared) state.returnToPreparedSetup() else state.go(WorkflowScreen.Sensors) },
        topActions = {
            WorkflowCameraStatusPill("${sensor.order}/${state.project.sensors.size} - ${sensor.id}")
        },
        shortcutActions = listOf(
            WorkflowCameraShortcut(
                key = "map2d",
                label = "2D\nweergave",
                onClick = { state.open2DModel(WorkflowScreen.Install) }
            ),
            WorkflowCameraShortcut(
                key = "scantag",
                label = "Scan-\ntag",
                iconKey = "sensor",
                onClick = state::confirmSensorByScannedTag
            ),
            WorkflowCameraShortcut(
                key = "previous",
                label = "Vorige",
                iconKey = "undo",
                onClick = state::previousSensor
            ),
            WorkflowCameraShortcut(
                key = "next",
                label = "Volgende",
                iconKey = "place",
                onClick = state::nextSensor
            )
            // Rapport zit in het hamburger-menu; de AR-knop komt uit het "ar"-menu zelf.
        ),
        topStart = {
            WorkflowCameraPlaneSelectorPanel(
                selectedPlane = state.selectedTagPlane,
                onPlaneSelected = state::selectTagPlane
            )
        },
        message = state.message,
        primaryActionText = "OK",
        onPrimaryAction = state::confirmInstallation,
        camera = {
            WorkflowCameraLayers(state, targetSensor = sensor)
        },
        requestedMenuKey = state.cameraMenuRequest,
        onMenuRequestConsumed = state::consumeCameraMenuRequest,
        menus = menus,
        planeText = state.selectedTagPlane.shortLabel,
        overflowItems = workflowTopBarMenuItems(state),
        cursorOffset = state.arCursorScreenOffset,
        onRecenterCursor = { state.arCursorScreenOffset = Offset.Zero }
    )
}

internal fun workflowInstallCameraMenus(state: WorkflowAppState, sensor: Sensor): List<WorkflowCameraMenu> =
    listOf(
        WorkflowCameraMenu("sensor", "Sensor") {
            WorkflowInstallSensorPanel(state, sensor)
        },
        WorkflowCameraMenu("cursor", "Cursor") {
            WorkflowInstallCursorFields(state)
        },
        WorkflowCameraMenu("layers", "Lagen") {
            WorkflowOverlayToggles(state)
        },
        WorkflowCameraMenu("ar", "AR model") {
            WorkflowArOptionsSheet(state)
        }
    )

@Composable
internal fun WorkflowInstallSensorPanel(state: WorkflowAppState, sensor: Sensor) {
    WorkflowStatusChip(
        text = state.nearestTagInstruction(sensor),
        status = SensorStatus.Pending
    )
    state.scannedMeasuredPosition?.let { measured ->
        val delta = distanceMm(measured - sensor.positionMm)
        WorkflowStatusChip(
            text = "Gescande meting · afwijking $delta mm (tolerantie ${sensor.toleranceMm} mm) — druk OK om te bevestigen",
            status = if (delta <= sensor.toleranceMm) SensorStatus.Ok else SensorStatus.Fail
        )
    }
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${sensor.id} · ${sensor.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            WorkflowInfoRow("Doel meet-XYZ", state.operatorText(sensor.positionMm))
            WorkflowInfoRow("Tolerantie", "${sensor.toleranceMm} mm")
            if (sensor.instruction.isNotBlank()) {
                Text(
                    sensor.instruction,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun WorkflowInstallCursorFields(state: WorkflowAppState) {
    WorkflowStatusChip(
        text = "Cursor ${state.operatorText(state.arCursorPosition)} · ${state.arCursorSourceLabel}",
        status = if (state.arCursorPosition != null && state.arCursorInsideTransformer) {
            SensorStatus.Ok
        } else {
            SensorStatus.Pending
        }
    )
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Handmatige fallback", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                WorkflowNumberField("Meet X", state.cursorX, { state.cursorX = it }, Modifier.weight(1f))
                WorkflowNumberField("Meet Y", state.cursorY, { state.cursorY = it }, Modifier.weight(1f))
                WorkflowNumberField("Meet Z", state.cursorZ, { state.cursorZ = it }, Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = state::setCursorFromCurrentSensor, modifier = Modifier.height(46.dp)) {
                    Text("Gebruik doelpositie")
                }
                OutlinedButton(onClick = state::useArCursorEstimate, modifier = Modifier.height(46.dp)) {
                    Text("Cursor overnemen")
                }
            }
        }
    }
}

