package com.example.arsens.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.arsens.ar.ProjectPointMm
import com.example.arsens.ar.calibration.WallVector
import com.example.arsens.ar.calibration.wallPoint

/** Saved geometry is reprojected in the current scan frame. No text obscures the physical tags. */
@Composable
internal fun WorkflowWallGraphOverlay(state: WorkflowAppState, showTags: Boolean, showLinks: Boolean) {
    if (!showTags && !showLinks) return
    val frame = state.wallScanFrame?.takeIf { it.tracking } ?: return
    val projection = frame.projectionFromReference ?: return
    val tags = state.wallGraphTags
    val graph = state.project.referenceGraph
    val verified = state.wallVerifiedTagIds
    val rejected = state.wallRejectedEdges
    val conflicts = state.wallConflictingTagIds
    Canvas(Modifier.fillMaxSize()) {
        val green = Color(0xFF52D39B); val amber = Color(0xFFFFBD48); val red = Color(0xFFFF667A)
        fun screen(point: WallVector) = projection.project(ProjectPointMm(point.x, point.y, point.z))?.let { Offset(it.xPx, it.yPx) }
        val centers = tags.mapNotNull { tag -> screen(tag.center)?.let { tag.assignment.tagId to it } }.toMap()
        fun arrow(tip: Offset, from: Offset, color: Color) {
            val delta = tip - from
            val length = delta.getDistance()
            if (length < 24.dp.toPx()) return
            val direction = delta / length
            val wing = Offset(-direction.y, direction.x) * 4.dp.toPx()
            val end = tip - direction * 8.dp.toPx()
            val base = end - direction * 8.dp.toPx()
            drawLine(color, end, base + wing, 2.dp.toPx())
            drawLine(color, end, base - wing, 2.dp.toPx())
        }
        if (showLinks) graph.edges.forEachIndexed { index, edge ->
            if (!edge.directSameFrame && edge.fromTagId in verified && edge.toTagId in verified) return@forEachIndexed
            val from = centers[edge.fromTagId] ?: return@forEachIndexed
            val to = centers[edge.toTagId] ?: return@forEachIndexed
            val color = when {
                index in rejected || edge.fromTagId in conflicts || edge.toTagId in conflicts -> red
                edge.directSameFrame && edge.fromTagId in verified && edge.toTagId in verified -> green
                else -> amber
            }
            drawLine(color.copy(alpha = 0.75f), from, to, 1.5.dp.toPx(), pathEffect =
                if (edge.directSameFrame) null else androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),5.dp.toPx())))
            arrow(to, from, color); arrow(from, to, color)
        }
        tags.forEach { tag ->
            val id = tag.assignment.tagId
            val color = if (id in conflicts) red else if (id in verified) green else amber
            val center = centers[id] ?: return@forEach
            if (showTags) {
                val half = tag.assignment.sizeMm / 2.0
                val corners = listOf(WallVector(-half,0.0,half), WallVector(half,0.0,half),
                    WallVector(half,0.0,-half), WallVector(-half,0.0,-half))
                    .map { screen(tag.referenceFromTag.wallPoint(it)) }
                if (corners.all { it != null }) for (i in 0..3)
                    drawLine(color.copy(alpha = 0.7f), corners[i]!!, corners[(i+1)%4]!!, 1.dp.toPx())
                drawCircle(color, 3.dp.toPx(), center)
                if (id == state.wallSelectedTagId) {
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = android.graphics.Color.WHITE; textSize = 14.dp.toPx(); textAlign = android.graphics.Paint.Align.CENTER
                        setShadowLayer(2.dp.toPx(),0f,0f,android.graphics.Color.BLACK)
                    }
                    drawContext.canvas.nativeCanvas.drawText(id.toString(),center.x,center.y-14.dp.toPx(),paint)
                }
            }
            if (id == graph.seedTagId) {
                drawCircle(color, 8.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                drawCircle(color, 12.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            }
        }
    }
}
