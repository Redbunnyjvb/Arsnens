package com.example.arsens.data

import java.util.UUID

enum class DimensionSource { MANUAL, STL, SCANNED }
data class DimensionValue(val axis: String, val valueMm: Int, val source: DimensionSource,
    val sourceReference: String? = null, val confirmedByOperator: Boolean = false, val revision: Int = 1)
enum class SessionStatus { OPEN, CLOSED }
enum class MeasurementAction { PLACE, MOVE, VERIFY }
enum class MeasurementMethod { DIRECT_MULTI_REF, DIRECT_SINGLE_REF, ARCORE_WITH_RECENT_REF, ARCORE_ONLY, MANUAL_CURSOR, LEGACY }
val MeasurementAction.label: String get() = when(this) { MeasurementAction.PLACE -> "Plaatsen"; MeasurementAction.MOVE -> "Verplaatsen"; MeasurementAction.VERIFY -> "Controleren" }
val MeasurementMethod.label: String get() = when(this) {
    MeasurementMethod.DIRECT_MULTI_REF -> "Meerdere vaste referenties"
    MeasurementMethod.DIRECT_SINGLE_REF -> "Eén vaste referentie"
    MeasurementMethod.ARCORE_WITH_RECENT_REF -> "ARCore · referentie recent gecontroleerd"
    MeasurementMethod.ARCORE_ONLY -> "ARCore · controleer de uitlijning"
    MeasurementMethod.MANUAL_CURSOR -> "Handmatige cursor"
    MeasurementMethod.LEGACY -> "Eerdere meting"
}
data class SensorCaptureEvidence(val sampleCount: Int, val spanMs: Long, val scatterMm: Double,
    val lastReferenceAgeMs: Long? = null)

/** Frozen geometry and plan, independent of subsequent edits and ARCore world origins. */
data class GeometryRevision(val id: String, val number: Int, val createdAt: String,
    val dimensionsMm: MmPosition, val dimensions: List<DimensionValue>, val markers: List<Marker>,
    val coordinateFrame: CoordinateFrameSettings, val calibration: WallCalibrationData?,
    val stlModels: List<StlModel>, val referenceGraph: ReferenceGraph = ReferenceGraph(),
    val comparisons: List<DimensionComparison> = emptyList(), val solverVersion: String = "rectangular-graph-2")
data class SensorMeasurement(val id: String = UUID.randomUUID().toString(), val sensorId: String,
    val action: MeasurementAction, val result: InstallationResult, val method: MeasurementMethod,
    val sensorTagId: Int? = null, val audit: SensorPlacementAudit? = null,
    val orientation: FloatVector? = null, val captureEvidence: SensorCaptureEvidence? = null)
data class ActionEvent(val id: String = UUID.randomUUID().toString(), val type: String,
    val timestamp: String, val measurementId: String, val targetActionId: String? = null)
data class MeasurementSession(val id: String = UUID.randomUUID().toString(), val name: String,
    val operator: String, val purpose: String = "", val startedAt: String, val closedAt: String? = null,
    val status: SessionStatus = SessionStatus.OPEN, val geometryRevisionId: String,
    val sensorDefinitions: List<Sensor>, val baseline: List<InstallationResult> = emptyList(),
    val measurements: List<SensorMeasurement> = emptyList(), val actions: List<ActionEvent> = emptyList(),
    val migrationSource: String? = null)
data class MeasurementDraft(val sessionId: String, val sensorId: String, val action: MeasurementAction,
    val result: InstallationResult, val method: MeasurementMethod, val sensorTagId: Int?,
    val audit: SensorPlacementAudit?, val createdAt: String, val captureEvidence: SensorCaptureEvidence? = null)

val Project.activeSession: MeasurementSession? get() = sessions.firstOrNull { it.id == activeSessionId }
val MeasurementSession.acceptedMeasurements: List<SensorMeasurement> get() {
    val reverted = actions.filter { it.type == "ACTION_REVERTED" }.map { it.measurementId }.toSet()
    return measurements.filterNot { it.id in reverted }
}
fun MeasurementSession.currentResults(): List<InstallationResult> {
    val current = baseline.associateBy { it.sensorId }.toMutableMap()
    acceptedMeasurements.filter { it.action != MeasurementAction.VERIFY }.forEach { current[it.sensorId] = it.result }
    return current.values.toList()
}
data class SessionSummary(val placed: Int, val moved: Int, val verified: Int, val unchanged: Int, val remaining: Int)
fun MeasurementSession.summary(): SessionSummary {
    val latest=acceptedMeasurements.groupBy { it.sensorId }
    val moved=latest.count { (_,events) -> events.any { it.action==MeasurementAction.MOVE } }
    val placed=latest.count { (_,events) -> events.any { it.action==MeasurementAction.PLACE } }
    val verified=latest.count { (_,events) -> events.any { it.action==MeasurementAction.VERIFY } }
    val current=currentResults().map { it.sensorId }.toSet()
    return SessionSummary(placed,moved,verified,baseline.count { it.sensorId !in latest },sensorDefinitions.count { it.id !in current })
}

fun Project.snapshotGeometry(now: String): GeometryRevision = GeometryRevision(
    id = UUID.randomUUID().toString(), number = geometryRevisions.size + 1, createdAt = now,
    dimensionsMm = dimensionsMm, dimensions = dimensionValues, markers = markers.toList(),
    coordinateFrame = coordinateFrame, calibration = wallCalibration, stlModels = stlModels.toList(), referenceGraph = referenceGraph, comparisons = dimensionComparisons)

fun Project.startMeasurementSession(name: String, operator: String, purpose: String, now: String): Project {
    require(sessions.none { it.status == SessionStatus.OPEN }) { "Rond eerst de actieve sessie af." }
    require(listOf(dimensionsMm.x, dimensionsMm.y, dimensionsMm.z).all { it > 0 } && !needsWallCalibration) {
        "Bepaal en accepteer eerst de projectgeometrie."
    }
    require(markers.any { it.active && it.isAprilTagCalibrationMarker() }) { "Leg eerst een vaste referentietag vast." }
    val prepared = ensureGeometryRevision(now)
    val revision = prepared.geometryRevisions.last()
    val session = MeasurementSession(name = name.ifBlank { "Sessie ${sessions.size + 1}" }, operator = operator.trim(),
        purpose = purpose.trim(), startedAt = now, geometryRevisionId = revision.id, sensorDefinitions = sensors.toList(),
        baseline = sessions.lastOrNull()?.currentResults().orEmpty())
    return prepared.copy(sessions = sessions + session,
        activeSessionId = session.id, measurementDraft = null)
}

fun Project.acceptMeasurementDraft(now: String): Project {
    val draft = requireNotNull(measurementDraft) { "Geen meting om op te slaan." }
    val session = requireNotNull(activeSession)
    require(session.status == SessionStatus.OPEN && session.id == draft.sessionId)
    val geometry = requireNotNull(geometryRevisions.firstOrNull { it.id == session.geometryRevisionId })
    require(geometry.dimensionsMm == dimensionsMm && geometry.markers == markers && geometry.coordinateFrame == coordinateFrame && geometry.calibration == wallCalibration) {
        "De geometrie is gewijzigd sinds de sessie begon. Verwerp de conceptmeting en begin een nieuwe sessie."
    }
    require(session.sensorDefinitions.any { it.id == draft.sensorId }) { "De sensor ontbreekt in het sessieplan." }
    val measurement = SensorMeasurement(sensorId = draft.sensorId, action = draft.action,
        result = draft.result, method = draft.method, sensorTagId = draft.sensorTagId, audit = draft.audit, captureEvidence = draft.captureEvidence)
    val event = ActionEvent(type = "SENSOR_${draft.action.name}", timestamp = now, measurementId = measurement.id)
    val updated = session.copy(measurements = session.measurements + measurement, actions = session.actions + event)
    return copy(sessions = sessions.map { if (it.id == session.id) updated else it }, measurementDraft = null)
}

fun Project.undoSessionMeasurement(now: String): Project {
    val session = requireNotNull(activeSession) { "Kies eerst een actieve sessie." }
    require(session.status == SessionStatus.OPEN) { "Afgeronde sessies zijn alleen-lezen." }
    if (measurementDraft != null) return copy(measurementDraft = null)
    val last = session.acceptedMeasurements.lastOrNull() ?: return this
    val event = ActionEvent(type = "ACTION_REVERTED", timestamp = now, measurementId = last.id,
        targetActionId = session.actions.lastOrNull { it.measurementId == last.id }?.id)
    return copy(sessions = sessions.map { if (it.id == session.id) it.copy(actions = it.actions + event) else it })
}

fun Project.closeMeasurementSession(now: String): Project {
    val session = requireNotNull(activeSession)
    require(session.status == SessionStatus.OPEN)
    require(measurementDraft == null) { "Sla de conceptmeting op of verwerp deze eerst." }
    return copy(sessions = sessions.map { if (it.id == session.id) it.copy(status = SessionStatus.CLOSED, closedAt = now) else it }, activeSessionId = null)
}

/** Idempotent migration; a legacy log becomes one closed, explicitly labelled session. */
fun Project.migrateLegacyMeasurements(log: InstallationLog): Project {
    if (sessions.isNotEmpty() || log.results.isEmpty()) return this
    val revision = snapshotGeometry(log.startedAt)
    val session = MeasurementSession(name = "Sessie 1 · eerdere metingen", operator = log.operator,
        startedAt = log.startedAt, closedAt = log.results.maxOf { it.confirmedAt }, status = SessionStatus.CLOSED,
        geometryRevisionId = revision.id, sensorDefinitions = sensors.toList(), migrationSource = "LEGACY",
        measurements = log.results.map { SensorMeasurement(sensorId = it.sensorId, action = MeasurementAction.PLACE,
            result = it, method = MeasurementMethod.LEGACY) })
    return copy(geometryRevisions = geometryRevisions + revision, sessions = listOf(session), migrationSource = "LEGACY")
}

/** Poses are relative to a persisted seed TAG, never an ARCore world frame. */
data class ReferenceTagNode(val tagId: Int, val wall: CalibrationWall, val sizeMm: Int,
    val seedFromTag: List<Double>, val referenceUp: List<Double>, val sampleCount: Int,
    val scatterMm: Double, val directVerified: Boolean)
data class ReferencePoseEdge(val fromTagId: Int, val toTagId: Int, val relativeTransform: List<Double>,
    val sampleCount: Int, val captureSpanMs: Long, val medianReprojectionError: Double,
    val translationScatterMm: Double, val rotationScatterDeg: Double, val createdAt: String,
    val directSameFrame: Boolean = true)
data class ReferenceGraph(val seedTagId: Int? = null, val assignments: List<WallTagAssignment> = emptyList(),
    val nodes: List<ReferenceTagNode> = emptyList(), val edges: List<ReferencePoseEdge> = emptyList(),
    val topSurfaceOffsetMm: Int = 0, val sideHeightMm: Int? = null)
