package com.example.arsens.ar.calibration

import com.example.arsens.ar.Transform3D
import com.example.arsens.data.*
import kotlin.math.sqrt

/** The delegate reduces camera samples; only seed-tag-relative geometry is durable. */
class PersistentReferenceScan {
    private val live = WallCalibrationSession()
    var graph = ReferenceGraph(); private set
    private var frameId: Long? = null
    private var seedFromReference: Transform3D? = null
    private var sequence = -1L
    private val pairs = mutableMapOf<Pair<Int, Int>, MutableList<Pair<Long, Transform3D>>>()
    private val reprojections = mutableMapOf<Pair<Int, Int>, MutableList<Double>>()
    private var optimizedGraph: ReferenceGraph? = null
    private var optimized = ReferenceGraphSolution(emptyMap(), emptySet())
    var conflictingTagIds: Set<Int> = emptySet(); private set
    private var leadingTagId: Int? = null
    val rejectedEdges get() = solution().rejectedEdges
    val referenceFromSeed get() = seedFromReference?.inverseRigid()
    val verifiedTagIds get() = graph.nodes.filter { it.directVerified }.map { it.tagId }.toSet()
    private fun solution(): ReferenceGraphSolution {
        if (optimizedGraph?.nodes != graph.nodes || optimizedGraph?.edges != graph.edges) {
            optimized = ReferenceGraphOptimizer.solve(graph); optimizedGraph = graph
        }
        return optimized
    }
    val invalidated get() = false
    val reason get() = if (conflictingTagIds.isNotEmpty()) "Referentietags spreken elkaar tegen. Controleer de rode tags in Opties."
        else if (seedFromReference == null && graph.nodes.isNotEmpty()) "Richt op een gekoppelde referentietag om de contour te hervatten." else null
    fun clear() { live.clear(); frameId = null; seedFromReference = null; sequence = -1; pairs.clear(); reprojections.clear(); graph = ReferenceGraph(); conflictingTagIds = emptySet() }
    fun restore(saved: ReferenceGraph) { clear(); graph = saved }
    fun removeTag(id: Int) {
        live.removeTag(id); pairs.keys.removeAll { it.first == id || it.second == id }
        graph = graph.copy(nodes = graph.nodes.filterNot { it.tagId == id }, edges = graph.edges.filterNot { it.fromTagId == id || it.toTagId == id })
    }
    fun count(id: Int) = maxOf(live.count(id), graph.nodes.firstOrNull { it.tagId == id }?.sampleCount ?: 0)
    fun hasTopOverlap(usedIds: List<Int>): Boolean = graph.edges.any { edge ->
        val a = graph.nodes.firstOrNull { it.tagId == edge.fromTagId }
        val b = graph.nodes.firstOrNull { it.tagId == edge.toTagId }
        edge.directSameFrame && edge.sampleCount >= 8 && edge.captureSpanMs >= 700 &&
            edge.fromTagId in usedIds && edge.toTagId in usedIds && a != null && b != null &&
            a.directVerified && b.directVerified && graph.edges.indexOf(edge) !in solution().rejectedEdges &&
            (a.wall == CalibrationWall.Top) != (b.wall == CalibrationWall.Top)
    }
    fun observe(frame: WallScanFrame, assignments: List<WallTagAssignment>, nowMillis: Long, record: Boolean = true) {
        graph = graph.copy(assignments = assignments)
        if (!frame.tracking || (frameId != null && frameId != frame.trackingFrameId)) {
            live.clear(); seedFromReference = null; sequence = -1; pairs.clear(); reprojections.clear()
        }
        frameId = frame.trackingFrameId
        if (!frame.tracking) return
        val valid = frame.observations.filter { o -> assignments.any { it.tagId == o.tagId && it.sizeMm == o.sizeMm } &&
            nowMillis - o.timestampMillis in 0..250 && WallCaptureTuning.acceptsImage(o.shortestEdgePx, o.reprojectionErrorPx) &&
            o.distanceMm in 150.0..6000.0 }
        if (valid.any { observation -> graph.nodes.any { it.tagId == observation.tagId && it.directVerified } } || seedFromReference == null) {
            // ARCore-only nodes are provisional. They must not veto a direct link to the fixed network.
            val visibleNodes = valid.filter { observation -> graph.nodes.any { it.tagId == observation.tagId } }
            val direct = visibleNodes.filter { it.tagId in verifiedTagIds }
            val candidates = direct.ifEmpty { visibleNodes }.sortedByDescending { it.selectionScore }
            val best = candidates.firstOrNull()
            val leader = candidates.firstOrNull { it.tagId == leadingTagId && best != null && it.selectionScore >= best.selectionScore * 0.7f } ?: best
            leadingTagId = leader?.tagId
            val reliable = candidates.filter { leader != null && it.selectionScore >= leader.selectionScore * 0.4f }
            val seedUp = graph.nodes.firstOrNull { it.tagId == graph.seedTagId }?.referenceUp?.let { WallVector.from(it.toDoubleArray()) }
            val known = reliable.map { observation ->
                val node = graph.nodes.first { it.tagId == observation.tagId }
                val candidate = (solution().poses[node.tagId] ?: Transform3D(node.seedFromTag.toDoubleArray())) * observation.referenceFromTag.inverseRigid()
                if (seedUp == null) candidate else gravityAlignedReference(candidate, observation.referenceUp, seedUp, observation.center)
            }
            if (known.isNotEmpty()) {
                // Disagreeing fixed references require another view; do not silently drag the project.
                val pivot = reliable.first().referenceFromCameraCv.translation()
                if (known.any { (WallVector.from(it.transformPoint(pivot)) - WallVector.from(known.first().transformPoint(pivot))).length() > 25 || it.rotationAngleDegreesTo(known.first()) > 5 }) {
                    conflictingTagIds = reliable.map { it.tagId }.toSet()
                    seedFromReference = null; live.clear(); pairs.clear(); reprojections.clear()
                    return
                }
                conflictingTagIds = emptySet()
                seedFromReference = known.drop(1).foldIndexed(known.first()) { i, mean, pose ->
                    mean.blendRigidAtPoint(pose, 1.0 / (i + 2), pivot) }
            } else if (graph.nodes.isEmpty() && valid.isNotEmpty()) {
                val first = valid.first()
                graph = graph.copy(seedTagId = first.tagId)
                seedFromReference = first.referenceFromTag.inverseRigid()
            } else return
        }
        // A visible seed cancels common ARCore drift even before the first stable node is reduced.
        if (graph.nodes.isEmpty()) valid.firstOrNull { it.tagId == graph.seedTagId }?.let { seedFromReference = it.referenceFromTag.inverseRigid() }
        val transform = seedFromReference ?: return
        if (!record) return // Preview may relocalize, but cannot change its accepted evidence.
        live.observe(frame.copy(observations = valid.map { it.copy(
            referenceFromTag = transform * it.referenceFromTag,
            referenceFromCameraCv = transform * it.referenceFromCameraCv,
            referenceUp = transform.wallDirection(it.referenceUp)) }), assignments, nowMillis)
        if (live.invalidated) { live.clear(); return }
        val estimates = live.estimates(assignments)
        val nodes = graph.nodes.associateBy { it.tagId }.toMutableMap()
        estimates.forEach { estimate ->
            if (estimate.assignment.tagId !in nodes) nodes[estimate.assignment.tagId] = ReferenceTagNode(
                estimate.assignment.tagId, estimate.assignment.wall, estimate.assignment.sizeMm,
                estimate.referenceFromTag.values.canonicalValues(), estimate.referenceUp.array().canonicalValues(),
                estimate.sampleCount, estimate.repeatabilityMm, estimate.assignment.tagId == graph.seedTagId)
        }
        // Durable weak links describe the ARCore bridge explicitly. Later direct edges coexist
        // as stronger evidence; the original bridge is kept for provenance.
        val seed = graph.seedTagId
        val bridges = nodes.values.filter { it.tagId != seed && graph.nodes.none { old -> old.tagId == it.tagId } }.mapNotNull { node ->
            val root = nodes[seed] ?: return@mapNotNull null
            val evidence = estimates.first { it.assignment.tagId == node.tagId }
            val relative = Transform3D(root.seedFromTag.toDoubleArray()).inverseRigid() * Transform3D(node.seedFromTag.toDoubleArray())
            ReferencePoseEdge(root.tagId,node.tagId,relative.values.canonicalValues(),node.sampleCount,evidence.captureSpanMillis,evidence.medianReprojectionErrorPx,node.scatterMm,evidence.rotationScatterDegrees,
                java.time.Instant.now().toString(),directSameFrame=false)
        }
        graph = graph.copy(edges = graph.edges + bridges)
        graph = graph.copy(nodes = nodes.values.toList())
        val seq = valid.firstOrNull()?.sequence ?: return
        if (seq <= sequence) return
        sequence = seq
        for (i in valid.indices) for (j in 0 until i) {
            val a = valid[j]; val b = valid[i]
            if (a.sequence != b.sequence || a.timestampMillis != b.timestampMillis ||
                !a.referenceFromCameraCv.values.contentEquals(b.referenceFromCameraCv.values)) continue
            val from = if (a.tagId < b.tagId) a else b
            val to = if (a.tagId < b.tagId) b else a
            val key = from.tagId to to.tagId
            if (graph.edges.any { it.fromTagId == key.first && it.toTagId == key.second && it.directSameFrame }) continue
            val cameraFromRef = from.referenceFromCameraCv.inverseRigid()
            val relative = (cameraFromRef * from.referenceFromTag).inverseRigid() * (cameraFromRef * to.referenceFromTag)
            val buffer = pairs.getOrPut(key) { mutableListOf() }
            if (buffer.lastOrNull()?.let { from.timestampMillis - it.first < 60 } == true) continue
            buffer += from.timestampMillis to relative
            val errors = reprojections.getOrPut(key) { mutableListOf() }
            errors += maxOf(from.reprojectionErrorPx, to.reprojectionErrorPx).toDouble()
            if (buffer.size > 40) { buffer.removeAt(0); errors.removeAt(0) }
            if (buffer.size < 8 || buffer.last().first - buffer.first().first < 700 || from.tagId !in nodes || to.tagId !in nodes) continue
            val mean = buffer.drop(1).foldIndexed(buffer.first().second) { index, pose, sample ->
                pose.blendRigidAtPoint(sample.second, 1.0 / (index + 2), doubleArrayOf(0.0, 0.0, 0.0)) }
            val scatter = sqrt(buffer.sumOf { distance(it.second, mean).let { d -> d * d } } / buffer.size)
            val rotation = buffer.maxOf { it.second.rotationAngleDegreesTo(mean) }
            if (scatter > 10 || rotation > 5) continue
            graph = graph.copy(edges = graph.edges + ReferencePoseEdge(from.tagId, to.tagId, mean.values.canonicalValues(), buffer.size,
                buffer.last().first - buffer.first().first, wallMedian(errors), scatter, rotation, java.time.Instant.now().toString()))
        }
        val rejected = solution().rejectedEdges
        val reachable = mutableSetOf<Int>().apply { graph.seedTagId?.let(::add) }
        repeat(graph.nodes.size) { graph.edges.forEachIndexed { index, edge -> if (index !in rejected && edge.directSameFrame) {
            if (edge.fromTagId in reachable) reachable += edge.toTagId; if (edge.toTagId in reachable) reachable += edge.fromTagId
        } } }
        graph = graph.copy(nodes = graph.nodes.map { it.copy(directVerified = it.tagId in reachable) })
    }
    fun estimates(assignments: List<WallTagAssignment>): List<WallTagEstimate> {
        val referenceFromSeed = seedFromReference?.inverseRigid() ?: return emptyList()
        return graph.nodes.mapNotNull { node ->
            val assignment = assignments.firstOrNull { it.tagId == node.tagId && it.sizeMm == node.sizeMm && it.wall == node.wall } ?: return@mapNotNull null
            WallTagEstimate(assignment, referenceFromSeed * (solution().poses[node.tagId] ?: Transform3D(node.seedFromTag.toDoubleArray())), node.scatterMm,
                node.sampleCount, referenceFromSeed.wallDirection(WallVector.from(
                    (graph.nodes.firstOrNull { it.tagId == graph.seedTagId }?.referenceUp ?: node.referenceUp).toDoubleArray())))
        }
    }
    private fun distance(a: Transform3D, b: Transform3D) = (WallVector.from(a.translation()) - WallVector.from(b.translation())).length()

    // Android JSON serializes -0.0 as 0. Keep in-memory snapshots identical after a reload.
    private fun DoubleArray.canonicalValues() = map { if (it == 0.0) 0.0 else it }
}
