package com.example.arsens.ar

data class AnchorUpdate(
    val event: String,
    val reason: String,
    /** Readiness of the published anchor, not acceptance of this individual observation. */
    val settled: Boolean,
    val requiresRecalibration: Boolean = false,
    val distanceMm: Double? = null,
    val angleDeg: Double? = null,
    val sampleCount: Int = 0
)

/** Filters the object anchor in world space. Camera motion is never smoothed here. */
class AnchorPoseFilter {
    var anchor: Transform3D? = null
        private set
    private var settled = false
    var requiresRecalibration = false
        private set
    private var pending: Transform3D? = null
    private var pendingIds: List<Int> = emptyList()
    private var samples = 0
    private var sinceMillis = 0L
    private var lastSampleMillis = 0L
    private var divergenceSince: Long? = null
    private var divergenceSamples = 0
    private var lastDivergenceMillis = 0L
    private var correctionTarget: Transform3D? = null
    private var correctionPoint = doubleArrayOf(0.0, 0.0, 0.0)
    private var lastAdvanceMillis: Long? = null

    val isSettled: Boolean get() = settled && !requiresRecalibration

    val prediction: Transform3D? get() = anchor ?: pending
    val initializationCandidate: Transform3D? get() = pending

    fun reset() {
        anchor = null
        settled = false
        correctionTarget = null
        lastAdvanceMillis = null
        clearDivergence()
        clearPending()
    }

    private fun clearDivergence() {
        divergenceSince = null
        divergenceSamples = 0
        requiresRecalibration = false
    }

    private fun observeDivergence(nowMillis: Long) {
        if (divergenceSince == null || nowMillis - lastDivergenceMillis > 500L) {
            divergenceSince = nowMillis
            divergenceSamples = 0
        }
        divergenceSamples++
        lastDivergenceMillis = nowMillis
        if (divergenceSamples >= 5 && nowMillis - divergenceSince!! >= 1000L) requiresRecalibration = true
    }

    /** A pose can be noisy in 3D yet the existing anchor still fits every observed corner. */
    fun confirmImageConsistency(): AnchorUpdate {
        check(anchor != null)
        clearPending()
        clearDivergence()
        settled = true
        correctionTarget = null
        return AnchorUpdate("ACCEPT", "anchor-image-held", true)
    }

    fun cancelCorrection() {
        if (correctionTarget != null) settled = false
        correctionTarget = null
    }

    /** Finish a correction already supported by stable observations, even after tags leave.
     * Camera motion is never smoothed. Paused tracking cannot advance the correction. */
    fun advance(nowMillis: Long, tracking: Boolean = true) {
        val previousMillis = lastAdvanceMillis
        lastAdvanceMillis = nowMillis
        if (!tracking || previousMillis == null) return
        val current = anchor ?: return
        val target = correctionTarget ?: return
        val delta = (nowMillis - previousMillis).coerceIn(0L, 100L)
        if (delta == 0L) return
        val fraction = 1.0 - kotlin.math.exp(-delta / 150.0)
        val next = current.blendRigidAtPoint(target, fraction, correctionPoint)
        val p = next.transformPoint(correctionPoint)
        val q = target.transformPoint(correctionPoint)
        val distance = kotlin.math.sqrt(p.indices.sumOf { (p[it] - q[it]) * (p[it] - q[it]) })
        val angle = next.rotationAngleDegreesTo(target)
        anchor = next
        settled = distance <= 8.0 && angle <= 0.4
        if (distance <= 1.0 && angle <= 0.05) {
            anchor = target
            correctionTarget = null
        }
    }

    fun clearPending() {
        pending = null
        pendingIds = emptyList()
        samples = 0
    }

    /** Called once per fresh detection packet, never once per render frame. */
    fun update(candidate: Transform3D, markerIds: List<Int>, nowMillis: Long, relocalizing: Boolean,
               referencePoint: DoubleArray = doubleArrayOf(0.0, 0.0, 0.0),
               consistentWithPendingImage: Boolean = false): AnchorUpdate {
        advance(nowMillis, !relocalizing)
        if (markerIds.isEmpty() || candidate.values.any { !it.isFinite() }) {
            clearPending()
            return AnchorUpdate("REJECT", "invalid-pose", false)
        }
        val ids = markerIds.distinct().sorted()
        val current = anchor
        if (relocalizing) settled = false
        fun distanceAtReference(a: Transform3D, b: Transform3D): Double {
            val p = a.transformPoint(referencePoint)
            val q = b.transformPoint(referencePoint)
            return kotlin.math.sqrt(p.indices.sumOf { (p[it] - q[it]) * (p[it] - q[it]) })
        }
        val distance = current?.let { distanceAtReference(it, candidate) } ?: 0.0
        val angle = current?.rotationAngleDegreesTo(candidate) ?: 0.0
        fun decision(event: String, reason: String) = AnchorUpdate(event, reason,
            settled && !requiresRecalibration, requiresRecalibration, distance, angle, samples)
        // An established frame must not jump to a moved single reference. Recover large
        // world changes only with multiple agreeing references or explicit relocalization.
        val maxDistance = if (ids.size >= 2) 120.0 else 30.0
        val maxAngle = if (ids.size >= 2) 5.0 else 1.5
        val largeCorrection = current != null && !relocalizing && (distance > maxDistance || angle > maxAngle)
        if (largeCorrection && ids.size < 2) {
            cancelCorrection()
            clearPending()
            observeDivergence(nowMillis)
            return decision("REJECT", "reference-jump")
        }
        if (current != null && !relocalizing && distance <= 3.0 && angle <= 0.15) {
            clearPending()
            clearDivergence()
            correctionTarget = null
            settled = true
            return decision("ACCEPT", "anchor-held")
        }
        if (current != null && !relocalizing) observeDivergence(nowMillis)
        val previous = pending
        if (previous == null || (ids != pendingIds && !consistentWithPendingImage) || nowMillis - lastSampleMillis > 500L ||
            distanceAtReference(previous, candidate) > 30.0 ||
            (!consistentWithPendingImage && previous.rotationAngleDegreesTo(candidate) > (if (ids.size == 1) 6.0 else 2.0))
        ) {
            pending = candidate
            pendingIds = ids
            samples = 1
            sinceMillis = nowMillis
        } else {
            // Keep a running estimate of independent observations, not only the last sample.
            samples++
            pending = previous.blendRigidAtPoint(candidate, 1.0 / samples.coerceAtMost(8), referencePoint)
            pendingIds = ids
        }
        lastSampleMillis = nowMillis
        val initializing = current == null || relocalizing
        val requiredSamples = if (largeCorrection) 8 else if (initializing) 5 else 3
        val requiredMillis = if (largeCorrection) 1000L else if (initializing) 400L else 200L
        if (samples < requiredSamples || nowMillis - sinceMillis < requiredMillis) {
            return decision("PENDING", "anchor-settling")
        }
        val target = pending ?: candidate
        anchor = if (initializing) target else current!!.blendRigidAtPoint(target, 0.15, referencePoint)
        correctionTarget = target.takeUnless { initializing }
        correctionPoint = referencePoint.copyOf()
        lastAdvanceMillis = nowMillis
        settled = distanceAtReference(anchor!!, target) <= 8.0 && anchor!!.rotationAngleDegreesTo(target) <= 0.4
        clearDivergence()
        // The next correction needs its own evidence window. Reusing initialization
        // samples biases its target towards the old pose and can accept it too early.
        return decision("ACCEPT", if (initializing) "calibrated" else "corrected").also { clearPending() }
    }
}
