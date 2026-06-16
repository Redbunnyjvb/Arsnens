package com.example.arsens

import com.example.arsens.ar.TagPoseSmoother
import com.example.arsens.ar.TransformerPose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagPoseSmootherTest {

    private fun pose(x: Float, y: Float, z: Float, rx: Float = 0f) = TransformerPose(
        translationMm = floatArrayOf(x, y, z),
        rotationVector = floatArrayOf(rx, 0f, 0f),
        reprojectionErrorPx = 1f
    )

    @Test
    fun smallJitterIsDampened() {
        val smoother = TagPoseSmoother()
        smoother.smooth(0, pose(1000f, 0f, 0f), nowMillis = 0)
        // Eén frame later 10 mm "ruis": het gladde resultaat volgt maar een fractie.
        val smoothed = smoother.smooth(0, pose(1010f, 0f, 0f), nowMillis = 33)
        val moved = smoothed.translationMm[0] - 1000f
        assertTrue("verwacht sterke demping, maar verschoof $moved mm", moved < 3f)
        assertTrue(moved > 0f)
    }

    @Test
    fun repeatedSamplesConvergeToNewPosition() {
        val smoother = TagPoseSmoother()
        smoother.smooth(0, pose(1000f, 0f, 0f), nowMillis = 0)
        var result = pose(1000f, 0f, 0f)
        var t = 33L
        repeat(120) {
            result = smoother.smooth(0, pose(1050f, 0f, 0f), nowMillis = t)
            t += 33
        }
        // Geen vaste offset overhouden: de gladde toestand kruipt naar de echte positie toe.
        assertEquals(1050f, result.translationMm[0], 2f)
    }

    @Test
    fun largeJumpSnapsAfterConfirmation() {
        val smoother = TagPoseSmoother()
        smoother.smooth(0, pose(1000f, 0f, 0f), nowMillis = 0)
        // Eerste sprong-frame: vasthouden (kan een uitschieter zijn)…
        val held = smoother.smooth(0, pose(1500f, 0f, 0f), nowMillis = 33)
        assertEquals(1000f, held.translationMm[0], 1e-3f)
        // …tweede frame bevestigt → direct overnemen (echte beweging).
        val snapped = smoother.smooth(0, pose(1500f, 0f, 0f), nowMillis = 66)
        assertEquals(1500f, snapped.translationMm[0], 1e-3f)
    }

    @Test
    fun rotationSignFlipSnapsAfterConfirmation() {
        val smoother = TagPoseSmoother()
        smoother.smooth(0, pose(0f, 0f, 0f, rx = 3.1f), nowMillis = 0)
        // Rodrigues-vector kan van teken wisselen (±π): nooit door nul heen middelen.
        // Eén frame vasthouden (±π is dezelfde rotatie, dus visueel geen verschil), dan volgen.
        smoother.smooth(0, pose(0f, 0f, 0f, rx = -3.1f), nowMillis = 33)
        val snapped = smoother.smooth(0, pose(0f, 0f, 0f, rx = -3.1f), nowMillis = 66)
        assertEquals(-3.1f, snapped.rotationVector[0], 1e-3f)
    }

    @Test
    fun singleFrameOutlierIsSuppressed() {
        // De gespiegelde vlak-oplossing (IPPE-ambiguïteit) duikt één frame op en is daarna weer
        // weg: de smoother mag daar NIET naartoe snappen — overlays bleven anders "rondvliegen".
        val smoother = TagPoseSmoother()
        smoother.smooth(0, pose(1000f, 0f, 0f), nowMillis = 0)
        val duringOutlier = smoother.smooth(0, pose(7800f, 0f, 0f, rx = 1.8f), nowMillis = 33)
        assertEquals(1000f, duringOutlier.translationMm[0], 1e-3f)
        assertEquals(0f, duringOutlier.rotationVector[0], 1e-3f)
        // Volgende frame is weer normaal: gewoon dempen, geen snap naar de uitschieter.
        val after = smoother.smooth(0, pose(1004f, 0f, 0f), nowMillis = 66)
        assertTrue(after.translationMm[0] in 1000f..1005f)
    }

    @Test
    fun staleStateResetsAfterTimeout() {
        val smoother = TagPoseSmoother()
        smoother.smooth(0, pose(1000f, 0f, 0f), nowMillis = 0)
        // Tag 2 seconden niet gezien → verse pose wordt 1-op-1 overgenomen, geen oude demping.
        val fresh = smoother.smooth(0, pose(1030f, 0f, 0f), nowMillis = 2_000)
        assertEquals(1030f, fresh.translationMm[0], 1e-3f)
    }

    @Test
    fun tagsAreSmoothedIndependently() {
        val smoother = TagPoseSmoother()
        smoother.smooth(1, pose(1000f, 0f, 0f), nowMillis = 0)
        smoother.smooth(2, pose(5000f, 0f, 0f), nowMillis = 0)
        val one = smoother.smooth(1, pose(1010f, 0f, 0f), nowMillis = 33)
        val two = smoother.smooth(2, pose(5010f, 0f, 0f), nowMillis = 33)
        assertTrue(one.translationMm[0] in 1000f..1011f)
        assertTrue(two.translationMm[0] in 5000f..5011f)
    }
}
