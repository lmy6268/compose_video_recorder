package com.example.video_recorder_test.ui

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.video_recorder_test.domain.CameraRepository
import com.example.video_recorder_test.model.ImageFrame
import com.example.video_recorder_test.model.PreviewImageFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: CameraRepository
) : ViewModel() {
    private var _frame = MutableStateFlow<PreviewImageFrame?>(null)
    val frame = _frame.asStateFlow()
    private var _latestCaptureImage = MutableStateFlow<ImageFrame?>(null)
    val latestCaptureImage = _latestCaptureImage.asStateFlow()
    fun initializeCamera(lifecycleOwner: LifecycleOwner, rotationDegree: Int) {
        viewModelScope.launch {
            repository.initializeCamera(lifecycleOwner, rotationDegree)
                .collectLatest {
                    _frame.value = it
                }
        }
    }

    fun changeMode(cameraMode: String) {
        viewModelScope.launch {
            repository.changeMode(cameraMode).collectLatest {
                _frame.value = it
            }
        }
    }


    fun takePhoto() {
        viewModelScope.launch {
            _latestCaptureImage.value = repository.takeCapture()
        }
    }


}