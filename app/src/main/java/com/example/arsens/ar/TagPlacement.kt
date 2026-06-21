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

/** Welk punt van de geprinte tag de operator op [MmPosition] gemeten heeft. Puur een INVOER-conventie:
 *  [Marker.positionMm] blijft intern altijd het CENTER (zie [tagMeasuredPointToCenter]). */
enum class TagMeasurementAnchor(val label: String) {
    /** De ingevoerde X/Y/Z is het midden van de tag (oud gedrag, default). */
    Center("Midden"),
    /** De ingevoerde X/Y/Z is de buitenste hoek linksonder van de tag (in vlak-assen U/V). */
    BottomLeftEdge("Linksonder rand")
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
    // u/v zijn ALTIJD canonieke box-frame assen, NIET operator-view per vlak. Eén canoniek
    // transformerframe: origin = voor-links-onder (van buiten vóór de trafo gezien), X+ naar rechts,
    // Y+ naar binnen/achter, Z+ omhoog. Op ELK vlak loopt u langs de eerste vrije box-as in +richting
    // en v langs de tweede; geen enkel vlak spiegelt u (de oude xFromOutsideBackU/yFromOutsideLeftU
    // voor Back/Left maakten die twee vlakken inconsistent met Front/Right/Top en met elkaar in een
    // multi-tag solve). Het vaste-vlak-component staat exact op 0 of op de maximale dimensie.
    fun xFromU() = (dimensionsMm.x * u).roundToInt()
    fun yFromU() = (dimensionsMm.y * u).roundToInt()
    fun yFromV() = (dimensionsMm.y * v).roundToInt()
    fun zFromV() = (dimensionsMm.z * v).roundToInt()
    return when (plane) {
        TagPlane.Front -> MmPosition(x = xFromU(), y = 0, z = zFromV())
        TagPlane.Back -> MmPosition(x = xFromU(), y = dimensionsMm.y, z = zFromV())
        TagPlane.Left -> MmPosition(x = 0, y = yFromU(), z = zFromV())
        TagPlane.Right -> MmPosition(x = dimensionsMm.x, y = yFromU(), z = zFromV())
        TagPlane.Top -> MmPosition(x = xFromU(), y = yFromV(), z = dimensionsMm.z)
    }
}

/**
 * Zet een door de operator GEMETEN punt op een tagvlak om naar het tag-CENTER dat in
 * [Marker.positionMm] hoort. [markerCornersInProjectFrame] blijft het center ± sizeMm/2 gebruiken; deze
 * helper verschuift dus enkel het meetpunt → center vóór opslaan en raakt de corner-generatie niet.
 *
 * Canoniek frame (origin voor-links-onder; X+ rechts, Y+ naar binnen, Z+ omhoog). Vlak-assen:
 * Front/Back U=+X, V=+Z; Left/Right U=+Y, V=+Z; Top U=+X, V=+Y.
 * [TagMeasurementAnchor.BottomLeftEdge]: de operator mat de buitenste hoek linksonder, dus het center
 * ligt +half langs U én +half langs V naar binnen (half = sizeMm/2). [TagMeasurementAnchor.Center]
 * laat de in-vlak-coördinaten staan. In beide gevallen wordt het vaste vlak-component exact op het
 * vlak gezet (Front Y=0, Right X=lengte, …) en het resultaat in de trafo-grenzen geklemd.
 */
fun tagMeasuredPointToCenter(
    measured: MmPosition,
    plane: TagPlane,
    sizeMm: Int,
    anchor: TagMeasurementAnchor,
    dimensionsMm: MmPosition
): MmPosition {
    val half = if (anchor == TagMeasurementAnchor.BottomLeftEdge) sizeMm / 2 else 0
    val center = when (plane) {
        TagPlane.Front -> MmPosition(x = measured.x + half, y = 0, z = measured.z + half)
        TagPlane.Back -> MmPosition(x = measured.x + half, y = dimensionsMm.y, z = measured.z + half)
        TagPlane.Left -> MmPosition(x = 0, y = measured.y + half, z = measured.z + half)
        TagPlane.Right -> MmPosition(x = dimensionsMm.x, y = measured.y + half, z = measured.z + half)
        TagPlane.Top -> MmPosition(x = measured.x + half, y = measured.y + half, z = dimensionsMm.z)
    }
    return MmPosition(
        x = center.x.coerceIn(0, dimensionsMm.x),
        y = center.y.coerceIn(0, dimensionsMm.y),
        z = center.z.coerceIn(0, dimensionsMm.z)
    )
}

/** Tagrotatie per vlak. De marker-corners moeten op hun ECHTE box-positie staan, gepaard met de
 *  detectiehoeken in geprinte volgorde (genormaliseerd in AprilTagDetector). Een leesbare front-tag
 *  (van buiten bekeken: printed-up = +Z, printed-right = +X) hoort dus bij Rz=0; Rz=180 zette de
 *  geprinte hoeken op de GESPIEGELDE box-X en gaf solvePnP een links/rechts-omgedraaide pose.
 *  Top idem: een leesbare top-tag (front-tag plat op het deksel, bovenrand naar achter) hoort bij
 *  Rx=-90; het oude Rx=+90 spiegelde de hoeken in Y (voor/achter) → pose voor/achter omgedraaid.
 *  De gecorrigeerde vlakken hebben hun winding-normaal naar BINNEN; dat is prima voor solvePnP en
 *  is juist de fix.
 *
 *  Zijvlakken (Left X=0, Right X=lengte) liggen in het Y-Z-vlak. Een van buitenaf leesbare zijtag
 *  (printed-up = +Z) hoort printed-right te krijgen naar de kijker-rechts: op Left (kijker kijkt +X)
 *  is dat -Y → Rz=-90; op Right (kijker kijkt -X) is dat +Y → Rz=+90. De oude Left=+90/Right=-90
 *  draaiden printed-right naar de andere kant → links/rechts-gespiegelde pose op de zijvlakken.
 *
 *  Back: een van-buiten leesbare achtertag lees je vanaf de TEGENOVERGESTELDE kant als de fronttag,
 *  dus hij staat 180° om de verticale as gedraaid → Rz=180. De kijker achter de box kijkt naar -Y;
 *  zijn rechts is -X, dus printed-right hoort naar -X (Rz=180 levert dat). Het oude Back=Rz=0 (gelijk
 *  aan Front) gaf printed-right=+X = kijker-LINKS → links/rechts-gespiegelde back-pose. Met Rz=180
 *  wijst de winding-normaal naar BINNEN (-Y), net als alle andere gecorrigeerde vlakken. */
fun tagRotationFor(plane: TagPlane): FloatVector =
    when (plane) {
        TagPlane.Front -> FloatVector(0f, 0f, 0f)
        TagPlane.Back -> FloatVector(0f, 0f, 180f)
        TagPlane.Left -> FloatVector(0f, 0f, -90f)
        TagPlane.Right -> FloatVector(0f, 0f, 90f)
        TagPlane.Top -> FloatVector(-90f, 0f, 0f)
    }
