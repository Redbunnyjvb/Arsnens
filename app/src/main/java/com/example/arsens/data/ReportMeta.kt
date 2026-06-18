package com.example.arsens.data

/**
 * Herkomst-/contextgegevens die NIET in [Project] of [InstallationLog] zitten maar wél in het
 * rapport horen voor een traceerbaar verhaal: wanneer is geëxporteerd en stond AR-driftcorrectie
 * aan. Optioneel met defaults zodat bestaande aanroepen (en tests) blijven compileren.
 */
data class ReportMeta(
    /** Mensleesbaar exporttijdstip (bv. "2026-06-18 14:32"); leeg = onbekend. */
    val exportedAt: String = "",
    /** Stond de app-brede AR-driftcorrectie aan op het exportmoment? */
    val driftCorrectionEnabled: Boolean = false
)
