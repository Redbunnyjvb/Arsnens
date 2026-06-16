package com.example.arsens

import com.example.arsens.data.MmPosition
import com.example.arsens.data.OriginCorner
import com.example.arsens.data.coordinateMapper
import com.example.arsens.data.defaultFrameForOrigin
import com.example.arsens.data.Project
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateFrameMapperTest {
    private val dimensions = MmPosition(10_000, 5_000, 3_200)

    @Test
    fun originCornersMapOperatorZeroToPhysicalCorner() {
        assertEquals(MmPosition(0, 0, 0), mapper(OriginCorner.FrontLeftBottom).operatorToBox(MmPosition(0, 0, 0)))
        assertEquals(MmPosition(10_000, 0, 0), mapper(OriginCorner.FrontRightBottom).operatorToBox(MmPosition(0, 0, 0)))
        assertEquals(MmPosition(0, 5_000, 0), mapper(OriginCorner.BackLeftBottom).operatorToBox(MmPosition(0, 0, 0)))
        assertEquals(MmPosition(10_000, 5_000, 0), mapper(OriginCorner.BackRightBottom).operatorToBox(MmPosition(0, 0, 0)))
    }

    @Test
    fun defaultOriginAxesPointIntoTransformerBox() {
        assertEquals(MmPosition(0, 0, 0), mapper(OriginCorner.FrontLeftBottom).operatorToBox(MmPosition(0, 0, 0)))
        assertEquals(MmPosition(9_000, 0, 0), mapper(OriginCorner.FrontRightBottom).operatorToBox(MmPosition(1_000, 0, 0)))
        assertEquals(MmPosition(0, 4_000, 0), mapper(OriginCorner.BackLeftBottom).operatorToBox(MmPosition(0, 1_000, 0)))
        assertEquals(MmPosition(9_000, 4_000, 0), mapper(OriginCorner.BackRightBottom).operatorToBox(MmPosition(1_000, 1_000, 0)))
    }

    @Test
    fun operatorRoundTripKeepsPhysicalPointStable() {
        val mapper = mapper(OriginCorner.BackRightBottom)
        val physical = MmPosition(8_500, 3_750, 1_200)
        val operator = mapper.boxToOperator(physical)

        assertEquals(physical, mapper.operatorToBox(operator))
    }

    @Test
    fun rightPlanePresetCanStayPhysicalRightWithDifferentOrigin() {
        val mapper = mapper(OriginCorner.BackRightBottom)
        val physicalRight = MmPosition(dimensions.x, dimensions.y, 0)
        val operator = mapper.boxToOperator(physicalRight)

        assertEquals(0, operator.x)
        assertEquals(physicalRight, mapper.operatorToBox(operator))
    }

    private fun mapper(originCorner: OriginCorner) =
        Project(
            projectName = "test",
            modelFile = "",
            sensors = emptyList(),
            markers = emptyList(),
            dimensionsMm = dimensions,
            coordinateFrame = defaultFrameForOrigin(originCorner)
        ).coordinateMapper()
}
