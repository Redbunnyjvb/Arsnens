package com.example.arsens.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.arsens.data.MmPosition
import com.example.arsens.data.StlMesh
import com.example.arsens.data.rotationMatrix3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Eén te tonen mesh met zijn schaal/offset (mm), rotatie (graden) en eigen kleur, zoals ingesteld in de STL-set. */
data class StlRenderModel(
    val mesh: StlMesh,
    val scalePercent: Int,
    val offsetMm: MmPosition,
    val rotXDeg: Int = 0,
    val rotYDeg: Int = 0,
    val rotZDeg: Int = 0,
    val visible: Boolean,
    val colorArgb: Int
)

/** Een referentiepunt (sensor of tag) dat naast het mesh getekend wordt om offsets uit te lijnen. */
data class StlScenePoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val argb: Int,
    val square: Boolean,
    val label: String
)

/** Vaste aanzichten + vrij 3D. In de vlak-modi staat de rotatie vast (alleen schuiven/zoomen). */
enum class StlViewMode(val label: String, val yaw: Float?, val pitch: Float?) {
    Free("3D", null, null),
    Top("Boven", 0f, 90f),
    Front("Voor", 0f, 0f),
    Back("Achter", 180f, 0f),
    Left("Links", 90f, 0f),
    Right("Rechts", -90f, 0f)
}

/** Preview-only horizontale spiegeling van de vaste vlak-views (display-only, raakt geen data/pose).
 *  De 3D-camera projecteert box-X naar schermrechts; in het Front-aanzicht hoort box-X echter naar
 *  operator-LINKS (front-links = x=max, zelfde conventie als de 2D-kaart en het echte vooraanzicht —
 *  gekoppeld aan de bewuste AR tag-spiegeling). We spiegelen daarom ALLEEN de scherm-X van Front;
 *  model, box, tags en sensoren delen dezelfde projectie en bewegen samen mee. Back/Left/Right/Top
 *  volgen de conventie al, en de vrije 3D-view blijft ongemoeid. */
private fun horizontalFlipFor(viewMode: StlViewMode): Float =
    if (viewMode == StlViewMode.Front) -1f else 1f

/**
 * Software-3D/2D-preview van STL-meshes mét de trafo-box, tags en sensoren. Het mesh wordt massief
 * gerasterd met een z-buffer (volledig gevuld, correcte bedekking); de box en meetpunten komen er
 * als vector bovenop. Gebaren (gangbaar in 3D-apps): in de vrije 3D-view draait één vinger het model
 * (orbit), twee vingers verschuiven + knijp-zoomen; in een vast aanzicht verschuift één vinger.
 * [resetSignal] verhogen reset de vrije rotatie/zoom/pan.
 */
@Suppress("FunctionName")
@Composable
fun StlPreview(
    models: List<StlRenderModel>,
    modifier: Modifier = Modifier,
    boxDimsMm: MmPosition? = null,
    /** Optionele berekende wandbox (projectframe-mm, [minX,minY,minZ,maxX,maxY,maxZ]) — toont waar
     *  "Lijn uit op tank" de afmetingen vandaan haalt. Wordt gestippeld over het model getekend. */
    wallBoxMm: FloatArray? = null,
    points: List<StlScenePoint> = emptyList(),
    viewMode: StlViewMode = StlViewMode.Free,
    renderFillPercent: Int = 100,
    resetSignal: Int = 0,
    wireframe: Boolean = false,
    /** Alleen voor het draadmodel: true = door de wanden kijken (alle randen), false = verdekte
     *  randen weglaten (hidden-line removal via de z-buffer). */
    seeThrough: Boolean = true,
    /** Meetpunten: A→B wordt als lijn met mm-afstand over de scène getekend. */
    measureA: StlScenePoint? = null,
    measureB: StlScenePoint? = null,
    /** Aangetikte wand-index (0=Links..5=Top) → licht dat boxvlak op + loodlijn vanaf measureA. */
    highlightWall: Int? = null,
    /** Niet-null = meetmodus: een tik kiest het dichtstbijzijnde scènepunt (sensor of tag). */
    onPickPoint: ((StlScenePoint) -> Unit)? = null
) {
    var freeYaw by remember { mutableFloatStateOf(28f) }
    var freePitch by remember { mutableFloatStateOf(22f) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(resetSignal) {
        freeYaw = 28f; freePitch = 22f; zoom = 1f; pan = Offset.Zero
    }

    val locked = viewMode != StlViewMode.Free
    val yaw = viewMode.yaw ?: freeYaw
    val pitch = viewMode.pitch ?: freePitch
    val hFlip = horizontalFlipFor(viewMode)

    val fill = renderFillPercent.coerceIn(5, 100)
    // Alleen de zichtbare, niet-lege meshes. Scale/offset zitten BEWUST niet in de geometrie:
    // die worden pas bij het renderen toegepast, zodat schalen/verschuiven geen dure her-opbouw
    // (en grote allocatie) van de vertex-arrays triggert. Dat voorkomt de OutOfMemory-crash.
    val visibleModels = remember(models) { models.filter { it.visible && !it.mesh.isEmpty } }
    val geometryKey = remember(visibleModels) { visibleModels.map { GeoKey(it.mesh, it.colorArgb) } }
    val combined = remember(geometryKey, fill) { combineGeometry(visibleModels, fill) }
    val bounds = remember(visibleModels, boxDimsMm, points) { sceneBounds(visibleModels, boxDimsMm, points) }
    // Per-model transform (rotatie+schaal+offset). Goedkoop en alleen herberekend bij een wijziging,
    // niet per frame; de zware geometrie blijft ongemoeid.
    val xforms = remember(visibleModels) { buildModelXforms(visibleModels) }
    val cache = remember { StlRenderCache() }
    val hasContent = combined.triangleCount > 0 || points.isNotEmpty() || boxDimsMm != null

    Box(modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.count { it.pressed }
                            if (pressed >= 2) {
                                // Twee vingers: knijp-zoomen + verschuiven (universeel in 3D-viewers).
                                zoom = (zoom * event.calculateZoom()).coerceIn(0.2f, 10f)
                                pan += event.calculatePan()
                            } else if (pressed == 1) {
                                val drag = event.calculatePan()
                                if (locked) {
                                    // Vast aanzicht kan niet draaien → één vinger verschuift.
                                    pan += drag
                                } else {
                                    // Vrij 3D: één vinger draait het model (orbit), zoals de meeste
                                    // 3D-apps. Horizontaal sleep = yaw, verticaal = pitch.
                                    freeYaw += drag.x * ORBIT_DEG_PER_PX
                                    freePitch = (freePitch + drag.y * ORBIT_DEG_PER_PX).coerceIn(-89f, 89f)
                                }
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(onPickPoint != null, points, boxDimsMm, viewMode, bounds) {
                    if (onPickPoint == null) return@pointerInput
                    detectTapGestures { tap ->
                        // freeYaw/zoom/pan zijn state-backed: hier lezen geeft de actuele waarden.
                        pickScenePoint(
                            tap = tap,
                            points = points,
                            box = boxDimsMm,
                            bounds = bounds,
                            yawDeg = viewMode.yaw ?: freeYaw,
                            pitchDeg = viewMode.pitch ?: freePitch,
                            zoom = zoom,
                            pan = pan,
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                            maxDistancePx = 44.dp.toPx(),
                            hFlip = horizontalFlipFor(viewMode)
                        )?.let(onPickPoint)
                    }
                }
        ) {
            if (hasContent) {
                if (combined.triangleCount > 0) {
                    if (wireframe && seeThrough) {
                        // Draadmodel mét doorkijk: alle randen op volle canvasresolutie (geen z-buffer).
                        drawWireMesh(combined, xforms, bounds, yaw, pitch, zoom, pan, hFlip)
                    } else {
                        val factor = (RENDER_RES / maxOf(size.width, size.height, 1f)).coerceAtMost(1f)
                        val rw = (size.width * factor).toInt().coerceAtLeast(1)
                        val rh = (size.height * factor).toInt().coerceAtLeast(1)
                        val panF = Offset(pan.x * factor, pan.y * factor)
                        cache.ensure(rw, rh)
                        if (wireframe) {
                            // Draadmodel zónder doorkijk: hidden-line removal via de z-buffer.
                            rasterizeHiddenLine(cache, combined, xforms, bounds, yaw, pitch, zoom, panF, hFlip)
                        } else {
                            rasterizeMesh(cache, combined, xforms, bounds, yaw, pitch, zoom, panF, hFlip)
                        }
                        cache.bitmap?.let { bmp ->
                            bmp.setPixels(cache.pixels, 0, rw, 0, 0, rw, rh)
                            drawImage(
                                image = bmp.asImageBitmap(),
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(rw, rh),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.toInt(), size.height.toInt())
                            )
                        }
                    }
                }
                drawSceneOverlay(bounds, yaw, pitch, zoom, pan, boxDimsMm, points, measureA, measureB, hFlip, wallBoxMm, highlightWall)
            }
        }

        if (!hasContent) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Text(
                    "Geen zichtbaar 3D-model. Laad een model of zet er één zichtbaar.",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

    }
}

private const val RENDER_RES = 960f

/** Graden rotatie per pixel sleep voor de vrije 3D-orbit met één vinger. */
private const val ORBIT_DEG_PER_PX = 0.3f

private class StlRenderCache {
    var bitmap: Bitmap? = null
    var pixels: IntArray = IntArray(0)
    var zbuf: FloatArray = FloatArray(0)
    private var w = 0
    private var h = 0

    fun ensure(width: Int, height: Int) {
        if (width == w && height == h && bitmap != null) return
        w = width
        h = height
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        pixels = IntArray(width * height)
        zbuf = FloatArray(width * height)
    }
}

/** Sleutel om te bepalen of de zware geometrie opnieuw gebouwd moet worden. Bewust ZONDER
 *  scale/offset: die veranderen vaak (knoppen) maar mogen de vertex-arrays niet heropbouwen. */
private data class GeoKey(val mesh: StlMesh, val color: Int)

private class CombinedMesh(
    val vertices: FloatArray,   // rauwe mesh-coördinaten; scale/offset komt er pas bij het renderen bij
    val normals: FloatArray,
    val triColor: IntArray,
    val triModel: IntArray,     // index in de zichtbare-modellenlijst → scale/offset per model
    val triangleCount: Int
)

private class SceneBounds(val cx: Float, val cy: Float, val cz: Float, val extent: Float)

/** Voorberekende per-model transform: wereldpositie = scale * (R * v) + t, met R de rotatiematrix.
 *  t is zo gekozen dat het mesh-midden op (mesh-midden*scale + offset) blijft, dus rotatie/schaal
 *  draaien/schalen het model "op zijn plek" en centreren blijft onafhankelijk van de rotatie. */
private class ModelXforms(val count: Int) {
    val r = FloatArray(count * 9)
    val s = FloatArray(count)
    val tx = FloatArray(count)
    val ty = FloatArray(count)
    val tz = FloatArray(count)
}

private fun buildModelXforms(visible: List<StlRenderModel>): ModelXforms {
    val xf = ModelXforms(visible.size)
    for (i in visible.indices) {
        val m = visible[i]
        val r = rotationMatrix3(m.rotXDeg, m.rotYDeg, m.rotZDeg)
        System.arraycopy(r, 0, xf.r, i * 9, 9)
        val s = m.scalePercent / 100f
        xf.s[i] = s
        val cx = m.mesh.centerX; val cy = m.mesh.centerY; val cz = m.mesh.centerZ
        val rcx = r[0] * cx + r[1] * cy + r[2] * cz
        val rcy = r[3] * cx + r[4] * cy + r[5] * cz
        val rcz = r[6] * cx + r[7] * cy + r[8] * cz
        xf.tx[i] = s * (cx - rcx) + m.offsetMm.x.toFloat()
        xf.ty[i] = s * (cy - rcy) + m.offsetMm.y.toFloat()
        xf.tz[i] = s * (cz - rcz) + m.offsetMm.z.toFloat()
    }
    return xf
}

private fun combineGeometry(visible: List<StlRenderModel>, fillPercent: Int): CombinedMesh {
    val total = visible.sumOf { it.mesh.triangleCount }
    if (total == 0) return CombinedMesh(FloatArray(0), FloatArray(0), IntArray(0), IntArray(0), 0)
    val verts = FloatArray(total * 9)
    val norms = FloatArray(total * 3)
    val triColor = IntArray(total)
    val triModel = IntArray(total)
    var vi = 0
    var ni = 0
    var ti = 0
    for ((modelIndex, model) in visible.withIndex()) {
        val mv = model.mesh.vertices
        val mn = model.mesh.normals
        val count = model.mesh.triangleCount
        val renderLimit = renderTriangleLimit(count, fillPercent)
        for (t in 0 until count) {
            if (!shouldRenderTriangle(t, count, renderLimit)) continue
            val src = t * 9
            verts[vi++] = mv[src]; verts[vi++] = mv[src + 1]; verts[vi++] = mv[src + 2]
            verts[vi++] = mv[src + 3]; verts[vi++] = mv[src + 4]; verts[vi++] = mv[src + 5]
            verts[vi++] = mv[src + 6]; verts[vi++] = mv[src + 7]; verts[vi++] = mv[src + 8]
            val nb = t * 3
            norms[ni++] = mn[nb]; norms[ni++] = mn[nb + 1]; norms[ni++] = mn[nb + 2]
            triColor[ti] = model.colorArgb
            triModel[ti] = modelIndex
            ti++
        }
    }
    if (ti == 0) return CombinedMesh(FloatArray(0), FloatArray(0), IntArray(0), IntArray(0), 0)
    // Bij 100% vulling is ti == total: dan geen extra copy nodig (scheelt een volledige duplicaat-allocatie).
    if (ti == total) return CombinedMesh(verts, norms, triColor, triModel, ti)
    return CombinedMesh(verts.copyOf(vi), norms.copyOf(ni), triColor.copyOf(ti), triModel.copyOf(ti), ti)
}

private fun renderTriangleLimit(total: Int, fillPercent: Int): Int {
    if (fillPercent >= 100) return total
    val percent = fillPercent.coerceIn(5, 100)
    return ((total.toLong() * percent.toLong()) / 100L).toInt().coerceIn(1, total)
}

private fun shouldRenderTriangle(index: Int, total: Int, limit: Int): Boolean {
    if (limit >= total) return true
    if (index == 0) return true
    val currentBucket = (index.toLong() * limit.toLong()) / total.toLong()
    val previousBucket = ((index.toLong() - 1L) * limit.toLong()) / total.toLong()
    return currentBucket != previousBucket
}

private fun sceneBounds(visible: List<StlRenderModel>, box: MmPosition?, points: List<StlScenePoint>): SceneBounds {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
    fun acc(x: Float, y: Float, z: Float) {
        minX = minOf(minX, x); maxX = maxOf(maxX, x)
        minY = minOf(minY, y); maxY = maxOf(maxY, y)
        minZ = minOf(minZ, z); maxZ = maxOf(maxZ, z)
    }
    // Mesh-grenzen mét rotatie+scale+offset, zónder alle vertices te doorlopen: alleen de 8 hoekpunten per model.
    for (model in visible) {
        val s = model.scalePercent / 100f
        val ox = model.offsetMm.x.toFloat()
        val oy = model.offsetMm.y.toFloat()
        val oz = model.offsetMm.z.toFloat()
        val r = rotationMatrix3(model.rotXDeg, model.rotYDeg, model.rotZDeg)
        val cx = model.mesh.centerX; val cy = model.mesh.centerY; val cz = model.mesh.centerZ
        val xs = floatArrayOf(model.mesh.minX, model.mesh.maxX)
        val ys = floatArrayOf(model.mesh.minY, model.mesh.maxY)
        val zs = floatArrayOf(model.mesh.minZ, model.mesh.maxZ)
        for (xi in 0..1) {
            for (yi in 0..1) {
                for (zi in 0..1) {
                    val dx = xs[xi] - cx; val dy = ys[yi] - cy; val dz = zs[zi] - cz
                    val rx = r[0] * dx + r[1] * dy + r[2] * dz
                    val ry = r[3] * dx + r[4] * dy + r[5] * dz
                    val rz = r[6] * dx + r[7] * dy + r[8] * dz
                    acc(s * rx + cx * s + ox, s * ry + cy * s + oy, s * rz + cz * s + oz)
                }
            }
        }
    }
    if (box != null) {
        acc(0f, 0f, 0f); acc(box.x.toFloat(), box.y.toFloat(), box.z.toFloat())
    }
    points.forEach { acc(it.x, it.y, it.z) }
    if (minX > maxX) return SceneBounds(0f, 0f, 0f, 1f)
    val extent = maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(0.001f)
    return SceneBounds((minX + maxX) / 2f, (minY + maxY) / 2f, (minZ + maxZ) / 2f, extent)
}

/** Budget voor het draadmodel op de 3D-pagina: statisch beeld, dus ruimer dan de AR-overlay. */
private const val WIRE_TRIANGLE_BUDGET = 24_000

/** Draadmodel-weergave: driehoeksranden als lijnen, zelfde projectie als [rasterizeMesh] maar
 *  op volle canvasresolutie. Kleur per model (run-batched: de gecombineerde mesh staat per
 *  model gegroepeerd, dus één drawPoints-call per kleur-run). */
private fun DrawScope.drawWireMesh(
    mesh: CombinedMesh,
    xf: ModelXforms,
    bounds: SceneBounds,
    yawDeg: Float,
    pitchDeg: Float,
    zoom: Float,
    pan: Offset,
    hFlip: Float = 1f
) {
    val yaw = yawDeg * (PI.toFloat() / 180f)
    val pitch = pitchDeg * (PI.toFloat() / 180f)
    val cosY = cos(yaw); val sinY = sin(yaw)
    val cosP = cos(pitch); val sinP = sin(pitch)
    val scale = minOf(size.width, size.height) * 0.42f / bounds.extent * zoom
    val scx = size.width / 2f + pan.x
    val scy = size.height / 2f + pan.y
    val cx = bounds.cx; val cy = bounds.cy; val cz = bounds.cz
    val v = mesh.vertices
    val count = mesh.triangleCount
    val stride = maxOf(1, count / WIRE_TRIANGLE_BUDGET)
    val sx = FloatArray(3)
    val sy = FloatArray(3)
    var lines = ArrayList<Offset>(4096)
    var runColor = 0
    fun flush() {
        if (lines.isNotEmpty()) {
            drawPoints(
                points = lines,
                pointMode = PointMode.Lines,
                color = Color(runColor),
                strokeWidth = 2.6f
            )
            lines = ArrayList(4096)
        }
    }
    var t = 0
    while (t < count) {
        val color = mesh.triColor[t]
        if (lines.isNotEmpty() && color != runColor) flush()
        runColor = color
        val mIdx = mesh.triModel[t]
        if (mIdx < xf.count) {
            val rb = mIdx * 9
            val ms = xf.s[mIdx]; val tox = xf.tx[mIdx]; val toy = xf.ty[mIdx]; val toz = xf.tz[mIdx]
            for (k in 0 until 3) {
                val base = t * 9 + k * 3
                val vx = v[base]; val vy = v[base + 1]; val vz = v[base + 2]
                val wx = ms * (xf.r[rb] * vx + xf.r[rb + 1] * vy + xf.r[rb + 2] * vz) + tox
                val wy = ms * (xf.r[rb + 3] * vx + xf.r[rb + 4] * vy + xf.r[rb + 5] * vz) + toy
                val wz = ms * (xf.r[rb + 6] * vx + xf.r[rb + 7] * vy + xf.r[rb + 8] * vz) + toz
                val dx = wx - cx
                val dy = wy - cy
                val dz = wz - cz
                val rx = dx * cosY - dy * sinY
                val ry = dx * sinY + dy * cosY
                val fz = ry * sinP + dz * cosP
                sx[k] = scx + hFlip * rx * scale
                sy[k] = scy - fz * scale
            }
            val p0 = Offset(sx[0], sy[0]); val p1 = Offset(sx[1], sy[1]); val p2 = Offset(sx[2], sy[2])
            lines.add(p0); lines.add(p1)
            lines.add(p1); lines.add(p2)
            lines.add(p2); lines.add(p0)
        }
        t += stride
    }
    flush()
}

/** Massieve z-buffer-rasterisatie van alle driehoeken naar [cache].pixels (achtergrond licht). */
private fun rasterizeMesh(
    cache: StlRenderCache,
    mesh: CombinedMesh,
    xf: ModelXforms,
    bounds: SceneBounds,
    yawDeg: Float,
    pitchDeg: Float,
    zoom: Float,
    pan: Offset,
    hFlip: Float = 1f
) {
    val bmp = cache.bitmap ?: return
    val w = bmp.width
    val h = bmp.height
    val pixels = cache.pixels
    val zbuf = cache.zbuf
    java.util.Arrays.fill(pixels, 0xFFEFF3F7.toInt())
    java.util.Arrays.fill(zbuf, Float.POSITIVE_INFINITY)

    val yaw = yawDeg * (PI.toFloat() / 180f)
    val pitch = pitchDeg * (PI.toFloat() / 180f)
    val cosY = cos(yaw); val sinY = sin(yaw)
    val cosP = cos(pitch); val sinP = sin(pitch)
    val scale = minOf(w, h) * 0.42f / bounds.extent * zoom
    val scx = w / 2f + pan.x
    val scy = h / 2f + pan.y
    val cx = bounds.cx; val cy = bounds.cy; val cz = bounds.cz

    val v = mesh.vertices
    val n = mesh.normals
    val color = mesh.triColor
    val count = mesh.triangleCount

    val lx = 0.35f; val ly = -0.45f; val lz = 0.82f
    val sxv = FloatArray(3); val syv = FloatArray(3); val dv = FloatArray(3)

    for (t in 0 until count) {
        val mIdx = mesh.triModel[t]
        if (mIdx >= xf.count) continue
        val rb = mIdx * 9
        val r0 = xf.r[rb]; val r1 = xf.r[rb + 1]; val r2 = xf.r[rb + 2]
        val r3 = xf.r[rb + 3]; val r4 = xf.r[rb + 4]; val r5 = xf.r[rb + 5]
        val r6 = xf.r[rb + 6]; val r7 = xf.r[rb + 7]; val r8 = xf.r[rb + 8]
        val ms = xf.s[mIdx]; val tox = xf.tx[mIdx]; val toy = xf.ty[mIdx]; val toz = xf.tz[mIdx]
        for (k in 0 until 3) {
            val base = t * 9 + k * 3
            val vx = v[base]; val vy = v[base + 1]; val vz = v[base + 2]
            val wx = ms * (r0 * vx + r1 * vy + r2 * vz) + tox
            val wy = ms * (r3 * vx + r4 * vy + r5 * vz) + toy
            val wz = ms * (r6 * vx + r7 * vy + r8 * vz) + toz
            val dx = wx - cx
            val dy = wy - cy
            val dz = wz - cz
            val rx = dx * cosY - dy * sinY
            val ry = dx * sinY + dy * cosY
            val fy = ry * cosP - dz * sinP
            val fz = ry * sinP + dz * cosP
            sxv[k] = scx + hFlip * rx * scale
            syv[k] = scy - fz * scale
            dv[k] = fy
        }
        val x0 = sxv[0]; val y0 = syv[0]
        val x1 = sxv[1]; val y1 = syv[1]
        val x2 = sxv[2]; val y2 = syv[2]
        val area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0)
        if (area == 0f) continue
        val invArea = 1f / area

        var minPx = minOf(x0, x1, x2).toInt(); if (minPx < 0) minPx = 0
        var maxPx = (maxOf(x0, x1, x2) + 1f).toInt(); if (maxPx > w) maxPx = w
        var minPy = minOf(y0, y1, y2).toInt(); if (minPy < 0) minPy = 0
        var maxPy = (maxOf(y0, y1, y2) + 1f).toInt(); if (maxPy > h) maxPy = h
        if (minPx >= maxPx || minPy >= maxPy) continue

        val nb = t * 3
        val onx = n[nb]; val ony = n[nb + 1]; val onz = n[nb + 2]
        val nx = r0 * onx + r1 * ony + r2 * onz
        val ny = r3 * onx + r4 * ony + r5 * onz
        val nz = r6 * onx + r7 * ony + r8 * onz
        val rnx = nx * cosY - ny * sinY
        val rny = nx * sinY + ny * cosY
        val fny = rny * cosP - nz * sinP
        val fnz = rny * sinP + nz * cosP
        val shade = 0.30f + 0.70f * abs(rnx * lx + fny * ly + fnz * lz)
        val argb = color[t]
        val rr = (((argb ushr 16) and 0xFF) * shade).toInt().coerceIn(0, 255)
        val gg = (((argb ushr 8) and 0xFF) * shade).toInt().coerceIn(0, 255)
        val bb = ((argb and 0xFF) * shade).toInt().coerceIn(0, 255)
        val pix = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb

        var py = minPy
        while (py < maxPy) {
            val pyc = py + 0.5f
            var px = minPx
            while (px < maxPx) {
                val pxc = px + 0.5f
                val l0 = ((x1 - pxc) * (y2 - pyc) - (x2 - pxc) * (y1 - pyc)) * invArea
                val l1 = ((x2 - pxc) * (y0 - pyc) - (x0 - pxc) * (y2 - pyc)) * invArea
                val l2 = 1f - l0 - l1
                if (l0 >= 0f && l1 >= 0f && l2 >= 0f) {
                    val depth = l0 * dv[0] + l1 * dv[1] + l2 * dv[2]
                    val idx = py * w + px
                    if (depth < zbuf[idx]) {
                        zbuf[idx] = depth
                        pixels[idx] = pix
                    }
                }
                px++
            }
            py++
        }
    }
}

/** Projecteert de 3 hoekpunten van driehoek [t] naar scherm-xy ([sxv]/[syv]) + camera-diepte ([dv]).
 *  Zelfde camerawiskunde als [rasterizeMesh]; gedeeld door de depth- en de randpass van hidden-line. */
private fun projectTriangle(
    t: Int, mIdx: Int, v: FloatArray, xf: ModelXforms,
    cosY: Float, sinY: Float, cosP: Float, sinP: Float, scale: Float,
    scx: Float, scy: Float, cx: Float, cy: Float, cz: Float, hFlip: Float,
    sxv: FloatArray, syv: FloatArray, dv: FloatArray
) {
    val rb = mIdx * 9
    val r0 = xf.r[rb]; val r1 = xf.r[rb + 1]; val r2 = xf.r[rb + 2]
    val r3 = xf.r[rb + 3]; val r4 = xf.r[rb + 4]; val r5 = xf.r[rb + 5]
    val r6 = xf.r[rb + 6]; val r7 = xf.r[rb + 7]; val r8 = xf.r[rb + 8]
    val ms = xf.s[mIdx]; val tox = xf.tx[mIdx]; val toy = xf.ty[mIdx]; val toz = xf.tz[mIdx]
    for (k in 0 until 3) {
        val base = t * 9 + k * 3
        val vx = v[base]; val vy = v[base + 1]; val vz = v[base + 2]
        val wx = ms * (r0 * vx + r1 * vy + r2 * vz) + tox
        val wy = ms * (r3 * vx + r4 * vy + r5 * vz) + toy
        val wz = ms * (r6 * vx + r7 * vy + r8 * vz) + toz
        val dx = wx - cx; val dy = wy - cy; val dz = wz - cz
        val rx = dx * cosY - dy * sinY
        val ry = dx * sinY + dy * cosY
        sxv[k] = scx + hFlip * rx * scale
        syv[k] = scy - (ry * sinP + dz * cosP) * scale
        dv[k] = ry * cosP - dz * sinP
    }
}

/** Tekent een lijn in [pixels], maar alleen waar hij vóór de z-buffer ligt (met − [bias] tolerantie
 *  voor randen die op hun eigen vlak liggen). DDA-stappen op pixelresolutie. */
private fun drawDepthTestedLine(
    pixels: IntArray, zbuf: FloatArray, w: Int, h: Int,
    x0: Float, y0: Float, d0: Float,
    x1: Float, y1: Float, d1: Float,
    bias: Float, color: Int
) {
    val dx = x1 - x0; val dy = y1 - y0
    val steps = maxOf(abs(dx), abs(dy)).toInt()
    if (steps <= 0) {
        val px = x0.toInt(); val py = y0.toInt()
        if (px in 0 until w && py in 0 until h && d0 - bias <= zbuf[py * w + px]) pixels[py * w + px] = color
        return
    }
    val inv = 1f / steps
    var i = 0
    while (i <= steps) {
        val tt = i * inv
        val px = (x0 + dx * tt).toInt()
        val py = (y0 + dy * tt).toInt()
        if (px in 0 until w && py in 0 until h) {
            val idx = py * w + px
            if ((d0 + (d1 - d0) * tt) - bias <= zbuf[idx]) pixels[idx] = color
        }
        i++
    }
}

/** Draadmodel ZÓNDER doorkijk: vult eerst de z-buffer met het massieve oppervlak (occluder) en
 *  tekent dan alleen de randsegmenten die vóór dat oppervlak liggen — echte hidden-line removal. */
private fun rasterizeHiddenLine(
    cache: StlRenderCache,
    mesh: CombinedMesh,
    xf: ModelXforms,
    bounds: SceneBounds,
    yawDeg: Float,
    pitchDeg: Float,
    zoom: Float,
    pan: Offset,
    hFlip: Float = 1f
) {
    val bmp = cache.bitmap ?: return
    val w = bmp.width
    val h = bmp.height
    val pixels = cache.pixels
    val zbuf = cache.zbuf
    java.util.Arrays.fill(pixels, 0xFFEFF3F7.toInt())
    java.util.Arrays.fill(zbuf, Float.POSITIVE_INFINITY)

    val yaw = yawDeg * (PI.toFloat() / 180f)
    val pitch = pitchDeg * (PI.toFloat() / 180f)
    val cosY = cos(yaw); val sinY = sin(yaw)
    val cosP = cos(pitch); val sinP = sin(pitch)
    val scale = minOf(w, h) * 0.42f / bounds.extent * zoom
    val scx = w / 2f + pan.x
    val scy = h / 2f + pan.y
    val cx = bounds.cx; val cy = bounds.cy; val cz = bounds.cz
    val v = mesh.vertices
    val count = mesh.triangleCount
    val sxv = FloatArray(3); val syv = FloatArray(3); val dv = FloatArray(3)

    // Pass 1 — depth-only: het massieve oppervlak als occluder in de z-buffer (geen kleur).
    for (t in 0 until count) {
        val mIdx = mesh.triModel[t]
        if (mIdx >= xf.count) continue
        projectTriangle(t, mIdx, v, xf, cosY, sinY, cosP, sinP, scale, scx, scy, cx, cy, cz, hFlip, sxv, syv, dv)
        val x0 = sxv[0]; val y0 = syv[0]; val x1 = sxv[1]; val y1 = syv[1]; val x2 = sxv[2]; val y2 = syv[2]
        val area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0)
        if (area == 0f) continue
        val invArea = 1f / area
        var minPx = minOf(x0, x1, x2).toInt(); if (minPx < 0) minPx = 0
        var maxPx = (maxOf(x0, x1, x2) + 1f).toInt(); if (maxPx > w) maxPx = w
        var minPy = minOf(y0, y1, y2).toInt(); if (minPy < 0) minPy = 0
        var maxPy = (maxOf(y0, y1, y2) + 1f).toInt(); if (maxPy > h) maxPy = h
        if (minPx >= maxPx || minPy >= maxPy) continue
        var py = minPy
        while (py < maxPy) {
            val pyc = py + 0.5f
            var px = minPx
            while (px < maxPx) {
                val pxc = px + 0.5f
                val l0 = ((x1 - pxc) * (y2 - pyc) - (x2 - pxc) * (y1 - pyc)) * invArea
                val l1 = ((x2 - pxc) * (y0 - pyc) - (x0 - pxc) * (y2 - pyc)) * invArea
                val l2 = 1f - l0 - l1
                if (l0 >= 0f && l1 >= 0f && l2 >= 0f) {
                    val depth = l0 * dv[0] + l1 * dv[1] + l2 * dv[2]
                    val idx = py * w + px
                    if (depth < zbuf[idx]) zbuf[idx] = depth
                }
                px++
            }
            py++
        }
    }

    // Pass 2 — zichtbare randen: per (gestreepte) driehoek de 3 randen met dieptetest.
    val stride = maxOf(1, count / WIRE_TRIANGLE_BUDGET)
    val bias = bounds.extent * 0.006f
    val lineColor = 0xFF1B2430.toInt()
    var t = 0
    while (t < count) {
        val mIdx = mesh.triModel[t]
        if (mIdx < xf.count) {
            projectTriangle(t, mIdx, v, xf, cosY, sinY, cosP, sinP, scale, scx, scy, cx, cy, cz, hFlip, sxv, syv, dv)
            drawDepthTestedLine(pixels, zbuf, w, h, sxv[0], syv[0], dv[0], sxv[1], syv[1], dv[1], bias, lineColor)
            drawDepthTestedLine(pixels, zbuf, w, h, sxv[1], syv[1], dv[1], sxv[2], syv[2], dv[2], bias, lineColor)
            drawDepthTestedLine(pixels, zbuf, w, h, sxv[2], syv[2], dv[2], sxv[0], syv[0], dv[0], bias, lineColor)
        }
        t += stride
    }
}

/** Dezelfde projectie als [drawSceneOverlay], maar als losse functie zodat ook tik-detectie
 *  (meetpunt kiezen) exact hetzelfde schermpunt berekent. */
private fun projectScenePointToScreen(
    x: Float,
    y: Float,
    z: Float,
    bounds: SceneBounds,
    yawDeg: Float,
    pitchDeg: Float,
    zoom: Float,
    pan: Offset,
    width: Float,
    height: Float,
    hFlip: Float = 1f
): Offset {
    val yaw = yawDeg * (PI.toFloat() / 180f)
    val pitch = pitchDeg * (PI.toFloat() / 180f)
    val cosY = cos(yaw); val sinY = sin(yaw)
    val cosP = cos(pitch); val sinP = sin(pitch)
    val scale = minOf(width, height) * 0.42f / bounds.extent * zoom
    val dx = x - bounds.cx; val dy = y - bounds.cy; val dz = z - bounds.cz
    val rx = dx * cosY - dy * sinY
    val ry = dx * sinY + dy * cosY
    return Offset(
        x = width / 2f + pan.x + hFlip * rx * scale,
        y = height / 2f + pan.y - (ry * sinP + dz * cosP) * scale
    )
}

/** De 8 hoekpunten van de trafo-box als meetbare punten ("hoek"), net als in de 2D-kaart. */
internal fun boxCornerScenePoints(box: MmPosition): List<StlScenePoint> = buildList {
    for (xi in 0..1) {
        for (yi in 0..1) {
            for (zi in 0..1) {
                add(
                    StlScenePoint(
                        x = (xi * box.x).toFloat(),
                        y = (yi * box.y).toFloat(),
                        z = (zi * box.z).toFloat(),
                        argb = 0xFF5D6B76.toInt(),
                        square = true,
                        label = "hoek"
                    )
                )
            }
        }
    }
}

/** Dichtstbijzijnde meetbare punt (scènepunten + boxhoeken) bij een tik, binnen [maxDistancePx]. */
private fun pickScenePoint(
    tap: Offset,
    points: List<StlScenePoint>,
    box: MmPosition?,
    bounds: SceneBounds,
    yawDeg: Float,
    pitchDeg: Float,
    zoom: Float,
    pan: Offset,
    width: Float,
    height: Float,
    maxDistancePx: Float,
    hFlip: Float = 1f
): StlScenePoint? {
    // Box-hoeken bewust NIET meer aantikbaar: fiddly met vingers, en de wand-afstand-overlay geeft
    // de afstand tot elke wand al. Meten gaat tussen sensoren/tags onderling.
    val candidates = points
    var best: StlScenePoint? = null
    var bestDistSq = maxDistancePx * maxDistancePx
    for (candidate in candidates) {
        val screen = projectScenePointToScreen(
            candidate.x, candidate.y, candidate.z,
            bounds, yawDeg, pitchDeg, zoom, pan, width, height, hFlip
        )
        val dx = screen.x - tap.x
        val dy = screen.y - tap.y
        val distSq = dx * dx + dy * dy
        if (distSq < bestDistSq) {
            bestDistSq = distSq
            best = candidate
        }
    }
    return best
}

internal fun stlMeasureDistanceMm(a: StlScenePoint, b: StlScenePoint): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    val dz = a.z - b.z
    return sqrt(dx * dx + dy * dy + dz * dz)
}

/** Box (draadmodel) en meetpunten als scherpe vector over de gerasterde mesh. */
private fun DrawScope.drawSceneOverlay(
    bounds: SceneBounds,
    yawDeg: Float,
    pitchDeg: Float,
    zoom: Float,
    pan: Offset,
    box: MmPosition?,
    points: List<StlScenePoint>,
    measureA: StlScenePoint? = null,
    measureB: StlScenePoint? = null,
    hFlip: Float = 1f,
    wallBox: FloatArray? = null,
    highlightWall: Int? = null
) {
    val yaw = yawDeg * (PI.toFloat() / 180f)
    val pitch = pitchDeg * (PI.toFloat() / 180f)
    val cosY = cos(yaw); val sinY = sin(yaw)
    val cosP = cos(pitch); val sinP = sin(pitch)
    val scale = minOf(size.width, size.height) * 0.42f / bounds.extent * zoom
    val scx = size.width / 2f + pan.x
    val scy = size.height / 2f + pan.y
    val cx = bounds.cx; val cy = bounds.cy; val cz = bounds.cz
    fun px(x: Float, y: Float): Float {
        val dx = x - cx; val dy = y - cy
        return scx + hFlip * (dx * cosY - dy * sinY) * scale
    }
    fun py(x: Float, y: Float, z: Float): Float {
        val dx = x - cx; val dy = y - cy; val dz = z - cz
        val ry = dx * sinY + dy * cosY
        return scy - (ry * sinP + dz * cosP) * scale
    }

    if (box != null) {
        val c = listOf(
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(box.x.toFloat(), 0f, 0f),
            floatArrayOf(box.x.toFloat(), box.y.toFloat(), 0f),
            floatArrayOf(0f, box.y.toFloat(), 0f),
            floatArrayOf(0f, 0f, box.z.toFloat()),
            floatArrayOf(box.x.toFloat(), 0f, box.z.toFloat()),
            floatArrayOf(box.x.toFloat(), box.y.toFloat(), box.z.toFloat()),
            floatArrayOf(0f, box.y.toFloat(), box.z.toFloat())
        ).map { Offset(px(it[0], it[1]), py(it[0], it[1], it[2])) }
        listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7).forEach { (a, b) ->
            drawLine(Color(0xFF2B3A47), c[a], c[b], strokeWidth = 2.5f)
        }
        // Vlaklabels maken de oriëntatie direct verifieerbaar ("welke kant is voor?") — alleen op
        // vlakken die naar de kijker gericht zijn, anders schemeren achterliggende labels erdoorheen.
        // De kijkrichting is de gradiënt van de dieptefunctie (ry·cosP − dz·sinP).
        val fwdX = sinY * cosP
        val fwdY = cosY * cosP
        val fwdZ = -sinP
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(170, 20, 28, 36)
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bxf = box.x.toFloat(); val byf = box.y.toFloat(); val bzf = box.z.toFloat()
        listOf(
            arrayOf("VOOR", bxf / 2f, 0f, bzf / 2f, 0f, -1f, 0f),
            arrayOf("ACHTER", bxf / 2f, byf, bzf / 2f, 0f, 1f, 0f),
            arrayOf("LINKS", 0f, byf / 2f, bzf / 2f, -1f, 0f, 0f),
            arrayOf("RECHTS", bxf, byf / 2f, bzf / 2f, 1f, 0f, 0f)
        ).forEach { face ->
            val nx = face[4] as Float; val ny = face[5] as Float; val nz = face[6] as Float
            if (nx * fwdX + ny * fwdY + nz * fwdZ < -0.05f) {
                val fx = face[1] as Float; val fy = face[2] as Float; val fz = face[3] as Float
                drawContext.canvas.nativeCanvas.drawText(
                    face[0] as String,
                    px(fx, fy),
                    py(fx, fy, fz),
                    facePaint
                )
            }
        }
    }

    // Aangetikte wand oplichten + loodlijn van het meetpunt ernaartoe (interactieve wand-afstand).
    if (box != null && highlightWall != null && measureA != null) {
        val bx = box.x.toFloat(); val by = box.y.toFloat(); val bz = box.z.toFloat()
        val face: List<FloatArray> = when (highlightWall) {
            0 -> listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, by, 0f), floatArrayOf(0f, by, bz), floatArrayOf(0f, 0f, bz))
            1 -> listOf(floatArrayOf(bx, 0f, 0f), floatArrayOf(bx, by, 0f), floatArrayOf(bx, by, bz), floatArrayOf(bx, 0f, bz))
            2 -> listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(bx, 0f, 0f), floatArrayOf(bx, 0f, bz), floatArrayOf(0f, 0f, bz))
            3 -> listOf(floatArrayOf(0f, by, 0f), floatArrayOf(bx, by, 0f), floatArrayOf(bx, by, bz), floatArrayOf(0f, by, bz))
            4 -> listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(bx, 0f, 0f), floatArrayOf(bx, by, 0f), floatArrayOf(0f, by, 0f))
            else -> listOf(floatArrayOf(0f, 0f, bz), floatArrayOf(bx, 0f, bz), floatArrayOf(bx, by, bz), floatArrayOf(0f, by, bz))
        }
        val poly = face.map { Offset(px(it[0], it[1]), py(it[0], it[1], it[2])) }
        val path = Path().apply {
            moveTo(poly[0].x, poly[0].y)
            for (i in 1 until poly.size) lineTo(poly[i].x, poly[i].y)
            close()
        }
        drawPath(path, Color(0x3342A5F5))
        drawPath(path, Color(0xFF2563EB), style = Stroke(width = 3f))
        val foot = when (highlightWall) {
            0 -> floatArrayOf(0f, measureA.y, measureA.z)
            1 -> floatArrayOf(bx, measureA.y, measureA.z)
            2 -> floatArrayOf(measureA.x, 0f, measureA.z)
            3 -> floatArrayOf(measureA.x, by, measureA.z)
            4 -> floatArrayOf(measureA.x, measureA.y, 0f)
            else -> floatArrayOf(measureA.x, measureA.y, bz)
        }
        val aPos = Offset(px(measureA.x, measureA.y), py(measureA.x, measureA.y, measureA.z))
        val footPos = Offset(px(foot[0], foot[1]), py(foot[0], foot[1], foot[2]))
        drawLine(Color(0xFF2563EB), aPos, footPos, strokeWidth = 4f)
        drawCircle(Color(0xFF2563EB), radius = 7f, center = footPos)
    }

    // Berekende wandbox (gestippeld, oranje): toont waar "Lijn uit op tank" de afmetingen vandaan
    // haalt — de buitenste wandvlakken, zónder ribben/uitstulpsels. Helpt de box-rand verifiëren.
    if (wallBox != null && wallBox.size >= 6) {
        val w = listOf(
            floatArrayOf(wallBox[0], wallBox[1], wallBox[2]),
            floatArrayOf(wallBox[3], wallBox[1], wallBox[2]),
            floatArrayOf(wallBox[3], wallBox[4], wallBox[2]),
            floatArrayOf(wallBox[0], wallBox[4], wallBox[2]),
            floatArrayOf(wallBox[0], wallBox[1], wallBox[5]),
            floatArrayOf(wallBox[3], wallBox[1], wallBox[5]),
            floatArrayOf(wallBox[3], wallBox[4], wallBox[5]),
            floatArrayOf(wallBox[0], wallBox[4], wallBox[5])
        ).map { Offset(px(it[0], it[1]), py(it[0], it[1], it[2])) }
        val dash = PathEffect.dashPathEffect(floatArrayOf(16f, 10f))
        listOf(0 to 1, 1 to 2, 2 to 3, 3 to 0, 4 to 5, 5 to 6, 6 to 7, 7 to 4, 0 to 4, 1 to 5, 2 to 6, 3 to 7).forEach { (a, b) ->
            drawLine(Color(0xFFE0884B), w[a], w[b], strokeWidth = 2.5f, pathEffect = dash)
        }
    }

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(20, 28, 36)
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    points.forEach { p ->
        val x = px(p.x, p.y)
        val y = py(p.x, p.y, p.z)
        val c = Color(p.argb)
        if (p.square) {
            drawRect(c, topLeft = Offset(x - 9f, y - 9f), size = Size(18f, 18f))
            drawRect(Color.White, topLeft = Offset(x - 9f, y - 9f), size = Size(18f, 18f), style = Stroke(width = 2f))
        } else {
            drawCircle(c, radius = 10f, center = Offset(x, y))
            drawCircle(Color.White, radius = 10f, center = Offset(x, y), style = Stroke(width = 2.5f))
        }
        if (p.label.isNotEmpty()) {
            drawContext.canvas.nativeCanvas.drawText(p.label, x + 12f, y - 10f, labelPaint)
        }
    }

    // Meting: gemarkeerde eindpunten + lijn met 3D-afstand in mm (zelfde stijl als de 2D-kaart).
    if (measureA != null) {
        val ink = Color(0xFF111827)
        val aPos = Offset(px(measureA.x, measureA.y), py(measureA.x, measureA.y, measureA.z))
        drawCircle(ink, radius = 13f, center = aPos, style = Stroke(width = 4f))
        if (measureB != null) {
            val bPos = Offset(px(measureB.x, measureB.y), py(measureB.x, measureB.y, measureB.z))
            drawLine(ink, aPos, bPos, strokeWidth = 4f)
            drawCircle(ink, radius = 13f, center = bPos, style = Stroke(width = 4f))
            val mid = Offset((aPos.x + bPos.x) / 2f, (aPos.y + bPos.y) / 2f)
            val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(17, 24, 39)
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            drawContext.canvas.nativeCanvas.drawText(
                "${stlMeasureDistanceMm(measureA, measureB).roundToInt()} mm",
                mid.x + 12f,
                mid.y - 12f,
                measurePaint
            )
        }
    }
}
