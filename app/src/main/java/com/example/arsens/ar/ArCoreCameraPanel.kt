package com.example.arsens.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.media.Image
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.util.Log
import com.example.arsens.data.Marker
import com.example.arsens.data.MmPosition
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.objdetect.Objdetect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

/** Live perf-telemetrie voor de debug-HUD: [arCoreHz] = nieuwe ARCore-cameraframes per seconde
 *  (op basis van de frame-timestamp), [uiHz] = resultaten die de overlays bereiken per seconde
 *  (ná coalescing). Beide worden ~2×/sec op de main thread bijgewerkt. */
data class ArPerfStats(val arCoreHz: Float = 0f, val uiHz: Float = 0f)

private val trackingFrameIds = AtomicLong()

@Composable
fun ArCoreCameraPanel(
    knownMarkers: List<Marker>,
    onResult: (AprilTagFrameResult) -> Unit,
    modifier: Modifier = Modifier,
    tagDictionary: TagDictionaryOption = TagDictionaryOption.DEFAULT,
    tagPoseMode: TagPoseMode = TagPoseMode.DEFAULT,
    calibrationRevision: Int = 0,
    onPerfStats: (ArPerfStats) -> Unit = {}
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }
    val latestMarkers by rememberUpdatedState(knownMarkers)
    val latestOnResult by rememberUpdatedState(onResult)
    val latestDictionary by rememberUpdatedState(tagDictionary)
    val latestPoseMode by rememberUpdatedState(tagPoseMode)
    val latestCalibrationRevision by rememberUpdatedState(calibrationRevision)
    val latestOnPerfStats by rememberUpdatedState(onPerfStats)
    val renderer = remember {
        ArCoreCameraRenderer(
            context = context,
            knownMarkers = { latestMarkers },
            onResult = { latestOnResult(it) },
            tagDictionaryId = { latestDictionary.openCvId },
            tagPoseMode = { latestPoseMode },
            calibrationRevision = { latestCalibrationRevision },
            onPerfStats = { latestOnPerfStats(it) }
        )
    }
    val surfaceView = remember {
        GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(hasPermission) {
        if (hasPermission) {
            surfaceView.onResume()
            renderer.onResume()
        }
        onDispose {
            renderer.onPause()
            surfaceView.onPause()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            renderer.onDispose()
        }
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (hasPermission) {
            AndroidView(factory = { surfaceView }, modifier = Modifier.fillMaxSize())
        } else {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Camera-toegang is nodig voor ARCore tracking en AprilTag kalibratie.",
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Camera toestaan")
                }
            }
        }
    }
}

private class ArCoreCameraRenderer(
    private val context: Context,
    private val knownMarkers: () -> List<Marker>,
    private val onResult: (AprilTagFrameResult) -> Unit,
    private val tagDictionaryId: () -> Int = { Objdetect.DICT_APRILTAG_36h11 },
    private val tagPoseMode: () -> TagPoseMode = { TagPoseMode.DEFAULT },
    private val calibrationRevision: () -> Int = { 0 },
    private val onPerfStats: (ArPerfStats) -> Unit = {}
) : GLSurfaceView.Renderer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeDictionaryId = tagDictionaryId()
    private var detector = AprilTagDetector(activeDictionaryId)
    private val detectorExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val detectionLock = Any()
    private val backgroundRenderer = ArCoreBackgroundRenderer()
    private val fusion = ArCoreAprilTagFusion()
    private var session: Session? = null
    private var nativeAnchor: Anchor? = null
    private var trackingFrameId = trackingFrameIds.incrementAndGet()
    private val nativeRecovery = NativeAnchorRecovery { trackingFrameIds.incrementAndGet() }
    private var anchorCreationError: String? = null
    private var installRequested = false
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    @Volatile
    private var detectionInFlight = false
    // Stap 4: tijd-gebaseerde detectie-scheduling i.p.v. frame-tellen. De cadans hangt af van de
    // laatst bekende trackingstatus (gecachet uit het vorige fuse-result).
    private var lastDetectionStartElapsedMillis = 0L
    private var lastDetectionTimestampNs = Long.MIN_VALUE
    private var lastTrackingStatus: ArTrackingStatus = ArTrackingStatus.NoPose
    private var lastDetectionAgeMillis = Long.MAX_VALUE
    private var latestDetectionSequence = 0
    private var consumedDetectionSequence = 0
    private var latestDetectionPacket: AprilTagDetectionPacket? = null
    private var knownMarkerSignatures: Map<Int, String> = emptyMap()
    // Stap 1: conflated publisher. Hoogstens één pending main-thread runnable; we bewaren
    // altijd het nieuwste AprilTagFrameResult en coalescen oudere wanneer de UI achterloopt.
    // Dit verlaagt geen trackingkwaliteit (detectie/fusion blijven per frame draaien), het
    // voorkomt alleen een main-thread backlog van 30-60 state-updates/sec.
    private val resultLock = Any()
    private var pendingUiResult: AprilTagFrameResult? = null
    private var uiPostScheduled = false
    private var producedFrames = 0L
    private var deliveredResults = 0L
    private var lastPublishLogMillis = 0L
    // Perf-HUD: nieuwe ARCore-cameraframes (gewijzigde frame-timestamp). Geschreven op de GL-thread
    // in postFrameResult, gelezen op de main thread in maybePublishPerfStats — daarom onder de lock.
    private var arCoreFrames = 0L
    private var lastArCoreTimestampNs = 0L
    private var perfWindowStartMillis = 0L
    private var perfWindowArCoreBase = 0L
    private var perfWindowDeliveredBase = 0L
    private val deliverPendingUiResult = Runnable {
        // uiPostScheduled wordt hier (binnen de lock) op false gezet vóór delivery, zodat een
        // result dat tijdens onResult() binnenkomt een nieuwe post triggert en niet verloren gaat.
        val latest = synchronized(resultLock) {
            val value = pendingUiResult
            pendingUiResult = null
            uiPostScheduled = false
            if (value != null) deliveredResults++
            value
        }
        if (latest != null) {
            onResult(latest)
            maybeLogPublishStats()
            maybePublishPerfStats()
        }
    }

    fun onResume() {
        if (session == null) {
            val activity = context.findActivity()
            if (activity == null) {
                postError("ARCore heeft een Activity-context nodig.")
                return
            }
            val installStatus = runCatching {
                ArCoreApk.getInstance().requestInstall(activity, !installRequested)
            }.getOrElse {
                postError("Google Play Services voor AR kon niet gecontroleerd worden: ${it.message}")
                return
            }
            if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installRequested = true
                postError("Installeer of update Google Play Services voor AR en open daarna de app opnieuw.")
                return
            }
            session = runCatching {
                Session(context).also { arSession ->
                    val config = Config(arSession).apply {
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        focusMode = Config.FocusMode.AUTO
                        lightEstimationMode = Config.LightEstimationMode.DISABLED
                        depthMode = if (
                            ENABLE_ARCORE_DEPTH_CURSOR &&
                            arSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                        ) {
                            Config.DepthMode.AUTOMATIC
                        } else {
                            Config.DepthMode.DISABLED
                        }
                        planeFindingMode = Config.PlaneFindingMode.DISABLED
                    }
                    arSession.configure(config)
                }
            }.getOrElse {
                postError("ARCore sessie kon niet starten: ${it.message}")
                return
            }
        }
        runCatching {
            session?.resume()
            session?.setDisplayGeometry(currentDisplayRotation(), surfaceWidth, surfaceHeight)
        }.onFailure {
            postError("ARCore camera kon niet hervatten: ${it.message}")
        }
    }

    fun onPause() {
        session?.pause()
    }

    fun onDispose() {
        detectorExecutor.shutdownNow()
        nativeAnchor?.detach()
        nativeAnchor = null
        session?.pause()
        session?.close()
        session = null
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        backgroundRenderer.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        session?.setDisplayGeometry(currentDisplayRotation(), surfaceWidth, surfaceHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        val arSession = session ?: return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        arSession.setCameraTextureName(backgroundRenderer.textureId)

        val frame = try {
            arSession.update()
        } catch (_: CameraNotAvailableException) {
            postError("ARCore camera is niet beschikbaar.")
            return
        } catch (error: Throwable) {
            postError("ARCore frame mislukt: ${error.message}")
            return
        }
        backgroundRenderer.draw(frame)
        postFrameResult(frame)
    }

    private fun postFrameResult(frame: Frame) {
        val frameCalibrationRevision = calibrationRevision()
        val camera = frame.camera
        val cameraTracking = camera.trackingState == TrackingState.TRACKING
        // Tel alleen een nieuw ARCore-cameraframe als de timestamp echt veranderde — onDrawFrame
        // draait continu op de display-refresh, maar ARCore levert vaak op 30 Hz een nieuw beeld.
        val ts = frame.timestamp
        if (ts != lastArCoreTimestampNs) {
            lastArCoreTimestampNs = ts
            synchronized(resultLock) { arCoreFrames++ }
        }
        val arPoseMatrix = FloatArray(16)
        val viewMatrix = FloatArray(16)
        val projectionMatrix = FloatArray(16)
        camera.pose.toMatrix(arPoseMatrix, 0)
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.01f, 100f)
        // ARCore poses are in meters, while solvePnP and the project model use millimeters.
        val arFromCameraGl = Transform3D
            .fromOpenGlColumnMajor(arPoseMatrix)
            .withScaledTranslation(METERS_TO_MILLIMETERS)
        val cameraGlFromAr = Transform3D
            .fromOpenGlColumnMajor(viewMatrix)
            .withScaledTranslation(METERS_TO_MILLIMETERS)

        val markers = knownMarkers().toList()
        refreshDetectorIfNeeded()
        resetFusionIfMarkersChanged(markers)
        if (nativeAnchor?.trackingState == TrackingState.STOPPED) resetTrackingReference()
        val nowMillis = SystemClock.elapsedRealtime()
        val expectedCaptureFrameId = nativeRecovery.captureFrameId(cameraTracking, nativeAnchor?.trackingState, trackingFrameId, nowMillis)
        var tagPacket = pollLatestDetectionPacket(knownMarkerSignatures)
            ?.takeIf { it.result.trackingFrameId == expectedCaptureFrameId }
        if (nativeAnchor?.trackingState == TrackingState.PAUSED && tagPacket != null) {
            tagPacket = if (nativeRecovery.observe(tagPacket.result, tagPacket.arFromCameraGlAtCapture, nowMillis)) {
                establishTrackingReference(tagPacket)
            } else null
        }
        if (nativeAnchor == null && cameraTracking) {
            tagPacket = establishTrackingReference(tagPacket)
        }
        val nativeTracking = nativeAnchor?.trackingState == TrackingState.TRACKING
        val reference = nativeAnchor?.takeIf { nativeTracking }?.let { TrackingReferenceFrame(it.pose.toMillimeterTransform()) }
        val tracking = cameraTracking && nativeTracking
        val referenceFromCameraGl = reference?.cameraPose(arFromCameraGl) ?: arFromCameraGl
        val cameraGlFromReference = reference?.viewPose(cameraGlFromAr) ?: cameraGlFromAr
        val detectionFrameId = nativeRecovery.captureFrameId(cameraTracking, nativeAnchor?.trackingState, trackingFrameId, nowMillis)
        if (detectionFrameId != null) {
            submitTagDetectionIfNeeded(frame, referenceFromCameraGl, markers, detectionFrameId,
                if (nativeTracking) fusion.predictedCameraPoseAt(referenceFromCameraGl) else null)
        }
        val tagResult = tagPacket?.result ?: AprilTagFrameResult(
            trackingStatus = if (tracking) ArTrackingStatus.ArCoreTracking else ArTrackingStatus.NoPose
        )
        val depthArFromPoint = if (
            ENABLE_ARCORE_DEPTH_CURSOR &&
            tracking
        ) {
            depthHitArFromPoint(frame)
        } else {
            null
        }
        val result = fusion.fuse(
            tagResult = tagResult,
            tagArFromCameraGl = tagPacket?.arFromCameraGlAtCapture,
            currentArFromCameraGl = referenceFromCameraGl,
            currentCameraGlFromAr = cameraGlFromReference,
            depthArFromPoint = depthArFromPoint,
            projectionMatrixColumnMajor = projectionMatrix,
            arTracking = tracking,
            displayWidth = surfaceWidth,
            displayHeight = surfaceHeight,
            persistentTrackingFrame = nativeAnchor != null,
            currentTrackingFrameId = trackingFrameId
        ).let { fused ->
            fused.copy(calibrationRevision = frameCalibrationRevision, trackingFrameId = trackingFrameId,
                nativeAnchorTracking = tracking,
                correctionState = if (nativeAnchor?.trackingState == TrackingState.PAUSED) AnchorCorrectionState.TrackingPaused else fused.correctionState,
                fusionDiagnostic = nativeRecovery.detail ?: nativeRecovery.diagnostic ?: fused.fusionDiagnostic,
                poseDiagnostic = anchorCreationError ?: if (cameraTracking && nativeAnchor != null && !nativeTracking)
                    nativeRecovery.diagnostic else fused.poseDiagnostic)
        }
        // Stap 4: status + detectie-versheid onthouden zodat submitTagDetectionIfNeeded de volgende
        // cadans kan kiezen (snel zodra de tag niet vers in beeld is → her-acquisitie blijft snel).
        lastTrackingStatus = result.trackingStatus
        lastDetectionAgeMillis = result.detectionAgeMillis
        publishResultToMain(result)
    }


    private fun resetTrackingReference() {
        nativeAnchor?.detach()
        nativeAnchor = null
        trackingFrameId = trackingFrameIds.incrementAndGet()
        anchorCreationError = null
        fusion.reset()
        nativeRecovery.reset()
        synchronized(detectionLock) {
            latestDetectionPacket = null
            consumedDetectionSequence = latestDetectionSequence
        }
    }

    private fun establishTrackingReference(packet: AprilTagDetectionPacket?): AprilTagDetectionPacket? {
        val detection = packet?.result ?: return packet
        val pose = detection.transformerPose ?: return packet
        val captureTime = detection.capturedAtElapsedMillis ?: return packet
        if (detection.referenceConflict || SystemClock.elapsedRealtime() - captureTime > DETECTION_FRESH_MILLIS) return packet
        val arSession = session ?: return packet
        val point = detection.referencePointMm ?: return packet
        val arFromTransformer = packet.arFromCameraGlAtCapture * Transform3D.cameraGlFromCameraCv() *
            Transform3D.cameraCvFromTransformerPose(pose)
        val location = arFromTransformer.transformPoint(doubleArrayOf(point.x.toDouble(), point.y.toDouble(), point.z.toDouble()))
        if (location.any { !it.isFinite() }) return null
        val created = runCatching {
            arSession.createAnchor(Pose(FloatArray(3) { (location[it] / METERS_TO_MILLIMETERS).toFloat() },
                floatArrayOf(0f, 0f, 0f, 1f)))
        }.getOrElse {
            anchorCreationError = "AR-anker kon niet worden gemaakt: ${it.message}"
            return null
        }
        nativeAnchor?.detach()
        nativeAnchor = created
        trackingFrameId = trackingFrameIds.incrementAndGet()
        anchorCreationError = null
        fusion.reset()
        nativeRecovery.reset()
        Log.i("ARSensFusion", "native-anchor-created frame=$trackingFrameId")
        // This first packet was captured before the anchor existed. Convert it once; any
        // other in-flight packet from that old coordinate frame is discarded by its frame ID.
        if (created.trackingState != TrackingState.TRACKING) return null
        return packet.copy(result = detection.copy(trackingFrameId = trackingFrameId),
            arFromCameraGlAtCapture = TrackingReferenceFrame(created.pose.toMillimeterTransform()).cameraPose(packet.arFromCameraGlAtCapture))
    }

    private fun refreshDetectorIfNeeded() {
        val requested = tagDictionaryId()
        if (!detectionInFlight && requested != activeDictionaryId) {
            activeDictionaryId = requested
            detector = AprilTagDetector(requested)
            resetTrackingReference()
        }
    }


    private fun publishResultToMain(result: AprilTagFrameResult) {
        // Conflated publish: nieuwe results overschrijven het pending result i.p.v. een
        // main-thread backlog op te bouwen. Er staat hoogstens één runnable in de queue.
        var shouldPost = false
        synchronized(resultLock) {
            pendingUiResult = result
            producedFrames++
            if (!uiPostScheduled) {
                uiPostScheduled = true
                shouldPost = true
            }
        }
        if (shouldPost) {
            mainHandler.post(deliverPendingUiResult)
        }
    }

    private fun maybeLogPublishStats() {
        // Debug-only en gethrottled, zodat de coalescing-counters geen nieuwe logcat-spam geven.
        if (!Log.isLoggable("ARSensPublish", Log.DEBUG)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastPublishLogMillis < PUBLISH_LOG_INTERVAL_MILLIS) return
        lastPublishLogMillis = now
        val produced: Long
        val delivered: Long
        synchronized(resultLock) {
            produced = producedFrames
            delivered = deliveredResults
        }
        Log.d(
            "ARSensPublish",
            "producedFrames=$produced deliveredResults=$delivered coalescedResults=${produced - delivered}"
        )
    }

    /** Berekent ~2×/sec de ARCore-framecadans en overlay-cadans uit de tellers en levert ze aan de
     *  debug-HUD. Draait op de main thread (vanuit [deliverPendingUiResult]); waarden worden tussen
     *  vensters vastgehouden zodat de HUD niet flikkert. */
    private fun maybePublishPerfStats() {
        val now = SystemClock.elapsedRealtime()
        val arCore: Long
        val delivered: Long
        synchronized(resultLock) {
            arCore = arCoreFrames
            delivered = deliveredResults
        }
        if (perfWindowStartMillis == 0L) {
            perfWindowStartMillis = now
            perfWindowArCoreBase = arCore
            perfWindowDeliveredBase = delivered
            return
        }
        val elapsed = now - perfWindowStartMillis
        if (elapsed < PERF_WINDOW_MILLIS) return
        val arCoreHz = (arCore - perfWindowArCoreBase) * 1000f / elapsed
        val uiHz = (delivered - perfWindowDeliveredBase) * 1000f / elapsed
        perfWindowStartMillis = now
        perfWindowArCoreBase = arCore
        perfWindowDeliveredBase = delivered
        onPerfStats(ArPerfStats(arCoreHz, uiHz))
    }

    private fun depthHitArFromPoint(frame: Frame): Transform3D? =
        runCatching {
            val hits = frame.hitTest(surfaceWidth / 2f, surfaceHeight / 2f)
            hits.firstOrNull { it.trackable.javaClass.simpleName == "DepthPoint" }
                ?.hitPose
                ?.let { pose ->
                    val matrix = FloatArray(16)
                    pose.toMatrix(matrix, 0)
                    Transform3D
                        .fromOpenGlColumnMajor(matrix)
                        .withScaledTranslation(METERS_TO_MILLIMETERS)
                }
        }.getOrNull()

    private fun submitTagDetectionIfNeeded(
        frame: Frame,
        arFromCameraGlAtCapture: Transform3D,
        markers: List<Marker>,
        captureFrameId: Long,
        predictedCameraPose: TransformerPose?
    ) {
        // Stap 4: backpressure (detectionInFlight) + tijd-gebaseerde cadans op elapsedRealtime().
        if (detectionInFlight) return
        if (frame.timestamp == lastDetectionTimestampNs) return
        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - lastDetectionStartElapsedMillis < detectionIntervalMillisFor(lastTrackingStatus, lastDetectionAgeMillis)) return
        val markerSignatures = knownMarkerSignatures
        val image = try {
            frame.acquireCameraImage()
        } catch (_: NotYetAvailableException) {
            return
        } catch (error: Throwable) {
            publishDetectionPacket(
                AprilTagDetectionPacket(
                    result = AprilTagFrameResult(trackingFrameId = captureFrameId,
                        errorMessage = error.message ?: "ARCore camera image kon niet gelezen worden"),
                    arFromCameraGlAtCapture = arFromCameraGlAtCapture,
                    markerSignatures = markerSignatures
                )
            )
            return
        }
        val intrinsics = frame.camera.imageIntrinsics
        val focal = intrinsics.focalLength
        val principal = intrinsics.principalPoint
        val imageToViewMapper = frame.captureImageToViewMapper(
            imageWidth = image.width,
            imageHeight = image.height,
            displayWidth = surfaceWidth,
            displayHeight = surfaceHeight
        )
        val pendingFrame = try {
            image.copyLumaFrame(
                markers = markers,
                intrinsics = CameraIntrinsics(
                    fx = focal.getOrNull(0) ?: approximateCameraIntrinsics(image.width, image.height).fx,
                    fy = focal.getOrNull(1) ?: approximateCameraIntrinsics(image.width, image.height).fy,
                    cx = principal.getOrNull(0) ?: image.width / 2f,
                    cy = principal.getOrNull(1) ?: image.height / 2f
                ),
                arFromCameraGlAtCapture = arFromCameraGlAtCapture,
                imageToViewMapper = imageToViewMapper,
                markerSignatures = markerSignatures,
                trackingFrameId = captureFrameId,
                predictedCameraPose = predictedCameraPose,
                poseMode = tagPoseMode(),
                capturedAtElapsedMillis = nowElapsed,
                detectionSequence = frame.timestamp
            )
        } finally {
            image.close()
        }
        detectionInFlight = true
        lastDetectionStartElapsedMillis = nowElapsed
        lastDetectionTimestampNs = frame.timestamp
        runCatching {
            detectorExecutor.execute {
                try {
                    val result = detectTags(pendingFrame)
                    publishDetectionPacket(
                        AprilTagDetectionPacket(
                            result = result,
                            arFromCameraGlAtCapture = pendingFrame.arFromCameraGlAtCapture,
                            markerSignatures = pendingFrame.markerSignatures
                        )
                    )
                } finally {
                    // Stap 4: pas hier vrijgeven — ná publicatie — zodat de volgende frame geen
                    // nieuwe detectie start vóór dit packet beschikbaar is (publicatie-race weg).
                    detectionInFlight = false
                }
            }
        }.onFailure {
            detectionInFlight = false
            publishDetectionPacket(
                AprilTagDetectionPacket(
                    result = AprilTagFrameResult(trackingFrameId = pendingFrame.trackingFrameId,
                        errorMessage = "AprilTag analyse kon niet starten: ${it.message}"),
                    arFromCameraGlAtCapture = arFromCameraGlAtCapture,
                    markerSignatures = markerSignatures
                )
            )
        }
    }

    private fun detectTags(frame: PendingAprilTagFrame): AprilTagFrameResult {
        if (!OpenCvRuntime.ensureLoaded()) {
            return AprilTagFrameResult(
                imageWidth = frame.width,
                imageHeight = frame.height,
                trackingFrameId = frame.trackingFrameId,
                capturedAtElapsedMillis = frame.capturedAtElapsedMillis,
                detectionSequence = frame.detectionSequence,
                errorMessage = OpenCvRuntime.lastError ?: "OpenCV native library kon niet geladen worden"
            )
        }
        val gray = Mat(frame.height, frame.width, CvType.CV_8UC1)
        return try {
            gray.put(0, 0, frame.luma)
            detector.detect(
                gray = gray,
                knownMarkers = frame.markers,
                cameraIntrinsics = frame.intrinsics,
                poseMode = frame.poseMode,
                predictedCameraPose = frame.predictedCameraPose
            ).withScreenDetections(frame.imageToViewMapper).copy(
                capturedAtElapsedMillis = frame.capturedAtElapsedMillis,
                detectionSequence = frame.detectionSequence,
                trackingFrameId = frame.trackingFrameId
            )
        } catch (error: Throwable) {
            AprilTagFrameResult(
                imageWidth = frame.width,
                imageHeight = frame.height,
                trackingFrameId = frame.trackingFrameId,
                capturedAtElapsedMillis = frame.capturedAtElapsedMillis,
                detectionSequence = frame.detectionSequence,
                errorMessage = error.message ?: "AprilTag detectie mislukt"
            )
        } finally {
            // detectionInFlight wordt door de executor-block vrijgegeven ná publicatie (stap 4).
            gray.release()
        }
    }

    private fun publishDetectionPacket(packet: AprilTagDetectionPacket) {
        synchronized(detectionLock) {
            latestDetectionPacket = packet
            latestDetectionSequence++
        }
    }

    private fun pollLatestDetectionPacket(markerSignatures: Map<Int, String>): AprilTagDetectionPacket? =
        synchronized(detectionLock) {
            if (latestDetectionSequence == consumedDetectionSequence) {
                null
            } else {
                consumedDetectionSequence = latestDetectionSequence
                latestDetectionPacket?.takeIf { packet -> packet.markerSignatures == markerSignatures }
            }
        }

    private fun postError(message: String) {
        val revision = calibrationRevision()
        val referenceId = trackingFrameId
        mainHandler.post {
            onResult(AprilTagFrameResult(calibrationRevision = revision, trackingFrameId = referenceId,
                errorMessage = message, trackingStatus = ArTrackingStatus.NoPose))
        }
    }

    private fun resetFusionIfMarkersChanged(markers: List<Marker>) {
        val nextSignatures = markers.associate { marker -> marker.id to markerSignature(marker) }
        val previousSignatures = knownMarkerSignatures
        if (previousSignatures != nextSignatures) {
            resetTrackingReference()
        }
        knownMarkerSignatures = nextSignatures
    }

    private fun markerSignature(marker: Marker): String =
        listOf(
            marker.type,
            marker.sizeMm,
            marker.positionMm.x,
            marker.positionMm.y,
            marker.positionMm.z,
            marker.rotationDeg.x,
            marker.rotationDeg.y,
            marker.rotationDeg.z,
            marker.active,
            marker.poseWeight,
            tagPoseMode().name,
            calibrationRevision()
        ).joinToString(",")

    private fun currentDisplayRotation(): Int {
        val rotation = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (context as? Activity)?.windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
        return rotation
    }
}

private fun Pose.toMillimeterTransform(): Transform3D {
    val matrix = FloatArray(16)
    toMatrix(matrix, 0)
    return Transform3D.fromOpenGlColumnMajor(matrix).withScaledTranslation(METERS_TO_MILLIMETERS)
}


private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

internal class ArCoreAprilTagFusion(
    private val logDecision: (String) -> Unit = { Log.i("ARSensFusion", it) }
) {
    private val anchorFilter = AnchorPoseFilter()
    private var lastDetection = AprilTagFrameResult()
    private var lastCalibrationMillis = 0L
    private var lastReprojectionErrorPx = 0f
    private var previousCamera: Transform3D? = null
    private var wasTracking = false
    private var relocalizing = false
    private var anchorSettled = false
    private var lastEvent: String? = null
    private var lastReason: String? = null
    private var lastProcessedSequence = 0L
    private var captureArFromCameraCv: Transform3D? = null
    private var lastLoggedDecision: String? = null
    private var lastLogMillis = 0L
    private var lastMotionMm: Float? = null
    private var lastMotionDeg: Float? = null
    private var lastAnchorImageErrorPx: Float? = null
    private var referenceConflictActive = false
    private var lastDeltaMm: Double? = null
    private var lastDeltaDeg: Double? = null
    private var lastSamples = 0

    /** Express the world prediction in this capture's camera, not the previous image's camera. */
    fun predictedCameraPoseAt(arFromCameraGl: Transform3D): TransformerPose? =
        anchorFilter.prediction?.takeUnless { relocalizing }?.let {
            ((arFromCameraGl * Transform3D.cameraGlFromCameraCv()).inverseRigid() * it)
                .toTransformerPose(lastReprojectionErrorPx)
        }

    fun reset() {
        anchorFilter.reset()
        activeTrackingFrameId = null
        lastDetection = AprilTagFrameResult()
        lastCalibrationMillis = 0L
        previousCamera = null
        wasTracking = false
        relocalizing = false
        anchorSettled = false
        lastEvent = null
        lastReason = null
        lastProcessedSequence = 0L
        captureArFromCameraCv = null
        lastMotionMm = null
        lastMotionDeg = null
        lastAnchorImageErrorPx = null
        referenceConflictActive = false
        lastUpdateDelta = ""
        lastDeltaMm = null
        lastDeltaDeg = null
        lastSamples = 0
    }

    fun fuse(
        tagResult: AprilTagFrameResult,
        tagArFromCameraGl: Transform3D?,
        currentArFromCameraGl: Transform3D,
        currentCameraGlFromAr: Transform3D,
        depthArFromPoint: Transform3D?,
        projectionMatrixColumnMajor: FloatArray,
        arTracking: Boolean,
        displayWidth: Int,
        displayHeight: Int,
        nowMillis: Long = SystemClock.elapsedRealtime(),
        persistentTrackingFrame: Boolean = false,
        currentTrackingFrameId: Long = 0L
    ): AprilTagFrameResult {
        if (activeTrackingFrameId != null && activeTrackingFrameId != currentTrackingFrameId) reset()
        activeTrackingFrameId = currentTrackingFrameId
        val previous = previousCamera
        if (!persistentTrackingFrame && anchorFilter.anchor != null && ((!arTracking && wasTracking) ||
                (arTracking && previous != null &&
                    (previous.distanceTo(currentArFromCameraGl) > MAX_ARCORE_FRAME_JUMP_MM ||
                        previous.rotationAngleDegreesTo(currentArFromCameraGl) > MAX_ARCORE_FRAME_JUMP_ANGLE_DEG)))) {
            relocalizing = true
            anchorSettled = false
            anchorFilter.clearPending()
            anchorFilter.cancelCorrection()
        }
        anchorFilter.advance(nowMillis, arTracking && !relocalizing && !referenceConflictActive)
        anchorSettled = anchorFilter.isSettled
        previousCamera = if (arTracking) currentArFromCameraGl else null
        wasTracking = arTracking
        val cvToGl = Transform3D.cameraGlFromCameraCv()
        val currentArFromCameraCv = currentArFromCameraGl * cvToGl
        val hasPacket = tagResult.trackingFrameId == currentTrackingFrameId &&
            tagArFromCameraGl != null && tagResult.capturedAtElapsedMillis != null &&
            tagResult.detectionSequence > lastProcessedSequence
        var motionMm: Float? = null
        var motionDeg: Float? = null
        if (hasPacket) {
            lastProcessedSequence = tagResult.detectionSequence
            lastDetection = tagResult
            captureArFromCameraCv = tagArFromCameraGl!! * cvToGl
            motionMm = tagArFromCameraGl!!.distanceTo(currentArFromCameraGl).toFloat()
            motionDeg = tagArFromCameraGl.rotationAngleDegreesTo(currentArFromCameraGl).toFloat()
            lastMotionMm = motionMm
            lastMotionDeg = motionDeg
            val age = (nowMillis - tagResult.capturedAtElapsedMillis!!).coerceAtLeast(0L)
            val tagPose = tagResult.transformerPose
            val evidenceIds = tagResult.verifiedReferenceIds.ifEmpty { tagResult.poseMarkerIds }.distinct()
            val usesExcluded = (tagResult.poseMarkerIds + evidenceIds).any { it in tagResult.rejectedMarkerIds }
            val freshConflict = age <= DETECTION_FRESH_MILLIS && arTracking && (tagResult.referenceConflict || usesExcluded)
            if (tagResult.referenceConflict || usesExcluded || age > DETECTION_FRESH_MILLIS || !arTracking || tagPose == null) {
                if (freshConflict) {
                    referenceConflictActive = true
                    anchorFilter.cancelCorrection()
                }
                if (freshConflict || age > DETECTION_FRESH_MILLIS || !arTracking) anchorFilter.clearPending()
                if (!arTracking) anchorSettled = false
                lastDeltaMm = null
                lastDeltaDeg = null
                lastSamples = 0
                lastUpdateDelta = ""
                lastEvent = "REJECT"
                lastReason = when {
                    age > DETECTION_FRESH_MILLIS -> "stale-detection"
                    !arTracking -> "tracking-lost"
                    usesExcluded -> "excluded-reference"
                    tagResult.referenceConflict -> "reference-conflict"
                    else -> "no-reference"
                }
            } else {
                val candidate = tagArFromCameraGl * cvToGl * Transform3D.cameraCvFromTransformerPose(tagPose)
                val point = tagResult.referencePointMm
                val referencePoint = doubleArrayOf(point?.x?.toDouble() ?: 0.0, point?.y?.toDouble() ?: 0.0, point?.z?.toDouble() ?: 0.0)
                lastDeltaMm = anchorFilter.anchor?.let {
                    val a = it.transformPoint(referencePoint)
                    val b = candidate.transformPoint(referencePoint)
                    kotlin.math.sqrt(a.indices.sumOf { axis -> (a[axis] - b[axis]) * (a[axis] - b[axis]) })
                }
                lastDeltaDeg = anchorFilter.anchor?.rotationAngleDegreesTo(candidate)
                val imageError = anchorFilter.anchor?.takeUnless { relocalizing }?.let { anchor ->
                    anchorImageErrorPx(captureArFromCameraCv!!.inverseRigid() * anchor, tagResult.cameraIntrinsics,
                        tagResult.referenceMarkers, tagResult.detections, evidenceIds)
                }
                lastAnchorImageErrorPx = imageError
                val pendingImageError = anchorFilter.initializationCandidate?.let { pending ->
                        anchorImageErrorPx(captureArFromCameraCv!!.inverseRigid() * pending, tagResult.cameraIntrinsics,
                            tagResult.referenceMarkers, tagResult.detections, evidenceIds)
                }
                // Pixel agreement alone can conceal depth error. Allow noisy orientation
                // for a small planar tag only when its observed center also stays close.
                val targetImageError = anchorFilter.correctionGoal?.let {
                    anchorImageErrorPx(captureArFromCameraCv!!.inverseRigid() * it, tagResult.cameraIntrinsics,
                        tagResult.referenceMarkers, tagResult.detections, evidenceIds)
                }
                val update = if (imageError != null && imageError <= ANCHOR_IMAGE_HOLD_MAX_PX &&
                    lastDeltaMm != null && lastDeltaMm!! <= 8.0 &&
                    (anchorFilter.correctionGoal == null || (targetImageError != null && targetImageError <= ANCHOR_IMAGE_HOLD_MAX_PX))) {
                    anchorFilter.confirmImageConsistency()
                } else anchorFilter.update(candidate, evidenceIds, nowMillis, relocalizing,
                    referencePoint, consistentWithPendingImage = pendingImageError?.let { it <= ANCHOR_IMAGE_HOLD_MAX_PX })
                lastEvent = update.event
                lastReason = update.reason
                anchorSettled = update.settled
                lastSamples = update.sampleCount
                if (update.event == "ACCEPT") {
                    referenceConflictActive = false
                    lastCalibrationMillis = tagResult.capturedAtElapsedMillis
                    lastReprojectionErrorPx = if (update.reason == "anchor-image-held") imageError!! else tagPose.reprojectionErrorPx
                    relocalizing = false
                }
                lastUpdateDelta = "delta=${lastDeltaMm?.shortMm()}/${lastDeltaDeg?.shortDeg()} samples=$lastSamples state=${anchorFilter.correctionState}"
            }
            val decision = "$lastEvent reason=$lastReason detected=${tagResult.detections.map { it.id }} tags=${tagResult.poseMarkerIds} verified=$evidenceIds excluded=${tagResult.rejectedMarkerIds}"
            if ((decision != lastLoggedDecision && nowMillis - lastLogMillis >= 500L) || nowMillis - lastLogMillis >= 1000L) {
                logDecision("$decision age=${age}ms settled=$anchorSettled tracking=$arTracking " +
                    "anchorPx=$lastAnchorImageErrorPx recalibration=${anchorFilter.requiresRecalibration} $lastUpdateDelta")
                lastLoggedDecision = decision
                lastLogMillis = nowMillis
            }
        }
        val capture = lastDetection.capturedAtElapsedMillis
        val age = if (lastDetection.detections.isEmpty()) Long.MAX_VALUE else
            capture?.let { (nowMillis - it).coerceAtLeast(0L) } ?: Long.MAX_VALUE
        val fresh = age <= DETECTION_FRESH_MILLIS
        val debugFresh = age <= DEBUG_DETECTION_HOLD_MILLIS
        val conflict = referenceConflictActive
        val calibrated = anchorFilter.anchor.takeUnless { relocalizing }
        val cameraCvFromTransformer = if (arTracking && calibrated != null) {
            currentArFromCameraCv.inverseRigid() * calibrated
        } else null
        val pose = cameraCvFromTransformer?.toTransformerPose(lastReprojectionErrorPx)
        val display = if (arTracking && calibrated != null) {
            ArDisplayProjection.fromOpenGlCamera(
                displayWidth, displayHeight, projectionMatrixColumnMajor, currentCameraGlFromAr * calibrated
            )
        } else null
        // All moving debug routes use the current view. Keeping capture pixels on screen
        // for 500 ms while the fused route follows the camera creates artificial drift.
        val captureCamera = captureArFromCameraCv?.takeIf { fresh && arTracking && !relocalizing }
        val candidateProjection = captureCamera?.let { capturePose ->
            lastDetection.transformerPose?.let { rawPose ->
                ArDisplayProjection.fromOpenGlCamera(displayWidth, displayHeight, projectionMatrixColumnMajor,
                    currentCameraGlFromAr * capturePose * Transform3D.cameraCvFromTransformerPose(rawPose))
            }
        }
        val trackedDetections = if (captureCamera != null && lastDetection.cameraIntrinsics != null) {
            val projectionFromCapture = ArDisplayProjection.fromOpenGlCamera(displayWidth, displayHeight,
                projectionMatrixColumnMajor, currentCameraGlFromAr * captureCamera)
            val references = lastDetection.referenceMarkers.associateBy { it.id }
            lastDetection.detections.mapNotNull { detection ->
                val marker = references[detection.id] ?: return@mapNotNull null
                val rawPose = lastDetection.freshPosePerTag[detection.id] ?: return@mapNotNull null
                projectTagObservationToCurrentView(detection, marker, Transform3D.cameraCvFromTransformerPose(rawPose),
                    lastDetection.cameraIntrinsics!!, projectionFromCapture)
            }
        } else emptyList()
        val tagDistances = if (captureCamera != null) {
            val currentCvFromCaptureCv = currentArFromCameraCv.inverseRigid() * captureCamera
            lastDetection.referenceMarkers.mapNotNull { marker ->
                val measured = lastDetection.freshPosePerTag[marker.id] ?: return@mapNotNull null
                tagDistanceMm(currentCvFromCaptureCv * Transform3D.cameraCvFromTransformerPose(measured), marker)
                    ?.let { marker.id to it }
            }.toMap()
        } else emptyMap()
        val sinceCalibration = nowMillis - lastCalibrationMillis
        val status = when {
            relocalizing || conflict || anchorFilter.requiresRecalibration -> ArTrackingStatus.NeedsRecalibration
            pose == null -> ArTrackingStatus.NoPose
            sinceCalibration <= TAG_STATUS_HOLD_MILLIS && anchorSettled -> ArTrackingStatus.TagCalibration
            persistentTrackingFrame && arTracking -> ArTrackingStatus.ArCoreTracking
            sinceCalibration <= 10_000L -> ArTrackingStatus.ArCoreTracking
            else -> ArTrackingStatus.DriftPossible
        }
        val quality = when (status) {
            ArTrackingStatus.TagCalibration -> 95
            ArTrackingStatus.ArCoreTracking -> 75
            ArTrackingStatus.DriftPossible -> 45
            ArTrackingStatus.NeedsRecalibration -> 20
            ArTrackingStatus.NoPose -> 0
        }
        val correctionState = when {
            !arTracking && persistentTrackingFrame -> AnchorCorrectionState.TrackingPaused
            conflict || relocalizing -> AnchorCorrectionState.RecoveryRequired
            else -> anchorFilter.correctionState
        }
        // Raw image coordinates must stay paired with their capture pose. UI projection and
        // placement instead use displayProjection, which follows the current camera every frame.
        return AprilTagFrameResult(
            trackingFrameId = currentTrackingFrameId,
            imageWidth = lastDetection.imageWidth,
            imageHeight = lastDetection.imageHeight,
            detections = if (debugFresh) lastDetection.detections else emptyList(),
            screenDetections = if (debugFresh) lastDetection.screenDetections else emptyList(),
            trackedScreenDetections = trackedDetections,
            tagDistancesMm = tagDistances,
            cameraIntrinsics = lastDetection.cameraIntrinsics,
            imageToViewMapper = lastDetection.imageToViewMapper,
            imageProjectionPose = if (fresh && calibrated != null) {
                captureArFromCameraCv?.inverseRigid()?.times(calibrated)?.toTransformerPose(lastReprojectionErrorPx)
            } else null,
            detectionsFresh = fresh && lastDetection.detections.isNotEmpty(),
            knownMarkerCount = lastDetection.knownMarkerCount,
            poseMarkerIds = if (fresh) lastDetection.poseMarkerIds else emptyList(),
            verifiedReferenceIds = if (fresh) lastDetection.verifiedReferenceIds else emptyList(),
            poseMarkerCount = if (fresh) lastDetection.poseMarkerCount else 0,
            posePerTag = emptyMap(),
            freshPosePerTag = if (hasPacket && fresh) lastDetection.freshPosePerTag else emptyMap(),
            freshImageProjectionPose = if (hasPacket && fresh) lastDetection.freshImageProjectionPose else null,
            transformerPose = pose,
            displayProjection = display,
            candidateDisplayProjection = candidateProjection,
            trackingQualityPercent = quality,
            errorMessage = tagResult.errorMessage,
            trackingStatus = status,
            arTracking = arTracking,
            fusionEvent = lastEvent,
            fusionReason = lastReason,
            detectionAgeMillis = age,
            cameraCvFromTransformer = cameraCvFromTransformer,
            motionDuringDetectionMm = lastMotionMm,
            motionDuringDetectionDeg = lastMotionDeg,
            arFromTransformer = calibrated,
            rejectedMarkerIds = if (fresh) lastDetection.rejectedMarkerIds else emptyList(),
            referenceConflict = conflict,
            capturedAtElapsedMillis = capture,
            detectionSequence = lastDetection.detectionSequence,
            referenceDepthMm = lastDetection.referenceDepthMm,
            calibrationAgeMillis = sinceCalibration.coerceAtLeast(0L),
            nativeAnchorTracking = persistentTrackingFrame && arTracking,
            anchorImageErrorPx = lastAnchorImageErrorPx,
            anchorSettled = anchorSettled && !relocalizing,
            correctionState = correctionState,
            fusionDeltaMm = lastDeltaMm,
            fusionDeltaDeg = lastDeltaDeg,
            fusionSamples = lastSamples,
            fusionDiagnostic = "${correctionState.label} · $lastEvent/$lastReason · tags ${lastDetection.poseMarkerIds} " +
                "· bevestigd ${lastDetection.verifiedReferenceIds.ifEmpty { lastDetection.poseMarkerIds }} " +
                "· uitgesloten ${lastDetection.rejectedMarkerIds} · leeftijd ${if (age == Long.MAX_VALUE) "—" else "$age ms"} " +
                "· Δ ${lastDeltaMm?.shortMm() ?: "—"}/${lastDeltaDeg?.shortDeg() ?: "—"} · samples $lastSamples",
            poseDiagnostic = when {
                !arTracking -> "ARCore volgt de camera niet. Breng ook de omgeving in beeld om tracking te herstellen."
                conflict -> "Referentietags spreken elkaar tegen. Scan gecontroleerde referenties om de uitlijning te bevestigen."
                relocalizing -> "ARCore moet opnieuw uitlijnen. Scan een bekende referentietag."
                anchorFilter.requiresRecalibration -> "Referentie blijft afwijken. Scan twee gecontroleerde referentietags samen, of bevestig de fysieke tagpositie en kies AR opnieuw ijken."
                anchorFilter.anchor == null && lastDetection.poseMarkerIds.isNotEmpty() -> "Referentietag herkend; de eerste kalibratie wordt opgebouwd."
                lastReason == "reference-jump" -> "Afwijkende tagmeting genegeerd; het bestaande AR-anker blijft staan."
                !anchorSettled && pose != null -> "AR-uitlijning wordt bijgesteld; wacht tot de correctie klaar is."
                capture != null && nowMillis - capture <= 1000L -> lastDetection.poseDiagnostic
                else -> null
            }
        )
    }


    private var lastUpdateDelta = ""
    private var activeTrackingFrameId: Long? = null
}

private class ArCoreBackgroundRenderer {
    var textureId: Int = -1
        private set

    private var program = 0
    private var positionAttribute = 0
    private var texCoordAttribute = 0
    private var textureUniform = 0
    private val quadCoords = directFloatBuffer(
        floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f
        )
    )
    private val transformedTexCoords = directFloatBuffer(FloatArray(8))
    // Stap 6: herbruikbare bron-buffer voor transformCoordinates2d, zodat draw() niet elke frame
    // een nieuwe directFloatBuffer(FloatArray(8)) alloceert (RENDERMODE_CONTINUOUSLY = GC-druk).
    private val sourceTexCoords = directFloatBuffer(FloatArray(8))

    fun createOnGlThread() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        positionAttribute = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttribute = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureUniform = GLES20.glGetUniformLocation(program, "sTexture")
    }

    fun draw(frame: Frame) {
        if (textureId == -1 || program == 0) return
        sourceTexCoords.position(0)
        quadCoords.position(0)
        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            quadCoords,
            Coordinates2d.TEXTURE_NORMALIZED,
            sourceTexCoords
        )
        sourceTexCoords.position(0)
        transformedTexCoords.clear()
        transformedTexCoords.put(sourceTexCoords)
        transformedTexCoords.position(0)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureUniform, 0)

        quadCoords.position(0)
        GLES20.glVertexAttribPointer(positionAttribute, 2, GLES20.GL_FLOAT, false, 0, quadCoords)
        GLES20.glEnableVertexAttribArray(positionAttribute)

        transformedTexCoords.position(0)
        GLES20.glVertexAttribPointer(texCoordAttribute, 2, GLES20.GL_FLOAT, false, 0, transformedTexCoords)
        GLES20.glEnableVertexAttribArray(texCoordAttribute)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionAttribute)
        GLES20.glDisableVertexAttribArray(texCoordAttribute)
    }

    private fun createProgram(vertexShader: String, fragmentShader: String): Int {
        val vertex = loadShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fragment = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertex)
            GLES20.glAttachShader(it, fragment)
            GLES20.glLinkProgram(it)
        }
    }

    private fun loadShader(type: Int, source: String): Int =
        GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, source)
            GLES20.glCompileShader(it)
        }

    private companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, v_TexCoord);
            }
        """
    }
}

private data class PendingAprilTagFrame(
    val width: Int,
    val height: Int,
    val luma: ByteArray,
    val markers: List<Marker>,
    val intrinsics: CameraIntrinsics,
    val arFromCameraGlAtCapture: Transform3D,
    val imageToViewMapper: ImageToViewMapper,
    val markerSignatures: Map<Int, String>,
    val trackingFrameId: Long,
    val predictedCameraPose: TransformerPose?,
    val poseMode: TagPoseMode,
    val capturedAtElapsedMillis: Long,
    val detectionSequence: Long
)

private data class AprilTagDetectionPacket(
    val result: AprilTagFrameResult,
    val arFromCameraGlAtCapture: Transform3D,
    val markerSignatures: Map<Int, String>
)

private fun Image.copyLumaFrame(
    markers: List<Marker>,
    intrinsics: CameraIntrinsics,
    arFromCameraGlAtCapture: Transform3D,
    imageToViewMapper: ImageToViewMapper,
    markerSignatures: Map<Int, String>,
    trackingFrameId: Long,
    predictedCameraPose: TransformerPose?,
    poseMode: TagPoseMode,
    capturedAtElapsedMillis: Long,
    detectionSequence: Long
): PendingAprilTagFrame {
    val plane = planes[0]
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val buffer = plane.buffer.duplicate()
    val data = ByteArray(buffer.remaining())
    buffer.get(data)
    val luma = ByteArray(width * height)
    val row = ByteArray(width)
    for (y in 0 until height) {
        val rowStart = y * rowStride
        for (x in 0 until width) {
            row[x] = data[rowStart + x * pixelStride]
        }
        System.arraycopy(row, 0, luma, y * width, width)
    }
    return PendingAprilTagFrame(
        width = width,
        height = height,
        luma = luma,
        markers = markers,
        intrinsics = intrinsics,
        arFromCameraGlAtCapture = arFromCameraGlAtCapture,
        imageToViewMapper = imageToViewMapper,
        markerSignatures = markerSignatures,
        trackingFrameId = trackingFrameId,
        predictedCameraPose = predictedCameraPose,
        poseMode = poseMode,
        capturedAtElapsedMillis = capturedAtElapsedMillis,
        detectionSequence = detectionSequence
    )
}

private fun Frame.captureImageToViewMapper(
    imageWidth: Int,
    imageHeight: Int,
    displayWidth: Int,
    displayHeight: Int
): ImageToViewMapper {
    val input = floatArrayOf(
        0f, 0f,
        imageWidth.toFloat(), 0f,
        0f, imageHeight.toFloat()
    )
    val output = FloatArray(input.size)
    return runCatching {
        val inputBuffer = directFloatBuffer(input)
        val outputBuffer = directFloatBuffer(output)
        transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            inputBuffer,
            Coordinates2d.VIEW,
            outputBuffer
        )
        outputBuffer.position(0)
        outputBuffer.get(output)
        ImageToViewMapper(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            topLeft = AprilTagCorner(output[0], output[1]),
            topRight = AprilTagCorner(output[2], output[3]),
            bottomLeft = AprilTagCorner(output[4], output[5])
        )
    }.getOrElse {
        ImageToViewMapper(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            displayWidth = displayWidth,
            displayHeight = displayHeight,
            topLeft = AprilTagCorner(0f, 0f),
            topRight = AprilTagCorner(displayWidth.toFloat(), 0f),
            bottomLeft = AprilTagCorner(0f, displayHeight.toFloat())
        )
    }
}

private fun AprilTagFrameResult.withScreenDetections(mapper: ImageToViewMapper): AprilTagFrameResult =
    copy(
        screenDetections = mapDetectionsToView(detections, mapper),
        imageToViewMapper = mapper
    )

private fun directFloatBuffer(values: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(values.size * java.lang.Float.BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(values)
            position(0)
        }

private fun detectionIntervalMillisFor(status: ArTrackingStatus, detectionAgeMillis: Long): Long {
    // Versheid-bewust: zodra de tag NIET (meer) vers in beeld is — re-acquire, drift, of zoekend
    // heen-en-weer — maximaal responsief detecteren, zodat her-acquisitie én her-ankering (3
    // opeenvolgende verse detecties) net zo snel blijven als vóór de cadans-wijziging. De rustige
    // cadans geldt alleen bij een bevestigde, VERSE, gelockte tag.
    if (detectionAgeMillis > DETECTION_FRESH_MILLIS) return DETECTION_INTERVAL_FAST_MILLIS
    return when (status) {
        // Echt gelockt en vers correcterend (recent geaccepteerde correctie) → rustig; ARCore draagt
        // de pose tussen detecties. posePerTag blijft binnen zijn 250ms-versheidsvenster ververst.
        ArTrackingStatus.TagCalibration -> DETECTION_INTERVAL_STABLE_MILLIS
        // Drift-risico: iets sneller dan stabiel om vlot te herijken.
        ArTrackingStatus.DriftPossible -> DETECTION_INTERVAL_DRIFT_MILLIS
        // Setup, herijking en re-ankering-bezig (ArCoreTracking = nog geen geaccepteerde correctie): snel.
        ArTrackingStatus.NoPose,
        ArTrackingStatus.NeedsRecalibration,
        ArTrackingStatus.ArCoreTracking -> DETECTION_INTERVAL_FAST_MILLIS
    }
}

// Stap 4: tijd-gebaseerde detectie-cadans (ms) per trackingstatus. Snel tijdens setup/scan en
// herijking (tag-detectie mag NIET trager voelen); rustiger zodra ARCore stabiel leidt en de
// overlays toch elk frame via displayProjection meelopen. Waarden binnen de audit-richtlijnen
// (setup ~33-67ms; stabiel ~100-200ms; drift ertussenin).
private const val DETECTION_INTERVAL_FAST_MILLIS = 50L
private const val DETECTION_INTERVAL_DRIFT_MILLIS = 100L
private const val DETECTION_INTERVAL_STABLE_MILLIS = 150L
// Versheidsdrempel: boven deze leeftijd is de tag niet "vers in beeld" → terug naar FAST-cadans.
// Ruim boven STABLE (150ms) zodat steady tracking niet wisselt; ≈ PER_TAG_POSE_FRESH_MILLIS (250ms).
private const val DETECTION_FRESH_MILLIS = 250L
private const val METERS_TO_MILLIMETERS = 1000.0
private const val ENABLE_ARCORE_DEPTH_CURSOR = false
private const val MAX_ARCORE_FRAME_JUMP_MM = 900.0
private const val MAX_ARCORE_FRAME_JUMP_ANGLE_DEG = 28.0
private const val TAG_STATUS_HOLD_MILLIS = 250L
private const val DEBUG_DETECTION_HOLD_MILLIS = 500L
private const val PUBLISH_LOG_INTERVAL_MILLIS = 2_000L
private const val PERF_WINDOW_MILLIS = 500L

private fun Float.shortPx(): String =
    "${((this * 100f).roundToInt() / 100f)}px"

private fun Double.shortMm(): String =
    "${roundToInt()}mm"

private fun Double.shortDeg(): String =
    "${((this * 10.0).roundToInt() / 10.0)}deg"

private fun Double.shortAlpha(): String =
    "${((this * 100.0).roundToInt() / 100.0)}"
