package com.example.arsens.data

import java.util.Locale

object SensorCsv {
    private val expectedHeaders = listOf(
        "order",
        "sensor_id",
        "name",
        "side",
        "x_mm",
        "y_mm",
        "z_mm",
        "tolerance_mm",
        "instruction"
    )

    fun importSensorsFromCsv(csvText: String): List<Sensor> {
        val rows = csvText
            .lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .map(::parseCsvLine)
            .toList()

        if (rows.isEmpty()) return emptyList()

        val header = rows.first().map { it.trim().lowercase() }
        val index = expectedHeaders.associateWith { header.indexOf(it) }
        val missing = index.filterValues { it < 0 }.keys
        require(missing.isEmpty()) {
            "CSV mist kolommen: ${missing.joinToString(", ")}"
        }

        return rows.drop(1).mapIndexed { rowIndex, row ->
            fun value(column: String): String {
                val cellIndex = index.getValue(column)
                return row.getOrNull(cellIndex)?.trim().orEmpty()
            }

            val order = value("order").toIntOrNull()
                ?: error("Rij ${rowIndex + 2}: order is geen getal")
            val x = value("x_mm").toIntOrNull()
                ?: error("Rij ${rowIndex + 2}: x_mm is geen getal")
            val y = value("y_mm").toIntOrNull()
                ?: error("Rij ${rowIndex + 2}: y_mm is geen getal")
            val z = value("z_mm").toIntOrNull()
                ?: error("Rij ${rowIndex + 2}: z_mm is geen getal")
            val tolerance = value("tolerance_mm").toIntOrNull()
                ?: error("Rij ${rowIndex + 2}: tolerance_mm is geen getal")

            Sensor(
                order = order,
                id = value("sensor_id"),
                name = value("name"),
                side = value("side"),
                positionMm = MmPosition(x, y, z),
                toleranceMm = tolerance,
                instruction = value("instruction")
            )
        }.sortedBy { it.order }
    }

    fun sensorsToCsv(sensors: List<Sensor>): String = buildString {
        appendLine(expectedHeaders.joinToString(","))
        sensors.sortedBy { it.order }.forEach { sensor ->
            appendLine(
                listOf(
                    sensor.order.toString(),
                    sensor.id,
                    sensor.name,
                    sensor.side,
                    sensor.positionMm.x.toString(),
                    sensor.positionMm.y.toString(),
                    sensor.positionMm.z.toString(),
                    sensor.toleranceMm.toString(),
                    sensor.instruction
                ).joinToString(",") { escapeCsv(it) }
            )
        }
    }

    fun exportReportCsv(log: InstallationLog, project: Project): String = buildString {
        appendLine(
            listOf(
                "order",
                "sensor_id",
                "name",
                "status",
                "expected_x_mm",
                "expected_y_mm",
                "expected_z_mm",
                "measured_x_mm",
                "measured_y_mm",
                "measured_z_mm",
                "offset_x_mm",
                "offset_y_mm",
                "offset_z_mm",
                "distance_error_mm",
                "distance_error_cm",
                "photo_file",
                "confirmed_at"
            ).joinToString(",")
        )
        val resultsBySensor = log.results.associateBy { it.sensorId }
        project.sensors.sortedBy { it.order }.forEach { sensor ->
            val result = resultsBySensor[sensor.id]
            val measured = result?.measuredPositionMm
            appendLine(
                listOf(
                    sensor.order.toString(),
                    sensor.id,
                    sensor.name,
                    (result?.status ?: sensor.status).wireName,
                    sensor.positionMm.x.toString(),
                    sensor.positionMm.y.toString(),
                    sensor.positionMm.z.toString(),
                    measured?.x?.toString().orEmpty(),
                    measured?.y?.toString().orEmpty(),
                    measured?.z?.toString().orEmpty(),
                    result?.measuredOffsetMm?.x?.toString().orEmpty(),
                    result?.measuredOffsetMm?.y?.toString().orEmpty(),
                    result?.measuredOffsetMm?.z?.toString().orEmpty(),
                    result?.distanceErrorMm?.toString().orEmpty(),
                    result?.distanceErrorMm?.let {
                        String.format(Locale.US, "%.1f", it / 10f)
                    }.orEmpty(),
                    result?.photoFile.orEmpty(),
                    result?.confirmedAt.orEmpty()
                ).joinToString(",") { escapeCsv(it) }
            )
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    cell.append('"')
                    index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    cells += cell.toString()
                    cell.clear()
                }
                else -> cell.append(char)
            }
            index++
        }
        cells += cell.toString()
        return cells
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}

fun importSensorsFromCsv(csvText: String): List<Sensor> = SensorCsv.importSensorsFromCsv(csvText)

fun exportReportCsv(log: InstallationLog, project: Project): String =
    SensorCsv.exportReportCsv(log, project)
