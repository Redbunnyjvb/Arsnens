package com.example.arsens

import com.example.arsens.ar.*
import com.example.arsens.ar.calibration.*
import com.example.arsens.data.*
import org.junit.Assert.*
import org.junit.Test

class PersistentReferenceScanTest {
    private val assignments = listOf(WallTagAssignment(1, CalibrationWall.Front, 100), WallTagAssignment(2, CalibrationWall.Top, 100))
    private fun pose(x: Double, y: Double = 0.0, z: Double = 0.0) = Transform3D.identity().let {
        Transform3D(it.values.copyOf().apply { this[3] = x; this[7] = y; this[11] = z }) }
    private val side = pose(0.0, 0.0, 1000.0)
    private val top = Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(300f,0f,1200f), floatArrayOf((-Math.PI/2).toFloat(),0f,0f),0f))
    private fun observe(scan: PersistentReferenceScan, index: Int, drift: Transform3D = Transform3D.identity(), frameId: Long = 1) {
        val time = 1000L + index * 100
        val observations = listOf(side, top).mapIndexed { i, tag -> WallTagObservation(i + 1, 100, time, index.toLong(), frameId,
            drift * tag, drift, drift.wallDirection(WallVector(0.0, 0.0, 1.0)), 0.2f, 100f) }
        scan.observe(WallScanFrame(1, frameId, true, observations, null), assignments, time)
    }
    @Test fun directEdgeCancelsChangingCommonArCoreDrift() {
        val scan = PersistentReferenceScan()
        repeat(14) { observe(scan, it, pose(it * 35.0, it * -15.0, 0.0)) }
        assertEquals(2, scan.graph.nodes.size)
        assertEquals(1, scan.graph.edges.size)
        assertArrayEquals((side.inverseRigid() * top).values, scan.graph.edges.single().relativeTransform.toDoubleArray(), 1e-4)
        assertTrue(scan.hasTopOverlap(listOf(1, 2)))
    }
    @Test fun partialGraphSurvivesJsonAndRequiresRelocalizationInNewWorld() {
        val scan = PersistentReferenceScan()
        repeat(12) { observe(scan, it) }
        val project = Project("P", "", emptyList(), emptyList(), referenceGraph = scan.graph)
        val restored = PersistentReferenceScan().also { it.restore(JsonProjectStore.projectFromJson(JsonProjectStore.projectToJson(project)).referenceGraph) }
        assertTrue(restored.estimates(assignments).isEmpty())
        observe(restored, 30, pose(4000.0, 200.0, 30.0), 9)
        assertEquals(2, restored.estimates(assignments).size)
        assertEquals(4000.0, restored.estimates(assignments).first().center.x, 0.1)
        assertEquals(scan.graph.edges, restored.graph.edges)
    }
    @Test fun topWithoutDirectOverlapIsNotVerified() {
        val scan = PersistentReferenceScan()
        for (index in 0..20) {
            val id = if (index < 10) 1 else 2
            val time = 1000L + index * 100
            val observation = WallTagObservation(id, 100, time, index.toLong(), 1,
                if (id == 1) side else top, Transform3D.identity(), WallVector(0.0,0.0,1.0),0.2f,100f)
            scan.observe(WallScanFrame(1,1,true,listOf(observation),null),assignments,time)
        }
        assertEquals(2, scan.graph.nodes.size)
        assertFalse(scan.hasTopOverlap(listOf(1, 2)))
    }
    @Test fun conflictingVisibleReferencesBlockRuntimeAlignmentWithoutChangingSavedGeometry() {
        val scan = PersistentReferenceScan()
        repeat(12) { observe(scan, it) }
        val saved = scan.graph
        val time = 3000L
        val observations = listOf(side, pose(120.0) * top).mapIndexed { i, tag ->
            WallTagObservation(i+1,100,time,30,1,tag,Transform3D.identity(),WallVector(0.0,0.0,1.0),0.2f,100f)
        }
        scan.observe(WallScanFrame(1,1,true,observations,null),assignments,time)
        assertNotNull(scan.reason)
        assertTrue(scan.estimates(assignments).isEmpty())
        assertEquals(saved,scan.graph)
        observe(scan,31)
        assertNull(scan.reason)
        assertEquals(2,scan.estimates(assignments).size)
    }
    @Test fun loopClosureReducesInconsistencyAndRejectsBadEdge() {
        val nodes = listOf(0.0, 1020.0, 2050.0).mapIndexed { i, x -> ReferenceTagNode(i + 1, CalibrationWall.Front, 100,
            pose(x).values.toList(), listOf(0.0, 0.0, 1.0), 12, 2.0, true) }
        fun edge(a: Int,b: Int,x: Double) = ReferencePoseEdge(a,b,pose(x).values.toList(),12,1000,0.2,1.0,0.2,"t")
        val graph = ReferenceGraph(1, nodes = nodes, edges = listOf(edge(1,2,1000.0),edge(2,3,1000.0),edge(1,3,2000.0),edge(1,3,5000.0)))
        val solution = ReferenceGraphOptimizer.solve(graph)
        assertEquals(setOf(3), solution.rejectedEdges)
        assertTrue(kotlin.math.abs(solution.poses.getValue(3).translation()[0] - 2000.0) < 5)
        assertEquals(2050.0, graph.nodes.last().seedFromTag[3], 0.0)
    }
}
