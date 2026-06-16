package com.example.arsens.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val ArSensChromeInk = Color(0xFF0F1B33)
internal val ArSensChromeMuted = Color(0xFF5F6D84)
internal val ArSensChromeTeal = Color(0xFF17C7C9)
internal val ArSensChromeBlue = Color(0xFF2378FF)
internal val ArSensChromeDarkGlass = Color(0xAA1B2430)

/** Eén regel in het 3-puntjes (hamburger) menu van de top bar. Zet [isDivider] voor een scheidslijn.
 *  [onClick] staat als laatste zodat de trailing-lambda-syntax `ArSensMenuItem("X") { ... }` werkt. */
internal data class ArSensMenuItem(
    val label: String = "",
    val enabled: Boolean = true,
    val isDivider: Boolean = false,
    val onClick: () -> Unit = {}
)

@Composable
internal fun ArSensGlassTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    overflowItems: List<ArSensMenuItem> = emptyList(),
    actions: @Composable RowScope.() -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }
    val background = if (dark) ArSensChromeDarkGlass else Color.White
    // Lichte variant: zachte ink-rand i.p.v. een felle witte rand (die las als een "witte streep").
    val border = if (dark) Color.White.copy(alpha = 0.16f) else Color(0x14101B33)
    val content = if (dark) Color.White else ArSensChromeInk
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(16.dp),
        color = background,
        border = BorderStroke(1.dp, border),
        shadowElevation = if (dark) 0.dp else 10.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clickable(enabled = onBack != null) { onBack?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(22.dp)) {
                    val stroke = 2.4.dp.toPx()
                    drawLine(
                        content,
                        Offset(size.width * 0.82f, size.height * 0.50f),
                        Offset(size.width * 0.22f, size.height * 0.50f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        content,
                        Offset(size.width * 0.22f, size.height * 0.50f),
                        Offset(size.width * 0.48f, size.height * 0.24f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        content,
                        Offset(size.width * 0.22f, size.height * 0.50f),
                        Offset(size.width * 0.48f, size.height * 0.76f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
            Text(
                text = title.ifBlank { "Trafo 001" },
                color = content,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = actions
            )
            Box {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clickable(enabled = overflowItems.isNotEmpty()) { menuOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(20.dp)) {
                        repeat(3) { index ->
                            drawCircle(
                                color = content.copy(alpha = 0.86f),
                                radius = 1.7.dp.toPx(),
                                center = Offset(size.width / 2f, size.height * (0.24f + index * 0.26f))
                            )
                        }
                    }
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    overflowItems.forEach { item ->
                        if (item.isDivider) {
                            HorizontalDivider()
                        } else {
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                enabled = item.enabled,
                                onClick = {
                                    menuOpen = false
                                    item.onClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArSensCounterPill(
    text: String,
    modifier: Modifier = Modifier,
    dark: Boolean = true
) {
    val background = if (dark) Color(0xCC20262D) else Color(0xD9222830)
    Surface(
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(999.dp),
        color = background,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .padding(0.dp)
            ) {
                Canvas(Modifier.size(8.dp)) {
                    drawCircle(ArSensChromeTeal)
                }
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

