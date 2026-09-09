package com.example.arsens

import com.example.arsens.ar.*
import org.junit.Assert.*
import org.junit.Test

class AnchorPoseFilterTest {
    private fun at(x: Double) = Transform3D.identity().let {
        Transform3D(it.values.copyOf().apply { this[3] = x })
    }
    private fun calibrated(): AnchorPoseFilter = AnchorPoseFilter().apply {
        for (i in 1..4) update(at(0.0), listOf(1), i * 100L, false)
        assertTrue(update(at(0.0), listOf(1), 500, false).settled)
    }

    @Test fun initialSingleReferenceNeedsIndependentStableObservations() {
        val filter = AnchorPoseFilter()
        filter.update(at(0.0), listOf(1), 100, false)
        assertNull(filter.anchor)
        filter.update(at(100.0), listOf(1), 200, false)
        filter.update(at(0.0), listOf(1), 300, false)
        assertNull(filter.anchor)
        for (i in 4..6) {
            filter.update(at(0.0), listOf(1), i * 100L, false)
            assertNull(filter.anchor)
        }
        assertTrue(filter.update(at(0.0), listOf(1), 700, false).settled)
        assertNotNull(filter.anchor)
    }

    @Test fun imageConfirmationCannotCancelAnExecutingCorrection() {
        val filter = calibrated()
        for (time in 600L..800L step 100L) filter.update(at(24.0), listOf(1), time, false)
        filter.confirmImageConsistency()
        for (time in 820L..1800L step 20L) filter.advance(time)
        assertEquals(24.0, filter.anchor!!.translation()[0], 0.001)
    }

    @Test fun alternatingReferencesRequireGeometricConfirmation() {
        for (consistent in listOf(true, false)) {
            val filter = calibrated()
            for (time in 600L..2500L step 100L) filter.update(at(20.0),
                if (time % 200 == 0L) listOf(0) else listOf(0, 1), time, false,
                consistentWithPendingImage = consistent)
            assertEquals(if (consistent) 20.0 else 0.0, filter.anchor!!.translation()[0], 1.0)
        }
    }

    @Test fun millimeterNoiseDoesNotMoveAnEstablishedModel() {
        val filter = calibrated()
        for (i in 6..100) filter.update(at(if (i % 2 == 0) 2.0 else -2.0), listOf(1), i * 100L, false)
        assertEquals(0.0, filter.anchor!!.translation()[0], 0.0)
    }

    @Test fun movedSingleReferenceDoesNotDragTheModelWithIt() {
        val filter = calibrated()
        repeat(30) { assertEquals("REJECT", filter.update(at(70.0), listOf(2), 600 + it * 100L, false).event) }
        assertEquals(0.0, filter.anchor!!.translation()[0], 0.0)
        assertTrue(filter.requiresRecalibration)
    }

    @Test fun repeatedMultiTagEvidenceCanCorrectEvenLargeDriftWithoutImmediateJump() {
        val filter = calibrated()
        assertEquals("PENDING", filter.update(at(200.0), listOf(1, 2), 600, false).event)
        assertEquals(0.0, filter.anchor!!.translation()[0], 0.0)
        for (i in 7..55) filter.update(at(200.0), listOf(1, 2), i * 100L, false)
        assertEquals(200.0, filter.anchor!!.translation()[0], 8.0)
    }

    @Test fun trackingRelocalizationCanReacquireASingleReference() {
        val filter = calibrated()
        for (i in 6..9) filter.update(at(1000.0), listOf(1), i * 100L, true)
        assertTrue(filter.update(at(1000.0), listOf(1), 1000, true).settled)
        assertEquals(1000.0, filter.anchor!!.translation()[0], 0.0)
    }

    @Test fun isolatedBadPoseAndPendingCorrectionDoNotRevokePublishedAnchor() {
        val filter = calibrated()
        val rejected = filter.update(at(70.0), listOf(1), 600, false)
        assertEquals("REJECT", rejected.event)
        assertTrue(rejected.settled)
        assertFalse(rejected.requiresRecalibration)
        val pending = filter.update(at(5.0), listOf(1), 700, false)
        assertEquals("PENDING", pending.event)
        assertTrue(pending.settled)
        assertTrue(filter.confirmImageConsistency().settled)
        assertFalse(filter.requiresRecalibration)
        assertEquals(0.0, filter.anchor!!.translation()[0], 0.0)
    }

    @Test fun noisySmallTagCanInitializeWithoutOneDegreeResetLoop() {
        val filter = AnchorPoseFilter()
        val point = doubleArrayOf(3933.0, 0.0, 0.0)
        for (i in 1..10) {
            val pose = Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(0f, 0f, 0f),
                floatArrayOf(0f, 0f, Math.toRadians(if (i % 2 == 0) 2.0 else -2.0).toFloat()), 0.3f))
            val rotated = pose.transformPoint(point)
            val candidate = Transform3D(pose.values.copyOf().apply {
                for (axis in 0..2) this[axis * 4 + 3] = point[axis] - rotated[axis]
            })
            filter.update(candidate, listOf(0), i * 100L, false, point)
        }
        assertNotNull(filter.anchor)
        assertArrayEquals(point, filter.anchor!!.transformPoint(point), 1e-6)
    }
}
