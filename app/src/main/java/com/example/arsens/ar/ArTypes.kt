package com.example.arsens.ar

/** Camera-pose t.o.v. het transformerframe (solvePnP-resultaat, mm + Rodrigues-rotatie). */
data class TransformerPose(
    val translationMm: FloatArray,
    val rotationVector: FloatArray,
    val reprojectionErrorPx: Float
)

data class CameraIntrinsics(
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float
)
