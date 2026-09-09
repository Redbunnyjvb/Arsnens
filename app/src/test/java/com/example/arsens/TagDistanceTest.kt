package com.example.arsens

import com.example.arsens.ar.*
import com.example.arsens.data.*
import org.junit.Assert.*
import org.junit.Test

class TagDistanceTest {
    private val marker = Marker(0, "apriltag", 47, MmPosition(300, 0, 400), tagRotationFor(TagPlane.Front))

    @Test fun distanceIsToTagCenterRatherThanOpticalDepthOrProjectOrigin() {
        assertEquals(500f, tagDistanceMm(Transform3D.identity(), marker)!!, 0.001f)
        val camera = Transform3D.identity().let { Transform3D(it.values.copyOf().apply { this[3] = -300.0 }) }
        assertEquals(400f, tagDistanceMm(camera, marker)!!, 0.001f)
    }

    @Test fun tagBehindCameraHasNoDistanceLabel() {
        assertNull(tagDistanceMm(Transform3D.identity(), marker.copy(positionMm = MmPosition(0, 0, -400))))
    }
}
