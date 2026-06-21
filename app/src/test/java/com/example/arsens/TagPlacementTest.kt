package com.example.arsens

import com.example.arsens.ar.TagAnchor
import com.example.arsens.ar.TagMeasurementAnchor
import com.example.arsens.ar.TagPlane
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
    fun bottomLeftEdgeMeasurementMapsToCenterOffsetByHalfTag() {
        // sizeMm=100 → half=50; gemeten = buitenste hoek linksonder → center +50 langs U én V.
        val dims = MmPosition(1_640, 930, 940)
        val size = 100
        fun edge(plane: TagPlane, measured: MmPosition) =
            tagMeasuredPointToCenter(measured, plane, size, TagMeasurementAnchor.BottomLeftEdge, dims)

        assertEquals(MmPosition(50, 0, 50), edge(TagPlane.Front, MmPosition(0, 0, 0)))
        assertEquals(MmPosition(1_640, 50, 50), edge(TagPlane.Right, MmPosition(1_640, 0, 0)))
        assertEquals(MmPosition(0, 50, 50), edge(TagPlane.Left, MmPosition(0, 0, 0)))
        assertEquals(MmPosition(50, 50, 940), edge(TagPlane.Top, MmPosition(0, 0, 940)))
        assertEquals(MmPosition(50, 930, 50), edge(TagPlane.Back, MmPosition(0, 930, 0)))
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
    fun edgeMeasurementClampsCenterIntoTransformerBounds() {
        // Tag in de uiterste hoek: het +half center valt buiten de box → geklemd op de rand.
        val dims = MmPosition(1_640, 930, 940)
        assertEquals(
            MmPosition(1_640, 0, 940),
            tagMeasuredPointToCenter(MmPosition(1_640, 0, 940), TagPlane.Front, 100, TagMeasurementAnchor.BottomLeftEdge, dims)
        )
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
