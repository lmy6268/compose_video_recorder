package com.example.video_recorder_test.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.video_recorder_test.domain.CameraRepository
import com.example.video_recorder_test.model.ImageFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class CameraRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : CameraRepository {
    companion object {
        const val TAG = "CameraRepositoryImpl"
    }

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
    private lateinit var imageAnalysis: ImageAnalysis
    override suspend fun initializeCamera(lifecycleOwner: LifecycleOwner): Flow<ImageFrame> =
        withContext(
            Dispatchers.IO
        ) {

            val imageAnalyzerResolutionSelector =
                ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(
                        Size(2880, 3840), //미리보기
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                    )
                ).build()
            imageAnalysis = ImageAnalysis.Builder().apply {
                setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                setResolutionSelector(imageAnalyzerResolutionSelector)
                setTargetRotation(Surface.ROTATION_0)
            }.build()
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
            return@withContext callbackFlow {
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
        }


}