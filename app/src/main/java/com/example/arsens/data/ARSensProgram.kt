package com.example.arsens.data

import org.json.JSONObject

data class ARSensProgram(val sensors: List<Sensor>)
enum class ProgramConflictPolicy(val label: String) { SKIP("Conflicten overslaan"), REPLACE("Bestaand sensorplan vervangen"), RENAME("Als nieuwe sensor, zonder conflicterende tag") }

object ARSensProgramImport {
    fun parse(text: String): ARSensProgram {
        val root = JSONObject(text)
        require(root.getInt("schemaVersion") == 1) { "Ondersteunde ARSensProgram schemaVersion: 1." }
        require(root.getString("units") == "mm") { "Het programma moet millimeters gebruiken (units: mm)." }
        require(root.getString("coordinateFrame") == "ARSENS_BOX_XYZ") { "Verwacht ARSENS_BOX_XYZ: X lengte, Y breedte, Z omhoog, oorsprong links-voor-onder." }
        val array = root.getJSONArray("sensors")
        val sensors = List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val target = item.getJSONObject("target")
            fun coordinate(key: String): Int {
                val value = target.getDouble(key)
                require(value.isFinite() && value in 0.0..100000.0 && value % 1.0 == 0.0) { "Doelcoördinaten moeten hele millimeters zijn (0–100000)." }
                return value.toInt()
            }
            val id = item.getString("id").trim()
            require(id.isNotEmpty()) { "Een sensor-ID mag niet leeg zijn." }
            val tolerance = item.optInt("toleranceMm", 25)
            require(tolerance > 0) { "Tolerantie moet groter dan nul zijn." }
            Sensor(item.optInt("order", index + 1), id, item.optString("name", id), item.optString("side", "Front"),
                MmPosition(coordinate("x"), coordinate("y"), coordinate("z")), toleranceMm = tolerance,
                instruction = item.optString("instruction"), sensorTagId = if (item.has("sensorTagId") && !item.isNull("sensorTagId")) item.getInt("sensorTagId") else null)
        }
        require(sensors.map { it.id }.distinct().size == sensors.size) { "Dubbele sensor-ID's in het programma." }
        val tags = sensors.mapNotNull { it.sensorTagId }
        require(tags.distinct().size == tags.size && tags.all { it >= 0 }) { "Dubbele of ongeldige sensor-tag-ID's." }
        return ARSensProgram(sensors.sortedBy { it.order })
    }
    fun merge(existing: List<Sensor>, program: ARSensProgram, policy: ProgramConflictPolicy): List<Sensor> {
        val result = existing.toMutableList()
        program.sensors.forEach { incoming ->
            val idConflict = result.any { it.id == incoming.id }
            val tagOwner = result.firstOrNull { incoming.sensorTagId != null && it.sensorTagId == incoming.sensorTagId && it.id != incoming.id }
            if ((idConflict || tagOwner != null) && policy == ProgramConflictPolicy.SKIP) return@forEach
            if (policy == ProgramConflictPolicy.RENAME && (idConflict || tagOwner != null)) {
                var suffix = 2
                var id = incoming.id
                while (result.any { it.id == id }) { id = "${incoming.id}-${suffix++}" }
                result += incoming.copy(id = id, sensorTagId = incoming.sensorTagId?.takeUnless { tag -> result.any { it.sensorTagId == tag } })
            } else {
                require(tagOwner == null) { "Tag ${incoming.sensorTagId} hoort bij ${tagOwner?.id}; kies overslaan of als nieuwe sensor." }
                result.removeAll { it.id == incoming.id }
                result += incoming
            }
        }
        return result.mapIndexed { index, sensor -> sensor.copy(order = index + 1) }
    }
}
