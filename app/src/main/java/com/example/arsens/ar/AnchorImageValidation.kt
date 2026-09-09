package com.example.arsens.ar

import com.example.arsens.data.Marker
import kotlin.math.hypot

/** Worst corner residual of every contributing reference, measured in the captured image. */
fun anchorImageErrorPx(
    cameraCvFromTransformer: Transform3D,
    intrinsics: CameraIntrinsics?,
    markers: List<Marker>,
    detections: List<AprilTagDetection>,
    contributingIds: List<Int>
): Float? {
    if (intrinsics == null || intrinsics.fx <= 0f || intrinsics.fy <= 0f || contributingIds.isEmpty()) return null
    val byId = markers.filter { it.active && it.sizeMm > 0 }.associateBy { it.id }
    var worst = 0.0
    for (id in contributingIds.distinct()) {
        val marker = byId[id] ?: return null
        val detection = detections.singleOrNull { it.id == id } ?: return null
        if (detection.cornersPx.size != 4) return null
        for ((point, pixel) in markerCornersInProjectFrame(marker).zip(detection.cornersPx)) {
            val camera = cameraCvFromTransformer.transformPoint(doubleArrayOf(point.x, point.y, point.z))
            if (camera.any { !it.isFinite() } || camera[2] <= 1.0) return null
            val x = intrinsics.fx * camera[0] / camera[2] + intrinsics.cx
            val y = intrinsics.fy * camera[1] / camera[2] + intrinsics.cy
            val error = hypot(x - pixel.xPx, y - pixel.yPx)
            if (!error.isFinite()) return null
            worst = maxOf(worst, error)
        }
    }
    return worst.toFloat()
}

// A consistency threshold, not an accuracy claim. No averaging across tags or corners.
internal const val ANCHOR_IMAGE_HOLD_MAX_PX = 1.5f
