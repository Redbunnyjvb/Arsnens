package com.example.arsens.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.arsens.data.*

@Composable
internal fun WorkflowProjectOverview(state: WorkflowAppState) {
    WorkflowShell(title = state.project.projectName, subtitle = "Projectoverzicht", onBack = state::closeProject,
        overflowItems = workflowTopBarMenuItems(state)) {
        item { WorkflowMessage(state.message) }
        item {
            val hasGeometry = !state.project.needsWallCalibration && state.project.markers.isNotEmpty()
            OverviewCard("Volgende stap") {
                Text(when {
                    !hasGeometry -> "Bepaal de maten en scan de vaste referentietags rondom de tank."
                    state.project.activeSession == null -> "De referentie staat klaar. Bereid sensoren voor of start een meetsessie."
                    else -> "${state.project.activeSession?.name} is actief. Open de camera en richt op een vaste referentietag."
                })
                Button(onClick = {
                    if (!hasGeometry) state.beginWallScan()
                    else if (state.project.activeSession != null) state.chooseMode(WorkMode.OnTheFly)
                    else state.message = "Vul hieronder de sessiegegevens in en kies Nieuwe sessie starten."
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (!hasGeometry) "Contour scannen" else if (state.project.activeSession != null) "Camera openen" else "Nieuwe sessie voorbereiden")
                }
            }
        }
        item { OverviewCard("1 · Maten & referentie") {
            val d = state.project.dimensionsMm
            Text("${d.x.takeIf { it > 0 } ?: "—"} × ${d.y.takeIf { it > 0 } ?: "—"} × ${d.z.takeIf { it > 0 } ?: "—"} mm")
            Text("${state.project.markers.size} vaste tags · ${state.project.referenceGraph.nodes.size} tags in de contour")
            Text(if (state.project.hasWallCalibration) "Contour geaccepteerd · bovenkant rechtstreeks gekoppeld"
                else if (state.project.referenceGraph.nodes.isNotEmpty()) "Contour gedeeltelijk opgeslagen"
                else "Contour nog instellen", style = MaterialTheme.typography.bodySmall)
            WorkflowDimensionsEditor(state)
            OutlinedButton(state::beginWallScan, Modifier.fillMaxWidth()) { Text("Contour scannen / hervatten") }
            var advanced by remember { mutableStateOf(false) }
            TextButton({ advanced = !advanced }) { Text(if (advanced) "Minder opties" else "Geavanceerd: bekende tagposities") }
            if (advanced) WorkflowReferenceMethodCard(state)
        } }
        item { WorkflowAssemblyCard(state) }
        item { OverviewCard("3 · Sensorplan") {
            Text("${state.project.sensors.size} sensoren · ${state.project.sensors.count { it.origin == PlacementOrigin.Prepared }} voorbereid")
            Text("Bereid doelpunten in 2D voor, importeer een meetprogramma of voeg sensoren toe tijdens een sessie.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton({ state.chooseMode(WorkMode.Prepared) }, Modifier.fillMaxWidth()) { Text("Sensoren voorbereiden in 2D") }
            WorkflowProgramImport(state)
        } }
        item { WorkflowSessionsCard(state) }
        item { OutlinedButton({ state.go(WorkflowScreen.Report) }, Modifier.fillMaxWidth()) { Text("Rapport & meethistorie") } }
    }
}

@Composable
internal fun OverviewCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun WorkflowSessionsCard(state: WorkflowAppState) {
    OverviewCard("4 · Meetsessies") {
        if (state.project.sessions.isEmpty()) Text("Nog geen meetsessies. Je bepaalt zelf wanneer je begint.")
        state.project.sessions.forEach { session ->
            OutlinedButton({ state.viewSession(session.id) }, Modifier.fillMaxWidth()) {
                Text("${session.name} · ${if (session.status == SessionStatus.OPEN) "Actief" else "Afgerond"}\n${session.acceptedMeasurements.size} metingen · ${session.operator}")
            }
        }
        if (state.project.activeSession?.status == SessionStatus.OPEN) {
            Button({ state.chooseMode(WorkMode.OnTheFly) }, Modifier.fillMaxWidth()) { Text("Sessie hervatten in camera") }
            OutlinedButton(state::closeSession, Modifier.fillMaxWidth()) { Text("Sessie afronden") }
        } else {
            OutlinedTextField(state.sessionName, { state.sessionName = it }, label = { Text("Sessienaam (optioneel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.sessionOperator, { state.sessionOperator = it }, label = { Text("Operator") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.sessionPurpose, { state.sessionPurpose = it }, label = { Text("Doel / notities (optioneel)") }, modifier = Modifier.fillMaxWidth())
            Button(state::startSession, Modifier.fillMaxWidth()) { Text("Nieuwe sessie starten") }
        }
        state.project.measurementDraft?.let { Text("Conceptmeting van sensor ${it.sensorId} bewaard. Open de camera om opnieuw te lokaliseren en te controleren.") }
    }
}

@Composable
internal fun WorkflowDimensionsEditor(state: WorkflowAppState) {
    var expanded by state::dimensionEditorExpanded
    TextButton({ expanded = !expanded }) { Text(if (expanded) "Maten sluiten" else "Maatbron kiezen / maten instellen") }
    if (!expanded) return
    var sources by remember(state.project.dimensionValues) { mutableStateOf(List(3) { index ->
        state.project.dimensionValues.firstOrNull { it.axis == listOf("x", "y", "z")[index] }?.source
            ?: if (index == 2) DimensionSource.MANUAL else DimensionSource.SCANNED }) }
    var tankId by remember { mutableStateOf(state.project.stlModels.firstOrNull { it.role == StlPartRole.Tank }?.id) }
    var confirmed by remember(tankId, state.project.stlModels) { mutableStateOf(false) }
    listOf("Lengte", "Breedte", "Hoogte").forEachIndexed { index, label ->
        var menu by remember { mutableStateOf(false) }
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton({ menu = true }) { Text(when (sources[index]) { DimensionSource.MANUAL -> "Handmatig"; DimensionSource.STL -> "STL · tankdeel"; DimensionSource.SCANNED -> "Contour scannen" }) }
            DropdownMenu(menu, { menu = false }) {
                DimensionSource.entries.filter { index != 2 || it != DimensionSource.SCANNED }.forEach { source ->
                    DropdownMenuItem(text = { Text(when (source) { DimensionSource.MANUAL -> "Handmatig"; DimensionSource.STL -> "STL · tankdeel"; DimensionSource.SCANNED -> "Contour scannen" }) }, onClick = { sources = sources.toMutableList().also { it[index] = source }; menu = false })
                }
            }
        }
        if (sources[index] == DimensionSource.MANUAL) WorkflowNumberField("$label (mm)",
            when (index) { 0 -> state.lengthMm; 1 -> state.widthMm; else -> state.heightMm },
            { when (index) { 0 -> state.lengthMm = it; 1 -> state.widthMm = it; else -> state.heightMm = it } }, Modifier.fillMaxWidth())
    }
    if (DimensionSource.STL in sources) {
        Text("Kies uitsluitend het tankdeel. Controleer de schaal en X/Y/Z-oriëntatie in 3D voordat je de maten gebruikt.")
        state.project.stlModels.filter { it.role == StlPartRole.Tank }.forEach { model ->
            OutlinedButton({ tankId = model.id }, Modifier.fillMaxWidth()) { Text("${if (tankId == model.id) "✓ " else ""}${model.name} · ${model.scalePercent}%") }
        }
        val dims = tankId?.let(state::tankDimensions)
        Text(dims?.let { "STL: ${it.x} × ${it.y} × ${it.z} mm" } ?: "Wijs in 3D eerst de rol Tank toe aan het juiste onderdeel.")
        Row { Checkbox(confirmed, { confirmed = it }); Text("Units, schaal en oriëntatie gecontroleerd", Modifier.padding(top = 12.dp)) }
    }
    Button({ state.applyDimensionSources(sources, tankId, confirmed) }, Modifier.fillMaxWidth()) { Text("Maatvoering gebruiken") }
}

@Composable
internal fun WorkflowMeasurementActions(state: WorkflowAppState, id: String) {
    Text(state.project.activeSession?.name ?: "Geen actieve sessie", style = MaterialTheme.typography.labelLarge)
    if (state.armedSensorId == id) {
        Text("${state.armedAction.name} · meting actief")
        TextButton(state::cancelMeasurement) { Text("Annuleren") }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ state.armMeasurement(id, MeasurementAction.VERIFY) }) { Text("Controleren") }
            OutlinedButton({ state.armMeasurement(id, if (state.resultFor(id) == null) MeasurementAction.PLACE else MeasurementAction.MOVE) }) { Text("${if (state.resultFor(id) == null) "Plaatsen" else "Verplaatsen"}") }
        }
    }
}

@Composable
internal fun WorkflowDraftDialog(state: WorkflowAppState) {
    val draft = state.project.measurementDraft ?: return
    if (state.screen !in listOf(WorkflowScreen.Tags, WorkflowScreen.Install) || !state.sensorPlacementReady) return
    AlertDialog(onDismissRequest = {}, title = { Text("Meting controleren · sensor ${draft.sensorId}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Positie: ${state.operatorText(draft.result.measuredPositionMm)} mm")
            Text("Afwijking van doel: ${draft.result.distanceErrorMm} mm · ${draft.method.name}")
            state.resultFor(draft.sensorId)?.measuredPositionMm?.let { old -> Text("Vorige positie: ${state.operatorText(old)} mm") }
            Text("${state.project.activeSession?.name} · ${draft.action.name}")
        } }, confirmButton = { TextButton(state::acceptDraft) { Text("Meting accepteren") } },
        dismissButton = { TextButton(state::cancelMeasurement) { Text("Verwerpen") } })
}

@Composable
internal fun WorkflowProgramImport(state: WorkflowAppState) {
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) state.previewProgram(uri, scope) }
    OutlinedButton({ picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, Modifier.fillMaxWidth()) { Text("ARSensProgram importeren") }
    val program = state.programPreview ?: return
    var policy by remember(program) { mutableStateOf(ProgramConflictPolicy.SKIP) }
    AlertDialog(onDismissRequest = { state.programPreview = null }, title = { Text("Sensorprogramma controleren") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("${program.sensors.size} sensoren · millimeters · projectframe")
            Text("${program.sensors.count { incoming -> state.project.sensors.any { it.id == incoming.id || (incoming.sensorTagId != null && it.sensorTagId == incoming.sensorTagId) } }} mogelijke conflicten")
            ProgramConflictPolicy.entries.forEach { option -> Row { RadioButton(policy == option, { policy = option }); Text(option.label, Modifier.padding(top = 12.dp)) } }
            program.sensors.take(20).forEach { Text("${it.id} · ${it.name} · ${it.positionMm.toReadableMm()}") }
        } }, confirmButton = { TextButton({ state.acceptProgram(policy) }) { Text("Importeren") } },
        dismissButton = { TextButton({ state.programPreview = null }) { Text("Annuleren") } })
}
