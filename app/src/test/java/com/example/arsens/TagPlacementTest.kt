package com.example.arsens

import com.example.arsens.ar.TagAnchor
import com.example.arsens.ar.TagPlane
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
        assertEquals(MmPosition(10_000, 0, 3_200), tagPositionFor(TagPlane.Front, TagAnchor.TopRight, dimensions))
        assertEquals(MmPosition(0, 5_000, 3_200), tagPositionFor(TagPlane.Back, TagAnchor.TopRight, dimensions))
        assertEquals(MmPosition(0, 0, 3_200), tagPositionFor(TagPlane.Left, TagAnchor.TopRight, dimensions))
        assertEquals(MmPosition(10_000, 5_000, 3_200), tagPositionFor(TagPlane.Top, TagAnchor.TopRight, dimensions))
    }

    @Test
    fun sideAndBackPlaneMiddleLeftAndMiddleRightUseOutsideView() {
        assertEquals(MmPosition(10_000, 5_000, 1_600), tagPositionFor(TagPlane.Back, TagAnchor.MiddleLeft, dimensions))
        assertEquals(MmPosition(0, 5_000, 1_600), tagPositionFor(TagPlane.Back, TagAnchor.MiddleRight, dimensions))
        assertEquals(MmPosition(0, 5_000, 1_600), tagPositionFor(TagPlane.Left, TagAnchor.MiddleLeft, dimensions))
        assertEquals(MmPosition(0, 0, 1_600), tagPositionFor(TagPlane.Left, TagAnchor.MiddleRight, dimensions))
        assertEquals(MmPosition(10_000, 0, 1_600), tagPositionFor(TagPlane.Right, TagAnchor.MiddleLeft, dimensions))
        assertEquals(MmPosition(10_000, 5_000, 1_600), tagPositionFor(TagPlane.Right, TagAnchor.MiddleRight, dimensions))
    }

    @Test
    fun topPlaneLeftAndRightMatchTopView() {
        assertEquals(MmPosition(0, 2_500, 3_200), tagPositionFor(TagPlane.Top, TagAnchor.MiddleLeft, dimensions))
        assertEquals(MmPosition(10_000, 2_500, 3_200), tagPositionFor(TagPlane.Top, TagAnchor.MiddleRight, dimensions))
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
