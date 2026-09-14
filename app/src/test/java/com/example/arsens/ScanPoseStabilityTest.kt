package com.example.arsens

import com.example.arsens.ar.*
import com.example.arsens.ar.calibration.*
import org.junit.Assert.*
import org.junit.Test

class ScanPoseStabilityTest {
    @Test fun gravityConstraintKeepsTagCenterAndRemovesFalseVerticalTiltFarFromOrigin() {
        val candidate=Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(10000f,3000f,100f),floatArrayOf(0.035f,0f,0f),0f))
        val center=WallVector(500.0,100.0,200.0)
        val constrained=gravityAlignedReference(candidate,WallVector(0.0,0.0,1.0),WallVector(0.0,0.0,1.0),center)
        assertTrue((candidate.wallPoint(center)-constrained.wallPoint(center)).length()<1e-6)
        assertTrue((constrained.wallDirection(WallVector(0.0,0.0,1.0))-WallVector(0.0,0.0,1.0)).length()<1e-6)
    }
    @Test fun priorFollowsCameraRollAndExpiresInsteadOfBecomingANewMeasurement() {
        val history=WallPoseHistory();val request=WallScanRequest(1,100,emptyMap(),200)
        history.predictions(request,1,1000,Transform3D.identity())
        val pose=Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(0f,0f,1000f),floatArrayOf(0f,0f,0f),0f))
        history.remember(listOf(StandaloneWallTag(0,100,pose,0.2f,100f)),1000,Transform3D.identity())
        val camera=Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(10f,0f,0f),floatArrayOf(0f,0f,1.57f),0f))
        val prediction=Transform3D.cameraCvFromTransformerPose(history.predictions(request,1,1100,camera).getValue(0))
        assertTrue(prediction.distanceTo(camera.inverseRigid()*pose)<0.01)
        assertTrue(prediction.rotationAngleDegreesTo(camera.inverseRigid()*pose)<0.01)
        assertTrue(history.predictions(request,2,1200,camera).isEmpty())
        history.remember(listOf(StandaloneWallTag(0,100,pose,0.2f,100f)),1200,camera)
        assertTrue(history.predictions(request,2,1901,camera).isEmpty())
    }
    @Test fun centerIsAPreferenceButDoesNotDisqualifyAnEdgeTag() {
        val tag=WallTagObservation(0,100,1000,1,1,Transform3D.identity(),Transform3D.identity(),WallVector(0.0,0.0,1.0),0.2f,80f,0f)
        assertTrue(tag.selectionScore>tag.copy(screenCenterDistance=1f).selectionScore)
        assertTrue(tag.copy(screenCenterDistance=1f).selectionScore>0)
        assertTrue(tag.selectionScore>tag.copy(shortestEdgePx=25f).selectionScore)
    }
}
