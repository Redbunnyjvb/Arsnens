package com.example.arsens

import com.example.arsens.ar.*
import com.example.arsens.data.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import com.google.ar.core.TrackingState

/** Exercises production fusion, projection and the production placement gate without a device. */
class ArCoreFusionRegressionTest {
    private val identity = Transform3D.identity()
    private val intrinsics = CameraIntrinsics(1000f, 1000f, 640f, 480f)
    private val marker = Marker(0, "apriltag", 47, MmPosition(3933, 0, 0), tagRotationFor(TagPlane.Front))
    private val center = doubleArrayOf(3933.0, 0.0, 0.0)
    private val camera = Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(-3933f, 0f, 600f),
        floatArrayOf((Math.PI / 2).toFloat(), 0f, 0f), 0.3f))
    // Finite 10 mm / 100 m clipping planes, like the real ARCore projection.
    private val projection = floatArrayOf(1.5625f,0f,0f,0f, 0f,2.0833333f,0f,0f, 0f,0f,-1.00020002f,-1f, 0f,0f,-20.0020002f,0f)

    private fun shifted(x: Double) = Transform3D(identity.values.copyOf().apply { this[3] = x })
    private fun rotatedAtTag(degrees: Double): Transform3D {
        val rotation = Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(0f, 0f, 0f),
            floatArrayOf(0f, 0f, Math.toRadians(degrees).toFloat()), 0.3f))
        val p = rotation.transformPoint(center)
        return Transform3D(rotation.values.copyOf().apply { for (axis in 0..2) this[axis * 4 + 3] = center[axis] - p[axis] })
    }

    private fun packet(time: Long, rawAnchor: Transform3D = identity, actualAnchor: Transform3D = identity,
                       captureCamera: Transform3D = camera): AprilTagFrameResult {
        val image = markerCornersInProjectFrame(marker).map { point ->
            val p = (captureCamera * actualAnchor).transformPoint(doubleArrayOf(point.x, point.y, point.z))
            AprilTagCorner((1000.0 * p[0] / p[2] + 640).toFloat(), (1000.0 * p[1] / p[2] + 480).toFloat())
        }
        val measuredPose = (captureCamera * rawAnchor).toTransformerPose(0.3f)
        return AprilTagFrameResult(imageWidth = 1280, imageHeight = 960, cameraIntrinsics = intrinsics,
            detections = listOf(AprilTagDetection(0, image, AprilTagCorner(640f, 480f))),
            transformerPose = measuredPose, freshPosePerTag = mapOf(0 to measuredPose),
            poseMarkerIds = listOf(0), poseMarkerCount = 1, referenceMarkers = listOf(marker),
            referencePointMm = marker.positionMm, referenceDepthMm = 600f,
            capturedAtElapsedMillis = time, detectionSequence = time)
    }

    private fun fuse(fusion: ArCoreAprilTagFusion, data: AprilTagFrameResult, now: Long,
                     captureCamera: Transform3D = camera, currentCamera: Transform3D = captureCamera,
                     tracking: Boolean = true, persistentTrackingFrame: Boolean = false, trackingFrameId: Long = 0L): AprilTagFrameResult {
        val captureGl = captureCamera.inverseRigid() * Transform3D.cameraGlFromCameraCv()
        val currentGl = currentCamera.inverseRigid() * Transform3D.cameraGlFromCameraCv()
        return fusion.fuse(data, captureGl, currentGl, currentGl.inverseRigid(), null, projection,
            tracking, 1280, 960, now, persistentTrackingFrame, trackingFrameId)
    }

    private fun calibrated(persistentTrackingFrame: Boolean = false): ArCoreAprilTagFusion = ArCoreAprilTagFusion(logDecision = {}).also { fusion ->
        for (i in 1..5) {
            val time = i * 100L
            val result = fuse(fusion, packet(time), time + 40, persistentTrackingFrame = persistentTrackingFrame)
            if (i < 5) assertNull(result.displayProjection) else assertReady(result)
        }
    }

    private fun assertReady(result: AprilTagFrameResult) {
        val quality = computePlacementQuality(result, 1f, false, 0)
        assertNotNull(result.displayProjection)
        assertTrue(result.anchorSettled)
        assertNull(sensorPlacementBlockReason(result, quality))
    }

    private fun withSecondReference(data: AprilTagFrameResult, actual: Transform3D,
                                    captureCamera: Transform3D = camera): AprilTagFrameResult {
        val extra = marker.copy(id = 1, positionMm = marker.positionMm.copy(x = 4133))
        val image = markerCornersInProjectFrame(extra).map {
            val p = (captureCamera * actual).transformPoint(doubleArrayOf(it.x, it.y, it.z))
            AprilTagCorner((1000 * p[0] / p[2] + 640).toFloat(), (1000 * p[1] / p[2] + 480).toFloat())
        }
        return data.copy(referenceMarkers = listOf(marker, extra), poseMarkerIds = listOf(0, 1), poseMarkerCount = 2,
            detections = data.detections + AprilTagDetection(1, image, image.first()))
    }

    @Test fun agreeingAlternatingTagSetsAccumulateOrdinaryCorrectionEvidence() {
        val fusion = calibrated(true)
        var result = AprilTagFrameResult()
        for (time in 600L..2400L step 100L) {
            val data = packet(time, shifted(20.0), shifted(20.0))
            result = fuse(fusion, if (time % 200 == 0L) data else withSecondReference(data, shifted(20.0)),
                time + 40, persistentTrackingFrame = true)
        }
        assertReady(result)
        assertEquals(20.0, result.arFromTransformer!!.translation()[0], 1.0)
    }

    @Test fun contradictoryAlternatingTagSetsDoNotAccumulateCorrectionEvidence() {
        val fusion = calibrated(true)
        var result = AprilTagFrameResult()
        for (time in 600L..2400L step 100L) {
            val offset = shifted(if (time % 200 == 0L) 20.0 else -20.0)
            val data = packet(time, offset, offset)
            result = fuse(fusion, if (time % 200 == 0L) data else withSecondReference(data, offset),
                time + 40, persistentTrackingFrame = true)
        }
        assertEquals(ArTrackingStatus.NeedsRecalibration, result.trackingStatus)
        assertEquals(0.0, result.arFromTransformer!!.translation()[0], 0.001)
    }

    @Test fun pixelHoldDoesNotStopAnAlreadyConfirmedCorrectionShortOfItsTarget() {
        val fusion = calibrated(true)
        for (time in 600L..1500L step 100L) {
            fuse(fusion, packet(time, shifted(24.0), shifted(24.0)), time + 40, persistentTrackingFrame = true)
        }
        var result = AprilTagFrameResult()
        for (now in 1600L..2500L step 16L) result = fuse(fusion, AprilTagFrameResult(), now, persistentTrackingFrame = true)
        assertReady(result)
        assertEquals(24.0, result.arFromTransformer!!.translation()[0], 0.001)
    }

    @Test fun smallPixelResidualDoesNotHideConsistentDepthDrift() {
        val fusion = ArCoreAprilTagFusion(logDecision = {})
        val distantCamera = Transform3D(camera.values.copyOf().apply { this[11] = 1200.0 })
        for (time in 100L..500L step 100L) fuse(fusion, packet(time, captureCamera = distantCamera), time + 40,
            captureCamera = distantCamera, persistentTrackingFrame = true)
        val depthDrift = Transform3D(identity.values.copyOf().apply { this[7] = 20.0 })
        var result = AprilTagFrameResult()
        for (time in 600L..2500L step 100L) result = fuse(fusion,
            packet(time, depthDrift, depthDrift, distantCamera), time + 40,
            captureCamera = distantCamera, persistentTrackingFrame = true)
        assertReady(result)
        assertTrue(result.anchorImageErrorPx!! < 1.5f)
        assertEquals(20.0, result.arFromTransformer!!.translation()[1], 1.0)
    }

    @Test fun excludedReferenceCannotEnterTheFusionEvenWithAnOtherwiseValidPose() {
        val fusion = calibrated(true)
        for (time in 600L..2000L step 100L) {
            val result = fuse(fusion, packet(time, shifted(20.0), shifted(20.0)).copy(rejectedMarkerIds = listOf(0)),
                time + 40, persistentTrackingFrame = true)
            assertEquals(0.0, result.arFromTransformer!!.translation()[0], 0.001)
            assertNotNull(sensorPlacementBlockReason(result, computePlacementQuality(result, 1f, false, 0)))
        }
    }

    @Test fun staleConflictingPacketDoesNotInvalidateCurrentCalibration() {
        val fusion = calibrated(true)
        val result = fuse(fusion, packet(600).copy(referenceConflict = true), 1200, persistentTrackingFrame = true)
        assertReady(result)
        assertEquals("stale-detection", result.fusionReason)
    }

    @Test fun largeSingleReferenceJumpCanRecoverThroughSmallEvidenceOrMultipleReferences() {
        for (recoveryOffset in listOf(20.0, 70.0, 200.0)) {
            val fusion = calibrated(true)
            var result = AprilTagFrameResult()
            for (time in 600L..2000L step 100L) result = fuse(fusion, packet(time, shifted(70.0), shifted(70.0)),
                time + 40, persistentTrackingFrame = true)
            assertEquals(ArTrackingStatus.NeedsRecalibration, result.trackingStatus)
            for (time in 2100L..4200L step 100L) {
                val offset = shifted(recoveryOffset)
                val data = packet(time, offset, offset)
                result = fuse(fusion, if (recoveryOffset > 30) withSecondReference(data, offset) else data,
                    time + 40, persistentTrackingFrame = true)
            }
            assertReady(result)
            assertEquals(recoveryOffset, result.arFromTransformer!!.translation()[0], 1.0)
        }
    }

    @Test fun nativeAnchorKeepsPlacementAvailableForMinutesWithoutAnyVisibleTag() {
        val fusion = calibrated(persistentTrackingFrame = true)
        for (now in 600L..180_000L step 50L) {
            val result = fuse(fusion, AprilTagFrameResult(), now, persistentTrackingFrame = true)
            assertReady(result)
            assertNotNull(result.displayProjection!!.centerRayInTransformer())
            if (now > 11000L) {
                assertTrue(result.poseMarkerIds.isEmpty())
                assertEquals(ArTrackingStatus.ArCoreTracking, result.trackingStatus)
                assertTrue(result.calibrationAgeMillis > 10000L)
            }
        }
    }

    @Test fun pastCaptureMotionAndLiveCursorMovementDoNotBlockCurrentArPlacement() {
        val fusion = calibrated(persistentTrackingFrame = true)
        val movedCamera = camera * shifted(120.0)
        val result = fuse(fusion, packet(600), 640, currentCamera = movedCamera, persistentTrackingFrame = true)
        assertTrue(result.motionDuringDetectionMm!! > 30f)
        val quality = computePlacementQuality(result, jitterMm = 120f, isStableLock = false, referenceTagId = 0)
        assertEquals(QualityGrade.Medium, quality.grade)
        assertNull(sensorPlacementBlockReason(result, quality))
        val noTag = fuse(fusion, AprilTagFrameResult(), 12000, currentCamera = movedCamera, persistentTrackingFrame = true)
        assertReady(noTag)
    }

    @Test fun confirmedCorrectionFinishesAfterTheReferenceTagLeavesTheImage() {
        val fusion = calibrated(persistentTrackingFrame = true)
        for (time in listOf(600L, 700L, 800L)) {
            val captureCamera = camera * shifted((time - 600) / 5.0) * rotatedAtTag((time - 600) / 100.0)
            val result = fuse(fusion, packet(time, shifted(24.0), shifted(24.0), captureCamera), time + 40,
                captureCamera = captureCamera, currentCamera = captureCamera * shifted(20.0), persistentTrackingFrame = true)
            assertEquals(if (time < 800) "PENDING" else "ACCEPT", result.fusionEvent)
        }
        var result = AprilTagFrameResult()
        for (now in 850L..1600L step 16L) {
            result = fuse(fusion, AprilTagFrameResult(), now, persistentTrackingFrame = true)
        }
        assertReady(result)
        assertEquals(24.0, result.arFromTransformer!!.translation()[0], 0.1)
        assertTrue(result.poseMarkerIds.isEmpty())
    }

    @Test fun measuredCornersAndFusedPoseUseTheSameCurrentCameraBetweenDetectionPackets() {
        val fusion = calibrated(persistentTrackingFrame = true)
        val captureCamera = camera * shifted(40.0) * rotatedAtTag(8.0)
        val data = packet(600, captureCamera = captureCamera)
        for (now in 640L..840L step 20L) {
            val currentCamera = camera * shifted((now - 600) / 2.0) * rotatedAtTag((now - 600) / 10.0)
            val result = fuse(fusion, if (now == 640L) data else AprilTagFrameResult(), now,
                captureCamera = captureCamera, currentCamera = currentCamera, persistentTrackingFrame = true)
            assertReady(result)
            assertTrue(result.anchorImageErrorPx!! < 0.01f)
            assertNotNull(result.candidateDisplayProjection)
            val observed = result.trackedScreenDetections.single().cornersPx
            markerCornersInProjectFrame(marker).forEachIndexed { index, point ->
                val fused = result.displayProjection!!.project(point)!!
                val candidate = result.candidateDisplayProjection!!.project(point)!!
                assertEquals(fused.xPx, observed[index].xPx, 0.02f)
                assertEquals(fused.yPx, observed[index].yPx, 0.02f)
                assertEquals(fused.xPx, candidate.xPx, 0.02f)
                assertEquals(fused.yPx, candidate.yPx, 0.02f)
            }
            assertArrayEquals(identity.values, result.arFromTransformer!!.values, 0.001)
        }
        val expired = fuse(fusion, AprilTagFrameResult(), 900, persistentTrackingFrame = true)
        assertTrue(expired.trackedScreenDetections.isEmpty())
        assertNull(expired.candidateDisplayProjection)
        assertReady(expired)
    }

    @Test fun motionCompensationPreservesRealDisagreementWithTheFusedAnchor() {
        val fusion = calibrated(persistentTrackingFrame = true)
        val result = fuse(fusion, packet(600, shifted(24.0), shifted(24.0)), 640,
            currentCamera = camera * shifted(120.0), persistentTrackingFrame = true)
        val observed = result.trackedScreenDetections.single().cornersPx
        markerCornersInProjectFrame(marker).forEachIndexed { index, point ->
            val fused = result.displayProjection!!.project(point)!!
            val candidate = result.candidateDisplayProjection!!.project(point)!!
            assertEquals(40f, observed[index].xPx - fused.xPx, 0.02f)
            assertEquals(candidate.xPx, observed[index].xPx, 0.02f)
        }
        assertEquals("PENDING", result.fusionEvent)
        assertTrue(result.anchorImageErrorPx!! > 30f)
    }

    @Test fun debugObservationCompensationUsesDisplayOrientationAsWellAsPhysicalCameraMotion() {
        val fusion = calibrated(persistentTrackingFrame = true)
        val currentCamera = camera * shifted(50.0)
        val captureGl = camera.inverseRigid() * Transform3D.cameraGlFromCameraCv()
        val currentGl = currentCamera.inverseRigid() * Transform3D.cameraGlFromCameraCv()
        val portrait = Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(0f, 0f, 0f),
            floatArrayOf(0f, 0f, (Math.PI / 2).toFloat()), 0f))
        val result = fusion.fuse(packet(600), captureGl, currentGl, portrait * currentGl.inverseRigid(), null,
            projection, true, 1280, 960, 640, true)
        val observed = result.trackedScreenDetections.single().cornersPx
        markerCornersInProjectFrame(marker).forEachIndexed { index, point ->
            val fused = result.displayProjection!!.project(point)!!
            assertEquals(fused.xPx, observed[index].xPx, 0.02f)
            assertEquals(fused.yPx, observed[index].yPx, 0.02f)
        }
    }

    @Test fun nativeAnchorCanResumeAfterTrackingPauseWithoutAnotherTagScan() {
        val fusion = calibrated(persistentTrackingFrame = true)
        val paused = fuse(fusion, AprilTagFrameResult(), 800, tracking = false, persistentTrackingFrame = true)
        assertNull(paused.displayProjection)
        assertNotNull(sensorPlacementBlockReason(paused, computePlacementQuality(paused, 0f, false, 0)))
        val resumed = fuse(fusion, AprilTagFrameResult(), 12000, persistentTrackingFrame = true)
        assertReady(resumed)
        assertTrue(resumed.poseMarkerIds.isEmpty())
    }

    @Test fun pausedNativeAnchorKeepsDetectingInAnIsolatedFrameAndNeedsMultipleReferences() {
        var nextId = 100L
        val recovery = NativeAnchorRecovery { ++nextId }
        assertEquals(10L, recovery.captureFrameId(true, TrackingState.TRACKING, 10, 900))
        val captureFrame = recovery.captureFrameId(true, TrackingState.PAUSED, 10, 1000)!!
        assertNotEquals(10L, captureFrame)
        val worldCamera = camera.inverseRigid() * Transform3D.cameraGlFromCameraCv()
        for (time in 1100L..2400L step 100L) {
            assertFalse(recovery.observe(packet(time).copy(trackingFrameId = captureFrame), worldCamera, time + 40))
        }
        var ready = false
        for (time in 2500L..2900L step 100L) {
            ready = recovery.observe(withSecondReference(packet(time), identity).copy(trackingFrameId = captureFrame), worldCamera, time + 40)
            if (time < 2900) assertFalse(ready)
        }
        assertTrue(ready)
    }

    @Test fun recoveryDiscardsEvidenceAfterCameraLossNativeResumeStopOrFrameReplacement() {
        for (transition in listOf("camera-lost", "tracking", "stopped", "replaced")) {
            var nextId = 100L
            val recovery = NativeAnchorRecovery { ++nextId }
            val oldFrame = recovery.captureFrameId(true, TrackingState.PAUSED, 10, 1000)!!
            val worldCamera = camera.inverseRigid() * Transform3D.cameraGlFromCameraCv()
            for (time in 1100L..1400L step 100L) assertFalse(recovery.observe(
                withSecondReference(packet(time), identity).copy(trackingFrameId = oldFrame), worldCamera, time + 40))
            when (transition) {
                "camera-lost" -> assertNull(recovery.captureFrameId(false, TrackingState.PAUSED, 10, 1450))
                "tracking" -> assertEquals(10L, recovery.captureFrameId(true, TrackingState.TRACKING, 10, 1450))
                "stopped" -> assertNull(recovery.captureFrameId(true, TrackingState.STOPPED, 10, 1450))
                "replaced" -> recovery.captureFrameId(true, TrackingState.PAUSED, 11, 1450)
            }
            val newFrame = recovery.captureFrameId(true, TrackingState.PAUSED, if (transition == "replaced") 11 else 10, 1500)!!
            assertNotEquals(oldFrame, newFrame)
            assertFalse(recovery.observe(withSecondReference(packet(1600), identity).copy(trackingFrameId = oldFrame), worldCamera, 1640))
            assertFalse(recovery.observe(withSecondReference(packet(1700), identity).copy(trackingFrameId = newFrame), worldCamera, 1740))
        }
    }

    @Test fun nativeRecoveryToleratesABriefMissingTagButLongGapsNeedNewEvidence() {
        for (gap in listOf(300L, 1000L)) {
            val recovery = NativeAnchorRecovery { 101 }
            val frameId = recovery.captureFrameId(true, TrackingState.PAUSED, 10, 1000)!!
            val worldCamera = camera.inverseRigid() * Transform3D.cameraGlFromCameraCv()
            for (time in 1100L..1400L step 100L) recovery.observe(
                withSecondReference(packet(time), identity).copy(trackingFrameId = frameId), worldCamera, time + 40)
            assertFalse(recovery.observe(packet(1500).copy(trackingFrameId = frameId, transformerPose = null,
                poseMarkerIds = emptyList()), worldCamera, 1540))
            val nextTime = 1400L + gap
            val ready = recovery.observe(withSecondReference(packet(nextTime), identity).copy(trackingFrameId = frameId),
                worldCamera, nextTime + 40)
            if (gap == 1000L) assertFalse(ready) else {
                assertTrue(recovery.observe(withSecondReference(packet(2100), identity).copy(trackingFrameId = frameId),
                    worldCamera, 2140))
            }
        }
    }

    @Test fun recoveryRejectsRepeatedStaleConflictingExcludedAndInconsistentPackets() {
        val worldCamera = camera.inverseRigid() * Transform3D.cameraGlFromCameraCv()
        for (mode in listOf("repeat", "stale", "conflict", "excluded", "wrong-corners")) {
            val recovery = NativeAnchorRecovery { 101 }
            val frameId = recovery.captureFrameId(true, TrackingState.PAUSED, 10, 1000)!!
            for (time in 1100L..3000L step 100L) {
                val data = withSecondReference(packet(if (mode == "repeat") 1100 else time), identity).copy(trackingFrameId = frameId)
                val invalid = when (mode) {
                    "stale" -> data.copy(capturedAtElapsedMillis = time - 1000)
                    "conflict" -> data.copy(referenceConflict = true)
                    "excluded" -> data.copy(rejectedMarkerIds = listOf(0))
                    "wrong-corners" -> data.copy(transformerPose = (camera * shifted(40.0)).toTransformerPose(0.1f))
                    else -> data
                }
                assertFalse("$mode at $time", recovery.observe(invalid, worldCamera, time + 40))
            }
        }
    }

    @Test fun nearestTagSelectionStillUsesIndependentlyVerifiedReferencesForRecovery() {
        val fusion = calibrated(true)
        val recovery = NativeAnchorRecovery { 101 }
        val frameId = recovery.captureFrameId(true, TrackingState.PAUSED, 10, 600)!!
        val worldCamera = camera.inverseRigid() * Transform3D.cameraGlFromCameraCv()
        var recovered = false
        var result = AprilTagFrameResult()
        for (time in 700L..2400L step 100L) {
            val data = withSecondReference(packet(time, shifted(70.0), shifted(70.0)), shifted(70.0))
                .copy(poseMarkerIds = listOf(0), poseMarkerCount = 1, verifiedReferenceIds = listOf(0, 1))
            result = fuse(fusion, data, time + 40, persistentTrackingFrame = true)
            recovered = recovery.observe(data.copy(trackingFrameId = frameId), worldCamera, time + 40) || recovered
        }
        assertReady(result)
        assertEquals(70.0, result.arFromTransformer!!.translation()[0], 1.0)
        assertTrue(recovered)
    }

    @Test fun fusionCannotConsumeOldFramesAfterNativeAnchorReplacement() {
        val fusion = calibrated(true)
        var result = fuse(fusion, packet(600), 640, persistentTrackingFrame = true, trackingFrameId = 20)
        assertNull(result.displayProjection)
        for (time in 700L..1100L step 100L) result = fuse(fusion, packet(time).copy(trackingFrameId = 20), time + 40,
            persistentTrackingFrame = true, trackingFrameId = 20)
        assertReady(result)
        val before = result.arFromTransformer!!.values.copyOf()
        result = fuse(fusion, packet(1200, shifted(20.0), shifted(20.0)), 1240, persistentTrackingFrame = true, trackingFrameId = 20)
        assertReady(result)
        assertArrayEquals(before, result.arFromTransformer!!.values, 0.001)
        assertEquals(1100L, result.detectionSequence)
        assertEquals(20L, result.trackingFrameId)
    }

    @Test fun briefReferenceGapDoesNotLoseCorrectionEvidenceAndPausedTrackingDoesNotAdvanceIt() {
        val fusion = calibrated(true)
        fuse(fusion, packet(600, shifted(24.0), shifted(24.0)), 640, persistentTrackingFrame = true)
        fuse(fusion, packet(700, shifted(24.0), shifted(24.0)), 740, persistentTrackingFrame = true)
        fuse(fusion, packet(800).copy(transformerPose = null, poseMarkerIds = emptyList()), 840, persistentTrackingFrame = true)
        var result = fuse(fusion, packet(900, shifted(24.0), shifted(24.0)), 940, persistentTrackingFrame = true)
        assertEquals("corrected", result.fusionReason)
        val executing = result.arFromTransformer!!.values.copyOf()
        for (time in 1000L..1600L step 100L) {
            result = fuse(fusion, AprilTagFrameResult(), time, tracking = false, persistentTrackingFrame = true)
            assertNull(result.displayProjection)
            assertArrayEquals(executing, result.arFromTransformer!!.values, 0.001)
        }
        for (time in 1700L..2700L step 20L) result = fuse(fusion, AprilTagFrameResult(), time, persistentTrackingFrame = true)
        assertReady(result)
        assertEquals(24.0, result.arFromTransformer!!.translation()[0], 0.001)
    }

    @Test fun visible47mmTagWithAlternatingLowErrorPosesStaysPlaceable() {
        val fusion = calibrated()
        for (i in 6..250) {
            val time = i * 100L
            val candidate = rotatedAtTag(if (i % 2 == 0) 2.5 else -2.0)
            val result = fuse(fusion, packet(time, rawAnchor = candidate), time + 40)
            assertReady(result)
            assertEquals("anchor-image-held", result.fusionReason)
            assertArrayEquals(identity.values, result.arFromTransformer!!.values, 1e-5)
            assertTrue(result.anchorImageErrorPx!! < 0.01f)
        }
    }

    @Test fun changingVisibleReferenceSetDoesNotRestartAConsistentInitialCalibration() {
        val fusion = ArCoreAprilTagFusion(logDecision = {})
        val extra = marker.copy(id = 1, positionMm = marker.positionMm.copy(x = 4033))
        val extraImage = markerCornersInProjectFrame(extra).map { point ->
            val p = camera.transformPoint(doubleArrayOf(point.x, point.y, point.z))
            AprilTagCorner((1000.0 * p[0] / p[2] + 640).toFloat(), (1000.0 * p[1] / p[2] + 480).toFloat())
        }
        for (i in 1..8) {
            val time = i * 100L
            val single = packet(time)
            val data = if (i % 2 == 0) single.copy(referenceMarkers = listOf(marker, extra),
                poseMarkerIds = listOf(0, 1), poseMarkerCount = 2,
                detections = single.detections + AprilTagDetection(1, extraImage, AprilTagCorner(800f, 480f))) else single
            val result = fuse(fusion, data, time + 40)
            if (i < 5) assertNull(result.displayProjection) else assertReady(result)
        }
    }

    @Test fun isolatedWrongImageDoesNotDisablePlacementButPersistentMovedTagDoes() {
        val fusion = calibrated()
        assertReady(fuse(fusion, packet(600, shifted(70.0), shifted(70.0)), 640))
        assertReady(fuse(fusion, packet(700), 740))
        var last = AprilTagFrameResult()
        for (i in 8..25) last = fuse(fusion, packet(i * 100L, shifted(70.0), shifted(70.0)), i * 100L + 40)
        assertEquals(ArTrackingStatus.NeedsRecalibration, last.trackingStatus)
        assertNotNull(sensorPlacementBlockReason(last, computePlacementQuality(last, 1f, false, 0)))
        assertArrayEquals(identity.values, last.arFromTransformer!!.values, 1e-5)
        assertReady(fuse(fusion, packet(2600), 2640))
    }

    @Test fun currentCameraMovesWhileImageValidationUsesItsCaptureCamera() {
        val fusion = calibrated()
        val movedCamera = camera * shifted(120.0)
        val result = fuse(fusion, packet(600), 640, currentCamera = movedCamera)
        assertEquals("anchor-image-held", result.fusionReason)
        assertTrue(result.anchorImageErrorPx!! < 0.01f)
        assertEquals(kotlin.math.hypot(600.0, 120.0).toFloat(), result.tagDistancesMm.getValue(0), 0.01f)
        assertArrayEquals(movedCamera.values, result.cameraCvFromTransformer!!.values, 1e-5)
        assertArrayEquals(camera.values, Transform3D.cameraCvFromTransformerPose(result.imageProjectionPose!!).values, 0.001)
        val predicted = fusion.predictedCameraPoseAt(movedCamera.inverseRigid() * Transform3D.cameraGlFromCameraCv())!!
        assertArrayEquals(movedCamera.values, Transform3D.cameraCvFromTransformerPose(predicted).values, 0.001)
    }

    @Test fun missingOrStaleTagDoesNotEraseAnchorButTrackingLossDoesRequireReacquisition() {
        val fusion = calibrated()
        assertReady(fuse(fusion, AprilTagFrameResult(), 800))
        assertReady(fuse(fusion, packet(600), 1000))
        val lost = fuse(fusion, packet(1100), 1140, tracking = false)
        assertNull(lost.displayProjection)
        assertNotNull(sensorPlacementBlockReason(lost, computePlacementQuality(lost, 0f, false, 0)))
        for (i in 12..16) {
            val result = fuse(fusion, packet(i * 100L), i * 100L + 40)
            if (i < 16) assertNull(result.displayProjection) else assertReady(result)
        }
    }

    @Test fun repeatedPacketDoesNotCreateEvidenceAndOldAnchorEventuallyExpiresForPlacement() {
        val fusion = ArCoreAprilTagFusion(logDecision = {})
        for (i in 0..6) assertNull(fuse(fusion, packet(100), 140 + i * 30L).displayProjection)
        val ready = calibrated()
        val expired = fuse(ready, AprilTagFrameResult(), 12000)
        assertNotNull(expired.displayProjection)
        assertEquals(ArTrackingStatus.DriftPossible, expired.trackingStatus)
        assertNotNull(sensorPlacementBlockReason(expired, computePlacementQuality(expired, 0f, false, 0)))
    }

    @Test fun conflictingReferencesBlockEvenWhenAValidAnchorExists() {
        val fusion = calibrated()
        val conflict = fuse(fusion, packet(600).copy(referenceConflict = true), 640)
        assertNotNull(conflict.displayProjection)
        assertNotNull(sensorPlacementBlockReason(conflict, computePlacementQuality(conflict, 0f, false, 0)))
        val afterTagsDisappear = fuse(fusion, AprilTagFrameResult(), 1500)
        assertNotNull(sensorPlacementBlockReason(afterTagsDisappear, computePlacementQuality(afterTagsDisappear, 0f, false, 0)))
        assertReady(fuse(fusion, packet(1600), 1640))
    }

    private fun openCvNoisePackets(): List<AprilTagFrameResult> {
        val text = javaClass.getResourceAsStream("/pose/small-tag-noise.json")!!.bufferedReader().use { it.readText() }
        val frames = JSONObject(text).getJSONArray("frames")
        return (0 until frames.length()).map { index ->
            val sample = frames.getJSONObject(index)
            val corners = sample.getJSONArray("corners")
            packet(600 + index * 100L).copy(
                transformerPose = TransformerPose(FloatArray(3) { sample.getJSONArray("t").getDouble(it).toFloat() },
                    FloatArray(3) { sample.getJSONArray("r").getDouble(it).toFloat() }, sample.getDouble("errorPx").toFloat()),
                detections = listOf(AprilTagDetection(0, (0..3).map {
                    val xy = corners.getJSONArray(it)
                    AprilTagCorner(xy.getDouble(0).toFloat(), xy.getDouble(1).toFloat())
                }, AprilTagCorner(640f, 480f)))
            )
        }
    }

    @Test fun actualOpenCvSmallTagNoiseCannotCauseTheLoggedPlaceableUnavailableCycle() {
        val fusion = calibrated()
        val packets = openCvNoisePackets()
        assertEquals(160, packets.size)
        for (data in packets) {
            val result = fuse(fusion, data, data.capturedAtElapsedMillis!! + 40)
            assertReady(result)
            assertEquals("anchor-image-held", result.fusionReason)
            assertArrayEquals(identity.values, result.arFromTransformer!!.values, 1e-5)
        }
    }

    @Test fun initialCalibrationCanBeAcquiredFromNoisyOpenCvPoses() {
        val fusion = ArCoreAprilTagFusion(logDecision = {})
        var readyAt: Long? = null
        var unavailableAfterReady = 0
        for (data in openCvNoisePackets()) {
            val result = fuse(fusion, data, data.capturedAtElapsedMillis!! + 40)
            val ready = sensorPlacementBlockReason(result, computePlacementQuality(result, 1f, false, 0)) == null
            if (ready && readyAt == null) readyAt = data.capturedAtElapsedMillis
            if (!ready && readyAt != null) unavailableAfterReady++
        }
        assertNotNull("No calibration acquired from 160 valid noisy observations", readyAt)
        assertTrue("Initial calibration took $readyAt ms", readyAt!! <= 2000L)
        assertEquals(0, unavailableAfterReady)
    }
}
