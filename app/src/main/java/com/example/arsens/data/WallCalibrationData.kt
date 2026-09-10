package com.example.arsens.data

enum class ReferenceGeometryMode(val wireName: String, val label: String) {
    KnownTagPositions("known_tag_positions", "Bekende tagposities"),
    ScannedWalls("scanned_walls", "Wanden scannen")
}

enum class WallDimensionSource(val wireName: String, val label: String) {
    Entered("entered", "Afmetingen bekend"), Scanned("scanned", "Afmetingen bepalen")
}

enum class CalibrationWall(val label: String, val axis: Int, val positive: Boolean) {
    Front("Voor", 1, false), Back("Achter", 1, true), Left("Links", 0, false), Right("Rechts", 0, true), Top("Boven", 2, true)
}

data class WallTagAssignment(val tagId: Int, val wall: CalibrationWall, val sizeMm: Int)

/** Offsets refer to tag CENTERS, never the printed paper edge. */
data class WallVerticalDatum(
    val lowerTagId: Int, val heightAboveBottomMm: Int,
    val upperTagId: Int? = null, val distanceBelowTopMm: Int? = null,
    val topSurfaceOffsetMm: Int = 0,
    /** No lower datum is needed when the lid defines the top; scanned L/W then use this side height. */
    val sideHeightMm: Int? = null
)

data class WallCalibrationQuality(
    val rmsMm: Double, val maxResidualMm: Double, val normalResidualDeg: Double,
    val repeatabilityMm: Double, val usedTagIds: List<Int>, val excludedTagIds: List<Int>,
    val topOverlapVerified: Boolean = false
)

/** Permanent project geometry only. Deliberately contains no ARCore pose or tracking frame ID. */
data class WallCalibrationData(
    val version: Int = 1, val accepted: Boolean = true, val calibratedAt: String,
    val dimensionsMm: MmPosition, val dimensionSource: WallDimensionSource,
    val assignments: List<WallTagAssignment>, val datum: WallVerticalDatum,
    val quality: WallCalibrationQuality, val geometrySignature: String = ""
)

val Project.hasWallCalibration: Boolean get() = wallCalibration?.let {
    it.accepted && it.version == 1 && it.dimensionsMm == dimensionsMm && it.quality.usedTagIds.isNotEmpty() &&
        it.geometrySignature.isNotEmpty() && it.geometrySignature == wallGeometrySignature(markers, it.quality.usedTagIds) &&
        it.quality.usedTagIds.all { id -> markers.any { marker -> marker.id == id && marker.isAprilTagCalibrationMarker() } }
} == true

val Project.needsWallCalibration: Boolean get() = referenceGeometryMode == ReferenceGeometryMode.ScannedWalls && !hasWallCalibration

/** Detect edits made through preparation/import or after switching to the other method.
 * Activity/weights may exclude a reference without changing its measured geometry. */
fun wallGeometrySignature(markers: List<Marker>, usedTagIds: List<Int>): String {
    fun bits(value: Float) = (if (value == 0f) 0f else value).toBits()
    val geometry = usedTagIds.sorted().joinToString("|") { id ->
        val tag = markers.singleOrNull { it.id == id && it.isAprilTagCalibrationMarker() }
        if (tag == null) "missing:$id" else listOf(tag.id,tag.sizeMm,tag.positionMm.x,tag.positionMm.y,tag.positionMm.z,
            bits(tag.rotationDeg.x),bits(tag.rotationDeg.y),bits(tag.rotationDeg.z)).joinToString(",")
    }
    return java.security.MessageDigest.getInstance("SHA-256").digest(geometry.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
