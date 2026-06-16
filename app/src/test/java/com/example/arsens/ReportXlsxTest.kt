package com.example.arsens

import com.example.arsens.data.FloatVector
import com.example.arsens.data.InstallationLog
import com.example.arsens.data.Marker
import com.example.arsens.data.MmPosition
import com.example.arsens.data.Project
import com.example.arsens.data.ReportXlsx
import com.example.arsens.data.Sensor
import com.example.arsens.data.SensorStatus
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportXlsxTest {
    @Test
    fun reportWorkbookContainsExpectedSheets() {
        val project = Project(
            projectName = "Test",
            modelFile = "model.glb",
            dimensionsMm = MmPosition(10_000, 5_000, 3_200),
            sensors = listOf(
                Sensor(
                    order = 1,
                    id = "S1",
                    name = "Sensor 1",
                    side = "boven",
                    positionMm = MmPosition(1_000, 2_000, 3_200),
                    toleranceMm = 50,
                    instruction = "",
                    status = SensorStatus.Pending
                )
            ),
            markers = listOf(
                Marker(
                    id = 1,
                    type = "apriltag",
                    sizeMm = 100,
                    positionMm = MmPosition(1_000, 2_000, 3_200),
                    rotationDeg = FloatVector(90f, 0f, 0f)
                )
            )
        )
        val bytes = ReportXlsx.build(
            project = project,
            log = InstallationLog(projectName = "Test", startedAt = "2026-06-02T00:00:00", operator = "Operator", results = emptyList())
        )

        val entries = mutableSetOf<String>()
        val contents = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += entry.name
                if (entry.name.endsWith(".xml")) {
                    contents[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }

        assertTrue(entries.contains("xl/workbook.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet1.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet9.xml"))
        assertTrue(contents.getValue("xl/worksheets/sheet4.xml").contains("2D Boven"))
        assertTrue(contents.getValue("xl/worksheets/sheet4.xml").contains("T1"))
        assertTrue(contents.getValue("xl/worksheets/sheet4.xml").contains("S1"))
        assertTrue(contents.getValue("xl/worksheets/sheet9.xml").contains("3D Model"))
    }
}
