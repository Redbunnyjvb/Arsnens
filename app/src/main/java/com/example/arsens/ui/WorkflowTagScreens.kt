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
import com.example.arsens.ar.TagAxisReference
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
import com.example.arsens.ar.tagPlaneMirrorsOperatorU
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
        primaryActionText = if (state.cameraPlacementTarget == CameraPlacementTarget.Tag) "Tag" else if (state.planSensorAtCursor) "Plan sensor" else "Sensor zit hier",
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
        state.aprilTagResult.poseDiagnostic?.let {
            Text(it, color = Color(0xFFFFB020), fontSize = 13.sp)
        }

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
        // Vlak selecteren — ongewijzigd component (het knoppen-kruis), nu op een eigen volle rij.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkflowSheetSectionLabel("Vlak selecteren")
            WorkflowPlaneCross(
                selectedPlane = state.selectedTagPlane,
                onPlaneSelected = state::selectTagPlane,
                compact = true
            )
        }
        // Plaatsing methode — 3-weg. Grid en XYZ blijven precies wat ze waren; "Meet vanaf rand" is de
        // nieuwe veldvriendelijke flow. Mapt op de bestaande (manual, edge)-vlaggen.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkflowSheetSectionLabel("Plaatsing methode")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                WorkflowToggleButton(
                    selected = !state.tagPlacementManual,
                    text = "Grid 7×7",
                    onClick = { state.chooseTagPlacementManual(false) },
                    modifier = Modifier.weight(1f).height(40.dp)
                )
                WorkflowToggleButton(
                    selected = state.tagPlacementManual && state.tagEdgeOffsetMode,
                    text = "Vanaf rand",
                    onClick = {
                        state.chooseTagPlacementManual(true)
                        state.chooseTagEdgeOffsetMode(true)
                    },
                    modifier = Modifier.weight(1f).height(40.dp)
                )
                WorkflowToggleButton(
                    selected = state.tagPlacementManual && !state.tagEdgeOffsetMode,
                    text = "XYZ",
                    onClick = {
                        state.chooseTagPlacementManual(true)
                        state.chooseTagEdgeOffsetMode(false)
                    },
                    modifier = Modifier.weight(1f).height(40.dp)
                )
            }
        }
        when {
            // GRID — onveranderd: het 7×7-raster + de X/Y/Z-velden eronder (werkt goed, blijft staan).
            !state.tagPlacementManual -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentBox = state.currentTagBoxPositionOrNull()
                    WorkflowTagPlacementGrid(
                        cellSelected = { u, v -> currentBox != null && currentBox == state.tagCellBoxPosition(u, v) },
                        onCellSelected = state::selectTagGridCell
                    )
                    WorkflowSheetSectionLabel("Coördinaten (mm) — uit grid")
                    WorkflowTagXyzFields(state)
                }
            }
            // MEET VANAF RAND — de nieuwe veldvriendelijke flow (fysieke randnamen + 2D-vlakpreview).
            state.tagEdgeOffsetMode -> WorkflowTagMeasureFromEdges(state)
            // XYZ HANDMATIG / debug — vrije X/Y/Z + operator-view anker + buitenwaartse offset.
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Welk punt van de tag is je X/Y/Z? (scherm-aanzicht, alsof je vóór het vlak staat)",
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = 12.sp
                    )
                    WorkflowTagMeasurementAnchorPad(
                        selected = state.tagMeasurementAnchor,
                        onSelected = state::chooseTagMeasurementAnchor
                    )
                    WorkflowSheetSectionLabel("Coördinaten (mm)")
                    WorkflowTagXyzFields(state)
                    WorkflowNumberField(
                        "Buiten oppervlak (mm)",
                        state.tagOutwardOffset,
                        { state.tagOutwardOffset = it },
                        Modifier.fillMaxWidth()
                    )
                }
            }
        }
        WorkflowTagOffsetPreview(state)
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

/** De drie meet-X/Y/Z-velden (operatorframe) — gedeeld door grid- en XYZ-handmatig-modus. */
@Composable
internal fun WorkflowTagXyzFields(state: WorkflowAppState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        WorkflowNumberField("X (mm)", state.tagX, { state.tagX = it }, Modifier.weight(1f))
        WorkflowNumberField("Y (mm)", state.tagY, { state.tagY = it }, Modifier.weight(1f))
        WorkflowNumberField("Z (mm)", state.tagZ, { state.tagZ = it }, Modifier.weight(1f))
    }
}

/** 3×3 operator-view ankerpad: waar op de tag de operator de X/Y/Z mat (scherm-aanzicht). De
 *  links/rechts-spiegeling op Achter/Links zit in [tagMeasuredPointToCenter]. */
@Composable
internal fun WorkflowTagMeasurementAnchorPad(
    selected: TagMeasurementAnchor,
    onSelected: (TagMeasurementAnchor) -> Unit
) {
    val rows = listOf(
        listOf(TagMeasurementAnchor.TopLeft, TagMeasurementAnchor.TopMiddle, TagMeasurementAnchor.TopRight),
        listOf(TagMeasurementAnchor.LeftMiddle, TagMeasurementAnchor.Center, TagMeasurementAnchor.RightMiddle),
        listOf(TagMeasurementAnchor.BottomLeft, TagMeasurementAnchor.BottomMiddle, TagMeasurementAnchor.BottomRight)
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { anchor ->
                    WorkflowToggleButton(
                        selected = selected == anchor,
                        text = anchor.shortUiLabel(),
                        onClick = { onSelected(anchor) },
                        modifier = Modifier.weight(1f).height(40.dp)
                    )
                }
            }
        }
    }
}

internal fun TagMeasurementAnchor.shortUiLabel(): String = when (this) {
    TagMeasurementAnchor.TopLeft -> "LB"
    TagMeasurementAnchor.TopMiddle -> "B"
    TagMeasurementAnchor.TopRight -> "RB"
    TagMeasurementAnchor.LeftMiddle -> "L"
    TagMeasurementAnchor.Center -> "M"
    TagMeasurementAnchor.RightMiddle -> "R"
    TagMeasurementAnchor.BottomLeft -> "LO"
    TagMeasurementAnchor.BottomMiddle -> "O"
    TagMeasurementAnchor.BottomRight -> "RO"
}

/** Eén fysieke rand-keuze op het gekozen vlak (operator-view label) en de canonieke as-referentie
 *  ([TagAxisReference]) die erbij hoort. */
private data class TagEdgeOption(val label: String, val reference: TagAxisReference)

/** De twee horizontale rand-keuzes (operator-view: eerste = scherm-links) per vlak. */
private fun tagFaceHorizontalEdges(plane: TagPlane): Pair<TagEdgeOption, TagEdgeOption> = when (plane) {
    TagPlane.Front -> TagEdgeOption("Links", TagAxisReference.FromMin) to TagEdgeOption("Rechts", TagAxisReference.FromMax)
    TagPlane.Back -> TagEdgeOption("Links", TagAxisReference.FromMax) to TagEdgeOption("Rechts", TagAxisReference.FromMin)
    TagPlane.Left, TagPlane.Right -> TagEdgeOption("Voor", TagAxisReference.FromMin) to TagEdgeOption("Achter", TagAxisReference.FromMax)
    TagPlane.Top -> TagEdgeOption("Links", TagAxisReference.FromMin) to TagEdgeOption("Rechts", TagAxisReference.FromMax)
}

/** De twee verticale (of diepte, op Boven) rand-keuzes (eerste = onder/voor) per vlak. */
private fun tagFaceVerticalEdges(plane: TagPlane): Pair<TagEdgeOption, TagEdgeOption> = when (plane) {
    TagPlane.Top -> TagEdgeOption("Voor", TagAxisReference.FromMin) to TagEdgeOption("Achter", TagAxisReference.FromMax)
    else -> TagEdgeOption("Onder", TagAxisReference.FromMin) to TagEdgeOption("Boven", TagAxisReference.FromMax)
}

private fun tagFaceHorizontalLabel(plane: TagPlane): String = when (plane) {
    TagPlane.Front, TagPlane.Back, TagPlane.Top -> "Horizontaal (X)"
    TagPlane.Left, TagPlane.Right -> "Horizontaal (Y)"
}

private fun tagFaceVerticalLabel(plane: TagPlane): String =
    if (plane == TagPlane.Top) "Diepte (Y)" else "Verticaal (Z)"

/** Korte oriëntatie-hint per vlak (zoals in de mockup): waar de operator staat. */
internal fun tagFaceOrientationHint(plane: TagPlane): String = when (plane) {
    TagPlane.Front -> "Je staat vóór de transformator en kijkt naar het voorvlak."
    TagPlane.Back -> "Je staat achter de transformator en kijkt naar het achtervlak."
    TagPlane.Left -> "Je kijkt naar de linker zijkant van de transformator."
    TagPlane.Right -> "Je kijkt naar de rechter zijkant van de transformator."
    TagPlane.Top -> "Je kijkt van boven op de transformator."
}

/** De uit de gekozen randen afgeleide tag-hoek (operator-view): scherm-links+onder = Linksonder, enz.
 *  Default voor "Tag hoek"/"Papier hoek" zodat de operator niet apart hoeft te kiezen. */
private fun inferredCornerAnchor(state: WorkflowAppState): TagMeasurementAnchor {
    val plane = state.selectedTagPlane
    val leftIsSelected = state.tagEdgeURef == tagFaceHorizontalEdges(plane).first.reference
    val bottomIsSelected = state.tagEdgeVRef == tagFaceVerticalEdges(plane).first.reference
    return when {
        leftIsSelected && bottomIsSelected -> TagMeasurementAnchor.BottomLeft
        !leftIsSelected && bottomIsSelected -> TagMeasurementAnchor.BottomRight
        leftIsSelected && !bottomIsSelected -> TagMeasurementAnchor.TopLeft
        else -> TagMeasurementAnchor.TopRight
    }
}

/**
 * "Meet vanaf rand" — veldvriendelijke plaatsing: fysieke randnamen + afstanden + 2D-vlakpreview +
 * wat-heb-je-gemeten + optionele papier/buitenwaartse offset. Rekent via dezelfde pure helpers als het
 * opslaan ([tagEdgeOffsetToCenter] → [tagMeasuredPointToCenter] → [applyTagOutwardOffset]); het
 * canonieke frame en de AR-pose-keten blijven ongemoeid.
 */
@Composable
internal fun WorkflowTagMeasureFromEdges(state: WorkflowAppState) {
    val plane = state.selectedTagPlane
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tagFaceOrientationHint(plane), color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp)
        WorkflowTagFacePreview(state)
        val (hFirst, hSecond) = tagFaceHorizontalEdges(plane)
        WorkflowTagEdgeRow(
            title = tagFaceHorizontalLabel(plane),
            first = hFirst,
            second = hSecond,
            selected = state.tagEdgeURef,
            onSelected = state::chooseTagEdgeURef,
            value = state.tagEdgeU,
            onValue = { state.tagEdgeU = it }
        )
        val (vFirst, vSecond) = tagFaceVerticalEdges(plane)
        WorkflowTagEdgeRow(
            title = tagFaceVerticalLabel(plane),
            first = vFirst,
            second = vSecond,
            selected = state.tagEdgeVRef,
            onSelected = state::chooseTagEdgeVRef,
            value = state.tagEdgeV,
            onValue = { state.tagEdgeV = it }
        )
        WorkflowSheetSectionLabel("Wat heb je gemeten?")
        val inferred = inferredCornerAnchor(state)
        val isCenter = state.tagMeasurementAnchor == TagMeasurementAnchor.Center
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            WorkflowToggleButton(
                selected = isCenter,
                text = "Midden",
                onClick = {
                    state.chooseTagMeasurementAnchor(TagMeasurementAnchor.Center)
                    state.chooseTagMeasuredToPaper(false)
                },
                modifier = Modifier.weight(1f).height(40.dp)
            )
            WorkflowToggleButton(
                selected = !isCenter && !state.tagMeasuredToPaper,
                text = "Tag hoek",
                onClick = {
                    if (state.tagMeasurementAnchor == TagMeasurementAnchor.Center) state.chooseTagMeasurementAnchor(inferred)
                    state.chooseTagMeasuredToPaper(false)
                },
                modifier = Modifier.weight(1f).height(40.dp)
            )
            WorkflowToggleButton(
                selected = !isCenter && state.tagMeasuredToPaper,
                text = "Papier",
                onClick = {
                    if (state.tagMeasurementAnchor == TagMeasurementAnchor.Center) state.chooseTagMeasurementAnchor(inferred)
                    state.chooseTagMeasuredToPaper(true)
                },
                modifier = Modifier.weight(1f).height(40.dp)
            )
        }
        if (!isCenter) {
            Text("Kies de hoek op dit vlak (scherm-aanzicht)", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            WorkflowTagMeasurementAnchorPad(
                selected = state.tagMeasurementAnchor,
                onSelected = state::chooseTagMeasurementAnchor
            )
        }
        if (!isCenter && state.tagMeasuredToPaper) {
            WorkflowSheetSectionLabel("Papier / witruimte tot tag")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                WorkflowNumberField("Horizontaal (mm)", state.tagPaperMarginU, { state.tagPaperMarginU = it }, Modifier.weight(1f))
                WorkflowNumberField("Verticaal (mm)", state.tagPaperMarginV, { state.tagPaperMarginV = it }, Modifier.weight(1f))
            }
        }
        WorkflowNumberField(
            "Buiten oppervlak (mm)",
            state.tagOutwardOffset,
            { state.tagOutwardOffset = it },
            Modifier.fillMaxWidth()
        )
    }
}

/** Eén as-rij in "Meet vanaf rand": titel + twee fysieke randknoppen ("Vanaf …") + afstandsveld. */
@Composable
private fun WorkflowTagEdgeRow(
    title: String,
    first: TagEdgeOption,
    second: TagEdgeOption,
    selected: TagAxisReference,
    onSelected: (TagAxisReference) -> Unit,
    value: String,
    onValue: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Vanaf", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            WorkflowToggleButton(
                selected = selected == first.reference,
                text = first.label,
                onClick = { onSelected(first.reference) },
                modifier = Modifier.weight(1f).height(38.dp)
            )
            WorkflowToggleButton(
                selected = selected == second.reference,
                text = second.label,
                onClick = { onSelected(second.reference) },
                modifier = Modifier.weight(1f).height(38.dp)
            )
        }
        WorkflowNumberField("Afstand (mm)", value, onValue, Modifier.fillMaxWidth())
    }
}

private data class TagFaceLabels(val top: String, val bottom: String, val left: String, val right: String)

private fun tagFacePreviewLabels(plane: TagPlane): TagFaceLabels = when (plane) {
    TagPlane.Front -> TagFaceLabels("Boven (Z max)", "Onder (Z 0)", "Links (X 0)", "Rechts (X max)")
    TagPlane.Back -> TagFaceLabels("Boven (Z max)", "Onder (Z 0)", "Links (X max)", "Rechts (X 0)")
    TagPlane.Left -> TagFaceLabels("Boven (Z max)", "Onder (Z 0)", "Achter (Y max)", "Voor (Y 0)")
    TagPlane.Right -> TagFaceLabels("Boven (Z max)", "Onder (Z 0)", "Voor (Y 0)", "Achter (Y max)")
    TagPlane.Top -> TagFaceLabels("Achter (Y max)", "Voor (Y 0)", "Links (X 0)", "Rechts (X max)")
}

/** Eenvoudige 2D-vlakpreview (GEEN camera): rechthoek met fysieke randlabels + een dradenkruis op de
 *  berekende tag-positie. Operator-view, gelijk aan het 7×7-raster en de 2D-kaart. */
@Composable
internal fun WorkflowTagFacePreview(state: WorkflowAppState) {
    val plane = state.selectedTagPlane
    val dims = state.project.dimensionsMm
    val labels = tagFacePreviewLabels(plane)
    val center = state.tagPlacementPreview()?.center
    val uMax = (if (plane == TagPlane.Left || plane == TagPlane.Right) dims.y else dims.x).coerceAtLeast(1)
    val vMax = (if (plane == TagPlane.Top) dims.y else dims.z).coerceAtLeast(1)
    val uCoord = when (plane) {
        TagPlane.Left, TagPlane.Right -> center?.y
        else -> center?.x
    }
    val vCoord = if (plane == TagPlane.Top) center?.y else center?.z
    val uFrac = ((uCoord ?: (uMax / 2)).toFloat() / uMax).coerceIn(0f, 1f)
    val vFrac = ((vCoord ?: (vMax / 2)).toFloat() / vMax).coerceIn(0f, 1f)
    val screenX = if (tagPlaneMirrorsOperatorU(plane)) 1f - uFrac else uFrac
    val screenY = 1f - vFrac
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 54.dp, vertical = 18.dp)
        ) {
            drawRoundRect(
                color = ArSensBlue.copy(alpha = 0.85f),
                cornerRadius = CornerRadius(10f, 10f),
                style = Stroke(width = 2.5f)
            )
            val cx = screenX * size.width
            val cy = screenY * size.height
            drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1.2f)
            drawLine(Color.White.copy(alpha = 0.25f), Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.2f)
            drawCircle(ArSensBlue, radius = 7f, center = Offset(cx, cy))
            drawCircle(Color.White, radius = 3f, center = Offset(cx, cy))
        }
        Text(labels.top, Modifier.align(Alignment.TopCenter), color = ArSensBlue, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        Text(labels.bottom, Modifier.align(Alignment.BottomCenter), color = ArSensBlue, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        Text(labels.left, Modifier.align(Alignment.CenterStart), color = ArSensBlue, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        Text(labels.right, Modifier.align(Alignment.CenterEnd), color = ArSensBlue, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

/** Resultaat-kaart: het berekende tag-MIDDEN als X/Y/Z (box-mm) + de toegepaste offsets. Zelfde
 *  rekenpad als opslaan ([WorkflowAppState.tagPlacementPreview]). */
@Composable
internal fun WorkflowTagOffsetPreview(state: WorkflowAppState) {
    val preview = state.tagPlacementPreview() ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Resultaat — tag-midden", color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp)
        Row(modifier = Modifier.fillMaxWidth()) {
            WorkflowTagResultAxis("X", preview.center.x, Modifier.weight(1f))
            WorkflowTagResultAxis("Y", preview.center.y, Modifier.weight(1f))
            WorkflowTagResultAxis("Z", preview.center.z, Modifier.weight(1f))
        }
        Text(
            "${state.selectedTagPlane.shortLabel} · ${preview.summary}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
        if (!preview.inPlane) {
            Text(
                "Let op: het in-vlak midden valt buiten dit vlak — controleer de waarden.",
                color = Color(0xFFE6A23C),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun WorkflowTagResultAxis(axis: String, valueMm: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(axis, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        Text("$valueMm mm", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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

