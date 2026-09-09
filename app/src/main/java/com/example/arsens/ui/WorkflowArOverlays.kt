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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arsens.ar.CameraIntrinsics
import com.example.arsens.ar.ImageToViewMapper
import com.example.arsens.ar.OpenCvRuntime
import com.example.arsens.ar.Transform3D
import com.example.arsens.ar.TransformerPose
import com.example.arsens.data.StlModelTransform
import com.example.arsens.data.stlModelTransform
import com.example.arsens.data.triangleFullyOffscreen
import com.example.arsens.ar.AprilTagCorner
import com.example.arsens.ar.AprilTagFrameResult
import com.example.arsens.ar.ArCoreCameraPanel
import com.example.arsens.ar.ArPerfStats
import com.example.arsens.ar.ArTrackingStatus
import com.example.arsens.ar.PlacementQuality
import com.example.arsens.data.QualityGrade
import com.example.arsens.ar.PlaneHit
import com.example.arsens.ar.TagAnchor
import com.example.arsens.ar.TagPlane
import com.example.arsens.ar.estimateCursorOnReferenceSurface
import com.example.arsens.ar.estimateSurfaceAtPixel
import com.example.arsens.ar.markerCornersInProjectFrame
import com.example.arsens.ar.sensorTargetRing
import com.example.arsens.ar.tagPlaneOutwardNormal
import com.example.arsens.ar.nearestTankPlane
import com.example.arsens.ar.projectPointToScreen
import com.example.arsens.ar.projectPointWithFusedPoseToScreen
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
import com.example.arsens.data.displayName
import com.example.arsens.data.SensorStatus
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
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/** Gedeelde cameralaag voor de Tags- en Installatie-camera: ARCore-paneel + alle overlays +
 *  sleepbare plaatsings-cursor. [targetSensor] tekent extra het doelkruis van de actieve sensor. */
@Composable
internal fun WorkflowCameraLayers(state: WorkflowAppState, targetSensor: Sensor? = null) {
    ArCoreCameraPanel(
        knownMarkers = state.knownAprilTags,
        onResult = state::updateAprilTagResult,
        modifier = Modifier.fillMaxSize(),
        tagDictionary = state.tagDictionary,
        tagPoseMode = state.tagPoseMode,
        calibrationRevision = state.arCalibrationRevision,
        // Alleen state schrijven (= hercompositie) wanneer de HUD daadwerkelijk getoond wordt.
        onPerfStats = { if (state.showDebugHud) state.arPerfStats = it }
    )
    if (state.showTagOverlay) {
        WorkflowAprilTagOverlay(state.project, state.overlayAprilTagResult, state.sensorTagStartId)
    }
    if (state.showTagDistances) {
        WorkflowTagDistanceOverlay(state.overlayAprilTagResult)
    }
    if (state.showAxisOverlay) {
        WorkflowAxisOverlay(state.overlayAprilTagResult, state.project)
    }
    if (state.showMiniAxisOverlay) {
        WorkflowMiniAxisOverlay(
            result = state.overlayAprilTagResult,
            project = state.project,
            onClick = {}
        )
    }
    if (state.showBoxEdgesOverlay) {
        WorkflowBoxEdgesOverlay(state.project, state.overlayAprilTagResult)
    }
    if (state.showTagPoseLabels) {
        WorkflowTagPoseLabelOverlay(state.project, state.overlayAprilTagResult)
    }
    if (state.showStlOverlay) {
        WorkflowStlArOverlay(state)
    }
    // Sensoren ná het model tekenen, zodat geplaatste sensoren niet achter de AR-assembly wegvallen.
    if (state.showSensorOverlay) {
        val measuredById = state.log.results.associateBy { it.sensorId }
        val displayedProject = state.project.copy(sensors = state.project.sensors.map { sensor ->
            measuredById[sensor.id]?.takeIf { sensor.status != SensorStatus.Pending }?.let {
                sensor.copy(positionMm = it.measuredPositionMm ?: sensor.positionMm)
            } ?: sensor
        })
        WorkflowSensorPointOverlay(displayedProject, state.overlayAprilTagResult)
    }
    if (targetSensor != null) {
        WorkflowCurrentSensorTargetOverlay(targetSensor, state.project, state.overlayAprilTagResult)
    }
    WorkflowCursorOverlay(
        cursor = state.arCursorPosition,
        insideTransformer = state.arCursorInsideTransformer,
        label = "${targetSensor?.displayName() ?: state.cameraSensorLabel} · " +
            (if (targetSensor?.status?.let { it != SensorStatus.Pending } == true) "Opnieuw vastleggen" else state.cameraSensorAction),
        availabilityLabel = when {
            !state.sensorPlacementReady -> "AR-uitlijning nog niet gereed"
            state.arCursorPosition == null -> "Richt op ${state.selectedTagPlane.shortLabel}"
            !state.arCursorInsideTransformer -> "Buiten trafo"
            else -> state.operatorText(state.arCursorPosition)
        },
        offset = state.arCursorScreenOffset,
        onOffsetChange = { state.arCursorScreenOffset = it }
    )
    WorkflowPlacementQualityIndicator(state.placementQuality, state.correctionSettleProgress)
    if (state.showDebugHud) {
        WorkflowDebugHud(state.arPerfStats, state.overlayAprilTagResult)
    }
}

/** Trafo-box als wireframe: de 12 ribben van [Project.dimensionsMm] in het canonieke box-frame,
 *  geprojecteerd via dezelfde gefuseerde tag-pose als de assen. Volgt de camerabeweging en
 *  verdwijnt pas wanneer er helemaal geen pose is (geen geflikker tussen detecties). */
@Composable
internal fun WorkflowBoxEdgesOverlay(project: Project, result: AprilTagFrameResult) {
    val d = project.dimensionsMm
    Canvas(Modifier.fillMaxSize()) {
        val corners = arrayOf(
            MmPosition(0, 0, 0), MmPosition(d.x, 0, 0), MmPosition(d.x, d.y, 0), MmPosition(0, d.y, 0),
            MmPosition(0, 0, d.z), MmPosition(d.x, 0, d.z), MmPosition(d.x, d.y, d.z), MmPosition(0, d.y, d.z)
        )
        val pts = corners.map { c ->
            projectPositionToScreen(c, result, preferImagePose = false)?.let { Offset(it.xPx, it.yPx) }
        }
        // 12 ribben: ondervlak (z=0), bovenvlak (z=max), verticale verbindingen.
        val edges = arrayOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7
        )
        val color = Color(0xFF00E5C8)
        edges.forEach { (a, b) ->
            val pa = pts[a] ?: return@forEach
            val pb = pts[b] ?: return@forEach
            drawLine(color, pa, pb, strokeWidth = 4f, cap = StrokeCap.Round)
        }
        pts.forEach { p -> if (p != null) drawCircle(color, radius = 5f, center = p) }
    }
}

/** Per zichtbare/bekende AprilTag een label met de OPGESLAGEN box-positie en rotatie. De positie
 *  van het label volgt het gefuseerde tag-centrum (zelfde projectie als de tag-overlay). */
@Composable
internal fun WorkflowTagPoseLabelOverlay(project: Project, result: AprilTagFrameResult) {
    Canvas(Modifier.fillMaxSize()) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(160, 0, 0, 0)
        }
        project.markers
            .filter { it.isAprilTagCalibrationMarker() && it.active }
            .map { it.asAprilTagCalibrationMarker(project.dimensionsMm) }
            .forEach { marker ->
                val corners = projectMarkerCornersCurrentFused(marker, result).toOffsets()
                if (corners.size != 4) return@forEach
                val center = corners.centerOffset()
                val p = marker.positionMm
                val r = marker.rotationDeg
                val lines = listOf(
                    "T${marker.id}",
                    "X${p.x} Y${p.y} Z${p.z}",
                    "R ${r.x.roundToInt()},${r.y.roundToInt()},${r.z.roundToInt()}°"
                )
                val pad = 6f
                val lineH = 28f
                val maxW = lines.maxOf { textPaint.measureText(it) }
                val boxLeft = center.x + 18f
                val boxTop = center.y - 6f
                val nativeCanvas = drawContext.canvas.nativeCanvas
                nativeCanvas.drawRect(
                    boxLeft - pad, boxTop - pad,
                    boxLeft + maxW + pad, boxTop + lines.size * lineH + pad,
                    bgPaint
                )
                lines.forEachIndexed { i, line ->
                    nativeCanvas.drawText(line, boxLeft, boxTop + (i + 1) * lineH - 6f, textPaint)
                }
                drawCircle(Color(0xFFFFD54F), radius = 4f, center = center)
            }
    }
}

/** Compacte, niet-flikkerende debug-HUD linksboven op de camera: live FPS (overlay-cadans) +
 *  ARCore-Hz (cameraframe-cadans), plus tag-leeftijd en trackingstatus als context. Waarden komen
 *  uit [ArPerfStats] (~2×/sec ververst) en blijven staan tussen updates. */
@Composable
private fun WorkflowDebugHud(stats: ArPerfStats, result: AprilTagFrameResult) {
    val ageText = if (result.detectionAgeMillis == Long.MAX_VALUE) "—" else "${result.detectionAgeMillis} ms"
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 12.dp, top = 96.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("DEBUG", color = Color(0xFF00E5C8), fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("ARCore  ${stats.arCoreHz.roundToInt()} Hz", color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            Text("FPS     ${stats.uiHz.roundToInt()}", color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            Text("tag     $ageText", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Text("track   ${result.trackingStatus.name}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/** Compacte kwaliteits-indicator rechts op het camerabeeld: een klein pilletje in de grade-kleur
 *  (groen = goed/stabiel, oranje = redelijk, rood = onnauwkeurig, grijs = geen pose). Tijdens een
 *  straal-replay-correctie wordt het een voortgangsbalk die in ~1 s volloopt (rustig anker op de
 *  tag); vol → de sensor wordt op dat moment bijgesteld (zie ook de save-melding). Geen tekst. */
@Composable
private fun WorkflowPlacementQualityIndicator(quality: PlacementQuality?, correctionSettleProgress: Float?) {
    val color = when (quality?.grade) {
        QualityGrade.High -> Color(0xFF2E7D32)
        QualityGrade.Medium -> Color(0xFFEF6C00)
        QualityGrade.Low -> Color(0xFFC62828)
        QualityGrade.Unsafe, null -> Color(0xFF9E9E9E)
    }
    Canvas(Modifier.fillMaxSize()) {
        val w = 24.dp.toPx()
        val h = 8.dp.toPx()
        val margin = 12.dp.toPx()
        val left = size.width - margin - w
        val top = size.height * 0.5f - h / 2f
        val topLeft = Offset(left, top)
        val radius = CornerRadius(h / 2f, h / 2f)
        // Donker randje voor contrast op een willekeurige camera-achtergrond.
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.45f),
            topLeft = Offset(left - 1.5f, top - 1.5f),
            size = Size(w + 3f, h + 3f),
            cornerRadius = CornerRadius((h + 3f) / 2f, (h + 3f) / 2f)
        )
        if (correctionSettleProgress != null) {
            // Track (dim) + voortgangsvulling die met de settle-timer volloopt.
            drawRoundRect(color = color.copy(alpha = 0.30f), topLeft = topLeft, size = Size(w, h), cornerRadius = radius)
            val fillW = (w * correctionSettleProgress.coerceIn(0f, 1f)).coerceAtLeast(h)
            drawRoundRect(color = color, topLeft = topLeft, size = Size(fillW, h), cornerRadius = radius)
        } else {
            drawRoundRect(color = color, topLeft = topLeft, size = Size(w, h), cornerRadius = radius)
        }
    }
}

@Composable
private fun WorkflowTagDistanceOverlay(result: AprilTagFrameResult) {
    Canvas(Modifier.fillMaxSize()) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16.dp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = android.graphics.Color.WHITE
            setShadowLayer(4.dp.toPx(), 0f, 0f, android.graphics.Color.BLACK)
        }
        result.trackedScreenDetections.forEach { detection ->
            val distance = result.tagDistancesMm[detection.id] ?: return@forEach
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.getDefault(), "≈ %.2f m", distance / 1000f),
                detection.centerPx.xPx + 12.dp.toPx(),
                detection.centerPx.yPx + 24.dp.toPx(), textPaint
            )
        }
    }
}

@Composable
internal fun WorkflowAprilTagOverlay(
    project: Project,
    result: AprilTagFrameResult,
    sensorTagStartId: Int = Int.MAX_VALUE
) {
    WorkflowAprilTagProjectionDiagnostics(project, result)
    Canvas(Modifier.fillMaxSize()) {
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val referenceIds = result.poseMarkerIds.toSet()
        val currentFusedColor = Color(0xFFB64DFF)
        val freshImageColor = Color(0xFFFFD54F)
        val candidateFusedColor = Color(0xFFFF5252)
        val liveDetectionColor = Color(0xFF00E5FF)
        // Sensor-tags (ID ≥ sensorTagStartId) krijgen bewust een andere kleur (groen) dan de
        // referentietags (cyaan), zodat in beeld direct te zien is wat een sensor is.
        val sensorTagColor = Color(0xFF00E676)
        val trackedById = result.trackedScreenDetections.associateBy { it.id }
        val liveDetections = if (result.detectionAgeMillis <= DEBUG_DETECTION_HOLD_MILLIS) {
            result.screenDetections.map { trackedById[it.id] ?: it }
        } else {
            emptyList()
        }
        val hasProjectedTagPose =
            result.hasOverlayPose() ||
                result.candidateDisplayProjection != null ||
                result.freshImageProjectionPose != null ||
                result.freshPosePerTag.isNotEmpty()
        if (hasProjectedTagPose) {
            project.markers
                .filter { it.isAprilTagCalibrationMarker() && it.active }
                .map { it.asAprilTagCalibrationMarker(project.dimensionsMm) }
                .forEach { marker ->
                    val currentFusedPoints = projectMarkerCornersCurrentFused(marker, result).toOffsets()
                    // Capture-space routes cannot be overlaid as if they were current pixels.
                    val freshImagePoints = if (result.displayProjection == null && result.candidateDisplayProjection == null)
                        projectMarkerCornersFreshImage(marker, result).toOffsets() else emptyList()
                    val candidateFusedPoints = projectMarkerCornersCandidateFused(marker, result).toOffsets()
                    drawCornerRoute(currentFusedPoints, currentFusedColor, if (marker.id in referenceIds) 7f else 4f, 6f, true)
                    drawCornerRoute(freshImagePoints, freshImageColor, 3f, 5f, false)
                    drawCornerRoute(candidateFusedPoints, candidateFusedColor, 3.5f, 5f, false)
                    if (currentFusedPoints.size == 4) {
                        val center = currentFusedPoints.centerOffset()
                        drawCircle(currentFusedColor, radius = 10f, center = center)
                        drawCircle(Color.White, radius = 15f, center = center, style = Stroke(width = 3f))
                        drawContext.canvas.nativeCanvas.drawText(
                            "Tag ${marker.id} fused",
                            center.x + 16f,
                            center.y - 16f,
                            labelPaint
                        )
                    }
                }
        }

        liveDetections.forEach { detection ->
            val points = detection.cornersPx.map { Offset(it.xPx, it.yPx) }
            if (points.size != 4) return@forEach
            val isKnown = project.markers.any { it.isAprilTagCalibrationMarker() && it.active && it.id == detection.id }
            val isReference = detection.id in referenceIds
            val isSensorTag = detection.id >= sensorTagStartId
            val color = when {
                isSensorTag -> sensorTagColor
                isReference || isKnown -> liveDetectionColor
                else -> Color(0xFFFFB020)
            }
            points.forEachIndexed { index, start ->
                drawLine(
                    color = color,
                    start = start,
                    end = points[(index + 1) % points.size],
                    strokeWidth = if (isReference) 9f else 6f,
                    cap = StrokeCap.Round
                )
                drawCircle(color, radius = 7f, center = start)
                drawCircle(Color.Black.copy(alpha = 0.65f), radius = 10f, center = start, style = Stroke(width = 2f))
            }
            val center = Offset(detection.centerPx.xPx, detection.centerPx.yPx)
            drawCircle(color, radius = 12f, center = center)
            drawCircle(Color.White, radius = 17f, center = center, style = Stroke(width = 3f))
            drawContext.canvas.nativeCanvas.drawText(
                when {
                    isSensorTag -> "Sensor-tag ${detection.id}"
                    detection.id in trackedById -> "Ref ${detection.id} gemeten"
                    isKnown -> "Tag ${detection.id} opname ${result.detectionAgeMillis} ms"
                    else -> "Tag ${detection.id} nieuw"
                },
                center.x + 16f,
                center.y - 16f,
                labelPaint
            )
        }
    }
}

private fun DrawScope.drawCornerRoute(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    cornerRadius: Float,
    drawWhiteRing: Boolean
) {
    if (points.size != 4) return
    points.forEachIndexed { index, start ->
        drawLine(
            color = color,
            start = start,
            end = points[(index + 1) % points.size],
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawCircle(color, radius = cornerRadius, center = start)
        if (drawWhiteRing) {
            drawCircle(Color.White, radius = cornerRadius + 3f, center = start, style = Stroke(width = 2f))
        }
    }
}

private fun List<AprilTagCorner>.toOffsets(): List<Offset> =
    map { Offset(it.xPx, it.yPx) }

private fun List<Offset>.centerOffset(): Offset =
    Offset(
        x = sumOf { it.x.toDouble() }.toFloat() / size.coerceAtLeast(1),
        y = sumOf { it.y.toDouble() }.toFloat() / size.coerceAtLeast(1)
    )

@Composable
private fun WorkflowAprilTagProjectionDiagnostics(project: Project, result: AprilTagFrameResult) {
    val visibleKnownIds = result.screenDetections
        .map { it.id }
        .distinct()
        .sorted()
        .joinToString(",")
    // Stap 2: result.detectionAgeMillis NIET als key gebruiken — die loopt elke frame op en zou
    // de effect (en zijn coroutine) elke frame herstarten. De overige keys dekken betekenisvolle
    // wijzigingen; de interne logregel blijft per tag gethrottled via TagProjectionDebugLogThrottle.
    LaunchedEffect(
        project.markers,
        visibleKnownIds,
        result.displayProjection,
        result.candidateDisplayProjection,
        result.transformerPose,
        result.imageProjectionPose,
        result.freshImageProjectionPose,
        result.freshPosePerTag,
        result.fusionEvent,
        result.fusionReason
    ) {
        logVisibleKnownTagProjectionDiagnostics(project, result)
    }
}

private fun logVisibleKnownTagProjectionDiagnostics(project: Project, result: AprilTagFrameResult) {
    val markersById = project.markers
        .filter { it.isAprilTagCalibrationMarker() && it.active }
        .map { it.asAprilTagCalibrationMarker(project.dimensionsMm) }
        .associateBy { it.id }
    val now = System.currentTimeMillis()
    result.screenDetections.forEach { detection ->
        val marker = markersById[detection.id] ?: return@forEach
        if (!TagProjectionDebugLogThrottle.shouldLog(marker.id, now)) return@forEach

        val freshImageCorners = projectMarkerCornersFreshImage(marker, result)
        val captureFusedCorners = projectMarkerCornersAtCapture(marker, result)
        val currentFusedCorners = projectMarkerCornersCurrentFused(marker, result)
        val candidateFusedCorners = projectMarkerCornersCandidateFused(marker, result)
        val trackedCorners = result.trackedScreenDetections.firstOrNull { it.id == marker.id }?.cornersPx
        val compareLiveCorners = result.detectionAgeMillis <= LIVE_DETECTION_FRESH_MILLIS
        val freshDelta = if (compareLiveCorners) {
            cornerDeltaStats(detection.cornersPx, freshImageCorners)
        } else {
            CornerDeltaStats(averagePx = null, maxPx = null)
        }
        val captureDelta = if (compareLiveCorners) {
            cornerDeltaStats(detection.cornersPx, captureFusedCorners)
        } else {
            CornerDeltaStats(averagePx = null, maxPx = null)
        }
        val currentDelta = if (compareLiveCorners && trackedCorners != null) {
            cornerDeltaStats(trackedCorners, currentFusedCorners)
        } else {
            CornerDeltaStats(averagePx = null, maxPx = null)
        }
        val candidateDelta = if (compareLiveCorners && trackedCorners != null) {
            cornerDeltaStats(trackedCorners, candidateFusedCorners)
        } else {
            CornerDeltaStats(averagePx = null, maxPx = null)
        }
        Log.i(
            "ARSensTagDebug",
            "tag=${marker.id} captureCorners=${detection.cornersPx.formatCornerList()} " +
                "captureState=${if (compareLiveCorners) "fresh" else "stale-debug-only"} " +
                "trackedCorners=${trackedCorners?.formatCornerListOrDash() ?: "-"} " +
                "freshImageCorners=${freshImageCorners.formatCornerListOrDash()} " +
                "currentFusedCorners=${currentFusedCorners.formatCornerListOrDash()} " +
                "candidateFusedCorners=${candidateFusedCorners.formatCornerListOrDash()} " +
                "avgDeltaFreshImageVsCapture=${freshDelta.formatAverage()} maxDeltaFreshImageVsCapture=${freshDelta.formatMax()} " +
                "avgDeltaFusedVsCapture=${captureDelta.formatAverage()} maxDeltaFusedVsCapture=${captureDelta.formatMax()} " +
                "avgDeltaCurrentFusedVsTracked=${currentDelta.formatAverage()} maxDeltaCurrentFusedVsTracked=${currentDelta.formatMax()} " +
                "avgDeltaCandidateVsTracked=${candidateDelta.formatAverage()} maxDeltaCandidateVsTracked=${candidateDelta.formatMax()} " +
                "transformerPose=${result.transformerPose.formatPose()} " +
                "displayProjection=${result.displayProjection != null} " +
                "candidateDisplayProjection=${result.candidateDisplayProjection != null} " +
                "imageProjectionPose=${result.imageProjectionPose != null} " +
                "freshImageProjectionPose=${result.freshImageProjectionPose != null} " +
                "detectionAgeMillis=${result.detectionAgeMillis} " +
                "fusion=${result.fusionEvent ?: "-"}:${result.fusionReason ?: "-"}"
        )
    }
}

private fun projectMarkerCornersAtCapture(marker: Marker, result: AprilTagFrameResult): List<AprilTagCorner> {
    if (result.detectionAgeMillis > PER_TAG_POSE_FRESH_MILLIS || result.imageProjectionPose == null) return emptyList()
    val mapper = result.imageToViewMapper ?: return emptyList()
    return markerCornersInProjectFrame(marker).mapNotNull { projectPointToImage(it, result)?.let(mapper::map) }
}

private fun projectMarkerCornersFreshImage(marker: Marker, result: AprilTagFrameResult): List<AprilTagCorner> {
    if (result.detectionAgeMillis > PER_TAG_POSE_FRESH_MILLIS) return emptyList()
    val mapper = result.imageToViewMapper ?: return emptyList()
    val pose = result.freshPosePerTag[marker.id]
        ?: result.freshImageProjectionPose
        ?: result.imageProjectionPose
        ?: return emptyList()
    val poseResult = result.copy(imageProjectionPose = pose)
    return markerCornersInProjectFrame(marker).mapNotNull { corner ->
        projectPointToImage(corner, poseResult)?.let(mapper::map)
    }
}

private fun projectMarkerCornersCurrentFused(marker: Marker, result: AprilTagFrameResult): List<AprilTagCorner> =
    markerCornersInProjectFrame(marker).mapNotNull { corner ->
        projectPointWithFusedPoseToScreen(corner, result)?.let { AprilTagCorner(it.xPx, it.yPx) }
    }

private fun projectMarkerCornersCandidateFused(marker: Marker, result: AprilTagFrameResult): List<AprilTagCorner> =
    markerCornersInProjectFrame(marker).mapNotNull { corner ->
        result.candidateDisplayProjection?.project(corner)?.let { AprilTagCorner(it.xPx, it.yPx) }
    }

private object TagProjectionDebugLogThrottle {
    private val lastLogMillisByTag = HashMap<Int, Long>()

    fun shouldLog(tagId: Int, nowMillis: Long): Boolean {
        val last = lastLogMillisByTag[tagId] ?: 0L
        if (nowMillis - last < TAG_PROJECTION_DEBUG_LOG_INTERVAL_MILLIS) return false
        lastLogMillisByTag[tagId] = nowMillis
        return true
    }
}

private data class CornerDeltaStats(
    val averagePx: Double?,
    val maxPx: Double?
) {
    fun formatAverage(): String = averagePx?.shortPxText() ?: "-"
    fun formatMax(): String = maxPx?.shortPxText() ?: "-"
}

private fun cornerDeltaStats(liveCorners: List<AprilTagCorner>, projectedCorners: List<AprilTagCorner>): CornerDeltaStats {
    if (liveCorners.size != 4 || projectedCorners.size != 4) {
        return CornerDeltaStats(averagePx = null, maxPx = null)
    }
    val deltas = liveCorners.zip(projectedCorners).map { (live, projected) ->
        hypot(
            (live.xPx - projected.xPx).toDouble(),
            (live.yPx - projected.yPx).toDouble()
        )
    }
    return CornerDeltaStats(
        averagePx = deltas.average(),
        maxPx = deltas.maxOrNull() ?: 0.0
    )
}

private fun List<AprilTagCorner>.formatCornerListOrDash(): String =
    takeIf { size == 4 }?.formatCornerList() ?: "-"

private fun List<AprilTagCorner>.formatCornerList(): String =
    joinToString(prefix = "[", postfix = "]") { corner ->
        "(${corner.xPx.shortCoordText()},${corner.yPx.shortCoordText()})"
    }

private fun TransformerPose?.formatPose(): String =
    this?.let { pose ->
        "t=${pose.translationMm.joinToString(prefix = "[", postfix = "]") { it.shortCoordText() }} " +
            "r=${pose.rotationVector.joinToString(prefix = "[", postfix = "]") { it.shortCoordText() }} " +
            "err=${pose.reprojectionErrorPx.shortPxText()}"
    } ?: "-"

private fun Float.shortCoordText(): String =
    "${((this * 10f).roundToInt() / 10f)}"

private fun Float.shortPxText(): String =
    "${((this * 10f).roundToInt() / 10f)}px"

private fun Double.shortPxText(): String =
    "${((this * 10.0).roundToInt() / 10.0)}px"

private const val TAG_PROJECTION_DEBUG_LOG_INTERVAL_MILLIS = 750L

/** Stap 7: gethrottlede debug-timing van het STL-tekenpad. Alleen actief bij
 *  `Log.isLoggable("ARSensStlPerf", DEBUG)`; meet, verlaagt nooit budgets. */
private object StlArPerfLogThrottle {
    private var lastLogMillis = 0L

    fun maybeLog(
        visibleParts: Int,
        chunks: Int,
        triangles: Int,
        mode: StlArRenderMode,
        elapsedNanos: Long
    ) {
        val now = System.currentTimeMillis()
        if (now - lastLogMillis < STL_AR_PERF_LOG_INTERVAL_MILLIS) return
        lastLogMillis = now
        Log.d(
            STL_AR_PERF_TAG,
            "mode=$mode visibleParts=$visibleParts chunks=$chunks triangles=$triangles " +
                "tookUs=${elapsedNanos / 1000}"
        )
    }
}

private const val STL_AR_PERF_TAG = "ARSensStlPerf"
private const val STL_AR_PERF_LOG_INTERVAL_MILLIS = 1_000L

@Composable
internal fun WorkflowAxisOverlay(result: AprilTagFrameResult, project: Project) {
    Canvas(Modifier.fillMaxSize()) {
        val mapper = project.coordinateMapper()
        val originBox = mapper.operatorToBox(MmPosition(0, 0, 0))
        val xBox = mapper.operatorToBox(MmPosition(1000, 0, 0))
        val yBox = mapper.operatorToBox(MmPosition(0, 1000, 0))
        val zBox = mapper.operatorToBox(MmPosition(0, 0, 1000))
        val originPx = projectPositionToScreen(originBox, result, preferImagePose = false) ?: return@Canvas
        val xPx = projectPositionToScreen(xBox, result, preferImagePose = false) ?: return@Canvas
        val yPx = projectPositionToScreen(yBox, result, preferImagePose = false) ?: return@Canvas
        val zPx = projectPositionToScreen(zBox, result, preferImagePose = false) ?: return@Canvas
        val origin = Offset(originPx.xPx, originPx.yPx)
        val xEnd = Offset(xPx.xPx, xPx.yPx)
        val yEnd = Offset(yPx.xPx, yPx.yPx)
        val zEnd = Offset(zPx.xPx, zPx.yPx)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        drawLine(Color(0xFFE53935), origin, xEnd, strokeWidth = 7f, cap = StrokeCap.Round)
        drawLine(Color(0xFF43A047), origin, yEnd, strokeWidth = 7f, cap = StrokeCap.Round)
        drawLine(Color(0xFF1E88E5), origin, zEnd, strokeWidth = 7f, cap = StrokeCap.Round)
        drawContext.canvas.nativeCanvas.drawText("X", xEnd.x + 8f, xEnd.y + 8f, paint)
        drawContext.canvas.nativeCanvas.drawText("Y", yEnd.x + 8f, yEnd.y - 4f, paint)
        drawContext.canvas.nativeCanvas.drawText("Z", zEnd.x - 6f, zEnd.y - 10f, paint)
    }
}

@Composable
internal fun WorkflowMiniAxisOverlay(
    result: AprilTagFrameResult,
    project: Project,
    onClick: () -> Unit
) {
    val axisLength = 44f
    val mapper = project.coordinateMapper()
    val originPx = projectPositionToScreen(mapper.operatorToBox(MmPosition(0, 0, 0)), result, preferImagePose = false)
    val xPx = projectPositionToScreen(mapper.operatorToBox(MmPosition(1000, 0, 0)), result, preferImagePose = false)
    val yPx = projectPositionToScreen(mapper.operatorToBox(MmPosition(0, 1000, 0)), result, preferImagePose = false)
    val zPx = projectPositionToScreen(mapper.operatorToBox(MmPosition(0, 0, 1000)), result, preferImagePose = false)
    fun direction(end: com.example.arsens.ar.ScreenPointPx?): Offset? {
        if (originPx == null || end == null) return null
        val dx = end.xPx - originPx.xPx
        val dy = end.yPx - originPx.yPx
        val length = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(1f)
        return Offset(dx / length * axisLength, dy / length * axisLength)
    }
    val liveDirections = direction(xPx)?.let { xDir ->
        direction(yPx)?.let { yDir ->
            direction(zPx)?.let { zDir -> Triple(xDir, yDir, zDir) }
        }
    }
    var heldDirections by remember(project.projectName) { mutableStateOf<Triple<Offset, Offset, Offset>?>(null) }
    LaunchedEffect(liveDirections) {
        if (liveDirections != null) heldDirections = liveDirections
    }
    val directions = liveDirections ?: heldDirections ?: Triple(
        Offset(axisLength, 0f),
        Offset(0f, axisLength),
        Offset(0f, -axisLength)
    )
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val anchor = Offset(68f, size.height - 118f)
            val xDir = directions.first
            val yDir = directions.second
            val zDir = directions.third
            drawCircle(Color.Black.copy(alpha = 0.55f), radius = 58f, center = anchor)
            drawLine(Color(0xFFE53935), anchor, anchor + xDir, strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(Color(0xFF43A047), anchor, anchor + yDir, strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(Color(0xFF1E88E5), anchor, anchor + zDir, strokeWidth = 6f, cap = StrokeCap.Round)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            drawContext.canvas.nativeCanvas.drawText("X", anchor.x + xDir.x + 7f, anchor.y + xDir.y + 7f, paint)
            drawContext.canvas.nativeCanvas.drawText("Y", anchor.x + yDir.x + 7f, anchor.y + yDir.y + 7f, paint)
            drawContext.canvas.nativeCanvas.drawText("Z", anchor.x + zDir.x + 7f, anchor.y + zDir.y + 7f, paint)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 58.dp)
                .size(118.dp)
                .clickable(onClick = onClick)
        )
    }
}

// ===== AR-meshrendering — tekentijd-tunables (de driehoekenbudgetten staan in StlArMeshCache.kt) =====

/** Punten dichter dan dit vóór de camera (mm) worden geclipt. */
private const val STL_AR_NEAR_MM = 1.0

/** Schermmarge voor zichtbaarheidstests: ruim, zodat een driehoek/rand die het beeld in steekt
 *  nooit te vroeg wegvalt. Verhoogd (was 300) zodat chunks aan de schermrand minder in/uit beeld
 *  wippen tijdens pannen — scheelt zichtbaar geflikker bij camerabeweging. */
private const val STL_AR_SCREEN_MARGIN_PX = 700f

/** Lijnbudgetten per chunk per frame (de featureLines zijn lang→kort gesorteerd). */
private const val STL_AR_FAST_LINES = 600
private const val STL_AR_TECH_LINES = 9_000
private const val STL_AR_FILL_EDGE_LINES = 2_200

/** Backface-culling alleen bij 100% dekking: bij transparantie wil je juist dóór het model
 *  kijken, en zonder z-buffer geeft culling daar geen winst maar wél verdwenen achterkanten. */
private const val STL_AR_CULL_OPAQUE_BACKFACES = true

/** Gecombineerde model→camera-transform van één deel voor één frame (rij-major 3×4 in [m]).
 *  Offset/rotatie/schaal van het deel zitten hierin gevouwen — de meshcache zelf blijft in
 *  model-mm en hoeft bij bewerken dus nooit opnieuw gebouwd te worden. [nrot] is de zuivere
 *  rotatie (3×3) om vlaknormalen naar camera-assen te brengen voor shading. */
private class StlArFrame(val m: DoubleArray, val nrot: FloatArray)

private fun stlArFrameFor(cam: DoubleArray, xf: StlModelTransform): StlArFrame {
    val m = DoubleArray(12)
    val nrot = FloatArray(9)
    for (r in 0 until 3) {
        for (c in 0 until 3) {
            var rot = 0.0
            for (k in 0 until 3) rot += cam[r * 4 + k] * xf.r[k * 3 + c]
            nrot[r * 3 + c] = rot.toFloat()
            m[r * 4 + c] = xf.s * rot
        }
        m[r * 4 + 3] = cam[r * 4] * xf.tx + cam[r * 4 + 1] * xf.ty +
            cam[r * 4 + 2] * xf.tz + cam[r * 4 + 3]
    }
    return StlArFrame(m, nrot)
}

/** Camera-intrinsics + beeld→scherm-mapping samengevouwen tot één affiene stap van
 *  (camX/camZ, camY/camZ) naar schermpixels — allocatievrij per hoekpunt. */
private class StlArScreenMap(
    val x0: Float, val xx: Float, val xy: Float,
    val y0: Float, val yx: Float, val yy: Float
)

private fun stlArScreenMap(intrinsics: CameraIntrinsics, mapper: ImageToViewMapper): StlArScreenMap {
    val w = mapper.imageWidth.coerceAtLeast(1).toFloat()
    val h = mapper.imageHeight.coerceAtLeast(1).toFloat()
    val ax = (mapper.topRight.xPx - mapper.topLeft.xPx) / w
    val ay = (mapper.bottomLeft.xPx - mapper.topLeft.xPx) / h
    val bx = (mapper.topRight.yPx - mapper.topLeft.yPx) / w
    val by = (mapper.bottomLeft.yPx - mapper.topLeft.yPx) / h
    return StlArScreenMap(
        x0 = mapper.topLeft.xPx + ax * intrinsics.cx + ay * intrinsics.cy,
        xx = ax * intrinsics.fx,
        xy = ay * intrinsics.fy,
        y0 = mapper.topLeft.yPx + bx * intrinsics.cx + by * intrinsics.cy,
        yx = bx * intrinsics.fx,
        yy = by * intrinsics.fy
    )
}

/** Projecteert alle hoekpunten van een chunk naar schermpixels ([StlArChunk.scratchView]) en
 *  camera-diepte ([StlArChunk.scratchZ], NaN = achter de camera). Eén keer per chunk per frame;
 *  alle driehoeken en randen van de chunk lezen daarna uit dezelfde projectie. */
private fun projectStlArChunk(chunk: StlArChunk, frame: StlArFrame, sm: StlArScreenMap) {
    val p = chunk.positions
    val view = chunk.scratchView
    val zs = chunk.scratchZ
    val m = frame.m
    var v = 0
    var i = 0
    while (i + 2 < p.size) {
        val x = p[i].toDouble(); val y = p[i + 1].toDouble(); val z = p[i + 2].toDouble()
        val cz = m[8] * x + m[9] * y + m[10] * z + m[11]
        if (cz <= STL_AR_NEAR_MM) {
            zs[v] = Float.NaN
        } else {
            val nx = ((m[0] * x + m[1] * y + m[2] * z + m[3]) / cz).toFloat()
            val ny = ((m[4] * x + m[5] * y + m[6] * z + m[7]) / cz).toFloat()
            view[v * 2] = sm.x0 + sm.xx * nx + sm.xy * ny
            view[v * 2 + 1] = sm.y0 + sm.yx * nx + sm.yy * ny
            zs[v] = cz.toFloat()
        }
        v++
        i += 3
    }
}

/** Grove frustum-test op een model-space bbox: false zodra alle 8 hoekpunten achter de camera
 *  liggen of allemaal aan dezelfde kant buiten beeld vallen. Conservatief: zodra een hoekpunt
 *  achter de camera ligt is de zijkant-test onbetrouwbaar en wordt er niet zij-gecullld. */
private fun stlArBoundsVisible(
    bounds: FloatArray,
    frame: StlArFrame,
    sm: StlArScreenMap,
    width: Float,
    height: Float,
    margin: Float
): Boolean {
    val m = frame.m
    var anyInFront = false
    var allLeft = true; var allRight = true; var allUp = true; var allDown = true
    for (corner in 0 until 8) {
        val x = (if (corner and 1 == 0) bounds[0] else bounds[3]).toDouble()
        val y = (if (corner and 2 == 0) bounds[1] else bounds[4]).toDouble()
        val z = (if (corner and 4 == 0) bounds[2] else bounds[5]).toDouble()
        val cz = m[8] * x + m[9] * y + m[10] * z + m[11]
        if (cz <= STL_AR_NEAR_MM) {
            // De box doorsnijdt mogelijk het beeldvlak — zijkant-test wordt dan onbetrouwbaar.
            allLeft = false; allRight = false; allUp = false; allDown = false
            continue
        }
        anyInFront = true
        val nx = ((m[0] * x + m[1] * y + m[2] * z + m[3]) / cz).toFloat()
        val ny = ((m[4] * x + m[5] * y + m[6] * z + m[7]) / cz).toFloat()
        val vx = sm.x0 + sm.xx * nx + sm.xy * ny
        val vy = sm.y0 + sm.yx * nx + sm.yy * ny
        if (vx >= -margin) allLeft = false
        if (vx <= width + margin) allRight = false
        if (vy >= -margin) allUp = false
        if (vy <= height + margin) allDown = false
    }
    return anyInFront && !allLeft && !allRight && !allUp && !allDown
}

/** Tekent een chunk als gevulde driehoeken via Canvas.drawVertices. Er is geen z-buffer →
 *  painter's algorithm: bucket-sort op camera-afstand, ver → dichtbij (O(n), "grofweg" is
 *  voldoende voor alpha-compositing). Flat shading per vlak; backface-culling alleen waar de
 *  chunk dat toestaat (gesloten componenten) én de weergave volledig dekkend is. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStlArChunkFill(
    chunk: StlArChunk,
    frame: StlArFrame,
    paint: android.graphics.Paint,
    baseColorArgb: Int,
    alpha: Int,
    cullBackfaces: Boolean
) {
    val triCount = chunk.triangleCount
    if (triCount == 0) return
    val idx = chunk.indices
    val view = chunk.scratchView
    val zs = chunk.scratchZ
    val triZ = chunk.scratchTriZ
    val triColor = chunk.scratchTriColor
    val normals = chunk.faceNormals
    val nrot = frame.nrot
    val width = size.width
    val height = size.height
    val margin = STL_AR_SCREEN_MARGIN_PX
    val baseR = (baseColorArgb shr 16) and 0xFF
    val baseG = (baseColorArgb shr 8) and 0xFF
    val baseB = baseColorArgb and 0xFF
    val alphaBits = alpha.coerceIn(0, 255) shl 24
    var visible = 0
    var minZ = Float.MAX_VALUE
    var maxZ = -Float.MAX_VALUE
    for (t in 0 until triCount) {
        triZ[t] = Float.NaN
        val a = idx[t * 3]; val b = idx[t * 3 + 1]; val c = idx[t * 3 + 2]
        val za = zs[a]; val zb = zs[b]; val zc = zs[c]
        if (za.isNaN() || zb.isNaN() || zc.isNaN()) continue
        val x0 = view[a * 2]; val y0 = view[a * 2 + 1]
        val x1 = view[b * 2]; val y1 = view[b * 2 + 1]
        val x2 = view[c * 2]; val y2 = view[c * 2 + 1]
        if (triangleFullyOffscreen(x0, y0, x1, y1, x2, y2, width, height, margin)) continue
        if (cullBackfaces && chunk.cullableTriangles[t]) {
            // Schermwinding is het perspectief-correcte facing-criterium (y wijst omlaag:
            // een naar de camera gekeerde buitenkant heeft negatieve getekende oppervlakte).
            val area2 = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if (area2 >= 0f) continue
        }
        val nz = nrot[6] * normals[t * 3] + nrot[7] * normals[t * 3 + 1] + nrot[8] * normals[t * 3 + 2]
        val intensity = 0.55f + 0.45f * kotlin.math.abs(nz)
        triColor[t] = alphaBits or
            ((baseR * intensity).toInt().coerceIn(0, 255) shl 16) or
            ((baseG * intensity).toInt().coerceIn(0, 255) shl 8) or
            (baseB * intensity).toInt().coerceIn(0, 255)
        val mean = (za + zb + zc) / 3f
        triZ[t] = mean
        if (mean < minZ) minZ = mean
        if (mean > maxZ) maxZ = mean
        visible++
    }
    if (visible == 0) return
    // Counting sort in dieptelagen, ver → dichtbij.
    val buckets = chunk.scratchBuckets
    java.util.Arrays.fill(buckets, 0)
    val lastBucket = STL_AR_DEPTH_BUCKETS - 1
    val range = (maxZ - minZ).coerceAtLeast(1e-3f)
    for (t in 0 until triCount) {
        val z = triZ[t]
        if (z.isNaN()) continue
        buckets[((maxZ - z) / range * lastBucket).toInt().coerceIn(0, lastBucket)]++
    }
    var start = 0
    for (k in 0 until STL_AR_DEPTH_BUCKETS) {
        val count = buckets[k]
        buckets[k] = start
        start += count
    }
    val order = chunk.scratchOrder
    for (t in 0 until triCount) {
        val z = triZ[t]
        if (z.isNaN()) continue
        val bucket = ((maxZ - z) / range * lastBucket).toInt().coerceIn(0, lastBucket)
        order[buckets[bucket]++] = t
    }
    val packed = chunk.scratchPacked
    val packedColor = chunk.scratchPackedColor
    for (oi in 0 until visible) {
        val t = order[oi]
        val a = idx[t * 3] * 2; val b = idx[t * 3 + 1] * 2; val c = idx[t * 3 + 2] * 2
        val pb = oi * 6
        packed[pb] = view[a]; packed[pb + 1] = view[a + 1]
        packed[pb + 2] = view[b]; packed[pb + 3] = view[b + 1]
        packed[pb + 4] = view[c]; packed[pb + 5] = view[c + 1]
        val cb = oi * 3
        val color = triColor[t]
        packedColor[cb] = color; packedColor[cb + 1] = color; packedColor[cb + 2] = color
    }
    drawContext.canvas.nativeCanvas.drawVertices(
        android.graphics.Canvas.VertexMode.TRIANGLES,
        visible * 6,
        packed, 0,
        null, 0,
        packedColor, 0,
        null, 0, 0,
        paint
    )
}

/** Tekent de eerste [maxLines] feature-randen van de chunk (lang→kort voorgesorteerd, dus een
 *  klein budget toont vanzelf de hoofdcontouren). Leest uit de gedeelde chunk-projectie. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStlArChunkFeatureLines(
    chunk: StlArChunk,
    maxLines: Int,
    paint: android.graphics.Paint,
    shadowPaint: android.graphics.Paint?,
    frame: StlArFrame,
    sm: StlArScreenMap
) {
    val lines = chunk.featureLines
    val total = minOf(lines.size / 2, maxLines)
    if (total <= 0) return
    val p = chunk.positions
    val m = frame.m
    val view = chunk.scratchView
    val zs = chunk.scratchZ
    val buf = chunk.scratchLines
    val width = size.width
    val height = size.height
    val margin = STL_AR_SCREEN_MARGIN_PX
    var out = 0
    for (li in 0 until total) {
        val a = lines[li * 2]; val b = lines[li * 2 + 1]
        val aBehind = zs[a].isNaN()
        val bBehind = zs[b].isNaN()
        if (aBehind && bBehind) continue
        val x0: Float; val y0: Float; val x1: Float; val y1: Float
        if (!aBehind && !bBehind) {
            x0 = view[a * 2]; y0 = view[a * 2 + 1]
            x1 = view[b * 2]; y1 = view[b * 2 + 1]
        } else {
            // Eén eindpunt ligt achter de near-plane: kap het segment af op de near-plane in plaats
            // van het hele segment te laten vallen — anders verdwijnen randen vlak vóór de camera.
            val front = if (aBehind) b else a
            val behind = if (aBehind) a else b
            val fx = p[front * 3].toDouble(); val fy = p[front * 3 + 1].toDouble(); val fz = p[front * 3 + 2].toDouble()
            val rx = p[behind * 3].toDouble(); val ry = p[behind * 3 + 1].toDouble(); val rz = p[behind * 3 + 2].toDouble()
            val fcx = m[0] * fx + m[1] * fy + m[2] * fz + m[3]
            val fcy = m[4] * fx + m[5] * fy + m[6] * fz + m[7]
            val fcz = m[8] * fx + m[9] * fy + m[10] * fz + m[11]
            val rcx = m[0] * rx + m[1] * ry + m[2] * rz + m[3]
            val rcy = m[4] * rx + m[5] * ry + m[6] * rz + m[7]
            val rcz = m[8] * rx + m[9] * ry + m[10] * rz + m[11]
            val denom = rcz - fcz
            if (denom == 0.0) continue
            val t = (STL_AR_NEAR_MM - fcz) / denom
            val clx = fcx + t * (rcx - fcx)
            val cly = fcy + t * (rcy - fcy)
            val nxc = (clx / STL_AR_NEAR_MM).toFloat()
            val nyc = (cly / STL_AR_NEAR_MM).toFloat()
            x0 = view[front * 2]; y0 = view[front * 2 + 1]
            x1 = sm.x0 + sm.xx * nxc + sm.xy * nyc
            y1 = sm.y0 + sm.yx * nxc + sm.yy * nyc
        }
        if ((x0 < -margin && x1 < -margin) || (x0 > width + margin && x1 > width + margin) ||
            (y0 < -margin && y1 < -margin) || (y0 > height + margin && y1 > height + margin)
        ) {
            continue
        }
        buf[out++] = x0; buf[out++] = y0; buf[out++] = x1; buf[out++] = y1
    }
    if (out == 0) return
    if (shadowPaint != null) drawContext.canvas.nativeCanvas.drawLines(buf, 0, out, shadowPaint)
    drawContext.canvas.nativeCanvas.drawLines(buf, 0, out, paint)
}

/** Silhouetranden van dit frame: randen waar een naar de camera gekeerd vlak aan een afgekeerd
 *  vlak grenst (schermwinding wisselt van teken). Vereist de consistente winding uit de cache. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStlArChunkSilhouette(
    chunk: StlArChunk,
    paint: android.graphics.Paint
) {
    val triCount = chunk.triangleCount
    if (triCount == 0 || chunk.edgeCount == 0) return
    val idx = chunk.indices
    val view = chunk.scratchView
    val zs = chunk.scratchZ
    val sign = chunk.scratchFaceSign
    for (t in 0 until triCount) {
        val a = idx[t * 3]; val b = idx[t * 3 + 1]; val c = idx[t * 3 + 2]
        if (zs[a].isNaN() || zs[b].isNaN() || zs[c].isNaN()) {
            sign[t] = 0
            continue
        }
        val area2 = (view[b * 2] - view[a * 2]) * (view[c * 2 + 1] - view[a * 2 + 1]) -
            (view[c * 2] - view[a * 2]) * (view[b * 2 + 1] - view[a * 2 + 1])
        sign[t] = if (area2 < 0f) 1 else 2
    }
    val buf = chunk.scratchLines
    val width = size.width
    val height = size.height
    val margin = STL_AR_SCREEN_MARGIN_PX
    var out = 0
    for (e in 0 until chunk.edgeCount) {
        val fb = chunk.edgeFaceB[e]
        if (fb < 0) continue
        val sa = sign[chunk.edgeFaceA[e]].toInt()
        val sb = sign[fb].toInt()
        if (sa == 0 || sb == 0 || sa == sb) continue
        val a = chunk.edgeA[e]; val b = chunk.edgeB[e]
        val x0 = view[a * 2]; val y0 = view[a * 2 + 1]
        val x1 = view[b * 2]; val y1 = view[b * 2 + 1]
        if ((x0 < -margin && x1 < -margin) || (x0 > width + margin && x1 > width + margin) ||
            (y0 < -margin && y1 < -margin) || (y0 > height + margin && y1 > height + margin)
        ) {
            continue
        }
        if (out + 4 > buf.size) break
        buf[out++] = x0; buf[out++] = y0; buf[out++] = x1; buf[out++] = y1
    }
    if (out > 0) drawContext.canvas.nativeCanvas.drawLines(buf, 0, out, paint)
}

/** De 12 ribben van een model-space bbox — het skelet van de Snel-modus. */
private val STL_AR_BOX_EDGES = intArrayOf(0, 1, 1, 3, 3, 2, 2, 0, 4, 5, 5, 7, 7, 6, 6, 4, 0, 4, 1, 5, 2, 6, 3, 7)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStlArBounds(
    bounds: FloatArray,
    frame: StlArFrame,
    sm: StlArScreenMap,
    paint: android.graphics.Paint
) {
    val m = frame.m
    val xs = FloatArray(8)
    val ys = FloatArray(8)
    val ok = BooleanArray(8)
    for (corner in 0 until 8) {
        val x = (if (corner and 1 == 0) bounds[0] else bounds[3]).toDouble()
        val y = (if (corner and 2 == 0) bounds[1] else bounds[4]).toDouble()
        val z = (if (corner and 4 == 0) bounds[2] else bounds[5]).toDouble()
        val cz = m[8] * x + m[9] * y + m[10] * z + m[11]
        if (cz <= STL_AR_NEAR_MM) continue
        val nx = ((m[0] * x + m[1] * y + m[2] * z + m[3]) / cz).toFloat()
        val ny = ((m[4] * x + m[5] * y + m[6] * z + m[7]) / cz).toFloat()
        xs[corner] = sm.x0 + sm.xx * nx + sm.xy * ny
        ys[corner] = sm.y0 + sm.yx * nx + sm.yy * ny
        ok[corner] = true
    }
    val buf = FloatArray(STL_AR_BOX_EDGES.size * 2)
    var out = 0
    var i = 0
    while (i < STL_AR_BOX_EDGES.size) {
        val a = STL_AR_BOX_EDGES[i]
        val b = STL_AR_BOX_EDGES[i + 1]
        if (ok[a] && ok[b]) {
            buf[out++] = xs[a]; buf[out++] = ys[a]; buf[out++] = xs[b]; buf[out++] = ys[b]
        }
        i += 2
    }
    if (out > 0) drawContext.canvas.nativeCanvas.drawLines(buf, 0, out, paint)
}

/** Eén zichtbaar deel in dit frame: cache-part + voorberekende frame-transform + dieptemaat. */
private class StlArVisiblePart(val part: StlArPart, val frame: StlArFrame, val depthMm: Double)

/**
 * Tekent de STL-assembly over het camerabeeld, verankerd aan de referentietag.
 *
 * Projectie loopt via exact dezelfde keten als sensoren/tags: individuele solvePnP-pose van de
 * eerste pose-tag (metrisch — tagformaat in mm bepaalt de schaal, dus het model schaalt
 * automatisch mee met de tag) → camera-intrinsics → imageToViewMapper. Geen pose/tag in beeld →
 * niets tekenen (zelfde bewuste keuze als de sensor-overlay).
 *
 * De geometrie komt uit de gewelde, geïndexeerde meshcache ([WorkflowAppState.stlArParts]):
 * vulling en randen lezen uit dezelfde chunk-projectie, dus draad en vlakken kloppen altijd
 * met elkaar. Per frame: frustum-culling per deel en per chunk, hoekpunten één keer
 * projecteren, en delen ver→dichtbij tekenen voor correcte transparantie.
 */
@Composable
internal fun WorkflowStlArOverlay(state: WorkflowAppState) {
    val models = state.project.stlModels
    if (models.none { it.visible }) return
    val meshes = state.stlMeshes
    // Meshes parsen en AR-cache bouwen (beide off-thread, eenmalig) zodra de laag aanstaat.
    // BEWUST gesplitst: zou laden+bouwen in één effect zitten, dan muteert het laden 'meshes'
    // (= een key), wat het effect cancelt en de net gestarte build weggooit → trage/dubbele prep.
    // Nu reageert laden alleen op de modellenlijst en bouwen alleen op de geparste meshes.
    LaunchedEffect(models) {
        state.ensureStlMeshesLoaded()
    }
    LaunchedEffect(models, meshes) {
        state.ensureStlArPartsBuilt()
    }
    // Het zware Detail-niveau pas bouwen wanneer Detail echt actief is (mid blijft tot dan de
    // bron). Keyen op stlArParts zodat high gebouwd wordt zodra het mid-niveau gepubliceerd is.
    LaunchedEffect(state.stlArParts, state.stlArRenderMode) {
        if (state.stlArRenderMode == StlArRenderMode.Detail) state.ensureStlArDetailBuilt()
    }
    val result = state.overlayAprilTagResult
    val refTagId = result.poseMarkerIds.firstOrNull()
    val livePose = refTagId?.let { result.posePerTag[it] }
    // De ARCore/fused pose blijft leidend; een per-tag pose is alleen nog een verse fallback.
    val freshTagPose = livePose?.takeIf { result.detectionAgeMillis <= PER_TAG_POSE_FRESH_MILLIS }
    val pose = result.transformerPose ?: freshTagPose
    // Duidelijke feedback i.p.v. stil niets tekenen — "waarom zie ik niks?" beantwoordt zichzelf.
    val parts = state.stlArParts
    if (parts == null || parts.isEmpty()) {
        val loading = state.stlLoadProgress
        StlArStatusPill(
            when {
                loading.isNotEmpty() -> "3D-model laden… ${(loading.values.average() * 100).toInt()}%"
                meshes.isEmpty() -> "3D-model laden…"
                else -> "3D-model voorbereiden…"
            }
        )
        return
    }
    if (pose == null) {
        StlArStatusPill(result.poseDiagnostic ?: "Scan een bekende referentietag om het 3D-model te ankeren")
        return
    }
    // Het model loopt op ARCore door zonder verse tag; meld het zodra de kwaliteit terugloopt,
    // zodat de operator weet dat één blik op een tag de boel herijkt.
    if (freshTagPose == null && result.trackingStatus == ArTrackingStatus.DriftPossible) {
        StlArStatusPill("Model volgt op ARCore — scan een tag voor herijking")
    }
    val meshPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val linePaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
    }
    val shadowPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val intrinsics = result.cameraIntrinsics ?: return@Canvas
        val mapper = result.imageToViewMapper ?: return@Canvas
        if (intrinsics.fx <= 0f || intrinsics.fy <= 0f) return@Canvas
        if (!OpenCvRuntime.ensureLoaded()) return@Canvas
        // Stap 7: optionele, gethrottlede debug-timing van het STL-tekenpad. Standaard uit
        // (Log.isLoggable DEBUG) → geen overhead en geen detail/budget-verlaging.
        val perfEnabled = Log.isLoggable(STL_AR_PERF_TAG, Log.DEBUG)
        val perfStartNanos = if (perfEnabled) System.nanoTime() else 0L
        var perfChunks = 0
        var perfTriangles = 0
        // Stap 5: in ARCore-leading mode is er een directe cameraCv→transformer matrix beschikbaar
        // (zelfde matrix die transformerPose voedt) → sla de per-draw Rodrigues round-trip over.
        // Buiten die mode het bestaande pad houden, zodat de solvePnP-smoothing niet wordt omzeild.
        val directCameraCv = result.cameraCvFromTransformer
        val cam = if (result.displayProjection != null && directCameraCv != null) {
            directCameraCv.values
        } else {
            Transform3D.cameraCvFromTransformerPose(pose).values
        }
        val sm = stlArScreenMap(intrinsics, mapper)
        val mode = state.stlArRenderMode
        val opacity = state.stlArOpacityPercent.coerceIn(20, 100)
        val fillAlpha = opacity * 255 / 100
        val cullBackfaces = STL_AR_CULL_OPAQUE_BACKFACES && opacity >= 100
        val margin = STL_AR_SCREEN_MARGIN_PX

        // Zichtbare delen verzamelen en ver→dichtbij sorteren (painter's algorithm over delen).
        val visibleParts = ArrayList<StlArVisiblePart>(models.size)
        models.forEach { model ->
            if (!model.visible) return@forEach
            val part = parts[model.id] ?: return@forEach
            val mesh = meshes[model.fileName] ?: return@forEach
            val frame = stlArFrameFor(cam, stlModelTransform(model, mesh))
            if (!stlArBoundsVisible(part.bounds, frame, sm, size.width, size.height, margin)) return@forEach
            val bx = (part.bounds[0] + part.bounds[3]) / 2.0
            val by = (part.bounds[1] + part.bounds[4]) / 2.0
            val bz = (part.bounds[2] + part.bounds[5]) / 2.0
            val depth = frame.m[8] * bx + frame.m[9] * by + frame.m[10] * bz + frame.m[11]
            visibleParts.add(StlArVisiblePart(part, frame, depth))
        }
        visibleParts.sortByDescending { it.depthMm }

        visibleParts.forEach { vp ->
            val part = vp.part
            val frame = vp.frame
            // Detail tekent het high-niveau zodra dat (op de achtergrond) gebouwd is; tot dan mid.
            val chunks = if (mode == StlArRenderMode.Detail && part.high.isNotEmpty()) part.high else part.mid
            chunks.forEach inner@{ chunk ->
                if (state.stlArGroupVisible[chunk.group] == false) return@inner
                if (chunk.triangleCount == 0) return@inner
                if (!stlArBoundsVisible(chunk.bounds, frame, sm, size.width, size.height, margin)) return@inner
                if (perfEnabled) {
                    perfChunks++
                    perfTriangles += chunk.triangleCount
                }
                projectStlArChunk(chunk, frame, sm)
                when (mode) {
                    StlArRenderMode.Fast -> {
                        linePaint.color = part.colorArgb
                        linePaint.alpha = 242
                        linePaint.strokeWidth = 3.5f
                        shadowPaint.color = android.graphics.Color.BLACK
                        shadowPaint.alpha = 90
                        shadowPaint.strokeWidth = 5.4f
                        drawStlArChunkFeatureLines(chunk, STL_AR_FAST_LINES, linePaint, shadowPaint, frame, sm)
                    }
                    StlArRenderMode.Technical -> {
                        linePaint.color = part.colorArgb
                        linePaint.alpha = 242
                        linePaint.strokeWidth = 3.0f
                        shadowPaint.color = android.graphics.Color.BLACK
                        shadowPaint.alpha = 90
                        shadowPaint.strokeWidth = 4.8f
                        drawStlArChunkFeatureLines(chunk, STL_AR_TECH_LINES, linePaint, shadowPaint, frame, sm)
                        linePaint.color = android.graphics.Color.WHITE
                        linePaint.alpha = 217
                        linePaint.strokeWidth = 2.2f
                        drawStlArChunkSilhouette(chunk, linePaint)
                    }
                    StlArRenderMode.Faces -> {
                        drawStlArChunkFill(chunk, frame, meshPaint, part.colorArgb, fillAlpha, cullBackfaces)
                        linePaint.color = android.graphics.Color.WHITE
                        linePaint.alpha = 128
                        linePaint.strokeWidth = 1.8f
                        drawStlArChunkFeatureLines(chunk, STL_AR_FILL_EDGE_LINES, linePaint, null, frame, sm)
                    }
                    StlArRenderMode.Detail -> {
                        drawStlArChunkFill(chunk, frame, meshPaint, part.colorArgb, fillAlpha, cullBackfaces)
                        linePaint.color = android.graphics.Color.WHITE
                        linePaint.alpha = 153
                        linePaint.strokeWidth = 2.0f
                        drawStlArChunkFeatureLines(chunk, STL_AR_TECH_LINES, linePaint, null, frame, sm)
                    }
                }
            }
            if (mode == StlArRenderMode.Fast) {
                linePaint.color = part.colorArgb
                linePaint.alpha = 153
                linePaint.strokeWidth = 2.0f
                drawStlArBounds(part.bounds, frame, sm, linePaint)
            }
        }
        if (perfEnabled) {
            StlArPerfLogThrottle.maybeLog(
                visibleParts = visibleParts.size,
                chunks = perfChunks,
                triangles = perfTriangles,
                mode = mode,
                elapsedNanos = System.nanoTime() - perfStartNanos
            )
        }
    }
}

/** Statusmelding van de AR-modellaag, in dezelfde donkere glas-stijl als de camera-pills. */
@Composable
private fun StlArStatusPill(text: String) {
    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 118.dp),
            shape = RoundedCornerShape(999.dp),
            color = Color(0x99141C28)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** Zo lang geldt een per-tag pose als "vers" (pixel-vast op de live-detectie); daarna neemt de
 *  gefuseerde ARCore-pose het over, die de camerabeweging elk renderframe volgt. */
internal const val PER_TAG_POSE_FRESH_MILLIS = 250L
private const val LIVE_DETECTION_FRESH_MILLIS = 250L
private const val DEBUG_DETECTION_HOLD_MILLIS = 500L

/**
 * Projecteert een box-punt naar schermcoördinaten. Voorkeursvolgorde EXACT zoals de code: de
 * gefuseerde ARCore-pose blijft leidend, een verse per-tag pose is enkel een fallback.
 *
 *  1. de gefuseerde ARCore-pose via [projectPointWithFusedPoseToScreen]: is er een actieve
 *     displayProjection, dan projecteert die rechtstreeks (en is dit de ENIGE bron — zie de
 *     `return null` hieronder); anders de fused transformerPose via de imageToViewMapper. Deze
 *     pose volgt de camerabeweging elk renderframe, ook terwijl je naar het BOVENVLAK kijkt waar
 *     geen tag leesbaar is, zodat model en sensoren niet bevriezen of verdwijnen;
 *  2. is er GEEN displayProjection en geen fused transformerPose, dan de VERSE individuele
 *     solvePnP-pose van de eigen referentietag — valt sub-pixel samen met de live-detectie van
 *     die tag (geen drift, geen verschuiving bij een tagwissel), eveneens via de imageToViewMapper;
 *  3. niets beschikbaar → null (aanroeper tekent niets; de 2D-kaart toont alles).
 *
 * NB: bewust fused-first, niet per-tag-first. Een oudere KDoc beschreef de omgekeerde volgorde;
 * alleen die documentatie is rechtgezet — het GEDRAG is onveranderd (zie ook de inline-comment
 * hieronder en de ontwerpnotitie bij [WorkflowStlArOverlay]).
 */
internal fun projectPointViaTag(
    point: ProjectPointMm,
    tagId: Int?,
    result: AprilTagFrameResult
): ScreenPointPx? {
    // Fixed overlays stay on the ARCore/fused basis; per-tag projection is only a fresh fallback.
    projectPointWithFusedPoseToScreen(point, result)?.let { return it }
    if (result.displayProjection != null) return null

    val mapper = result.imageToViewMapper ?: return null
    val livePose = tagId?.let { result.posePerTag[it] }
    val freshTagPose = livePose?.takeIf {
        result.detectionAgeMillis <= PER_TAG_POSE_FRESH_MILLIS
    }

    val pose = result.transformerPose ?: freshTagPose ?: return null
    val imgPoint = projectPointToImage(point, result.copy(imageProjectionPose = pose)) ?: return null
    val screenPoint = mapper.map(imgPoint)

    return ScreenPointPx(screenPoint.xPx, screenPoint.yPx, 0f)
}

internal fun projectSensorToScreen(
    sensor: Sensor,
    project: Project,
    result: AprilTagFrameResult
): ScreenPointPx? {
    // Gebruik de vaste referentietag; ontbreekt die (oude/voorbereide data), val terug op de
    // dichtstbijzijnde nu-zichtbare tag zodat de sensor toch bij een gescande tag verschijnt.
    val tagId = sensor.referenceTagId ?: nearestVisibleTagId(project, sensor.positionMm, result)
    return projectPointViaTag(
        point = ProjectPointMm(
            sensor.positionMm.x.toDouble(),
            sensor.positionMm.y.toDouble(),
            sensor.positionMm.z.toDouble()
        ),
        tagId = tagId,
        result = result
    )
}

/** Dichtstbijzijnde tag die NU zichtbaar is (in posePerTag), op basis van box-afstand. */
internal fun nearestVisibleTagId(
    project: Project,
    position: MmPosition,
    result: AprilTagFrameResult
): Int? {
    if (result.detectionAgeMillis > PER_TAG_POSE_FRESH_MILLIS) return null
    val visible = result.posePerTag.keys
    if (visible.isEmpty()) return null
    return project.markers
        .filter { it.isAprilTagCalibrationMarker() && it.id in visible }
        .minByOrNull { distanceMm(position - it.positionMm) }
        ?.id
}

@Composable
internal fun WorkflowSensorPointOverlay(
    project: Project,
    result: AprilTagFrameResult
) {
    Canvas(Modifier.fillMaxSize()) {
        // Niets te projecteren zonder per-tag pose én zonder gefuseerde ARCore-pose.
        if (
            result.displayProjection == null &&
            result.transformerPose == null &&
            (result.detectionAgeMillis > PER_TAG_POSE_FRESH_MILLIS || result.posePerTag.isEmpty())
        ) return@Canvas
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(165, 0, 0, 0)
        }
        project.sensors.forEach { sensor ->
            // Vers gezien → via de eigen referentietag (pixel-vast); anders via de gefuseerde
            // ARCore-pose zodat sensoren blijven staan als hun tag even uit beeld is.
            val projected = projectSensorToScreen(sensor, project, result) ?: return@forEach
            val center = Offset(projected.xPx, projected.yPx)
            if (sensor.status == SensorStatus.Pending) drawPlannedSensorRing(sensor, project, result)
            // Duidelijk leesbaar bovenop het (drukke, deels-dekkende) AR-model: donkere contrastrand,
            // felle kern, witte ring. Sensoren worden ná het model getekend (zie WorkflowCameraLayers).
            drawCircle(Color.Black.copy(alpha = 0.6f), radius = 12f, center = center)
            drawCircle(workflowStatusColor(sensor.status), radius = 8f, center = center)
            drawCircle(Color.White, radius = 12f, center = center, style = Stroke(width = 2.5f))
            val label = sensor.id
            val tw = labelPaint.measureText(label)
            val lx = center.x + 16f
            val baseline = center.y - 12f
            drawContext.canvas.nativeCanvas.drawRoundRect(
                lx - 6f, baseline - 24f, lx + tw + 6f, baseline + 7f, 7f, 7f, labelBg
            )
            drawContext.canvas.nativeCanvas.drawText(label, lx, baseline, labelPaint)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlannedSensorRing(
    sensor: Sensor, project: Project, result: AprilTagFrameResult
) {
    val plane = com.example.arsens.ar.TagPlane.entries.firstOrNull { it.name.equals(sensor.side, true) }
        ?: nearestTankPlane(sensor.positionMm, project.dimensionsMm)
    val points = sensorTargetRing(sensor.positionMm, tagPlaneOutwardNormal(plane), sensor.toleranceMm)
        .map { projectPointViaTag(it, sensor.referenceTagId, result) }
    if (points.isEmpty() || points.any { it == null }) return
    val path = androidx.compose.ui.graphics.Path().apply {
        points.forEachIndexed { i, p ->
            if (i == 0) moveTo(p!!.xPx, p.yPx) else lineTo(p!!.xPx, p.yPx)
        }
        close()
    }
    drawPath(path, Color(0xFFFFB020).copy(alpha = 0.15f))
    drawPath(path, Color(0xFFFFB020), style = Stroke(width = 3f))
}

@Composable
internal fun WorkflowCurrentSensorTargetOverlay(
    sensor: Sensor,
    project: Project,
    result: AprilTagFrameResult
) {
    Canvas(Modifier.fillMaxSize()) {
        val projected = projectSensorToScreen(sensor, project, result) ?: return@Canvas
        val center = Offset(projected.xPx, projected.yPx)
        val color = when (sensor.status) {
            SensorStatus.Ok -> Color(0xFF1B8F3A)
            SensorStatus.Pending -> Color(0xFFFFB020)
            SensorStatus.Fail -> Color(0xFFD32F2F)
        }
        if (sensor.status == SensorStatus.Pending) drawPlannedSensorRing(sensor, project, result)
        drawCircle(color, radius = 12f, center = center, style = Stroke(width = 3f))
        drawLine(color, Offset(center.x - 48f, center.y), Offset(center.x + 48f, center.y), strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(color, Offset(center.x, center.y - 48f), Offset(center.x, center.y + 48f), strokeWidth = 5f, cap = StrokeCap.Round)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        drawContext.canvas.nativeCanvas.drawText(if (sensor.status == SensorStatus.Pending) "${sensor.id} · straal ${sensor.toleranceMm} mm" else sensor.id, center.x + 42f, center.y - 20f, paint)
    }
}

@Composable
internal fun WorkflowCursorOverlay(
    cursor: MmPosition?,
    insideTransformer: Boolean,
    label: String,
    availabilityLabel: String = "",
    offset: Offset = Offset.Zero,
    onOffsetChange: ((Offset) -> Unit)? = null
) {
    val currentOffset by rememberUpdatedState(offset)
    val currentOnChange by rememberUpdatedState(onOffsetChange)
    Canvas(
        Modifier
            .fillMaxSize()
            .then(
                if (onOffsetChange != null) {
                    Modifier.pointerInput(Unit) {
                        var acc = Offset.Zero
                        detectDragGestures(
                            onDragStart = { acc = currentOffset },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val halfW = (size.width / 2f).coerceAtLeast(1f)
                                val halfH = (size.height / 2f).coerceAtLeast(1f)
                                acc = Offset(
                                    (acc.x + dragAmount.x / halfW).coerceIn(-0.9f, 0.9f),
                                    (acc.y + dragAmount.y / halfH).coerceIn(-0.9f, 0.9f)
                                )
                                currentOnChange?.invoke(acc)
                            }
                        )
                    }
                } else Modifier
            )
    ) {
        val center = Offset(
            size.width / 2f + currentOffset.x * size.width / 2f,
            size.height / 2f + currentOffset.y * size.height / 2f
        )
        val color = when {
            cursor == null -> Color(0xFFFFB020)
            !insideTransformer -> Color(0xFFD32F2F)
            else -> Color(0xFF00E5FF)
        }
        // Lijn van het schermmidden naar de verschoven cursor, zodat duidelijk is hoever je
        // de cursor hebt weggesleept (de tag mag in het midden in beeld blijven).
        if (currentOffset != Offset.Zero) {
            val screenCenter = Offset(size.width / 2f, size.height / 2f)
            drawLine(color.copy(alpha = 0.5f), screenCenter, center, strokeWidth = 3f, cap = StrokeCap.Round)
            drawCircle(color.copy(alpha = 0.5f), radius = 6f, center = screenCenter)
        }
        drawLine(color, Offset(center.x - 26f, center.y), Offset(center.x + 26f, center.y), strokeWidth = 5f, cap = StrokeCap.Round)
        drawLine(color, Offset(center.x, center.y - 26f), Offset(center.x, center.y + 26f), strokeWidth = 5f, cap = StrokeCap.Round)
        drawCircle(color, radius = 34f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = 12.dp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val maxWidth = (size.width - 24.dp.toPx()).coerceAtLeast(1f)
        fun abbreviated(value: String): String {
            if (paint.measureText(value) <= maxWidth) return value
            val count = paint.breakText(value, true, maxWidth - paint.measureText("…"), null)
            return value.take(count) + "…"
        }
        val lines = listOf(label, availabilityLabel).filter { it.isNotBlank() }.map(::abbreviated)
        val padding = 8.dp.toPx()
        val width = (lines.maxOfOrNull { paint.measureText(it) } ?: 0f) + padding * 2
        val lineHeight = 17.dp.toPx()
        val height = lineHeight * lines.size + padding * 2
        val x = (center.x - width / 2).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
        val y = (center.y + 44f).coerceAtMost(size.height - height)
        drawRoundRect(Color.Black.copy(alpha = 0.72f), Offset(x, y), Size(width, height), CornerRadius(padding))
        lines.forEachIndexed { i, text ->
            drawContext.canvas.nativeCanvas.drawText(text, x + padding, y + padding + lineHeight * (i + 0.8f), paint)
        }
    }
}

internal fun workflowStatusColor(status: SensorStatus): Color =
    when (status) {
        SensorStatus.Pending -> Color(0xFFF5B544)
        SensorStatus.Ok -> Color(0xFF2ED573)
        SensorStatus.Fail -> Color(0xFFE0573B)
    }

internal fun CoordinateFrameSettings.axisXLabel(): String =
    if (flipX) "X+ naar links" else "X+ naar rechts"

internal fun CoordinateFrameSettings.axisYLabel(): String =
    if (flipY) "Y+ naar voren" else "Y+ naar achter"

internal fun CoordinateFrameSettings.axisZLabel(): String =
    if (flipZ) "Z+ omlaag" else "Z+ omhoog"

