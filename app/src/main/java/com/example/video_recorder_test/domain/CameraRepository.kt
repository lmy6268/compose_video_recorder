package com.example.video_recorder_test.domain

import android.graphics.Bitmap
import androidx.lifecycle.LifecycleOwner
import com.example.video_recorder_test.model.ImageFrame
import com.example.video_recorder_test.model.PreviewImageFrame
import kotlinx.coroutines.flow.Flow

interface CameraRepository {
 suspend fun initializeCamera(
        lifecycleOwner: LifecycleOwner,
        deviceRotationDegree: Int
    ):Flow<PreviewImageFrame>

  suspend  fun changeMode(cameraMode: String): Flow<PreviewImageFrame>
    suspend fun takeCapture(): ImageFrame

    fun startRecording()
    fun stopRecording()
}