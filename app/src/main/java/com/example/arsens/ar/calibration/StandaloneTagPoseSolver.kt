package com.example.arsens.ar.calibration

import com.example.arsens.ar.*
import com.example.arsens.data.*
import kotlin.math.hypot

/** Existing IPPE/pose-selection code, expressed in a local front-tag frame at its center. */
fun solveStandaloneWallTags(detections: List<AprilTagDetection>, intrinsics: CameraIntrinsics,
    request: WallScanRequest): List<StandaloneWallTag> = detections
    .filter { detection -> detection.id in 0 until request.maxTagId && detections.count { it.id == detection.id } == 1 }
    .mapNotNull { detection ->
        val size = request.tagSizes[detection.id] ?: request.defaultSizeMm
        if (size !in 10..2000 || detection.cornersPx.size != 4) return@mapNotNull null
        val marker = Marker(detection.id, "apriltag", size, MmPosition(0,0,0), FloatVector(0f,0f,0f))
        val pose = estimateTransformerPoseFromAprilTags(listOf(detection), listOf(marker), intrinsics)?.pose ?: return@mapNotNull null
        val edge = detection.cornersPx.indices.minOf { i ->
            val a = detection.cornersPx[i]; val b = detection.cornersPx[(i+1)%4]
            hypot(a.xPx-b.xPx, a.yPx-b.yPx)
        }
        StandaloneWallTag(detection.id, size, Transform3D.cameraCvFromTransformerPose(pose), pose.reprojectionErrorPx, edge)
    }
