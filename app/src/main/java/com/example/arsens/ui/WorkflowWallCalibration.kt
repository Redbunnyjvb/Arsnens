@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.arsens.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.arsens.ar.*
import com.example.arsens.ar.calibration.*
import com.example.arsens.data.*
import java.util.Locale

@Composable
internal fun WorkflowReferenceMethodChoice(mode: ReferenceGeometryMode, source: WallDimensionSource,
    onMode: (ReferenceGeometryMode) -> Unit, onSource: (WallDimensionSource) -> Unit) {
    Text("Referentiemethode", style = MaterialTheme.typography.titleMedium)
    ReferenceGeometryMode.entries.forEach { option ->
        Row(Modifier.fillMaxWidth().clickable { onMode(option) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(mode == option, onClick = { onMode(option) })
            Column { Text(option.label); Text(if (option == ReferenceGeometryMode.KnownTagPositions)
                "Tagposities vooraf opmeten of voorbereiden" else "Tags per wand opnemen; daarna dezelfde AR-werkwijze",
                style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (mode == ReferenceGeometryMode.ScannedWalls) {
        WallDimensionSource.entries.forEach { option ->
            Row(Modifier.fillMaxWidth().clickable { onSource(option) }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(source == option, onClick = { onSource(option) }); Text(option.label)
            }
        }
        Text(if (source == WallDimensionSource.Entered)
            "Maten blijven vast. Scan twee aangrenzende zijwanden en het deksel. Het deksel bepaalt de bovenkant."
            else "Scan alle vier de zijwanden voor lengte en breedte. Koppel het deksel en geef de gewenste zijwandhoogte daaronder op.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun WorkflowReferenceMethodCard(state: WorkflowAppState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkflowReferenceMethodChoice(state.project.referenceGeometryMode, state.project.wallDimensionSource,
                state::setReferenceGeometryMode, state::setWallDimensionSource)
            if (state.project.referenceGeometryMode == ReferenceGeometryMode.ScannedWalls) {
                Text(if (state.project.hasWallCalibration) "Tankreferentie opgeslagen" else "Tankreferentie nog instellen")
                Button(onClick = state::beginWallScan, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.project.hasWallCalibration) "Tankreferentie opnieuw kalibreren" else "Wanden scannen")
                }
            }
        }
    }
}

@Composable
internal fun WallCaptureTagChoice(state: WorkflowAppState, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton({ expanded = true }, enabled = state.wallSeenTags.isNotEmpty()) {
            Text(state.wallSelectedTagId?.let { "Tag $it${if (state.wallCaptureTag == null) " · uit beeld" else ""} ▾" } ?: "Geen tag geselecteerd")
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("Volgende kandidaat") }, onClick = { state.nextWallCandidate(); expanded = false })
            state.wallSeenTags.sortedBy { it.tagId }.forEach { tag ->
                DropdownMenuItem(text = { Text("Tag ${tag.tagId}") }, onClick = { state.wallSelectedTagId = tag.tagId; expanded = false })
            }
        }
    }
}

@Composable
internal fun WallScanSettings(state: WorkflowAppState, height: Boolean, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(if (height) "Deksel en zijwandhoogte" else "Zwarte tagmaat") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (height) {
                    Text("Boventags op het deksel bepalen de bovenkant. De box loopt vanaf daar omlaag. Een ondervlak of tag onder de tank is niet nodig.")
                    if (state.wallScanSource == WallDimensionSource.Scanned) {
                        OutlinedTextField(state.wallSideHeight, { state.wallSideHeight = it }, label = { Text("Zijwandhoogte onder deksel (mm)") },
                            supportingText = { Text("Lengte en breedte volgen uit de scan; vul hier de hoogte in.") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    } else Text("Projecthoogte: ${state.project.dimensionsMm.z} mm")
                    OutlinedTextField(state.wallTopOffset, { state.wallTopOffset = it }, label = { Text("Tagvlak boven box (mm)") },
                        supportingText = { Text("0 = deksel is de bovenkant. Alleen aanpassen voor een verhoogd tagvlak.") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                } else {
                    Text("Meet het zwarte vierkant, zonder de witte papierrand. Deze maat geldt voor de volgende tag die je vastlegt.")
                    OutlinedTextField(state.wallScanSize, { state.wallScanSize = it }, label = { Text("Zwarte tagmaat (mm)") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            }
        }, confirmButton = { TextButton(close) { Text("Gereed") } })
}

@Composable
internal fun WorkflowWallPreview(frame: WallScanFrame?, solution: WallCalibrationSolution?, baseOnly: Boolean = false) {
    val projection = frame?.projectionFromReference ?: return
    if (solution == null) return
    Canvas(Modifier.fillMaxSize()) {
        val d = solution.dimensionsMm
        val count = if (baseOnly) 4 else 8
        val corners = (0 until count).map { i ->
            val p = solution.referenceFromProject.wallPoint(WallVector(if (i and 1 == 0) 0.0 else d.x.toDouble(),
                if (i and 2 == 0) 0.0 else d.y.toDouble(), if (i and 4 == 0) 0.0 else d.z.toDouble()))
            projection.project(ProjectPointMm(p.x,p.y,p.z))?.let { Offset(it.xPx,it.yPx) }
        }
        for (i in 0 until count) for (bit in if (baseOnly) listOf(1,2) else listOf(1,2,4)) if (i and bit == 0) {
            val a = corners[i]; val b = corners[i or bit]
            if (a != null && b != null) drawLine(Color(0xFF00E5FF), a, b, 3.dp.toPx())
        }
    }
}

/** Reproject observations with the CURRENT view in the SAME anchor frame. */
@Composable
internal fun WorkflowWallTagOverlay(frame: WallScanFrame?, assignments: List<WallTagAssignment>, ready: List<Int>, selectedWall: CalibrationWall) {
    val projection = frame?.projectionFromReference ?: return
    Canvas(Modifier.fillMaxSize()) {
        for (tag in frame.observations) {
            val half = tag.sizeMm / 2.0
            val corners = listOf(WallVector(-half,0.0,half), WallVector(half,0.0,half),
                WallVector(half,0.0,-half), WallVector(-half,0.0,-half)).map { p ->
                val reference = tag.referenceFromTag.wallPoint(p)
                projection.project(ProjectPointMm(reference.x,reference.y,reference.z))?.let { Offset(it.xPx,it.yPx) }
            }
            if (corners.any { it == null }) continue
            val color = if (tag.tagId in ready) Color(0xFF52D39B) else Color(0xFFFFBD48)
            for (i in 0..3) drawLine(color, corners[i]!!, corners[(i+1)%4]!!, 2.dp.toPx())
            val point = corners[0]!!
            val assignment = assignments.firstOrNull { it.tagId == tag.tagId }
            val text = "Tag ${tag.tagId}" + (assignment?.let { " · ${it.wall.label}" } ?: "")
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = android.graphics.Color.WHITE; textSize = 14.dp.toPx()
                setShadowLayer(3.dp.toPx(), 0f, 0f, android.graphics.Color.BLACK)
            }
            drawContext.canvas.nativeCanvas.drawText(text, point.x, point.y-8.dp.toPx(), paint)
        }
    }
}
