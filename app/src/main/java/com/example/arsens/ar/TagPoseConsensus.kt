package com.example.arsens.ar

/** Residuals are measured against whole tags in one captured image, in pixels. */
data class TagPoseHypothesis<T>(val pose: T, val errorByTag: Map<Int, Float>)

data class TagConsensus<T>(val hypothesis: TagPoseHypothesis<T>, val inlierIds: List<Int>)

/**
 * A readable tag is not evidence that its stored position is correct. Require a strict
 * majority of agreeing tags before using multiple references. Equally large, different
 * groups are ambiguous; a lower fit error must not silently choose one of them.
 */
fun <T> selectTagConsensus(
    visibleIds: Set<Int>,
    hypotheses: List<TagPoseHypothesis<T>>,
    maxTagErrorPx: Float = 3f
): TagConsensus<T>? {
    if (visibleIds.isEmpty()) return null
    val required = if (visibleIds.size == 1) 1 else maxOf(2, visibleIds.size / 2 + 1)
    val candidates = hypotheses.mapNotNull { hypothesis ->
        val inliers = visibleIds.filter { id ->
            hypothesis.errorByTag[id]?.let { it.isFinite() && it >= 0f && it <= maxTagErrorPx } == true
        }.sorted()
        if (inliers.size < required) null else TagConsensus(hypothesis, inliers)
    }
    val largest = candidates.maxOfOrNull { it.inlierIds.size } ?: return null
    val finalists = candidates.filter { it.inlierIds.size == largest }
    if (finalists.map { it.inlierIds }.distinct().size != 1) return null
    return finalists.minBy { candidate ->
        candidate.inlierIds.sumOf { candidate.hypothesis.errorByTag.getValue(it).toDouble() }
    }
}

/** A rejected reference cannot take over simply because the other tags leave the image. */
class TagReferenceQuarantine {
    private val rejected = mutableSetOf<Int>()
    private val recoveryCounts = mutableMapOf<Int, Int>()

    fun update(visibleIds: Set<Int>, consensusIds: Set<Int>): Set<Int> {
        if (consensusIds.size >= 2) {
            rejected += visibleIds - consensusIds
            for (id in rejected.toList()) {
                if (id in consensusIds) {
                    val count = (recoveryCounts[id] ?: 0) + 1
                    recoveryCounts[id] = count
                    if (count >= 3) {
                        rejected -= id
                        recoveryCounts.remove(id)
                    }
                } else {
                    recoveryCounts.remove(id)
                }
            }
        } else {
            recoveryCounts.clear()
        }
        return rejected.toSet()
    }

    fun reset() {
        rejected.clear()
        recoveryCounts.clear()
    }
}
