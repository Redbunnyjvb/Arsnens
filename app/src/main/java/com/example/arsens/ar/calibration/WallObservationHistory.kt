package com.example.arsens.ar.calibration

/** Display continuity only. Stored observations retain their original capture timestamp/sequence. */
class WallObservationHistory {
    private var frameId: Long? = null
    private var held = emptyList<WallTagObservation>()
    fun clear() { held = emptyList(); frameId = null }
    fun visible(now: Long, trackingFrameId: Long, tracking: Boolean, incoming: List<WallTagObservation>): List<WallTagObservation> {
        if (!tracking || frameId != trackingFrameId) clear()
        frameId = trackingFrameId
        if (!tracking) return emptyList()
        if (incoming.isNotEmpty()) held = incoming
        held = held.filter { now - it.timestampMillis in 0..700 && it.trackingFrameId == trackingFrameId }
        return held
    }
}
