package com.example.arsens.data

import org.json.JSONArray
import org.json.JSONObject

internal fun WallCalibrationData.toWallJson(): JSONObject = JSONObject()
    .put("version", version).put("accepted", accepted).put("calibrated_at", calibratedAt)
    .put("dimensions_mm", JSONArray(listOf(dimensionsMm.x, dimensionsMm.y, dimensionsMm.z)))
    .put("dimension_source", dimensionSource.wireName).put("geometry_signature", geometrySignature)
    .put("wall_assignments", JSONArray(assignments.map { JSONObject().put("tag_id", it.tagId)
        .put("wall", it.wall.name).put("size_mm", it.sizeMm) }))
    .put("datum", JSONObject().put("lower_tag_id", datum.lowerTagId).put("height_above_bottom_mm", datum.heightAboveBottomMm).put("top_surface_offset_mm", datum.topSurfaceOffsetMm)
        .apply { datum.sideHeightMm?.let { put("side_height_mm", it) }; datum.upperTagId?.let { put("upper_tag_id", it) }; datum.distanceBelowTopMm?.let { put("distance_below_top_mm", it) } })
    .put("quality", JSONObject().put("rms_mm", quality.rmsMm).put("max_residual_mm", quality.maxResidualMm)
        .put("normal_residual_deg", quality.normalResidualDeg).put("repeatability_mm", quality.repeatabilityMm).put("top_overlap_verified", quality.topOverlapVerified)
        .put("used_tag_ids", JSONArray(quality.usedTagIds)).put("excluded_tag_ids", JSONArray(quality.excludedTagIds)))

internal fun JSONObject.readWallCalibration(): WallCalibrationData? = runCatching {
    fun JSONArray.ints() = (0 until length()).map { getInt(it) }
    val d = getJSONArray("dimensions_mm")
    val datum = getJSONObject("datum")
    val q = getJSONObject("quality")
    val assignments = getJSONArray("wall_assignments")
    WallCalibrationData(getInt("version"), optBoolean("accepted", false), getString("calibrated_at"),
        MmPosition(d.getInt(0), d.getInt(1), d.getInt(2)),
        WallDimensionSource.entries.first { it.wireName == getString("dimension_source") },
        (0 until assignments.length()).map { index -> assignments.getJSONObject(index).let {
            WallTagAssignment(it.getInt("tag_id"), CalibrationWall.valueOf(it.getString("wall")), it.getInt("size_mm"))
        } },
        WallVerticalDatum(datum.getInt("lower_tag_id"), datum.getInt("height_above_bottom_mm"),
            datum.optInt("upper_tag_id", -1).takeIf { it >= 0 }, datum.optInt("distance_below_top_mm", -1).takeIf { it >= 0 }, datum.optInt("top_surface_offset_mm", 0), datum.optInt("side_height_mm", 0).takeIf { it > 0 }),
        WallCalibrationQuality(q.getDouble("rms_mm"), q.getDouble("max_residual_mm"), q.getDouble("normal_residual_deg"),
            q.getDouble("repeatability_mm"), q.getJSONArray("used_tag_ids").ints(), q.getJSONArray("excluded_tag_ids").ints(), q.optBoolean("top_overlap_verified", false)), optString("geometry_signature", "")
    ).takeIf { it.quality.usedTagIds.isNotEmpty() && listOf(it.quality.rmsMm, it.quality.maxResidualMm,
        it.quality.normalResidualDeg, it.quality.repeatabilityMm).all { value -> value.isFinite() && value >= 0 } }
}.getOrNull()
