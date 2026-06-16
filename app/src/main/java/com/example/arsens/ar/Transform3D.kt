package com.example.arsens.ar

import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import kotlin.math.acos
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
        val rotation = Mat(3, 3, CvType.CV_64F).apply {
            put(0, 0, values[0], values[1], values[2])
            put(1, 0, values[4], values[5], values[6])
            put(2, 0, values[8], values[9], values[10])
        }
        val rvec = Mat()
        Calib3d.Rodrigues(rotation, rvec)
        val pose = TransformerPose(
            translationMm = floatArrayOf(values[3].toFloat(), values[7].toFloat(), values[11].toFloat()),
            rotationVector = FloatArray(3) { index ->
                rvec.get(index, 0)?.firstOrNull()?.toFloat() ?: 0f
            },
            reprojectionErrorPx = reprojectionErrorPx
        )
        rotation.release()
        rvec.release()
        return pose
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
            val rvec = Mat(3, 1, CvType.CV_64F).apply {
                put(0, 0, pose.rotationVector[0].toDouble())
                put(1, 0, pose.rotationVector[1].toDouble())
                put(2, 0, pose.rotationVector[2].toDouble())
            }
            val rotation = Mat()
            Calib3d.Rodrigues(rvec, rotation)
            val out = identity().values.copyOf()
            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    out[row * 4 + col] = rotation.get(row, col)?.firstOrNull() ?: 0.0
                }
            }
            out[3] = pose.translationMm[0].toDouble()
            out[7] = pose.translationMm[1].toDouble()
            out[11] = pose.translationMm[2].toDouble()
            rvec.release()
            rotation.release()
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
