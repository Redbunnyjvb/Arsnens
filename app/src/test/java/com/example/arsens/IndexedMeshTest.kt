package com.example.arsens

import com.example.arsens.data.EdgeTopology
import com.example.arsens.data.IndexedMesh
import com.example.arsens.data.StlMesh
import com.example.arsens.data.StlParser
import com.example.arsens.data.buildEdgeTopology
import com.example.arsens.data.clusterDecimateSubset
import com.example.arsens.data.connectedComponents
import com.example.arsens.data.decimationCellForBudget
import com.example.arsens.data.featureEdgePairs
import com.example.arsens.data.orientConsistently
import com.example.arsens.data.sharpEdgeFlags
import com.example.arsens.data.triangleFullyOffscreen
import com.example.arsens.data.trianglesOutsideBox
import com.example.arsens.data.weldStlMesh
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexedMeshTest {

    // ---------- helpers ----------

    private fun binaryStl(triangles: List<FloatArray>): ByteArray {
        val bytes = ByteArray(84 + triangles.size * 50)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(80)
        buf.putInt(triangles.size)
        triangles.forEach { tri ->
            repeat(3) { buf.putFloat(0f) }
            tri.forEach { buf.putFloat(it) }
            buf.putShort(0)
        }
        return bytes
    }

    private fun soup(triangles: List<FloatArray>): StlMesh = StlParser.parse(binaryStl(triangles))

    private fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray): List<FloatArray> =
        listOf(
            floatArrayOf(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2]),
            floatArrayOf(a[0], a[1], a[2], c[0], c[1], c[2], d[0], d[1], d[2])
        )

    private fun v(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)

    /** 12 driehoeken van een kubus [0..size]^3 met consistente, naar buiten gerichte winding. */
    private fun cubeTriangles(size: Float): List<FloatArray> {
        val s = size
        return quad(v(0f, 0f, 0f), v(0f, s, 0f), v(s, s, 0f), v(s, 0f, 0f)) + // onder (-z)
            quad(v(0f, 0f, s), v(s, 0f, s), v(s, s, s), v(0f, s, s)) +         // boven (+z)
            quad(v(0f, 0f, 0f), v(0f, 0f, s), v(0f, s, s), v(0f, s, 0f)) +     // x=0 (-x)
            quad(v(s, 0f, 0f), v(s, s, 0f), v(s, s, s), v(s, 0f, s)) +         // x=s (+x)
            quad(v(0f, 0f, 0f), v(s, 0f, 0f), v(s, 0f, s), v(0f, 0f, s)) +     // y=0 (-y)
            quad(v(0f, s, 0f), v(0f, s, s), v(s, s, s), v(s, s, 0f))           // y=s (+y)
    }

    /** Kubus [0..size]^3 met elke zijde onderverdeeld in n×n cellen (6·n²·2 driehoeken). */
    private fun subdividedCubeTriangles(size: Float, n: Int): List<FloatArray> {
        data class Face(val o: FloatArray, val u: FloatArray, val vv: FloatArray)
        val s = size
        val faces = listOf(
            Face(v(0f, 0f, 0f), v(0f, s, 0f), v(s, 0f, 0f)),
            Face(v(0f, 0f, s), v(s, 0f, 0f), v(0f, s, 0f)),
            Face(v(0f, 0f, 0f), v(0f, 0f, s), v(0f, s, 0f)),
            Face(v(s, 0f, 0f), v(0f, s, 0f), v(0f, 0f, s)),
            Face(v(0f, 0f, 0f), v(s, 0f, 0f), v(0f, 0f, s)),
            Face(v(0f, s, 0f), v(0f, 0f, s), v(s, 0f, 0f))
        )
        val out = ArrayList<FloatArray>(6 * n * n * 2)
        for (face in faces) {
            for (i in 0 until n) {
                for (j in 0 until n) {
                    fun corner(fi: Float, fj: Float) = v(
                        face.o[0] + face.u[0] * fi / n + face.vv[0] * fj / n,
                        face.o[1] + face.u[1] * fi / n + face.vv[1] * fj / n,
                        face.o[2] + face.u[2] * fi / n + face.vv[2] * fj / n
                    )
                    out += quad(
                        corner(i.toFloat(), j.toFloat()),
                        corner(i + 1f, j.toFloat()),
                        corner(i + 1f, j + 1f),
                        corner(i.toFloat(), j + 1f)
                    )
                }
            }
        }
        return out
    }

    private fun reversed(tri: FloatArray): FloatArray =
        floatArrayOf(tri[0], tri[1], tri[2], tri[6], tri[7], tri[8], tri[3], tri[4], tri[5])

    private fun signedVolume(mesh: IndexedMesh): Double {
        var sum = 0.0
        val p = mesh.positions
        val idx = mesh.indices
        for (t in 0 until mesh.triangleCount) {
            val a = idx[t * 3] * 3; val b = idx[t * 3 + 1] * 3; val c = idx[t * 3 + 2] * 3
            sum += p[a].toDouble() * (p[b + 1].toDouble() * p[c + 2] - p[b + 2].toDouble() * p[c + 1]) -
                p[a + 1].toDouble() * (p[b].toDouble() * p[c + 2] - p[b + 2].toDouble() * p[c]) +
                p[a + 2].toDouble() * (p[b].toDouble() * p[c + 1] - p[b + 1].toDouble() * p[c])
        }
        return sum / 6.0
    }

    private fun windingConsistent(mesh: IndexedMesh, topo: EdgeTopology): Boolean {
        fun forward(face: Int, a: Int, b: Int): Boolean {
            val i0 = mesh.indices[face * 3]
            val i1 = mesh.indices[face * 3 + 1]
            val i2 = mesh.indices[face * 3 + 2]
            return (i0 == a && i1 == b) || (i1 == a && i2 == b) || (i2 == a && i0 == b)
        }
        for (e in 0 until topo.edgeCount) {
            val fb = topo.faceB[e]
            if (fb < 0 || topo.nonManifold[e]) continue
            if (forward(topo.faceA[e], topo.edgeA[e], topo.edgeB[e]) ==
                forward(fb, topo.edgeA[e], topo.edgeB[e])
            ) {
                return false
            }
        }
        return true
    }

    private fun vertexAt(mesh: IndexedMesh, x: Float, y: Float, z: Float): Int {
        for (i in 0 until mesh.vertexCount) {
            if (abs(mesh.positions[i * 3] - x) < 1e-4f &&
                abs(mesh.positions[i * 3 + 1] - y) < 1e-4f &&
                abs(mesh.positions[i * 3 + 2] - z) < 1e-4f
            ) {
                return i
            }
        }
        return -1
    }

    private fun edgeIndexOf(topo: EdgeTopology, a: Int, b: Int): Int {
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        for (e in 0 until topo.edgeCount) {
            if (topo.edgeA[e] == lo && topo.edgeB[e] == hi) return e
        }
        return -1
    }

    /** Open quad (0..2, 0..1) in z=0 met een 90°-flap omlaag aan de rand y=0. */
    private fun quadWithFlap(): IndexedMesh {
        val a = v(0f, 0f, 0f); val b = v(2f, 0f, 0f); val c = v(2f, 1f, 0f); val d = v(0f, 1f, 0f)
        val e = v(0f, 0f, -1f); val f = v(2f, 0f, -1f)
        val tris = quad(a, b, c, d) + quad(a, b, f, e)
        return weldStlMesh(soup(tris))
    }

    // ---------- vertex welding ----------

    @Test
    fun weldMergesCubeCornersToEightVertices() {
        val mesh = weldStlMesh(soup(cubeTriangles(1f)))
        assertEquals(8, mesh.vertexCount)
        assertEquals(12, mesh.triangleCount)
    }

    @Test
    fun weldRespectsTolerance() {
        // T2 deelt de rand (0,0,0)-(10,0,0) met T1, maar de kopieën liggen 0,004 mm verschoven:
        // binnen de lastolerantie van 0,01 mm → samenvoegen (4 hoekpunten totaal).
        val near = soup(
            listOf(
                floatArrayOf(0f, 0f, 0f, 10f, 0f, 0f, 0f, 10f, 0f),
                floatArrayOf(0.004f, 0f, 0f, 10.004f, 0f, 0f, 5f, -8f, 0f)
            )
        )
        assertEquals(4, weldStlMesh(near).vertexCount)
        // Zelfde opzet met 0,02 mm verschuiving: buiten de tolerantie → gescheiden punten.
        val far = soup(
            listOf(
                floatArrayOf(0f, 0f, 0f, 10f, 0f, 0f, 0f, 10f, 0f),
                floatArrayOf(0.02f, 0f, 0f, 10.02f, 0f, 0f, 5f, -8f, 0f)
            )
        )
        assertEquals(6, weldStlMesh(far).vertexCount)
    }

    @Test
    fun weldAndTopologyExactOnSubdividedCube() {
        // Regressie-ankerpunt voor de primitieve LongIntMap-swap: exacte weld- én topologie-
        // tellingen op een grotere mesh. 4×4 per kubusvlak = 192 driehoeken; gewelde hoekpunten
        // = 8 hoeken + 12·3 randpunten + 6·9 vlakpunten = 98; Euler V−E+F=2 ⇒ E=288, gesloten.
        val mesh = weldStlMesh(soup(subdividedCubeTriangles(100f, 4)))
        assertEquals(98, mesh.vertexCount)
        assertEquals(192, mesh.triangleCount)
        val topo = buildEdgeTopology(mesh)
        assertEquals(288, topo.edgeCount)
        assertEquals(0, topo.boundaryEdgeCount)
    }

    // ---------- edge adjacency ----------

    @Test
    fun cubeAdjacencyIsClosedManifold() {
        val mesh = weldStlMesh(soup(cubeTriangles(1f)))
        val topo = buildEdgeTopology(mesh)
        // Euler: V − E + F = 2 → 8 − E + 12 = 2 → 18 randen, allemaal met precies 2 vlakken.
        assertEquals(18, topo.edgeCount)
        assertEquals(0, topo.boundaryEdgeCount)
        for (e in 0 until topo.edgeCount) {
            assertTrue(topo.faceB[e] >= 0)
            assertFalse(topo.nonManifold[e])
        }
    }

    // ---------- boundary + sharp detectie ----------

    @Test
    fun openQuadHasBoundaryOnOuterEdgesOnly() {
        val mesh = weldStlMesh(
            soup(quad(v(0f, 0f, 0f), v(2f, 0f, 0f), v(2f, 1f, 0f), v(0f, 1f, 0f)))
        )
        val topo = buildEdgeTopology(mesh)
        assertEquals(5, topo.edgeCount)
        assertEquals(4, topo.boundaryEdgeCount)
        // De diagonaal (0,0,0)-(2,1,0) is de enige gedeelde rand — geen boundary.
        val a = vertexAt(mesh, 0f, 0f, 0f)
        val c = vertexAt(mesh, 2f, 1f, 0f)
        val diagonal = edgeIndexOf(topo, a, c)
        assertTrue(diagonal >= 0)
        assertFalse(topo.isBoundary(diagonal))
    }

    @Test
    fun coplanarInternalEdgeIsNotSharp() {
        val mesh = weldStlMesh(
            soup(quad(v(0f, 0f, 0f), v(2f, 0f, 0f), v(2f, 1f, 0f), v(0f, 1f, 0f)))
        )
        val topo = buildEdgeTopology(mesh)
        val sharp = sharpEdgeFlags(mesh, topo)
        val diagonal = edgeIndexOf(topo, vertexAt(mesh, 0f, 0f, 0f), vertexAt(mesh, 2f, 1f, 0f))
        assertFalse(sharp[diagonal])
    }

    @Test
    fun ninetyDegreeFoldIsSharp() {
        val mesh = quadWithFlap()
        val topo = buildEdgeTopology(mesh)
        val sharp = sharpEdgeFlags(mesh, topo)
        val fold = edgeIndexOf(topo, vertexAt(mesh, 0f, 0f, 0f), vertexAt(mesh, 2f, 0f, 0f))
        assertTrue(fold >= 0)
        assertFalse(topo.isBoundary(fold))
        assertTrue(sharp[fold])
    }

    // ---------- winding-oriëntatie ----------

    @Test
    fun orientationRepairsFlippedFacesAndPointsOutward() {
        // Driehoeken 2, 7 en 9 omgekeerd gewonden — zoals STL-export dat vaak doet.
        val tris = cubeTriangles(1f).mapIndexed { i, tri ->
            if (i == 2 || i == 7 || i == 9) reversed(tri) else tri
        }
        val oriented = orientConsistently(weldStlMesh(soup(tris)))
        val topo = buildEdgeTopology(oriented.mesh)
        assertTrue(windingConsistent(oriented.mesh, topo))
        // Buitenwaartse normalen ⇒ getekend volume = +1 (kubus van 1×1×1).
        assertEquals(1.0, signedVolume(oriented.mesh), 1e-6)
        assertEquals(1, oriented.components.componentCount)
        assertTrue(oriented.componentClosed[0])
    }

    @Test
    fun openSurfaceIsMarkedNotClosed() {
        val oriented = orientConsistently(
            weldStlMesh(soup(quad(v(0f, 0f, 0f), v(2f, 0f, 0f), v(2f, 1f, 0f), v(0f, 1f, 0f))))
        )
        assertEquals(1, oriented.components.componentCount)
        assertFalse(oriented.componentClosed[0])
    }

    // ---------- cluster-decimatie ----------

    @Test
    fun clusterDecimationKeepsClosedMeshClosed() {
        val oriented = orientConsistently(weldStlMesh(soup(subdividedCubeTriangles(100f, 4))))
        val source = oriented.mesh
        assertEquals(192, source.triangleCount)
        val all = IntArray(source.triangleCount) { it }
        val decimated = clusterDecimateSubset(source, all, cellMm = 40f)
        assertTrue(decimated.triangleCount in 1 until 192)
        val mesh = IndexedMesh.of(decimated.positions, decimated.indices, decimated.faceNormals)
        // Decimatie mag géén gaten maken: gesloten blijft gesloten (0 boundary-randen).
        assertEquals(0, buildEdgeTopology(mesh).boundaryEdgeCount)
        // Winding (en dus buitenkant) blijft behouden: volume blijft positief.
        assertTrue(signedVolume(mesh) > 0.0)
    }

    @Test
    fun decimationBudgetIsRespected() {
        val oriented = orientConsistently(weldStlMesh(soup(subdividedCubeTriangles(100f, 4))))
        val source = oriented.mesh
        val all = IntArray(source.triangleCount) { it }
        val cell = decimationCellForBudget(source, all, 50)
        assertTrue(cell > 0f)
        val decimated = clusterDecimateSubset(source, all, cell)
        assertTrue(decimated.triangleCount in 1..50)
    }

    @Test
    fun decimationWithoutBudgetPressureKeepsEverything() {
        val oriented = orientConsistently(weldStlMesh(soup(cubeTriangles(1f))))
        val all = IntArray(oriented.mesh.triangleCount) { it }
        assertEquals(0f, decimationCellForBudget(oriented.mesh, all, 1_000))
        val copy = clusterDecimateSubset(oriented.mesh, all, 0f)
        assertEquals(12, copy.triangleCount)
        assertEquals(8, copy.vertexCount)
    }

    // ---------- feature edges ----------

    @Test
    fun featureEdgesKeepBoundaryAndSharpAndSkipCoplanar() {
        val mesh = quadWithFlap()
        val topo = buildEdgeTopology(mesh)
        val sharp = sharpEdgeFlags(mesh, topo)
        val pairs = featureEdgePairs(mesh, topo, sharp)
        // 6 boundary-randen + 1 scherpe vouw = 7; de twee coplanaire diagonalen ontbreken.
        assertEquals(7, pairs.size / 2)
        val a = vertexAt(mesh, 0f, 0f, 0f)
        val c = vertexAt(mesh, 2f, 1f, 0f)
        val fold = setOf(vertexAt(mesh, 0f, 0f, 0f), vertexAt(mesh, 2f, 0f, 0f))
        var foldIncluded = false
        for (i in 0 until pairs.size / 2) {
            val pair = setOf(pairs[i * 2], pairs[i * 2 + 1])
            assertFalse("coplanaire diagonaal hoort geen feature te zijn", pair == setOf(a, c))
            if (pair == fold) foldIncluded = true
        }
        assertTrue(foldIncluded)
        // Lang→kort gesorteerd: een budget van 3 lijnen pakt precies de drie randen van 2 mm.
        for (i in 0 until 3) {
            val pa = pairs[i * 2] * 3
            val pb = pairs[i * 2 + 1] * 3
            val dx = mesh.positions[pa] - mesh.positions[pb]
            val dy = mesh.positions[pa + 1] - mesh.positions[pb + 1]
            val dz = mesh.positions[pa + 2] - mesh.positions[pb + 2]
            assertEquals(2f, sqrt(dx * dx + dy * dy + dz * dz), 1e-4f)
        }
    }

    // ---------- culling ----------

    @Test
    fun offscreenCullingNeverDropsPartiallyVisibleTriangles() {
        // Geheel links buiten beeld → cullen mag.
        assertTrue(triangleFullyOffscreen(-30f, 10f, -10f, 20f, -20f, 90f, 100f, 100f, 0f))
        // Eén hoekpunt in beeld → nooit cullen.
        assertFalse(triangleFullyOffscreen(-30f, 10f, -10f, 20f, 50f, 50f, 100f, 100f, 0f))
        // Hoekpunten links én rechts buiten beeld (driehoek overspant het scherm) → nooit cullen.
        assertFalse(triangleFullyOffscreen(-50f, 10f, 150f, 20f, -50f, 90f, 100f, 100f, 0f))
        // Boven/onder-variant.
        assertTrue(triangleFullyOffscreen(10f, 130f, 60f, 140f, 90f, 200f, 100f, 100f, 10f))
        assertFalse(triangleFullyOffscreen(10f, 130f, 60f, 95f, 90f, 200f, 100f, 100f, 10f))
    }

    // ---------- componentclassificatie (radiator-splitsing) ----------

    @Test
    fun componentsOutsideBoxAreMarkedAsWhole() {
        // Kubus A binnen de box, kubus B er ver buiten (alsof het een radiator is).
        val a = cubeTriangles(1f)
        val b = cubeTriangles(1f).map { tri ->
            FloatArray(9) { i -> if (i % 3 == 0) tri[i] + 5f else tri[i] }
        }
        val oriented = orientConsistently(weldStlMesh(soup(a + b)))
        val comps = connectedComponents(oriented.mesh, buildEdgeTopology(oriented.mesh))
        assertEquals(2, comps.componentCount)
        val outside = trianglesOutsideBox(
            oriented.mesh,
            comps,
            floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f),
            marginMm = 0.2f
        )
        val p = oriented.mesh.positions
        val idx = oriented.mesh.indices
        for (t in 0 until oriented.mesh.triangleCount) {
            val cx = (p[idx[t * 3] * 3] + p[idx[t * 3 + 1] * 3] + p[idx[t * 3 + 2] * 3]) / 3f
            assertEquals("driehoek met zwaartepunt-x $cx", cx > 2f, outside[t])
        }
    }
}
