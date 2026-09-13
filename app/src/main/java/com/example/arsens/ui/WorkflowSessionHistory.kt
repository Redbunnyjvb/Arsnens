package com.example.arsens.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.arsens.data.*

@Composable
internal fun WorkflowSessionHistory(state: WorkflowAppState) {
    OverviewCard("Sessies & historie") {
        var expanded by remember { mutableStateOf(false) }
        val selected = state.project.sessions.firstOrNull { it.id == state.reportSessionId }
        Box {
            OutlinedButton({ expanded = true }) { Text("${selected?.name ?: "Actueel"} ▾") }
            DropdownMenu(expanded, { expanded = false }) {
                DropdownMenuItem(text = { Text("Actueel") }, onClick = { state.viewSession(null); expanded = false })
                state.project.sessions.forEach { session -> DropdownMenuItem(text = { Text(session.name) },
                    onClick = { state.viewSession(session.id); expanded = false }) }
            }
        }
        if (selected != null) {
            val revision = state.project.geometryRevisions.firstOrNull { it.id == selected.geometryRevisionId }
            Text("${if (selected.status == SessionStatus.CLOSED) "Afgerond · alleen-lezen" else "Actieve sessie"} · geometrie ${revision?.number}")
            Text("${selected.startedAt} · ${selected.operator}", style = MaterialTheme.typography.bodySmall)
        }
        val sessions = selected?.let(::listOf) ?: state.project.sessions
        if (sessions.isEmpty()) Text("Nog geen sessiemetingen.")
        sessions.forEach { session ->
            Text(session.name, style = MaterialTheme.typography.titleSmall)
            session.measurements.forEach { measurement ->
                val reverted = measurement !in session.acceptedMeasurements
                val previous = session.baseline.firstOrNull { it.sensorId == measurement.sensorId }?.measuredPositionMm
                val actual = measurement.result.measuredPositionMm
                val plan = session.sensorDefinitions.firstOrNull { it.id == measurement.sensorId }
                Text("Sensor ${measurement.sensorId} · ${measurement.action.name}${if (reverted) " · teruggedraaid" else ""}")
                Text("Gemeten: ${actual?.toReadableMm()} mm · ${measurement.method.name}", style = MaterialTheme.typography.bodySmall)
                if (plan?.origin == PlacementOrigin.Prepared) Text("Doel: ${measurement.result.expectedPositionMm.toReadableMm()} · afwijking ${measurement.result.distanceErrorMm} mm · radius ${plan.toleranceMm} mm", style = MaterialTheme.typography.bodySmall)
                if (actual != null && previous != null) Text("Verplaatsing sinds vorige sessie: ${distanceMm(actual - previous)} mm", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
            }
        }
    }
}
