package com.example.arsens

import com.example.arsens.ar.ArDisplayProjection
import com.example.arsens.ar.ProjectPointMm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ArProjectionTest {
    @Test
    fun centerPointProjectsToDisplayCenter() {
        val projection = ArDisplayProjection(
            displayWidthPx = 1000,
            displayHeightPx = 500,
            clipFromTransformer = doubleArrayOf(
                1.0, 0.0, 0.0, 0.0,
                0.0, 1.0, 0.0, 0.0,
                0.0, 0.0, 1.0, 0.0,
                0.0, 0.0, 0.0, 1.0
            )
        )

        val projected = projection.project(ProjectPointMm(0.0, 0.0, 0.0))

        assertNotNull(projected)
        assertEquals(500f, projected!!.xPx, 0.001f)
        assertEquals(250f, projected.yPx, 0.001f)
    }

    @Test
    fun pointBehindCameraIsRejected() {
        val projection = ArDisplayProjection(
            displayWidthPx = 1000,
            displayHeightPx = 500,
            clipFromTransformer = doubleArrayOf(
                1.0, 0.0, 0.0, 0.0,
                0.0, 1.0, 0.0, 0.0,
                0.0, 0.0, 1.0, 0.0,
                0.0, 0.0, 0.0, -1.0
            )
        )

        assertNull(projection.project(ProjectPointMm(0.0, 0.0, 0.0)))
    }
}
