package com.example.arsens

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arsens.ar.*
import com.example.arsens.ar.calibration.*
import com.example.arsens.data.*
import com.example.arsens.ui.WorkflowAppState
import java.io.File
import java.util.UUID

internal object WallTestGeometry {
    val dimensions = MmPosition(1000,800,1000)
    val up = WallVector(0.0,1.0,0.0)
    val frame = Transform3D(doubleArrayOf(0.8,-0.6,0.0,1500.0, 0.0,0.0,1.0,-900.0, -0.6,-0.8,0.0,1200.0, 0.0,0.0,0.0,1.0))
    val datum = WallVerticalDatum(0,200)
    val points = listOf(
        CalibrationWall.Front to MmPosition(200,0,200), CalibrationWall.Front to MmPosition(800,0,800),
        CalibrationWall.Back to MmPosition(200,800,300), CalibrationWall.Back to MmPosition(800,800,850),
        CalibrationWall.Left to MmPosition(0,200,250), CalibrationWall.Left to MmPosition(0,600,750),
        CalibrationWall.Right to MmPosition(1000,200,250), CalibrationWall.Right to MmPosition(1000,600,750),
        CalibrationWall.Top to MmPosition(200,200,1000), CalibrationWall.Top to MmPosition(800,600,1000))
    val tags = points.mapIndexed { id, (wall,p) ->
        val angle = when(wall) { CalibrationWall.Front -> 0.0; CalibrationWall.Back -> Math.PI
            CalibrationWall.Left -> -Math.PI/2; CalibrationWall.Right -> Math.PI/2; CalibrationWall.Top -> 0.0 }
        val pose = Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(p.x.toFloat(),p.y.toFloat(),p.z.toFloat()),
            if(wall==CalibrationWall.Top) floatArrayOf((-Math.PI/2).toFloat(),0f,0f) else floatArrayOf(0f,0f,angle.toFloat()),0f))
        WallTagEstimate(WallTagAssignment(id,wall,100),frame*pose,1.0,10,up)
    }
    fun observations(time: Long, sequence: Long, frameId: Long = 1) = tags.map { t ->
        val location=frame.wallPoint(WallVector(500.0,-400.0,1400.0))
        val camera=Transform3D(Transform3D.identity().values.copyOf().apply { this[3]=location.x;this[7]=location.y;this[11]=location.z })
        WallTagObservation(t.assignment.tagId,100,time,sequence,frameId,t.referenceFromTag,camera,up,0.2f,100f)
    }
}

/** Isolate actual repository and preferences from every user project. */
internal class WallTestStore : AutoCloseable {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "wall-test-${UUID.randomUUID()}"
    private val folder = File(base.cacheDir,prefix).also { it.mkdirs() }
    private val prefs = mutableSetOf<String>()
    val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = folder
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            val key="$prefix-$name";prefs+=key;return base.getSharedPreferences(key,mode)
        }
    }
    fun state() = WorkflowAppState(context)
    override fun close() {
        check(folder.canonicalFile.parentFile == base.cacheDir.canonicalFile)
        folder.deleteRecursively();prefs.forEach { base.deleteSharedPreferences(it) }
    }
}
