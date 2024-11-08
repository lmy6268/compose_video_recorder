package com.example.video_recorder_test.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.WindowInsets
import android.view.WindowManager
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.video_recorder_test.domain.CameraRepository
import com.example.video_recorder_test.model.CaptureImageInfo
import com.example.video_recorder_test.model.ImageFrame
import com.example.video_recorder_test.model.PreviewImageFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Core.ROTATE_180
import org.opencv.core.Core.ROTATE_90_CLOCKWISE
import org.opencv.core.Core.ROTATE_90_COUNTERCLOCKWISE
import org.opencv.core.CvType
import org.opencv.core.Mat
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


@SuppressLint("RestrictedApi")
class CameraRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CameraRepository {
    companion object {
        const val TAG = "CameraRepositoryImpl"
    }


    private var videoRecording: Recording? = null

    private val displayRational by lazy {
        val windowManager = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
        val resolution = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val display = windowManager.defaultDisplay
            val metrics = DisplayMetrics()
            display.getMetrics(metrics)
            Size(metrics.widthPixels, metrics.heightPixels)
        } else {
            windowManager.currentWindowMetrics.let { windowMetrics ->
                val bounds = windowMetrics.bounds
                windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                    .let { insets ->
                        Size(
                            bounds.width() - (insets.left + insets.right),
                            bounds.height() - (insets.top + insets.bottom)
                        )
                    }
            }
        }
        Rational(resolution.width, resolution.height)
    }

    private val videoRecorder by lazy {
        val selector = QualitySelector.from(
            Quality.FHD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)
        )
        Recorder.Builder().apply {
            setQualitySelector(selector)
        }.build()
    }
    private var deviceRotationDegree: Int = 0
    private val videoCapture by lazy {
        VideoCapture.withOutput(videoRecorder)
    }
    private val imageCapture by lazy {
        ImageCapture.Builder().apply {
            setHighResolutionDisabled(false)
            setTargetRotation(calculateImageRotation())
            setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            setResolutionSelector(
                ResolutionSelector.Builder().build()
            )
        }.build().apply {
            setCropAspectRatio(displayRational)
        }
    }
    private lateinit var lifecycleOwner: LifecycleOwner
    private fun cameraCallback(
        onError: (ImageCaptureException) -> Unit, onSuccess: (ImageProxy) -> Unit
    ): ImageCapture.OnImageCapturedCallback = object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            onSuccess(image)
        }

        override fun onError(exception: ImageCaptureException) {
            onError(exception)
        }
    }

    private var currentMode = "Photo"
    private val cameraExecutor by lazy { ContextCompat.getMainExecutor(context) }
    private lateinit var camera: Camera
    private val cameraProviderFuture by lazy {
        ProcessCameraProvider.getInstance(context)
    }
    private val cameraProvider by lazy {
        cameraProviderFuture.get()
    }
    private val cameraSelector by lazy {
        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
    }
    private val imageAnalysis: ImageAnalysis by lazy {
        val imageAnalyzerResolutionSelector = ResolutionSelector.Builder().setResolutionStrategy(
            ResolutionStrategy(
                Size(2880, 3840), //미리보기
                ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
        ).build()


        ImageAnalysis.Builder().apply {
            setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            setResolutionSelector(imageAnalyzerResolutionSelector)
            setTargetRotation(Surface.ROTATION_0)
        }.build()
    }

    override suspend fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        deviceRotationDegree: Int
    ): Flow<PreviewImageFrame> =
        withContext(
            Dispatchers.IO
        ) {
            this@CameraRepositoryImpl.lifecycleOwner = lifecycleOwner
            this@CameraRepositoryImpl.deviceRotationDegree = deviceRotationDegree

            bindCameraToLifeCycle()
        }

    override suspend fun changeMode(cameraMode: String): Flow<PreviewImageFrame> {
        this.currentMode = cameraMode
        return bindCameraToLifeCycle()
    }


    private suspend fun bindCameraToLifeCycle(): Flow<PreviewImageFrame> =
        withContext(Dispatchers.IO) {
            if (currentMode == "Photo") try {
                withContext(Dispatchers.Main) {
                    with(cameraProvider) {
                        unbindAll()
                        camera = bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            imageAnalysis,
                            imageCapture
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error On Camera: %s", e.message)
            }
            else try {
                withContext(Dispatchers.Main) {
                    with(cameraProvider) {
                        unbindAll()
                        camera = bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            imageAnalysis,
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error On Camera: %s", e.message)
            }

            return@withContext createImageFrameFlow()
        }


    private suspend fun getCaptureImageData() = suspendCancellableCoroutine { cont ->
        imageCapture.takePicture(
            cameraExecutor, cameraCallback(onError = {
                cont.resumeWithException(it)
            }, onSuccess = { imageProxy ->
                imageProxy.use {
                    val bitmap = it.toBitmap()
                    cont.resume(
                        bitmap to CaptureImageInfo(
                            rotation = it.imageInfo.rotationDegrees,
                            cropRect = it.cropRect
                        )
                    )
                }
            })
        )
    }


    override suspend fun takeCapture(): ImageFrame {
        val bitmap = getCaptureImageData().let { (image, info) ->
            image.cropWithRotation(
                cropRect = info.cropRect,
                degree = info.rotation,
            )
        }
        return ImageFrame(
            data = bitmap,
            width = bitmap.width,
            height = bitmap.height
        )
    }

    @SuppressLint("MissingPermission")
    override fun startRecording() {
        val fileName = "CameraX-recording-" +
                SimpleDateFormat("yyyy-mm-dd", Locale.US)
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        }

        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL
                )
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        ).setContentValues(contentValues).build()




        videoRecording =
            videoCapture.output.prepareRecording(context, mediaStoreOutput).withAudioEnabled()
                .start(cameraExecutor) {
                    Timber.tag("Video Capture Status").d("${it.recordingStats}")
                }
    }


    override fun stopRecording() {
        videoRecording?.stop()
    }


    private fun calculateImageRotation(): Int {

        val sign = -1
        val imageRotation = (-deviceRotationDegree * sign + 360) % 360

        val res = when (imageRotation) {
            in 45 until 135 -> Surface.ROTATION_90
            in 135 until 225 -> Surface.ROTATION_180
            in 225 until 315 -> Surface.ROTATION_270
            else -> Surface.ROTATION_0
        }

        return res
    }

    private fun createImageFrameFlow() = callbackFlow {
        imageAnalysis.setAnalyzer(cameraExecutor) { proxy ->
            proxy.use { imageData ->
                with(imageData) {
                    PreviewImageFrame(
                        toBitmap().rotation(imageInfo.rotationDegrees),
                        width = width,
                        height = height,
                        rotation = imageInfo.rotationDegrees
                    ).let { trySend(it) }
                }

            }

        }
        awaitClose {
            imageAnalysis.clearAnalyzer()
        }
    }


    private fun Bitmap.cropWithRotation(
        cropRect: Rect,
        degree: Int,
    ): Bitmap {
        // Step 1: Crop the Bitmap
        val croppedBitmap = Bitmap.createBitmap(
            this,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        )

        // Step 2: Create a Matrix for rotation and mirroring
        val matrix = Matrix().apply {
            // Rotate the Bitmap by the given degree
            postRotate(degree.toFloat())
        }

        // Step 3: Create a new Bitmap with the transformations applied
        val rotatedBitmap = Bitmap.createBitmap(
            croppedBitmap,
            0,
            0,
            croppedBitmap.width,
            croppedBitmap.height,
            matrix,
            true
        )

        // Step 4: Recycle the croppedBitmap if it is no longer needed
        if (croppedBitmap != this) {
            croppedBitmap.recycle()
        }

        return rotatedBitmap
    }

    //https://parade621.tistory.com/95
    private fun ImageProxy.imageToNV21(): ByteArray {
        val ySize = width * height
        val numPixels = (ySize * 1.5f).toInt()
        val yuvData = ByteArray(numPixels)

        // Process Y plane
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride

        var index = 0

        val yPixelStride = yPlane.pixelStride
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rowOffset = (y * yRowStride) + (x * yPixelStride)
                yuvData[index++] = yBuffer[rowOffset]
            }
        }

        // Process U plane
        val uPlane = planes[1]
        val vPlane = planes[2]
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        for (y in 0 until height / 2) {
            for (x in 0 until width / 2) {
                val rowOffset = (y * uvRowStride) + (x * uvPixelStride)
                yuvData[index++] = uBuffer[rowOffset]
                yuvData[index++] = vBuffer[rowOffset]
            }
        }

        return yuvData
    }

    fun Bitmap.rotation(rotate: Int): Bitmap {
        val mat = Mat.zeros(width, height, CvType.CV_8UC3).apply {
            Utils.bitmapToMat(this@rotation, this)
        }

        val rotateCode = when (rotate) {
            90 -> ROTATE_90_CLOCKWISE
            180 -> ROTATE_180
            270 -> ROTATE_90_COUNTERCLOCKWISE
            else -> null
        }
        rotateCode?.let { Core.rotate(mat, mat, it) }

        val newBitmap = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, newBitmap)

        return newBitmap
    }
}