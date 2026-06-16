package com.example.arsens.data

data class CoordinateAxisLabels(
    val xPositive: String,
    val yPositive: String,
    val zPositive: String
)

class CoordinateFrameMapper(
    private val dimensionsMm: MmPosition,
    private val settings: CoordinateFrameSettings
) {
    private val origin = originInBox()
    private val xSign = if (settings.flipX) -1 else 1
    private val ySign = if (settings.flipY) -1 else 1
    private val zSign = if (settings.flipZ) -1 else 1

    val axisLabels: CoordinateAxisLabels
        get() = CoordinateAxisLabels(
            xPositive = if (settings.flipX) "X+ naar links" else "X+ naar rechts",
            yPositive = if (settings.flipY) "Y+ naar voren" else "Y+ naar achter",
            zPositive = if (settings.flipZ) "Z+ omlaag" else "Z+ omhoog"
        )

    fun operatorToBox(position: MmPosition): MmPosition =
        MmPosition(
            x = origin.x + position.x * xSign,
            y = origin.y + position.y * ySign,
            z = origin.z + position.z * zSign
        )

    fun boxToOperator(position: MmPosition): MmPosition =
        MmPosition(
            x = (position.x - origin.x) * xSign,
            y = (position.y - origin.y) * ySign,
            z = (position.z - origin.z) * zSign
        )

    fun originInBox(): MmPosition =
        when (settings.originCorner) {
            OriginCorner.FrontLeftBottom -> MmPosition(0, 0, 0)
            OriginCorner.FrontRightBottom -> MmPosition(dimensionsMm.x, 0, 0)
            OriginCorner.BackLeftBottom -> MmPosition(0, dimensionsMm.y, 0)
            OriginCorner.BackRightBottom -> MmPosition(dimensionsMm.x, dimensionsMm.y, 0)
        }
}

fun Project.coordinateMapper(): CoordinateFrameMapper =
    CoordinateFrameMapper(dimensionsMm, coordinateFrame)

fun MmPosition.toReadableMm(): String =
    "X=${x} mm, Y=${y} mm, Z=${z} mm"

fun defaultFrameForOrigin(originCorner: OriginCorner): CoordinateFrameSettings =
    CoordinateFrameSettings(
        originCorner = originCorner,
        flipX = originCorner == OriginCorner.FrontRightBottom ||
            originCorner == OriginCorner.BackRightBottom,
        flipY = originCorner == OriginCorner.BackLeftBottom ||
            originCorner == OriginCorner.BackRightBottom,
        flipZ = false
    )
