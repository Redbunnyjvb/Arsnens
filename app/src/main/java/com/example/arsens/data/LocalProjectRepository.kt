package com.example.arsens.data

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ProjectSummary(
    val id: String,
    val name: String,
    val folder: File,
    val updatedAtMillis: Long
)

class LocalProjectRepository(private val context: Context) {
    private val appContext = context.applicationContext
    private var activeProjectId: String = loadActiveProjectId()

    fun loadInitialProject(): Project {
        loadProject(activeProjectId)?.let { return it }

        val markers = runCatching {
            JsonProjectStore.markersFromJson(readAsset("markers.json"))
        }.getOrElse { defaultMarkers() }

        return runCatching {
            JsonProjectStore.projectFromJson(readAsset("sensors.json"), markers)
        }.getOrElse {
            Project(
                projectName = "Transformer A",
                modelFile = "transformer_model.glb",
                dimensionsMm = MmPosition(10000, 5000, 3200),
                sensors = SensorCsv.importSensorsFromCsv(readAsset("sensors.csv")),
                markers = markers
            )
        }
    }

    fun listProjects(): List<ProjectSummary> {
        val folders = projectsDir().listFiles()?.filter { it.isDirectory }.orEmpty()
        return folders.mapNotNull { folder ->
            val file = File(folder, PROJECT_FILE)
            if (!file.exists()) return@mapNotNull null
            val project = runCatching { JsonProjectStore.projectFromJson(file.readText()) }.getOrNull()
                ?: return@mapNotNull null
            ProjectSummary(
                id = folder.name,
                name = project.projectName,
                folder = folder,
                updatedAtMillis = file.lastModified()
            )
        }.sortedByDescending { it.updatedAtMillis }
    }

    fun newProject(): Project = createProject("", MmPosition(10000, 5000, 3200))

    fun createProject(name: String, dimensionsMm: MmPosition): Project {
        val cleanName = name.ifBlank { "Nieuw transformatorproject" }.trim()
        val id = uniqueProjectId(cleanName)
        activeProjectId = id
        saveActiveProjectId(id)
        val project = Project(
            projectName = cleanName,
            modelFile = "transformer_model.glb",
            dimensionsMm = dimensionsMm,
            sensors = emptyList(),
            markers = emptyList()
        )
        saveProject(project)
        saveLog(
            InstallationLog(
                projectName = project.projectName,
                startedAt = nowIso(),
                operator = "Operator naam",
                results = emptyList()
            )
        )
        return project
    }

    fun selectProject(id: String): Project? {
        val project = loadProject(id) ?: return null
        activeProjectId = id
        saveActiveProjectId(id)
        return project
    }

    fun deleteProject(id: String): Boolean {
        val folder = projectDir(id)
        if (!folder.exists()) return false
        val deleted = folder.deleteRecursively()
        if (deleted && activeProjectId == id) {
            activeProjectId = DEFAULT_PROJECT_ID
            saveActiveProjectId(activeProjectId)
        }
        return deleted
    }

    fun activeProjectSummary(): ProjectSummary? =
        listProjects().firstOrNull { it.id == activeProjectId }

    fun loadLog(projectName: String): InstallationLog =
        JsonProjectStore.loadInstallationLog(activeProjectDir())?.takeIf { it.projectName == projectName }
            ?: InstallationLog(
                projectName = projectName,
                startedAt = nowIso(),
                operator = "Operator naam",
                results = emptyList()
            )

    fun saveProject(project: Project): File =
        JsonProjectStore.saveProjectToJson(activeProjectDir(), project)

    fun saveLog(log: InstallationLog): File =
        JsonProjectStore.saveInstallationLog(activeProjectDir(), log)

    fun importSensorsFromCsv(uri: Uri): List<Sensor> =
        SensorCsv.importSensorsFromCsv(readUri(uri))

    fun exportSensorsCsv(project: Project): File {
        val file = File(activeProjectDir(), "sensors_export.csv")
        file.writeText("\uFEFF" + SensorCsv.sensorsToCsv(project.sensors))
        publishDownload(file.name, "text/csv", file.readBytes())
        return file
    }

    fun exportReportCsv(project: Project, log: InstallationLog): File {
        val file = JsonProjectStore.exportReportCsv(activeProjectDir(), project, log)
        file.writeText("\uFEFF" + SensorCsv.exportReportCsv(log, project))
        publishDownload(file.name, "text/csv", file.readBytes())
        return file
    }

    /** Volledig machine-leesbaar bundelpakket (project + frame + STL-offsets + sensoren-met-audit +
     *  driftcorrecties + installatielog). Vervangt het oude log-only JSON-rapport. */
    fun exportReportJson(project: Project, log: InstallationLog, driftCorrectionEnabled: Boolean): File {
        val file = File(activeProjectDir(), "arsens_report.json")
        file.writeText(JsonProjectStore.projectReportToJson(project, log, reportMeta(driftCorrectionEnabled)))
        publishDownload(file.name, "application/json", file.readBytes())
        return file
    }

    fun exportReportXlsx(project: Project, log: InstallationLog, driftCorrectionEnabled: Boolean): File {
        val file = File(activeProjectDir(), "installation_report.xlsx")
        file.writeBytes(ReportXlsx.build(project, log, reportMeta(driftCorrectionEnabled)))
        publishDownload(file.name, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", file.readBytes())
        return file
    }

    private fun reportMeta(driftCorrectionEnabled: Boolean): ReportMeta =
        ReportMeta(
            exportedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
            driftCorrectionEnabled = driftCorrectionEnabled
        )

    fun displayName(uri: Uri): String {
        val cursor = appContext.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) return it.getString(index)
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "bestand"
    }

    /** Kopieert een gekozen STL/3D-bestand naar de projectmap. Geeft (opgeslagen bestandsnaam, weergavenaam). */
    fun importStlFile(uri: Uri): Pair<String, String>? {
        val display = displayName(uri)
        val dir = File(activeProjectDir(), MODELS_SUBDIR).also { it.mkdirs() }
        val target = uniqueFile(dir, sanitizeFileName(display))
        return runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            target.name to display
        }.getOrNull()
    }

    fun stlModelFile(fileName: String): File =
        File(File(activeProjectDir(), MODELS_SUBDIR), fileName)

    fun deleteStlFile(fileName: String) {
        runCatching { stlModelFile(fileName).delete() }
    }

    private fun sanitizeFileName(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "model.stl" }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 2
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isBlank()) "$base-$index" else "$base-$index.$ext")
            index++
        }
        return candidate
    }

    fun nowIso(): String =
        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    fun mockPhotoName(sensorId: String): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return "${sensorId}_${stamp}.jpg"
    }

    fun mockOffsetFor(order: Int): MmPosition {
        val x = (order * 7 % 31) - 15
        val y = (order * 5 % 25) - 12
        val z = (order * 11 % 41) - 20
        return MmPosition(x, y, z)
    }

    fun mockMeasuredPositionFor(sensor: Sensor): MmPosition =
        sensor.positionMm + mockOffsetFor(sensor.order)

    private fun readUri(uri: Uri): String =
        appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Bestand kon niet geopend worden.")

    private fun publishDownload(fileName: String, mimeType: String, bytes: ByteArray) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/ARsens")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@runCatching
                appContext.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                appContext.contentResolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ARsens")
                dir.mkdirs()
                File(dir, fileName).writeBytes(bytes)
            }
        }
    }

    private fun readAsset(name: String): String =
        appContext.assets.open(name).bufferedReader().use { it.readText() }

    private fun loadProject(id: String): Project? {
        val file = File(projectDir(id), PROJECT_FILE)
        return if (file.exists()) {
            runCatching { JsonProjectStore.projectFromJson(file.readText()) }.getOrNull()
        } else {
            null
        }
    }

    private fun projectsDir(): File =
        File(JsonProjectStore.arsensDir(appContext), "projects").also { it.mkdirs() }

    private fun activeProjectDir(): File =
        projectDir(activeProjectId).also { it.mkdirs() }

    private fun projectDir(id: String): File =
        File(projectsDir(), id)

    private fun loadActiveProjectId(): String {
        val file = File(JsonProjectStore.arsensDir(appContext), ACTIVE_PROJECT_FILE)
        return file.takeIf { it.exists() }?.readText()?.trim()?.ifBlank { DEFAULT_PROJECT_ID }
            ?: DEFAULT_PROJECT_ID
    }

    private fun saveActiveProjectId(id: String) {
        File(JsonProjectStore.arsensDir(appContext), ACTIVE_PROJECT_FILE).writeText(id)
    }

    private fun uniqueProjectId(name: String): String {
        val base = slug(name).ifBlank { "project" }
        var candidate = base
        var index = 2
        while (projectDir(candidate).exists()) {
            candidate = "$base-$index"
            index++
        }
        return candidate
    }

    private fun slug(value: String): String =
        value.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    private fun defaultMarkers(): List<Marker> =
        listOf(
            Marker(1, "apriltag", 100, MmPosition(0, 0, 0), FloatVector(0f, 0f, 0f)),
            Marker(2, "apriltag", 100, MmPosition(5000, 0, 0), FloatVector(0f, 0f, 0f)),
            Marker(3, "apriltag", 100, MmPosition(0, 0, 2500), FloatVector(0f, 0f, 0f))
        )

    companion object {
        private const val DEFAULT_PROJECT_ID = "default"
        private const val ACTIVE_PROJECT_FILE = "active_project.txt"
        private const val PROJECT_FILE = "project.json"
        private const val MODELS_SUBDIR = "models"
    }
}
