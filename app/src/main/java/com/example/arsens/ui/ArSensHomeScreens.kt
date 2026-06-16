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
import com.example.arsens.ar.TagDictionaryOption
import com.example.arsens.ar.TagPoseMode
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

internal enum class ArSensGlyph {
    Back,
    Bell,
    Building,
    Checklist,
    Cube,
    Document,
    Folder,
    Home,
    More,
    Navigation,
    Plus,
    Report,
    Sensor,
    User
}

@Composable
internal fun WorkflowProjectPickerScreen(state: WorkflowAppState) {
    var showCreateForm by remember { mutableStateOf(state.projects.isEmpty()) }
    var showAllProjects by remember { mutableStateOf(false) }
    val visibleProjects = if (showAllProjects) state.projects else state.projects.take(4)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArSensBackground)
            .safeDrawingPadding()
    ) {
        ArSensBlueprintBackground(Modifier.fillMaxSize())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Welkom bij ARsens", color = ArSensInk, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Text("Beheer al je projecten op een plek.", color = ArSensMuted, fontSize = 15.sp)
                    }
                    // Hamburger-menu (3 puntjes): hier alleen app-acties — er is nog geen project open.
                    ArSensHomeMenuButton(
                        items = listOf(
                            ArSensMenuItem("Instellingen") { state.navigateTo(WorkflowScreen.Settings) }
                        )
                    )
                }
            }
            item { WorkflowMessage(state.message) }
            item {
                ArSensActionCard(
                    title = "Project maken",
                    body = "Start een nieuw project",
                    icon = ArSensGlyph.Plus,
                    brush = Brush.linearGradient(listOf(ArSensBlue, Color(0xFF4494FF))),
                    onClick = { showCreateForm = !showCreateForm }
                )
            }
            item {
                ArSensActionCard(
                    title = "Project openen",
                    body = "Open een bestaand project",
                    icon = ArSensGlyph.Folder,
                    brush = Brush.linearGradient(listOf(ArSensTeal, Color(0xFF42C8C7))),
                    onClick = {
                        showCreateForm = false
                        showAllProjects = true
                    }
                )
            }
            if (showCreateForm) {
                item { ArSensCreateProjectCard(state) }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recente projecten", color = ArSensInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (state.projects.size > 4) {
                        TextButton(onClick = { showAllProjects = !showAllProjects }) {
                            Text(if (showAllProjects) "Minder" else "Bekijk alle")
                        }
                    }
                }
            }
            if (state.projects.isEmpty()) {
                item { ArSensEmptyProjectCard() }
            } else {
                items(visibleProjects, key = { it.id }) { summary ->
                    ArSensRecentProjectRow(
                        summary = summary,
                        onOpen = { state.openProject(summary) },
                        onDelete = { state.requestDeleteProject(summary) }
                    )
                }
            }
        }
        // Onderste navigatiebalk verwijderd — navigatie zit nu in het hamburger-menu (top bar).
    }
}

@Composable
internal fun WorkflowStartScreen(state: WorkflowAppState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ArSensBackground)
            .safeDrawingPadding()
    ) {
        ArSensBlueprintBackground(
            modifier = Modifier.fillMaxSize(),
            skylineTop = true
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArSensIconButton(icon = ArSensGlyph.Back, onClick = state::closeProject)
                    Spacer(Modifier.weight(1f))
                    // Zelfde hamburger-menu als de top bars op de overige schermen.
                    ArSensHomeMenuButton(items = workflowTopBarMenuItems(state))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(state.project.projectName, color = ArSensInk, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            "Laatst bijgewerkt: ${formatProjectUpdatedAt(state.activeProjectUpdatedAtMillis)}",
                            color = ArSensMuted,
                            fontSize = 14.sp
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(Color(0xFF52D39B), RoundedCornerShape(50))
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(84.dp)) }
            item { WorkflowMessage(state.message) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ArSensFeatureTile(
                        title = "On the fly",
                        body = "Direct meten en vastleggen",
                        icon = ArSensGlyph.Navigation,
                        tint = ArSensBlue,
                        onClick = { state.chooseMode(WorkMode.OnTheFly) },
                        modifier = Modifier
                            .weight(1f)
                            .height(158.dp)
                    )
                    ArSensFeatureTile(
                        title = "Voorbereid",
                        body = "Gebruik een voorbereid plan",
                        icon = ArSensGlyph.Checklist,
                        tint = ArSensTeal,
                        onClick = { state.chooseMode(WorkMode.Prepared) },
                        modifier = Modifier
                            .weight(1f)
                            .height(158.dp)
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ArSensFeatureTile(
                        title = "3D weergave",
                        body = "Bekijk en meet in 3D",
                        icon = ArSensGlyph.Cube,
                        tint = ArSensBlue,
                        onClick = { state.go(WorkflowScreen.Stl) },
                        modifier = Modifier
                            .weight(1f)
                            .height(158.dp)
                    )
                    ArSensFeatureTile(
                        title = "Rapport",
                        body = "Analyseer en deel rapporten",
                        icon = ArSensGlyph.Report,
                        tint = Color(0xFF7A5CFF),
                        onClick = { state.go(WorkflowScreen.Report) },
                        modifier = Modifier
                            .weight(1f)
                            .height(158.dp)
                    )
                }
            }
            item { WorkflowAssemblyCard(state) }
        }
        // Onderste navigatiebalk verwijderd — navigatie zit nu in het hamburger-menu (top bar).
    }
}

/** Hamburger-knop voor de home-schermen (zelfde stijl als de overige icoonknoppen daar):
 *  opent een DropdownMenu met dezelfde [ArSensMenuItem]-regels als de glazen top bar. */
@Composable
internal fun ArSensHomeMenuButton(items: List<ArSensMenuItem>) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        ArSensIconButton(icon = ArSensGlyph.More, onClick = { menuOpen = true })
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            items.forEach { item ->
                if (item.isDivider) {
                    HorizontalDivider()
                } else {
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        enabled = item.enabled,
                        onClick = {
                            menuOpen = false
                            item.onClick()
                        }
                    )
                }
            }
        }
    }
}

/** Instellingen-scherm (app-breed, los van het project): bereikbaar via het hamburger-menu. */
@Composable
internal fun WorkflowSettingsScreen(state: WorkflowAppState) {
    WorkflowShell(
        title = "Instellingen",
        subtitle = "",
        onBack = state::navigateBack
    ) {
        item { WorkflowMessage(state.message) }
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ArSensSurface),
                border = BorderStroke(1.dp, ArSensLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AprilTag-dictionary", color = ArSensInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Tagfamilie die de camera herkent. Alle tags in een project moeten uit " +
                            "dezelfde familie komen; de wijziging geldt direct, ook in een open camera.",
                        color = ArSensMuted,
                        fontSize = 13.sp
                    )
                    TagDictionaryOption.entries.forEach { option ->
                        val selected = state.tagDictionary == option
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) ArSensBlue.copy(alpha = 0.10f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (selected) ArSensBlue else ArSensLine),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { state.selectTagDictionary(option) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(option.label, color = ArSensInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(option.description, color = ArSensMuted, fontSize = 12.sp)
                                }
                                if (selected) {
                                    Text("Actief", color = ArSensBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = ArSensLine)
                    Text(
                        "tagStandard41h12 (en de andere AprilTag 3 \"standard\"/\"circle\"-families) " +
                            "wordt niet ondersteund: die families leggen een deel van de databits " +
                            "BUITEN de zwarte rand, en de OpenCV-detector in deze app kan alleen " +
                            "binnen de rand lezen. Print de tags als tag36h11 (aanbevolen), of er " +
                            "moet later de originele AprilTag 3-bibliotheek ingebouwd worden.",
                        color = ArSensMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }
        item { WorkflowTagSettingsCard(state) }
    }
}

/** Instellingen-kaart voor tagformaten, de pose-modus en sensor-tags. */
@Composable
internal fun WorkflowTagSettingsCard(state: WorkflowAppState) {
    var tagSizeText by remember { mutableStateOf(state.defaultTagSizeMm.toString()) }
    var sensorStartText by remember { mutableStateOf(state.sensorTagStartId.toString()) }
    var sensorSizeText by remember { mutableStateOf(state.sensorTagSizeMm.toString()) }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ArSensSurface),
        border = BorderStroke(1.dp, ArSensLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tags", color = ArSensInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            WorkflowNumberField(
                "Standaard tagformaat (mm)",
                tagSizeText,
                {
                    tagSizeText = it
                    it.toIntOrNull()?.let(state::setDefaultTagSize)
                },
                Modifier.fillMaxWidth()
            )
            Text(
                "Tagformaat = het zwarte vierkant van de print; wordt het standaardformaat voor " +
                    "nieuwe referentietags.",
                color = ArSensMuted,
                fontSize = 12.sp
            )
            val existingTagCount = state.savedAprilTags.size
            Button(
                onClick = state::applyDefaultTagSizeToExistingTags,
                enabled = existingTagCount > 0,
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("Pas toe op bestaande tags ($existingTagCount)")
            }
            Text(
                "Normaal geldt dit formaat alleen voor nieuwe tags. Deze knop herschaalt ook de al " +
                    "geplaatste referentietags (de AR-kalibratie wordt dan gereset).",
                color = ArSensMuted,
                fontSize = 12.sp
            )
            HorizontalDivider(color = ArSensLine)
            Text("Pose-modus", color = ArSensInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            TagPoseMode.entries.forEach { mode ->
                val selected = state.tagPoseMode == mode
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) ArSensBlue.copy(alpha = 0.10f) else Color.Transparent,
                    border = BorderStroke(1.dp, if (selected) ArSensBlue else ArSensLine),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { state.selectTagPoseMode(mode) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(mode.label, color = ArSensInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(mode.description, color = ArSensMuted, fontSize = 12.sp)
                        }
                        if (selected) {
                            Text("Actief", color = ArSensBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
            HorizontalDivider(color = ArSensLine)
            Text("Sensor-tags", color = ArSensInk, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                WorkflowNumberField(
                    "Vanaf ID",
                    sensorStartText,
                    {
                        sensorStartText = it
                        it.toIntOrNull()?.let(state::setSensorTagStart)
                    },
                    Modifier.weight(1f)
                )
                WorkflowNumberField(
                    "Formaat (mm)",
                    sensorSizeText,
                    {
                        sensorSizeText = it
                        it.toIntOrNull()?.let(state::setSensorTagSize)
                    },
                    Modifier.weight(1f)
                )
            }
            Text(
                "Kleine AprilTag óp elke sensor: de app herkent dan wélke sensor geplakt is en " +
                    "meet de werkelijke positie. ID's vanaf dit nummer zijn voor sensoren; " +
                    "referentietags krijgen automatisch ID's eronder (vanaf 0).",
                color = ArSensMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
internal fun ArSensBlueprintBackground(
    modifier: Modifier = Modifier,
    skylineTop: Boolean = false
) {
    Box(modifier.background(Brush.verticalGradient(listOf(Color.White, ArSensBackground)))) {
        Canvas(Modifier.fillMaxSize()) {
            val line = ArSensBlue.copy(alpha = 0.10f)
            val teal = ArSensTeal.copy(alpha = 0.10f)
            val baseY = if (skylineTop) size.height * 0.28f else size.height * 0.72f
            val wave = Path().apply {
                moveTo(0f, baseY + size.height * 0.08f)
                cubicTo(size.width * 0.22f, baseY - 70f, size.width * 0.34f, baseY + 90f, size.width * 0.50f, baseY + 12f)
                cubicTo(size.width * 0.68f, baseY - 70f, size.width * 0.82f, baseY - 30f, size.width, baseY - 96f)
            }
            drawPath(wave, line, style = Stroke(width = 2f))
            val skylineBottom = if (skylineTop) baseY + 92f else size.height - 112f
            val buildingWidth = size.width / 6f
            repeat(7) { index ->
                val left = index * buildingWidth - buildingWidth * 0.2f
                val h = (44f + (index % 4) * 24f)
                val top = skylineBottom - h
                drawRect(
                    color = line,
                    topLeft = Offset(left, top),
                    size = Size(buildingWidth * 0.72f, h),
                    style = Stroke(width = 2f)
                )
                repeat(3) { row ->
                    drawLine(
                        color = line,
                        start = Offset(left + 8f, top + 12f + row * 18f),
                        end = Offset(left + buildingWidth * 0.58f, top + 12f + row * 18f),
                        strokeWidth = 1.4f
                    )
                }
            }
            repeat(4) { ring ->
                drawCircle(
                    color = teal,
                    radius = 34f + ring * 28f,
                    center = Offset(size.width * 0.50f, if (skylineTop) baseY + 112f else size.height - 96f),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

@Composable
internal fun ArSensActionCard(
    title: String,
    body: String,
    icon: ArSensGlyph,
    brush: Brush,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ArSensIconBadge(icon = icon, tint = ArSensBlue, background = Color.White, square = true)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(body, color = Color.White.copy(alpha = 0.88f), fontSize = 14.sp)
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.20f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Canvas(Modifier.fillMaxSize().padding(11.dp)) {
                        drawLine(Color.White, Offset(size.width * 0.30f, size.height * 0.18f), Offset(size.width * 0.68f, size.height * 0.50f), strokeWidth = 4f, cap = StrokeCap.Round)
                        drawLine(Color.White, Offset(size.width * 0.68f, size.height * 0.50f), Offset(size.width * 0.30f, size.height * 0.82f), strokeWidth = 4f, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArSensCreateProjectCard(state: WorkflowAppState) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ArSensSurface),
        border = BorderStroke(1.dp, ArSensLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Nieuw project", color = ArSensInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            OutlinedTextField(
                value = state.newProjectName,
                onValueChange = { state.newProjectName = it },
                label = { Text("Projectnaam") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Trafo-afmetingen", color = ArSensInk, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                WorkflowNumberField("Lengte", state.lengthMm, { state.lengthMm = it }, Modifier.weight(1f))
                WorkflowNumberField("Breedte", state.widthMm, { state.widthMm = it }, Modifier.weight(1f))
                WorkflowNumberField("Hoogte", state.heightMm, { state.heightMm = it }, Modifier.weight(1f))
            }
            Button(onClick = state::createProject, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Project aanmaken")
            }
            Text(
                "Met STL-assembly? Maak het project aan en importeer de delen via de kaart " +
                    "„3D-model (assembly)” — de trafo-afmetingen worden dan van de tank overgenomen, " +
                    "tenzij je ze op het projectscherm vergrendelt.",
                color = ArSensMuted,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Trafo-afmetingen op het projectscherm: handmatig aan te passen, óók nadat een STL geladen is.
 * Met "Maten vergrendelen" laat STL-import / "Lijn uit op tank" de handmatige maten staan —
 * handig bij een afwijkende STL of wanneer je in een ander meetkader wilt werken.
 */
@Composable
internal fun WorkflowDimensionsCard(state: WorkflowAppState) {
    val dims = state.project.dimensionsMm
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ArSensSurface),
        border = BorderStroke(1.dp, ArSensLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Trafo-afmetingen",
                    color = ArSensInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${dims.x} × ${dims.y} × ${dims.z} mm",
                    color = ArSensMuted,
                    fontSize = 13.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                WorkflowNumberField("Lengte", state.lengthMm, { state.lengthMm = it }, Modifier.weight(1f))
                WorkflowNumberField("Breedte", state.widthMm, { state.widthMm = it }, Modifier.weight(1f))
                WorkflowNumberField("Hoogte", state.heightMm, { state.heightMm = it }, Modifier.weight(1f))
            }
            Button(onClick = state::applyManualDimensions, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                Text("Pas maten toe")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Maten vergrendelen", color = ArSensInk, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(
                        "STL-import en \"Lijn uit op tank\" overschrijven de maten dan niet.",
                        color = ArSensMuted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = state.project.dimensionsLocked,
                    onCheckedChange = state::setDimensionsLocked
                )
            }
        }
    }
}

/**
 * Assembly-kaart op het projectscherm: STL-delen importeren, automatisch uitlijnen op de tank
 * (projectafmetingen = tankafmetingen) en doorklikken naar de 3D-editor voor fijnafstelling.
 * Zelfde kaartstijl als de overige projectkaarten (wit, ink, ArSens-lijnen).
 */
@Composable
internal fun WorkflowAssemblyCard(state: WorkflowAppState) {
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) state.addStlModelsThenAskPlace(uris, scope)
    }
    LaunchedEffect(state.project.stlModels) { state.ensureStlMeshesLoaded() }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ArSensSurface),
        border = BorderStroke(1.dp, ArSensLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("3D-model (assembly)", color = ArSensInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            WorkflowStlLoadBar(state)
            if (state.project.stlModels.isEmpty()) {
                Text(
                    "Importeer de STL-delen (tank, deksel, kern…). De app neemt de tankafmetingen " +
                        "over als trafo-afmetingen en lijnt de delen automatisch uit.",
                    color = ArSensMuted,
                    fontSize = 13.sp
                )
            } else {
                state.project.stlModels.forEachIndexed { index, model ->
                    val mesh = state.stlMeshes[model.fileName]
                    val dims = if (mesh != null && !mesh.isEmpty) {
                        val ext = mesh.rotatedExtents(model.rotationDeg.x, model.rotationDeg.y, model.rotationDeg.z)
                        val s = model.scalePercent / 100f
                        "${model.role.label} · ${(ext[0] * s).roundToInt()} × ${(ext[1] * s).roundToInt()} ×" +
                            " ${(ext[2] * s).roundToInt()} mm · ${compactCount(mesh.totalTriangleCount)} driehoeken"
                    } else if (model.visible) {
                        "${model.role.label} · mesh wordt geladen…"
                    } else {
                        "${model.role.label} · uit — laadt zodra zichtbaar"
                    }
                    val rotation = model.rotationDeg
                    val transformText = buildString {
                        append("offset ${model.offsetMm.x}/${model.offsetMm.y}/${model.offsetMm.z}")
                        if (rotation.x != 0 || rotation.y != 0 || rotation.z != 0) {
                            append("\nrot ${rotation.x}/${rotation.y}/${rotation.z}°")
                        }
                        if (!model.visible) append("\nverborgen")
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            // Tik op een deel → 3D-editor (rol, offsets, rotatie, rendertechniek).
                            .clickable { state.navigateTo(WorkflowScreen.Stl) }
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(Color(stlPartColor(index)), RoundedCornerShape(50))
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                model.name,
                                color = ArSensInk,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(dims, color = ArSensMuted, fontSize = 12.sp)
                        }
                        Text(transformText, color = ArSensMuted, fontSize = 11.sp)
                    }
                }
            }
            // Importeren blijft hier; uitlijnen, 3D-editor, tags en AR-test zitten nu in de
            // 3D-weergave (kader-sheet) resp. de 3D-tegel/het menu. Tik op een onderdeel hierboven
            // om het in de 3D-weergave te bewerken.
            OutlinedButton(
                onClick = { picker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("Importeer STL's")
            }
        }
    }
}

@Composable
internal fun ArSensEmptyProjectCard() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ArSensSurface.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, ArSensLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ArSensLogoMark(Modifier.size(62.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Nog geen projecten", color = ArSensInk, fontWeight = FontWeight.Bold)
                Text("Maak je eerste ARsens-project aan.", color = ArSensMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
internal fun ArSensRecentProjectRow(
    summary: ProjectSummary,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ArSensSurface.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, ArSensLine),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArSensIconBadge(icon = ArSensGlyph.Building, tint = ArSensMuted, background = Color(0xFFF0F4FA), square = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(summary.name, color = ArSensInk, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Aangemaakt: ${formatProjectUpdatedAt(summary.updatedAtMillis)}", color = ArSensMuted, fontSize = 12.sp, maxLines = 1)
            }
            TextButton(onClick = onDelete) {
                ArSensIcon(ArSensGlyph.More, tint = ArSensInk, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
internal fun ArSensFeatureTile(
    title: String,
    body: String,
    icon: ArSensGlyph,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ArSensSurface.copy(alpha = 0.96f)),
        border = BorderStroke(1.dp, ArSensLine),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
    ) {
        if (horizontal) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                ArSensIconBadge(icon = icon, tint = tint, background = tint.copy(alpha = 0.10f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = ArSensInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(body, color = ArSensMuted, fontSize = 13.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ArSensIconBadge(icon = icon, tint = tint, background = tint.copy(alpha = 0.10f))
                Spacer(Modifier.height(14.dp))
                Text(title, color = ArSensInk, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(body, color = ArSensMuted, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun ArSensIconButton(
    icon: ArSensGlyph,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.82f),
        border = BorderStroke(1.dp, ArSensLine),
        shadowElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArSensIcon(icon = icon, tint = ArSensInk, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
internal fun ArSensIconBadge(
    icon: ArSensGlyph,
    tint: Color,
    background: Color,
    square: Boolean = false
) {
    Surface(
        modifier = Modifier.size(if (square) 62.dp else 68.dp),
        shape = RoundedCornerShape(if (square) 18.dp else 50.dp),
        color = background
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArSensIcon(icon = icon, tint = tint, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
internal fun ArSensLogoMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val blue = ArSensBlue
        val teal = ArSensTeal
        val stroke = size.minDimension * 0.055f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(blue, radius = size.minDimension * 0.045f, center = Offset(c.x, size.height * 0.10f))
        val hex = Path().apply {
            moveTo(c.x, size.height * 0.18f)
            lineTo(size.width * 0.78f, size.height * 0.34f)
            lineTo(size.width * 0.78f, size.height * 0.66f)
            lineTo(c.x, size.height * 0.84f)
            lineTo(size.width * 0.22f, size.height * 0.66f)
            lineTo(size.width * 0.22f, size.height * 0.34f)
            close()
        }
        drawPath(hex, Brush.linearGradient(listOf(blue, teal)), style = Stroke(width = stroke, cap = StrokeCap.Round))
        val top = Offset(c.x, size.height * 0.35f)
        val left = Offset(size.width * 0.36f, size.height * 0.45f)
        val right = Offset(size.width * 0.64f, size.height * 0.45f)
        val bottom = Offset(c.x, size.height * 0.62f)
        drawLine(blue, left, bottom, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(teal, right, bottom, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(Color(0xFF36AEEA), top, bottom, strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(blue.copy(alpha = 0.30f), Offset(size.width * 0.05f, c.y), Offset(size.width * 0.00f, c.y), strokeWidth = stroke, cap = StrokeCap.Round)
        drawArc(blue.copy(alpha = 0.80f), 135f, 90f, false, topLeft = Offset(size.width * 0.04f, size.height * 0.28f), size = Size(size.width * 0.28f, size.height * 0.44f), style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawArc(teal.copy(alpha = 0.80f), -45f, 90f, false, topLeft = Offset(size.width * 0.68f, size.height * 0.28f), size = Size(size.width * 0.28f, size.height * 0.44f), style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
internal fun ArSensIcon(
    icon: ArSensGlyph,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = (s * 0.095f).coerceAtLeast(2f)
        val thin = (s * 0.065f).coerceAtLeast(1.6f)
        when (icon) {
            ArSensGlyph.Back -> {
                drawLine(tint, Offset(s * 0.72f, s * 0.18f), Offset(s * 0.28f, s * 0.50f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(s * 0.28f, s * 0.50f), Offset(s * 0.72f, s * 0.82f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            ArSensGlyph.Bell -> {
                drawArc(tint, 200f, 140f, false, Offset(s * 0.24f, s * 0.16f), Size(s * 0.52f, s * 0.62f), style = Stroke(width = thin, cap = StrokeCap.Round))
                drawLine(tint, Offset(s * 0.24f, s * 0.64f), Offset(s * 0.76f, s * 0.64f), strokeWidth = thin, cap = StrokeCap.Round)
                drawCircle(tint, radius = s * 0.055f, center = Offset(s * 0.50f, s * 0.80f))
            }
            ArSensGlyph.Building -> {
                drawRoundRect(tint, Offset(s * 0.24f, s * 0.14f), Size(s * 0.52f, s * 0.74f), CornerRadius(s * 0.05f), style = Stroke(width = thin))
                repeat(3) { col ->
                    repeat(3) { row ->
                        drawRect(tint.copy(alpha = 0.75f), Offset(s * (0.34f + col * 0.13f), s * (0.26f + row * 0.16f)), Size(s * 0.055f, s * 0.065f))
                    }
                }
                drawLine(tint, Offset(s * 0.16f, s * 0.88f), Offset(s * 0.84f, s * 0.88f), strokeWidth = thin, cap = StrokeCap.Round)
            }
            ArSensGlyph.Checklist -> {
                drawRoundRect(tint, Offset(s * 0.24f, s * 0.15f), Size(s * 0.52f, s * 0.70f), CornerRadius(s * 0.08f), style = Stroke(width = thin))
                drawLine(tint, Offset(s * 0.34f, s * 0.48f), Offset(s * 0.44f, s * 0.58f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(s * 0.44f, s * 0.58f), Offset(s * 0.66f, s * 0.36f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(s * 0.38f, s * 0.24f), Offset(s * 0.62f, s * 0.24f), strokeWidth = thin, cap = StrokeCap.Round)
            }
            ArSensGlyph.Cube -> {
                val top = Path().apply {
                    moveTo(s * 0.50f, s * 0.12f)
                    lineTo(s * 0.82f, s * 0.30f)
                    lineTo(s * 0.50f, s * 0.48f)
                    lineTo(s * 0.18f, s * 0.30f)
                    close()
                }
                val left = Path().apply {
                    moveTo(s * 0.18f, s * 0.30f)
                    lineTo(s * 0.50f, s * 0.48f)
                    lineTo(s * 0.50f, s * 0.86f)
                    lineTo(s * 0.18f, s * 0.66f)
                    close()
                }
                val right = Path().apply {
                    moveTo(s * 0.82f, s * 0.30f)
                    lineTo(s * 0.50f, s * 0.48f)
                    lineTo(s * 0.50f, s * 0.86f)
                    lineTo(s * 0.82f, s * 0.66f)
                    close()
                }
                drawPath(top, tint)
                drawPath(left, tint.copy(alpha = 0.72f))
                drawPath(right, tint.copy(alpha = 0.90f))
            }
            ArSensGlyph.Document, ArSensGlyph.Report -> {
                drawRoundRect(tint, Offset(s * 0.26f, s * 0.14f), Size(s * 0.48f, s * 0.72f), CornerRadius(s * 0.05f), style = Stroke(width = thin))
                if (icon == ArSensGlyph.Report) {
                    repeat(3) { i ->
                        val h = s * (0.18f + i * 0.08f)
                        drawRoundRect(tint, Offset(s * (0.36f + i * 0.12f), s * (0.70f - h)), Size(s * 0.07f, h), CornerRadius(s * 0.02f))
                    }
                } else {
                    drawLine(tint, Offset(s * 0.36f, s * 0.36f), Offset(s * 0.64f, s * 0.36f), strokeWidth = thin, cap = StrokeCap.Round)
                    drawLine(tint, Offset(s * 0.36f, s * 0.52f), Offset(s * 0.64f, s * 0.52f), strokeWidth = thin, cap = StrokeCap.Round)
                    drawLine(tint, Offset(s * 0.36f, s * 0.68f), Offset(s * 0.56f, s * 0.68f), strokeWidth = thin, cap = StrokeCap.Round)
                }
            }
            ArSensGlyph.Folder -> {
                drawRoundRect(tint, Offset(s * 0.13f, s * 0.30f), Size(s * 0.74f, s * 0.54f), CornerRadius(s * 0.08f), style = Stroke(width = stroke))
                drawLine(tint, Offset(s * 0.18f, s * 0.30f), Offset(s * 0.36f, s * 0.30f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(s * 0.36f, s * 0.30f), Offset(s * 0.43f, s * 0.38f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            ArSensGlyph.Home -> {
                val roof = Path().apply {
                    moveTo(s * 0.16f, s * 0.46f)
                    lineTo(s * 0.50f, s * 0.18f)
                    lineTo(s * 0.84f, s * 0.46f)
                }
                drawPath(roof, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawRoundRect(tint, Offset(s * 0.26f, s * 0.44f), Size(s * 0.48f, s * 0.40f), CornerRadius(s * 0.06f), style = Stroke(width = stroke))
            }
            ArSensGlyph.More -> {
                drawCircle(tint, radius = s * 0.07f, center = Offset(s * 0.25f, s * 0.50f))
                drawCircle(tint, radius = s * 0.07f, center = Offset(s * 0.50f, s * 0.50f))
                drawCircle(tint, radius = s * 0.07f, center = Offset(s * 0.75f, s * 0.50f))
            }
            ArSensGlyph.Navigation -> {
                val arrow = Path().apply {
                    moveTo(s * 0.82f, s * 0.15f)
                    lineTo(s * 0.18f, s * 0.43f)
                    lineTo(s * 0.45f, s * 0.55f)
                    lineTo(s * 0.58f, s * 0.86f)
                    close()
                }
                drawPath(arrow, tint)
            }
            ArSensGlyph.Plus -> {
                drawLine(tint, Offset(s * 0.50f, s * 0.18f), Offset(s * 0.50f, s * 0.82f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(tint, Offset(s * 0.18f, s * 0.50f), Offset(s * 0.82f, s * 0.50f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
            ArSensGlyph.Sensor -> {
                drawCircle(tint, radius = s * 0.08f, center = Offset(s * 0.50f, s * 0.66f))
                drawArc(tint, 210f, 120f, false, Offset(s * 0.30f, s * 0.34f), Size(s * 0.40f, s * 0.40f), style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawArc(tint, 210f, 120f, false, Offset(s * 0.18f, s * 0.20f), Size(s * 0.64f, s * 0.64f), style = Stroke(width = thin, cap = StrokeCap.Round))
            }
            ArSensGlyph.User -> {
                drawCircle(tint, radius = s * 0.17f, center = Offset(s * 0.50f, s * 0.34f), style = Stroke(width = thin))
                drawArc(tint, 205f, 130f, false, Offset(s * 0.20f, s * 0.46f), Size(s * 0.60f, s * 0.46f), style = Stroke(width = thin, cap = StrokeCap.Round))
            }
        }
    }
}

internal fun formatProjectUpdatedAt(updatedAtMillis: Long?): String {
    val zone = ZoneId.systemDefault()
    val instant = Instant.ofEpochMilli(updatedAtMillis ?: System.currentTimeMillis())
    val dateTime = instant.atZone(zone)
    val today = LocalDate.now(zone)
    val dayLabel = when (dateTime.toLocalDate()) {
        today -> "vandaag"
        today.minusDays(1) -> "gisteren"
        else -> dateTime.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("nl-NL")))
    }
    return "$dayLabel ${dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
}

