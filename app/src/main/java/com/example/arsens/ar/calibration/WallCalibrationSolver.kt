package com.example.arsens.ar.calibration

import com.example.arsens.ar.Transform3D
import com.example.arsens.data.*
import kotlin.math.*

/** Fits ONE rectangular frame. Gravity fixes Z; a robust one-dimensional solve fixes yaw.
 * No free intersection of noisy, independently tilted planes and no sensor coordinate edits. */
object WallCalibrationSolver {
    fun solve(dimensions: MmPosition, source: WallDimensionSource, tags: List<WallTagEstimate>, datum: WallVerticalDatum): WallSolveResult =
        solveInternal(dimensions, source, tags, datum, requireTop = true)

    fun solveFootprint(dimensions: MmPosition, source: WallDimensionSource, tags: List<WallTagEstimate>, datum: WallVerticalDatum): WallSolveResult =
        solveInternal(dimensions, source, tags.filter { it.assignment.wall != CalibrationWall.Top }, datum, requireTop = false)

    private fun solveInternal(dimensions: MmPosition, source: WallDimensionSource, tags: List<WallTagEstimate>,
        datum: WallVerticalDatum, requireTop: Boolean): WallSolveResult {
        fun fail(reason: String) = WallSolveResult(reason = reason)
        if (tags.map { it.assignment.tagId }.distinct().size != tags.size) return fail("Dubbele tag-ID in wandscan.")
        if (source == WallDimensionSource.Entered && listOf(dimensions.x, dimensions.y, dimensions.z).any { it <= 0 })
            return fail("Voer geldige afmetingen in.")
        val up = tags.firstOrNull()?.referenceUp?.unit() ?: return fail("Neem eerst tags op de wanden op.")
        if (tags.any { !it.center.finite() || it.referenceFromTag.values.any { v -> !v.isFinite() } ||
                it.referenceUp.unit()?.dot(up)?.let { dot -> dot < cos(Math.toRadians(2.0)) } != false })
            return fail("Metingen hebben geen consistente verticale richting.")
        val lower = tags.firstOrNull { it.assignment.tagId == datum.lowerTagId }
        if (datum.lowerTagId >= 0 && lower == null) return fail("Scan de gekozen hoogtereferentietag.")
        if (lower?.assignment?.wall == CalibrationWall.Top) return fail("Kies een zijtag als onderreferentie en meet zijn hoogte boven de onderrand.")
        if (datum.heightAboveBottomMm < 0) return fail("De datumhoogte moet positief of nul zijn.")
        val topTags = tags.filter { it.assignment.wall == CalibrationWall.Top }
        if (requireTop && topTags.isEmpty()) return fail("Scan minstens één boventag om de bovenkant te koppelen.")
        if (datum.topSurfaceOffsetMm < 0) return fail("Het hoogteverschil van de boventags moet positief of nul zijn.")
        val topLevel = if (topTags.isNotEmpty()) wallMedian(topTags.map { it.center.dot(up) }) - datum.topSurfaceOffsetMm else null
        val lowerOrigin = lower?.center?.dot(up)?.minus(datum.heightAboveBottomMm)
        val height = when {
            source == WallDimensionSource.Entered -> dimensions.z.toDouble()
            !requireTop -> 0.0
            lowerOrigin != null -> topLevel!! - lowerOrigin
            else -> datum.sideHeightMm?.toDouble() ?: return fail("Vul de gewenste hoogte van de zijwanden onder het deksel in.")
        }
        if ((requireTop || source == WallDimensionSource.Entered) && (height !in 100.0..100000.0 || (lower != null && datum.heightAboveBottomMm > height)))
            return fail("De hoogtereferentie past niet bij de hoogte van het zijvlak.")
        // Before the lid is linked, show the 2D section at the visible side tags' level.
        // Linking the lid fixes Z without requiring a tag or sensor on the underside.
        val zOrigin = lowerOrigin ?: if (requireTop) topLevel!! - height else tags.minOf { it.center.dot(up) }
        val groups = tags.groupBy { it.assignment.wall }
        val requiredWalls = if (source == WallDimensionSource.Scanned) CalibrationWall.entries.filter { requireTop || it.axis != 2 } else groups.keys.toList()
        if (requiredWalls.none { it.axis == 0 } || requiredWalls.none { it.axis == 1 } ||
            requiredWalls.any { groups[it].orEmpty().size < if(it.axis==2) 1 else 2 })
            return fail(if (source == WallDimensionSource.Scanned) "Scan minstens twee verspreide tags op elk van de vier zijwanden."
                else "Scan minstens twee verspreide tags per wand, op twee aangrenzende zijwanden.")
        val base = if (abs(up.x) < 0.8) WallVector(1.0, 0.0, 0.0) else WallVector(0.0, 0.0, 1.0)
        val u = (base - up * base.dot(up)).unit()!!
        val v = up.cross(u).unit()!!
        fun angleOf(x: WallVector) = atan2(x.dot(v), x.dot(u))
        val seeds = mutableListOf<Double>()
        for (tag in tags.filter { it.assignment.wall.axis != 2 }) {
            val n = (tag.normal - up * tag.normal.dot(up)).unit() ?: continue
            val signed = n * (if (tag.assignment.wall.positive) 1.0 else -1.0)
            seeds += angleOf(if (tag.assignment.wall.axis == 0) signed else signed.cross(up))
        }
        // Spread of tag CENTERS also constrains yaw, without trusting a single planar normal.
        groups.filterKeys { it.axis != 2 }.forEach { (wall, points) ->
            for (i in points.indices) for (j in 0 until i) {
                val d = points[i].center - points[j].center
                val horizontal = (d - up * d.dot(up)).unit() ?: continue
                val x = if (wall.axis == 1) horizontal else horizontal.cross(up)
                seeds += angleOf(x); seeds += angleOf(x * -1.0)
            }
        }
        data class Fit(val theta: Double, val x: WallVector, val y: WallVector, val ox: Double, val oy: Double,
            val length: Double, val width: Double, val residuals: List<Double>, val angles: List<Double>, val score: Double)
        fun fit(theta: Double, used: List<WallTagEstimate> = tags): Fit? {
            val x = u * cos(theta) + v * sin(theta)
            val y = up.cross(x)
            fun coordinates(wall: CalibrationWall) = used.filter { it.assignment.wall == wall }.map { it.center.dot(if (wall.axis == 0) x else y) }
            val ox: Double; val oy: Double; val length: Double; val width: Double
            if (source == WallDimensionSource.Scanned) {
                val left = coordinates(CalibrationWall.Left); val right = coordinates(CalibrationWall.Right)
                val front = coordinates(CalibrationWall.Front); val back = coordinates(CalibrationWall.Back)
                if (listOf(left, right, front, back).any { it.isEmpty() }) return null
                ox = wallMedian(left); oy = wallMedian(front)
                length = wallMedian(right) - ox; width = wallMedian(back) - oy
            } else {
                length = dimensions.x.toDouble(); width = dimensions.y.toDouble()
                val xs = used.filter { it.assignment.wall.axis == 0 }.map { it.center.dot(x) - if (it.assignment.wall.positive) length else 0.0 }
                val ys = used.filter { it.assignment.wall.axis == 1 }.map { it.center.dot(y) - if (it.assignment.wall.positive) width else 0.0 }
                if (xs.isEmpty() || ys.isEmpty()) return null
                ox = wallMedian(xs); oy = wallMedian(ys)
            }
            if (length !in 100.0..100000.0 || width !in 100.0..100000.0) return null
            val residuals = tags.map { tag ->
                val wall = tag.assignment.wall
                if (wall.axis == 2) return@map tag.center.dot(up) - zOrigin - height - datum.topSurfaceOffsetMm
                val coordinate = tag.center.dot(if (wall.axis == 0) x else y)
                coordinate - (if (wall.axis == 0) ox else oy) - if (wall.positive) (if (wall.axis == 0) length else width) else 0.0
            }
            val angles = tags.map { tag ->
                val wall = tag.assignment.wall
                val expected = (if (wall.axis == 2) up else if (wall.axis == 0) x else y) * (if (wall.positive) 1.0 else -1.0)
                Math.toDegrees(acos(tag.normal.dot(expected).coerceIn(-1.0, 1.0)))
            }
            // Capped tag-level costs prevent one shifted or wrongly assigned tag dominating.
            val score = tags.indices.sumOf { min(residuals[it].pow(2), 30.0.pow(2)) + min(angles[it].pow(2), 15.0.pow(2)) * 2 }
            return Fit(theta, x, y, ox, oy, length, width, residuals, angles, score)
        }
        var best = seeds.mapNotNull { fit(it) }.minByOrNull { it.score } ?: return fail("Wandrichtingen spreken elkaar tegen.")
        for (stepDeg in listOf(0.5, 0.05, 0.005)) {
            val baseTheta = best.theta
            for (i in -10..10) fit(baseTheta + Math.toRadians(i * stepDeg))?.let { if (it.score < best.score) best = it }
        }
        fun inliers(f: Fit) = tags.filterIndexed { i, _ -> abs(f.residuals[i]) <= 20.0 && f.angles[i] <= 12.0 }
        var used = inliers(best)
        if (used.size < tags.size) {
            val refined = fit(best.theta, used)
            if (refined != null) { best = refined; used = inliers(best) }
        }
        if (lower != null && lower !in used) return fail("Een hoogtereferentie wijkt af. Controleer de tag of kies een andere datumtag.")
        val usedGroups = used.groupBy { it.assignment.wall }
        if (requiredWalls.any { usedGroups[it].orEmpty().size < if(it.axis==2) 1 else 2 }) return fail("Na uitsluiten blijven te weinig tags over. Scan een extra tag op de betreffende wand.")
        for ((wall, points) in usedGroups) {
            if (wall.axis == 2) continue // Top tags may be placed anywhere; side walls already fix yaw.
            val along = if (wall.axis == 0) best.y else best.x
            val spread = if (wall.axis == 2) points.maxOf { a -> points.maxOf { b ->
                val d = a.center - b.center
                (d - up*d.dot(up)).length()
            } } else points.maxOf { it.center.dot(along) } - points.minOf { it.center.dot(along) }
            val extent = if (wall.axis == 2) min(best.width,best.length) else if (wall.axis == 0) best.width else best.length
            if (spread < max(100.0, extent * 0.1)) return fail("Tags op ${wall.label} staan horizontaal te dicht bij elkaar. Spreid ze verder over de wand.")
        }
        val origin = best.x * best.ox + best.y * best.oy + up * zOrigin
        fun matrix(x: WallVector, y: WallVector, z: WallVector, t: WallVector) = Transform3D(doubleArrayOf(
            x.x,y.x,z.x,t.x, x.y,y.y,z.y,t.y, x.z,y.z,z.z,t.z, 0.0,0.0,0.0,1.0))
        val referenceFromProject = matrix(best.x, best.y, up, origin)
        val projectFromReference = referenceFromProject.inverseRigid()
        val solvedDimensions = if (source == WallDimensionSource.Entered) dimensions else MmPosition(best.length.roundToInt(), best.width.roundToInt(), height.roundToInt())
        val markers = used.map { tag ->
            // A flat raised cover can be measured explicitly; retain each tag's ACTUAL height.
            val p = projectFromReference.wallPoint(tag.center)
            val maxZ = solvedDimensions.z + if (tag.assignment.wall.axis == 2) datum.topSurfaceOffsetMm else 0
            if (p.x !in -20.0..(solvedDimensions.x + 20.0) || p.y !in -20.0..(solvedDimensions.y + 20.0) || p.z < -20.0 || (requireTop && p.z > maxZ + 20.0))
                return fail("Tag ${tag.assignment.tagId} valt buiten de tankcontour. Controleer afmetingen en wandkeuze.")
            val wall = tag.assignment.wall
            val position = MmPosition(
                if (wall.axis == 0) (if (wall.positive) solvedDimensions.x else 0) else p.x.roundToInt().coerceIn(0, solvedDimensions.x),
                if (wall.axis == 1) (if (wall.positive) solvedDimensions.y else 0) else p.y.roundToInt().coerceIn(0, solvedDimensions.y),
                if (wall.axis == 2) maxZ else if (!requireTop) p.z.roundToInt().coerceAtLeast(0) else p.z.roundToInt().coerceIn(0, solvedDimensions.z))
            val normal = if (wall.axis == 2) WallVector(0.0,0.0,1.0) else if (wall.axis == 0) WallVector(if (wall.positive) 1.0 else -1.0, 0.0, 0.0)
                else WallVector(0.0, if (wall.positive) 1.0 else -1.0, 0.0)
            val measuredX = (projectFromReference * tag.referenceFromTag).wallDirection(WallVector(1.0,0.0,0.0))
            val tagX = (measuredX - normal * measuredX.dot(normal)).unit() ?: return fail("Tagoriëntatie ongeldig.")
            val tagZ = normal.cross(tagX)
            val rotation = wallMarkerEuler(matrix(tagX, normal * -1.0, tagZ, WallVector(0.0,0.0,0.0)))
            Marker(tag.assignment.tagId, "apriltag", tag.assignment.sizeMm, position, rotation, origin = PlacementOrigin.OnTheFly)
        }
        val residuals = tags.indices.associate { tags[it].assignment.tagId to abs(best.residuals[it]) }
        val usedIds = used.map { it.assignment.tagId }.sorted()
        val errors = usedIds.map { residuals.getValue(it) }
        val quality = WallCalibrationQuality(sqrt(errors.sumOf { it * it } / errors.size), errors.max(),
            tags.indices.filter { tags[it] in used }.maxOf { best.angles[it] }, used.maxOf { it.repeatabilityMm },
            usedIds, tags.map { it.assignment.tagId }.filterNot { it in usedIds }.sorted())
        return WallSolveResult(WallCalibrationSolution(referenceFromProject, solvedDimensions, markers, quality, residuals))
    }
}
