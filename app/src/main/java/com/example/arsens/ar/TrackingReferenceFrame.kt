package com.example.arsens.ar

/** Stable local coordinates attached to the one native ARCore anchor for this transformer. */
data class TrackingReferenceFrame(val arFromReference: Transform3D) {
    fun cameraPose(arFromCamera: Transform3D): Transform3D = arFromReference.inverseRigid() * arFromCamera
    fun viewPose(cameraFromAr: Transform3D): Transform3D = cameraFromAr * arFromReference
}
