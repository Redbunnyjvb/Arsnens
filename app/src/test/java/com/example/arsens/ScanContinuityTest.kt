package com.example.arsens

import com.example.arsens.ar.*
import com.example.arsens.ar.calibration.*
import com.example.arsens.data.*
import org.junit.Assert.*
import org.junit.Test

class ScanContinuityTest {
    private fun observation() = WallTagObservation(1,100,1000,1,1,
        Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(0f,0f,1000f),floatArrayOf(0f,0f,0f),0f)),
        Transform3D.identity(),WallVector(0.0,0.0,1.0),0.2f,60f)
    @Test fun overlayHoldsThroughMissedImageButOldObservationCannotBecomeNewMeasurement() {
        val cache=WallObservationHistory();val tag=observation()
        assertEquals(listOf(tag),cache.visible(1100,1,true,listOf(tag)))
        assertEquals(listOf(tag),cache.visible(1450,1,true,emptyList()))
        val sampler=WallCalibrationSession()
        sampler.observe(WallScanFrame(1,1,true,cache.visible(1450,1,true,emptyList()),null),listOf(WallTagAssignment(1,CalibrationWall.Front,100)),1450)
        assertEquals(0,sampler.count(1))
        assertTrue(cache.visible(1701,1,true,emptyList()).isEmpty())
    }
    @Test fun overlayIsImmediatelyClearedWhenTrackingFrameChangesOrTrackingIsLost() {
        val cache=WallObservationHistory();cache.visible(1100,1,true,listOf(observation()))
        assertTrue(cache.visible(1200,2,true,emptyList()).isEmpty())
        cache.visible(1100,1,true,listOf(observation()))
        assertTrue(cache.visible(1200,1,false,emptyList()).isEmpty())
    }
    @Test fun sensorCaptureNeedsIndependentStableFramesAndCannotCrossRuntimeFrames() {
        val window=SensorCaptureWindow()
        repeat(12) { window.observe("S",200,1,1,1000,MmPosition(100,0,500)) }
        assertNull(window.estimate(1000))
        for (i in 2..9) window.observe("S",200,1,i.toLong(),900+i*100L,MmPosition(100+i%2,0,500))
        assertNotNull(window.estimate(1800))
        window.observe("S",200,2,10,1900,MmPosition(100,0,500))
        assertNull(window.estimate(1900))
    }
}
