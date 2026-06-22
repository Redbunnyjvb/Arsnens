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
 *  [Marker.positionMm] blijft intern altijd het CENTER (zie [tagMeasuredPointToCenter]).
 *
 *  [du]/[dv] zijn de OPERATOR-VIEW fracties van het gemeten punt op de tag (de operator staat buiten
 *  het gekozen vlak en kijkt er recht op): du 0=scherm-links .. 1=scherm-rechts, dv 0=onder .. 1=boven.
 *  Op Achter en Links is scherm-links de canonieke +as; die spiegeling zit in [tagMeasuredPointToCenter]
 *  via [tagPlaneMirrorsOperatorU], net als bij het 7×7-raster en de 2D-kaart. */
enum class TagMeasurementAnchor(val label: String, val du: Float, val dv: Float) {
    /** De ingevoerde X/Y/Z is het midden van de tag (oud gedrag, default). */
    Center("Midden", 0.5f, 0.5f),
    BottomLeft("Linksonder", 0f, 0f),
    BottomMiddle("Onder midden", 0.5f, 0f),
    BottomRight("Rechtsonder", 1f, 0f),
    LeftMiddle("Links midden", 0f, 0.5f),
    RightMiddle("Rechts midden", 1f, 0.5f),
    TopLeft("Linksboven", 0f, 1f),
    TopMiddle("Boven midden", 0.5f, 1f),
    TopRight("Rechtsboven", 1f, 1f)
}

/** Hoe een in-vlak as in de box-rand-offset-modus bepaald wordt: gemeten vanaf de min-rand, de
 *  max-rand, of een handmatige in-vlak coördinaat. Zie [tagEdgeOffsetToCenter]. */
enum class TagAxisReference(val label: String) {
    FromMin("Vanaf min"),
    FromMax("Vanaf max"),
    Manual("Handmatig")
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

/** Of het gekozen vlak de operator-view horizontale as (U) spiegelt t.o.v. de canonieke box-U. De
 *  operator staat buiten het vlak en kijkt er recht op; op Achter en Links is scherm-links de canonieke
 *  +as (X-max resp. Y-max). IDENTIEK aan de 2D-kaart (ExtendedScreens.toBoxPosition/toMapMeasurePoint)
 *  en aan [tagGridDisplayUvToCanonicalUv]/[tagMeasuredPointToCenter]. V (verticaal) spiegelt nooit.
 *  Dit is puur een INVOER/DISPLAY-laag: [tagPositionFor] blijft canoniek en de AR-pose-keten ongemoeid. */
fun tagPlaneMirrorsOperatorU(plane: TagPlane): Boolean = when (plane) {
    TagPlane.Back, TagPlane.Left -> true
    TagPlane.Front, TagPlane.Right, TagPlane.Top -> false
}

/** Operator-view rastercel ([displayU]/[displayV] elk 0..1; displayU = scherm-links→rechts,
 *  displayV = onder→boven) → canonieke box-frame u/v voor [tagPositionFor]. Zo plaatst het 7×7-raster
 *  alsof de operator vóór het gekozen vlak staat, gelijk aan de 2D-kaart. Front/Rechts/Boven 1-op-1;
 *  Achter/Links spiegelen U. [tagPositionFor] zelf blijft puur canoniek. */
fun tagGridDisplayUvToCanonicalUv(plane: TagPlane, displayU: Float, displayV: Float): Pair<Float, Float> {
    val canonicalU = if (tagPlaneMirrorsOperatorU(plane)) 1f - displayU else displayU
    return canonicalU to displayV
}

/** Maximale mm langs de eerste vrije vlak-as (U) — Front/Back/Top: X (lengte), Left/Right: Y (diepte). */
fun tagPlaneUMaxMm(plane: TagPlane, dimensionsMm: MmPosition): Int = when (plane) {
    TagPlane.Front, TagPlane.Back, TagPlane.Top -> dimensionsMm.x
    TagPlane.Left, TagPlane.Right -> dimensionsMm.y
}

/** Maximale mm langs de tweede vrije vlak-as (V) — Front/Back/Left/Right: Z (hoogte), Top: Y (diepte). */
fun tagPlaneVMaxMm(plane: TagPlane, dimensionsMm: MmPosition): Int = when (plane) {
    TagPlane.Front, TagPlane.Back, TagPlane.Left, TagPlane.Right -> dimensionsMm.z
    TagPlane.Top -> dimensionsMm.y
}

private fun tagPlaneUComponent(plane: TagPlane, p: MmPosition): Int = when (plane) {
    TagPlane.Front, TagPlane.Back, TagPlane.Top -> p.x
    TagPlane.Left, TagPlane.Right -> p.y
}

private fun tagPlaneVComponent(plane: TagPlane, p: MmPosition): Int = when (plane) {
    TagPlane.Front, TagPlane.Back, TagPlane.Left, TagPlane.Right -> p.z
    TagPlane.Top -> p.y
}

/** Bouwt een tag-CENTER (canonieke box-mm, EXACT op het vlak) uit in-vlak coördinaten [uMm],[vMm]
 *  langs de vlak-assen. De in-vlak coördinaten worden in de box-grenzen geklemd; het vaste
 *  vlak-component staat exact op 0 of de maximale dimensie. De buitenwaartse offset (langs de normaal)
 *  hoort NIET hier maar in [applyTagOutwardOffset], zodat die niet weg-geklemd wordt. */
private fun tagCenterOnPlane(plane: TagPlane, uMm: Int, vMm: Int, dimensionsMm: MmPosition): MmPosition {
    val u = uMm.coerceIn(0, tagPlaneUMaxMm(plane, dimensionsMm))
    val v = vMm.coerceIn(0, tagPlaneVMaxMm(plane, dimensionsMm))
    return when (plane) {
        TagPlane.Front -> MmPosition(x = u, y = 0, z = v)
        TagPlane.Back -> MmPosition(x = u, y = dimensionsMm.y, z = v)
        TagPlane.Left -> MmPosition(x = 0, y = u, z = v)
        TagPlane.Right -> MmPosition(x = dimensionsMm.x, y = u, z = v)
        TagPlane.Top -> MmPosition(x = u, y = v, z = dimensionsMm.z)
    }
}

private fun signOf(value: Float): Int = if (value > 0f) 1 else if (value < 0f) -1 else 0

/**
 * Zet een door de operator GEMETEN punt op een tagvlak om naar het tag-CENTER dat in
 * [Marker.positionMm] hoort. [markerCornersInProjectFrame] blijft het center ± sizeMm/2 gebruiken; deze
 * helper verschuift dus enkel het meetpunt → center vóór opslaan en raakt de corner-generatie niet.
 *
 * Canoniek frame (origin voor-links-onder; X+ rechts, Y+ naar binnen, Z+ omhoog). Vlak-assen:
 * Front/Back U=+X, V=+Z; Left/Right U=+Y, V=+Z; Top U=+X, V=+Y.
 *
 * [anchor] is OPERATOR-VIEW: (du,dv) geeft waar op de tag de operator mat, met scherm-links=0. De
 * verschuiving naar het center is (0.5−du)·size langs U en (0.5−dv)·size langs V; op Achter/Links
 * spiegelt de U-richting ([tagPlaneMirrorsOperatorU]) zodat "linksonder" altijd de scherm-linker-onder
 * hoek is — consistent met het 7×7-raster en de 2D-kaart. [TagMeasurementAnchor.Center] laat de in-vlak
 * coördinaten staan (du=dv=0.5 → geen verschuiving).
 *
 * [paperMarginUMm]/[paperMarginVMm]: optionele extra verschuiving naar binnen langs de rand(en) waar het
 * anker op ligt — voor meten tot de papier-rand i.p.v. de tag-rand. Op een gecentreerde as (anker du of
 * dv = 0.5) is er geen richting en wordt de marge daar genegeerd.
 *
 * Het vaste vlak-component wordt exact op het vlak gezet (Front Y=0, Right X=lengte, …) en de in-vlak
 * coördinaten in de trafo-grenzen geklemd. Een buitenwaartse offset hoort in [applyTagOutwardOffset].
 */
fun tagMeasuredPointToCenter(
    measured: MmPosition,
    plane: TagPlane,
    sizeMm: Int,
    anchor: TagMeasurementAnchor,
    dimensionsMm: MmPosition,
    paperMarginUMm: Int = 0,
    paperMarginVMm: Int = 0
): MmPosition {
    val mirror = if (tagPlaneMirrorsOperatorU(plane)) -1f else 1f
    val corrU = mirror * (0.5f - anchor.du) * sizeMm
    val corrV = (0.5f - anchor.dv) * sizeMm
    val marginU = signOf(corrU) * paperMarginUMm
    val marginV = signOf(corrV) * paperMarginVMm
    val centerU = tagPlaneUComponent(plane, measured) + (corrU).roundToInt() + marginU
    val centerV = tagPlaneVComponent(plane, measured) + (corrV).roundToInt() + marginV
    return tagCenterOnPlane(plane, centerU, centerV, dimensionsMm)
}

/**
 * "Meet vanaf rand": bepaalt een PUNT op het vlak uit een referentie + afstand per vrije vlak-as,
 * i.p.v. een vrij gemeten X/Y/Z. Per as (U = eerste vrije box-as, V = tweede):
 * [TagAxisReference.FromMin] → coördinaat = waarde (vanaf 0), [TagAxisReference.FromMax] → coördinaat =
 * dimensie − waarde, [TagAxisReference.Manual] → coördinaat = waarde. Canonieke box-assen (de UI labelt
 * de min/max-kant per vlak fysiek). Resultaat exact op het vlak en in-vlak geklemd.
 *
 * Dit is het GEMETEN punt: bij meten-tot-tagmidden is dat meteen het center, maar voor een tag-/papier-
 * hoek chain je het resultaat door [tagMeasuredPointToCenter] (anker + marge) en daarna
 * [applyTagOutwardOffset]. Zo deelt "Meet vanaf rand" exact dezelfde correctie als de anker-modus.
 */
fun tagEdgeOffsetToCenter(
    plane: TagPlane,
    uReference: TagAxisReference,
    uValueMm: Int,
    vReference: TagAxisReference,
    vValueMm: Int,
    dimensionsMm: MmPosition
): MmPosition {
    val uMm = resolveAxisCoord(uReference, uValueMm, tagPlaneUMaxMm(plane, dimensionsMm))
    val vMm = resolveAxisCoord(vReference, vValueMm, tagPlaneVMaxMm(plane, dimensionsMm))
    return tagCenterOnPlane(plane, uMm, vMm, dimensionsMm)
}

private fun resolveAxisCoord(reference: TagAxisReference, valueMm: Int, maxMm: Int): Int =
    when (reference) {
        TagAxisReference.FromMin -> valueMm
        TagAxisReference.FromMax -> maxMm - valueMm
        TagAxisReference.Manual -> valueMm
    }

/**
 * Verschuift een tag-CENTER langs de BUITENWAARTSE vlak-normaal met [outwardOffsetMm] (mag negatief):
 * Front −Y, Back +Y, Links −X, Rechts +X, Boven +Z. Voor tags op een rib, pijp of dik papier die bewust
 * buiten het nominale vlak liggen. Wordt NIET in de box geklemd (audit punt 10) — de in-vlak coördinaten
 * blijven wat ze waren; alleen het normaal-component verschuift. markerCornersInProjectFrame schuift dan
 * de hele tag-rechthoek netjes mee naar buiten.
 */
fun applyTagOutwardOffset(center: MmPosition, plane: TagPlane, outwardOffsetMm: Int): MmPosition =
    when (plane) {
        TagPlane.Front -> MmPosition(center.x, center.y - outwardOffsetMm, center.z)
        TagPlane.Back -> MmPosition(center.x, center.y + outwardOffsetMm, center.z)
        TagPlane.Left -> MmPosition(center.x - outwardOffsetMm, center.y, center.z)
        TagPlane.Right -> MmPosition(center.x + outwardOffsetMm, center.y, center.z)
        TagPlane.Top -> MmPosition(center.x, center.y, center.z + outwardOffsetMm)
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
