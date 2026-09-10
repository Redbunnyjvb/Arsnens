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
    val calibrationRevision: Int = 0,
    /** Changes when the native tracking reference is replaced; session rays cannot cross it. */
    val trackingFrameId: Long = 0L,
    val nativeAnchorTracking: Boolean = false,
    val calibrationAgeMillis: Long = Long.MAX_VALUE,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val detections: List<AprilTagDetection> = emptyList(),
    val screenDetections: List<AprilTagDetection> = emptyList(),
    /** Reference observations transported to the current camera, independently of the fused
     * anchor. Raw screenDetections remain paired with the capture for measurements/logging. */
    val trackedScreenDetections: List<AprilTagDetection> = emptyList(),
    /** Current camera-to-tag-center distance, based on the measured reference pose, in mm. */
    val tagDistancesMm: Map<Int, Float> = emptyMap(),
    val cameraIntrinsics: CameraIntrinsics? = null,
    val imageToViewMapper: ImageToViewMapper? = null,
    val imageProjectionPose: TransformerPose? = null,
    val detectionsFresh: Boolean = false,
    val knownMarkerCount: Int = 0,
    val poseMarkerCount: Int = 0,
    val poseMarkerIds: List<Int> = emptyList(),
    /** All references checked against this pose, including in NearestTag mode. Excludes quarantine. */
    val verifiedReferenceIds: List<Int> = emptyList(),
    val rejectedMarkerIds: List<Int> = emptyList(),
    val referenceConflict: Boolean = false,
    /** Monotonic capture time and sequence; repeated display frames are not new detections. */
    val capturedAtElapsedMillis: Long? = null,
    val detectionSequence: Long = 0L,
    val referenceDepthMm: Float? = null,
    val referencePointMm: MmPosition? = null,
    /** Immutable reference geometry from the same capture as detections. */
    val referenceMarkers: List<Marker> = emptyList(),
    val anchorImageErrorPx: Float? = null,
    val anchorSettled: Boolean = false,
    val correctionState: AnchorCorrectionState = AnchorCorrectionState.Uncalibrated,
    val fusionDeltaMm: Double? = null,
    val fusionDeltaDeg: Double? = null,
    val fusionSamples: Int = 0,
    val fusionDiagnostic: String? = null,
    val poseDiagnostic: String? = null,
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
    /** Motion between capture and processing, for diagnostics. Capture pose compensates it;
     * this is not a measurement of image blur and must not block the current AR cursor. */
    val motionDuringDetectionMm: Float? = null,
    val motionDuringDetectionDeg: Float? = null,
    /** Transformer→shared tracking reference (native anchor-local in the ARCore route).
     *  Only comparable within the same trackingFrameId; same transform used by
     *  [displayProjection]/[cameraCvFromTransformer] volgen). Voor straal-replay-driftcorrectie:
     *  samen met de bij plaatsing bewaarde transformer-straal reconstrueert dit de anker-
     *  onafhankelijke camerastraal in ARCore's wereld. */
    val arFromTransformer: Transform3D? = null,
    val standaloneWallPacket: com.example.arsens.ar.calibration.StandaloneWallPacket? = null,
    val wallScanFrame: com.example.arsens.ar.calibration.WallScanFrame? = null
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
    Automatic(
        "Automatisch",
        "Controleert referenties onderling en combineert de tags die overeenkomen"
    ),
    NearestTag(
        "Dichtstbijzijnde tag",
        "Gebruikt één goed zichtbare tag na controle met de andere referenties"
    ),
    MultiTag(
        "Multi-tag",
        "Combineert zichtbare referenties na controle op onderlinge overeenstemming"
    );

    companion object {
        val DEFAULT = Automatic

        fun fromName(name: String?): TagPoseMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

class AprilTagDetector(
    dictionaryId: Int = Objdetect.DICT_APRILTAG_36h11
) {
    private var preferredLocalAnchorId: Int? = null
    private val quarantine = TagReferenceQuarantine()
    private var previousMarkers: List<Marker> = emptyList()
    private var previousPoseMode: TagPoseMode? = null

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
        poseMode: TagPoseMode = TagPoseMode.DEFAULT,
        predictedCameraPose: TransformerPose? = null
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
            if (knownMarkers != previousMarkers || poseMode != previousPoseMode) {
                recentPosePerTag.clear()
                quarantine.reset()
                preferredLocalAnchorId = null
                previousMarkers = knownMarkers.toList()
                previousPoseMode = poseMode
            }
            val selection = selectTransformerPoseFromAprilTags(
                detections = detections,
                knownMarkers = knownMarkers,
                cameraIntrinsics = cameraIntrinsics,
                preferredLocalMarkerId = preferredLocalAnchorId,
                poseMode = poseMode,
                priorPosePerTag = if (predictedCameraPose != null) knownMarkers.associate { it.id to predictedCameraPose }
                    else recentPosePerTag.mapValues { it.value.first }
            )
            val visibleKnownIds = detections.map { it.id }.toSet()
                .intersect(knownMarkers.filter { it.active }.map { it.id }.toSet())
            val quarantined = quarantine.update(visibleKnownIds, selection.consensusIds.toSet())
            val poseEstimate = selection.estimate?.takeIf { estimate ->
                estimate.markerIds.none { it in quarantined }
            }
            val referenceConflict = selection.referenceConflict ||
                (poseEstimate == null && visibleKnownIds.any { it in quarantined })
            val diagnostic = when {
                poseEstimate != null -> null
                visibleKnownIds.any { it in quarantined } -> "Tag ${quarantined.intersect(visibleKnownIds).sorted()} is uitgesloten; scan ook twee gecontroleerde referentietags."
                else -> selection.diagnostic
            }
            PoseDecisionLogger.info(
                key = "detect|${detections.map { it.id }}|${poseEstimate?.markerIds}|$diagnostic",
                message = "detected=${detections.map { it.id }} known=${knownMarkers.knownMarkerLogSummary()} " +
                    "poseTags=${poseEstimate?.markerIds ?: emptyList<Int>()} excluded=$quarantined reason=${diagnostic ?: "verified"}"
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
                verifiedReferenceIds = if (poseEstimate != null) selection.consensusIds.filterNot { it in quarantined } else emptyList(),
                rejectedMarkerIds = (selection.rejectedIds + quarantined.intersect(visibleKnownIds)).distinct().sorted(),
                referenceConflict = referenceConflict,
                referenceMarkers = knownMarkers.filter { poseEstimate != null && it.id in selection.consensusIds && it.id !in quarantined },
                poseDiagnostic = diagnostic,
                referencePointMm = poseEstimate?.let { estimate ->
                    knownMarkers.filter { it.id in estimate.markerIds }.takeIf { it.isNotEmpty() }?.let { markers ->
                        MmPosition(markers.map { it.positionMm.x }.average().roundToInt(),
                            markers.map { it.positionMm.y }.average().roundToInt(),
                            markers.map { it.positionMm.z }.average().roundToInt())
                    }
                },
                referenceDepthMm = pose?.let { accepted ->
                    val camera = Transform3D.cameraCvFromTransformerPose(accepted)
                    knownMarkers.filter { it.id in (poseEstimate.markerIds) }.map { marker ->
                        camera.transformPoint(doubleArrayOf(
                            marker.positionMm.x.toDouble(), marker.positionMm.y.toDouble(), marker.positionMm.z.toDouble()
                        ))[2].toFloat()
                    }.filter { it.isFinite() && it > 0f }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
                },
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

/** Kept as a small public entry point for native geometry regression tests. */
fun estimateTransformerPoseFromAprilTags(
    detections: List<AprilTagDetection>,
    knownMarkers: List<Marker>,
    cameraIntrinsics: CameraIntrinsics,
    preferredLocalMarkerId: Int? = null,
    poseMode: TagPoseMode = TagPoseMode.DEFAULT,
    priorPosePerTag: Map<Int, TransformerPose> = emptyMap()
): AprilTagPoseEstimate? = selectTransformerPoseFromAprilTags(
    detections, knownMarkers, cameraIntrinsics, preferredLocalMarkerId, poseMode, priorPosePerTag
).estimate

internal data class AprilTagPoseSelection(
    val estimate: AprilTagPoseEstimate? = null,
    val consensusIds: List<Int> = emptyList(),
    val rejectedIds: List<Int> = emptyList(),
    val referenceConflict: Boolean = false,
    val diagnostic: String? = null
)

internal fun selectTransformerPoseFromAprilTags(
    detections: List<AprilTagDetection>,
    knownMarkers: List<Marker>,
    cameraIntrinsics: CameraIntrinsics,
    preferredLocalMarkerId: Int? = null,
    poseMode: TagPoseMode = TagPoseMode.DEFAULT,
    priorPosePerTag: Map<Int, TransformerPose> = emptyMap()
): AprilTagPoseSelection {
    val markersById = knownMarkers.filter { it.active && it.sizeMm > 0 }.associateBy { it.id }
    val matched = detections.filter { it.id in markersById }
    // Duplicate printed IDs cannot define two independent reference points.
    if (matched.groupingBy { it.id }.eachCount().any { it.value > 1 }) {
        return AprilTagPoseSelection(referenceConflict = true, diagnostic = "Dezelfde tag-ID staat meer dan één keer in beeld.")
    }
    val known = matched.map { detection ->
        KnownAprilTagDetection(detection, markersById.getValue(detection.id), polygonAreaPx(detection.cornersPx))
    }
    if (known.isEmpty()) return AprilTagPoseSelection(diagnostic = if (detections.isEmpty())
        "Geen tag herkend. Houd de hele zwarte rand en witte marge in beeld."
        else "Tag ${detections.map { it.id }} herkend, maar geen actieve referentietag op een opgeslagen positie.")
    val locals = known.mapNotNull { tag ->
        val estimate = solveSingleAprilTagPose(tag, cameraIntrinsics, priorPosePerTag[tag.marker.id])
            ?: solveKnownAprilTagPose(listOf(tag), cameraIntrinsics) ?: return@mapNotNull null
        if (!estimate.pose.isUsable() || estimate.pose.reprojectionErrorPx > MAX_USABLE_LOCAL_REPROJECTION_PX) {
            return@mapNotNull null
        }
        LocalPoseCandidate(tag, estimate, localReferenceScore(tag, estimate.pose.reprojectionErrorPx))
    }
    val visibleIds = known.map { it.marker.id }.toSet()
    if (locals.isEmpty()) return AprilTagPoseSelection(diagnostic = "Referentietag herkend, maar pose-fit onvoldoende. Kijk rechter op de tag en controleer maat en oriëntatie.")
    fun errors(pose: TransformerPose) = known.associate { tag ->
        tag.marker.id to reprojectionErrorForPose(pose, listOf(tag), cameraIntrinsics)
    }
    // Validate an independently estimated reference against the OTHER tags before refitting.
    // Fitting all tags first can hide a moved tag inside a low-error compromise.
    val hypotheses = locals.map { TagPoseHypothesis(it.estimate, errors(it.estimate.pose)) }.toMutableList()
    // A small planar tag alone has weak orientation observability. Pair hypotheses keep
    // correct boards usable under subpixel noise; every hypothesis is checked against ALL
    // visible tags, and each corner of a supported tag must fit. Bound work on dense boards.
    val seeds = known.sortedByDescending { it.readabilityScore }.take(8)
    for (i in seeds.indices) for (j in i + 1 until seeds.size) {
        val pair = solveKnownAprilTagPose(listOf(seeds[i], seeds[j]), cameraIntrinsics) ?: continue
        if (pair.pose.isUsable()) hypotheses += TagPoseHypothesis(pair, errors(pair.pose))
    }
    // All-corner hypotheses resolve the weak planar orientation of small individual tags.
    // Admit this seed only with a tighter bound on EVERY tag, never on the global average.
    if (known.size >= 3) {
        solveKnownAprilTagPose(known, cameraIntrinsics)?.let { joint ->
            val residuals = errors(joint.pose)
            if (residuals.values.all { it.isFinite() && it <= 1.5f }) {
                hypotheses += TagPoseHypothesis(joint, residuals)
            }
        }
    }
    val consensus = selectTagConsensus(visibleIds, hypotheses)
        ?: return AprilTagPoseSelection(referenceConflict = known.size > 1, diagnostic = if (known.size > 1)
            "Referentietags spreken elkaar tegen; controleer positie, vlak en oriëntatie."
            else "Tagpose past onvoldoende op alle vier hoeken. Kom dichter bij de tag.")
    val inliers = known.filter { it.marker.id in consensus.inlierIds }
    val localInliers = locals.filter { it.known.marker.id in consensus.inlierIds }
    val best = localInliers.maxByOrNull { it.score }
    val preferred = localInliers.firstOrNull { it.known.marker.id == preferredLocalMarkerId }
    val local = preferred?.takeIf { best != null && best.score <= it.score * LOCAL_ANCHOR_SWITCH_SCORE_RATIO } ?: best
    val localFitsGroup = local?.let { candidate ->
        val residuals = errors(candidate.estimate.pose)
        consensus.inlierIds.all { residuals.getValue(it) <= MAX_STRONG_MULTI_TAG_REPROJECTION_PX }
    } ?: false
    val estimate = if (inliers.size >= 2 && (poseMode != TagPoseMode.NearestTag || !localFitsGroup)) {
        solveKnownAprilTagPose(inliers, cameraIntrinsics)
            ?: return AprilTagPoseSelection(referenceConflict = true)
    } else local?.estimate ?: consensus.hypothesis.pose
    val finalErrors = errors(estimate.pose)
    // Refitting must not invalidate any member of the selected consensus.
    if (!estimate.pose.isUsable() || consensus.inlierIds.any {
        finalErrors.getValue(it).let { error -> !error.isFinite() || error > MAX_STRONG_MULTI_TAG_REPROJECTION_PX }
    }) return AprilTagPoseSelection(referenceConflict = known.size > 1)
    val rejected = (visibleIds - consensus.inlierIds.toSet()).sorted()
    PoseDecisionLogger.info(
        key = "verified|${estimate.markerIds}|$rejected",
        message = "pose=verified tags=${estimate.markerIds} excluded=$rejected err=${estimate.pose.reprojectionErrorPx.shortPx()}"
    )
    return AprilTagPoseSelection(
        estimate = estimate.copy(posePerTag = localInliers.associate { it.known.marker.id to it.estimate.pose }),
        consensusIds = consensus.inlierIds,
        rejectedIds = rejected
    )
}

private fun TransformerPose.isUsable(): Boolean =
    reprojectionErrorPx.isFinite() && translationMm.all { it.isFinite() } && rotationVector.all { it.isFinite() }

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

    val chosenIppe = ippeCandidates.filter { poseProjectsInFront(it, objectPoints) }
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
    imgMat: MatOfPoint2f,
    method: Int = Calib3d.SOLVEPNP_SQPNP
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
                method
            )
        }.getOrDefault(false)
        if (!ok) return null
        if (objectPoints.size > 4) {
            // Refine the joint pose on the original corners. The initial planar homography
            // is sensitive to noise and must not be mistaken for disagreement between tags.
            runCatching { Calib3d.solvePnPRefineLM(objMat, imgMat, cameraMatrix, distCoeffs, rvec, tvec) }
        }
        val error = reprojectionErrorMm(
            objectPoints = objectPoints,
            imagePoints = imagePoints,
            cameraMatrix = cameraMatrix,
            distCoeffs = distCoeffs,
            rvec = rvec,
            tvec = tvec
        )
        if (!error.isFinite()) return null
        val pose = TransformerPose(
            translationMm = FloatArray(3) { i -> tvec.get(i, 0)?.firstOrNull()?.toFloat() ?: 0f },
            rotationVector = FloatArray(3) { i -> rvec.get(i, 0)?.firstOrNull()?.toFloat() ?: 0f },
            reprojectionErrorPx = error
        )
        pose.takeIf { poseProjectsInFront(it, objectPoints) }
    } finally {
        rvec.release()
        tvec.release()
    }
}

private fun solveKnownAprilTagPose(
    knownDetections: List<KnownAprilTagDetection>,
    cameraIntrinsics: CameraIntrinsics
): AprilTagPoseEstimate? {
    val objectPoints = knownDetections.flatMap { known ->
        markerCornersInProjectFrame(known.marker).map { Point3(it.x, it.y, it.z) }
    }
    val imagePoints = knownDetections.flatMap { known ->
        known.detection.cornersPx.map { Point(it.xPx.toDouble(), it.yPx.toDouble()) }
    }
    if (objectPoints.size < 4 || objectPoints.size != imagePoints.size) return null
    val cameraMatrix = cameraIntrinsics.toOpenCvCameraMatrix()
    val distortion = MatOfDouble(0.0, 0.0, 0.0, 0.0)
    val objMat = MatOfPoint3f(*objectPoints.toTypedArray())
    val imgMat = MatOfPoint2f(*imagePoints.toTypedArray())
    return try {
        val base = solveSqPnpPose(objectPoints, imagePoints, cameraMatrix, distortion, objMat, imgMat, solvePnpMethodFor(objectPoints))
        val retry = if (base == null || base.reprojectionErrorPx > LOCAL_POSE_RETRY_REPROJECTION_PX) {
            solveSqPnpPose(objectPoints, imagePoints, cameraMatrix, distortion, objMat, imgMat)
        } else null
        val pose = listOfNotNull(base, retry).minByOrNull { it.reprojectionErrorPx } ?: return null
        AprilTagPoseEstimate(pose, knownDetections.map { it.marker.id }.distinct().sorted())
    } finally {
        cameraMatrix.release()
        distortion.release()
        objMat.release()
        imgMat.release()
    }
}

private fun poseProjectsInFront(pose: TransformerPose, points: List<Point3>): Boolean {
    if (!pose.isUsable()) return false
    val camera = Transform3D.cameraCvFromTransformerPose(pose)
    return points.all { camera.transformPoint(doubleArrayOf(it.x, it.y, it.z))[2] > 1.0 }
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
        tvec = tvec,
        maximum = true
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
    val ray = imagePoseRayForPixel(result, pixelX, pixelY) ?: return null
    return intersectTransformerBox(ray, dimensionsMm)?.position
}

/**
 * Als [estimateSurfaceAtPixel], maar snijdt de camerastraal met EXACT het opgegeven [plane]-vlak in
 * plaats van met de hele trafo-box. Voor referentietag-setup: de operator kiest het vlak (Voor,
 * Rechts, …) en de afgeleide tagpositie krijgt gegarandeerd het bijbehorende vaste-vlak-component
 * (Front→Y=0, Right→X=lengte, …). Zo kan een Rechts-tag nooit stilletjes op Voor terechtkomen
 * doordat de straal onder een scherende hoek eerst een ander box-vlak raakt.
 *
 * Geeft null als er geen pose is, of als de straal het gekozen vlak niet binnen zijn rechthoek raakt
 * — de aanroeper moet dat als "geen plaatsing op dit vlak" behandelen (niet stil terugvallen op een
 * ander vlak). [estimateSurfaceAtPixel] blijft bestaan voor sensor-op-oppervlak / algemene box-hits.
 */
fun estimateSurfaceAtPixelOnPlane(
    result: AprilTagFrameResult,
    pixelX: Float,
    pixelY: Float,
    plane: TagPlane,
    dimensionsMm: MmPosition
): MmPosition? {
    val ray = imagePoseRayForPixel(result, pixelX, pixelY) ?: return null
    return intersectTagPlaneExact(ray, plane, dimensionsMm)
}

/**
 * Bouwt de camerastraal (transformerframe) door beeldpixel [pixelX],[pixelY] vanuit de image-pose.
 * Cameraoorsprong C = -R^T·t; richting d_world = R^T·[(px-cx)/fx, (py-cy)/fy, 1]. Gedeeld door
 * [estimateSurfaceAtPixel] en [estimateSurfaceAtPixelOnPlane] zodat beide exact dezelfde straal
 * gebruiken — alleen het te snijden vlak verschilt.
 */
fun imagePoseRayForPixel(
    result: AprilTagFrameResult,
    pixelX: Float,
    pixelY: Float
): RayMm? {
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
    return RayMm(
        origin = cameraOriginWorld,
        direction = DoubleArray(3) { rayWorld[it] / len }
    )
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
    referenceMarker: Marker?,
    placementPlane: TagPlane? = null
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
    if (placementPlane != null) return intersectTagPlaneExact(ray, placementPlane, dimensionsMm)
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
    if (intrinsics == null || intrinsics.fx <= 0f || intrinsics.fy <= 0f) {
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
    tvec: Mat,
    maximum: Boolean = false
): Float {
    val projected = MatOfPoint2f()
    val objectsMat = MatOfPoint3f(*objectPoints.toTypedArray())
    Calib3d.projectPoints(
        objectsMat,
        rvec,
        tvec,
        cameraMatrix,
        distCoeffs,
        projected
    )
    val projectedPoints = projected.toArray()
    val errors = projectedPoints.zip(imagePoints).map { (projectedPoint, imagePoint) ->
        hypot(projectedPoint.x - imagePoint.x, projectedPoint.y - imagePoint.y)
    }
    projected.release()
    objectsMat.release()
    return (if (maximum) errors.maxOrNull() ?: Double.POSITIVE_INFINITY else errors.average()).toFloat()
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
private const val LOCAL_POSE_RETRY_REPROJECTION_PX = 2.0f

/** Zo lang blijft de laatst gekozen tag-pose bruikbaar als temporele prior voor de
 *  ambiguïteits-keuze; daarna (tag lang uit beeld) beslist de reprojectiefout weer alleen. */
private const val POSE_PRIOR_TTL_MILLIS = 1_500L

/** Boven deze fout is een enkel-tag pose gedegenereerd (scherende hoek) en wordt hij genegeerd. */
private const val MAX_USABLE_LOCAL_REPROJECTION_PX = 3.0f
private const val POSE_DECISION_LOG_INTERVAL_MILLIS = 1_000L
private const val LOCAL_ANCHOR_SWITCH_SCORE_RATIO = 1.35
private const val REFERENCE_MIN_EDGE_PX = 40.0
private const val REFERENCE_AREA_SIDE_PX = 120.0

private object PoseDecisionLogger {
    private val lastByCategory = mutableMapOf<String, Pair<String, Long>>()

    fun info(key: String, message: String) {
        log(key, message, warning = false)
    }

    fun warn(key: String, message: String) {
        log(key, message, warning = true)
    }

    private fun log(key: String, message: String, warning: Boolean) {
        val now = System.currentTimeMillis()
        val category = key.substringBefore('|')
        val previous = lastByCategory[category]
        if (previous != null && now - previous.second <
            (if (key == previous.first) POSE_DECISION_LOG_INTERVAL_MILLIS else 500L)) return
        lastByCategory[category] = key to now
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
