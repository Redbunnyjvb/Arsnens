package com.example.arsens.data

data class DimensionComparison(val axis: String, val stlMm: Int, val scanMm: Int,
    val tankModelId: String, val capturedAt: String) {
    val deltaMm get() = scanMm - stlMm
}
enum class ContourStatus { EMPTY, PARTIAL, SOLVABLE, VERIFIED, LOCKED }
val Project.contourStatus: ContourStatus get() = when {
    hasWallCalibration && geometryRevisions.any { r -> r.markers == markers && r.dimensionsMm == dimensionsMm && sessions.any { it.geometryRevisionId == r.id } } -> ContourStatus.LOCKED
    hasWallCalibration -> ContourStatus.VERIFIED
    referenceGraph.nodes.map { it.wall }.toSet().containsAll(CalibrationWall.entries) && dimensionsMm.z > 0 -> ContourStatus.SOLVABLE
    referenceGraph.nodes.isNotEmpty() || referenceGraph.assignments.isNotEmpty() -> ContourStatus.PARTIAL
    else -> ContourStatus.EMPTY
}
/** Snapshots are append-only. Session start reuses a matching accepted geometry. */
fun Project.ensureGeometryRevision(now: String): Project {
    val last=geometryRevisions.lastOrNull()
    if (last != null && last.dimensionsMm == dimensionsMm && last.dimensions == dimensionValues &&
        last.markers == markers && last.coordinateFrame == coordinateFrame && last.calibration == wallCalibration &&
        last.stlModels == stlModels && last.referenceGraph == referenceGraph && last.comparisons == dimensionComparisons) return this
    return copy(geometryRevisions=geometryRevisions+snapshotGeometry(now))
}
