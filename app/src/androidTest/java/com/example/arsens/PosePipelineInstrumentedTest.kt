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
}
