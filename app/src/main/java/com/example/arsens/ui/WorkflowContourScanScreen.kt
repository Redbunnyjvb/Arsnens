@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.arsens.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.arsens.ar.*
import com.example.arsens.data.*

@Composable
internal fun WorkflowWallCalibrationScreen(state: WorkflowAppState, camera: @Composable () -> Unit = {
    ArCoreCameraPanel(emptyList(), state::updateWallScanFrame, Modifier.fillMaxSize(),
        tagDictionary = state.tagDictionary, calibrationRevision = state.arCalibrationRevision, wallScanRequest = state.wallScanRequest)
}) {
    var options by remember { mutableStateOf(false) }
    var settings by remember { mutableStateOf<String?>(null) }
    var modeMenu by remember { mutableStateOf(false) }
    var faceMenu by remember { mutableStateOf(false) }
    var contourVisible by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    val preview = state.wallScanSolution ?: state.wallLinkedPreview ?: state.wallScanFootprint
    BackHandler { state.cancelWallScan() }
    MaterialTheme(colorScheme = WorkflowCameraDarkScheme) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val compact = maxWidth > maxHeight
            if (state.wallScanActive) {
                camera()
                if (contourVisible) WorkflowWallPreview(state.wallScanFrame, preview, state.wallScanSolution == null && state.wallLinkedPreview == null)
                WorkflowWallTagOverlay(state.wallScanFrame, state.wallAssignments, state.wallReadyTagIds, state.wallScanWall)
            }
            Surface(Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(8.dp).fillMaxWidth(),
                shape = RoundedCornerShape(14.dp), color = WorkflowCameraPanel, contentColor = Color.White) {
                Row(Modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Contour", style = MaterialTheme.typography.titleMedium)
                        if (!compact) Text("${state.wallReadyTagIds.size} opgenomen · ${state.wallSeenTags.size} zichtbaar", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton({ controlsVisible = !controlsVisible }) { Text(if (controlsVisible) "Hele beeld" else "Details") }
                    TextButton({ options = true }) { Text("Opties") }
                    TextButton(state::cancelWallScan) { Text("Sluiten") }
                }
            }
            if (controlsVisible) Surface(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(8.dp)
                .widthIn(max = 620.dp).fillMaxWidth().heightIn(max = if (compact) maxHeight * 0.68f else maxHeight * 0.43f),
                shape = RoundedCornerShape(16.dp), color = WorkflowCameraPanel, contentColor = Color.White) {
                Column(Modifier.padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!state.wallScanActive) {
                        Text("Kies de eerste zijde. Scan daarna rustig rondom de tank.")
                        Button(state::beginWallScan, Modifier.fillMaxWidth()) { Text("Scan beginnen") }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf(CalibrationWall.Front, CalibrationWall.Right, CalibrationWall.Back, CalibrationWall.Left, CalibrationWall.Top).forEach { wall ->
                                val ready = state.wallAssignments.any { it.wall == wall && it.tagId in state.wallReadyTagIds }
                                Text("${wall.label} ${if (ready) "✓" else "○"}", style = MaterialTheme.typography.labelSmall,
                                    color = if (ready) Color(0xFF70DDB4) else Color.LightGray)
                            }
                        }
                        val solution = state.wallScanSolution
                        if (solution != null) {
                            Text("Controleer de tankcontour", style = MaterialTheme.typography.titleMedium)
                            Text("${solution.dimensionsMm.x} × ${solution.dimensionsMm.y} × ${solution.dimensionsMm.z} mm")
                            if (!solution.quality.topOverlapVerified) Text("Houd een zijtag en boventag samen in beeld.")
                            state.wallScanMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(state::resumeWallScan, Modifier.weight(1f)) { Text("Verder scannen") }
                                Button(state::acceptWallScan, Modifier.weight(1f).testTag("accept-wall-calibration"),
                                    enabled = state.wallScanFrame?.tracking == true && solution.quality.topOverlapVerified) { Text("Contour gebruiken") }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box {
                                    TextButton({ modeMenu = true }, Modifier.testTag("wall-scan-mode")) { Text(if (state.wallAutoCapture) "Automatisch ▾" else "Handmatig ▾") }
                                    DropdownMenu(modeMenu, { modeMenu = false }) {
                                        DropdownMenuItem(text = { Text("Automatisch") }, onClick = { state.setWallAutoMode(true); modeMenu = false })
                                        DropdownMenuItem(text = { Text("Handmatig") }, onClick = { state.setWallAutoMode(false); modeMenu = false })
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                TextButton({ settings = "size" }) { Text("${state.wallScanSize} mm") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WallCaptureTagChoice(state, Modifier.weight(1f))
                                Box {
                                    TextButton({ faceMenu = true }) { Text("${state.wallScanWall.label} ▾") }
                                    DropdownMenu(faceMenu, { faceMenu = false }) {
                                        CalibrationWall.entries.forEach { wall -> DropdownMenuItem(text = { Text(wall.label) },
                                            onClick = { state.selectWallScanFace(wall); faceMenu = false }) }
                                    }
                                }
                            }
                            Text(state.wallScanMessage ?: state.wallShortStatus, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            if (state.wallScanFootprint != null && state.wallScanSource == WallDimensionSource.Scanned &&
                                state.wallSideHeight.toIntOrNull()?.takeIf { it > 0 } == null) {
                                TextButton({ settings = "height" }) { Text("Hoogte instellen") }
                            }
                            val tag = state.wallCaptureTag
                            val assigned = state.wallAssignments.any { it.tagId == tag?.tagId && it.wall == state.wallScanWall }
                            val ready = tag?.tagId in state.wallReadyTagIds
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { if (ready) state.nextWallCandidate() else tag?.let { state.assignWallTag(it.tagId) } },
                                    modifier = Modifier.weight(1f).testTag("capture-wall-tag"),
                                    enabled = state.wallScanFrame?.tracking == true && tag != null && (!assigned || ready)) {
                                    Text(when { ready -> "Volgende tag"; assigned -> "Opnemen…"; tag == null -> "Richt op een tag"
                                        else -> "${state.wallScanWall.label} vastleggen" })
                                }
                                OutlinedButton(state::calculateContour, Modifier.weight(1f)) { Text("Contour bekijken") }
                            }
                        }
                    }
                }
            }
        }
        if (settings != null) WallScanSettings(state, settings == "height") { settings = null }
        if (options) ModalBottomSheet(onDismissRequest = { options = false }) {
            Column(Modifier.padding(horizontal = 20.dp).heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Scanopties", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Automatisch opnemen", Modifier.weight(1f)); Switch(state.wallAutoCapture, state::setWallAutoMode)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Contour tonen", Modifier.weight(1f)); Switch(contourVisible, { contourVisible = it })
                }
                TextButton({ settings = "size"; options = false }) { Text("Tagmaat · ${state.wallScanSize} mm") }
                TextButton({ settings = "height"; options = false }) { Text("Hoogte & dekseloffset") }
                WorkflowDimensionComparison(state.wallDimensionComparisons,state.project.dimensionWarningMm)
                state.wallCaptureTag?.let { Text("Tag ${it.tagId}: ${it.shortestEdgePx.toInt()} px · pose-fit ${"%.2f".format(it.reprojectionErrorPx)} px", style = MaterialTheme.typography.bodySmall) }
                state.wallAssignments.forEach { assignment -> Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tag ${assignment.tagId} · ${assignment.wall.label}", Modifier.weight(1f))
                    TextButton({ state.wallSelectedTagId = assignment.tagId; state.selectWallScanFace(assignment.wall); options = false }) { Text("Zijde") }
                    TextButton({ state.reobserveReferenceTag(assignment.tagId); options = false }) { Text("Opnieuw meten") }
                  }
                  TextButton({state.toggleReferenceIgnored(assignment.tagId)}) {Text(if(assignment.tagId in state.ignoredReferenceIds) "Tag weer gebruiken" else "Tijdelijk negeren")}
                } }
                state.wallScanSolution?.quality?.let { Text("Spreiding ${"%.1f".format(it.repeatabilityMm)} mm · vlakfit ${"%.1f".format(it.rmsMm)} mm", style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
