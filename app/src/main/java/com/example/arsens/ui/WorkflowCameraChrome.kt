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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
internal fun WorkflowSheetSectionLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.82f),
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    )
}

internal fun workflowSheetCoords(p: MmPosition): String =
    "X: ${p.x / 10} cm   Y: ${p.y / 10} cm   Z: ${p.z / 10} cm"

/** Lijstregel voor een sensor of tag in de camera-sheets: stip + naam + vlak + coördinaten,
 *  met bewerk- en verwijder-knop — zoals phones 3 en 4. */
@Composable
internal fun WorkflowSheetEntityRow(
    name: String,
    plane: String,
    coords: String,
    dotColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(10.dp).background(dotColor, RoundedCornerShape(50)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Plane: $plane", color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp)
                Text(coords, color = Color.White.copy(alpha = 0.66f), fontSize = 12.sp)
            }
            WorkflowSheetIconButton("edit", onEdit)
            WorkflowSheetIconButton("trash", onDelete)
        }
    }
}

@Composable
internal fun WorkflowSheetIconButton(iconKey: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(38.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        color = Color.White.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Canvas(Modifier.fillMaxSize().padding(9.dp)) {
            drawWorkflowCameraIcon(iconKey, Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
internal fun WorkflowSheetPlaceButton(
    label: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = color.copy(alpha = 0.40f)
        )
    ) {
        Canvas(Modifier.size(20.dp)) {
            val s = size.width
            drawLine(Color.White, Offset(s * 0.5f, s * 0.16f), Offset(s * 0.5f, s * 0.84f), strokeWidth = s * 0.12f, cap = StrokeCap.Round)
            drawLine(Color.White, Offset(s * 0.16f, s * 0.5f), Offset(s * 0.84f, s * 0.5f), strokeWidth = s * 0.12f, cap = StrokeCap.Round)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

/** Vaste inhoud van het 3-puntjes (hamburger) menu in de top bar: snelle navigatie naar de
 *  hoofdschermen + projectacties. Overal hetzelfde zodat je vanaf elk scherm kunt springen. */
internal fun workflowTopBarMenuItems(state: WorkflowAppState): List<ArSensMenuItem> = listOf(
    ArSensMenuItem("2D weergave") { state.open2DModel(WorkflowScreen.Report) },
    ArSensMenuItem("3D weergave") { state.navigateTo(WorkflowScreen.Stl) },
    ArSensMenuItem("Rapport") { state.navigateTo(WorkflowScreen.Report) },
    ArSensMenuItem("Sensoren") { state.navigateTo(WorkflowScreen.Sensors) },
    ArSensMenuItem(isDivider = true),
    ArSensMenuItem("Exporteren…") { state.showExportDialog = true },
    ArSensMenuItem("Instellingen") { state.navigateTo(WorkflowScreen.Settings) },
    ArSensMenuItem("Projecten") { state.go(WorkflowScreen.ProjectPicker) }
)

@Composable
internal fun WorkflowShell(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    topActions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    overflowItems: List<ArSensMenuItem> = emptyList(),
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    // Zelfde top bar-opbouw als de camera (terug-pijl, titel, acties, 3-puntjes), maar de lichte
    // variant op de lichte werkschermen — zoals gevraagd: de camera-top bar door de hele app.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        ArSensGlassTopBar(
            title = title,
            onBack = onBack,
            dark = false,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
            overflowItems = overflowItems,
            actions = topActions
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (subtitle.isNotBlank()) {
                item {
                    Text(
                        subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            content()
        }
    }
}

@Composable
internal fun FullScreenCameraWorkflowShell(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    topActions: @Composable () -> Unit,
    shortcutActions: List<WorkflowCameraShortcut> = emptyList(),
    topStart: (@Composable () -> Unit)? = null,
    message: String?,
    primaryActionText: String,
    onPrimaryAction: () -> Unit,
    placementTarget: CameraPlacementTarget? = null,
    onPlacementTargetChange: ((CameraPlacementTarget) -> Unit)? = null,
    camera: @Composable BoxScope.() -> Unit,
    requestedMenuKey: String?,
    onMenuRequestConsumed: () -> Unit,
    menus: List<WorkflowCameraMenu>,
    positionText: String? = null,
    planeText: String? = null,
    overflowItems: List<ArSensMenuItem> = emptyList(),
    cursorOffset: Offset = Offset.Zero,
    onRecenterCursor: (() -> Unit)? = null,
    secondaryActionIcon: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    var openMenuKey by remember { mutableStateOf<String?>(null) }
    val openMenu = menus.firstOrNull { it.key == openMenuKey }
    val planeSelectorOpen = openMenuKey == "__plane_selector"
    // Puur visuele zoom: schaalt camerabeeld én overlays samen rond het midden (de cursor).
    // De tag-detectie blijft op het volledige camerabeeld draaien (zie ArCoreCameraPanel),
    // dus inzoomen helpt mikken zonder het scanbereik te verkleinen.
    var cameraZoom by remember { mutableStateOf(1f) }
    LaunchedEffect(requestedMenuKey) {
        if (!requestedMenuKey.isNullOrBlank()) {
            openMenuKey = requestedMenuKey
            onMenuRequestConsumed()
        }
    }
    BackHandler(enabled = openMenuKey != null) {
        openMenuKey = null
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = cameraZoom
                    scaleY = cameraZoom
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        cameraZoom = (cameraZoom * zoom).coerceIn(1f, 3f)
                    }
                }
        ) {
            camera()
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ArSensGlassTopBar(
                title = title,
                onBack = onBack,
                dark = true,
                overflowItems = overflowItems
            ) {
                topActions()
            }
            if (subtitle.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = WorkflowCameraPanelButton,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = 0.84f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        // De vlak-/paneelkiezer staat nu als bovenste knop ("Paneel selector") in de linker
        // werkbalk en klapt uit als bottom-sheet (zie hieronder), niet meer als chip midden-boven.
        if (!message.isNullOrBlank()) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(start = 84.dp, end = 72.dp, bottom = 74.dp)
                .widthIn(max = 420.dp)
            ) {
                Text(message, modifier = Modifier.padding(10.dp), fontSize = 13.sp)
            }
        }
        if (openMenu != null) {
            val tallPanel = openMenu.key == "tags"
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = WorkflowCameraPanel,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    // Smaller + ingesprongen, zodat de linker werkbalk zichtbaar blijft (er niet
                    // meer doorheen valt).
                    .padding(start = 84.dp, end = 12.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            ) {
                MaterialTheme(
                    colorScheme = WorkflowCameraDarkScheme,
                    typography = MaterialTheme.typography
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Vaste header met sluit-X — blijft bovenaan staan, scrollt niet mee.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                openMenu.label,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable { openMenuKey = null },
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White.copy(alpha = 0.12f)
                            ) {
                                Canvas(Modifier.fillMaxSize().padding(9.dp)) {
                                    drawWorkflowCameraIcon("close", Color.White)
                                }
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = if (tallPanel) 520.dp else 340.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            openMenu.content.invoke(this)
                        }
                    }
                }
            }
        }
        // "Paneel selector" klapt uit als klein zwevend paneel náást de knop (niet als bottom-sheet),
        // compact zoals het vlak-kruis in het Tags-menu.
        if (topStart != null && planeSelectorOpen) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = WorkflowCameraPanel,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(start = 76.dp, top = 112.dp)
                    .width(212.dp)
            ) {
                MaterialTheme(
                    colorScheme = WorkflowCameraDarkScheme,
                    typography = MaterialTheme.typography
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        topStart.invoke()
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .safeDrawingPadding()
                .padding(start = 10.dp, top = 112.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (topStart != null) {
                WorkflowCameraToolButton(
                    key = "plane",
                    label = "Paneel\nselector",
                    selected = planeSelectorOpen,
                    onClick = {
                        openMenuKey = if (planeSelectorOpen) null else "__plane_selector"
                    }
                )
            }
            shortcutActions.firstOrNull { it.key == "map2d" }?.let { shortcut ->
                WorkflowCameraToolButton(
                    key = shortcut.iconKey,
                    label = shortcut.label,
                    selected = false,
                    onClick = shortcut.onClick
                )
            }
            listOf("layers", "sensor", "tags", "cursor", "models", "edit", "fill").forEach { preferredKey ->
                menus.firstOrNull { it.key == preferredKey }?.let { menu ->
                    WorkflowCameraToolButton(
                        key = menu.key,
                        label = cameraToolLabel(menu.key, menu.label),
                        selected = openMenuKey == menu.key,
                        onClick = {
                            openMenuKey = if (openMenuKey == menu.key) null else menu.key
                        }
                    )
                }
            }
            menus
                .filter { menu -> menu.key !in listOf("layers", "sensor", "tags", "cursor", "models", "edit", "fill") }
                .forEach { menu ->
                    WorkflowCameraToolButton(
                        key = menu.key,
                        label = cameraToolLabel(menu.key, menu.label),
                        selected = openMenuKey == menu.key,
                        onClick = {
                            openMenuKey = if (openMenuKey == menu.key) null else menu.key
                        }
                    )
                }
            shortcutActions
                .filter { it.key != "map2d" && it.key != "report" }
                .forEach { shortcut ->
                    WorkflowCameraToolButton(
                        key = shortcut.iconKey,
                        label = shortcut.label,
                        selected = false,
                        onClick = shortcut.onClick
                    )
                }
            shortcutActions.firstOrNull { it.key == "report" }?.let { shortcut ->
                WorkflowCameraToolButton(
                    key = shortcut.iconKey,
                    label = shortcut.label,
                    selected = false,
                    onClick = shortcut.onClick
                )
            }
        }
        if (openMenuKey == null && !positionText.isNullOrBlank()) {
            WorkflowCameraPositionCard(
                positionText = positionText,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(
                        start = 86.dp,
                        end = 86.dp,
                        bottom = if (placementTarget != null) 154.dp else 94.dp
                    )
                    .widthIn(max = 360.dp)
            )
        }
        if (openMenuKey == null) {
            WorkflowCameraPrimaryButton(
                label = primaryActionText,
                onClick = onPrimaryAction,
                placementTarget = placementTarget,
                onPlacementTargetChange = onPlacementTargetChange,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 18.dp)
            )
        }
        if (openMenuKey == null && secondaryActionIcon != null && onSecondaryAction != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .safeDrawingPadding()
                    .padding(start = 12.dp, bottom = 18.dp)
                    .size(54.dp)
                    .clickable(onClick = onSecondaryAction),
                shape = RoundedCornerShape(14.dp),
                color = WorkflowCameraPanelSoft,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
                Canvas(Modifier.fillMaxSize().padding(12.dp)) {
                    drawWorkflowCameraIcon(secondaryActionIcon, Color.White)
                }
            }
        }
        if (openMenuKey == null && cursorOffset != Offset.Zero && onRecenterCursor != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding()
                    .padding(end = 12.dp, bottom = 18.dp)
                    .height(54.dp)
                    .clickable(onClick = onRecenterCursor),
                shape = RoundedCornerShape(14.dp),
                color = WorkflowCameraPanelButton,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Canvas(Modifier.size(22.dp)) { drawWorkflowCameraIcon("cursor", Color.White) }
                    Text("Centreer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        if (cameraZoom > 1.02f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .safeDrawingPadding()
                    .padding(start = 10.dp, bottom = 12.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.52f)
            ) {
                Text(
                    text = "${String.format(Locale.US, "%.1f", cameraZoom)}×",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
internal fun WorkflowCameraToolButton(
    key: String,
    label: String? = null,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) {
        ArSensBlue.copy(alpha = 0.82f)
    } else {
        WorkflowCameraPanelSoft
    }
    val iconColor = Color.White
    Surface(
        modifier = Modifier
            .width(58.dp)
            .height(if (label == null) 54.dp else 66.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = background,
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (selected) 0.30f else 0.16f)),
        shadowElevation = if (selected) 7.dp else 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(Modifier.size(24.dp)) {
                drawWorkflowCameraIcon(key, iconColor)
            }
            if (label != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    label,
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun WorkflowCameraStatusPill(text: String) {
    ArSensCounterPill(text = text, dark = true)
}

@Composable
internal fun WorkflowCameraPositionCard(
    positionText: String,
    modifier: Modifier = Modifier
) {
    // Alleen de live X/Y/Z-coördinaten boven de trigger; de "Plane:"-regel is verwijderd
    // (het actieve vlak staat al op de "Paneel selector"-knop).
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = WorkflowCameraPanelButton,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        shadowElevation = 6.dp
    ) {
        Text(
            positionText.replace(",", "  |  "),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

internal fun cameraToolLabel(key: String, fallback: String): String =
    when (key) {
        "sensor" -> "Sensoren"
        "tags" -> "Tags"
        "layers" -> "Lagen"
        "cursor" -> "Cursor"
        "models" -> "Models"
        "edit" -> "Bewerk"
        "fill" -> "Vulling"
        "orient" -> "View"
        else -> fallback
    }

@Composable
internal fun WorkflowPlacementTargetSwitch(
    selected: CameraPlacementTarget,
    onSelected: (CameraPlacementTarget) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WorkflowCameraPanelButton,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CameraPlacementTarget.entries.forEach { target ->
                val active = target == selected
                Surface(
                    modifier = Modifier
                        .height(32.dp)
                        .width(76.dp)
                        .clickable { onSelected(target) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) {
                        if (target == CameraPlacementTarget.Tag) ArSensBlue else ArSensTeal
                    } else {
                        Color.White.copy(alpha = 0.10f)
                    },
                    border = BorderStroke(1.dp, Color.White.copy(alpha = if (active) 0.24f else 0.10f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            target.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkflowCameraPrimaryButton(
    label: String,
    onClick: () -> Unit,
    placementTarget: CameraPlacementTarget? = null,
    onPlacementTargetChange: ((CameraPlacementTarget) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val actionLabel = when {
        label.equals("Tag", ignoreCase = true) -> "Plaats tag"
        label.equals("Sensor", ignoreCase = true) -> "Plaats sensor"
        label.equals("Plaats", ignoreCase = true) -> "Plaats sensor"
        else -> label
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .size(68.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(999.dp),
            color = Color.White.copy(alpha = 0.92f),
            border = BorderStroke(5.dp, Color.White.copy(alpha = 0.36f)),
            shadowElevation = 8.dp
        ) {
            Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                drawCircle(ArSensBlue.copy(alpha = 0.18f), radius = size.minDimension * 0.42f, center = Offset(size.width / 2f, size.height / 2f))
                drawCircle(ArSensBlue, radius = size.minDimension * 0.22f, center = Offset(size.width / 2f, size.height / 2f))
                drawCircle(Color.White, radius = size.minDimension * 0.075f, center = Offset(size.width / 2f, size.height / 2f))
            }
        }
        Surface(
            modifier = Modifier.clickable(onClick = onClick),
            shape = RoundedCornerShape(10.dp),
            color = WorkflowCameraPanelButton,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
        ) {
            Text(
                actionLabel,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
        if (placementTarget != null && onPlacementTargetChange != null) {
            WorkflowPlacementTargetSwitch(
                selected = placementTarget,
                onSelected = onPlacementTargetChange
            )
        }
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWorkflowCameraIcon(
    key: String,
    color: Color
) {
    val w = size.width
    val h = size.height
    val center = Offset(w / 2f, h / 2f)
    val stroke = (w * 0.11f).coerceAtLeast(2.2f)
    when (key) {
        "plane" -> {
            val top = Path().apply {
                moveTo(w * 0.50f, h * 0.12f)
                lineTo(w * 0.78f, h * 0.28f)
                lineTo(w * 0.50f, h * 0.44f)
                lineTo(w * 0.22f, h * 0.28f)
                close()
            }
            val lower = Path().apply {
                moveTo(w * 0.22f, h * 0.48f)
                lineTo(w * 0.50f, h * 0.64f)
                lineTo(w * 0.78f, h * 0.48f)
            }
            val bottom = Path().apply {
                moveTo(w * 0.22f, h * 0.66f)
                lineTo(w * 0.50f, h * 0.82f)
                lineTo(w * 0.78f, h * 0.66f)
            }
            drawPath(top, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
            drawPath(lower, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
            drawPath(bottom, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        "map2d" -> {
            repeat(2) { row ->
                repeat(2) { col ->
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(w * (0.18f + col * 0.34f), h * (0.18f + row * 0.34f)),
                        size = Size(w * 0.22f, h * 0.22f),
                        cornerRadius = CornerRadius(w * 0.03f, h * 0.03f),
                        style = Stroke(width = stroke)
                    )
                }
            }
        }
        "report" -> {
            drawRoundRect(color, Offset(w * 0.24f, h * 0.12f), Size(w * 0.52f, h * 0.76f), CornerRadius(w * 0.06f, h * 0.06f), style = Stroke(width = stroke))
            drawLine(color, Offset(w * 0.36f, h * 0.36f), Offset(w * 0.64f, h * 0.36f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.36f, h * 0.52f), Offset(w * 0.64f, h * 0.52f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.36f, h * 0.68f), Offset(w * 0.56f, h * 0.68f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "tags" -> {
            drawRect(color, topLeft = Offset(w * 0.18f, h * 0.18f), size = Size(w * 0.64f, h * 0.64f), style = Stroke(width = stroke))
            drawCircle(color, radius = w * 0.11f, center = center)
            drawLine(color, Offset(w * 0.18f, h * 0.36f), Offset(w * 0.36f, h * 0.18f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.64f, h * 0.82f), Offset(w * 0.82f, h * 0.64f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "cursor", "place" -> {
            drawCircle(color, radius = w * 0.30f, center = center, style = Stroke(width = stroke))
            drawLine(color, Offset(w * 0.06f, center.y), Offset(w * 0.34f, center.y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.66f, center.y), Offset(w * 0.94f, center.y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(center.x, h * 0.06f), Offset(center.x, h * 0.34f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(center.x, h * 0.66f), Offset(center.x, h * 0.94f), strokeWidth = stroke, cap = StrokeCap.Round)
            if (key == "place") {
                drawCircle(color, radius = w * 0.10f, center = center)
            }
        }
        "orient" -> {
            drawLine(Color(0xFFE53935), center, Offset(w * 0.88f, h * 0.58f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(Color(0xFF43A047), center, Offset(w * 0.28f, h * 0.82f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(Color(0xFF1E88E5), center, Offset(w * 0.46f, h * 0.12f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawCircle(color, radius = w * 0.08f, center = center)
        }
        "layers" -> {
            drawLine(color, Offset(w * 0.18f, h * 0.30f), Offset(w * 0.82f, h * 0.30f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.26f, h * 0.50f), Offset(w * 0.74f, h * 0.50f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.34f, h * 0.70f), Offset(w * 0.66f, h * 0.70f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "models" -> {
            drawRect(color, topLeft = Offset(w * 0.20f, h * 0.24f), size = Size(w * 0.42f, h * 0.26f), style = Stroke(width = stroke))
            drawRect(color, topLeft = Offset(w * 0.34f, h * 0.38f), size = Size(w * 0.46f, h * 0.30f), style = Stroke(width = stroke))
            drawLine(color, Offset(w * 0.20f, h * 0.24f), Offset(w * 0.34f, h * 0.38f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.62f, h * 0.24f), Offset(w * 0.80f, h * 0.38f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "edit" -> {
            drawLine(color, Offset(w * 0.24f, h * 0.72f), Offset(w * 0.72f, h * 0.24f), strokeWidth = stroke * 1.35f, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.82f, h * 0.38f), strokeWidth = stroke * 1.15f, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.20f, h * 0.78f), Offset(w * 0.36f, h * 0.72f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "fill" -> {
            drawRect(color, topLeft = Offset(w * 0.22f, h * 0.18f), size = Size(w * 0.56f, h * 0.64f), style = Stroke(width = stroke))
            drawRect(color.copy(alpha = 0.72f), topLeft = Offset(w * 0.30f, h * 0.52f), size = Size(w * 0.40f, h * 0.20f))
            drawLine(color, Offset(w * 0.30f, h * 0.40f), Offset(w * 0.70f, h * 0.40f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "sensor" -> {
            drawCircle(color, radius = w * 0.22f, center = center, style = Stroke(width = stroke))
            drawCircle(color, radius = w * 0.08f, center = center)
            drawLine(color, Offset(center.x, h * 0.10f), Offset(center.x, h * 0.25f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(center.x, h * 0.75f), Offset(center.x, h * 0.90f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.10f, center.y), Offset(w * 0.25f, center.y), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.75f, center.y), Offset(w * 0.90f, center.y), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "undo" -> {
            // Gebogen terug-pijl (↶) — undo van de laatst geplaatste sensor.
            val r = w * 0.27f
            drawArc(
                color = color,
                startAngle = 30f,
                sweepAngle = 285f,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            val a = Math.toRadians(30.0)
            val tip = Offset(center.x + (r * cos(a)).toFloat(), center.y + (r * sin(a)).toFloat())
            val ah = w * 0.17f
            val arrow = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(tip.x - ah, tip.y - ah * 0.25f)
                lineTo(tip.x - ah * 0.15f, tip.y + ah)
                close()
            }
            drawPath(arrow, color)
        }
        "grid" -> {
            // Positioneringsgrid (#): twee verticale + twee horizontale lijnen.
            repeat(2) { i ->
                val x = w * (0.36f + i * 0.28f)
                drawLine(color, Offset(x, h * 0.16f), Offset(x, h * 0.84f), strokeWidth = stroke, cap = StrokeCap.Round)
                val y = h * (0.36f + i * 0.28f)
                drawLine(color, Offset(w * 0.16f, y), Offset(w * 0.84f, y), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
        "trash" -> {
            // Prullenbak: deksel + handvat + bak met ribbels.
            drawLine(color, Offset(w * 0.22f, h * 0.30f), Offset(w * 0.78f, h * 0.30f), strokeWidth = stroke, cap = StrokeCap.Round)
            val lid = Path().apply {
                moveTo(w * 0.42f, h * 0.30f)
                lineTo(w * 0.42f, h * 0.20f)
                lineTo(w * 0.58f, h * 0.20f)
                lineTo(w * 0.58f, h * 0.30f)
            }
            drawPath(lid, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
            val bin = Path().apply {
                moveTo(w * 0.30f, h * 0.32f)
                lineTo(w * 0.34f, h * 0.82f)
                lineTo(w * 0.66f, h * 0.82f)
                lineTo(w * 0.70f, h * 0.32f)
            }
            drawPath(bin, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
            drawLine(color, Offset(w * 0.44f, h * 0.42f), Offset(w * 0.45f, h * 0.72f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.56f, h * 0.42f), Offset(w * 0.55f, h * 0.72f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
        }
        "export" -> {
            // Bak met pijl omhoog eruit (delen/exporteren).
            drawLine(color, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.5f, h * 0.56f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.36f, h * 0.30f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.64f, h * 0.30f), strokeWidth = stroke, cap = StrokeCap.Round)
            val tray = Path().apply {
                moveTo(w * 0.26f, h * 0.48f)
                lineTo(w * 0.26f, h * 0.82f)
                lineTo(w * 0.74f, h * 0.82f)
                lineTo(w * 0.74f, h * 0.48f)
            }
            drawPath(tray, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        "twod" -> {
            drawRoundRect(color, Offset(w * 0.18f, h * 0.22f), Size(w * 0.28f, h * 0.56f), CornerRadius(w * 0.04f, w * 0.04f), style = Stroke(width = stroke))
            drawRoundRect(color, Offset(w * 0.54f, h * 0.22f), Size(w * 0.28f, h * 0.56f), CornerRadius(w * 0.04f, w * 0.04f), style = Stroke(width = stroke))
        }
        "cube" -> {
            val p = Path().apply {
                moveTo(w * 0.5f, h * 0.16f)
                lineTo(w * 0.82f, h * 0.34f)
                lineTo(w * 0.82f, h * 0.68f)
                lineTo(w * 0.5f, h * 0.86f)
                lineTo(w * 0.18f, h * 0.68f)
                lineTo(w * 0.18f, h * 0.34f)
                close()
            }
            drawPath(p, color, style = Stroke(width = stroke))
            drawLine(color, Offset(w * 0.5f, h * 0.16f), Offset(w * 0.5f, h * 0.51f), strokeWidth = stroke)
            drawLine(color, Offset(w * 0.18f, h * 0.34f), Offset(w * 0.5f, h * 0.51f), strokeWidth = stroke)
            drawLine(color, Offset(w * 0.82f, h * 0.34f), Offset(w * 0.5f, h * 0.51f), strokeWidth = stroke)
        }
        "stl", "ar" -> {
            val p = Path().apply {
                moveTo(w * 0.5f, h * 0.22f)
                lineTo(w * 0.80f, h * 0.38f)
                lineTo(w * 0.80f, h * 0.68f)
                lineTo(w * 0.5f, h * 0.84f)
                lineTo(w * 0.20f, h * 0.68f)
                lineTo(w * 0.20f, h * 0.38f)
                close()
            }
            drawPath(p, color, style = Stroke(width = stroke))
            drawLine(color, Offset(w * 0.5f, h * 0.22f), Offset(w * 0.5f, h * 0.53f), strokeWidth = stroke)
            drawLine(color, Offset(w * 0.20f, h * 0.38f), Offset(w * 0.5f, h * 0.53f), strokeWidth = stroke)
            drawLine(color, Offset(w * 0.80f, h * 0.38f), Offset(w * 0.5f, h * 0.53f), strokeWidth = stroke)
            drawCircle(color, radius = w * 0.035f, center = Offset(w * 0.5f, h * 0.12f))
        }
        "chevron" -> {
            drawLine(color, Offset(w * 0.42f, h * 0.28f), Offset(w * 0.62f, h * 0.50f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.62f, h * 0.50f), Offset(w * 0.42f, h * 0.72f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "measure" -> {
            // Meetlat: diagonale lijn met eindpunten (zelfde metafoor als de 2D-kaart).
            val a = Offset(w * 0.22f, h * 0.78f)
            val b = Offset(w * 0.78f, h * 0.22f)
            drawLine(color, a, b, strokeWidth = stroke, cap = StrokeCap.Round)
            drawCircle(color, radius = w * 0.09f, center = a)
            drawCircle(color, radius = w * 0.09f, center = b)
        }
        "list" -> {
            // Lijst: drie regels met opsommingsstippen.
            repeat(3) { i ->
                val y = h * (0.26f + i * 0.24f)
                drawCircle(color, radius = w * 0.05f, center = Offset(w * 0.20f, y))
                drawLine(color, Offset(w * 0.34f, y), Offset(w * 0.82f, y), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
        "close" -> {
            drawLine(color, Offset(w * 0.30f, h * 0.30f), Offset(w * 0.70f, h * 0.70f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.70f, h * 0.30f), Offset(w * 0.30f, h * 0.70f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "pin" -> {
            drawCircle(color, radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.40f), style = Stroke(width = stroke))
            drawCircle(color, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.40f))
            drawLine(color, Offset(w * 0.5f, h * 0.62f), Offset(w * 0.5f, h * 0.84f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "calendar" -> {
            drawRoundRect(color, Offset(w * 0.20f, h * 0.24f), Size(w * 0.60f, h * 0.56f), CornerRadius(w * 0.05f, w * 0.05f), style = Stroke(width = stroke))
            drawLine(color, Offset(w * 0.20f, h * 0.40f), Offset(w * 0.80f, h * 0.40f), strokeWidth = stroke)
            drawLine(color, Offset(w * 0.36f, h * 0.18f), Offset(w * 0.36f, h * 0.30f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.64f, h * 0.18f), Offset(w * 0.64f, h * 0.30f), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        "person" -> {
            drawCircle(color, radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.32f), style = Stroke(width = stroke))
            val body = Path().apply {
                moveTo(w * 0.24f, h * 0.82f)
                cubicTo(w * 0.24f, h * 0.56f, w * 0.76f, h * 0.56f, w * 0.76f, h * 0.82f)
            }
            drawPath(body, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        "select" -> {
            // Aanwijzer/cursor-pijl — de "Selecteer"-tool in de 2D-kaart.
            val path = Path().apply {
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
        "start" -> {
            // Play-driehoek — start de installatie vanuit het voorbereide 2D-plan.
            val path = Path().apply {
                moveTo(w * 0.34f, h * 0.22f)
                lineTo(w * 0.76f, h * 0.50f)
                lineTo(w * 0.34f, h * 0.78f)
                close()
            }
            drawPath(path, color)
        }
        "view" -> {
            // Schuifregelaars — het "Gereedschap"-menu (meten, verplaatsen, reset) in de 2D-kaart.
            // Bewust géén venster-icoon, zodat het visueel niet botst met de "Vlak"-keuze.
            drawLine(color, Offset(w * 0.16f, h * 0.36f), Offset(w * 0.84f, h * 0.36f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, Offset(w * 0.16f, h * 0.64f), Offset(w * 0.84f, h * 0.64f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawCircle(color, radius = w * 0.10f, center = Offset(w * 0.64f, h * 0.36f))
            drawCircle(color, radius = w * 0.10f, center = Offset(w * 0.36f, h * 0.64f))
        }
        else -> {
            drawCircle(color, radius = w * 0.28f, center = center, style = Stroke(width = stroke))
        }
    }
}

@Composable
internal fun WorkflowCameraPlaneSelectorPanel(
    selectedPlane: TagPlane,
    onPlaneSelected: (TagPlane) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Vlak",
            color = Color.White.copy(alpha = 0.82f),
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp
        )
        WorkflowPlaneCross(
            selectedPlane = selectedPlane,
            onPlaneSelected = onPlaneSelected,
            compact = true
        )
    }
}

/**
 * Uitgeklapte trafo als knoppen-kruis (net) vanuit de voorkant van de trafo:
 * Achterzijde boven, zijvlakken Links/Boven/Rechts in het midden, Voorzijde onder.
 * Het geselecteerde vlak licht teal op.
 */
@Composable
internal fun WorkflowPlaneCross(
    selectedPlane: TagPlane,
    onPlaneSelected: (TagPlane) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            Spacer(Modifier.weight(1f))
            PlaneCrossButton(TagPlane.Back, selectedPlane, onPlaneSelected, Modifier.weight(1f), compact)
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            PlaneCrossButton(TagPlane.Left, selectedPlane, onPlaneSelected, Modifier.weight(1f), compact)
            PlaneCrossButton(TagPlane.Top, selectedPlane, onPlaneSelected, Modifier.weight(1f), compact)
            PlaneCrossButton(TagPlane.Right, selectedPlane, onPlaneSelected, Modifier.weight(1f), compact)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            Spacer(Modifier.weight(1f))
            PlaneCrossButton(TagPlane.Front, selectedPlane, onPlaneSelected, Modifier.weight(1f), compact)
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun PlaneCrossButton(
    plane: TagPlane,
    selectedPlane: TagPlane,
    onPlaneSelected: (TagPlane) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val selected = plane == selectedPlane
    Surface(
        modifier = modifier
            .height(if (compact) 46.dp else 54.dp)
            .clickable { onPlaneSelected(plane) },
        shape = RoundedCornerShape(if (compact) 11.dp else 14.dp),
        color = if (selected) ArSensTeal else Color.White.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, if (selected) ArSensTeal else Color.White.copy(alpha = 0.18f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                if (compact) plane.shortLabel else plane.cameraPlaneLabel(),
                color = Color.White,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 3.dp)
            )
        }
    }
}

internal fun TagPlane.cameraPlaneLabel(): String =
    when (this) {
        TagPlane.Front -> "Voorzijde"
        TagPlane.Back -> "Achterzijde"
        TagPlane.Left -> "Links"
        TagPlane.Right -> "Rechts"
        TagPlane.Top -> "Boven"
    }


@Composable
internal fun WorkflowOverlayToggles(state: WorkflowAppState) {
    // Lagen-paneel zoals de afbeelding: per laag een rij met icoon + titel + subtitel + schakelaar.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        WorkflowLayerToggleRow(
            iconKey = "tags",
            title = "AprilTags",
            subtitle = "Toon gerelateerde tags",
            checked = state.showTagOverlay,
            onCheckedChange = { state.showTagOverlay = it }
        )
        WorkflowLayerToggleRow(
            iconKey = "sensor",
            title = "Sensoren",
            subtitle = "Toon geplaatste sensoren",
            checked = state.showSensorOverlay,
            onCheckedChange = { state.showSensorOverlay = it }
        )
        WorkflowLayerToggleRow(
            iconKey = "orient",
            title = "Coördinaten",
            subtitle = "Toon X, Y, Z assen",
            checked = state.showAxisOverlay,
            onCheckedChange = { state.showAxisOverlay = it }
        )
        WorkflowLayerToggleRow(
            iconKey = "grid",
            title = "Grid",
            subtitle = "Toon positioneringsgrid",
            checked = state.showMiniAxisOverlay,
            onCheckedChange = { state.showMiniAxisOverlay = it }
        )
        WorkflowLayerToggleRow(
            iconKey = "cube",
            title = "Box-randen (trafo)",
            subtitle = "Toon de randen van de trafo-box in AR",
            checked = state.showBoxEdgesOverlay,
            onCheckedChange = { state.showBoxEdgesOverlay = it }
        )
        WorkflowLayerToggleRow(
            iconKey = "orient",
            title = "XYZ + hoek per tag",
            subtitle = "Toon opgeslagen positie en rotatie per AprilTag",
            checked = state.showTagPoseLabels,
            onCheckedChange = { state.showTagPoseLabels = it }
        )
        if (state.project.stlModels.isNotEmpty()) {
            WorkflowLayerToggleRow(
                iconKey = "stl",
                title = "3D-model",
                subtitle = "3D-assembly op de tags",
                checked = state.showStlOverlay,
                onCheckedChange = { state.showStlOverlay = it }
            )
            if (state.showStlOverlay) {
                WorkflowStlArModeButtons(state)
            }
        }
    }
}

/** Keuzeknoppen voor de AR-weergavemodus (2×2): Snel / Technisch / Vlakken / Detail. */
@Composable
internal fun WorkflowStlArModeButtons(state: WorkflowAppState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StlArRenderMode.entries.chunked(2).forEach { rowModes ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowModes.forEach { mode ->
                    WorkflowToggleButton(
                        selected = state.stlArRenderMode == mode,
                        text = mode.label,
                        onClick = { state.stlArRenderMode = mode },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    )
                }
            }
        }
    }
}

internal fun stlArGroupSubtitle(group: StlArPartGroup): String =
    when (group) {
        StlArPartGroup.Tank -> "Tankwanden"
        StlArPartGroup.Deksel -> "Deksel / bovenplaat"
        StlArPartGroup.ActiefDeel -> "Binnenwerk — standaard uit"
        StlArPartGroup.Radiatoren -> "Koelers en aanbouwdelen"
        StlArPartGroup.Overig -> "Overige delen — standaard uit"
    }

/**
 * AR-sheet in de camera: 3D-model aan/uit, rendertechniek, dekking, per-groep en per-deel
 * zichtbaarheid en live tag-kalibratie. Vanuit hier verschuif je de OPGESLAGEN tagpositie in
 * millimeters terwijl je in AR ziet waar de trafo werkelijk staat — het model én alle sensoren
 * schuiven mee, want de tagpositie definieert het projectframe.
 */
@Composable
internal fun WorkflowArOptionsSheet(state: WorkflowAppState) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = state::recalibrateAr, modifier = Modifier.fillMaxWidth()) {
            Text("AR opnieuw ijken")
        }
        state.aprilTagResult.poseDiagnostic?.let { Text(it, color = Color.White, fontSize = 13.sp) }

        if (state.project.stlModels.isEmpty()) {
            Text(
                "Geen 3D-assembly in dit project. Importeer de delen via de kaart " +
                    "„3D-model (assembly)” op het projectscherm.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
        } else {
            WorkflowLayerToggleRow(
                iconKey = "stl",
                title = "3D-model in AR",
                subtitle = "Assembly verankerd op de tags",
                checked = state.showStlOverlay,
                onCheckedChange = { state.showStlOverlay = it }
            )
            WorkflowSheetSectionLabel("Weergave")
            WorkflowStlArModeButtons(state)
            WorkflowSheetSectionLabel("Dekking · ${state.stlArOpacityPercent}%")
            Slider(
                value = state.stlArOpacityPercent.toFloat(),
                onValueChange = { state.stlArOpacityPercent = it.roundToInt().coerceIn(20, 100) },
                valueRange = 20f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = ArSensTeal,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
            WorkflowSheetSectionLabel("Onderdelen")
            // Groepen volgen de gebouwde AR-cache; zolang die er niet is tonen we alle opties.
            val presentGroups = state.stlArParts?.values?.flatMapTo(mutableSetOf()) { it.groups }
                ?: StlArPartGroup.entries.toMutableSet()
            StlArPartGroup.entries.filter { it in presentGroups }.forEach { group ->
                WorkflowLayerToggleRow(
                    iconKey = "cube",
                    title = group.label,
                    subtitle = stlArGroupSubtitle(group),
                    checked = state.stlArGroupVisible[group] != false,
                    onCheckedChange = { state.stlArGroupVisible[group] = it }
                )
            }
            WorkflowSheetSectionLabel("Delen")
            state.project.stlModels.forEach { model ->
                WorkflowLayerToggleRow(
                    iconKey = "cube",
                    title = model.name,
                    subtitle = "Toon dit deel in AR en 3D",
                    checked = model.visible,
                    onCheckedChange = { state.toggleStlVisible(model.id) }
                )
            }
            WorkflowSheetSectionLabel("Tag-kalibratie")
            val refTagId = state.overlayAprilTagResult.poseMarkerIds.firstOrNull()
            val refMarker = refTagId?.let { id -> state.project.markers.firstOrNull { it.id == id } }
            if (refMarker == null) {
                Text(
                    "Geen tag in beeld — richt de camera op een AprilTag om vanaf hier te kalibreren.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
            } else {
                Text(
                    "Tag #${refMarker.id} · ${workflowSheetCoords(refMarker.positionMm)}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                WorkflowArNudgeRow("X") { delta -> state.nudgeTagPosition(refMarker.id, delta, 0, 0) }
                WorkflowArNudgeRow("Y") { delta -> state.nudgeTagPosition(refMarker.id, 0, delta, 0) }
                WorkflowArNudgeRow("Z") { delta -> state.nudgeTagPosition(refMarker.id, 0, 0, delta) }
                Text(
                    "Verschuift de opgeslagen tagpositie (mm) — model en sensoren schuiven mee.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
                OutlinedButton(
                    onClick = { scope.launch { state.snapTagToModelSurface(refMarker.id) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("Tag op modeloppervlak (model op tag-diepte)")
                }
                Text(
                    "Zet de tagdiepte op de werkelijke modelwand op die plek, zodat de tag óp het " +
                        "model lijkt te zitten in plaats van ervoor te zweven.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            }
            OutlinedButton(
                onClick = { state.requestAutoAlignAssembly(scope) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text("Lijn assembly uit op tank")
            }
        }
    }
}

@Composable
private fun WorkflowArNudgeRow(label: String, onNudge: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        listOf(-50, -10, 10, 50).forEach { delta ->
            OutlinedButton(
                onClick = { onNudge(delta) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(if (delta > 0) "+$delta" else "$delta", fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun WorkflowLayerToggleRow(
    iconKey: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(11.dp),
                color = Color.White.copy(alpha = 0.10f)
            ) {
                Canvas(Modifier.fillMaxSize().padding(9.dp)) {
                    drawWorkflowCameraIcon(iconKey, Color.White)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ArSensTeal,
                    checkedBorderColor = ArSensTeal,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.16f),
                    uncheckedBorderColor = Color.White.copy(alpha = 0.24f)
                )
            )
        }
    }
}

@Composable
internal fun WorkflowToggleButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.height(52.dp)
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    }
}

@Composable
internal fun WorkflowKnownTags(
    project: Project,
    tags: List<Marker>,
    onEdit: ((Marker) -> Unit)? = null,
    onToggleActive: (Marker) -> Unit,
    onDelete: (Marker) -> Unit
) {
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Bekende AprilTags", fontWeight = FontWeight.Bold)
            if (tags.isEmpty()) {
                Text("Nog geen tags opgeslagen.")
            } else {
                tags.sortedBy { it.id }.forEach {
                    val operatorPosition = project.coordinateMapper().boxToOperator(it.positionMm)
                    val planeLabel = markerSurfaceLabel(it, project.dimensionsMm)
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(9.dp).background(workflowStatusColor(if (it.active) SensorStatus.Ok else SensorStatus.Pending), RoundedCornerShape(50)))
                            Text("ID ${it.id}", fontWeight = FontWeight.Bold)
                            Text("$planeLabel · ${it.sizeMm}mm", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(operatorPosition.toReadableMm(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (onEdit != null) {
                                Button(onClick = { onEdit(it) }, modifier = Modifier.height(44.dp)) {
                                    Text("Bewerk")
                                }
                            }
                            OutlinedButton(onClick = { onToggleActive(it) }, modifier = Modifier.height(44.dp)) {
                                Text(if (it.active) "Zet uit" else "Zet aan")
                            }
                            OutlinedButton(onClick = { onDelete(it) }, modifier = Modifier.height(44.dp)) {
                                Text("Verwijder")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun WorkflowRecentSensorsPanel(state: WorkflowAppState) {
    // Lichte variant (donkere tekst op lichte kaart): dit paneel staat in de 2D-voorbereidmenu's
    // op een lichte achtergrond — wit-op-wit was onleesbaar.
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Geplaatste sensoren", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val recent = state.project.sensors.sortedByDescending { it.order }.take(5)
            if (recent.isEmpty()) {
                Text("Nog geen sensoren opgeslagen.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            } else {
                recent.forEach { sensor ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.selectSensorForEdit(sensor) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.size(9.dp).background(ArSensTeal, RoundedCornerShape(50)))
                        Text(
                            sensor.id.ifBlank { "Sensor ${sensor.order}" },
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            state.operatorText(sensor.positionMm).replace(",", " | "),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** Compacte, moderne statusregel: gekleurde stip + korte tekst. Vervangt de grote statuskaart
 *  op plekken waar alleen een beknopte status nodig is — rustiger en strakker beeld. */
@Composable
internal fun WorkflowStatusChip(
    text: String,
    status: SensorStatus,
    modifier: Modifier = Modifier
) {
    val color = workflowStatusColor(status)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(Modifier.size(9.dp).background(color, RoundedCornerShape(50)))
        Text(text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Compacte sleutel/waarde-regel voor een rustige, moderne uitlijning binnen kaarten. */
@Composable
internal fun WorkflowInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun WorkflowMessage(message: String?) {
    if (message.isNullOrBlank()) return
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(message, modifier = Modifier.padding(12.dp))
    }
}

@Composable
internal fun WorkflowSection(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    Spacer(Modifier.height(2.dp))
}

@Composable
internal fun WorkflowNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

