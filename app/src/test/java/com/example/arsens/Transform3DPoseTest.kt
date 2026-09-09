package com.example.arsens

import com.example.arsens.ar.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt
import java.util.Random

class Transform3DPoseTest {
    @Test fun invalidRotationIsNotSilentlyConvertedToIdentity() {
        val matrix = Transform3D.cameraCvFromTransformerPose(TransformerPose(
            floatArrayOf(0f, 0f, 0f), floatArrayOf(Float.NaN, 0f, 0f), 0f))
        assertTrue(matrix.values.any { !it.isFinite() })
    }
    @Test fun knownQuarterTurnAndTranslationTransformAWorldPoint() {
        val matrix = Transform3D.cameraCvFromTransformerPose(TransformerPose(
            floatArrayOf(10f, 20f, 30f), floatArrayOf(0f, 0f, (Math.PI / 2).toFloat()), 0f))
        assertArrayEquals(doubleArrayOf(10.0, 21.0, 30.0), matrix.transformPoint(doubleArrayOf(1.0, 0.0, 0.0)), 1e-6)
        assertArrayEquals(Transform3D.identity().values, (matrix * matrix.inverseRigid()).values, 1e-9)
    }

    @Test fun conversionRoundTripsZeroPiNearPiAndRandomRotations() {
        val random = Random(913)
        for (degrees in listOf(0.0, 0.000001, 90.0, 179.9999, 180.0, 180.0001, 270.0, 359.9999)) {
            repeat(60) {
                val axis = DoubleArray(3) { random.nextDouble() * 2 - 1 }
                val length = sqrt(axis.sumOf { it * it })
                val matrix = Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(3933f, -120f, 75f),
                    FloatArray(3) { (axis[it] / length * Math.toRadians(degrees)).toFloat() }, 0.3f))
                val restored = Transform3D.cameraCvFromTransformerPose(matrix.toTransformerPose(0.3f))
                assertArrayEquals(matrix.values, restored.values, 5e-7)
            }
        }
    }

    @Test fun averagingAtADistantTagKeepsItsMeasuredCenterFixed() {
        val center = doubleArrayOf(3933.0, 0.0, 0.0)
        val rotated = Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(0f, 0f, 0f),
            floatArrayOf(0f, 0f, Math.toRadians(15.0).toFloat()), 0f))
        val target = Transform3D(rotated.values.copyOf().apply {
            val delta = rotated.transformPoint(center)
            for (axis in 0..2) this[axis * 4 + 3] = center[axis] - delta[axis]
        })
        val blended = Transform3D.identity().blendRigidAtPoint(target, 0.5, center)
        assertArrayEquals(center, blended.transformPoint(center), 1e-9)
        assertArrayEquals(Transform3D.identity().values, (blended * blended.inverseRigid()).values, 1e-9)
    }
}
