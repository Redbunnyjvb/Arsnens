package com.example.arsens.data

import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Leest een door de PC-editor ("ARsens Project Editor") geëxporteerd project in: óf het volledige
 * exportpakket (`arsens_project_export.zip` met `project.json` + `models/<bestand>`), óf een los
 * `project.json`. De JSON is exact het wire-formaat van [JsonProjectStore], dus het parsen hergebruikt
 * [JsonProjectStore.projectFromJson]; hier zit alleen het uitpakken + de veiligheidscontroles.
 *
 * Bewust géén Android-types (Uri/Context): zo is dit puur op de JVM te testen, net als
 * JsonProjectStoreTest. Modellen worden één voor één naar [parse]'s `modelSink` gestreamd, dus de hele
 * assembly staat nooit tegelijk in geheugen.
 */
object ProjectPackageImporter {
    private const val PROJECT_JSON = "project.json"
    private const val MODELS_PREFIX = "models/"
    /** Lokale ZIP-bestandsheader: "PK". */
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    data class Result(
        val project: Project,
        /** Basisnamen van de uitgepakte modelbestanden (zoals doorgegeven aan de sink). */
        val modelNamesWritten: List<String>,
        /** In project.stlModels gerefereerde bestanden die NIET in het pakket zaten
         *  (los project.json, of een ontbrekend model). */
        val missingModels: List<String>
    )

    /**
     * @param rawInput stream over het bestand (zip of json). Wordt volledig gelezen, niet gesloten.
     * @param modelSink ontvangt per modelbestand de basisnaam + een stream over díe entry. De stream
     *   NIET sluiten; lees hem volledig (copyTo/readBytes) vóór terugkeer.
     */
    fun parse(
        rawInput: InputStream,
        modelSink: (fileName: String, entryStream: InputStream) -> Unit = { _, _ -> }
    ): Result {
        val input = rawInput as? BufferedInputStream ?: BufferedInputStream(rawInput)
        return if (looksLikeZip(input)) {
            parseZip(input, modelSink)
        } else {
            val project = projectFromText(input.readBytes().toString(Charsets.UTF_8))
            Result(project, emptyList(), project.stlModels.map { it.fileName })
        }
    }

    /** Kijkt 4 bytes vooruit (mark/reset) of het een ZIP is — robuust tegen verkeerde MIME/extensie. */
    private fun looksLikeZip(input: BufferedInputStream): Boolean {
        input.mark(ZIP_MAGIC.size + 1)
        val head = ByteArray(ZIP_MAGIC.size)
        var read = 0
        while (read < head.size) {
            val n = input.read(head, read, head.size - read)
            if (n < 0) break
            read += n
        }
        input.reset()
        return read == head.size && head.contentEquals(ZIP_MAGIC)
    }

    private fun parseZip(
        input: InputStream,
        modelSink: (String, InputStream) -> Unit
    ): Result {
        var projectText: String? = null
        val written = mutableListOf<String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                if (!entry.isDirectory && isSafePath(name)) {
                    val base = name.substringAfterLast('/')
                    when {
                        name == PROJECT_JSON || name.endsWith("/$PROJECT_JSON") ->
                            projectText = zip.readBytes().toString(Charsets.UTF_8)
                        name.startsWith(MODELS_PREFIX) && base.isNotBlank() -> {
                            modelSink(base, zip)
                            written += base
                        }
                        // manifest.json, project_template.xlsx, … → genegeerd.
                    }
                }
                zip.closeEntry()
            }
        }
        val text = projectText
            ?: throw IllegalArgumentException("Geen project.json in het pakket gevonden.")
        val project = projectFromText(text)
        val writtenSet = written.toSet()
        val missing = project.stlModels.map { it.fileName }.filter { it !in writtenSet }
        return Result(project, written, missing)
    }

    /** Weert pad-ontsnapping (zip-slip): geen absolute paden, geen ".."-segmenten. */
    private fun isSafePath(normalizedName: String): Boolean =
        normalizedName.isNotBlank() &&
            !normalizedName.startsWith("/") &&
            normalizedName.split('/').none { it == ".." }

    /** Accepteert het platte project-schema én — vergevingsgezind — de app-rapportbundel
     *  (project genest onder "project", met "installation_log" ernaast; zie
     *  [JsonProjectStore.projectReportToJson]). */
    private fun projectFromText(text: String): Project {
        val obj = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw IllegalArgumentException(
                "Geen geldig ARsens-bestand (verwacht een .zip-pakket of een project.json).", e
            )
        }
        val projectJson = if (!obj.has("sensors") && obj.optJSONObject("project") != null) {
            obj.getJSONObject("project").toString()
        } else {
            text
        }
        return try {
            JsonProjectStore.projectFromJson(projectJson)
        } catch (e: JSONException) {
            throw IllegalArgumentException("project.json kon niet gelezen worden: ${e.message}", e)
        }
    }
}
