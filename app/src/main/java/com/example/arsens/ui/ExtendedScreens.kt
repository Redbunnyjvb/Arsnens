@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.arsens.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.testTag
import com.example.arsens.data.displayName
import com.example.arsens.data.placementCountLabel
import com.example.arsens.data.toReadableMm
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arsens.ar.AprilTagDetection
import com.example.arsens.ar.AprilTagFrameResult
import com.example.arsens.ar.TagPlane
import com.example.arsens.data.InstallationLog
import com.example.arsens.data.MmPosition
import com.example.arsens.data.Project
import com.example.arsens.data.Sensor
import com.example.arsens.data.SensorStatus
import com.example.arsens.data.asAprilTagCalibrationMarker
import com.example.arsens.data.isAprilTagCalibrationMarker
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
internal fun TransformerMapWorkspace(
    project: Project,
    log: InstallationLog,
    onBack: () -> Unit,
    initialView: TransformerMapView = TransformerMapView.Top,
    onViewChanged: (TransformerMapView) -> Unit = {},
    message: String? = null,
    onPlaceSensorPoint: (MmPosition) -> Unit,
    onMoveSensorPoint: (String, MmPosition, String) -> Unit,
    onPlaceTagPoint: (MmPosition, String) -> Unit,
    onMoveTagPoint: (Int, MmPosition, String) -> Unit,
    sensorControls: @Composable ColumnScope.() -> Unit,
    tagControls: @Composable ColumnScope.() -> Unit,
    onBeginPrepare: () -> Unit,
    nextSensorLabel: String,
    nextTagLabel: String,
    onEditSensor: (String, String, Int, Int?, String) -> String?,
    onEditTag: (Int) -> Unit,
    onDeleteSensor: (String) -> Unit,
    onDeleteTag: (Int) -> Unit,
    onResetPlacement: (String) -> Unit,
    onCamera: (String?) -> Unit,
    overflowItems: List<ArSensMenuItem> = emptyList()
) {
    var selectedView by remember(initialView) { mutableStateOf(initialView) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var mode by remember { mutableStateOf(MapEditMode.Select) }
    var prepareTag by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MapMoveTarget?>(null) }
    var measureStart by remember { mutableStateOf<MapMeasurePoint?>(null) }
    var measureEnd by remember { mutableStateOf<MapMeasurePoint?>(null) }
    var moving by remember { mutableStateOf(false) }
    var draftPoint by remember { mutableStateOf<MapMeasurePoint?>(null) }
    var menu by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Sensor?>(null) }
    var sensorTab by remember { mutableStateOf(true) }
    var search by remember { mutableStateOf("") }
    var showSensors by remember { mutableStateOf(true) }
    var showTags by remember { mutableStateOf(true) }
    val sensors = project.sensors.sortedBy { it.order }
    val tags = project.markers.filter { it.isAprilTagCalibrationMarker() }.sortedBy { it.id }
    val selectedSensor = (selected as? MapMoveTarget.Sensor)?.let { t -> sensors.firstOrNull { it.id == t.id } }
    val selectedTag = (selected as? MapMoveTarget.Tag)?.let { t -> tags.firstOrNull { it.id == t.id } }
    val mapProject = project.copy(
        sensors = if (showSensors) sensors.filter { mapViewForSensor(it, project.dimensionsMm) == selectedView } else emptyList(),
        markers = if (showTags) tags.filter { mapViewForMarker(it) == selectedView } else emptyList()
    )
    fun changeView(view: TransformerMapView) {
        selectedView = view; onViewChanged(view)
        zoom = 1f; pan = Offset.Zero
        measureStart = null; measureEnd = null; moving = false; draftPoint = null
    }
    fun select(target: MapMoveTarget?) {
        selected = target; moving = false; draftPoint = null
        measureStart = null; measureEnd = null
        val view = when (target) {
            is MapMoveTarget.Sensor -> sensors.firstOrNull { it.id == target.id }?.let { mapViewForSensor(it, project.dimensionsMm) }
            is MapMoveTarget.Tag -> tags.firstOrNull { it.id == target.id }?.let(::mapViewForMarker)
            null -> null
        }
        if (view != null && view != selectedView) changeView(view)
    }
    fun selectMode(value: MapEditMode) {
        mode = value; select(null); menu = null
        if (value == MapEditMode.Sensor) onBeginPrepare()
    }
    fun tap(point: MapMeasurePoint) {
        if (mode == MapEditMode.Measure) {
            if (measureStart == null || measureEnd != null) { measureStart = point; measureEnd = null }
            else measureEnd = point
            return
        }
        val target = point.toMoveTarget(project)
        if (target != null) { select(target); return }
        if (mode == MapEditMode.Sensor) {
            val position = point.toBoxPosition(selectedView, project.dimensionsMm)
            if (prepareTag) onPlaceTagPoint(position, selectedView.name) else onPlaceSensorPoint(position)
        }
        select(null)
    }
    val modeLabel = when (mode) {
        MapEditMode.Measure -> "Meten"
        MapEditMode.Sensor -> "Voorbereiden"
        else -> "Selecteren"
    }
    val sequence = if (selected is MapMoveTarget.Tag) tags.map { MapMoveTarget.Tag(it.id) }
        else sensors.map { MapMoveTarget.Sensor(it.id) }
    val index = sequence.indexOf(selected)
    Column(Modifier.fillMaxSize().background(Color(0xFFEFF3F7)).safeDrawingPadding()) {
        ArSensGlassTopBar(title = project.projectName, onBack = onBack,
            modifier = Modifier.padding(8.dp).fillMaxWidth(), dark = false, overflowItems = overflowItems) {
            ArSensCounterPill(project.placementCountLabel, dark = false)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            TransformerMapCanvas(project = mapProject, log = log, selectedView = selectedView,
                mapZoom = zoom, mapPan = pan, measureStart = measureStart, measureEnd = measureEnd,
                selectedTarget = selected, moveMode = moving,
                dragTarget = selected.takeIf { moving }, dragPoint = draftPoint,
                onMoveBegin = {}, onMovePoint = { draftPoint = it }, onMoveCommit = {},
                onMoveCancel = { draftPoint = null }, onTapPoint = ::tap,
                onTransform = { delta, factor -> zoom = (zoom * factor).coerceIn(0.35f, 6f); pan += delta },
                modifier = Modifier.fillMaxSize().testTag("transformer-map"))
            Box(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                OutlinedButton(onClick = { menu = "plane" }, enabled = !moving) { Text(selectedView.label) }
                DropdownMenu(expanded = menu == "plane", onDismissRequest = { menu = null }) {
                    TransformerMapView.entries.forEach { view ->
                        DropdownMenuItem(text = { Text(view.label) }, onClick = { changeView(view); selected = null; menu = null })
                    }
                }
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MapRailButton("Lagen", menu == "layers", enabled = !moving) { menu = "layers" }
                MapRailButton(modeLabel, true, enabled = !moving) { menu = "mode" }
                MapRailButton("Lijst", menu == "list", enabled = !moving) { sensorTab = selected !is MapMoveTarget.Tag; menu = "list" }
                MapRailButton("Fit", false) { zoom = 1f; pan = Offset.Zero }
            }
        }
        MaterialTheme(colorScheme = WorkflowCameraDarkScheme) {
            Surface(color = WorkflowCameraPanel, contentColor = Color.White,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                Column(Modifier.fillMaxWidth().heightIn(max = 245.dp).verticalScroll(rememberScrollState()).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (selectedSensor != null || selectedTag != null) {
                        Text(selectedSensor?.displayName() ?: "Tag ${selectedTag!!.id}", fontWeight = FontWeight.Bold)
                        selectedSensor?.let { sensor ->
                            Text("${sensor.status.label} · radius ${sensor.toleranceMm} mm", fontSize = 13.sp)
                            Text("Doel: ${sensor.positionMm.toReadableMm()}", fontSize = 12.sp)
                            log.results.firstOrNull { it.sensorId == sensor.id }?.let { result ->
                                Text("Gemeten: ${result.measuredPositionMm?.toReadableMm()} · afwijking ${result.distanceErrorMm} mm", fontSize = 12.sp)
                            }
                        }
                        selectedTag?.let { Text("${it.sizeMm} × ${it.sizeMm} mm · ${it.positionMm.toReadableMm()}", fontSize = 12.sp) }
                        if (moving) {
                            Text("Sleep het geselecteerde punt. Alleen Opslaan wijzigt het doel.", fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { moving = false; draftPoint = null }) { Text("Annuleren") }
                                Button(enabled = draftPoint != null, onClick = {
                                    val p = draftPoint!!.toBoxPosition(selectedView, project.dimensionsMm)
                                    when (val t = selected) {
                                        is MapMoveTarget.Sensor -> onMoveSensorPoint(t.id, p, selectedView.name)
                                        is MapMoveTarget.Tag -> onMoveTagPoint(t.id, p, selectedView.name)
                                        null -> Unit
                                    }
                                    moving = false; draftPoint = null
                                }) { Text("Opslaan") }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(enabled = index > 0, onClick = { select(sequence[index - 1]) }) { Text("Vorige") }
                                TextButton(onClick = { sensorTab = selected !is MapMoveTarget.Tag; menu = "list" }) { Text("${index + 1} / ${sequence.size}") }
                                TextButton(enabled = index in 0 until sequence.lastIndex, onClick = { select(sequence[index + 1]) }) { Text("Volgende") }
                                TextButton(onClick = { select(null) }) { Text("Sluit") }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { moving = true }) { Text("Verplaatsen") }
                                OutlinedButton(onClick = {
                                    if (selectedSensor != null) editing = selectedSensor
                                    else { onEditTag(selectedTag!!.id); menu = "tagEdit" }
                                }) { Text("Bewerken") }
                                TextButton(onClick = { menu = "actions" }) { Text("Meer") }
                            }
                        }
                    } else if (mode == MapEditMode.Measure) {
                        Text("Meten | ${selectedView.label}", fontWeight = FontWeight.Bold)
                        val start = measureStart
                        val end = measureEnd
                        Text(if (start != null && end != null) "${start.distanceTo(end).roundToInt()} mm · ${start.componentsTo(end, selectedView)}"
                            else if (start != null) "Tik het tweede punt." else "Tik twee punten om te meten.")
                        TextButton(onClick = { measureStart = null; measureEnd = null }) { Text("Nieuwe meting") }
                    } else if (mode == MapEditMode.Sensor) {
                        Text("Voorbereiden | ${selectedView.label}", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MapRailButton("Sensor", !prepareTag) { prepareTag = false }
                            MapRailButton("Tag", prepareTag) { prepareTag = true }
                            TextButton(onClick = { menu = "prepare" }) { Text("Instellen") }
                        }
                        Text("Volgende: ${if (prepareTag) nextTagLabel else nextSensorLabel}", fontSize = 13.sp)
                        Text("Tik een lege plek op het vlak. Tik een bestaand punt om het te selecteren.", fontSize = 12.sp)
                    } else {
                        Text("Selecteren | ${selectedView.label}", fontWeight = FontWeight.Bold)
                        Text("Tik een sensor of tag. Sleep om de kaart te verschuiven.", fontSize = 13.sp)
                        TextButton(onClick = { onCamera(null) }) { Text("Camera openen") }
                    }
                    message?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
    if (menu != null && menu != "plane") {
        androidx.compose.ui.window.Dialog(onDismissRequest = { menu = null }) {
            WorkflowStlToolPanel(title = when (menu) {
                "mode" -> "Werkwijze"; "list" -> "Sensoren en tags"; "layers" -> "Lagen"
                "actions" -> "Acties"; "tagEdit" -> "Tag bewerken"; else -> "Voorbereiden"
            }, onClose = { menu = null }) {
                when (menu) {
                    "mode" -> listOf("Selecteren" to MapEditMode.Select, "Meten" to MapEditMode.Measure,
                        "Voorbereiden" to MapEditMode.Sensor).forEach { (label, value) ->
                        TextButton(onClick = { selectMode(value) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
                    "layers" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) { Switch(showSensors, { showSensors = it }); Text("Sensoren en doelradius") }
                        Row(verticalAlignment = Alignment.CenterVertically) { Switch(showTags, { showTags = it }); Text("Referentietags") }
                    }
                    "list" -> {
                        Row {
                            TextButton(onClick = { sensorTab = true }) { Text("Sensoren (${sensors.size})") }
                            TextButton(onClick = { sensorTab = false }) { Text("Tags (${tags.size})") }
                        }
                        OutlinedTextField(search, { search = it }, label = { Text("Zoek naam of ID") }, modifier = Modifier.fillMaxWidth())
                        if (sensorTab) sensors.filter { it.displayName().contains(search, true) }.forEach { sensor ->
                            TextButton(onClick = { select(MapMoveTarget.Sensor(sensor.id)); menu = null }, modifier = Modifier.fillMaxWidth()) {
                                Text("${sensor.displayName()} · ${sensor.status.label}", color = workflowStatusColor(sensor.status))
                            }
                        } else tags.filter { it.id.toString().contains(search) }.forEach { tag ->
                            TextButton(onClick = { select(MapMoveTarget.Tag(tag.id)); menu = null }, modifier = Modifier.fillMaxWidth()) {
                                Text("Tag ${tag.id} · ${if (tag.active) "Referentie" else "Uitgesloten"}")
                            }
                        }
                    }
                    "prepare" -> if (prepareTag) tagControls() else sensorControls()
                    "tagEdit" -> tagControls()
                    "actions" -> {
                        selectedSensor?.let { sensor ->
                            Button(onClick = { menu = null; onCamera(sensor.id) }) { Text("Met camera plaatsen") }
                            if (sensor.status != SensorStatus.Pending) TextButton(onClick = { onResetPlacement(sensor.id); menu = null }) { Text("Opnieuw te plaatsen") }
                            TextButton(onClick = { onDeleteSensor(sensor.id); select(null); menu = null }) { Text("Sensor verwijderen") }
                        }
                        selectedTag?.let { tag -> TextButton(onClick = { onDeleteTag(tag.id); select(null); menu = null }) { Text("Tag verwijderen") } }
                    }
                }
            }
        }
    }
    editing?.let { sensor ->
        WorkflowSensorEditDialog(sensor = sensor, onDismiss = { editing = null }, onSave = onEditSensor)
    }
}

@Composable
private fun MapRailButton(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(12.dp),
        color = if (selected) ArSensTeal else WorkflowCameraPanel, contentColor = Color.White) {
        Text(label, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp))
    }
}

internal fun mapViewForSensor(sensor: Sensor, dimensions: MmPosition): TransformerMapView =
    TransformerMapView.entries.firstOrNull { it.name.equals(sensor.side, true) }
        ?: listOf(TransformerMapView.Front to sensor.positionMm.y,
            TransformerMapView.Back to (dimensions.y - sensor.positionMm.y),
            TransformerMapView.Left to sensor.positionMm.x,
            TransformerMapView.Right to (dimensions.x - sensor.positionMm.x),
            TransformerMapView.Top to (dimensions.z - sensor.positionMm.z)).minBy { kotlin.math.abs(it.second) }.first

private fun mapViewForMarker(marker: com.example.arsens.data.Marker): TransformerMapView = when {
    kotlin.math.abs(marker.rotationDeg.x) > 45f -> TransformerMapView.Top
    marker.rotationDeg.z > 135f || marker.rotationDeg.z < -135f -> TransformerMapView.Back
    marker.rotationDeg.z > 45f -> TransformerMapView.Right
    marker.rotationDeg.z < -45f -> TransformerMapView.Left
    else -> TransformerMapView.Front
}

@Composable
private fun TransformerMapCanvas(
    project: Project,
    log: InstallationLog,
    selectedView: TransformerMapView,
    mapZoom: Float,
    mapPan: Offset,
    measureStart: MapMeasurePoint?,
    measureEnd: MapMeasurePoint?,
    highlightEdge: Int? = null,
    selectedTarget: MapMoveTarget? = null,
    moveMode: Boolean = false,
    dragTarget: MapMoveTarget? = null,
    dragPoint: MapMeasurePoint? = null,
    onMoveBegin: (MapMoveTarget) -> Unit = {},
    onMovePoint: (MapMeasurePoint) -> Unit = {},
    onMoveCommit: () -> Unit = {},
    onMoveCancel: () -> Unit = {},
    onTapPoint: (MapMeasurePoint) -> Unit,
    onTransform: (Offset, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val tapHandler by rememberUpdatedState(onTapPoint)
    val moveBegin by rememberUpdatedState(onMoveBegin)
    val movePoint by rememberUpdatedState(onMovePoint)
    val moveCommit by rememberUpdatedState(onMoveCommit)
    val moveCancel by rememberUpdatedState(onMoveCancel)
    val transformHandler by rememberUpdatedState(onTransform)
    val draft by rememberUpdatedState(dragPoint)
    val results = log.results.associateBy { it.sensorId }
    Canvas(
        modifier = modifier
            .background(Color(0xFFF7F9FB))
            .then(
                if (moveMode) {
                    // Verplaats-modus: sleep een sensor/tag rechtstreeks (geen pan/zoom). De sleep pakt
                    // het dichtstbijzijnde punt onder de vinger; zonder treffer gebeurt er niets.
                    Modifier.pointerInput(selectedView, mapZoom, mapPan, project.sensors, project.markers, selectedTarget) {
                        var grabbed = false
                        detectDragGestures(
                            onDragStart = { offset ->
                                val layout = mapLayoutFor(Size(size.width.toFloat(), size.height.toFloat()), selectedView, project.dimensionsMm, mapZoom, mapPan)
                                val target = measurePointFromTap(offset, layout, selectedView, project)?.toMoveTarget(project)
                                val draftScreen = draft?.toScreenPoint(selectedView, layout.origin, layout.mapWidth, layout.mapHeight, project.dimensionsMm)
                                val hitDraft = draftScreen != null && (draftScreen - offset).getDistance() <= 42f
                                grabbed = selectedTarget != null && (target == selectedTarget || hitDraft)
                                if (grabbed) {
                                    moveBegin(selectedTarget!!)
                                    movePoint(freeMapMeasurePoint(offset, layout, selectedView, project))
                                }
                            },
                            onDrag = { change, _ ->
                                if (grabbed) {
                                    change.consume()
                                    val layout = mapLayoutFor(Size(size.width.toFloat(), size.height.toFloat()), selectedView, project.dimensionsMm, mapZoom, mapPan)
                                    movePoint(freeMapMeasurePoint(change.position, layout, selectedView, project))
                                }
                            },
                            onDragEnd = { if (grabbed) { moveCommit(); grabbed = false } },
                            onDragCancel = { if (grabbed) { moveCancel(); grabbed = false } }
                        )
                    }
                } else {
                    Modifier
                        .pointerInput(selectedView, mapZoom, mapPan, project.sensors, project.markers) {
                            detectTapGestures { tap ->
                                val layout = mapLayoutFor(
                                    canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                                    view = selectedView,
                                    dimensions = project.dimensionsMm,
                                    zoom = mapZoom,
                                    pan = mapPan
                                )
                                val tappedPoint = measurePointFromTap(
                                    tap = tap,
                                    layout = layout,
                                    view = selectedView,
                                    project = project
                                ) ?: return@detectTapGestures
                                tapHandler(tappedPoint)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                transformHandler(pan, zoom)
                            }
                        }
                }
            )
    ) {
        drawTransformerMap(
            project = project,
            selectedView = selectedView,
            mapZoom = mapZoom,
            mapPan = mapPan,
            measureStart = measureStart,
            measureEnd = measureEnd,
            highlightEdge = highlightEdge,
            selectedTarget = selectedTarget,
            dragTarget = dragTarget,
            dragPoint = dragPoint,
            results = results
        )
    }
}

private fun DrawScope.drawTransformerMap(
    project: Project,
    selectedView: TransformerMapView,
    mapZoom: Float,
    mapPan: Offset,
    measureStart: MapMeasurePoint?,
    measureEnd: MapMeasurePoint?,
    highlightEdge: Int? = null,
    selectedTarget: MapMoveTarget? = null,
    dragTarget: MapMoveTarget? = null,
    dragPoint: MapMeasurePoint? = null,
    results: Map<String, com.example.arsens.data.InstallationResult>
) {
    val layout = mapLayoutFor(size, selectedView, project.dimensionsMm, mapZoom, mapPan)
    val mapWidth = layout.mapWidth
    val mapHeight = layout.mapHeight
    val origin = layout.origin
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(31, 41, 51)
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    drawRect(
        color = Color.White,
        topLeft = origin,
        size = Size(mapWidth, mapHeight)
    )
    drawRect(
        color = Color(0xFF415466),
        topLeft = origin,
        size = Size(mapWidth, mapHeight),
        style = Stroke(width = 4f)
    )

    repeat(5) { index ->
        val x = origin.x + mapWidth * index / 4f
        drawLine(Color(0xFFD8E0E8), Offset(x, origin.y), Offset(x, origin.y + mapHeight), strokeWidth = 1.5f)
    }
    repeat(4) { index ->
        val y = origin.y + mapHeight * index / 3f
        drawLine(Color(0xFFD8E0E8), Offset(origin.x, y), Offset(origin.x + mapWidth, y), strokeWidth = 1.5f)
    }

    val horizontalLabel = selectedView.horizontalMm(project.dimensionsMm).toMeterLabel()
    val verticalLabel = selectedView.verticalMm(project.dimensionsMm).toMeterLabel()
    drawContext.canvas.nativeCanvas.drawText("0 m", origin.x - 8f, origin.y + mapHeight + 30f, paint)
    drawContext.canvas.nativeCanvas.drawText(
        horizontalLabel,
        origin.x + mapWidth - paint.measureText(horizontalLabel),
        origin.y + mapHeight + 30f,
        paint
    )
    drawContext.canvas.nativeCanvas.drawText(
        verticalLabel,
        origin.x - paint.measureText(verticalLabel) - 8f,
        origin.y + 18f,
        paint
    )

    project.markers
        .filter { it.isAprilTagCalibrationMarker() }
        .map { it.asAprilTagCalibrationMarker(project.dimensionsMm) }
        .forEach { marker ->
            val point = dragPoint
                ?.takeIf { (dragTarget as? MapMoveTarget.Tag)?.id == marker.id }
                ?.toScreenPoint(selectedView, origin, mapWidth, mapHeight, project.dimensionsMm)
                ?: marker.positionMm.toMapPoint(selectedView, origin, mapWidth, mapHeight, project.dimensionsMm)
            val markerColor = if (marker.active) Color(0xFF9C27B0) else Color(0xFF7E8792)
            val half = 10f
            drawRect(
                color = markerColor,
                topLeft = Offset(point.x - half, point.y - half),
                size = Size(half * 2f, half * 2f),
                style = if (marker.active) androidx.compose.ui.graphics.drawscope.Fill else Stroke(width = 3f)
            )
            drawCircle(Color.White, radius = 3.5f, center = point)
            drawContext.canvas.nativeCanvas.drawText("T${marker.id}", point.x + 13f, point.y - 9f, paint)
        }

    project.sensors.forEach { sensor ->
        val expected = dragPoint
            ?.takeIf { (dragTarget as? MapMoveTarget.Sensor)?.id == sensor.id }
            ?.toScreenPoint(selectedView, origin, mapWidth, mapHeight, project.dimensionsMm)
            ?: sensor.positionMm.toMapPoint(selectedView, origin, mapWidth, mapHeight, project.dimensionsMm)
        val result = results[sensor.id]
        val status = result?.status ?: sensor.status
        val color = mapStatusColor(status)

        val radius = sensor.toleranceMm.toFloat() / selectedView.horizontalMm(project.dimensionsMm) * mapWidth
        drawCircle(color.copy(alpha = 0.14f), radius = radius, center = expected)
        drawCircle(color.copy(alpha = 0.55f), radius = radius, center = expected, style = Stroke(2f))
        result?.measuredPositionMm?.takeIf { sensor.status != SensorStatus.Pending }?.let { measured ->
            val actual = measured.toMapPoint(selectedView, origin, mapWidth, mapHeight, project.dimensionsMm)
            drawLine(color, expected, actual, strokeWidth = 2f)
            drawCircle(color, radius = 7f, center = actual)
        }
        drawCircle(color = color, radius = 11f, center = expected)
        drawCircle(
            color = Color.White,
            radius = 11f,
            center = expected,
            style = Stroke(width = 2f)
        )
        drawContext.canvas.nativeCanvas.drawText(sensor.id, expected.x + 13f, expected.y + 8f, paint)
    }

    if (measureStart != null) {
        val startPx = measureStart.toScreenPoint(selectedView, origin, mapWidth, mapHeight, project.dimensionsMm)
        drawCircle(Color(0xFF111827), radius = 8f, center = startPx)
        if (measureEnd != null) {
            val endPx = measureEnd.toScreenPoint(selectedView, origin, mapWidth, mapHeight, project.dimensionsMm)
            val dist = measureStart.distanceTo(measureEnd).roundToInt()
            val dH = kotlin.math.abs(measureStart.horizontalMm - measureEnd.horizontalMm).roundToInt()
            val dV = kotlin.math.abs(measureStart.verticalMm - measureEnd.verticalMm).roundToInt()
            // Alleen bij een schuine meting (beide assen > 0) tekenen we de X- en Y-benen apart. Ligt de
            // meting puur langs één as, dan vallen been en schuine zijde op elkaar → één gecombineerd
            // label "X/Y ### mm" i.p.v. twee waarden die over elkaar heen vallen.
            val diagonal = dH > 0 && dV > 0
            if (diagonal) {
                val corner = Offset(endPx.x, startPx.y)
                val legColor = Color(0xFF2563EB)
                drawLine(legColor, startPx, corner, strokeWidth = 3f)
                drawLine(legColor, corner, endPx, strokeWidth = 3f)
                val legPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(37, 99, 235)
                    textSize = 22f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                drawContext.canvas.nativeCanvas.drawText(
                    "${selectedView.horizontalAxisLabel()} $dH",
                    (startPx.x + corner.x) / 2f - 12f, startPx.y - 8f, legPaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "${selectedView.verticalAxisLabel()} $dV",
                    corner.x + 8f, (corner.y + endPx.y) / 2f + 6f, legPaint
                )
            }
            drawLine(Color(0xFF111827), startPx, endPx, strokeWidth = 4f, cap = StrokeCap.Round)
            drawCircle(Color(0xFF111827), radius = 8f, center = endPx)
            val label = when {
                dH > 0 && dV == 0 -> "${selectedView.horizontalAxisLabel()} $dist mm"
                dV > 0 && dH == 0 -> "${selectedView.verticalAxisLabel()} $dist mm"
                else -> "$dist mm"
            }
            val mid = Offset((startPx.x + endPx.x) / 2f, (startPx.y + endPx.y) / 2f)
            drawContext.canvas.nativeCanvas.drawText(label, mid.x + 10f, mid.y - 10f, paint)
        }
    }

    // Aangetikte rand oplichten + loodlijn van het meetpunt ernaartoe (interactieve rand-afstand).
    if (highlightEdge != null && measureStart != null) {
        val sp = measureStart.toScreenPoint(selectedView, origin, mapWidth, mapHeight, project.dimensionsMm)
        val right = origin.x + mapWidth
        val bottom = origin.y + mapHeight
        val accent = Color(0xFF2563EB)
        val (e0, e1, foot) = when (highlightEdge) {
            0 -> Triple(Offset(origin.x, origin.y), Offset(origin.x, bottom), Offset(origin.x, sp.y))
            1 -> Triple(Offset(right, origin.y), Offset(right, bottom), Offset(right, sp.y))
            2 -> Triple(Offset(origin.x, bottom), Offset(right, bottom), Offset(sp.x, bottom))
            else -> Triple(Offset(origin.x, origin.y), Offset(right, origin.y), Offset(sp.x, origin.y))
        }
        drawLine(accent, e0, e1, strokeWidth = 5f)
        drawLine(accent, sp, foot, strokeWidth = 3f)
        drawCircle(accent, radius = 6f, center = foot)
    }

    if (selectedTarget != null) {
        val selectedPosition = when (selectedTarget) {
            is MapMoveTarget.Sensor -> project.sensors.firstOrNull { it.id == selectedTarget.id }?.positionMm
            is MapMoveTarget.Tag -> project.markers
                .firstOrNull { it.id == selectedTarget.id && it.isAprilTagCalibrationMarker() }
                ?.positionMm
        }
        if (selectedPosition != null) {
            val point = selectedPosition.toMapPoint(selectedView, origin, mapWidth, mapHeight, project.dimensionsMm)
            drawCircle(Color(0xFFFFC107), radius = 20f, center = point, style = Stroke(width = 4f))
        }
    }

    // Gesleept deel: blauwe ring op de live positie onder de vinger.
    if (dragTarget != null && dragPoint != null) {
        val p = dragPoint.toScreenPoint(selectedView, origin, mapWidth, mapHeight, project.dimensionsMm)
        drawCircle(Color(0xFF2563EB), radius = 20f, center = p, style = Stroke(width = 4f))
    }
}

private fun MmPosition.toMapPoint(origin: Offset, width: Float, height: Float, dimensionsMm: MmPosition): Offset {
    val maxX = dimensionsMm.x.coerceAtLeast(1)
    val maxY = dimensionsMm.y.coerceAtLeast(1)
    val xRatio = x.coerceIn(0, maxX) / maxX.toFloat()
    val yRatio = y.coerceIn(0, maxY) / maxY.toFloat()
    return Offset(
        x = origin.x + xRatio * width,
        y = origin.y + yRatio * height
    )
}

enum class TransformerMapView(val label: String) {
    Top("Boven"),
    Front("Voor"),
    Back("Achter"),
    Left("Links"),
    Right("Rechts");

    fun horizontalMm(dimensions: MmPosition): Int =
        when (this) {
            Top, Front, Back -> dimensions.x
            Left, Right -> dimensions.y
        }.coerceAtLeast(1)

    fun verticalMm(dimensions: MmPosition): Int =
        when (this) {
            Top -> dimensions.y
            Front, Back, Left, Right -> dimensions.z
        }.coerceAtLeast(1)

    fun aspectRatio(dimensions: MmPosition): Float =
        (horizontalMm(dimensions).toFloat() / verticalMm(dimensions).toFloat())
            .coerceIn(0.1f, 10.0f)
}

/** De box-as die in dit aanzicht horizontaal in het vlak ligt — voor de Δ-uitsplitsing van een meting
 *  (de twee zijden die je in het echt apart afmeet). Sluit aan bij de 3D-uitlezing (ΔX/ΔY/ΔZ). */
internal fun TransformerMapView.horizontalAxisLabel(): String = when (this) {
    TransformerMapView.Top, TransformerMapView.Front, TransformerMapView.Back -> "X"
    TransformerMapView.Left, TransformerMapView.Right -> "Y"
}

/** De box-as die in dit aanzicht verticaal in het vlak ligt. */
internal fun TransformerMapView.verticalAxisLabel(): String = when (this) {
    TransformerMapView.Top -> "Y"
    TransformerMapView.Front, TransformerMapView.Back, TransformerMapView.Left, TransformerMapView.Right -> "Z"
}

private data class MapLayout(val origin: Offset, val mapWidth: Float, val mapHeight: Float)

internal data class MapMeasurePoint(
    val label: String,
    val horizontalMm: Double,
    val verticalMm: Double,
    val target: MapMoveTarget? = null
) {
    fun distanceTo(other: MapMeasurePoint): Double =
        hypot(horizontalMm - other.horizontalMm, verticalMm - other.verticalMm)
}

/** Δ per vlak-as tussen twee meetpunten ("ΔX 1200 · ΔY 800") — de twee zijden die je in het echt apart
 *  afmeet (eerst horizontaal, dan verticaal); de directe afstand is dan de schuine zijde (Pythagoras). */
private fun MapMeasurePoint.componentsTo(other: MapMeasurePoint, view: TransformerMapView): String {
    val dh = kotlin.math.abs(horizontalMm - other.horizontalMm).roundToInt()
    val dv = kotlin.math.abs(verticalMm - other.verticalMm).roundToInt()
    return "Δ${view.horizontalAxisLabel()} $dh · Δ${view.verticalAxisLabel()} $dv"
}

internal fun MapMeasurePoint.toMoveTarget(project: Project): MapMoveTarget? = when (val item = target) {
    is MapMoveTarget.Sensor -> item.takeIf { project.sensors.any { it.id == item.id } }
    is MapMoveTarget.Tag -> item.takeIf { project.markers.any { it.id == item.id && it.isAprilTagCalibrationMarker() } }
    null -> null
}

private enum class MapEditMode { Select, Measure, Sensor }

internal sealed class MapMoveTarget(open val label: String) {
    data class Tag(val id: Int) : MapMoveTarget("Tag $id")
    data class Sensor(val id: String) : MapMoveTarget("Sensor $id")
}

private fun mapLayoutFor(
    canvasSize: Size,
    view: TransformerMapView,
    dimensions: MmPosition,
    zoom: Float,
    pan: Offset
): MapLayout {
    val padding = 44f
    val availableWidth = (canvasSize.width - padding * 2f).coerceAtLeast(1f)
    val availableHeight = (canvasSize.height - padding * 2f).coerceAtLeast(1f)
    val mapAspectRatio = view.aspectRatio(dimensions)
    val availableAspectRatio = availableWidth / availableHeight
    val baseWidth: Float
    val baseHeight: Float
    if (availableAspectRatio > mapAspectRatio) {
        baseHeight = availableHeight
        baseWidth = baseHeight * mapAspectRatio
    } else {
        baseWidth = availableWidth
        baseHeight = baseWidth / mapAspectRatio
    }
    val mapWidth = (baseWidth * zoom).coerceAtLeast(1f)
    val mapHeight = (baseHeight * zoom).coerceAtLeast(1f)
    return MapLayout(
        origin = Offset(
            x = (canvasSize.width - mapWidth) / 2f + pan.x,
            y = (canvasSize.height - mapHeight) / 2f + pan.y
        ),
        mapWidth = mapWidth,
        mapHeight = mapHeight
    )
}

private fun MmPosition.toMapMeasurePoint(
    view: TransformerMapView,
    dimensions: MmPosition,
    label: String
): MapMeasurePoint {
    val horizontal = when (view) {
        TransformerMapView.Top -> x
        TransformerMapView.Front -> x
        TransformerMapView.Back -> dimensions.x - x
        TransformerMapView.Left -> dimensions.y - y
        TransformerMapView.Right -> y
    }.coerceIn(0, view.horizontalMm(dimensions))
    val vertical = when (view) {
        TransformerMapView.Top -> y
        TransformerMapView.Front, TransformerMapView.Back, TransformerMapView.Left, TransformerMapView.Right -> z
    }.coerceIn(0, view.verticalMm(dimensions))
    return MapMeasurePoint(label, horizontal.toDouble(), vertical.toDouble())
}

private fun MapMeasurePoint.toBoxPosition(
    view: TransformerMapView,
    dimensions: MmPosition
): MmPosition {
    val horizontal = horizontalMm.roundToInt().coerceIn(0, view.horizontalMm(dimensions))
    val vertical = verticalMm.roundToInt().coerceIn(0, view.verticalMm(dimensions))
    return when (view) {
        TransformerMapView.Top -> MmPosition(
            x = horizontal,
            y = vertical,
            z = dimensions.z
        )
        TransformerMapView.Front -> MmPosition(
            x = horizontal,
            y = 0,
            z = vertical
        )
        TransformerMapView.Back -> MmPosition(
            x = dimensions.x - horizontal,
            y = dimensions.y,
            z = vertical
        )
        TransformerMapView.Left -> MmPosition(
            x = 0,
            y = dimensions.y - horizontal,
            z = vertical
        )
        TransformerMapView.Right -> MmPosition(
            x = dimensions.x,
            y = horizontal,
            z = vertical
        )
    }
}

private fun MmPosition.toMapPoint(
    view: TransformerMapView,
    origin: Offset,
    width: Float,
    height: Float,
    dimensionsMm: MmPosition
): Offset =
    toMapMeasurePoint(view, dimensionsMm, label = "").toScreenPoint(view, origin, width, height, dimensionsMm)

private fun MapMeasurePoint.toScreenPoint(
    view: TransformerMapView,
    origin: Offset,
    width: Float,
    height: Float,
    dimensionsMm: MmPosition
): Offset {
    val horizontalRatio = (horizontalMm / view.horizontalMm(dimensionsMm)).toFloat().coerceIn(0f, 1f)
    val verticalRatio = (verticalMm / view.verticalMm(dimensionsMm)).toFloat().coerceIn(0f, 1f)
    val screenVerticalRatio = 1f - verticalRatio
    return Offset(
        x = origin.x + horizontalRatio * width,
        y = origin.y + screenVerticalRatio * height
    )
}

/** Vrij punt onder de vinger (geen snap naar bestaande punten) — gebruikt tijdens het slepen. */
private fun freeMapMeasurePoint(
    tap: Offset,
    layout: MapLayout,
    view: TransformerMapView,
    project: Project
): MapMeasurePoint {
    val horizontalRatio = ((tap.x - layout.origin.x) / layout.mapWidth).coerceIn(0f, 1f)
    val screenVerticalRatio = ((tap.y - layout.origin.y) / layout.mapHeight).coerceIn(0f, 1f)
    val verticalRatio = 1f - screenVerticalRatio
    return MapMeasurePoint(
        label = "punt",
        horizontalMm = (horizontalRatio * view.horizontalMm(project.dimensionsMm)).toDouble(),
        verticalMm = (verticalRatio * view.verticalMm(project.dimensionsMm)).toDouble()
    )
}

private fun measurePointFromTap(
    tap: Offset,
    layout: MapLayout,
    view: TransformerMapView,
    project: Project
): MapMeasurePoint? {
    val nearest = selectableMapPoints(project, view)
        .minByOrNull { point ->
            val screen = point.toScreenPoint(view, layout.origin, layout.mapWidth, layout.mapHeight, project.dimensionsMm)
            hypot((screen.x - tap.x).toDouble(), (screen.y - tap.y).toDouble())
        }
    if (nearest != null) {
        val screen = nearest.toScreenPoint(view, layout.origin, layout.mapWidth, layout.mapHeight, project.dimensionsMm)
        val distancePx = hypot((screen.x - tap.x).toDouble(), (screen.y - tap.y).toDouble())
        if (distancePx <= 42.0) return nearest
    }

    val horizontalRatio = ((tap.x - layout.origin.x) / layout.mapWidth).coerceIn(0f, 1f)
    val screenVerticalRatio = ((tap.y - layout.origin.y) / layout.mapHeight).coerceIn(0f, 1f)
    if (tap.x < layout.origin.x || tap.x > layout.origin.x + layout.mapWidth ||
        tap.y < layout.origin.y || tap.y > layout.origin.y + layout.mapHeight
    ) {
        return null
    }
    val verticalRatio = 1f - screenVerticalRatio
    return MapMeasurePoint(
        label = "punt",
        horizontalMm = horizontalRatio * view.horizontalMm(project.dimensionsMm).toDouble(),
        verticalMm = verticalRatio * view.verticalMm(project.dimensionsMm).toDouble()
    )
}

internal fun selectableMapPoints(project: Project, view: TransformerMapView): List<MapMeasurePoint> {
    val dimensions = project.dimensionsMm
    val corners = listOf(
        MapMeasurePoint("hoek", 0.0, 0.0),
        MapMeasurePoint("hoek", view.horizontalMm(dimensions).toDouble(), 0.0),
        MapMeasurePoint("hoek", 0.0, view.verticalMm(dimensions).toDouble()),
        MapMeasurePoint("hoek", view.horizontalMm(dimensions).toDouble(), view.verticalMm(dimensions).toDouble())
    )
    val tags = project.markers
        .filter { it.isAprilTagCalibrationMarker() }
        .map { marker -> marker.positionMm.toMapMeasurePoint(view, dimensions, "T${marker.id}").copy(target = MapMoveTarget.Tag(marker.id)) }
    val sensors = project.sensors.map { sensor ->
        sensor.positionMm.toMapMeasurePoint(view, dimensions, sensor.id).copy(target = MapMoveTarget.Sensor(sensor.id))
    }
    return corners + tags + sensors
}

private fun MmPosition.toDimensionLabelMeters(): String =
    "${x.toMeterLabel()} x ${y.toMeterLabel()} x ${z.toMeterLabel()}"

private fun Int.toMeterLabel(): String {
    if (this % 1000 == 0) return "${this / 1000}m"
    val meters = String.format(Locale.US, "%.2f", this / 1000.0)
        .trimEnd('0')
        .trimEnd('.')
    return "${meters}m"
}

private fun mapStatusColor(status: SensorStatus): Color =
    when (status) {
        SensorStatus.Pending -> Color(0xFFFFB020)
        SensorStatus.Ok -> Color(0xFF1B8F3A)
        SensorStatus.Fail -> Color(0xFFD32F2F)
    }
