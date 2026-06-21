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
import com.example.arsens.ar.TagMeasurementAnchor
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
internal fun WorkflowTagsScreen(state: WorkflowAppState) {
    val menus = workflowTagCameraMenus(state)
    // Na on-the-fly tag-opslag: tag op het modeloppervlak zetten (model op tag-diepte in AR).
    // Async, want de meshes kunnen nog aan het parsen zijn en de raycast is zwaar.
    LaunchedEffect(state.pendingTagSnapId) {
        val tagId = state.pendingTagSnapId ?: return@LaunchedEffect
        state.pendingTagSnapId = null
        // quiet: als er geen oppervlak gevonden wordt blijft de "tag opgeslagen"-melding staan.
        state.snapTagToModelSurface(tagId, quiet = true)
    }
    FullScreenCameraWorkflowShell(
        title = state.project.projectName,
        subtitle = "",
        onBack = state::navigateBack,
        topActions = {
            WorkflowCameraStatusPill("${state.project.sensors.size} sensoren geplaatst")
            // Opent de 2D-kaart met ALLE sensoren en tags — ook die waarvan de referentietag
            // nu niet in beeld is (die worden in de live overlay bewust verborgen).
            // Gevulde knoppen in de app-kleur (i.p.v. outline) zodat ze opvallen op het camerabeeld.
        },
        shortcutActions = listOf(
            WorkflowCameraShortcut(
                key = "map2d",
                label = "2D\nweergave",
                onClick = { state.open2DModel(WorkflowScreen.Tags) }
            )
            // Rapport/Sensoren zitten in het hamburger-menu; de AR-knop komt uit het "ar"-menu zelf.
        ),
        topStart = {
            // De vlak-kiezer staat nu in lijn met de bovenbalk (en niet meer in de gezoomde
            // cameralaag), links — zoals gevraagd.
            WorkflowCameraPlaneSelectorPanel(
                selectedPlane = state.selectedTagPlane,
                onPlaneSelected = state::selectTagPlane
            )
        },
        message = state.message,
        primaryActionText = if (state.cameraPlacementTarget == CameraPlacementTarget.Tag) "Tag" else "Sensor",
        onPrimaryAction = {
            if (state.cameraPlacementTarget == CameraPlacementTarget.Tag) {
                state.saveMeasuredTag()
            } else {
                state.saveSensorAtCursor()
            }
        },
        placementTarget = state.cameraPlacementTarget,
        onPlacementTargetChange = state::selectCameraPlacementTarget,
        camera = {
            WorkflowCameraLayers(state)
        },
        requestedMenuKey = state.cameraMenuRequest,
        onMenuRequestConsumed = state::consumeCameraMenuRequest,
        menus = menus,
        positionText = state.operatorText(state.arCursorPosition),
        planeText = state.selectedTagPlane.shortLabel,
        overflowItems = workflowTopBarMenuItems(state),
        cursorOffset = state.arCursorScreenOffset,
        onRecenterCursor = { state.arCursorScreenOffset = Offset.Zero },
        secondaryActionIcon = if (state.mode == WorkMode.OnTheFly) "undo" else null,
        onSecondaryAction = if (state.mode == WorkMode.OnTheFly) state::undoLastPlacedSensor else null
    )
}

internal fun workflowTagCameraMenus(state: WorkflowAppState): List<WorkflowCameraMenu> {
    // Volgorde volgt de werkstroom: eerst een referentietag plaatsen, dan sensoren, daarna de
    // hulpmiddelen (lagen/orientatie). De redundante "Cursor"-tab is weggehaald: de cursorstatus
    // staat al in de bovenbalk én in het Sensor-paneel.
    val menus = mutableListOf<WorkflowCameraMenu>()
    menus += WorkflowCameraMenu("tags", "Tag menu") {
        WorkflowTagSetupPanel(state)
    }
    if (state.mode == WorkMode.OnTheFly) {
        menus += WorkflowCameraMenu("sensor", "Sensoren") {
            WorkflowSensorSheet(state)
        }
    }
    menus += WorkflowCameraMenu("layers", "Lagen") {
        WorkflowOverlayToggles(state)
    }
    menus += WorkflowCameraMenu("ar", "AR model") {
        WorkflowArOptionsSheet(state)
    }
    // Het "Frame"/orientatie-menu is bewust weggehaald uit de camera (zie WorkflowOrientationPanel,
    // dat hoort thuis in de project-setup, niet live in beeld).
    return menus
}

@Composable
internal fun WorkflowTagSetupPanel(state: WorkflowAppState) {
    // Tags-paneel zoals de afbeelding: vlak-kruis + 5x5 grid naast elkaar, handmatige coördinaten,
    // lijst met gerelateerde tags en de blauwe "Plaats tag"-knop. De scanstatus blijft compact
    // bovenaan staan zodat de operator ziet of een tag in beeld is.
    val tags = state.savedAprilTags.sortedBy { it.id }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Bekende tags die nu zichtbaar zijn maar géén bruikbare pose geven: dat is vrijwel altijd
        // een te scherende kijkhoek of een te kleine tag (gedegenereerde solvePnP-fit) — zeg dat
        // i.p.v. het misleidende "nog niet opgeslagen".
        val knownVisibleWithoutPose = state.recentTagIds.filter { id ->
            state.savedAprilTags.any { it.id == id }
        }.takeIf { state.aprilTagResult.poseMarkerIds.isEmpty() }.orEmpty()
        WorkflowStatusChip(
            text = when {
                state.aprilTagResult.errorMessage != null -> state.aprilTagResult.errorMessage ?: ""
                state.tagScanArmed -> "Scanmodus — richt op de tag"
                knownVisibleWithoutPose.isNotEmpty() ->
                    "Tag ${knownVisibleWithoutPose.joinToString(", ")} gezien, maar de kijkhoek is te " +
                        "schuin voor een pose — houd de camera rechter vóór/boven de tag of dichterbij"
                state.recentTagIds.isNotEmpty() && state.aprilTagResult.poseMarkerIds.isEmpty() ->
                    "Zichtbaar: ${state.recentTagIds.take(4).joinToString(", ")} — nog niet opgeslagen"
                state.recentTagIds.isEmpty() -> "Geen tags in beeld"
                else -> "Gezien: ${state.recentTagIds.take(4).joinToString(", ")}"
            },
            status = when {
                state.aprilTagResult.errorMessage != null -> SensorStatus.Fail
                state.aprilTagResult.poseMarkerIds.isNotEmpty() -> SensorStatus.Ok
                else -> SensorStatus.Pending
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkflowSheetSectionLabel("Vlak selecteren")
                WorkflowPlaneCross(
                    selectedPlane = state.selectedTagPlane,
                    onPlaneSelected = state::selectTagPlane,
                    compact = true
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Toggle in dezelfde knopstijl: raster stuurt de coördinaten, óf handmatige invoer
                // blijft staan. Eén tonig — zelfde WorkflowToggleButton als elders.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    WorkflowToggleButton(
                        selected = !state.tagPlacementManual,
                        text = "Grid",
                        onClick = { state.chooseTagPlacementManual(false) },
                        modifier = Modifier.weight(1f).height(40.dp)
                    )
                    WorkflowToggleButton(
                        selected = state.tagPlacementManual,
                        text = "Handmatig",
                        onClick = { state.chooseTagPlacementManual(true) },
                        modifier = Modifier.weight(1f).height(40.dp)
                    )
                }
                if (state.tagPlacementManual) {
                    Text(
                        "Handmatig: vul X/Y/Z hieronder in. Het vlak links bepaalt alleen de rotatie.",
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = 12.sp
                    )
                } else {
                    val currentBox = state.currentTagBoxPositionOrNull()
                    WorkflowTagPlacementGrid(
                        cellSelected = { u, v -> currentBox != null && currentBox == state.tagCellBoxPosition(u, v) },
                        onCellSelected = state::selectTagGridCell
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkflowSheetSectionLabel(
                if (state.tagPlacementManual) "Coördinaten (mm)" else "Coördinaten (mm) — uit grid"
            )
            // Gemeten-punt-keuze: alleen bij handmatige invoer. Grid/AR leveren al een center.
            if (state.tagPlacementManual) {
                Text("Gemeten punt", color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    WorkflowToggleButton(
                        selected = state.tagMeasurementAnchor == TagMeasurementAnchor.Center,
                        text = "Midden",
                        onClick = { state.chooseTagMeasurementAnchor(TagMeasurementAnchor.Center) },
                        modifier = Modifier.weight(1f).height(40.dp)
                    )
                    WorkflowToggleButton(
                        selected = state.tagMeasurementAnchor == TagMeasurementAnchor.BottomLeftEdge,
                        text = "Linksonder rand",
                        onClick = { state.chooseTagMeasurementAnchor(TagMeasurementAnchor.BottomLeftEdge) },
                        modifier = Modifier.weight(1f).height(40.dp)
                    )
                }
                if (state.tagMeasurementAnchor == TagMeasurementAnchor.BottomLeftEdge) {
                    Text(
                        "X/Y/Z = buitenste hoek linksonder van de tag; het opgeslagen midden ligt een halve " +
                            "tag naar binnen langs beide vlak-assen.",
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = 12.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                WorkflowNumberField("X (mm)", state.tagX, { state.tagX = it }, Modifier.weight(1f))
                WorkflowNumberField("Y (mm)", state.tagY, { state.tagY = it }, Modifier.weight(1f))
                WorkflowNumberField("Z (mm)", state.tagZ, { state.tagZ = it }, Modifier.weight(1f))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkflowSheetSectionLabel("Gerelateerde tags (${tags.size})")
            if (tags.isEmpty()) {
                Text("Nog geen tags geplaatst.", color = Color.White.copy(alpha = 0.66f), fontSize = 13.sp)
            } else {
                tags.forEach { tag ->
                    WorkflowSheetEntityRow(
                        name = "Tag ${tag.id.toString().padStart(3, '0')}",
                        plane = markerSurfaceLabel(tag, state.project.dimensionsMm),
                        coords = workflowSheetCoords(tag.positionMm),
                        dotColor = ArSensBlue,
                        onEdit = { state.selectTagForEdit(tag) },
                        onDelete = { state.requestDeleteMarker(tag) }
                    )
                }
            }
        }
        WorkflowSheetPlaceButton(
            label = "Plaats tag",
            color = ArSensBlue,
            onClick = {
                state.selectCameraPlacementTarget(CameraPlacementTarget.Tag)
                state.saveMeasuredTag()
            }
        )
    }
}

/** Schoon N×N raster (zonder labels) dat rechtstreeks een (u,v)-punt op het tagvlak kiest: 7×7 =
 *  het vlak opgedeeld in zesden plus de randen (fijner dan het oude 5×5). [cellSelected] bepaalt
 *  welke cel teal oplicht — gevoed door de huidige coördinaten, zodat handmatig typen de selectie
 *  automatisch loslaat. */
@Composable
internal fun WorkflowTagPlacementGrid(
    cellSelected: (u: Float, v: Float) -> Boolean,
    onCellSelected: (u: Float, v: Float) -> Unit,
    steps: Int = 7
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (row in 0 until steps) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until steps) {
                        // u links→rechts 0..1, v boven→onder 1..0 (zoals het oude raster: bovenrij = top).
                        val u = col.toFloat() / (steps - 1)
                        val v = 1f - row.toFloat() / (steps - 1)
                        val selected = cellSelected(u, v)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clickable { onCellSelected(u, v) },
                            shape = RoundedCornerShape(5.dp),
                            color = if (selected) ArSensTeal else Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, if (selected) ArSensTeal else Color.White.copy(alpha = 0.16f))
                        ) {}
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkflowTagCoordinateFields(state: WorkflowAppState) {
    OutlinedButton(
        onClick = { state.tagCoordinateFieldsExpanded = !state.tagCoordinateFieldsExpanded },
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Text(if (state.tagCoordinateFieldsExpanded) "Verberg coordinaten" else "Coordinaten")
    }
    if (!state.tagCoordinateFieldsExpanded) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            WorkflowNumberField("Meet X mm", state.tagX, { state.tagX = it }, Modifier.weight(1f))
            WorkflowNumberField("Meet Y mm", state.tagY, { state.tagY = it }, Modifier.weight(1f))
            WorkflowNumberField("Meet Z mm", state.tagZ, { state.tagZ = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            WorkflowNumberField("Rot X", state.tagRotX, { state.tagRotX = it }, Modifier.weight(1f))
            WorkflowNumberField("Rot Y", state.tagRotY, { state.tagRotY = it }, Modifier.weight(1f))
            WorkflowNumberField("Rot Z", state.tagRotZ, { state.tagRotZ = it }, Modifier.weight(1f))
        }
        // Tagformaat bepaalt de metrische schaal van de hele pose-keten — snelkeuze van de
        // standaardformaten naast het vrije veld op het tagscherm.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(100, 125, 150).forEach { sizeMm ->
                WorkflowToggleButton(
                    selected = state.tagSize.toIntOrNull() == sizeMm,
                    text = "$sizeMm mm",
                    onClick = { state.tagSize = sizeMm.toString() },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                )
            }
        }
        val halfTag = (state.tagSize.toIntOrNull() ?: 100) / 2
        Text(
            text = "Positie = midden van de tag. Meet je vanaf de rand van de tag, " +
                "tel dan ½ tagformaat ($halfTag mm) bij de gemeten waarde op.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun WorkflowTagPlacementPicker(state: WorkflowAppState) {
    val placement = tagPlacementFor(state.selectedTagPlane, state.selectedTagAnchor, state.project.dimensionsMm)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WorkflowPlaneCross(
            selectedPlane = state.selectedTagPlane,
            onPlaneSelected = state::selectTagPlane
        )
        Text(
            "2. Selecteer positie op vlak (${state.selectedTagPlane.cameraPlaneLabel()})",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        WorkflowTagAnchorGrid(
            selectedAnchor = state.selectedTagAnchor,
            onAnchorSelected = state::selectTagAnchor
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("${placement.plane.cameraPlaneLabel()} | ${placement.anchor.label}", color = Color.White, fontWeight = FontWeight.Bold)
            if (state.tagCoordinateFieldsExpanded) {
                val operatorPosition = state.project.coordinateMapper().boxToOperator(placement.positionMm)
                Text("Meet-XYZ ${operatorPosition.toReadableMm()}", color = Color.White.copy(alpha = 0.72f))
                Text("Box-XYZ ${placement.positionMm.toReadableMm()}", color = Color.White.copy(alpha = 0.72f))
                Text("Rotatie ${placement.rotationDeg.x.toInt()}, ${placement.rotationDeg.y.toInt()}, ${placement.rotationDeg.z.toInt()} deg", color = Color.White.copy(alpha = 0.72f))
            }
        }
    }
}

@Composable
internal fun WorkflowTagAnchorGrid(
    selectedAnchor: TagAnchor,
    onAnchorSelected: (TagAnchor) -> Unit
) {
    val rows = listOf(
        listOf(TagAnchor.TopLeft, TagAnchor.TopQuarterLeft, TagAnchor.TopCenter, TagAnchor.TopQuarterRight, TagAnchor.TopRight),
        listOf(TagAnchor.UpperLeft, TagAnchor.UpperQuarterLeft, TagAnchor.UpperCenter, TagAnchor.UpperQuarterRight, TagAnchor.UpperRight),
        listOf(TagAnchor.MiddleLeft, TagAnchor.MiddleQuarterLeft, TagAnchor.Center, TagAnchor.MiddleQuarterRight, TagAnchor.MiddleRight),
        listOf(TagAnchor.LowerLeft, TagAnchor.LowerQuarterLeft, TagAnchor.LowerCenter, TagAnchor.LowerQuarterRight, TagAnchor.LowerRight),
        listOf(TagAnchor.BottomLeft, TagAnchor.BottomQuarterLeft, TagAnchor.BottomCenter, TagAnchor.BottomQuarterRight, TagAnchor.BottomRight)
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { anchor ->
                    WorkflowToggleButton(
                        selected = selectedAnchor == anchor,
                        text = anchor.shortUiLabel(),
                        onClick = { onAnchorSelected(anchor) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    )
                }
            }
        }
    }
}

internal fun TagPlane.toMapView(): TransformerMapView =
    when (this) {
        TagPlane.Top -> TransformerMapView.Top
        TagPlane.Front -> TransformerMapView.Front
        TagPlane.Back -> TransformerMapView.Back
        TagPlane.Left -> TransformerMapView.Left
        TagPlane.Right -> TransformerMapView.Right
    }

internal fun TagAnchor.shortUiLabel(): String =
    when (this) {
        TagAnchor.BottomLeft -> "LO"
        TagAnchor.BottomQuarterLeft -> "25O"
        TagAnchor.BottomCenter -> "MO"
        TagAnchor.BottomQuarterRight -> "75O"
        TagAnchor.BottomRight -> "RO"
        TagAnchor.LowerLeft -> "L25"
        TagAnchor.LowerQuarterLeft -> "25/25"
        TagAnchor.LowerCenter -> "M25"
        TagAnchor.LowerQuarterRight -> "75/25"
        TagAnchor.LowerRight -> "R25"
        TagAnchor.MiddleLeft -> "LM"
        TagAnchor.MiddleQuarterLeft -> "25M"
        TagAnchor.Center -> "MM"
        TagAnchor.MiddleQuarterRight -> "75M"
        TagAnchor.MiddleRight -> "RM"
        TagAnchor.UpperLeft -> "L75"
        TagAnchor.UpperQuarterLeft -> "25/75"
        TagAnchor.UpperCenter -> "M75"
        TagAnchor.UpperQuarterRight -> "75/75"
        TagAnchor.UpperRight -> "R75"
        TagAnchor.TopLeft -> "LB"
        TagAnchor.TopQuarterLeft -> "25B"
        TagAnchor.TopCenter -> "MB"
        TagAnchor.TopQuarterRight -> "75B"
        TagAnchor.TopRight -> "RB"
    }

@Composable
internal fun WorkflowOriginCornerPicker(state: WorkflowAppState) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pointerInput(state.project.coordinateFrame.originCorner) {
                detectTapGestures { tap ->
                    originCornerRects(Size(size.width.toFloat(), size.height.toFloat()))
                        .firstOrNull { it.rect.contains(tap) }
                        ?.let { state.requestSetOriginCorner(it.origin) }
                }
            }
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        originCornerRects(size).forEach { item ->
            val selected = item.origin == state.project.coordinateFrame.originCorner
            drawRoundRect(
                color = if (selected) Color(0xFF00A7C8) else Color(0xFF374151),
                topLeft = item.rect.topLeft,
                size = item.rect.size,
                cornerRadius = CornerRadius(14f, 14f)
            )
            drawRoundRect(
                color = if (selected) Color.White else Color.White.copy(alpha = 0.38f),
                topLeft = item.rect.topLeft,
                size = item.rect.size,
                cornerRadius = CornerRadius(14f, 14f),
                style = Stroke(width = if (selected) 4f else 2f)
            )
            drawContext.canvas.nativeCanvas.drawText(
                item.origin.shortUiLabel(),
                item.rect.center.x,
                item.rect.center.y + 8f,
                paint
            )
        }
    }
}

internal data class OriginCornerRect(
    val origin: OriginCorner,
    val rect: Rect
)

internal fun originCornerRects(size: Size): List<OriginCornerRect> {
    val gap = (size.minDimension * 0.07f).coerceIn(8f, 18f)
    val cellWidth = ((size.width - gap * 3f) / 2f).coerceAtLeast(1f)
    val cellHeight = ((size.height - gap * 3f) / 2f).coerceAtLeast(1f)
    fun rect(col: Int, row: Int): Rect {
        val left = gap + col * (cellWidth + gap)
        val top = gap + row * (cellHeight + gap)
        return Rect(left, top, left + cellWidth, top + cellHeight)
    }
    return listOf(
        OriginCornerRect(OriginCorner.BackLeftBottom, rect(0, 0)),
        OriginCornerRect(OriginCorner.BackRightBottom, rect(1, 0)),
        OriginCornerRect(OriginCorner.FrontLeftBottom, rect(0, 1)),
        OriginCornerRect(OriginCorner.FrontRightBottom, rect(1, 1))
    )
}

internal fun OriginCorner.shortUiLabel(): String =
    when (this) {
        OriginCorner.FrontLeftBottom -> "Voor links"
        OriginCorner.FrontRightBottom -> "Voor rechts"
        OriginCorner.BackLeftBottom -> "Achter links"
        OriginCorner.BackRightBottom -> "Achter rechts"
    }


@Composable
internal fun WorkflowOrientationPanel(state: WorkflowAppState) {
    val dims = state.project.dimensionsMm
    WorkflowStatusChip(
        text = "${state.overlayAprilTagResult.trackingStatus.label} · frame ${dims.x}×${dims.y}×${dims.z} mm",
        status = when (state.overlayAprilTagResult.trackingStatus) {
            ArTrackingStatus.TagCalibration, ArTrackingStatus.ArCoreTracking -> SensorStatus.Ok
            ArTrackingStatus.DriftPossible, ArTrackingStatus.NeedsRecalibration -> SensorStatus.Pending
            ArTrackingStatus.NoPose -> SensorStatus.Fail
        }
    )
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Nulpunt & assen", fontWeight = FontWeight.Bold)
            Text(
                state.project.coordinateFrame.summary,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            WorkflowOrientationPreview(state.project)
            WorkflowOriginCornerPicker(state)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkflowToggleButton(
                    selected = state.project.coordinateFrame.flipX,
                    text = state.project.coordinateFrame.axisXLabel(),
                    onClick = { state.requestToggleAxisFlip('X') }
                )
                WorkflowToggleButton(
                    selected = state.project.coordinateFrame.flipY,
                    text = state.project.coordinateFrame.axisYLabel(),
                    onClick = { state.requestToggleAxisFlip('Y') }
                )
                WorkflowToggleButton(
                    selected = state.project.coordinateFrame.flipZ,
                    text = state.project.coordinateFrame.axisZLabel(),
                    onClick = { state.requestToggleAxisFlip('Z') }
                )
            }
            if (state.project.markers.isNotEmpty() || state.project.sensors.isNotEmpty()) {
                Text(
                    "Wijzigen past alleen labels en nieuwe invoer aan; bestaande punten blijven staan.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun WorkflowOrientationPreview(project: Project) {
    val mapper = project.coordinateMapper()
    val origin = mapper.originInBox()
    val labels = mapper.axisLabels
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Preview", fontWeight = FontWeight.Bold)
        Text("Origin box: ${origin.toReadableMm()}")
        Text("${labels.xPositive} | ${labels.yPositive} | ${labels.zPositive}")
    }
}

