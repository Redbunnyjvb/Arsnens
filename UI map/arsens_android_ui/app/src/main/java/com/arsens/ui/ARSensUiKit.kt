package com.arsens.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.SensorOccupied
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.ThreeDRotation
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ArBlue = Color(0xFF2E66FF)
private val ArCyan = Color(0xFF18C5C8)
private val ArDark = Color(0xFF08111B)
private val GlassDark = Color(0xAA1B2430)
private val GlassLight = Color(0x55FFFFFF)
private val PanelDark = Color(0xDD101820)
private val TextMuted = Color(0xFFB8C2D0)

data class PlacedItem(
    val id: String,
    val plane: String,
    val x: String,
    val y: String,
    val z: String,
    val type: ItemType
)

enum class ItemType { Tag, Sensor }

data class PlaneSelection(val plane: String, val gridX: Int, val gridY: Int)

private val demoTags = listOf(
    PlacedItem("Tag 001", "Voorzijde", "0 mm", "50 cm", "12 cm", ItemType.Tag),
    PlacedItem("Tag 002", "Voorzijde", "120 cm", "30 cm", "15 cm", ItemType.Tag),
    PlacedItem("Tag 014", "Boven", "60 cm", "80 cm", "20 cm", ItemType.Tag)
)

private val demoSensors = listOf(
    PlacedItem("Sensor 001", "Voorzijde", "0 mm", "50 cm", "12 cm", ItemType.Sensor),
    PlacedItem("Sensor 002", "Voorzijde", "15 cm", "42 cm", "10 cm", ItemType.Sensor)
)

@Composable
fun ARSensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ArBlue,
            secondary = ArCyan,
            background = ArDark,
            surface = Color(0xFF101820)
        ),
        content = content
    )
}

@Composable
fun ARSensOnTheFlyScreen() {
    Box(Modifier.fillMaxSize().background(ArDark)) {
        TransformerCameraMock()
        LeftToolRail()
        SensorCounter(Modifier.align(Alignment.TopEnd).padding(18.dp), count = 2)
        CenterCursor(Modifier.align(Alignment.Center))
        CoordinatePill(Modifier.align(Alignment.BottomCenter).padding(bottom = 118.dp), "X: 0 mm", "Y: 50 cm", "Z: 12 cm", "Voorzijde")
        UndoButton(Modifier.align(Alignment.BottomStart).padding(22.dp))
        PrimaryTriggerButton(Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp), text = "Plaats sensor")
    }
}

@Composable
fun ARSensTagMenuScreen() {
    Box(Modifier.fillMaxSize().background(ArDark)) {
        TransformerCameraMock()
        LeftToolRail()
        CenterCursor(Modifier.align(Alignment.Center))
        CoordinatePill(Modifier.align(Alignment.BottomCenter).padding(bottom = 118.dp), "X: 0 mm", "Y: 50 cm", "Z: 12 cm", "Voorzijde")
        UndoButton(Modifier.align(Alignment.BottomStart).padding(22.dp))
        PrimaryTriggerButton(Modifier.align(Alignment.BottomCenter).padding(bottom = 34.dp), text = "Plaats tag")
        TagMenuPanel(Modifier.align(Alignment.CenterEnd).padding(14.dp).fillMaxHeight(0.92f).width(330.dp))
    }
}

@Composable
fun ARSensReportScreen() {
    Column(Modifier.fillMaxSize().background(ArDark).padding(18.dp)) {
        Text("ARSens", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("Trafo 001 - Rapport", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ReportMeta("Project", "Trafo Station Noord")
                    ReportMeta("Aangemaakt", "24 mei 2025, 14:32")
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ReportMeta("Locatie", "HS-02")
                    ReportMeta("Aangepast", "25 mei 2025, 09:41")
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ActionTile("Exporteren", Icons.Default.Download, ArCyan, Modifier.weight(1f))
            ActionTile("Open 2D", Icons.Default.GridView, GlassDark, Modifier.weight(1f))
            ActionTile("Open 3D", Icons.Default.ThreeDRotation, GlassDark, Modifier.weight(1f))
            ActionTile("Open STL", Icons.Default.PictureAsPdf, GlassDark, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        SectionList("Tags (${demoTags.size})", demoTags)
        Spacer(Modifier.height(14.dp))
        SectionList("Sensoren (${demoSensors.size})", demoSensors)
    }
}

@Composable
fun ARSens2DViewScreen() {
    Box(Modifier.fillMaxSize().background(ArDark)) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            ScreenTopBar("2D weergave")
            Spacer(Modifier.height(12.dp))
            LayerSwitchPanel()
            Spacer(Modifier.height(18.dp))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                TransformerUnfoldedSchema(
                    selectedPlane = "Voorzijde",
                    modifier = Modifier.fillMaxWidth(0.9f).height(380.dp),
                    showMarkers = true
                )
            }
            SectionList("Geplaatste items (voorzijde)", demoSensors.take(1) + demoTags.take(2), compact = true)
        }
    }
}

@Composable
fun ARSens3DViewScreen() {
    Box(Modifier.fillMaxSize().background(ArDark)) {
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                Spacer(Modifier.width(14.dp))
                Text("3D weergave", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                SmallGlassButton("Open STL", Icons.Default.PictureAsPdf)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xFF111923))) {
                Transformer3DMock(Modifier.fillMaxSize())
                LayerSwitchPanel(Modifier.align(Alignment.TopStart).padding(14.dp))
                Column(Modifier.align(Alignment.CenterEnd).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallGlassButton("Roteren", Icons.Default.Redo)
                    SmallGlassButton("Zoom", Icons.Default.MyLocation)
                    SmallGlassButton("Reset", Icons.Default.Public)
                }
            }
            Spacer(Modifier.height(12.dp))
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(ArCyan)
                        Spacer(Modifier.width(10.dp))
                        Text("Sensor 001", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        SmallGlassButton("Bewerken", Icons.Default.Place)
                    }
                    Spacer(Modifier.height(12.dp))
                    DetailLine("Positie (Voorzijde)", "X: 0 mm   |   Y: 50 cm   |   Z: 12 cm")
                    DetailLine("Vlak", "Voorzijde")
                    DetailLine("Geplaatst", "24 mei 2025, 14:32")
                }
            }
        }
    }
}

@Composable
private fun TransformerCameraMock() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF5D6569), Color(0xFF222A2E), Color(0xFF5A554F))))
        val w = size.width
        val h = size.height
        drawRect(Color(0xFF313C35), topLeft = Offset(w * 0.16f, h * 0.34f), size = Size(w * 0.68f, h * 0.34f))
        for (i in 0..11) {
            val x = w * 0.18f + i * w * 0.055f
            drawLine(Color(0xFF7D8779), Offset(x, h * 0.35f), Offset(x, h * 0.68f), strokeWidth = 3f)
        }
        drawRect(Color(0xFFE8E8E8), topLeft = Offset(w * 0.68f, h * 0.34f), size = Size(w * 0.12f, h * 0.1f))
        drawRect(Color(0xFF111111), topLeft = Offset(w * 0.705f, h * 0.355f), size = Size(w * 0.07f, h * 0.05f))
        drawRect(Color(0x99000000))
    }
}

@Composable
private fun LeftToolRail() {
    Column(Modifier.padding(start = 18.dp, top = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RailButton("Plane\nselector", Icons.Default.Public)
        RailButton("2D\nweergave", Icons.Default.GridView)
        RailButton("Lagen", Icons.Default.Layers)
        RailButton("Sensoren", Icons.Default.SensorOccupied)
        RailButton("Tags", Icons.Default.Tag)
        RailButton("Rapport", Icons.Default.Report)
    }
}

@Composable
private fun RailButton(label: String, icon: ImageVector) {
    Column(
        Modifier.size(width = 62.dp, height = 62.dp).clip(RoundedCornerShape(14.dp)).background(GlassLight).border(1.dp, Color.White.copy(0.25f), RoundedCornerShape(14.dp)).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 10.sp)
    }
}

@Composable
private fun SensorCounter(modifier: Modifier, count: Int) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(GlassDark).border(1.dp, Color.White.copy(0.18f), RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(ArCyan)
        Spacer(Modifier.width(8.dp))
        Text("$count sensoren\ngeplaatst", color = Color.White, fontSize = 12.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun CenterCursor(modifier: Modifier) {
    Canvas(modifier.size(54.dp)) {
        val c = center
        drawCircle(Color.White, radius = 11.dp.toPx(), center = c, style = Stroke(2.5.dp.toPx()))
        drawLine(Color.White, Offset(c.x - 27.dp.toPx(), c.y), Offset(c.x - 14.dp.toPx(), c.y), strokeWidth = 2.dp.toPx())
        drawLine(Color.White, Offset(c.x + 14.dp.toPx(), c.y), Offset(c.x + 27.dp.toPx(), c.y), strokeWidth = 2.dp.toPx())
        drawLine(Color.White, Offset(c.x, c.y - 27.dp.toPx()), Offset(c.x, c.y - 14.dp.toPx()), strokeWidth = 2.dp.toPx())
        drawLine(Color.White, Offset(c.x, c.y + 14.dp.toPx()), Offset(c.x, c.y + 27.dp.toPx()), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun CoordinatePill(modifier: Modifier, x: String, y: String, z: String, plane: String) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(GlassDark).border(1.dp, Color.White.copy(0.18f), RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$x   |   $y   |   $z", color = Color.White, fontSize = 14.sp)
        Spacer(Modifier.height(5.dp))
        Text("Plane: $plane", color = Color.White, fontSize = 13.sp)
    }
}

@Composable
private fun UndoButton(modifier: Modifier) {
    Box(modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(GlassLight), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Undo, null, tint = Color.White)
    }
}

@Composable
private fun PrimaryTriggerButton(modifier: Modifier, text: String) {
    Button(
        onClick = {},
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (text.contains("tag")) ArBlue else ArCyan)
    ) { Text(text, color = Color.White, fontWeight = FontWeight.Bold) }
}

@Composable
private fun TagMenuPanel(modifier: Modifier = Modifier) {
    var selectedPlane by remember { mutableStateOf("Voorzijde") }
    var x by remember { mutableStateOf("0") }
    var y by remember { mutableStateOf("50") }
    var z by remember { mutableStateOf("12") }
    GlassPanel(modifier.background(PanelDark, RoundedCornerShape(28.dp))) {
        LazyColumn(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tag menu", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
            item { Text("1. Kies vlak (transformatorzijde)", color = Color.White, fontWeight = FontWeight.SemiBold) }
            item {
                TransformerUnfoldedSchema(selectedPlane, Modifier.fillMaxWidth().height(175.dp), showMarkers = false) { selectedPlane = it }
            }
            item { Text("2. Selecteer positie op vlak ($selectedPlane)", color = Color.White, fontWeight = FontWeight.SemiBold) }
            item { GridCoordinateSelector(Modifier.fillMaxWidth().height(190.dp)) }
            item { Text("Of voer coördinaten handmatig in", color = Color.White, fontWeight = FontWeight.SemiBold) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoordinateInput("X (mm)", x, { x = it }, Modifier.weight(1f))
                    CoordinateInput("Y (cm)", y, { y = it }, Modifier.weight(1f))
                    CoordinateInput("Z (cm)", z, { z = it }, Modifier.weight(1f))
                }
            }
            item { SectionList("Geplaatste tags", demoTags, compact = true, showUndo = true) }
            item { PrimaryTriggerButton(Modifier.fillMaxWidth(), "Plaats tag") }
        }
    }
}

@Composable
private fun CoordinateInput(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label, color = TextMuted, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.height(64.dp),
        textStyle = LocalTextStyle.current.copy(color = Color.White),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.White.copy(0.35f),
            unfocusedBorderColor = Color.White.copy(0.16f),
            focusedContainerColor = GlassLight,
            unfocusedContainerColor = GlassLight
        )
    )
}

@Composable
private fun GridCoordinateSelector(modifier: Modifier) {
    var selected by remember { mutableStateOf(Triple(1, 2, 0)) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Y", color = Color.White)
            Text("120 cm", color = TextMuted, fontSize = 11.sp)
            Box(Modifier.width(1.dp).height(100.dp).background(Color.White.copy(0.6f)))
            Text("0 cm", color = TextMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            for (row in 0 until 5) {
                Row {
                    for (col in 0 until 5) {
                        val active = selected.first == col && selected.second == row
                        Box(
                            Modifier.padding(3.dp).size(32.dp).clip(RoundedCornerShape(7.dp)).background(if (active) ArBlue.copy(0.8f) else Color.White.copy(0.18f)).border(1.dp, Color.White.copy(0.35f), RoundedCornerShape(7.dp)).clickable { selected = Triple(col, row, 0) },
                            contentAlignment = Alignment.Center
                        ) { if (active) Dot(Color.White, 5) }
                    }
                }
            }
            Row(Modifier.width(190.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("X  0 cm", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("200 cm", color = TextMuted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Z", color = Color.White, fontWeight = FontWeight.Bold)
            Text("Boven", color = TextMuted, fontSize = 11.sp)
            MiniAxis()
            Text("Voor", color = TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun TransformerUnfoldedSchema(selectedPlane: String, modifier: Modifier, showMarkers: Boolean, onPlane: (String) -> Unit = {}) {
    Canvas(modifier) {
        val cX = size.width / 2f
        val cY = size.height / 2f
        val w = size.width * 0.22f
        val h = size.height * 0.28f
        fun planeRect(name: String, left: Float, top: Float) {
            val active = name == selectedPlane
            drawRect(if (active) ArBlue.copy(0.35f) else Color.White.copy(0.08f), Offset(left, top), Size(w, h))
            drawRect(if (active) ArBlue else Color.White.copy(0.45f), Offset(left, top), Size(w, h), style = Stroke(if (active) 3f else 1.3f))
            for (i in 1..5) drawLine(Color.White.copy(0.18f), Offset(left + i * w / 6, top + h * 0.25f), Offset(left + i * w / 6, top + h * 0.75f), 1.2f)
        }
        planeRect("Voorzijde", cX - w / 2, cY + h * 0.45f)
        planeRect("Boven", cX - w / 2, cY - h * 1.15f)
        planeRect("Links", cX - w * 1.55f, cY - h * 0.25f)
        planeRect("Rechts", cX + w * 0.55f, cY - h * 0.25f)
        planeRect("Achterzijde", cX - w / 2, cY - h * 0.25f)
        if (showMarkers) {
            marker(cX, cY + h * 0.85f, "S001", ArCyan)
            marker(cX - w * 1.2f, cY, "T002", ArBlue)
            marker(cX + w * 0.15f, cY - h * 0.75f, "T021", ArBlue)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.marker(x: Float, y: Float, label: String, color: Color) {
    drawCircle(color, 10f, Offset(x, y))
    drawRoundRect(Color(0xAA1B2430), Offset(x + 12f, y - 20f), Size(80f, 34f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f))
}

@Composable
private fun SectionList(title: String, list: List<PlacedItem>, compact: Boolean = false, showUndo: Boolean = false) {
    GlassPanel(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(if (compact) 10.dp else 14.dp)) {
            Text(title, color = Color.White, fontSize = if (compact) 16.sp else 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            list.forEach { ItemRow(it, compact, showUndo) }
        }
    }
}

@Composable
private fun ItemRow(item: PlacedItem, compact: Boolean, showUndo: Boolean) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(0.08f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(if (item.type == ItemType.Sensor) ArCyan else ArBlue)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(item.id, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = if (compact) 13.sp else 15.sp)
            Text("X: ${item.x}   |   Y: ${item.y}   |   Z: ${item.z}", color = TextMuted, fontSize = if (compact) 11.sp else 13.sp)
        }
        if (showUndo) Icon(Icons.Default.Undo, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun GlassPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(GlassDark).border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(22.dp))) { content() }
}

@Composable
private fun ReportMeta(label: String, value: String) {
    Column { Text(label, color = TextMuted, fontSize = 12.sp); Text(value, color = Color.White, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ActionTile(text: String, icon: ImageVector, bg: Color, modifier: Modifier) {
    Column(modifier.height(84.dp).clip(RoundedCornerShape(16.dp)).background(bg).border(1.dp, Color.White.copy(0.14f), RoundedCornerShape(16.dp)).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = Color.White)
        Spacer(Modifier.height(6.dp))
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LayerSwitchPanel(modifier: Modifier = Modifier) {
    GlassPanel(modifier.width(150.dp)) {
        Column(Modifier.padding(10.dp)) {
            LayerToggle("Tags", ArBlue)
            LayerToggle("Sensoren", ArCyan)
        }
    }
}

@Composable
private fun LayerToggle(label: String, color: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Switch(checked = true, onCheckedChange = {}, modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun ScreenTopBar(title: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.ArrowBack, null, tint = Color.White)
        Spacer(Modifier.width(14.dp))
        Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallGlassButton(label: String, icon: ImageVector) {
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(GlassDark).border(1.dp, Color.White.copy(0.16f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun Transformer3DMock(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF1D2732), Color(0xFF08111B))))
        val body = Rect(w * 0.20f, h * 0.30f, w * 0.78f, h * 0.68f)
        drawRect(Color(0xFF303A34), body.topLeft, body.size)
        for (i in 0..12) {
            val x = body.left + i * body.width / 12
            drawLine(Color(0xFF778073), Offset(x, body.top), Offset(x, body.bottom), strokeWidth = 3f)
        }
        for (i in 0..5) drawCircle(Color(0xFF514A3D), 13f, Offset(w * (0.3f + i * 0.08f), h * 0.25f))
        drawCircle(ArBlue, 10f, Offset(w * 0.25f, h * 0.46f))
        drawCircle(ArCyan, 10f, Offset(w * 0.56f, h * 0.47f))
        drawCircle(ArBlue, 10f, Offset(w * 0.70f, h * 0.34f))
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = TextMuted, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MiniAxis() {
    Canvas(Modifier.size(54.dp)) {
        val c = center
        drawLine(Color.White.copy(0.7f), c, Offset(c.x, c.y - 22f), 2f)
        drawLine(Color.White.copy(0.7f), c, Offset(c.x + 22f, c.y + 16f), 2f)
        drawLine(Color.White.copy(0.7f), c, Offset(c.x - 22f, c.y + 16f), 2f)
    }
}

@Composable
private fun Dot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}
