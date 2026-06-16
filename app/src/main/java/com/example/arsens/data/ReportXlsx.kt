package com.example.arsens.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

object ReportXlsx {
    fun build(project: Project, log: InstallationLog): ByteArray {
        val sheets = listOf(
            tableSheet("Samenvatting", summaryRows(project, log)),
            tableSheet("Sensoren", sensorRows(project, log)),
            tableSheet("Tags", tagRows(project)),
            model2dSheet(project, log, MapProjection.Top, "2D Boven"),
            model2dSheet(project, log, MapProjection.Front, "2D Voor"),
            model2dSheet(project, log, MapProjection.Back, "2D Achter"),
            model2dSheet(project, log, MapProjection.Left, "2D Links"),
            model2dSheet(project, log, MapProjection.Right, "2D Rechts"),
            model3dSheet(project, log)
        )
        return XlsxPackage(sheets).toByteArray()
    }

    private fun summaryRows(project: Project, log: InstallationLog): List<List<XlsxCell>> {
        val results = log.results.associateBy { it.sensorId }
        val okCount = project.sensors.count { sensor -> (results[sensor.id]?.status ?: sensor.status) == SensorStatus.Ok }
        val failCount = project.sensors.count { sensor -> (results[sensor.id]?.status ?: sensor.status) == SensorStatus.Fail }
        return listOf(
            row("Project", project.projectName),
            row("Gestart", log.startedAt),
            row("Operator", log.operator),
            row("Afmetingen X/Y/Z mm", "${project.dimensionsMm.x}/${project.dimensionsMm.y}/${project.dimensionsMm.z}"),
            row("Sensoren", project.sensors.size),
            row("Tags", project.markers.count { it.isAprilTagCalibrationMarker() }),
            row("OK", okCount),
            row("Fout", failCount)
        )
    }

    private fun sensorRows(project: Project, log: InstallationLog): List<List<XlsxCell>> {
        val results = log.results.associateBy { it.sensorId }
        return listOf(
            row("Order", "Sensor ID", "Naam", "Status", "X mm", "Y mm", "Z mm", "Gemeten X", "Gemeten Y", "Gemeten Z", "Fout mm", "Foto", "Bevestigd")
        ) + project.sensors.sortedBy { it.order }.map { sensor ->
            val result = results[sensor.id]
            val measured = result?.measuredPositionMm
            row(
                sensor.order,
                sensor.id,
                sensor.name,
                (result?.status ?: sensor.status).label,
                sensor.positionMm.x,
                sensor.positionMm.y,
                sensor.positionMm.z,
                measured?.x,
                measured?.y,
                measured?.z,
                result?.distanceErrorMm,
                result?.photoFile.orEmpty(),
                result?.confirmedAt.orEmpty()
            )
        }
    }

    private fun tagRows(project: Project): List<List<XlsxCell>> =
        listOf(
            row("Tag ID", "Type", "Actief", "Formaat mm", "X mm", "Y mm", "Z mm", "Rot X", "Rot Y", "Rot Z")
        ) + project.markers
            .filter { it.isAprilTagCalibrationMarker() }
            .sortedBy { it.id }
            .map { marker ->
                row(
                    marker.id,
                    marker.type,
                    if (marker.active) "Ja" else "Nee",
                    marker.sizeMm,
                    marker.positionMm.x,
                    marker.positionMm.y,
                    marker.positionMm.z,
                    marker.rotationDeg.x,
                    marker.rotationDeg.y,
                    marker.rotationDeg.z
                )
            }

    private fun model2dSheet(
        project: Project,
        log: InstallationLog,
        projection: MapProjection,
        name: String
    ): XlsxSheet {
        val cells = SheetCells()
        val anchorColumn = 2
        val anchorRow = 4

        cells.put(1, anchorColumn, XlsxCell.Text(name, XlsxStyle.Title))
        cells.put(
            2,
            anchorColumn,
            XlsxCell.Text(
                "${projection.label} | ${projection.horizontalMm(project.dimensionsMm)} x ${projection.verticalMm(project.dimensionsMm)} mm",
                XlsxStyle.Subtitle
            )
        )

        // Render een echte afbeelding (kopie) van het 2D-model i.p.v. losse cellen.
        // runCatching: in een puur-JVM unit-test bestaat android.graphics niet → terugvallen
        // op alleen titel + exacte-mm tabel (de export blijft dan geldig).
        val rendered = runCatching { render2dModelPng(project, log, projection) }.getOrNull()
        val image = rendered?.let {
            SheetImage(
                pngBytes = it.bytes,
                widthPx = it.widthPx,
                heightPx = it.heightPx,
                fromColumn = anchorColumn,
                fromRow = anchorRow
            )
        }
        // Hoogte van de afbeelding omgerekend naar rijen (standaard rijhoogte ~20 px) zodat de
        // tabel eronder de afbeelding niet overlapt.
        val imageRowSpan = if (rendered != null) {
            (rendered.heightPx / 20.0).roundToInt().coerceAtLeast(8)
        } else {
            0
        }

        val legendRow = anchorRow + imageRowSpan + 1
        cells.put(legendRow, anchorColumn, XlsxCell.Text("T = AprilTag, cirkel = sensor (kleur = status), M = gemeten positie.", XlsxStyle.Note))
        cells.put(legendRow + 1, anchorColumn, XlsxCell.Text("Afbeelding is een kopie van het 2D-model; tabel hieronder bevat de exacte mm.", XlsxStyle.Note))

        val tableStartRow = legendRow + 3
        cells.addTable(tableStartRow, anchorColumn, model2dRows(project, projection))

        val widths = mutableMapOf<Int, Double>()
        for (column in anchorColumn until anchorColumn + 8) {
            widths[column] = 16.0
        }
        return XlsxSheet(
            name = name,
            cells = cells.toList(),
            columnWidths = widths,
            freezeRows = 0,
            image = image
        )
    }

    private data class RenderedPng(val bytes: ByteArray, val widthPx: Int, val heightPx: Int)

    /** Tekent een kopie van het 2D-model (zelfde look als het scherm) naar een PNG. */
    private fun render2dModelPng(
        project: Project,
        log: InstallationLog,
        projection: MapProjection
    ): RenderedPng {
        val dims = project.dimensionsMm
        val hMm = projection.horizontalMm(dims).toFloat()
        val vMm = projection.verticalMm(dims).toFloat()
        val aspect = (hMm / vMm).coerceIn(0.2f, 5f)

        val margin = 70f
        val maxMap = 920f
        val mapW: Float
        val mapH: Float
        if (aspect >= 1f) {
            mapW = maxMap
            mapH = maxMap / aspect
        } else {
            mapH = maxMap
            mapW = maxMap * aspect
        }
        val width = (mapW + margin * 2f).roundToInt().coerceAtLeast(1)
        val height = (mapH + margin * 2f).roundToInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFF7F9FB.toInt())

        val originX = margin
        val originY = margin
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1F2933.toInt()
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // Witte box
        fill.color = 0xFFFFFFFF.toInt()
        canvas.drawRect(originX, originY, originX + mapW, originY + mapH, fill)
        // Rasterlijnen
        stroke.color = 0xFFD8E0E8.toInt()
        stroke.strokeWidth = 1.5f
        for (i in 0..4) {
            val x = originX + mapW * i / 4f
            canvas.drawLine(x, originY, x, originY + mapH, stroke)
        }
        for (i in 0..3) {
            val y = originY + mapH * i / 3f
            canvas.drawLine(originX, y, originX + mapW, y, stroke)
        }
        // Rand
        stroke.color = 0xFF415466.toInt()
        stroke.strokeWidth = 4f
        canvas.drawRect(originX, originY, originX + mapW, originY + mapH, stroke)

        fun toPx(position: MmPosition): Pair<Float, Float> {
            val projected = projection.project(position, dims)
            val hr = (projected.first.toFloat() / hMm).coerceIn(0f, 1f)
            val vr = (projected.second.toFloat() / vMm).coerceIn(0f, 1f)
            return (originX + hr * mapW) to (originY + (1f - vr) * mapH)
        }

        val results = log.results.associateBy { it.sensorId }
        project.markers
            .filter { it.isAprilTagCalibrationMarker() }
            .sortedBy { it.id }
            .forEach { marker ->
                val (x, y) = toPx(marker.positionMm)
                fill.color = if (marker.active) 0xFF9C27B0.toInt() else 0xFF7E8792.toInt()
                canvas.drawRect(x - 9f, y - 9f, x + 9f, y + 9f, fill)
                canvas.drawText("T${marker.id}", x + 12f, y - 8f, text)
            }
        project.sensors.sortedBy { it.order }.forEach { sensor ->
            val (x, y) = toPx(sensor.positionMm)
            val status = results[sensor.id]?.status ?: sensor.status
            val statusColor = statusArgb(status)
            results[sensor.id]?.measuredPositionMm?.let { measured ->
                val (mx, my) = toPx(measured)
                stroke.color = 0xFF5D6B76.toInt()
                stroke.strokeWidth = 2f
                canvas.drawLine(x, y, mx, my, stroke)
                stroke.color = statusColor
                stroke.strokeWidth = 3f
                canvas.drawCircle(mx, my, 11f, stroke)
            }
            fill.color = statusColor
            canvas.drawCircle(x, y, 11f, fill)
            stroke.color = 0xFFFFFFFF.toInt()
            stroke.strokeWidth = 2f
            canvas.drawCircle(x, y, 11f, stroke)
            canvas.drawText(sensor.id, x + 13f, y + 8f, text)
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return RenderedPng(out.toByteArray(), width, height)
    }

    private fun statusArgb(status: SensorStatus): Int =
        when (status) {
            SensorStatus.Ok -> 0xFF0F766E.toInt()
            SensorStatus.Fail -> 0xFFDC2626.toInt()
            SensorStatus.Pending -> 0xFFF59E0B.toInt()
        }

    private fun model2dRows(project: Project, projection: MapProjection): List<List<XlsxCell>> {
        val header = row("Type", "ID", "Naam", "H mm", "V mm", "Bron X", "Bron Y", "Bron Z")
        val tags = project.markers
            .filter { it.isAprilTagCalibrationMarker() }
            .sortedBy { it.id }
            .map { marker ->
                val point = projection.project(marker.positionMm, project.dimensionsMm)
                row("Tag", "T${marker.id}", marker.type, point.first, point.second, marker.positionMm.x, marker.positionMm.y, marker.positionMm.z)
            }
        val sensors = project.sensors.sortedBy { it.order }.map { sensor ->
            val point = projection.project(sensor.positionMm, project.dimensionsMm)
            row("Sensor", sensor.id, sensor.name, point.first, point.second, sensor.positionMm.x, sensor.positionMm.y, sensor.positionMm.z)
        }
        return listOf(header) + tags + sensors
    }

    private fun model3dSheet(project: Project, log: InstallationLog): XlsxSheet {
        val cells = SheetCells()
        val anchorColumn = 2
        val anchorRow = 4

        cells.put(1, anchorColumn, XlsxCell.Text("3D Model", XlsxStyle.Title))
        cells.put(2, anchorColumn, XlsxCell.Text("Isometrische kopie met tags en sensoren", XlsxStyle.Subtitle))

        val rendered = runCatching { render3dModelPng(project, log) }.getOrNull()
        val image = rendered?.let {
            SheetImage(it.bytes, it.widthPx, it.heightPx, fromColumn = anchorColumn, fromRow = anchorRow)
        }
        val imageRowSpan = if (rendered != null) {
            (rendered.heightPx / 20.0).roundToInt().coerceAtLeast(8)
        } else {
            0
        }

        val legendRow = anchorRow + imageRowSpan + 1
        cells.put(legendRow, anchorColumn, XlsxCell.Text("Paars = AprilTag, cirkel = sensor (kleur = status), M = gemeten positie.", XlsxStyle.Note))
        cells.put(legendRow + 1, anchorColumn, XlsxCell.Text("Afbeelding is een kopie van het 3D-model; tabel hieronder bevat de exacte mm.", XlsxStyle.Note))

        val tableStartRow = legendRow + 3
        cells.addTable(tableStartRow, anchorColumn, model3dRows(project))

        val widths = mutableMapOf<Int, Double>()
        for (column in anchorColumn until anchorColumn + 8) {
            widths[column] = 16.0
        }
        return XlsxSheet(
            name = "3D Model",
            cells = cells.toList(),
            columnWidths = widths,
            freezeRows = 0,
            image = image
        )
    }

    /** Tekent een isometrische kopie van het 3D-model (box, tags, sensoren) naar een PNG. */
    private fun render3dModelPng(project: Project, log: InstallationLog): RenderedPng {
        val positions = project.sensors.map { it.positionMm } + project.markers.map { it.positionMm }
        val dimensions = MmPosition(
            x = maxOf(project.dimensionsMm.x, positions.maxOfOrNull { it.x } ?: 0),
            y = maxOf(project.dimensionsMm.y, positions.maxOfOrNull { it.y } ?: 0),
            z = maxOf(project.dimensionsMm.z, positions.maxOfOrNull { it.z } ?: 0)
        )
        val corners = listOf(
            MmPosition(0, 0, 0),
            MmPosition(dimensions.x, 0, 0),
            MmPosition(dimensions.x, dimensions.y, 0),
            MmPosition(0, dimensions.y, 0),
            MmPosition(0, 0, dimensions.z),
            MmPosition(dimensions.x, 0, dimensions.z),
            MmPosition(dimensions.x, dimensions.y, dimensions.z),
            MmPosition(0, dimensions.y, dimensions.z)
        )
        val allRaw = (corners + positions).map { it.toIsoRaw(dimensions) }
        val minX = allRaw.minOfOrNull { it.first } ?: 0.0
        val maxX = allRaw.maxOfOrNull { it.first } ?: 1.0
        val minY = allRaw.minOfOrNull { it.second } ?: 0.0
        val maxY = allRaw.maxOfOrNull { it.second } ?: 1.0
        val spanX = (maxX - minX).takeIf { it > 0.0 } ?: 1.0
        val spanY = (maxY - minY).takeIf { it > 0.0 } ?: 1.0

        val margin = 70f
        val maxDim = 900f
        val aspect = (spanX / spanY).coerceIn(0.25, 4.0)
        val mapW: Float
        val mapH: Float
        if (aspect >= 1.0) {
            mapW = maxDim
            mapH = (maxDim / aspect).toFloat()
        } else {
            mapH = maxDim
            mapW = (maxDim * aspect).toFloat()
        }
        val width = (mapW + margin * 2f).roundToInt().coerceAtLeast(1)
        val height = (mapH + margin * 2f).roundToInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(0xFFF7F9FB.toInt())
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF1F2933.toInt()
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        fun toPx(position: MmPosition): Pair<Float, Float> {
            val raw = position.toIsoRaw(dimensions)
            val rx = ((raw.first - minX) / spanX).toFloat().coerceIn(0f, 1f)
            val ry = ((raw.second - minY) / spanY).toFloat().coerceIn(0f, 1f)
            return (margin + rx * mapW) to (margin + ry * mapH)
        }

        val cornerPx = corners.map { toPx(it) }
        stroke.color = 0xFF415466.toInt()
        stroke.strokeWidth = 3f
        listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7
        ).forEach { (a, b) ->
            canvas.drawLine(cornerPx[a].first, cornerPx[a].second, cornerPx[b].first, cornerPx[b].second, stroke)
        }

        val results = log.results.associateBy { it.sensorId }
        project.markers
            .filter { it.isAprilTagCalibrationMarker() }
            .sortedBy { it.id }
            .forEach { marker ->
                val (x, y) = toPx(marker.positionMm)
                fill.color = if (marker.active) 0xFF9C27B0.toInt() else 0xFF7E8792.toInt()
                canvas.drawRect(x - 8f, y - 8f, x + 8f, y + 8f, fill)
                canvas.drawText("T${marker.id}", x + 11f, y - 7f, text)
            }
        project.sensors.sortedBy { it.order }.forEach { sensor ->
            val (x, y) = toPx(sensor.positionMm)
            val status = results[sensor.id]?.status ?: sensor.status
            val statusColor = statusArgb(status)
            results[sensor.id]?.measuredPositionMm?.let { measured ->
                val (mx, my) = toPx(measured)
                stroke.color = 0xFF5D6B76.toInt()
                stroke.strokeWidth = 2f
                canvas.drawLine(x, y, mx, my, stroke)
                stroke.color = statusColor
                stroke.strokeWidth = 3f
                canvas.drawCircle(mx, my, 10f, stroke)
            }
            fill.color = statusColor
            canvas.drawCircle(x, y, 9f, fill)
            canvas.drawText(sensor.id, x + 11f, y + 7f, text)
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return RenderedPng(out.toByteArray(), width, height)
    }

    private fun model3dRows(project: Project): List<List<XlsxCell>> {
        val header = row("Type", "ID", "Naam", "X mm", "Y mm", "Z mm", "Iso X", "Iso Y")
        val tags = project.markers
            .filter { it.isAprilTagCalibrationMarker() }
            .sortedBy { it.id }
            .map { marker -> model3dRow("Tag", "T${marker.id}", marker.type, marker.positionMm, project.dimensionsMm) }
        val sensors = project.sensors
            .sortedBy { it.order }
            .map { sensor -> model3dRow("Sensor", sensor.id, sensor.name, sensor.positionMm, project.dimensionsMm) }
        return listOf(header) + tags + sensors
    }

    private fun model3dRow(type: String, id: String, name: String, position: MmPosition, dimensions: MmPosition): List<XlsxCell> {
        val raw = position.toIsoRaw(dimensions)
        return row(type, id, name, position.x, position.y, position.z, raw.first.roundToInt(), raw.second.roundToInt())
    }

    private fun row(vararg values: Any?): List<XlsxCell> =
        values.map { value ->
            when (value) {
                null -> XlsxCell.Blank()
                is Number -> XlsxCell.Number(value.toDouble())
                is Boolean -> XlsxCell.Text(if (value) "TRUE" else "FALSE")
                else -> XlsxCell.Text(value.toString())
            }
        }
}

private enum class MapProjection(val label: String) {
    Top("Bovenaanzicht"),
    Front("Vooraanzicht"),
    Back("Achteraanzicht"),
    Left("Linker zijaanzicht"),
    Right("Rechter zijaanzicht");

    fun horizontalMm(dimensions: MmPosition): Int =
        when (this) {
            Top, Front, Back -> dimensions.x
            Left, Right -> dimensions.y
        }.coerceAtLeast(1)

    fun verticalMm(dimensions: MmPosition): Int =
        when (this) {
            Top -> dimensions.y
            Front, Back, Left, Right -> dimensions.z
        }.coerceAtLeast(1)

    fun project(position: MmPosition, dimensions: MmPosition): Pair<Int, Int> =
        when (this) {
            Top -> position.x to position.y
            Front -> position.x to position.z
            Back -> dimensions.x - position.x to position.z
            Left -> dimensions.y - position.y to position.z
            Right -> position.y to position.z
        }
}

private sealed class XlsxCell(open val style: XlsxStyle? = null) {
    data class Blank(override val style: XlsxStyle? = null) : XlsxCell(style)
    data class Text(val value: String, override val style: XlsxStyle? = null) : XlsxCell(style)
    data class Number(val value: Double, override val style: XlsxStyle? = null) : XlsxCell(style)
}

private enum class XlsxStyle {
    Header,
    Title,
    Subtitle,
    PreviewBackground,
    Model,
    ModelLine,
    Tag,
    TagInactive,
    Sensor,
    SensorMeasured,
    SensorFail,
    SensorPending,
    Note
}

private data class XlsxCellAt(
    val row: Int,
    val column: Int,
    val cell: XlsxCell
)

private data class XlsxSheet(
    val name: String,
    val cells: List<XlsxCellAt>,
    val columnWidths: Map<Int, Double> = emptyMap(),
    val rowHeights: Map<Int, Double> = emptyMap(),
    val defaultColumnWidth: Double = 18.0,
    val freezeRows: Int = 1,
    val image: SheetImage? = null
)

/** Eén ingesloten PNG-afbeelding op een werkblad, verankerd vanaf een cel (1-gebaseerd). */
private data class SheetImage(
    val pngBytes: ByteArray,
    val widthPx: Int,
    val heightPx: Int,
    val fromColumn: Int,
    val fromRow: Int
)

private class SheetCells {
    private val cells = linkedMapOf<Pair<Int, Int>, XlsxCell>()

    fun put(row: Int, column: Int, cell: XlsxCell) {
        cells[row to column] = cell
    }

    fun addTable(startRow: Int, startColumn: Int, rows: List<List<XlsxCell>>) {
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { columnIndex, cell ->
                put(
                    row = startRow + rowIndex,
                    column = startColumn + columnIndex,
                    cell = if (rowIndex == 0) cell.withStyle(XlsxStyle.Header) else cell
                )
            }
        }
    }

    fun toList(): List<XlsxCellAt> =
        cells.map { (key, cell) -> XlsxCellAt(key.first, key.second, cell) }
}

private fun tableSheet(name: String, rows: List<List<XlsxCell>>): XlsxSheet {
    val cells = SheetCells()
    cells.addTable(1, 1, rows)
    val widths = (1..(rows.maxOfOrNull { it.size } ?: 1)).associateWith { 18.0 }
    return XlsxSheet(name = name, cells = cells.toList(), columnWidths = widths)
}

private fun XlsxCell.withStyle(style: XlsxStyle): XlsxCell =
    when (this) {
        is XlsxCell.Blank -> copy(style = style)
        is XlsxCell.Number -> copy(style = style)
        is XlsxCell.Text -> copy(style = style)
    }

private fun MmPosition.toIsoRaw(dimensions: MmPosition): Pair<Double, Double> {
    // Canoniek box-frame: x=0 = fysiek links. Gebruik x RECHTSTREEKS (niet dimensions.x - x), zodat
    // de iso-export dezelfde links/rechts-conventie heeft als de 2D-kaart (Front = position.x) en de
    // in-app vrije 3D. De oude (dimensions.x - x) spiegelde X en zette x=0 rechts in beeld.
    val viewX = x
    val viewY = dimensions.y - y
    return (viewX - viewY).toDouble() to ((viewX + viewY) * 0.28 - z * 0.78)
}

private class XlsxPackage(private val sheets: List<XlsxSheet>) {
    fun toByteArray(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.text("[Content_Types].xml", contentTypes())
            zip.text("_rels/.rels", rootRels())
            zip.text("xl/workbook.xml", workbookXml())
            zip.text("xl/_rels/workbook.xml.rels", workbookRels())
            zip.text("xl/styles.xml", stylesXml())
            sheets.forEachIndexed { index, sheet ->
                val sheetNo = index + 1
                zip.text("xl/worksheets/sheet$sheetNo.xml", worksheetXml(sheet))
                val image = sheet.image
                if (image != null) {
                    zip.text("xl/worksheets/_rels/sheet$sheetNo.xml.rels", worksheetDrawingRels(sheetNo))
                    zip.text("xl/drawings/drawing$sheetNo.xml", drawingXml(image))
                    zip.text("xl/drawings/_rels/drawing$sheetNo.xml.rels", drawingImageRels(sheetNo))
                    zip.binary("xl/media/image$sheetNo.png", image.pngBytes)
                }
            }
        }
        return output.toByteArray()
    }

    private fun contentTypes(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Default Extension="png" ContentType="image/png"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        sheets.indices.forEach { index ->
            append("""<Override PartName="/xl/worksheets/sheet${index + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        sheets.forEachIndexed { index, sheet ->
            if (sheet.image != null) {
                append("""<Override PartName="/xl/drawings/drawing${index + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""")
            }
        }
        append("</Types>")
    }

    private fun rootRels(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun worksheetDrawingRels(sheetNo: Int): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing$sheetNo.xml"/></Relationships>"""

    private fun drawingImageRels(sheetNo: Int): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image$sheetNo.png"/></Relationships>"""

    private fun drawingXml(image: SheetImage): String {
        val emuPerPixel = 9525L
        val cx = image.widthPx.toLong() * emuPerPixel
        val cy = image.heightPx.toLong() * emuPerPixel
        val fromCol = (image.fromColumn - 1).coerceAtLeast(0)
        val fromRow = (image.fromRow - 1).coerceAtLeast(0)
        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">""")
            append("""<xdr:oneCellAnchor>""")
            append("""<xdr:from><xdr:col>$fromCol</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>$fromRow</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>""")
            append("""<xdr:ext cx="$cx" cy="$cy"/>""")
            append("""<xdr:pic>""")
            append("""<xdr:nvPicPr><xdr:cNvPr id="2" name="Model2D"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr>""")
            append("""<xdr:blipFill><a:blip xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" r:embed="rId1"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>""")
            append("""<xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr>""")
            append("""</xdr:pic>""")
            append("""<xdr:clientData/>""")
            append("""</xdr:oneCellAnchor>""")
            append("""</xdr:wsDr>""")
        }
    }

    private fun workbookRels(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        sheets.indices.forEach { index ->
            append("""<Relationship Id="rId${index + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${index + 1}.xml"/>""")
        }
        append("""<Relationship Id="rId${sheets.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun workbookXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { index, sheet ->
            append("""<sheet name="${sheet.name.xmlEscape()}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun stylesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <fonts count="5">
            <font><sz val="11"/><name val="Calibri"/></font>
            <font><b/><color rgb="FFFFFFFF"/><sz val="11"/><name val="Calibri"/></font>
            <font><b/><color rgb="FF111827"/><sz val="14"/><name val="Calibri"/></font>
            <font><color rgb="FF52616F"/><sz val="10"/><name val="Calibri"/></font>
            <font><b/><color rgb="FFFFFFFF"/><sz val="9"/><name val="Calibri"/></font>
        </fonts>
        <fills count="13">
            <fill><patternFill patternType="none"/></fill>
            <fill><patternFill patternType="gray125"/></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FF1F4E5F"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFF7F9FB"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFEAF1F6"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FF415466"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FF9C27B0"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FF7E8792"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FF0F766E"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FF2563EB"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFDC2626"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFF59E0B"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFFFF7D6"/><bgColor indexed="64"/></patternFill></fill>
        </fills>
        <borders count="2">
            <border><left/><right/><top/><bottom/><diagonal/></border>
            <border><left style="thin"><color rgb="FFD8E0E8"/></left><right style="thin"><color rgb="FFD8E0E8"/></right><top style="thin"><color rgb="FFD8E0E8"/></top><bottom style="thin"><color rgb="FFD8E0E8"/></bottom><diagonal/></border>
        </borders>
        <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
        <cellXfs count="14">
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
            <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFill="1" applyFont="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
            <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
            <xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>
            <xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1"/>
            <xf numFmtId="0" fontId="0" fillId="4" borderId="1" xfId="0" applyFill="1" applyBorder="1"/>
            <xf numFmtId="0" fontId="0" fillId="5" borderId="1" xfId="0" applyFill="1" applyBorder="1"/>
            <xf numFmtId="0" fontId="4" fillId="6" borderId="1" xfId="0" applyFill="1" applyFont="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
            <xf numFmtId="0" fontId="4" fillId="7" borderId="1" xfId="0" applyFill="1" applyFont="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
            <xf numFmtId="0" fontId="4" fillId="8" borderId="1" xfId="0" applyFill="1" applyFont="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
            <xf numFmtId="0" fontId="4" fillId="9" borderId="1" xfId="0" applyFill="1" applyFont="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
            <xf numFmtId="0" fontId="4" fillId="10" borderId="1" xfId="0" applyFill="1" applyFont="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
            <xf numFmtId="0" fontId="4" fillId="11" borderId="1" xfId="0" applyFill="1" applyFont="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
            <xf numFmtId="0" fontId="0" fillId="12" borderId="0" xfId="0" applyFill="1"/>
        </cellXfs>
        </styleSheet>""".trimIndent()

    private fun worksheetXml(sheet: XlsxSheet): String = buildString {
        val maxCols = sheet.cells.maxOfOrNull { it.column } ?: 1
        val maxRows = sheet.cells.maxOfOrNull { it.row } ?: 1
        val byRow = sheet.cells.groupBy { it.row }.toSortedMap()
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        if (sheet.image != null) {
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        } else {
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        }
        append("""<dimension ref="A1:${columnName(maxCols)}$maxRows"/>""")
        if (sheet.freezeRows > 0) {
            val nextCell = "A${sheet.freezeRows + 1}"
            append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="${sheet.freezeRows}" topLeftCell="$nextCell" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        } else {
            append("""<sheetViews><sheetView workbookViewId="0"/></sheetViews>""")
        }
        append("""<cols>""")
        for (column in 1..maxCols) {
            val width = sheet.columnWidths[column] ?: sheet.defaultColumnWidth
            append("""<col min="$column" max="$column" width="${width.formatWidth()}" customWidth="1"/>""")
        }
        append("""</cols><sheetData>""")
        val rowsToWrite = ((1..maxRows).filter { byRow.containsKey(it) || sheet.rowHeights.containsKey(it) }).toSortedSet()
        rowsToWrite.forEach { rowNumber ->
            val rowHeight = sheet.rowHeights[rowNumber]
            if (rowHeight != null) {
                append("""<row r="$rowNumber" ht="${rowHeight.formatWidth()}" customHeight="1">""")
            } else {
                append("""<row r="$rowNumber">""")
            }
            byRow[rowNumber]?.sortedBy { it.column }?.forEach { cell ->
                append(cellXml(cell.cell, rowNumber, cell.column))
            }
            append("</row>")
        }
        append("</sheetData>")
        if (sheet.image != null) {
            append("""<drawing r:id="rId1"/>""")
        }
        append("</worksheet>")
    }

    private fun cellXml(cell: XlsxCell, row: Int, column: Int): String {
        val ref = "${columnName(column)}$row"
        val style = cell.style?.styleId()?.let { " s=\"$it\"" }.orEmpty()
        return when (cell) {
            is XlsxCell.Blank -> """<c r="$ref"$style/>"""
            is XlsxCell.Number -> {
                val text = if (cell.value % 1.0 == 0.0) {
                    cell.value.toLong().toString()
                } else {
                    String.format(Locale.US, "%.3f", cell.value)
                }
                """<c r="$ref"$style><v>$text</v></c>"""
            }
            is XlsxCell.Text -> """<c r="$ref" t="inlineStr"$style><is><t>${cell.value.xmlEscape()}</t></is></c>"""
        }
    }

    private fun XlsxStyle.styleId(): Int =
        when (this) {
            XlsxStyle.Header -> 1
            XlsxStyle.Title -> 2
            XlsxStyle.Subtitle -> 3
            XlsxStyle.PreviewBackground -> 4
            XlsxStyle.Model -> 5
            XlsxStyle.ModelLine -> 6
            XlsxStyle.Tag -> 7
            XlsxStyle.TagInactive -> 8
            XlsxStyle.Sensor -> 9
            XlsxStyle.SensorMeasured -> 10
            XlsxStyle.SensorFail -> 11
            XlsxStyle.SensorPending -> 12
            XlsxStyle.Note -> 13
        }

    private fun ZipOutputStream.text(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.binary(path: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(path))
        write(bytes)
        closeEntry()
    }

    private fun columnName(index: Int): String {
        var value = index
        val name = StringBuilder()
        while (value > 0) {
            value--
            name.insert(0, ('A'.code + value % 26).toChar())
            value /= 26
        }
        return name.toString()
    }

    private fun Double.formatWidth(): String =
        String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')

    private fun String.xmlEscape(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
