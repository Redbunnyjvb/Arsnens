package com.example.arsens

import com.example.arsens.ar.AprilTagFrameResult
import com.example.arsens.ar.ArTrackingStatus
import com.example.arsens.ar.CameraIntrinsics
import com.example.arsens.ar.RayMm
import com.example.arsens.ar.Transform3D
import com.example.arsens.ar.TransformerPose
import com.example.arsens.ar.computePlacementQuality
import com.example.arsens.ar.reprojectPlacementRay
import com.example.arsens.data.MmPosition
import com.example.arsens.data.QualityGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementQualityTest {

    @Test
    fun conflictingReferencesNeverShowGreenEvenWithPerfectReprojection() {
        val result = AprilTagFrameResult(transformerPose = pose(0f), anchorSettled = true,
            referenceConflict = true, trackingStatus = ArTrackingStatus.TagCalibration)
        val quality = computePlacementQuality(result, 0f, true, 1)
        assertEquals(QualityGrade.Unsafe, quality.grade)
        assertTrue(quality.reasons.any { it.contains("spreken elkaar tegen") })
    }

    @Test
    fun originDistanceIsNotUsedAsTagDepth() {
        val result = AprilTagFrameResult(
            transformerPose = TransformerPose(floatArrayOf(3000f, 0f, 2000f), floatArrayOf(0f, 0f, 0f), 2f),
            referenceDepthMm = 2000f, cameraIntrinsics = CameraIntrinsics(1000f, 1000f, 640f, 480f),
            anchorSettled = true, trackingStatus = ArTrackingStatus.TagCalibration
        )
        assertEquals(4f, computePlacementQuality(result, 0f, true, 1).reprojectionErrorMm!!, 0.001f)
        assertNull(computePlacementQuality(result.copy(referenceDepthMm = null), 0f, true, 1).reprojectionErrorMm)
    }

    private fun pose(reprojectionErrorPx: Float, distanceMm: Float = 1000f): TransformerPose =
        TransformerPose(
            translationMm = floatArrayOf(0f, 0f, distanceMm),
            rotationVector = floatArrayOf(0f, 0f, 0f),
            reprojectionErrorPx = reprojectionErrorPx
        )

    @Test
    fun freshTagCalibration_lowReproj_isHigh_noReasons() {
        val result = AprilTagFrameResult(
            anchorSettled = true,
            transformerPose = pose(1f),
            trackingStatus = ArTrackingStatus.TagCalibration,
            trackingQualityPercent = 95,
            detectionAgeMillis = 0L,
            poseMarkerIds = listOf(14)
        )
        val q = computePlacementQuality(result, jitterMm = 2f, isStableLock = true, referenceTagId = 14)
        assertEquals(QualityGrade.High, q.grade)
        assertEquals(14, q.referenceTagId)
        assertTrue(q.reasons.isEmpty())
    }

    @Test
    fun driftPossible_isLow() {
        val result = AprilTagFrameResult(
            anchorSettled = true,
            transformerPose = pose(3f),
            trackingStatus = ArTrackingStatus.DriftPossible,
            trackingQualityPercent = 45,
            detectionAgeMillis = 2_000L,
            poseMarkerIds = emptyList()
        )
        val q = computePlacementQuality(result, jitterMm = null, isStableLock = false, referenceTagId = 14)
        assertEquals(QualityGrade.Low, q.grade)
    }

    @Test
    fun recentArCoreTracking_isMedium() {
        val result = AprilTagFrameResult(
            anchorSettled = true,
            transformerPose = pose(5f),
            trackingStatus = ArTrackingStatus.ArCoreTracking,
            trackingQualityPercent = 75,
            detectionAgeMillis = 1_000L,
            poseMarkerIds = listOf(7)
        )
        val q = computePlacementQuality(result, jitterMm = 10f, isStableLock = false, referenceTagId = 7)
        assertEquals(QualityGrade.Medium, q.grade)
    }

    @Test
    fun highJitter_downgradesFromHigh() {
        val result = AprilTagFrameResult(
            anchorSettled = true,
            transformerPose = pose(1f),
            trackingStatus = ArTrackingStatus.TagCalibration,
            trackingQualityPercent = 95,
            detectionAgeMillis = 0L,
            poseMarkerIds = listOf(1)
        )
        val q = computePlacementQuality(result, jitterMm = 25f, isStableLock = false, referenceTagId = 1)
        assertTrue(q.grade != QualityGrade.High)
        assertTrue(q.reasons.any { it.contains("cursorbeweging") })
    }

    @Test
    fun noPose_isUnsafe() {
        val q = computePlacementQuality(
            AprilTagFrameResult(trackingStatus = ArTrackingStatus.NoPose),
            jitterMm = null,
            isStableLock = false,
            referenceTagId = null
        )
        assertEquals(QualityGrade.Unsafe, q.grade)
    }

    @Test
    fun needsRecalibration_isUnsafe() {
        val result = AprilTagFrameResult(
            anchorSettled = true,
            transformerPose = pose(2f),
            trackingStatus = ArTrackingStatus.NeedsRecalibration,
            trackingQualityPercent = 20,
            detectionAgeMillis = 5_000L
        )
        val q = computePlacementQuality(result, jitterMm = null, isStableLock = false, referenceTagId = null)
        assertEquals(QualityGrade.Unsafe, q.grade)
    }

    @Test
    fun reprojectionMm_isPhysicalConversion_pxTimesDistanceOverFx() {
        // 2 px op 1500 mm met fx 1400 → 2 * 1500 / 1400 ≈ 2.14 mm.
        val result = AprilTagFrameResult(
            anchorSettled = true,
            transformerPose = pose(2f, distanceMm = 1500f),
            referenceDepthMm = 1500f,
            cameraIntrinsics = CameraIntrinsics(fx = 1400f, fy = 1400f, cx = 640f, cy = 360f),
            trackingStatus = ArTrackingStatus.TagCalibration,
            trackingQualityPercent = 95,
            detectionAgeMillis = 0L,
            poseMarkerIds = listOf(1)
        )
        val q = computePlacementQuality(result, jitterMm = 0f, isStableLock = true, referenceTagId = 1)
        assertEquals(2.142857f, q.reprojectionErrorMm!!, 0.05f)
    }

    @Test
    fun reprojectionMm_isNull_withoutIntrinsics() {
        val result = AprilTagFrameResult(
            anchorSettled = true,
            transformerPose = pose(2f),
            trackingStatus = ArTrackingStatus.TagCalibration,
            trackingQualityPercent = 95,
            detectionAgeMillis = 0L
        )
        val q = computePlacementQuality(result, jitterMm = 0f, isStableLock = true, referenceTagId = null)
        assertNull(q.reprojectionErrorMm)
    }

    @Test
    fun reprojectPlacementRay_identityAnchors_hitsFrontFace() {
        // Straal langs +Y vanaf buiten (y<0) → raakt het voorvlak y=0; identity-ankers = onveranderd.
        val ray = RayMm(origin = doubleArrayOf(500.0, -100.0, 300.0), direction = doubleArrayOf(0.0, 1.0, 0.0))
        val identity = Transform3D.identity()
        val pos = reprojectPlacementRay(ray, identity, identity, MmPosition(1000, 1000, 1000), referenceMarker = null)
        assertNotNull(pos)
        assertEquals(500, pos!!.x)
        assertEquals(0, pos.y)
        assertEquals(300, pos.z)
    }

    @Test
    fun reprojectPlacementRay_shiftedAnchor_shiftsCorrectedPosition() {
        // anchorNow 100 mm in +X verschoven t.o.v. plaatsing → de straal valt 100 mm lager in X
        // in het gecorrigeerde frame.
        val ray = RayMm(origin = doubleArrayOf(500.0, -100.0, 300.0), direction = doubleArrayOf(0.0, 1.0, 0.0))
        val identity = Transform3D.identity()
        val shifted = Transform3D(
            doubleArrayOf(
                1.0, 0.0, 0.0, 100.0,
                0.0, 1.0, 0.0, 0.0,
                0.0, 0.0, 1.0, 0.0,
                0.0, 0.0, 0.0, 1.0
            )
        )
        val pos = reprojectPlacementRay(ray, identity, shifted, MmPosition(1000, 1000, 1000), referenceMarker = null)
        assertNotNull(pos)
        assertEquals(400, pos!!.x)
        assertEquals(0, pos.y)
    }
}
