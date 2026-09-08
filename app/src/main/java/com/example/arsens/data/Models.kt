package com.example.arsens.data

import kotlin.math.sqrt

enum class SensorStatus(val wireName: String, val label: String) {
    Pending("pending", "Nog te plaatsen"),
    Ok("ok", "OK"),
    Fail("fail", "Fout")
}

/** Hoe een sensor of tag in het project terecht is gekomen — LOS van de plaatsings-[SensorStatus].
 *  Prepared = vooraf gepland (2D/3D of geïmporteerd); OnTheFly = live in AR vastgelegd. Het rapport
 *  toont dit als "Herkomst" (Voorbereid/On the fly); de KLEUR van de stip komt daarentegen uit de
 *  status (oranje = nog te plaatsen, blauw = geplaatst). Twee onafhankelijke assen. */
enum class PlacementOrigin(val wireName: String, val label: String) {
    Prepared("prepared", "Voorbereid"),
    OnTheFly("on_the_fly", "On the fly")
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
    /** Referentietag bij plaatsing, voor traceerbaarheid. Alle overlays delen het wereldanker. */
    val referenceTagId: Int? = null,
    /** Onveranderlijke kwaliteits-/audit-snapshot van het LIVE-AR plaatsingsmoment (grade + ruwe
     *  signalen: reproj, jitter, motion, tag-context). Null bij voorbereide/2D-plaatsingen.
     *  Verandert nooit mee met latere herankering — [placement] blijft eveneens immutable. */
    val placement: SensorPlacementAudit? = null,
    /** Vastgelegd zodra de straal-replay-driftcorrectie [positionMm] heeft bijgesteld na herankering.
     *  Null = nooit gecorrigeerd. Maakt de bijstelling traceerbaar (en zet de "*" in het rapport). */
    val driftCorrection: SensorDriftCorrection? = null,
    /** AprilTag-ID die fysiek óp deze sensor geplakt is (≥ AppSettings.sensorTagStartId). Maakt het
     *  mogelijk de sensor live te herkennen en zijn werkelijke positie te meten in de Install-flow.
     *  Null = geen sensor-tag gekoppeld. */
    val sensorTagId: Int? = null,
    /** Hoe deze sensor in het project kwam: vooraf gepland/geïmporteerd of live geplaatst. Bepaalt
     *  het "Herkomst"-label in het rapport (NIET de kleur — die komt uit [status]). Een voorbereide
     *  sensor die later live bevestigd wordt blijft [PlacementOrigin.Prepared]: herkomst = hoe hij
     *  ontstond, niet of hij al geplaatst is. */
    val origin: PlacementOrigin = PlacementOrigin.Prepared
)

/**
 * Traceer-record van een toegepaste straal-replay-driftcorrectie. Anders dan [positionMm] (dat de
 * gecorrigeerde, actuele plek is) bewaart dit de ORIGINELE plaatsing plus de bijstelling, zodat het
 * rapport kan tonen dát er gecorrigeerd is, met hoeveel en op welk anker.
 */
data class SensorDriftCorrection(
    /** Positie zoals oorspronkelijk LIVE geplaatst, vóór enige correctie. */
    val asPlacedPositionMm: MmPosition,
    /** Verplaatsing in mm t.o.v. de oorspronkelijke plaatsing (Euclidische afstand). */
    val deltaMm: Int,
    /** System.currentTimeMillis() op het correctiemoment. */
    val correctedAtWallMillis: Long,
    /** Tag-ids die in beeld waren toen de correctie werd toegepast. */
    val poseMarkerIds: List<Int>
)

data class Marker(
    val id: Int,
    val type: String,
    val sizeMm: Int,
    val positionMm: MmPosition,
    val rotationDeg: FloatVector,
    val active: Boolean = true,
    val poseWeight: Float = 1f,
    /** Vooraf gepland/geïmporteerd of live ontdekt — parallel aan [Sensor.origin]. */
    val origin: PlacementOrigin = PlacementOrigin.Prepared
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
        // Back gecorrigeerd (Rz=180 — een achtertag wordt van de TEGENOVERGESTELDE kant gelezen): oude
        // back-tags op de niet-gedraaide Rz=0 → bij het lezen omzetten naar 180. (Back-tags die al 180
        // zijn matchen niet en blijven ongemoeid; een fronttag staat op y==0, niet y==diepte.)
        rotationDeg.sameAs(FloatVector(0f, 0f, 0f)) && positionMm.y == dimensionsMm.y ->
            FloatVector(0f, 0f, 180f)
        // Zijvlakken gecorrigeerd (na de Left=-90/Right=+90 fix): oude opgeslagen zijtags staan op de
        // GESPIEGELDE waarde (Left Rz=+90, Right Rz=-90) → bij het lezen omzetten naar de juiste. Tags
        // die al op de nieuwe waarde staan matchen hier niet en blijven ongemoeid.
        rotationDeg.sameAs(FloatVector(0f, 0f, 90f)) && positionMm.x == 0 ->
            FloatVector(0f, 0f, -90f)
        rotationDeg.sameAs(FloatVector(0f, 0f, -90f)) && positionMm.x == dimensionsMm.x ->
            FloatVector(0f, 0f, 90f)
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

/** De sensor waaraan een gedetecteerde sensor-tag-ID gekoppeld is, of null als geen enkele sensor
 *  die tag draagt. Pure mapping (geen AR/Android), zodat de koppeling los te unit-testen is. */
fun sensorForSensorTag(sensors: List<Sensor>, tagId: Int): Sensor? =
    sensors.firstOrNull { it.sensorTagId == tagId }

/** Sensor-ID voor een (live gescande) sensor-tag bij on-the-fly plaatsen:
 *  - draagt al een sensor deze tag → diens ID (her-plaatsen werkt die sensor bij i.p.v. dupliceren);
 *  - anders het afgeleide nummer tagId − [sensorTagStartId] + 1 (tag 200 → "1", 201 → "2", …);
 *  - is dat afgeleide nummer al door een ándere sensor bezet → het eerstvolgende vrije gehele ID. */
fun deriveSensorIdForScannedTag(sensors: List<Sensor>, tagId: Int, sensorTagStartId: Int): String {
    sensorForSensorTag(sensors, tagId)?.let { return it.id }
    val derived = (tagId - sensorTagStartId + 1).coerceAtLeast(1).toString()
    if (sensors.none { it.id == derived }) return derived
    val used = sensors.mapNotNull { it.id.toIntOrNull() }.toSet()
    return generateSequence(1) { it + 1 }.first { it !in used }.toString()
}

fun distanceMm(offset: MmPosition): Int {
    val squared = offset.x * offset.x + offset.y * offset.y + offset.z * offset.z
    return sqrt(squared.toDouble()).toInt()
}

fun statusFromWireName(value: String?): SensorStatus =
    SensorStatus.entries.firstOrNull { it.wireName == value } ?: SensorStatus.Pending

fun originFromWireName(value: String?): PlacementOrigin =
    PlacementOrigin.entries.firstOrNull { it.wireName == value } ?: PlacementOrigin.Prepared

fun originCornerFromWireName(value: String?): OriginCorner =
    OriginCorner.entries.firstOrNull { it.wireName == value } ?: OriginCorner.FrontLeftBottom
