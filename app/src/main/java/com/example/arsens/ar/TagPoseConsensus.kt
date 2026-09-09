package com.example.arsens.ar

/** Residuals are measured against whole tags in one captured image, in pixels. */
data class TagPoseHypothesis<T>(val pose: T, val errorByTag: Map<Int, Float>)

data class TagConsensus<T>(val hypothesis: TagPoseHypothesis<T>, val inlierIds: List<Int>)

/**
 * A readable tag is not evidence that its stored position is correct. Require a strict
 * majority of agreeing tags before using multiple references. Equally large, different
 * groups need a clearly better fit; tiny error differences must not choose the winner.
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
    if (candidates.isEmpty()) return null
    fun error(candidate: TagConsensus<T>) =
        candidate.inlierIds.sumOf { candidate.hypothesis.errorByTag.getValue(it).toDouble() }
            .div(candidate.inlierIds.size)
    val allGroups = candidates.groupBy { it.inlierIds }.values.map { group -> group.minBy { error(it) } }
    fun clearlyBetter(a: TagConsensus<T>, b: TagConsensus<T>) =
        error(b) - error(a) >= 0.5 && error(a) <= error(b) * 0.5
    fun uniqueBest(groups: List<TagConsensus<T>>): TagConsensus<T>? {
        val ordered = groups.sortedBy { error(it) }
        return ordered.first().takeIf { ordered.size == 1 || clearlyBetter(it, ordered[1]) }
    }
    val largest = allGroups.maxOf { it.inlierIds.size }
    val primary = uniqueBest(allGroups.filter { it.inlierIds.size == largest }) ?: return null
    // A low-error compromise across all tags must not defeat a demonstrably cleaner
    // majority. Still require a clear residual margin, so ordinary subpixel noise does
    // not continuously exclude the third tag from an otherwise consistent board.
    val cleaner = allGroups.filter { it.inlierIds.size < largest && clearlyBetter(it, primary) }
    if (cleaner.isEmpty()) return primary
    val cleanerSize = cleaner.maxOf { it.inlierIds.size }
    return uniqueBest(allGroups.filter { it.inlierIds.size == cleanerSize })
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
