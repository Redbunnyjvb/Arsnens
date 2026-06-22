package com.example.arsens

import com.example.arsens.ar.TagAnchor
import com.example.arsens.ar.TagAxisReference
import com.example.arsens.ar.TagMeasurementAnchor
import com.example.arsens.ar.TagPlane
import com.example.arsens.ar.applyTagOutwardOffset
import com.example.arsens.ar.tagEdgeOffsetToCenter
import com.example.arsens.ar.tagGridDisplayUvToCanonicalUv
import com.example.arsens.ar.tagMeasuredPointToCenter
import com.example.arsens.ar.tagPositionFor
import com.example.arsens.ar.tagRotationFor
import com.example.arsens.data.MmPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class TagPlacementTest {
    private val dimensions = MmPosition(10_000, 5_000, 3_200)

    @Test
    fun centerAnchorMapsToEveryTransformerPlane() {
        assertEquals(MmPosition(5_000, 0, 1_600), tagPositionFor(TagPlane.Front, TagAnchor.Center, dimensions))
        assertEquals(MmPosition(5_000, 5_000, 1_600), tagPositionFor(TagPlane.Back, TagAnchor.Center, dimensions))
        assertEquals(MmPosition(0, 2_500, 1_600), tagPositionFor(TagPlane.Left, TagAnchor.Center, dimensions))
        assertEquals(MmPosition(10_000, 2_500, 1_600), tagPositionFor(TagPlane.Right, TagAnchor.Center, dimensions))
        assertEquals(MmPosition(5_000, 2_500, 3_200), tagPositionFor(TagPlane.Top, TagAnchor.Center, dimensions))
    }

    @Test
    fun frontPlaneLeftAnchorsMapToBoxLeftFromOutsideView() {
        listOf(
            TagAnchor.BottomLeft to 0,
            TagAnchor.LowerLeft to 800,
            TagAnchor.MiddleLeft to 1_600,
            TagAnchor.UpperLeft to 2_400,
            TagAnchor.TopLeft to 3_200
        ).forEach { (anchor, expectedZ) ->
            assertEquals(
                MmPosition(0, 0, expectedZ),
                tagPositionFor(TagPlane.Front, anchor, dimensions)
            )
        }
    }

    @Test
    fun frontPlaneRightAnchorsMapToBoxRightFromOutsideView() {
        // Niet-gespiegelde Front: UI-rechts (u=1) → box X=lengte, gelijk aan de van-buiten conventie.
        listOf(
            TagAnchor.BottomRight to 0,
            TagAnchor.LowerRight to 800,
            TagAnchor.MiddleRight to 1_600,
            TagAnchor.UpperRight to 2_400,
            TagAnchor.TopRight to 3_200
        ).forEach { (anchor, expectedZ) ->
            assertEquals(
                MmPosition(10_000, 0, expectedZ),
                tagPositionFor(TagPlane.Front, anchor, dimensions)
            )
        }
    }

    @Test
    fun frontPlaneCenterStaysCenteredAndVerticalAxisIsUnchanged() {
        assertEquals(MmPosition(5_000, 0, 1_600), tagPositionFor(TagPlane.Front, TagAnchor.Center, dimensions))
        // UI-links (u=0) → box X=0 (niet-gespiegelde Front, gelijk aan Left/Right/Back/Top).
        assertEquals(MmPosition(0, 0, 0), tagPositionFor(TagPlane.Front, TagAnchor.BottomLeft, dimensions))
        assertEquals(MmPosition(0, 0, 3_200), tagPositionFor(TagPlane.Front, TagAnchor.TopLeft, dimensions))
    }

    @Test
    fun cornerAnchorUsesPlaneAxes() {
        // Canoniek (niet-gespiegeld) op ELK vlak: u=1 → +as. Back u=1 → X=lengte (was 0), Left u=1 →
        // Y=diepte (was 0).
        assertEquals(MmPosition(10_000, 0, 3_200), tagPositionFor(TagPlane.Front, TagAnchor.TopRight, dimensions))
        assertEquals(MmPosition(10_000, 5_000, 3_200), tagPositionFor(TagPlane.Back, TagAnchor.TopRight, dimensions))
        assertEquals(MmPosition(0, 5_000, 3_200), tagPositionFor(TagPlane.Left, TagAnchor.TopRight, dimensions))
        assertEquals(MmPosition(10_000, 5_000, 3_200), tagPositionFor(TagPlane.Top, TagAnchor.TopRight, dimensions))
    }

    @Test
    fun backLeftRightPlanesUseCanonicalUnmirroredU() {
        // Eén canoniek frame: u loopt op ELK vlak in +richting van de eerste vrije box-as. Back en
        // Left spiegelen u NIET meer (de oude xFromOutsideBackU/yFromOutsideLeftU zijn weg).
        assertEquals(MmPosition(0, 5_000, 1_600), tagPositionFor(TagPlane.Back, TagAnchor.MiddleLeft, dimensions))
        assertEquals(MmPosition(10_000, 5_000, 1_600), tagPositionFor(TagPlane.Back, TagAnchor.MiddleRight, dimensions))
        assertEquals(MmPosition(0, 0, 1_600), tagPositionFor(TagPlane.Left, TagAnchor.MiddleLeft, dimensions))
        assertEquals(MmPosition(0, 5_000, 1_600), tagPositionFor(TagPlane.Left, TagAnchor.MiddleRight, dimensions))
        assertEquals(MmPosition(10_000, 0, 1_600), tagPositionFor(TagPlane.Right, TagAnchor.MiddleLeft, dimensions))
        assertEquals(MmPosition(10_000, 5_000, 1_600), tagPositionFor(TagPlane.Right, TagAnchor.MiddleRight, dimensions))
    }

    @Test
    fun canonicalFrameLocksEveryPlaneCornerMapping() {
        // Canoniek transformerframe (origin = voor-links-onder; X+ rechts, Y+ naar binnen, Z+ omhoog).
        // Legt de exacte u/v→box-hoek mapping per vlak vast; mag niet wisselen op operator-standpunt.
        val dims = MmPosition(1_640, 930, 940)
        assertEquals(MmPosition(0, 0, 0), tagPositionFor(TagPlane.Front, 0f, 0f, dims))
        assertEquals(MmPosition(1_640, 0, 940), tagPositionFor(TagPlane.Front, 1f, 1f, dims))
        assertEquals(MmPosition(1_640, 0, 0), tagPositionFor(TagPlane.Right, 0f, 0f, dims))
        assertEquals(MmPosition(1_640, 930, 940), tagPositionFor(TagPlane.Right, 1f, 1f, dims))
        assertEquals(MmPosition(0, 0, 0), tagPositionFor(TagPlane.Left, 0f, 0f, dims))
        assertEquals(MmPosition(0, 930, 940), tagPositionFor(TagPlane.Left, 1f, 1f, dims))
        assertEquals(MmPosition(0, 930, 0), tagPositionFor(TagPlane.Back, 0f, 0f, dims))
        assertEquals(MmPosition(1_640, 930, 940), tagPositionFor(TagPlane.Back, 1f, 1f, dims))
        assertEquals(MmPosition(0, 0, 940), tagPositionFor(TagPlane.Top, 0f, 0f, dims))
        assertEquals(MmPosition(1_640, 930, 940), tagPositionFor(TagPlane.Top, 1f, 1f, dims))
    }

    @Test
    fun topPlaneLeftAndRightMatchTopView() {
        assertEquals(MmPosition(0, 2_500, 3_200), tagPositionFor(TagPlane.Top, TagAnchor.MiddleLeft, dimensions))
        assertEquals(MmPosition(10_000, 2_500, 3_200), tagPositionFor(TagPlane.Top, TagAnchor.MiddleRight, dimensions))
    }

    @Test
    fun bottomLeftAnchorMeasurementMapsToCenterOperatorView() {
        // sizeMm=100 → half=50. "Linksonder" is OPERATOR-VIEW: de operator-linker-onder hoek. Op
        // Front/Right/Top is scherm-links de canonieke −U, dus center = gemeten +50 langs U (en +50 V).
        // Op Back/Left spiegelt U (scherm-links = canonieke +U), dus center = gemeten −50 langs U.
        val dims = MmPosition(1_640, 930, 940)
        val size = 100
        fun bottomLeft(plane: TagPlane, measured: MmPosition) =
            tagMeasuredPointToCenter(measured, plane, size, TagMeasurementAnchor.BottomLeft, dims)

        // Niet-gespiegeld: operator-linksonder = canonieke min-U, min-V hoek.
        assertEquals(MmPosition(50, 0, 50), bottomLeft(TagPlane.Front, MmPosition(0, 0, 0)))
        assertEquals(MmPosition(1_640, 50, 50), bottomLeft(TagPlane.Right, MmPosition(1_640, 0, 0)))
        assertEquals(MmPosition(50, 50, 940), bottomLeft(TagPlane.Top, MmPosition(0, 0, 940)))
        // Gespiegeld (Back/Left): operator-links = canonieke MAX-U → gemeten op hoge X resp. Y, center −50.
        assertEquals(MmPosition(1_590, 930, 50), bottomLeft(TagPlane.Back, MmPosition(1_640, 930, 0)))
        assertEquals(MmPosition(0, 880, 50), bottomLeft(TagPlane.Left, MmPosition(0, 930, 0)))
    }

    @Test
    fun everyOperatorAnchorRecoversTheSameTagCenter() {
        // Audit acceptatietest 6: dezelfde fysieke tag, gemeten op vier verschillende hoeken/randen,
        // levert exact hetzelfde center. Front (niet-gespiegeld), size 100, center (500,0,300).
        val dims = MmPosition(1_640, 930, 940)
        val size = 100
        fun c(plane: TagPlane, measured: MmPosition, anchor: TagMeasurementAnchor) =
            tagMeasuredPointToCenter(measured, plane, size, anchor, dims)
        val front = MmPosition(500, 0, 300)
        assertEquals(front, c(TagPlane.Front, MmPosition(450, 0, 250), TagMeasurementAnchor.BottomLeft))
        assertEquals(front, c(TagPlane.Front, MmPosition(550, 0, 250), TagMeasurementAnchor.BottomRight))
        assertEquals(front, c(TagPlane.Front, MmPosition(450, 0, 350), TagMeasurementAnchor.TopLeft))
        assertEquals(front, c(TagPlane.Front, MmPosition(550, 0, 350), TagMeasurementAnchor.TopRight))
        assertEquals(front, c(TagPlane.Front, MmPosition(500, 0, 300), TagMeasurementAnchor.Center))
        // Back (gespiegeld): operator-links = canonieke +X. Center (500,930,300).
        val back = MmPosition(500, 930, 300)
        assertEquals(back, c(TagPlane.Back, MmPosition(550, 930, 250), TagMeasurementAnchor.BottomLeft))
        assertEquals(back, c(TagPlane.Back, MmPosition(450, 930, 250), TagMeasurementAnchor.BottomRight))
        assertEquals(back, c(TagPlane.Back, MmPosition(550, 930, 350), TagMeasurementAnchor.TopLeft))
        assertEquals(back, c(TagPlane.Back, MmPosition(450, 930, 350), TagMeasurementAnchor.TopRight))
    }

    @Test
    fun paperMarginExtendsInwardAlongAnchorEdgesOnly() {
        val dims = MmPosition(1_640, 930, 940)
        val size = 100
        // BottomLeft op Front: corrU=+50,corrV=+50 → marge +10/+5 in dezelfde (+U,+V) richting.
        assertEquals(
            MmPosition(510, 0, 305),
            tagMeasuredPointToCenter(MmPosition(450, 0, 250), TagPlane.Front, size, TagMeasurementAnchor.BottomLeft, dims, 10, 5)
        )
        // BottomMiddle: op U gecentreerd (corrU=0) → marge-U genegeerd; alleen V-marge telt.
        assertEquals(
            MmPosition(500, 0, 305),
            tagMeasuredPointToCenter(MmPosition(500, 0, 250), TagPlane.Front, size, TagMeasurementAnchor.BottomMiddle, dims, 10, 5)
        )
    }

    @Test
    fun centerMeasurementKeepsInPlaneAndSnapsFixedAxis() {
        // Center = oud gedrag: in-vlak coördinaten blijven staan; alleen het vaste vlak-component
        // wordt exact op het vlak gezet (geen halve-tag offset).
        val dims = MmPosition(1_640, 930, 940)
        val size = 100
        fun center(plane: TagPlane, measured: MmPosition) =
            tagMeasuredPointToCenter(measured, plane, size, TagMeasurementAnchor.Center, dims)

        assertEquals(MmPosition(500, 0, 300), center(TagPlane.Front, MmPosition(500, 7, 300)))
        assertEquals(MmPosition(1_640, 400, 300), center(TagPlane.Right, MmPosition(1_600, 400, 300)))
        assertEquals(MmPosition(800, 600, 940), center(TagPlane.Top, MmPosition(800, 600, 900)))
    }

    @Test
    fun anchorMeasurementClampsCenterIntoTransformerBounds() {
        // Tag in de uiterste hoek: het +half center valt buiten de box → in-vlak geklemd op de rand.
        val dims = MmPosition(1_640, 930, 940)
        assertEquals(
            MmPosition(1_640, 0, 940),
            tagMeasuredPointToCenter(MmPosition(1_640, 0, 940), TagPlane.Front, 100, TagMeasurementAnchor.BottomLeft, dims)
        )
    }

    @Test
    fun gridDisplayUvMirrorsBackAndLeftOnlyMatchingTheTwoDMap() {
        // Front/Right/Top 1-op-1; Back/Left spiegelen de horizontale (U) as; V nooit.
        assertEquals(0.2f to 0.7f, tagGridDisplayUvToCanonicalUv(TagPlane.Front, 0.2f, 0.7f))
        assertEquals(0.2f to 0.7f, tagGridDisplayUvToCanonicalUv(TagPlane.Right, 0.2f, 0.7f))
        assertEquals(0.2f to 0.7f, tagGridDisplayUvToCanonicalUv(TagPlane.Top, 0.2f, 0.7f))
        assertEquals(0.8f to 0.7f, tagGridDisplayUvToCanonicalUv(TagPlane.Back, 0.2f, 0.7f))
        assertEquals(0.8f to 0.7f, tagGridDisplayUvToCanonicalUv(TagPlane.Left, 0.2f, 0.7f))
    }

    @Test
    fun gridScreenLeftRightSavesPerOperatorView() {
        // Audit acceptatietests 1–5: scherm-links/rechts → de juiste box-rand per vlak (operator-view).
        fun cell(plane: TagPlane, displayU: Float): MmPosition {
            val (u, v) = tagGridDisplayUvToCanonicalUv(plane, displayU, 0.5f)
            return tagPositionFor(plane, u, v, dimensions)
        }
        // Front: links→X=0, rechts→X=max.
        assertEquals(0, cell(TagPlane.Front, 0f).x)
        assertEquals(10_000, cell(TagPlane.Front, 1f).x)
        // Back: links→X=max, rechts→X=0.
        assertEquals(10_000, cell(TagPlane.Back, 0f).x)
        assertEquals(0, cell(TagPlane.Back, 1f).x)
        // Left: links→Y=max (achter), rechts→Y=0 (voor).
        assertEquals(5_000, cell(TagPlane.Left, 0f).y)
        assertEquals(0, cell(TagPlane.Left, 1f).y)
        // Right: links→Y=0 (voor), rechts→Y=max (achter).
        assertEquals(0, cell(TagPlane.Right, 0f).y)
        assertEquals(5_000, cell(TagPlane.Right, 1f).y)
    }

    @Test
    fun edgeOffsetModeAddsFromMinSubtractsFromMaxAndPassesManual() {
        val dims = MmPosition(1_640, 930, 940)
        // Front U=X(max1640) V=Z(max940): vanaf min X=200, vanaf max Z=940−100=840, vast Y=0.
        assertEquals(
            MmPosition(200, 0, 840),
            tagEdgeOffsetToCenter(TagPlane.Front, TagAxisReference.FromMin, 200, TagAxisReference.FromMax, 100, dims)
        )
        // Left U=Y(max930) V=Z: vanaf max Y=930−300=630, vanaf min Z=200, vast X=0.
        assertEquals(
            MmPosition(0, 630, 200),
            tagEdgeOffsetToCenter(TagPlane.Left, TagAxisReference.FromMax, 300, TagAxisReference.FromMin, 200, dims)
        )
        // Right U=Y handmatig=400, V=Z handmatig=500, vast X=1640.
        assertEquals(
            MmPosition(1_640, 400, 500),
            tagEdgeOffsetToCenter(TagPlane.Right, TagAxisReference.Manual, 400, TagAxisReference.Manual, 500, dims)
        )
    }

    @Test
    fun outwardOffsetShiftsOnlyTheNormalAxisAndIsNotClamped() {
        // Buitenwaarts: Front −Y, Back +Y, Links −X, Rechts +X, Boven +Z. Mag buiten de box (rib/pijp).
        assertEquals(MmPosition(500, -30, 300), applyTagOutwardOffset(MmPosition(500, 0, 300), TagPlane.Front, 30))
        assertEquals(MmPosition(500, 960, 300), applyTagOutwardOffset(MmPosition(500, 930, 300), TagPlane.Back, 30))
        assertEquals(MmPosition(-30, 400, 300), applyTagOutwardOffset(MmPosition(0, 400, 300), TagPlane.Left, 30))
        assertEquals(MmPosition(1_670, 400, 300), applyTagOutwardOffset(MmPosition(1_640, 400, 300), TagPlane.Right, 30))
        assertEquals(MmPosition(500, 400, 970), applyTagOutwardOffset(MmPosition(500, 400, 940), TagPlane.Top, 30))
        // Negatief = naar binnen.
        assertEquals(MmPosition(500, 30, 300), applyTagOutwardOffset(MmPosition(500, 0, 300), TagPlane.Front, -30))
    }

    @Test
    fun rotationsAreCentralizedPerPlane() {
        // Front gecorrigeerd naar Rz=0 (geprinte hoeken op echte box-X; fix voor de gespiegelde AR-pose).
        assertEquals(0f, tagRotationFor(TagPlane.Front).z)
        // Back gecorrigeerd naar Rz=180 (achtertag wordt van de tegenovergestelde kant gelezen → 180°
        // om Front; het oude Rz=0 gaf een links/rechts-gespiegelde back-pose).
        assertEquals(180f, tagRotationFor(TagPlane.Back).z)
        // Zijvlakken gecorrigeerd: printed-right naar kijker-rechts (Left=-90 → -Y, Right=+90 → +Y);
        // de oude +90/-90 gaven een links/rechts-gespiegelde pose op de zijkanten.
        assertEquals(-90f, tagRotationFor(TagPlane.Left).z)
        assertEquals(90f, tagRotationFor(TagPlane.Right).z)
        // Top gecorrigeerd naar Rx=-90 (geprinte hoeken op echte box-Y; fix voor de voor/achter-flip).
        assertEquals(-90f, tagRotationFor(TagPlane.Top).x)
    }
}
