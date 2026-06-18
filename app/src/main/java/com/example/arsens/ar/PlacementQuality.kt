package com.example.arsens.ar

import com.example.arsens.data.QualityGrade
import com.example.arsens.data.SensorPlacementAudit
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Live plaatsingskwaliteit: de RUWE, echt gemeten signalen voor de huidige AR-pose, plus een grade
 * (groen/oranje/rood/grijs) die uit drempels op die signalen volgt. Bewust GEEN samengesteld
 * "±mm"-getal — dat zou valse precisie suggereren. De enige mm-waarde is [reprojectionErrorMm], de
 * fysisch onderbouwde fit-fout (px × afstand/fx).
 *
 * Tijd weegt NIET expliciet mee in de grade — alleen impliciet via [trackingStatus] (TagCalibration
 * = tag net gezien; ArCoreTracking = recent; DriftPossible = ouder). Blokkeert het opslaan niet.
 */
data class PlacementQuality(
    val grade: QualityGrade,
    val reasons: List<String>,
    val trackingStatus: ArTrackingStatus,
    val trackingQualityPercent: Int,
    val detectionAgeMillis: Long,
    val poseMarkerIds: List<Int>,
    val referenceTagId: Int?,
    /** Reprojectiefout van de pose-fit op de tag-hoeken (pixels) — echt gemeten. */
    val reprojectionErrorPx: Float?,
    /** Reprojectiefout in mm op tag-diepte (px × afstand/fx) — onderbouwd; alleen de FIT-fout,
     *  niet de totale plaatsingsfout. Null als afstand/fx ontbreekt. */
    val reprojectionErrorMm: Float?,
    /** RMS cursor-jitter (mm) in het trafo-frame — echt gemeten. */
    val jitterMm: Float?,
    val motionDuringDetectionMm: Float?,
    val motionDuringDetectionDeg: Float?,
    /** Voldeed de huidige pose aan ALLE stabiele-lock-criteria (dwell, samples, jitter, motion)? */
    val isStableLock: Boolean
)

/** Drempels voor de grade-bepaling en de stabiele-lock. Géén verzonnen mm-aggregatie en géén
 *  expliciete tijd-term — alleen grenzen op echte metingen. Centraal zodat ze makkelijk te tunen zijn. */
object PlacementQualityTuning {
    const val REPROJ_GOOD_PX = 3f
    const val REPROJ_OK_PX = 8f
    const val JITTER_GOOD_MM = 8f
    const val JITTER_OK_MM = 20f
    const val MOTION_WARN_MM = 30f
    const val MOTION_WARN_DEG = 3f

    // Stabiele-lock-criteria (alleen voor de onzichtbare audit-registratie).
    const val LOCK_FRESH_AGE_MILLIS = 300L
    const val LOCK_MIN_QUALITY_PERCENT = 85
    const val LOCK_MIN_DWELL_MILLIS = 1_500L
    const val LOCK_MIN_SAMPLES = 5
    const val LOCK_MAX_JITTER_MM = 8f
    const val LOCK_MAX_MOTION_MM = 30f
    const val LOCK_MAX_MOTION_DEG = 3f
}

/**
 * Bepaalt grade + ruwe signalen voor de huidige (gefuseerde) [result]. [jitterMm] is de RMS
 * cursor-jitter; [isStableLock] of aan alle lock-criteria voldaan is; [referenceTagId] de tag die
 * de pose levert. De grade volgt uit de trackingstatus en drempels op reprojectie/jitter/beweging —
 * tijd-sinds-tag zit alleen impliciet in de status, niet als losse term.
 */
fun computePlacementQuality(
    result: AprilTagFrameResult,
    jitterMm: Float?,
    isStableLock: Boolean,
    referenceTagId: Int?
): PlacementQuality {
    val pose = result.transformerPose
    val status = result.trackingStatus
    val motionMm = result.motionDuringDetectionMm
    val motionDeg = result.motionDuringDetectionDeg
    val reprojPx = pose?.reprojectionErrorPx?.takeIf { it.isFinite() }
    val reprojMm = reprojectionErrorMm(pose, result.cameraIntrinsics, reprojPx)
    val noPose = pose == null || status == ArTrackingStatus.NoPose

    val reprojGood = reprojPx == null || reprojPx <= PlacementQualityTuning.REPROJ_GOOD_PX
    val reprojOk = reprojPx == null || reprojPx <= PlacementQualityTuning.REPROJ_OK_PX
    val jitterGood = (jitterMm ?: 0f) <= PlacementQualityTuning.JITTER_GOOD_MM
    val jitterOk = (jitterMm ?: 0f) <= PlacementQualityTuning.JITTER_OK_MM
    val motionGood = (motionMm ?: 0f) <= PlacementQualityTuning.MOTION_WARN_MM &&
        (motionDeg ?: 0f) <= PlacementQualityTuning.MOTION_WARN_DEG

    val grade = when {
        noPose -> QualityGrade.Unsafe
        status == ArTrackingStatus.NeedsRecalibration -> QualityGrade.Unsafe
        status == ArTrackingStatus.TagCalibration && reprojGood && jitterGood && motionGood ->
            QualityGrade.High
        (status == ArTrackingStatus.TagCalibration || status == ArTrackingStatus.ArCoreTracking) &&
            reprojOk && jitterOk ->
            QualityGrade.Medium
        else -> QualityGrade.Low
    }

    val reasons = buildList {
        if (reprojPx != null && reprojPx > PlacementQualityTuning.REPROJ_GOOD_PX) {
            add("pose-fit ${reprojPx.roundToInt()} px")
        }
        if ((jitterMm ?: 0f) > PlacementQualityTuning.JITTER_GOOD_MM) {
            add("jitter ${(jitterMm ?: 0f).roundToInt()} mm")
        }
        if ((motionMm ?: 0f) > PlacementQualityTuning.MOTION_WARN_MM ||
            (motionDeg ?: 0f) > PlacementQualityTuning.MOTION_WARN_DEG
        ) {
            add("beweging ${(motionMm ?: 0f).roundToInt()} mm tijdens detectie")
        }
        when (status) {
            ArTrackingStatus.DriftPossible -> add("drift mogelijk")
            ArTrackingStatus.NeedsRecalibration -> add("herkalibratie nodig")
            ArTrackingStatus.NoPose -> add("geen tag-pose")
            else -> Unit
        }
    }

    return PlacementQuality(
        grade = grade,
        reasons = reasons,
        trackingStatus = status,
        trackingQualityPercent = result.trackingQualityPercent,
        detectionAgeMillis = result.detectionAgeMillis,
        poseMarkerIds = result.poseMarkerIds,
        referenceTagId = referenceTagId,
        reprojectionErrorPx = reprojPx,
        reprojectionErrorMm = reprojMm,
        jitterMm = jitterMm,
        motionDuringDetectionMm = motionMm,
        motionDuringDetectionDeg = motionDeg,
        isStableLock = isStableLock
    )
}

/** Reprojectiefout (px) → mm op tag-diepte: px × afstand/fx. Afstand = ‖tag-translatie‖ (mm),
 *  fx in pixels. Fysisch onderbouwde conversie i.p.v. een vaste factor. */
private fun reprojectionErrorMm(
    pose: TransformerPose?,
    intrinsics: CameraIntrinsics?,
    reprojPx: Float?
): Float? {
    if (pose == null || reprojPx == null) return null
    val fx = intrinsics?.fx?.takeIf { it > 0f } ?: return null
    val t = pose.translationMm
    val distance = sqrt((t[0] * t[0] + t[1] * t[1] + t[2] * t[2]).toDouble()).toFloat()
    if (distance <= 0f) return null
    return reprojPx * distance / fx
}

/** Bevriest deze live kwaliteit tot de onveranderlijke audit-snapshot die op de sensor komt. */
fun PlacementQuality.toAudit(
    placedAtWallMillis: Long,
    fusionEvent: String?,
    fusionReason: String?
): SensorPlacementAudit =
    SensorPlacementAudit(
        grade = grade,
        trackingStatus = trackingStatus.name,
        trackingQualityPercent = trackingQualityPercent,
        detectionAgeMillis = detectionAgeMillis,
        poseMarkerIds = poseMarkerIds,
        referenceTagId = referenceTagId,
        reprojectionErrorPx = reprojectionErrorPx,
        reprojectionErrorMm = reprojectionErrorMm,
        jitterMm = jitterMm,
        motionDuringDetectionMm = motionDuringDetectionMm,
        motionDuringDetectionDeg = motionDuringDetectionDeg,
        wasStablePlacementLock = isStableLock,
        fusionEvent = fusionEvent,
        fusionReason = fusionReason,
        reasons = reasons,
        placedAtWallMillis = placedAtWallMillis
    )
