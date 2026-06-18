package com.example.arsens.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.arsens.data.Marker
import com.example.arsens.data.MmPosition
import com.example.arsens.data.Project
import com.example.arsens.data.coordinateMapper
import com.example.arsens.data.isAprilTagCalibrationMarker
import kotlin.math.roundToInt

/**
 * Diagnose-hulpmiddel voor de links/rechts-audit. BEWUST puur additief: dit leest alleen
 * bestaande data en logt/tekent, het verandert NIETS aan opslag, AR-pose, 2D-kaart, export of
 * de STL-transform.
 *
 *  - [logArSensRawTagCheck] / [ArSensRawTagCheckCard] — toont voor elke opgeslagen AprilTag de
 *    RAUWE box-positie ([Marker.positionMm]) en het daaruit afgeleide vlak, ZONDER coordinateMapper.
 *    Hiermee is direct te zien of een fysiek front-links geplaatste tag intern op X=0 (links) of
 *    X=max (rechts) staat. Logtag: [AR_SENS_RAW_TAG_CHECK_TAG].
 */

const val AR_SENS_RAW_TAG_CHECK_TAG = "ARSensRawTagCheck"

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

// De canonieke-box-assen-overlay (ARSensFrameCheck) is verwijderd: vervangen door de box-randen-
// laag + de FPS/ARCore-Hz debug-HUD (zie WorkflowArOverlays.kt). De rauwe tag-check hierboven
// blijft als losse links/rechts-audit.
