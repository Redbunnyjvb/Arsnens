package com.example.arsens

import com.example.arsens.data.FloatVector
import com.example.arsens.data.Marker
import com.example.arsens.data.MmPosition
import com.example.arsens.data.asAprilTagCalibrationMarker
import com.example.arsens.data.isAprilTagCalibrationMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerTypeTest {
    @Test
    fun legacyArucoMarkersStillCountAsAprilTagCalibrationMarkers() {
        assertTrue(marker(type = "apriltag").isAprilTagCalibrationMarker())
        assertTrue(marker(type = "aruco").isAprilTagCalibrationMarker())
        assertTrue(marker(type = "APRILTAG").isAprilTagCalibrationMarker())
        assertFalse(marker(type = "qr").isAprilTagCalibrationMarker())
    }

    @Test
    fun inwardSurfaceRotationsAreCorrectedForOutsideCalibration() {
        val dimensions = MmPosition(10_000, 5_000, 3_200)

        // Front gecorrigeerd: een onveranderde front-tag (0,0,0) blijft 0; een oude gespiegelde
        // front-tag (0,0,180) wordt bij het lezen omgezet naar 0.
        val front = marker(type = "aruco", position = MmPosition(2_000, 0, 1_000), rotation = FloatVector(0f, 0f, 0f))
            .asAprilTagCalibrationMarker(dimensions)
        val legacyMirroredFront = marker(type = "aruco", position = MmPosition(2_000, 0, 1_000), rotation = FloatVector(0f, 0f, 180f))
            .asAprilTagCalibrationMarker(dimensions)
        // Back na de Rz=180 fix: een tag die al 180 is blijft 180; een huidige (niet-gedraaide) back-tag
        // op Rz=0 wordt bij het lezen omgezet naar 180.
        val back = marker(type = "apriltag", position = MmPosition(2_000, 5_000, 1_000), rotation = FloatVector(0f, 0f, 180f))
            .asAprilTagCalibrationMarker(dimensions)
        val legacyUnrotatedBack = marker(type = "apriltag", position = MmPosition(2_000, 5_000, 1_000), rotation = FloatVector(0f, 0f, 0f))
            .asAprilTagCalibrationMarker(dimensions)
        // Zijvlakken na de Left=-90/Right=+90 fix: de huidige canonieke waarde blijft staan; de oude
        // gespiegelde waarde (Left Rz=+90, Right Rz=-90) wordt bij het lezen omgezet.
        val right = marker(type = "apriltag", position = MmPosition(10_000, 2_000, 1_000), rotation = FloatVector(0f, 0f, 90f))
            .asAprilTagCalibrationMarker(dimensions)
        val legacyMirroredRight = marker(type = "apriltag", position = MmPosition(10_000, 2_000, 1_000), rotation = FloatVector(0f, 0f, -90f))
            .asAprilTagCalibrationMarker(dimensions)
        val left = marker(type = "apriltag", position = MmPosition(0, 2_000, 1_000), rotation = FloatVector(0f, 0f, -90f))
            .asAprilTagCalibrationMarker(dimensions)
        val legacyMirroredLeft = marker(type = "apriltag", position = MmPosition(0, 2_000, 1_000), rotation = FloatVector(0f, 0f, 90f))
            .asAprilTagCalibrationMarker(dimensions)
        // Top gecorrigeerd: een onveranderde top-tag (-90,0,0) blijft -90; een oude gespiegelde
        // top-tag (90,0,0) wordt bij het lezen omgezet naar -90.
        val top = marker(type = "apriltag", position = MmPosition(2_000, 2_000, 3_200), rotation = FloatVector(-90f, 0f, 0f))
            .asAprilTagCalibrationMarker(dimensions)
        val legacyMirroredTop = marker(type = "apriltag", position = MmPosition(2_000, 2_000, 3_200), rotation = FloatVector(90f, 0f, 0f))
            .asAprilTagCalibrationMarker(dimensions)

        assertEquals("apriltag", front.type)
        assertEquals(FloatVector(0f, 0f, 0f), front.rotationDeg)
        assertEquals(FloatVector(0f, 0f, 0f), legacyMirroredFront.rotationDeg)
        assertEquals(FloatVector(0f, 0f, 180f), back.rotationDeg)
        assertEquals(FloatVector(0f, 0f, 180f), legacyUnrotatedBack.rotationDeg)
        assertEquals(FloatVector(0f, 0f, 90f), right.rotationDeg)
        assertEquals(FloatVector(0f, 0f, 90f), legacyMirroredRight.rotationDeg)
        assertEquals(FloatVector(0f, 0f, -90f), left.rotationDeg)
        assertEquals(FloatVector(0f, 0f, -90f), legacyMirroredLeft.rotationDeg)
        assertEquals(FloatVector(-90f, 0f, 0f), top.rotationDeg)
        assertEquals(FloatVector(-90f, 0f, 0f), legacyMirroredTop.rotationDeg)
    }

    private fun marker(
        type: String,
        position: MmPosition = MmPosition(0, 0, 0),
        rotation: FloatVector = FloatVector(0f, 0f, 0f)
    ) =
        Marker(
            id = 1,
            type = type,
            sizeMm = 100,
            positionMm = position,
            rotationDeg = rotation
        )
}
