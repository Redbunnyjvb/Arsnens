package com.example.arsens.data

/** The business ID identifies a sensor. An AprilTag is only an optional physical label. */
fun sensorTagConflict(sensors: List<Sensor>, sensorId: String, tagId: Int?): String? =
    tagId?.let { tag ->
        sensors.firstOrNull { it.id != sensorId && it.sensorTagId == tag }?.let {
            "Tag $tag hoort al bij sensor ${it.id} · ${it.name}. Kies die sensor of wijzig eerst de koppeling."
        }
    }

data class SensorInstallationChange(val sensor: Sensor, val log: InstallationLog)

/** Reset the current placement, retaining identity, target, radius and optional tag association. */
fun resetSensorInstallation(sensor: Sensor, log: InstallationLog): SensorInstallationChange =
    SensorInstallationChange(
        sensor.copy(status = SensorStatus.Pending, placement = null, driftCorrection = null),
        log.copy(results = log.results.filterNot { it.sensorId == sensor.id })
    )

/** Editing a target must never move an actual measurement or replace its capture time. */
fun updateSensorPlan(sensor: Sensor, log: InstallationLog): SensorInstallationChange {
    val result = log.results.firstOrNull { it.sensorId == sensor.id }
        ?.takeIf { sensor.status != SensorStatus.Pending }
        ?: return SensorInstallationChange(sensor, log)
    val measured = result.measuredPositionMm ?: (result.expectedPositionMm + result.measuredOffsetMm)
    val offset = measured - sensor.positionMm
    val error = distanceMm(offset)
    val status = if (error <= sensor.toleranceMm) SensorStatus.Ok else SensorStatus.Fail
    return SensorInstallationChange(sensor.copy(status = status), log.copy(results = log.results.map {
        if (it.sensorId == sensor.id) it.copy(expectedPositionMm = sensor.positionMm,
            measuredPositionMm = measured, measuredOffsetMm = offset, distanceErrorMm = error, status = status)
        else it
    }))
}

fun Sensor.displayName(): String = if (name.isBlank() || name == "sens" || name == id) "Sensor $id" else "$id · $name"

val Project.placementCountLabel: String
    get() = "${sensors.count { it.status != SensorStatus.Pending }} / ${sensors.size} geplaatst"
