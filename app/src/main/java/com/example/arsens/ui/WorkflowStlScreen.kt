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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import com.example.arsens.data.StlPartRole
import com.example.arsens.data.estimateCoreBounds
import com.example.arsens.data.rotatedBoundsOfBox
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
internal fun WorkflowStlScreen(state: WorkflowAppState) {
    val scope = rememberCoroutineScope()
    // Meerdere documenten tegelijk: een assembly bestaat uit losse STL-delen (tank, kern, deksel…).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) state.addStlModelsThenAskPlace(uris, scope)
    }
    // Sleutel op de modellenlijst: na een import worden de nieuwe meshes alsnog (off-thread) geparsed.
    LaunchedEffect(state.project.stlModels) { state.ensureStlMeshesLoaded() }
    // Diagnose links/rechts-audit: log de rauwe box-positie van elke opgeslagen tag bij binnenkomst.
    LaunchedEffect(state.savedAprilTags) { logArSensRawTagCheck(state.project) }
    var viewMode by remember { mutableStateOf(StlViewMode.Free) }
    var renderWireframe by remember { mutableStateOf(false) }
    // Alleen voor draadmodel: doorkijk (alle randen) vs verdekt (hidden-line via z-buffer).
    var renderSeeThrough by remember { mutableStateOf(true) }
    var resetSignal by remember { mutableStateOf(0) }
    var openTool by remember { mutableStateOf<String?>(null) }
    var selectedModelId by remember { mutableStateOf<String?>(null) }
    // Toont de berekende wandbox (waar "Lijn uit op tank" de afmetingen uit haalt). De vroegere
    // "vulling %" is eruit — die werd niet gebruikt; de preview tekent altijd vol (100%).
    var showWallBox by remember { mutableStateOf(false) }
    var showStlModels by remember { mutableStateOf(true) }
    var showSensors by remember { mutableStateOf(true) }
    var showMeasuredPoints by remember { mutableStateOf(true) }
    var showAprilTags by remember { mutableStateOf(true) }
    var showFrame by remember { mutableStateOf(true) }
    var showLabels by remember { mutableStateOf(true) }
    // Meet-tool: twee gekozen punten (sensor of tag) → 3D-afstand in mm.
    var measureA by remember { mutableStateOf<StlScenePoint?>(null) }
    var measureB by remember { mutableStateOf<StlScenePoint?>(null) }
    // Aangetikte wand in de meet-overlay (0=Links..5=Top); licht het bijbehorende boxvlak op.
    var highlightWall by remember { mutableStateOf<Int?>(null) }
    // Hernoem-dialoog voor de geselecteerde sensor (id != null → dialoog open).
    var renameSensorId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    fun addMeasurePoint(point: StlScenePoint) {
        highlightWall = null
        if (measureA == null || measureB != null) {
            measureA = point
            measureB = null
        } else {
            measureB = point
        }
    }

    val modelIds = state.project.stlModels.map { it.id }
    LaunchedEffect(modelIds) {
        if (selectedModelId == null || modelIds.none { it == selectedModelId }) {
            selectedModelId = modelIds.firstOrNull()
        }
    }

    val renderModels = state.project.stlModels.mapIndexed { index, model ->
        StlRenderModel(
            mesh = state.stlMeshes[model.fileName] ?: StlMesh.EMPTY,
            scalePercent = model.scalePercent,
            offsetMm = model.offsetMm,
            rotXDeg = model.rotationDeg.x,
            rotYDeg = model.rotationDeg.y,
            rotZDeg = model.rotationDeg.z,
            visible = showStlModels && model.visible,
            colorArgb = stlPartColor(index)
        )
    }
    val scenePoints = buildStlScenePoints(
        state = state,
        showSensors = showSensors,
        showMeasuredPoints = showMeasuredPoints,
        showAprilTags = showAprilTags,
        showLabels = showLabels
    )
    // Wandbox éénmalig (her)berekenen bij gewijzigde modellen/meshes — estimateCoreBounds is O(driehoeken).
    val wallBoxMm = remember(state.project.stlModels, state.stlMeshes) { computeTankWallBoxMm(state) }
    BackHandler(enabled = openTool != null) { openTool = null }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFEFF3F7))
            .safeDrawingPadding()
    ) {
        StlPreview(
            models = renderModels,
            boxDimsMm = if (showFrame) state.project.dimensionsMm else null,
            wallBoxMm = if (showWallBox) wallBoxMm else null,
            points = scenePoints,
            viewMode = viewMode,
            resetSignal = resetSignal,
            wireframe = renderWireframe,
            seeThrough = renderSeeThrough,
            measureA = measureA,
            measureB = measureB,
            highlightWall = highlightWall,
            // Geen tool open → tik een sensor of tag aan om te selecteren/meten (zie de selectie-sheet).
            onPickPoint = if (openTool == null) ::addMeasurePoint else null,
            modifier = Modifier.fillMaxSize()
        )

        // Zelfde top bar als de 2D-weergave: projectnaam, terug, teller-pill en hamburger-menu.
        ArSensGlassTopBar(
            title = state.project.projectName,
            onBack = state::navigateBack,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(12.dp)
                .fillMaxWidth(),
            dark = false,
            overflowItems = workflowTopBarMenuItems(state)
        ) {
            ArSensCounterPill("${state.project.sensors.size} sensoren geplaatst", dark = false)
        }
        WorkflowStlLoadBar(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 16.dp, end = 16.dp, top = 86.dp)
                .fillMaxWidth()
        )

        // Selectie/meet-overlay (bovenin): verschijnt zodra je een sensor/tag aantikt (geen tool nodig).
        // Eén punt → afstanden tot de wanden + opties (⋮); twee punten → onderlinge afstand.
        val selection = measureA?.let { resolveStl3DSelection(state, it) }
        if (openTool == null && measureA != null) {
            WorkflowStlMeasureOverlay(
                measureA = measureA,
                measureB = measureB,
                box = state.project.dimensionsMm,
                highlightWall = highlightWall,
                onWallClick = { highlightWall = it },
                hasOptions = selection != null,
                canRename = selection is Stl3DSelection.Sensor,
                onRename = {
                    (selection as? Stl3DSelection.Sensor)?.let { sel ->
                        state.project.sensors.firstOrNull { it.id == sel.id }?.let { sensor ->
                            state.selectSensorForEdit(sensor)
                            renameText = state.sensorName
                            renameSensorId = sel.id
                        }
                    }
                },
                onDelete = {
                    when (val sel = selection) {
                        is Stl3DSelection.Sensor -> state.project.sensors.firstOrNull { it.id == sel.id }?.let(state::requestRemoveSensor)
                        is Stl3DSelection.Tag -> state.savedAprilTags.firstOrNull { it.id == sel.id }?.let(state::requestDeleteMarker)
                        null -> Unit
                    }
                    measureA = null; measureB = null; highlightWall = null
                },
                onMove = { state.open2DModel(WorkflowScreen.Stl) },
                onDeselect = { measureA = null; measureB = null; highlightWall = null },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp, start = 16.dp, end = 16.dp)
                    .widthIn(max = 360.dp)
            )
        }

        // Statuschip linksonder: alleen als er niets geselecteerd is (anders neemt de selectie-sheet het over).
        if (openTool == null && measureA == null) {
            WorkflowStlStatusChip(
                toolLabel = workflowStlToolTitle(openTool),
                viewMode = viewMode,
                info = "${state.project.stlModels.count { it.visible }}/${state.project.stlModels.size} STL${if (showWallBox) " · wandbox aan" else ""}",
                measureText = stlMeasureStatusText(measureA, measureB),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 74.dp, bottom = 12.dp)
            )
        }

        // Tool-paneel (rechtsonder), zoals in de camera/2D
        if (openTool != null) {
            WorkflowStlToolPanel(
                title = workflowStlToolTitle(openTool),
                onClose = { openTool = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(start = 12.dp, end = 84.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            ) {
                when (openTool) {
                    "kader" -> WorkflowStlKaderPanel(
                        state = state,
                        selectedModelId = selectedModelId,
                        onSelectModel = { selectedModelId = if (selectedModelId == it) null else it },
                        onLoadStl = { picker.launch(arrayOf("*/*")) },
                        onAutoAlign = { state.requestAutoAlignAssembly(scope) },
                        showFrame = showFrame,
                        onShowFrameChange = { showFrame = it },
                        showWallBox = showWallBox,
                        onShowWallBoxChange = { showWallBox = it }
                    )
                    "layers" -> WorkflowStlLayersPanel(
                        showStlModels = showStlModels,
                        onShowStlModelsChange = { showStlModels = it },
                        showSensors = showSensors,
                        onShowSensorsChange = { showSensors = it },
                        showMeasuredPoints = showMeasuredPoints,
                        onShowMeasuredPointsChange = { showMeasuredPoints = it },
                        showAprilTags = showAprilTags,
                        onShowAprilTagsChange = { showAprilTags = it },
                        showLabels = showLabels,
                        onShowLabelsChange = { showLabels = it }
                    )
                    "list" -> WorkflowStlEntityListPanel(
                        state = state,
                        onMeasureFrom = { point ->
                            addMeasurePoint(point)
                            openTool = null
                        }
                    )
                    else -> WorkflowStlViewPanel(
                        viewMode = viewMode,
                        onViewModeChange = { viewMode = it },
                        wireframe = renderWireframe,
                        onWireframeChange = { renderWireframe = it },
                        seeThrough = renderSeeThrough,
                        onSeeThroughChange = { renderSeeThrough = it },
                        onReset = { resetSignal++ }
                    )
                }
            }
        }

        // Rechter rand: icoon-toolknoppen (zelfde stijl als camera/2D)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End
        ) {
            WorkflowCameraToolButton(
                key = "orient",
                selected = openTool == "view",
                onClick = { openTool = if (openTool == "view") null else "view" }
            )
            WorkflowCameraToolButton(
                key = "list",
                selected = openTool == "list",
                onClick = { openTool = if (openTool == "list") null else "list" }
            )
            WorkflowCameraToolButton(
                key = "layers",
                selected = openTool == "layers",
                onClick = { openTool = if (openTool == "layers") null else "layers" }
            )
            WorkflowCameraToolButton(
                key = "fill",
                selected = openTool == "kader",
                onClick = { openTool = if (openTool == "kader") null else "kader" }
            )
        }

        // Hernoem-dialoog voor de geselecteerde sensor.
        renameSensorId?.let {
            AlertDialog(
                onDismissRequest = { renameSensorId = null },
                title = { Text("Sensor hernoemen") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Naam") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        state.sensorName = renameText
                        state.saveSensorPoint()
                        renameSensorId = null
                    }) { Text("Opslaan") }
                },
                dismissButton = {
                    TextButton(onClick = { renameSensorId = null }) { Text("Annuleren") }
                }
            )
        }
    }
}

internal fun buildStlScenePoints(
    state: WorkflowAppState,
    showSensors: Boolean = true,
    showMeasuredPoints: Boolean = true,
    showAprilTags: Boolean = true,
    showLabels: Boolean = true
): List<StlScenePoint> {
    val points = mutableListOf<StlScenePoint>()
    val results = state.log.results.associateBy { it.sensorId }
    if (showAprilTags) {
        state.savedAprilTags.forEach { tag ->
            points += StlScenePoint(
                x = tag.positionMm.x.toFloat(),
                y = tag.positionMm.y.toFloat(),
                z = tag.positionMm.z.toFloat(),
                argb = if (tag.active) 0xFF9C27B0.toInt() else 0xFF7E8792.toInt(),
                square = true,
                label = if (showLabels) "T${tag.id}" else ""
            )
        }
    }
    state.project.sensors.forEach { sensor ->
        val status = results[sensor.id]?.status ?: sensor.status
        if (showSensors) {
            points += StlScenePoint(
                x = sensor.positionMm.x.toFloat(),
                y = sensor.positionMm.y.toFloat(),
                z = sensor.positionMm.z.toFloat(),
                argb = sensorStatusArgb(status),
                square = false,
                label = if (showLabels) sensor.id else ""
            )
        }
        if (showMeasuredPoints) {
            results[sensor.id]?.measuredPositionMm?.let { measured ->
                points += StlScenePoint(
                    x = measured.x.toFloat(),
                    y = measured.y.toFloat(),
                    z = measured.z.toFloat(),
                    argb = 0xFF2563EB.toInt(),
                    square = false,
                    label = ""
                )
            }
        }
    }
    return points
}

/** Wat de gebruiker in de 3D-weergave aantikte: een sensor of een tag (of niets bij een los punt). */
internal sealed interface Stl3DSelection {
    data class Sensor(val id: String) : Stl3DSelection
    data class Tag(val id: Int) : Stl3DSelection
}

/** Zoekt bij een aangetikt scènepunt de bijbehorende sensor of tag (exacte positie-match; de
 *  scènepunten zijn immers uit dezelfde posities opgebouwd). Null voor een gemeten/los punt. */
internal fun resolveStl3DSelection(state: WorkflowAppState, p: StlScenePoint): Stl3DSelection? {
    if (p.square) {
        return state.savedAprilTags.firstOrNull {
            it.positionMm.x.toFloat() == p.x && it.positionMm.y.toFloat() == p.y && it.positionMm.z.toFloat() == p.z
        }?.let { Stl3DSelection.Tag(it.id) }
    }
    return state.project.sensors.firstOrNull {
        it.positionMm.x.toFloat() == p.x && it.positionMm.y.toFloat() == p.y && it.positionMm.z.toFloat() == p.z
    }?.let { Stl3DSelection.Sensor(it.id) }
}

/** Berekent de wandbox van de tank in projectframe-mm ([minX,minY,minZ,maxX,maxY,maxZ]) — de
 *  buitenste wandvlakken die "Lijn uit op tank" als afmetingen overneemt. Null als er geen bruikbare
 *  mesh is. Zelfde tankkeuze en transform (s·q + offset) als WorkflowAppState.autoAlignAssembly. */
internal fun computeTankWallBoxMm(state: WorkflowAppState): FloatArray? {
    val withMesh = state.project.stlModels.mapNotNull { m ->
        state.stlMeshes[m.fileName]?.takeIf { !it.isEmpty }?.let { m to it }
    }
    if (withMesh.isEmpty()) return null
    val (model, mesh) = withMesh.firstOrNull { (m, _) -> m.role == StlPartRole.Tank }
        ?: withMesh.firstOrNull { (m, _) -> m.name.contains("tank", ignoreCase = true) }
        ?: withMesh.maxByOrNull { (_, mesh) ->
            (mesh.maxX - mesh.minX).toDouble() * (mesh.maxY - mesh.minY) * (mesh.maxZ - mesh.minZ)
        }
        ?: return null
    val wall = mesh.rotatedBoundsOfBox(
        mesh.estimateCoreBounds(),
        model.rotationDeg.x, model.rotationDeg.y, model.rotationDeg.z
    )
    val s = model.scalePercent / 100f
    val o = model.offsetMm
    return floatArrayOf(
        s * wall[0] + o.x, s * wall[1] + o.y, s * wall[2] + o.z,
        s * wall[3] + o.x, s * wall[4] + o.y, s * wall[5] + o.z
    )
}

internal fun sensorStatusArgb(status: SensorStatus): Int =
    when (status) {
        SensorStatus.Ok -> 0xFF0F766E.toInt()
        SensorStatus.Fail -> 0xFFDC2626.toInt()
        SensorStatus.Pending -> 0xFFF59E0B.toInt()
    }

/** Onderscheidende kleuren per STL-onderdeel (tank, cover, bushings, …). */
internal val stlPartColors = listOf(
    0xFF6E8CA6.toInt(),
    0xFFE0884B.toInt(),
    0xFF5BA66E.toInt(),
    0xFFB066C9.toInt(),
    0xFF4BAFB0.toInt(),
    0xFFD0607A.toInt(),
    0xFFC9A24B.toInt(),
    0xFF8C9AA6.toInt()
)

internal fun stlPartColor(index: Int): Int =
    stlPartColors[((index % stlPartColors.size) + stlPartColors.size) % stlPartColors.size]

internal fun compactCount(value: Int): String =
    when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
        value >= 1_000 -> "${value / 1_000}k"
        else -> value.toString()
    }

/** Laadbalken voor STL-bestanden die nu geparset worden (één rij per bestand, 0–100%).
 *  Verschijnt vanzelf zodra er iets laadt en verdwijnt daarna weer. */
@Composable
internal fun WorkflowStlLoadBar(state: WorkflowAppState, modifier: Modifier = Modifier) {
    val progress = state.stlLoadProgress
    if (progress.isEmpty()) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 5.dp
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            progress.entries.sortedBy { it.key }.forEach { (fileName, fraction) ->
                val displayName = state.project.stlModels.firstOrNull { it.fileName == fileName }?.name
                    ?: fileName
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Laden: $displayName",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(fraction * 100).roundToInt()}%",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun WorkflowStlToolPanel(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = WorkflowCameraPanel,
        // Witte content-kleur: ongekleurde Text-regels (zoals "Tags (N)") erven anders de donkere
        // inkt van het lichte buitenste thema → onleesbaar donker-op-donker in deze glas-sheet.
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 10.dp
    ) {
        // Zelfde donkere glas-sheet als de camera, zodat 3D-weergave en camera één geheel voelen.
        MaterialTheme(colorScheme = WorkflowCameraDarkScheme, typography = MaterialTheme.typography) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { onClose() },
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✕", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

internal fun workflowStlToolTitle(tool: String?): String =
    when (tool) {
        "kader" -> "Box & onderdelen"
        "layers" -> "Lagen"
        "measure" -> "Meten"
        "list" -> "Sensoren & tags"
        else -> "3D weergave"
    }

/** Statusregel van de meet-tool: startpunt of volledig resultaat (3D-afstand + Δ per as). */
internal fun stlMeasureStatusText(a: StlScenePoint?, b: StlScenePoint?): String? {
    if (a == null) return null
    fun name(p: StlScenePoint) = p.label.ifEmpty { "punt" }
    if (b == null) return "Meting start: ${name(a)} — tik het tweede punt."
    val distance = stlMeasureDistanceMm(a, b).roundToInt()
    val dx = kotlin.math.abs(a.x - b.x).roundToInt()
    val dy = kotlin.math.abs(a.y - b.y).roundToInt()
    val dz = kotlin.math.abs(a.z - b.z).roundToInt()
    return "Meting ${name(a)} → ${name(b)}: $distance mm (ΔX $dx · ΔY $dy · ΔZ $dz)"
}

@Composable
internal fun WorkflowStlStatusChip(
    toolLabel: String,
    viewMode: StlViewMode,
    info: String,
    measureText: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(max = 420.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        shadowElevation = 5.dp
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$toolLabel | ${viewMode.label}", fontWeight = FontWeight.Bold)
            Text(
                measureText ?: info,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

/** Loodrechte afstand van een meetpunt tot elke boxwand (mm): de box-coördinaten zijn de offsets. */
internal fun wallOffsetsMm(p: StlScenePoint, box: MmPosition): List<Pair<String, Int>> = listOf(
    "Links" to p.x.roundToInt(),
    "Rechts" to (box.x - p.x).roundToInt(),
    "Voor" to p.y.roundToInt(),
    "Achter" to (box.y - p.y).roundToInt(),
    "Vloer" to p.z.roundToInt(),
    "Top" to (box.z - p.z).roundToInt()
)

/** Doorzichtige, INTERACTIEVE meet-overlay bovenin: één punt → afstand tot de 6 wanden (tik een
 *  waarde → die wand licht op in 3D); twee punten → 3D-afstand. Houdt de meet-sheet klein. */
@Composable
internal fun WorkflowStlMeasureOverlay(
    measureA: StlScenePoint?,
    measureB: StlScenePoint?,
    box: MmPosition,
    highlightWall: Int?,
    onWallClick: (Int?) -> Unit,
    hasOptions: Boolean = false,
    canRename: Boolean = false,
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    onMove: () -> Unit = {},
    onDeselect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (measureA == null) return
    var menuOpen by remember(measureA, measureB) { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = WorkflowCameraPanel.copy(alpha = 0.92f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 8.dp
    ) {
        Column(
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (measureB != null) (stlMeasureStatusText(measureA, measureB) ?: "")
                    else measureA.label.ifEmpty { "Meetpunt" },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (measureB != null) 13.sp else 12.sp,
                    modifier = Modifier.weight(1f)
                )
                // ⋮ opties alleen bij één geselecteerde sensor/tag (niet bij een 2-punts meting).
                if (measureB == null && hasOptions) {
                    Box {
                        Surface(
                            modifier = Modifier.size(28.dp).clickable { menuOpen = true },
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.12f)
                        ) {
                            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                                repeat(3) { i ->
                                    drawCircle(
                                        Color.White,
                                        radius = size.minDimension * 0.11f,
                                        center = Offset(size.width / 2f, size.height * (0.2f + i * 0.3f))
                                    )
                                }
                            }
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (canRename) {
                                DropdownMenuItem(text = { Text("Hernoemen") }, onClick = { menuOpen = false; onRename() })
                            }
                            DropdownMenuItem(text = { Text("Verplaats (2D)") }, onClick = { menuOpen = false; onMove() })
                            DropdownMenuItem(text = { Text("Verwijderen") }, onClick = { menuOpen = false; onDelete() })
                            DropdownMenuItem(text = { Text("Deselecteren") }, onClick = { menuOpen = false; onDeselect() })
                        }
                    }
                }
            }
            if (measureB == null) {
                wallOffsetsMm(measureA, box).chunked(3).forEachIndexed { rowIdx, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEachIndexed { colIdx, (label, mm) ->
                            val wall = rowIdx * 3 + colIdx
                            val selected = wall == highlightWall
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clickable { onWallClick(if (selected) null else wall) }
                                    .background(
                                        if (selected) ArSensBlue.copy(alpha = 0.40f) else Color.Transparent,
                                        RoundedCornerShape(7.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 3.dp)
                            ) {
                                Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
                                Text("$mm", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Text("tik een waarde → wand licht op", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
            }
        }
    }
}

/** Sensor-/taglijst in de STL-weergave, zelfde rijstijl als het rapport en de 2D-zijpanelen.
 *  Een tik op een rij zet er een meetpunt op (en opent de meet-tool). */
@Composable
internal fun WorkflowStlEntityListPanel(
    state: WorkflowAppState,
    onMeasureFrom: (StlScenePoint) -> Unit
) {
    val mapper = state.project.coordinateMapper()
    val tags = state.savedAprilTags.sortedBy { it.id }
    val sensors = state.project.sensors.sortedBy { it.order }
    val results = state.log.results.associateBy { it.sensorId }
    // De links/rechts-diagnostics (rauwe box-check + "Herbouw tag-rotaties") staan nu onder
    // Instellingen › Diagnose, zodat deze lijst alleen de sensoren en tags toont.
    Text(
        "Tik een rij om er een meetpunt op te zetten.",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp
    )
    Text("Tags (${tags.size})", fontWeight = FontWeight.Bold)
    if (tags.isEmpty()) {
        Text("Nog geen tags geplaatst.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
    tags.forEach { tag ->
        WorkflowReportEntityRow(
            name = "Tag ${tag.id.toString().padStart(3, '0')}",
            coords = workflowReportCoords(mapper.boxToOperator(tag.positionMm)),
            plane = markerSurfaceLabel(tag, state.project.dimensionsMm),
            dotColor = if (tag.active) ArSensBlue else Color(0xFF7E8792),
            onClick = {
                onMeasureFrom(
                    StlScenePoint(
                        x = tag.positionMm.x.toFloat(),
                        y = tag.positionMm.y.toFloat(),
                        z = tag.positionMm.z.toFloat(),
                        argb = 0xFF9C27B0.toInt(),
                        square = true,
                        label = "T${tag.id}"
                    )
                )
            }
        )
    }
    HorizontalDivider()
    Text("Sensoren (${sensors.size})", fontWeight = FontWeight.Bold)
    if (sensors.isEmpty()) {
        Text("Nog geen sensoren geplaatst.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
    sensors.forEach { sensor ->
        val status = results[sensor.id]?.status ?: sensor.status
        WorkflowReportEntityRow(
            name = sensor.id.ifBlank { "Sensor ${sensor.order.toString().padStart(3, '0')}" },
            coords = workflowReportCoords(mapper.boxToOperator(sensor.positionMm)),
            plane = sensor.side.ifBlank { "—" },
            dotColor = Color(sensorStatusArgb(status)),
            onClick = {
                onMeasureFrom(
                    StlScenePoint(
                        x = sensor.positionMm.x.toFloat(),
                        y = sensor.positionMm.y.toFloat(),
                        z = sensor.positionMm.z.toFloat(),
                        argb = sensorStatusArgb(status),
                        square = false,
                        label = sensor.id
                    )
                )
            }
        )
    }
}

/**
 * Kader-sheet: alle assembly-bediening op één plek in de 3D-weergave — boxweergave (box-randen +
 * berekende wandbox), trafo-afmetingen (handinvoer/buitenwand + vergrendelen), de onderdelenlijst
 * met per-deel uitlijnen, en het uitlijnen van de hele assembly. Donkere camera-glas-stijl via de
 * `WorkflowSheet*`-bouwblokken, zodat het matcht met de tags-sheet in de camera.
 */
@Composable
internal fun WorkflowStlKaderPanel(
    state: WorkflowAppState,
    selectedModelId: String?,
    onSelectModel: (String) -> Unit,
    onLoadStl: () -> Unit,
    onAutoAlign: () -> Unit,
    showFrame: Boolean,
    onShowFrameChange: (Boolean) -> Unit,
    showWallBox: Boolean,
    onShowWallBoxChange: (Boolean) -> Unit
) {
    val models = state.project.stlModels
    val scope = rememberCoroutineScope()
    WorkflowMessage(state.message)

    // 1. Box — maten, vergrendelen en wat je van de box ziet, bij elkaar.
    WorkflowSheetSectionLabel("Box (mm)")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        WorkflowNumberField("Lengte", state.lengthMm, { state.lengthMm = it }, Modifier.weight(1f))
        WorkflowNumberField("Breedte", state.widthMm, { state.widthMm = it }, Modifier.weight(1f))
        WorkflowNumberField("Hoogte", state.heightMm, { state.heightMm = it }, Modifier.weight(1f))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedButton(onClick = state::applyManualDimensions, modifier = Modifier.weight(1f)) {
            Text("Pas maten toe")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Vergrendel", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
            Switch(checked = state.project.dimensionsLocked, onCheckedChange = state::setDimensionsLocked)
        }
    }
    OutlinedButton(
        onClick = { state.recomputeBoxFromStl(scope) },
        modifier = Modifier.fillMaxWidth().height(44.dp)
    ) {
        Text("Box uit 3D-model")
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StlLayerToggle("Toon box", showFrame) { onShowFrameChange(!showFrame) }
        StlLayerToggle("Toon wandbox", showWallBox) { onShowWallBoxChange(!showWallBox) }
    }

    // 2. Onderdelen — importeren en per onderdeel rol/positie aanpassen.
    WorkflowSheetSectionLabel("Onderdelen (${models.size})")
    OutlinedButton(onClick = onLoadStl, modifier = Modifier.fillMaxWidth().height(46.dp)) {
        Text("Importeer STL's")
    }
    if (models.isEmpty()) {
        Text("Nog geen STL geladen.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    } else {
        Text(
            "Tik een onderdeel om rol en positie aan te passen.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            models.forEachIndexed { index, model ->
                StlModelListRow(
                    state = state,
                    model = model,
                    colorArgb = stlPartColor(index),
                    selected = model.id == selectedModelId,
                    onSelect = { onSelectModel(model.id) }
                )
            }
        }

        // 3. Uitlijnen — alles in één keer op de tank + vloeroffset binnenwerk.
        WorkflowSheetSectionLabel("Uitlijnen")
        WorkflowSheetPlaceButton(
            label = "Lijn alles uit op tank",
            color = ArSensBlue,
            onClick = onAutoAlign
        )
        WorkflowNumberField(
            label = "Vloeroffset binnenwerk (mm)",
            value = state.assemblyFloorOffsetMm,
            onValueChange = { state.assemblyFloorOffsetMm = it },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun StlModelListRow(
    state: WorkflowAppState,
    model: StlModel,
    colorArgb: Int,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val mesh = state.stlMeshes[model.fileName]
    val meshText = mesh?.let { if (it.isEmpty) "geen mesh" else "${compactCount(it.totalTriangleCount)} driehoeken" }
        ?: if (model.visible) "laden…" else "uit — laadt zodra zichtbaar"
    val roleText = "${model.role.label} · $meshText"
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect)
            ) {
                Box(Modifier.size(14.dp).background(Color(colorArgb), RoundedCornerShape(4.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(model.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(roleText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                StlSmallButton(
                    text = if (model.visible) "Aan" else "Uit",
                    selected = model.visible,
                    onClick = { state.toggleStlVisible(model.id) }
                )
                OutlinedButton(
                    onClick = { state.removeStlModel(model.id) },
                    modifier = Modifier.height(38.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Wis")
                }
            }
            if (selected) {
                HorizontalDivider()
                StlModelTransformEditor(state, model)
            }
        }
    }
}

@Composable
internal fun StlModelTransformEditor(state: WorkflowAppState, model: StlModel) {
    val scope = rememberCoroutineScope()
    var scaleText by remember(model.id, model.scalePercent) { mutableStateOf(model.scalePercent.toString()) }
    var offsetX by remember(model.id, model.offsetMm.x) { mutableStateOf(model.offsetMm.x.toString()) }
    var offsetY by remember(model.id, model.offsetMm.y) { mutableStateOf(model.offsetMm.y.toString()) }
    var offsetZ by remember(model.id, model.offsetMm.z) { mutableStateOf(model.offsetMm.z.toString()) }
    var rotX by remember(model.id, model.rotationDeg.x) { mutableStateOf(model.rotationDeg.x.toString()) }
    var rotY by remember(model.id, model.rotationDeg.y) { mutableStateOf(model.rotationDeg.y.toString()) }
    var rotZ by remember(model.id, model.rotationDeg.z) { mutableStateOf(model.rotationDeg.z.toString()) }

    // Werkelijke afmetingen in mm (geroteerde bounding box × schaal) — STL-bestanden van de
    // assembly staan al in mm, dus bij schaal 100% is dit de echte grootte van het onderdeel.
    val mesh = state.stlMeshes[model.fileName]
    if (mesh != null && !mesh.isEmpty) {
        val ext = mesh.rotatedExtents(model.rotationDeg.x, model.rotationDeg.y, model.rotationDeg.z)
        val s = model.scalePercent / 100f
        Text(
            text = "Afmetingen: ${(ext[0] * s).roundToInt()} × ${(ext[1] * s).roundToInt()} × ${(ext[2] * s).roundToInt()} mm" +
                " · ${compactCount(mesh.totalTriangleCount)} driehoeken",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // Lokale (ongedraaide) STL-bbox — laat zien of delen hetzelfde NX-frame delen
        // (gelijke middens) en waarom auto-uitlijnen wel/niet een globale offset gebruikt.
        Text(
            text = "Lokale bbox: X ${mesh.minX.roundToInt()}…${mesh.maxX.roundToInt()}" +
                " · Y ${mesh.minY.roundToInt()}…${mesh.maxY.roundToInt()}" +
                " · Z ${mesh.minZ.roundToInt()}…${mesh.maxZ.roundToInt()} mm",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Rol bepaalt de auto-uitlijnregels en de AR-onderdelengroep.
    Text(
        "Rol van dit deel",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    StlPartRole.entries.toList().chunked(4).forEach { rowRoles ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            rowRoles.forEach { role ->
                StlSmallButton(
                    text = role.label,
                    selected = model.role == role,
                    onClick = { state.setStlRole(model.id, role) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    StlAdjustField(
        label = "Schaal %",
        value = scaleText,
        onValueChange = {
            scaleText = it
            it.toIntOrNull()?.let { value -> state.updateStlScale(model.id, value) }
        },
        minusText = "-10",
        plusText = "+10",
        onMinus = {
            val next = (model.scalePercent - 10).coerceIn(1, 100_000)
            scaleText = next.toString()
            state.updateStlScale(model.id, next)
        },
        onPlus = {
            val next = (model.scalePercent + 10).coerceIn(1, 100_000)
            scaleText = next.toString()
            state.updateStlScale(model.id, next)
        }
    )
    StlOffsetAdjustField("Offset X", offsetX, { offsetX = it; it.toIntOrNull()?.let { value -> state.updateStlOffset(model.id, 'x', value) } }) {
        val next = model.offsetMm.x + it
        offsetX = next.toString()
        state.updateStlOffset(model.id, 'x', next)
    }
    StlOffsetAdjustField("Offset Y", offsetY, { offsetY = it; it.toIntOrNull()?.let { value -> state.updateStlOffset(model.id, 'y', value) } }) {
        val next = model.offsetMm.y + it
        offsetY = next.toString()
        state.updateStlOffset(model.id, 'y', next)
    }
    StlOffsetAdjustField("Offset Z", offsetZ, { offsetZ = it; it.toIntOrNull()?.let { value -> state.updateStlOffset(model.id, 'z', value) } }) {
        val next = model.offsetMm.z + it
        offsetZ = next.toString()
        state.updateStlOffset(model.id, 'z', next)
    }
    StlRotationAdjustField("Rotatie X°", rotX, { rotX = it; it.toIntOrNull()?.let { value -> state.updateStlRotation(model.id, 'x', value) } }) {
        state.updateStlRotation(model.id, 'x', model.rotationDeg.x + it)
    }
    StlRotationAdjustField("Rotatie Y°", rotY, { rotY = it; it.toIntOrNull()?.let { value -> state.updateStlRotation(model.id, 'y', value) } }) {
        state.updateStlRotation(model.id, 'y', model.rotationDeg.y + it)
    }
    StlRotationAdjustField("Rotatie Z°", rotZ, { rotZ = it; it.toIntOrNull()?.let { value -> state.updateStlRotation(model.id, 'z', value) } }) {
        state.updateStlRotation(model.id, 'z', model.rotationDeg.z + it)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { state.centerStlInBox(model.id) }, modifier = Modifier.weight(1f)) {
            Text("Centreer in trafo")
        }
        OutlinedButton(onClick = { state.fitStlInBox(model.id) }, modifier = Modifier.weight(1f)) {
            Text("Passend maken")
        }
    }
    Button(
        onClick = { state.alignPartToTank(model.id, scope) },
        modifier = Modifier.fillMaxWidth().height(46.dp)
    ) {
        Text("Lijn dit deel uit op tank")
    }
}

@Composable
internal fun StlOffsetAdjustField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onStep: (Int) -> Unit
) {
    StlAdjustField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        minusText = "-10",
        plusText = "+10",
        onMinus = { onStep(-10) },
        onPlus = { onStep(10) }
    )
}

@Composable
internal fun StlRotationAdjustField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onStep: (Int) -> Unit
) {
    StlAdjustField(
        label = label,
        value = value,
        onValueChange = onValueChange,
        minusText = "-90",
        plusText = "+90",
        onMinus = { onStep(-90) },
        onPlus = { onStep(90) }
    )
}

@Composable
internal fun StlAdjustField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minusText: String,
    plusText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedButton(onClick = onMinus, modifier = Modifier.height(54.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(minusText)
        }
        WorkflowNumberField(label, value, onValueChange, Modifier.weight(1f))
        OutlinedButton(onClick = onPlus, modifier = Modifier.height(54.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(plusText)
        }
    }
}

@Composable
internal fun WorkflowStlLayersPanel(
    showStlModels: Boolean,
    onShowStlModelsChange: (Boolean) -> Unit,
    showSensors: Boolean,
    onShowSensorsChange: (Boolean) -> Unit,
    showMeasuredPoints: Boolean,
    onShowMeasuredPointsChange: (Boolean) -> Unit,
    showAprilTags: Boolean,
    onShowAprilTagsChange: (Boolean) -> Unit,
    showLabels: Boolean,
    onShowLabelsChange: (Boolean) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StlLayerToggle("STL", showStlModels) { onShowStlModelsChange(!showStlModels) }
        StlLayerToggle("Sensoren", showSensors) { onShowSensorsChange(!showSensors) }
        StlLayerToggle("Gemeten", showMeasuredPoints) { onShowMeasuredPointsChange(!showMeasuredPoints) }
        StlLayerToggle("Tags", showAprilTags) { onShowAprilTagsChange(!showAprilTags) }
        StlLayerToggle("Labels", showLabels) { onShowLabelsChange(!showLabels) }
    }
}

@Composable
internal fun WorkflowStlViewPanel(
    viewMode: StlViewMode,
    onViewModeChange: (StlViewMode) -> Unit,
    wireframe: Boolean,
    onWireframeChange: (Boolean) -> Unit,
    seeThrough: Boolean,
    onSeeThroughChange: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    WorkflowSheetSectionLabel("Aanzicht")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StlViewMode.entries.forEach { mode ->
            StlSmallButton(
                text = mode.label,
                selected = mode == viewMode,
                onClick = { onViewModeChange(mode) }
            )
        }
    }
    WorkflowSheetSectionLabel("Weergave")
    // Drie modi, net als het AR-model: massief, of draadmodel mét doorkijk / zónder (hidden-line).
    val renderChoice = when {
        !wireframe -> 0
        seeThrough -> 1
        else -> 2
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        WorkflowToggleButton(
            selected = renderChoice == 0,
            text = "Massief",
            onClick = { onWireframeChange(false) },
            modifier = Modifier.weight(1f).height(44.dp)
        )
        WorkflowToggleButton(
            selected = renderChoice == 1,
            text = "Draad",
            onClick = { onWireframeChange(true); onSeeThroughChange(true) },
            modifier = Modifier.weight(1f).height(44.dp)
        )
        WorkflowToggleButton(
            selected = renderChoice == 2,
            text = "Verdekt",
            onClick = { onWireframeChange(true); onSeeThroughChange(false) },
            modifier = Modifier.weight(1f).height(44.dp)
        )
    }
    Text(
        "Massief = dichte vlakken · Draad = met doorkijk · Verdekt = zonder doorkijk.",
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 11.sp
    )
    OutlinedButton(onClick = onReset, modifier = Modifier.height(44.dp)) {
        Text("Reset aanzicht")
    }
}

@Composable
internal fun StlLayerToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    StlSmallButton(text = label, selected = selected, onClick = onClick)
}

@Composable
internal fun StlSmallButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    @Suppress("NAME_SHADOWING")
    val modifier = modifier.height(38.dp)
    val padding = PaddingValues(horizontal = 10.dp)
    if (selected) {
        Button(onClick = onClick, modifier = modifier, contentPadding = padding) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = padding) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

