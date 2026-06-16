package com.example.arsens

import com.example.arsens.data.MmPosition
import com.example.arsens.data.StlMesh
import com.example.arsens.data.StlModel
import com.example.arsens.data.firstRayHitDistance
import com.example.arsens.data.modelSurfaceDistanceAlongRay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Raycast voor "tag op modeloppervlak": eerste raakpunt langs de vlaknormaal. */
class StlRaycastTest {

    private fun meshOf(vararg tris: FloatArray): StlMesh {
        val verts = FloatArray(tris.size * 9)
        tris.forEachIndexed { i, t -> System.arraycopy(t, 0, verts, i * 9, 9) }
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < verts.size) {
            minX = minOf(minX, verts[i]); maxX = maxOf(maxX, verts[i])
            minY = minOf(minY, verts[i + 1]); maxY = maxOf(maxY, verts[i + 1])
            minZ = minOf(minZ, verts[i + 2]); maxZ = maxOf(maxZ, verts[i + 2])
            i += 3
        }
        return StlMesh(verts, FloatArray(tris.size * 3), tris.size, minX, minY, minZ, maxX, maxY, maxZ)
    }

    private val trianglePlaneZ5 = floatArrayOf(0f, 0f, 5f, 10f, 0f, 5f, 0f, 10f, 5f)

    @Test
    fun firstRayHitFindsNearestTriangle() {
        val mesh = meshOf(
            trianglePlaneZ5,
            floatArrayOf(0f, 0f, 2f, 10f, 0f, 2f, 0f, 10f, 2f) // dieper vlak op z=2
        )
        val t = mesh.firstRayHitDistance(2f, 2f, 100f, 0f, 0f, -1f)
        // Eerste (buitenste) raakvlak vanaf boven is z=5 → afstand 95.
        assertEquals(95f, t!!, 1e-3f)
    }

    @Test
    fun firstRayHitMissesOutsideTriangle() {
        val mesh = meshOf(trianglePlaneZ5)
        assertNull(mesh.firstRayHitDistance(9f, 9f, 100f, 0f, 0f, -1f)) // buiten x+y<=10
    }

    @Test
    fun surfaceDistanceAppliesScaleAndOffset() {
        val mesh = meshOf(trianglePlaneZ5)
        val model = StlModel(
            id = "m", name = "m", fileName = "m",
            scalePercent = 200,
            offsetMm = MmPosition(100, 0, 0)
        )
        // proj = 2·v + (100,0,0): vlak op z=10; straal vanaf z=1000 omlaag op (104,4).
        val t = modelSurfaceDistanceAlongRay(
            model, mesh,
            originMm = floatArrayOf(104f, 4f, 1000f),
            direction = floatArrayOf(0f, 0f, -1f)
        )
        assertEquals(990f, t!!, 1e-2f)
    }

    @Test
    fun surfaceDistanceRespectsRotation() {
        val mesh = meshOf(trianglePlaneZ5)
        // 90° om X, om het mesh-midden (5,5,5): vlak z=5 wordt vlak y=5 (driehoek in XZ).
        val model = StlModel(
            id = "m", name = "m", fileName = "m",
            rotationDeg = MmPosition(90, 0, 0)
        )
        val t = modelSurfaceDistanceAlongRay(
            model, mesh,
            originMm = floatArrayOf(2f, 100f, 2f),
            direction = floatArrayOf(0f, -1f, 0f)
        )
        assertEquals(95f, t!!, 1e-2f)
    }
}
