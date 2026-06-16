package com.example.arsens

import com.example.arsens.ar.AprilTagCorner
import com.example.arsens.ar.AprilTagDetection
import com.example.arsens.ar.ImageToViewMapper
import com.example.arsens.ar.cameraGlFromCameraCv
import com.example.arsens.ar.mapDetectionsToView
import org.junit.Assert.assertEquals
import org.junit.Test

class AprilTagScreenMappingTest {
    @Test
    fun screenMappingDoesNotMutateRawDetections() {
        val raw = listOf(
            AprilTagDetection(
                id = 7,
                cornersPx = listOf(
                    AprilTagCorner(0f, 0f),
                    AprilTagCorner(100f, 0f),
                    AprilTagCorner(100f, 100f),
                    AprilTagCorner(0f, 100f)
                ),
                centerPx = AprilTagCorner(50f, 50f)
            )
        )
        val mapped = mapDetectionsToView(
            raw,
            ImageToViewMapper(
                imageWidth = 100,
                imageHeight = 100,
                displayWidth = 200,
                displayHeight = 300,
                topLeft = AprilTagCorner(10f, 20f),
                topRight = AprilTagCorner(210f, 20f),
                bottomLeft = AprilTagCorner(10f, 320f)
            )
        )

        assertEquals(0f, raw.first().cornersPx.first().xPx)
        assertEquals(10f, mapped.first().cornersPx.first().xPx)
        assertEquals(110f, mapped.first().centerPx.xPx)
        assertEquals(170f, mapped.first().centerPx.yPx)
    }

    @Test
    fun unmapInvertsMap() {
        val mapper = ImageToViewMapper(
            imageWidth = 100,
            imageHeight = 100,
            displayWidth = 200,
            displayHeight = 300,
            topLeft = AprilTagCorner(10f, 20f),
            topRight = AprilTagCorner(210f, 20f),
            bottomLeft = AprilTagCorner(10f, 320f)
        )
        val image = AprilTagCorner(25f, 75f)
        val view = mapper.map(image)
        val roundTrip = mapper.unmap(view)
        requireNotNull(roundTrip)
        assertEquals(image.xPx, roundTrip.xPx, 1e-3f)
        assertEquals(image.yPx, roundTrip.yPx, 1e-3f)
    }

    @Test
    fun unmapHandlesRotatedPortraitDisplay() {
        // Landscape sensorbeeld (400x300), 90° gedraaid op een portrait scherm (300x400):
        // beeld-(0,0) → scherm-rechtsboven, beeld-(0,300) → scherm-linksboven.
        val mapper = ImageToViewMapper(
            imageWidth = 400,
            imageHeight = 300,
            displayWidth = 300,
            displayHeight = 400,
            topLeft = AprilTagCorner(300f, 0f),
            topRight = AprilTagCorner(300f, 400f),
            bottomLeft = AprilTagCorner(0f, 0f)
        )
        // Een punt links-midden op het SCHERM ligt onder-midden in het BEELD —
        // precies de verwisseling die zonder unmap de cursor-offset 90° draaide.
        val image = mapper.unmap(AprilTagCorner(0f, 200f))
        requireNotNull(image)
        assertEquals(200f, image.xPx, 1e-3f)
        assertEquals(300f, image.yPx, 1e-3f)
        val view = mapper.map(image)
        assertEquals(0f, view.xPx, 1e-3f)
        assertEquals(200f, view.yPx, 1e-3f)
    }

    @Test
    fun cameraGlFromCameraCvFollowsImageToViewRotation() {
        val mapper = ImageToViewMapper(
            imageWidth = 400,
            imageHeight = 300,
            displayWidth = 300,
            displayHeight = 400,
            topLeft = AprilTagCorner(300f, 0f),
            topRight = AprilTagCorner(300f, 400f),
            bottomLeft = AprilTagCorner(0f, 0f)
        )

        val transform = requireNotNull(mapper.cameraGlFromCameraCv())

        assertEquals(0.0, transform.values[0], 1e-6)
        assertEquals(-1.0, transform.values[1], 1e-6)
        assertEquals(-1.0, transform.values[4], 1e-6)
        assertEquals(0.0, transform.values[5], 1e-6)
        assertEquals(-1.0, transform.values[10], 1e-6)
    }

    @Test
    fun unmapReturnsNullForDegenerateMapping() {
        val mapper = ImageToViewMapper(
            imageWidth = 100,
            imageHeight = 100,
            displayWidth = 200,
            displayHeight = 300,
            topLeft = AprilTagCorner(10f, 20f),
            topRight = AprilTagCorner(10f, 20f),
            bottomLeft = AprilTagCorner(10f, 20f)
        )
        assertEquals(null, mapper.unmap(AprilTagCorner(50f, 50f)))
    }
}
