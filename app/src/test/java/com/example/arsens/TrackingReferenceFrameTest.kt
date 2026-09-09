package com.example.arsens

import com.example.arsens.ar.*
import org.junit.Assert.*
import org.junit.Test

class TrackingReferenceFrameTest {
    private fun transform(x: Float, zAngle: Float) = Transform3D.cameraCvFromTransformerPose(
        TransformerPose(floatArrayOf(x, 240f, 120f), floatArrayOf(0f, 0f, zAngle), 0f))

    @Test fun nativeWorldRefinementDoesNotChangeLocalCameraOrObjectCoordinates() {
        val worldFromReference = transform(3900f, 0.1f)
        val worldFromCamera = transform(3600f, 0.2f)
        val refinement = transform(150f, 0.3f)
        val before = TrackingReferenceFrame(worldFromReference)
        val after = TrackingReferenceFrame(refinement * worldFromReference)
        assertArrayEquals(before.cameraPose(worldFromCamera).values,
            after.cameraPose(refinement * worldFromCamera).values, 1e-8)
        assertArrayEquals(before.viewPose(worldFromCamera.inverseRigid()).values,
            after.viewPose((refinement * worldFromCamera).inverseRigid()).values, 1e-8)
    }

    @Test fun physicalCaptureCameraAndDisplayOrientedViewKeepTheirDifferentAxes() {
        val reference = TrackingReferenceFrame(transform(4000f, 0.2f))
        val camera = transform(3700f, -0.1f)
        val displayRotation = transform(0f, 1.57f).copy(values = transform(0f, 1.57f).values.copyOf().apply {
            this[3] = 0.0; this[7] = 0.0; this[11] = 0.0
        })
        val localCapture = reference.cameraPose(camera)
        val localDisplay = reference.viewPose(displayRotation * camera.inverseRigid())
        assertArrayEquals((displayRotation * localCapture.inverseRigid()).values, localDisplay.values, 1e-8)
    }

}
