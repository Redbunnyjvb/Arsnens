package com.example.arsens.ar

import android.util.Log
import com.example.arsens.data.Marker
import com.example.arsens.data.MmPosition
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect
import java.util.Locale
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

data class AprilTagCorner(
    val xPx: Float,
    val yPx: Float
)

data class AprilTagDetection(
    val id: Int,
    val cornersPx: List<AprilTagCorner>,
    val centerPx: AprilTagCorner
)

enum class ArTrackingStatus(val label: String) {
    TagCalibration("Tag kalibratie"),
    ArCoreTracking("ARCore tracking"),
    DriftPossible("Drift mogelijk"),
    NeedsRecalibration("Herkalibratie nodig"),
    NoPose("Geen pose")
}

data class AprilTagFrameResult(
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val detections: List<AprilTagDetection> = emptyList(),
    val screenDetections: List<AprilTagDetection> = emptyList(),
    val cameraIntrinsics: CameraIntrinsics? = null,
    val imageToViewMapper: ImageToViewMapper? = null,
    val imageProjectionPose: TransformerPose? = null,
    val detectionsFresh: Boolean = false,
    val knownMarkerCount: Int = 0,
    val poseMarkerCount: Int = 0,
    val poseMarkerIds: List<Int> = emptyList(),
    val transformerPose: TransformerPose? = null,
    val displayProjection: ArDisplayProjection? = null,
    val candidateDisplayProjection: ArDisplayProjection? = null,
    val depthHitPositionMm: MmPosition? = null,
    val trackingQualityPercent: Int = 0,
    val errorMessage: String? = null,
    val trackingStatus: ArTrackingStatus = ArTrackingStatus.NoPose,
    val arTracking: Boolean = false,
    val fusionEvent: String? = null,
    val fusionReason: String? = null,
    val freshImageProjectionPose: TransformerPose? = null,
    val freshPosePerTag: Map<Int, TransformerPose> = emptyMap(),
    /** Per-tag individuele cameraPose (solvePnP) voor elke zichtbare/recent geziene tag.
     *  Hiermee projecteren we elke tag en sensor via ZIJN EIGEN referentietag — pixel-exact
     *  uitgelijnd op de live-detectie, ongeacht welke tag de dominante pose levert. */
    val posePerTag: Map<Int, TransformerPose> = emptyMap(),
    /** Leeftijd (ms) van de jongste tag-detectie waarop [posePerTag] gebaseerd is. Overlays
     *  gebruiken de per-tag pose alleen zolang die VERS is; daarna nemen ze de gefuseerde
     *  ARCore-pose (volgt de camerabeweging elk frame) zodat het beeld niet bevriest of
     *  verdwijnt wanneer de tag uit beeld raakt. */
    val detectionAgeMillis: Long = Long.MAX_VALUE,
    /** Stap 5 (perf): directe cameraCv→transformer matrix, ALLEEN gezet in ARCore-leading mode
     *  (samen met [displayProjection]). Dit is exact de matrix waaruit [transformerPose] is afgeleid,
     *  maar zonder de per-draw Rodrigues round-trip in de STL-overlay. In die mode wordt
     *  [transformerPose] niet gesmoothed, dus de directe matrix omzeilt geen smoothing. Buiten
     *  ARCore-leading mode null → de overlay houdt het bestaande solvePnP-pad. */
    val cameraCvFromTransformer: Transform3D? = null,
    /** Cameraverplaatsing tussen het grijpen van het camerabeeld en het verwerken/fuseren van de
     *  detectie (ARCore-pose bij capture vs. nu). Hoog = de telefoon bewoog tijdens de detectie →
     *  de tag-pose is minder betrouwbaar. Voedt de plaatsingskwaliteit. Null = geen tag-packet. */
    val motionDuringDetectionMm: Float? = null,
    val motionDuringDetectionDeg: Float? = null,
    /** Anker transformer→ARCore-wereld (alleen gezet als gekalibreerd; zelfde transform waaruit
     *  [displayProjection]/[cameraCvFromTransformer] volgen). Voor straal-replay-driftcorrectie:
     *  samen met de bij plaatsing bewaarde transformer-straal reconstrueert dit de anker-
     *  onafhankelijke camerastraal in ARCore's wereld. */
    val arFromTransformer: Transform3D? = null
)

/**
 * Tagfamilies die de OpenCV ArUco-detector aankan. tagStandard41h12 (AprilTag 3) ontbreekt
 * BEWUST: die familie legt een deel van de databits BUITEN de zwarte rand, en OpenCV's
 * quad-decoder (zwarte rand zoeken → bits bínnen de rand uitlezen) kan die per definitie niet
 * lezen — daarvoor is de originele AprilTag 3-bibliotheek nodig. Gebruik de klassieke families.
 */
enum class TagDictionaryOption(val label: String, val description: String, val openCvId: Int) {
    Apriltag36h11("tag36h11", "Standaard — klassieke AprilTag-familie, 587 id's", Objdetect.DICT_APRILTAG_36h11),
    Apriltag36h10("tag36h10", "Klassieke familie, 2320 id's, iets minder robuust", Objdetect.DICT_APRILTAG_36h10),
    Apriltag25h9("tag25h9", "Kleinere familie (35 id's), grover raster", Objdetect.DICT_APRILTAG_25h9),
    Apriltag16h5("tag16h5", "Kleinste familie (30 id's), alleen op korte afstand", Objdetect.DICT_APRILTAG_16h5);

    companion object {
        val DEFAULT = Apriltag36h11

        fun fromName(name: String?): TagDictionaryOption =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Pose-selectiestrategie (instelbaar via Instellingen > Tags). */
enum class TagPoseMode(val label: String, val description: String) {
    NearestTag(
        "Dichtstbijzijnde tag",
        "Standaard — pose van de best zichtbare tag; robuust bij handmatig gemeten tagposities"
    ),
    MultiTag(
        "Multi-tag",
        "Gezamenlijke pose over alle zichtbare tags — alleen nauwkeuriger als de onderlinge tagposities mm-exact kloppen"
    );

    companion object {
        val DEFAULT = NearestTag

        fun fromName(name: String?): TagPoseMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

class AprilTagDetector(
    dictionaryId: Int = Objdetect.DICT_APRILTAG_36h11
) {
    private var preferredLocalAnchorId: Int? = null

    /** Laatst gekozen (goede) pose per tag + tijdstip: de temporele prior waarmee de
     *  vlakke-pose-ambiguïteit per frame consistent wordt opgelost. Alleen benaderd vanaf de
     *  detector-executor (één thread). */
    private val recentPosePerTag = HashMap<Int, Pair<TransformerPose, Long>>()

    private val detector: ArucoDetector by lazy {
        check(OpenCvRuntime.ensureLoaded()) {
            OpenCvRuntime.lastError ?: "OpenCV kon niet geladen worden"
        }
        val parameters = DetectorParameters().apply {
            set_cornerRefinementMethod(Objdetect.CORNER_REFINE_APRILTAG)
            // Quad-detectie op VOLLE resolutie (geen decimatie): bij kleine of schuin bekeken
            // tags (bovenvlak!) is de hoeknauwkeurigheid bepalend — met decimatie 1.5 was de
            // pose daar al gauw gedegenereerd (reprojectie honderden px). Detectie draait om de
            // N frames op de achtergrond-executor, dus de extra rekentijd is acceptabel.
            set_aprilTagQuadDecimate(1.0f)
            set_aprilTagQuadSigma(0.0f)
            set_minMarkerPerimeterRate(0.02)
            set_maxMarkerPerimeterRate(4.0)
        }
        ArucoDetector(Objdetect.getPredefinedDictionary(dictionaryId), parameters)
    }

    fun detect(
        gray: Mat,
        knownMarkers: List<Marker>,
        cameraIntrinsics: CameraIntrinsics = approximateCameraIntrinsics(gray.width(), gray.height()),
        poseMode: TagPoseMode = TagPoseMode.DEFAULT
    ): AprilTagFrameResult {
        return runCatching {
            val corners = mutableListOf<Mat>()
            val rejected = mutableListOf<Mat>()
            val ids = Mat()
            detector.detectMarkers(gray, corners, ids, rejected)

            val detections = idsToDetections(ids, corners)
            detections.forEach { detection ->
                val match = knownMarkers.any { marker -> marker.id == detection.id }
                if (!match && ENABLE_VERBOSE_APRILTAG_LOGS) {
                    Log.v("AprilTagDetector", "Detection ID ${detection.id} not in known markers")
                }
            }
            val nowMillis = System.currentTimeMillis()
            recentPosePerTag.entries.removeAll { nowMillis - it.value.second > POSE_PRIOR_TTL_MILLIS }
            val poseEstimate = estimateTransformerPoseFromAprilTags(
                detections = detections,
                knownMarkers = knownMarkers,
                cameraIntrinsics = cameraIntrinsics,
                preferredLocalMarkerId = preferredLocalAnchorId,
                poseMode = poseMode,
                priorPosePerTag = recentPosePerTag.mapValues { it.value.first }
            )
            poseEstimate?.posePerTag?.forEach { (id, pose) ->
                recentPosePerTag[id] = pose to nowMillis
            }
            preferredLocalAnchorId = when {
                poseEstimate == null -> preferredLocalAnchorId
                poseEstimate.markerIds.size == 1 -> poseEstimate.markerIds.single()
                else -> null
            }
            val pose = poseEstimate?.pose
            corners.forEach { it.release() }
            rejected.forEach { it.release() }
            ids.release()

            AprilTagFrameResult(
                imageWidth = gray.width(),
                imageHeight = gray.height(),
                detections = detections,
                cameraIntrinsics = cameraIntrinsics,
                imageProjectionPose = pose,
                detectionsFresh = detections.isNotEmpty(),
                transformerPose = pose,
                freshImageProjectionPose = pose,
                knownMarkerCount = knownMarkers.size,
                poseMarkerCount = poseEstimate?.markerIds?.distinct()?.size ?: 0,
                poseMarkerIds = poseEstimate?.markerIds ?: emptyList(),
                trackingQualityPercent = trackingQuality(detections, pose),
                trackingStatus = if (pose != null) ArTrackingStatus.TagCalibration else ArTrackingStatus.NoPose,
                freshPosePerTag = poseEstimate?.posePerTag ?: emptyMap(),
                posePerTag = poseEstimate?.posePerTag ?: emptyMap(),
                detectionAgeMillis = if (detections.isNotEmpty()) 0L else Long.MAX_VALUE
            )
        }.getOrElse { error ->
            AprilTagFrameResult(
                imageWidth = gray.width(),
                imageHeight = gray.height(),
                errorMessage = error.message ?: "AprilTag detectie mislukt"
            )
        }
    }

    private fun idsToDetections(ids: Mat, corners: List<Mat>): List<AprilTagDetection> {
        if (ids.empty() || corners.isEmpty()) return emptyList()
        return (0 until ids.rows()).mapNotNull { index ->
            val id = ids.get(index, 0)?.firstOrNull()?.toInt() ?: return@mapNotNull null
            val cornerMat = corners.getOrNull(index) ?: return@mapNotNull null
            val raw = (0 until 4).mapNotNull { cornerIndex ->
                cornerMat.get(0, cornerIndex)?.let { values ->
                    AprilTagCorner(values[0].toFloat(), values[1].toFloat())
                }
            }
            if (raw.size != 4) return@mapNotNull null
            // OpenCV's APRILTAG-dictionaries staan 180° gedraaid t.o.v. de gangbare geprinte
            // tag36h11-afbeeldingen: hoek 0 van de detectie ligt op de GEPRINTE rechtsonder.
            // Twee posities doorschuiven geeft alle afnemers (solvePnP-pairing met
            // markerCornersInProjectFrame, cursor-homografie, overlays) de hoeken in geprinte
            // volgorde [linksboven, rechtsboven, rechtsonder, linksonder]. Een leesbaar (rechtop)
            // geplakte tag levert dan een correct georiënteerde pose — zonder deze correctie
            // moest de tag op de kop hangen om het AR-model te laten kloppen.
            val points = List(4) { i -> raw[(i + 2) % 4] }
            val center = AprilTagCorner(
                xPx = points.map { it.xPx }.average().toFloat(),
                yPx = points.map { it.yPx }.average().toFloat()
            )
            AprilTagDetection(id = id, cornersPx = points, centerPx = center)
        }
    }
}

data class AprilTagPoseEstimate(
    val pose: TransformerPose,
    val markerIds: List<Int>,
    val posePerTag: Map<Int, TransformerPose> = emptyMap()
)

fun estimateTransformerPoseFromAprilTags(
    detections: List<AprilTagDetection>,
    knownMarkers: List<Marker>,
    cameraIntrinsics: CameraIntrinsics,
    preferredLocalMarkerId: Int? = null,
    poseMode: TagPoseMode = TagPoseMode.DEFAULT,
    /** Recente pose per tag (vorig frame): kiest bij de vlakke-pose-ambiguïteit de temporeel
     *  consistente oplossing i.p.v. de gespiegelde. Leeg = geen voorkennis. */
    priorPosePerTag: Map<Int, TransformerPose> = emptyMap()
): AprilTagPoseEstimate? {
    val markersById = knownMarkers
        .filter { it.active }
        .associateBy { it.id }
    val knownDetections = detections.mapNotNull { detection ->
        markersById[detection.id]?.let { marker ->
            KnownAprilTagDetection(
                detection = detection,
                marker = marker,
                areaPx = polygonAreaPx(detection.cornersPx)
            )
        }
    }

    if (knownDetections.isEmpty()) {
        if (detections.isNotEmpty()) {
            PoseDecisionLogger.warn(
                key = "no-known|${detections.map { it.id }}",
                message = "pose=no-known-active detected=${detections.map { it.id }} known=${knownMarkers.knownMarkerLogSummary()}"
            )
        } else if (ENABLE_VERBOSE_APRILTAG_LOGS) {
            Log.v("AprilTagDetector", "No known marker pose: 0 matched points.")
        }
        return null
    }

    // Stap 1: bepaal de beste enkel-tag pose (dichtstbijzijnde tag = grootste oppervlak).
    // De opgeslagen rotatie blijft leidend; daarmee blijft het model bij single-tag AR stabiel
    // in dezelfde richting als het voorbereide tagvlak.
    val localCandidates = knownDetections.mapNotNull { known ->
        val estimate = solveSingleAprilTagPose(known, cameraIntrinsics, priorPosePerTag[known.marker.id])
            ?: solveKnownAprilTagPose(listOf(known), cameraIntrinsics)
            ?: return@mapNotNull null
        // Gedegenereerde fit (vlakke tag onder een scherende kijkhoek → reprojectie honderden px):
        // NIET als referentie gebruiken. Liever eerlijk "geen pose" met een hint dan een cursor
        // en overlay die wild verkeerd staan.
        if (estimate.pose.reprojectionErrorPx > MAX_USABLE_LOCAL_REPROJECTION_PX) {
            PoseDecisionLogger.info(
                key = "degenerate|${known.marker.id}",
                message = "pose=degenerate-drop tag=${known.marker.id} err=${estimate.pose.reprojectionErrorPx.shortPx()} q=${known.readabilityScore.shortScore()}"
            )
            return@mapNotNull null
        }
        LocalPoseCandidate(
            known = known,
            estimate = estimate,
            score = localReferenceScore(known, estimate.pose.reprojectionErrorPx)
        )
    }
    if (localCandidates.isEmpty()) {
        // Alle enkel-tag fits onbruikbaar (scherende hoek). Met ≥2 zichtbare tags kan de
        // gezamenlijke oplossing alsnog goed geconditioneerd zijn — probeer die voordat we opgeven.
        if (knownDetections.size >= 2) {
            val multi = solveKnownAprilTagPose(knownDetections, cameraIntrinsics)
            if (multi != null && multi.pose.reprojectionErrorPx <= MAX_STRONG_MULTI_TAG_REPROJECTION_PX) {
                return multi.copy(posePerTag = knownDetections.associate { it.marker.id to multi.pose })
            }
        }
        return null
    }
    val bestCandidate = localCandidates.maxBy { it.score }
    val preferredCandidate = preferredLocalMarkerId?.let { id ->
        localCandidates.firstOrNull { candidate -> candidate.known.marker.id == id }
    }
    val localCandidate = if (
        preferredCandidate != null &&
        bestCandidate.known.marker.id != preferredCandidate.known.marker.id &&
        bestCandidate.score <= preferredCandidate.score * LOCAL_ANCHOR_SWITCH_SCORE_RATIO
    ) {
        preferredCandidate
    } else {
        bestCandidate
    }

    // Per-tag poses: elke zichtbare tag heeft een individuele pose-schatting.
    // Wordt gebruikt zodat elke sensor geprojecteerd kan worden via zijn eigen referentietag,
    // ongeacht welke tag op dit moment de dominante pose levert.
    val posePerTag = localCandidates.associate { it.known.marker.id to it.estimate.pose }

    // Pose-modus "Multi-tag" (instelling): gezamenlijke solve over álle zichtbare bekende tags
    // als primair pad. Alleen zinvol als de onderlinge tagposities mm-exact zijn — de keuze ligt
    // bewust bij de gebruiker. Alle per-tag poses volgen dan dezelfde gezamenlijke pose, zodat
    // cursor, sensoren en STL-overlay consistent één frame gebruiken.
    if (poseMode == TagPoseMode.MultiTag && knownDetections.size >= 2) {
        val multiEstimate = solveKnownAprilTagPose(knownDetections, cameraIntrinsics)
        if (multiEstimate != null &&
            multiEstimate.pose.reprojectionErrorPx <= MAX_STRONG_MULTI_TAG_REPROJECTION_PX
        ) {
            PoseDecisionLogger.info(
                key = "multimode|${multiEstimate.markerIds}",
                message = "pose=multi-mode tags=${multiEstimate.markerIds} err=${multiEstimate.pose.reprojectionErrorPx.shortPx()}"
            )
            return multiEstimate.copy(
                posePerTag = knownDetections.associate { it.marker.id to multiEstimate.pose }
            )
        }
    }

    // Stap 2: als de tag met het hoogste gewicht (poseWeight) goed genoeg is, gebruik die direct.
    // poseWeight geeft de operator controle over welke tag de primaire referentie is. Een tag
    // met poseWeight=2 domineert over een gelijkwaardige tag met poseWeight=1, zodat sensoren
    // altijd in hetzelfde referentiekader worden opgeslagen en niet "schuiven" bij een tagwissel.
    if (localCandidate.estimate.pose.reprojectionErrorPx <= MAX_STRONG_MULTI_TAG_REPROJECTION_PX) {
        PoseDecisionLogger.info(
            key = "local|${localCandidate.known.marker.id}|${knownDetections.map { it.marker.id }}",
            message = "pose=nearest tag=${localCandidate.known.marker.id} w=${localCandidate.known.marker.poseWeight} visible=${knownDetections.map { it.marker.id }} err=${localCandidate.estimate.pose.reprojectionErrorPx.shortPx()} score=${localCandidate.score.shortScore()}"
        )
        return localCandidate.estimate.copy(posePerTag = posePerTag)
    }

    // Stap 3: enkel-tag is niet goed genoeg — probeer multi-tag als fallback.
    if (knownDetections.size >= 2) {
        val multiTagEstimate = solveKnownAprilTagPose(
            knownDetections = knownDetections,
            cameraIntrinsics = cameraIntrinsics
        )
        if (multiTagEstimate != null &&
            multiTagEstimate.pose.reprojectionErrorPx <= MAX_STRONG_MULTI_TAG_REPROJECTION_PX
        ) {
            PoseDecisionLogger.info(
                key = "multi|${multiTagEstimate.markerIds}",
                message = "pose=multi tags=${multiTagEstimate.markerIds} err=${multiTagEstimate.pose.reprojectionErrorPx.shortPx()}"
            )
            return multiTagEstimate.copy(posePerTag = posePerTag)
        }
        val consensusEstimate = solveConsensusAprilTagPose(
            knownDetections = knownDetections,
            cameraIntrinsics = cameraIntrinsics
        )
        if (consensusEstimate != null &&
            consensusEstimate.pose.reprojectionErrorPx < localCandidate.estimate.pose.reprojectionErrorPx
        ) {
            PoseDecisionLogger.info(
                key = "consensus|${consensusEstimate.markerIds}|${knownDetections.map { it.marker.id }}",
                message = "pose=consensus tags=${consensusEstimate.markerIds} visible=${knownDetections.map { it.marker.id }} err=${consensusEstimate.pose.reprojectionErrorPx.shortPx()}"
            )
            return consensusEstimate.copy(posePerTag = posePerTag)
        }
    }

    // Stap 4: gebruik toch de beste enkel-tag als geen multi-tag beter is.
    return localCandidate.estimate.copy(posePerTag = posePerTag).also { estimate ->
        PoseDecisionLogger.info(
            key = "local|${localCandidate.known.marker.id}|${knownDetections.map { it.marker.id }}",
            message = "pose=local-fallback tag=${localCandidate.known.marker.id} w=${localCandidate.known.marker.poseWeight} visible=${knownDetections.map { it.marker.id }} err=${estimate.pose.reprojectionErrorPx.shortPx()} score=${localCandidate.score.shortScore()} q=${localCandidate.known.readabilityScore.shortScore()}"
        )
    }
}

private data class KnownAprilTagDetection(
    val detection: AprilTagDetection,
    val marker: Marker,
    val areaPx: Double
) {
    private val edgeLengthsPx: List<Double>
        get() = detection.cornersPx.indices.map { index ->
            val start = detection.cornersPx[index]
            val end = detection.cornersPx[(index + 1) % detection.cornersPx.size]
            hypot(
                (end.xPx - start.xPx).toDouble(),
                (end.yPx - start.yPx).toDouble()
            )
        }

    val readabilityScore: Double
        get() {
            val edges = edgeLengthsPx
            val minEdge = edges.minOrNull() ?: 0.0
            val maxEdge = edges.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
            val edgeBalance = (minEdge / maxEdge).coerceIn(0.20, 1.0)
            val averageEdge = edges.average().takeIf { it.isFinite() } ?: minEdge
            val areaSidePx = kotlin.math.sqrt(areaPx.coerceAtLeast(0.0))
            val edgeSize = (minEdge / REFERENCE_MIN_EDGE_PX).coerceIn(0.20, 1.0)
            val areaSize = (areaSidePx / REFERENCE_AREA_SIDE_PX).coerceIn(0.20, 1.0)
            val consistency = (minEdge / averageEdge.coerceAtLeast(1.0)).coerceIn(0.20, 1.0)
            return edgeSize *
                areaSize *
                edgeBalance * edgeBalance * edgeBalance *
                consistency * consistency
        }

    val weightedReadabilityScore: Double
        get() = readabilityScore
}

private data class LocalPoseCandidate(
    val known: KnownAprilTagDetection,
    val estimate: AprilTagPoseEstimate,
    val score: Double
)

private data class PoseConsensusCandidate(
    val estimate: AprilTagPoseEstimate,
    val inlierCount: Int,
    val totalAreaPx: Double
)

private fun solveConsensusAprilTagPose(
    knownDetections: List<KnownAprilTagDetection>,
    cameraIntrinsics: CameraIntrinsics
): AprilTagPoseEstimate? {
    if (knownDetections.size < 2) return null
    val candidates = knownDetections.mapNotNull { anchor ->
        val anchorEstimate = solveKnownAprilTagPose(
            knownDetections = listOf(anchor),
            cameraIntrinsics = cameraIntrinsics
        ) ?: return@mapNotNull null
        val inliers = knownDetections.filter { known ->
            reprojectionErrorForPose(
                pose = anchorEstimate.pose,
                knownDetections = listOf(known),
                cameraIntrinsics = cameraIntrinsics
            ) <= MAX_CONSENSUS_INLIER_REPROJECTION_PX
        }
        if (inliers.size < 2) return@mapNotNull null
        val refit = solveKnownAprilTagPose(
            knownDetections = inliers,
            cameraIntrinsics = cameraIntrinsics
        ) ?: return@mapNotNull null
        if (refit.pose.reprojectionErrorPx > MAX_STRONG_MULTI_TAG_REPROJECTION_PX) {
            return@mapNotNull null
        }
        PoseConsensusCandidate(
            estimate = refit,
            inlierCount = inliers.size,
            totalAreaPx = inliers.sumOf { it.weightedReadabilityScore }
        )
    }
    return candidates
        .sortedWith(
            compareByDescending<PoseConsensusCandidate> { it.inlierCount }
                .thenBy { it.estimate.pose.reprojectionErrorPx }
                .thenByDescending { it.totalAreaPx }
        )
        .firstOrNull()
        ?.estimate
}

/**
 * Enkel-tag pose met expliciete behandeling van de vlakke-pose-ambiguïteit. Eén vierkante tag
 * heeft ALTIJD twee wiskundig bijna gelijkwaardige oplossingen (de echte en de "gespiegelde");
 * welke de laagste reprojectiefout heeft wisselt per frame door subpixel-hoekruis. Wie blind de
 * laagste fout kiest, ziet de pose daardoor regelmatig 60–120° omklappen — overlays "schieten
 * alle kanten op" terwijl de detectie zelf perfect stil staat.
 *
 * Oplossing: solvePnPGeneric (IPPE) levert BEIDE kandidaten; zonder voorkennis wint de laagste
 * reprojectiefout, maar mét een recente eerdere pose van dezelfde tag wint de kandidaat die daar
 * qua rotatie bij past — tenzij die echt niet meer op de hoeken past (camera daadwerkelijk
 * omgelopen). De spiegel ligt typisch >60° van de echte oplossing, dus de keuze is eenduidig.
 */
private fun solveSingleAprilTagPose(
    known: KnownAprilTagDetection,
    cameraIntrinsics: CameraIntrinsics,
    priorPose: TransformerPose?
): AprilTagPoseEstimate? {
    val objectPoints = mutableListOf<Point3>()
    val imagePoints = mutableListOf<Point>()
    markerCornersInProjectFrame(known.marker).zip(known.detection.cornersPx).forEach { (objectPoint, imagePoint) ->
        objectPoints += Point3(objectPoint.x, objectPoint.y, objectPoint.z)
        imagePoints += Point(imagePoint.xPx.toDouble(), imagePoint.yPx.toDouble())
    }
    if (objectPoints.size < 4) return null

    val cameraMatrix = cameraIntrinsics.toOpenCvCameraMatrix()
    val distCoeffs = MatOfDouble(0.0, 0.0, 0.0, 0.0)
    val objMat = MatOfPoint3f(*objectPoints.toTypedArray())
    val imgMat = MatOfPoint2f(*imagePoints.toTypedArray())
    val rvecs = ArrayList<Mat>()
    val tvecs = ArrayList<Mat>()
    val solutionCount = runCatching {
        // De 4 hoeken van één tag zijn per constructie coplanair → generiek IPPE mag altijd.
        Calib3d.solvePnPGeneric(objMat, imgMat, cameraMatrix, distCoeffs, rvecs, tvecs, false, Calib3d.SOLVEPNP_IPPE)
    }.getOrDefault(0)

    val ippeCandidates = (0 until minOf(solutionCount, rvecs.size, tvecs.size)).mapNotNull { index ->
        val rvec = rvecs[index]
        val tvec = tvecs[index]
        val error = reprojectionErrorMm(
            objectPoints = objectPoints,
            imagePoints = imagePoints,
            cameraMatrix = cameraMatrix,
            distCoeffs = distCoeffs,
            rvec = rvec,
            tvec = tvec
        )
        if (!error.isFinite()) return@mapNotNull null
        TransformerPose(
            translationMm = FloatArray(3) { i -> tvec.get(i, 0)?.firstOrNull()?.toFloat() ?: 0f },
            rotationVector = FloatArray(3) { i -> rvec.get(i, 0)?.firstOrNull()?.toFloat() ?: 0f },
            reprojectionErrorPx = error
        )
    }

    val chosenIppe = ippeCandidates
        .takeIf { it.isNotEmpty() }
        ?.let { chooseSingleTagSolution(it, priorPose) }
    var chosen = chosenIppe
    val sqpnpCandidate = if (
        chosenIppe == null ||
        chosenIppe.reprojectionErrorPx > LOCAL_POSE_RETRY_REPROJECTION_PX
    ) {
        solveSqPnpPose(
            objectPoints = objectPoints,
            imagePoints = imagePoints,
            cameraMatrix = cameraMatrix,
            distCoeffs = distCoeffs,
            objMat = objMat,
            imgMat = imgMat
        )
    } else {
        null
    }
    if (sqpnpCandidate != null && (chosen == null || sqpnpCandidate.reprojectionErrorPx < chosen.reprojectionErrorPx)) {
        chosen = sqpnpCandidate
    }
    if (sqpnpCandidate != null || chosenIppe == null) {
        val chosenMethod = when {
            chosen == null -> "none"
            chosen === sqpnpCandidate -> "sqpnp"
            else -> "ippe"
        }
        PoseDecisionLogger.info(
            key = "single-retry|${known.marker.id}|$chosenMethod",
            message = "pose=single-retry tag=${known.marker.id} ippeErr=${chosenIppe?.reprojectionErrorPx.shortPxOrDash()} sqpnpErr=${sqpnpCandidate?.reprojectionErrorPx.shortPxOrDash()} chosen=$chosenMethod q=${known.readabilityScore.shortScore()}"
        )
    }

    cameraMatrix.release()
    distCoeffs.release()
    objMat.release()
    imgMat.release()
    rvecs.forEach { it.release() }
    tvecs.forEach { it.release() }

    val chosenPose = chosen ?: return null
    return AprilTagPoseEstimate(
        pose = chosenPose,
        markerIds = listOf(known.marker.id)
    )
}

/** Laagste reprojectiefout, tenzij een recente pose de andere (temporeel consistente) kandidaat
 *  aanwijst en die nog acceptabel op de hoeken past. */
private fun chooseSingleTagSolution(
    candidates: List<TransformerPose>,
    priorPose: TransformerPose?
): TransformerPose {
    val byError = candidates.minBy { it.reprojectionErrorPx }
    if (priorPose == null || candidates.size < 2) return byError
    val byPrior = candidates.minBy { rotationAngleDegBetween(it, priorPose) }
    if (byPrior === byError) return byError
    // De consistente kandidaat mag iets slechter passen (ruis), maar niet wezenlijk slechter:
    // dan is de camera echt van kant gewisseld en is de "spiegel" nu de juiste.
    return if (byPrior.reprojectionErrorPx <= byError.reprojectionErrorPx * 4f + 2f) byPrior else byError
}

/** Rotatieverschil (graden) tussen twee poses — spiegeloplossingen liggen typisch >60° uit elkaar. */
private fun rotationAngleDegBetween(a: TransformerPose, b: TransformerPose): Double =
    Transform3D.cameraCvFromTransformerPose(a)
        .rotationAngleDegreesTo(Transform3D.cameraCvFromTransformerPose(b))

private fun solveSqPnpPose(
    objectPoints: List<Point3>,
    imagePoints: List<Point>,
    cameraMatrix: Mat,
    distCoeffs: MatOfDouble,
    objMat: MatOfPoint3f,
    imgMat: MatOfPoint2f
): TransformerPose? {
    val rvec = Mat()
    val tvec = Mat()
    return try {
        val ok = runCatching {
            Calib3d.solvePnP(
                objMat,
                imgMat,
                cameraMatrix,
                distCoeffs,
                rvec,
                tvec,
                false,
                Calib3d.SOLVEPNP_SQPNP
            )
        }.getOrDefault(false)
        if (!ok) return null
        val error = reprojectionErrorMm(
            objectPoints = objectPoints,
            imagePoints = imagePoints,
            cameraMatrix = cameraMatrix,
            distCoeffs = distCoeffs,
            rvec = rvec,
            tvec = tvec
        )
        if (!error.isFinite()) return null
        TransformerPose(
            translationMm = FloatArray(3) { i -> tvec.get(i, 0)?.firstOrNull()?.toFloat() ?: 0f },
            rotationVector = FloatArray(3) { i -> rvec.get(i, 0)?.firstOrNull()?.toFloat() ?: 0f },
            reprojectionErrorPx = error
        )
    } finally {
        rvec.release()
        tvec.release()
    }
}

private fun solveKnownAprilTagPose(
    knownDetections: List<KnownAprilTagDetection>,
    cameraIntrinsics: CameraIntrinsics
): AprilTagPoseEstimate? {
    val objectPoints = mutableListOf<Point3>()
    val imagePoints = mutableListOf<Point>()
    knownDetections.forEach { known ->
        markerCornersInProjectFrame(known.marker).zip(known.detection.cornersPx).forEach { (objectPoint, imagePoint) ->
            objectPoints += Point3(objectPoint.x, objectPoint.y, objectPoint.z)
            imagePoints += Point(imagePoint.xPx.toDouble(), imagePoint.yPx.toDouble())
        }
    }

    if (objectPoints.size < 4) return null

    val cameraMatrix = cameraIntrinsics.toOpenCvCameraMatrix()
    val distCoeffs = MatOfDouble(0.0, 0.0, 0.0, 0.0)
    val rvec = Mat()
    val tvec = Mat()
    val method = solvePnpMethodFor(objectPoints)
    if (ENABLE_VERBOSE_APRILTAG_LOGS) {
        Log.v("AprilTagDetector", "solvePnP tags=${knownDetections.map { it.marker.id }} method=$method")
    }
    val ok = Calib3d.solvePnP(
        MatOfPoint3f(*objectPoints.toTypedArray()),
        MatOfPoint2f(*imagePoints.toTypedArray()),
        cameraMatrix,
        distCoeffs,
        rvec,
        tvec,
        false,
        method
    )

    if (!ok) {
        Log.e("AprilTagDetector", "solvePnP failed")
        cameraMatrix.release()
        distCoeffs.release()
        rvec.release()
        tvec.release()
        return null
    }

    var error = reprojectionErrorMm(
        objectPoints = objectPoints,
        imagePoints = imagePoints,
        cameraMatrix = cameraMatrix,
        distCoeffs = distCoeffs,
        rvec = rvec,
        tvec = tvec
    )
    var bestRvec = rvec
    var bestTvec = tvec
    val baseError = error
    var rvecRetry: Mat? = null
    var tvecRetry: Mat? = null
    // Tweede kans bij een onbruikbare fit: een vlakke tag onder een scherende kijkhoek (typisch
    // een tag op het BOVENVLAK gezien vanaf de grond) geeft een gedegenereerde homografie en
    // daarmee een wild verkeerde IPPE-oplossing (reprojectie honderden px). SQPnP is een globale
    // oplosser zonder dat lokale minimum — houd de beste van de twee.
    if (error > LOCAL_POSE_RETRY_REPROJECTION_PX) {
        rvecRetry = Mat()
        tvecRetry = Mat()
        val retryOk = runCatching {
            Calib3d.solvePnP(
                MatOfPoint3f(*objectPoints.toTypedArray()),
                MatOfPoint2f(*imagePoints.toTypedArray()),
                cameraMatrix,
                distCoeffs,
                rvecRetry,
                tvecRetry,
                false,
                Calib3d.SOLVEPNP_SQPNP
            )
        }.getOrDefault(false)
        if (retryOk) {
            val retryError = reprojectionErrorMm(
                objectPoints = objectPoints,
                imagePoints = imagePoints,
                cameraMatrix = cameraMatrix,
                distCoeffs = distCoeffs,
                rvec = rvecRetry,
                tvec = tvecRetry
            )
            if (retryError < error) {
                error = retryError
                bestRvec = rvecRetry
                bestTvec = tvecRetry
            }
            PoseDecisionLogger.info(
                key = "pnp-retry|${knownDetections.map { it.marker.id }}|${if (retryError < baseError) "sqpnp" else "base"}",
                message = "pose=pnp-retry tags=${knownDetections.map { it.marker.id }} baseErr=${baseError.shortPx()} sqpnpErr=${retryError.shortPx()} chosen=${if (retryError < baseError) "sqpnp" else "base"}"
            )
        }
    }
    val translation = FloatArray(3) { index -> bestTvec.get(index, 0)?.firstOrNull()?.toFloat() ?: 0f }
    val rotation = FloatArray(3) { index -> bestRvec.get(index, 0)?.firstOrNull()?.toFloat() ?: 0f }

    cameraMatrix.release()
    distCoeffs.release()
    rvec.release()
    tvec.release()
    rvecRetry?.release()
    tvecRetry?.release()

    return AprilTagPoseEstimate(
        pose = TransformerPose(
            translationMm = translation,
            rotationVector = rotation,
            reprojectionErrorPx = error
        ),
        markerIds = knownDetections.map { it.marker.id }.distinct()
    )
}

private fun reprojectionErrorForPose(
    pose: TransformerPose,
    knownDetections: List<KnownAprilTagDetection>,
    cameraIntrinsics: CameraIntrinsics
): Float {
    val objectPoints = mutableListOf<Point3>()
    val imagePoints = mutableListOf<Point>()
    knownDetections.forEach { known ->
        markerCornersInProjectFrame(known.marker).zip(known.detection.cornersPx).forEach { (objectPoint, imagePoint) ->
            objectPoints += Point3(objectPoint.x, objectPoint.y, objectPoint.z)
            imagePoints += Point(imagePoint.xPx.toDouble(), imagePoint.yPx.toDouble())
        }
    }
    if (objectPoints.isEmpty()) return Float.POSITIVE_INFINITY
    val rvec = Mat(3, 1, CvType.CV_64F).apply {
        put(0, 0, pose.rotationVector[0].toDouble())
        put(1, 0, pose.rotationVector[1].toDouble())
        put(2, 0, pose.rotationVector[2].toDouble())
    }
    val tvec = Mat(3, 1, CvType.CV_64F).apply {
        put(0, 0, pose.translationMm[0].toDouble())
        put(1, 0, pose.translationMm[1].toDouble())
        put(2, 0, pose.translationMm[2].toDouble())
    }
    val cameraMatrix = cameraIntrinsics.toOpenCvCameraMatrix()
    val distCoeffs = MatOfDouble(0.0, 0.0, 0.0, 0.0)
    val error = reprojectionErrorMm(
        objectPoints = objectPoints,
        imagePoints = imagePoints,
        cameraMatrix = cameraMatrix,
        distCoeffs = distCoeffs,
        rvec = rvec,
        tvec = tvec
    )
    rvec.release()
    tvec.release()
    cameraMatrix.release()
    distCoeffs.release()
    return error
}

private fun polygonAreaPx(points: List<AprilTagCorner>): Double {
    if (points.size < 3) return 0.0
    return kotlin.math.abs(
        points.indices.sumOf { index ->
            val start = points[index]
            val end = points[(index + 1) % points.size]
            (start.xPx * end.yPx - end.xPx * start.yPx).toDouble()
        }
    ) / 2.0
}

private fun localReferenceScore(
    known: KnownAprilTagDetection,
    reprojectionErrorPx: Float
): Double {
    // Pose-selectie is puur op kwaliteit: afstand (oppervlak in beeld) en reprojectie-nauwkeurigheid.
    // poseWeight beïnvloedt de selectie NIET meer — sensor-stabiliteit wordt afgehandeld via
    // referenceTagId per sensor, zodat tags vrij kunnen wisselen zonder dat sensoren verschuiven.
    val reprojectionQuality = 1.0 / (1.0 + reprojectionErrorPx.coerceAtLeast(0f).toDouble())
    return known.weightedReadabilityScore *
        reprojectionQuality *
        reprojectionQuality *
        reprojectionQuality
}

private fun solvePnpMethodFor(objectPoints: List<Point3>): Int =
    if (objectPoints.areCoplanar()) {
        Calib3d.SOLVEPNP_IPPE
    } else {
        Calib3d.SOLVEPNP_ITERATIVE
    }

private fun List<Point3>.areCoplanar(): Boolean {
    if (isEmpty()) return false
    fun sameAxis(value: (Point3) -> Double): Boolean {
        val first = value(first())
        return all { kotlin.math.abs(value(it) - first) < 1e-3 }
    }
    return sameAxis { it.x } || sameAxis { it.y } || sameAxis { it.z }
}

fun approximateCameraIntrinsics(width: Int, height: Int): CameraIntrinsics {
    // Assumes 70° diagonal FOV (half-angle 35°), typical for modern smartphone cameras.
    // max*1.2 overschatte de brandpuntsafstand ~46%, waardoor IPPE de verkeerde pose koos.
    val diagonal = sqrt((width.toLong() * width + height.toLong() * height).toDouble()).toFloat()
    val focal = diagonal / 2f / tan(35.0 * PI / 180.0).toFloat()
    return CameraIntrinsics(
        fx = focal,
        fy = focal,
        cx = width / 2f,
        cy = height / 2f
    )
}

/**
 * Berekent de 3D-wereldpositie van een willekeurig pixel door een straal vanuit de camera
 * door dat pixel te schieten en het snijpunt met de trafo-box te bepalen.
 *
 * Gebruik: wanneer een NIEUWE tag gedetecteerd wordt terwijl de cameraPose nog afkomstig is
 * van een andere, reeds geregistreerde tag. Door het tagmiddelpunt (in beeldcoördinaten) te
 * gebruiken in plaats van het beeldmidden, zit de nieuwe tag automatisch in hetzelfde
 * coördinatenstelsel — ongeacht handmatige meetfouten.
 *
 * Geeft null terug als er geen actieve pose is of de straal de trafo-box niet raakt.
 */
fun estimateSurfaceAtPixel(
    result: AprilTagFrameResult,
    pixelX: Float,
    pixelY: Float,
    dimensionsMm: MmPosition
): MmPosition? {
    val pose = result.imageProjectionPose ?: result.transformerPose ?: return null
    if (!OpenCvRuntime.ensureLoaded()) return null

    val intrinsics = result.cameraIntrinsics
        ?: approximateCameraIntrinsics(result.imageWidth, result.imageHeight)

    val rvec = Mat(3, 1, CvType.CV_64F).apply {
        put(0, 0, pose.rotationVector[0].toDouble())
        put(1, 0, pose.rotationVector[1].toDouble())
        put(2, 0, pose.rotationVector[2].toDouble())
    }
    val rotation = Mat()
    Calib3d.Rodrigues(rvec, rotation)

    val t = pose.translationMm.map { it.toDouble() }
    val r = Array(3) { row ->
        DoubleArray(3) { col -> rotation.get(row, col)?.firstOrNull() ?: 0.0 }
    }

    // Cameraoorsprong in wereldcoördinaten: C = -R^T * t
    val cameraOriginWorld = DoubleArray(3) { col ->
        -(r[0][col] * t[0] + r[1][col] * t[1] + r[2][col] * t[2])
    }

    // Straalrichting voor het opgegeven pixel in cameracoördinaten, daarna omgezet naar wereld
    // d_cam = [(px - cx)/fx, (py - cy)/fy, 1]  →  d_world = R^T * d_cam
    val dCamX = (pixelX - intrinsics.cx) / intrinsics.fx
    val dCamY = (pixelY - intrinsics.cy) / intrinsics.fy
    val rayWorld = DoubleArray(3) { col ->
        r[0][col] * dCamX.toDouble() + r[1][col] * dCamY.toDouble() + r[2][col]
    }
    val len = sqrt(rayWorld[0] * rayWorld[0] + rayWorld[1] * rayWorld[1] + rayWorld[2] * rayWorld[2])

    rvec.release()
    rotation.release()

    if (len < 1e-9) return null
    val ray = RayMm(
        origin = cameraOriginWorld,
        direction = DoubleArray(3) { rayWorld[it] / len }
    )
    return intersectTransformerBox(ray, dimensionsMm)?.position
}

fun estimateCursorOnReferenceSurface(
    result: AprilTagFrameResult,
    dimensionsMm: MmPosition,
    knownMarkers: List<Marker>,
    cursorNdcX: Double = 0.0,
    cursorNdcY: Double = 0.0,
    placementReferenceMarker: Marker? = null
): PlaneHit? {
    val ray = cameraRayInTransformer(result, cursorNdcX, cursorNdcY) ?: return null
    val markersById = knownMarkers.associateBy { it.id }
    val candidateMarkers = (
        result.poseMarkerIds +
            result.detections.map { it.id } +
            result.screenDetections.map { it.id }
        )
        .distinct()
        .mapNotNull { id -> markersById[id] }
    val markerPlaneHits = candidateMarkers
        .mapNotNull { marker -> intersectMarkerReferencePlane(ray, marker, dimensionsMm) }
        .sortedBy { it.distanceAlongRay }
    markerPlaneHits.firstOrNull { it.hit.insideTransformerBox }?.let { return it.hit }
    markerPlaneHits.firstOrNull()?.let { return it.hit }
    // Geen verse/zichtbare tag meer, maar ARCore levert nog de straal: houd de cursor op het vlak
    // van de laatst gebruikte plaatsingstag i.p.v. blind met de hele trafo-box te snijden.
    if (placementReferenceMarker != null) {
        intersectMarkerReferencePlane(ray, placementReferenceMarker, dimensionsMm)?.let { return it.hit }
    }
    return intersectTransformerBox(ray, dimensionsMm)
}

/**
 * Straal-replay-driftcorrectie: herprojecteert de bij plaatsing bewaarde camerastraal op het nu
 * (correct) verankerde frame en geeft de bijgewerkte box-positie. [rayAtPlacement] staat in het
 * transformerframe van het plaatsmoment; met [anchorAtPlacement] (transformer→ARCore-wereld toen)
 * en [anchorNow] (idem nu) wordt de anker-onafhankelijke wereldstraal opnieuw in het huidige frame
 * uitgedrukt en gesneden met het referentievlak van de tag (anders de trafo-box). Null = geen snit.
 */
fun reprojectPlacementRay(
    rayAtPlacement: RayMm,
    anchorAtPlacement: Transform3D,
    anchorNow: Transform3D,
    dimensionsMm: MmPosition,
    referenceMarker: Marker?
): MmPosition? {
    // transformer_nu ← transformer_plaatsing, via de wereld: anchorNow⁻¹ ∘ anchorAtPlacement.
    val rebase = anchorNow.inverseRigid() * anchorAtPlacement
    val origin = rebase.transformPoint(rayAtPlacement.origin)
    val tip = rebase.transformPoint(
        doubleArrayOf(
            rayAtPlacement.origin[0] + rayAtPlacement.direction[0],
            rayAtPlacement.origin[1] + rayAtPlacement.direction[1],
            rayAtPlacement.origin[2] + rayAtPlacement.direction[2]
        )
    )
    val dir = doubleArrayOf(tip[0] - origin[0], tip[1] - origin[1], tip[2] - origin[2])
    val len = sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2])
    if (len < 1e-9) return null
    val ray = RayMm(origin = origin, direction = doubleArrayOf(dir[0] / len, dir[1] / len, dir[2] / len))
    if (referenceMarker != null) {
        intersectMarkerReferencePlane(ray, referenceMarker, dimensionsMm)?.let { return it.hit.position }
    }
    return intersectTransformerBox(ray, dimensionsMm)?.position
}

fun cameraRayInTransformer(
    result: AprilTagFrameResult,
    ndcX: Double = 0.0,
    ndcY: Double = 0.0
): RayMm? {
    result.displayProjection?.rayInTransformer(ndcX, ndcY)?.let { ray ->
        return ray
    }
    val pose = result.imageProjectionPose ?: result.transformerPose ?: return null
    val cameraCvFromTransformer = Transform3D.cameraCvFromTransformerPose(pose)
    val transformerFromCameraCv = cameraCvFromTransformer.inverseRigid()
    val origin = transformerFromCameraCv.translation()
    val forward = transformerFromCameraCv.forwardAxis()
    val intrinsics = result.cameraIntrinsics
    if ((ndcX == 0.0 && ndcY == 0.0) || intrinsics == null || intrinsics.fx <= 0f || intrinsics.fy <= 0f) {
        return RayMm(origin = origin, direction = forward)
    }
    // Geen displayProjection (tag-pose pad): bouw de off-center straal via de camera-intrinsics.
    // LET OP: het ARCore-camerabeeld staat in SENSORORIËNTATIE en is t.o.v. het scherm meestal
    // 90° gedraaid. Scherm-NDC is dus NIET gelijk aan beeld-NDC. Zet het schermpunt daarom met
    // de inverse van imageToViewMapper om naar een echte beeldpixel — de exacte inverse van hoe
    // sensoren via projectPointToImage + mapper.map() op het scherm getekend worden. Daarna
    // d_cam = [(px-cx)/fx, (py-cy)/fy, 1] met de CV-camera-assen in het transformerframe:
    // rightAxis (=+X), upAxis (=+Y, omlaag in beeld) en forward (=+Z).
    val mapper = result.imageToViewMapper
    val imagePoint = if (mapper != null && mapper.displayWidth > 0 && mapper.displayHeight > 0) {
        mapper.unmap(
            AprilTagCorner(
                xPx = ((ndcX * 0.5 + 0.5) * mapper.displayWidth).toFloat(),
                yPx = ((0.5 - ndcY * 0.5) * mapper.displayHeight).toFloat()
            )
        )
    } else {
        null
    }
    val cvX: Double
    val cvY: Double
    if (imagePoint != null) {
        cvX = (imagePoint.xPx - intrinsics.cx).toDouble() / intrinsics.fx
        cvY = (imagePoint.yPx - intrinsics.cy).toDouble() / intrinsics.fy
    } else {
        // Fallback zonder mapper (bv. tests/oude resultaten): benadering die aanneemt dat het
        // beeld al display-georiënteerd is (+ndcX = rechts, +ndcY = omhoog; beeld-Y omlaag).
        cvX = ndcX * (intrinsics.cx / intrinsics.fx)
        cvY = -ndcY * (intrinsics.cy / intrinsics.fy)
    }
    val right = transformerFromCameraCv.rightAxis()
    val down = transformerFromCameraCv.upAxis()
    val dir = doubleArrayOf(
        right[0] * cvX + down[0] * cvY + forward[0],
        right[1] * cvX + down[1] * cvY + forward[1],
        right[2] * cvX + down[2] * cvY + forward[2]
    )
    val len = kotlin.math.sqrt(dir[0] * dir[0] + dir[1] * dir[1] + dir[2] * dir[2])
    val direction = if (len > 1e-9) {
        doubleArrayOf(dir[0] / len, dir[1] / len, dir[2] / len)
    } else {
        forward
    }
    return RayMm(origin = origin, direction = direction)
}

private data class MarkerPlaneHit(
    val hit: PlaneHit,
    val distanceAlongRay: Double
)

private fun intersectMarkerReferencePlane(
    ray: RayMm,
    marker: Marker,
    dimensionsMm: MmPosition
): MarkerPlaneHit? {
    val corners = markerCornersInProjectFrame(marker)
    if (corners.size < 4) return null
    val origin = doubleArrayOf(corners[0].x, corners[0].y, corners[0].z)
    val edgeA = doubleArrayOf(
        corners[1].x - corners[0].x,
        corners[1].y - corners[0].y,
        corners[1].z - corners[0].z
    )
    val edgeB = doubleArrayOf(
        corners[3].x - corners[0].x,
        corners[3].y - corners[0].y,
        corners[3].z - corners[0].z
    )
    val normal = crossRaw(edgeA, edgeB)
    val denom = dotRaw(normal, ray.direction)
    if (kotlin.math.abs(denom) < 1e-6) return null
    val distanceAlongRay = dotRaw(
        normal,
        doubleArrayOf(
            origin[0] - ray.origin[0],
            origin[1] - ray.origin[1],
            origin[2] - ray.origin[2]
        )
    ) / denom
    if (distanceAlongRay <= 0.0) return null
    val point = DoubleArray(3) { index ->
        ray.origin[index] + distanceAlongRay * ray.direction[index]
    }
    val position = MmPosition(
        x = point[0].roundToInt(),
        y = point[1].roundToInt(),
        z = point[2].roundToInt()
    )
    return MarkerPlaneHit(
        hit = PlaneHit(
            position = position,
            insideTransformerBox = position.x in 0..dimensionsMm.x &&
                position.y in 0..dimensionsMm.y &&
                position.z in 0..dimensionsMm.z
        ),
        distanceAlongRay = distanceAlongRay
    )
}

private fun crossRaw(a: DoubleArray, b: DoubleArray): DoubleArray =
    doubleArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0]
    )

private fun dotRaw(a: DoubleArray, b: DoubleArray): Double =
    a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

fun projectPointToImage(
    point: ProjectPointMm,
    result: AprilTagFrameResult
): AprilTagCorner? {
    val pose = result.imageProjectionPose ?: result.transformerPose ?: return null
    if (result.imageWidth <= 0 || result.imageHeight <= 0) return null
    if (!OpenCvRuntime.ensureLoaded()) return null

    val rvec = Mat(3, 1, CvType.CV_64F).apply {
        put(0, 0, pose.rotationVector[0].toDouble())
        put(1, 0, pose.rotationVector[1].toDouble())
        put(2, 0, pose.rotationVector[2].toDouble())
    }
    val tvec = Mat(3, 1, CvType.CV_64F).apply {
        put(0, 0, pose.translationMm[0].toDouble())
        put(1, 0, pose.translationMm[1].toDouble())
        put(2, 0, pose.translationMm[2].toDouble())
    }
    val intrinsics = result.cameraIntrinsics ?: approximateCameraIntrinsics(
        result.imageWidth,
        result.imageHeight
    )
    val cameraMatrix = intrinsics.toOpenCvCameraMatrix()
    val distCoeffs = MatOfDouble(0.0, 0.0, 0.0, 0.0)
    val projected = MatOfPoint2f()
    Calib3d.projectPoints(
        MatOfPoint3f(Point3(point.x, point.y, point.z)),
        rvec,
        tvec,
        cameraMatrix,
        distCoeffs,
        projected
    )
    val point = projected.toArray().firstOrNull()

    rvec.release()
    tvec.release()
    cameraMatrix.release()
    distCoeffs.release()
    projected.release()

    return point?.let { AprilTagCorner(it.x.toFloat(), it.y.toFloat()) }
}

private fun CameraIntrinsics.toOpenCvCameraMatrix(): Mat =
    Mat.eye(3, 3, CvType.CV_64F).also { matrix ->
        matrix.put(0, 0, fx.toDouble())
        matrix.put(1, 1, fy.toDouble())
        matrix.put(0, 2, cx.toDouble())
        matrix.put(1, 2, cy.toDouble())
    }

private fun reprojectionErrorMm(
    objectPoints: List<Point3>,
    imagePoints: List<Point>,
    cameraMatrix: Mat,
    distCoeffs: MatOfDouble,
    rvec: Mat,
    tvec: Mat
): Float {
    val projected = MatOfPoint2f()
    Calib3d.projectPoints(
        MatOfPoint3f(*objectPoints.toTypedArray()),
        rvec,
        tvec,
        cameraMatrix,
        distCoeffs,
        projected
    )
    val projectedPoints = projected.toArray()
    val error = projectedPoints.zip(imagePoints).map { (projectedPoint, imagePoint) ->
        hypot(projectedPoint.x - imagePoint.x, projectedPoint.y - imagePoint.y)
    }.average().toFloat()
    projected.release()
    return error
}

private fun trackingQuality(
    detections: List<AprilTagDetection>,
    pose: TransformerPose?
): Int =
    when {
        detections.isEmpty() -> 0
        pose == null -> (detections.size * 20).coerceAtMost(60)
        pose.reprojectionErrorPx <= 2f -> 95
        pose.reprojectionErrorPx <= 5f -> 85
        pose.reprojectionErrorPx <= 10f -> 70
        else -> 50
    }

private const val ENABLE_VERBOSE_APRILTAG_LOGS = false
private const val MAX_STRONG_MULTI_TAG_REPROJECTION_PX = 3.0f

/** Boven deze enkel-tag reprojectiefout proberen we een tweede oplosser (SQPnP). */
private const val LOCAL_POSE_RETRY_REPROJECTION_PX = 25.0f

/** Zo lang blijft de laatst gekozen tag-pose bruikbaar als temporele prior voor de
 *  ambiguïteits-keuze; daarna (tag lang uit beeld) beslist de reprojectiefout weer alleen. */
private const val POSE_PRIOR_TTL_MILLIS = 1_500L

/** Boven deze fout is een enkel-tag pose gedegenereerd (scherende hoek) en wordt hij genegeerd. */
private const val MAX_USABLE_LOCAL_REPROJECTION_PX = 60.0f
private const val MAX_CONSENSUS_INLIER_REPROJECTION_PX = 5.0f
private const val POSE_DECISION_LOG_INTERVAL_MILLIS = 1_000L
private const val LOCAL_ANCHOR_SWITCH_SCORE_RATIO = 1.35
private const val REFERENCE_MIN_EDGE_PX = 40.0
private const val REFERENCE_AREA_SIDE_PX = 120.0

private object PoseDecisionLogger {
    private var lastKey: String = ""
    private var lastMillis: Long = 0L

    fun info(key: String, message: String) {
        log(key, message, warning = false)
    }

    fun warn(key: String, message: String) {
        log(key, message, warning = true)
    }

    private fun log(key: String, message: String, warning: Boolean) {
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastMillis < POSE_DECISION_LOG_INTERVAL_MILLIS) return
        lastKey = key
        lastMillis = now
        if (warning) {
            Log.w("ARsensPose", message)
        } else {
            Log.i("ARsensPose", message)
        }
    }
}

private fun Float.shortPx(): String =
    String.format(Locale.US, "%.2fpx", this)

private fun Float?.shortPxOrDash(): String =
    this?.takeIf { it.isFinite() }?.shortPx() ?: "-"

private fun Double.shortScore(): String =
    String.format(Locale.US, "%.1f", this)

private fun List<Marker>.knownMarkerLogSummary(): String =
    joinToString(prefix = "[", postfix = "]", limit = 8) { marker ->
        "${marker.id}:${if (marker.active) "active" else "inactive"}:${marker.sizeMm}mm@${marker.positionMm.x},${marker.positionMm.y},${marker.positionMm.z}"
    }
