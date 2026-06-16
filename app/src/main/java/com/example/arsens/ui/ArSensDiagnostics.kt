package com.example.arsens.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arsens.ar.AprilTagFrameResult
import com.example.arsens.ar.ScreenPointPx
import com.example.arsens.ar.projectPositionToScreen
import com.example.arsens.data.Marker
import com.example.arsens.data.MmPosition
import com.example.arsens.data.Project
import com.example.arsens.data.coordinateMapper
import com.example.arsens.data.isAprilTagCalibrationMarker
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Diagnose-hulpmiddelen voor de links/rechts-audit. BEWUST puur additief: dit leest alleen
 * bestaande data en logt/tekent, het verandert NIETS aan opslag, AR-pose, 2D-kaart, export of
 * de STL-transform. Twee onafhankelijke checks:
 *
 *  - [logArSensRawTagCheck] / [ArSensRawTagCheckCard] — toont voor elke opgeslagen AprilTag de
 *    RAUWE box-positie ([Marker.positionMm]) en het daaruit afgeleide vlak, ZONDER coordinateMapper.
 *    Hiermee is direct te zien of een fysiek front-links geplaatste tag intern op X=0 (links) of
 *    X=max (rechts) staat. Logtag: [AR_SENS_RAW_TAG_CHECK_TAG].
 *
 *  - [WorkflowCanonicalFrameCheckOverlay] — projecteert de CANONIEKE box-assen (origin, X+, Y+, Z+)
 *    rechtstreeks via de tag-pose, ZONDER coordinateMapper, zodat de overlay de echte box-assen toont
 *    in plaats van de operator-assen. Logtag: [AR_SENS_FRAME_CHECK_TAG].
 */

const val AR_SENS_RAW_TAG_CHECK_TAG = "ARSensRawTagCheck"
const val AR_SENS_FRAME_CHECK_TAG = "ARSensFrameCheck"

// ---------------------------------------------------------------------------------------------
// ARSensRawTagCheck — rauwe opgeslagen tagpositie (canoniek box-frame, geen mapper)
// ---------------------------------------------------------------------------------------------

/** Vlak-afleiding precies volgens de diagnose-spec, puur op de rauwe box-coördinaten. */
internal fun rawBoxSurface(position: MmPosition, dimensions: MmPosition): String =
    when {
        position.z == dimensions.z -> "Top"
        position.y == 0 -> "Front"
        position.y == dimensions.y -> "Back"
        position.x == 0 -> "Left"
        position.x == dimensions.x -> "Right"
        else -> "Inside"
    }

/** Korte X-zijde-aanduiding op het canonieke box-frame (x=0 = fysiek links, x=max = fysiek rechts). */
internal fun rawBoxXSide(x: Int, dimX: Int): String =
    when {
        x == 0 -> "X=0(LEFT)"
        x == dimX -> "X=max(RIGHT)"
        x * 2 < dimX -> "X<50% (left half)"
        else -> "X>50% (right half)"
    }

internal fun arSensRawTagCheckHeader(project: Project): String {
    val frame = project.coordinateFrame
    val d = project.dimensionsMm
    return "frame origin=${frame.originCorner.wireName} flipX=${frame.flipX} flipY=${frame.flipY} " +
        "flipZ=${frame.flipZ} | dimsMm=${d.x}x${d.y}x${d.z}"
}

internal fun arSensRawTagCheckLine(marker: Marker, project: Project): String {
    val d = project.dimensionsMm
    val raw = marker.positionMm
    val op = project.coordinateMapper().boxToOperator(raw)
    val surface = rawBoxSurface(raw, d)
    val side = rawBoxXSide(raw.x, d.x)
    return "tag ${marker.id}: rawBox=(${raw.x},${raw.y},${raw.z}) surface=$surface $side | " +
        "boxToOperator=(${op.x},${op.y},${op.z}) | rotDeg=(${marker.rotationDeg.x.roundToInt()}," +
        "${marker.rotationDeg.y.roundToInt()},${marker.rotationDeg.z.roundToInt()})"
}

internal fun arSensRawTagCheckLines(project: Project): List<String> {
    val tags = project.markers
        .filter { it.isAprilTagCalibrationMarker() }
        .sortedBy { it.id }
    val header = arSensRawTagCheckHeader(project)
    if (tags.isEmpty()) return listOf(header, "no saved AprilTags")
    return listOf(header) + tags.map { arSensRawTagCheckLine(it, project) }
}

/** Logt de rauwe tag-check naar logcat (tag [AR_SENS_RAW_TAG_CHECK_TAG]). */
internal fun logArSensRawTagCheck(project: Project) {
    arSensRawTagCheckLines(project).forEach { Log.i(AR_SENS_RAW_TAG_CHECK_TAG, it) }
}

/** Zichtbare debug-kaart met dezelfde regels als [logArSensRawTagCheck]. */
@Composable
internal fun ArSensRawTagCheckCard(project: Project, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(AR_SENS_RAW_TAG_CHECK_TAG, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            arSensRawTagCheckLines(project).forEach { line ->
                Text(
                    line,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Verwacht voor de fysiek front-links tag: rawBox X=0 (of laag), Y=0, surface=Front.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// ARSensFrameCheck — canonieke box-assen (geen mapper) op de live tag-pose
// ---------------------------------------------------------------------------------------------

internal data class ArSensFrameCheck(
    val origin: ScreenPointPx,
    val xPlus: ScreenPointPx,
    val yPlus: ScreenPointPx,
    val zPlus: ScreenPointPx
)

/** Projecteert de canonieke box-assen RECHTSTREEKS (geen coordinateMapper) via de tag-pose. */
internal fun computeArSensFrameCheck(result: AprilTagFrameResult): ArSensFrameCheck? {
    val origin = projectPositionToScreen(MmPosition(0, 0, 0), result, preferImagePose = false) ?: return null
    val xPlus = projectPositionToScreen(MmPosition(1000, 0, 0), result, preferImagePose = false) ?: return null
    val yPlus = projectPositionToScreen(MmPosition(0, 1000, 0), result, preferImagePose = false) ?: return null
    val zPlus = projectPositionToScreen(MmPosition(0, 0, 1000), result, preferImagePose = false) ?: return null
    return ArSensFrameCheck(origin, xPlus, yPlus, zPlus)
}

/** Schermrichting van een as t.o.v. de origin (scherm-Y groeit naar BENEDEN). */
private fun arSensAxisScreenDirection(origin: ScreenPointPx, end: ScreenPointPx): String {
    val dx = end.xPx - origin.xPx
    val dy = end.yPx - origin.yPx
    val horizontal = when {
        abs(dx) < 1f -> "—"
        dx > 0f -> "right"
        else -> "left"
    }
    val vertical = when {
        abs(dy) < 1f -> "—"
        dy > 0f -> "down"
        else -> "up"
    }
    return "d=(${dx.roundToInt()},${dy.roundToInt()}) screen=$horizontal/$vertical"
}

internal fun arSensFrameCheckLines(check: ArSensFrameCheck): List<String> = listOf(
    "origin px=(${check.origin.xPx.roundToInt()},${check.origin.yPx.roundToInt()})",
    "X+ ${arSensAxisScreenDirection(check.origin, check.xPlus)}  (verwacht: right)",
    "Y+ ${arSensAxisScreenDirection(check.origin, check.yPlus)}  (in trafo)",
    "Z+ ${arSensAxisScreenDirection(check.origin, check.zPlus)}  (verwacht: up)"
)

/**
 * Debug-overlay die de canonieke box-assen (gestippeld, los van de bestaande operator-as-overlay)
 * tekent en throttle-loos op wijziging naar [AR_SENS_FRAME_CHECK_TAG] logt.
 */
@Composable
internal fun WorkflowCanonicalFrameCheckOverlay(result: AprilTagFrameResult) {
    val check = computeArSensFrameCheck(result)
    val lines = check?.let { arSensFrameCheckLines(it) }
    LaunchedEffect(lines) {
        if (lines != null) {
            lines.forEach { Log.i(AR_SENS_FRAME_CHECK_TAG, it) }
        } else {
            Log.i(AR_SENS_FRAME_CHECK_TAG, "no canonical projection (no tag pose this frame)")
        }
    }
    if (check == null || lines == null) return
    Canvas(Modifier.fillMaxSize()) {
        val origin = Offset(check.origin.xPx, check.origin.yPx)
        val xEnd = Offset(check.xPlus.xPx, check.xPlus.yPx)
        val yEnd = Offset(check.yPlus.xPx, check.yPlus.yPx)
        val zEnd = Offset(check.zPlus.xPx, check.zPlus.yPx)
        val dash = PathEffect.dashPathEffect(floatArrayOf(20f, 12f))
        drawLine(Color(0xFFE53935), origin, xEnd, strokeWidth = 5f, cap = StrokeCap.Round, pathEffect = dash)
        drawLine(Color(0xFF43A047), origin, yEnd, strokeWidth = 5f, cap = StrokeCap.Round, pathEffect = dash)
        drawLine(Color(0xFF1E88E5), origin, zEnd, strokeWidth = 5f, cap = StrokeCap.Round, pathEffect = dash)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.YELLOW
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        drawContext.canvas.nativeCanvas.drawText("Xc", xEnd.x + 8f, xEnd.y + 8f, labelPaint)
        drawContext.canvas.nativeCanvas.drawText("Yc", yEnd.x + 8f, yEnd.y - 4f, labelPaint)
        drawContext.canvas.nativeCanvas.drawText("Zc", zEnd.x - 6f, zEnd.y - 10f, labelPaint)
        // Tekstpaneel linksboven zodat de richtingen ook zonder logcat leesbaar zijn.
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.YELLOW
            textSize = 26f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(150, 0, 0, 0)
        }
        val left = 24f
        var top = 120f
        drawContext.canvas.nativeCanvas.drawRect(left - 10f, top - 34f, left + 520f, top + lines.size * 32f, bgPaint)
        drawContext.canvas.nativeCanvas.drawText(AR_SENS_FRAME_CHECK_TAG, left, top, textPaint)
        lines.forEach { line ->
            top += 32f
            drawContext.canvas.nativeCanvas.drawText(line, left, top, textPaint)
        }
    }
}
