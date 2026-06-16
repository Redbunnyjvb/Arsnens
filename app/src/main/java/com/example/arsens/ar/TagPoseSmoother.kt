package com.example.arsens.ar

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Temporele demping van solvePnP-poses tegen trillen ("jitter") op afstand.
 *
 * Op een paar meter van de tag is de hoekruis van de detectie al gauw een halve pixel; via
 * solvePnP vertaalt dat zich in millimeters tot centimeters pose-ruis per frame, waardoor
 * sensoren en het 3D-model zichtbaar trillen. Deze smoother houdt per tag een gladde toestand
 * bij en volgt de verse pose met een bewegingsafhankelijke factor:
 *
 *  - vrijwel stilstand → factor [minAlpha] (zware demping: ruis valt weg);
 *  - groter verschil → factor loopt kwadratisch op naar 1 (geen merkbare lag bij beweging);
 *  - boven de snap-drempels (echte sprong, nieuwe kijkhoek of rvec-tekenwissel) → direct volgen.
 *
 * Bewust geen Kalman: de pose-keten heeft geen snelheidsmodel nodig, alleen ruisonderdrukking,
 * en dit gedrag is op het toestel eenvoudig te tunen via de drie constanten hieronder.
 */
class TagPoseSmoother(
    private val snapTranslationMm: Float = SNAP_TRANSLATION_MM,
    private val snapRotationRad: Float = SNAP_ROTATION_RAD,
    private val minAlpha: Float = MIN_ALPHA,
    private val resetAfterMillis: Long = RESET_AFTER_MILLIS
) {
    companion object {
        /** Verschuiving per frame waarboven we de verse pose direct overnemen (mm). */
        const val SNAP_TRANSLATION_MM = 120f

        /** Rotatieverschil per frame waarboven we direct overnemen (rad, ~11°). */
        const val SNAP_ROTATION_RAD = 0.20f

        /** Volgfactor bij stilstand: lager = rustiger beeld, trager herstel van kleine drift. */
        const val MIN_ALPHA = 0.12f

        /** Tag zo lang niet gezien → toestand weggooien en opnieuw beginnen. */
        const val RESET_AFTER_MILLIS = 800L

        /** Sleutel voor de gefuseerde multi-tag pose (geen echte tag-id). */
        const val FUSED_KEY = Int.MIN_VALUE

        /** Sleutel voor de beeldprojectie-pose als die los staat van de hoofdpose. */
        const val IMAGE_KEY = Int.MIN_VALUE + 1
    }

    private class State(
        val translation: FloatArray,
        val rotation: FloatArray,
        var lastSeenMillis: Long,
        var pendingTranslation: FloatArray? = null,
        var pendingRotation: FloatArray? = null
    )

    private val states = HashMap<Int, State>()

    fun smooth(key: Int, pose: TransformerPose, nowMillis: Long): TransformerPose {
        val state = states[key]
        if (state == null || nowMillis - state.lastSeenMillis > resetAfterMillis) {
            states[key] = State(pose.translationMm.copyOf(), pose.rotationVector.copyOf(), nowMillis)
            return pose
        }
        // Stap 3: bepaal het TIJDsverschil sinds de vorige call VÓÓR lastSeenMillis overschreven
        // wordt. De call-cadans (= UI-delivery cadans) is variabel geworden door de conflated
        // publisher (stap 1) en kan per toestel/belasting verschillen; door de EMA-factor met dtMillis
        // te schalen blijft de wall-clock demping gelijk, ongeacht hoe vaak smooth() wordt aangeroepen.
        val dtMillis = (nowMillis - state.lastSeenMillis)
            .coerceIn(1L, SMOOTHING_MAX_DT_MILLIS)
            .toFloat()
        state.lastSeenMillis = nowMillis
        val dt = distance(pose.translationMm, state.translation)
        val dr = distance(pose.rotationVector, state.rotation)
        if (dt > snapTranslationMm || dr > snapRotationRad) {
            // Sprong: échte verplaatsing of een één-frame-uitschieter (bv. de gespiegelde
            // IPPE-oplossing van een vlakke tag)? Pas snappen wanneer een TWEEDE opeenvolgend
            // frame hetzelfde zegt: uitschieters worden zo volledig onderdrukt, echte beweging
            // volgt met hooguit één detectieframe vertraging.
            val pendingT = state.pendingTranslation
            val pendingR = state.pendingRotation
            if (pendingT != null && pendingR != null &&
                distance(pose.translationMm, pendingT) <= snapTranslationMm &&
                distance(pose.rotationVector, pendingR) <= snapRotationRad
            ) {
                pose.translationMm.copyInto(state.translation)
                pose.rotationVector.copyInto(state.rotation)
                state.pendingTranslation = null
                state.pendingRotation = null
                return pose
            }
            state.pendingTranslation = pose.translationMm.copyOf()
            state.pendingRotation = pose.rotationVector.copyOf()
            // Houd de huidige gladde toestand één frame vast in afwachting van bevestiging.
            return TransformerPose(
                translationMm = state.translation.copyOf(),
                rotationVector = state.rotation.copyOf(),
                reprojectionErrorPx = pose.reprojectionErrorPx
            )
        }
        state.pendingTranslation = null
        state.pendingRotation = null
        val alphaT = alphaFor(dt, snapTranslationMm, dtMillis)
        val alphaR = alphaFor(dr, snapRotationRad, dtMillis)
        for (i in 0..2) {
            state.translation[i] += alphaT * (pose.translationMm[i] - state.translation[i])
            state.rotation[i] += alphaR * (pose.rotationVector[i] - state.rotation[i])
        }
        return TransformerPose(
            translationMm = state.translation.copyOf(),
            rotationVector = state.rotation.copyOf(),
            reprojectionErrorPx = pose.reprojectionErrorPx
        )
    }

    /** Gooit toestanden weg van tags die langer dan [resetAfterMillis] niet gezien zijn. */
    fun prune(nowMillis: Long) {
        states.entries.removeAll { nowMillis - it.value.lastSeenMillis > resetAfterMillis }
    }

    fun reset() {
        states.clear()
    }

    /**
     * Kwadratisch oplopende volgfactor: stilstand → [minAlpha], richting snap-drempel → 1.
     *
     * [dtMillis] maakt de factor frame-rate-onafhankelijk. [alphaBase] is de factor per baseline-frame
     * ([SMOOTHING_BASELINE_DT_MILLIS]); bij precies die call-cadans geldt alphaDt == alphaBase, dus
     * identiek aan het gedrag van vóór de tijd-schaal. Wordt smooth() trager aangeroepen (grotere
     * dtMillis), dan compenseert de exponent zodat de wall-clock demping gelijk blijft.
     */
    private fun alphaFor(delta: Float, snap: Float, dtMillis: Float): Float {
        val x = (delta / snap).coerceIn(0f, 1f)
        val alphaBase = minAlpha + (1f - minAlpha) * x * x
        val exponent = dtMillis / SMOOTHING_BASELINE_DT_MILLIS
        return 1f - (1f - alphaBase).pow(exponent)
    }

    private fun distance(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]
        val dy = a[1] - b[1]
        val dz = a[2] - b[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

/**
 * Baseline call-cadans waarop de demping is afgesteld (≈60 fps result-delivery, conform de
 * audit-spec). Bij dtMillis == deze waarde is alphaDt == alphaBase → identiek aan het gedrag van
 * vóór de tijd-schaal. Dit is de ENIGE tuning-knop: levert het toestel results op ~30 fps, zet
 * deze dan op ~33.3f zodat de huidige rust behouden blijft. De unit-tests (33ms-stappen) blijven
 * bij beide waarden groen; de keuze beïnvloedt alleen de wall-clock dempingssnelheid.
 */
private const val SMOOTHING_BASELINE_DT_MILLIS = 16.67f

/** Bovengrens op dtMillis: voorkomt een overdreven inhaal-alpha na een korte stilstand
 *  (echte stilstand > resetAfterMillis valt al onder de reset-tak en wordt 1-op-1 overgenomen). */
private const val SMOOTHING_MAX_DT_MILLIS = 250L
