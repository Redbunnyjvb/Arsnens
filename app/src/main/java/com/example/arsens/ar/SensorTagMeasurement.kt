package com.example.arsens.ar

import com.example.arsens.data.*
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Same-image sensor pose. The temporary local marker is NEVER supplied to world tracking. */
fun measureSensorTagCenter(frame: AprilTagFrameResult, tagId: Int, sizeMm: Int): MmPosition? {
    if (!frame.detectionsFresh || sizeMm !in 5..2000) return null
    val detection = frame.detections.singleOrNull { it.id == tagId } ?: return null
    if (detection.cornersPx.size != 4 || detection.cornersPx.indices.minOf { index ->
            val a = detection.cornersPx[index]; val b = detection.cornersPx[(index + 1) % 4]
            hypot(a.xPx - b.xPx, a.yPx - b.yPx)
        } < 22f) return null
    val intrinsics = frame.cameraIntrinsics ?: return null
    val cameraFromProject = (frame.freshImageProjectionPose ?: frame.imageProjectionPose)
        ?.let(Transform3D::cameraCvFromTransformerPose) ?: return null
    val local = Marker(tagId, "apriltag", sizeMm, MmPosition(0, 0, 0), FloatVector(0f, 0f, 0f))
    val pose = estimateTransformerPoseFromAprilTags(listOf(detection), listOf(local), intrinsics)?.pose ?: return null
    if (!pose.reprojectionErrorPx.isFinite() || pose.reprojectionErrorPx > 1.5f) return null
    val translation = (cameraFromProject.inverseRigid() * Transform3D.cameraCvFromTransformerPose(pose)).translation()
    if (translation.any { !it.isFinite() || kotlin.math.abs(it) > 100000 }) return null
    return MmPosition(translation[0].roundToInt(), translation[1].roundToInt(), translation[2].roundToInt())
}
