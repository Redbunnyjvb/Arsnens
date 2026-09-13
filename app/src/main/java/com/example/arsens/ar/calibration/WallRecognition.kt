package com.example.arsens.ar.calibration

import com.example.arsens.data.CalibrationWall
import kotlin.math.*

data class WallSuggestion(val wall: CalibrationWall, val angleErrorDeg: Double)

/** Uses gravity and physical plane normals, never display/camera roll or tag print rotation. */
fun recognizeWall(observation: WallTagObservation, references: List<WallTagEstimate>): WallSuggestion? {
    val up = observation.referenceUp.unit() ?: return null
    val normal = observation.normal.unit() ?: return null
    val vertical = normal.dot(up)
    if (vertical >= cos(Math.toRadians(12.0))) return WallSuggestion(CalibrationWall.Top, Math.toDegrees(acos(vertical.coerceIn(-1.0,1.0))))
    if (abs(vertical) > sin(Math.toRadians(15.0))) return null
    val horizontal = (normal - up * vertical).unit() ?: return null
    val xDirections = references.filter { it.assignment.wall != CalibrationWall.Top }.mapNotNull { ref ->
        val n = ref.normal.let { (it - up * it.dot(up)).unit() } ?: return@mapNotNull null
        when (ref.assignment.wall) {
            CalibrationWall.Front -> (n * -1.0).cross(up)
            CalibrationWall.Back -> n.cross(up)
            CalibrationWall.Left -> n * -1.0
            CalibrationWall.Right -> n
            CalibrationWall.Top -> null
        }
    }
    if (xDirections.isEmpty()) return null
    val x = xDirections.reduce { a,b -> a+b }.unit() ?: return null
    if (xDirections.any { it.dot(x) < cos(Math.toRadians(15.0)) }) return null
    val y = up.cross(x).unit() ?: return null
    val scores = listOf(CalibrationWall.Front to y * -1.0, CalibrationWall.Right to x,
        CalibrationWall.Back to y, CalibrationWall.Left to x * -1.0).map { (wall, direction) -> wall to horizontal.dot(direction) }
        .sortedByDescending { it.second }
    val best = scores.first()
    if (best.second < cos(Math.toRadians(20.0)) || best.second - scores[1].second < 0.45) return null
    return WallSuggestion(best.first, Math.toDegrees(acos(best.second.coerceIn(-1.0, 1.0))))
}

/** Recognition and metrology have different gates. Smaller tags need more independent samples. */
object WallCaptureTuning {
    const val MIN_EDGE_PX = 22f
    const val NORMAL_EDGE_PX = 40f
    const val MAX_REPROJECTION_PX = 1.5f
    fun minimumSamples(edgePx: Float) = if (edgePx < NORMAL_EDGE_PX) 16 else 8
    fun minimumSpanMillis(edgePx: Float) = if (edgePx < NORMAL_EDGE_PX) 1400L else 700L
    fun acceptsImage(edgePx: Float, errorPx: Float): Boolean = edgePx >= MIN_EDGE_PX && errorPx.isFinite() &&
        errorPx <= minOf(MAX_REPROJECTION_PX, edgePx * 0.035f)
}
