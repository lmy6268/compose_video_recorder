package com.example.video_recorder_test.model

import android.graphics.Bitmap

data class ImageFrame(
    val data: Bitmap,
    val width: Int,
    val height: Int
)

data class PreviewImageFrame(
    val data: Bitmap,
    val width: Int,
    val height: Int,
    val rotation: Int
) {

}