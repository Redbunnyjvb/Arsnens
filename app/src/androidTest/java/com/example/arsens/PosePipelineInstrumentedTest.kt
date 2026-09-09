package com.example.arsens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.arsens.ar.*
import com.example.arsens.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.calib3d.Calib3d
import org.opencv.core.*

/** Native OpenCV regression tests. No Activity, camera, project store or sensor log is opened. */
@RunWith(AndroidJUnit4::class)
class PosePipelineInstrumentedTest {
    private val intrinsics = CameraIntrinsics(1000f, 1000f, 640f, 480f)
    @Before fun loadOpenCv() { assertTrue(OpenCvRuntime.ensureLoaded()) }

    private fun frontPose() = TransformerPose(
        floatArrayOf(-820f, 500f, 2000f), floatArrayOf((Math.PI / 2).toFloat(), 0f, 0f), 0f
    )

    private fun project(marker: Marker, pose: TransformerPose): AprilTagDetection {
        val result = AprilTagFrameResult(imageWidth = 1280, imageHeight = 960,
            transformerPose = pose, imageProjectionPose = pose, cameraIntrinsics = intrinsics)
        val corners = markerCornersInProjectFrame(marker).map { projectPointToImage(it, result)!! }
        return AprilTagDetection(marker.id, corners, AprilTagCorner(
            corners.map { it.xPx }.average().toFloat(), corners.map { it.yPx }.average().toFloat()
        ))
    }

    @Test fun frontTagAtBottomMiddleProducesPose() {
        val marker = Marker(1, "apriltag", 100, MmPosition(820, 0, 50), tagRotationFor(TagPlane.Front))
        val truth = frontPose()
        val actual = estimateTransformerPoseFromAprilTags(listOf(project(marker, truth)), listOf(marker), intrinsics)
        assertNotNull(actual)
        assertTrue(actual!!.pose.reprojectionErrorPx < 0.1f)
        val expectedCamera = Transform3D.cameraCvFromTransformerPose(truth)
        val actualCamera = Transform3D.cameraCvFromTransformerPose(actual.pose)
        assertTrue(expectedCamera.distanceTo(actualCamera) < 2.0)
        assertTrue(expectedCamera.rotationAngleDegreesTo(actualCamera) < 0.1)
    }

    @Test fun threeReferencesExcludeOneMovedReference() {
        val markers = listOf(
            Marker(1, "apriltag", 100, MmPosition(150, 0, 100), tagRotationFor(TagPlane.Front)),
            Marker(2, "apriltag", 100, MmPosition(820, 0, 800), tagRotationFor(TagPlane.Front)),
            Marker(3, "apriltag", 100, MmPosition(1500, 0, 100), tagRotationFor(TagPlane.Front))
        )
        val detections = markers.map { marker ->
            project(if (marker.id == 3) marker.copy(positionMm = marker.positionMm.copy(x = 1550)) else marker, frontPose())
        }
        val selection = selectTransformerPoseFromAprilTags(detections, markers, intrinsics)
        assertNotNull(selection.estimate)
        assertEquals(listOf(1, 2), selection.estimate!!.markerIds)
        assertEquals(listOf(3), selection.rejectedIds)
    }

    @Test fun nearestTagPoseIsAlsoCheckedAgainstTheOtherVisibleReference() {
        val markers = listOf(
            Marker(1, "apriltag", 100, MmPosition(150, 0, 100), tagRotationFor(TagPlane.Front)),
            Marker(2, "apriltag", 100, MmPosition(820, 0, 800), tagRotationFor(TagPlane.Front)))
        val selection = selectTransformerPoseFromAprilTags(markers.map { project(it, frontPose()) }, markers,
            intrinsics, poseMode = TagPoseMode.NearestTag)
        assertNotNull(selection.estimate)
        assertEquals(1, selection.estimate!!.markerIds.size)
        assertEquals(listOf(1, 2), selection.consensusIds)
    }

    @Test fun staleCaptureAndRepeatedPacketCannotInitializeAnchor() {
        val fusion = ArCoreAprilTagFusion()
        val identity = Transform3D.identity()
        val projection = floatArrayOf(1f,0f,0f,0f, 0f,1f,0f,0f, 0f,0f,-1f,-1f, 0f,0f,-0.02f,0f)
        val packet = AprilTagFrameResult(transformerPose = frontPose(), poseMarkerIds = listOf(1),
            capturedAtElapsedMillis = 100L, detectionSequence = 1L)
        val old = fusion.fuse(packet, identity, identity, identity, null, projection, true, 1280, 960, 500L)
        assertNull(old.displayProjection)
        assertEquals("stale-detection", old.fusionReason)
        fusion.reset()
        repeat(6) { i ->
            val result = fusion.fuse(packet, identity, identity, identity, null, projection, true, 1280, 960, 100L + i * 30L)
            assertNull(result.displayProjection)
        }
    }

    @Test fun nativePlanarPoseNoiseWithSmallTranslationRecoversInProductionFusion() {
        val marker = Marker(0, "apriltag", 47, MmPosition(3933, 0, 0), tagRotationFor(TagPlane.Front))
        val camera = Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(-3933f, 0f, 600f),
            floatArrayOf((Math.PI / 2).toFloat(), 0f, 0f), 0f))
        val capture = camera.inverseRigid() * Transform3D.cameraGlFromCameraCv()
        val projection = floatArrayOf(1.5625f,0f,0f,0f, 0f,2.0833333f,0f,0f, 0f,0f,-1.0002f,-1f, 0f,0f,-20.002f,0f)
        val fusion = ArCoreAprilTagFusion(logDecision = {})
        fun feed(time: Long, detection: AprilTagDetection, measured: TransformerPose): AprilTagFrameResult {
            val packet = AprilTagFrameResult(cameraIntrinsics = intrinsics, referenceMarkers = listOf(marker),
                detections = listOf(detection), transformerPose = measured, poseMarkerIds = listOf(0),
                referencePointMm = marker.positionMm, referenceDepthMm = 600f, poseMarkerCount = 1,
                capturedAtElapsedMillis = time, detectionSequence = time)
            return fusion.fuse(packet, capture, capture, capture.inverseRigid(), null, projection, true,
                1280, 960, time + 40, persistentTrackingFrame = true)
        }
        for (time in 100L..500L step 100) feed(time, project(marker, camera.toTransformerPose(0f)), camera.toTransformerPose(0f))
        val truth = Transform3D(camera.values.copyOf().apply { this[3] += 20.0 }).toTransformerPose(0f)
        val random = java.util.Random(42)
        var constrained = 0
        var result = AprilTagFrameResult()
        repeat(100) { index ->
            val ideal = project(marker, truth)
            val observation = ideal.copy(cornersPx = ideal.cornersPx.map {
                AprilTagCorner(it.xPx + (random.nextGaussian() * 0.2).toFloat(), it.yPx + (random.nextGaussian() * 0.2).toFloat())
            })
            val selection = selectTransformerPoseFromAprilTags(listOf(observation), listOf(marker), intrinsics,
                priorPosePerTag = mapOf(0 to fusion.predictedCameraPoseAt(capture)!!))
            assertNotNull(selection.estimate)
            result = feed(600 + index * 100L, observation, selection.estimate!!.pose)
            if (result.fusionReason?.endsWith("rotation-held") == true) constrained++
            assertNotEquals(ArTrackingStatus.NeedsRecalibration, result.trackingStatus)
        }
        assertTrue("The native samples must exercise the constrained path", constrained > 0)
        assertNotNull(result.displayProjection)
        assertNull(sensorPlacementBlockReason(result, computePlacementQuality(result, 1f, false, 0)))
        val reference = doubleArrayOf(3933.0, 0.0, 0.0)
        val fitted = result.arFromTransformer!!.transformPoint(reference)
        assertEquals(3953.0, fitted[0], 3.0)
        assertTrue(result.arFromTransformer!!.rotationAngleDegreesTo(Transform3D.identity()) < 1.5)
    }
}
