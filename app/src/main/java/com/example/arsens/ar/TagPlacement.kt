package com.example.arsens.ar

import com.example.arsens.data.FloatVector
import com.example.arsens.data.MmPosition
import kotlin.math.roundToInt

enum class TagPlane(val label: String, val shortLabel: String) {
    Front("Voorvlak Y=0", "Voor"),
    Back("Achtervlak Y=breedte", "Achter"),
    Left("Links X=0", "Links"),
    Right("Rechts X=lengte", "Rechts"),
    Top("Boven Z=hoogte", "Boven")
}

enum class TagAnchor(val label: String, val u: Float, val v: Float) {
    BottomLeft("Linksonder", 0f, 0f),
    BottomQuarterLeft("Onder 25%", 0.25f, 0f),
    BottomCenter("Onder midden", 0.5f, 0f),
    BottomQuarterRight("Onder 75%", 0.75f, 0f),
    BottomRight("Rechtsonder", 1f, 0f),
    LowerLeft("Links 25%", 0f, 0.25f),
    LowerQuarterLeft("25% / 25%", 0.25f, 0.25f),
    LowerCenter("Midden / 25%", 0.5f, 0.25f),
    LowerQuarterRight("75% / 25%", 0.75f, 0.25f),
    LowerRight("Rechts 25%", 1f, 0.25f),
    MiddleLeft("Midden links", 0f, 0.5f),
    MiddleQuarterLeft("Midden 25%", 0.25f, 0.5f),
    Center("Midden", 0.5f, 0.5f),
    MiddleQuarterRight("Midden 75%", 0.75f, 0.5f),
    MiddleRight("Midden rechts", 1f, 0.5f),
    UpperLeft("Links 75%", 0f, 0.75f),
    UpperQuarterLeft("25% / 75%", 0.25f, 0.75f),
    UpperCenter("Midden / 75%", 0.5f, 0.75f),
    UpperQuarterRight("75% / 75%", 0.75f, 0.75f),
    UpperRight("Rechts 75%", 1f, 0.75f),
    TopLeft("Linksboven", 0f, 1f),
    TopQuarterLeft("Boven 25%", 0.25f, 1f),
    TopCenter("Boven midden", 0.5f, 1f),
    TopQuarterRight("Boven 75%", 0.75f, 1f),
    TopRight("Rechtsboven", 1f, 1f)
}

data class TagPlacement(
    val plane: TagPlane,
    val anchor: TagAnchor,
    val positionMm: MmPosition,
    val rotationDeg: FloatVector
)

fun tagPlacementFor(
    plane: TagPlane,
    anchor: TagAnchor,
    dimensionsMm: MmPosition
): TagPlacement =
    TagPlacement(
        plane = plane,
        anchor = anchor,
        positionMm = tagPositionFor(plane, anchor, dimensionsMm),
        rotationDeg = tagRotationFor(plane)
    )

fun tagPositionFor(
    plane: TagPlane,
    anchor: TagAnchor,
    dimensionsMm: MmPosition
): MmPosition = tagPositionFor(plane, anchor.u, anchor.v, dimensionsMm)

/** Als [tagPositionFor] maar met een vrij rasterpunt [u],[v] (elk 0..1) op het vlak in plaats van
 *  een vast [TagAnchor]. Gebruikt door het fijnere N×N-raster en handmatige plaatsing; de vaste-anker
 *  variant delegeert hierheen zodat beide exact dezelfde conventie volgen. */
fun tagPositionFor(
    plane: TagPlane,
    u: Float,
    v: Float,
    dimensionsMm: MmPosition
): MmPosition {
    fun xFromU() = (dimensionsMm.x * u).roundToInt()
    fun xFromOutsideBackU() = (dimensionsMm.x * (1f - u)).roundToInt()
    fun yFromU() = (dimensionsMm.y * u).roundToInt()
    fun yFromOutsideLeftU() = (dimensionsMm.y * (1f - u)).roundToInt()
    fun yFromV() = (dimensionsMm.y * v).roundToInt()
    fun zFromV() = (dimensionsMm.z * v).roundToInt()
    return when (plane) {
        // Van buitenaf gezien: u=0 (UI-links) → X=0 (linker-hoek), net als Left/Right/Back/Top. Een
        // gespiegelde Front maakte de tag inconsistent met die vlakken én met de zijtags in een
        // multi-tag solve; hou daarom dezelfde niet-gespiegelde conventie aan.
        TagPlane.Front -> MmPosition(x = xFromU(), y = 0, z = zFromV())
        TagPlane.Back -> MmPosition(x = xFromOutsideBackU(), y = dimensionsMm.y, z = zFromV())
        TagPlane.Left -> MmPosition(x = 0, y = yFromOutsideLeftU(), z = zFromV())
        TagPlane.Right -> MmPosition(x = dimensionsMm.x, y = yFromU(), z = zFromV())
        TagPlane.Top -> MmPosition(x = xFromU(), y = yFromV(), z = dimensionsMm.z)
    }
}

/** Tagrotatie per vlak. De marker-corners moeten op hun ECHTE box-positie staan, gepaard met de
 *  detectiehoeken in geprinte volgorde (genormaliseerd in AprilTagDetector). Een leesbare front-tag
 *  (van buiten bekeken: printed-up = +Z, printed-right = +X) hoort dus bij Rz=0; Rz=180 zette de
 *  geprinte hoeken op de GESPIEGELDE box-X en gaf solvePnP een links/rechts-omgedraaide pose.
 *  Top idem: een leesbare top-tag (front-tag plat op het deksel, bovenrand naar achter) hoort bij
 *  Rx=-90; het oude Rx=+90 spiegelde de hoeken in Y (voor/achter) → pose voor/achter omgedraaid.
 *  De gecorrigeerde vlakken hebben hun winding-normaal naar BINNEN; dat is prima voor solvePnP en
 *  is juist de fix. GEVERIFIEERD met ARSensFrameCheck: Front, Top. Back/Left/Right staan nog op de
 *  oude waarden en worden pas per vlak omgezet ná verificatie. */
fun tagRotationFor(plane: TagPlane): FloatVector =
    when (plane) {
        TagPlane.Front -> FloatVector(0f, 0f, 0f)
        TagPlane.Back -> FloatVector(0f, 0f, 0f)
        TagPlane.Left -> FloatVector(0f, 0f, 90f)
        TagPlane.Right -> FloatVector(0f, 0f, -90f)
        TagPlane.Top -> FloatVector(-90f, 0f, 0f)
    }
