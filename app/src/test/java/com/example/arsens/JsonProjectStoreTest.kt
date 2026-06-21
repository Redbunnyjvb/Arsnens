package com.example.arsens

import com.example.arsens.data.FloatVector
import com.example.arsens.data.JsonProjectStore
import com.example.arsens.data.Marker
import com.example.arsens.data.MmPosition
import com.example.arsens.data.PlacementOrigin
import com.example.arsens.data.Project
import com.example.arsens.data.Sensor
import com.example.arsens.data.SensorStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonProjectStoreTest {

    /** De expliciete herkomst (Voorbereid/On the fly) overleeft een volledige JSON-rondrit,
     *  onafhankelijk van de plaatsings-status, voor zowel sensoren als tags. */
    @Test
    fun originSurvivesJsonRoundTrip() {
        val project = Project(
            projectName = "T",
            modelFile = "m.glb",
            sensors = listOf(
                Sensor(
                    order = 1, id = "1", name = "a", side = "veld",
                    positionMm = MmPosition(100, 100, 100), toleranceMm = 50, instruction = "",
                    status = SensorStatus.Ok, origin = PlacementOrigin.OnTheFly
                ),
                Sensor(
                    order = 2, id = "2", name = "b", side = "veld",
                    positionMm = MmPosition(200, 200, 200), toleranceMm = 50, instruction = "",
                    status = SensorStatus.Pending, origin = PlacementOrigin.Prepared
                )
            ),
            markers = listOf(
                Marker(1, "apriltag", 100, MmPosition(0, 0, 0), FloatVector(0f, 0f, 0f), origin = PlacementOrigin.Prepared),
                Marker(2, "apriltag", 100, MmPosition(10, 10, 10), FloatVector(0f, 0f, 0f), origin = PlacementOrigin.OnTheFly)
            )
        )

        val back = JsonProjectStore.projectFromJson(JsonProjectStore.projectToJson(project))

        assertEquals(PlacementOrigin.OnTheFly, back.sensors.first { it.id == "1" }.origin)
        assertEquals(PlacementOrigin.Prepared, back.sensors.first { it.id == "2" }.origin)
        assertEquals(PlacementOrigin.Prepared, back.markers.first { it.id == 1 }.origin)
        assertEquals(PlacementOrigin.OnTheFly, back.markers.first { it.id == 2 }.origin)
    }

    /** Oude projecten (vóór het origin-veld) hebben geen "origin"-sleutel. Dan afleiden: een sensor
     *  mét placement-audit = live geplaatst (on-the-fly), zonder = voorbereid; tags = voorbereid. */
    @Test
    fun absentOriginIsDerivedForLegacyProjects() {
        val legacy = """
            {
              "project_name": "T",
              "model_file": "m.glb",
              "dimensions_mm": [10000, 5000, 3200],
              "sensors": [
                {"order":1,"id":"1","name":"a","side":"veld","position_mm":[100,100,100],
                 "tolerance_mm":50,"instruction":"","status":"ok","placement":{}},
                {"order":2,"id":"2","name":"b","side":"veld","position_mm":[200,200,200],
                 "tolerance_mm":50,"instruction":"","status":"pending"}
              ],
              "markers": [
                {"id":1,"type":"apriltag","size_mm":100,"position_mm":[0,0,0],
                 "rotation_deg":[0,0,0],"active":true,"pose_weight":1.0}
              ]
            }
        """.trimIndent()

        val back = JsonProjectStore.projectFromJson(legacy)

        assertEquals(PlacementOrigin.OnTheFly, back.sensors.first { it.id == "1" }.origin)
        assertEquals(PlacementOrigin.Prepared, back.sensors.first { it.id == "2" }.origin)
        assertEquals(PlacementOrigin.Prepared, back.markers.first().origin)
    }
}
