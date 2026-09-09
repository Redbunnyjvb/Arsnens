package com.example.arsens

import com.example.arsens.ar.*
import com.example.arsens.data.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

class SensorTargetGeometryTest {
    @Test fun plannedRadiusIsFiftyMillimetersOnEveryOutsideFace() {
        val dimensions = MmPosition(1640, 930, 940)
        for (plane in TagPlane.entries) {
            val center = tagPositionFor(plane, 0.5f, 0.5f, dimensions)
            val normal = tagPlaneOutwardNormal(plane)
            val ring = sensorTargetRing(center, normal, 50)
            assertEquals(48, ring.size)
            for (point in ring) {
                val dx = point.x - center.x; val dy = point.y - center.y; val dz = point.z - center.z
                assertEquals(50.0, sqrt(dx * dx + dy * dy + dz * dz), 1e-8)
                assertEquals(0.0, dx * normal.x + dy * normal.y + dz * normal.z, 1e-8)
            }
        }
    }

    @Test fun operatorBottomLeftAlwaysMeansOutsideView() {
        val d = MmPosition(1640, 930, 940)
        val expected = mapOf(
            TagPlane.Front to MmPosition(0, 0, 0), TagPlane.Back to MmPosition(1640, 930, 0),
            TagPlane.Left to MmPosition(0, 930, 0), TagPlane.Right to MmPosition(1640, 0, 0),
            TagPlane.Top to MmPosition(0, 0, 940)
        )
        for ((plane, point) in expected) {
            val (u, v) = tagGridDisplayUvToCanonicalUv(plane, 0f, 0f)
            assertEquals(point, tagPositionFor(plane, u, v, d))
        }
    }

    @Test fun replayRetainsTheChosenTankFaceInsteadOfTheCalibrationTagFace() {
        val ray = RayMm(doubleArrayOf(500.0, -100.0, 2000.0), doubleArrayOf(0.0, 1.0, -2.0))
        val point = reprojectPlacementRay(ray, Transform3D.identity(), Transform3D.identity(),
            MmPosition(1000, 1000, 1000), null, TagPlane.Top)
        assertEquals(MmPosition(500, 400, 1000), point)
    }
}
