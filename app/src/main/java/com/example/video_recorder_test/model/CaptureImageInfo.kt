package com.example.video_recorder_test.model

import android.graphics.Rect

data class CaptureImageInfo(
        val rotation: Int, val cropRect: Rect
    )