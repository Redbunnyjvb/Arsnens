package com.example.arsens.ar.calibration

import com.example.arsens.ar.Transform3D
import com.example.arsens.data.ReferenceGraph

data class ReferenceGraphSolution(val poses: Map<Int, Transform3D>, val rejectedEdges: Set<Int>)

/** Anchored rigid-pose relaxation. Direct edges dominate weak ARCore node priors.
 * Raw nodes/edges are retained; this derived solution can always be rebuilt. */
object ReferenceGraphOptimizer {
    fun solve(graph: ReferenceGraph): ReferenceGraphSolution {
        val raw = graph.nodes.associate { it.tagId to Transform3D(it.seedFromTag.toDoubleArray()) }
        val seed = graph.seedTagId ?: return ReferenceGraphSolution(raw, emptySet())
        val rejected = graph.edges.indices.filter { index ->
            val edge = graph.edges[index]
            val a = raw[edge.fromTagId]; val b = raw[edge.toTagId]
            if (a == null || b == null) true else {
                val prediction = a * Transform3D(edge.relativeTransform.toDoubleArray())
                prediction.distanceTo(b) > 150.0 || prediction.rotationAngleDegreesTo(b) > 12.0 ||
                    edge.translationScatterMm > 10 || edge.rotationScatterDeg > 5
            }
        }.toSet()
        var poses = raw
        repeat(40) {
            poses = poses.mapValues { (id, previous) ->
                if (id == seed) return@mapValues raw.getValue(seed)
                var mean = raw.getValue(id)
                var weight = 0.02 // ARCore bridge is only a weak prior.
                graph.edges.forEachIndexed { index, edge ->
                    if (index in rejected || (edge.fromTagId != id && edge.toTagId != id)) return@forEachIndexed
                    val relative = Transform3D(edge.relativeTransform.toDoubleArray())
                    val candidate = if (edge.toTagId == id) poses[edge.fromTagId]?.times(relative)
                        else poses[edge.toTagId]?.times(relative.inverseRigid())
                    if (candidate != null) {
                        val residual = candidate.distanceTo(previous)
                        val robust = if (residual <= 25) 1.0 else 25.0 / residual
                        val nextWeight = (if (edge.directSameFrame) 1.0 else 0.1) * robust /
                            (1.0 + edge.translationScatterMm * edge.translationScatterMm)
                        mean = mean.blendRigidAtPoint(candidate, nextWeight / (weight + nextWeight), doubleArrayOf(0.0, 0.0, 0.0))
                        weight += nextWeight
                    }
                }
                previous.blendRigidAtPoint(mean, 0.65, doubleArrayOf(0.0, 0.0, 0.0))
            }
        }
        return ReferenceGraphSolution(poses, rejected)
    }
}
