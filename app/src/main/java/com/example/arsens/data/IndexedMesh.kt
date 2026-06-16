package com.example.arsens.data

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/** Lastolerantie voor het samenvoegen van dubbele STL-hoekpunten (mm). */
const val STL_WELD_TOLERANCE_MM = 0.01f

/** Een rand tussen twee vlakken die meer dan ~28° knikt is "scherp" (feature edge).
 *  Waarde = minimale |dot| van de twee vlaknormalen om als vlak (niet scherp) te gelden. */
const val STL_SHARP_EDGE_MIN_DOT = 0.883f

/**
 * Geïndexeerde driehoeksmesh met gedeelde (gewelde) hoekpunten. STL slaat elk hoekpunt per
 * driehoek apart op; voor een gesloten AR-weergave moeten buren écht dezelfde hoekpunten delen —
 * losse kopieën geven haarscheuren ("cracks") en maken rand-/silhouetdetectie onmogelijk.
 */
class IndexedMesh(
    val positions: FloatArray,   // 3 floats per hoekpunt (model-mm)
    val indices: IntArray,       // 3 hoekpunt-indices per driehoek
    val faceNormals: FloatArray, // 3 floats per driehoek, genormaliseerd
    val minX: Float, val minY: Float, val minZ: Float,
    val maxX: Float, val maxY: Float, val maxZ: Float
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3
    val isEmpty: Boolean get() = triangleCount == 0

    companion object {
        val EMPTY = IndexedMesh(FloatArray(0), IntArray(0), FloatArray(0), 0f, 0f, 0f, 0f, 0f, 0f)

        /** Bouwt een mesh en bepaalt de bounding box uit de posities zelf. */
        fun of(positions: FloatArray, indices: IntArray, faceNormals: FloatArray): IndexedMesh {
            if (indices.isEmpty()) return EMPTY
            var miX = Float.MAX_VALUE; var miY = Float.MAX_VALUE; var miZ = Float.MAX_VALUE
            var maX = -Float.MAX_VALUE; var maY = -Float.MAX_VALUE; var maZ = -Float.MAX_VALUE
            var i = 0
            while (i < positions.size - 2) {
                val x = positions[i]; val y = positions[i + 1]; val z = positions[i + 2]
                if (x < miX) miX = x; if (x > maX) maX = x
                if (y < miY) miY = y; if (y > maY) maY = y
                if (z < miZ) miZ = z; if (z > maZ) maZ = z
                i += 3
            }
            return IndexedMesh(positions, indices, faceNormals, miX, miY, miZ, maX, maY, maZ)
        }
    }
}

// 21 bits per as passen samen in één Long-rasterkey; bias legt 0 in het midden van het bereik.
private const val GRID_BITS = 21
private const val GRID_BIAS = 1 shl (GRID_BITS - 1)
private const val GRID_MASK = (1L shl GRID_BITS) - 1L

private fun packGridKey(qx: Int, qy: Int, qz: Int): Long =
    (((qx + GRID_BIAS).toLong() and GRID_MASK) shl (2 * GRID_BITS)) or
        (((qy + GRID_BIAS).toLong() and GRID_MASK) shl GRID_BITS) or
        ((qz + GRID_BIAS).toLong() and GRID_MASK)

/** Vlaknormaal van driehoek (a,b,c) in [out] vanaf [outBase]; false bij ~nul-oppervlak. */
private fun triangleNormal(p: FloatArray, a: Int, b: Int, c: Int, out: FloatArray, outBase: Int): Boolean {
    val ab = a * 3; val bb = b * 3; val cb = c * 3
    val ux = p[bb] - p[ab]; val uy = p[bb + 1] - p[ab + 1]; val uz = p[bb + 2] - p[ab + 2]
    val wx = p[cb] - p[ab]; val wy = p[cb + 1] - p[ab + 1]; val wz = p[cb + 2] - p[ab + 2]
    val nx = uy * wz - uz * wy
    val ny = uz * wx - ux * wz
    val nz = ux * wy - uy * wx
    val len = sqrt(nx * nx + ny * ny + nz * nz)
    if (len <= 1e-12f) return false
    out[outBase] = nx / len
    out[outBase + 1] = ny / len
    out[outBase + 2] = nz / len
    return true
}

/**
 * Weldt de losse STL-driehoekssoep tot een geïndexeerde mesh: hoekpunten binnen [toleranceMm]
 * van elkaar worden één gedeeld hoekpunt. Driehoeken die daardoor tot een lijn of punt krimpen
 * vervallen. De vlaknormalen worden opnieuw uit de gewelde posities berekend.
 */
fun weldStlMesh(mesh: StlMesh, toleranceMm: Float = STL_WELD_TOLERANCE_MM): IndexedMesh {
    if (mesh.isEmpty) return IndexedMesh.EMPTY
    val src = mesh.vertices
    val cornerCount = src.size / 3
    val maxAbs = maxOf(
        abs(mesh.minX), abs(mesh.maxX), abs(mesh.minY),
        abs(mesh.maxY), abs(mesh.minZ), abs(mesh.maxZ), 1f
    )
    // De rasterkey heeft 21 bits per as: het kwantum mag niet fijner zijn dan het bereik toelaat,
    // anders zouden verre punten op dezelfde key kunnen vallen (fout-welds).
    val quantum = maxOf(toleranceMm, maxAbs / 1_000_000f)
    val cells = LongIntMap(cornerCount)
    val positions = FloatArray(cornerCount * 3)
    var vertexCount = 0
    val cornerVertex = IntArray(cornerCount)
    for (corner in 0 until cornerCount) {
        val b = corner * 3
        val x = src[b]; val y = src[b + 1]; val z = src[b + 2]
        val key = packGridKey(
            Math.round(x / quantum),
            Math.round(y / quantum),
            Math.round(z / quantum)
        )
        var id = cells.getOrDefault(key, -1)
        if (id < 0) {
            id = vertexCount
            val vb = id * 3
            positions[vb] = x; positions[vb + 1] = y; positions[vb + 2] = z
            vertexCount++
            cells.put(key, id)
        }
        cornerVertex[corner] = id
    }
    val triCount = cornerCount / 3
    val outIndices = IntArray(triCount * 3)
    val outNormals = FloatArray(triCount * 3)
    var outTri = 0
    for (tri in 0 until triCount) {
        val a = cornerVertex[tri * 3]
        val b = cornerVertex[tri * 3 + 1]
        val c = cornerVertex[tri * 3 + 2]
        if (a == b || b == c || a == c) continue // tot lijn/punt gekrompen — geen vlak meer
        if (!triangleNormal(positions, a, b, c, outNormals, outTri * 3)) continue
        outIndices[outTri * 3] = a
        outIndices[outTri * 3 + 1] = b
        outIndices[outTri * 3 + 2] = c
        outTri++
    }
    return IndexedMesh(
        positions.copyOf(vertexCount * 3),
        outIndices.copyOf(outTri * 3),
        outNormals.copyOf(outTri * 3),
        mesh.minX, mesh.minY, mesh.minZ, mesh.maxX, mesh.maxY, mesh.maxZ
    )
}

/**
 * Rand-topologie van een geïndexeerde mesh. Elke unieke hoekpunt-paar-rand kent zijn (max twee)
 * aanliggende driehoeken: [faceB] < 0 betekent boundary (open rand), [nonManifold] markeert
 * randen die door ≥3 driehoeken worden gebruikt. [faceEdges] geeft per driehoek zijn 3 randen.
 */
class EdgeTopology(
    val edgeA: IntArray,
    val edgeB: IntArray,
    val faceA: IntArray,
    val faceB: IntArray,
    val nonManifold: BooleanArray,
    val faceEdges: IntArray
) {
    val edgeCount: Int get() = edgeA.size
    fun isBoundary(edge: Int): Boolean = faceB[edge] < 0
    val boundaryEdgeCount: Int
        get() {
            var n = 0
            for (e in 0 until edgeCount) if (faceB[e] < 0) n++
            return n
        }
}

fun buildEdgeTopology(mesh: IndexedMesh): EdgeTopology {
    val triCount = mesh.triangleCount
    var capacity = maxOf(16, triCount * 2)
    var edgeA = IntArray(capacity)
    var edgeB = IntArray(capacity)
    var faceA = IntArray(capacity)
    var faceB = IntArray(capacity)
    var nonManifold = BooleanArray(capacity)
    var edgeCount = 0
    val map = LongIntMap(triCount * 2)
    val faceEdges = IntArray(triCount * 3)
    val idx = mesh.indices
    for (tri in 0 until triCount) {
        for (k in 0 until 3) {
            val a = idx[tri * 3 + k]
            val b = idx[tri * 3 + (k + 1) % 3]
            val lo = minOf(a, b)
            val hi = maxOf(a, b)
            val key = (lo.toLong() shl 32) or hi.toLong()
            var e = map.getOrDefault(key, -1)
            if (e < 0) {
                if (edgeCount == capacity) {
                    capacity *= 2
                    edgeA = edgeA.copyOf(capacity)
                    edgeB = edgeB.copyOf(capacity)
                    faceA = faceA.copyOf(capacity)
                    faceB = faceB.copyOf(capacity)
                    nonManifold = nonManifold.copyOf(capacity)
                }
                e = edgeCount++
                edgeA[e] = lo; edgeB[e] = hi
                faceA[e] = tri; faceB[e] = -1
                map.put(key, e)
            } else {
                if (faceB[e] < 0 && faceA[e] != tri) faceB[e] = tri else nonManifold[e] = true
            }
            faceEdges[tri * 3 + k] = e
        }
    }
    return EdgeTopology(
        edgeA.copyOf(edgeCount), edgeB.copyOf(edgeCount),
        faceA.copyOf(edgeCount), faceB.copyOf(edgeCount),
        nonManifold.copyOf(edgeCount), faceEdges
    )
}

/**
 * Per rand: is hij "scherp" (vouw tussen twee vlakken met |dot| van de normalen onder [minDot])?
 * Non-manifold randen gelden altijd als scherp (het zijn constructienaden). De |dot| maakt de
 * test ongevoelig voor inconsistente STL-winding: een coplanaire interne rand is nooit scherp,
 * ook niet als de buurdriehoek andersom gewonden is.
 */
fun sharpEdgeFlags(mesh: IndexedMesh, topology: EdgeTopology, minDot: Float = STL_SHARP_EDGE_MIN_DOT): BooleanArray {
    val n = mesh.faceNormals
    val out = BooleanArray(topology.edgeCount)
    for (e in 0 until topology.edgeCount) {
        if (topology.nonManifold[e]) {
            out[e] = true
            continue
        }
        val fb = topology.faceB[e]
        if (fb < 0) continue
        val fa = topology.faceA[e]
        val dot = n[fa * 3] * n[fb * 3] + n[fa * 3 + 1] * n[fb * 3 + 1] + n[fa * 3 + 2] * n[fb * 3 + 2]
        if (abs(dot) < minDot) out[e] = true
    }
    return out
}

/** Verbonden componenten (over gedeelde randen) van de driehoeken. */
class MeshComponents(val triangleComponent: IntArray, val componentCount: Int)

fun connectedComponents(mesh: IndexedMesh, topology: EdgeTopology): MeshComponents {
    val triCount = mesh.triangleCount
    val parent = IntArray(triCount) { it }

    fun find(x: Int): Int {
        var r = x
        while (parent[r] != r) {
            parent[r] = parent[parent[r]]
            r = parent[r]
        }
        return r
    }

    for (e in 0 until topology.edgeCount) {
        val fb = topology.faceB[e]
        if (fb < 0) continue
        val ra = find(topology.faceA[e])
        val rb = find(fb)
        if (ra != rb) parent[ra] = rb
    }
    val remap = HashMap<Int, Int>()
    val comp = IntArray(triCount)
    for (t in 0 until triCount) comp[t] = remap.getOrPut(find(t)) { remap.size }
    return MeshComponents(comp, remap.size)
}

/** Mesh met consistente winding per component. [componentClosed] = gesloten 2-manifold zonder
 *  open of non-manifold randen — alleen dáár is backface-culling gegarandeerd veilig. */
class OrientedMesh(
    val mesh: IndexedMesh,
    val components: MeshComponents,
    val componentClosed: BooleanArray
)

/**
 * Maakt de winding consistent: buren moeten hun gedeelde rand in TEGENGESTELDE richting
 * doorlopen. STL-bestanden wisselen dit vaak per driehoek, waardoor backface-culling en
 * eenzijdige shading onmogelijk zijn. Gesloten componenten met negatief getekend volume
 * worden daarna in hun geheel omgeklapt zodat de normalen naar buiten wijzen.
 */
fun orientConsistently(mesh: IndexedMesh): OrientedMesh {
    if (mesh.isEmpty) return OrientedMesh(mesh, MeshComponents(IntArray(0), 0), BooleanArray(0))
    val topology = buildEdgeTopology(mesh)
    val comps = connectedComponents(mesh, topology)
    val triCount = mesh.triangleCount
    val indices = mesh.indices.copyOf()

    fun traversesForward(face: Int, a: Int, b: Int): Boolean {
        val i0 = indices[face * 3]; val i1 = indices[face * 3 + 1]; val i2 = indices[face * 3 + 2]
        return (i0 == a && i1 == b) || (i1 == a && i2 == b) || (i2 == a && i0 == b)
    }

    fun flip(face: Int) {
        val t = indices[face * 3 + 1]
        indices[face * 3 + 1] = indices[face * 3 + 2]
        indices[face * 3 + 2] = t
    }

    val visited = BooleanArray(triCount)
    val stack = IntArray(triCount)
    for (seed in 0 until triCount) {
        if (visited[seed]) continue
        visited[seed] = true
        var top = 0
        stack[top++] = seed
        while (top > 0) {
            val f = stack[--top]
            for (k in 0 until 3) {
                val e = topology.faceEdges[f * 3 + k]
                if (topology.nonManifold[e]) continue // geen betrouwbare buurrelatie over zo'n naad
                val fa = topology.faceA[e]
                val fb = topology.faceB[e]
                val g = if (fa == f) fb else fa
                if (g < 0 || visited[g]) continue
                val a = topology.edgeA[e]
                val b = topology.edgeB[e]
                if (traversesForward(f, a, b) == traversesForward(g, a, b)) flip(g)
                visited[g] = true
                stack[top++] = g
            }
        }
    }

    val closed = BooleanArray(comps.componentCount) { true }
    for (e in 0 until topology.edgeCount) {
        if (topology.faceB[e] < 0 || topology.nonManifold[e]) {
            closed[comps.triangleComponent[topology.faceA[e]]] = false
            val fb = topology.faceB[e]
            if (fb >= 0) closed[comps.triangleComponent[fb]] = false
        }
    }
    // Getekend volume (×6) per component: positief = normalen naar buiten (rechterhandregel).
    val volume = DoubleArray(comps.componentCount)
    val p = mesh.positions
    for (t in 0 until triCount) {
        val a = indices[t * 3] * 3; val b = indices[t * 3 + 1] * 3; val c = indices[t * 3 + 2] * 3
        val det = p[a].toDouble() * (p[b + 1].toDouble() * p[c + 2] - p[b + 2].toDouble() * p[c + 1]) -
            p[a + 1].toDouble() * (p[b].toDouble() * p[c + 2] - p[b + 2].toDouble() * p[c]) +
            p[a + 2].toDouble() * (p[b].toDouble() * p[c + 1] - p[b + 1].toDouble() * p[c])
        volume[comps.triangleComponent[t]] += det
    }
    for (t in 0 until triCount) {
        val comp = comps.triangleComponent[t]
        if (closed[comp] && volume[comp] < 0) flip(t)
    }
    val normals = FloatArray(triCount * 3)
    for (t in 0 until triCount) {
        if (!triangleNormal(p, indices[t * 3], indices[t * 3 + 1], indices[t * 3 + 2], normals, t * 3)) {
            normals[t * 3 + 2] = 1f
        }
    }
    return OrientedMesh(
        IndexedMesh(p, indices, normals, mesh.minX, mesh.minY, mesh.minZ, mesh.maxX, mesh.maxY, mesh.maxZ),
        comps,
        closed
    )
}

/** Resultaat van [clusterDecimateSubset]; [sourceTriangles] wijst per uitvoerdriehoek terug naar
 *  de brondriehoek zodat component-/groepslabels meegenomen kunnen worden. */
class DecimatedSubset(
    val positions: FloatArray,
    val indices: IntArray,
    val faceNormals: FloatArray,
    val sourceTriangles: IntArray
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3
}

/**
 * Grid-cluster-decimatie: hoekpunten snappen naar een raster met celmaat [cellMm]; driehoeken
 * waarvan twee of drie hoekpunten in dezelfde cel vallen, verdwijnen (kleiner dan het raster).
 * Buren delen rastercellen, dus aaneengesloten oppervlak BLIJFT aaneengesloten — in tegenstelling
 * tot "om de N driehoeken overslaan", dat overal gaten prikt. Bewust géén ontdubbeling van
 * samenvallende driehoeken: bij dunne dubbele wanden zou dat juist wél gaten maken.
 *
 * [gridMinX/Y/Z] moet voor alle subsets van één part gelijk zijn, zodat groepen (tank/radiatoren)
 * op hun naad exact dezelfde rasterpunten gebruiken en daar geen kier ontstaat.
 * cellMm ≤ 0 → alleen compact kopiëren (geen decimatie).
 */
fun clusterDecimateSubset(
    mesh: IndexedMesh,
    triangleIds: IntArray,
    cellMm: Float,
    gridMinX: Float = mesh.minX,
    gridMinY: Float = mesh.minY,
    gridMinZ: Float = mesh.minZ
): DecimatedSubset {
    val p = mesh.positions
    val idx = mesh.indices
    val clusterByKey = LongIntMap(triangleIds.size)
    val vertexCluster = IntArray(mesh.vertexCount) { -1 }
    var clusterCount = 0
    var sums = FloatArray(maxOf(48, triangleIds.size)) // xyz-sommen per cluster, groeit mee
    var counts = IntArray(sums.size / 3)

    fun clusterOfVertex(v: Int): Int {
        val cached = vertexCluster[v]
        if (cached >= 0) return cached
        val vb = v * 3
        val key = if (cellMm > 0f) {
            packGridKey(
                floor((p[vb] - gridMinX) / cellMm).toInt(),
                floor((p[vb + 1] - gridMinY) / cellMm).toInt(),
                floor((p[vb + 2] - gridMinZ) / cellMm).toInt()
            )
        } else {
            v.toLong()
        }
        var id = clusterByKey.getOrDefault(key, -1)
        if (id < 0) {
            id = clusterCount++
            clusterByKey.put(key, id)
            if (id * 3 + 2 >= sums.size) {
                sums = sums.copyOf(sums.size * 2)
                counts = counts.copyOf(counts.size * 2)
            }
        }
        sums[id * 3] += p[vb]
        sums[id * 3 + 1] += p[vb + 1]
        sums[id * 3 + 2] += p[vb + 2]
        counts[id]++
        vertexCluster[v] = id
        return id
    }

    val candIdx = IntArray(triangleIds.size * 3)
    val candSrc = IntArray(triangleIds.size)
    var candCount = 0
    for (ti in triangleIds.indices) {
        val t = triangleIds[ti]
        val a = clusterOfVertex(idx[t * 3])
        val b = clusterOfVertex(idx[t * 3 + 1])
        val c = clusterOfVertex(idx[t * 3 + 2])
        if (a == b || b == c || a == c) continue
        candIdx[candCount * 3] = a
        candIdx[candCount * 3 + 1] = b
        candIdx[candCount * 3 + 2] = c
        candSrc[candCount] = t
        candCount++
    }
    val outPositions = FloatArray(clusterCount * 3)
    for (cl in 0 until clusterCount) {
        val n = counts[cl].coerceAtLeast(1)
        outPositions[cl * 3] = sums[cl * 3] / n
        outPositions[cl * 3 + 1] = sums[cl * 3 + 1] / n
        outPositions[cl * 3 + 2] = sums[cl * 3 + 2] / n
    }
    // Gemiddelde celposities kunnen een kandidaat alsnog plat maken — tweede filter op oppervlak.
    val outIndices = IntArray(candCount * 3)
    val outNormals = FloatArray(candCount * 3)
    val outSrc = IntArray(candCount)
    var outTri = 0
    for (t in 0 until candCount) {
        val a = candIdx[t * 3]; val b = candIdx[t * 3 + 1]; val c = candIdx[t * 3 + 2]
        if (!triangleNormal(outPositions, a, b, c, outNormals, outTri * 3)) continue
        outIndices[outTri * 3] = a
        outIndices[outTri * 3 + 1] = b
        outIndices[outTri * 3 + 2] = c
        outSrc[outTri] = candSrc[t]
        outTri++
    }
    return DecimatedSubset(
        outPositions,
        outIndices.copyOf(outTri * 3),
        outNormals.copyOf(outTri * 3),
        outSrc.copyOf(outTri)
    )
}

/** Rastercel waarmee de subset binnen [targetTriangles] blijft (0 = geen decimatie nodig).
 *  Grof zoeken is voldoende: het budget is een richtwaarde, geen harde grens.
 *
 *  Het aantal bewaarde driehoeken schaalt ~1/cel² (oppervlak verdeeld over rastercellen). Daarom
 *  volstaat één proefmeting + een sqrt-sprong naar de geschatte celmaat, gevolgd door een paar
 *  correctiestappen — ~3 passes i.p.v. de ~8–18 van blind van fijn naar grof klimmen. De
 *  correctie-lus garandeert dat het resultaat [kept] ≤ [targetTriangles] oplevert (geen budget-
 *  overschrijding); de schatting kan hooguit iets te grof uitvallen (binnen budget, onzichtbaar). */
fun decimationCellForBudget(mesh: IndexedMesh, triangleIds: IntArray, targetTriangles: Int): Float {
    if (triangleIds.size <= targetTriangles || targetTriangles <= 0) return 0f
    val extent = maxOf(mesh.maxX - mesh.minX, mesh.maxY - mesh.minY, mesh.maxZ - mesh.minZ, 1e-3f)
    var cell = extent / 256f
    var kept = countKeptAfterClustering(mesh, triangleIds, cell)
    if (kept > 0 && kept != targetTriangles) {
        val scaled = cell * sqrt(kept.toFloat() / targetTriangles)
        if (scaled.isFinite() && scaled > 1e-6f) {
            cell = scaled
            kept = countKeptAfterClustering(mesh, triangleIds, cell)
        }
    }
    // De sqrt-sprong kan net te fijn uitvallen → stapsgewijs grover tot we binnen budget zitten.
    // Begrensd op cell < extent: bij cell ≈ extent valt alles in één cel (kept→0), dus de lus
    // termineert altijd. Voor echte meshes klopt de sprong al bijna; dit zijn dan 0–3 stapjes.
    while (kept > targetTriangles && cell < extent) {
        cell = (cell * 1.4f).coerceAtMost(extent)
        kept = countKeptAfterClustering(mesh, triangleIds, cell)
    }
    return cell
}

private fun countKeptAfterClustering(mesh: IndexedMesh, triangleIds: IntArray, cellMm: Float): Int {
    val p = mesh.positions
    val idx = mesh.indices
    val clusterByKey = LongIntMap(triangleIds.size)
    val vertexCluster = IntArray(mesh.vertexCount) { -1 }
    var clusterCount = 0

    fun clusterOfVertex(v: Int): Int {
        val cached = vertexCluster[v]
        if (cached >= 0) return cached
        val vb = v * 3
        val key = packGridKey(
            floor((p[vb] - mesh.minX) / cellMm).toInt(),
            floor((p[vb + 1] - mesh.minY) / cellMm).toInt(),
            floor((p[vb + 2] - mesh.minZ) / cellMm).toInt()
        )
        var id = clusterByKey.getOrDefault(key, -1)
        if (id < 0) {
            id = clusterCount++
            clusterByKey.put(key, id)
        }
        vertexCluster[v] = id
        return id
    }

    var kept = 0
    for (ti in triangleIds.indices) {
        val t = triangleIds[ti]
        val a = clusterOfVertex(idx[t * 3])
        val b = clusterOfVertex(idx[t * 3 + 1])
        val c = clusterOfVertex(idx[t * 3 + 2])
        if (a != b && b != c && a != c) kept++
    }
    return kept
}

/**
 * Vertex-paren (a,b)* van alle feature-randen — boundary + scherp + non-manifold — gesorteerd
 * van lang naar kort. Een afnemer met een lijnenbudget tekent zo de beeldbepalende contouren
 * eerst en laat ribbel-microranden als eerste vallen.
 */
fun featureEdgePairs(mesh: IndexedMesh, topology: EdgeTopology, sharp: BooleanArray): IntArray {
    val ids = ArrayList<Int>()
    for (e in 0 until topology.edgeCount) {
        if (topology.faceB[e] < 0 || sharp[e]) ids.add(e)
    }
    val p = mesh.positions

    fun lengthSq(e: Int): Float {
        val a = topology.edgeA[e] * 3
        val b = topology.edgeB[e] * 3
        val dx = p[a] - p[b]; val dy = p[a + 1] - p[b + 1]; val dz = p[a + 2] - p[b + 2]
        return dx * dx + dy * dy + dz * dz
    }

    ids.sortByDescending { lengthSq(it) }
    val out = IntArray(ids.size * 2)
    for (i in ids.indices) {
        out[i * 2] = topology.edgeA[ids[i]]
        out[i * 2 + 1] = topology.edgeB[ids[i]]
    }
    return out
}

/**
 * True alleen als de driehoek GEHEEL buiten het scherm(+[margin]) ligt, aan één kant. Deels
 * zichtbare driehoeken mogen nooit weggelaten worden — dat geeft gaten aan de schermrand.
 */
fun triangleFullyOffscreen(
    x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float,
    width: Float, height: Float, margin: Float
): Boolean =
    (x0 < -margin && x1 < -margin && x2 < -margin) ||
        (x0 > width + margin && x1 > width + margin && x2 > width + margin) ||
        (y0 < -margin && y1 < -margin && y2 < -margin) ||
        (y0 > height + margin && y1 > height + margin && y2 > height + margin)

/**
 * Markeert per driehoek of hij buiten [box] = [minX,minY,minZ,maxX,maxY,maxZ] ligt (zwaartepunt
 * buiten box + [marginMm]). Er wordt per verbonden component gestemd: een component die ≥80%
 * buiten ligt is geheel "buiten" (een radiator blijft één geheel), ≤20% is geheel "binnen";
 * alleen bij gemengde componenten beslist elke driehoek zelf (snede op het boxvlak).
 */
fun trianglesOutsideBox(
    mesh: IndexedMesh,
    components: MeshComponents,
    box: FloatArray,
    marginMm: Float
): BooleanArray {
    val triCount = mesh.triangleCount
    val out = BooleanArray(triCount)
    if (triCount == 0 || box.size < 6) return out
    val loX = box[0] - marginMm; val loY = box[1] - marginMm; val loZ = box[2] - marginMm
    val hiX = box[3] + marginMm; val hiY = box[4] + marginMm; val hiZ = box[5] + marginMm
    val p = mesh.positions
    val idx = mesh.indices
    val outsideTri = BooleanArray(triCount)
    val outsideCount = IntArray(components.componentCount)
    val totalCount = IntArray(components.componentCount)
    for (t in 0 until triCount) {
        val a = idx[t * 3] * 3; val b = idx[t * 3 + 1] * 3; val c = idx[t * 3 + 2] * 3
        val cx = (p[a] + p[b] + p[c]) / 3f
        val cy = (p[a + 1] + p[b + 1] + p[c + 1]) / 3f
        val cz = (p[a + 2] + p[b + 2] + p[c + 2]) / 3f
        val outside = cx < loX || cx > hiX || cy < loY || cy > hiY || cz < loZ || cz > hiZ
        outsideTri[t] = outside
        val comp = components.triangleComponent[t]
        totalCount[comp]++
        if (outside) outsideCount[comp]++
    }
    for (t in 0 until triCount) {
        val comp = components.triangleComponent[t]
        val total = totalCount[comp].coerceAtLeast(1)
        val fraction = outsideCount[comp].toFloat() / total
        out[t] = when {
            fraction >= 0.8f -> true
            fraction <= 0.2f -> false
            else -> outsideTri[t]
        }
    }
    return out
}
