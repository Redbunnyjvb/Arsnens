package com.example.arsens

import com.example.arsens.data.*
import org.junit.Assert.*
import org.junit.Test

class SensorPlanningTest {
    private val target = Sensor(1, "TEMP-TANK", "Temperatuur tank", "Front", MmPosition(300, 0, 400),
        toleranceMm = 50, instruction = "Bij de koeling", sensorTagId = 200)
    private val emptyLog = InstallationLog("Trafo", "start", "operator", emptyList())

    @Test fun outsideRadiusPlacementKeepsPlanAndActualSeparately() {
        val actual = MmPosition(500, 0, 400)
        val log = confirmSensorAtMeasuredPosition(emptyLog, target, actual, null, "capture-time")
        val result = log.results.single()
        assertEquals(target.positionMm, result.expectedPositionMm)
        assertEquals(actual, result.measuredPositionMm)
        assertEquals(SensorStatus.Fail, result.status)
        assertEquals(200, result.distanceErrorMm)
        assertEquals("TEMP-TANK", result.sensorId)
    }

    @Test fun radiusIsEuclideanAndBoundaryIsIncluded() {
        val edge = confirmSensorAtMeasuredPosition(emptyLog, target, target.positionMm + MmPosition(30, 0, 40), null, "t")
        assertEquals(SensorStatus.Ok, edge.results.single().status)
        val outside = confirmSensorAtMeasuredPosition(emptyLog, target, target.positionMm + MmPosition(51, 0, 0), null, "t")
        assertEquals(SensorStatus.Fail, outside.results.single().status)
    }

    @Test fun editingRadiusReclassifiesWithoutMovingMeasurementOrCaptureTime() {
        val log = confirmSensorAtMeasuredPosition(emptyLog, target, MmPosition(500, 0, 400), "photo", "capture-time")
        val change = updateSensorPlan(target.copy(status = SensorStatus.Fail, toleranceMm = 250), log)
        assertEquals(SensorStatus.Ok, change.sensor.status)
        assertEquals(MmPosition(500, 0, 400), change.log.results.single().measuredPositionMm)
        assertEquals("capture-time", change.log.results.single().confirmedAt)
        assertEquals("photo", change.log.results.single().photoFile)
    }

    @Test fun movingTargetRecomputesDeviationWithoutMovingActual() {
        val log = confirmSensorAtMeasuredPosition(emptyLog, target, MmPosition(500, 0, 400), null, "t")
        val change = updateSensorPlan(target.copy(status = SensorStatus.Fail, positionMm = MmPosition(480, 0, 400)), log)
        assertEquals(20, change.log.results.single().distanceErrorMm)
        assertEquals(SensorStatus.Ok, change.sensor.status)
        assertEquals(MmPosition(500, 0, 400), change.log.results.single().measuredPositionMm)
    }

    @Test fun resetPreservesIdentityPlanAndTagButClearsCurrentPhysicalResult() {
        val log = confirmSensorAtMeasuredPosition(emptyLog, target, MmPosition(500, 0, 400), null, "t")
        val sensor = target.copy(status = SensorStatus.Fail, driftCorrection = SensorDriftCorrection(MmPosition(490, 0, 400), 10, 5, listOf(0)))
        val change = resetSensorInstallation(sensor, log)
        assertEquals(target, change.sensor)
        assertTrue(change.log.results.isEmpty())
        val reloaded = JsonProjectStore.projectFromJson(JsonProjectStore.projectToJson(Project("Trafo", "", markers = emptyList(), sensors = listOf(change.sensor))))
        assertEquals(target, reloaded.sensors.single())
    }

    @Test fun resetDoesNotEraseOtherSensorsMeasurements() {
        val first = confirmSensorAtMeasuredPosition(emptyLog, target, target.positionMm, null, "t")
        val second = target.copy(id = "B")
        val both = confirmSensorAtMeasuredPosition(first, second, second.positionMm, null, "t2")
        assertEquals(listOf("B"), resetSensorInstallation(target, both).log.results.map { it.sensorId })
    }

    @Test fun repeatedConfirmationReplacesOnlyCurrentResult() {
        val first = confirmSensorAtMeasuredPosition(emptyLog, target, target.positionMm, null, "t")
        val second = confirmSensorAtMeasuredPosition(first, target, MmPosition(500, 0, 400), null, "t2")
        assertEquals(1, second.results.size)
        assertEquals("t2", second.results.single().confirmedAt)
    }

    @Test fun tagCannotBeAssignedToTwoSensorsEvenWithSameName() {
        assertNotNull(sensorTagConflict(listOf(target), "B", 200))
        assertNull(sensorTagConflict(listOf(target), target.id, 200))
        assertNull(sensorTagConflict(listOf(target), "B", null))
        assertNull(sensorTagConflict(listOf(target), "B", 201))
    }

    @Test fun pcPreparedProjectRetainsItsRadiusIdentityAndWireFormat() {
        val json = """{"project_name":"PC plan","model_file":"","sensors":[
            {"order":1,"id":"TEMP-TANK","name":"Temperatuur tank","side":"Front",
             "position_mm":[300,0,400],"tolerance_mm":50,"instruction":"Bij koeling",
             "status":"pending","origin":"prepared","sensor_tag_id":200}]}"""
        val project = JsonProjectStore.projectFromJson(json)
        val sensor = project.sensors.single()
        assertEquals(50, sensor.toleranceMm)
        assertEquals(200, sensor.sensorTagId)
        assertNull(sensor.placement)
        assertEquals(PlacementOrigin.Prepared, sensor.origin)
        assertEquals("0 / 1 geplaatst", project.placementCountLabel)
        val stored = JsonProjectStore.projectToJson(project)
        assertTrue(stored.contains("prepared"))
        assertEquals(sensor, JsonProjectStore.projectFromJson(stored).sensors.single())
    }

    @Test fun totalsIncludeOutsideRadiusPlacementsButNotPlans() {
        val project = Project("T", "", markers = emptyList(), sensors = listOf(target, target.copy(id = "B", status = SensorStatus.Fail), target.copy(id = "C", status = SensorStatus.Ok)))
        assertEquals("2 / 3 geplaatst", project.placementCountLabel)
    }
}
