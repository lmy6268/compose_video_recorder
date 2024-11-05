package com.example.video_recorder_test.ui

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.video_recorder_test.domain.CameraRepository
import com.example.video_recorder_test.model.ImageFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: CameraRepository
) : ViewModel() {
    private var _frame = MutableStateFlow<ImageFrame?>(null)
    val frame = _frame.asStateFlow()
    fun initializeCamera(lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            repository.initializeCamera(lifecycleOwner)
                .collectLatest {
                    _frame.value = it
                }
        }
    }
}