package com.example.arsens.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.arsens.data.Sensor
import com.example.arsens.data.displayName
import com.example.arsens.data.placementCountLabel

@Composable
internal fun WorkflowCameraSensorPicker(state: WorkflowAppState) {
    WorkflowCameraSensorPicker(state.cameraSensors, state.sensorId, state.project.placementCountLabel,
        state::selectCameraSensor, state::beginNewCameraSensor)
}

/** The top counter opens a compact selection list; selection itself never changes a measurement. */
@Composable
internal fun WorkflowCameraSensorPicker(
    sensors: List<Sensor>, selectedId: String, countLabel: String,
    onSelect: (Sensor) -> Unit, onNew: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    Box {
        Box(Modifier.testTag("camera-sensor-picker").semantics { contentDescription = "Sensor kiezen" }
            .clickable { query = ""; expanded = true }) {
            WorkflowCameraStatusPill("Sensor $selectedId · $countLabel ▾")
        }
        MaterialTheme(colorScheme = WorkflowCameraDarkScheme) {
            DropdownMenu(expanded, onDismissRequest = { expanded = false },
                modifier = Modifier.width(300.dp).heightIn(max = 420.dp)) {
                OutlinedTextField(query, { query = it }, singleLine = true,
                    label = { Text("Zoek naam of ID") }, modifier = Modifier.padding(horizontal = 12.dp))
                DropdownMenuItem(text = { Text("+ Nieuwe sensor") }, onClick = { onNew(); expanded = false })
                HorizontalDivider()
                sensors.filter { it.displayName().contains(query, ignoreCase = true) }.forEach { sensor ->
                    DropdownMenuItem(
                        modifier = Modifier.testTag("camera-sensor-${sensor.id}"),
                        leadingIcon = { Canvas(Modifier.size(12.dp)) { drawCircle(workflowStatusColor(sensor.status)) } },
                        text = { Column {
                            Text(sensor.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(sensor.status.label, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } },
                        trailingIcon = { if (sensor.id == selectedId) Text("✓") },
                        onClick = { onSelect(sensor); expanded = false }
                    )
                }
                if (sensors.none { it.displayName().contains(query, true) }) {
                    Text("Geen sensoren gevonden", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
