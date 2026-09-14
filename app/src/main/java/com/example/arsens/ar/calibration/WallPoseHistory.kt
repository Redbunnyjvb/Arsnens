package com.example.arsens.ar.calibration

import com.example.arsens.ar.Transform3D
import com.example.arsens.ar.TransformerPose

/** Detector-thread-only prior for planar ambiguity. Never reused as new measurement evidence. */
class WallPoseHistory {
    private data class Entry(val pose: Transform3D, val size: Int, val time: Long)
    private var key: Pair<Long,Long>? = null
    private val saved = mutableMapOf<Int,Entry>()
    fun predictions(request: WallScanRequest, frameId: Long, now: Long, referenceFromCamera: Transform3D): Map<Int,TransformerPose> {
        if (key != (request.sessionId to frameId)) { saved.clear(); key = request.sessionId to frameId }
        saved.entries.removeAll { now-it.value.time !in 0..700 }
        return saved.filter { (id,e) -> e.size == (request.tagSizes[id] ?: request.defaultSizeMm) }
            .mapValues { (_,e) -> (referenceFromCamera.inverseRigid()*e.pose).toTransformerPose(0f) }
    }
    fun remember(tags: List<StandaloneWallTag>, now: Long, referenceFromCamera: Transform3D) {
        tags.filter { WallCaptureTuning.acceptsImage(it.shortestEdgePx,it.reprojectionErrorPx) }.forEach {
            saved[it.tagId] = Entry(referenceFromCamera*it.cameraCvFromTag,it.sizeMm,now)
        }
    }
}
