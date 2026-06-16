package com.example.arsens.ar

import kotlin.math.sqrt

data class ImageToViewMapper(
    val imageWidth: Int,
    val imageHeight: Int,
    val displayWidth: Int,
    val displayHeight: Int,
    val topLeft: AprilTagCorner,
    val topRight: AprilTagCorner,
    val bottomLeft: AprilTagCorner
) {
    fun map(point: AprilTagCorner): AprilTagCorner {
        val u = if (imageWidth <= 0) 0f else point.xPx / imageWidth.toFloat()
        val v = if (imageHeight <= 0) 0f else point.yPx / imageHeight.toFloat()
        val xAxis = AprilTagCorner(topRight.xPx - topLeft.xPx, topRight.yPx - topLeft.yPx)
        val yAxis = AprilTagCorner(bottomLeft.xPx - topLeft.xPx, bottomLeft.yPx - topLeft.yPx)
        return AprilTagCorner(
            xPx = topLeft.xPx + xAxis.xPx * u + yAxis.xPx * v,
            yPx = topLeft.yPx + xAxis.yPx * u + yAxis.yPx * v
        )
    }

    /** Inverse van [map]: schermpunt (view-px) → beeldpixel. Het camerabeeld staat in
     *  sensororiëntatie en is t.o.v. het scherm meestal 90° gedraaid (en gecropt); deze
     *  inverse zet een schermcoördinaat om naar de bijbehorende pixel in dat beeld.
     *  Geeft null bij een gedegenereerde mapping. */
    fun unmap(point: AprilTagCorner): AprilTagCorner? {
        val xAxis = AprilTagCorner(topRight.xPx - topLeft.xPx, topRight.yPx - topLeft.yPx)
        val yAxis = AprilTagCorner(bottomLeft.xPx - topLeft.xPx, bottomLeft.yPx - topLeft.yPx)
        val det = xAxis.xPx * yAxis.yPx - xAxis.yPx * yAxis.xPx
        if (kotlin.math.abs(det) < 1e-6f) return null
        val dx = point.xPx - topLeft.xPx
        val dy = point.yPx - topLeft.yPx
        val u = (dx * yAxis.yPx - dy * yAxis.xPx) / det
        val v = (dy * xAxis.xPx - dx * xAxis.yPx) / det
        return AprilTagCorner(
            xPx = u * imageWidth,
            yPx = v * imageHeight
        )
    }
}

fun mapDetectionsToView(
    detections: List<AprilTagDetection>,
    mapper: ImageToViewMapper
): List<AprilTagDetection> =
    detections.map { detection ->
        detection.copy(
            cornersPx = detection.cornersPx.map(mapper::map),
            centerPx = mapper.map(detection.centerPx)
        )
    }

fun ImageToViewMapper.cameraGlFromCameraCv(): Transform3D? {
    val imageXInView = doubleArrayOf(
        (topRight.xPx - topLeft.xPx).toDouble(),
        (topRight.yPx - topLeft.yPx).toDouble(),
        0.0
    )
    val imageYInView = doubleArrayOf(
        (bottomLeft.xPx - topLeft.xPx).toDouble(),
        (bottomLeft.yPx - topLeft.yPx).toDouble(),
        0.0
    )
    val cvXInGl = normalizedOrNull(
        doubleArrayOf(imageXInView[0], -imageXInView[1], 0.0)
    ) ?: return null
    val cvYInGl = normalizedOrNull(
        doubleArrayOf(imageYInView[0], -imageYInView[1], 0.0)
    ) ?: return null
    val cvZInGl = normalizedOrNull(cross(cvXInGl, cvYInGl)) ?: return null
    return Transform3D(
        doubleArrayOf(
            cvXInGl[0], cvYInGl[0], cvZInGl[0], 0.0,
            cvXInGl[1], cvYInGl[1], cvZInGl[1], 0.0,
            cvXInGl[2], cvYInGl[2], cvZInGl[2], 0.0,
            0.0, 0.0, 0.0, 1.0
        )
    )
}

private fun normalizedOrNull(vector: DoubleArray): DoubleArray? {
    val length = sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2])
    if (length < 1e-9) return null
    return doubleArrayOf(vector[0] / length, vector[1] / length, vector[2] / length)
}

private fun cross(a: DoubleArray, b: DoubleArray): DoubleArray =
    doubleArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0]
    )
