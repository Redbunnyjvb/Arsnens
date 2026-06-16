package com.example.arsens.ui

import com.example.arsens.data.IndexedMesh
import com.example.arsens.data.STL_WELD_TOLERANCE_MM
import com.example.arsens.data.StlMesh
import com.example.arsens.data.StlModel
import com.example.arsens.data.StlPartRole
import com.example.arsens.data.buildEdgeTopology
import com.example.arsens.data.clusterDecimateSubset
import com.example.arsens.data.decimationCellForBudget
import com.example.arsens.data.estimateCoreBounds
import com.example.arsens.data.featureEdgePairs
import com.example.arsens.data.orientConsistently
import com.example.arsens.data.sharpEdgeFlags
import com.example.arsens.data.trianglesOutsideBox
import com.example.arsens.data.weldStlMesh
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Onderdelengroepen van de AR-assembly. Een STL-deel krijgt één hoofdgroep op naam; binnen het
 * tankdeel worden componenten buiten de wandbox (koelradiatoren, conservator) afgesplitst naar
 * [Radiatoren] zodat ze apart aan/uit kunnen.
 */
internal enum class StlArPartGroup(val label: String) {
    Tank("Tank"),
    Deksel("Deksel"),
    ActiefDeel("Actief deel"),
    Radiatoren("Radiatoren"),
    Overig("Overig")
}

// ===== Tunables voor de AR-meshweergave (op de telefoon af te stellen) =====

/** Driehoekenbudget per STL-deel voor Snel/Technisch/Vlakken (radiatoren tellen mee met de tank;
 *  een los bushings/turrets-deel valt als hoofdgroep onder Radiatoren). */
internal val STL_AR_MID_TRIANGLE_BUDGET = mapOf(
    StlArPartGroup.Tank to 36_000,
    StlArPartGroup.Deksel to 16_000,
    StlArPartGroup.ActiefDeel to 12_000,
    StlArPartGroup.Radiatoren to 10_000,
    StlArPartGroup.Overig to 8_000
)

/** Driehoekenbudget per STL-deel voor de Detail-modus. */
internal val STL_AR_HIGH_TRIANGLE_BUDGET = mapOf(
    StlArPartGroup.Tank to 90_000,
    StlArPartGroup.Deksel to 36_000,
    StlArPartGroup.ActiefDeel to 28_000,
    StlArPartGroup.Radiatoren to 24_000,
    StlArPartGroup.Overig to 20_000
)

/** Maximaal aantal opgeslagen feature-randen per chunk (lang→kort gesorteerd; tekentijd-caps
 *  per modus liggen lager — zie WorkflowArOverlays). */
internal const val STL_AR_MAX_STORED_FEATURE_LINES = 12_000

/** Marge rond de geschatte wandbox bij de radiator-splitsing, als fractie van de tankmaat. */
internal const val STL_AR_RADIATOR_BOX_MARGIN_FRACTION = 0.015f

/** Aantal dieptelagen voor de ver→dichtbij-sortering van transparante vlakken. */
internal const val STL_AR_DEPTH_BUCKETS = 256

/**
 * Eén tekenbare brok AR-geometrie: alle driehoeken van één (deel, groep) op één detailniveau,
 * in MODEL-coördinaten. De model→project-transform (offset/rotatie/schaal, live instelbaar)
 * wordt pas per frame in de projectiematrix gevouwen — bewerken bouwt dus niets opnieuw.
 */
internal class StlArChunk(
    val group: StlArPartGroup,
    val positions: FloatArray,
    val indices: IntArray,
    val faceNormals: FloatArray,
    /** Per driehoek: true = component is gesloten → backface-culling is daar veilig. */
    val cullableTriangles: BooleanArray,
    // Rand-adjacency voor silhouetdetectie per frame (edgeFaceB < 0 = boundary).
    val edgeA: IntArray,
    val edgeB: IntArray,
    val edgeFaceA: IntArray,
    val edgeFaceB: IntArray,
    /** Vertex-paren van boundary/scherpe randen, lang→kort — tekenbudget pakt de eerste N. */
    val featureLines: IntArray,
    /** Model-space bbox [minX,minY,minZ,maxX,maxY,maxZ] voor frustum-culling per chunk. */
    val bounds: FloatArray
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3
    val edgeCount: Int get() = edgeA.size

    // Scratchbuffers, per frame hergebruikt zodat het tekenen allocatievrij blijft.
    // Lazy: een verborgen chunk kost geen werkgeheugen.
    val scratchView: FloatArray by lazy { FloatArray(vertexCount * 2) }
    val scratchZ: FloatArray by lazy { FloatArray(vertexCount) }
    val scratchTriZ: FloatArray by lazy { FloatArray(triangleCount) }
    val scratchTriColor: IntArray by lazy { IntArray(triangleCount) }
    val scratchOrder: IntArray by lazy { IntArray(triangleCount) }
    val scratchBuckets: IntArray by lazy { IntArray(STL_AR_DEPTH_BUCKETS + 1) }
    val scratchFaceSign: ByteArray by lazy { ByteArray(triangleCount) }
    val scratchPacked: FloatArray by lazy { FloatArray(triangleCount * 6) }
    val scratchPackedColor: IntArray by lazy { IntArray(triangleCount * 3) }
    val scratchLines: FloatArray by lazy {
        FloatArray(maxOf(featureLines.size * 2, edgeCount * 4, 48))
    }
}

/** Alle AR-geometrie van één STL-deel: chunks per groep, op twee detailniveaus. */
internal class StlArPart(
    val modelId: String,
    val fileName: String,
    val name: String,
    val colorArgb: Int,
    /** Model-space bbox van het hele deel. */
    val bounds: FloatArray,
    val mid: List<StlArChunk>,
    val high: List<StlArChunk>
) {
    val groups: Set<StlArPartGroup> by lazy { mid.mapTo(mutableSetOf()) { it.group } }
}

private fun bboxVolume(mesh: StlMesh): Double =
    (mesh.maxX - mesh.minX).toDouble() *
        (mesh.maxY - mesh.minY).toDouble() *
        (mesh.maxZ - mesh.minZ).toDouble()

/**
 * Bouwt de AR-meshcache voor alle delen met een ingelezen mesh. Zwaar werk (welden van honderd-
 * duizenden driehoeken) — aanroepen op een achtergrond-dispatcher. De cache is onafhankelijk van
 * offset/rotatie/schaal van de delen; alleen opnieuw bouwen bij andere bestanden of namen.
 */
internal suspend fun buildStlArParts(
    models: List<StlModel>,
    meshes: Map<String, StlMesh>,
    buildHigh: Boolean = true
): Map<String, StlArPart> = coroutineScope {
    val withMesh = models.mapIndexedNotNull { index, model ->
        val mesh = meshes[model.fileName]?.takeIf { !it.isEmpty } ?: return@mapIndexedNotNull null
        Triple(index, model, mesh)
    }
    if (withMesh.isEmpty()) return@coroutineScope emptyMap<String, StlArPart>()
    // Groepsindeling volgt de PART-ROL (autodetectie op naam bij import, handmatig aanpasbaar).
    // Zonder expliciete tank-rol wordt het grootste deel de tank — zelfde aanname als
    // autoAlignAssembly, en het garandeert dat de standaard zichtbare groep iets bevat.
    val hasExplicitTank = withMesh.any { it.second.role == StlPartRole.Tank }
    val largestModelId = withMesh.maxByOrNull { bboxVolume(it.third) }?.second?.id
    // De delen zijn onafhankelijk → parallel bouwen over de CPU-kernen (de zwaarste stap van
    // "voorbereiden"). BEGRENSD op enkele kernen zodat de PIEK-geheugendruk (meerdere grote weld-/
    // decimatie-buffers tegelijk) niet ontspoort op grote assemblies. Volgorde blijft behouden.
    val gate = Semaphore(Runtime.getRuntime().availableProcessors().coerceIn(1, 4))
    val built = withMesh.map { (index, model, mesh) ->
        val primaryGroup = when {
            model.role == StlPartRole.Tank -> StlArPartGroup.Tank
            !hasExplicitTank && model.id == largestModelId -> StlArPartGroup.Tank
            model.role == StlPartRole.Cover -> StlArPartGroup.Deksel
            model.role == StlPartRole.ActivePart -> StlArPartGroup.ActiefDeel
            // Bushings/turrets zitten buitenop (deksel) — bij de zichtbare "aanbouwdelen"-groep.
            model.role == StlPartRole.BushingsTurrets -> StlArPartGroup.Radiatoren
            else -> StlArPartGroup.Overig // Core, Wiksets, Other: binnenwerk, standaard uit
        }
        async { gate.withPermit { model.id to buildStlArPart(model, mesh, primaryGroup, stlPartColor(index), buildHigh) } }
    }.awaitAll()
    val result = LinkedHashMap<String, StlArPart>(built.size)
    for ((id, part) in built) result[id] = part
    result
}

private fun buildStlArPart(
    model: StlModel,
    mesh: StlMesh,
    primaryGroup: StlArPartGroup,
    colorArgb: Int,
    buildHigh: Boolean
): StlArPart {
    val oriented = orientConsistently(weldStlMesh(mesh, STL_WELD_TOLERANCE_MM))
    val m = oriented.mesh
    val triCount = m.triangleCount
    // Groep per driehoek: tankdelen splitsen op de wandbox in Tank (wanden) en Radiatoren.
    val triangleGroup: Array<StlArPartGroup> = if (primaryGroup == StlArPartGroup.Tank && triCount > 0) {
        val core = mesh.estimateCoreBounds()
        val margin = mesh.maxExtent * STL_AR_RADIATOR_BOX_MARGIN_FRACTION
        val outside = trianglesOutsideBox(m, oriented.components, core, margin)
        Array(triCount) { if (outside[it]) StlArPartGroup.Radiatoren else StlArPartGroup.Tank }
    } else {
        Array(triCount) { primaryGroup }
    }
    val sourceCullable = BooleanArray(triCount) {
        oriented.componentClosed[oriented.components.triangleComponent[it]]
    }
    val allTriangles = IntArray(triCount) { it }
    val midCell = decimationCellForBudget(m, allTriangles, STL_AR_MID_TRIANGLE_BUDGET.getValue(primaryGroup))
    val groupTriangles = StlArPartGroup.entries.mapNotNull { group ->
        var count = 0
        for (t in 0 until triCount) if (triangleGroup[t] == group) count++
        if (count == 0) return@mapNotNull null
        val ids = IntArray(count)
        var out = 0
        for (t in 0 until triCount) if (triangleGroup[t] == group) ids[out++] = t
        group to ids
    }
    // Beide niveaus delen per part hetzelfde rasterminimum (mesh-min): groepsnaden vallen samen.
    val mid = groupTriangles.map { (group, ids) -> buildStlArChunk(m, ids, group, midCell, sourceCullable) }
    // High is duur én alleen nodig in de Detail-modus: pas bouwen wanneer daarom gevraagd wordt
    // (inclusief de bijbehorende decimatie-zoekstap, die anders bij elke prep meeliep).
    val high = if (buildHigh) {
        val highCell = decimationCellForBudget(m, allTriangles, STL_AR_HIGH_TRIANGLE_BUDGET.getValue(primaryGroup))
        groupTriangles.map { (group, ids) -> buildStlArChunk(m, ids, group, highCell, sourceCullable) }
    } else {
        emptyList()
    }
    return StlArPart(
        modelId = model.id,
        fileName = model.fileName,
        name = model.name,
        colorArgb = colorArgb,
        bounds = floatArrayOf(m.minX, m.minY, m.minZ, m.maxX, m.maxY, m.maxZ),
        mid = mid,
        high = high
    )
}

private fun buildStlArChunk(
    source: IndexedMesh,
    triangleIds: IntArray,
    group: StlArPartGroup,
    cellMm: Float,
    sourceCullable: BooleanArray
): StlArChunk {
    val d = clusterDecimateSubset(source, triangleIds, cellMm)
    val chunkMesh = IndexedMesh.of(d.positions, d.indices, d.faceNormals)
    val topology = buildEdgeTopology(chunkMesh)
    val sharp = sharpEdgeFlags(chunkMesh, topology)
    val allLines = featureEdgePairs(chunkMesh, topology, sharp)
    val lines = if (allLines.size > STL_AR_MAX_STORED_FEATURE_LINES * 2) {
        allLines.copyOf(STL_AR_MAX_STORED_FEATURE_LINES * 2)
    } else {
        allLines
    }
    val cullable = BooleanArray(d.triangleCount) { sourceCullable[d.sourceTriangles[it]] }
    return StlArChunk(
        group = group,
        positions = d.positions,
        indices = d.indices,
        faceNormals = d.faceNormals,
        cullableTriangles = cullable,
        edgeA = topology.edgeA,
        edgeB = topology.edgeB,
        edgeFaceA = topology.faceA,
        edgeFaceB = topology.faceB,
        featureLines = lines,
        bounds = floatArrayOf(
            chunkMesh.minX, chunkMesh.minY, chunkMesh.minZ,
            chunkMesh.maxX, chunkMesh.maxY, chunkMesh.maxZ
        )
    )
}
