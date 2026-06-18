package com.example.arsens

import com.example.arsens.ar.TagPlane
import com.example.arsens.ar.tagRotationFor
import com.example.arsens.data.Marker
import com.example.arsens.data.MmPosition
import com.example.arsens.ui.markerSurfaceLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Het vlaklabel van een tag moet het ECHTE vlak volgen, ook als de tag op een gedeelde rand ligt.
 * Een Links/Rechts-tag op de achterrand (y == diepte) werd voorheen als "Achter" gelabeld omdat de
 * positie-precedentie y==diepte vóór x==0/x==lengte checkte — de rotatie maakt het nu eenduidig.
 */
class MarkerSurfaceLabelTest {
    private val dimensions = MmPosition(10_000, 5_000, 3_200)

    private fun tag(position: MmPosition, plane: TagPlane) = Marker(
        id = 1,
        type = "apriltag",
        sizeMm = 100,
        positionMm = position,
        rotationDeg = tagRotationFor(plane)
    )

    @Test
    fun sideTagOnBackEdgeIsLabelledBySideNotBack() {
        assertEquals("Links", markerSurfaceLabel(tag(MmPosition(0, 5_000, 1_600), TagPlane.Left), dimensions))
        assertEquals("Rechts", markerSurfaceLabel(tag(MmPosition(10_000, 5_000, 1_600), TagPlane.Right), dimensions))
    }

    @Test
    fun sideTagInMiddleStillLabelledBySide() {
        assertEquals("Links", markerSurfaceLabel(tag(MmPosition(0, 2_500, 1_600), TagPlane.Left), dimensions))
        assertEquals("Rechts", markerSurfaceLabel(tag(MmPosition(10_000, 2_500, 1_600), TagPlane.Right), dimensions))
    }

    @Test
    fun frontBackTopUseTheirOwnFace() {
        assertEquals("Voor", markerSurfaceLabel(tag(MmPosition(2_000, 0, 1_600), TagPlane.Front), dimensions))
        assertEquals("Achter", markerSurfaceLabel(tag(MmPosition(2_000, 5_000, 1_600), TagPlane.Back), dimensions))
        assertEquals("Boven", markerSurfaceLabel(tag(MmPosition(2_000, 2_500, 3_200), TagPlane.Top), dimensions))
    }
}
