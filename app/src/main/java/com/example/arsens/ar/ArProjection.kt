package com.example.arsens.ar

import com.example.arsens.data.MmPosition
import kotlin.math.abs
import kotlin.math.sqrt

data class ProjectPointMm(
    val x: Double,
    val y: Double,
    val z: Double
)

data class ScreenPointPx(
    val xPx: Float,
    val yPx: Float,
    val depthMm: Float
)

data class ArDisplayProjection(
    val displayWidthPx: Int,
    val displayHeightPx: Int,
    val clipFromTransformer: DoubleArray
) {
    init {
        require(displayWidthPx > 0) { "displayWidthPx must be positive" }
        require(displayHeightPx > 0) { "displayHeightPx must be positive" }
        require(clipFromTransformer.size == 16) { "clipFromTransformer requires 16 row-major values" }
    }

    fun project(point: ProjectPointMm): ScreenPointPx? {
        val x = point.x
        val y = point.y
        val z = point.z
        val clipX = clipFromTransformer[0] * x +
            clipFromTransformer[1] * y +
            clipFromTransformer[2] * z +
            clipFromTransformer[3]
        val clipY = clipFromTransformer[4] * x +
            clipFromTransformer[5] * y +
            clipFromTransformer[6] * z +
            clipFromTransformer[7]
        val clipW = clipFromTransformer[12] * x +
            clipFromTransformer[13] * y +
            clipFromTransformer[14] * z +
            clipFromTransformer[15]

        if (abs(clipW) < 1e-6 || clipW <= 0.0) return null

        val ndcX = clipX / clipW
        val ndcY = clipY / clipW
        return ScreenPointPx(
            xPx = ((ndcX + 1.0) * 0.5 * displayWidthPx).toFloat(),
            yPx = ((1.0 - ndcY) * 0.5 * displayHeightPx).toFloat(),
            depthMm = clipW.toFloat()
        )
    }

    fun centerRayInTransformer(): RayMm? = rayInTransformer(0.0, 0.0)

    /**
     * Straal door een willekeurig schermpunt in NDC (normalized device coordinates,
     * x,y ∈ [-1,1], midden = 0,0; +x = rechts, +y = omhoog). Hiermee kun je de plaatsings-cursor
     * van het schermmidden wegslepen (bv. naar de rand) terwijl de tag in beeld blijft.
     */
    fun rayInTransformer(ndcX: Double, ndcY: Double): RayMm? {
        val inverse = invert4x4(clipFromTransformer) ?: return null
        val near = multiplyHomogeneous(inverse, doubleArrayOf(ndcX, ndcY, -1.0, 1.0)) ?: return null
        val far = multiplyHomogeneous(inverse, doubleArrayOf(ndcX, ndcY, 1.0, 1.0)) ?: return null
        val direction = doubleArrayOf(
            far[0] - near[0],
            far[1] - near[1],
            far[2] - near[2]
        ).normalizedOrNull() ?: return null
        return RayMm(
            origin = near,
            direction = direction
        )
    }

    companion object {
        fun fromOpenGlCamera(
            displayWidthPx: Int,
            displayHeightPx: Int,
            projectionMatrixColumnMajor: FloatArray,
            cameraGlFromTransformer: Transform3D
        ): ArDisplayProjection {
            val projection = Transform3D.fromOpenGlColumnMajor(projectionMatrixColumnMajor)
            return ArDisplayProjection(
                displayWidthPx = displayWidthPx,
                displayHeightPx = displayHeightPx,
                clipFromTransformer = (projection * cameraGlFromTransformer).values
            )
        }
    }
}

private fun multiplyHomogeneous(matrix: DoubleArray, vector: DoubleArray): DoubleArray? {
    val x = matrix[0] * vector[0] + matrix[1] * vector[1] + matrix[2] * vector[2] + matrix[3] * vector[3]
    val y = matrix[4] * vector[0] + matrix[5] * vector[1] + matrix[6] * vector[2] + matrix[7] * vector[3]
    val z = matrix[8] * vector[0] + matrix[9] * vector[1] + matrix[10] * vector[2] + matrix[11] * vector[3]
    val w = matrix[12] * vector[0] + matrix[13] * vector[1] + matrix[14] * vector[2] + matrix[15] * vector[3]
    if (abs(w) < 1e-9) return null
    return doubleArrayOf(x / w, y / w, z / w)
}

private fun invert4x4(matrix: DoubleArray): DoubleArray? {
    val augmented = Array(4) { row ->
        DoubleArray(8) { col ->
            when {
                col < 4 -> matrix[row * 4 + col]
                col - 4 == row -> 1.0
                else -> 0.0
            }
        }
    }
    for (col in 0 until 4) {
        var pivotRow = col
        for (row in col + 1 until 4) {
            if (abs(augmented[row][col]) > abs(augmented[pivotRow][col])) {
                pivotRow = row
            }
        }
        if (abs(augmented[pivotRow][col]) < 1e-9) return null
        if (pivotRow != col) {
            val tmp = augmented[col]
            augmented[col] = augmented[pivotRow]
            augmented[pivotRow] = tmp
        }
        val pivot = augmented[col][col]
        for (index in 0 until 8) {
            augmented[col][index] /= pivot
        }
        for (row in 0 until 4) {
            if (row == col) continue
            val factor = augmented[row][col]
            for (index in 0 until 8) {
                augmented[row][index] -= factor * augmented[col][index]
            }
        }
    }
    return DoubleArray(16) { index ->
        val row = index / 4
        val col = index % 4
        augmented[row][col + 4]
    }
}

private fun DoubleArray.normalizedOrNull(): DoubleArray? {
    val length = sqrt(this[0] * this[0] + this[1] * this[1] + this[2] * this[2])
    if (length < 1e-9) return null
    return doubleArrayOf(this[0] / length, this[1] / length, this[2] / length)
}

fun projectPositionToScreen(
    position: MmPosition,
    result: AprilTagFrameResult,
    preferImagePose: Boolean = true
): ScreenPointPx? =
    projectPointToScreen(
        ProjectPointMm(position.x.toDouble(), position.y.toDouble(), position.z.toDouble()),
        result,
        preferImagePose
    )

fun projectPointToScreen(
    point: ProjectPointMm,
    result: AprilTagFrameResult,
    preferImagePose: Boolean = true
): ScreenPointPx? {
    val imageProjectionPose = result.imageProjectionPose
    val imageToViewMapper = result.imageToViewMapper
    if (preferImagePose && imageProjectionPose != null && imageToViewMapper != null) {
        val imagePoint = projectPointToImage(point, result)
        if (imagePoint != null) {
            val screenPoint = imageToViewMapper.map(imagePoint)
            return ScreenPointPx(
                xPx = screenPoint.xPx,
                yPx = screenPoint.yPx,
                depthMm = 0f
            )
        }
    }
    val displayPoint = result.displayProjection?.project(point)
    if (displayPoint != null) return displayPoint
    if (!preferImagePose && imageProjectionPose != null && imageToViewMapper != null) {
        val imagePoint = projectPointToImage(point, result)
        if (imagePoint != null) {
            val screenPoint = imageToViewMapper.map(imagePoint)
            return ScreenPointPx(
                xPx = screenPoint.xPx,
                yPx = screenPoint.yPx,
                depthMm = 0f
            )
        }
    }
    return null
}

fun projectPointWithFusedPoseToScreen(
    point: ProjectPointMm,
    result: AprilTagFrameResult
): ScreenPointPx? {
    result.displayProjection?.let { projection ->
        return projection.project(point)
    }
    val transformerPose = result.transformerPose ?: return null
    val imageToViewMapper = result.imageToViewMapper ?: return null
    val imagePoint = projectPointToImage(
        point = point,
        result = result.copy(imageProjectionPose = transformerPose)
    ) ?: return null
    val screenPoint = imageToViewMapper.map(imagePoint)
    return ScreenPointPx(
        xPx = screenPoint.xPx,
        yPx = screenPoint.yPx,
        depthMm = 0f
    )
}
