@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.arsens.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.arsens.data.*

@Composable
internal fun WorkflowProjectOverview(state: WorkflowAppState) {
    var panel by remember(state.project.projectId) { mutableStateOf<String?>(if(state.dimensionEditorExpanded) "geometry" else null) }
    LaunchedEffect(state.sessionSetupRequested) { if (state.sessionSetupRequested) panel = "sessions" }
    val scope=rememberCoroutineScope()
    val stlPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { files ->
        if(files.isNotEmpty()) state.addStlModelsThenAskPlace(files,scope)
    }
    val project=state.project
    val ready=!project.needsWallCalibration && project.markers.any { it.active && it.isAprilTagCalibrationMarker() }
    val session=project.activeSession
    LaunchedEffect(project.stlModels) { state.ensureStlMeshesLoaded() }
    WorkflowShell(title=project.projectName,subtitle="Project",onBack=state::closeProject,overflowItems=workflowTopBarMenuItems(state)) {
        item {
            Text(if(session!=null) "${session.name} · actief" else if(ready) "Klaar voor een meetsessie" else "Stel je transformator in",
                style=MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(if(session!=null) "${session.acceptedMeasurements.size} metingen opgeslagen" else if(ready) "Kies een sessie of bereid sensoren voor." else "Voeg eventueel STL toe. Bepaal daarna de contour.",
                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick={ if(!ready) state.beginWallScan() else if(session!=null) state.chooseMode(WorkMode.OnTheFly) else panel="sessions" },
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(!ready) "Contour scannen" else if(session!=null) "Camera hervatten" else "Nieuwe meetsessie") }
        }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                ProjectRow("Geometrie", project.dimensionsMm.let { d ->
                    if(d.x>0 && d.y>0 && d.z>0) "${d.x} × ${d.y} × ${d.z} mm · ${project.markers.size} refs" else "Maten en referentietags" }) {
                    state.dimensionEditorExpanded=true;panel="geometry"
                }
                HorizontalDivider()
                ProjectRow("CAD / STL",if(project.stlModels.isEmpty()) "Optioneel · model toevoegen" else "${project.stlModels.size} onderdelen · 3D bekijken") {
                    if(project.stlModels.isEmpty()) stlPicker.launch(arrayOf("*/*")) else state.go(WorkflowScreen.Stl)
                }
                HorizontalDivider()
                ProjectRow("Sensorplan · 2D", "${project.sensors.size} sensoren · ${project.sensors.count { it.origin==PlacementOrigin.Prepared }} voorbereid") { panel="plan" }
                HorizontalDivider()
                ProjectRow("Meetsessies",if(session!=null) "${session.name} · actief" else "${project.sessions.size} afgerond") { panel="sessions" }
            }
        }
        item {
            OutlinedButton({ state.go(WorkflowScreen.Report) },Modifier.fillMaxWidth()) { Text("Rapport & historie") }
            state.project.measurementDraft?.let { draft ->
                Text("Concept van ${draft.sensorId} bewaard",style=MaterialTheme.typography.bodySmall)
                TextButton({ state.chooseMode(WorkMode.OnTheFly) }) { Text("Concept hervatten") }
                TextButton(state::cancelMeasurement) { Text("Concept verwerpen") }
            }
        }
        item { state.message?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
    if(panel!=null) ModalBottomSheet(onDismissRequest={ panel=null;state.dimensionEditorExpanded=false;state.sessionSetupRequested=false }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(panel) {
                "geometry" -> {
                    Text("Maten & referentie",style=MaterialTheme.typography.titleLarge)
                    WorkflowDimensionsEditor(state,showToggle=false)
                    WorkflowDimensionComparison(project.dimensionComparisons,project.dimensionWarningMm)
                    OutlinedButton({ panel=null;state.beginWallScan() },Modifier.fillMaxWidth()) { Text("Contour scannen / hervatten") }
                    var advanced by remember { mutableStateOf(false) }
                    TextButton({advanced=!advanced}) {Text("Geavanceerd")}
                    if(advanced) {
                        WorkflowReferenceMethodCard(state)
                        WorkflowNumberField("Waarschuwen bij verschil (mm)",project.dimensionWarningMm.toString(),state::setDimensionWarning,Modifier.fillMaxWidth())
                    }
                }
                "plan" -> {
                    Text("Sensorplan",style=MaterialTheme.typography.titleLarge)
                    Button({panel=null;state.chooseMode(WorkMode.Prepared)},Modifier.fillMaxWidth()) {Text("2D openen")}
                    WorkflowProgramImport(state)
                }
                else -> WorkflowSessionsCard(state)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProjectRow(title: String, summary: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(18.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleMedium)
            Text(summary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
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
    OverviewCard("Meetsessies") {
        if (state.project.sessions.isEmpty()) Text("Nog geen meetsessies. Je bepaalt zelf wanneer je begint.")
        state.project.sessions.forEach { session ->
            OutlinedButton({ state.viewSession(session.id) }, Modifier.fillMaxWidth()) {
                Text("${session.name} · ${if (session.status == SessionStatus.OPEN) "Actief" else "Afgerond"}\n${session.acceptedMeasurements.size} metingen · ${session.operator}")
            }
        }
        if (state.project.activeSession?.status == SessionStatus.OPEN) {
            state.project.activeSession?.summary()?.let { Text("${it.placed} geplaatst · ${it.moved} verplaatst · ${it.verified} gecontroleerd",style=MaterialTheme.typography.bodySmall) }
            state.project.activeSession?.summary()?.let { Text("${it.unchanged} ongewijzigd · ${it.remaining} nog te plaatsen",style=MaterialTheme.typography.bodySmall) }
            Button({ state.chooseMode(WorkMode.OnTheFly) }, Modifier.fillMaxWidth()) { Text("Sessie hervatten in camera") }
            OutlinedButton(state::closeSession, Modifier.fillMaxWidth()) { Text("Sessie afronden") }
        } else {
            OutlinedTextField(state.sessionName, { state.sessionName = it }, label = { Text("Sessienaam (optioneel)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.sessionOperator, { state.sessionOperator = it }, label = { Text("Operator") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.sessionPurpose, { state.sessionPurpose = it }, label = { Text("Doel / notities (optioneel)") }, modifier = Modifier.fillMaxWidth())
            Button(state::startSessionAndOpenCamera, Modifier.fillMaxWidth()) { Text("Sessie starten · camera openen") }
        }
        state.project.measurementDraft?.let { Text("Conceptmeting van sensor ${it.sensorId} bewaard. Open de camera om opnieuw te lokaliseren en te controleren.") }
    }
}

@Composable
internal fun WorkflowDimensionsEditor(state: WorkflowAppState, showToggle: Boolean = true) {
    var expanded by state::dimensionEditorExpanded
    if (showToggle) TextButton({ expanded = !expanded }) { Text(if (expanded) "Maten sluiten" else "Maatbron kiezen / maten instellen") }
    if (showToggle && !expanded) return
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
        Text("${state.armedAction.label} · meting actief")
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
            if(state.project.sensors.firstOrNull { it.id==draft.sensorId }?.origin==PlacementOrigin.Prepared)
                Text("Afwijking van doel: ${draft.result.distanceErrorMm} mm")
            Text(draft.method.label)
            draft.captureEvidence?.let { Text("${it.sampleCount} beelden · spreiding ${"%.1f".format(it.scatterMm)} mm",style=MaterialTheme.typography.bodySmall) }
            state.resultFor(draft.sensorId)?.measuredPositionMm?.let { old -> Text("Vorige positie: ${state.operatorText(old)} mm") }
            Text("${state.project.activeSession?.name} · ${draft.action.label}")
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
