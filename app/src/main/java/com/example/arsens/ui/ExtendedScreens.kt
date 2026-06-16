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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    onViewChanged: ((TransformerMapView) -> Unit)? = null,
    message: String? = null,
    sensorPlacementLabel: String? = null,
    onPlaceSensorPoint: ((MmPosition) -> Unit)? = null,
    onMoveSensorPoint: ((String, MmPosition) -> Unit)? = null,
    tagPlacementLabel: String? = null,
    onPlaceTagPoint: ((MmPosition, String) -> Unit)? = null,
    onMoveTagPoint: ((Int, MmPosition, String) -> Unit)? = null,
    tagControls: (@Composable ColumnScope.() -> Unit)? = null,
    sensorControls: (@Composable ColumnScope.() -> Unit)? = null,
    startControls: (@Composable ColumnScope.() -> Unit)? = null,
    onSelectSensor: ((String) -> Unit)? = null,
    onSelectTag: ((Int) -> Unit)? = null,
    selectControls: (@Composable ColumnScope.() -> Unit)? = null,
    overflowItems: List<ArSensMenuItem> = emptyList()
) {
    var selectedView by remember(initialView) { mutableStateOf(initialView) }
    var mapZoom by remember { mutableStateOf(1f) }
    var mapPan by remember { mutableStateOf(Offset.Zero) }
    var measureStart by remember { mutableStateOf<MapMeasurePoint?>(null) }
    var measureEnd by remember { mutableStateOf<MapMeasurePoint?>(null) }
    // Aangetikte rand in de meet-overlay (0=Links,1=Rechts,2=Onder,3=Boven); licht die rand op.
    var highlightEdge by remember { mutableStateOf<Int?>(null) }
    var selectedMoveTarget by remember { mutableStateOf<MapMoveTarget?>(null) }
    var planeDropdownOpen by remember { mutableStateOf(false) }
    val initialEditMode = when {
        onPlaceTagPoint != null -> MapEditMode.Tag
        onPlaceSensorPoint != null -> MapEditMode.Sensor
        else -> MapEditMode.Measure
    }
    var editMode by remember(onPlaceSensorPoint != null, onPlaceTagPoint != null) { mutableStateOf(initialEditMode) }
    var openMenuKey by remember { mutableStateOf<String?>(null) }
    val hasPlacementMode = onPlaceSensorPoint != null || onPlaceTagPoint != null
    val hasMoveMode = onMoveSensorPoint != null || onMoveTagPoint != null
    val measureText = if (editMode == MapEditMode.Measure) {
        measureStart?.let { start ->
            measureEnd?.let { end ->
                "Meting ${start.label} -> ${end.label}: ${start.distanceTo(end).roundToInt()} mm"
            } ?: "Meting start: ${start.label}"
        }
    } else if (editMode == MapEditMode.Move) {
        selectedMoveTarget?.let { "${it.label} geselecteerd. Tik de nieuwe positie." }
            ?: "Verplaats: tik eerst een bestaande tag of sensor."
    } else if (editMode == MapEditMode.Select) {
        selectedMoveTarget?.let { "${it.label} geselecteerd — bewerk in het paneel." }
            ?: "Selecteer: tik een sensor of tag op de kaart."
    } else {
        null
    }
    fun handleMapTap(point: MapMeasurePoint) {
        when (editMode) {
            MapEditMode.Sensor -> if (onPlaceSensorPoint != null) {
                onPlaceSensorPoint(point.toBoxPosition(selectedView, project.dimensionsMm))
                measureStart = null
                measureEnd = null
                selectedMoveTarget = null
                openMenuKey = null
                return
            }
            MapEditMode.Tag -> if (onPlaceTagPoint != null) {
                onPlaceTagPoint(point.toBoxPosition(selectedView, project.dimensionsMm), selectedView.name)
                measureStart = null
                measureEnd = null
                selectedMoveTarget = null
                openMenuKey = null
                return
            }
            MapEditMode.Move -> {
                val selected = selectedMoveTarget
                if (selected == null) {
                    selectedMoveTarget = point.toMoveTarget(project)
                    measureStart = null
                    measureEnd = null
                    return
                }
                val position = point.toBoxPosition(selectedView, project.dimensionsMm)
                when (selected) {
                    is MapMoveTarget.Sensor -> onMoveSensorPoint?.invoke(selected.id, position)
                    is MapMoveTarget.Tag -> onMoveTagPoint?.invoke(selected.id, position, selectedView.name)
                }
                selectedMoveTarget = null
                measureStart = null
                measureEnd = null
                openMenuKey = null
                return
            }
            MapEditMode.Select -> {
                val target = point.toMoveTarget(project)
                if (target != null) {
                    selectedMoveTarget = target
                    measureStart = null
                    measureEnd = null
                    when (target) {
                        is MapMoveTarget.Sensor -> onSelectSensor?.invoke(target.id)
                        is MapMoveTarget.Tag -> onSelectTag?.invoke(target.id)
                    }
                    openMenuKey = "select"
                }
                return
            }
            MapEditMode.Measure -> Unit
        }
        selectedMoveTarget = null
        highlightEdge = null
        if (measureStart == null || measureEnd != null) {
            measureStart = point
            measureEnd = null
        } else {
            measureEnd = point
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEFF3F7))
            .safeDrawingPadding()
    ) {
        TransformerMapCanvas(
            project = project,
            log = log,
            selectedView = selectedView,
            mapZoom = mapZoom,
            mapPan = mapPan,
            measureStart = measureStart,
            measureEnd = measureEnd,
            highlightEdge = highlightEdge,
            selectedTarget = selectedMoveTarget,
            onTapPoint = ::handleMapTap,
            onTransform = { pan, zoom ->
                mapZoom = (mapZoom * zoom).coerceIn(0.35f, 6f)
                mapPan += pan
            },
            modifier = Modifier.fillMaxSize()
        )
        ArSensGlassTopBar(
            title = project.projectName,
            onBack = onBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .fillMaxWidth(),
            dark = false,
            overflowItems = overflowItems
        ) {
            ArSensCounterPill("${project.sensors.size} sensoren geplaatst", dark = false)
        }
        TransformerMapPlaneDropdown(
            selectedView = selectedView,
            expanded = planeDropdownOpen,
            onExpandedChange = { planeDropdownOpen = it },
            onViewSelected = {
                selectedView = it
                onViewChanged?.invoke(it)
                planeDropdownOpen = false
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 82.dp)
        )
        val activeMeasure = measureStart
        if (editMode == MapEditMode.Measure && activeMeasure != null && measureEnd == null) {
            MapWallOffsetOverlay(
                measureStart = activeMeasure,
                view = selectedView,
                dimensions = project.dimensionsMm,
                highlightEdge = highlightEdge,
                onEdgeClick = { highlightEdge = it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 150.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 360.dp)
            )
        }
        // Eén 2D-layout voor álle modi (rapport, voorbereiden én sensor-setup): de camera-glas
        // tool-rail rechts + donkere tool-sheet. De vroegere lichte onderbalk (mini-trafo +
        // tekstknoppen) is vervallen zodat de 2D-kaart overal hetzelfde oogt als de 3D-weergave.
        run {
            val menus = transformerMapCompactMenus(
                sensorPlacementLabel = sensorPlacementLabel,
                onPlaceSensorPoint = onPlaceSensorPoint,
                sensorControls = sensorControls,
                tagPlacementLabel = tagPlacementLabel,
                onPlaceTagPoint = onPlaceTagPoint,
                tagControls = tagControls,
                startControls = startControls,
                includeMeasureTool = !hasPlacementMode,
                selectControls = selectControls,
                selectedView = selectedView,
                onViewSelected = { selectedView = it; onViewChanged?.invoke(it) },
                canMove = hasMoveMode,
                editMode = editMode,
                onEditModeSelected = {
                    editMode = it
                    selectedMoveTarget = null
                    measureStart = null
                    measureEnd = null
                },
                onReset = {
                    mapZoom = 1f
                    mapPan = Offset.Zero
                    measureStart = null
                    measureEnd = null
                    selectedMoveTarget = null
                }
            )
            val openMenu = menus.firstOrNull { it.key == openMenuKey }
            if (openMenu == null) {
                TransformerMapCompactStatus(
                    mode = editMode,
                    selectedView = selectedView,
                    sensorPlacementLabel = sensorPlacementLabel,
                    tagPlacementLabel = tagPlacementLabel,
                    message = message,
                    measureText = measureText,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 74.dp, bottom = 12.dp)
                )
            }
            if (openMenu != null) {
                // Donkere camera-glas-sheet, identiek aan de 3D-weergave en de camera, zodat de
                // 2D-kaart bij de rest aansluit. De menu-inhoud gebruikt thematische kleuren en
                // schakelt daardoor vanzelf mee naar het donkere schema.
                WorkflowStlToolPanel(
                    title = openMenu.label,
                    onClose = { openMenuKey = null },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 84.dp, bottom = 12.dp)
                        .fillMaxWidth()
                        .widthIn(max = 460.dp)
                ) {
                    openMenu.content(this)
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End
            ) {
                menus.reversed().forEach { menu ->
                    WorkflowCameraToolButton(
                        key = menu.key,
                        selected = openMenuKey == menu.key,
                        onClick = {
                            menu.editMode?.let { editMode = it }
                            openMenuKey = if (openMenuKey == menu.key) null else menu.key
                        }
                    )
                }
            }
        }
    }
}

private data class TransformerMapWorkspaceMenu(
    val key: String,
    val label: String,
    val editMode: MapEditMode?,
    val content: @Composable ColumnScope.() -> Unit
)

private fun transformerMapCompactMenus(
    sensorPlacementLabel: String?,
    onPlaceSensorPoint: ((MmPosition) -> Unit)?,
    sensorControls: (@Composable ColumnScope.() -> Unit)?,
    tagPlacementLabel: String?,
    onPlaceTagPoint: ((MmPosition, String) -> Unit)?,
    tagControls: (@Composable ColumnScope.() -> Unit)?,
    startControls: (@Composable ColumnScope.() -> Unit)?,
    includeMeasureTool: Boolean,
    selectControls: (@Composable ColumnScope.() -> Unit)?,
    selectedView: TransformerMapView,
    onViewSelected: (TransformerMapView) -> Unit,
    canMove: Boolean,
    editMode: MapEditMode,
    onEditModeSelected: (MapEditMode) -> Unit,
    onReset: () -> Unit
): List<TransformerMapWorkspaceMenu> {
    val menus = mutableListOf<TransformerMapWorkspaceMenu>()
    if (onPlaceTagPoint != null && tagPlacementLabel != null) {
        menus += TransformerMapWorkspaceMenu("tags", tagPlacementLabel, MapEditMode.Tag) {
            Text("Tik op het actieve vlak om een AprilTag te plaatsen.", fontWeight = FontWeight.Bold)
            tagControls?.invoke(this)
        }
    }
    if (onPlaceSensorPoint != null && sensorPlacementLabel != null) {
        menus += TransformerMapWorkspaceMenu("sensor", sensorPlacementLabel, MapEditMode.Sensor) {
            Text("Tik op de kaart om een sensorpunt te plaatsen.", fontWeight = FontWeight.Bold)
            sensorControls?.invoke(this)
        }
    }
    // Eigen icoonknop voor Meten in weergaven zonder plaatsings-tools (bv. de rapport-2D).
    if (includeMeasureTool) {
        menus += TransformerMapWorkspaceMenu("measure", "Meten", MapEditMode.Measure) {
            Text("Tik twee punten op de kaart om de afstand te meten.", fontWeight = FontWeight.Bold)
        }
    }
    // Selecteer-tool: tik een sensor of tag → bewerk/verwijder in het zijpaneel.
    if (selectControls != null) {
        menus += TransformerMapWorkspaceMenu("select", "Selecteer", MapEditMode.Select) {
            selectControls.invoke(this)
        }
    }
    // "Gereedschap"-menu: alleen de tools. De vlak-/aanzichtkiezer is verwijderd hier — die zit al
    // in de plane-selector (de "Vlak"-dropdown bovenaan), dus dit was dubbel. Idem voor Meten:
    // alleen tonen als er geen eigen Meten-toolknop is, anders staat hij dubbel in beeld.
    menus += TransformerMapWorkspaceMenu("view", "Gereedschap", null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!includeMeasureTool) {
                if (editMode == MapEditMode.Measure) {
                    Button(onClick = { onEditModeSelected(MapEditMode.Measure) }, modifier = Modifier.height(48.dp)) {
                        Text("Meten")
                    }
                } else {
                    OutlinedButton(onClick = { onEditModeSelected(MapEditMode.Measure) }, modifier = Modifier.height(48.dp)) {
                        Text("Meten")
                    }
                }
            }
            if (canMove) {
                if (editMode == MapEditMode.Move) {
                    Button(onClick = { onEditModeSelected(MapEditMode.Move) }, modifier = Modifier.height(48.dp)) {
                        Text("Verplaats")
                    }
                } else {
                    OutlinedButton(onClick = { onEditModeSelected(MapEditMode.Move) }, modifier = Modifier.height(48.dp)) {
                        Text("Verplaats")
                    }
                }
            }
            OutlinedButton(onClick = onReset, modifier = Modifier.height(48.dp)) {
                Text("Reset")
            }
        }
    }
    if (startControls != null) {
        menus += TransformerMapWorkspaceMenu("start", "Start", null) {
            startControls.invoke(this)
        }
    }
    return menus
}

@Composable
private fun TransformerMapCompactStatus(
    mode: MapEditMode,
    selectedView: TransformerMapView,
    sensorPlacementLabel: String?,
    tagPlacementLabel: String?,
    message: String?,
    measureText: String?,
    modifier: Modifier = Modifier
) {
    val modeLabel = when (mode) {
        MapEditMode.Tag -> tagPlacementLabel ?: "Tag"
        MapEditMode.Sensor -> sensorPlacementLabel ?: "Sensor"
        MapEditMode.Measure -> "Meten"
        MapEditMode.Move -> "Verplaats"
        MapEditMode.Select -> "Selecteer"
    }
    Surface(
        modifier = modifier.widthIn(max = 420.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        shadowElevation = 5.dp
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$modeLabel | ${selectedView.label}", fontWeight = FontWeight.Bold)
            when {
                measureText != null -> Text(measureText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                !message.isNullOrBlank() -> Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                else -> Text("Tik op de kaart of open een tool rechts.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

// De 2D-kaart gebruikt nu de gedeelde WorkflowCameraToolButton (camera-glas) voor de tool-rail,
// zodat de tool-knoppen identiek zijn aan de camera en de 3D-weergave. Dit icoon blijft alleen nog
// over voor de "Vlak"-dropdown bovenaan.
private fun DrawScope.drawTransformerMapToolIcon(key: String, color: Color) {
    val w = size.width
    val h = size.height
    val center = Offset(w / 2f, h / 2f)
    val stroke = (w * 0.11f).coerceAtLeast(2.2f)
    when (key) {
        "tags" -> {
            drawRect(color, topLeft = Offset(w * 0.18f, h * 0.18f), size = Size(w * 0.64f, h * 0.64f), style = Stroke(width = stroke))
            drawCircle(color, radius = w * 0.11f, center = center)
            drawLine(color, Offset(w * 0.18f, h * 0.36f), Offset(w * 0.36f, h * 0.18f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.64f, h * 0.82f), Offset(w * 0.82f, h * 0.64f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "sensor" -> {
            drawCircle(color, radius = w * 0.24f, center = center, style = Stroke(width = stroke))
            drawCircle(color, radius = w * 0.08f, center = center)
            drawLine(color, Offset(center.x, h * 0.08f), Offset(center.x, h * 0.24f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(center.x, h * 0.76f), Offset(center.x, h * 0.92f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.08f, center.y), Offset(w * 0.24f, center.y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.76f, center.y), Offset(w * 0.92f, center.y), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "view" -> {
            drawRect(color, topLeft = Offset(w * 0.18f, h * 0.26f), size = Size(w * 0.64f, h * 0.48f), style = Stroke(width = stroke))
            drawLine(color, Offset(w * 0.18f, h * 0.42f), Offset(w * 0.82f, h * 0.42f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.40f, h * 0.26f), Offset(w * 0.40f, h * 0.74f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "start" -> {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.34f, h * 0.22f)
                lineTo(w * 0.76f, h * 0.50f)
                lineTo(w * 0.34f, h * 0.78f)
                close()
            }
            drawPath(path, color)
        }
        "measure" -> {
            // Meetlat: diagonale lijn met eindpunten.
            val a = Offset(w * 0.22f, h * 0.78f)
            val b = Offset(w * 0.78f, h * 0.22f)
            drawLine(color, a, b, strokeWidth = stroke, cap = StrokeCap.Round)
            drawCircle(color, radius = w * 0.09f, center = a)
            drawCircle(color, radius = w * 0.09f, center = b)
        }
        "select" -> {
            // Aanwijzer/cursor-pijl.
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.30f, h * 0.20f)
                lineTo(w * 0.30f, h * 0.74f)
                lineTo(w * 0.43f, h * 0.61f)
                lineTo(w * 0.53f, h * 0.82f)
                lineTo(w * 0.62f, h * 0.78f)
                lineTo(w * 0.52f, h * 0.57f)
                lineTo(w * 0.70f, h * 0.55f)
                close()
            }
            drawPath(path, color)
        }
        else -> {
            drawCircle(color, radius = w * 0.28f, center = center, style = Stroke(width = stroke))
        }
    }
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
    onTapPoint: (MapMeasurePoint) -> Unit,
    onTransform: (Offset, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val results = log.results.associateBy { it.sensorId }
    Canvas(
        modifier = modifier
            .background(Color(0xFFF7F9FB))
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
                    onTapPoint(tappedPoint)
                }
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onTransform(pan, zoom)
                }
            }
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
            val point = marker.positionMm.toMapPoint(selectedView, origin, mapWidth, mapHeight, project.dimensionsMm)
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
        val expected = sensor.positionMm.toMapPoint(selectedView, origin, mapWidth, mapHeight, project.dimensionsMm)
        val result = results[sensor.id]
        val status = result?.status ?: sensor.status
        val color = mapStatusColor(status)

        result?.measuredPositionMm?.let { measured ->
            val measuredPoint = measured.toMapPoint(selectedView, origin, mapWidth, mapHeight, project.dimensionsMm)
            drawLine(
                color = Color(0xFF5D6B76),
                start = expected,
                end = measuredPoint,
                strokeWidth = 2f
            )
            drawCircle(
                color = color,
                radius = 15f,
                center = measuredPoint,
                style = Stroke(width = 4f)
            )
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
            drawLine(Color(0xFF111827), startPx, endPx, strokeWidth = 4f, cap = StrokeCap.Round)
            drawCircle(Color(0xFF111827), radius = 8f, center = endPx)
            val mid = Offset((startPx.x + endPx.x) / 2f, (startPx.y + endPx.y) / 2f)
            val label = "${measureStart.distanceTo(measureEnd).roundToInt()} mm"
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

/**
 * Compacte, herbruikbare "mini-trafo" om een aanzicht (vlak) te kiezen. Toont een kruis met
 * Boven in het midden en Voor/Achter/Links/Rechts eromheen — dezelfde ruimtelijke metafoor als
 * bij tag-plaatsing, zodat het overal hetzelfde voelt. Gebruik o.a. in de 2D-kaart i.p.v. losse
 * tekstknoppen.
 */
@Composable
fun TransformerPlaneMiniMap(
    selectedView: TransformerMapView,
    onViewSelected: (TransformerMapView) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val cell = MaterialTheme.colorScheme.surfaceVariant
    val onCell = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier = modifier.pointerInput(selectedView) {
            detectTapGestures { tap ->
                planeMiniRects(Size(size.width.toFloat(), size.height.toFloat()))
                    .firstOrNull { it.second.contains(tap) }
                    ?.let { onViewSelected(it.first) }
            }
        }
    ) {
        val rects = planeMiniRects(size)
        val center = rects.first { it.first == TransformerMapView.Top }.second
        rects.filter { it.first != TransformerMapView.Top }.forEach {
            drawLine(accent.copy(alpha = 0.3f), center.center, it.second.center, strokeWidth = 2f, cap = StrokeCap.Round)
        }
        val textSize = (size.minDimension * 0.12f).coerceIn(15f, 24f)
        rects.forEach { (view, rect) ->
            val selected = view == selectedView
            drawRoundRect(
                color = if (selected) accent else cell,
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(10f, 10f)
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = (if (selected) onAccent else onCell).toArgb()
                textAlign = Paint.Align.CENTER
                this.textSize = textSize
                typeface = Typeface.create(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
            }
            drawContext.canvas.nativeCanvas.drawText(
                view.label,
                rect.center.x,
                rect.center.y + textSize / 3f,
                paint
            )
        }
    }
}

private fun planeMiniRects(size: Size): List<Pair<TransformerMapView, Rect>> {
    val gap = (size.minDimension * 0.05f).coerceIn(5f, 12f)
    val cellWidth = ((size.width - gap * 4f) / 3f).coerceAtLeast(1f)
    val cellHeight = ((size.height - gap * 4f) / 3f).coerceAtLeast(1f)
    fun rect(col: Int, row: Int): Rect {
        val left = gap + col * (cellWidth + gap)
        val top = gap + row * (cellHeight + gap)
        return Rect(left, top, left + cellWidth, top + cellHeight)
    }
    return listOf(
        // Netkaart vanuit de voorkant van de trafo: achter boven, voor onder.
        TransformerMapView.Back to rect(1, 0),
        TransformerMapView.Left to rect(0, 1),
        TransformerMapView.Top to rect(1, 1),
        TransformerMapView.Right to rect(2, 1),
        TransformerMapView.Front to rect(1, 2)
    )
}

@Composable
private fun TransformerMapPlaneDropdown(
    selectedView: TransformerMapView,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onViewSelected: (TransformerMapView) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .height(58.dp)
                .widthIn(min = 178.dp, max = 236.dp)
                .clickable { onExpandedChange(!expanded) },
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0x14101B33)),
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Canvas(Modifier.size(30.dp)) {
                    drawTransformerMapToolIcon("view", ArSensChromeMuted)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text("Vlak", color = ArSensChromeMuted, fontSize = 13.sp)
                    Text(
                        selectedView.label,
                        color = ArSensChromeInk,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Canvas(Modifier.size(18.dp)) {
                    val stroke = 2.1.dp.toPx()
                    val top = if (expanded) size.height * 0.62f else size.height * 0.38f
                    val bottom = if (expanded) size.height * 0.38f else size.height * 0.62f
                    drawLine(
                        ArSensChromeMuted,
                        Offset(size.width * 0.18f, top),
                        Offset(size.width * 0.50f, bottom),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        ArSensChromeMuted,
                        Offset(size.width * 0.82f, top),
                        Offset(size.width * 0.50f, bottom),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        if (expanded) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0x14101B33)),
                shadowElevation = 12.dp
            ) {
                TransformerPlaneMiniMap(
                    selectedView = selectedView,
                    onViewSelected = onViewSelected,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(178.dp)
                )
            }
        }
    }
}

private data class MapLayout(
    val origin: Offset,
    val mapWidth: Float,
    val mapHeight: Float
)

/** Interactieve rand-afstand-overlay voor de 2D-kaart: afstand van het meetpunt tot de 4 randen
 *  van het gekozen vlak; tik een waarde → die rand licht op in de kaart (parallel aan de 3D-overlay). */
@Composable
private fun MapWallOffsetOverlay(
    measureStart: MapMeasurePoint,
    view: TransformerMapView,
    dimensions: MmPosition,
    highlightEdge: Int?,
    onEdgeClick: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val hMax = view.horizontalMm(dimensions)
    val vMax = view.verticalMm(dimensions)
    val edges = listOf(
        "Links" to measureStart.horizontalMm.roundToInt(),
        "Rechts" to (hMax - measureStart.horizontalMm).roundToInt(),
        "Onder" to measureStart.verticalMm.roundToInt(),
        "Boven" to (vMax - measureStart.verticalMm).roundToInt()
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.94f),
        shadowElevation = 6.dp
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Afstand tot rand (mm) — tik om op te lichten", color = ArSensChromeMuted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                edges.forEachIndexed { i, (label, mm) ->
                    val selected = i == highlightEdge
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { onEdgeClick(if (selected) null else i) }
                            .background(
                                if (selected) ArSensBlue.copy(alpha = 0.18f) else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(label, color = ArSensChromeMuted, fontSize = 10.sp)
                        Text("$mm", color = Color(0xFF111827), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private data class MapMeasurePoint(
    val label: String,
    val horizontalMm: Double,
    val verticalMm: Double
) {
    fun distanceTo(other: MapMeasurePoint): Double =
        hypot(horizontalMm - other.horizontalMm, verticalMm - other.verticalMm)
}

private fun MapMeasurePoint.toMoveTarget(project: Project): MapMoveTarget? {
    val tagId = label.removePrefix("T").takeIf { label.startsWith("T") }?.toIntOrNull()
    if (tagId != null && project.markers.any { it.id == tagId && it.isAprilTagCalibrationMarker() }) {
        return MapMoveTarget.Tag(tagId)
    }
    return project.sensors.firstOrNull { it.id == label }?.let { MapMoveTarget.Sensor(it.id) }
}

private enum class MapEditMode {
    Measure,
    Sensor,
    Tag,
    Move,
    Select
}

private sealed class MapMoveTarget(open val label: String) {
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

private fun selectableMapPoints(project: Project, view: TransformerMapView): List<MapMeasurePoint> {
    val dimensions = project.dimensionsMm
    val corners = listOf(
        MapMeasurePoint("hoek", 0.0, 0.0),
        MapMeasurePoint("hoek", view.horizontalMm(dimensions).toDouble(), 0.0),
        MapMeasurePoint("hoek", 0.0, view.verticalMm(dimensions).toDouble()),
        MapMeasurePoint("hoek", view.horizontalMm(dimensions).toDouble(), view.verticalMm(dimensions).toDouble())
    )
    val tags = project.markers
        .filter { it.isAprilTagCalibrationMarker() }
        .map { marker -> marker.positionMm.toMapMeasurePoint(view, dimensions, "T${marker.id}") }
    val sensors = project.sensors.map { sensor ->
        sensor.positionMm.toMapMeasurePoint(view, dimensions, sensor.id)
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
