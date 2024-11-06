package com.example.video_recorder_test.domain

import android.graphics.Bitmap
import androidx.lifecycle.LifecycleOwner
import com.example.video_recorder_test.model.ImageFrame
import kotlinx.coroutines.flow.Flow

interface CameraRepository {
 suspend fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        deviceRotationDegree: Int
    ):Flow<ImageFrame>

  suspend  fun changeMode(cameraMode: String): Flow<ImageFrame>
    suspend fun takeCapture(): ImageFrame

    fun startRecording()
    fun stopRecording()
}