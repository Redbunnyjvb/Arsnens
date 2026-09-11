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
import com.example.arsens.ar.ArPerfStats
import com.example.arsens.ar.ArTrackingStatus
import com.example.arsens.ar.PlaneHit
import com.example.arsens.ar.TagAnchor
import com.example.arsens.ar.TagMeasurementAnchor
import com.example.arsens.ar.TagPlane
import com.example.arsens.ar.estimateCursorOnReferenceSurface
import com.example.arsens.ar.estimateSurfaceAtPixel
import com.example.arsens.ar.estimateSurfaceAtPixelOnPlane
import com.example.arsens.ar.markerCornersInProjectFrame
import com.example.arsens.ar.projectPointToScreen
import com.example.arsens.ar.ProjectPointMm
import com.example.arsens.ar.projectPointToImage
import com.example.arsens.ar.projectPositionToScreen
import com.example.arsens.ar.ScreenPointPx
import com.example.arsens.ar.TagDictionaryOption
import com.example.arsens.ar.TagPoseMode
import com.example.arsens.ar.TagPoseSmoother
import com.example.arsens.ar.PlacementQuality
import com.example.arsens.ar.PlacementQualityTuning
import com.example.arsens.ar.computePlacementQuality
import com.example.arsens.ar.toAudit
import com.example.arsens.ar.RayMm
import com.example.arsens.ar.intersectTagPlaneExact
import com.example.arsens.ar.imagePoseRayForPixel
import com.example.arsens.ar.tagPlaneOutwardNormal
import com.example.arsens.ar.Transform3D
import com.example.arsens.ar.cameraRayInTransformer
import com.example.arsens.ar.reprojectPlacementRay
import com.example.arsens.ar.TagAxisReference
import com.example.arsens.ar.applyTagOutwardOffset
import com.example.arsens.ar.tagEdgeOffsetToCenter
import com.example.arsens.ar.tagGridDisplayUvToCanonicalUv
import com.example.arsens.ar.tagMeasuredPointToCenter
import com.example.arsens.ar.tagPlacementFor
import com.example.arsens.ar.tagPositionFor
import com.example.arsens.ar.tagRotationFor
import com.example.arsens.data.AppSettings
import com.example.arsens.data.CoordinateFrameSettings
import com.example.arsens.data.FloatVector
import com.example.arsens.data.InstallationResult
import com.example.arsens.data.LocalProjectRepository
import com.example.arsens.data.Marker
import com.example.arsens.data.PlacementOrigin
import com.example.arsens.data.MmPosition
import com.example.arsens.data.OriginCorner
import com.example.arsens.data.Project
import com.example.arsens.data.ProjectSummary
import com.example.arsens.data.Sensor
import com.example.arsens.ar.calibration.*
import com.example.arsens.data.ReferenceGeometryMode
import com.example.arsens.data.WallDimensionSource
import com.example.arsens.data.WallTagAssignment
import com.example.arsens.data.CalibrationWall
import com.example.arsens.data.WallVerticalDatum
import com.example.arsens.data.WallCalibrationData
import com.example.arsens.data.wallGeometrySignature
import com.example.arsens.data.hasWallCalibration
import com.example.arsens.data.needsWallCalibration
import com.example.arsens.data.displayName
import com.example.arsens.data.sensorTagConflict
import com.example.arsens.data.resetSensorInstallation
import com.example.arsens.data.updateSensorPlan
import com.example.arsens.data.SensorStatus
import com.example.arsens.data.SensorDriftCorrection
import com.example.arsens.data.SensorPlacementAudit
import com.example.arsens.data.StlMesh
import com.example.arsens.data.StlModel
import com.example.arsens.data.StlPartRole
import com.example.arsens.data.StlParser
import com.example.arsens.data.asAprilTagCalibrationMarker
import com.example.arsens.data.confirmSensorAtMeasuredPosition
import com.example.arsens.data.deriveSensorIdForScannedTag
import com.example.arsens.data.sensorForSensorTag
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
    OnTheFly("Camera")
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

    var showTagDistances by mutableStateOf(appSettings.showTagDistances)
        private set

    fun setTagDistancesVisible(visible: Boolean) {
        showTagDistances = visible
        appSettings.showTagDistances = visible
    }

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

    /** Alle strategieën controleren zichtbare referenties onderling vóór ankerupdates. */
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
    tagSize = size.toString()
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
            tagSize = size.toString()
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

    /** Sensor-tag automatisch koppelen bij het plaatsen van een sensor (on-the-fly). */
    var autoLinkSensorTagOnPlace by mutableStateOf(appSettings.autoLinkSensorTagOnPlace)
        private set

    fun setAutoLinkSensorTag(enabled: Boolean) {
        if (autoLinkSensorTagOnPlace == enabled) return
        autoLinkSensorTagOnPlace = enabled
        appSettings.autoLinkSensorTagOnPlace = enabled
        message = if (enabled) {
            "Sensor-tag koppelen aan: 'Plaats sensor' pakt automatisch de sensor-tag onder de cursor."
        } else {
            "Sensor-tag koppelen uit: sensoren worden zonder tag-koppeling geplaatst."
        }
    }

    /** Automatische straal-replay-driftcorrectie aan/uit (app-breed, instelling). */
    var autoCorrectSensorDrift by mutableStateOf(appSettings.autoCorrectSensorDrift)
        private set

    fun setAutoCorrectDrift(enabled: Boolean) {
        if (autoCorrectSensorDrift == enabled) return
        autoCorrectSensorDrift = enabled
        appSettings.autoCorrectSensorDrift = enabled
        if (!enabled) placementRays.clear()
        message = if (enabled) {
            "Automatische driftcorrectie aan: een sensor wordt bijgewerkt zodra zijn tag weer stabiel in beeld komt."
        } else {
            "Automatische driftcorrectie uit."
        }
    }
    var mode by mutableStateOf(WorkMode.Prepared)
    var cameraPlacementTarget by mutableStateOf(CameraPlacementTarget.Sensor)
    var project by mutableStateOf(repository.loadInitialProject())
    var log by mutableStateOf(repository.loadLog(project.projectName))
    var projects by mutableStateOf(repository.listProjects())
    var activeProjectUpdatedAtMillis by mutableStateOf(repository.activeProjectSummary()?.updatedAtMillis)
    var newProjectName by mutableStateOf("")
    var newReferenceGeometryMode by mutableStateOf(ReferenceGeometryMode.KnownTagPositions)
    var newWallDimensionSource by mutableStateOf(WallDimensionSource.Entered)
    var wallScanActive by mutableStateOf(false)
    var wallScanId by mutableStateOf(0L)
    var wallScanSource by mutableStateOf(WallDimensionSource.Entered)
    var wallScanSize by mutableStateOf("100")
    var wallScanWall by mutableStateOf(CalibrationWall.Front)
    var wallAssignments by mutableStateOf<List<WallTagAssignment>>(emptyList())
    var wallTopOffset by mutableStateOf("0")
    var wallSideHeight by mutableStateOf("")
    var wallSelectedTagId by mutableStateOf<Int?>(null)
    var wallTopOverlapVerified by mutableStateOf(false)
    var wallScanFrame by mutableStateOf<WallScanFrame?>(null)
    var wallSeenTags by mutableStateOf<List<WallTagObservation>>(emptyList())
    var wallScanCounts by mutableStateOf<Map<Int, Int>>(emptyMap())
    var wallReadyTagIds by mutableStateOf<List<Int>>(emptyList())
    var wallScanSolution by mutableStateOf<WallCalibrationSolution?>(null)
    var wallScanFootprint by mutableStateOf<WallCalibrationSolution?>(null)
    var wallLinkedPreview by mutableStateOf<WallCalibrationSolution?>(null)
    var wallTopStage by mutableStateOf(false)
    private var wallFootprintSamples: List<WallTagEstimate> = emptyList()
    private var wallFootprintDatum: WallVerticalDatum? = null
    var wallScanMessage by mutableStateOf<String?>(null)
    private val wallSession = WallCalibrationSession()
    private var wallSolvedFrameId: Long? = null
    private var wallSolvedDatum: WallVerticalDatum? = null
    private var wallSolvedAssignments: List<WallTagAssignment> = emptyList()
    private var wallSolvedSource = WallDimensionSource.Entered
    private var wallEvidenceSequence = -1L
    private var wallLastFrameMillis = 0L
    private var wallRequestBaseRevision = 0
    val wallScanRequest: WallScanRequest? get() = if (wallScanActive) WallScanRequest(wallScanId,
        wallScanSize.toIntOrNull()?.takeIf { it in 10..2000 } ?: 0,
        wallAssignments.associate { it.tagId to it.sizeMm }, sensorTagStartId) else null

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
    /** Herkomst van de huidige tag-coördinaten voor de ARSensFrameAudit-log: "grid" (rastercel),
     *  "manual" (handmatig getypt) of "raycast-selected-plane" (afgeleid op het gekozen vlak). */
    private var lastTagPlacementSource: String = "grid"
    /** Welk punt van de tag de operator in de X/Y/Z-velden invoert. Alleen relevant bij HANDMATIGE
     *  invoer: grid- en AR-afgeleide posities zijn al een center. [Marker.positionMm] blijft altijd het
     *  center (de conversie gebeurt in [saveMeasuredTag] via [tagMeasuredPointToCenter]). Default Center
     *  = onveranderd gedrag. */
    var tagMeasurementAnchor by mutableStateOf(TagMeasurementAnchor.Center)
        private set
    /** Tagsheet-invoermodus: false = het raster stuurt de coördinaten, true = handmatig getypte
     *  X/Y/Z blijven staan (een vlakwissel zet dan alleen de rotatie, niet de positie). */
    var tagPlacementManual by mutableStateOf(false)
        private set
    /** Handmatige offset-assistent: false = anker-modus (gemeten X/Y/Z + welk tag-punt), true =
     *  box-rand-offset-modus (per as vanaf min/max-rand of handmatig). Alleen in handmatige modus. */
    var tagEdgeOffsetMode by mutableStateOf(false)
        private set
    /** Box-rand-offset: referentie + waarde (mm) per vrije vlak-as (U = eerste, V = tweede). De waarde
     *  is in mm op het CENTER van de tag. Zie [tagEdgeOffsetToCenter]. */
    var tagEdgeURef by mutableStateOf(TagAxisReference.FromMin)
        private set
    var tagEdgeU by mutableStateOf("0")
    var tagEdgeVRef by mutableStateOf(TagAxisReference.FromMin)
        private set
    var tagEdgeV by mutableStateOf("0")
    /** Of in "Meet vanaf rand" naar de PAPIER-hoek/rand is gemeten (i.p.v. de tag zelf) — dan telt de
     *  papier/witruimte-marge mee. False = gemeten tot de tag (midden of hoek), marge genegeerd. */
    var tagMeasuredToPaper by mutableStateOf(false)
        private set
    /** Optionele papier/tag-marge (mm) langs de rand(en) waar het anker op ligt — meten tot de
     *  papier-rand i.p.v. de tag-rand. Alleen relevant als [tagMeasuredToPaper]. */
    var tagPaperMarginU by mutableStateOf("0")
    var tagPaperMarginV by mutableStateOf("0")
    /** Optionele buitenwaartse offset (mm) langs de vlak-normaal — tag op rib/pijp/dik papier buiten
     *  het nominale vlak. Mag negatief (naar binnen). Niet geklemd. Zie [applyTagOutwardOffset]. */
    var tagOutwardOffset by mutableStateOf("0")
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
    var sensorTolerance by mutableStateOf("25")
    var planSensorAtCursor by mutableStateOf(false)
    var arCalibrationRevision by mutableStateOf(0)
        private set
    private var cursorPlacementRay: RayMm? = null
    val sensorPlacementBlockReason: String?
        get() = when {
            wallScanActive -> "Rond eerst de wandkalibratie af."
            project.needsWallCalibration -> "Stel eerst de tankreferentie in via Wanden scannen."
            else -> com.example.arsens.ar.sensorPlacementBlockReason(aprilTagResult, placementQuality)
        }
    val sensorPlacementReady: Boolean
        get() = sensorPlacementBlockReason == null
    var sensorInstruction by mutableStateOf("")
    /** Formulierveld: AprilTag-ID die fysiek óp de sensor geplakt is (leeg = geen). */
    var sensorTagId by mutableStateOf("")
    /** ID van de tag die gebruikt werd bij plaatsing van de huidige sensor. */
    var sensorReferenceTagId: Int? = null
    /** Laatst gebruikte verse, bekende referentietag (id + marker). Wordt het plaatsingsvlak zodra
     *  de tag niet meer vers in beeld is maar ARCore nog trackt: cursor/sensor blijven dan op dít
     *  marker-vlak i.p.v. op een willekeurige trafo-box-hit. */
    var lastPlacementReference: PlacementReference? = null

    var currentSensorIndex by mutableIntStateOf(0)

    /** Gemeten positie afgeleid uit een gescande sensor-tag, klaargezet voor handmatige bevestiging
     *  in de Install-flow. Null = geen gescande meting; [confirmInstallation] valt dan terug op de
     *  actuele AR-cursor, mits plaatsingskwaliteit en tracking dit toelaten. */
    var scannedMeasuredPosition: MmPosition? by mutableStateOf<MmPosition?>(null)
        private set
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
    /** RMS-jitter (mm) van de laatste cursorposities tijdens een lock; null = onvoldoende samples. */
    var cursorJitterMm by mutableStateOf<Float?>(null)
        private set
    /** Live plaatsingskwaliteit (±mm + grade) voor de huidige pose; voedt de status-chip en wordt
     *  als onveranderlijke snapshot bij een sensor opgeslagen. Blokkeert het opslaan NIET. */
    var placementQuality by mutableStateOf<PlacementQuality?>(null)
        private set
    private var lastCursorLogKey: String = ""
    private var lastCursorLogMillis: Long = 0L
    private var lastFrameAuditOverlayKey: String = ""
    private var lastFrameAuditOverlayMillis: Long = 0L
    private val cursorJitterSamples = ArrayDeque<MmPosition>()
    private var lockTagSignature: List<Int> = emptyList()
    private var lockSinceMillis: Long = 0L
    private var lockSamples: Int = 0
    private var lastLockStable: Boolean = false
    private var lastLockLogMillis: Long = 0L
    /** Sessie-gebonden camerastralen van het plaatsmoment per sensor-id, voor straal-replay-
     *  driftcorrectie. NIET gepersisteerd: alleen geldig binnen de huidige ARCore-sessie. */
    private val placementRays = mutableMapOf<String, StoredPlacementRay>()
    /** Voortgang (0..1) van de settle-timer vóór een straal-replay-correctie vuurt; null = geen
     *  correctie-procedure bezig. Voedt het voortgangsringetje om het camera-kwaliteitspuntje. */
    var correctionSettleProgress by mutableStateOf<Float?>(null)
        private set
    private var correctionSettleSinceMillis: Long = 0L
    private var correctionSettleTags: List<Int> = emptyList()
    var showAxisOverlay by mutableStateOf(false)
    var showMiniAxisOverlay by mutableStateOf(true)
    /** Toon de trafo-box (12 ribben van [Project.dimensionsMm]) als wireframe over de camera,
     *  verankerd via de tag-pose. */
    var showBoxEdgesOverlay by mutableStateOf(false)
    /** Toon per AprilTag een label met de OPGESLAGEN box-positie ([Marker.positionMm]) en rotatie
     *  ([Marker.rotationDeg]). Bediend vanuit de Lagen-sheet én Instellingen → Debug. */
    var showTagPoseLabels by mutableStateOf(false)
    /** Debug-HUD linksboven op de camera: live FPS + ARCore-Hz. Bediend vanuit Instellingen → Debug. */
    var showDebugHud by mutableStateOf(false)
    /** Laatst gemeten perf-telemetrie voor de debug-HUD (ARCore-cadans + overlay-cadans). Wordt
     *  alleen bijgewerkt zolang [showDebugHud] aan staat. */
    var arPerfStats by mutableStateOf(ArPerfStats())
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

    // ARCore already bridges missing tag detections. Reusing an old camera projection when
    // tracking itself is lost would make the model stick to the screen for three seconds.
    val overlayAprilTagResult: AprilTagFrameResult
        get() = aprilTagResult

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

    /** Importeert een PC-export (.zip-pakket of los project.json) als NIEUW project en opent het.
     *  Zwaar werk (uitpakken + modellen wegschrijven) op de IO-dispatcher; daarna dezelfde nazorg als
     *  [openProject]. Faalt zacht met een melding; een mislukte import laat het actieve project ongemoeid. */
    fun importProjectFromUri(uri: Uri, scope: CoroutineScope) {
        scope.launch {
            message = "Project importeren…"
            val result = runCatching {
                withContext(Dispatchers.IO) { repository.importProjectPackage(uri) }
            }.getOrElse { error ->
                Log.w("ARSensImport", "Project-import mislukt", error)
                message = "Importeren mislukt: ${error.message ?: "onbekende fout"}"
                return@launch
            }
            project = result.project
            syncDimensionsFromProject()
            log = repository.loadLog(project.projectName)
            // Verse meshes voor het nieuwe project: oude mesh-/AR-cache (op bestandsnaam) ongeldig maken.
            stlMeshes = emptyMap()
            stlArParts = null
            stlArPartsKey = null
            stlArDetailKey = null
            resetArPoseState()
            refreshProjects()
            activeProjectUpdatedAtMillis = repository.activeProjectSummary()?.updatedAtMillis
                ?: System.currentTimeMillis()
            navHistory.clear()
            screen = WorkflowScreen.Start
            val summary = "Project geïmporteerd: ${project.projectName} — " +
                "${project.sensors.size} sensoren, ${project.markers.size} tags, ${result.modelsWritten} 3D-delen"
            message = if (result.missingModels.isEmpty()) {
                summary
            } else {
                "$summary. Let op: ${result.missingModels.size} 3D-deel(en) ontbreken in het bestand " +
                    "(${result.missingModels.joinToString()})."
            }
        }
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
        val dimensions = if (newReferenceGeometryMode == ReferenceGeometryMode.ScannedWalls &&
            newWallDimensionSource == WallDimensionSource.Scanned) MmPosition(0,0,0) else dimensionsFromFields()
        if (dimensions == null) {
            message = "Vul geldige trafo-afmetingen in hele millimeters in."
            return
        }
        project = repository.createProject(name, dimensions, newReferenceGeometryMode, newWallDimensionSource)
        log = repository.loadLog(project.projectName)
        newProjectName = ""
        newReferenceGeometryMode = ReferenceGeometryMode.KnownTagPositions
        newWallDimensionSource = WallDimensionSource.Entered
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
            planSensorAtCursor = false
            resetSensorFormForNext()
            cameraSensors.firstOrNull { it.status == SensorStatus.Pending }?.let(::selectCameraSensor)
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
        if (sensorTolerance.isBlank()) sensorTolerance = "25"
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
            if (sensorTolerance.isBlank()) sensorTolerance = "25"
        }
        message = null
    }

    fun go(target: WorkflowScreen) {
        if (target in listOf(WorkflowScreen.Stl, WorkflowScreen.ReportMap2D, WorkflowScreen.Install) &&
            listOf(project.dimensionsMm.x, project.dimensionsMm.y, project.dimensionsMm.z).any { it <= 0 }) {
            screen = WorkflowScreen.Tags
            message = "Bepaal eerst de tankafmetingen via Wanden scannen."
            return
        }
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
        if (project.dimensionsMm.x <= 0 || project.dimensionsMm.y <= 0 || project.dimensionsMm.z <= 0) {
            message = "Bepaal eerst de tankafmetingen via Wanden scannen."
            screen = WorkflowScreen.Tags
            return
        }
        model2dBackTarget = backTarget
        model2dPurpose = purpose
        selectedMapTarget = null
        if (pushHistory) navigateTo(WorkflowScreen.ReportMap2D) else go(WorkflowScreen.ReportMap2D)
    }

    fun updateAprilTagResult(rawResult: AprilTagFrameResult) {
        if (rawResult.calibrationRevision != arCalibrationRevision) return
        if (rawResult.trackingFrameId < aprilTagResult.trackingFrameId) return
        if (rawResult.trackingFrameId != aprilTagResult.trackingFrameId) {
            // Saved rays and pending tag measurements belong to one tracking frame only.
            placementRays.clear()
            scannedMeasuredPosition = null
            cursorPlacementRay = null
            arCursorUsesDepth = false
            lastObjectCursorAtMillis = 0L
            lastPlacementReference = null
            lockTagSignature = emptyList()
            lockSinceMillis = 0L
            lockSamples = 0
            lastLockDetectionSequence = 0L
            resetCorrectionSettle()
            cursorJitterSamples.clear()
        }
        val now = System.currentTimeMillis()
        // All overlays and placements share the anchor filtered in ARCore world space.
        val result = rawResult
        aprilTagResult = result
        logFrameAuditOverlay(now, result)
        if (tagScanArmed && result.detections.isNotEmpty()) {
            captureVisibleTagForSetup(result)
        }
        // One world anchor for model, cursor, stored point and replay. The operator explicitly
        // chooses the tank face; visible tags establish the frame, not the placement surface.
        result.poseMarkerIds.firstOrNull()?.let { id ->
            knownAprilTags.firstOrNull { it.id == id }?.let { marker ->
                lastPlacementReference = PlacementReference(id, marker)
            }
        }
        cursorPlacementRay = if (result.displayProjection != null) cameraRayInTransformer(
            result, arCursorScreenOffset.x.toDouble(), -arCursorScreenOffset.y.toDouble()
        ) else null
        val surfaceHit = cursorPlacementRay?.let {
            intersectTagPlaneExact(it, selectedTagPlane, project.dimensionsMm)
        }?.let { PlaneHit(it, true) }
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
        updatePlacementQuality(now, hit, cursorSource)
    }

    /** Werkt de jitter-buffer, stabiele-lock-bepaling en live [placementQuality] bij, en markeert
     *  reeds (stabiel) geplaatste sensoren als "opnieuw valideren" bij een significante herankering.
     *  Blokkeert nooit het opslaan — puur informatief + audittrail. */
    private var lastLockDetectionSequence = 0L

    private fun updatePlacementQuality(now: Long, cursorHit: PlaneHit?, cursorSource: String) {
        val result = aprilTagResult
        // Verse, hoge-kwaliteit tag-pose? Dat is de basis voor een stabiele lock.
        val freshGoodPose = result.detectionAgeMillis <= PlacementQualityTuning.LOCK_FRESH_AGE_MILLIS &&
            result.trackingQualityPercent >= PlacementQualityTuning.LOCK_MIN_QUALITY_PERCENT &&
            result.transformerPose != null &&
            result.trackingStatus == ArTrackingStatus.TagCalibration && result.anchorSettled && !result.referenceConflict
        val signature = result.poseMarkerIds.sorted()
        if (freshGoodPose && signature.isNotEmpty()) {
            if (signature != lockTagSignature) {
                // Andere (set) tags of opnieuw vergrendeld → dwell/samples/jitter opnieuw opbouwen.
                lockTagSignature = signature
                lockSinceMillis = now
                lockSamples = 1
                cursorJitterSamples.clear()
            } else if (result.detectionSequence != lastLockDetectionSequence) {
                lockSamples += 1
            }
            lastLockDetectionSequence = result.detectionSequence
            val hitPosition = cursorHit?.position
                ?.takeIf { cursorSource == "surface" || cursorSource == "depth" }
            if (hitPosition != null) {
                cursorJitterSamples.addLast(hitPosition)
                while (cursorJitterSamples.size > CURSOR_JITTER_WINDOW) cursorJitterSamples.removeFirst()
            }
        } else {
            lockTagSignature = emptyList()
            lockSinceMillis = 0L
            lockSamples = 0
            cursorJitterSamples.clear()
        }
        val jitter = rmsJitterMm(cursorJitterSamples)
        cursorJitterMm = jitter

        val motionMm = result.motionDuringDetectionMm ?: 0f
        val motionDeg = result.motionDuringDetectionDeg ?: 0f
        val dwellOk = lockSinceMillis != 0L &&
            now - lockSinceMillis >= PlacementQualityTuning.LOCK_MIN_DWELL_MILLIS
        val jitterOk = (jitter ?: Float.MAX_VALUE) <= PlacementQualityTuning.LOCK_MAX_JITTER_MM
        val motionOk = motionMm <= PlacementQualityTuning.LOCK_MAX_MOTION_MM &&
            motionDeg <= PlacementQualityTuning.LOCK_MAX_MOTION_DEG
        val isStableLock = freshGoodPose &&
            lockSamples >= PlacementQualityTuning.LOCK_MIN_SAMPLES &&
            dwellOk && jitterOk && motionOk

        val referenceTagId = result.poseMarkerIds.firstOrNull() ?: lastPlacementReference?.tagId
        placementQuality = computePlacementQuality(
            result = result,
            jitterMm = jitter,
            isStableLock = isStableLock,
            referenceTagId = referenceTagId
        )
        placementQuality?.let { logCalibrationLock(now, it) }
        maybeAutoCorrectSensors(result)
    }

    /** Bewaart (in-memory, sessie-gebonden) de camerastraal van het plaatsmoment, zodat de positie
     *  later via straal-replay gecorrigeerd kan worden. Vereist een ARCore-anker + displayProjection;
     *  anders niets te corrigeren en wordt geen straal bewaard. */
    private fun storePlacementRayFor(sensorId: String, referenceTagId: Int?, ray: RayMm?, plane: TagPlane) {
        val anchor = aprilTagResult.arFromTransformer
        if (anchor == null || ray == null) {
            placementRays.remove(sensorId)
            return
        }
        placementRays[sensorId] = StoredPlacementRay(
            rayTransformer = ray,
            anchorAtPlacement = anchor,
            referenceTagId = referenceTagId,
            placedAtMillis = System.currentTimeMillis(),
            plane = plane
        )
    }

    /** Straal-replay-driftcorrectie (alleen als de instelling aan staat): zodra de referentietag van
     *  een eerder geplaatste sensor weer VERS en betrouwbaar in beeld is, herprojecteert dit de
     *  bewaarde camerastraal op het gecorrigeerde frame en werkt [Sensor.positionMm] bij. Eén keer
     *  per sensor (de straal wordt daarna gewist). Werkt alleen binnen dezelfde ARCore-sessie. */
    private fun maybeAutoCorrectSensors(result: AprilTagFrameResult) {
        if (!autoCorrectSensorDrift || placementRays.isEmpty()) { resetCorrectionSettle(); return }
        val anchorNow = result.arFromTransformer ?: run { resetCorrectionSettle(); return }
        if (!result.anchorSettled || result.referenceConflict || result.trackingStatus != ArTrackingStatus.TagCalibration ||
            result.detectionAgeMillis > PlacementQualityTuning.LOCK_FRESH_AGE_MILLIS
        ) {
            resetCorrectionSettle(); return
        }
        val visibleTags = result.poseMarkerIds.toSet()
        if (visibleTags.isEmpty()) { resetCorrectionSettle(); return }
        // Openstaande sensoren waarvan de referentietag nu in beeld is.
        val pending = project.sensors.filter { sensor ->
            if (sensor.origin == PlacementOrigin.Prepared) return@filter false
            val stored = placementRays[sensor.id] ?: return@filter false
            val refTag = stored.referenceTagId ?: sensor.referenceTagId
            refTag != null && refTag in visibleTags
        }
        if (pending.isEmpty()) { resetCorrectionSettle(); return }
        // Rustig anker vereist: bij te veel beweging tijdens detectie wacht de timer (reset naar 0).
        val motionMm = result.motionDuringDetectionMm ?: 0f
        val motionDeg = result.motionDuringDetectionDeg ?: 0f
        if (motionMm > PlacementQualityTuning.LOCK_MAX_MOTION_MM ||
            motionDeg > PlacementQualityTuning.LOCK_MAX_MOTION_DEG
        ) {
            correctionSettleSinceMillis = 0L
            correctionSettleTags = emptyList()
            correctionSettleProgress = 0f
            return
        }
        // Settle-timer: dezelfde tag-set moet CORRECTION_SETTLE_MILLIS aaneengesloten rustig + vers
        // in beeld blijven; pas dan is het anker "rustig" genoeg om op te corrigeren.
        val now = System.currentTimeMillis()
        val signature = visibleTags.sorted()
        if (correctionSettleSinceMillis == 0L || signature != correctionSettleTags) {
            correctionSettleSinceMillis = now
            correctionSettleTags = signature
        }
        val elapsed = now - correctionSettleSinceMillis
        correctionSettleProgress = (elapsed.toFloat() / CORRECTION_SETTLE_MILLIS).coerceIn(0f, 1f)
        if (elapsed < CORRECTION_SETTLE_MILLIS) return
        // Anker is gesetteld → herprojecteren en toepassen. Stralen worden hoe dan ook verbruikt.
        val dims = project.dimensionsMm
        val markersById = knownAprilTags.associateBy { it.id }
        val corrections = mutableMapOf<String, Pair<MmPosition, SensorDriftCorrection>>()
        val handled = mutableListOf<String>()
        var lastId: String? = null
        var lastDelta = 0
        pending.forEach { sensor ->
            val stored = placementRays[sensor.id] ?: return@forEach
            val refTag = stored.referenceTagId ?: sensor.referenceTagId ?: return@forEach
            handled += sensor.id
            val newPos = reprojectPlacementRay(
                rayAtPlacement = stored.rayTransformer,
                anchorAtPlacement = stored.anchorAtPlacement,
                anchorNow = anchorNow,
                dimensionsMm = dims,
                referenceMarker = null,
                placementPlane = stored.plane
            ) ?: return@forEach
            if (!newPos.insideBox(dims)) return@forEach
            val delta = distanceMm(newPos - sensor.positionMm)
            if (delta <= AUTO_CORRECT_MIN_DELTA_MM || delta > AUTO_CORRECT_MAX_DELTA_MM) return@forEach
            corrections[sensor.id] = newPos to SensorDriftCorrection(
                // Bewaar de écht-oorspronkelijke plaatsing: bij een (theoretische) tweede correctie
                // blijft de eerste as-placed staan i.p.v. de al-gecorrigeerde positie.
                asPlacedPositionMm = sensor.driftCorrection?.asPlacedPositionMm ?: sensor.positionMm,
                deltaMm = delta,
                correctedAtWallMillis = now,
                poseMarkerIds = signature
            )
            lastId = sensor.id
            lastDelta = delta
        }
        if (handled.isNotEmpty()) placementRays.keys.removeAll(handled.toSet())
        resetCorrectionSettle()
        if (corrections.isEmpty()) return
        project = project.copy(
            sensors = project.sensors.map { sensor ->
                corrections[sensor.id]?.let { (pos, corr) ->
                    sensor.copy(positionMm = pos, driftCorrection = corr)
                } ?: sensor
            }
        )
        saveProject()
        Log.i("ARSensCalibrationLock", "AUTO_CORRECT corrected=${corrections.size} tags=$visibleTags")
        message = if (corrections.size == 1) {
            "Sensor $lastId auto-gecorrigeerd: $lastDelta mm bijgesteld na herankering."
        } else {
            "${corrections.size} sensoren auto-gecorrigeerd na herankering."
        }
    }

    private fun resetCorrectionSettle() {
        if (correctionSettleProgress != null) correctionSettleProgress = null
        correctionSettleSinceMillis = 0L
        correctionSettleTags = emptyList()
    }

    private fun rmsJitterMm(samples: Collection<MmPosition>): Float? {
        val n = samples.size
        if (n < 3) return null
        val meanX = samples.sumOf { it.x.toDouble() } / n
        val meanY = samples.sumOf { it.y.toDouble() } / n
        val meanZ = samples.sumOf { it.z.toDouble() } / n
        val variance = samples.sumOf {
            val dx = it.x - meanX
            val dy = it.y - meanY
            val dz = it.z - meanZ
            dx * dx + dy * dy + dz * dz
        } / n
        return kotlin.math.sqrt(variance).toFloat()
    }

    private fun logCalibrationLock(now: Long, quality: PlacementQuality) {
        if (quality.isStableLock == lastLockStable &&
            now - lastLockLogMillis < CALIBRATION_LOCK_LOG_INTERVAL_MILLIS
        ) {
            return
        }
        lastLockStable = quality.isStableLock
        lastLockLogMillis = now
        Log.i(
            "ARSensCalibrationLock",
            "stableLock=${quality.isStableLock} grade=${quality.grade.name} reprojPx=${quality.reprojectionErrorPx} " +
                "reprojMm=${quality.reprojectionErrorMm} tags=${quality.poseMarkerIds} age=${quality.detectionAgeMillis}ms q=${quality.trackingQualityPercent} " +
                "jitter=${quality.jitterMm?.roundToInt() ?: -1}mm motion=${quality.motionDuringDetectionMm?.roundToInt() ?: -1}mm " +
                "reasons=${quality.reasons}"
        )
    }

    /** Bevriest de huidige plaatsingskwaliteit tot een audit-snapshot voor de zojuist geplaatste
     *  sensor (ruwe signalen + grade), met de jitter/lock-status van het plaatsingsmoment. */
    private fun buildPlacementAudit(): SensorPlacementAudit {
        val referenceTagId = aprilTagResult.poseMarkerIds.firstOrNull() ?: lastPlacementReference?.tagId
        val quality = computePlacementQuality(
            result = aprilTagResult,
            jitterMm = cursorJitterMm,
            isStableLock = placementQuality?.isStableLock ?: false,
            referenceTagId = referenceTagId
        )
        return quality.toAudit(
            placedAtWallMillis = System.currentTimeMillis(),
            fusionEvent = aprilTagResult.fusionEvent,
            fusionReason = aprilTagResult.fusionReason
        )
    }

    fun selectTagPlane(plane: TagPlane) {
        scannedMeasuredPosition = null
        if (selectedTagPlane != plane) clearCursorForPlaneChange()
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

    /** Tik op een rastercel ([displayU],[displayV] elk 0..1, OPERATOR-VIEW: links→rechts / onder→boven)
     *  in de tagsheet: zet de coördinaten op dat punt van het huidige vlak en de rotatie op die van het
     *  vlak. De cel is operator-view (alsof je vóór het gekozen vlak staat); converteer naar canonieke
     *  box-u/v vóór opslag zodat het 7×7-raster met de 2D-kaart overeenkomt (Achter/Links spiegelen U). */
    fun selectTagGridCell(displayU: Float, displayV: Float) {
        selectedTagGridUv = tagGridDisplayUvToCanonicalUv(selectedTagPlane, displayU, displayV)
        clearScannedTagSelection()
        applyTagCellPlacement()
    }

    /** Wisselt tussen raster- en handmatige invoer. Terug naar grid zet de coördinaten weer op het
     *  laatst gekozen rasterpunt; naar handmatig laat de huidige (getypte) coördinaten staan. */
    fun chooseTagPlacementManual(manual: Boolean) {
        if (tagPlacementManual == manual) return
        tagPlacementManual = manual
        if (manual) lastTagPlacementSource = "manual" else applyTagCellPlacement()
    }

    /** Kiest welk punt van de tag de handmatig ingevoerde X/Y/Z is (operator-view anker, zie
     *  [TagMeasurementAnchor]). Werkt alleen door in handmatige anker-modus; grid/AR-posities blijven
     *  center (zie [saveMeasuredTag]). Naam `choose…` i.p.v. `set…` om de JVM-clash met de gegenereerde
     *  property-setter te vermijden. */
    fun chooseTagMeasurementAnchor(anchor: TagMeasurementAnchor) {
        tagMeasurementAnchor = anchor
    }

    /** Wisselt de handmatige invoer tussen anker-modus (false) en box-rand-offset-modus (true). */
    fun chooseTagEdgeOffsetMode(edge: Boolean) {
        tagEdgeOffsetMode = edge
    }

    /** Referentie (min-rand/max-rand/handmatig) voor de eerste resp. tweede vrije vlak-as in de
     *  box-rand-offset-modus. `choose…` om de JVM-clash met de property-setter te vermijden. */
    fun chooseTagEdgeURef(reference: TagAxisReference) {
        tagEdgeURef = reference
    }

    fun chooseTagEdgeVRef(reference: TagAxisReference) {
        tagEdgeVRef = reference
    }

    /** Of in "Meet vanaf rand" tot de PAPIER-rand/hoek is gemeten (papier/witruimte-marge telt mee). */
    fun chooseTagMeasuredToPaper(paper: Boolean) {
        tagMeasuredToPaper = paper
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
        lastTagPlacementSource = "grid"
        setTagFieldsFromBox(tagPositionFor(selectedTagPlane, u, v, project.dimensionsMm))
        applySelectedTagRotation()
    }

    private fun applySelectedTagRotation() {
        val rotation = tagRotationFor(selectedTagPlane)
        setTagRotation(rotation.x.toInt(), rotation.y.toInt(), rotation.z.toInt())
    }

    /** Box-positie van een operator-view rastercel — voor het oplichten van de gekozen cel. Converteert
     *  display→canoniek (zelfde mapping als [selectTagGridCell]) zodat de oplichtende cel en de
     *  opgeslagen coördinaten exact overeenkomen. */
    fun tagCellBoxPosition(displayU: Float, displayV: Float): MmPosition {
        val (u, v) = tagGridDisplayUvToCanonicalUv(selectedTagPlane, displayU, displayV)
        return tagPositionFor(selectedTagPlane, u, v, project.dimensionsMm)
    }

    /** Huidige (getypte) tag-coördinaten omgezet naar box-mm, of null bij ongeldige invoer. */
    fun currentTagBoxPositionOrNull(): MmPosition? =
        operatorPositionOrNull(tagX, tagY, tagZ)?.let { project.coordinateMapper().operatorToBox(it) }

    /** Preview van het tag-CENTER dat [saveMeasuredTag] zou opslaan, langs exact hetzelfde rekenpad
     *  (anker-/box-rand-modus, papier-marge, buitenwaartse offset) — zonder de detectie/ID-poorten. Voor
     *  de offset-assistent in de tagsheet. Null bij ongeldige invoer. */
    fun tagPlacementPreview(): WorkflowTagCenterPreview? {
        val size = tagSize.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val dims = project.dimensionsMm
        val outwardOffsetMm = if (tagPlacementManual) (tagOutwardOffset.toIntOrNull() ?: 0) else 0
        val inPlaneCenter: MmPosition
        val reference: String
        if (tagPlacementManual && tagEdgeOffsetMode) {
            // Meet vanaf rand: de rand-afstanden geven het GEMETEN punt op het vlak; daarna corrigeert
            // het anker (tag-midden/hoek) + optionele papier-marge dat naar het tag-center.
            val uVal = tagEdgeU.toIntOrNull() ?: return null
            val vVal = tagEdgeV.toIntOrNull() ?: return null
            val measuredPoint = tagEdgeOffsetToCenter(selectedTagPlane, tagEdgeURef, uVal, tagEdgeVRef, vVal, dims)
            val marginU = if (tagMeasuredToPaper) (tagPaperMarginU.toIntOrNull() ?: 0) else 0
            val marginV = if (tagMeasuredToPaper) (tagPaperMarginV.toIntOrNull() ?: 0) else 0
            inPlaneCenter = tagMeasuredPointToCenter(measuredPoint, selectedTagPlane, size, tagMeasurementAnchor, dims, marginU, marginV)
            reference = "rand U ${tagEdgeURef.label} $uVal · V ${tagEdgeVRef.label} $vVal · ${tagMeasurementAnchor.label}" +
                if (tagMeasuredToPaper && (marginU != 0 || marginV != 0)) " · papier U$marginU V$marginV" else ""
        } else {
            val operatorPosition = operatorPositionOrNull(tagX, tagY, tagZ) ?: return null
            val measuredBox = project.coordinateMapper().operatorToBox(operatorPosition)
            val anchor = if (tagPlacementManual) tagMeasurementAnchor else TagMeasurementAnchor.Center
            inPlaneCenter = tagMeasuredPointToCenter(measuredBox, selectedTagPlane, size, anchor, dims)
            reference = "gemeten ${anchor.label}"
        }
        val center = applyTagOutwardOffset(inPlaneCenter, selectedTagPlane, outwardOffsetMm)
        val summary = reference + if (outwardOffsetMm != 0) " · buitenwaarts $outwardOffsetMm" else ""
        return WorkflowTagCenterPreview(
            center = center,
            operator = project.coordinateMapper().boxToOperator(center),
            inPlane = inPlaneCenter.insideBox(dims),
            summary = summary
        )
    }

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
            // Snijd met EXACT het gekozen vlak (selectedTagPlane), niet met de hele box: zo kan een
            // Rechts-tag nooit stilletjes op Voor belanden doordat de straal onder een scherende hoek
            // eerst het voorvlak raakt.
            val poseFromOtherTag = result.transformerPose != null && detection.id !in result.poseMarkerIds
            if (poseFromOtherTag) {
                val derivedPos = estimateSurfaceAtPixelOnPlane(
                    result = result,
                    pixelX = detection.centerPx.xPx,
                    pixelY = detection.centerPx.yPx,
                    plane = selectedTagPlane,
                    dimensionsMm = project.dimensionsMm
                )
                if (derivedPos != null) {
                    lastTagPlacementSource = "raycast-selected-plane"
                    // AR-afgeleide positie is het tag-CENTER (uit centerPx) → forceer Center zodat een
                    // per ongeluk aanstaande edge-modus de positie niet met een halve tag verschuift.
                    tagMeasurementAnchor = TagMeasurementAnchor.Center
                    setTagFieldsFromBox(derivedPos)
                    logFrameAudit(
                        "raycast plane=${selectedTagPlane.name} hit=(${derivedPos.x},${derivedPos.y},${derivedPos.z}) " +
                            "tag=${detection.id} poseSource=${frameAuditPoseSource(result)}"
                    )
                    message = "AprilTag ${detection.id} gelezen — positie afgeleid op vlak ${selectedTagPlane.shortLabel}. Controleer en pas aan indien nodig."
                    return
                }
                // Straal raakt het gekozen vlak niet binnen zijn rechthoek: NIET stil terugvallen op
                // een ander vlak. Laat de operator het vlak corrigeren of de cursor op de tag richten.
                logFrameAudit(
                    "raycast plane=${selectedTagPlane.name} rejected=ray-misses-selected-plane " +
                        "tag=${detection.id} poseSource=${frameAuditPoseSource(result)}"
                )
                tagScanArmed = true
                message = "Tag niet geplaatst: de straal raakt het gekozen vlak ${selectedTagPlane.shortLabel} niet. " +
                    "Kies het juiste vlak of richt de cursor op de tag."
                return
            }
        }
        message = "AprilTag ${detection.id} gelezen. Controleer meet-XYZ, rotatie en druk op Tag opslaan."
    }

    /** Of [position] exact op het vaste-vlak-component van [plane] ligt (canonieke conventie, gelijk
     *  aan [tagPositionFor] en [estimateSurfaceAtPixelOnPlane]). Harde poort vóór het opslaan van een
     *  referentietag: de positie MOET op het gekozen vlak liggen, anders kan rotatie/positie
     *  mismatchen → een gespiegelde/inverted pose. De UI kiest nooit stilletjes een ander vlak. */
    private fun isPositionOnPlane(position: MmPosition, dims: MmPosition, plane: TagPlane): Boolean =
        when (plane) {
            TagPlane.Front -> position.y == 0
            TagPlane.Back -> position.y == dims.y
            TagPlane.Left -> position.x == 0
            TagPlane.Right -> position.x == dims.x
            TagPlane.Top -> position.z == dims.z
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
        val size = tagSize.toIntOrNull()
        val poseWeight = tagPoseWeight.toFloatOrNull()?.coerceAtLeast(0.01f) ?: 1f
        if (id == null || size == null) {
            message = "Tag-ID en formaat moeten getallen zijn."
            return
        }
        if (size <= 0) {
            message = "Tagformaat moet groter zijn dan 0 mm."
            return
        }
        if (visibleDetection != null && tagId.toIntOrNull() != visibleDetection.id) {
            tagId = visibleDetection.id.toString()
        }
        // Buitenwaartse offset (rib/pijp/dik papier) geldt alleen bij handmatige invoer; wordt NA de
        // on-plane-poort toegepast zodat het in-vlak deel netjes op het vlak gevalideerd blijft.
        val outwardOffsetMm = if (tagPlacementManual) (tagOutwardOffset.toIntOrNull() ?: 0) else 0
        // Het in-vlak tag-CENTER (outward=0), via twee invoerwegen. [Marker.positionMm] blijft het
        // tag-center; markerCornersInProjectFrame ongewijzigd.
        val inPlaneCenter: MmPosition
        if (tagPlacementManual && tagEdgeOffsetMode) {
            // Meet vanaf rand: geen vrij gemeten X/Y/Z, dus geen on-plane-poort nodig — het GEMETEN punt
            // ligt per constructie op het gekozen vlak. De rand-afstanden geven dat meetpunt; het anker
            // (tag-midden/hoek) + optionele papier-marge corrigeren dan naar het tag-center.
            val uVal = tagEdgeU.toIntOrNull()
            val vVal = tagEdgeV.toIntOrNull()
            if (uVal == null || vVal == null) {
                message = "Meet vanaf rand: vul geldige mm-afstanden in voor beide randen."
                return
            }
            val measuredPoint = tagEdgeOffsetToCenter(
                plane = selectedTagPlane,
                uReference = tagEdgeURef,
                uValueMm = uVal,
                vReference = tagEdgeVRef,
                vValueMm = vVal,
                dimensionsMm = project.dimensionsMm
            )
            val marginU = if (tagMeasuredToPaper) (tagPaperMarginU.toIntOrNull() ?: 0) else 0
            val marginV = if (tagMeasuredToPaper) (tagPaperMarginV.toIntOrNull() ?: 0) else 0
            inPlaneCenter = tagMeasuredPointToCenter(
                measured = measuredPoint,
                plane = selectedTagPlane,
                sizeMm = size,
                anchor = tagMeasurementAnchor,
                dimensionsMm = project.dimensionsMm,
                paperMarginUMm = marginU,
                paperMarginVMm = marginV
            )
        } else {
            // Anker-modus (handmatig) of grid/AR: het GEMETEN punt moet op het gekozen vlak liggen.
            val operatorPosition = operatorPositionOrNull(tagX, tagY, tagZ)
            if (operatorPosition == null) {
                message = "Tag-ID, positie en formaat moeten getallen zijn."
                return
            }
            val measuredBox = project.coordinateMapper().operatorToBox(operatorPosition)
            if (!measuredBox.insideBox(project.dimensionsMm)) {
                message = "Tagpositie valt buiten de trafo-box. Controleer origin/asrichting of meet-XYZ."
                return
            }
            // Harde poort op het GEMETEN punt: het MOET exact op het gekozen vlak liggen. Zo kan een
            // Rechts-tag nooit stilletjes als Voor worden opgeslagen (verkeerde rotatie → gespiegelde
            // pose). De rotatie volgt dan eenduidig selectedTagPlane; geen stille vlak-herleiding.
            if (!isPositionOnPlane(measuredBox, project.dimensionsMm, selectedTagPlane)) {
                logFrameAudit(
                    "save rejected=position-not-on-selected-plane selectedPlane=${selectedTagPlane.name} " +
                        "box=(${measuredBox.x},${measuredBox.y},${measuredBox.z}) tag=$id"
                )
                message = "Tag niet opgeslagen: meet-XYZ ${operatorPosition.toReadableMm()} ligt niet op " +
                    "het gekozen vlak ${selectedTagPlane.shortLabel}. Kies het juiste vlak of corrigeer de meet-XYZ."
                return
            }
            // XYZ-handmatig (of grid/AR): operator-view anker → center. Papier-marge hoort alleen bij
            // "Meet vanaf rand"; grid/AR forceren Center (geen dubbele offset).
            val measurementAnchor = if (tagPlacementManual) tagMeasurementAnchor else TagMeasurementAnchor.Center
            inPlaneCenter = tagMeasuredPointToCenter(
                measured = measuredBox,
                plane = selectedTagPlane,
                sizeMm = size,
                anchor = measurementAnchor,
                dimensionsMm = project.dimensionsMm
            )
        }
        // De tag-footprint (in-vlak center) moet binnen het vlak vallen; de buitenwaartse offset mag het
        // normaal-component bewust buiten de box duwen en wordt daarom NA deze check toegepast.
        if (!inPlaneCenter.insideBox(project.dimensionsMm)) {
            message = "Tagpositie valt buiten de trafo-box. Controleer origin/asrichting of de offsets."
            return
        }
        val boxPosition = applyTagOutwardOffset(inPlaneCenter, selectedTagPlane, outwardOffsetMm)
        // Voor meldingen: de meet-XYZ van het uiteindelijke center (werkt voor beide invoerwegen).
        val operatorPosition = project.coordinateMapper().boxToOperator(boxPosition)
        val existing = project.markers.firstOrNull {
            it.id == id && it.isAprilTagCalibrationMarker()
        }
        val rotation = tagRotationFor(selectedTagPlane)
        setTagRotation(rotation.x.toInt(), rotation.y.toInt(), rotation.z.toInt())
        val marker = Marker(
            id = id,
            type = "apriltag",
            sizeMm = size,
            positionMm = boxPosition,
            rotationDeg = rotation,
            active = existing?.active ?: true,
            poseWeight = poseWeight,
            // Live in AR vastgelegde tag. Bestaande (voorbereide) tag behoudt zijn herkomst.
            origin = existing?.origin ?: PlacementOrigin.OnTheFly
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
        val savedPlane = markerSurfaceLabel(marker, project.dimensionsMm)
        val saveSource = when {
            tagPlacementManual -> "manual"
            visibleDetection != null && lastTagPlacementSource == "raycast-selected-plane" -> "raycast-selected-plane"
            else -> lastTagPlacementSource
        }
        logFrameAudit(
            "save selectedPlane=${selectedTagPlane.name} savedPlane=$savedPlane " +
                "box=(${marker.positionMm.x},${marker.positionMm.y},${marker.positionMm.z}) " +
                "rot=(${rotation.x.toInt()},${rotation.y.toInt()},${rotation.z.toInt()}) tag=$id source=$saveSource"
        )
        // Opslaan zonder dat de camera de tag ooit gezien heeft kan (handmatig ID), maar levert
        // nooit een pose op zolang de detector hem niet herkent — meestal een tag uit een andere
        // familie dan de ingestelde dictionary. Benoem dat expliciet i.p.v. stil te slagen.
        val savedWithoutDetection = visibleDetection == null && recentlyScannedId == null
        val inSensorTagRange = id >= sensorTagStartId
        message = "AprilTag $id opgeslagen. Meet-XYZ ${operatorPosition.toReadableMm()} | box ${marker.positionMm.toReadableMm()}." +
            if (savedWithoutDetection) {
                " Let op: deze tag is nog niet door de camera gedetecteerd — zonder detectie komt er geen pose. " +
                    "Controleer of de print uit de familie ${tagDictionary.label} komt (Instellingen)."
            } else {
                ""
            } +
            if (inSensorTagRange) {
                " Let op: ID $id valt in het sensor-tag-bereik (≥ $sensorTagStartId); referentietags horen daaronder."
            } else {
                ""
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
            message = "3D-bestand(en) konden niet geladen worden."
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
            append(if (imported.size == 1) "3D-model toegevoegd: ${newModels.first().name}" else "${imported.size} 3D-delen toegevoegd")
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
                "De 3D-delen worden op de tankwanden uitgelijnd. De trafo-afmetingen zijn vergrendeld " +
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
            message = "Geen geldige 3D-meshes om uit te lijnen."
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
                -1 -> "Geen geldige 3D-meshes om uit te lijnen."
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
                message = "Geen geldige 3D-meshes om de box te berekenen."
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
        if (!locked && project.referenceGeometryMode == ReferenceGeometryMode.ScannedWalls) {
            message = "Bij Wanden scannen blijven de projectmaten beschermd tegen 3D-import. Kies Bekende tagposities om dit te wijzigen."
            return
        }
        if (project.dimensionsLocked == locked) return
        project = project.copy(dimensionsLocked = locked)
        saveProject()
        message = if (locked) {
            "Maten vergrendeld: 3D-import overschrijft de trafo-afmetingen niet meer."
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
        message = if (fitToBox) "Model passend gemaakt en gecentreerd in de trafo-box." else "Model gecentreerd in de trafo-box."
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

    private fun saveSensorPointInternal(markInstalled: Boolean, placement: SensorPlacementAudit? = null, plane: TagPlane? = null): Sensor? {
        val operatorPosition = operatorPositionOrNull(sensorX, sensorY, sensorZ)
        val tolerance = sensorTolerance.toIntOrNull()
        val id = sensorId.ifBlank { nextSensorId() }.trim()
        val name = sensorName.ifBlank { "sens" }.trim()
        if (operatorPosition == null || tolerance == null || tolerance <= 0) {
            message = "Sensorpositie moet in hele millimeters staan; de tolerantie moet groter dan nul zijn."
            return null
        }
        val boxPosition = project.coordinateMapper().operatorToBox(operatorPosition)
        if (!boxPosition.insideBox(project.dimensionsMm)) {
            message = "Sensorpositie valt buiten de trafo-box. Controleer origin/asrichting of meet-XYZ."
            return null
        }
        val existingSensor = project.sensors.firstOrNull { it.id == id }
        val target = existingSensor?.takeIf { markInstalled && (it.status == SensorStatus.Pending || it.origin == PlacementOrigin.Prepared) }
        val tagIdValue = sensorTagId.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (sensorTagId.isNotBlank() && (tagIdValue == null || tagIdValue < sensorTagStartId)) {
            message = "Kies een sensor-tag-ID vanaf $sensorTagStartId of laat het veld leeg."
            return null
        }
        sensorTagConflict(project.sensors, id, tagIdValue)?.let { message = it; return null }
        val savedPosition = target?.positionMm ?: boxPosition
        val nextOrder = (project.sensors.maxOfOrNull { it.order } ?: 0) + 1
        var sensor = Sensor(
            order = existingSensor?.order ?: nextOrder,
            id = id,
            name = target?.name ?: name,
            side = target?.side ?: (plane ?: tagPlaneForSurfacePosition(savedPosition)).name,
            normal = target?.normal ?: tagPlaneOutwardNormal(plane ?: tagPlaneForSurfacePosition(savedPosition)),
            positionMm = savedPosition,
            toleranceMm = target?.toleranceMm ?: tolerance,
            instruction = target?.instruction ?: sensorInstruction.trim(),
            status = if (markInstalled) SensorStatus.Ok else existingSensor?.status ?: SensorStatus.Pending,
            // Bewaar de referentietag als plaatsingsherkomst; de overlay deelt het wereldanker.
            // On-the-fly: de actief gescande tag. Voorbereid: de
            // dichtstbijzijnde tag (zo verschijnt de sensor bij die tag zodra hij gescand wordt).
            referenceTagId = sensorReferenceTagId
                ?: existingSensor?.referenceTagId
                ?: nearestTagIdForBox(boxPosition),
            // Live-AR plaatsing levert een audit-snapshot; form-/2D-edits (placement == null) behouden
            // de bestaande audit.
            placement = placement ?: existingSensor?.placement,
            // An empty field explicitly removes the optional physical label.
            sensorTagId = tagIdValue,
            driftCorrection = if (markInstalled) null else existingSensor?.driftCorrection,
            // Herkomst: een bestaande sensor behoudt zijn herkomst; een NIEUWE sensor is OnTheFly als
            // hij live is vastgelegd (markInstalled), anders Voorbereid (formulier/2D).
            origin = existingSensor?.origin
                ?: if (markInstalled) PlacementOrigin.OnTheFly else PlacementOrigin.Prepared
        )
        if (markInstalled) {
            log = confirmSensorAtMeasuredPosition(
                log = log, sensor = sensor, measuredPosition = boxPosition,
                photo = null, confirmedAt = repository.nowIso()
            )
            sensor = sensor.copy(status = log.results.first { it.sensorId == sensor.id }.status)
            repository.saveLog(log)
        }
        if (!markInstalled) {
            val change = updateSensorPlan(sensor, log)
            sensor = change.sensor
            log = change.log
            repository.saveLog(log)
        }
        val sensors = (project.sensors.filterNot { it.id == sensor.id } + sensor)
            .sortedBy { it.order }.mapIndexed { index, item -> item.copy(order = index + 1) }
        project = project.copy(sensors = sensors)
        resetSensorFormForNext()
        showSensorOverlay = true
        saveProject()
        message = if (markInstalled) {
            "Sensor ${sensor.id} geplaatst en bevestigd op meet-XYZ ${operatorPosition.toReadableMm()}."
        } else {
            "Sensor ${sensor.id} opgeslagen op meet-XYZ ${operatorPosition.toReadableMm()}."
        }
        // Handmatige/2D-bewerking (geen audit) = positie is autoritatief → eventuele oude straal weg.
        if (placement == null) placementRays.remove(sensor.id)
        return sensor
    }

    fun saveSensorAtCursor() {
        if (!sensorPlacementReady) {
            message = sensorPlacementBlockReason ?: "De AR-uitlijning is nog niet beschikbaar."
            return
        }
        val cursor = arCursorPosition
        if (cursor == null) {
            message = "Sensor niet opgeslagen: richt de AR-cursor op het gekozen trafovlak."
            return
        }
        if (arCursorSource != "surface" && arCursorSource != "depth") {
            message = "Sensor niet opgeslagen: de actuele AR-cursor raakt het gekozen trafovlak niet."
            return
        }
        if (!arCursorInsideTransformer) {
            message = "Cursor raakt buiten de trafo-afmetingen. Richt opnieuw op de trafo."
            return
        }
        // On-the-fly sensor-tag koppeling: staat er een sensor-tag (ID ≥ sensorTagStartId) onder de
        // cursor en is de instelling aan, dan koppelt deze plaatsing die tag automatisch. De positie
        // komt dan van het tagmiddelpunt op het gekozen trafo-vlak. De gekozen sensor-ID blijft staan.
        val sensorTag = if (!planSensorAtCursor && autoLinkSensorTagOnPlace && aprilTagResult.hasFreshDetection()) {
            bestDetectionAtCursorForSetup(aprilTagResult) { it.id >= sensorTagStartId }
        } else {
            null
        }
        val tagRay = sensorTag?.let { tag ->
            imagePoseRayForPixel(aprilTagResult, tag.centerPx.xPx, tag.centerPx.yPx)
        }
        val tagSurface = tagRay?.let { intersectTagPlaneExact(it, selectedTagPlane, project.dimensionsMm) }
        if (sensorTag != null && tagSurface == null) {
            message = "Sensor-tag raakt het gekozen vlak niet. Kies het juiste trafovlak en scan opnieuw."
            return
        }
        val placePosition = tagSurface ?: cursor
        val placementRay = tagRay ?: cursorPlacementRay
        val placementPlane = selectedTagPlane
        if (sensorTag != null) {
            if (sensorId.isBlank()) sensorId = nextSensorId()
            sensorTagConflict(project.sensors, sensorId, sensorTag.id)?.let { message = it; return }
            val linkedTag = project.sensors.firstOrNull { it.id == sensorId }?.sensorTagId
            if (linkedTag != null && linkedTag != sensorTag.id) {
                message = "Sensor $sensorId is gekoppeld aan tag $linkedTag. Wijzig de koppeling via Bewerken."
                return
            }
            sensorTagId = sensorTag.id.toString()
        }
        // Sla de tag op die nu de pose levert — dit wordt de vaste referentietag voor deze sensor.
        // Bij een stale tag (ARCore-fallback) is poseMarkerIds leeg; val dan terug op de laatst
        // gebruikte plaatsingsreferentie zodat de sensor toch aan de juiste tag gekoppeld wordt.
        sensorReferenceTagId = aprilTagResult.poseMarkerIds.firstOrNull()
            ?: lastPlacementReference?.tagId
        setSensorFieldsFromBox(placePosition)
        if (sensorId.isBlank()) sensorId = nextSensorId()
        if (sensorName.isBlank()) sensorName = "sens"
        if (planSensorAtCursor && project.sensors.any { it.id == sensorId }) {
            message = "Dit sensornummer bestaat al. Kies een nieuw nummer of bewerk de bestaande sensor."
            return
        }
        val audit = buildPlacementAudit()
        Log.i(
            "ARSensPlacementQuality",
            "save sensor=$sensorId grade=${audit.grade.name} reprojPx=${audit.reprojectionErrorPx} " +
                "reprojMm=${audit.reprojectionErrorMm} stableLock=${audit.wasStablePlacementLock} " +
                "tags=${audit.poseMarkerIds} ref=${audit.referenceTagId} jitter=${audit.jitterMm} " +
                "motionMm=${audit.motionDuringDetectionMm} fusion=${audit.fusionEvent}:${audit.fusionReason} " +
                "reasons=${audit.reasons}"
        )
        val preservesTarget = project.sensors.any { it.id == sensorId &&
            (it.origin == PlacementOrigin.Prepared || it.status == SensorStatus.Pending) }
        val saved = saveSensorPointInternal(markInstalled = !planSensorAtCursor,
            placement = audit.takeUnless { planSensorAtCursor }, plane = selectedTagPlane)
        if (saved != null) {
            if (!planSensorAtCursor) {
                if (!preservesTarget) storePlacementRayFor(saved.id, saved.referenceTagId, placementRay, placementPlane)
                selectNextUnplacedCameraSensor(saved.id)
            }
            val tagNote = sensorTag?.let { " · tag ${it.id}" }.orEmpty()
            val installed = log.results.firstOrNull { it.sensorId == saved.id }
            message = if (planSensorAtCursor) {
                "Sensor ${saved.id} voorbereid · doelradius ${saved.toleranceMm} mm."
            } else if (preservesTarget && installed != null) {
                "Sensor ${saved.id} vastgelegd$tagNote · ${installed.distanceErrorMm} mm van doel " +
                    "(${if (installed.status == SensorStatus.Fail) "buiten" else "binnen"} radius ${saved.toleranceMm} mm)."
            } else {
                "Sensor ${saved.id} vastgelegd$tagNote."
            }
        }
    }

    fun saveSensorAtBoxPosition(position: MmPosition) {
        if (!position.insideBox(project.dimensionsMm)) {
            message = "Sensorpunt valt buiten de trafo-box."
            return
        }
        if (project.sensors.any { it.id == sensorId.trim() }) {
            message = "Sensor $sensorId bestaat al. Kies een nieuw ID; verplaatsen kan via de selectie."
            return
        }
        setSensorFieldsFromBox(position)
        if (sensorId.isBlank()) sensorId = nextSensorId()
        if (sensorName.isBlank()) sensorName = "sens"
        saveSensorPointInternal(markInstalled = false, plane = tagPlaneForMapView(activeMapView.name, position))
    }

    fun saveTagAtBoxPosition(position: MmPosition, viewName: String) {
        if (!position.insideBox(project.dimensionsMm)) {
            message = "Tagpunt valt buiten de trafo-box."
            return
        }
        val plane = tagPlaneForMapView(viewName, position)
        val requestedId = tagId.toIntOrNull()
        val id = requestedId ?: nextAprilTagId()
        val size = tagSize.toIntOrNull()?.takeIf { it > 0 } ?: defaultTagSizeMm
        val rotation = tagRotationFor(plane)
        upsertPreparedTag(
            id = id,
            size = size,
            position = position,
            rotation = rotation,
            allowMoveExisting = false,
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
            poseWeight = poseWeight ?: existing?.poseWeight ?: 1f,
            // Voorbereide tag (2D/plan). Bestaande tag behoudt zijn herkomst.
            origin = existing?.origin ?: PlacementOrigin.Prepared
        )
        project = project.copy(markers = project.markers.filterNot { it.id == id && it.isAprilTagCalibrationMarker() } + marker)
        setTagFieldsFromBox(position)
        tagPoseWeight = marker.poseWeight.toString()
        setTagRotation(rotation.x.toInt(), rotation.y.toInt(), rotation.z.toInt())
        if (advanceToNextId) {
            tagId = nextAprilTagId().toString()
            tagSize = defaultTagSizeMm.toString()
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
        sensorTagId = sensor.sensorTagId?.toString() ?: ""
        sensorReferenceTagId = sensor.referenceTagId
    }

    fun beginMapPreparation() {
        beginNewSensor()
        tagId = nextAprilTagId().toString()
        tagSize = defaultTagSizeMm.toString()
    }

    fun saveNewSensorFromFields() {
        if (project.sensors.any { it.id == sensorId.trim() }) {
            message = "Dit sensor-ID bestaat al. Kies een nieuw ID of selecteer de bestaande sensor."
            return
        }
        saveSensorPointInternal(markInstalled = false)
    }

    fun beginNewSensor() {
        resetSensorFormForNext()
        scannedMeasuredPosition = null
    }

    val cameraSensors: List<Sensor> get() = project.sensors.sortedBy { it.order }
    val cameraSensor: Sensor? get() = project.sensors.firstOrNull { it.id == sensorId }
    val cameraSensorLabel: String get() = cameraSensor?.displayName() ?: "Nieuwe sensor $sensorId"
    val cameraSensorAction: String get() = when {
        planSensorAtCursor -> "Doelgebied voorbereiden"
        cameraSensor?.status?.let { it != SensorStatus.Pending } == true -> "Opnieuw vastleggen"
        else -> "Vastleggen"
    }
    val canSelectPreviousCameraSensor: Boolean get() = cameraSensors.let { sensors ->
        sensors.isNotEmpty() && sensors.indexOfFirst { it.id == sensorId } != 0
    }
    val canSelectNextCameraSensor: Boolean get() = cameraSensor != null

    fun beginNewCameraSensor() {
        beginNewSensor()
        planSensorAtCursor = false
        cameraPlacementTarget = CameraPlacementTarget.Sensor
        message = null
    }

    fun stepCameraSensor(direction: Int) {
        val sensors = cameraSensors
        val index = sensors.indexOfFirst { it.id == sensorId }.let { if (it < 0) sensors.size else it }
        val next = (index + direction.coerceIn(-1, 1)).coerceIn(0, sensors.size)
        if (next == index) return
        sensors.getOrNull(next)?.let(::selectCameraSensor) ?: beginNewCameraSensor()
    }

    private fun selectNextUnplacedCameraSensor(afterId: String) {
        val sensors = cameraSensors
        val start = sensors.indexOfFirst { it.id == afterId } + 1
        val next = (sensors.drop(start) + sensors.take(start)).firstOrNull {
            it.id != afterId && it.status == SensorStatus.Pending
        }
        next?.let(::selectCameraSensor) ?: beginNewCameraSensor()
    }

    fun selectCameraSensor(sensor: Sensor) {
        selectSensorForEdit(sensor)
        planSensorAtCursor = false
        scannedMeasuredPosition = null
        val plane = sensorPlane(sensor)
        if (selectedTagPlane != plane) clearCursorForPlaneChange()
        selectedTagPlane = plane
        cameraPlacementTarget = CameraPlacementTarget.Sensor
        message = if (sensor.status == SensorStatus.Pending) null else
            "${sensor.displayName()} opnieuw vastleggen: richt op de nieuwe plek en druk op de opnameknop."
    }

    private fun clearCursorForPlaneChange() {
        // The next render result intersects the new plane. A rapid second press must not
        // record a cursor/ray computed on the previous sensor's face.
        arCursorPosition = null
        arCursorSource = "none"
        cursorPlacementRay = null
    }

    fun openCameraForSensor(id: String?) {
        chooseMode(WorkMode.OnTheFly)
        id?.let { value -> project.sensors.firstOrNull { it.id == value }?.let(::selectCameraSensor) }
        cameraMenuRequest = "sensor"
    }

    fun editSensorDetails(id: String, name: String, radius: Int, tag: Int?, instruction: String): String? {
        val sensor = project.sensors.firstOrNull { it.id == id } ?: return "Sensor bestaat niet meer."
        if (radius <= 0) return "De radius moet groter zijn dan nul."
        if (tag != null && tag < sensorTagStartId) return "Sensor-tag-ID moet minstens $sensorTagStartId zijn."
        sensorTagConflict(project.sensors, id, tag)?.let { return it }
        val change = updateSensorPlan(sensor.copy(name = name.trim(), toleranceMm = radius,
            sensorTagId = tag, instruction = instruction.trim()), log)
        project = project.copy(sensors = project.sensors.map { if (it.id == id) change.sensor else it })
        log = change.log
        repository.saveLog(log)
        saveProject()
        message = "Sensor $id bijgewerkt."
        return null
    }

    fun resetPlacement(id: String) {
        val sensor = project.sensors.firstOrNull { it.id == id } ?: return
        val change = resetSensorInstallation(sensor, log)
        project = project.copy(sensors = project.sensors.map { if (it.id == id) change.sensor else it })
        log = change.log
        placementRays.remove(id)
        scannedMeasuredPosition = null
        repository.saveLog(log)
        saveProject()
        message = "Sensor $id staat weer op te plaatsen; zijn doelgebied blijft bewaard."
    }

    fun moveSensorToBoxPosition(sensorId: String, position: MmPosition, viewName: String? = null) {
        if (!position.insideBox(project.dimensionsMm)) {
            message = "Sensorpunt valt buiten de trafo-box."
            return
        }
        val existing = project.sensors.firstOrNull { it.id == sensorId } ?: return
        val plane = viewName?.let { tagPlaneForMapView(it, position) } ?: tagPlaneForSurfacePosition(position)
        val change = updateSensorPlan(existing.copy(positionMm = position, side = plane.name,
            normal = tagPlaneOutwardNormal(plane),
            referenceTagId = nearestTagIdForBox(position) ?: existing.referenceTagId), log)
        project = project.copy(sensors = project.sensors.map { if (it.id == sensorId) change.sensor else it })
        log = change.log
        placementRays.remove(sensorId)
        repository.saveLog(log)
        showSensorOverlay = true
        saveProject()
        message = "Doel van sensor $sensorId verplaatst. Een bestaande meting blijft op zijn werkelijke plek."
    }

    fun selectTagForEdit(marker: Marker) {
        val normalized = marker.asAprilTagCalibrationMarker(project.dimensionsMm)
        tagId = normalized.id.toString()
        tagSize = normalized.sizeMm.toString()
        tagPoseWeight = normalized.poseWeight.toString()
        setTagFieldsFromBox(normalized.positionMm)
        // De opgeslagen positie is het tag-center → bewerken vertrekt vanaf Center (geen edge-offset).
        tagMeasurementAnchor = TagMeasurementAnchor.Center
        setTagRotation(
            normalized.rotationDeg.x.toInt(),
            normalized.rotationDeg.y.toInt(),
            normalized.rotationDeg.z.toInt()
        )
        selectedTagPlane = tagPlaneForMarker(normalized)
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
        placementRays.remove(sensor.id)
        log = log.copy(results = log.results.filterNot { it.sensorId == sensor.id })
        repository.saveLog(log)
        saveProject()
        message = "Sensor ${sensor.id} verwijderd."
    }

    /** Zet de laatste bevestiging terug op te plaatsen; behoud de sensor en zijn doelgebied. */
    fun undoLastPlacedSensor() {
        val last = log.results.maxByOrNull { it.confirmedAt }
        if (last == null) { message = "Geen plaatsing om terug te zetten."; return }
        resetPlacement(last.sensorId)
    }

    fun previousSensor() {
        scannedMeasuredPosition = null
        currentSensorIndex = (currentSensorIndex - 1).coerceAtLeast(0)
        setCursorFromCurrentSensor()
    }

    fun nextSensor() {
        scannedMeasuredPosition = null
        currentSensorIndex = (currentSensorIndex + 1).coerceAtMost((project.sensors.size - 1).coerceAtLeast(0))
        setCursorFromCurrentSensor()
    }

    private fun sensorPlane(sensor: Sensor): TagPlane =
        TagPlane.entries.firstOrNull { it.name.equals(sensor.side, true) } ?: tagPlaneForSurfacePosition(sensor.positionMm)

    fun setCursorFromCurrentSensor() {
        currentSensor?.let {
            selectedTagPlane = sensorPlane(it)
            setCursorFieldsFromBox(it.positionMm)
        }
    }

    /** Herkent de sensor-tag (ID ≥ [sensorTagStartId]) bij de cursor, koppelt hem aan de bijbehorende
     *  sensor en zet diens werkelijke positie op het trafo-vlak klaar als gemeten waarde. Bevestigen
     *  blijft handmatig (de OK-knop → [confirmInstallation]). Gemodelleerd naar [saveMeasuredTag]. */
    fun confirmSensorByScannedTag() {
        val result = aprilTagResult
        if (!sensorPlacementReady) {
            message = sensorPlacementBlockReason ?: "De AR-uitlijning is nog niet beschikbaar."
            return
        }
        if (!result.hasFreshDetection()) {
            message = "Geen verse tag-detectie in dit cameraframe. Houd de sensor-tag midden in beeld."
            return
        }
        val detection = bestDetectionAtCursorForSetup(result) { it.id >= sensorTagStartId }
        if (detection == null) {
            message = "Geen sensor-tag (ID ≥ $sensorTagStartId) bij de cursor. Richt de cursor op de tag óp de sensor."
            return
        }
        val sensor = sensorForSensorTag(project.sensors, detection.id)
        if (sensor == null) {
            message = "Tag ${detection.id} is aan geen enkele sensor gekoppeld. Koppel hem via 'ID-tag' in de sensor-setup."
            return
        }
        if (sensor.id != currentSensor?.id) {
            message = "Tag ${detection.id} hoort bij sensor ${sensor.id}. Selecteer eerst die sensor."
            return
        }
        val measured = imagePoseRayForPixel(result, detection.centerPx.xPx, detection.centerPx.yPx)?.let {
            intersectTagPlaneExact(it, selectedTagPlane, project.dimensionsMm)
        }
        if (measured == null || !measured.insideBox(project.dimensionsMm)) {
            message = "Kon de positie van tag ${detection.id} niet op de trafo bepalen. Houd een referentietag in beeld en kom dichter/rechter voor de sensor."
            return
        }
        currentSensorIndex = project.sensors.sortedBy { it.order }
            .indexOfFirst { it.id == sensor.id }
            .coerceAtLeast(0)
        setCursorFieldsFromBox(measured)
        scannedMeasuredPosition = measured
        val delta = distanceMm(measured - sensor.positionMm)
        message = "Sensor ${sensor.id} herkend via tag ${detection.id} — afwijking $delta mm. Druk op OK om te bevestigen."
    }

    fun confirmInstallation() {
        val sensor = currentSensor ?: return
        if (!sensorPlacementReady) { message = sensorPlacementBlockReason; return }
        val measuredPosition = scannedMeasuredPosition ?: arCursorPosition?.takeIf {
            arCursorInsideTransformer && (arCursorSource == "surface" || arCursorSource == "depth")
        }
        if (measuredPosition == null) {
            message = "Richt de cursor op de geplaatste sensor of scan zijn sensor-tag bij een stabiele kalibratie."
            return
        }
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
                if (it.id == sensor.id) it.copy(status = log.results.first { r -> r.sensorId == sensor.id }.status, placement = buildPlacementAudit()) else it
            }
        )
        repository.saveLog(log)
        saveProject()
        val viaScan = scannedMeasuredPosition != null
        scannedMeasuredPosition = null
        message = if (viaScan) {
            "Sensor ${sensor.id} bevestigd via gescande tag op meet-XYZ ${operatorText(measuredPosition)}."
        } else {
            "Sensor ${sensor.id} gemeten met de cursor op meet-XYZ ${operatorText(measuredPosition)}."
        }
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
            if (!quiet) message = "Geen zichtbaar 3D-model om de tagdiepte op te bepalen."
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

    /** Vlak van een tag. Back/Links/Rechts/Boven hebben een UNIEKE standaard-rotatie → die wint
     *  (eenduidig, ook op een gedeelde rand). Front (Rz=0) en niet-standaard/oude rotaties vallen terug
     *  op de vlakpositie (Front = y==0; ook een nog-niet-gemigreerde back-tag op Rz=0 klopt zo). */
    private fun tagPlaneForMarker(marker: Marker): TagPlane = when (marker.rotationDeg) {
        tagRotationFor(TagPlane.Back) -> TagPlane.Back
        tagRotationFor(TagPlane.Left) -> TagPlane.Left
        tagRotationFor(TagPlane.Right) -> TagPlane.Right
        tagRotationFor(TagPlane.Top) -> TagPlane.Top
        else -> tagPlaneForSurfacePosition(marker.positionMm)
    }

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
        runCatching { repository.exportReportJson(project, log, autoCorrectSensorDrift) }
            .onSuccess { message = "JSON rapport geexporteerd: ${it.absolutePath} en Downloads/ARsens." }
            .onFailure { message = "JSON export mislukt: ${it.message ?: "onbekende fout"}" }
    }

    fun exportReportXlsx() {
        runCatching { repository.exportReportXlsx(project, log, autoCorrectSensorDrift) }
            .onSuccess { message = "Excel rapport geexporteerd: ${it.absolutePath} en Downloads/ARsens." }
            .onFailure { message = "Excel export mislukt: ${it.message ?: "onbekende fout"}" }
    }

    fun exportReportBoth() {
        runCatching {
            Triple(
                repository.exportReportCsv(project, log),
                repository.exportReportJson(project, log, autoCorrectSensorDrift),
                repository.exportReportXlsx(project, log, autoCorrectSensorDrift)
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
    private fun bestDetectionAtCursorForSetup(
        result: AprilTagFrameResult,
        filter: (com.example.arsens.ar.AprilTagDetection) -> Boolean = { true }
    ): com.example.arsens.ar.AprilTagDetection? {
        val detections = result.detections.filter(filter)
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
        sensorTolerance = "25"
        sensorInstruction = ""
        sensorTagId = ""
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

    /** Canoniek-frame audittrail (tag "ARSensFrameAudit"): tagopslag, raycast-setup en (throttled)
     *  overlay-posebron. Bewijst dat posities/vlakken consistent zijn vóórdat aan rotatie/corners
     *  gesleuteld wordt. */
    private fun logFrameAudit(message: String) {
        Log.i("ARSensFrameAudit", message)
    }

    /** Korte beschrijving van de actieve posebron voor de frame-audit. */
    private fun frameAuditPoseSource(result: AprilTagFrameResult): String {
        val source = when {
            result.displayProjection != null -> "fused-display"
            result.imageProjectionPose != null || result.transformerPose != null -> "tag-solvepnp"
            else -> "none"
        }
        return "$source/poseTags=${result.poseMarkerIds}"
    }

    /** Throttled overlay-posebron audit: welke bron de overlays nu gebruiken, plus de zichtbare
     *  posetags en per-tag keys. Eén regel per ~1,5 s zolang de bron-signatuur gelijk blijft. */
    private fun logFrameAuditOverlay(now: Long, result: AprilTagFrameResult) {
        val key = "${frameAuditPoseSource(result)}|perTag=${result.posePerTag.keys.sorted()}|" +
            "transformer=${result.transformerPose != null}|display=${result.displayProjection != null}"
        if (key == lastFrameAuditOverlayKey && now - lastFrameAuditOverlayMillis < CURSOR_LOG_INTERVAL_MILLIS) return
        lastFrameAuditOverlayKey = key
        lastFrameAuditOverlayMillis = now
        logFrameAudit(
            "overlay poseSource=${frameAuditPoseSource(result)} posePerTag=${result.posePerTag.keys.sorted()} " +
                "transformerPose=${result.transformerPose != null} displayProjection=${result.displayProjection != null}"
        )
    }

    fun setReferenceGeometryMode(mode: ReferenceGeometryMode) {
        if (mode == project.referenceGeometryMode) return
        if (mode == ReferenceGeometryMode.KnownTagPositions && listOf(project.dimensionsMm.x, project.dimensionsMm.y, project.dimensionsMm.z).any { it <= 0 }) {
            message = "Vul eerst afmetingen in of rond de wandscan af."
            return
        }
        resetArPoseState()
        project = project.copy(referenceGeometryMode = mode,
            dimensionsLocked = project.dimensionsLocked || mode == ReferenceGeometryMode.ScannedWalls)
        saveProject()
    }

    fun setWallDimensionSource(source: WallDimensionSource) {
        project = project.copy(wallDimensionSource = source)
        saveProject()
    }

    fun beginWallScan() {
        resetArPoseState()
        wallScanId = System.nanoTime()
        wallRequestBaseRevision = arCalibrationRevision
        wallScanActive = true
        wallScanSource = project.wallDimensionSource
        wallScanSize = defaultTagSizeMm.toString()
        wallScanWall = CalibrationWall.Front
        wallAssignments = emptyList()
        wallScanCounts = emptyMap()
        wallReadyTagIds = emptyList()
        wallTopOffset = "0"; wallTopOverlapVerified = false
        wallSideHeight = project.dimensionsMm.z.takeIf { it > 0 }?.toString().orEmpty()
        wallSelectedTagId = null
        wallScanMessage = null
        wallSolvedFrameId = null
        screen = WorkflowScreen.Tags
    }

    fun cancelWallScan() {
        resetArPoseState()
        screen = WorkflowScreen.Start
        message = "Wandscan geannuleerd; opgeslagen projectgeometrie is behouden."
    }

    fun updateWallScanFrame(result: AprilTagFrameResult) {
        if (!wallScanActive || result.calibrationRevision != wallRequestBaseRevision) return
        val frame = result.wallScanFrame ?: wallScanFrame?.copy(tracking = false, observations = emptyList(), projectionFromReference = null) ?: return
        if (frame.sessionId != wallScanId) return
        wallScanFrame = frame
        wallLastFrameMillis = android.os.SystemClock.elapsedRealtime()
        wallSession.observe(if (wallScanSolution == null) frame else frame.copy(observations = emptyList()),
            wallAssignments, android.os.SystemClock.elapsedRealtime())
        if (wallSession.invalidated) {
            wallScanSolution = null
            wallScanFootprint = null
            wallLinkedPreview = null
            wallScanCounts = emptyMap()
            wallReadyTagIds = emptyList()
            wallScanMessage = wallSession.reason
            wallTopOverlapVerified = false
            wallSeenTags = emptyList()
        } else {
            if (frame.observations.isNotEmpty()) wallSeenTags = frame.observations
            if (!frame.tracking) wallSeenTags = emptyList()
            val sequence = frame.observations.firstOrNull()?.sequence
            if (sequence != null && sequence > wallEvidenceSequence) {
                wallEvidenceSequence = sequence
                wallScanCounts = wallAssignments.associate { it.tagId to wallSession.count(it.tagId) }
                wallReadyTagIds = wallSession.estimates(wallAssignments).map { it.assignment.tagId }
                wallTopOverlapVerified = wallSession.hasTopOverlap(wallReadyTagIds)
            }
        }
    }

    fun selectWallScanFace(wall: CalibrationWall) {
        wallScanWall = wall
        if (wall == CalibrationWall.Top && wallScanFootprint != null) wallTopStage = true
        wallScanMessage = null
    }

    fun assignWallTag(id: Int) {
        if (wallScanSolution != null) return
        val observed = wallSeenTags.firstOrNull { it.tagId == id } ?: return
        if (android.os.SystemClock.elapsedRealtime() - observed.timestampMillis > 300L || wallScanFrame?.tracking != true) {
            wallScanMessage = "Scan de tag opnieuw om hem aan een wand toe te wijzen."; return
        }
        val targetWall = wallScanWall
        val existing = wallAssignments.firstOrNull { it.tagId == id }
        val size = wallScanSize.toIntOrNull()?.takeIf { it in 10..2000 }
            ?: run { wallScanMessage = "Voer de zwarte tagmaat in (10–2000 mm)."; return }
        if (existing?.wall == targetWall && existing.sizeMm == size) return
        if (wallScanFootprint != null && (targetWall != CalibrationWall.Top || (existing != null && existing.wall != CalibrationWall.Top))) editWallFootprint()
        selectWallScanFace(targetWall)
        wallSession.removeTag(id)
        wallReadyTagIds = wallReadyTagIds.filterNot { it == id }
        wallScanCounts = wallScanCounts - id
        wallAssignments = wallAssignments.filterNot { it.tagId == id } + WallTagAssignment(id, targetWall, size)
        wallSelectedTagId = id
        wallScanMessage = null
    }

    fun removeWallTag(id: Int) {
        if (wallAssignments.any { it.tagId == id && it.wall != CalibrationWall.Top }) editWallFootprint()
        wallScanSolution = null
        wallAssignments = wallAssignments.filterNot { it.tagId == id }
        wallSession.removeTag(id)
        wallReadyTagIds = wallReadyTagIds.filterNot { it == id }
    }

    private fun wallDatum(): WallVerticalDatum? {
        val topOffset = wallTopOffset.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        return WallVerticalDatum(-1, 0, topSurfaceOffsetMm = topOffset,
            sideHeightMm = wallSideHeight.toIntOrNull()?.takeIf { it > 0 })
    }

    val wallCaptureTag: WallTagObservation? get() = wallSeenTags.firstOrNull { it.tagId == wallSelectedTagId }
        ?: wallSeenTags.filter { o -> wallAssignments.none { it.tagId == o.tagId } }.minByOrNull { it.distanceMm }
        ?: wallSeenTags.minByOrNull { it.distanceMm }

    val wallCaptureHint: String get() {
        val tag = wallCaptureTag ?: return "Richt de camera op een tag. Kies daarna het vlak en leg de tag vast."
        val vertical = tag.normal.dot(tag.referenceUp)
        if (wallScanWall != CalibrationWall.Top && kotlin.math.abs(vertical) > 0.75)
            return "Deze tag lijkt horizontaal te liggen. Kies Boven, of zet hem vlak op de gekozen zijwand."
        if (wallScanWall != CalibrationWall.Top && kotlin.math.abs(vertical) > kotlin.math.sin(Math.toRadians(15.0)))
            return "Deze tag staat schuin. Leg hem vlak op de gekozen zijwand of controleer de vlakkeuze."
        if (wallScanWall == CalibrationWall.Top && vertical < kotlin.math.cos(Math.toRadians(12.0)))
            return "Deze tag lijkt niet op een horizontale bovenkant te liggen. Controleer het gekozen vlak."
        val assignment = wallAssignments.firstOrNull { it.tagId == tag.tagId }
        return when {
            assignment?.wall != wallScanWall -> "Tag ${tag.tagId} wordt gekoppeld aan ${wallScanWall.label}."
            tag.tagId in wallReadyTagIds -> "Tag ${tag.tagId} opgenomen op ${assignment.wall.label}."
            else -> "Tag ${tag.tagId} toegewezen · ${wallScanCounts[tag.tagId] ?: 0} metingen. Nog even scannen tot de positie vaststaat."
        }
    }

    fun solveWallFootprint() {
        if (wallSession.invalidated || wallScanFrame?.tracking != true) { wallScanMessage = wallSession.reason ?: "Herstel eerst tracking."; return }
        val datum = wallDatum() ?: run { wallScanMessage = "Vul een geldig hoogteverschil voor het bovenvlak in."; return }
        val samples = wallSession.estimates(wallAssignments).filter { it.assignment.wall != CalibrationWall.Top }
        val result = WallCalibrationSolver.solveFootprint(project.dimensionsMm, wallScanSource, samples, datum)
        wallScanFootprint = result.solution
        if (result.solution != null) {
            wallFootprintSamples = samples.filter { it.assignment.tagId in result.solution.quality.usedTagIds }
            wallFootprintDatum = datum
            wallTopStage = false
        }
        wallScanMessage = result.reason
    }

    fun startWallTopStage() {
        if (wallScanFootprint == null) return
        wallTopStage = true
        wallScanWall = CalibrationWall.Top
        wallScanMessage = null
    }

    fun editWallFootprint() {
        wallScanSolution = null; wallScanFootprint = null; wallLinkedPreview = null; wallTopStage = false
        wallScanWall = CalibrationWall.Front; wallScanMessage = null
    }

    fun solveWallScan() {
        if (wallScanFootprint == null) { wallScanMessage = "Bereken eerst de grondcontour uit de zijwanden."; return }
        if (wallSession.invalidated || wallScanFrame?.tracking != true || android.os.SystemClock.elapsedRealtime() - wallLastFrameMillis > 500L) { wallScanMessage = wallSession.reason ?: "Herstel eerst tracking."; return }
        val offset = wallTopOffset.toIntOrNull()?.takeIf { it >= 0 }
            ?: run { wallScanMessage = "Vul de verhoging van het bovenvlak in (0 op de tankbovenkant)."; return }
        val datum = wallFootprintDatum?.copy(topSurfaceOffsetMm = offset, sideHeightMm = wallSideHeight.toIntOrNull()?.takeIf { it > 0 }) ?: return
        // Keep the accepted side evidence fixed while learning arbitrary top tags in the same frame.
        val tags = wallFootprintSamples + wallSession.estimates(wallAssignments).filter { it.assignment.wall == CalibrationWall.Top }
        val result = WallCalibrationSolver.solve(project.dimensionsMm, wallScanSource, tags, datum)
        wallScanSolution = result.solution?.let { solved ->
            solved.copy(quality = solved.quality.copy(topOverlapVerified = wallSession.hasTopOverlap(solved.quality.usedTagIds), excludedTagIds =
                wallAssignments.map { it.tagId }.filterNot { it in solved.quality.usedTagIds }.sorted()))
        }
        wallScanSolution?.let { wallLinkedPreview = it }
        wallSolvedDatum = datum
        wallSolvedAssignments = wallAssignments.toList()
        wallSolvedSource = wallScanSource
        wallSolvedFrameId = wallScanFrame?.trackingFrameId
        wallScanMessage = result.reason
    }

    fun resumeWallScan() { wallScanSolution = null; wallSolvedFrameId = null }

    fun acceptWallScan() {
        val solved = wallScanSolution ?: return
        val datum = wallSolvedDatum ?: return
        if (wallSession.invalidated || wallScanFrame?.tracking != true || android.os.SystemClock.elapsedRealtime() - wallLastFrameMillis > 500L || wallSolvedFrameId != wallScanFrame?.trackingFrameId) {
            wallScanMessage = "De trackingreferentie veranderde; scan opnieuw."; return
        }
        val metadata = WallCalibrationData(calibratedAt = repository.nowIso(), dimensionsMm = solved.dimensionsMm,
            dimensionSource = wallSolvedSource, assignments = wallSolvedAssignments, datum = datum, quality = solved.quality,
            geometrySignature = wallGeometrySignature(solved.markers, solved.quality.usedTagIds))
        project = project.copy(referenceGeometryMode = ReferenceGeometryMode.ScannedWalls,
            wallDimensionSource = wallSolvedSource, dimensionsMm = solved.dimensionsMm, dimensionsLocked = true,
            markers = project.markers.filterNot { it.isAprilTagCalibrationMarker() } + solved.markers, wallCalibration = metadata)
        // Sensors, logs, coordinate convention and their recorded positions are untouched.
        saveProject()
        syncDimensionsFromProject()
        resetArPoseState()
        showBoxEdgesOverlay = true
        mode = WorkMode.OnTheFly
        cameraSensors.firstOrNull { it.status == SensorStatus.Pending }?.let(::selectCameraSensor)
        message = "Tankreferentie opgeslagen. Scan een gekalibreerde tag om uit te lijnen."
        screen = WorkflowScreen.Tags
    }

    fun recalibrateAr() {
        resetArPoseState()
        message = "AR opnieuw ijken: houd de telefoon rustig en scan de gecontroleerde referentietags."
    }

    private fun resetArPoseState() {
        wallScanActive = false
        wallScanFootprint = null
        wallLinkedPreview = null
        wallFootprintSamples = emptyList()
        wallFootprintDatum = null
        wallTopStage = false
        wallEvidenceSequence = -1L
        wallSolvedDatum = null
        wallSolvedAssignments = emptyList()
        wallScanSolution = null
        wallScanFrame = null
        wallSeenTags = emptyList()
        wallTopOverlapVerified = false
        wallSession.clear()
        scannedMeasuredPosition = null
        cursorPlacementRay = null
        arCalibrationRevision++
        lastLockDetectionSequence = 0L
        aprilTagResult = AprilTagFrameResult()
        lastObjectCursorAtMillis = 0L
        arCursorPosition = null
        arCursorInsideTransformer = true
        arCursorUsesDepth = false
        arCursorSource = "none"
        lastPlacementReference = null
        lastCursorLogKey = ""
        lastCursorLogMillis = 0L
        lastFrameAuditOverlayKey = ""
        lastFrameAuditOverlayMillis = 0L
        cursorJitterSamples.clear()
        cursorJitterMm = null
        lockTagSignature = emptyList()
        lockSinceMillis = 0L
        lockSamples = 0
        lastLockStable = false
        lastLockLogMillis = 0L
        placementQuality = null
        placementRays.clear()
        correctionSettleProgress = null
        correctionSettleSinceMillis = 0L
        correctionSettleTags = emptyList()
    }
}

/** Laatst gebruikte verse, bekende referentietag: het marker-vlak waarop de plaatsings-cursor
 *  blijft zodra de tag niet meer vers in beeld is maar ARCore nog trackt. */
internal data class PlacementReference(
    val tagId: Int,
    val marker: Marker
)

/** Sessie-gebonden camerastraal van het plaatsmoment (transformer-frame van toen) + het anker van
 *  toen, voor straal-replay-driftcorrectie. Niet gepersisteerd — alleen geldig binnen de ARCore-sessie. */
private data class StoredPlacementRay(
    val rayTransformer: RayMm,
    val anchorAtPlacement: Transform3D,
    val referenceTagId: Int?,
    val placedAtMillis: Long,
    val plane: TagPlane
)

/** Resultaat van [WorkflowAppState.tagPlacementPreview]: het berekende tag-center (box + meet-XYZ),
 *  of het in-vlak deel binnen het vlak valt, en een korte omschrijving van de toegepaste offsets. */
internal data class WorkflowTagCenterPreview(
    val center: MmPosition,
    val operator: MmPosition,
    val inPlane: Boolean,
    val summary: String
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
        // Back/Links/Rechts/Boven hebben een UNIEKE standaard-rotatie → die identificeert het vlak
        // eenduidig, ook als de tag op een gedeelde rand ligt (bv. een Links-tag op de achterrand
        // y==diepte; die werd anders als "Achter" gelabeld omdat y==diepte vóór x==0 gecheckt werd).
        // Front (Rz=0) en oude/niet-standaard rotaties vallen terug op de positie.
        marker.rotationDeg == tagRotationFor(TagPlane.Back) -> "Achter"
        marker.rotationDeg == tagRotationFor(TagPlane.Left) -> "Links"
        marker.rotationDeg == tagRotationFor(TagPlane.Right) -> "Rechts"
        marker.rotationDeg == tagRotationFor(TagPlane.Top) -> "Boven"
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

private const val CURSOR_JITTER_WINDOW = 15
private const val CALIBRATION_LOCK_LOG_INTERVAL_MILLIS = 1_500L
/** Straal-replay-correctie wordt pas toegepast boven [AUTO_CORRECT_MIN_DELTA_MM] (ruis negeren) en
 *  genegeerd boven [AUTO_CORRECT_MAX_DELTA_MM] (sanity: een absurd grote sprong duidt op een fout,
 *  niet op drift). */
private const val AUTO_CORRECT_MIN_DELTA_MM = 5
private const val AUTO_CORRECT_MAX_DELTA_MM = 2_000
/** Hoelang de tag rustig + vers in beeld moet blijven vóór een straal-replay-correctie vuurt
 *  (settle-timer; voedt het voortgangsringetje om het camera-puntje). */
private const val CORRECTION_SETTLE_MILLIS = 1_000L
private const val TAG_SCAN_SAVE_GRACE_MILLIS = 30_000L
private const val OBJECT_CURSOR_HOLD_MILLIS = 1_500L
private const val FRESH_PER_TAG_POSE_MILLIS = 300L
private const val CURSOR_LOG_INTERVAL_MILLIS = 1_500L
private const val DEPTH_SURFACE_SNAP_TOLERANCE_MM = 300
private const val DEPTH_BOX_OUTSIDE_TOLERANCE_MM = 300
