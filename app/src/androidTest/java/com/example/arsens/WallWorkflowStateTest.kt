package com.example.arsens

import android.os.SystemClock
import com.example.arsens.ar.*
import com.example.arsens.ar.calibration.*
import com.example.arsens.data.*
import com.example.arsens.ui.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class WallWorkflowStateTest {
    private lateinit var store: WallTestStore
    private lateinit var state: WorkflowAppState
    @Before fun setup() { store=WallTestStore();state=store.state() }
    @After fun cleanup() { store.close() }
    private fun create(source: WallDimensionSource = WallDimensionSource.Entered) {
        state.newProjectName="Walls"
        state.newReferenceGeometryMode=ReferenceGeometryMode.ScannedWalls
        state.newWallDimensionSource=source
        state.lengthMm="1000";state.widthMm="800";state.heightMm="1000"
        state.createProject()
    }
    private fun packet(sequence: Long, frameId: Long=1, tracking: Boolean=true, session: Long=state.wallScanId) =
        AprilTagFrameResult(calibrationRevision=state.arCalibrationRevision,
            wallScanFrame=WallScanFrame(session,frameId,tracking,
                if(tracking) WallTestGeometry.observations(SystemClock.elapsedRealtime(),sequence,frameId) else emptyList(),null))
    private fun scan(separateTop: Boolean = false, topCount: Int = 2) {
        state.beginWallScan()
        state.wallScanSize="100"
        state.updateWallScanFrame(packet(1))
        WallTestGeometry.tags.filter { it.assignment.tagId < 8+topCount }.forEach { tag ->
            state.wallScanWall=tag.assignment.wall;state.assignWallTag(tag.assignment.tagId)
        }
        repeat(9) { index ->
            if (separateTop) {
                for (top in listOf(false,true)) {
                    SystemClock.sleep(100)
                    val result=packet(2+index*2L+if(top) 1 else 0)
                    state.updateWallScanFrame(result.copy(wallScanFrame=result.wallScanFrame!!.let { frame ->
                        frame.copy(observations=frame.observations.filter { (it.tagId>=8)==top })
                    }))
                }
            } else { SystemClock.sleep(100);state.updateWallScanFrame(packet(index+2L)) }
        }
        state.wallSideHeight="1000";state.wallTopOffset="0"
        assertEquals(8+topCount,state.wallReadyTagIds.size)
        state.solveWallFootprint()
        assertNotNull(state.wallScanMessage,state.wallScanFootprint)
        state.startWallTopStage()
        state.solveWallScan()
        assertNotNull(state.wallScanMessage,state.wallScanSolution)
    }
    @Test fun measuredProjectStartsWithUnknownDimensionsAndCannotOpenPlacementOrModel() {
        create(WallDimensionSource.Scanned)
        assertEquals(MmPosition(0,0,0),state.project.dimensionsMm)
        assertEquals(state.project,store.state().project)
        assertFalse(state.sensorPlacementReady)
        state.go(WorkflowScreen.Stl)
        assertEquals(WorkflowScreen.Tags,state.screen)
        assertTrue(state.project.needsWallCalibration)
    }
    @Test fun acceptancePersistsBothSourcesAndKeepsSensorsAndLog() {
        for(source in WallDimensionSource.entries) {
            create(source)
            val target=MmPosition(350,0,450)
            state.project=state.project.copy(sensors=listOf(Sensor(1,"TEMP-A","Tank","Front",target,toleranceMm=50,instruction="Controleer tank")))
            state.log=state.log.copy(results=listOf(InstallationResult("TEMP-A",target,MmPosition(355,0,450),
                MmPosition(5,0,0),5,SensorStatus.Ok,null,"previous measurement")))
            val sensors=state.project.sensors;val log=state.log
            scan()
            assertFalse(state.sensorPlacementReady)
            // Preview metadata must remain paired even if input state is edited later.
            state.wallSideHeight="999";state.wallAssignments=emptyList()
            state.wallScanSource=if(source==WallDimensionSource.Entered) WallDimensionSource.Scanned else WallDimensionSource.Entered
            state.acceptWallScan()
            assertFalse(state.wallScanActive)
            assertTrue(state.project.hasWallCalibration)
            assertEquals(WallTestGeometry.dimensions,state.project.dimensionsMm)
            assertEquals(source,state.project.wallCalibration!!.dimensionSource)
            assertEquals(-1,state.project.wallCalibration!!.datum.lowerTagId)
            assertEquals(1000,state.project.wallCalibration!!.datum.sideHeightMm)
            assertEquals(10,state.project.markers.size)
            assertEquals(sensors,state.project.sensors);assertEquals(log,state.log)
            val reloaded=store.state()
            assertEquals(state.project,reloaded.project)
            assertFalse(reloaded.wallScanActive);assertNull(reloaded.aprilTagResult.transformerPose)
            assertFalse(reloaded.sensorPlacementReady) // Requires a known reference after restart.
            state.beginWallScan();state.cancelWallScan()
            assertEquals(reloaded.project,state.project)
        }
    }
    @Test fun lostTrackingChangedFrameAndCameraErrorInvalidatePreviewWithoutSaving() {
        for(kind in listOf("paused","frame","error")) {
            create();val before=state.project;scan()
            state.updateWallScanFrame(when(kind) {
                "paused" -> packet(30,tracking=false)
                "frame" -> packet(30,frameId=2)
                else -> AprilTagFrameResult(calibrationRevision=state.arCalibrationRevision,errorMessage="camera unavailable")
            })
            assertNull(kind,state.wallScanSolution)
            state.acceptWallScan();assertEquals(before,state.project)
        }
    }
    @Test fun earlierScanPacketsCannotPopulateNewScanAndRepeatedImagesAreNotEvidence() {
        create();state.beginWallScan();val oldId=state.wallScanId
        state.beginWallScan();state.updateWallScanFrame(packet(1,session=oldId))
        assertTrue(state.wallSeenTags.isEmpty())
        val first=packet(1);state.updateWallScanFrame(first);state.assignWallTag(0)
        repeat(20) { state.updateWallScanFrame(first) }
        state.solveWallScan();assertNull(state.wallScanSolution)
        state.cancelWallScan();assertTrue(state.project.markers.isEmpty())
    }
    @Test fun returningToKnownPositionsRetainsAcceptedGeometryAndDimensionsStayProtectedFromImport() {
        create();scan();state.acceptWallScan();val geometry=state.project.markers
        state.setDimensionsLocked(false);assertTrue(state.project.dimensionsLocked)
        state.setReferenceGeometryMode(ReferenceGeometryMode.KnownTagPositions)
        assertEquals(geometry,state.project.markers);assertFalse(state.project.needsWallCalibration)
        state.setDimensionsLocked(false);assertFalse(state.project.dimensionsLocked)
        state.setReferenceGeometryMode(ReferenceGeometryMode.ScannedWalls)
        assertTrue(state.project.hasWallCalibration)
    }
    @Test fun stalePreviewCannotBeAcceptedEvenWithoutAnExplicitPausedFrame() {
        create();scan();val before=state.project
        SystemClock.sleep(550)
        state.acceptWallScan();assertEquals(before,state.project)
        assertNotNull(state.wallScanMessage)
    }
    @Test fun separateSideAndTopScansCanLinkThroughTheTrackedContourWithoutOverlap() {
        create();scan(separateTop=true)
        assertFalse(state.wallTopOverlapVerified)
        assertNotNull(state.wallScanFootprint)
        assertNotNull(state.wallScanSolution)
        state.acceptWallScan();assertTrue(state.project.hasWallCalibration)
        assertFalse(state.project.wallCalibration!!.quality.topOverlapVerified)
    }

    @Test fun firstTopTagLinksTheContourAndFurtherTopTagsCanBeAddedFreely() {
        create(WallDimensionSource.Scanned);scan(topCount=1)
        val linked=state.wallScanSolution!!.referenceFromProject
        assertEquals(9,state.wallScanSolution!!.markers.size)
        state.resumeWallScan()
        assertNotNull(state.wallLinkedPreview) // Full contour remains visible while adding tags.
        state.updateWallScanFrame(packet(30));state.assignWallTag(9)
        repeat(9) { SystemClock.sleep(100);state.updateWallScanFrame(packet(31+it.toLong())) }
        state.solveWallScan()
        assertNotNull(state.wallScanMessage,state.wallScanSolution)
        assertEquals(10,state.wallScanSolution!!.markers.size)
        assertArrayEquals(linked.values,state.wallScanSolution!!.referenceFromProject.values,0.02)
        state.acceptWallScan();assertTrue(state.project.hasWallCalibration)
    }

    @Test fun wrongSideAssignmentCanBeCorrectedToTopWithoutHidingCaptureOrKeepingOldSamples() {
        create();state.beginWallScan();state.wallScanSize="100"
        state.updateWallScanFrame(packet(1));state.assignWallTag(8) // Flat tag, explicitly assigned to Front.
        repeat(9) { SystemClock.sleep(100);state.updateWallScanFrame(packet(it+2L)) }
        assertTrue(state.wallReadyTagIds.isEmpty())
        assertTrue(state.wallCaptureHint.contains("horizontaal"))
        assertEquals(8,state.wallCaptureTag!!.tagId)
        state.selectWallScanFace(CalibrationWall.Top);state.assignWallTag(8)
        assertEquals(CalibrationWall.Top,state.wallAssignments.single().wall)
        repeat(9) { SystemClock.sleep(100);state.updateWallScanFrame(packet(it+20L)) }
        assertEquals(listOf(8),state.wallReadyTagIds)
        assertEquals(1,state.wallAssignments.size)
    }
    @Test fun pickingTopAfterFootprintOpensLinkingAndChangingASideInvalidatesItsOldContour() {
        create();scan();state.resumeWallScan()
        state.selectWallScanFace(CalibrationWall.Top)
        assertTrue(state.wallTopStage)
        state.updateWallScanFrame(packet(40))
        state.selectWallScanFace(CalibrationWall.Back);state.assignWallTag(0)
        assertNull(state.wallScanFootprint);assertNull(state.wallLinkedPreview)
        assertFalse(state.wallTopStage)
        assertEquals(CalibrationWall.Back,state.wallAssignments.single { it.tagId==0 }.wall)
        assertFalse(0 in state.wallReadyTagIds)
    }

}
