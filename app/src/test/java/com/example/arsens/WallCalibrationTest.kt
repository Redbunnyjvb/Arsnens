package com.example.arsens

import com.example.arsens.ar.*
import com.example.arsens.ar.calibration.*
import com.example.arsens.data.*
import org.junit.Assert.*
import org.junit.Test

class WallCalibrationTest {
    private val dimensions = MmPosition(1000,800,1000)
    private val up = WallVector(0.0,1.0,0.0)
    private val frame = Transform3D(doubleArrayOf(0.8,-0.6,0.0,1500.0, 0.0,0.0,1.0,-900.0, -0.6,-0.8,0.0,1200.0, 0.0,0.0,0.0,1.0))
    private val points = listOf(
        CalibrationWall.Front to MmPosition(200,0,200), CalibrationWall.Front to MmPosition(800,0,800),
        CalibrationWall.Back to MmPosition(200,800,300), CalibrationWall.Back to MmPosition(800,800,850),
        CalibrationWall.Left to MmPosition(0,200,250), CalibrationWall.Left to MmPosition(0,600,750),
        CalibrationWall.Right to MmPosition(1000,200,250), CalibrationWall.Right to MmPosition(1000,600,750),
        CalibrationWall.Top to MmPosition(200,200,1000), CalibrationWall.Top to MmPosition(800,600,1000))
    private fun tags(): List<WallTagEstimate> = points.mapIndexed { id, (wall, p) ->
        val radians = when (wall) { CalibrationWall.Front -> 0.0; CalibrationWall.Back -> Math.PI
            CalibrationWall.Left -> -Math.PI/2; CalibrationWall.Right -> Math.PI/2; CalibrationWall.Top -> 0.0 }
        val pose = Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(p.x.toFloat(),p.y.toFloat(),p.z.toFloat()),
            if(wall==CalibrationWall.Top) floatArrayOf((-Math.PI/2).toFloat(),0f,0f) else floatArrayOf(0f,0f,radians.toFloat()),0f))
        WallTagEstimate(WallTagAssignment(id,wall,100), frame * pose, 1.0,12,up)
    }
    private val datum = WallVerticalDatum(0,200)
    private fun solve(tags: List<WallTagEstimate> = tags(), source: WallDimensionSource = WallDimensionSource.Entered,
        datum: WallVerticalDatum = this.datum) = WallCalibrationSolver.solve(dimensions,source,tags,datum)

    @Test fun enteredDimensionsAndGravityGiveOneOrthonormalFrameWithTopCheck() {
        val solution = solve().solution!!
        assertEquals(dimensions, solution.dimensionsMm)
        assertArrayEquals(frame.values, solution.referenceFromProject.values, 0.02)
        val axes = (0..2).map { i -> solution.referenceFromProject.wallDirection(WallVector.from(DoubleArray(3) { if(it==i) 1.0 else 0.0 })) }
        axes.forEach { assertEquals(1.0,it.length(),1e-8) }
        assertEquals(0.0,axes[0].dot(axes[1]),1e-8)
        assertEquals(up, axes[2])
    }
    @Test fun adjacentWallsAndTopAreEnoughWhenDimensionsAreKnown() {
        val solution = solve(tags().filter { it.assignment.wall in listOf(CalibrationWall.Front,CalibrationWall.Left,CalibrationWall.Top) }).solution!!
        assertArrayEquals(frame.values,solution.referenceFromProject.values,0.02)
        assertEquals(dimensions,solution.dimensionsMm)
    }
    @Test fun scanningDimensionsUsesFourWallsTopAndOneBottomDatum() {
        val solution = WallCalibrationSolver.solve(MmPosition(0,0,0),WallDimensionSource.Scanned,tags(),datum).solution!!
        assertEquals(dimensions,solution.dimensionsMm)
        assertArrayEquals(frame.values,solution.referenceFromProject.values,0.02)
        assertNull(solve(tags().filter { it.assignment.wall != CalibrationWall.Back },WallDimensionSource.Scanned).solution)
        assertNull(solve(tags().filter { it.assignment.wall != CalibrationWall.Top },WallDimensionSource.Scanned).solution)
    }
    @Test fun datumHeightChangesOnlyVerticalOriginWhenDimensionsAreKnown() {
        val shifted = solve(datum=WallVerticalDatum(0,210)).solution!!
        assertEquals(frame.translation()[1]-10,shifted.referenceFromProject.translation()[1],0.01)
        assertEquals(dimensions,shifted.dimensionsMm)
    }
    @Test fun generatedMarkersPreserveFaceAndCornerOrientation() {
        val solution = solve().solution!!
        solution.markers.forEach { marker ->
            assertEquals(points[marker.id].second, marker.positionMm)
            val expected = Marker(marker.id,"apriltag",100,points[marker.id].second,
                tagRotationFor(TagPlane.valueOf(points[marker.id].first.name)))
            markerCornersInProjectFrame(expected).zip(markerCornersInProjectFrame(marker)).forEach { (a,b) ->
                assertEquals(a.x,b.x,0.01);assertEquals(a.y,b.y,0.01);assertEquals(a.z,b.z,0.01)
            }
        }
        assertEquals(800, solution.markers.first { it.id==2 }.positionMm.y - solution.markers.first { it.id==0 }.positionMm.y)
        assertEquals(1000, solution.markers.first { it.id==6 }.positionMm.x - solution.markers.first { it.id==4 }.positionMm.x)
    }

    @Test fun printedTagRollIncludingEulerSingularitySurvivesNormalMarkerPipeline() {
        for (degrees in listOf(-90,90,180,37)) {
            val roll=Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(0f,0f,0f),
                floatArrayOf(0f,Math.toRadians(degrees.toDouble()).toFloat(),0f),0f))
            val rolled=tags().map { it.copy(referenceFromTag=it.referenceFromTag*roll) }
            val solution=solve(rolled).solution!!
            for(marker in solution.markers) {
                val normalized=marker.asAprilTagCalibrationMarker(dimensions)
                val half=marker.sizeMm/2.0
                val local=listOf(WallVector(-half,0.0,half),WallVector(half,0.0,half),WallVector(half,0.0,-half),WallVector(-half,0.0,-half))
                val actual=markerCornersInProjectFrame(normalized)
                local.forEachIndexed { i,p ->
                    val expected=frame.inverseRigid().wallPoint(rolled[marker.id].referenceFromTag.wallPoint(p))
                    assertEquals(expected.x,actual[i].x,0.03);assertEquals(expected.y,actual[i].y,0.03);assertEquals(expected.z,actual[i].z,0.03)
                }
            }
        }
    }
    @Test fun smallNoiseDoesNotSkewTheRectangularBox() {
        val noisy = tags().mapIndexed { i,t -> t.copy(referenceFromTag=Transform3D(t.referenceFromTag.values.copyOf().apply {
            this[3] += if(i%2==0) 2 else -2; this[11] += if(i%3==0) 3 else -1
        })) }
        val solution = solve(noisy).solution!!
        assertEquals(dimensions,solution.dimensionsMm)
        assertTrue(solution.quality.rmsMm < 5)
        assertTrue(solution.referenceFromProject.distanceTo(frame) < 6)
    }
    @Test fun oneShiftedTagAmongThreeOnAWallIsExcluded() {
        val extra = tags()[0].let { t -> t.copy(assignment=t.assignment.copy(tagId=10),
            referenceFromTag=Transform3D(t.referenceFromTag.values.copyOf().apply {
                val shift = frame.wallDirection(WallVector(300.0,80.0,200.0))
                this[3]+=shift.x;this[7]+=shift.y;this[11]+=shift.z
            })) }
        val solution = solve(tags()+extra).solution!!
        assertEquals(listOf(10),solution.quality.excludedTagIds)
        assertFalse(solution.markers.any { it.id==10 })
        assertArrayEquals(frame.values,solution.referenceFromProject.values,0.05)
    }
    @Test fun missingDatumOrDuplicateIdsCannotBeAccepted() {
        assertNull(solve(datum=WallVerticalDatum(99,200)).solution)
        assertNull(solve(tags()+tags()[0]).solution)
        assertNull(solve(datum=WallVerticalDatum(0,-1)).solution)
        assertNull(solve(datum=WallVerticalDatum(0,2001)).solution)
    }
    @Test fun legacyJsonDefaultsToKnownPositionsAndAcceptedScanRoundTripsWithoutWorldPose() {
        val old = JsonProjectStore.projectFromJson("""{"project_name":"Old","sensors":[],"markers":[]}""")
        assertEquals(ReferenceGeometryMode.KnownTagPositions,old.referenceGeometryMode)
        assertFalse(old.needsWallCalibration)
        val solution = solve().solution!!
        val sensor = Sensor(1,"TEMP-A","Tank","Front",MmPosition(300,0,400),toleranceMm=50,instruction="")
        val project = Project("Scan","",listOf(sensor),solution.markers,dimensions,referenceGeometryMode=ReferenceGeometryMode.ScannedWalls,
            wallCalibration=WallCalibrationData(calibratedAt="test",dimensionsMm=dimensions,dimensionSource=WallDimensionSource.Entered,
                assignments=tags().map { it.assignment },datum=datum,quality=solution.quality,
                geometrySignature=wallGeometrySignature(solution.markers,solution.quality.usedTagIds)))
        val json = JsonProjectStore.projectToJson(project)
        val loaded = JsonProjectStore.projectFromJson(json)
        assertEquals(project,loaded)
        assertEquals(sensor,loaded.sensors.single())
        assertTrue(loaded.hasWallCalibration)
        assertFalse(json.contains("trackingFrameId"));assertFalse(json.contains("referenceFromProject"))
        assertTrue(loaded.copy(dimensionsMm=MmPosition(2000,800,1000)).needsWallCalibration)
        assertTrue(loaded.copy(markers=emptyList()).needsWallCalibration)
        assertTrue(loaded.copy(markers=loaded.markers.map { it.copy(sizeMm=200) }).needsWallCalibration)
        assertTrue(loaded.copy(markers=loaded.markers.map { it.copy(positionMm=it.positionMm.copy(x=it.positionMm.x+1)) }).needsWallCalibration)
    }
    private fun observation(time: Long, sequence: Long = time, frameId: Long = 1): WallTagObservation {
        val t=tags().first()
        val camera=t.referenceFromTag.values.copyOf().apply { this[3]+=t.normal.x*600;this[7]+=t.normal.y*600;this[11]+=t.normal.z*600 }
        return WallTagObservation(0,100,time,sequence,frameId,t.referenceFromTag,Transform3D(camera),up,0.2f,100f)
    }
    @Test fun sessionAccumulatesIndependentSamplesButNeverAcrossTrackingFramesOrTrackingLoss() {
        for (loseTracking in listOf(false,true)) {
            val session=WallCalibrationSession()
            val assignments=listOf(tags().first().assignment)
            for(time in 100L..1000L step 100) session.observe(WallScanFrame(1,1,true,listOf(observation(time)),null),assignments,time+20)
            assertEquals(1,session.estimates(assignments).size)
            session.observe(WallScanFrame(1,if(loseTracking) 1 else 2,!loseTracking,emptyList(),null),assignments,1200)
            assertTrue(session.invalidated);assertTrue(session.estimates(assignments).isEmpty())
            session.clear();assertFalse(session.invalidated)
        }
    }
    @Test fun repeatedStaleWrongSizedAndDifferentFrameSamplesDoNotCreateEvidence() {
        val assignments=listOf(tags().first().assignment)
        for(kind in listOf("repeat","stale","size","frame","tiny")) {
            val session=WallCalibrationSession()
            for(time in 100L..1000L step 100) {
                val o=when(kind) {
                    "repeat" -> observation(100)
                    "stale" -> observation(time-400)
                    "size" -> observation(time).copy(sizeMm=200)
                    "frame" -> observation(time,frameId=2)
                    else -> observation(time).copy(shortestEdgePx=20f)
                }
                session.observe(WallScanFrame(1,1,true,listOf(o),null),assignments,time+20)
            }
            assertTrue(kind,session.estimates(assignments).isEmpty())
        }
    }
    @Test fun topTagsAreRequiredAndAFlatRaisedCoverRetainsItsActualTagHeight() {
        assertNull(solve(tags().filter { it.assignment.wall != CalibrationWall.Top }).solution)
        val raised=tags().map { t -> if(t.assignment.wall==CalibrationWall.Top)
            t.copy(referenceFromTag=Transform3D(t.referenceFromTag.values.copyOf().apply { this[7]+=100.0 })) else t }
        assertNull(solve(raised).solution) // A higher lid may not silently change fixed tank dimensions.
        for(source in WallDimensionSource.entries) {
            val solved=solve(raised,source,WallVerticalDatum(0,200,topSurfaceOffsetMm=100)).solution!!
            assertEquals(dimensions,solved.dimensionsMm)
            assertTrue(solved.markers.filter { it.id>=8 }.all { it.positionMm.z==1100 })
        }
        val tilt=Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(0f,0f,0f),floatArrayOf(0.4f,0f,0f),0f))
        val tilted=tags().map { if(it.assignment.wall==CalibrationWall.Top) it.copy(referenceFromTag=it.referenceFromTag*tilt) else it }
        assertNull(solve(tilted).solution)
    }

    private fun tagObservation(tag: WallTagEstimate,time: Long, sequence: Long=time, camera: Transform3D=Transform3D.identity()) =
        WallTagObservation(tag.assignment.tagId,100,time,sequence,1,tag.referenceFromTag,camera,up,0.2f,100f)

    @Test fun optionalOverlapDiagnosticNeedsSharedCameraImagesAndSurvivesTagsLeavingView() {
        val pair=listOf(tags()[0],tags()[8]);val assignments=pair.map { it.assignment };val session=WallCalibrationSession()
        for(time in 100L..900L step 100) session.observe(WallScanFrame(1,1,true,listOf(tagObservation(pair[0],time)),null),assignments,time+20)
        for(time in 1000L..1800L step 100) session.observe(WallScanFrame(1,1,true,listOf(tagObservation(pair[1],time)),null),assignments,time+20)
        assertEquals(2,session.estimates(assignments).size)
        assertFalse(session.hasTopOverlap(listOf(0,8)))
        for(time in 1900L..2100L step 100) session.observe(WallScanFrame(1,1,true,pair.map { tagObservation(it,time) },null),assignments,time+20)
        assertTrue(session.hasTopOverlap(listOf(0,8)))
        assertFalse(session.hasTopOverlap(listOf(0))) // Excluded tags cannot verify the transition.
        session.observe(WallScanFrame(1,1,true,emptyList(),null),assignments,5000)
        assertTrue(session.hasTopOverlap(listOf(0,8))) // Neither tag needs to stay visible afterward.
        session.removeTag(8);assertFalse(session.hasTopOverlap(listOf(0,8)))
    }

    @Test fun mixedCamerasOrSequencesCannotPretendToSeeBothSidesOfACorner() {
        val pair=listOf(tags()[0],tags()[8]);val assignments=pair.map { it.assignment }
        for(mixCamera in listOf(false,true)) {
            val session=WallCalibrationSession()
            for(time in 100L..1000L step 100) {
                val top=tagObservation(pair[1],time).let { if(mixCamera) it.copy(referenceFromCameraCv=
                    Transform3D(Transform3D.identity().values.copyOf().apply { this[3]=100.0 })) else it.copy(sequence=time+1) }
                session.observe(WallScanFrame(1,1,true,listOf(tagObservation(pair[0],time),top),null),assignments,time+20)
            }
            assertFalse(session.hasTopOverlap(listOf(0,8)))
        }
    }

    @Test fun sideScanDefinesFootprintBeforeHeightAndOneArbitraryTopTagCompletesIt() {
        val sides=tags().filter { it.assignment.wall != CalibrationWall.Top }
        val footprint=WallCalibrationSolver.solveFootprint(MmPosition(0,0,0),WallDimensionSource.Scanned,sides,datum).solution!!
        assertEquals(MmPosition(1000,800,0),footprint.dimensionsMm)
        assertArrayEquals(frame.values,footprint.referenceFromProject.values,0.02)
        for(top in tags().filter { it.assignment.wall == CalibrationWall.Top }) {
            val complete=solve(sides+top,WallDimensionSource.Scanned).solution!!
            assertEquals(dimensions,complete.dimensionsMm)
            assertArrayEquals(footprint.referenceFromProject.values,complete.referenceFromProject.values,0.02)
        }
        val complete=solve(sides+tags().filter { it.assignment.wall==CalibrationWall.Top },WallDimensionSource.Scanned).solution!!
        assertEquals(10,complete.markers.size)
    }

    @Test fun lidDefinesTopWithoutAnyBottomTagInBothDimensionModes() {
        val datum=WallVerticalDatum(-1,0,sideHeightMm=1000)
        for(source in WallDimensionSource.entries) {
            val result=solve(source=source,datum=datum)
            assertNotNull(result.reason,result.solution)
            assertEquals(dimensions,result.solution!!.dimensionsMm)
            assertArrayEquals(frame.values,result.solution!!.referenceFromProject.values,0.02)
            assertEquals(1000,result.solution!!.markers.first { it.id==8 }.positionMm.z)
        }
        assertNull(solve(source=WallDimensionSource.Scanned,datum=WallVerticalDatum(-1,0)).solution)
    }
    @Test fun lidLinkFixesHeightWhileKeepingScannedSideContourAndYaw() {
        val datum=WallVerticalDatum(-1,0,sideHeightMm=1000)
        val footprint=WallCalibrationSolver.solveFootprint(MmPosition(0,0,0),WallDimensionSource.Scanned,tags(),datum).solution!!
        val linked=solve(source=WallDimensionSource.Scanned,datum=datum).solution!!
        assertEquals(MmPosition(1000,800,0),footprint.dimensionsMm)
        assertEquals(dimensions,linked.dimensionsMm)
        // Gravity is reference Y in this fixture: only that provisional origin component changes.
        for(i in frame.values.indices.filter { it!=7 }) assertEquals(footprint.referenceFromProject.values[i],linked.referenceFromProject.values[i],0.02)
        assertEquals(200.0,footprint.referenceFromProject.values[7]-linked.referenceFromProject.values[7],0.02)
        val json=WallCalibrationData(calibratedAt="lid",dimensionsMm=dimensions,dimensionSource=WallDimensionSource.Scanned,
            assignments=tags().map { it.assignment },datum=datum,quality=linked.quality).toWallJson()
        assertEquals(datum,json.readWallCalibration()!!.datum)
    }

    @Test fun oneTagOnEachRequiredFaceWorksForBothDimensionSources() {
        val datum=WallVerticalDatum(-1,0,sideHeightMm=1000)
        for(source in WallDimensionSource.entries) {
            val ids=if(source==WallDimensionSource.Entered) listOf(0,4,8) else listOf(0,2,4,6,8)
            val minimal=tags().filter { it.assignment.tagId in ids }
            val footprint=WallCalibrationSolver.solveFootprint(dimensions,source,minimal,datum)
            assertNotNull(footprint.reason,footprint.solution)
            val result=solve(minimal,source,datum)
            assertNotNull(result.reason,result.solution)
            assertEquals(dimensions,result.solution!!.dimensionsMm)
            assertArrayEquals(frame.values,result.solution!!.referenceFromProject.values,0.02)
            assertEquals(ids,result.solution!!.quality.usedTagIds)
        }
    }
    @Test fun smallCabinetAcceptsTagsWithOnlySixtyMillimetresHorizontalSpread() {
        val small=MmPosition(300,240,300)
        val compact=tags().map { t ->
            val id=t.assignment.tagId
            val original=points[id].second
            val first=points[id-id%2].second
            var point=WallVector(original.x*0.3,original.y*0.3,original.z*0.3)
            if(id%2==1 && t.assignment.wall.axis!=2) point=if(t.assignment.wall.axis==0)
                point.copy(y=first.y*0.3+60) else point.copy(x=first.x*0.3+60)
            val world=frame.wallPoint(point)
            t.copy(assignment=t.assignment.copy(sizeMm=40),referenceFromTag=Transform3D(t.referenceFromTag.values.copyOf().apply {
                this[3]=world.x;this[7]=world.y;this[11]=world.z
            }))
        }
        for(source in WallDimensionSource.entries) {
            val result=WallCalibrationSolver.solve(small,source,compact,WallVerticalDatum(-1,0,sideHeightMm=300))
            assertNotNull(result.reason,result.solution)
            assertEquals(small,result.solution!!.dimensionsMm)
            assertArrayEquals(frame.values,result.solution!!.referenceFromProject.values,0.02)
        }
    }
    @Test fun singleTagModeStillRequiresAdjacentWallsAndRejectsAConflictingOnlyReference() {
        val datum=WallVerticalDatum(-1,0,sideHeightMm=1000)
        assertNull(solve(tags().filter { it.assignment.tagId in listOf(0,8) },datum=datum).solution)
        assertNull(solve(tags().filter { it.assignment.tagId in listOf(0,2,8) },datum=datum).solution)
        assertNull(solve(tags().filter { it.assignment.tagId in listOf(0,4,8) },WallDimensionSource.Scanned,datum).solution)
        val bad=tags().filter { it.assignment.tagId in listOf(0,2,4,6,8) }.map { t ->
            if(t.assignment.tagId!=6) t else {
                val flipped=Transform3D.cameraCvFromTransformerPose(TransformerPose(floatArrayOf(0f,0f,0f),floatArrayOf(0f,0f,Math.PI.toFloat()),0f))
                t.copy(referenceFromTag=t.referenceFromTag*flipped)
            }
        }
        assertNull(solve(bad,WallDimensionSource.Scanned,datum).solution)
    }

}
