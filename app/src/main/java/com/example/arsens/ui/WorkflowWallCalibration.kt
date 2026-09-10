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
internal fun WorkflowWallCalibrationScreen(state: WorkflowAppState, camera: @Composable () -> Unit = {
    ArCoreCameraPanel(emptyList(), state::updateWallScanFrame, Modifier.fillMaxSize(),
        tagDictionary = state.tagDictionary, calibrationRevision = state.arCalibrationRevision,
        wallScanRequest = state.wallScanRequest)
}) {
    var detailsVisible by remember(state.wallScanId) { mutableStateOf(true) }
    var contourVisible by remember(state.wallScanId) { mutableStateOf(true) }
    var settings by remember { mutableStateOf<String?>(null) }
    var overview by remember { mutableStateOf(false) }
    val preview = state.wallScanSolution ?: state.wallLinkedPreview ?: state.wallScanFootprint
    BackHandler { state.cancelWallScan() }
    MaterialTheme(colorScheme = WorkflowCameraDarkScheme) {
        Box(Modifier.fillMaxSize().background(WorkflowCameraDarkScheme.background)) {
            if (state.wallScanActive) {
                camera()
                if (contourVisible) WorkflowWallPreview(state.wallScanFrame, preview,
                    state.wallScanSolution == null && state.wallLinkedPreview == null)
                WorkflowWallTagOverlay(state.wallScanFrame, state.wallAssignments, state.wallReadyTagIds, state.wallScanWall)
            }
            Surface(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(12.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp), color = WorkflowCameraPanel, contentColor = Color.White) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tankreferentie", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = state::cancelWallScan) { Text("Sluiten") }
                    }
                    if (state.wallScanActive) Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.wallScanFrame?.tracking == true) "● Tracking actief" else "Tracking zoeken…",
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { contourVisible = !contourVisible }, enabled = preview != null) {
                            Text(if (contourVisible) "Contour aan" else "Contour uit")
                        }
                        TextButton(onClick = { detailsVisible = !detailsVisible }) {
                            Text(if (detailsVisible) "Hele beeld" else "Details")
                        }
                    }
                }
            }
            if (detailsVisible) Surface(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp).fillMaxWidth()
                .fillMaxHeight(if (state.wallScanActive) 0.55f else 0.75f), shape = RoundedCornerShape(20.dp),
                color = WorkflowCameraPanel, contentColor = Color.White) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!state.wallScanActive) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Wanden scannen", style = MaterialTheme.typography.titleLarge)
                            Text("Plak tags vlak op de tankwand en spreid ze horizontaal. Kies het vlak en de zwarte tagmaat; de camera meet de tagposities.")
                            Text(if (state.project.wallDimensionSource == WallDimensionSource.Scanned)
                                "Scan alle vier zijwanden, met minstens twee verspreide tags per wand. Koppel daarna het deksel met één boventag. Geef de gewenste zijwandhoogte onder het deksel op."
                                else "Je ingevoerde afmetingen blijven behouden. Scan twee aangrenzende zijwanden, met minstens twee tags per wand. Koppel daarna het deksel met één boventag.")
                        }
                        Button(state::beginWallScan, Modifier.fillMaxWidth()) { Text("Scan beginnen") }
                    } else if (state.wallScanSolution != null) {
                        val solution = state.wallScanSolution!!
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Controleer de tankcontour", style = MaterialTheme.typography.titleLarge)
                            Text("${solution.dimensionsMm.x} × ${solution.dimensionsMm.y} × ${solution.dimensionsMm.z} mm")
                            Text("Deksel gekoppeld · ${solution.quality.usedTagIds.size} tags gebruikt")
                            Text("Vergelijk de lijnen met de echte tankranden. De zijmetingen blijven vast terwijl je extra boventags opneemt.")
                            solution.quality.let { q ->
                                Text(String.format(Locale.getDefault(), "Wand RMS %.1f mm · max %.1f mm · normaal %.1f°",
                                    q.rmsMm, q.maxResidualMm, q.normalResidualDeg), style = MaterialTheme.typography.bodySmall)
                                if (q.excludedTagIds.isNotEmpty()) Text("Niet gebruikt: ${q.excludedTagIds.joinToString()}")
                            }
                            Text("Deze waarden tonen de onderlinge overeenkomst van de scan. Sensorcoördinaten blijven behouden.", style = MaterialTheme.typography.bodySmall)
                            state.wallScanMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        OutlinedButton(state::resumeWallScan, Modifier.fillMaxWidth()) { Text("Extra boventags opnemen") }
                        Button(state::acceptWallScan, Modifier.fillMaxWidth().testTag("accept-wall-calibration"),
                            enabled = state.wallScanFrame?.tracking == true) { Text("Kalibratie gebruiken") }
                    } else {
                        Text(if (state.wallScanFootprint == null) "1 · Zijkanten scannen" else "2 · Deksel koppelen",
                            style = MaterialTheme.typography.titleMedium)
                        // Face choice is explicit, including Top from the very first image.
                        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CalibrationWall.entries.forEach { wall ->
                                val selected = state.wallScanWall == wall
                                Surface(Modifier.weight(1f).height(44.dp).selectable(selected, role = Role.RadioButton,
                                    onClick = { state.selectWallScanFace(wall) }), shape = RoundedCornerShape(10.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                                    border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(wall.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WallCaptureTagChoice(state, Modifier.weight(1f))
                            TextButton({ overview = true }) { Text("Tags (${state.wallAssignments.size})") }
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(state.wallCaptureHint, style = MaterialTheme.typography.bodyMedium)
                            state.wallScanMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            if (state.wallScanFootprint != null) {
                                val footprint = state.wallScanFootprint!!
                                Text("Contour ${footprint.dimensionsMm.x} × ${footprint.dimensionsMm.y} mm", style = MaterialTheme.typography.titleSmall)
                                Text("Kies Boven en leg de dekseltag vast. Bekijk de zijtag en boventag samen of na elkaar; de camera koppelt ze in hetzelfde tankframe.",
                                    style = MaterialTheme.typography.bodySmall)
                            } else Text(if (state.wallScanSource == WallDimensionSource.Scanned)
                                "Twee verspreide tags per zijwand. Vier zijwanden bepalen lengte en breedte."
                                else "Twee verspreide tags per wand, op twee aangrenzende zijwanden.", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton({ settings = "size" }) { Text("Tagmaat ${state.wallScanSize} mm") }
                            TextButton({ settings = "height" }) { Text(if (state.wallScanSource == WallDimensionSource.Scanned && state.wallSideHeight.isBlank())
                                "Hoogte instellen" else "Deksel / hoogte") }
                        }
                        val tag = state.wallCaptureTag
                        val assignment = state.wallAssignments.firstOrNull { it.tagId == tag?.tagId }
                        val assignedHere = assignment?.wall == state.wallScanWall && assignment.sizeMm == state.wallScanSize.toIntOrNull()
                        Button({ tag?.let { state.assignWallTag(it.tagId) } }, Modifier.fillMaxWidth().testTag("capture-wall-tag"),
                            enabled = tag != null && state.wallScanFrame?.tracking == true && !assignedHere) {
                            Text(when {
                                tag == null -> "Richt op een tag"
                                !assignedHere -> "Tag ${tag.tagId} op ${state.wallScanWall.label} vastleggen"
                                tag.tagId in state.wallReadyTagIds -> "✓ Tag ${tag.tagId} opgenomen op ${state.wallScanWall.label}"
                                else -> "Tag ${tag.tagId} · metingen verzamelen…"
                            })
                        }
                        if (state.wallScanFootprint == null) {
                            OutlinedButton(state::solveWallFootprint, Modifier.fillMaxWidth()) { Text("Grondcontour berekenen") }
                        } else if (!state.wallTopStage) {
                            OutlinedButton(state::startWallTopStage, Modifier.fillMaxWidth()) { Text("Verder: bovenkant koppelen") }
                        } else {
                            OutlinedButton(state::solveWallScan, Modifier.fillMaxWidth()) {
                                Text(if (state.wallLinkedPreview == null) "Bovenkant koppelen" else "Boventags bijwerken")
                            }
                        }
                    }
                }
            }
        }
        if (settings != null) WallScanSettings(state, settings == "height") { settings = null }
        if (overview) ModalBottomSheet(onDismissRequest = { overview = false }) {
            Column(Modifier.fillMaxWidth().padding(20.dp).heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Opgenomen tags", style = MaterialTheme.typography.titleLarge)
                if (state.wallAssignments.isEmpty()) Text("Nog geen tags vastgelegd. Kies een vlak en leg de zichtbare tag vast.")
                state.wallAssignments.forEach { assignment ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tag ${assignment.tagId} · ${assignment.wall.label} · ${assignment.sizeMm} mm\n" +
                            if (assignment.tagId in state.wallReadyTagIds) "✓ Opgenomen" else "${state.wallScanCounts[assignment.tagId] ?: 0} metingen · nog scannen",
                            Modifier.weight(1f))
                        TextButton({ state.removeWallTag(assignment.tagId) }) { Text("Wissen") }
                    }
                }
                Text("Verkeerd vlak gekozen? Breng de tag in beeld, kies het juiste vlak en leg hem opnieuw vast.", style = MaterialTheme.typography.bodySmall)
                TextButton({ state.beginWallScan(); overview = false }) { Text("Scan opnieuw beginnen") }
                TextButton({ overview = false }) { Text("Terug naar scan") }
            }
        }
    }
}

@Composable
private fun WallCaptureTagChoice(state: WorkflowAppState, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton({ expanded = true }, enabled = state.wallSeenTags.isNotEmpty()) {
            Text(state.wallCaptureTag?.let { "Tag ${it.tagId} ▾" } ?: "Geen tag in beeld")
        }
        DropdownMenu(expanded, { expanded = false }) {
            state.wallSeenTags.sortedBy { it.tagId }.forEach { tag ->
                DropdownMenuItem(text = { Text("Tag ${tag.tagId}") }, onClick = { state.wallSelectedTagId = tag.tagId; expanded = false })
            }
        }
    }
}

@Composable
private fun WallScanSettings(state: WorkflowAppState, height: Boolean, close: () -> Unit) {
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
private fun WorkflowWallPreview(frame: WallScanFrame?, solution: WallCalibrationSolution?, baseOnly: Boolean = false) {
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
private fun WorkflowWallTagOverlay(frame: WallScanFrame?, assignments: List<WallTagAssignment>, ready: List<Int>, selectedWall: CalibrationWall) {
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
            val text = "Tag ${tag.tagId}" + (assignment?.let { " · ${it.wall.label}" } ?: " · ${selectedWall.label} vastleggen ↓")
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = android.graphics.Color.WHITE; textSize = 14.dp.toPx()
                setShadowLayer(3.dp.toPx(), 0f, 0f, android.graphics.Color.BLACK)
            }
            drawContext.canvas.nativeCanvas.drawText(text, point.x, point.y-8.dp.toPx(), paint)
        }
    }
}
