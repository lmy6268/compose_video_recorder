package com.example.video_recorder_test.domain

import androidx.lifecycle.LifecycleOwner
import com.example.video_recorder_test.model.ImageFrame
import kotlinx.coroutines.flow.Flow

interface CameraRepository {
    suspend fun initializeCamera(lifecycleOwner: LifecycleOwner): Flow<ImageFrame>
}