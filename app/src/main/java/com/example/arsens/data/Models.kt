package com.example.arsens.data

import kotlin.math.sqrt

enum class SensorStatus(val wireName: String, val label: String) {
    Pending("pending", "Nog te plaatsen"),
    Ok("ok", "OK"),
    Fail("fail", "Fout")
}

data class MmPosition(
    val x: Int,
    val y: Int,
    val z: Int
) {
    fun toCsvCell(): String = "$x,$y,$z"

    operator fun plus(other: MmPosition): MmPosition =
        MmPosition(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: MmPosition): MmPosition =
        MmPosition(x - other.x, y - other.y, z - other.z)
}

data class FloatVector(
    val x: Float,
    val y: Float,
    val z: Float
)

data class Sensor(
    val order: Int,
    val id: String,
    val name: String,
    val side: String,
    val positionMm: MmPosition,
    val normal: FloatVector = FloatVector(0f, 1f, 0f),
    val toleranceMm: Int,
    val instruction: String,
    val status: SensorStatus = SensorStatus.Pending,
    /** ID van de AprilTag die als referentie gebruikt werd bij plaatsing van deze sensor.
     *  Als deze tag zichtbaar is, wordt ALLEEN deze pose gebruikt voor overlay-projectie —
     *  zodat de sensor nooit verschuift wanneer een andere tag actief wordt. */
    val referenceTagId: Int? = null,
    /** Onveranderlijke kwaliteits-/audit-snapshot van het LIVE-AR plaatsingsmoment (grade + ruwe
     *  signalen: reproj, jitter, motion, tag-context). Null bij voorbereide/2D-plaatsingen.
     *  Verandert nooit mee met latere herankering — [positionMm] blijft eveneens immutable. */
    val placement: SensorPlacementAudit? = null
)

data class Marker(
    val id: Int,
    val type: String,
    val sizeMm: Int,
    val positionMm: MmPosition,
    val rotationDeg: FloatVector,
    val active: Boolean = true,
    val poseWeight: Float = 1f
)

fun Marker.isAprilTagCalibrationMarker(): Boolean =
    type.equals("apriltag", ignoreCase = true) ||
        // Older bundled and saved projects used "aruco" for these same AprilTag reference markers.
        type.equals("aruco", ignoreCase = true)

fun Marker.asAprilTagCalibrationMarker(dimensionsMm: MmPosition): Marker =
    if (!isAprilTagCalibrationMarker()) {
        this
    } else {
        copy(
            type = "apriltag",
            rotationDeg = correctedLegacyAprilTagRotation(dimensionsMm) ?: rotationDeg
        )
    }

private fun Marker.correctedLegacyAprilTagRotation(dimensionsMm: MmPosition): FloatVector? =
    when {
        // Front gecorrigeerd: oude opgeslagen front-tags staan op de gespiegelde Rz=180 → zet ze bij
        // het lezen om naar de juiste Rz=0. (Front-tags met (0,0,0) blijven zo ongemoeid.)
        rotationDeg.sameAs(FloatVector(0f, 0f, 180f)) && positionMm.y == 0 ->
            FloatVector(0f, 0f, 0f)
        rotationDeg.sameAs(FloatVector(0f, 0f, 180f)) && positionMm.y == dimensionsMm.y ->
            FloatVector(0f, 0f, 0f)
        rotationDeg.sameAs(FloatVector(0f, 0f, -90f)) && positionMm.x == 0 ->
            FloatVector(0f, 0f, 90f)
        rotationDeg.sameAs(FloatVector(0f, 0f, 90f)) && positionMm.x == dimensionsMm.x ->
            FloatVector(0f, 0f, -90f)
        // Top gecorrigeerd: oude opgeslagen top-tags staan op de gespiegelde Rx=+90 → zet ze bij
        // het lezen om naar de juiste Rx=-90. (Top-tags met (-90,0,0) blijven zo ongemoeid.)
        rotationDeg.sameAs(FloatVector(90f, 0f, 0f)) && positionMm.z == dimensionsMm.z ->
            FloatVector(-90f, 0f, 0f)
        else -> null
    }

private fun FloatVector.sameAs(other: FloatVector): Boolean =
    x == other.x && y == other.y && z == other.z

enum class OriginCorner(val wireName: String, val label: String) {
    FrontLeftBottom("front_left_bottom", "Voor links onder"),
    FrontRightBottom("front_right_bottom", "Voor rechts onder"),
    BackLeftBottom("back_left_bottom", "Achter links onder"),
    BackRightBottom("back_right_bottom", "Achter rechts onder")
}

data class CoordinateFrameSettings(
    val originCorner: OriginCorner = OriginCorner.FrontLeftBottom,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    val flipZ: Boolean = false
) {
    val summary: String
        get() = buildList {
            add(originCorner.label)
            add(if (flipX) "X+ naar links" else "X+ naar rechts")
            add(if (flipY) "Y+ naar voren" else "Y+ naar achter")
            add(if (flipZ) "Z+ omlaag" else "Z+ omhoog")
        }.joinToString(", ")
}

/** Rol van een STL-deel binnen de assembly. Bepaalt de auto-uitlijnregels ("Lijn uit op tank")
 *  en de AR-onderdelengroep. Autodetectie op naam bij import; handmatig aanpasbaar in de 3D-editor. */
enum class StlPartRole(val label: String, val wireName: String) {
    Tank("Tank", "TANK"),
    Cover("Deksel", "COVER"),
    Core("Kern", "CORE"),
    ActivePart("Actief deel", "ACTIVE_PART"),
    Wiksets("Wikkelingen", "WIKSETS"),
    BushingsTurrets("Bushings", "BUSHINGS_TURRETS"),
    Other("Overig", "OTHER");

    companion object {
        fun fromWireName(value: String?): StlPartRole =
            entries.firstOrNull { it.wireName == value } ?: Other

        /** Herkent de rol uit een bestands-/partnaam (NX-exports: "Tank.stl", "Active Part.stl", …).
         *  Let op de volgorde: "active" bevat de letters "ct", dus ActivePart vóór de CT-regel. */
        fun detectFromName(name: String): StlPartRole {
            val lower = name.lowercase()
            return when {
                // Deksel vóór tank: "Tankdeksel"/"Tank cover" moet Cover worden, niet Tank.
                "cover" in lower || "deksel" in lower || "lid" in lower || "kap" in lower -> Cover
                "tank" in lower -> Tank
                "active" in lower || "actief" in lower -> ActivePart
                "core" in lower || "kern" in lower -> Core
                "wikset" in lower || "winding" in lower || "wikkel" in lower || "coil" in lower -> Wiksets
                "bushing" in lower || "turret" in lower || "aluplate" in lower || "alu plate" in lower -> BushingsTurrets
                lower == "ct" || lower.startsWith("ct ") || lower.startsWith("ct_") ||
                    " ct" in lower || "_ct" in lower -> BushingsTurrets
                else -> Other
            }
        }
    }
}

/** Eén geladen STL/3D-mesh in de "STL-set" van een project. Het bronbestand wordt naar de
 *  projectmap gekopieerd ([fileName]); schaal en offset positioneren het mesh in het trafo-frame. */
data class StlModel(
    val id: String,
    val name: String,
    val fileName: String,
    val scalePercent: Int = 100,
    val offsetMm: MmPosition = MmPosition(0, 0, 0),
    /** Rotatie in graden per as (X,Y,Z), toegepast om het mesh-midden. Hergebruikt MmPosition als int-triple. */
    val rotationDeg: MmPosition = MmPosition(0, 0, 0),
    val visible: Boolean = true,
    val role: StlPartRole = StlPartRole.Other
)

data class Project(
    val projectName: String,
    val modelFile: String,
    val sensors: List<Sensor>,
    val markers: List<Marker>,
    val dimensionsMm: MmPosition = MmPosition(10000, 5000, 3200),
    /** Vergrendeld = handmatig ingevoerde maten zijn leidend: STL-import/"Lijn uit op tank"
     *  mag [dimensionsMm] dan niet overschrijven met de tankafmetingen. */
    val dimensionsLocked: Boolean = false,
    val coordinateFrame: CoordinateFrameSettings = CoordinateFrameSettings(),
    val stlModels: List<StlModel> = emptyList()
)

data class InstallationResult(
    val sensorId: String,
    val expectedPositionMm: MmPosition,
    val measuredPositionMm: MmPosition? = null,
    val measuredOffsetMm: MmPosition,
    val distanceErrorMm: Int,
    val status: SensorStatus,
    val photoFile: String?,
    val confirmedAt: String
)

data class InstallationLog(
    val projectName: String,
    val startedAt: String,
    val operator: String,
    val results: List<InstallationResult>
)

fun distanceMm(offset: MmPosition): Int {
    val squared = offset.x * offset.x + offset.y * offset.y + offset.z * offset.z
    return sqrt(squared.toDouble()).toInt()
}

fun statusFromWireName(value: String?): SensorStatus =
    SensorStatus.entries.firstOrNull { it.wireName == value } ?: SensorStatus.Pending

fun originCornerFromWireName(value: String?): OriginCorner =
    OriginCorner.entries.firstOrNull { it.wireName == value } ?: OriginCorner.FrontLeftBottom
