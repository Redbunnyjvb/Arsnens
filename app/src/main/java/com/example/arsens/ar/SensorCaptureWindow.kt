package com.example.arsens.ar

import com.example.arsens.data.MmPosition
import com.example.arsens.data.SensorCaptureEvidence
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Armed sensor measurements only. Samples are temporary and never mutate a sensor or session. */
class SensorCaptureWindow {
    private data class Sample(val sequence: Long, val time: Long, val position: MmPosition)
    private val samples = mutableListOf<Sample>()
    private var key: Triple<String, Int, Long>? = null
    val count get() = samples.size
    fun clear() { samples.clear(); key = null }
    fun observe(sensorId: String, tagId: Int, frameId: Long, sequence: Long, time: Long, position: MmPosition?) {
        val next = Triple(sensorId,tagId,frameId)
        if (next != key) { clear(); key = next }
        if (position == null) { samples.clear(); return }
        if (samples.lastOrNull()?.let { sequence <= it.sequence } == true) return
        if (samples.lastOrNull()?.let { time - it.time > 600 } == true) samples.clear()
        if (samples.lastOrNull()?.let { time - it.time < 60 } == true) return
        samples += Sample(sequence,time,position)
        if (samples.size > 24) samples.removeAt(0)
    }
    fun estimate(now: Long): Pair<MmPosition, SensorCaptureEvidence>? {
        if (samples.size < 8 || now - samples.last().time !in 0..300 || samples.last().time - samples.first().time < 700) return null
        fun median(axis: (MmPosition) -> Int) = samples.map { axis(it.position) }.sorted()[samples.size/2]
        val center = MmPosition(median { it.x },median { it.y },median { it.z })
        fun squared(a: MmPosition,b: MmPosition): Double = (a.x-b.x).toDouble().let { it*it } +
            (a.y-b.y).toDouble().let { it*it } + (a.z-b.z).toDouble().let { it*it }
        val inliers = samples.filter { squared(it.position,center) <= 400 }
        if (inliers.size < 8 || inliers.size < samples.size * 0.75 || inliers.last().time-inliers.first().time < 700 ||
            now - inliers.last().time !in 0..300 || samples.last() !in inliers) return null
        val mean=MmPosition(inliers.map { it.position.x }.average().roundToInt(),inliers.map { it.position.y }.average().roundToInt(),inliers.map { it.position.z }.average().roundToInt())
        val scatter=sqrt(inliers.map { squared(it.position,mean) }.average())
        if (scatter > 8) return null
        return mean to SensorCaptureEvidence(inliers.size,inliers.last().time-inliers.first().time,scatter)
    }
}
