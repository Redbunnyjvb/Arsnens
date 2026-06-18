package com.example.arsens.data

/** Kwaliteitsklasse van een AR-plaatsing. Bepaalt de kleur in de UI (groen/oranje/rood/grijs) en
 *  wordt mee opgeslagen in de audit-snapshot per sensor. Afgeleid van DREMPELS op de ruwe signalen
 *  (reprojectie-fit, jitter, beweging, tag-leeftijd), niet uit één samengesteld mm-getal. */
enum class QualityGrade(val wireName: String, val label: String) {
    High("high", "Hoog"),
    Medium("medium", "Gemiddeld"),
    Low("low", "Laag"),
    Unsafe("unsafe", "Onveilig");

    companion object {
        fun fromWireName(value: String?): QualityGrade =
            entries.firstOrNull { it.wireName == value } ?: Unsafe
    }
}

/**
 * Onveranderlijke kwaliteits-/audit-snapshot, vastgelegd op het moment dat een sensor LIVE in AR
 * werd geplaatst. Verandert nooit mee met latere herankering — puur audittrail naast de (eveneens
 * immutable) [Sensor.positionMm].
 *
 * Bewust GEEN samengesteld "estimatedAccuracyMm": dat suggereerde een precisie die er niet is. In
 * plaats daarvan de RUWE, echt gemeten signalen, zodat een controleur zelf kan oordelen. De enige
 * mm-waarde is [reprojectionErrorMm] — de pose-fitfout fysisch omgerekend (px × afstand/fx), en
 * dus onderbouwd; dat is wél de fout van de tag-fit, niet de totale plaatsingsfout.
 *
 * Alle velden zijn primitief zodat het data-pakket niet van het ar-pakket hoeft af te hangen.
 */
data class SensorPlacementAudit(
    val grade: QualityGrade,
    /** [com.example.arsens.ar.ArTrackingStatus].name op het plaatsingsmoment. */
    val trackingStatus: String,
    val trackingQualityPercent: Int,
    val detectionAgeMillis: Long,
    val poseMarkerIds: List<Int>,
    val referenceTagId: Int?,
    /** Reprojectiefout van de pose-fit op de tag-hoeken (pixels) — echt gemeten. */
    val reprojectionErrorPx: Float?,
    /** Reprojectiefout omgerekend naar mm op tag-diepte (px × afstand/fx) — onderbouwd, maar enkel
     *  de FIT-fout van de tag, niet de totale plaatsingsfout. */
    val reprojectionErrorMm: Float?,
    val jitterMm: Float?,
    val motionDuringDetectionMm: Float?,
    val motionDuringDetectionDeg: Float?,
    /** Voldeed de plaatsing aan alle stabiele-lock-criteria (dwell, samples, jitter, motion)? */
    val wasStablePlacementLock: Boolean,
    val fusionEvent: String?,
    val fusionReason: String?,
    /** Mensleesbare redenen waarom de grade niet High was (leeg bij High). */
    val reasons: List<String>,
    /** System.currentTimeMillis() op het plaatsingsmoment — gebruikt om latere herankering-
     *  correcties alleen aan sensoren te koppelen die vóór die correctie geplaatst zijn. */
    val placedAtWallMillis: Long
)
