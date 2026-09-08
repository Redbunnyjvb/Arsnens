package com.example.arsens.ar

import com.example.arsens.data.FloatVector
import com.example.arsens.data.MmPosition
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

fun tagPlaneOutwardNormal(plane: TagPlane): FloatVector = when (plane) {
    TagPlane.Front -> FloatVector(0f, -1f, 0f)
    TagPlane.Back -> FloatVector(0f, 1f, 0f)
    TagPlane.Left -> FloatVector(-1f, 0f, 0f)
    TagPlane.Right -> FloatVector(1f, 0f, 0f)
    TagPlane.Top -> FloatVector(0f, 0f, 1f)
}

fun nearestTankPlane(position: MmPosition, dimensions: MmPosition): TagPlane = listOf(
    TagPlane.Front to kotlin.math.abs(position.y),
    TagPlane.Back to kotlin.math.abs(position.y - dimensions.y),
    TagPlane.Left to kotlin.math.abs(position.x),
    TagPlane.Right to kotlin.math.abs(position.x - dimensions.x),
    TagPlane.Top to kotlin.math.abs(position.z - dimensions.z)
).minBy { it.second }.first

/** A physical ring in millimeters, lying on the sensor's plane, not a fixed pixel circle. */
fun sensorTargetRing(center: MmPosition, normal: FloatVector, radiusMm: Int, segments: Int = 48): List<ProjectPointMm> {
    if (radiusMm <= 0 || segments < 3) return emptyList()
    val n = doubleArrayOf(normal.x.toDouble(), normal.y.toDouble(), normal.z.toDouble())
    fun length(v: DoubleArray) = sqrt(v.sumOf { it * it })
    fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]
    )
    val len = length(n)
    if (!len.isFinite() || len < 1e-6) return emptyList()
    for (i in 0..2) n[i] /= len
    val axis = if (kotlin.math.abs(n[2]) < 0.9) doubleArrayOf(0.0, 0.0, 1.0) else doubleArrayOf(1.0, 0.0, 0.0)
    val u = cross(n, axis)
    val uLen = length(u)
    for (i in 0..2) u[i] /= uLen
    val v = cross(n, u)
    return List(segments) { i ->
        val a = 2.0 * PI * i / segments
        val offset = DoubleArray(3) { radiusMm * (u[it] * cos(a) + v[it] * sin(a)) }
        ProjectPointMm(center.x + offset[0], center.y + offset[1], center.z + offset[2])
    }
}
