package com.example.arsens

import com.example.arsens.ar.*
import com.example.arsens.ar.calibration.*
import com.example.arsens.data.*
import org.junit.Assert.*
import org.junit.Test

class WallRecognitionTest {
    private fun pose(wall: CalibrationWall): Transform3D {
        val rotation = tagRotationFor(TagPlane.valueOf(wall.name))
        return Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(300f,200f,1000f),
            floatArrayOf(Math.toRadians(rotation.x.toDouble()).toFloat(), Math.toRadians(rotation.y.toDouble()).toFloat(), Math.toRadians(rotation.z.toDouble()).toFloat()),0f))
    }
    @Test fun everyFirstSideRecognizesAllWallsWithPortraitLandscapeAndUpsideDownFrames() {
        for (roll in listOf(0f, 90f, 180f, 270f)) for (first in CalibrationWall.entries.filter { it != CalibrationWall.Top }) {
            val frame = Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(400f,-200f,600f),
                floatArrayOf(0.23f,-0.45f,Math.toRadians(roll.toDouble()).toFloat()),0f))
            val up = frame.wallDirection(WallVector(0.0,0.0,1.0))
            val reference = WallTagEstimate(WallTagAssignment(1,first,100),frame * pose(first),1.0,12,up)
            for (wall in CalibrationWall.entries) {
                val observation = WallTagObservation(2,100,1000,1,1,frame * pose(wall),frame,up,0.2f,50f)
                assertEquals("First=$first; roll=$roll; wall=$wall", wall, recognizeWall(observation,listOf(reference))?.wall)
            }
        }
    }
    @Test fun noSideIsGuessedFromGravityAloneAndDiagonalFacesStayUnassigned() {
        val up = WallVector(0.0,0.0,1.0)
        val front = WallTagObservation(2,100,1000,1,1,pose(CalibrationWall.Front),Transform3D.identity(),up,0.2f,50f)
        assertNull(recognizeWall(front, emptyList()))
        val diagonal = Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(0f,0f,1000f),floatArrayOf(0f,0f,(Math.PI/4).toFloat()),0f))
        assertNull(recognizeWall(front.copy(referenceFromTag=diagonal * pose(CalibrationWall.Front)),listOf(WallTagEstimate(WallTagAssignment(1,CalibrationWall.Front,100),pose(CalibrationWall.Front),1.0,12,up))))
    }
    @Test fun smallButSharpTagUsesLongerCaptureWithoutRelaxingScatter() {
        assertTrue(WallCaptureTuning.acceptsImage(26f,0.3f))
        assertFalse(WallCaptureTuning.acceptsImage(26f,1.4f))
        assertFalse(WallCaptureTuning.acceptsImage(15f,0.1f))
        val session = WallCalibrationSession()
        val assignment = WallTagAssignment(1,CalibrationWall.Front,100)
        fun frame(index: Int) {
            val time = index*100L+1000
            session.observe(WallScanFrame(1,1,true,listOf(WallTagObservation(1,100,time,index.toLong(),1,
                pose(CalibrationWall.Front),Transform3D.identity(),WallVector(0.0,0.0,1.0),0.3f,26f)),null),listOf(assignment),time)
        }
        repeat(9,::frame)
        assertTrue(session.estimates(listOf(assignment)).isEmpty())
        for (i in 9..17) frame(i)
        assertEquals(1,session.estimates(listOf(assignment)).size)
    }
}
