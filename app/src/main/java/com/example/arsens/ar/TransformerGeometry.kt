package com.example.arsens.ar

import com.example.arsens.data.FloatVector
import com.example.arsens.data.Marker
import com.example.arsens.data.MmPosition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class TransformerPlane(val axisIndex: Int) {
    Front(axisIndex = 1),
    Back(axisIndex = 1),
    Left(axisIndex = 0),
    Right(axisIndex = 0),
    Top(axisIndex = 2)
}

data class RayMm(
    val origin: DoubleArray,
    val direction: DoubleArray
)

data class PlaneHit(
    val position: MmPosition,
    val insideTransformerBox: Boolean
)

fun intersectTransformerBox(
    ray: RayMm,
    dimensionsMm: MmPosition
): PlaneHit? {
    val min = doubleArrayOf(0.0, 0.0, 0.0)
    val max = doubleArrayOf(
        dimensionsMm.x.toDouble(),
        dimensionsMm.y.toDouble(),
        dimensionsMm.z.toDouble()
    )
    var enter = Double.NEGATIVE_INFINITY
    var exit = Double.POSITIVE_INFINITY

    for (axis in 0..2) {
        val origin = ray.origin[axis]
        val direction = ray.direction[axis]
        if (kotlin.math.abs(direction) < 1e-6) {
            if (origin !in min[axis]..max[axis]) return null
            continue
        }
        val a = (min[axis] - origin) / direction
        val b = (max[axis] - origin) / direction
        val near = minOf(a, b)
        val far = maxOf(a, b)
        enter = maxOf(enter, near)
        exit = minOf(exit, far)
        if (enter > exit) return null
    }

    val distanceAlongRay = when {
        enter > 0.0 -> enter
        exit > 0.0 -> exit
        else -> return null
    }
    val point = DoubleArray(3) { index ->
        ray.origin[index] + distanceAlongRay * ray.direction[index]
    }
    val position = MmPosition(
        x = point[0].roundToInt().coerceIn(0, dimensionsMm.x),
        y = point[1].roundToInt().coerceIn(0, dimensionsMm.y),
        z = point[2].roundToInt().coerceIn(0, dimensionsMm.z)
    )
    return PlaneHit(
        position = position,
        insideTransformerBox = true
    )
}

fun planeCoordinateMm(plane: TransformerPlane, dimensionsMm: MmPosition): Float =
    when (plane) {
        TransformerPlane.Front -> 0f
        TransformerPlane.Back -> dimensionsMm.y.toFloat()
        TransformerPlane.Left -> 0f
        TransformerPlane.Right -> dimensionsMm.x.toFloat()
        TransformerPlane.Top -> dimensionsMm.z.toFloat()
    }

fun intersectTransformerPlane(
    ray: RayMm,
    plane: TransformerPlane,
    dimensionsMm: MmPosition
): PlaneHit? {
    val axis = plane.axisIndex
    val directionOnAxis = ray.direction[axis]
    if (kotlin.math.abs(directionOnAxis) < 1e-6) return null

    val planeMm = planeCoordinateMm(plane, dimensionsMm).toDouble()
    val distanceAlongRay = (planeMm - ray.origin[axis]) / directionOnAxis
    if (distanceAlongRay <= 0.0) return null

    val point = DoubleArray(3) { index ->
        ray.origin[index] + distanceAlongRay * ray.direction[index]
    }
    val position = MmPosition(
        x = point[0].roundToInt(),
        y = point[1].roundToInt(),
        z = point[2].roundToInt()
    )
    return PlaneHit(
        position = position,
        insideTransformerBox = position.x in 0..dimensionsMm.x &&
            position.y in 0..dimensionsMm.y &&
            position.z in 0..dimensionsMm.z
    )
}

fun markerCornersInProjectFrame(marker: Marker): List<ProjectPointMm> {
    val half = marker.sizeMm / 2.0
    val center = marker.positionMm
    return listOf(
        doubleArrayOf(-half, 0.0, half),  // Top Left
        doubleArrayOf(half, 0.0, half),   // Top Right
        doubleArrayOf(half, 0.0, -half),  // Bottom Right
        doubleArrayOf(-half, 0.0, -half)  // Bottom Left
    ).map { local ->
        val rotated = rotateLocalMarkerPoint(local, marker.rotationDeg)
        ProjectPointMm(
            x = center.x + rotated[0],
            y = center.y + rotated[1],
            z = center.z + rotated[2]
        )
    }
}

private fun rotateLocalMarkerPoint(point: DoubleArray, rotationDeg: FloatVector): DoubleArray {
    val rx = rotationDeg.x * PI / 180.0
    val ry = rotationDeg.y * PI / 180.0
    val rz = rotationDeg.z * PI / 180.0

    val afterX = doubleArrayOf(
        point[0],
        point[1] * cos(rx) - point[2] * sin(rx),
        point[1] * sin(rx) + point[2] * cos(rx)
    )
    val afterY = doubleArrayOf(
        afterX[0] * cos(ry) + afterX[2] * sin(ry),
        afterX[1],
        -afterX[0] * sin(ry) + afterX[2] * cos(ry)
    )
    return doubleArrayOf(
        afterY[0] * cos(rz) - afterY[1] * sin(rz),
        afterY[0] * sin(rz) + afterY[1] * cos(rz),
        afterY[2]
    )
}
