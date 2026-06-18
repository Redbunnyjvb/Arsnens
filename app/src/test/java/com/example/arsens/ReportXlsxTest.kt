package com.example.arsens

import com.example.arsens.data.FloatVector
import com.example.arsens.data.InstallationLog
import com.example.arsens.data.Marker
import com.example.arsens.data.MmPosition
import com.example.arsens.data.Project
import com.example.arsens.data.ReportMeta
import com.example.arsens.data.ReportXlsx
import com.example.arsens.data.Sensor
import com.example.arsens.data.SensorDriftCorrection
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
                ),
                Sensor(
                    order = 2,
                    id = "S2",
                    name = "Sensor 2",
                    side = "voor",
                    positionMm = MmPosition(2_000, 0, 1_500),
                    toleranceMm = 50,
                    instruction = "",
                    status = SensorStatus.Ok,
                    driftCorrection = SensorDriftCorrection(
                        asPlacedPositionMm = MmPosition(1_990, 0, 1_488),
                        deltaMm = 16,
                        correctedAtWallMillis = 1_750_000_000_000L,
                        poseMarkerIds = listOf(1)
                    )
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
            log = InstallationLog(projectName = "Test", startedAt = "2026-06-02T00:00:00", operator = "Operator", results = emptyList()),
            meta = ReportMeta(exportedAt = "2026-06-18 14:32", driftCorrectionEnabled = true)
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

        // Alle werkbladen samen, zodat de asserts niet op vaste sheet-nummers leunen.
        val allSheets = contents.filterKeys { it.startsWith("xl/worksheets/sheet") }.values.joinToString("\n")

        assertTrue(entries.contains("xl/workbook.xml"))
        // Toelichting is nu het eerste tabblad; 3D Model het laatste van de tien.
        assertTrue(entries.contains("xl/worksheets/sheet10.xml"))
        assertTrue(contents.getValue("xl/worksheets/sheet1.xml").contains("Toelichting"))
        assertTrue(contents.getValue("xl/worksheets/sheet1.xml").contains("Coordinatenstelsel"))
        assertTrue(allSheets.contains("2D Boven"))
        assertTrue(allSheets.contains("3D Model"))
        assertTrue(allSheets.contains("T1"))
        assertTrue(allSheets.contains("S1"))
        // Audit-kolom aanwezig en de gecorrigeerde sensor draagt de "*".
        assertTrue(allSheets.contains("Grade"))
        assertTrue(allSheets.contains("S2 *"))
    }
}
