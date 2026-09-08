package com.example.arsens.ar

data class AnchorUpdate(val event: String, val reason: String, val settled: Boolean)

/** Filters the object anchor in world space. Camera motion is never smoothed here. */
class AnchorPoseFilter {
    var anchor: Transform3D? = null
        private set
    private var pending: Transform3D? = null
    private var pendingIds: List<Int> = emptyList()
    private var samples = 0
    private var sinceMillis = 0L
    private var lastSampleMillis = 0L

    fun reset() {
        anchor = null
        clearPending()
    }

    fun clearPending() {
        pending = null
        pendingIds = emptyList()
        samples = 0
    }

    /** Called once per fresh detection packet, never once per render frame. */
    fun update(candidate: Transform3D, markerIds: List<Int>, nowMillis: Long, relocalizing: Boolean): AnchorUpdate {
        if (markerIds.isEmpty() || candidate.values.any { !it.isFinite() }) {
            clearPending()
            return AnchorUpdate("REJECT", "invalid-pose", false)
        }
        val ids = markerIds.distinct().sorted()
        val current = anchor
        val distance = current?.distanceTo(candidate) ?: 0.0
        val angle = current?.rotationAngleDegreesTo(candidate) ?: 0.0
        // An established frame must not jump to a moved single reference. Recover large
        // world changes only with multiple agreeing references or explicit relocalization.
        val maxDistance = if (ids.size >= 2) 120.0 else 30.0
        val maxAngle = if (ids.size >= 2) 5.0 else 1.5
        if (current != null && !relocalizing && (distance > maxDistance || angle > maxAngle)) {
            clearPending()
            return AnchorUpdate("REJECT", "reference-jump", false)
        }
        if (current != null && !relocalizing && distance <= 3.0 && angle <= 0.15) {
            clearPending()
            return AnchorUpdate("ACCEPT", "anchor-held", true)
        }
        val previous = pending
        if (previous == null || ids != pendingIds || nowMillis - lastSampleMillis > 500L ||
            previous.distanceTo(candidate) > 30.0 || previous.rotationAngleDegreesTo(candidate) > 1.0
        ) {
            pending = candidate
            pendingIds = ids
            samples = 1
            sinceMillis = nowMillis
        } else {
            // Keep a running estimate of independent observations, not only the last sample.
            samples++
            pending = previous.blendRigidToward(candidate, 1.0 / samples.coerceAtMost(8))
        }
        lastSampleMillis = nowMillis
        if (samples < 3 || nowMillis - sinceMillis < 200L) {
            return AnchorUpdate("PENDING", "anchor-settling", false)
        }
        val target = pending ?: candidate
        anchor = if (current == null || relocalizing) target else current.blendRigidToward(target, 0.15)
        val settled = anchor!!.distanceTo(target) <= 8.0 && anchor!!.rotationAngleDegreesTo(target) <= 0.4
        return AnchorUpdate("ACCEPT", if (current == null || relocalizing) "calibrated" else "corrected", settled)
    }
}
