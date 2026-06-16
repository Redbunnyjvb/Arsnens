package com.example.arsens.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object JsonProjectStore {
    fun projectToJson(project: Project): String =
        JSONObject()
            .put("project_name", project.projectName)
            .put("model_file", project.modelFile)
            .put("dimensions_mm", project.dimensionsMm.toJsonArray())
            .put("dimensions_locked", project.dimensionsLocked)
            .put("coordinate_frame", coordinateFrameToJson(project.coordinateFrame))
            .put("sensors", JSONArray(project.sensors.map(::sensorToJson)))
            .put("markers", JSONArray(project.markers.map(::markerToJson)))
            .put("stl_models", JSONArray(project.stlModels.map(::stlModelToJson)))
            .toString(2)

    fun projectFromJson(jsonText: String, markersFallback: List<Marker> = emptyList()): Project {
        val json = JSONObject(jsonText)
        val sensors = json.getJSONArray("sensors").mapObjects(::sensorFromJson)
        val markers = json.optJSONArray("markers")?.mapObjects(::markerFromJson) ?: markersFallback
        return Project(
            projectName = json.optString("project_name", "Transformer A"),
            modelFile = json.optString("model_file", "transformer_model.glb"),
            dimensionsMm = json.optJSONArray("dimensions_mm")?.toMmPosition() ?: MmPosition(10000, 5000, 3200),
            dimensionsLocked = json.optBoolean("dimensions_locked", false),
            coordinateFrame = json.optJSONObject("coordinate_frame")?.toCoordinateFrame()
                ?: CoordinateFrameSettings(),
            sensors = sensors.sortedBy { it.order },
            markers = markers,
            stlModels = json.optJSONArray("stl_models")?.mapObjects(::stlModelFromJson).orEmpty()
        )
    }

    fun markersToJson(markers: List<Marker>): String =
        JSONObject()
            .put("markers", JSONArray(markers.map(::markerToJson)))
            .toString(2)

    fun markersFromJson(jsonText: String): List<Marker> {
        val json = JSONObject(jsonText)
        return json.getJSONArray("markers").mapObjects(::markerFromJson)
    }

    fun logToJson(log: InstallationLog): String =
        JSONObject()
            .put("project_name", log.projectName)
            .put("started_at", log.startedAt)
            .put("operator", log.operator)
            .put("results", JSONArray(log.results.map(::resultToJson)))
            .toString(2)

    fun logFromJson(jsonText: String): InstallationLog {
        val json = JSONObject(jsonText)
        return InstallationLog(
            projectName = json.optString("project_name", "Transformer A"),
            startedAt = json.optString("started_at"),
            operator = json.optString("operator", "Operator naam"),
            results = json.optJSONArray("results")?.mapObjects(::resultFromJson).orEmpty()
        )
    }

    fun saveProjectToJson(context: Context, project: Project): File {
        return saveProjectToJson(arsensDir(context), project)
    }

    fun loadProjectFromJson(context: Context): Project? {
        val file = File(arsensDir(context), "project.json")
        return if (file.exists()) projectFromJson(file.readText()) else null
    }

    fun saveInstallationLog(context: Context, log: InstallationLog): File {
        return saveInstallationLog(arsensDir(context), log)
    }

    fun loadInstallationLog(context: Context): InstallationLog? {
        return loadInstallationLog(arsensDir(context))
    }

    fun exportReportCsv(context: Context, project: Project, log: InstallationLog): File {
        return exportReportCsv(arsensDir(context), project, log)
    }

    fun exportReportJson(context: Context, log: InstallationLog): File =
        saveInstallationLog(context, log)

    fun saveProjectToJson(projectDir: File, project: Project): File {
        projectDir.mkdirs()
        val file = File(projectDir, "project.json")
        file.writeText(projectToJson(project))
        File(projectDir, "markers.json").writeText(markersToJson(project.markers))
        File(projectDir, "sensors.json").writeText(projectToJson(project))
        return file
    }

    fun saveInstallationLog(projectDir: File, log: InstallationLog): File {
        projectDir.mkdirs()
        val file = File(projectDir, "installation_log.json")
        file.writeText(logToJson(log))
        return file
    }

    fun loadInstallationLog(projectDir: File): InstallationLog? {
        val file = File(projectDir, "installation_log.json")
        return if (file.exists()) logFromJson(file.readText()) else null
    }

    fun exportReportCsv(projectDir: File, project: Project, log: InstallationLog): File {
        projectDir.mkdirs()
        val file = File(projectDir, "installation_report.csv")
        file.writeText(SensorCsv.exportReportCsv(log, project))
        return file
    }

    fun exportReportJson(projectDir: File, log: InstallationLog): File =
        saveInstallationLog(projectDir, log)

    fun arsensDir(context: Context): File =
        File(context.filesDir, "arsens").also { it.mkdirs() }

    private fun sensorToJson(sensor: Sensor): JSONObject =
        JSONObject()
            .put("order", sensor.order)
            .put("id", sensor.id)
            .put("name", sensor.name)
            .put("side", sensor.side)
            .put("position_mm", sensor.positionMm.toJsonArray())
            .put("normal", sensor.normal.toJsonArray())
            .put("tolerance_mm", sensor.toleranceMm)
            .put("instruction", sensor.instruction)
            .put("status", sensor.status.wireName)
            .apply { sensor.referenceTagId?.let { put("reference_tag_id", it) } }

    private fun sensorFromJson(json: JSONObject): Sensor =
        Sensor(
            order = json.optInt("order", 0),
            id = json.optString("id"),
            name = json.optString("name"),
            side = json.optString("side"),
            positionMm = json.getJSONArray("position_mm").toMmPosition(),
            normal = json.optJSONArray("normal")?.toFloatVector() ?: FloatVector(0f, 1f, 0f),
            toleranceMm = json.optInt("tolerance_mm", 50),
            instruction = json.optString("instruction"),
            status = statusFromWireName(json.optString("status")),
            referenceTagId = json.optInt("reference_tag_id", -1).takeIf { it >= 0 }
        )

    private fun markerToJson(marker: Marker): JSONObject =
        JSONObject()
            .put("id", marker.id)
            .put("type", marker.type)
            .put("size_mm", marker.sizeMm)
            .put("position_mm", marker.positionMm.toJsonArray())
            .put("rotation_deg", marker.rotationDeg.toJsonArray())
            .put("active", marker.active)
            .put("pose_weight", marker.poseWeight)

    private fun markerFromJson(json: JSONObject): Marker =
        Marker(
            id = json.optInt("id"),
            type = json.optString("type", "apriltag"),
            sizeMm = json.optInt("size_mm", 100),
            positionMm = json.getJSONArray("position_mm").toMmPosition(),
            rotationDeg = json.optJSONArray("rotation_deg")?.toFloatVector()
                ?: FloatVector(0f, 0f, 0f),
            active = json.optBoolean("active", true),
            poseWeight = json.optDouble("pose_weight", 1.0).toFloat()
        )

    private fun stlModelToJson(model: StlModel): JSONObject =
        JSONObject()
            .put("id", model.id)
            .put("name", model.name)
            .put("file_name", model.fileName)
            .put("scale_percent", model.scalePercent)
            .put("offset_mm", model.offsetMm.toJsonArray())
            .put("rotation_deg", model.rotationDeg.toJsonArray())
            .put("visible", model.visible)
            .put("role", model.role.wireName)

    private fun stlModelFromJson(json: JSONObject): StlModel {
        val name = json.optString("name")
        return StlModel(
            id = json.optString("id"),
            name = name,
            fileName = json.optString("file_name"),
            scalePercent = json.optInt("scale_percent", 100),
            offsetMm = json.optJSONArray("offset_mm")?.toMmPosition() ?: MmPosition(0, 0, 0),
            rotationDeg = json.optJSONArray("rotation_deg")?.toMmPosition() ?: MmPosition(0, 0, 0),
            visible = json.optBoolean("visible", true),
            // Oude projecten zonder rol-veld krijgen meteen een zinnige rol via naamdetectie.
            role = if (json.has("role")) {
                StlPartRole.fromWireName(json.optString("role"))
            } else {
                StlPartRole.detectFromName(name)
            }
        )
    }

    private fun coordinateFrameToJson(settings: CoordinateFrameSettings): JSONObject =
        JSONObject()
            .put("origin_corner", settings.originCorner.wireName)
            .put("flip_x", settings.flipX)
            .put("flip_y", settings.flipY)
            .put("flip_z", settings.flipZ)

    private fun JSONObject.toCoordinateFrame(): CoordinateFrameSettings =
        CoordinateFrameSettings(
            originCorner = originCornerFromWireName(optString("origin_corner")),
            flipX = optBoolean("flip_x", false),
            flipY = optBoolean("flip_y", false),
            flipZ = optBoolean("flip_z", false)
        )

    private fun resultToJson(result: InstallationResult): JSONObject =
        JSONObject()
            .put("sensor_id", result.sensorId)
            .put("expected_position_mm", result.expectedPositionMm.toJsonArray())
            .apply {
                result.measuredPositionMm?.let {
                    put("measured_position_mm", it.toJsonArray())
                }
            }
            .put("measured_offset_mm", result.measuredOffsetMm.toJsonArray())
            .put("distance_error_mm", result.distanceErrorMm)
            .put("status", result.status.wireName)
            .put("photo_file", result.photoFile)
            .put("confirmed_at", result.confirmedAt)

    private fun resultFromJson(json: JSONObject): InstallationResult =
        InstallationResult(
            sensorId = json.optString("sensor_id"),
            expectedPositionMm = json.getJSONArray("expected_position_mm").toMmPosition(),
            measuredPositionMm = json.optJSONArray("measured_position_mm")?.toMmPosition(),
            measuredOffsetMm = json.getJSONArray("measured_offset_mm").toMmPosition(),
            distanceErrorMm = json.optInt("distance_error_mm"),
            status = statusFromWireName(json.optString("status")),
            photoFile = json.optString("photo_file").ifBlank { null },
            confirmedAt = json.optString("confirmed_at")
        )

    private fun MmPosition.toJsonArray(): JSONArray =
        JSONArray(listOf(x, y, z))

    private fun FloatVector.toJsonArray(): JSONArray =
        JSONArray(listOf(x, y, z))

    private fun JSONArray.toMmPosition(): MmPosition =
        MmPosition(optInt(0), optInt(1), optInt(2))

    private fun JSONArray.toFloatVector(): FloatVector =
        FloatVector(optDouble(0).toFloat(), optDouble(1).toFloat(), optDouble(2).toFloat())

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        List(length()) { index -> transform(getJSONObject(index)) }
}

fun saveProjectToJson(context: Context, project: Project): File =
    JsonProjectStore.saveProjectToJson(context, project)

fun loadProjectFromJson(context: Context): Project? =
    JsonProjectStore.loadProjectFromJson(context)
