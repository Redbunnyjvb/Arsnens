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
internal fun WorkflowReportScreen(state: WorkflowAppState) {
    val tags = state.savedAprilTags.sortedBy { it.id }
    val sensors = state.project.sensors.sortedBy { it.order }
    val mapper = state.project.coordinateMapper()
    // Voor de 3D-model-kaart: meshes lazy laden zodat de echte afmetingen (mm) getoond worden.
    LaunchedEffect(state.project.stlModels) { state.ensureStlMeshesLoaded() }
    WorkflowShell(
        title = "Rapport",
        subtitle = "",
        onBack = state::navigateBack,
        overflowItems = workflowTopBarMenuItems(state)
    ) {
        item { WorkflowReportProjectCard(state) }
        item {
            WorkflowReportSectionCard(title = "Tags", count = tags.size) {
                if (tags.isEmpty()) {
                    WorkflowReportEmpty("Nog geen tags geplaatst.")
                } else {
                    tags.forEachIndexed { index, tag ->
                        WorkflowReportEntityRow(
                            name = "Tag ${tag.id.toString().padStart(3, '0')}",
                            coords = "${workflowReportCoords(mapper.boxToOperator(tag.positionMm))}  ·  ${tag.sizeMm} mm",
                            plane = markerSurfaceLabel(tag, state.project.dimensionsMm),
                            dotColor = ArSensBlue,
                            onClick = { state.selectTagForEdit(tag) }
                        )
                        if (index < tags.lastIndex) WorkflowReportRowDivider()
                    }
                }
            }
        }
        item {
            WorkflowReportSectionCard(title = "Sensoren", count = sensors.size) {
                if (sensors.isEmpty()) {
                    WorkflowReportEmpty("Nog geen sensoren geplaatst.")
                } else {
                    sensors.forEachIndexed { index, sensor ->
                        WorkflowReportEntityRow(
                            name = sensor.id.ifBlank { "Sensor ${sensor.order.toString().padStart(3, '0')}" },
                            coords = workflowReportCoords(mapper.boxToOperator(sensor.positionMm)),
                            plane = sensor.side.ifBlank { "—" },
                            dotColor = ArSensTeal,
                            onClick = { state.selectSensorForEdit(sensor) }
                        )
                        if (index < sensors.lastIndex) WorkflowReportRowDivider()
                    }
                }
            }
        }
        item {
            // Assembly-overzicht: per STL-deel de werkelijke afmetingen in mm (geroteerde
            // bounding box × schaal). Tik op een deel om de transform-editor te openen.
            WorkflowReportSectionCard(title = "3D-model", count = state.project.stlModels.size) {
                if (state.project.stlModels.isEmpty()) {
                    WorkflowReportEmpty("Geen STL-assembly — importeer delen via Open STL.")
                } else {
                    state.project.stlModels.forEachIndexed { index, model ->
                        val mesh = state.stlMeshes[model.fileName]
                        val dims = if (mesh != null && !mesh.isEmpty) {
                            val ext = mesh.rotatedExtents(model.rotationDeg.x, model.rotationDeg.y, model.rotationDeg.z)
                            val s = model.scalePercent / 100f
                            "${(ext[0] * s).roundToInt()} × ${(ext[1] * s).roundToInt()} × ${(ext[2] * s).roundToInt()} mm"
                        } else if (model.visible) {
                            "mesh wordt geladen…"
                        } else {
                            "uit — laadt zodra zichtbaar"
                        }
                        WorkflowReportEntityRow(
                            name = model.name,
                            coords = dims,
                            plane = if (model.visible) "Aan" else "Uit",
                            dotColor = Color(stlPartColor(index)),
                            onClick = { state.navigateTo(WorkflowScreen.Stl) }
                        )
                        if (index < state.project.stlModels.lastIndex) WorkflowReportRowDivider()
                    }
                }
            }
        }
    }
}

internal fun workflowReportCoords(p: MmPosition): String =
    "X ${p.x / 10} cm    |    Y ${p.y / 10} cm    |    Z ${p.z / 10} cm"

@Composable
internal fun WorkflowReportProjectCard(state: WorkflowAppState) {
    val dateText = state.log.startedAt.ifBlank { formatProjectUpdatedAt(state.activeProjectUpdatedAtMillis) }
    val authorText = state.log.operator.ifBlank { "Onbekend" }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(
                    modifier = Modifier.size(96.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFE9EDF2)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(40.dp)) { drawWorkflowCameraIcon("cube", ArSensChromeMuted) }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        state.project.projectName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = ArSensChromeInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    WorkflowReportInfoRow("pin", "Locatie n.t.b.")
                    WorkflowReportInfoRow("calendar", dateText)
                    WorkflowReportInfoRow("person", authorText)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                WorkflowReportActionTile("Exporteren", "export", Modifier.weight(1f)) { state.showExportDialog = true }
                WorkflowReportActionTile("Open 2D", "twod", Modifier.weight(1f)) { state.open2DModel(WorkflowScreen.Report) }
                WorkflowReportActionTile("Open 3D", "stl", Modifier.weight(1f)) { state.navigateTo(WorkflowScreen.Stl) }
            }
        }
    }
}

@Composable
internal fun WorkflowReportInfoRow(iconKey: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(18.dp)) { drawWorkflowCameraIcon(iconKey, ArSensChromeMuted) }
        Text(text, fontSize = 14.sp, color = ArSensChromeMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun WorkflowReportActionTile(
    label: String,
    iconKey: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(78.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(Modifier.size(26.dp)) { drawWorkflowCameraIcon(iconKey, ArSensChromeInk) }
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 12.sp, color = ArSensChromeInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun WorkflowReportSectionCard(
    title: String,
    count: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ArSensChromeInk, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFEDF1F6)) {
                    Text(
                        "$count",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ArSensChromeMuted,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
            content()
        }
    }
}

@Composable
internal fun WorkflowReportEntityRow(
    name: String,
    coords: String,
    plane: String,
    dotColor: Color,
    onClick: () -> Unit
) {
    // Themakleuren i.p.v. vaste donkere inkt: zo blijft de rij leesbaar op de lichte rapport-
    // schermen én op de donkere camera-glas-sheet van de 3D-weergave (waar deze rij ook gebruikt wordt).
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(10.dp).background(dotColor, RoundedCornerShape(50)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)
            Text(coords, fontSize = 12.sp, color = muted)
        }
        Text("Vlak: $plane", fontSize = 13.sp, color = muted, fontWeight = FontWeight.Medium)
        Canvas(Modifier.size(18.dp)) { drawWorkflowCameraIcon("chevron", muted) }
    }
}

@Composable
internal fun WorkflowReportRowDivider() {
    HorizontalDivider(color = Color(0xFFEDF1F6), modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
internal fun WorkflowReportEmpty(text: String) {
    Text(text, fontSize = 13.sp, color = ArSensChromeMuted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
internal fun WorkflowReportMap2DScreen(state: WorkflowAppState) {
    val preparedSetup = state.model2dPurpose == Model2DPurpose.PreparedSetup
    val placingSensors = state.model2dPurpose == Model2DPurpose.SensorSetup || preparedSetup
    TransformerMapWorkspace(
        project = state.project,
        log = state.log,
        onBack = state::navigateBack,
        initialView = state.activeMapView,
        onViewChanged = { state.activeMapView = it },
        message = state.message,
        sensorPlacementLabel = if (placingSensors) "Sensor" else null,
        onPlaceSensorPoint = if (placingSensors) state::saveSensorAtBoxPosition else null,
        // Verplaatsen (slepen) overal beschikbaar in de 2D-kaart, niet alleen tijdens plaatsen.
        onMoveSensorPoint = state::moveSensorToBoxPosition,
        tagPlacementLabel = if (preparedSetup) "Tag" else null,
        onPlaceTagPoint = if (preparedSetup) state::saveTagAtBoxPosition else null,
        onMoveTagPoint = state::moveTagToBoxPosition,
        onSelectSensor = if (!placingSensors) { id: String ->
            state.project.sensors.firstOrNull { it.id == id }?.let(state::selectSensorForEdit)
            state.selectedMapTarget = MapSelection.Sensor(id)
        } else null,
        onSelectTag = if (!placingSensors) { id: Int ->
            state.savedAprilTags.firstOrNull { it.id == id }?.let(state::selectTagForEdit)
            state.selectedMapTarget = MapSelection.Tag(id)
        } else null,
        selectControls = if (!placingSensors) {
            { WorkflowMapSelectionPanel(state) }
        } else null,
        tagControls = if (preparedSetup) {
            { WorkflowPreparedTagMapMenu(state) }
        } else {
            null
        },
        sensorControls = if (preparedSetup) {
            { WorkflowPreparedSensorMapMenu(state) }
        } else {
            null
        },
        startControls = if (preparedSetup) {
            { WorkflowPreparedStartMapMenu(state) }
        } else {
            null
        },
        overflowItems = workflowTopBarMenuItems(state)
    )
}

/** Zijpaneel van de Selecteer-tool in de 2D-kaart: bewerk (naam/positie) of verwijder de
 *  aangetikte sensor of tag. Hergebruikt de bestaande edit-velden en state-methodes. */
@Composable
internal fun WorkflowMapSelectionPanel(state: WorkflowAppState) {
    when (val selection = state.selectedMapTarget) {
        is MapSelection.Sensor -> {
            val sensor = state.project.sensors.firstOrNull { it.id == selection.id }
            if (sensor == null) {
                WorkflowStatusChip(text = "Sensor niet meer beschikbaar.", status = SensorStatus.Pending)
                return
            }
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Sensor ${sensor.id} bewerken", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = state.sensorName,
                        onValueChange = { state.sensorName = it },
                        label = { Text("Naam") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                state.saveSensorPoint()
                                state.project.sensors.firstOrNull { it.id == selection.id }
                                    ?.let(state::selectSensorForEdit)
                            },
                            modifier = Modifier.height(48.dp)
                        ) { Text("Opslaan") }
                        OutlinedButton(
                            onClick = { state.requestRemoveSensor(sensor) },
                            modifier = Modifier.height(48.dp)
                        ) { Text("Verwijder") }
                    }
                }
            }
        }
        is MapSelection.Tag -> {
            val tag = state.savedAprilTags.firstOrNull { it.id == selection.id }
            if (tag == null) {
                WorkflowStatusChip(text = "Tag niet meer beschikbaar.", status = SensorStatus.Pending)
                return
            }
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AprilTag ${tag.id} bewerken", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        WorkflowNumberField("Tag ID", state.tagId, { state.tagId = it }, Modifier.weight(1f))
                        WorkflowNumberField("Formaat mm", state.tagSize, { state.tagSize = it }, Modifier.weight(1f))
                    }
                    WorkflowTagCoordinateFields(state)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                state.savePreparedTagFromFields()
                                state.savedAprilTags.firstOrNull { it.id == selection.id }
                                    ?.let(state::selectTagForEdit)
                            },
                            modifier = Modifier.height(48.dp)
                        ) { Text("Opslaan") }
                        OutlinedButton(
                            onClick = { state.requestDeleteMarker(tag) },
                            modifier = Modifier.height(48.dp)
                        ) { Text("Verwijder") }
                    }
                }
            }
        }
        null -> {
            WorkflowStatusChip(
                text = "Tik een sensor of tag op de kaart om te bewerken of te verwijderen.",
                status = SensorStatus.Pending
            )
        }
    }
}

@Composable
internal fun WorkflowPreparedTagMapMenu(state: WorkflowAppState) {
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tag voorbereiden", fontWeight = FontWeight.Bold)
            WorkflowTagPlacementPicker(state)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                WorkflowNumberField("Tag ID", state.tagId, { state.tagId = it }, Modifier.weight(1f))
                WorkflowNumberField("Formaat mm", state.tagSize, { state.tagSize = it }, Modifier.weight(1f))
            }
            WorkflowTagCoordinateFields(state)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = state::saveSelectedPreparedTag, modifier = Modifier.height(52.dp)) {
                    Text("Plaats gekozen anker")
                }
                OutlinedButton(onClick = state::savePreparedTagFromFields, modifier = Modifier.height(52.dp)) {
                    Text("Sla coordinaten op")
                }
            }
        }
    }
    WorkflowKnownTags(
        project = state.project,
        tags = state.savedAprilTags,
        onEdit = state::selectTagForEdit,
        onToggleActive = state::toggleMarkerActive,
        onDelete = state::requestDeleteMarker
    )
}

@Composable
internal fun WorkflowPreparedSensorMapMenu(state: WorkflowAppState) {
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sensor voorbereiden", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.sensorId,
                    onValueChange = { state.sensorId = it },
                    label = { Text("Sensor-ID") },
                    singleLine = true,
                    modifier = Modifier.widthIn(min = 120.dp, max = 170.dp)
                )
                OutlinedTextField(
                    value = state.sensorName,
                    onValueChange = { state.sensorName = it },
                    label = { Text("Naam") },
                    singleLine = true,
                    modifier = Modifier.widthIn(min = 190.dp, max = 320.dp)
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkflowNumberField("Meet X", state.sensorX, { state.sensorX = it }, Modifier.widthIn(min = 112.dp, max = 150.dp))
                WorkflowNumberField("Meet Y", state.sensorY, { state.sensorY = it }, Modifier.widthIn(min = 112.dp, max = 150.dp))
                WorkflowNumberField("Meet Z", state.sensorZ, { state.sensorZ = it }, Modifier.widthIn(min = 112.dp, max = 150.dp))
                WorkflowNumberField("Tol mm", state.sensorTolerance, { state.sensorTolerance = it }, Modifier.widthIn(min = 112.dp, max = 150.dp))
            }
            OutlinedTextField(
                value = state.sensorInstruction,
                onValueChange = { state.sensorInstruction = it },
                label = { Text("Instructie") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = state::saveSensorPoint, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Sensor opslaan")
            }
        }
    }
    WorkflowRecentSensorsPanel(state)
}

@Composable
internal fun WorkflowPreparedStartMapMenu(state: WorkflowAppState) {
    WorkflowStatusChip(
        text = "${state.knownAprilTags.size} tags · ${state.project.sensors.size} sensoren klaar",
        status = if (state.knownAprilTags.isNotEmpty() && state.project.sensors.isNotEmpty()) SensorStatus.Ok else SensorStatus.Pending
    )
    Button(onClick = state::startPreparedInstallation, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Camera starten")
    }
}
