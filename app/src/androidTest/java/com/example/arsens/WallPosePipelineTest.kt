package com.example.arsens

import com.example.arsens.ar.*
import com.example.arsens.ar.calibration.*
import com.example.arsens.data.*
import org.junit.Test
import org.junit.Assert.*

class WallPosePipelineTest {
    private val intrinsics=CameraIntrinsics(1000f,1000f,640f,480f)
    private val localCamera=Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(-30f,20f,800f),
        floatArrayOf((Math.PI/2+0.12).toFloat(),0.10f,0.05f),0f))
    private fun detection(marker: Marker, camera: Transform3D): AprilTagDetection {
        val result=AprilTagFrameResult(imageWidth=1280,imageHeight=960,transformerPose=camera.toTransformerPose(0f),cameraIntrinsics=intrinsics)
        val corners=markerCornersInProjectFrame(marker).map { projectPointToImage(it,result)!! }
        return AprilTagDetection(marker.id,corners,AprilTagCorner(corners.map{it.xPx}.average().toFloat(),corners.map{it.yPx}.average().toFloat()))
    }
    @Test fun unknownNativeTagPosesCalibrateWallsThenRelocalizeThroughExistingKnownTagFusion() {
        assertTrue(OpenCvRuntime.ensureLoaded())
        val geometry=WallTestGeometry
        val nativeTags=geometry.tags.map { tag ->
            val marker=Marker(tag.assignment.tagId,"apriltag",100,MmPosition(0,0,0),FloatVector(0f,0f,0f))
            val observed=solveStandaloneWallTags(listOf(detection(marker,localCamera)),intrinsics,WallScanRequest(1,100,emptyMap(),200)).single()
            val capture=tag.referenceFromTag*localCamera.inverseRigid()
            tag.copy(referenceFromTag=capture*observed.cameraCvFromTag)
        }
        val solution=WallCalibrationSolver.solve(MmPosition(0,0,0),WallDimensionSource.Scanned,nativeTags,geometry.datum).solution!!
        assertEquals(geometry.dimensions,solution.dimensionsMm)
        assertTrue(solution.referenceFromProject.distanceTo(geometry.frame)<2)
        // Reopening must work from either a side reference OR a top reference alone.
        for (tagIndex in listOf(0,8)) {
        val marker=solution.markers.first { it.id==tagIndex }
        val captureCv=geometry.tags[tagIndex].referenceFromTag*localCamera.inverseRigid()
        val cameraFromProject=captureCv.inverseRigid()*geometry.frame
        val seen=detection(marker,cameraFromProject)
        val estimate=estimateTransformerPoseFromAprilTags(listOf(seen),solution.markers,intrinsics)!!
        assertTrue(Transform3D.cameraCvFromTransformerPose(estimate.pose).distanceTo(cameraFromProject)<2)
        val captureGl=captureCv*Transform3D.cameraGlFromCameraCv()
        val projection=floatArrayOf(1.5625f,0f,0f,0f, 0f,2.0833333f,0f,0f, 0f,0f,-1.0002f,-1f, 0f,0f,-20.002f,0f)
        val fusion=ArCoreAprilTagFusion(logDecision={})
        var fused=AprilTagFrameResult()
        for(time in 100L..1200L step 100) {
            fused=fusion.fuse(AprilTagFrameResult(transformerPose=estimate.pose,poseMarkerIds=listOf(marker.id),
                poseMarkerCount=1,referencePointMm=marker.positionMm,referenceMarkers=solution.markers,
                detections=listOf(seen),cameraIntrinsics=intrinsics,capturedAtElapsedMillis=time,detectionSequence=time,
                trackingFrameId=77),captureGl,captureGl,captureGl.inverseRigid(),null,projection,true,1280,960,time+20,
                persistentTrackingFrame=true,currentTrackingFrameId=77)
        }
        assertNotNull(fused.displayProjection)
        assertTrue(fused.arFromTransformer!!.distanceTo(geometry.frame)<2)
        assertNull(sensorPlacementBlockReason(fused,computePlacementQuality(fused,1f,false,0)))
        assertNull(estimateTransformerPoseFromAprilTags(listOf(seen.copy(id=99)),solution.markers,intrinsics))
        }
    }
    @Test fun sensorRangeAndDuplicateIdsAreNeverLearnedAsWallReferences() {
        assertTrue(OpenCvRuntime.ensureLoaded())
        val marker=Marker(1,"apriltag",100,MmPosition(0,0,0),FloatVector(0f,0f,0f))
        val seen=detection(marker,localCamera)
        val request=WallScanRequest(1,100,emptyMap(),200)
        assertTrue(solveStandaloneWallTags(listOf(seen,seen),intrinsics,request).isEmpty())
        assertTrue(solveStandaloneWallTags(listOf(seen.copy(id=200)),intrinsics,request).isEmpty())
    }
}
