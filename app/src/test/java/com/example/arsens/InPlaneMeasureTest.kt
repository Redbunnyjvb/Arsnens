package com.example.arsens

import com.example.arsens.ui.MeasureAxis
import com.example.arsens.ui.StlScenePoint
import com.example.arsens.ui.StlViewMode
import com.example.arsens.ui.TransformerMapView
import com.example.arsens.ui.horizontalAxisLabel
import com.example.arsens.ui.inPlaneAxes
import com.example.arsens.ui.stlMeasureDistanceMm
import com.example.arsens.ui.verticalAxisLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * De in-het-vlak meting (punt 2): in een vast aanzicht telt alleen de XY-in-het-vlak mee — de
 * diepte-as van dat vlak valt eruit. De directe afstand is dan de schuine zijde (Pythagoras over
 * twee assen); in de vrije 3D-view blijft het de volle 3D-afstand. De 2D-aslabels (punt 1) moeten
 * met dezelfde box-as overeenkomen.
 */
class InPlaneMeasureTest {

    private fun p(x: Float, y: Float, z: Float) =
        StlScenePoint(x = x, y = y, z = z, argb = 0, square = false, label = "")

    @Test
    fun topViewMeasuresXyAndDropsDepthZ() {
        val axes = StlViewMode.Top.inPlaneAxes()
        assertEquals(listOf(MeasureAxis.X, MeasureAxis.Y), axes)
        // Z verschilt sterk maar telt niet mee → klassieke 3-4-5 in het vlak.
        assertEquals(500f, stlMeasureDistanceMm(p(0f, 0f, 0f), p(300f, 400f, 9999f), axes), 1e-3f)
    }

    @Test
    fun frontViewMeasuresXzAndDropsDepthY() {
        val axes = StlViewMode.Front.inPlaneAxes()
        assertEquals(listOf(MeasureAxis.X, MeasureAxis.Z), axes)
        assertEquals(500f, stlMeasureDistanceMm(p(0f, 9999f, 0f), p(300f, 0f, 400f), axes), 1e-3f)
    }

    @Test
    fun leftViewMeasuresYzAndDropsDepthX() {
        val axes = StlViewMode.Left.inPlaneAxes()
        assertEquals(listOf(MeasureAxis.Y, MeasureAxis.Z), axes)
        assertEquals(500f, stlMeasureDistanceMm(p(9999f, 0f, 0f), p(0f, 300f, 400f), axes), 1e-3f)
    }

    @Test
    fun freeViewKeepsFull3dDistance() {
        val axes = StlViewMode.Free.inPlaneAxes()
        assertEquals(listOf(MeasureAxis.X, MeasureAxis.Y, MeasureAxis.Z), axes)
        // Ruimtelijke diagonaal 300·400·1200 → 1300.
        assertEquals(1300f, stlMeasureDistanceMm(p(0f, 0f, 0f), p(300f, 400f, 1200f), axes), 1e-3f)
    }

    @Test
    fun twoArgDistanceStaysFull3d() {
        assertEquals(1300f, stlMeasureDistanceMm(p(0f, 0f, 0f), p(300f, 400f, 1200f)), 1e-3f)
    }

    @Test
    fun twoDAxisLabelsMatchTheViewPlane() {
        assertEquals("X", TransformerMapView.Top.horizontalAxisLabel())
        assertEquals("Y", TransformerMapView.Top.verticalAxisLabel())
        assertEquals("X", TransformerMapView.Front.horizontalAxisLabel())
        assertEquals("Z", TransformerMapView.Front.verticalAxisLabel())
        assertEquals("X", TransformerMapView.Back.horizontalAxisLabel())
        assertEquals("Z", TransformerMapView.Back.verticalAxisLabel())
        assertEquals("Y", TransformerMapView.Left.horizontalAxisLabel())
        assertEquals("Z", TransformerMapView.Left.verticalAxisLabel())
        assertEquals("Y", TransformerMapView.Right.horizontalAxisLabel())
        assertEquals("Z", TransformerMapView.Right.verticalAxisLabel())
    }
}
