package com.example.video_recorder_test.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
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
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.video_recorder_test.domain.CameraRepository
import com.example.video_recorder_test.model.CaptureImageInfo
import com.example.video_recorder_test.model.ImageFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
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
            setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            setResolutionSelector(imageAnalyzerResolutionSelector)
            setTargetRotation(Surface.ROTATION_0)
        }.build()
    }

    override suspend fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        deviceRotationDegree: Int
    ): Flow<ImageFrame> =
        withContext(
            Dispatchers.IO
        ) {
            this@CameraRepositoryImpl.lifecycleOwner = lifecycleOwner
            this@CameraRepositoryImpl.deviceRotationDegree = deviceRotationDegree

            try {
                withContext(Dispatchers.Main) {
                    with(cameraProvider) {
                        unbindAll()
                        camera = bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            imageAnalysis
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error On Camera: %s", e.message)
            }
            return@withContext createImageFrameFlow()
        }

    override suspend fun changeMode(cameraMode: String): Flow<ImageFrame> {
        this.currentMode = cameraMode
        return bindCameraToLifeCycle()
    }


    private suspend fun bindCameraToLifeCycle(): Flow<ImageFrame> =
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
                            videoCapture
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
                            rotation = it.imageInfo.rotationDegrees, cropRect = it.cropRect
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

    private fun calculateAspectRatio(): Rational {
        //지정된 화면 비율에 맞추어 크롭할 수 있도록
        return displayRational
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
                val res = ImageFrame(
                    data = imageData.toBitmap().run {
                        Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply {
                            postRotate(imageData.imageInfo.rotationDegrees.toFloat())
                        }, true)
                    },
                    width = imageData.width,
                    height = imageData.height
                )
                trySend(res)
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
}