package com.example.arsens

import com.example.arsens.data.MmPosition
import com.example.arsens.data.Sensor
import com.example.arsens.data.deriveSensorIdForScannedTag
import com.example.arsens.data.sensorForSensorTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SensorTagMappingTest {
    private fun sensor(id: String, sensorTagId: Int? = null): Sensor =
        Sensor(
            order = id.toIntOrNull() ?: 0,
            id = id,
            name = "sens $id",
            side = "veld",
            positionMm = MmPosition(0, 0, 0),
            toleranceMm = 50,
            instruction = "",
            sensorTagId = sensorTagId
        )

    @Test
    fun mapsScannedTagToTheSensorThatCarriesIt() {
        val sensors = listOf(
            sensor("1", sensorTagId = 100),
            sensor("2", sensorTagId = 101),
            sensor("3", sensorTagId = null)
        )
        assertEquals("1", sensorForSensorTag(sensors, 100)?.id)
        assertEquals("2", sensorForSensorTag(sensors, 101)?.id)
    }

    @Test
    fun returnsNullWhenNoSensorCarriesTheTag() {
        val sensors = listOf(sensor("1", sensorTagId = 100), sensor("2", sensorTagId = null))
        assertNull(sensorForSensorTag(sensors, 999))
    }

    @Test
    fun ignoresSensorsWithoutASensorTag() {
        // Een sensor zonder gekoppelde tag mag nooit per ongeluk matchen, ook niet op een tag-id.
        val sensors = listOf(sensor("1", sensorTagId = null), sensor("2", sensorTagId = null))
        assertNull(sensorForSensorTag(sensors, 100))
    }

    @Test
    fun derivesSensorIdFromTagWhenFree() {
        // Lege set, start 200 → tag 200 wordt sensor "1", tag 201 wordt "2".
        assertEquals("1", deriveSensorIdForScannedTag(emptyList(), 200, 200))
        assertEquals("2", deriveSensorIdForScannedTag(emptyList(), 201, 200))
    }

    @Test
    fun reusesIdOfSensorThatAlreadyCarriesTheTag() {
        // Her-plaatsen van dezelfde tag werkt de bestaande sensor bij i.p.v. een duplicaat te maken.
        val sensors = listOf(sensor("7", sensorTagId = 200))
        assertEquals("7", deriveSensorIdForScannedTag(sensors, 200, 200))
    }

    @Test
    fun avoidsClobberingAnUnrelatedSensorWithTheDerivedId() {
        // Sensor "1" bestaat al (zonder deze tag); tag 200 zou "1" afleiden → wijk uit naar vrij ID.
        val sensors = listOf(sensor("1", sensorTagId = null))
        assertEquals("2", deriveSensorIdForScannedTag(sensors, 200, 200))
    }
}
