package com.example.arsens.ar

import com.example.arsens.data.Marker
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** Fit the three translation components from four corners while keeping the established rotation.
 * A planar PnP normal can be weakly determined even when position/scale are measurable.
 * This does NOT authorize rotation recovery or large single-reference corrections. */
internal fun fitSingleReferenceTranslation(
    priorCameraFromProject: Transform3D,
    measuredCameraFromProject: Transform3D,
    intrinsics: CameraIntrinsics?,
    marker: Marker,
    detection: AprilTagDetection
): Transform3D? {
    if (intrinsics == null || !marker.active || marker.sizeMm <= 0 || detection.id != marker.id ||
        detection.cornersPx.size != 4 || intrinsics.fx <= 0 || intrinsics.fy <= 0) return null
    val corners = detection.cornersPx
    val shortestEdge = corners.indices.minOf { i ->
        val a = corners[i]; val b = corners[(i + 1) % 4]
        hypot((a.xPx - b.xPx).toDouble(), (a.yPx - b.yPx).toDouble())
    }
    if (!shortestEdge.isFinite() || shortestEdge < 40.0) return null
    val center = doubleArrayOf(marker.positionMm.x.toDouble(), marker.positionMm.y.toDouble(), marker.positionMm.z.toDouble())
    fun distanceAtCenter(a: Transform3D, b: Transform3D): Double {
        val p = a.transformPoint(center); val q = b.transformPoint(center)
        return sqrt(p.indices.sumOf { (p[it] - q[it]) * (p[it] - q[it]) })
    }
    // Never reinterpret a large translation, a bad corner fit, or a gross rotation as small drift.
    if (distanceAtCenter(priorCameraFromProject, measuredCameraFromProject) > 30.0 ||
        priorCameraFromProject.rotationAngleDegreesTo(measuredCameraFromProject) > 12.0) return null
    val rawError = anchorImageErrorPx(measuredCameraFromProject, intrinsics, listOf(marker), listOf(detection), listOf(marker.id))
    if (rawError == null || rawError > ANCHOR_IMAGE_HOLD_MAX_PX) return null

    // Solve for the camera-space tag center, keeping large project coordinates out of the fit.
    val normal = Array(3) { DoubleArray(4) }
    val rotation = priorCameraFromProject.values
    fun equation(a: DoubleArray, value: Double) {
        for (row in 0..2) {
            for (col in 0..2) normal[row][col] += a[row] * a[col]
            normal[row][3] += a[row] * value
        }
    }
    markerCornersInProjectFrame(marker).zip(corners).forEach { (point, pixel) ->
        val relative = doubleArrayOf(point.x - center[0], point.y - center[1], point.z - center[2])
        val rotated = DoubleArray(3) { row -> (0..2).sumOf { col -> rotation[row * 4 + col] * relative[col] } }
        val u = (pixel.xPx - intrinsics.cx).toDouble() / intrinsics.fx
        val v = (pixel.yPx - intrinsics.cy).toDouble() / intrinsics.fy
        equation(doubleArrayOf(1.0, 0.0, -u), u * rotated[2] - rotated[0])
        equation(doubleArrayOf(0.0, 1.0, -v), v * rotated[2] - rotated[1])
    }
    for (col in 0..2) {
        val pivot = (col..2).maxBy { abs(normal[it][col]) }
        if (!normal[pivot][col].isFinite() || abs(normal[pivot][col]) < 1e-10) return null
        val swap = normal[col]; normal[col] = normal[pivot]; normal[pivot] = swap
        val divisor = normal[col][col]
        for (j in col..3) normal[col][j] /= divisor
        for (row in 0..2) if (row != col) {
            val factor = normal[row][col]
            for (j in col..3) normal[row][j] -= factor * normal[col][j]
        }
    }
    val result = Transform3D(rotation.copyOf().apply {
        for (row in 0..2) this[row * 4 + 3] = normal[row][3] - (0..2).sumOf { col -> rotation[row * 4 + col] * center[col] }
    })
    if (result.values.any { !it.isFinite() } || distanceAtCenter(priorCameraFromProject, result) > 30.0 ||
        distanceAtCenter(measuredCameraFromProject, result) > 8.0) return null
    val fittedError = anchorImageErrorPx(result, intrinsics, listOf(marker), listOf(detection), listOf(marker.id))
    return result.takeIf { fittedError != null && fittedError <= ANCHOR_IMAGE_HOLD_MAX_PX }
}
