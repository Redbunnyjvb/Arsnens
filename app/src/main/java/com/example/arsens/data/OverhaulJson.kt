package com.example.arsens.data

import org.json.JSONArray
import org.json.JSONObject

/** Explicit wire schema. Legacy fields remain readable by existing exports and 2D geometry. */
internal object OverhaulJson {
    private fun <T> JSONArray?.objects(read: (JSONObject) -> T): List<T> =
        if (this == null) emptyList() else List(length()) { read(getJSONObject(it)) }
    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
    private fun JSONArray.doubles() = List(length()) { getDouble(it) }
    private fun dimensions(values: List<DimensionValue>) = JSONArray(values.map {
        JSONObject().put("axis", it.axis).put("value_mm", it.valueMm).put("source", it.source.name)
            .put("source_reference", it.sourceReference).put("confirmed", it.confirmedByOperator).put("revision", it.revision)
    })
    private fun readDimensions(json: JSONArray?) = json.objects {
        DimensionValue(it.getString("axis"), it.getInt("value_mm"), DimensionSource.valueOf(it.getString("source")),
            it.text("source_reference"), it.optBoolean("confirmed"), it.optInt("revision", 1))
    }
    fun graph(graph: ReferenceGraph): JSONObject = JSONObject().put("seed", graph.seedTagId)
        .put("top_offset_mm", graph.topSurfaceOffsetMm).put("height_mm", graph.sideHeightMm)
        .put("assignments", JSONArray(graph.assignments.map { JSONObject().put("id", it.tagId).put("wall", it.wall.name).put("size", it.sizeMm).put("label_source", it.labelSource) }))
        .put("nodes", JSONArray(graph.nodes.map { JSONObject().put("id", it.tagId).put("wall", it.wall.name).put("size", it.sizeMm)
            .put("pose", JSONArray(it.seedFromTag)).put("up", JSONArray(it.referenceUp)).put("samples", it.sampleCount)
            .put("scatter_mm", it.scatterMm).put("direct", it.directVerified) }))
        .put("edges", JSONArray(graph.edges.map { JSONObject().put("from", it.fromTagId).put("to", it.toTagId)
            .put("pose", JSONArray(it.relativeTransform)).put("samples", it.sampleCount).put("span_ms", it.captureSpanMs)
            .put("reprojection_px", it.medianReprojectionError).put("scatter_mm", it.translationScatterMm)
            .put("scatter_deg", it.rotationScatterDeg).put("created_at", it.createdAt).put("direct", it.directSameFrame) }))
    fun readGraph(json: JSONObject?): ReferenceGraph {
        if (json == null) return ReferenceGraph()
        return ReferenceGraph(json.text("seed")?.toInt(), json.optJSONArray("assignments").objects {
            WallTagAssignment(it.getInt("id"), CalibrationWall.valueOf(it.getString("wall")), it.getInt("size"), it.optString("label_source", "OPERATOR"))
        }, json.optJSONArray("nodes").objects {
            ReferenceTagNode(it.getInt("id"), CalibrationWall.valueOf(it.getString("wall")), it.getInt("size"),
                it.getJSONArray("pose").doubles(), it.getJSONArray("up").doubles(), it.getInt("samples"), it.getDouble("scatter_mm"), it.getBoolean("direct"))
        }, json.optJSONArray("edges").objects {
            ReferencePoseEdge(it.getInt("from"), it.getInt("to"), it.getJSONArray("pose").doubles(), it.getInt("samples"),
                it.getLong("span_ms"), it.getDouble("reprojection_px"), it.getDouble("scatter_mm"), it.getDouble("scatter_deg"),
                it.getString("created_at"), it.getBoolean("direct"))
        }, json.optInt("top_offset_mm"), json.text("height_mm")?.toInt())
    }
    fun write(project: Project): JSONObject = JSONObject()
        .put("dimensions", dimensions(project.dimensionValues)).put("migration_source", project.migrationSource)
        .put("reference_graph", graph(project.referenceGraph)).put("active_session_id", project.activeSessionId)
        .put("geometry_revisions", JSONArray(project.geometryRevisions.map { revision ->
            val snapshot = Project(project.projectName, project.modelFile, emptyList(), revision.markers,
                dimensionsMm = revision.dimensionsMm, coordinateFrame = revision.coordinateFrame,
                stlModels = revision.stlModels, wallCalibration = revision.calibration, projectId = project.projectId)
            JSONObject().put("id", revision.id).put("number", revision.number).put("created_at", revision.createdAt)
                .put("snapshot", JSONObject(JsonProjectStore.projectToJson(snapshot)))
                .put("dimensions", dimensions(revision.dimensions)).put("graph", graph(revision.referenceGraph))
        })).put("sessions", JSONArray(project.sessions.map(::session)))
        .apply { project.measurementDraft?.let { d -> put("draft", JSONObject().put("session_id", d.sessionId)
            .put("sensor_id", d.sensorId).put("action", d.action.name).put("result", JsonProjectStore.resultToJson(d.result))
            .put("method", d.method.name).put("sensor_tag_id", d.sensorTagId).put("created_at", d.createdAt)
            .apply { d.audit?.let { put("audit", JsonProjectStore.placementToJson(it)) } }) } }

    private fun session(s: MeasurementSession) = JSONObject().put("id", s.id).put("name", s.name).put("operator", s.operator)
        .put("purpose", s.purpose).put("started_at", s.startedAt).put("closed_at", s.closedAt).put("status", s.status.name)
        .put("geometry_revision_id", s.geometryRevisionId).put("migration_source", s.migrationSource)
        .put("sensor_definitions", JSONArray(s.sensorDefinitions.map(JsonProjectStore::sensorToJson)))
        .put("baseline", JSONArray(s.baseline.map(JsonProjectStore::resultToJson)))
        .put("measurements", JSONArray(s.measurements.map { m -> JSONObject().put("id", m.id).put("sensor_id", m.sensorId)
            .put("action", m.action.name).put("result", JsonProjectStore.resultToJson(m.result)).put("method", m.method.name)
            .put("sensor_tag_id", m.sensorTagId).apply { m.audit?.let { put("audit", JsonProjectStore.placementToJson(it)) }
                m.orientation?.let { put("orientation", JSONArray(listOf(it.x, it.y, it.z))) } } }))
        .put("actions", JSONArray(s.actions.map { JSONObject().put("id", it.id).put("type", it.type).put("timestamp", it.timestamp)
            .put("measurement_id", it.measurementId).put("target_action_id", it.targetActionId) }))

    fun read(project: Project, json: JSONObject?): Project {
        if (json == null) return project
        return project.copy(dimensionValues = readDimensions(json.optJSONArray("dimensions")),
            migrationSource = json.text("migration_source") ?: project.migrationSource,
            referenceGraph = readGraph(json.optJSONObject("reference_graph")), activeSessionId = json.text("active_session_id"),
            geometryRevisions = json.optJSONArray("geometry_revisions").objects { r ->
                val p = JsonProjectStore.projectFromJson(r.getJSONObject("snapshot").toString())
                GeometryRevision(r.getString("id"), r.getInt("number"), r.getString("created_at"), p.dimensionsMm,
                    readDimensions(r.optJSONArray("dimensions")), p.markers, p.coordinateFrame, p.wallCalibration, p.stlModels, readGraph(r.optJSONObject("graph")))
            }, sessions = json.optJSONArray("sessions").objects { s -> MeasurementSession(
                id = s.getString("id"), name = s.getString("name"), operator = s.getString("operator"), purpose = s.optString("purpose"),
                startedAt = s.getString("started_at"), closedAt = s.text("closed_at"), status = SessionStatus.valueOf(s.getString("status")),
                geometryRevisionId = s.getString("geometry_revision_id"), migrationSource = s.text("migration_source"),
                sensorDefinitions = s.optJSONArray("sensor_definitions").objects(JsonProjectStore::sensorFromJson),
                baseline = s.optJSONArray("baseline").objects(JsonProjectStore::resultFromJson),
                measurements = s.optJSONArray("measurements").objects { m -> SensorMeasurement(id = m.getString("id"), sensorId = m.getString("sensor_id"),
                    action = MeasurementAction.valueOf(m.getString("action")), result = JsonProjectStore.resultFromJson(m.getJSONObject("result")),
                    method = MeasurementMethod.valueOf(m.getString("method")), sensorTagId = m.text("sensor_tag_id")?.toInt(),
                    audit = m.optJSONObject("audit")?.let(JsonProjectStore::placementFromJson),
                    orientation = m.optJSONArray("orientation")?.let { FloatVector(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat()) }) },
                actions = s.optJSONArray("actions").objects { ActionEvent(it.getString("id"), it.getString("type"), it.getString("timestamp"),
                    it.getString("measurement_id"), it.text("target_action_id")) })
            }, measurementDraft = json.optJSONObject("draft")?.let { d -> MeasurementDraft(d.getString("session_id"), d.getString("sensor_id"),
                MeasurementAction.valueOf(d.getString("action")), JsonProjectStore.resultFromJson(d.getJSONObject("result")),
                MeasurementMethod.valueOf(d.getString("method")), d.text("sensor_tag_id")?.toInt(),
                d.optJSONObject("audit")?.let(JsonProjectStore::placementFromJson), d.getString("created_at")) })
    }
}
