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
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.roundToInt

@Composable
fun ArCoreCameraPanel(
    knownMarkers: List<Marker>,
    onResult: (AprilTagFrameResult) -> Unit,
    modifier: Modifier = Modifier,
    tagDictionary: TagDictionaryOption = TagDictionaryOption.DEFAULT,
    tagPoseMode: TagPoseMode = TagPoseMode.DEFAULT
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
    val renderer = remember {
        ArCoreCameraRenderer(
            context = context,
            knownMarkers = { latestMarkers },
            onResult = { latestOnResult(it) },
            tagDictionaryId = { latestDictionary.openCvId },
            tagPoseMode = { latestPoseMode }
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
    private val tagPoseMode: () -> TagPoseMode = { TagPoseMode.DEFAULT }
) : GLSurfaceView.Renderer {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeDictionaryId = tagDictionaryId()
    private var detector = AprilTagDetector(activeDictionaryId)
    private val detectorExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val detectionLock = Any()
    private val backgroundRenderer = ArCoreBackgroundRenderer()
    private val fusion = ArCoreAprilTagFusion()
    private var session: Session? = null
    private var installRequested = false
    private var surfaceWidth = 1
    private var surfaceHeight = 1
    @Volatile
    private var detectionInFlight = false
    // Stap 4: tijd-gebaseerde detectie-scheduling i.p.v. frame-tellen. De cadans hangt af van de
    // laatst bekende trackingstatus (gecachet uit het vorige fuse-result).
    private var lastDetectionStartElapsedMillis = 0L
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
        val camera = frame.camera
        val tracking = camera.trackingState == TrackingState.TRACKING
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
        resetFusionIfMarkersChanged(markers)
        submitTagDetectionIfNeeded(frame, arFromCameraGl, markers)
        val tagPacket = pollLatestDetectionPacket(knownMarkerSignatures)
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
            currentArFromCameraGl = arFromCameraGl,
            currentCameraGlFromAr = cameraGlFromAr,
            depthArFromPoint = depthArFromPoint,
            projectionMatrixColumnMajor = projectionMatrix,
            arTracking = tracking,
            displayWidth = surfaceWidth,
            displayHeight = surfaceHeight
        )
        // Stap 4: status + detectie-versheid onthouden zodat submitTagDetectionIfNeeded de volgende
        // cadans kan kiezen (snel zodra de tag niet vers in beeld is → her-acquisitie blijft snel).
        lastTrackingStatus = result.trackingStatus
        lastDetectionAgeMillis = result.detectionAgeMillis
        publishResultToMain(result)
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
        markers: List<Marker>
    ) {
        // Stap 4: backpressure (detectionInFlight) + tijd-gebaseerde cadans op elapsedRealtime().
        if (detectionInFlight) return
        val nowElapsed = SystemClock.elapsedRealtime()
        if (nowElapsed - lastDetectionStartElapsedMillis < detectionIntervalMillisFor(lastTrackingStatus, lastDetectionAgeMillis)) return
        // Dictionary-wissel (instellingen): nieuwe detector + schone fusion. Veilig hier:
        // er loopt geen detectie (detectionInFlight is false) en de volgende run gebruikt
        // de nieuwe instantie via de executor-submissie.
        val requestedDictionaryId = tagDictionaryId()
        if (requestedDictionaryId != activeDictionaryId) {
            activeDictionaryId = requestedDictionaryId
            detector = AprilTagDetector(requestedDictionaryId)
            fusion.reset()
            synchronized(detectionLock) {
                latestDetectionPacket = null
                consumedDetectionSequence = latestDetectionSequence
            }
        }
        val markerSignatures = knownMarkerSignatures
        val image = try {
            frame.acquireCameraImage()
        } catch (_: NotYetAvailableException) {
            return
        } catch (error: Throwable) {
            publishDetectionPacket(
                AprilTagDetectionPacket(
                    result = AprilTagFrameResult(errorMessage = error.message ?: "ARCore camera image kon niet gelezen worden"),
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
                markerSignatures = markerSignatures
            )
        } finally {
            image.close()
        }
        detectionInFlight = true
        lastDetectionStartElapsedMillis = nowElapsed
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
                    result = AprilTagFrameResult(errorMessage = "AprilTag analyse kon niet starten: ${it.message}"),
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
                poseMode = tagPoseMode()
            ).withScreenDetections(frame.imageToViewMapper)
        } catch (error: Throwable) {
            AprilTagFrameResult(
                imageWidth = frame.width,
                imageHeight = frame.height,
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
        mainHandler.post {
            onResult(AprilTagFrameResult(errorMessage = message, trackingStatus = ArTrackingStatus.NoPose))
        }
    }

    private fun resetFusionIfMarkersChanged(markers: List<Marker>) {
        val nextSignatures = markers.associate { marker -> marker.id to markerSignature(marker) }
        val previousSignatures = knownMarkerSignatures
        if (previousSignatures != nextSignatures) {
            fusion.reset()
            synchronized(detectionLock) {
                latestDetectionPacket = null
                consumedDetectionSequence = latestDetectionSequence
            }
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
            marker.poseWeight
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

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private class ArCoreAprilTagFusion {
    private var arFromTransformer: Transform3D? = null
    private var lastTagCalibrationMillis: Long = 0
    private var lastDetectionsMillis: Long = 0
    private var lastDetections: List<AprilTagDetection> = emptyList()
    private var lastScreenDetections: List<AprilTagDetection> = emptyList()
    /** Laatst bekende individuele pose per tag, plus de bijbehorende beeld→scherm mapper.
     *  Hiermee tekenen we sensoren/tags ELK frame via de EIGEN referentietag — ook tussen
     *  (asynchrone) detecties door. Puur detectie-gedreven, geen ARCore-wereldfusie → geen drift
     *  en geen verschuiving wanneer een andere tag in beeld komt. */
    private var lastPosePerTag: Map<Int, TransformerPose> = emptyMap()
    private var lastPoseMarkerIds: List<Int> = emptyList()
    private var lastImageToViewMapper: ImageToViewMapper? = null
    private var lastCameraIntrinsics: CameraIntrinsics? = null
    private var lastReprojectionErrorPx: Float = 0f
    private var previousArFromCameraGl: Transform3D? = null
    private var wasArTracking: Boolean = false
    private var needsFreshCalibration: Boolean = false
    private var lastSingleCorrectionMarkerId: Int? = null
    private var singleCorrectionStreak: Int = 0
    private var pendingSingleReanchor: PendingSingleReanchor? = null
    private var lastFusionLogKey: String = ""
    private var lastFusionLogMillis: Long = 0L
    // Stap 2: throttle-state voor de ARSensOverlayRoute debug-log.
    private var lastOverlayRouteLogKey: String = ""
    private var lastOverlayRouteLogMillis: Long = 0L
    private val fallbackCameraGlFromCameraCv = Transform3D.cameraGlFromCameraCv()

    private data class PendingSingleReanchor(
        val markerIds: List<Int>,
        val candidate: Transform3D,
        val stableFrames: Int
    )

    private data class SingleReanchorUpdate(
        val pendingFrames: Int? = null,
        val candidateDeltaMm: Double? = null,
        val candidateDeltaAngleDeg: Double? = null,
        val confirmed: Boolean = false,
        val rejected: Boolean = false
    )

    fun reset() {
        arFromTransformer = null
        lastTagCalibrationMillis = 0
        lastDetectionsMillis = 0
        lastDetections = emptyList()
        lastScreenDetections = emptyList()
        lastPosePerTag = emptyMap()
        lastPoseMarkerIds = emptyList()
        lastImageToViewMapper = null
        lastCameraIntrinsics = null
        lastReprojectionErrorPx = 0f
        previousArFromCameraGl = null
        wasArTracking = false
        needsFreshCalibration = false
        lastSingleCorrectionMarkerId = null
        singleCorrectionStreak = 0
        pendingSingleReanchor = null
        lastFusionLogKey = ""
        lastFusionLogMillis = 0L
        lastOverlayRouteLogKey = ""
        lastOverlayRouteLogMillis = 0L
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
        displayHeight: Int
    ): AprilTagFrameResult {
        val now = SystemClock.elapsedRealtime()
        var fusionEvent: String? = null
        var fusionReason: String? = null
        fun recordFusionDecision(event: String, reason: String): String {
            fusionEvent = event
            fusionReason = reason
            return reason
        }
        updateArTrackingContinuity(arTracking, currentArFromCameraGl)
        val currentCameraGlFromCameraCv = cameraGlFromCameraCvFor(tagResult.imageToViewMapper ?: lastImageToViewMapper)
        val tagCameraGlFromCameraCv = cameraGlFromCameraCvFor(tagResult.imageToViewMapper ?: lastImageToViewMapper)
        val currentArFromCameraCv = currentArFromCameraGl * currentCameraGlFromCameraCv
        val tagArFromCameraCv = (tagArFromCameraGl ?: currentArFromCameraGl) * tagCameraGlFromCameraCv
        val tagPose = tagResult.transformerPose
        var usedTagPose: TransformerPose? = null
        var candidateDisplayProjection: ArDisplayProjection? = null
        tagResult.imageToViewMapper?.let { lastImageToViewMapper = it }
        tagResult.cameraIntrinsics?.let { lastCameraIntrinsics = it }
        if (tagResult.detections.isNotEmpty()) {
            lastDetections = tagResult.detections
            lastScreenDetections = tagResult.screenDetections
            lastDetectionsMillis = now
            // Per-tag poses mogen alleen kort na de detectie als verse tag-fallback bestaan.
            // Een nieuwe detectie zonder bruikbare pose wist daarom de vorige pose expliciet.
            lastPosePerTag = tagResult.posePerTag
            lastPoseMarkerIds = tagResult.poseMarkerIds
        }
        if (tagPose != null) {
            val poseMarkerIds = tagResult.poseMarkerIds.distinct()
            val hasMultipleVisibleKnownTags = poseMarkerIds.size >= 2
            val maxReprojectionErrorPx = if (hasMultipleVisibleKnownTags) {
                MAX_MULTI_TAG_REPROJECTION_ERROR_PX
            } else {
                MAX_REPROJECTION_ERROR_PX
            }
            if (tagPose.reprojectionErrorPx <= maxReprojectionErrorPx) {
                val candidate = tagArFromCameraCv * Transform3D.cameraCvFromTransformerPose(tagPose)
                candidateDisplayProjection = if (arTracking) {
                    ArDisplayProjection.fromOpenGlCamera(
                        displayWidthPx = displayWidth,
                        displayHeightPx = displayHeight,
                        projectionMatrixColumnMajor = projectionMatrixColumnMajor,
                        cameraGlFromTransformer = currentCameraGlFromAr * candidate
                    )
                } else {
                    null
                }
                val current = arFromTransformer
                val correctionMm = current?.distanceTo(candidate) ?: 0.0
                val correctionAngleDeg = current?.rotationAngleDegreesTo(candidate) ?: 0.0
                val calibrationIsStale = now - lastTagCalibrationMillis > TAG_RESET_AFTER_MILLIS
                val hasMultipleSavedKnownTags = tagResult.knownMarkerCount >= 2
                val correctionWithinMultiTagLimits =
                    correctionMm <= MAX_TAG_CORRECTION_JUMP_MM &&
                        correctionAngleDeg <= MAX_TAG_CORRECTION_ANGLE_DEG
                val correctionWithinSingleTagLimits =
                    correctionMm <= MAX_SINGLE_TAG_CORRECTION_JUMP_MM &&
                        correctionAngleDeg <= MAX_SINGLE_TAG_CORRECTION_ANGLE_DEG
                val correctionWithinMultiReferenceSingleTagLimits =
                    correctionMm <= MAX_MULTI_REFERENCE_SINGLE_TAG_CORRECTION_JUMP_MM &&
                        correctionAngleDeg <= MAX_MULTI_REFERENCE_SINGLE_TAG_CORRECTION_ANGLE_DEG
                val isSingleVisibleTagInMultiReferenceProject =
                    hasMultipleSavedKnownTags && !hasMultipleVisibleKnownTags
                val singleVisibleMarkerId = poseMarkerIds.singleOrNull()
                val isSingleVisibleTag = poseMarkerIds.size == 1
                val singleTagStable = if (isSingleVisibleTag && singleVisibleMarkerId != null) {
                    if (singleVisibleMarkerId == lastSingleCorrectionMarkerId) {
                        singleCorrectionStreak += 1
                    } else {
                        lastSingleCorrectionMarkerId = singleVisibleMarkerId
                        singleCorrectionStreak = 1
                    }
                    singleCorrectionStreak >= MIN_STABLE_SINGLE_TAG_CORRECTION_FRAMES
                } else {
                    lastSingleCorrectionMarkerId = null
                    singleCorrectionStreak = 0
                    true
                }
                val correctionWithinVisibleSingleTagLimits =
                    if (isSingleVisibleTagInMultiReferenceProject) {
                        correctionWithinMultiReferenceSingleTagLimits
                    } else {
                        correctionWithinSingleTagLimits
                    }
                val singleTagExceedsNormalLimits =
                    isSingleVisibleTag && !correctionWithinVisibleSingleTagLimits
                val singleTagNeedsReplacement =
                    isSingleVisibleTag &&
                        (current == null || needsFreshCalibration || calibrationIsStale)
                val eligibleForSingleReanchor =
                    isSingleVisibleTag &&
                        singleVisibleMarkerId != null &&
                        tagPose.reprojectionErrorPx <= SINGLE_TAG_REANCHOR_REPROJECTION_PX &&
                        (singleTagExceedsNormalLimits || singleTagNeedsReplacement)
                val pendingReanchorUpdate = if (eligibleForSingleReanchor) {
                    updatePendingSingleReanchor(
                        markerIds = poseMarkerIds,
                        candidate = candidate,
                        reprojectionErrorPx = tagPose.reprojectionErrorPx
                    )
                } else {
                    resetPendingSingleReanchor()
                    SingleReanchorUpdate()
                }
                val singleTagReplacementReady =
                    isSingleVisibleTag &&
                        singleTagNeedsReplacement &&
                        pendingReanchorUpdate.confirmed
                val canAcceptCorrection = when {
                    pendingReanchorUpdate.confirmed -> true
                    current == null -> if (isSingleVisibleTag) singleTagReplacementReady else true
                    needsFreshCalibration -> if (isSingleVisibleTag) singleTagReplacementReady else true
                    calibrationIsStale -> if (isSingleVisibleTag) singleTagReplacementReady else true
                    hasMultipleVisibleKnownTags -> correctionWithinMultiTagLimits
                    isSingleVisibleTagInMultiReferenceProject -> singleTagStable && correctionWithinMultiReferenceSingleTagLimits
                    isSingleVisibleTag -> singleTagStable && correctionWithinSingleTagLimits
                    else -> correctionWithinSingleTagLimits
                }

                if (canAcceptCorrection) {
                    val shouldReplaceTransform =
                        current == null ||
                            calibrationIsStale ||
                            needsFreshCalibration ||
                            pendingReanchorUpdate.confirmed
                    var appliedAlpha = 1.0
                    arFromTransformer = when {
                        shouldReplaceTransform -> {
                            candidate
                        }
                        else -> {
                            val alpha = when {
                                hasMultipleVisibleKnownTags -> multiTagCorrectionAlpha(tagPose.reprojectionErrorPx)
                                poseMarkerIds.size == 1 -> SINGLE_TAG_LOCAL_CORRECTION_ALPHA
                                else -> correctionAlpha(correctionMm, tagPose.reprojectionErrorPx)
                            }
                            appliedAlpha = alpha
                            current?.blendRigidToward(
                                target = candidate,
                                alpha = alpha
                            ) ?: candidate
                        }
                    }
                    logFusionDecision(
                        now = now,
                        event = "ACCEPT",
                        markerIds = poseMarkerIds,
                        knownMarkerCount = tagResult.knownMarkerCount,
                        reprojectionErrorPx = tagPose.reprojectionErrorPx,
                        maxReprojectionErrorPx = maxReprojectionErrorPx,
                        correctionMm = correctionMm,
                        correctionAngleDeg = correctionAngleDeg,
                        alpha = appliedAlpha,
                        depthHit = depthArFromPoint != null,
                        pendingFrames = pendingReanchorUpdate.pendingFrames,
                        candidateDeltaMm = pendingReanchorUpdate.candidateDeltaMm,
                        candidateDeltaAngleDeg = pendingReanchorUpdate.candidateDeltaAngleDeg,
                        eligibleForReanchor = eligibleForSingleReanchor,
                        reason = recordFusionDecision(
                            "ACCEPT",
                            when {
                                pendingReanchorUpdate.confirmed -> "confirmed-single-reanchor"
                                shouldReplaceTransform && needsFreshCalibration -> "fresh-needed"
                                shouldReplaceTransform && calibrationIsStale -> "stale-recalibration"
                                shouldReplaceTransform -> "fresh"
                                hasMultipleVisibleKnownTags -> "multi"
                                else -> "single"
                            }
                        )
                    )
                    lastTagCalibrationMillis = now
                    lastReprojectionErrorPx = tagPose.reprojectionErrorPx
                    needsFreshCalibration = false
                    if (pendingReanchorUpdate.confirmed) {
                        resetPendingSingleReanchor()
                    }
                    usedTagPose = tagPose
                } else if (eligibleForSingleReanchor && !pendingReanchorUpdate.rejected) {
                    logFusionDecision(
                        now = now,
                        event = "PENDING",
                        markerIds = poseMarkerIds,
                        knownMarkerCount = tagResult.knownMarkerCount,
                        reprojectionErrorPx = tagPose.reprojectionErrorPx,
                        maxReprojectionErrorPx = maxReprojectionErrorPx,
                        correctionMm = correctionMm,
                        correctionAngleDeg = correctionAngleDeg,
                        alpha = null,
                        depthHit = depthArFromPoint != null,
                        pendingFrames = pendingReanchorUpdate.pendingFrames,
                        candidateDeltaMm = pendingReanchorUpdate.candidateDeltaMm,
                        candidateDeltaAngleDeg = pendingReanchorUpdate.candidateDeltaAngleDeg,
                        eligibleForReanchor = eligibleForSingleReanchor,
                        reason = recordFusionDecision("PENDING", "single-reanchor-pending")
                    )
                } else {
                    logFusionDecision(
                        now = now,
                        event = "REJECT",
                        markerIds = poseMarkerIds,
                        knownMarkerCount = tagResult.knownMarkerCount,
                        reprojectionErrorPx = tagPose.reprojectionErrorPx,
                        maxReprojectionErrorPx = maxReprojectionErrorPx,
                        correctionMm = correctionMm,
                        correctionAngleDeg = correctionAngleDeg,
                        alpha = null,
                        depthHit = depthArFromPoint != null,
                        pendingFrames = pendingReanchorUpdate.pendingFrames,
                        candidateDeltaMm = pendingReanchorUpdate.candidateDeltaMm,
                        candidateDeltaAngleDeg = pendingReanchorUpdate.candidateDeltaAngleDeg,
                        eligibleForReanchor = eligibleForSingleReanchor,
                        reason = recordFusionDecision(
                            "REJECT",
                            when {
                                pendingReanchorUpdate.rejected -> "single-reanchor-rejected"
                                isSingleVisibleTag && !singleTagStable -> "single-not-stable"
                                !correctionWithinMultiReferenceSingleTagLimits && isSingleVisibleTagInMultiReferenceProject -> "single-jump"
                                !correctionWithinSingleTagLimits && isSingleVisibleTag -> "single-jump"
                                !correctionWithinMultiTagLimits && hasMultipleVisibleKnownTags -> "multi-jump"
                                else -> "jump"
                            }
                        )
                    )
                }
            } else {
                logFusionDecision(
                    now = now,
                    event = "REJECT",
                    markerIds = poseMarkerIds,
                    knownMarkerCount = tagResult.knownMarkerCount,
                    reprojectionErrorPx = tagPose.reprojectionErrorPx,
                    maxReprojectionErrorPx = maxReprojectionErrorPx,
                    correctionMm = null,
                    correctionAngleDeg = null,
                    alpha = null,
                    depthHit = depthArFromPoint != null,
                    reason = recordFusionDecision("REJECT", "reprojection")
                )
            }
        }

        val calibratedTransform = arFromTransformer.takeUnless { needsFreshCalibration }
        // Stap 5: bewaar de directe cameraCv→transformer matrix in ARCore-leading mode (dezelfde
        // matrix waaruit trackingPose wordt afgeleid) zodat de STL-overlay de per-draw Rodrigues
        // round-trip kan overslaan zonder de plaatsing te veranderen.
        var cameraCvFromTransformerDirect: Transform3D? = null
        val trackingPose = if (arTracking && calibratedTransform != null) {
            val cameraCvFromAr = currentArFromCameraCv.inverseRigid()
            val cameraCvFromTransformer = cameraCvFromAr * calibratedTransform
            cameraCvFromTransformerDirect = cameraCvFromTransformer
            cameraCvFromTransformer.toTransformerPose(lastReprojectionErrorPx)
        } else {
            usedTagPose
        }
        val cameraGlFromTransformer = when {
            arTracking && calibratedTransform != null -> currentCameraGlFromAr * calibratedTransform
            else -> null
        }
        val displayProjection = cameraGlFromTransformer?.let {
            ArDisplayProjection.fromOpenGlCamera(
                displayWidthPx = displayWidth,
                displayHeightPx = displayHeight,
                projectionMatrixColumnMajor = projectionMatrixColumnMajor,
                cameraGlFromTransformer = it
            )
        }
        val depthHitPosition = if (calibratedTransform != null && depthArFromPoint != null) {
            val transformerFromAr = calibratedTransform.inverseRigid()
            val point = transformerFromAr.transformPoint(depthArFromPoint.translation())
            MmPosition(
                x = point[0].roundToInt(),
                y = point[1].roundToInt(),
                z = point[2].roundToInt()
            )
        } else {
            null
        }

        val millisSinceTag = now - lastTagCalibrationMillis
        val detectionAgeMillis = if (lastDetectionsMillis > 0) now - lastDetectionsMillis else Long.MAX_VALUE
        val isLiveDetectionFresh = detectionAgeMillis <= LIVE_DETECTION_HOLD_MILLIS
        val isPerTagPoseFresh = detectionAgeMillis <= PER_TAG_POSE_FRESH_MILLIS
        val isDebugHold = detectionAgeMillis <= DEBUG_DETECTION_HOLD_MILLIS
        val visibleDetections = if (isDebugHold) lastDetections else emptyList()
        val visibleScreenDetections = if (isDebugHold) lastScreenDetections else emptyList()
        val effectivePosePerTag = if (isPerTagPoseFresh) lastPosePerTag else emptyMap()
        val effectivePoseMarkerIds = if (isPerTagPoseFresh) lastPoseMarkerIds else emptyList()
        val effectiveImageToViewMapper = tagResult.imageToViewMapper ?: lastImageToViewMapper
        val effectiveCameraIntrinsics = tagResult.cameraIntrinsics ?: lastCameraIntrinsics
        val detectionsFresh = isLiveDetectionFresh && lastDetections.isNotEmpty()
        val status = when {
            needsFreshCalibration && arFromTransformer != null -> ArTrackingStatus.NeedsRecalibration
            arFromTransformer == null && visibleDetections.isNotEmpty() -> ArTrackingStatus.NeedsRecalibration
            trackingPose != null && millisSinceTag <= TAG_STATUS_HOLD_MILLIS -> ArTrackingStatus.TagCalibration
            trackingPose != null && arTracking && millisSinceTag <= 10_000 -> ArTrackingStatus.ArCoreTracking
            trackingPose != null && arTracking -> ArTrackingStatus.DriftPossible
            else -> ArTrackingStatus.NoPose
        }
        val quality = when (status) {
            ArTrackingStatus.TagCalibration -> tagResult.trackingQualityPercent.coerceAtLeast(85)
            ArTrackingStatus.ArCoreTracking -> 75
            ArTrackingStatus.DriftPossible -> 45
            ArTrackingStatus.NeedsRecalibration -> 20
            ArTrackingStatus.NoPose -> 0
        }
        val overlayRoute = when {
            displayProjection != null -> "displayProjection"
            trackingPose != null && effectiveImageToViewMapper != null -> "transformerPoseFallback"
            isPerTagPoseFresh && effectivePosePerTag.isNotEmpty() && effectiveImageToViewMapper != null -> "freshTagPoseFallback"
            else -> "none"
        }
        // Stap 2: debug-only (Log.isLoggable DEBUG, standaard uit -> geen logcat-spam) en
        // gethrottled op route/status/fusion-wijziging of >=500ms. detectionAge zit niet in de
        // throttle-key (verandert elke frame) maar blijft wel in de message staan.
        if (Log.isLoggable("ARSensOverlayRoute", Log.DEBUG)) {
            val overlayRouteLogKey = "$overlayRoute|${status.name}|${fusionEvent ?: "-"}|" +
                "${fusionReason ?: "-"}|${displayProjection != null}|${trackingPose != null}"
            if (overlayRouteLogKey != lastOverlayRouteLogKey ||
                now - lastOverlayRouteLogMillis >= OVERLAY_ROUTE_LOG_INTERVAL_MILLIS
            ) {
                lastOverlayRouteLogKey = overlayRouteLogKey
                lastOverlayRouteLogMillis = now
                Log.d(
                    "ARSensOverlayRoute",
                    "route=$overlayRoute detectionAge=$detectionAgeMillis tracking=${status.name} " +
                        "displayProjection=${displayProjection != null} transformerPose=${trackingPose != null} " +
                        "posePerTag=${effectivePosePerTag.size} detectionsFresh=$detectionsFresh " +
                        "screenDetections=${visibleScreenDetections.size} imageProjectionPose=${usedTagPose != null} " +
                        "trackingStatus=${status.name} arTracking=$arTracking detectionAgeMillis=$detectionAgeMillis"
                )
            }
        }
        return tagResult.copy(
            imageWidth = tagResult.imageWidth.takeIf { it > 0 } ?: displayWidth,
            imageHeight = tagResult.imageHeight.takeIf { it > 0 } ?: displayHeight,
            detections = visibleDetections,
            screenDetections = visibleScreenDetections,
            cameraIntrinsics = effectiveCameraIntrinsics,
            imageToViewMapper = effectiveImageToViewMapper,
            posePerTag = effectivePosePerTag,
            poseMarkerIds = effectivePoseMarkerIds,
            detectionsFresh = detectionsFresh,
            transformerPose = trackingPose,
            displayProjection = displayProjection,
            candidateDisplayProjection = candidateDisplayProjection,
            imageProjectionPose = usedTagPose,
            arTracking = arTracking,
            freshImageProjectionPose = if (isPerTagPoseFresh) {
                tagResult.freshImageProjectionPose ?: tagResult.imageProjectionPose
            } else {
                null
            },
            freshPosePerTag = if (isPerTagPoseFresh) {
                tagResult.freshPosePerTag.takeIf { it.isNotEmpty() } ?: tagResult.posePerTag
            } else {
                emptyMap()
            },
            depthHitPositionMm = depthHitPosition,
            trackingStatus = status,
            trackingQualityPercent = quality,
            fusionEvent = fusionEvent,
            fusionReason = fusionReason,
            detectionAgeMillis = detectionAgeMillis,
            cameraCvFromTransformer = cameraCvFromTransformerDirect
        )
    }

    private fun correctionAlpha(correctionMm: Double, reprojectionErrorPx: Float): Double {
        val correctionWeight = when {
            correctionMm >= 1_000.0 -> 0.45
            correctionMm >= 300.0 -> 0.32
            else -> 0.22
        }
        val qualityWeight = when {
            reprojectionErrorPx <= 3f -> 1.0
            reprojectionErrorPx <= 6f -> 0.75
            else -> 0.50
        }
        return correctionWeight * qualityWeight
    }

    private fun multiTagCorrectionAlpha(reprojectionErrorPx: Float): Double =
        when {
            reprojectionErrorPx <= 4f -> MULTI_TAG_CORRECTION_ALPHA
            reprojectionErrorPx <= 10f -> 0.65
            else -> 0.50
        }

    @Suppress("UNUSED_PARAMETER")
    private fun cameraGlFromCameraCvFor(mapper: ImageToViewMapper?): Transform3D =
        // ImageToViewMapper maps image pixels to Compose/view pixels. ARCore's projection matrix
        // already accounts for display geometry, so camera axes must stay the fixed CV->GL basis.
        fallbackCameraGlFromCameraCv

    private fun updatePendingSingleReanchor(
        markerIds: List<Int>,
        candidate: Transform3D,
        reprojectionErrorPx: Float
    ): SingleReanchorUpdate {
        val previous = pendingSingleReanchor
        if (previous == null || previous.markerIds != markerIds) {
            pendingSingleReanchor = PendingSingleReanchor(
                markerIds = markerIds,
                candidate = candidate,
                stableFrames = 1
            )
            return SingleReanchorUpdate(pendingFrames = 1)
        }

        val candidateDeltaMm = previous.candidate.distanceTo(candidate)
        val candidateDeltaAngleDeg = previous.candidate.rotationAngleDegreesTo(candidate)
        val stable =
            reprojectionErrorPx <= SINGLE_TAG_REANCHOR_REPROJECTION_PX &&
                candidateDeltaMm <= PENDING_SINGLE_REANCHOR_STABLE_TRANSLATION_MM &&
                candidateDeltaAngleDeg <= PENDING_SINGLE_REANCHOR_STABLE_ANGLE_DEG
        if (!stable) {
            pendingSingleReanchor = null
            return SingleReanchorUpdate(
                pendingFrames = 0,
                candidateDeltaMm = candidateDeltaMm,
                candidateDeltaAngleDeg = candidateDeltaAngleDeg,
                rejected = true
            )
        }

        val stableFrames = previous.stableFrames + 1
        pendingSingleReanchor = PendingSingleReanchor(
            markerIds = markerIds,
            candidate = candidate,
            stableFrames = stableFrames
        )
        return SingleReanchorUpdate(
            pendingFrames = stableFrames,
            candidateDeltaMm = candidateDeltaMm,
            candidateDeltaAngleDeg = candidateDeltaAngleDeg,
            confirmed = stableFrames >= PENDING_SINGLE_REANCHOR_STABLE_FRAMES
        )
    }

    private fun resetPendingSingleReanchor() {
        pendingSingleReanchor = null
    }

    private fun logFusionDecision(
        now: Long,
        event: String,
        markerIds: List<Int>,
        knownMarkerCount: Int,
        reprojectionErrorPx: Float,
        maxReprojectionErrorPx: Float,
        correctionMm: Double?,
        correctionAngleDeg: Double?,
        alpha: Double?,
        depthHit: Boolean,
        pendingFrames: Int? = null,
        candidateDeltaMm: Double? = null,
        candidateDeltaAngleDeg: Double? = null,
        eligibleForReanchor: Boolean? = null,
        reason: String
    ) {
        val markerKey = markerIds.joinToString(",").ifBlank { "-" }
        val key = "$event|$markerKey|$knownMarkerCount|$reason|$depthHit|" +
            "${pendingFrames ?: "-"}|${candidateDeltaMm?.roundToInt() ?: "-"}|" +
            "${candidateDeltaAngleDeg?.roundToInt() ?: "-"}|${eligibleForReanchor ?: "-"}"
        if (key == lastFusionLogKey && now - lastFusionLogMillis < FUSION_LOG_INTERVAL_MILLIS) return
        lastFusionLogKey = key
        lastFusionLogMillis = now

        val message = buildString {
            append(event)
            append(" tags=[")
            append(markerKey)
            append("] known=")
            append(knownMarkerCount)
            append(" err=")
            append(reprojectionErrorPx.shortPx())
            append("/")
            append(maxReprojectionErrorPx.shortPx())
            correctionMm?.let {
                append(" jump=")
                append(it.shortMm())
            }
            correctionAngleDeg?.let {
                append(" angle=")
                append(it.shortDeg())
            }
            alpha?.let {
                append(" alpha=")
                append(it.shortAlpha())
            }
            pendingFrames?.let {
                append(" pendingFrames=")
                append(it)
            }
            candidateDeltaMm?.let {
                append(" candidateDeltaMm=")
                append(it.shortMm())
            }
            candidateDeltaAngleDeg?.let {
                append(" candidateDeltaAngleDeg=")
                append(it.shortDeg())
            }
            eligibleForReanchor?.let {
                append(" eligibleForReanchor=")
                append(it)
            }
            append(" depth=")
            append(if (depthHit) "hit" else "none")
            append(" reason=")
            append(reason)
        }
        if (event == "REJECT") {
            Log.w("ARsensFusion", message)
        } else {
            Log.i("ARsensFusion", message)
        }
    }

    private fun updateArTrackingContinuity(arTracking: Boolean, currentArFromCameraGl: Transform3D) {
        if (!arTracking) {
            if (arFromTransformer != null && !needsFreshCalibration) {
                needsFreshCalibration = true
                Log.w("ARsensFusion", "TRACKING_LOST needsFreshCalibration=true")
            }
            previousArFromCameraGl = null
            wasArTracking = false
            return
        }

        val previous = previousArFromCameraGl
        if (wasArTracking && previous != null && arFromTransformer != null && !needsFreshCalibration) {
            val cameraJumpMm = previous.distanceTo(currentArFromCameraGl)
            val cameraJumpAngleDeg = previous.rotationAngleDegreesTo(currentArFromCameraGl)
            if (cameraJumpMm > MAX_ARCORE_FRAME_JUMP_MM || cameraJumpAngleDeg > MAX_ARCORE_FRAME_JUMP_ANGLE_DEG) {
                needsFreshCalibration = true
                Log.w(
                    "ARsensFusion",
                    "ARCORE_JUMP needsFreshCalibration=true jump=${cameraJumpMm.shortMm()} angle=${cameraJumpAngleDeg.shortDeg()}"
                )
            }
        }
        previousArFromCameraGl = currentArFromCameraGl
        wasArTracking = true
    }

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
    val markerSignatures: Map<Int, String>
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
    markerSignatures: Map<Int, String>
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
        markerSignatures = markerSignatures
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
private const val MAX_REPROJECTION_ERROR_PX = 60f
private const val MAX_MULTI_TAG_REPROJECTION_ERROR_PX = 5f
private const val MAX_TAG_CORRECTION_JUMP_MM = 2_500.0
private const val MAX_TAG_CORRECTION_ANGLE_DEG = 35.0
private const val MAX_SINGLE_TAG_CORRECTION_JUMP_MM = 120.0
private const val MAX_SINGLE_TAG_CORRECTION_ANGLE_DEG = 6.0
private const val MAX_MULTI_REFERENCE_SINGLE_TAG_CORRECTION_JUMP_MM = 250.0
private const val MAX_MULTI_REFERENCE_SINGLE_TAG_CORRECTION_ANGLE_DEG = 10.0
private const val SINGLE_TAG_REANCHOR_REPROJECTION_PX = 3.0f
private const val PENDING_SINGLE_REANCHOR_STABLE_FRAMES = 3
private const val PENDING_SINGLE_REANCHOR_STABLE_TRANSLATION_MM = 60.0
private const val PENDING_SINGLE_REANCHOR_STABLE_ANGLE_DEG = 2.5
private const val MULTI_TAG_CORRECTION_ALPHA = 0.85
private const val SINGLE_TAG_LOCAL_CORRECTION_ALPHA = 0.25
private const val MIN_STABLE_SINGLE_TAG_CORRECTION_FRAMES = 3
private const val MAX_ARCORE_FRAME_JUMP_MM = 900.0
private const val MAX_ARCORE_FRAME_JUMP_ANGLE_DEG = 28.0
private const val TAG_STATUS_HOLD_MILLIS = 250L
private const val LIVE_DETECTION_HOLD_MILLIS = 250L
private const val PER_TAG_POSE_FRESH_MILLIS = 250L
private const val DEBUG_DETECTION_HOLD_MILLIS = 500L
private const val TAG_RESET_AFTER_MILLIS = 20_000L
private const val FUSION_LOG_INTERVAL_MILLIS = 1_000L
private const val OVERLAY_ROUTE_LOG_INTERVAL_MILLIS = 500L
private const val PUBLISH_LOG_INTERVAL_MILLIS = 2_000L

private fun Float.shortPx(): String =
    "${((this * 100f).roundToInt() / 100f)}px"

private fun Double.shortMm(): String =
    "${roundToInt()}mm"

private fun Double.shortDeg(): String =
    "${((this * 10.0).roundToInt() / 10.0)}deg"

private fun Double.shortAlpha(): String =
    "${((this * 100.0).roundToInt() / 100.0)}"
