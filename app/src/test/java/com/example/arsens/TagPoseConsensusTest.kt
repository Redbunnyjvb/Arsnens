package com.example.arsens

import com.example.arsens.ar.*
import org.junit.Assert.*
import org.junit.Test

class TagPoseConsensusTest {
    private fun hypothesis(name: String, vararg errors: Float) =
        TagPoseHypothesis(name, errors.mapIndexed { i, error -> i + 1 to error }.toMap())

    @Test fun twoCorrectTagsBeatTheMovedTagEvenWhenItFitsItselfPerfectly() {
        val result = selectTagConsensus(setOf(1, 2, 3), listOf(
            hypothesis("correct", 0.2f, 0.3f, 12f),
            hypothesis("moved", 12f, 11f, 0f)
        ))!!
        assertEquals(listOf(1, 2), result.inlierIds)
        assertEquals("correct", result.hypothesis.pose)
    }

    @Test fun smallResidualDifferencesBetweenConflictingGroupsDoNotChooseAReference() {
        assertNull(selectTagConsensus(setOf(1, 2, 3), listOf(
            hypothesis("ab", 0.2f, 0.2f, 10f), hypothesis("bc", 10f, 0.21f, 0.21f)
        )))
    }

    @Test fun twoContradictingTagsCannotOutvoteEachOther() {
        assertNull(selectTagConsensus(setOf(1, 2), listOf(
            hypothesis("a", 0f, 8f), hypothesis("b", 8f, 0f)
        )))
    }

    @Test fun globalLowErrorCompromiseDoesNotDefeatAClearlyCleanerMajority() {
        val selected = selectTagConsensus(setOf(1, 2, 3), listOf(
            hypothesis("majority", 0.1f, 0.1f, 8f), hypothesis("compromise", 1.2f, 1.3f, 1.4f)
        ))!!
        assertEquals("majority", selected.hypothesis.pose)
        assertEquals(listOf(1, 2), selected.inlierIds)
    }

    @Test fun oneAccurateReferenceCanBootstrapAndThreeConsistentReferencesCanCombine() {
        assertNotNull(selectTagConsensus(setOf(1), listOf(hypothesis("single", 0.6f))))
        assertEquals(listOf(1, 2, 3), selectTagConsensus(setOf(1, 2, 3), listOf(
            hypothesis("pair", 0.5f, 0.6f, 1.2f)
        ))!!.inlierIds)
    }

    @Test fun nanMissingAndInfiniteResidualsNeverBecomeInliers() {
        assertNull(selectTagConsensus(setOf(1, 2, 3), listOf(hypothesis("bad", Float.NaN, Float.POSITIVE_INFINITY))))
    }

    @Test fun excludedTagCannotTakeOverAloneAndNeedsRepeatedIndependentRecovery() {
        val quarantine = TagReferenceQuarantine()
        assertEquals(setOf(3), quarantine.update(setOf(1, 2, 3), setOf(1, 2)))
        assertEquals(setOf(3), quarantine.update(setOf(3), setOf(3)))
        repeat(2) { assertEquals(setOf(3), quarantine.update(setOf(1, 2, 3), setOf(1, 2, 3))) }
        assertTrue(quarantine.update(setOf(1, 2, 3), setOf(1, 2, 3)).isEmpty())
    }
}
