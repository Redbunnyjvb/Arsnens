package com.example.arsens

import com.example.arsens.ar.RayMm
import com.example.arsens.ar.ProjectPointMm
import com.example.arsens.ar.TagPlane
import com.example.arsens.ar.TransformerPlane
import com.example.arsens.ar.intersectTransformerBox
import com.example.arsens.ar.markerCornersInProjectFrame
import com.example.arsens.ar.intersectTransformerPlane
import com.example.arsens.ar.tagRotationFor
import com.example.arsens.data.Marker
import com.example.arsens.data.MmPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransformerGeometryTest {
    private val dimensions = MmPosition(10_000, 5_000, 3_200)

    @Test
    fun rayHitsEveryTransformerPlaneInsideBox() {
        val front = intersectTransformerPlane(
            ray = RayMm(origin = doubleArrayOf(5_000.0, -1_000.0, 1_600.0), direction = doubleArrayOf(0.0, 1.0, 0.0)),
            plane = TransformerPlane.Front,
            dimensionsMm = dimensions
        )
        val back = intersectTransformerPlane(
            ray = RayMm(origin = doubleArrayOf(5_000.0, 6_000.0, 1_600.0), direction = doubleArrayOf(0.0, -1.0, 0.0)),
            plane = TransformerPlane.Back,
            dimensionsMm = dimensions
        )
        val left = intersectTransformerPlane(
            ray = RayMm(origin = doubleArrayOf(-1_000.0, 2_500.0, 1_600.0), direction = doubleArrayOf(1.0, 0.0, 0.0)),
            plane = TransformerPlane.Left,
            dimensionsMm = dimensions
        )
        val right = intersectTransformerPlane(
            ray = RayMm(origin = doubleArrayOf(11_000.0, 2_500.0, 1_600.0), direction = doubleArrayOf(-1.0, 0.0, 0.0)),
            plane = TransformerPlane.Right,
            dimensionsMm = dimensions
        )
        val top = intersectTransformerPlane(
            ray = RayMm(origin = doubleArrayOf(5_000.0, 2_500.0, 4_000.0), direction = doubleArrayOf(0.0, 0.0, -1.0)),
            plane = TransformerPlane.Top,
            dimensionsMm = dimensions
        )

        listOf(front, back, left, right, top).forEach {
            assertNotNull(it)
            assertTrue(it!!.insideTransformerBox)
        }
        assertEquals(0, front!!.position.y)
        assertEquals(5_000, back!!.position.y)
        assertEquals(0, left!!.position.x)
        assertEquals(10_000, right!!.position.x)
        assertEquals(3_200, top!!.position.z)
    }

    @Test
    fun rayHitsNearestTransformerBoxSurface() {
        val front = intersectTransformerBox(
            ray = RayMm(origin = doubleArrayOf(5_000.0, -1_000.0, 1_600.0), direction = doubleArrayOf(0.0, 1.0, 0.0)),
            dimensionsMm = dimensions
        )
        val top = intersectTransformerBox(
            ray = RayMm(origin = doubleArrayOf(5_000.0, 2_500.0, 4_000.0), direction = doubleArrayOf(0.0, 0.0, -1.0)),
            dimensionsMm = dimensions
        )
        val miss = intersectTransformerBox(
            ray = RayMm(origin = doubleArrayOf(12_000.0, -1_000.0, 1_600.0), direction = doubleArrayOf(0.0, 1.0, 0.0)),
            dimensionsMm = dimensions
        )

        assertEquals(MmPosition(5_000, 0, 1_600), front!!.position)
        assertEquals(MmPosition(5_000, 2_500, 3_200), top!!.position)
        assertNull(miss)
    }

    @Test
    fun rayHitOutsideTransformerBoxIsFlagged() {
        val hit = intersectTransformerPlane(
            ray = RayMm(origin = doubleArrayOf(12_000.0, -1_000.0, 1_600.0), direction = doubleArrayOf(0.0, 1.0, 0.0)),
            plane = TransformerPlane.Front,
            dimensionsMm = dimensions
        )

        assertNotNull(hit)
        assertFalse(hit!!.insideTransformerBox)
    }

    @Test
    fun parallelRayDoesNotHitPlane() {
        val hit = intersectTransformerPlane(
            ray = RayMm(origin = doubleArrayOf(5_000.0, -1_000.0, 1_600.0), direction = doubleArrayOf(1.0, 0.0, 0.0)),
            plane = TransformerPlane.Front,
            dimensionsMm = dimensions
        )

        assertNull(hit)
    }

    @Test
    fun markerPresetCornersStayOnTransformerPlanes() {
        val front = markerCornersFor(TagPlane.Front, MmPosition(5_000, 0, 1_600))
        assertTrue(front.all { kotlin.math.abs(it.y - 0.0) < 0.001 })

        val back = markerCornersFor(TagPlane.Back, MmPosition(5_000, dimensions.y, 1_600))
        assertTrue(back.all { kotlin.math.abs(it.y - dimensions.y.toDouble()) < 0.001 })

        val left = markerCornersFor(TagPlane.Left, MmPosition(0, 2_500, 1_600))
        assertTrue(left.all { kotlin.math.abs(it.x - 0.0) < 0.001 })

        val right = markerCornersFor(TagPlane.Right, MmPosition(dimensions.x, 2_500, 1_600))
        assertTrue(right.all { kotlin.math.abs(it.x - dimensions.x.toDouble()) < 0.001 })

        val top = markerCornersFor(TagPlane.Top, MmPosition(5_000, 2_500, dimensions.z))
        assertTrue(top.all { kotlin.math.abs(it.z - dimensions.z.toDouble()) < 0.001 })
    }

    @Test
    fun markerPresetNormalsPointOutsideTransformerPlanes() {
        assertNormal(TagPlane.Front, MmPosition(5_000, 0, 1_600), 0.0, -1.0, 0.0)
        assertNormal(TagPlane.Back, MmPosition(5_000, dimensions.y, 1_600), 0.0, 1.0, 0.0)
        assertNormal(TagPlane.Left, MmPosition(0, 2_500, 1_600), -1.0, 0.0, 0.0)
        assertNormal(TagPlane.Right, MmPosition(dimensions.x, 2_500, 1_600), 1.0, 0.0, 0.0)
        assertNormal(TagPlane.Top, MmPosition(5_000, 2_500, dimensions.z), 0.0, 0.0, 1.0)
    }

    private fun markerCornersFor(plane: TagPlane, position: MmPosition) =
        markerCornersInProjectFrame(
            Marker(
                id = 1,
                type = "apriltag",
                sizeMm = 100,
                positionMm = position,
                rotationDeg = tagRotationFor(plane)
            )
        )

    private fun assertNormal(
        plane: TagPlane,
        position: MmPosition,
        expectedX: Double,
        expectedY: Double,
        expectedZ: Double
    ) {
        val normal = normalOf(markerCornersFor(plane, position))
        assertEquals(expectedX, normal[0], 0.001)
        assertEquals(expectedY, normal[1], 0.001)
        assertEquals(expectedZ, normal[2], 0.001)
    }

    private fun normalOf(corners: List<ProjectPointMm>): DoubleArray {
        val a = doubleArrayOf(
            corners[1].x - corners[0].x,
            corners[1].y - corners[0].y,
            corners[1].z - corners[0].z
        )
        val b = doubleArrayOf(
            corners[3].x - corners[0].x,
            corners[3].y - corners[0].y,
            corners[3].z - corners[0].z
        )
        val cross = doubleArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        )
        val length = kotlin.math.sqrt(cross[0] * cross[0] + cross[1] * cross[1] + cross[2] * cross[2])
        return doubleArrayOf(cross[0] / length, cross[1] / length, cross[2] / length)
    }
}
