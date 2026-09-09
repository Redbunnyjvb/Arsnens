package com.example.arsens.ar

import com.example.arsens.data.Marker
import kotlin.math.abs
import kotlin.math.sqrt

internal fun tagDistanceMm(cameraCvFromTransformer: Transform3D, marker: Marker): Float? {
    val p = marker.positionMm
    val cameraPoint = cameraCvFromTransformer.transformPoint(doubleArrayOf(p.x.toDouble(), p.y.toDouble(), p.z.toDouble()))
    if (cameraPoint.any { !it.isFinite() } || cameraPoint[2] <= 0.0) return null
    return sqrt(cameraPoint.sumOf { it * it }).toFloat().takeIf { it.isFinite() }
}

/** Carries measured pixels from their capture to the current view using the observed tag
 * plane and camera motion. The fused object anchor is deliberately not an input: disagreement
 * must stay visible. Only fixed reference tags with a measured pose can be transported. */
internal fun projectTagObservationToCurrentView(
    detection: AprilTagDetection,
    marker: Marker,
    cameraCvFromTransformerAtCapture: Transform3D,
    intrinsics: CameraIntrinsics,
    currentProjectionFromCaptureCamera: ArDisplayProjection
): AprilTagDetection? {
    if (detection.cornersPx.size != 4 || intrinsics.fx <= 0f || intrinsics.fy <= 0f) return null
    val corners = markerCornersInProjectFrame(marker).map {
        cameraCvFromTransformerAtCapture.transformPoint(doubleArrayOf(it.x, it.y, it.z))
    }
    val a = DoubleArray(3) { corners[1][it] - corners[0][it] }
    val b = DoubleArray(3) { corners[3][it] - corners[0][it] }
    val normal = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    val numerator = normal.indices.sumOf { normal[it] * corners[0][it] }
    fun project(pixel: AprilTagCorner): AprilTagCorner? {
        val ray = doubleArrayOf(((pixel.xPx - intrinsics.cx) / intrinsics.fx).toDouble(),
            ((pixel.yPx - intrinsics.cy) / intrinsics.fy).toDouble(), 1.0)
        val denominator = normal.indices.sumOf { normal[it] * ray[it] }
        if (abs(denominator) < 1e-9) return null
        val depth = numerator / denominator
        if (!depth.isFinite() || depth <= 0.0) return null
        val screen = currentProjectionFromCaptureCamera.project(ProjectPointMm(ray[0] * depth, ray[1] * depth, depth)) ?: return null
        if (!screen.xPx.isFinite() || !screen.yPx.isFinite()) return null
        return AprilTagCorner(screen.xPx, screen.yPx)
    }
    val projected = detection.cornersPx.map { project(it) ?: return null }
    return detection.copy(cornersPx = projected, centerPx = project(detection.centerPx) ?: return null)
}
