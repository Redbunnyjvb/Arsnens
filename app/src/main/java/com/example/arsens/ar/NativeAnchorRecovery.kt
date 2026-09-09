package com.example.arsens.ar

import com.google.ar.core.TrackingState

/** Recovery observations live in a separate temporary world frame. No paused anchor pose
 * is accepted here. Returning to TRACKING discards that evidence, as does camera loss. */
internal class NativeAnchorRecovery(private val nextFrameId: () -> Long) {
    private val filter = AnchorPoseFilter()
    private var recoveryFrameId: Long? = null
    private var activeFrameId: Long? = null
    private var pausedSince = 0L
    private var lastSequence = 0L
    private var lastEvidenceMillis: Long? = null
    var diagnostic: String? = null
        private set
    var detail: String? = null
        private set

    fun reset() {
        filter.reset()
        recoveryFrameId = null
        activeFrameId = null
        lastSequence = 0L
        lastEvidenceMillis = null
        diagnostic = null
        detail = null
    }

    /** Null disables capture. A paused native anchor uses a distinct raw-world epoch. */
    fun captureFrameId(cameraTracking: Boolean, nativeState: TrackingState?, trackingFrameId: Long, nowMillis: Long): Long? {
        if (!cameraTracking || nativeState == TrackingState.STOPPED) { reset(); return null }
        if (nativeState != TrackingState.PAUSED) { reset(); return trackingFrameId }
        if (recoveryFrameId == null || activeFrameId != trackingFrameId) {
            reset()
            activeFrameId = trackingFrameId
            recoveryFrameId = nextFrameId()
            pausedSince = nowMillis
            diagnostic = "AR-anker gepauzeerd. Scan twee gecontroleerde referentietags samen, of controleer de tagpositie en kies AR opnieuw ijken."
        }
        return recoveryFrameId
    }

    /** True authorizes replacing the native anchor; fusion still calibrates in the NEW frame. */
    fun observe(result: AprilTagFrameResult, worldFromCaptureGl: Transform3D, nowMillis: Long): Boolean {
        if (recoveryFrameId == null || result.trackingFrameId != recoveryFrameId || result.detectionSequence <= lastSequence) return false
        lastSequence = result.detectionSequence
        if (lastEvidenceMillis?.let { nowMillis - it > 500L } == true) filter.reset()
        val age = result.capturedAtElapsedMillis?.let { nowMillis - it }
        val pose = result.transformerPose
        val point = result.referencePointMm
        val evidenceIds = result.verifiedReferenceIds.ifEmpty { result.poseMarkerIds }.distinct()
        val reason = when {
            age == null || age !in 0L..250L -> "stale-detection"
            result.referenceConflict -> "reference-conflict"
            (result.poseMarkerIds + evidenceIds).any { it in result.rejectedMarkerIds } -> "excluded-reference"
            evidenceIds.size < 2 -> "second-reference-required"
            pose == null || !pose.reprojectionErrorPx.isFinite() || pose.reprojectionErrorPx > 3f || point == null -> "invalid-pose"
            else -> null
        }
        if (reason != null) {
            if (reason != "second-reference-required") { filter.reset(); lastEvidenceMillis = null }
            detail = "Herstel: $reason · tags ${result.poseMarkerIds} · bevestigd $evidenceIds · uitgesloten ${result.rejectedMarkerIds} · leeftijd $age ms · samples 0"
            return false
        }
        val worldFromCv = worldFromCaptureGl * Transform3D.cameraGlFromCameraCv()
        val candidate = worldFromCv * Transform3D.cameraCvFromTransformerPose(pose!!)
        val selfError = anchorImageErrorPx(Transform3D.cameraCvFromTransformerPose(pose), result.cameraIntrinsics,
            result.referenceMarkers, result.detections, evidenceIds)
        if (selfError == null || selfError > 3f) { filter.reset(); detail = "Herstel: inconsistent-image"; return false }
        val pendingError = filter.initializationCandidate?.let {
            anchorImageErrorPx(worldFromCv.inverseRigid() * it, result.cameraIntrinsics,
                result.referenceMarkers, result.detections, evidenceIds)
        }
        val update = filter.update(candidate, evidenceIds, nowMillis, false,
            doubleArrayOf(point!!.x.toDouble(), point.y.toDouble(), point.z.toDouble()),
            consistentWithPendingImage = pendingError?.let { it <= ANCHOR_IMAGE_HOLD_MAX_PX })
        lastEvidenceMillis = nowMillis
        detail = "Herstel: ${update.event}/${update.reason} · tags ${result.poseMarkerIds} · bevestigd $evidenceIds · uitgesloten ${result.rejectedMarkerIds} " +
            "· leeftijd $age ms · Δ ${update.distanceMm} mm/${update.angleDeg}° · samples ${update.sampleCount}"
        return nowMillis - pausedSince >= 1000L && update.event == "ACCEPT" && filter.isSettled
    }
}
