package com.example.arsens

import com.example.arsens.data.ProjectPackageImporter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifieert het inlezen van een PC-export (.zip-pakket of los project.json) op de pure JVM —
 *  parsen, modellen streamen, ontbrekende modellen melden, zip-slip weren, rapportbundel uitpakken. */
class ProjectPackageImporterTest {

    private val projectJson = """
        {
          "project_name": "Trafo X",
          "model_file": "tank.stl",
          "dimensions_mm": [1000, 2000, 3000],
          "coordinate_frame": {"origin_corner":"front_left_bottom","flip_x":false,"flip_y":false,"flip_z":false},
          "sensors": [
            {"order":1,"id":"S001","name":"Sensor","side":"veld","position_mm":[10,20,30],"normal":[0,1,0],"tolerance_mm":50,"instruction":"","status":"pending","origin":"prepared"}
          ],
          "markers": [
            {"id":1,"type":"apriltag","size_mm":100,"position_mm":[1,2,3],"rotation_deg":[-90,0,0],"active":true,"pose_weight":1.0,"origin":"prepared"}
          ],
          "stl_models": [
            {"id":"tank.stl","name":"tank","file_name":"tank.stl","scale_percent":100,"offset_mm":[0,0,0],"rotation_deg":[0,0,0],"visible":true,"role":"TANK"}
          ]
        }
    """.trimIndent()

    private fun zipBytes(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Parse + verzamel de gestreamde modellen in een map (zoals de repository ze naar schijf schrijft). */
    private fun parse(bytes: ByteArray): Pair<ProjectPackageImporter.Result, Map<String, ByteArray>> {
        val models = linkedMapOf<String, ByteArray>()
        val result = ProjectPackageImporter.parse(ByteArrayInputStream(bytes)) { name, stream ->
            models[name] = stream.readBytes()
        }
        return result to models
    }

    @Test
    fun parsesZipWithProjectAndModels() {
        val bytes = zipBytes(
            listOf(
                "models/tank.stl" to "SOLID".toByteArray(),
                "project.json" to projectJson.toByteArray(),
                "manifest.json" to "{}".toByteArray(),
                "project_template.xlsx" to "ignored".toByteArray()
            )
        )
        val (result, models) = parse(bytes)
        assertEquals("Trafo X", result.project.projectName)
        assertEquals(1000, result.project.dimensionsMm.x)
        assertEquals(1, result.project.sensors.size)
        assertEquals(1, result.project.markers.size)
        assertEquals(1, result.project.stlModels.size)
        assertEquals("SOLID", String(models.getValue("tank.stl")))
        assertEquals(listOf("tank.stl"), result.modelNamesWritten)
        assertTrue(result.missingModels.isEmpty())
    }

    @Test
    fun parsesBareJsonWithoutModels() {
        val (result, models) = parse(projectJson.toByteArray())
        assertEquals("Trafo X", result.project.projectName)
        assertTrue(models.isEmpty())
        // Een los project.json bevat geen modellen → het gerefereerde tank.stl ontbreekt.
        assertEquals(listOf("tank.stl"), result.missingModels)
    }

    @Test
    fun reportsMissingModelWhenAbsentFromZip() {
        val bytes = zipBytes(listOf("project.json" to projectJson.toByteArray()))
        val (result, models) = parse(bytes)
        assertTrue(models.isEmpty())
        assertEquals(listOf("tank.stl"), result.missingModels)
    }

    @Test
    fun skipsZipSlipEntries() {
        val bytes = zipBytes(
            listOf(
                "models/../evil.stl" to "BAD".toByteArray(),
                "project.json" to projectJson.toByteArray()
            )
        )
        val (result, models) = parse(bytes)
        assertTrue("zip-slip entry mag niet uitgepakt worden", models.isEmpty())
        assertEquals("Trafo X", result.project.projectName)
    }

    @Test
    fun unwrapsReportBundleShape() {
        val report = """
            {"exported_at":"2026-06-22 10:00","drift_correction_enabled":false,
             "project":$projectJson,
             "installation_log":{"project_name":"Trafo X","started_at":"","operator":"x","results":[]}}
        """.trimIndent()
        val (result, _) = parse(report.toByteArray())
        assertEquals("Trafo X", result.project.projectName)
        assertEquals(1, result.project.sensors.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZipWithoutProjectJson() {
        val bytes = zipBytes(listOf("models/tank.stl" to "x".toByteArray()))
        ProjectPackageImporter.parse(ByteArrayInputStream(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsGarbageInput() {
        ProjectPackageImporter.parse(ByteArrayInputStream("not json at all".toByteArray()))
    }
}
