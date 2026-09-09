package com.example.arsens

import com.example.arsens.data.*
import com.example.arsens.ui.*
import org.junit.Assert.assertEquals
import org.junit.Test

class MapSelectionIdentityTest {
    @Test fun sensorNamedLikeATagRemainsASensor() {
        val project = Project(projectName = "Test", modelFile = "", dimensionsMm = MmPosition(1000, 1000, 1000),
            markers = listOf(Marker(0, "apriltag", 100, MmPosition(100, 100, 1000), FloatVector(-90f, 0f, 0f))),
            sensors = listOf(Sensor(1, "T0", "Temperatuur tank", "Top", MmPosition(600, 600, 1000),
                toleranceMm = 50, instruction = "")))
        val sensorPoint = selectableMapPoints(project, TransformerMapView.Top).first { it.horizontalMm == 600.0 }
        assertEquals(MapMoveTarget.Sensor("T0"), sensorPoint.toMoveTarget(project))
    }
}
