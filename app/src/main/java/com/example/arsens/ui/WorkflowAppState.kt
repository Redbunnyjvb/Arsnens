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
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.arsens.ar.TagDictionaryOption
import com.example.arsens.ar.TagPoseMode
import com.example.arsens.ar.TagPoseSmoother
import com.example.arsens.ar.tagPlacementFor
import com.example.arsens.ar.tagPositionFor
import com.example.arsens.ar.tagRotationFor
import com.example.arsens.data.AppSettings
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
import com.example.arsens.data.StlPartRole
import com.example.arsens.data.StlParser
import com.example.arsens.data.asAprilTagCalibrationMarker
import com.example.arsens.data.confirmSensorAtMeasuredPosition
import com.example.arsens.data.coordinateMapper
import com.example.arsens.data.defaultFrameForOrigin
import com.example.arsens.data.distanceMm
import com.example.arsens.data.isAprilTagCalibrationMarker
import com.example.arsens.data.estimateCoreBounds
import com.example.arsens.data.rotatedBounds
import com.example.arsens.data.rotatedBoundsOfBox
import com.example.arsens.data.rotatedExtents
import com.example.arsens.data.toReadableMm
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal enum class WorkflowScreen {
    ProjectPicker,
    Start,
    Tags,
    Stl,
    Sensors,
    Install,
    Report,
    ReportMap2D,
    Settings
}

internal enum class WorkMode(val label: String) {
    Prepared("Voorbereid"),
    OnTheFly("On the fly")
}

internal enum class CameraPlacementTarget(val label: String) {
    Sensor("Sensor"),
    Tag("Tag")
}

internal enum class Model2DPurpose {
    Report,
    SensorSetup,
    PreparedSetup
}

/** Wat er op de 2D-kaart geselecteerd is om te bewerken/verwijderen via het zijpaneel. */
internal sealed interface MapSelection {
    data class Sensor(val id: String) : MapSelection
    data class Tag(val id: Int) : MapSelection
}

/** Weergavemodus van de STL-assembly in het camerabeeld.
 *  Snel = bounding boxes + hoofdcontouren; Technisch = feature- en silhouetranden;
 *  Vlakken = gevulde (semi-transparante) mesh; Detail = gevuld op hoog driehoekenbudget. */
internal enum class StlArRenderMode(val label: String) {
    Fast("Snel"),
    Technical("Technisch"),
    Faces("Vlakken"),
    Detail("Detail")
}

internal data class WorkflowConfirmRequest(
    val title: String,
    val body: String,
    val confirmText: String,
    val onConfirm: () -> Unit
)

internal data class WorkflowCameraMenu(
    val key: String,
    val label: String,
    val content: @Composable ColumnScope.() -> Unit
)

internal data class WorkflowCameraShortcut(
    val key: String,
    val label: String,
    val onClick: () -> Unit,
    val iconKey: String = key
)

internal class WorkflowAppState(context: Context) {
    private val repository = LocalProjectRepository(context)
    private val appSettings = AppSettings(context)

    var screen by mutableStateOf(WorkflowScreen.ProjectPicker)

    /** Tagfamilie voor de camera-detectie (app-breed, bewaard in instellingen). */
    var tagDictionary by mutableStateOf(TagDictionaryOption.fromName(appSettings.tagDictionaryName))
        private set

    fun selectTagDictionary(option: TagDictionaryOption) {
        if (tagDictionary == option) return
        tagDictionary = option
        appSettings.tagDictionaryName = option.name
        resetArPoseState()
        message = "AprilTag-dictionary: ${option.label}. De camera detecteert nu alleen tags uit deze familie."
    }

    /** Pose-selectiestrategie (app-breed): dichtstbijzijnde tag (robuust) of multi-tag (vereist
     *  mm-exact ingemeten tagposities). Geprinte 2×2-clusters worden altijd gezamenlijk opgelost. */
    var tagPoseMode by mutableStateOf(TagPoseMode.fromName(appSettings.tagPoseModeName))
        private set

    fun selectTagPoseMode(mode: TagPoseMode) {
        if (tagPoseMode == mode) return
        tagPoseMode = mode
        appSettings.tagPoseModeName = mode.name
        resetArPoseState()
        message = "Pose-modus: ${mode.label}."
    }

    /** Standaard tagformaat (mm) voor nieuwe referentietags; vult het Formaat-veld. */
    var defaultTagSizeMm by mutableStateOf(appSettings.defaultTagSizeMm)
        private set

    fun setDefaultTagSize(sizeMm: Int) {
        val size = sizeMm.coerceIn(10, 1000)
        defaultTagSizeMm = size
        appSettings.defaultTagSizeMm = size
        if (tagSize.toIntOrNull() == null || project.markers.none { it.isAprilTagCalibrationMarker() }) {
            tagSize = size.toString()
        }
    }

    /** Past het standaard tagformaat toe op ALLE bestaande referentietags (met bevestiging). De
     *  setting zelf geldt normaal alleen voor nieuwe tags; deze actie herschaalt ook de al
     *  geplaatste tags. Dat verandert de metrische pose van die tags, dus de AR-kalibratie wordt
     *  gereset. */
    fun applyDefaultTagSizeToExistingTags() {
        val size = defaultTagSizeMm
        val tags = project.markers.filter { it.isAprilTagCalibrationMarker() }
        if (tags.isEmpty()) {
            message = "Geen referentietags om aan te passen."
            return
        }
        val affected = tags.count { it.sizeMm != size }
        if (affected == 0) {
            message = "Alle ${tags.size} tags staan al op $size mm."
            return
        }
        confirmRequest = WorkflowConfirmRequest(
            title = "Tagformaat toepassen?",
            body = "$affected van ${tags.size} referentietags krijgen het standaardformaat $size mm. " +
                "Dat verandert de metrische schaal van de AR-pose voor die tags; de AR-kalibratie wordt gereset.",
            confirmText = "Pas toe ($size mm)"
        ) {
            project = project.copy(
                markers = project.markers.map { marker ->
                    if (marker.isAprilTagCalibrationMarker()) marker.copy(sizeMm = size) else marker
                }
            )
            resetArPoseState()
            saveProject()
            message = "$affected tag(s) op $size mm gezet. AR-pose gereset."
        }
    }

    /** Eerste tag-ID voor sensor-tags; referentietags krijgen ID's eronder. */
    var sensorTagStartId by mutableStateOf(appSettings.sensorTagStartId)
        private set

    fun setSensorTagStart(startId: Int) {
        val start = startId.coerceIn(1, 580)
        sensorTagStartId = start
        appSettings.sensorTagStartId = start
    }

    /** Formaat (mm) van de kleine AprilTags op de sensoren zelf. */
    var sensorTagSizeMm by mutableStateOf(appSettings.sensorTagSizeMm)
        private set

    fun setSensorTagSize(sizeMm: Int) {
        val size = sizeMm.coerceIn(10, 200)
        sensorTagSizeMm = size
        appSettings.sensorTagSizeMm = size
    }
    var mode by mutableStateOf(WorkMode.Prepared)
    var cameraPlacementTarget by mutableStateOf(CameraPlacementTarget.Sensor)
    var project by mutableStateOf(repository.loadInitialProject())
    var log by mutableStateOf(repository.loadLog(project.projectName))
    var projects by mutableStateOf(repository.listProjects())
    var activeProjectUpdatedAtMillis by mutableStateOf(repository.activeProjectSummary()?.updatedAtMillis)
    var newProjectName by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
    var confirmRequest by mutableStateOf<WorkflowConfirmRequest?>(null)
    var showExportDialog by mutableStateOf(false)
    var cameraMenuRequest by mutableStateOf<String?>(null)
    var model2dBackTarget by mutableStateOf(WorkflowScreen.Report)
    var model2dPurpose by mutableStateOf(Model2DPurpose.Report)
    var selectedMapTarget by mutableStateOf<MapSelection?>(null)

    /** Echte navigatie-geschiedenis: systeem-back keert terug naar het werkelijk vorige scherm
     *  (bv. camera → rapport → 2D → terug → rapport → terug → camera). Alleen de "spring"-
     *  navigaties tussen camera/rapport/2D/3D vullen deze stack; de lineaire wizard valt terug
     *  op het vaste schema in [navigateBack]. */
    private val navHistory = mutableListOf<WorkflowScreen>()

    var lengthMm by mutableStateOf(project.dimensionsMm.x.toString())
    var widthMm by mutableStateOf(project.dimensionsMm.y.toString())
    var heightMm by mutableStateOf(project.dimensionsMm.z.toString())
    /** Ingelezen STL-meshes per opgeslagen bestandsnaam (cache voor de 3D-preview). */
    var stlMeshes by mutableStateOf<Map<String, StlMesh>>(emptyMap())

    var aprilTagResult by mutableStateOf(AprilTagFrameResult())
    var heldAprilTagResult by mutableStateOf(AprilTagFrameResult())
    private var heldAprilTagAtMillis: Long = 0
    private var lastObjectCursorAtMillis: Long = 0
    var tagId by mutableStateOf("")
    var tagX by mutableStateOf("0")
    var tagY by mutableStateOf("0")
    var tagZ by mutableStateOf("0")
    var tagSize by mutableStateOf(appSettings.defaultTagSizeMm.toString())
    var tagRotX by mutableStateOf("0")
    var tagRotY by mutableStateOf("0")
    var tagRotZ by mutableStateOf("0")
    /** Gewicht voor pose-selectie: hogere waarde = hogere prioriteit als referentietag.
     *  Stel de primaire referentietag in op 2 (of meer) zodat die consistent domineert
     *  en sensoren altijd in hetzelfde coördinatenstelsel worden gemeten. */
    var tagPoseWeight by mutableStateOf("1")
    var tagScanArmed by mutableStateOf(false)
    private var lastScannedTagId: Int? = null
    private var lastScannedTagAtMillis: Long = 0L
    var tagCoordinateFieldsExpanded by mutableStateOf(false)
    var selectedTagPlane by mutableStateOf(TagPlane.Front)
    var selectedTagAnchor by mutableStateOf(TagAnchor.Center)
    /** Tagsheet-invoermodus: false = het raster stuurt de coördinaten, true = handmatig getypte
     *  X/Y/Z blijven staan (een vlakwissel zet dan alleen de rotatie, niet de positie). */
    var tagPlacementManual by mutableStateOf(false)
        private set
    /** Laatst gekozen rasterpunt (u,v elk 0..1) op het tagvlak; bepaalt bij een vlakwissel de
     *  positie in gridmodus. */
    var selectedTagGridUv by mutableStateOf(0.5f to 0.5f)
        private set
    var activeMapView by mutableStateOf(TransformerMapView.Front)

    var sensorId by mutableStateOf("")
    var sensorName by mutableStateOf("")
    var sensorX by mutableStateOf("0")
    var sensorY by mutableStateOf("0")
    var sensorZ by mutableStateOf("0")
    var sensorTolerance by mutableStateOf("50")
    var sensorInstruction by mutableStateOf("")
    /** ID van de tag die gebruikt werd bij plaatsing van de huidige sensor. */
    var sensorReferenceTagId: Int? = null
    /** Laatst gebruikte verse, bekende referentietag (id + marker). Wordt het plaatsingsvlak zodra
     *  de tag niet meer vers in beeld is maar ARCore nog trackt: cursor/sensor blijven dan op dít
     *  marker-vlak i.p.v. op een willekeurige trafo-box-hit. */
    var lastPlacementReference: PlacementReference? = null

    var currentSensorIndex by mutableIntStateOf(0)
    var cursorX by mutableStateOf("0")
    var cursorY by mutableStateOf("0")
    var cursorZ by mutableStateOf("0")
    var arCursorPosition by mutableStateOf<MmPosition?>(null)
    var arCursorInsideTransformer by mutableStateOf(true)
    var arCursorUsesDepth by mutableStateOf(false)
    var arCursorSource by mutableStateOf("none")
    /** Verschuiving van de plaatsings-cursor t.o.v. het schermmidden, als fractie van het halve
     *  scherm (x,y ∈ [-1,1]; +x = rechts, +y = omlaag in schermzin). Zo kun je een sensor aan de
     *  rand plaatsen terwijl de AprilTag in beeld (en dus getrackt) blijft. (0,0) = midden. */
    var arCursorScreenOffset by mutableStateOf(Offset.Zero)
    private var lastCursorLogKey: String = ""
    private var lastCursorLogMillis: Long = 0L
    var showAxisOverlay by mutableStateOf(false)
    var showMiniAxisOverlay by mutableStateOf(true)
    /** Debug (links/rechts-audit): tekent de CANONIEKE box-assen via de tag-pose, zonder
     *  coordinateMapper, en logt naar [AR_SENS_FRAME_CHECK_TAG]. Los van [showAxisOverlay]
     *  (die toont de operator-assen). */
    var showFrameCheckOverlay by mutableStateOf(false)
    var showTagOverlay by mutableStateOf(true)
    var showSensorOverlay by mutableStateOf(false)
    /** Toon de STL-assembly in het camerabeeld, verankerd via de tag-pose. */
    var showStlOverlay by mutableStateOf(false)
    var stlArRenderMode by mutableStateOf(StlArRenderMode.Faces)
    /** Dekking van de AR-assembly in % (20–100); laag = doorzichtig spookbeeld over de camera. */
    var stlArOpacityPercent by mutableStateOf(60)
    /** Zichtbaarheid per onderdelengroep in AR. Interne delen staan standaard uit — de operator
     *  kijkt tegen de buitenkant van de tank aan; binnenwerk maakt het beeld onleesbaar. */
    val stlArGroupVisible = mutableStateMapOf(
        StlArPartGroup.Tank to true,
        StlArPartGroup.Deksel to true,
        StlArPartGroup.Radiatoren to true,
        StlArPartGroup.ActiefDeel to false,
        StlArPartGroup.Overig to false
    )
    /** AR-meshcache per model-id (gewelde, geïndexeerde, gedecimeerde chunks in model-mm).
     *  null = (nog) niet gebouwd. Gebouwd via [ensureStlArPartsBuilt]. */
    var stlArParts by mutableStateOf<Map<String, StlArPart>?>(null)
    private var stlArPartsKey: String? = null
    /** Sleutel waarvoor óók het zware Detail-niveau (high) gebouwd is; null = alleen mid. */
    private var stlArDetailKey: String? = null
    private var stlArPartsBuilding = false

    val currentSensor: Sensor?
        get() = project.sensors.sortedBy { it.order }.getOrNull(currentSensorIndex)

    val arCursorSourceLabel: String
        get() = when (arCursorSource) {
            "depth" -> "Objectvlak"
            "surface" -> "Trafo-oppervlak"
            "hold" -> "Oude cursor"
            else -> "Geen objectvlak"
        }

    val knownAprilTags: List<Marker>
        get() = savedAprilTags
            .filter { it.active }

    val savedAprilTags: List<Marker>
        get() = project.markers
            .filter { it.isAprilTagCalibrationMarker() }
            .map { it.asAprilTagCalibrationMarker(project.dimensionsMm) }

    val overlayAprilTagResult: AprilTagFrameResult
        get() {
            val now = System.currentTimeMillis()
            val liveHasTracking = aprilTagResult.hasOverlayPose()
            val liveHasDetection = aprilTagResult.hasAnyDetection()
            val heldIsFresh = heldAprilTagResult.hasOverlayPose() && now - heldAprilTagAtMillis < 3_000
            return when {
                liveHasTracking -> aprilTagResult
                heldIsFresh -> heldAprilTagResult
                liveHasDetection -> aprilTagResult
                else -> aprilTagResult
            }
        }

    val recentTagIds: List<Int>
        get() = overlayAprilTagResult.detections
            .map { it.id }
            .distinct()
            .sorted()

    fun refreshProjects() {
        projects = repository.listProjects()
        activeProjectUpdatedAtMillis = repository.activeProjectSummary()?.updatedAtMillis ?: activeProjectUpdatedAtMillis
    }

    fun openProject(summary: ProjectSummary) {
        val loaded = repository.selectProject(summary.id)
        if (loaded == null) {
            message = "Project kon niet geladen worden."
            refreshProjects()
            return
        }
        project = loaded
        syncDimensionsFromProject()
        log = repository.loadLog(project.projectName)
        activeProjectUpdatedAtMillis = summary.updatedAtMillis
        resetArPoseState()
        message = "Project geopend: ${project.projectName}"
        navHistory.clear()
        screen = WorkflowScreen.Start
    }

    fun requestDeleteProject(summary: ProjectSummary) {
        confirmRequest = WorkflowConfirmRequest(
            title = "Project verwijderen?",
            body = "Project '${summary.name}' wordt met alle lokale JSON, logs en exports uit de projectmap verwijderd.",
            confirmText = "Verwijder"
        ) {
            deleteProject(summary)
        }
    }

    private fun deleteProject(summary: ProjectSummary) {
        if (repository.deleteProject(summary.id)) {
            refreshProjects()
            message = "Project verwijderd: ${summary.name}"
        } else {
            message = "Project kon niet verwijderd worden."
        }
    }

    fun createProject() {
        val name = newProjectName.ifBlank { "Nieuw transformatorproject" }
        val dimensions = dimensionsFromFields()
        if (dimensions == null) {
            message = "Vul geldige trafo-afmetingen in hele millimeters in."
            return
        }
        project = repository.createProject(name, dimensions)
        log = repository.loadLog(project.projectName)
        newProjectName = ""
        syncDimensionsFromProject()
        resetArPoseState()
        refreshProjects()
        activeProjectUpdatedAtMillis = System.currentTimeMillis()
        message = "Project aangemaakt: ${project.projectName}"
        navHistory.clear()
        screen = WorkflowScreen.Start
    }

    fun closeProject() {
        resetArPoseState()
        refreshProjects()
        navHistory.clear()
        screen = WorkflowScreen.ProjectPicker
    }

    fun chooseMode(selected: WorkMode) {
        mode = selected
        cameraPlacementTarget = if (selected == WorkMode.OnTheFly) CameraPlacementTarget.Sensor else CameraPlacementTarget.Tag
        navHistory.clear()
        if (selected == WorkMode.OnTheFly) {
            resetSensorFormForNext()
            reseedTagPlacement()
            showTagOverlay = true
            showSensorOverlay = true
            screen = WorkflowScreen.Tags
            message = null
            return
        }
        showTagOverlay = true
        showSensorOverlay = project.sensors.isNotEmpty()
        if (tagId.isBlank()) tagId = nextAprilTagId().toString()
        if (sensorName.isBlank()) sensorName = "sens"
        if (sensorTolerance.isBlank()) sensorTolerance = "50"
        open2DModel(WorkflowScreen.Start, Model2DPurpose.PreparedSetup, pushHistory = false)
        message = null
    }

    fun selectCameraPlacementTarget(target: CameraPlacementTarget) {
        cameraPlacementTarget = target
        if (target == CameraPlacementTarget.Tag) {
            reseedTagPlacement()
            if (tagId.isBlank()) tagId = nextAprilTagId().toString()
        } else {
            if (sensorName.isBlank()) sensorName = "sens"
            if (sensorTolerance.isBlank()) sensorTolerance = "50"
        }
        message = null
    }

    fun go(target: WorkflowScreen) {
        if (target == WorkflowScreen.ProjectPicker) navHistory.clear()
        screen = target
        if (target == WorkflowScreen.Install) {
            setCursorFromCurrentSensor()
            showTagOverlay = true
            showSensorOverlay = true
        }
        if (target == WorkflowScreen.Tags) {
            showTagOverlay = true
            showSensorOverlay = mode == WorkMode.OnTheFly || project.sensors.isNotEmpty()
        }
    }

    /** Voorwaartse "spring"-navigatie die het huidige scherm op de history-stack zet, zodat
     *  systeem-back er weer naartoe terugkeert. */
    fun navigateTo(target: WorkflowScreen) {
        if (target != screen) navHistory.add(screen)
        go(target)
    }

    fun open2DModel(
        backTarget: WorkflowScreen,
        purpose: Model2DPurpose = when (backTarget) {
            WorkflowScreen.Sensors -> Model2DPurpose.SensorSetup
            else -> Model2DPurpose.Report
        },
        pushHistory: Boolean = true
    ) {
        model2dBackTarget = backTarget
        model2dPurpose = purpose
        selectedMapTarget = null
        if (pushHistory) navigateTo(WorkflowScreen.ReportMap2D) else go(WorkflowScreen.ReportMap2D)
    }

    fun updateAprilTagResult(rawResult: AprilTagFrameResult) {
        val now = System.currentTimeMillis()
        // Demp de solvePnP-poses vóór ze de state in gaan: alle overlays (sensoren, tags,
        // STL-model én cursor) lezen daardoor hetzelfde rustige beeld.
        val result = smoothArPoses(rawResult, now)
        aprilTagResult = result
        if (result.hasOverlayPose()) {
            heldAprilTagResult = result
            heldAprilTagAtMillis = now
        }
        if (tagScanArmed && result.detections.isNotEmpty()) {
            captureVisibleTagForSetup(result)
        }
        // Bereken de plaatsings-cursor via een verse individuele referentietag-pose. Zodra die
        // pose ouder wordt, blijft de gefuseerde ARCore-projectie leidend zodat de cursor niet
        // aan een vastgehouden tag-frame blijft plakken.
        val cursorRefTagId = aprilTagResult.poseMarkerIds
            .firstOrNull()
            ?.takeIf { aprilTagResult.detectionAgeMillis <= FRESH_PER_TAG_POSE_MILLIS }
        val cursorRefPose = cursorRefTagId?.let { aprilTagResult.posePerTag[it] }
        // Onthoud de laatst gebruikte verse bekende referentietag als plaatsingsvlak. Zodra de tag
        // niet meer vers is, gebruikt estimateCursorOnReferenceSurface dit vlak via de ARCore-straal.
        if (cursorRefTagId != null) {
            knownAprilTags.firstOrNull { it.id == cursorRefTagId }?.let { marker ->
                lastPlacementReference = PlacementReference(tagId = cursorRefTagId, marker = marker)
            }
        }
        // Tijdens een verse detectie projecteren cursor en sensor in hetzelfde per-tag frame.
        // Tussen detecties door mag displayProjection blijven staan en volgt ARCore de camera.
        val cursorResult = if (cursorRefPose != null) {
            aprilTagResult.copy(
                imageProjectionPose = cursorRefPose,
                transformerPose = cursorRefPose,
                displayProjection = null
            )
        } else {
            aprilTagResult
        }
        val surfaceHit = estimateCursorOnReferenceSurface(
            result = cursorResult,
            dimensionsMm = project.dimensionsMm,
            knownMarkers = knownAprilTags,
            // Schermfractie → NDC: x gelijk, y omgekeerd (scherm omlaag = NDC omhoog).
            cursorNdcX = arCursorScreenOffset.x.toDouble(),
            cursorNdcY = -arCursorScreenOffset.y.toDouble(),
            placementReferenceMarker = lastPlacementReference?.marker
        )
        val depthHit = aprilTagResult.depthHitPositionMm?.snapToTransformerSurface(project.dimensionsMm)
        val objectHit = surfaceHit ?: depthHit
        if (objectHit != null) {
            lastObjectCursorAtMillis = now
        }
        val heldObjectHit = if (
            objectHit == null &&
            arCursorUsesDepth &&
            now - lastObjectCursorAtMillis <= OBJECT_CURSOR_HOLD_MILLIS
        ) {
            arCursorPosition?.let { PlaneHit(it, arCursorInsideTransformer) }
        } else {
            null
        }
        val hit = objectHit ?: heldObjectHit
        arCursorPosition = hit?.position
        arCursorInsideTransformer = hit?.insideTransformerBox ?: true
        arCursorUsesDepth = hit != null
        val cursorSource = when {
            surfaceHit != null -> "surface"
            depthHit != null -> "depth"
            heldObjectHit != null -> "hold"
            else -> "none"
        }
        arCursorSource = cursorSource
        logCursorSource(now, cursorSource, hit)
    }

    fun selectTagPlane(plane: TagPlane) {
        selectedTagPlane = plane
        activeMapView = plane.toMapView()
        reseedTagPlacement()
        message = "Vlak: ${plane.shortLabel}."
    }

    fun selectTagAnchor(anchor: TagAnchor) {
        selectedTagAnchor = anchor
        selectedTagGridUv = anchor.u to anchor.v
        clearScannedTagSelection()
        applyTagCellPlacement()
        message = "Taganker gekozen: ${anchor.label}."
    }

    /** Tik op een rastercel ([u],[v] elk 0..1) in de tagsheet: zet de coördinaten op dat punt van
     *  het huidige vlak en de rotatie op die van het vlak. */
    fun selectTagGridCell(u: Float, v: Float) {
        selectedTagGridUv = u to v
        clearScannedTagSelection()
        applyTagCellPlacement()
    }

    /** Wisselt tussen raster- en handmatige invoer. Terug naar grid zet de coördinaten weer op het
     *  laatst gekozen rasterpunt; naar handmatig laat de huidige (getypte) coördinaten staan. */
    fun chooseTagPlacementManual(manual: Boolean) {
        if (tagPlacementManual == manual) return
        tagPlacementManual = manual
        if (!manual) applyTagCellPlacement()
    }

    /** Herleidt de invoervelden uit vlak + laatst gekozen rastercel. In handmatige modus blijven de
     *  getypte coördinaten staan en volgt alleen de rotatie het vlak. Voor mode-, origin- en
     *  oriëntatiewissels die de velden opnieuw moeten zetten. */
    private fun reseedTagPlacement() {
        clearScannedTagSelection()
        if (!tagPlacementManual) {
            val (u, v) = selectedTagGridUv
            setTagFieldsFromBox(tagPositionFor(selectedTagPlane, u, v, project.dimensionsMm))
        }
        applySelectedTagRotation()
    }

    /** Expliciete rastercel-/ankerkeuze: zet altijd zowel de coördinaten als de rotatie. */
    private fun applyTagCellPlacement() {
        val (u, v) = selectedTagGridUv
        setTagFieldsFromBox(tagPositionFor(selectedTagPlane, u, v, project.dimensionsMm))
        applySelectedTagRotation()
    }

    private fun applySelectedTagRotation() {
        val rotation = tagRotationFor(selectedTagPlane)
        setTagRotation(rotation.x.toInt(), rotation.y.toInt(), rotation.z.toInt())
    }

    /** Box-positie van een rastercel op het huidige vlak — voor het oplichten van de gekozen cel. */
    fun tagCellBoxPosition(u: Float, v: Float): MmPosition =
        tagPositionFor(selectedTagPlane, u, v, project.dimensionsMm)

    /** Huidige (getypte) tag-coördinaten omgezet naar box-mm, of null bij ongeldige invoer. */
    fun currentTagBoxPositionOrNull(): MmPosition? =
        operatorPositionOrNull(tagX, tagY, tagZ)?.let { project.coordinateMapper().operatorToBox(it) }

    fun requestSetOriginCorner(originCorner: OriginCorner) {
        if (project.markers.isEmpty() && project.sensors.isEmpty()) {
            setOriginCorner(originCorner)
            return
        }
        confirmRequest = WorkflowConfirmRequest(
            title = "Origin wijzigen?",
            body = "Er staan al ${project.markers.size} tags en ${project.sensors.size} sensoren in dit project. De fysieke punten blijven staan, maar nieuwe meetinvoer gebruikt de nieuwe origin.",
            confirmText = "Wijzig origin"
        ) {
            setOriginCorner(originCorner)
        }
    }

    private fun setOriginCorner(originCorner: OriginCorner) {
        project = project.copy(coordinateFrame = defaultFrameForOrigin(originCorner))
        reseedTagPlacement()
        saveProject()
        message = "Origin ingesteld: ${project.coordinateFrame.summary}. Nieuwe invoer gebruikt dit meetframe."
    }

    fun requestToggleAxisFlip(axis: Char) {
        if (project.markers.isEmpty() && project.sensors.isEmpty()) {
            toggleAxisFlip(axis)
            return
        }
        confirmRequest = WorkflowConfirmRequest(
            title = "Asrichting wijzigen?",
            body = "Er staan al ${project.markers.size} tags en ${project.sensors.size} sensoren in dit project. De fysieke punten blijven staan, maar nieuwe meetinvoer gebruikt de nieuwe asrichting.",
            confirmText = "Wijzig as"
        ) {
            toggleAxisFlip(axis)
        }
    }

    private fun toggleAxisFlip(axis: Char) {
        val frame = project.coordinateFrame
        project = project.copy(
            coordinateFrame = when (axis.uppercaseChar()) {
                'X' -> frame.copy(flipX = !frame.flipX)
                'Y' -> frame.copy(flipY = !frame.flipY)
                'Z' -> frame.copy(flipZ = !frame.flipZ)
                else -> frame
            }
        )
        reseedTagPlacement()
        saveProject()
        message = "Orientatie opgeslagen: ${project.coordinateFrame.summary}. Bestaande punten blijven fysiek gelijk."
    }

    fun armSelectedTagSetup() {
        reseedTagPlacement()
        tagScanArmed = true
        message = "Scan nu de AprilTag op ${selectedTagPlane.shortLabel} / ${selectedTagAnchor.label}. Houd die tag in het midden van het beeld."
    }

    fun captureVisibleTagForSetup(result: AprilTagFrameResult = aprilTagResult) {
        captureTagForSetup(result = result, preferCursor = true)
    }

    fun captureLargestVisibleTagForSetup(result: AprilTagFrameResult = aprilTagResult) {
        captureTagForSetup(result = result, preferCursor = false)
    }

    private fun captureTagForSetup(result: AprilTagFrameResult, preferCursor: Boolean) {
        if (!result.hasFreshDetection()) {
            tagScanArmed = true
            message = "Nog geen nieuwe tag-detectie in dit cameraframe. Houd de gewenste AprilTag midden in beeld."
            return
        }
        val detection = if (preferCursor) {
            bestDetectionAtCursorForSetup(result)
        } else {
            largestDetectionForSetup(result)
        }
        if (detection == null) {
            tagScanArmed = true
            message = if (preferCursor && result.hasAnyDetection()) {
                "Tag zichtbaar, maar niet bij de cursor. Zet de gewenste tag in het midden van het beeld."
            } else {
                "Nog geen tag zichtbaar. Richt de cursor op de gewenste AprilTag."
            }
            return
        }
        tagId = detection.id.toString()
        rememberScannedTag(detection.id)
        tagScanArmed = false
        val isNewTag = project.markers.none { it.id == detection.id && it.isAprilTagCalibrationMarker() }
        if (isNewTag) {
            // Alle tags krijgen gelijk gewicht. Pose-selectie wisselt vrij op kwaliteit
            // (afstand/zichtbaarheid). Sensor-stabiliteit loopt via referenceTagId per sensor.
            tagPoseWeight = "1"
            // Als de huidige pose van een andere bekende tag afkomstig is, bereken de positie
            // van de nieuwe tag via ray-casting zodat beide in hetzelfde coördinatenstelsel vallen.
            val poseFromOtherTag = result.transformerPose != null && detection.id !in result.poseMarkerIds
            if (poseFromOtherTag) {
                val derivedPos = estimateSurfaceAtPixel(
                    result = result,
                    pixelX = detection.centerPx.xPx,
                    pixelY = detection.centerPx.yPx,
                    dimensionsMm = project.dimensionsMm
                )
                if (derivedPos != null) {
                    setTagFieldsFromBox(derivedPos)
                    message = "AprilTag ${detection.id} gelezen — positie afgeleid van actief referentiekader. Controleer en pas aan indien nodig."
                    return
                }
            }
        }
        message = "AprilTag ${detection.id} gelezen. Controleer meet-XYZ, rotatie en druk op Tag opslaan."
    }

    /** Bepaalt het tagvlak voor een box-positie: het [preferred] vlak (de vlak-keuze) als de positie
     *  daar daadwerkelijk op ligt, anders het vlak waar de positie wél exact op valt. Zo volgt de
     *  rotatie altijd het vlak van de positie en kunnen rotatie/positie niet mismatchen — de oorzaak
     *  van een gespiegelde/inverted pose bij handmatig getypte coördinaten. */
    private fun planeForBoxPosition(position: MmPosition, dims: MmPosition, preferred: TagPlane): TagPlane {
        fun onFace(plane: TagPlane): Boolean = when (plane) {
            TagPlane.Front -> position.y == 0
            TagPlane.Back -> position.y == dims.y
            TagPlane.Left -> position.x == 0
            TagPlane.Right -> position.x == dims.x
            TagPlane.Top -> position.z == dims.z
        }
        if (onFace(preferred)) return preferred
        return when {
            position.z == dims.z -> TagPlane.Top
            position.y == 0 -> TagPlane.Front
            position.y == dims.y -> TagPlane.Back
            position.x == 0 -> TagPlane.Left
            position.x == dims.x -> TagPlane.Right
            else -> preferred
        }
    }

    fun saveMeasuredTag() {
        val inputResult = aprilTagResult
        val typedId = tagId.toIntOrNull()
        val recentlyScannedId = typedId?.takeIf { id ->
            id == lastScannedTagId &&
                System.currentTimeMillis() - lastScannedTagAtMillis <= TAG_SCAN_SAVE_GRACE_MILLIS
        }
        val visibleDetection = if (inputResult.hasFreshDetection()) {
            bestDetectionAtCursorForSetup(inputResult)
        } else {
            null
        }
        if (inputResult.hasFreshDetection() && visibleDetection == null) {
            message = "Tag niet opgeslagen: zet de gewenste AprilTag eerst midden in beeld, zodat het juiste ID wordt gebruikt."
            return
        }
        if (!inputResult.hasFreshDetection() && inputResult.hasAnyDetection() && recentlyScannedId == null) {
            message = "Tag niet opgeslagen: de zichtbare tag is nog een korte overlay-hold. Houd de gewenste AprilTag opnieuw midden in beeld."
            return
        }
        val id = visibleDetection?.id ?: recentlyScannedId ?: typedId
        val operatorPosition = operatorPositionOrNull(tagX, tagY, tagZ)
        val size = tagSize.toIntOrNull()
        val poseWeight = tagPoseWeight.toFloatOrNull()?.coerceAtLeast(0.01f) ?: 1f
        if (id == null || operatorPosition == null || size == null) {
            message = "Tag-ID, positie en formaat moeten getallen zijn."
            return
        }
        if (size <= 0) {
            message = "Tagformaat moet groter zijn dan 0 mm."
            return
        }
        if (visibleDetection != null && tagId.toIntOrNull() != visibleDetection.id) {
            tagId = visibleDetection.id.toString()
        }
        val boxPosition = project.coordinateMapper().operatorToBox(operatorPosition)
        if (!boxPosition.insideBox(project.dimensionsMm)) {
            message = "Tagpositie valt buiten de trafo-box. Controleer origin/asrichting of meet-XYZ."
            return
        }
        val existing = project.markers.firstOrNull {
            it.id == id && it.isAprilTagCalibrationMarker()
        }
        // Rotatie volgt het VLAK waar de positie op valt (niet zomaar de vlak-keuze): zo kunnen
        // rotatie en positie nooit mismatchen, ook bij handmatig getypte coördinaten — dat
        // veroorzaakte een gespiegelde/inverted pose.
        val rotation = tagRotationFor(planeForBoxPosition(boxPosition, project.dimensionsMm, selectedTagPlane))
        setTagRotation(rotation.x.toInt(), rotation.y.toInt(), rotation.z.toInt())
        val marker = Marker(
            id = id,
            type = "apriltag",
            sizeMm = size,
            positionMm = boxPosition,
            rotationDeg = rotation,
            active = existing?.active ?: true,
            poseWeight = poseWeight
        )
        if (existing != null && existing.positionMm != boxPosition) {
            val oldOperatorPosition = project.coordinateMapper().boxToOperator(existing.positionMm)
            message = "AprilTag-ID $id staat al op meet-XYZ ${oldOperatorPosition.toReadableMm()}. Gebruik voor links en rechts unieke AprilTag-ID's, of verwijder deze tag eerst als je hem echt wilt verplaatsen."
            return
        }
        val markerAtSamePosition = project.markers.firstOrNull {
            it.id != id && it.isAprilTagCalibrationMarker() && it.positionMm == boxPosition
        }
        if (markerAtSamePosition != null) {
            message = "Op meet-XYZ ${operatorPosition.toReadableMm()} staat al AprilTag ${markerAtSamePosition.id}. Kies eerst het juiste vlak/anker of vul een andere meet-XYZ in."
            return
        }
        project = project.copy(
            markers = project.markers.filterNot { it.id == id } + marker
        )
        clearScannedTagSelection()
        resetArPoseState()
        saveProject()
        // Diagnose links/rechts-audit: log de rauwe box-positie van elke opgeslagen tag.
        logArSensRawTagCheck(project)
        // Opslaan zonder dat de camera de tag ooit gezien heeft kan (handmatig ID), maar levert
        // nooit een pose op zolang de detector hem niet herkent — meestal een tag uit een andere
        // familie dan de ingestelde dictionary. Benoem dat expliciet i.p.v. stil te slagen.
        val savedWithoutDetection = visibleDetection == null && recentlyScannedId == null
        message = "AprilTag $id opgeslagen. Meet-XYZ ${operatorPosition.toReadableMm()} | box ${marker.positionMm.toReadableMm()}." +
            if (savedWithoutDetection) {
                " Let op: deze tag is nog niet door de camera gedetecteerd — zonder detectie komt er geen pose. " +
                    "Controleer of de print uit de familie ${tagDictionary.label} komt (Instellingen)."
            } else {
                ""
            }
        // On-the-fly met een STL-assembly: zet de tag automatisch op het werkelijke modeloppervlak
        // (async via het camerascherm) zodat het AR-model op tag-diepte verschijnt.
        if (mode == WorkMode.OnTheFly && project.stlModels.any { it.visible }) {
            pendingTagSnapId = id
        }
    }

    fun requestDeleteMarker(marker: Marker) {
        confirmRequest = WorkflowConfirmRequest(
            title = "AprilTag verwijderen?",
            body = "AprilTag ${marker.id} wordt uit dit project verwijderd. De fysieke tag blijft natuurlijk bestaan, maar telt niet meer mee voor kalibratie.",
            confirmText = "Verwijder"
        ) {
            deleteMarker(marker)
        }
    }

    private fun deleteMarker(marker: Marker) {
        project = project.copy(markers = project.markers.filterNot { it.id == marker.id })
        resetArPoseState()
        saveProject()
        message = "AprilTag ${marker.id} verwijderd."
    }

    fun toggleMarkerActive(marker: Marker) {
        updateStoredMarker(marker.id) {
            it.copy(active = !it.active)
        }?.let { updated ->
            resetArPoseState()
            saveProject()
            message = if (updated.active) {
                "AprilTag ${updated.id} telt weer mee voor AR-kalibratie."
            } else {
                "AprilTag ${updated.id} staat uit en telt niet mee voor AR-kalibratie."
            }
        }
    }

    private fun updateStoredMarker(markerId: Int, transform: (Marker) -> Marker): Marker? {
        var updatedMarker: Marker? = null
        project = project.copy(
            markers = project.markers.map { marker ->
                if (marker.id == markerId && marker.isAprilTagCalibrationMarker()) {
                    transform(marker).also { updatedMarker = it }
                } else {
                    marker
                }
            }
        )
        return updatedMarker
    }

    /** Debug/migratie (links/rechts-audit): herbouwt ALLEEN [Marker.rotationDeg] van elke opgeslagen
     *  AprilTag uit het vlak dat uit [Marker.positionMm] volgt. Verplaatst geen tag en behoudt id,
     *  formaat, actief-status en poseWeight. Nodig omdat een gewijzigde [tagRotationFor] alleen NIEUWE
     *  tags raakt — bestaande tags hebben hun rotatie opgeslagen. Reset daarna de AR-fusie/pose. */
    fun rebuildAprilTagRotationsFromSurface() {
        val dims = project.dimensionsMm
        // Vlak-afleiding in dezelfde volgorde als markerSurfaceLabel/ARSensRawTagCheck.
        fun planeForSurface(p: MmPosition): TagPlane? = when {
            p.z == dims.z -> TagPlane.Top
            p.y == 0 -> TagPlane.Front
            p.y == dims.y -> TagPlane.Back
            p.x == 0 -> TagPlane.Left
            p.x == dims.x -> TagPlane.Right
            else -> null
        }
        var changed = 0
        var skipped = 0
        val updated = project.markers.map { marker ->
            if (!marker.isAprilTagCalibrationMarker()) return@map marker
            val plane = planeForSurface(marker.positionMm)
            if (plane == null) {
                skipped++
                return@map marker
            }
            val newRotation = tagRotationFor(plane)
            if (marker.rotationDeg == newRotation) {
                marker
            } else {
                changed++
                marker.copy(rotationDeg = newRotation)
            }
        }
        project = project.copy(markers = updated)
        resetArPoseState()
        saveProject()
        logArSensRawTagCheck(project)
        message = buildString {
            append("Tag-rotaties herbouwd vanuit vlak: $changed bijgewerkt")
            if (skipped > 0) append(", $skipped binnen-tag overgeslagen")
            append(". Posities/id/formaat/actief/gewicht ongewijzigd; AR-pose gereset.")
        }
    }

    /** Bestandsnamen waarvan een parse loopt — voorkomt dubbel werk als STL-scherm én
     *  camera-overlay tegelijk om meshes vragen. Alleen vanaf de main-dispatcher muteren. */
    private val stlParsingInFlight = mutableSetOf<String>()

    /** Laadvoortgang per STL-bestand (0..1) — voedt de laadbalken; entry verdwijnt zodra klaar. */
    var stlLoadProgress by mutableStateOf<Map<String, Float>>(emptyMap())
        private set

    /** Parse ontbrekende STL-meshes off-thread (assembly-delen kunnen honderden MB's zijn).
     *  Aanroepen vanuit een LaunchedEffect; de state-update gebeurt terug op main.
     *  Standaard worden alleen ZICHTBARE delen geladen (sneller, minder geheugen) — een deel
     *  aanzetten wijzigt project.stlModels en triggert daarmee automatisch een herlaadronde.
     *  [onlyVisible]=false (bv. auto-uitlijnen) laadt alles. */
    suspend fun ensureStlMeshesLoaded(onlyVisible: Boolean = true) {
        val missing = project.stlModels.filter { model ->
            (!onlyVisible || model.visible) &&
                model.fileName !in stlMeshes &&
                model.fileName !in stlParsingInFlight
        }
        if (missing.isEmpty()) return
        stlParsingInFlight += missing.map { it.fileName }
        try {
            val parsed = withContext(Dispatchers.IO) {
                missing.map { model ->
                    stlLoadProgress = stlLoadProgress + (model.fileName to 0f)
                    val mesh = StlParser.parseFile(repository.stlModelFile(model.fileName)) { fraction ->
                        // MutableState-schrijfacties zijn thread-veilig (snapshot-systeem);
                        // de parser throttlet al op ≥1%-stappen.
                        stlLoadProgress = stlLoadProgress + (model.fileName to fraction)
                    }
                    model.fileName to mesh
                }
            }
            stlMeshes = stlMeshes + parsed
            val empty = parsed.filter { it.second.isEmpty }
            if (empty.isNotEmpty()) {
                message = "Geen geldige mesh herkend in: ${empty.joinToString { it.first }}"
            }
        } finally {
            stlParsingInFlight -= missing.map { it.fileName }.toSet()
            stlLoadProgress = stlLoadProgress - missing.map { it.fileName }.toSet()
        }
    }

    /** Bouwt (eenmalig, off-thread) de AR-meshcache uit de geparste meshes: welden, oriënteren,
     *  groeperen en decimeren. Onafhankelijk van offset/rotatie/schaal — alleen een andere set
     *  bestanden of namen (groepsindeling!) maakt de cache ongeldig. */
    // Rol zit in de sleutel: de AR-groepsindeling (Tank/Deksel/…) volgt de rol van het deel.
    private fun stlArCacheKey(models: List<StlModel>, meshes: Map<String, StlMesh>): String =
        models.joinToString("|") { "${it.id}:${it.fileName}:${it.name}:${it.role.wireName}" } +
            "#" + System.identityHashCode(meshes)

    suspend fun ensureStlArPartsBuilt() {
        val meshesNow = stlMeshes
        val modelsNow = project.stlModels
        if (modelsNow.none { meshesNow[it.fileName]?.isEmpty == false }) return
        val key = stlArCacheKey(modelsNow, meshesNow)
        if (stlArPartsKey == key || stlArPartsBuilding) return
        stlArPartsBuilding = true
        try {
            if (stlArPartsKey != null) stlArParts = null // oude cache vrijgeven vóór de herbouw
            // Alleen het MID-niveau: dat tekenen Snel/Technisch/Vlakken (de default). Het zware
            // Detail-niveau (high) bouwen we pas als de gebruiker écht Detail kiest — zie
            // [ensureStlArDetailBuilt]. Scheelt bij de prep ~de helft van het chunk-werk.
            val built = withContext(Dispatchers.Default) { buildStlArParts(modelsNow, meshesNow, buildHigh = false) }
            stlArParts = built
            stlArPartsKey = key
            stlArDetailKey = null
        } finally {
            stlArPartsBuilding = false
        }
    }

    /** Bouwt (eenmalig, off-thread) óók het hoge Detail-niveau (high), zodra de Detail-modus
     *  actief is. Tot dat klaar is tekent Detail het mid-niveau; daarna upgradet het vanzelf.
     *  Her-triggeren gebeurt via de [stlArParts]-key in de aanroepende LaunchedEffect. */
    suspend fun ensureStlArDetailBuilt() {
        val meshesNow = stlMeshes
        val modelsNow = project.stlModels
        if (modelsNow.none { meshesNow[it.fileName]?.isEmpty == false }) return
        val key = stlArCacheKey(modelsNow, meshesNow)
        if (stlArDetailKey == key || stlArPartsBuilding) return
        stlArPartsBuilding = true
        try {
            val built = withContext(Dispatchers.Default) { buildStlArParts(modelsNow, meshesNow, buildHigh = true) }
            stlArParts = built
            stlArPartsKey = key
            stlArDetailKey = key
        } finally {
            stlArPartsBuilding = false
        }
    }

    /** Importeert één of meer STL-bestanden (assembly-delen). Kopieert off-thread naar de
     *  projectmap en registreert de modellen; het parsen gebeurt lazy via [ensureStlMeshesLoaded]. */
    suspend fun addStlModels(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val imported = withContext(Dispatchers.IO) {
            uris.mapNotNull { uri -> repository.importStlFile(uri) }
        }
        val failed = uris.size - imported.size
        if (imported.isEmpty()) {
            message = "STL-bestand(en) konden niet geladen worden."
            return
        }
        val newModels = imported.map { (fileName, displayName) ->
            StlModel(
                id = UUID.randomUUID().toString(),
                name = displayName,
                fileName = fileName,
                role = StlPartRole.detectFromName(displayName)
            )
        }
        project = project.copy(stlModels = project.stlModels + newModels)
        saveProject()
        message = buildString {
            append(if (imported.size == 1) "STL toegevoegd: ${newModels.first().name}" else "${imported.size} STL-delen toegevoegd")
            if (failed > 0) append(" ($failed mislukt)")
            append(" — mesh wordt geladen…")
        }
    }

    /** Vraagt eerst bevestiging vóór [autoAlignAssembly]: "Lijn uit op tank" herberekent de
     *  trafo-afmetingen (de box) uit de tankwanden — tenzij de maten vergrendeld zijn — en kan
     *  punten op de verre vlakken verschuiven. Zonder tags/sensoren meteen uitvoeren. */
    fun requestAutoAlignAssembly(scope: CoroutineScope) {
        if (project.markers.isEmpty() && project.sensors.isEmpty()) {
            scope.launch { autoAlignAssembly() }
            return
        }
        val d = project.dimensionsMm
        confirmRequest = WorkflowConfirmRequest(
            title = "Lijn uit op tank?",
            body = if (project.dimensionsLocked) {
                "De STL-delen worden op de tankwanden uitgelijnd. De trafo-afmetingen zijn vergrendeld " +
                    "en blijven ${d.x}×${d.y}×${d.z} mm; tags en sensoren behouden hun mm-positie."
            } else {
                "Dit HERBEREKENT de trafo-afmetingen (de box) uit de tankwanden en lijnt de delen uit. " +
                    "Huidige box: ${d.x}×${d.y}×${d.z} mm. Tags/sensoren houden hun mm-positie, maar punten " +
                    "op de verre vlakken (achter/rechts/boven) verschuiven mee met het nieuwe kader. Wil je " +
                    "de box behouden, vergrendel dan eerst de maten."
            },
            confirmText = "Lijn uit"
        ) { scope.launch { autoAlignAssembly() } }
    }

    /**
     * Lijnt de assembly automatisch uit, berekend vanuit de TANK als referentiedeel:
     *  - tank (naam bevat "tank", anders het grootste deel): bbox-min → (0,0,0), en optioneel
     *    worden de projectafmetingen op de tank-bbox gezet (project = tank);
     *  - deksel ("cover"/"deksel"): gecentreerd in X/Y, onderkant van de hoofdplaat op de tankrand;
     *  - overige delen (kern, actief deel, wikkelingen): gecentreerd in X/Y, onderkant op de vloer.
     * De STL-delen van een assembly hebben elk hun EIGEN lokale oorsprong (vastgesteld bij de
     * 1485-assembly), dus zonder deze stap staan ze kriskras. Daarna fijnslijpen via de 3D-editor.
     */
    suspend fun autoAlignAssembly(adoptTankDimensions: Boolean = true) {
        val built = buildTankFrame(adoptTankDimensions)
        if (built == null) {
            message = "Geen geldige STL-meshes om uit te lijnen."
            return
        }
        val (frame, withMesh) = built
        val meshById = withMesh.associate { (m, mesh) -> m.id to mesh }
        val manualParts = mutableListOf<String>()
        val aligned = project.stlModels.map { model ->
            val mesh = meshById[model.id] ?: return@map model
            val placed = placeInTankFrame(model, mesh, frame)
            if (placed.manual) manualParts += model.name
            placed.model
        }
        project = project.copy(stlModels = aligned, dimensionsMm = frame.dims)
        syncDimensionsFromProject()
        saveProject()
        val frameNote = if (frame.sharedZGroup.isNotEmpty()) " — gedeeld CAD-frame herkend" else ""
        val manualNote = if (manualParts.isEmpty()) "" else " · Handmatig plaatsen: ${manualParts.joinToString()}"
        val lockNote = if (project.dimensionsLocked) " (maten vergrendeld: handmatige afmetingen behouden)" else ""
        message = "Assembly uitgelijnd op tankwanden: ${frame.dims.x}×${frame.dims.y}×${frame.dims.z} mm$frameNote$manualNote$lockNote."
    }

    /** Lijnt één deel uit op de tank; tank en overige delen blijven staan en de maten wijzigen niet.
     *  Voor de per-deel-knop "Lijn uit op tank" in de kader-sheet. */
    fun alignPartToTank(id: String, scope: CoroutineScope) {
        scope.launch {
            val name = project.stlModels.firstOrNull { it.id == id }?.name ?: "Onderdeel"
            message = when (placePartsInTankFrame(listOf(id))) {
                -1 -> "Geen geldige STL-meshes om uit te lijnen."
                0 -> "$name: geen automatische plaatsing mogelijk (geen gedeeld CAD-frame) — verzet handmatig."
                else -> "$name uitgelijnd op de tank."
            }
        }
    }

    /** Herberekent ALLEEN de box-maten uit het 3D-model (tank/deksel-wandbox); de delen blijven
     *  staan. Respecteert vergrendelde maten — ontgrendel eerst om te overschrijven. */
    fun recomputeBoxFromStl(scope: CoroutineScope) {
        scope.launch {
            if (project.dimensionsLocked) {
                message = "Maten zijn vergrendeld — ontgrendel eerst om de box uit het model te halen."
                return@launch
            }
            val built = buildTankFrame(adoptTankDimensions = true)
            if (built == null) {
                message = "Geen geldige STL-meshes om de box te berekenen."
                return@launch
            }
            val stl = built.first.dims
            if (stl == project.dimensionsMm) {
                message = "Box is al ${stl.x}×${stl.y}×${stl.z} mm."
                return@launch
            }
            project = project.copy(dimensionsMm = stl)
            syncDimensionsFromProject()
            saveProject()
            message = "Box uit 3D-model: ${stl.x}×${stl.y}×${stl.z} mm (delen ongewijzigd)."
        }
    }

    /** Importeert STL's en vraagt daarna of we ze automatisch op de tank plaatsen (i.p.v. stil
     *  uitlijnen). Bevestigen → nieuwe delen op de tank; is de tank zélf erbij → volledige uitlijning. */
    fun addStlModelsThenAskPlace(uris: List<Uri>, scope: CoroutineScope) {
        scope.launch {
            val before = project.stlModels.map { it.id }.toSet()
            addStlModels(uris)
            val newIds = project.stlModels.map { it.id }.filter { it !in before }
            if (newIds.isEmpty()) return@launch
            val addedTank = project.stlModels.any { it.id in newIds && it.role == StlPartRole.Tank }
            val label = if (newIds.size == 1) "het onderdeel" else "de ${newIds.size} onderdelen"
            confirmRequest = WorkflowConfirmRequest(
                title = "Automatisch plaatsen?",
                body = "Wil je $label op de tank uitlijnen? Bijsturen kan later per onderdeel in de kader-sheet.",
                confirmText = "Plaats op tank"
            ) {
                scope.launch {
                    if (addedTank) autoAlignAssembly() else placePartsInTankFrame(newIds)
                }
            }
        }
    }

    /** Plaatst de opgegeven delen in het tankframe (tank + overige blijven staan, maten ongewijzigd).
     *  Retour: aantal geplaatste delen, of -1 als er geen bruikbare meshes zijn. */
    private suspend fun placePartsInTankFrame(ids: List<String>): Int {
        val built = buildTankFrame(adoptTankDimensions = false) ?: return -1
        val (frame, withMesh) = built
        val meshById = withMesh.associate { (m, mesh) -> m.id to mesh }
        val idSet = ids.toSet()
        var count = 0
        val updated = project.stlModels.map { model ->
            if (model.id !in idSet) return@map model
            val mesh = meshById[model.id] ?: return@map model
            val placed = placeInTankFrame(model, mesh, frame)
            if (placed.manual) return@map model
            count++
            placed.model
        }
        if (count > 0) {
            project = project.copy(stlModels = updated)
            saveProject()
        }
        return count
    }

    /** Bouwt de tank-referentiecontext + de (deel, mesh)-lijst. Laadt alle meshes. Null = geen mesh. */
    private suspend fun buildTankFrame(
        adoptTankDimensions: Boolean
    ): Pair<TankFrame, List<Pair<StlModel, StlMesh>>>? {
        // Uitlijnen heeft álle meshes nodig, ook van delen die nu onzichtbaar staan.
        ensureStlMeshesLoaded(onlyVisible = false)
        fun meshOf(model: StlModel): StlMesh? = stlMeshes[model.fileName]?.takeIf { !it.isEmpty }
        val withMesh = project.stlModels.mapNotNull { m -> meshOf(m)?.let { m to it } }
        if (withMesh.isEmpty()) return null
        fun volumeOf(mesh: StlMesh) =
            (mesh.maxX - mesh.minX).toDouble() * (mesh.maxY - mesh.minY) * (mesh.maxZ - mesh.minZ)
        // Box-master: grootste deel met rol Tank (voorkomt dat een verkeerd gedetecteerde deksel
        // de referentie kaapt), anders op naam, anders de DEKSEL als fallback (geen tank aanwezig),
        // anders het grootste deel.
        val (tankModel, tankMesh) = withMesh.filter { (m, _) -> m.role == StlPartRole.Tank }
            .maxByOrNull { (_, mesh) -> volumeOf(mesh) }
            ?: withMesh.firstOrNull { (m, _) -> m.name.contains("tank", ignoreCase = true) }
            ?: withMesh.filter { (m, _) -> m.role == StlPartRole.Cover }
                .maxByOrNull { (_, mesh) -> volumeOf(mesh) }
            ?: withMesh.maxBy { (_, mesh) -> volumeOf(mesh) }

        // Tank: WANDBOX bepaalt afmetingen én nulpunt, zodat de wanden exact op 0..dims liggen
        // (ribben/radiatoren steken erbuiten en tellen niet mee — daar zitten geen sensoren op).
        val tankWall = wallBoundsOf(tankModel, tankMesh)
        val ts = tankModel.scalePercent / 100f
        // Echte tankrand in projectframe, ONAFHANKELIJK van dims.z: hierop rust de deksel. Bij
        // vergrendelde/handmatige maten kan dims.z afwijken; dan zou een op dims.z geplaatste deksel zweven.
        val tankTopZ = (tankWall[5] - tankWall[2]) * ts
        // Box-hoogte: is er náást de tank óók een deksel, dan loopt de box door tot de BOVENKANT van
        // het MASSIEVE deksel (wandbox-Z-max = géén ribben/uitstulpsels). De deksel-flens rust op de
        // tankrand, dus deksel-top = tankrand + deksel-wandboxhoogte. Geen apart deksel → tot de tankrand.
        val coverEntry = withMesh
            .filter { (m, _) -> m.role == StlPartRole.Cover && m.id != tankModel.id }
            .maxByOrNull { (_, mesh) -> volumeOf(mesh) }
        val boxHeightMm = if (coverEntry != null) {
            val (coverModel, coverMesh) = coverEntry
            val coverWall = wallBoundsOf(coverModel, coverMesh)
            tankTopZ + (coverWall[5] - coverWall[2]) * (coverModel.scalePercent / 100f)
        } else {
            tankTopZ
        }
        // Vergrendelde maten zijn leidend: de operator heeft ze bewust handmatig ingevoerd.
        val dims = if (adoptTankDimensions && !project.dimensionsLocked) {
            MmPosition(
                x = ((tankWall[3] - tankWall[0]) * ts).roundToInt().coerceAtLeast(1),
                y = ((tankWall[4] - tankWall[1]) * ts).roundToInt().coerceAtLeast(1),
                z = boxHeightMm.roundToInt().coerceAtLeast(1)
            )
        } else {
            project.dimensionsMm
        }
        val tankOffset = MmPosition(
            x = (-tankWall[0] * ts).roundToInt(),
            y = (-tankWall[1] * ts).roundToInt(),
            z = (-tankWall[2] * ts).roundToInt()
        )
        val base = TankFrame(
            tankModelId = tankModel.id,
            tankModel = tankModel,
            tankOffset = tankOffset,
            dims = dims,
            tankTopZ = tankTopZ,
            tankWallMidX = (tankWall[0] + tankWall[3]) / 2f,
            tankWallMidY = (tankWall[1] + tankWall[4]) / 2f,
            tankWallW = (tankWall[3] - tankWall[0]).coerceAtLeast(1f),
            tankWallD = (tankWall[4] - tankWall[1]).coerceAtLeast(1f),
            sharedZGroup = emptySet(),
            groupMinZ = 0f,
            floorOffset = assemblyFloorOffsetMm.toIntOrNull() ?: 0
        )
        // Binnenwerk (kern/wikkelingen/actief deel) dat één Z-frame deelt → als GROEP op de vloer,
        // zodat de onderlinge hoogtes (wikkelingen ZWEVEN boven de vloer) kloppen.
        val interior = withMesh.filter { (m, _) ->
            m.id != tankModel.id &&
                (m.role == StlPartRole.Core || m.role == StlPartRole.ActivePart || m.role == StlPartRole.Wiksets)
        }
        val sharedInterior = interior.filter { (m, mesh) -> sharesTankFrame(m, mesh, base) }
        var frame = base
        if (sharedInterior.size >= 2) {
            val zRanges = sharedInterior.map { (m, mesh) -> fullBoundsOf(m, mesh).let { it[2] to it[5] } }
            val zMids = zRanges.map { (lo, hi) -> (lo + hi) / 2f }
            val maxHeight = zRanges.maxOf { (lo, hi) -> hi - lo }
            if (zMids.max() - zMids.min() <= maxHeight * 0.5f) {
                frame = base.copy(
                    sharedZGroup = sharedInterior.map { it.first.id }.toSet(),
                    groupMinZ = zRanges.minOf { it.first }
                )
            }
        }
        return frame to withMesh
    }

    private fun isZeroRot(m: StlModel) =
        m.rotationDeg.x == 0 && m.rotationDeg.y == 0 && m.rotationDeg.z == 0

    private fun wallBoundsOf(model: StlModel, mesh: StlMesh): FloatArray = mesh.rotatedBoundsOfBox(
        mesh.estimateCoreBounds(), model.rotationDeg.x, model.rotationDeg.y, model.rotationDeg.z
    )

    private fun fullBoundsOf(model: StlModel, mesh: StlMesh): FloatArray =
        mesh.rotatedBounds(model.rotationDeg.x, model.rotationDeg.y, model.rotationDeg.z)

    /** Gedeeld CAD-frame? Alle delen om hetzelfde X/Y-middelpunt gecentreerd (wandmiddens ≈
     *  tankwandmidden) → de tank-offset is voor iedereen exact goed en asymmetrische uitstulpsels
     *  (conservator op het deksel!) blijven op hun echte plek i.p.v. scheefgetrokken door centreren. */
    private fun sharesTankFrame(model: StlModel, mesh: StlMesh, frame: TankFrame): Boolean {
        if (!isZeroRot(model) || !isZeroRot(frame.tankModel)) return false
        if (model.scalePercent != frame.tankModel.scalePercent) return false
        val w = wallBoundsOf(model, mesh)
        val midX = (w[0] + w[3]) / 2f
        val midY = (w[1] + w[4]) / 2f
        return kotlin.math.abs(midX - frame.tankWallMidX) <= frame.tankWallW * 0.12f &&
            kotlin.math.abs(midY - frame.tankWallMidY) <= frame.tankWallD * 0.12f
    }

    /** Plaatsing van één deel in het tankframe. [PlacedPart.manual] = niet automatisch te plaatsen. */
    private fun placeInTankFrame(model: StlModel, mesh: StlMesh, frame: TankFrame): PlacedPart {
        if (model.id == frame.tankModelId) {
            return PlacedPart(model.copy(offsetMm = frame.tankOffset), manual = false)
        }
        val s = model.scalePercent / 100f
        val full = fullBoundsOf(model, mesh)
        val wall = wallBoundsOf(model, mesh)
        val shared = sharesTankFrame(model, mesh, frame)
        // Bushings/turrets en "overig": positie behouden. Bij gedeeld frame is de tankoffset in X/Y
        // exact goed; hun eigen Z-frame is niet betrouwbaar → Z blijft staan; anders "handmatig".
        if (model.role == StlPartRole.BushingsTurrets || model.role == StlPartRole.Other) {
            return if (shared) {
                PlacedPart(
                    model.copy(offsetMm = MmPosition(frame.tankOffset.x, frame.tankOffset.y, model.offsetMm.z)),
                    manual = false
                )
            } else {
                PlacedPart(model, manual = true)
            }
        }
        val offX: Int
        val offY: Int
        if (shared) {
            offX = frame.tankOffset.x
            offY = frame.tankOffset.y
        } else {
            // Fallback: centreer op het WANDBOX-midden (niet bbox — uitstulpsels trekken anders scheef).
            offX = (frame.dims.x / 2f - (wall[0] + wall[3]) / 2f * s).roundToInt()
            offY = (frame.dims.y / 2f - (wall[1] + wall[4]) / 2f * s).roundToInt()
        }
        val offZ = when {
            // Deksel: dominante vlakke onderkant (wandbox-Z-min = montageflens, robuust tegen
            // kleine uitstulpsels die onder de oppervlakte-drempel wegvallen) op de tankrand
            // (tank-wandbox-Z-max). Alleen Z-offset; niets aan het model.
            model.role == StlPartRole.Cover -> (frame.tankTopZ - wall[2] * s).roundToInt()
            // Binnenwerk-groep: gedeelde Z-offset, laagste deel op de vloer (+ vloeroffset).
            model.id in frame.sharedZGroup -> (-frame.groupMinZ * s).roundToInt() + frame.floorOffset
            // Los binnenwerk: eigen onderkant op de vloer (+ vloeroffset).
            else -> (-full[2] * s).roundToInt() + frame.floorOffset
        }
        return PlacedPart(model.copy(offsetMm = MmPosition(offX, offY, offZ)), manual = false)
    }

    private data class TankFrame(
        val tankModelId: String,
        val tankModel: StlModel,
        val tankOffset: MmPosition,
        val dims: MmPosition,
        val tankTopZ: Float,
        val tankWallMidX: Float,
        val tankWallMidY: Float,
        val tankWallW: Float,
        val tankWallD: Float,
        val sharedZGroup: Set<String>,
        val groupMinZ: Float,
        val floorOffset: Int
    )

    private class PlacedPart(val model: StlModel, val manual: Boolean)

    /** Vloeroffset (mm) voor het binnenwerk bij "Lijn uit op tank": onderkant = tankbodem + offset. */
    var assemblyFloorOffsetMm by mutableStateOf("0")

    /** Past de handmatig ingevulde lengte/breedte/hoogte toe op het project. Bestaande tags en
     *  sensoren behouden hun mm-positie; alleen het boxkader (en dus de vlakposities) verandert. */
    fun applyManualDimensions() {
        val dimensions = dimensionsFromFields()
        if (dimensions == null) {
            message = "Vul geldige trafo-afmetingen in hele millimeters in."
            return
        }
        if (dimensions == project.dimensionsMm) {
            message = "Afmetingen zijn al ${dimensions.x}×${dimensions.y}×${dimensions.z} mm."
            return
        }
        val apply = {
            project = project.copy(dimensionsMm = dimensions)
            syncDimensionsFromProject()
            saveProject()
            message = "Trafo-afmetingen ingesteld op ${dimensions.x}×${dimensions.y}×${dimensions.z} mm." +
                if (project.dimensionsLocked) " (vergrendeld)" else ""
        }
        if (project.markers.isEmpty() && project.sensors.isEmpty()) {
            apply()
            return
        }
        confirmRequest = WorkflowConfirmRequest(
            title = "Afmetingen wijzigen?",
            body = "Er staan al ${project.markers.size} tags en ${project.sensors.size} sensoren in dit project. " +
                "Hun mm-posities blijven staan, maar punten op de verre vlakken (achter/rechts/boven) " +
                "verschuiven mee met het nieuwe kader.",
            confirmText = "Wijzig afmetingen"
        ) { apply() }
    }

    /** Maten vergrendelen: STL-import / "Lijn uit op tank" overschrijft de afmetingen dan niet meer. */
    fun setDimensionsLocked(locked: Boolean) {
        if (project.dimensionsLocked == locked) return
        project = project.copy(dimensionsLocked = locked)
        saveProject()
        message = if (locked) {
            "Maten vergrendeld: STL-import overschrijft de trafo-afmetingen niet meer."
        } else {
            "Maten ontgrendeld: \"Lijn uit op tank\" neemt de tankafmetingen weer over."
        }
    }

    fun setStlRole(id: String, role: StlPartRole) {
        project = project.copy(
            stlModels = project.stlModels.map { if (it.id == id) it.copy(role = role) else it }
        )
        saveProject()
    }

    fun removeStlModel(id: String) {
        val model = project.stlModels.firstOrNull { it.id == id } ?: return
        project = project.copy(stlModels = project.stlModels.filterNot { it.id == id })
        repository.deleteStlFile(model.fileName)
        stlMeshes = stlMeshes - model.fileName
        saveProject()
        message = "3D-model verwijderd: ${model.name}"
    }

    fun toggleStlVisible(id: String) {
        project = project.copy(
            stlModels = project.stlModels.map { if (it.id == id) it.copy(visible = !it.visible) else it }
        )
        saveProject()
    }

    fun updateStlScale(id: String, percent: Int) {
        project = project.copy(
            stlModels = project.stlModels.map {
                if (it.id == id) it.copy(scalePercent = percent.coerceIn(1, 100_000)) else it
            }
        )
        saveProject()
    }

    fun updateStlOffset(id: String, axis: Char, value: Int) {
        project = project.copy(
            stlModels = project.stlModels.map {
                if (it.id != id) it else {
                    val offset = it.offsetMm
                    it.copy(
                        offsetMm = when (axis) {
                            'x', 'X' -> offset.copy(x = value)
                            'y', 'Y' -> offset.copy(y = value)
                            else -> offset.copy(z = value)
                        }
                    )
                }
            }
        )
        saveProject()
    }

    /** Rotatie (graden) per as, genormaliseerd naar [0,360). Verandert alleen de render-transform, niet de geometrie. */
    fun updateStlRotation(id: String, axis: Char, value: Int) {
        val norm = ((value % 360) + 360) % 360
        project = project.copy(
            stlModels = project.stlModels.map {
                if (it.id != id) it else {
                    val rot = it.rotationDeg
                    it.copy(
                        rotationDeg = when (axis) {
                            'x', 'X' -> rot.copy(x = norm)
                            'y', 'Y' -> rot.copy(y = norm)
                            else -> rot.copy(z = norm)
                        }
                    )
                }
            }
        )
        saveProject()
    }

    /** Zet de offset zó dat het midden van de (geschaalde) STL samenvalt met het midden van de trafo-box,
     *  zodat de STL uitlijnt met de tags/sensoren die in hetzelfde frame staan. Huidige schaal blijft. */
    fun centerStlInBox(id: String) = positionStlInBox(id, fitToBox = false)

    /** Schaalt de STL eerst uniform zodat hij binnen de trafo-box past en centreert hem daarna. */
    fun fitStlInBox(id: String) = positionStlInBox(id, fitToBox = true)

    private fun positionStlInBox(id: String, fitToBox: Boolean) {
        val model = project.stlModels.firstOrNull { it.id == id } ?: return
        val mesh = stlMeshes[model.fileName]
        if (mesh == null || mesh.isEmpty) {
            message = "Geen geldige mesh om te centreren."
            return
        }
        val box = project.dimensionsMm
        val scalePercent = if (fitToBox) {
            // Geroteerde bounding box, zodat de STL na rotatie nog steeds exact in de box past.
            val ext = mesh.rotatedExtents(model.rotationDeg.x, model.rotationDeg.y, model.rotationDeg.z)
            val factor = minOf(box.x / ext[0], box.y / ext[1], box.z / ext[2])
            (factor * 100f).toInt().coerceIn(1, 100_000)
        } else {
            model.scalePercent
        }
        val scale = scalePercent / 100f
        // Wereldpositie = rauw * scale + offset. Voor centreren: offset = boxmidden - meshmidden * scale.
        val offset = MmPosition(
            Math.round(box.x / 2f - mesh.centerX * scale),
            Math.round(box.y / 2f - mesh.centerY * scale),
            Math.round(box.z / 2f - mesh.centerZ * scale)
        )
        project = project.copy(
            stlModels = project.stlModels.map {
                if (it.id == id) it.copy(scalePercent = scalePercent, offsetMm = offset) else it
            }
        )
        saveProject()
        message = if (fitToBox) "STL passend gemaakt en gecentreerd in de trafo-box." else "STL gecentreerd in de trafo-box."
    }

    fun importSensors(uri: Uri) {
        runCatching { repository.importSensorsFromCsv(uri) }
            .onSuccess {
                project = project.copy(sensors = it)
                saveProject()
                message = "${it.size} sensoren geimporteerd."
            }
            .onFailure {
                message = "CSV import mislukt: ${it.message ?: "onbekende fout"}"
            }
    }

    fun saveSensorPoint() {
        saveSensorPointInternal(markInstalled = false)
    }

    private fun saveSensorPointInternal(markInstalled: Boolean): Sensor? {
        val operatorPosition = operatorPositionOrNull(sensorX, sensorY, sensorZ)
        val tolerance = sensorTolerance.toIntOrNull()
        val id = sensorId.ifBlank { nextSensorId() }.trim()
        val name = sensorName.ifBlank { "sens" }.trim()
        if (operatorPosition == null || tolerance == null) {
            message = "Sensorpositie en tolerantie moeten hele millimeters zijn."
            return null
        }
        val boxPosition = project.coordinateMapper().operatorToBox(operatorPosition)
        if (!boxPosition.insideBox(project.dimensionsMm)) {
            message = "Sensorpositie valt buiten de trafo-box. Controleer origin/asrichting of meet-XYZ."
            return null
        }
        val existingSensor = project.sensors.firstOrNull { it.id == id }
        val nextOrder = (project.sensors.maxOfOrNull { it.order } ?: 0) + 1
        val sensor = Sensor(
            order = existingSensor?.order ?: nextOrder,
            id = id,
            name = name,
            side = "veld",
            positionMm = boxPosition,
            toleranceMm = tolerance,
            instruction = sensorInstruction.trim(),
            status = if (markInstalled) SensorStatus.Ok else existingSensor?.status ?: SensorStatus.Pending,
            // Koppel aan een referentietag zodat de overlay altijd via die specifieke tag
            // geprojecteerd wordt. On-the-fly: de actief gescande tag. Voorbereid: de
            // dichtstbijzijnde tag (zo verschijnt de sensor bij die tag zodra hij gescand wordt).
            referenceTagId = sensorReferenceTagId
                ?: existingSensor?.referenceTagId
                ?: nearestTagIdForBox(boxPosition)
        )
        val sensors = (project.sensors.filterNot { it.id == sensor.id } + sensor)
            .sortedBy { it.order }
            .mapIndexed { index, item -> item.copy(order = index + 1) }
        project = project.copy(sensors = sensors)
        if (markInstalled) {
            log = confirmSensorAtMeasuredPosition(
                log = log,
                sensor = sensor,
                measuredPosition = boxPosition,
                photo = null,
                confirmedAt = repository.nowIso()
            )
            repository.saveLog(log)
        }
        resetSensorFormForNext()
        showSensorOverlay = true
        saveProject()
        message = if (markInstalled) {
            "Sensor ${sensor.id} geplaatst en bevestigd op meet-XYZ ${operatorPosition.toReadableMm()}."
        } else {
            "Sensor ${sensor.id} opgeslagen op meet-XYZ ${operatorPosition.toReadableMm()}."
        }
        return sensor
    }

    fun saveSensorAtCursor() {
        val cursor = arCursorPosition
        if (cursor == null) {
            message = "Sensor niet opgeslagen: geen objectvlak onder de AR-cursor. Scan een bekende tag en richt de cursor op de trafo."
            return
        }
        if (arCursorSource != "surface" && arCursorSource != "depth") {
            message = "Sensor niet opgeslagen: richt de cursor op het trafovlak met een verse tag-pose. Een oude cursor wordt niet opgeslagen."
            return
        }
        if (!arCursorInsideTransformer) {
            message = "Cursor raakt buiten de trafo-afmetingen. Richt opnieuw op de trafo."
            return
        }
        // Sla de tag op die nu de pose levert — dit wordt de vaste referentietag voor deze sensor.
        // Bij een stale tag (ARCore-fallback) is poseMarkerIds leeg; val dan terug op de laatst
        // gebruikte plaatsingsreferentie zodat de sensor toch aan de juiste tag gekoppeld wordt.
        sensorReferenceTagId = aprilTagResult.poseMarkerIds.firstOrNull()
            ?: lastPlacementReference?.tagId
        setSensorFieldsFromBox(cursor)
        if (sensorId.isBlank()) sensorId = nextSensorId()
        if (sensorName.isBlank()) sensorName = "sens"
        saveSensorPointInternal(markInstalled = true)
    }

    fun saveSensorAtBoxPosition(position: MmPosition) {
        if (!position.insideBox(project.dimensionsMm)) {
            message = "Sensorpunt valt buiten de trafo-box."
            return
        }
        setSensorFieldsFromBox(position)
        if (sensorId.isBlank()) sensorId = nextSensorId()
        if (sensorName.isBlank()) sensorName = "sens"
        saveSensorPointInternal(markInstalled = false)
    }

    fun saveTagAtBoxPosition(position: MmPosition, viewName: String) {
        if (!position.insideBox(project.dimensionsMm)) {
            message = "Tagpunt valt buiten de trafo-box."
            return
        }
        val plane = tagPlaneForMapView(viewName, position)
        val requestedId = tagId.toIntOrNull()
        val id = requestedId ?: nextAprilTagId()
        val size = tagSize.toIntOrNull()?.takeIf { it > 0 } ?: 100
        val rotation = tagRotationFor(plane)
        upsertPreparedTag(
            id = id,
            size = size,
            position = position,
            rotation = rotation,
            allowMoveExisting = true,
            advanceToNextId = true,
            messagePrefix = "AprilTag $id voorbereid op ${plane.label}"
        )
    }

    fun saveSelectedPreparedTag() {
        val placement = tagPlacementFor(selectedTagPlane, selectedTagAnchor, project.dimensionsMm)
        setTagFieldsFromBox(placement.positionMm)
        setTagRotation(
            placement.rotationDeg.x.toInt(),
            placement.rotationDeg.y.toInt(),
            placement.rotationDeg.z.toInt()
        )
        saveTagAtBoxPosition(placement.positionMm, placement.plane.name)
    }

    fun savePreparedTagFromFields() {
        val id = tagId.toIntOrNull()
        val operatorPosition = operatorPositionOrNull(tagX, tagY, tagZ)
        val size = tagSize.toIntOrNull()
        val rotX = tagRotX.toFloatOrNull()
        val rotY = tagRotY.toFloatOrNull()
        val rotZ = tagRotZ.toFloatOrNull()
        val poseWeight = tagPoseWeight.toFloatOrNull()?.coerceAtLeast(0.01f)
        if (id == null || operatorPosition == null || size == null || rotX == null || rotY == null || rotZ == null) {
            message = "Tag-ID, positie, formaat en rotatie moeten getallen zijn."
            return
        }
        val boxPosition = project.coordinateMapper().operatorToBox(operatorPosition)
        if (!boxPosition.insideBox(project.dimensionsMm)) {
            message = "Tagpositie valt buiten de trafo-box. Controleer origin/asrichting of meet-XYZ."
            return
        }
        val existed = project.markers.any { it.id == id && it.isAprilTagCalibrationMarker() }
        upsertPreparedTag(
            id = id,
            size = size,
            position = boxPosition,
            rotation = FloatVector(rotX, rotY, rotZ),
            allowMoveExisting = true,
            advanceToNextId = !existed,
            messagePrefix = "AprilTag $id opgeslagen",
            poseWeight = poseWeight
        )
    }

    fun moveTagToBoxPosition(tagId: Int, position: MmPosition, viewName: String) {
        if (!position.insideBox(project.dimensionsMm)) {
            message = "Tagpunt valt buiten de trafo-box."
            return
        }
        val existing = project.markers.firstOrNull { it.id == tagId && it.isAprilTagCalibrationMarker() }
        if (existing == null) {
            message = "AprilTag $tagId bestaat niet meer."
            return
        }
        val plane = tagPlaneForMapView(viewName, position)
        upsertPreparedTag(
            id = tagId,
            size = existing.sizeMm,
            position = position,
            rotation = tagRotationFor(plane),
            allowMoveExisting = true,
            advanceToNextId = false,
            messagePrefix = "AprilTag $tagId verplaatst naar ${plane.label}"
        )
    }

    private fun upsertPreparedTag(
        id: Int,
        size: Int,
        position: MmPosition,
        rotation: FloatVector,
        allowMoveExisting: Boolean,
        advanceToNextId: Boolean,
        messagePrefix: String,
        poseWeight: Float? = null
    ): Boolean {
        val existing = project.markers.firstOrNull { it.id == id && it.isAprilTagCalibrationMarker() }
        if (existing != null && existing.positionMm != position && !allowMoveExisting) {
            val oldOperatorPosition = project.coordinateMapper().boxToOperator(existing.positionMm)
            message = "AprilTag-ID $id staat al op meet-XYZ ${oldOperatorPosition.toReadableMm()}. Gebruik Verplaats of Bewerk om deze tag te wijzigen."
            return false
        }
        val markerAtSamePosition = project.markers.firstOrNull {
            it.id != id && it.isAprilTagCalibrationMarker() && it.positionMm == position
        }
        if (markerAtSamePosition != null) {
            message = "Op meet-XYZ ${operatorText(position)} staat al AprilTag ${markerAtSamePosition.id}."
            return false
        }
        val marker = Marker(
            id = id,
            type = "apriltag",
            sizeMm = size,
            positionMm = position,
            rotationDeg = rotation,
            active = existing?.active ?: true,
            poseWeight = poseWeight ?: existing?.poseWeight ?: 1f
        )
        project = project.copy(markers = project.markers.filterNot { it.id == id && it.isAprilTagCalibrationMarker() } + marker)
        setTagFieldsFromBox(position)
        tagPoseWeight = marker.poseWeight.toString()
        setTagRotation(rotation.x.toInt(), rotation.y.toInt(), rotation.z.toInt())
        if (advanceToNextId) {
            tagId = nextAprilTagId().toString()
        } else {
            tagId = id.toString()
        }
        clearScannedTagSelection()
        resetArPoseState()
        saveProject()
        message = "$messagePrefix, meet-XYZ ${operatorText(position)}."
        return true
    }

    fun startPreparedInstallation() {
        val activeTags = knownAprilTags.size
        if (activeTags == 0) {
            message = "Plaats eerst minimaal een AprilTag in het 2D model."
            return
        }
        if (project.sensors.isEmpty()) {
            message = "Plaats eerst minimaal een sensorpunt in het 2D model."
            return
        }
        mode = WorkMode.Prepared
        showTagOverlay = true
        showSensorOverlay = true
        model2dBackTarget = WorkflowScreen.Start
        model2dPurpose = Model2DPurpose.PreparedSetup
        resetArPoseState()
        go(WorkflowScreen.Install)
        val ids = knownAprilTags.sortedBy { it.id }.joinToString(", ") { marker ->
            "${marker.id}/${markerSurfaceLabel(marker, project.dimensionsMm)}"
        }
        message = if (knownAprilTags.size < 2) {
            "Camera gestart met $ids. Een enkele tag blijft live zichtbaar, maar 3D-pose kan springen; plaats liever 2-3 tags op verschillende punten."
        } else {
            "Camera gestart met voorbereide AprilTag-ID's: $ids. Scan dezelfde fysieke tags; onbekende ID's worden als nieuw getoond."
        }
    }

    fun returnToPreparedSetup() {
        mode = WorkMode.Prepared
        showTagOverlay = true
        showSensorOverlay = project.sensors.isNotEmpty()
        resetArPoseState()
        open2DModel(WorkflowScreen.Start, Model2DPurpose.PreparedSetup, pushHistory = false)
    }

    fun useArCursorEstimate() {
        val cursor = arCursorPosition
        if (cursor == null) {
            message = if (aprilTagResult.transformerPose == null) {
                "Automatische cursorpositie kan nog niet: er is eerst een stabiele AprilTag-pose nodig."
            } else {
                "AprilTag-pose is bekend, maar de cursor raakt nog geen objectvlak."
            }
        } else {
            setCursorFieldsFromBox(cursor)
            message = "AR-cursor overgenomen als meet-XYZ ${operatorText(cursor)}."
        }
    }

    fun selectSensorForEdit(sensor: Sensor) {
        val operatorPosition = project.coordinateMapper().boxToOperator(sensor.positionMm)
        sensorId = sensor.id
        sensorName = sensor.name
        sensorX = operatorPosition.x.toString()
        sensorY = operatorPosition.y.toString()
        sensorZ = operatorPosition.z.toString()
        sensorTolerance = sensor.toleranceMm.toString()
        sensorInstruction = sensor.instruction
    }

    fun moveSensorToBoxPosition(sensorId: String, position: MmPosition) {
        if (!position.insideBox(project.dimensionsMm)) {
            message = "Sensorpunt valt buiten de trafo-box."
            return
        }
        val existing = project.sensors.firstOrNull { it.id == sensorId }
        if (existing == null) {
            message = "Sensor $sensorId bestaat niet meer."
            return
        }
        val updated = existing.copy(
            positionMm = position,
            // Herkoppel aan de dichtstbijzijnde tag op de nieuwe plek (behoud oude als er geen is).
            referenceTagId = nearestTagIdForBox(position) ?: existing.referenceTagId
        )
        project = project.copy(
            sensors = project.sensors.map { sensor ->
                if (sensor.id == sensorId) updated else sensor
            }
        )
        selectSensorForEdit(updated)
        showSensorOverlay = true
        saveProject()
        message = "Sensor $sensorId verplaatst naar meet-XYZ ${operatorText(position)}."
    }

    fun selectTagForEdit(marker: Marker) {
        val normalized = marker.asAprilTagCalibrationMarker(project.dimensionsMm)
        tagId = normalized.id.toString()
        tagSize = normalized.sizeMm.toString()
        tagPoseWeight = normalized.poseWeight.toString()
        setTagFieldsFromBox(normalized.positionMm)
        setTagRotation(
            normalized.rotationDeg.x.toInt(),
            normalized.rotationDeg.y.toInt(),
            normalized.rotationDeg.z.toInt()
        )
        selectedTagPlane = tagPlaneForSurfacePosition(normalized.positionMm)
        selectedTagAnchor = nearestTagAnchorForPosition(selectedTagPlane, normalized.positionMm)
        tagCoordinateFieldsExpanded = true
        clearScannedTagSelection()
        message = "AprilTag ${normalized.id} geladen voor bewerken."
    }

    fun requestRemoveSensor(sensor: Sensor) {
        confirmRequest = WorkflowConfirmRequest(
            title = "Sensor verwijderen?",
            body = "Sensor ${sensor.id} - ${sensor.name} wordt uit de sensorlijst verwijderd.",
            confirmText = "Verwijder"
        ) {
            removeSensor(sensor)
        }
    }

    private fun removeSensor(sensor: Sensor) {
        project = project.copy(
            sensors = project.sensors.filterNot { it.id == sensor.id }
                .mapIndexed { index, item -> item.copy(order = index + 1) }
        )
        saveProject()
        message = "Sensor ${sensor.id} verwijderd."
    }

    /** Verwijder de laatst geplaatste sensor (hoogste order) — handig wanneer een sensor
     *  net verkeerd is neergezet tijdens live plaatsen. */
    fun undoLastPlacedSensor() {
        val last = project.sensors.maxByOrNull { it.order }
        if (last == null) {
            message = "Geen sensor om te verwijderen."
            return
        }
        project = project.copy(
            sensors = project.sensors.filterNot { it.id == last.id }
                .sortedBy { it.order }
                .mapIndexed { index, item -> item.copy(order = index + 1) }
        )
        saveProject()
        message = "Laatst geplaatste sensor (${last.id}) verwijderd."
    }

    fun previousSensor() {
        currentSensorIndex = (currentSensorIndex - 1).coerceAtLeast(0)
        setCursorFromCurrentSensor()
    }

    fun nextSensor() {
        currentSensorIndex = (currentSensorIndex + 1).coerceAtMost((project.sensors.size - 1).coerceAtLeast(0))
        setCursorFromCurrentSensor()
    }

    fun setCursorFromCurrentSensor() {
        currentSensor?.let {
            setCursorFieldsFromBox(it.positionMm)
        }
    }

    fun confirmInstallation() {
        val sensor = currentSensor ?: return
        val measuredPosition = sensor.positionMm
        setCursorFieldsFromBox(measuredPosition)
        log = confirmSensorAtMeasuredPosition(
            log = log,
            sensor = sensor,
            measuredPosition = measuredPosition,
            photo = null,
            confirmedAt = repository.nowIso()
        )
        project = project.copy(
            sensors = project.sensors.map {
                if (it.id == sensor.id) it.copy(status = SensorStatus.Ok) else it
            }
        )
        repository.saveLog(log)
        saveProject()
        message = "Sensor ${sensor.id} bevestigd op vaste meet-XYZ ${operatorText(measuredPosition)}."
    }

    /** Verschuift de OPGESLAGEN positie van een tag (mm) — live AR-kalibratie: het hele model
     *  én alle sensoren schuiven mee, want de tagpositie definieert het projectframe.
     *  De markers-wijziging reset de pose-fusion automatisch (signature-check in het camerapaneel). */
    fun nudgeTagPosition(tagId: Int, dx: Int, dy: Int, dz: Int) {
        val marker = project.markers.firstOrNull { it.id == tagId && it.isAprilTagCalibrationMarker() }
        if (marker == null) {
            message = "Tag #$tagId staat niet in dit project."
            return
        }
        val next = MmPosition(
            x = marker.positionMm.x + dx,
            y = marker.positionMm.y + dy,
            z = marker.positionMm.z + dz
        )
        project = project.copy(
            markers = project.markers.map { if (it.id == tagId) it.copy(positionMm = next) else it }
        )
        saveProject()
        message = "Tag #$tagId → ${operatorText(next)}."
    }

    /** Tag-id waarvoor nog een modeloppervlak-snap moet draaien (gezet na on-the-fly opslaan;
     *  het camerascherm voert hem async uit zodra de meshes geladen zijn). */
    var pendingTagSnapId by mutableStateOf<Int?>(null)

    /**
     * Zet de OPGESLAGEN tagpositie op het werkelijke modeloppervlak: vanaf buiten de trafo wordt
     * langs de vlaknormaal door de STL-assembly "geschoten" op de in-plane positie van de tag;
     * het buitenste raakpunt wordt de nieuwe diepte-coördinaat. Effect in AR: het model schuift
     * naar de diepte van de fysieke tag — de tag lijkt óp het model te zitten in plaats van ervoor
     * of erin. De tag mag hierdoor buiten de 0..dims-box uitkomen (bv. op een uitstekend deksel
     * of een rib); dat is bewust, want daar zit de fysieke tag nu eenmaal.
     */
    suspend fun snapTagToModelSurface(tagId: Int, quiet: Boolean = false) {
        val marker = project.markers.firstOrNull { it.id == tagId && it.isAprilTagCalibrationMarker() }
        if (marker == null) {
            if (!quiet) message = "Tag #$tagId staat niet in dit project."
            return
        }
        ensureStlMeshesLoaded()
        val models = project.stlModels.filter { it.visible }
            .mapNotNull { m -> stlMeshes[m.fileName]?.takeIf { !it.isEmpty }?.let { m to it } }
        if (models.isEmpty()) {
            if (!quiet) message = "Geen zichtbaar STL-model om de tagdiepte op te bepalen."
            return
        }
        val plane = tagPlaneForMarker(marker)
        // Straal van ver buiten de box naar binnen, langs de vlaknormaal. Het EERSTE raakpunt is
        // het buitenste oppervlak — daar rust de geplakte tag op.
        val dims = project.dimensionsMm
        val far = 100_000f
        val axis: Int
        val origin = floatArrayOf(marker.positionMm.x.toFloat(), marker.positionMm.y.toFloat(), marker.positionMm.z.toFloat())
        val direction = floatArrayOf(0f, 0f, 0f)
        when (plane) {
            TagPlane.Front -> { axis = 1; origin[1] = -far; direction[1] = 1f }
            TagPlane.Back -> { axis = 1; origin[1] = dims.y + far; direction[1] = -1f }
            TagPlane.Left -> { axis = 0; origin[0] = -far; direction[0] = 1f }
            TagPlane.Right -> { axis = 0; origin[0] = dims.x + far; direction[0] = -1f }
            TagPlane.Top -> { axis = 2; origin[2] = dims.z + far; direction[2] = -1f }
        }
        // 5 stralen (midden + 4 kwartpunten van het tagvlak): een gat (boutgat e.d.) precies in het
        // midden mist dan niet, en de tag rust fysiek op het meest uitstekende punt → kleinste t.
        val half = marker.sizeMm / 4f
        val inPlane = (0..2).filter { it != axis }
        val offsets = listOf(
            floatArrayOf(0f, 0f), floatArrayOf(half, 0f), floatArrayOf(-half, 0f),
            floatArrayOf(0f, half), floatArrayOf(0f, -half)
        )
        val bestT = withContext(Dispatchers.Default) {
            var best = Float.MAX_VALUE
            for (offset in offsets) {
                val o = origin.copyOf()
                o[inPlane[0]] += offset[0]
                o[inPlane[1]] += offset[1]
                for ((model, mesh) in models) {
                    val t = com.example.arsens.data.modelSurfaceDistanceAlongRay(model, mesh, o, direction)
                    if (t != null && t < best) best = t
                }
            }
            best
        }
        if (bestT == Float.MAX_VALUE) {
            if (!quiet) message = "Geen modeloppervlak gevonden ter hoogte van tag #$tagId (${plane.shortLabel})."
            return
        }
        val surface = origin[axis] + direction[axis] * bestT
        val newValue = surface.roundToInt()
        val old = when (axis) {
            0 -> marker.positionMm.x
            1 -> marker.positionMm.y
            else -> marker.positionMm.z
        }
        if (newValue == old) {
            if (!quiet) message = "Tag #$tagId staat al op het modeloppervlak (${plane.shortLabel})."
            return
        }
        val next = when (axis) {
            0 -> marker.positionMm.copy(x = newValue)
            1 -> marker.positionMm.copy(y = newValue)
            else -> marker.positionMm.copy(z = newValue)
        }
        project = project.copy(
            markers = project.markers.map { if (it.id == tagId) it.copy(positionMm = next) else it }
        )
        saveProject()
        val axisName = when (axis) { 0 -> "X"; 1 -> "Y"; else -> "Z" }
        message = "Tag #$tagId op modeloppervlak gezet: $axisName $old → $newValue mm. " +
            "Het model staat nu op tag-diepte."
    }

    /** Vlak van een tag: eerst exact op de standaard-rotatie, anders op de vlakpositie. */
    private fun tagPlaneForMarker(marker: Marker): TagPlane =
        TagPlane.entries.firstOrNull { plane ->
            val rotation = tagRotationFor(plane)
            rotation.x == marker.rotationDeg.x &&
                rotation.y == marker.rotationDeg.y &&
                rotation.z == marker.rotationDeg.z
        } ?: tagPlaneForSurfacePosition(marker.positionMm)

    fun openCameraMenu(key: String) {
        cameraMenuRequest = key
    }

    fun consumeCameraMenuRequest() {
        cameraMenuRequest = null
    }

    fun exportReportCsv() {
        runCatching { repository.exportReportCsv(project, log) }
            .onSuccess { message = "CSV rapport geexporteerd: ${it.absolutePath} en Downloads/ARsens." }
            .onFailure { message = "CSV export mislukt: ${it.message ?: "onbekende fout"}" }
    }

    fun exportReportJson() {
        runCatching { repository.exportReportJson(log) }
            .onSuccess { message = "JSON rapport geexporteerd: ${it.absolutePath} en Downloads/ARsens." }
            .onFailure { message = "JSON export mislukt: ${it.message ?: "onbekende fout"}" }
    }

    fun exportReportXlsx() {
        runCatching { repository.exportReportXlsx(project, log) }
            .onSuccess { message = "Excel rapport geexporteerd: ${it.absolutePath} en Downloads/ARsens." }
            .onFailure { message = "Excel export mislukt: ${it.message ?: "onbekende fout"}" }
    }

    fun exportReportBoth() {
        runCatching {
            Triple(
                repository.exportReportCsv(project, log),
                repository.exportReportJson(log),
                repository.exportReportXlsx(project, log)
            )
        }.onSuccess { (csv, json, xlsx) ->
            message = "CSV, JSON en Excel geexporteerd: ${csv.absolutePath}, ${json.absolutePath}, ${xlsx.absolutePath} en Downloads/ARsens."
        }.onFailure {
            message = "Rapport export mislukt: ${it.message ?: "onbekende fout"}"
        }
    }

    fun resultFor(sensorId: String): InstallationResult? =
        log.results.firstOrNull { it.sensorId == sensorId }

    fun nearestTagInstruction(sensor: Sensor): String {
        val marker = knownAprilTags.minByOrNull { tag -> distanceMm(sensor.positionMm - tag.positionMm) }
            ?: return "Nog geen voorbereide tag-refentie beschikbaar."
        val distance = distanceMm(sensor.positionMm - marker.positionMm)
        return "Referentie: AprilTag ${marker.id} (${distance} mm)."
    }

    /** Dichtstbijzijnde bekende (actieve) tag bij een box-positie. Wordt gebruikt om een
     *  voorbereide sensor automatisch aan een referentietag te koppelen, zodat hij in AR bij
     *  die tag verschijnt — net als on-the-fly, maar dan vooraf bepaald. */
    fun nearestTagIdForBox(position: MmPosition): Int? =
        knownAprilTags.minByOrNull { tag -> distanceMm(position - tag.positionMm) }?.id

    fun operatorText(position: MmPosition?): String =
        position?.let { project.coordinateMapper().boxToOperator(it).toReadableMm() } ?: "-"

    private fun saveProject() {
        repository.saveProject(project)
        activeProjectUpdatedAtMillis = System.currentTimeMillis()
        refreshProjects()
    }

    fun navigateBack() {
        // Echte terug: keer terug naar het werkelijk vorige scherm uit de history-stack.
        val previous = navHistory.removeLastOrNull()
        if (previous != null) {
            go(previous)
            return
        }
        // Geen historie → val terug op het vaste schema (lineaire wizard / projectgrens).
        when (screen) {
            WorkflowScreen.ProjectPicker -> Unit
            WorkflowScreen.Start -> closeProject()
            WorkflowScreen.Tags -> go(WorkflowScreen.Start)
            WorkflowScreen.Stl -> go(WorkflowScreen.Start)
            WorkflowScreen.Sensors -> if (mode == WorkMode.Prepared) returnToPreparedSetup() else go(WorkflowScreen.Start)
            WorkflowScreen.Install -> if (mode == WorkMode.Prepared) returnToPreparedSetup() else go(WorkflowScreen.Sensors)
            WorkflowScreen.Report -> go(WorkflowScreen.Start)
            WorkflowScreen.ReportMap2D -> go(model2dBackTarget)
            WorkflowScreen.Settings -> go(WorkflowScreen.ProjectPicker)
        }
    }

    private fun setTagRotation(x: Int, y: Int, z: Int) {
        tagRotX = x.toString()
        tagRotY = y.toString()
        tagRotZ = z.toString()
    }

    private fun rememberScannedTag(id: Int) {
        lastScannedTagId = id
        lastScannedTagAtMillis = System.currentTimeMillis()
    }

    private fun clearScannedTagSelection() {
        lastScannedTagId = null
        lastScannedTagAtMillis = 0L
    }

    private fun dimensionsFromFields(): MmPosition? {
        val length = lengthMm.toIntOrNull()
        val width = widthMm.toIntOrNull()
        val height = heightMm.toIntOrNull()
        return if (length != null && width != null && height != null && length > 0 && width > 0 && height > 0) {
            MmPosition(length, width, height)
        } else {
            null
        }
    }

    private fun syncDimensionsFromProject() {
        lengthMm = project.dimensionsMm.x.toString()
        widthMm = project.dimensionsMm.y.toString()
        heightMm = project.dimensionsMm.z.toString()
    }

    /** Detectie het dichtst bij de plaatsings-cursor, in BEELDcoördinaten. De gekozen detectie
     *  gaat later naar estimateSurfaceAtPixel (verwacht beeldpixels), dus hier géén
     *  screenDetections gebruiken: die staan in view-px en horen niet bij imageWidth/Height. */
    private fun bestDetectionAtCursorForSetup(result: AprilTagFrameResult): com.example.arsens.ar.AprilTagDetection? {
        val detections = result.detections
        if (detections.isEmpty()) return null
        val width = result.imageWidth.takeIf { it > 0 } ?: return largestDetectionForSetup(result)
        val height = result.imageHeight.takeIf { it > 0 } ?: return largestDetectionForSetup(result)
        // Cursor (mogelijk versleept) van schermfractie → beeldpixel; zonder mapper het beeldmidden.
        val mapper = result.imageToViewMapper
        val cursorImagePoint = if (mapper != null && mapper.displayWidth > 0 && mapper.displayHeight > 0) {
            mapper.unmap(
                AprilTagCorner(
                    xPx = (arCursorScreenOffset.x * 0.5f + 0.5f) * mapper.displayWidth,
                    yPx = (arCursorScreenOffset.y * 0.5f + 0.5f) * mapper.displayHeight
                )
            )
        } else {
            null
        }
        val centerX = cursorImagePoint?.xPx ?: (width / 2f)
        val centerY = cursorImagePoint?.yPx ?: (height / 2f)
        val maxDistance = minOf(width, height) * 0.32f
        val closest = detections.minByOrNull { detection ->
            val dx = detection.centerPx.xPx - centerX
            val dy = detection.centerPx.yPx - centerY
            dx * dx + dy * dy
        } ?: return null
        val dx = closest.centerPx.xPx - centerX
        val dy = closest.centerPx.yPx - centerY
        return closest.takeIf { dx * dx + dy * dy <= maxDistance * maxDistance }
    }

    private fun largestDetectionForSetup(result: AprilTagFrameResult): com.example.arsens.ar.AprilTagDetection? =
        result.detections.maxByOrNull { detection ->
            polygonAreaPx(detection.cornersPx)
        }

    private fun tagRoleLabel(role: String): String =
        when (role) {
            "origin" -> "nulpunt/voorvlak"
            "front-x" -> "lengte-as"
            "left" -> "linker zijkant"
            "right" -> "rechter zijkant"
            "back" -> "achtervlak"
            "top" -> "bovenkant"
            else -> "de gekozen positie"
        }

    private fun resetSensorFormForNext() {
        sensorId = nextSensorId()
        sensorName = "sens"
        sensorX = "0"
        sensorY = "0"
        sensorZ = "0"
        sensorTolerance = "50"
        sensorInstruction = ""
        // Voorkom dat de referentietag van een vorige (on-the-fly) plaatsing lekt naar de
        // volgende: een voorbereide plaatsing bepaalt zijn tag op basis van nabijheid.
        sensorReferenceTagId = null
    }

    private fun nextSensorId(): String {
        val used = project.sensors.mapNotNull { it.id.toIntOrNull() }.toSet()
        return (1..32).firstOrNull { it !in used }?.toString()
            ?: ((project.sensors.maxOfOrNull { it.id.toIntOrNull() ?: 0 } ?: project.sensors.size) + 1).toString()
    }

    private fun nextAprilTagId(): Int =
        nextFreeReferenceTagIds(1).first()

    /** Eerstvolgende vrije referentietag-ID's: vanaf 0 ("tag 0 linksonder"), onder het
     *  sensor-tag-bereik (dat is gereserveerd voor de tags óp de sensoren). */
    private fun nextFreeReferenceTagIds(count: Int): List<Int> {
        val used = project.markers
            .filter { it.isAprilTagCalibrationMarker() }
            .map { it.id }
            .toSet()
        val free = (0 until sensorTagStartId).asSequence().filter { it !in used }.take(count).toList()
        if (free.size >= count) return free
        // Bereik vol (zou niet moeten gebeuren): tel door boven het hoogste gebruikte ID.
        val start = (used.maxOrNull() ?: -1) + 1
        return free + (start until start + (count - free.size)).toList()
    }

    private fun tagPlaneForMapView(viewName: String, position: MmPosition): TagPlane =
        when (viewName) {
            "Top" -> TagPlane.Top
            "Front" -> TagPlane.Front
            "Back" -> TagPlane.Back
            "Left" -> TagPlane.Left
            "Right" -> TagPlane.Right
            else -> tagPlaneForSurfacePosition(position)
        }

    private fun tagPlaneForSurfacePosition(position: MmPosition): TagPlane =
        when {
            position.z == project.dimensionsMm.z -> TagPlane.Top
            position.y == 0 -> TagPlane.Front
            position.y == project.dimensionsMm.y -> TagPlane.Back
            position.x == 0 -> TagPlane.Left
            position.x == project.dimensionsMm.x -> TagPlane.Right
            else -> TagPlane.Top
        }

    private fun nearestTagAnchorForPosition(plane: TagPlane, position: MmPosition): TagAnchor =
        TagAnchor.entries.minByOrNull { anchor ->
            val anchorPosition = tagPlacementFor(plane, anchor, project.dimensionsMm).positionMm
            distanceMm(anchorPosition - position)
        } ?: TagAnchor.Center

    private fun cursorPositionOrNull(): MmPosition? {
        val operatorPosition = operatorPositionOrNull(cursorX, cursorY, cursorZ) ?: return null
        return project.coordinateMapper().operatorToBox(operatorPosition)
    }

    private fun operatorPositionOrNull(xText: String, yText: String, zText: String): MmPosition? {
        val x = xText.toIntOrNull()
        val y = yText.toIntOrNull()
        val z = zText.toIntOrNull()
        return if (x != null && y != null && z != null) MmPosition(x, y, z) else null
    }

    private fun setTagFieldsFromBox(position: MmPosition) {
        val operatorPosition = project.coordinateMapper().boxToOperator(position)
        tagX = operatorPosition.x.toString()
        tagY = operatorPosition.y.toString()
        tagZ = operatorPosition.z.toString()
    }

    private fun setSensorFieldsFromBox(position: MmPosition) {
        val operatorPosition = project.coordinateMapper().boxToOperator(position)
        sensorX = operatorPosition.x.toString()
        sensorY = operatorPosition.y.toString()
        sensorZ = operatorPosition.z.toString()
    }

    private fun setCursorFieldsFromBox(position: MmPosition) {
        val operatorPosition = project.coordinateMapper().boxToOperator(position)
        cursorX = operatorPosition.x.toString()
        cursorY = operatorPosition.y.toString()
        cursorZ = operatorPosition.z.toString()
    }

    private fun logCursorSource(now: Long, source: String, hit: PlaneHit?) {
        val inside = hit?.insideTransformerBox ?: false
        val key = "$source|$inside"
        if (key == lastCursorLogKey && now - lastCursorLogMillis < CURSOR_LOG_INTERVAL_MILLIS) return
        lastCursorLogKey = key
        lastCursorLogMillis = now
        val positionText = hit?.position?.toReadableMm() ?: "-"
        Log.i("ARsensCursor", "source=$source pos=$positionText inside=$inside")
    }

    /** Dempt per-tag en gefuseerde poses tegen trillen; zie [TagPoseSmoother] voor de tunables. */
    private val tagPoseSmoother = TagPoseSmoother()

private fun smoothArPoses(result: AprilTagFrameResult, now: Long): AprilTagFrameResult {
    if (result.posePerTag.isEmpty() && result.transformerPose == null) return result

    val smoothedPerTag = result.posePerTag.mapValues { (id, pose) ->
        tagPoseSmoother.smooth(id, pose, now)
    }

    // De gefuseerde hoofd-pose alleen dempen zolang ARCore GÉÉN displayProjection levert: dan is
    // transformerPose een (springerige) solvePnP-pose die demping nodig heeft. Zodra ARCore trackt
    // is transformerPose juist de al gladde ARCore-camera (zelfde basis als displayProjection) — die
    // door de solvePnP-smoother halen geeft alleen lag/inhaal-jitter bij beweging (het 3D-model
    // "zwemt" terwijl de sensoren via de rauwe displayProjection strak meelopen).
    val arCoreLeading = result.displayProjection != null
    val smoothedMain = result.transformerPose?.let { pose ->
        if (arCoreLeading) pose else tagPoseSmoother.smooth(TagPoseSmoother.FUSED_KEY, pose, now)
    }

    val smoothedImage = when {
        result.imageProjectionPose == null -> null
        result.imageProjectionPose === result.transformerPose -> smoothedMain
        else -> tagPoseSmoother.smooth(TagPoseSmoother.IMAGE_KEY, result.imageProjectionPose, now)
    }

    tagPoseSmoother.prune(now)

    return result.copy(
        posePerTag = smoothedPerTag,
        transformerPose = smoothedMain,
        imageProjectionPose = smoothedImage
    )
}

    private fun resetArPoseState() {
        tagPoseSmoother.reset()
        aprilTagResult = AprilTagFrameResult()
        heldAprilTagResult = AprilTagFrameResult()
        heldAprilTagAtMillis = 0L
        lastObjectCursorAtMillis = 0L
        arCursorPosition = null
        arCursorInsideTransformer = true
        arCursorUsesDepth = false
        arCursorSource = "none"
        lastPlacementReference = null
        lastCursorLogKey = ""
        lastCursorLogMillis = 0L
    }
}

/** Laatst gebruikte verse, bekende referentietag: het marker-vlak waarop de plaatsings-cursor
 *  blijft zodra de tag niet meer vers in beeld is maar ARCore nog trackt. */
internal data class PlacementReference(
    val tagId: Int,
    val marker: Marker
)

internal fun MmPosition.insideBox(dimensions: MmPosition): Boolean =
    x in 0..dimensions.x && y in 0..dimensions.y && z in 0..dimensions.z

internal fun MmPosition.snapToTransformerSurface(dimensions: MmPosition): PlaneHit? {
    val clampedX = x.coerceIn(0, dimensions.x)
    val clampedY = y.coerceIn(0, dimensions.y)
    val clampedZ = z.coerceIn(0, dimensions.z)
    val surfaceCandidates = listOf(
        SurfaceSnapCandidate(kotlin.math.abs(x), axis = 0, MmPosition(0, clampedY, clampedZ)),
        SurfaceSnapCandidate(kotlin.math.abs(x - dimensions.x), axis = 0, MmPosition(dimensions.x, clampedY, clampedZ)),
        SurfaceSnapCandidate(kotlin.math.abs(y), axis = 1, MmPosition(clampedX, 0, clampedZ)),
        SurfaceSnapCandidate(kotlin.math.abs(y - dimensions.y), axis = 1, MmPosition(clampedX, dimensions.y, clampedZ)),
        SurfaceSnapCandidate(kotlin.math.abs(z), axis = 2, MmPosition(clampedX, clampedY, 0)),
        SurfaceSnapCandidate(kotlin.math.abs(z - dimensions.z), axis = 2, MmPosition(clampedX, clampedY, dimensions.z))
    )
    val nearest = surfaceCandidates.minByOrNull { it.distanceMm } ?: return null
    val outsideDistance = maxOf(
        -x,
        x - dimensions.x,
        -y,
        y - dimensions.y,
        -z,
        z - dimensions.z,
        0
    )
    if (nearest.distanceMm > DEPTH_SURFACE_SNAP_TOLERANCE_MM || outsideDistance > DEPTH_BOX_OUTSIDE_TOLERANCE_MM) {
        return null
    }
    val insideOnSurfaceAxes = when (nearest.axis) {
        0 -> y in 0..dimensions.y && z in 0..dimensions.z
        1 -> x in 0..dimensions.x && z in 0..dimensions.z
        else -> x in 0..dimensions.x && y in 0..dimensions.y
    }
    return PlaneHit(
        position = nearest.position,
        insideTransformerBox = insideOnSurfaceAxes
    )
}

internal fun markerSurfaceLabel(marker: Marker, dimensions: MmPosition): String =
    when {
        marker.positionMm.z == dimensions.z -> "Boven"
        marker.positionMm.y == 0 -> "Voor"
        marker.positionMm.y == dimensions.y -> "Achter"
        marker.positionMm.x == 0 -> "Links"
        marker.positionMm.x == dimensions.x -> "Rechts"
        else -> "Binnen"
    }

internal data class SurfaceSnapCandidate(
    val distanceMm: Int,
    val axis: Int,
    val position: MmPosition
)

internal fun polygonAreaPx(points: List<AprilTagCorner>): Double {
    if (points.size < 3) return 0.0
    return kotlin.math.abs(
        points.indices.sumOf { index ->
            val start = points[index]
            val end = points[(index + 1) % points.size]
            (start.xPx * end.yPx - end.xPx * start.yPx).toDouble()
        }
    ) / 2.0
}

internal fun AprilTagFrameResult.hasAnyDetection(): Boolean =
    detections.isNotEmpty() || screenDetections.isNotEmpty()

internal fun AprilTagFrameResult.hasOverlayPose(): Boolean =
    transformerPose != null || imageProjectionPose != null || displayProjection != null

internal fun AprilTagFrameResult.hasFreshDetection(): Boolean =
    detectionsFresh && hasAnyDetection()

private const val TAG_SCAN_SAVE_GRACE_MILLIS = 30_000L
private const val OBJECT_CURSOR_HOLD_MILLIS = 1_500L
private const val FRESH_PER_TAG_POSE_MILLIS = 300L
private const val CURSOR_LOG_INTERVAL_MILLIS = 1_500L
private const val DEPTH_SURFACE_SNAP_TOLERANCE_MM = 300
private const val DEPTH_BOX_OUTSIDE_TOLERANCE_MM = 300
