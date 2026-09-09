package com.example.arsens.ar

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Transform3D(
    val values: DoubleArray
) {
    init {
        require(values.size == 16) { "Transform3D requires 16 row-major values" }
    }

    operator fun times(other: Transform3D): Transform3D {
        val out = DoubleArray(16)
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                var sum = 0.0
                for (k in 0 until 4) {
                    sum += values[row * 4 + k] * other.values[k * 4 + col]
                }
                out[row * 4 + col] = sum
            }
        }
        return Transform3D(out)
    }

    fun inverseRigid(): Transform3D {
        val out = identity().values.copyOf()
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                out[row * 4 + col] = values[col * 4 + row]
            }
        }
        for (row in 0 until 3) {
            out[row * 4 + 3] = -(
                out[row * 4] * values[3] +
                    out[row * 4 + 1] * values[7] +
                    out[row * 4 + 2] * values[11]
                )
        }
        return Transform3D(out)
    }

    fun translation(): DoubleArray =
        doubleArrayOf(values[3], values[7], values[11])

    fun transformPoint(point: DoubleArray): DoubleArray =
        doubleArrayOf(
            values[0] * point[0] + values[1] * point[1] + values[2] * point[2] + values[3],
            values[4] * point[0] + values[5] * point[1] + values[6] * point[2] + values[7],
            values[8] * point[0] + values[9] * point[1] + values[10] * point[2] + values[11]
        )

    fun withScaledTranslation(scale: Double): Transform3D {
        val out = values.copyOf()
        out[3] *= scale
        out[7] *= scale
        out[11] *= scale
        return Transform3D(out)
    }

    fun forwardAxis(): DoubleArray =
        doubleArrayOf(values[2], values[6], values[10])

    /** Eerste kolom (camera +X / rechts) als richtingsvector. */
    fun rightAxis(): DoubleArray =
        doubleArrayOf(values[0], values[4], values[8])

    /** Tweede kolom (camera +Y) als richtingsvector. Let op: in de CV-cameraconventie wijst +Y
     *  omláág in het beeld. */
    fun upAxis(): DoubleArray =
        doubleArrayOf(values[1], values[5], values[9])

    fun distanceTo(other: Transform3D): Double {
        val dx = values[3] - other.values[3]
        val dy = values[7] - other.values[7]
        val dz = values[11] - other.values[11]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun rotationAngleDegreesTo(other: Transform3D): Double {
        var trace = 0.0
        for (row in 0 until 3) {
            var value = 0.0
            for (k in 0 until 3) {
                value += values[k * 4 + row] * other.values[k * 4 + row]
            }
            trace += value
        }
        val cosTheta = ((trace - 1.0) / 2.0).coerceIn(-1.0, 1.0)
        return acos(cosTheta) * 180.0 / Math.PI
    }

    fun blendRigidToward(target: Transform3D, alpha: Double): Transform3D {
        val clamped = alpha.coerceIn(0.0, 1.0)
        val blended = identity().values.copyOf()
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                blended[row * 4 + col] = values[row * 4 + col] * (1.0 - clamped) +
                    target.values[row * 4 + col] * clamped
            }
        }
        blended[3] = values[3] * (1.0 - clamped) + target.values[3] * clamped
        blended[7] = values[7] * (1.0 - clamped) + target.values[7] * clamped
        blended[11] = values[11] * (1.0 - clamped) + target.values[11] * clamped
        return Transform3D(blended).orthonormalized()
    }

    /** Blend around the observed reference, so a distant project origin cannot move it. */
    fun blendRigidAtPoint(target: Transform3D, alpha: Double, point: DoubleArray): Transform3D {
        val fraction = alpha.coerceIn(0.0, 1.0)
        val blended = blendRigidToward(target, fraction)
        val from = transformPoint(point)
        val to = target.transformPoint(point)
        val actual = blended.transformPoint(point)
        return Transform3D(blended.values.copyOf().apply {
            for (axis in 0..2) this[axis * 4 + 3] += from[axis] * (1.0 - fraction) + to[axis] * fraction - actual[axis]
        })
    }

    fun blendTranslationToward(target: Transform3D, alpha: Double): Transform3D {
        val clamped = alpha.coerceIn(0.0, 1.0)
        val out = values.copyOf()
        out[3] = values[3] * (1.0 - clamped) + target.values[3] * clamped
        out[7] = values[7] * (1.0 - clamped) + target.values[7] * clamped
        out[11] = values[11] * (1.0 - clamped) + target.values[11] * clamped
        return Transform3D(out)
    }

    fun withTranslationFrom(target: Transform3D): Transform3D {
        val out = values.copyOf()
        out[3] = target.values[3]
        out[7] = target.values[7]
        out[11] = target.values[11]
        return Transform3D(out)
    }

    private fun orthonormalized(): Transform3D {
        val x = normalized(doubleArrayOf(values[0], values[4], values[8]))
        val rawY = doubleArrayOf(values[1], values[5], values[9])
        val yWithoutX = subtract(rawY, scale(x, dot(rawY, x)))
        val y = normalized(yWithoutX)
        val z = cross(x, y)
        val out = identity().values.copyOf()
        out[0] = x[0]; out[4] = x[1]; out[8] = x[2]
        out[1] = y[0]; out[5] = y[1]; out[9] = y[2]
        out[2] = z[0]; out[6] = z[1]; out[10] = z[2]
        out[3] = values[3]
        out[7] = values[7]
        out[11] = values[11]
        return Transform3D(out)
    }

    fun toTransformerPose(reprojectionErrorPx: Float): TransformerPose {
        // Matrix -> quaternion -> rotation vector is stable at both zero and pi. This
        // conversion runs every display frame; it needs no native matrices or Android API.
        val m = values
        val trace = m[0] + m[5] + m[10]
        val q = if (trace > 0.0) {
            val s = sqrt(trace + 1.0) * 2.0
            doubleArrayOf(s / 4.0, (m[9] - m[6]) / s, (m[2] - m[8]) / s, (m[4] - m[1]) / s)
        } else if (m[0] > m[5] && m[0] > m[10]) {
            val s = sqrt(1.0 + m[0] - m[5] - m[10]) * 2.0
            doubleArrayOf((m[9] - m[6]) / s, s / 4.0, (m[1] + m[4]) / s, (m[2] + m[8]) / s)
        } else if (m[5] > m[10]) {
            val s = sqrt(1.0 + m[5] - m[0] - m[10]) * 2.0
            doubleArrayOf((m[2] - m[8]) / s, (m[1] + m[4]) / s, s / 4.0, (m[6] + m[9]) / s)
        } else {
            val s = sqrt(1.0 + m[10] - m[0] - m[5]) * 2.0
            doubleArrayOf((m[4] - m[1]) / s, (m[2] + m[8]) / s, (m[6] + m[9]) / s, s / 4.0)
        }
        if (q[0] < 0.0) for (i in q.indices) q[i] = -q[i]
        val length = sqrt(q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
        val factor = if (length < 1e-12) 2.0 else 2.0 * atan2(length, q[0]) / length
        return TransformerPose(
            translationMm = floatArrayOf(values[3].toFloat(), values[7].toFloat(), values[11].toFloat()),
            rotationVector = FloatArray(3) { (q[it + 1] * factor).toFloat() },
            reprojectionErrorPx = reprojectionErrorPx
        )
    }

    companion object {
        fun identity(): Transform3D =
            Transform3D(
                doubleArrayOf(
                    1.0, 0.0, 0.0, 0.0,
                    0.0, 1.0, 0.0, 0.0,
                    0.0, 0.0, 1.0, 0.0,
                    0.0, 0.0, 0.0, 1.0
                )
            )

        fun fromOpenGlColumnMajor(values: FloatArray): Transform3D {
            require(values.size >= 16)
            val out = DoubleArray(16)
            for (row in 0 until 4) {
                for (col in 0 until 4) {
                    out[row * 4 + col] = values[col * 4 + row].toDouble()
                }
            }
            return Transform3D(out)
        }

        fun cameraGlFromCameraCv(): Transform3D =
            Transform3D(
                doubleArrayOf(
                    1.0, 0.0, 0.0, 0.0,
                    0.0, -1.0, 0.0, 0.0,
                    0.0, 0.0, -1.0, 0.0,
                    0.0, 0.0, 0.0, 1.0
                )
            )

        fun cameraCvFromTransformerPose(pose: TransformerPose): Transform3D {
            val vector = pose.rotationVector.map { it.toDouble() }
            val angle = sqrt(vector.sumOf { it * it })
            val out = identity().values.copyOf()
            if (angle != 0.0) {
                val axis = vector.map { it / angle }
                val c = cos(angle)
                val s = sin(angle)
                val oneMinusC = 2.0 * sin(angle / 2.0) * sin(angle / 2.0)
                val skew = arrayOf(doubleArrayOf(0.0, -axis[2], axis[1]),
                    doubleArrayOf(axis[2], 0.0, -axis[0]), doubleArrayOf(-axis[1], axis[0], 0.0))
                for (row in 0..2) for (col in 0..2) {
                    out[row * 4 + col] = (if (row == col) c else 0.0) + axis[row] * axis[col] * oneMinusC + skew[row][col] * s
                }
            }
            out[3] = pose.translationMm[0].toDouble()
            out[7] = pose.translationMm[1].toDouble()
            out[11] = pose.translationMm[2].toDouble()
            return Transform3D(out)
        }
    }
}

private fun dot(a: DoubleArray, b: DoubleArray): Double =
    a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

private fun scale(vector: DoubleArray, factor: Double): DoubleArray =
    doubleArrayOf(vector[0] * factor, vector[1] * factor, vector[2] * factor)

private fun subtract(a: DoubleArray, b: DoubleArray): DoubleArray =
    doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])

private fun normalized(vector: DoubleArray): DoubleArray {
    val length = sqrt(dot(vector, vector)).coerceAtLeast(1e-9)
    return doubleArrayOf(vector[0] / length, vector[1] / length, vector[2] / length)
}

private fun cross(a: DoubleArray, b: DoubleArray): DoubleArray =
    normalized(
        doubleArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        )
    )
