package com.example.arsens.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.arsens.data.Sensor

@Composable
internal fun WorkflowSensorEditDialog(
    sensor: Sensor,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int?, String) -> String?
) {
    var name by remember(sensor.id) { mutableStateOf(sensor.name) }
    var radius by remember(sensor.id) { mutableStateOf(sensor.toleranceMm.toString()) }
    var tag by remember(sensor.id) { mutableStateOf(sensor.sensorTagId?.toString().orEmpty()) }
    var note by remember(sensor.id) { mutableStateOf(sensor.instruction) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Sensor ${sensor.id} bewerken") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vaste sensor-ID: ${sensor.id}")
            OutlinedTextField(name, { name = it }, label = { Text("Naam") }, singleLine = true)
            OutlinedTextField(radius, { radius = it }, label = { Text("Doelradius (mm)") }, singleLine = true)
            Text("50 mm = 5 cm. Buiten deze radius plaatsen mag; het rapport toont de afwijking.")
            OutlinedTextField(tag, { tag = it }, label = { Text("Sensor-tag-ID (optioneel)") }, singleLine = true)
            OutlinedTextField(note, { note = it }, label = { Text("Notitie / instructie") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val parsedRadius = radius.toIntOrNull()
            val parsedTag = tag.trim().toIntOrNull()
            error = when {
                parsedRadius == null || parsedRadius <= 0 -> "Vul een radius groter dan nul in hele millimeters in."
                tag.isNotBlank() && parsedTag == null -> "De sensor-tag-ID moet een geheel getal zijn."
                else -> onSave(sensor.id, name, parsedRadius, parsedTag, note)
            }
            if (error == null) onDismiss()
        }) { Text("Opslaan") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } })
}
