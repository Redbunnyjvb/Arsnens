package com.example.arsens.data

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Een ingelezen STL-mesh, voor de preview gedecimeerd tot een beheersbaar aantal driehoeken en
 * opgeslagen als platte float-arrays (geen object per driehoek → weinig geheugen, snel tekenen).
 * [vertices] bevat per driehoek 9 floats (v0,v1,v2), [normals] 3 floats (genormaliseerde vlaknormaal).
 */
class StlMesh(
    val vertices: FloatArray,
    val normals: FloatArray,
    val totalTriangleCount: Int,
    val minX: Float, val minY: Float, val minZ: Float,
    val maxX: Float, val maxY: Float, val maxZ: Float
) {
    val triangleCount: Int get() = vertices.size / 9
    val isEmpty: Boolean get() = triangleCount == 0
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
    val centerZ: Float get() = (minZ + maxZ) / 2f
    val maxExtent: Float get() = maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(0.001f)

    companion object {
        val EMPTY = StlMesh(FloatArray(0), FloatArray(0), 0, 0f, 0f, 0f, 0f, 0f, 0f)
    }
}

/** Rotatiematrix (row-major, 9 floats) voor Euler-hoeken in graden, volgorde X→Y→Z (R = Rz·Ry·Rx). */
fun rotationMatrix3(rotXDeg: Int, rotYDeg: Int, rotZDeg: Int): FloatArray {
    val a = Math.toRadians(rotXDeg.toDouble())
    val b = Math.toRadians(rotYDeg.toDouble())
    val c = Math.toRadians(rotZDeg.toDouble())
    val sa = sin(a).toFloat(); val ca = cos(a).toFloat()
    val sb = sin(b).toFloat(); val cb = cos(b).toFloat()
    val sc = sin(c).toFloat(); val cc = cos(c).toFloat()
    return floatArrayOf(
        cc * cb, cc * sb * sa - sc * ca, cc * sb * ca + sc * sa,
        sc * cb, sc * sb * sa + cc * ca, sc * sb * ca - cc * sa,
        -sb,     cb * sa,                cb * ca
    )
}

/** Afmetingen (breedte/diepte/hoogte) van de bounding box ná rotatie om het mesh-midden (schaal 1).
 *  Wordt gebruikt door "Passend maken" zodat de geroteerde STL exact in de trafo-box past. */
fun StlMesh.rotatedExtents(rotXDeg: Int, rotYDeg: Int, rotZDeg: Int): FloatArray {
    if (isEmpty) return floatArrayOf(0.001f, 0.001f, 0.001f)
    val r = rotationMatrix3(rotXDeg, rotYDeg, rotZDeg)
    val cx = centerX; val cy = centerY; val cz = centerZ
    var miX = Float.MAX_VALUE; var miY = Float.MAX_VALUE; var miZ = Float.MAX_VALUE
    var maX = -Float.MAX_VALUE; var maY = -Float.MAX_VALUE; var maZ = -Float.MAX_VALUE
    val xs = floatArrayOf(minX, maxX); val ys = floatArrayOf(minY, maxY); val zs = floatArrayOf(minZ, maxZ)
    for (xi in 0..1) {
        for (yi in 0..1) {
            for (zi in 0..1) {
                val dx = xs[xi] - cx; val dy = ys[yi] - cy; val dz = zs[zi] - cz
                val px = r[0] * dx + r[1] * dy + r[2] * dz
                val py = r[3] * dx + r[4] * dy + r[5] * dz
                val pz = r[6] * dx + r[7] * dy + r[8] * dz
                if (px < miX) miX = px; if (px > maX) maX = px
                if (py < miY) miY = py; if (py > maY) maY = py
                if (pz < miZ) miZ = pz; if (pz > maZ) maZ = pz
            }
        }
    }
    return floatArrayOf(
        (maX - miX).coerceAtLeast(0.001f),
        (maY - miY).coerceAtLeast(0.001f),
        (maZ - miZ).coerceAtLeast(0.001f)
    )
}

/** Bounding box ná rotatie om het mesh-midden (schaal 1), in dezelfde coördinaten als de
 *  render-transform vóór schaal/offset: q = c + R·(hoekpunt − c). Wereld-bbox = schaal·q + offset.
 *  Retourneert [minX,minY,minZ,maxX,maxY,maxZ] — basis voor automatische assembly-uitlijning. */
fun StlMesh.rotatedBounds(rotXDeg: Int, rotYDeg: Int, rotZDeg: Int): FloatArray =
    rotatedBoundsOfBox(floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ), rotXDeg, rotYDeg, rotZDeg)

/** Als [rotatedBounds], maar voor een willekeurige deel-box [box] (bv. de geschatte wand-box).
 *  De rotatie blijft om het MESH-midden draaien — identiek aan de render-transform. */
fun StlMesh.rotatedBoundsOfBox(box: FloatArray, rotXDeg: Int, rotYDeg: Int, rotZDeg: Int): FloatArray {
    if (isEmpty || box.size < 6) return floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
    val r = rotationMatrix3(rotXDeg, rotYDeg, rotZDeg)
    val cx = centerX; val cy = centerY; val cz = centerZ
    var miX = Float.MAX_VALUE; var miY = Float.MAX_VALUE; var miZ = Float.MAX_VALUE
    var maX = -Float.MAX_VALUE; var maY = -Float.MAX_VALUE; var maZ = -Float.MAX_VALUE
    val xs = floatArrayOf(box[0], box[3]); val ys = floatArrayOf(box[1], box[4]); val zs = floatArrayOf(box[2], box[5])
    for (xi in 0..1) {
        for (yi in 0..1) {
            for (zi in 0..1) {
                val dx = xs[xi] - cx; val dy = ys[yi] - cy; val dz = zs[zi] - cz
                val px = cx + r[0] * dx + r[1] * dy + r[2] * dz
                val py = cy + r[3] * dx + r[4] * dy + r[5] * dz
                val pz = cz + r[6] * dx + r[7] * dy + r[8] * dz
                if (px < miX) miX = px; if (px > maX) maX = px
                if (py < miY) miY = py; if (py > maY) maY = py
                if (pz < miZ) miZ = pz; if (pz > maZ) maZ = pz
            }
        }
    }
    return floatArrayOf(miX, miY, miZ, maX, maY, maZ)
}

/**
 * Schat de WAND-box van de mesh: per as de buitenste vlakken met grote oppervlakte-concentratie.
 * Uitstulpsels (koelribben, radiatoren, doorvoeren) beïnvloeden de trafo-afmetingen dan niet —
 * sensoren en tags zitten immers op de wand, niet op de ribben.
 *
 * Werkwijze: oppervlakte-gewogen histogram per as van driehoeken waarvan de normaal dominant
 * (|n|≥0,8) langs die as wijst; de wand is één vlak en geeft één hoge piek, ribben spreiden
 * over vele posities. De buitenste bins ≥ 25% van de piek zijn de wandposities.
 * Valt per as terug op de volledige bbox als er geen duidelijke pieken zijn.
 */
fun StlMesh.estimateCoreBounds(bins: Int = 256): FloatArray {
    val fallback = floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
    if (isEmpty) return fallback
    val result = fallback.copyOf()
    val v = vertices
    val n = normals
    val lo = floatArrayOf(minX, minY, minZ)
    val range = floatArrayOf(maxX - minX, maxY - minY, maxZ - minZ)
    val hists = Array(3) { FloatArray(bins) }
    for (t in 0 until triangleCount) {
        val nb = t * 3
        var axis = -1
        for (a in 0..2) {
            if (kotlin.math.abs(n[nb + a]) >= 0.8f) {
                axis = a
                break
            }
        }
        if (axis < 0 || range[axis] <= 1e-3f) continue
        val b = t * 9
        val ux = v[b + 3] - v[b]; val uy = v[b + 4] - v[b + 1]; val uz = v[b + 5] - v[b + 2]
        val wx = v[b + 6] - v[b]; val wy = v[b + 7] - v[b + 1]; val wz = v[b + 8] - v[b + 2]
        val cxn = uy * wz - uz * wy
        val cyn = uz * wx - ux * wz
        val czn = ux * wy - uy * wx
        val area = sqrt(cxn * cxn + cyn * cyn + czn * czn)
        val centroid = (v[b + axis] + v[b + 3 + axis] + v[b + 6 + axis]) / 3f
        val bin = (((centroid - lo[axis]) / range[axis]) * bins).toInt().coerceIn(0, bins - 1)
        hists[axis][bin] += area
    }
    for (axis in 0..2) {
        val hist = hists[axis]
        var peak = 0f
        for (value in hist) if (value > peak) peak = value
        if (peak <= 0f) continue
        val threshold = peak * 0.25f
        var lowBin = -1
        var highBin = -1
        for (bIdx in 0 until bins) if (hist[bIdx] >= threshold) { lowBin = bIdx; break }
        for (bIdx in bins - 1 downTo 0) if (hist[bIdx] >= threshold) { highBin = bIdx; break }
        if (lowBin < 0 || highBin <= lowBin) continue
        val binWidth = range[axis] / bins
        result[axis] = lo[axis] + (lowBin + 0.5f) * binWidth
        result[axis + 3] = lo[axis] + (highBin + 0.5f) * binWidth
    }
    return result
}

/** Render-transform van een STL-model: projectframe-punt = s·R·(v − center) + center·s + offset.
 *  Zelfde formule als de STL-preview, zodat AR-overlay en preview het model identiek plaatsen. */
class StlModelTransform(
    val r: FloatArray,
    val s: Float,
    val tx: Float,
    val ty: Float,
    val tz: Float
) {
    /** Transformeert [v] (x,y,z aaneengesloten, model-mm) in-place naar projectframe-mm in [out]. */
    fun apply(v: FloatArray, out: FloatArray) {
        var i = 0
        while (i < v.size - 2) {
            val x = v[i]; val y = v[i + 1]; val z = v[i + 2]
            out[i] = s * (r[0] * x + r[1] * y + r[2] * z) + tx
            out[i + 1] = s * (r[3] * x + r[4] * y + r[5] * z) + ty
            out[i + 2] = s * (r[6] * x + r[7] * y + r[8] * z) + tz
            i += 3
        }
    }
}

fun stlModelTransform(model: StlModel, mesh: StlMesh): StlModelTransform {
    val r = rotationMatrix3(model.rotationDeg.x, model.rotationDeg.y, model.rotationDeg.z)
    val s = model.scalePercent / 100f
    val cx = mesh.centerX; val cy = mesh.centerY; val cz = mesh.centerZ
    val rcx = r[0] * cx + r[1] * cy + r[2] * cz
    val rcy = r[3] * cx + r[4] * cy + r[5] * cz
    val rcz = r[6] * cx + r[7] * cy + r[8] * cz
    return StlModelTransform(
        r = r,
        s = s,
        tx = s * (cx - rcx) + model.offsetMm.x.toFloat(),
        ty = s * (cy - rcy) + model.offsetMm.y.toFloat(),
        tz = s * (cz - rcz) + model.offsetMm.z.toFloat()
    )
}

/**
 * Eerste snijpunt van een straal met het (getransformeerde) model, als afstand in PROJECTFRAME-mm
 * langs de straal. De straal staat in projectframe-coördinaten; hij wordt éénmalig naar
 * model-ruimte teruggerekend (inverse van [StlModelTransform]) zodat de — mogelijk miljoenen —
 * driehoeken niet per stuk getransformeerd hoeven te worden. Geen hit → null.
 *
 * Gebruik: "tag op modeloppervlak" — vanaf buiten de trafo langs de vlaknormaal schieten en de
 * eerste (= buitenste) modelwand vinden waar de fysieke tag op geplakt zit.
 */
fun modelSurfaceDistanceAlongRay(
    model: StlModel,
    mesh: StlMesh,
    originMm: FloatArray,
    direction: FloatArray
): Float? {
    if (mesh.isEmpty) return null
    val xf = stlModelTransform(model, mesh)
    val s = xf.s
    if (s <= 1e-6f) return null
    val r = xf.r
    // Inverse: v_model = Rᵀ · ((p − t) / s); richting: d_model = Rᵀ · d (R is orthonormaal).
    val px = (originMm[0] - xf.tx) / s
    val py = (originMm[1] - xf.ty) / s
    val pz = (originMm[2] - xf.tz) / s
    val ox = r[0] * px + r[3] * py + r[6] * pz
    val oy = r[1] * px + r[4] * py + r[7] * pz
    val oz = r[2] * px + r[5] * py + r[8] * pz
    val dx = r[0] * direction[0] + r[3] * direction[1] + r[6] * direction[2]
    val dy = r[1] * direction[0] + r[4] * direction[1] + r[7] * direction[2]
    val dz = r[2] * direction[0] + r[5] * direction[1] + r[8] * direction[2]
    val tModel = mesh.firstRayHitDistance(ox, oy, oz, dx, dy, dz) ?: return null
    // Afstand in model-mm → projectframe-mm via de uniforme schaal.
    return tModel * s
}

/** Möller–Trumbore over alle bewaarde driehoeken: kleinste positieve t, of null. Model-ruimte. */
fun StlMesh.firstRayHitDistance(
    ox: Float, oy: Float, oz: Float,
    dx: Float, dy: Float, dz: Float
): Float? {
    val v = vertices
    var best = Float.MAX_VALUE
    var i = 0
    while (i < v.size - 8) {
        val ax = v[i]; val ay = v[i + 1]; val az = v[i + 2]
        val e1x = v[i + 3] - ax; val e1y = v[i + 4] - ay; val e1z = v[i + 5] - az
        val e2x = v[i + 6] - ax; val e2y = v[i + 7] - ay; val e2z = v[i + 8] - az
        // p = d × e2
        val px = dy * e2z - dz * e2y
        val py = dz * e2x - dx * e2z
        val pz = dx * e2y - dy * e2x
        val det = e1x * px + e1y * py + e1z * pz
        if (det > -1e-7f && det < 1e-7f) { i += 9; continue }
        val invDet = 1f / det
        val tx = ox - ax; val ty = oy - ay; val tz = oz - az
        val u = (tx * px + ty * py + tz * pz) * invDet
        if (u < -1e-4f || u > 1.0001f) { i += 9; continue }
        // q = t × e1
        val qx = ty * e1z - tz * e1y
        val qy = tz * e1x - tx * e1z
        val qz = tx * e1y - ty * e1x
        val w = (dx * qx + dy * qy + dz * qz) * invDet
        if (w < -1e-4f || u + w > 1.0001f) { i += 9; continue }
        val t = (e2x * qx + e2y * qy + e2z * qz) * invDet
        if (t > 1e-3f && t < best) best = t
        i += 9
    }
    return if (best == Float.MAX_VALUE) null else best
}

/** Leest binaire en ASCII STL-bestanden in. Faalt nooit hard: bij twijfel een leeg mesh. */
object StlParser {
    /** Maximaal aantal driehoeken dat we bewaren (geheugencap) PER bestand. Een assembly bestaat
     *  uit meerdere delen die tegelijk in het geheugen staan (5 delen × cap × 48 B/driehoek),
     *  dus de cap staat lager dan vroeger (1M); 400k is in de preview niet te onderscheiden.
     *  De bounding box gebruikt altijd alle driehoeken. */
    private const val RENDER_CAP = 400_000

    /** Records per leesblok bij streamen (50 bytes per driehoek → 64k-blokken). */
    private const val STREAM_RECORDS = 1280
    private val WHITESPACE = Regex("\\s+")

    /** Streamt het bestand in plaats van `readBytes()`: een 240 MB STL zou anders als één
     *  byte-array in het geheugen staan en op een telefoon direct OOM geven.
     *
     *  Boven de cap wordt in TWEE passes gelezen met oppervlakte-prioriteit: grote (wand)driehoeken
     *  blijven állemaal behouden, alleen het fijne detail (koelribben e.d.) wordt uitgedund.
     *  Daardoor is "Vulling 100%" ook echt dicht — domme om-de-N-decimatie sloeg juist ook de
     *  grote wanddriehoeken over en gaf gaten.
     *
     *  [onProgress] (0..1, monotoon, gethrottled) voedt de laadbalk; binaire bestanden kennen het
     *  totaal uit de header, het twee-pass-pad telt de analyse als 0→0,4 en het lezen als 0,4→1. */
    fun parseFile(file: File, onProgress: ((Float) -> Unit)? = null): StlMesh =
        when (detectFormat(file)) {
            MeshFormat.OBJ -> parseObjFile(file, onProgress)
            MeshFormat.PLY -> parsePlyFile(file, onProgress)
            MeshFormat.STL -> parseStlFile(file, onProgress)
        }

    /** Leest binaire en ASCII STL-bestanden in — het oorspronkelijke pad, ongewijzigd. */
    private fun parseStlFile(file: File, onProgress: ((Float) -> Unit)? = null): StlMesh =
        runCatching {
            val sizeBytes = file.length()
            val head = ByteArray(84)
            val headRead = file.inputStream().use { readFully(it, head, head.size) }
            if (headRead < 84) return@runCatching parseAscii(head.copyOf(headRead))
            val mesh = if (looksBinary(head, sizeBytes)) {
                val total = readUInt32LE(head, 80).toInt().coerceAtLeast(0)
                if (total <= RENDER_CAP) {
                    file.inputStream().buffered(64 * 1024).use { input ->
                        skipFully(input, 84L)
                        parseBinaryStream(input, total, ProgressReporter(onProgress, 0f, 1f))
                    }
                } else {
                    val plan = file.inputStream().buffered(64 * 1024).use { input ->
                        skipFully(input, 84L)
                        analyzeBinaryAreas(input, total, ProgressReporter(onProgress, 0f, 0.4f))
                    }
                    file.inputStream().buffered(64 * 1024).use { input ->
                        skipFully(input, 84L)
                        parseBinaryPrioritized(input, total, plan, ProgressReporter(onProgress, 0.4f, 0.6f))
                    }
                }
            } else {
                file.inputStream().buffered(64 * 1024).use { input ->
                    val h = ByteArray(84)
                    val r = readFully(input, h, h.size)
                    parseAsciiStream(h.copyOf(r), input, sizeBytes, ProgressReporter(onProgress, 0f, 1f))
                }
            }
            onProgress?.invoke(1f)
            mesh
        }.getOrDefault(StlMesh.EMPTY)

    /** Gethrottlede voortgang binnen een (deel)bereik: meldt pas bij ≥1% verschuiving. */
    private class ProgressReporter(
        private val onProgress: ((Float) -> Unit)?,
        private val from: Float,
        private val span: Float
    ) {
        private var lastReported = -1f

        fun report(fraction: Float) {
            val callback = onProgress ?: return
            val value = from + span * fraction.coerceIn(0f, 1f)
            if (value - lastReported >= 0.01f) {
                lastReported = value
                callback(value)
            }
        }
    }

    fun parse(bytes: ByteArray): StlMesh =
        runCatching {
            if (looksBinary(bytes, bytes.size.toLong())) parseBinary(bytes) else parseAscii(bytes)
        }.getOrDefault(StlMesh.EMPTY)

    // ───────────────────────── Extra mesh-formaten: OBJ & PLY ─────────────────────────
    // Naast STL leest de app Wavefront OBJ en Stanford PLY. Beide parsers vullen exact dezelfde
    // MeshBuilder (bounding box + zelf-berekende vlaknormalen) en geven een gewone StlMesh terug,
    // zodat preview, AR, meten, uitlijnen en raycasting niets van het bronformaat hoeven te weten.
    // De keuze gebeurt op de bestandsextensie; STL houdt exact het oude pad (geen regressie).

    private enum class MeshFormat { STL, OBJ, PLY }

    private fun detectFormat(file: File): MeshFormat =
        when (file.extension.lowercase()) {
            "obj" -> MeshFormat.OBJ
            "ply" -> MeshFormat.PLY
            "stl" -> MeshFormat.STL
            // Onbekende/lege extensie: alleen het PLY-magic ("ply") is betrouwbaar te snuiven;
            // al het andere valt terug op STL (dat zelf binair vs. ASCII detecteert) = oud gedrag.
            else -> if (looksLikePly(file)) MeshFormat.PLY else MeshFormat.STL
        }

    private fun looksLikePly(file: File): Boolean =
        runCatching {
            file.inputStream().use { input ->
                val b = ByteArray(4)
                val n = readFully(input, b, 4)
                n >= 3 && b[0] == 'p'.code.toByte() && b[1] == 'l'.code.toByte() && b[2] == 'y'.code.toByte()
            }
        }.getOrDefault(false)

    /** Voegt driehoek (i0,i1,i2) toe aan de bounds en — als [add] — aan de mesh. Retourneert of er
     *  daadwerkelijk een driehoek is toegevoegd (voor de kept-teller bij decimatie). */
    private fun emitTri(
        builder: MeshBuilder,
        coords: FloatArray,
        i0: Int, i1: Int, i2: Int,
        add: Boolean
    ): Boolean {
        val a = i0 * 3; val b = i1 * 3; val c = i2 * 3
        builder.bounds(
            coords[a], coords[a + 1], coords[a + 2],
            coords[b], coords[b + 1], coords[b + 2],
            coords[c], coords[c + 1], coords[c + 2]
        )
        if (add) {
            builder.add(
                coords[a], coords[a + 1], coords[a + 2],
                coords[b], coords[b + 1], coords[b + 2],
                coords[c], coords[c + 1], coords[c + 2]
            )
        }
        return add
    }

    // ── Wavefront OBJ (tekst) ──
    /** Leest 'v'-hoekpunten en 'f'-faces; polygonen worden fan-getrianguleerd, '/'-suffixen
     *  (textuur/normaal-indexen) en negatieve (relatieve) indexen worden afgehandeld. Twee passes
     *  over het bestand (lokale schijf, goedkoop): pass 1 verzamelt alle vertices en telt de
     *  driehoeken, pass 2 bouwt de mesh met dezelfde decimatie/cap-logica als binaire STL. */
    private fun parseObjFile(file: File, onProgress: ((Float) -> Unit)? = null): StlMesh =
        runCatching {
            var coords = FloatArray(3 * 1024)
            var cc = 0
            var triTotal = 0
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.length < 2) continue
                    val c1 = line[1]
                    if (c1 != ' ' && c1 != '\t') continue
                    when (line[0]) {
                        'v' -> {
                            val p = line.split(WHITESPACE)
                            val x = p.getOrNull(1)?.toFloatOrNull() ?: 0f
                            val y = p.getOrNull(2)?.toFloatOrNull() ?: 0f
                            val z = p.getOrNull(3)?.toFloatOrNull() ?: 0f
                            if (cc + 3 > coords.size) coords = coords.copyOf(coords.size * 2)
                            coords[cc++] = x; coords[cc++] = y; coords[cc++] = z
                        }
                        'f' -> {
                            val verts = countObjFaceVerts(line)
                            if (verts >= 3) triTotal += verts - 2
                        }
                    }
                }
            }
            val vertCount = cc / 3
            if (vertCount == 0 || triTotal == 0) return@runCatching StlMesh.EMPTY
            val decimation = maxOf(1, triTotal / RENDER_CAP)
            val hardCap = RENDER_CAP + RENDER_CAP / 20
            val builder = MeshBuilder()
            var facet = 0
            var kept = 0
            file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.length < 2 || line[0] != 'f') continue
                    val c1 = line[1]
                    if (c1 != ' ' && c1 != '\t') continue
                    var v0 = -1
                    var vPrev = -1
                    var idx = 1
                    val len = line.length
                    while (idx < len) {
                        while (idx < len && (line[idx] == ' ' || line[idx] == '\t')) idx++
                        if (idx >= len) break
                        val start = idx
                        while (idx < len && line[idx] != ' ' && line[idx] != '\t') idx++
                        var slash = start
                        while (slash < idx && line[slash] != '/') slash++
                        val viRaw = line.substring(start, slash).toIntOrNull() ?: continue
                        val vi = if (viRaw < 0) vertCount + viRaw else viRaw - 1
                        if (vi < 0 || vi >= vertCount) continue
                        when {
                            v0 < 0 -> v0 = vi
                            vPrev < 0 -> vPrev = vi
                            else -> {
                                val take = facet % decimation == 0
                                if (emitTri(builder, coords, v0, vPrev, vi, take && kept < hardCap)) kept++
                                facet++
                                if (facet and 0xFFF == 0) {
                                    onProgress?.invoke((facet.toFloat() / triTotal).coerceIn(0f, 1f))
                                }
                                vPrev = vi
                            }
                        }
                    }
                }
            }
            onProgress?.invoke(1f)
            builder.build(triTotal)
        }.getOrDefault(StlMesh.EMPTY)

    /** Aantal hoekpunten in een OBJ 'f'-regel (witruimte-gescheiden tokens na de 'f'). */
    private fun countObjFaceVerts(line: String): Int {
        var count = 0
        var inToken = false
        var idx = 1
        val len = line.length
        while (idx < len) {
            val ch = line[idx]
            if (ch == ' ' || ch == '\t') {
                inToken = false
            } else if (!inToken) {
                count++
                inToken = true
            }
            idx++
        }
        return count
    }

    // ── Stanford PLY (ASCII + binair, little/big-endian) ──
    private const val PLY_ASCII = 0
    private const val PLY_LE = 1
    private const val PLY_BE = 2

    private class PlyProp(val isList: Boolean, val countType: String, val type: String, val name: String)
    private class PlyElement(val name: String, val count: Int, val props: MutableList<PlyProp> = mutableListOf())
    private class PlyHeader(val format: Int, val elements: List<PlyElement>)

    /** Eén ASCII-regel byte-voor-byte uit de stream (zodat de stroompositie ná de header exact op
     *  het begin van het binaire blok staat — een gebufferde reader zou te ver vooruit lezen). */
    private fun readAsciiLine(input: java.io.InputStream): String? {
        val sb = StringBuilder()
        var any = false
        while (true) {
            val b = input.read()
            if (b < 0) return if (any) sb.toString() else null
            any = true
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun readPlyHeader(input: java.io.InputStream): PlyHeader? {
        if ((readAsciiLine(input)?.trim()) != "ply") return null
        var format = -1
        val elements = mutableListOf<PlyElement>()
        while (true) {
            val line = (readAsciiLine(input) ?: return null).trim()
            if (line == "end_header") break
            if (line.isEmpty()) continue
            val p = line.split(WHITESPACE)
            when (p[0]) {
                "format" -> format = when (p.getOrNull(1)) {
                    "ascii" -> PLY_ASCII
                    "binary_little_endian" -> PLY_LE
                    "binary_big_endian" -> PLY_BE
                    else -> -1
                }
                "element" -> elements += PlyElement(
                    name = p.getOrNull(1) ?: "",
                    count = p.getOrNull(2)?.toIntOrNull() ?: 0
                )
                "property" -> {
                    val el = elements.lastOrNull() ?: continue
                    if (p.getOrNull(1) == "list") {
                        el.props += PlyProp(true, p.getOrNull(2) ?: "uchar", p.getOrNull(3) ?: "int", p.getOrNull(4) ?: "")
                    } else {
                        el.props += PlyProp(false, "", p.getOrNull(1) ?: "float", p.getOrNull(2) ?: "")
                    }
                }
            }
        }
        if (format < 0) return null
        return PlyHeader(format, elements)
    }

    private fun plyTypeSize(t: String): Int = when (t) {
        "char", "uchar", "int8", "uint8" -> 1
        "short", "ushort", "int16", "uint16" -> 2
        "double", "float64" -> 8
        else -> 4 // int/uint/int32/uint32/float/float32 + onbekend
    }

    private fun readPlyScalar(buf: ByteBuffer, pos: Int, type: String): Float {
        buf.position(pos)
        return when (type) {
            "char", "int8" -> buf.get().toFloat()
            "uchar", "uint8" -> (buf.get().toInt() and 0xFF).toFloat()
            "short", "int16" -> buf.short.toFloat()
            "ushort", "uint16" -> (buf.short.toInt() and 0xFFFF).toFloat()
            "int", "int32" -> buf.int.toFloat()
            "uint", "uint32" -> (buf.int.toLong() and 0xFFFFFFFFL).toFloat()
            "double", "float64" -> buf.double.toFloat()
            else -> buf.float
        }
    }

    /** Leest één index/teller sequentieel uit [buf] (positie schuift mee). */
    private fun readPlyInt(buf: ByteBuffer, type: String): Int = when (type) {
        "char", "uchar", "int8", "uint8" -> buf.get().toInt() and 0xFF
        "short", "ushort", "int16", "uint16" -> buf.short.toInt() and 0xFFFF
        "double", "float64" -> buf.double.toInt()
        else -> buf.int
    }

    private fun parsePlyFile(file: File, onProgress: ((Float) -> Unit)? = null): StlMesh =
        runCatching {
            file.inputStream().buffered(64 * 1024).use { input ->
                val header = readPlyHeader(input) ?: return@runCatching StlMesh.EMPTY
                val mesh = if (header.format == PLY_ASCII) {
                    parsePlyAscii(input, header, onProgress)
                } else {
                    val order = if (header.format == PLY_LE) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
                    parsePlyBinary(input, header, order, onProgress)
                }
                onProgress?.invoke(1f)
                mesh
            }
        }.getOrDefault(StlMesh.EMPTY)

    private fun parsePlyAscii(
        input: java.io.InputStream,
        header: PlyHeader,
        onProgress: ((Float) -> Unit)?
    ): StlMesh {
        val builder = MeshBuilder()
        var coords = FloatArray(0)
        var vertCount = 0
        var triTotal = 0
        for (el in header.elements) {
            when (el.name) {
                "vertex" -> {
                    val names = el.props.map { it.name }
                    val xi = names.indexOf("x"); val yi = names.indexOf("y"); val zi = names.indexOf("z")
                    coords = FloatArray(el.count * 3)
                    vertCount = el.count
                    for (i in 0 until el.count) {
                        val vals = (readAsciiLine(input) ?: break).trim().split(WHITESPACE)
                        coords[i * 3] = vals.getOrNull(xi)?.toFloatOrNull() ?: 0f
                        coords[i * 3 + 1] = vals.getOrNull(yi)?.toFloatOrNull() ?: 0f
                        coords[i * 3 + 2] = vals.getOrNull(zi)?.toFloatOrNull() ?: 0f
                    }
                }
                "face" -> {
                    val stride = maxOf(1, el.count / RENDER_CAP)
                    val hardCap = RENDER_CAP + RENDER_CAP / 20
                    var facet = 0
                    var kept = 0
                    for (i in 0 until el.count) {
                        val vals = (readAsciiLine(input) ?: break).trim().split(WHITESPACE)
                        val n = vals.getOrNull(0)?.toIntOrNull() ?: continue
                        if (n < 3 || vals.size < n + 1) continue
                        val i0 = vals[1].toIntOrNull() ?: continue
                        for (k in 2 until n) {
                            val i1 = vals[k].toIntOrNull() ?: continue
                            val i2 = vals[k + 1].toIntOrNull() ?: continue
                            val take = facet % stride == 0
                            facet++
                            triTotal++
                            if (i0 in 0 until vertCount && i1 in 0 until vertCount && i2 in 0 until vertCount) {
                                if (emitTri(builder, coords, i0, i1, i2, take && kept < hardCap)) kept++
                            }
                        }
                        if (i and 0xFFF == 0 && el.count > 0) onProgress?.invoke(0.5f + 0.5f * i / el.count)
                    }
                }
                else -> repeat(el.count) { readAsciiLine(input) }
            }
        }
        return builder.build(triTotal)
    }

    private fun parsePlyBinary(
        input: java.io.InputStream,
        header: PlyHeader,
        order: ByteOrder,
        onProgress: ((Float) -> Unit)?
    ): StlMesh {
        val builder = MeshBuilder()
        var coords = FloatArray(0)
        var vertCount = 0
        var triTotal = 0
        for (el in header.elements) {
            when (el.name) {
                "vertex" -> {
                    // Vaste record-stride + byte-offsets/typen van x,y,z (overige props worden mee-
                    // overgeslagen via de stride). Een lijst-property binnen vertex kan geen vaste
                    // stride hebben → niet ondersteund.
                    var stride = 0
                    var xOff = -1; var yOff = -1; var zOff = -1
                    var xType = "float"; var yType = "float"; var zType = "float"
                    for (prop in el.props) {
                        if (prop.isList) return StlMesh.EMPTY
                        when (prop.name) {
                            "x" -> { xOff = stride; xType = prop.type }
                            "y" -> { yOff = stride; yType = prop.type }
                            "z" -> { zOff = stride; zType = prop.type }
                        }
                        stride += plyTypeSize(prop.type)
                    }
                    if (xOff < 0 || yOff < 0 || zOff < 0 || stride <= 0) return StlMesh.EMPTY
                    coords = FloatArray(el.count * 3)
                    vertCount = el.count
                    val recsPerBlock = 4096
                    val block = ByteArray(stride * recsPerBlock)
                    var read = 0
                    var base = 0
                    while (read < el.count) {
                        val want = minOf(recsPerBlock, el.count - read)
                        val got = readFully(input, block, want * stride)
                        val have = got / stride
                        if (have <= 0) break
                        val buf = ByteBuffer.wrap(block, 0, have * stride).order(order)
                        for (r in 0 until have) {
                            val rb = r * stride
                            coords[base++] = readPlyScalar(buf, rb + xOff, xType)
                            coords[base++] = readPlyScalar(buf, rb + yOff, yType)
                            coords[base++] = readPlyScalar(buf, rb + zOff, zType)
                        }
                        read += have
                        if (el.count > 0) onProgress?.invoke(0.4f * read / el.count)
                        if (have < want) break
                    }
                }
                "face" -> {
                    val listProp = el.props.firstOrNull { it.isList } ?: continue
                    val countSize = plyTypeSize(listProp.countType)
                    val idxSize = plyTypeSize(listProp.type)
                    val stride = maxOf(1, el.count / RENDER_CAP)
                    val hardCap = RENDER_CAP + RENDER_CAP / 20
                    var facet = 0
                    var kept = 0
                    val countBuf = ByteArray(8)
                    var idxBuf = ByteArray(256)
                    for (i in 0 until el.count) {
                        if (readFully(input, countBuf, countSize) < countSize) break
                        val n = readPlyInt(ByteBuffer.wrap(countBuf, 0, countSize).order(order), listProp.countType)
                        if (n < 0) break
                        val needed = n * idxSize
                        if (needed > idxBuf.size) idxBuf = ByteArray(needed)
                        if (readFully(input, idxBuf, needed) < needed) break
                        if (n < 3) continue
                        val ibuf = ByteBuffer.wrap(idxBuf, 0, needed).order(order)
                        val i0 = readPlyInt(ibuf, listProp.type)
                        var prev = readPlyInt(ibuf, listProp.type)
                        for (k in 2 until n) {
                            val cur = readPlyInt(ibuf, listProp.type)
                            val take = facet % stride == 0
                            facet++
                            triTotal++
                            if (i0 in 0 until vertCount && prev in 0 until vertCount && cur in 0 until vertCount) {
                                if (emitTri(builder, coords, i0, prev, cur, take && kept < hardCap)) kept++
                            }
                            prev = cur
                        }
                        if (i and 0xFFF == 0 && el.count > 0) onProgress?.invoke(0.4f + 0.6f * i / el.count)
                    }
                }
                else -> {
                    // Onbekend element: alleen overslaan als alle props een vaste grootte hebben.
                    var recSize = 0
                    var fixed = true
                    for (prop in el.props) {
                        if (prop.isList) { fixed = false; break }
                        recSize += plyTypeSize(prop.type)
                    }
                    if (!fixed) return builder.build(triTotal)
                    skipFully(input, recSize.toLong() * el.count)
                }
            }
        }
        return builder.build(triTotal)
    }

    private fun skipFully(input: java.io.InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            if (input.read() < 0) return
            remaining--
        }
    }

    /** Plan uit pass 1: driehoeken met areaSq ≥ [bigThresholdSq] altijd bewaren (tot [bigCap]),
     *  de rest om de [smallStride]. */
    private class AreaPlan(val bigThresholdSq: Float, val bigCap: Int, val smallStride: Int)

    /** Deel van de cap dat gereserveerd is voor de grootste driehoeken (wanden). */
    private const val BIG_BUDGET_FRACTION = 0.7

    private fun triangleAreaSq(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float
    ): Float {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val wx = cx - ax; val wy = cy - ay; val wz = cz - az
        val nx = uy * wz - uz * wy
        val ny = uz * wx - ux * wz
        val nz = ux * wy - uy * wx
        return nx * nx + ny * ny + nz * nz // (2·opp)² — alleen relatief gebruikt
    }

    private fun areaSqBucket(areaSq: Float): Int {
        if (areaSq <= 0f || areaSq.isNaN()) return 0
        val log2 = kotlin.math.ln(areaSq.toDouble()) / kotlin.math.ln(2.0)
        return (log2 + 128.0).toInt().coerceIn(0, 255)
    }

    private fun bucketLowerBoundSq(bucket: Int): Float =
        Math.pow(2.0, (bucket - 128).toDouble()).toFloat()

    private fun analyzeBinaryAreas(
        input: java.io.InputStream,
        total: Int,
        progress: ProgressReporter? = null
    ): AreaPlan {
        // Histogram op log2(opp²): elke emmer = factor 2 in oppervlak² — grof maar ruim voldoende
        // om "wand" van "ribbeldetail" te scheiden zonder iets te bewaren.
        val counts = IntArray(256)
        val block = ByteArray(50 * STREAM_RECORDS)
        var i = 0
        while (i < total) {
            val want = minOf(STREAM_RECORDS, total - i) * 50
            val got = readFully(input, block, want)
            val records = got / 50
            if (records <= 0) break
            val buf = ByteBuffer.wrap(block, 0, records * 50).order(ByteOrder.LITTLE_ENDIAN)
            for (r in 0 until records) {
                buf.position(r * 50 + 12)
                val ax = buf.float; val ay = buf.float; val az = buf.float
                val bx = buf.float; val by = buf.float; val bz = buf.float
                val cx = buf.float; val cy = buf.float; val cz = buf.float
                counts[areaSqBucket(triangleAreaSq(ax, ay, az, bx, by, bz, cx, cy, cz))]++
            }
            i += records
            progress?.report(i.toFloat() / total)
            if (got < want) break
        }
        val bigBudget = (RENDER_CAP * BIG_BUDGET_FRACTION).toInt()
        var acc = 0
        var bucket = counts.size
        while (bucket > 0 && acc < bigBudget) {
            bucket--
            acc += counts[bucket]
        }
        val bigCap = (RENDER_CAP * 0.85).toInt()
        val smallCount = (total - acc).coerceAtLeast(0)
        val smallBudget = (RENDER_CAP - minOf(acc, bigCap)).coerceAtLeast(RENDER_CAP / 10)
        val stride = if (smallCount <= smallBudget) 1 else (smallCount + smallBudget - 1) / smallBudget
        return AreaPlan(
            bigThresholdSq = bucketLowerBoundSq(bucket.coerceIn(0, counts.size - 1)),
            bigCap = bigCap,
            smallStride = stride
        )
    }

    private fun parseBinaryPrioritized(
        input: java.io.InputStream,
        total: Int,
        plan: AreaPlan,
        progress: ProgressReporter? = null
    ): StlMesh {
        val builder = MeshBuilder()
        val block = ByteArray(50 * STREAM_RECORDS)
        val hardCap = RENDER_CAP + RENDER_CAP / 20
        var bigKept = 0
        var smallIndex = 0
        var kept = 0
        var i = 0
        while (i < total) {
            val want = minOf(STREAM_RECORDS, total - i) * 50
            val got = readFully(input, block, want)
            val records = got / 50
            if (records <= 0) break
            val buf = ByteBuffer.wrap(block, 0, records * 50).order(ByteOrder.LITTLE_ENDIAN)
            for (r in 0 until records) {
                buf.position(r * 50 + 12)
                val ax = buf.float; val ay = buf.float; val az = buf.float
                val bx = buf.float; val by = buf.float; val bz = buf.float
                val cx = buf.float; val cy = buf.float; val cz = buf.float
                builder.bounds(ax, ay, az, bx, by, bz, cx, cy, cz)
                val areaSq = triangleAreaSq(ax, ay, az, bx, by, bz, cx, cy, cz)
                val keep = if (areaSq >= plan.bigThresholdSq && bigKept < plan.bigCap) {
                    bigKept++
                    true
                } else {
                    val take = smallIndex % plan.smallStride == 0
                    smallIndex++
                    take
                }
                if (keep && kept < hardCap) {
                    builder.add(ax, ay, az, bx, by, bz, cx, cy, cz)
                    kept++
                }
            }
            i += records
            progress?.report(i.toFloat() / total)
            if (got < want) break
        }
        return builder.build(total)
    }

    private fun readFully(input: java.io.InputStream, target: ByteArray, count: Int): Int {
        var off = 0
        while (off < count) {
            val n = input.read(target, off, count - off)
            if (n < 0) break
            off += n
        }
        return off
    }

    private fun parseBinaryStream(
        input: java.io.InputStream,
        total: Int,
        progress: ProgressReporter? = null
    ): StlMesh {
        if (total <= 0) return StlMesh.EMPTY
        val decimation = maxOf(1, total / RENDER_CAP)
        val builder = MeshBuilder()
        val block = ByteArray(50 * STREAM_RECORDS)
        var i = 0
        while (i < total) {
            val want = minOf(STREAM_RECORDS, total - i) * 50
            val got = readFully(input, block, want)
            val records = got / 50
            if (records <= 0) break
            val buf = ByteBuffer.wrap(block, 0, records * 50).order(ByteOrder.LITTLE_ENDIAN)
            for (r in 0 until records) {
                buf.position(r * 50 + 12) // sla de opgeslagen normaal over — zelf berekenen
                val ax = buf.float; val ay = buf.float; val az = buf.float
                val bx = buf.float; val by = buf.float; val bz = buf.float
                val cx = buf.float; val cy = buf.float; val cz = buf.float
                builder.bounds(ax, ay, az, bx, by, bz, cx, cy, cz)
                if ((i + r) % decimation == 0) builder.add(ax, ay, az, bx, by, bz, cx, cy, cz)
            }
            i += records
            progress?.report(i.toFloat() / total)
            if (got < want) break
        }
        return builder.build(total)
    }

    /** ASCII via regel-stream. [head] bevat de al gelezen eerste 84 bytes. Twee passes zijn niet
     *  mogelijk op een stream, dus de decimatie wordt geschat op de bestandsgrootte
     *  (~200 bytes per driehoek in ASCII-STL). */
    private fun parseAsciiStream(
        head: ByteArray,
        rest: java.io.InputStream,
        sizeBytes: Long,
        progress: ProgressReporter? = null
    ): StlMesh {
        val estimatedTotal = (sizeBytes / 200L).coerceAtLeast(1L).toInt()
        val decimation = maxOf(1, estimatedTotal / RENDER_CAP)
        val builder = MeshBuilder()
        val tri = FloatArray(9)
        var vi = 0
        var facet = 0
        val reader = java.io.SequenceInputStream(java.io.ByteArrayInputStream(head), rest)
            .bufferedReader(Charsets.US_ASCII)
        reader.useLines { linesSeq ->
            for (raw in linesSeq) {
                val line = raw.trim()
                if (line.length < 6) continue
                val lower = line.lowercase()
                if (lower.startsWith("vertex")) {
                    val parts = line.split(WHITESPACE)
                    val x = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                    val y = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
                    val z = parts.getOrNull(3)?.toFloatOrNull() ?: 0f
                    if (vi <= 6) {
                        tri[vi] = x; tri[vi + 1] = y; tri[vi + 2] = z
                        vi += 3
                    }
                } else if (lower.startsWith("endfacet")) {
                    if (vi >= 9) {
                        builder.bounds(tri[0], tri[1], tri[2], tri[3], tri[4], tri[5], tri[6], tri[7], tri[8])
                        if (facet % decimation == 0) {
                            builder.add(tri[0], tri[1], tri[2], tri[3], tri[4], tri[5], tri[6], tri[7], tri[8])
                        }
                        facet++
                        if (facet % 4096 == 0) progress?.report(facet.toFloat() / estimatedTotal)
                    }
                    vi = 0
                }
            }
        }
        return builder.build(facet)
    }

    private fun looksBinary(head: ByteArray, sizeBytes: Long): Boolean {
        if (head.size < 84) return false
        val count = readUInt32LE(head, 80)
        if (84L + count * 50L == sizeBytes) return true
        val text = String(head, 0, minOf(head.size, 512), Charsets.US_ASCII).trimStart().lowercase()
        return !text.startsWith("solid")
    }

    private fun readUInt32LE(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)

    private fun parseBinary(bytes: ByteArray): StlMesh {
        val total = readUInt32LE(bytes, 80).toInt().coerceAtLeast(0)
        if (total <= 0) return StlMesh.EMPTY
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(84)
        val decimation = maxOf(1, total / RENDER_CAP)
        val builder = MeshBuilder()
        var i = 0
        while (i < total && buf.remaining() >= 50) {
            buf.float; buf.float; buf.float // vlaknormaal in bestand — negeren, zelf berekenen
            val ax = buf.float; val ay = buf.float; val az = buf.float
            val bx = buf.float; val by = buf.float; val bz = buf.float
            val cx = buf.float; val cy = buf.float; val cz = buf.float
            buf.short // attribute byte count
            builder.bounds(ax, ay, az, bx, by, bz, cx, cy, cz)
            if (i % decimation == 0) builder.add(ax, ay, az, bx, by, bz, cx, cy, cz)
            i++
        }
        return builder.build(total)
    }

    private fun parseAscii(bytes: ByteArray): StlMesh {
        val text = String(bytes, Charsets.US_ASCII)
        val total = countOccurrences(text, "vertex") / 3
        if (total <= 0) return StlMesh.EMPTY
        val decimation = maxOf(1, total / RENDER_CAP)
        val builder = MeshBuilder()
        val tri = FloatArray(9)
        var vi = 0
        var facet = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.length < 6) continue
            val lower = line.lowercase()
            if (lower.startsWith("vertex")) {
                val parts = line.split(WHITESPACE)
                val x = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                val y = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
                val z = parts.getOrNull(3)?.toFloatOrNull() ?: 0f
                if (vi <= 6) {
                    tri[vi] = x; tri[vi + 1] = y; tri[vi + 2] = z
                    vi += 3
                }
            } else if (lower.startsWith("endfacet")) {
                if (vi >= 9) {
                    builder.bounds(tri[0], tri[1], tri[2], tri[3], tri[4], tri[5], tri[6], tri[7], tri[8])
                    if (facet % decimation == 0) {
                        builder.add(tri[0], tri[1], tri[2], tri[3], tri[4], tri[5], tri[6], tri[7], tri[8])
                    }
                    facet++
                }
                vi = 0
            }
        }
        return builder.build(total)
    }

    private fun countOccurrences(text: String, sub: String): Int {
        var count = 0
        var index = text.indexOf(sub)
        while (index >= 0) {
            count++
            index = text.indexOf(sub, index + sub.length)
        }
        return count
    }
}

private class MeshBuilder {
    private var verts = FloatArray(9 * 256)
    private var norms = FloatArray(3 * 256)
    private var vc = 0
    private var nc = 0
    private var minX = Float.MAX_VALUE
    private var minY = Float.MAX_VALUE
    private var minZ = Float.MAX_VALUE
    private var maxX = -Float.MAX_VALUE
    private var maxY = -Float.MAX_VALUE
    private var maxZ = -Float.MAX_VALUE

    fun bounds(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float) {
        minX = minOf(minX, ax, bx, cx); maxX = maxOf(maxX, ax, bx, cx)
        minY = minOf(minY, ay, by, cy); maxY = maxOf(maxY, ay, by, cy)
        minZ = minOf(minZ, az, bz, cz); maxZ = maxOf(maxZ, az, bz, cz)
    }

    fun add(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float) {
        if (vc + 9 > verts.size) verts = verts.copyOf(verts.size * 2)
        if (nc + 3 > norms.size) norms = norms.copyOf(norms.size * 2)
        verts[vc++] = ax; verts[vc++] = ay; verts[vc++] = az
        verts[vc++] = bx; verts[vc++] = by; verts[vc++] = bz
        verts[vc++] = cx; verts[vc++] = cy; verts[vc++] = cz
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val wx = cx - ax; val wy = cy - ay; val wz = cz - az
        var nx = uy * wz - uz * wy
        var ny = uz * wx - ux * wz
        var nz = ux * wy - uy * wx
        val len = sqrt(nx * nx + ny * ny + nz * nz)
        if (len > 1e-6f) { nx /= len; ny /= len; nz /= len }
        norms[nc++] = nx; norms[nc++] = ny; norms[nc++] = nz
    }

    fun build(total: Int): StlMesh {
        if (vc == 0) return StlMesh.EMPTY
        return StlMesh(verts.copyOf(vc), norms.copyOf(nc), total, minX, minY, minZ, maxX, maxY, maxZ)
    }
}
