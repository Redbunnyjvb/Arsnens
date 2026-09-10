package com.example.arsens.ar.calibration

import com.example.arsens.ar.*
import com.example.arsens.data.*
import kotlin.math.*

data class WallVector(val x: Double, val y: Double, val z: Double) {
    operator fun plus(b: WallVector) = WallVector(x + b.x, y + b.y, z + b.z)
    operator fun minus(b: WallVector) = WallVector(x - b.x, y - b.y, z - b.z)
    operator fun times(s: Double) = WallVector(x * s, y * s, z * s)
    fun dot(b: WallVector) = x * b.x + y * b.y + z * b.z
    fun cross(b: WallVector) = WallVector(y*b.z-z*b.y, z*b.x-x*b.z, x*b.y-y*b.x)
    fun length() = sqrt(dot(this))
    fun unit(): WallVector? = length().takeIf { it.isFinite() && it > 1e-8 }?.let { this * (1.0 / it) }
    fun array() = doubleArrayOf(x, y, z)
    fun finite() = x.isFinite() && y.isFinite() && z.isFinite()
    companion object { fun from(a: DoubleArray) = WallVector(a[0], a[1], a[2]) }
}

fun Transform3D.wallPoint(point: WallVector) = WallVector.from(transformPoint(point.array()))
fun Transform3D.wallDirection(v: WallVector) = wallPoint(v) - WallVector.from(translation())

data class WallScanRequest(val sessionId: Long, val defaultSizeMm: Int, val tagSizes: Map<Int, Int>, val maxTagId: Int)
data class StandaloneWallTag(val tagId: Int, val sizeMm: Int, val cameraCvFromTag: Transform3D,
    val reprojectionErrorPx: Float, val shortestEdgePx: Float)
data class StandaloneWallPacket(val sessionId: Long, val tags: List<StandaloneWallTag>, val referenceUp: WallVector)
data class WallTagObservation(
    val tagId: Int, val sizeMm: Int, val timestampMillis: Long, val sequence: Long, val trackingFrameId: Long,
    val referenceFromTag: Transform3D, val referenceFromCameraCv: Transform3D, val referenceUp: WallVector,
    val reprojectionErrorPx: Float, val shortestEdgePx: Float
) {
    val center get() = WallVector.from(referenceFromTag.translation())
    val normal get() = referenceFromTag.wallDirection(WallVector(0.0, -1.0, 0.0))
    val distanceMm get() = (center - WallVector.from(referenceFromCameraCv.translation())).length()
}

data class WallScanFrame(val sessionId: Long, val trackingFrameId: Long, val tracking: Boolean,
    val observations: List<WallTagObservation>, val projectionFromReference: ArDisplayProjection?)

data class WallTagEstimate(val assignment: WallTagAssignment, val referenceFromTag: Transform3D,
    val repeatabilityMm: Double, val sampleCount: Int, val referenceUp: WallVector) {
    val center get() = WallVector.from(referenceFromTag.translation())
    val normal get() = referenceFromTag.wallDirection(WallVector(0.0, -1.0, 0.0)).unit()!!
}

data class WallCalibrationSolution(val referenceFromProject: Transform3D, val dimensionsMm: MmPosition,
    val markers: List<Marker>, val quality: WallCalibrationQuality, val residualsMm: Map<Int, Double>)
data class WallSolveResult(val solution: WallCalibrationSolution? = null, val reason: String? = null)

internal fun wallMedian(values: List<Double>): Double = values.sorted().let {
    if (it.size % 2 == 1) it[it.size/2] else (it[it.size/2-1] + it[it.size/2]) / 2
}

/** Euler convention used by Marker: Rz * Ry * Rx. */
internal fun wallMarkerEuler(matrix: Transform3D): FloatVector {
    val r = matrix.values
    val y = asin((-r[8]).coerceIn(-1.0, 1.0))
    val x = if (abs(cos(y)) > 1e-6) atan2(r[9], r[10]) else atan2(-r[6], r[5])
    val z = if (abs(cos(y)) > 1e-6) atan2(r[4], r[0]) else 0.0
    // Android's JSONObject serializes -0.0 as 0; normalize before persisting geometry.
    fun degrees(angle: Double) = Math.toDegrees(angle).toFloat().let { if (it == 0f) 0f else it }
    return FloatVector(degrees(x), degrees(y), degrees(z))
}
