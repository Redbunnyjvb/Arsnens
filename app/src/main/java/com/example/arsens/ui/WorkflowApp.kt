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
fun WorkflowApp() {
    val context = LocalContext.current
    val state = remember { WorkflowAppState(context) }
    BackHandler(enabled = state.screen != WorkflowScreen.ProjectPicker) {
        state.navigateBack()
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (state.screen) {
                WorkflowScreen.ProjectPicker -> WorkflowProjectPickerScreen(state)
                WorkflowScreen.Start -> WorkflowStartScreen(state)
                WorkflowScreen.Tags -> WorkflowTagsScreen(state)
                WorkflowScreen.Stl -> WorkflowStlScreen(state)
                WorkflowScreen.Sensors -> WorkflowSensorsScreen(state)
                WorkflowScreen.Install -> WorkflowInstallScreen(state)
                WorkflowScreen.Report -> WorkflowReportScreen(state)
                WorkflowScreen.ReportMap2D -> WorkflowReportMap2DScreen(state)
                WorkflowScreen.Settings -> WorkflowSettingsScreen(state)
            }
        }
    }
    WorkflowConfirmDialog(state)
    WorkflowExportDialog(state)
}

@Composable
internal fun WorkflowConfirmDialog(state: WorkflowAppState) {
    val request = state.confirmRequest ?: return
    AlertDialog(
        onDismissRequest = { state.confirmRequest = null },
        title = { Text(request.title) },
        text = { Text(request.body) },
        confirmButton = {
            Button(
                onClick = {
                    request.onConfirm()
                    state.confirmRequest = null
                }
            ) {
                Text(request.confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = { state.confirmRequest = null }) {
                Text("Annuleer")
            }
        }
    )
}

@Composable
internal fun WorkflowExportDialog(state: WorkflowAppState) {
    if (!state.showExportDialog) return
    AlertDialog(
        onDismissRequest = { state.showExportDialog = false },
        title = { Text("Rapport exporteren?") },
        text = { Text("Kies welk lokaal rapportbestand je wilt maken voor project '${state.project.projectName}'.") },
        confirmButton = {
            Button(
                onClick = {
                    state.exportReportXlsx()
                    state.showExportDialog = false
                }
            ) {
                Text("Excel")
            }
        },
        dismissButton = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        state.exportReportCsv()
                        state.showExportDialog = false
                    }
                ) {
                    Text("CSV")
                }
                OutlinedButton(
                    onClick = {
                        state.exportReportJson()
                        state.showExportDialog = false
                    }
                ) {
                    Text("JSON")
                }
                OutlinedButton(
                    onClick = {
                        state.exportReportBoth()
                        state.showExportDialog = false
                    }
                ) {
                    Text("Alles")
                }
                TextButton(onClick = { state.showExportDialog = false }) {
                    Text("Annuleer")
                }
            }
        }
    )
}

