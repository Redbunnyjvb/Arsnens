package com.example.arsens.ar.calibration

import com.example.arsens.ar.Transform3D

/** Use ARCore gravity for roll/pitch while preserving the measured tag-center position. */
fun gravityAlignedReference(candidate: Transform3D, referenceUp: WallVector, seedUp: WallVector,
    observedCenter: WallVector): Transform3D {
    val a = candidate.wallDirection(referenceUp).unit() ?: return candidate
    val b = seedUp.unit() ?: return candidate
    val c = a.dot(b).coerceIn(-1.0,1.0)
    if (c < -0.99999) return candidate
    val v = a.cross(b)
    val k = arrayOf(doubleArrayOf(0.0,-v.z,v.y),doubleArrayOf(v.z,0.0,-v.x),doubleArrayOf(-v.y,v.x,0.0))
    val r = DoubleArray(16)
    for (i in 0..2) for (j in 0..2)
        r[i*4+j] = (if (i==j) 1.0 else 0.0) + k[i][j] + (0..2).sumOf { k[i][it]*k[it][j] } / (1.0+c)
    r[15]=1.0
    val pivot = candidate.wallPoint(observedCenter)
    val correction = Transform3D(r)
    val shift = pivot - correction.wallPoint(pivot)
    r[3]=shift.x; r[7]=shift.y; r[11]=shift.z
    return Transform3D(r) * candidate
}
