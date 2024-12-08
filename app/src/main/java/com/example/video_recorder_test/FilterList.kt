package com.example.video_recorder_test

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup

class FilterList(vararg filters: GPUImageFilter) {
    private val filter = mutableListOf<GPUImageFilter>().apply {
        for (f in filters) {
            add(f)
        }
    }

    var state by mutableStateOf(GPUImageFilterGroup(filter))
        private set

    fun push(filter: GPUImageFilter) {
        this.filter.add(filter)
        updateState()
    }

    fun pop() {
        this.filter.removeLast()
        updateState()
    }

    private fun updateState() {
        state = GPUImageFilterGroup(filter)
    }

    fun hasFilter() {

    }
}