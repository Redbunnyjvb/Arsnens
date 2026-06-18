package com.example.arsens

import com.example.arsens.data.MmPosition
import com.example.arsens.data.StlModel
import com.example.arsens.data.StlParser
import com.example.arsens.data.estimateCoreBounds
import com.example.arsens.data.rotatedBounds
import com.example.arsens.data.stlModelTransform
import com.example.arsens.data.StlMesh
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StlParserTest {

    private fun binaryStl(triangles: List<FloatArray>): ByteArray {
        val bytes = ByteArray(84 + triangles.size * 50)
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(80)
        buf.putInt(triangles.size)
        triangles.forEach { tri ->
            repeat(3) { buf.putFloat(0f) } // normaal — parser berekent zelf
            tri.forEach { buf.putFloat(it) }
            buf.putShort(0)
        }
        return bytes
    }

    @Test
    fun parseFileStreamsBinaryStl() {
        val file = File.createTempFile("test", ".stl")
        try {
            file.writeBytes(
                binaryStl(
                    listOf(
                        floatArrayOf(0f, 0f, 0f, 10f, 0f, 0f, 0f, 5f, 0f),
                        floatArrayOf(0f, 0f, 0f, 0f, 0f, 7f, 10f, 0f, 0f)
                    )
                )
            )
            val mesh = StlParser.parseFile(file)
            assertEquals(2, mesh.totalTriangleCount)
            assertEquals(2, mesh.triangleCount)
            assertEquals(0f, mesh.minX)
            assertEquals(10f, mesh.maxX)
            assertEquals(5f, mesh.maxY)
            assertEquals(7f, mesh.maxZ)
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseFileReadsAsciiStl() {
        val file = File.createTempFile("test", ".stl")
        try {
            file.writeText(
                """
                solid test
                facet normal 0 0 1
                 outer loop
                  vertex 0 0 0
                  vertex 4 0 0
                  vertex 0 3 0
                 endloop
                endfacet
                endsolid test
                """.trimIndent()
            )
            val mesh = StlParser.parseFile(file)
            assertEquals(1, mesh.triangleCount)
            assertEquals(4f, mesh.maxX)
            assertEquals(3f, mesh.maxY)
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseFileReadsObj() {
        val file = File.createTempFile("test", ".obj")
        try {
            file.writeText(
                """
                # twee driehoeken (quad opgesplitst)
                v 0 0 0
                v 4 0 0
                v 0 3 0
                v 4 3 0
                f 1 2 3
                f 2 4 3
                """.trimIndent()
            )
            val mesh = StlParser.parseFile(file)
            assertEquals(2, mesh.triangleCount)
            assertEquals(0f, mesh.minX)
            assertEquals(4f, mesh.maxX)
            assertEquals(3f, mesh.maxY)
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseFileObjTriangulatesQuadsAndIgnoresSlashes() {
        // Eén quad met v/vt/vn-tokens → fan-triangulatie geeft 2 driehoeken; '/'-suffix genegeerd.
        val file = File.createTempFile("quad", ".obj")
        try {
            file.writeText(
                """
                v 0 0 0
                v 2 0 0
                v 2 2 0
                v 0 2 0
                f 1/1/1 2/2/2 3/3/3 4/4/4
                """.trimIndent()
            )
            val mesh = StlParser.parseFile(file)
            assertEquals(2, mesh.triangleCount)
            assertEquals(2f, mesh.maxX)
            assertEquals(2f, mesh.maxY)
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseFileReadsAsciiPly() {
        val file = File.createTempFile("test", ".ply")
        try {
            file.writeText(
                "ply\n" +
                    "format ascii 1.0\n" +
                    "element vertex 3\n" +
                    "property float x\n" +
                    "property float y\n" +
                    "property float z\n" +
                    "element face 1\n" +
                    "property list uchar int vertex_indices\n" +
                    "end_header\n" +
                    "0 0 0\n" +
                    "5 0 0\n" +
                    "0 6 0\n" +
                    "3 0 1 2\n"
            )
            val mesh = StlParser.parseFile(file)
            assertEquals(1, mesh.triangleCount)
            assertEquals(5f, mesh.maxX)
            assertEquals(6f, mesh.maxY)
        } finally {
            file.delete()
        }
    }

    @Test
    fun parseFileReadsBinaryLittleEndianPly() {
        val header = "ply\n" +
            "format binary_little_endian 1.0\n" +
            "element vertex 3\n" +
            "property float x\n" +
            "property float y\n" +
            "property float z\n" +
            "element face 1\n" +
            "property list uchar int vertex_indices\n" +
            "end_header\n"
        val body = ByteArrayOutputStream()
        val vbuf = ByteBuffer.allocate(3 * 12).order(ByteOrder.LITTLE_ENDIAN)
        floatArrayOf(0f, 0f, 0f, 8f, 0f, 0f, 0f, 9f, 0f).forEach { vbuf.putFloat(it) }
        body.write(vbuf.array())
        val fbuf = ByteBuffer.allocate(1 + 3 * 4).order(ByteOrder.LITTLE_ENDIAN)
        fbuf.put(3.toByte())
        fbuf.putInt(0); fbuf.putInt(1); fbuf.putInt(2)
        body.write(fbuf.array())
        val file = File.createTempFile("bin", ".ply")
        try {
            file.outputStream().use {
                it.write(header.toByteArray(Charsets.US_ASCII))
                it.write(body.toByteArray())
            }
            val mesh = StlParser.parseFile(file)
            assertEquals(1, mesh.triangleCount)
            assertEquals(8f, mesh.maxX)
            assertEquals(9f, mesh.maxY)
        } finally {
            file.delete()
        }
    }

    @Test
    fun estimateCoreBoundsIgnoresProtrusions() {
        // Twee grote "wanden" op x=0 en x=4000 + tien kleine "koelrib"-driehoekjes op x=4500.
        // De wand-box moet 0..4000 zijn — de ribben (klein oppervlak) tellen niet mee.
        val tris = mutableListOf<FloatArray>()
        val norms = mutableListOf<FloatArray>()
        fun wall(x: Float, nx: Float) {
            tris += floatArrayOf(x, 0f, 0f, x, 2000f, 0f, x, 0f, 3000f)
            norms += floatArrayOf(nx, 0f, 0f)
            tris += floatArrayOf(x, 2000f, 0f, x, 2000f, 3000f, x, 0f, 3000f)
            norms += floatArrayOf(nx, 0f, 0f)
        }
        wall(0f, -1f)
        wall(4000f, 1f)
        repeat(10) { i ->
            val y = i * 150f
            tris += floatArrayOf(4500f, y, 0f, 4500f, y + 100f, 0f, 4500f, y, 100f)
            norms += floatArrayOf(1f, 0f, 0f)
        }
        val vertices = FloatArray(tris.size * 9)
        tris.forEachIndexed { i, t -> System.arraycopy(t, 0, vertices, i * 9, 9) }
        val normals = FloatArray(norms.size * 3)
        norms.forEachIndexed { i, n -> System.arraycopy(n, 0, normals, i * 3, 3) }
        val mesh = StlMesh(
            vertices = vertices,
            normals = normals,
            totalTriangleCount = tris.size,
            minX = 0f, minY = 0f, minZ = 0f,
            maxX = 4500f, maxY = 2000f, maxZ = 3000f
        )
        val core = mesh.estimateCoreBounds()
        // Bin-resolutie is range/256 (~17,6 mm) — ruime marge van 25 mm.
        assertEquals(0f, core[0], 25f)
        assertEquals(4000f, core[3], 25f)
        // Geen ±Y/±Z-dominante vlakken → die assen vallen terug op de volledige bbox.
        assertEquals(0f, core[1], 1e-3f)
        assertEquals(2000f, core[4], 1e-3f)
    }

    @Test
    fun rotatedBoundsRotatesAroundMeshCenter() {
        // Asymmetrische bbox 4×2×2 (center 2,1,1); 90° om Z → x'∈[1,3], y'∈[-1,3], z ongewijzigd.
        val mesh = StlMesh(
            vertices = FloatArray(9),
            normals = FloatArray(3),
            totalTriangleCount = 1,
            minX = 0f, minY = 0f, minZ = 0f,
            maxX = 4f, maxY = 2f, maxZ = 2f
        )
        val b = mesh.rotatedBounds(0, 0, 90)
        assertEquals(1f, b[0], 1e-3f)
        assertEquals(-1f, b[1], 1e-3f)
        assertEquals(0f, b[2], 1e-3f)
        assertEquals(3f, b[3], 1e-3f)
        assertEquals(3f, b[4], 1e-3f)
        assertEquals(2f, b[5], 1e-3f)
    }

    @Test
    fun stlModelTransformAppliesScaleRotationOffsetAroundMeshCenter() {
        // Mesh met bbox 0..2 op alle assen (center = 1,1,1) — één driehoek volstaat voor de bounds.
        val mesh = StlMesh(
            vertices = floatArrayOf(0f, 0f, 0f, 2f, 2f, 2f, 0f, 2f, 0f),
            normals = floatArrayOf(0f, 0f, 1f),
            totalTriangleCount = 1,
            minX = 0f, minY = 0f, minZ = 0f,
            maxX = 2f, maxY = 2f, maxZ = 2f
        )
        val model = StlModel(
            id = "m",
            name = "test",
            fileName = "test.stl",
            scalePercent = 100,
            offsetMm = MmPosition(0, 0, 0),
            rotationDeg = MmPosition(0, 0, 90)
        )
        val xf = stlModelTransform(model, mesh)
        val v = floatArrayOf(2f, 1f, 1f)
        val out = FloatArray(3)
        xf.apply(v, out)
        // 90° om Z om het mesh-midden: (2,1,1) → (1,2,1)
        assertEquals(1f, out[0], 1e-4f)
        assertEquals(2f, out[1], 1e-4f)
        assertEquals(1f, out[2], 1e-4f)
    }
}
