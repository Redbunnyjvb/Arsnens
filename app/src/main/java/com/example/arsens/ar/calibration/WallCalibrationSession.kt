package com.example.arsens.ar.calibration

import com.example.arsens.data.WallTagAssignment
import com.example.arsens.data.CalibrationWall
import kotlin.math.*

/** Temporary, anchor-relative evidence. A lost/replaced reference invalidates the whole scan. */
class WallCalibrationSession {
    private var frameId: Long? = null
    private var lastSequence = -1L
    private val overlaps = mutableMapOf<Pair<Int, Int>, MutableList<Long>>()
    private val samples = mutableMapOf<Int, MutableList<WallTagObservation>>()
    var invalidated = false
        private set
    var reason: String? = null
        private set

    fun clear() { frameId = null; lastSequence = -1L; samples.clear(); overlaps.clear(); invalidated = false; reason = null }
    fun removeTag(id: Int) { samples.remove(id); overlaps.keys.removeAll { it.first == id || it.second == id } }
    fun count(id: Int) = samples[id]?.size ?: 0
    fun hasTopOverlap(usedIds: List<Int>): Boolean = overlaps.any { (pair, times) ->
        pair.first in usedIds && pair.second in usedIds && times.size >= 3 && times.last() - times.first() >= 200L
    }

    fun observe(frame: WallScanFrame, assignments: List<WallTagAssignment>, nowMillis: Long) {
        if (invalidated) return
        if ((frameId != null && frameId != frame.trackingFrameId) || (!frame.tracking && samples.isNotEmpty())) {
            samples.clear(); overlaps.clear(); invalidated = true
            reason = "Tracking onderbroken of referentie veranderd. Begin deze wandscan opnieuw."
            return
        }
        if (!frame.tracking) return
        frameId = frame.trackingFrameId
        val packet = frame.observations
        val sequence = packet.firstOrNull()?.sequence ?: return
        if (sequence <= lastSequence) return
        lastSequence = sequence
        val accepted = mutableListOf<WallTagObservation>()
        for (o in packet) {
            val assignment = assignments.singleOrNull { it.tagId == o.tagId && it.sizeMm == o.sizeMm } ?: continue
            if (o.trackingFrameId != frameId || nowMillis - o.timestampMillis !in 0L..250L ||
                !o.reprojectionErrorPx.isFinite() || o.reprojectionErrorPx > 1.5f || o.shortestEdgePx < 40f ||
                !o.center.finite() || o.distanceMm !in 150.0..6000.0 || o.referenceUp.unit() == null) continue
            val normal = o.normal.unit() ?: continue
            val vertical = normal.dot(o.referenceUp.unit()!!)
            if (assignment.wall == CalibrationWall.Top) {
                if (vertical < cos(Math.toRadians(12.0))) continue
            } else if (abs(vertical) > sin(Math.toRadians(15.0))) continue
            val buffer = samples.getOrPut(assignment.tagId) { mutableListOf() }
            if (buffer.lastOrNull()?.let { o.timestampMillis - it.timestampMillis < 60L } == true) continue
            buffer += o
            accepted += o
            if (buffer.size > 40) buffer.removeAt(0)
        }
        val topIds = assignments.filter { it.wall == CalibrationWall.Top }.map { it.tagId }
        for (top in accepted.filter { it.tagId in topIds }) for (side in accepted.filter { it.tagId !in topIds }) {
            // One image/camera snapshot must support BOTH sides of the corner.
            if (top.sequence != side.sequence || top.timestampMillis != side.timestampMillis ||
                !top.referenceFromCameraCv.values.contentEquals(side.referenceFromCameraCv.values)) continue
            val times = overlaps.getOrPut(top.tagId to side.tagId) { mutableListOf() }
            times += top.timestampMillis
            if (times.size > 10) times.removeAt(0)
        }
    }

    fun estimates(assignments: List<WallTagAssignment>): List<WallTagEstimate> = if (invalidated) emptyList() else
        assignments.mapNotNull { assignment ->
            val buffer = samples[assignment.tagId]?.takeIf { it.size >= 8 && it.last().timestampMillis - it.first().timestampMillis >= 700 } ?: return@mapNotNull null
            val median = WallVector(wallMedian(buffer.map { it.center.x }), wallMedian(buffer.map { it.center.y }), wallMedian(buffer.map { it.center.z }))
            val inliers = buffer.filter { (it.center - median).length() <= 20.0 }
            if (inliers.size < 8 || inliers.size < buffer.size * 0.7 || inliers.last().timestampMillis - inliers.first().timestampMillis < 700) return@mapNotNull null
            val first = inliers.first()
            val mean = inliers.drop(1).foldIndexed(first.referenceFromTag) { i, acc, o ->
                acc.blendRigidAtPoint(o.referenceFromTag, 1.0 / (i + 2), doubleArrayOf(0.0, 0.0, 0.0))
            }
            val scatter = sqrt(inliers.sumOf { (it.center - com.example.arsens.ar.calibration.WallVector.from(mean.translation())).length().pow(2) } / inliers.size)
            if (scatter > 10.0 || inliers.any { it.referenceFromTag.rotationAngleDegreesTo(mean) > 12.0 }) return@mapNotNull null
            WallTagEstimate(assignment, mean, scatter, inliers.size, first.referenceUp)
        }
}
