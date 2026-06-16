package com.example.arsens.data

fun confirmSensor(
    log: InstallationLog,
    sensorId: String,
    expectedPosition: MmPosition,
    photo: String?,
    offset: MmPosition,
    toleranceMm: Int,
    confirmedAt: String,
    measuredPosition: MmPosition? = null
): InstallationLog {
    val error = distanceMm(offset)
    val result = InstallationResult(
        sensorId = sensorId,
        expectedPositionMm = expectedPosition,
        measuredPositionMm = measuredPosition ?: expectedPosition + offset,
        measuredOffsetMm = offset,
        distanceErrorMm = error,
        status = if (error <= toleranceMm) SensorStatus.Ok else SensorStatus.Fail,
        photoFile = photo,
        confirmedAt = confirmedAt
    )
    return log.copy(results = log.results.filterNot { it.sensorId == sensorId } + result)
}

fun confirmSensorAtMeasuredPosition(
    log: InstallationLog,
    sensor: Sensor,
    measuredPosition: MmPosition,
    photo: String?,
    confirmedAt: String
): InstallationLog =
    confirmSensor(
        log = log,
        sensorId = sensor.id,
        expectedPosition = sensor.positionMm,
        photo = photo,
        offset = measuredPosition - sensor.positionMm,
        toleranceMm = sensor.toleranceMm,
        confirmedAt = confirmedAt,
        measuredPosition = measuredPosition
    )
