package com.example.video_recorder_test.ui

import android.opengl.GLSurfaceView
import android.view.OrientationEventListener
import android.view.ViewGroup
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.GPUImageView.RENDERMODE_WHEN_DIRTY
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

@Composable
fun CameraViewFinder(
    modifier: Modifier = Modifier,
) {
    var isRecording by remember {
        mutableStateOf(false)
    }
    val viewModel: MainViewModel = hiltViewModel()
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val frame by viewModel.frame.collectAsState()

    val latestCaptureImage by viewModel.latestCaptureImage.collectAsState()

    var showCaptureImage by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(latestCaptureImage) {
        latestCaptureImage?.run {
            showCaptureImage = true
        }
    }

    var currentMode by remember { mutableStateOf("Photo") }

    val targetRotation by remember {
        callbackFlow {
            val listener = object : OrientationEventListener(context) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation != ORIENTATION_UNKNOWN) {
                        trySend(
                            when (orientation) {
                                in 45..134 -> 90 // 기기가 시계 방향으로 90도 회전
                                in 135..224 -> 180 // 기기가 180도 회전
                                in 225..314 -> 270  // 기기가 반시계 방향으로 90도 회전
                                else -> 0 // 기본 방향
                            }
                        )
                    }
                }
            }
            listener.enable()
            awaitClose {
                listener.disable()
            }
        }
    }.collectAsState(0)

    LaunchedEffect(Unit) {
        viewModel.initializeCamera(owner, rotationDegree = targetRotation)
    }

    LaunchedEffect(isRecording) {


    }
    LaunchedEffect(currentMode) {
        viewModel.changeMode(currentMode)
    }
    AnimatedContent(targetState = showCaptureImage, label = "프리뷰 화면") { value ->
        if (value) latestCaptureImage?.data?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "이미지",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        showCaptureImage = false
                    })
        }
        else {
            Box(modifier = modifier.fillMaxSize()) {
                AndroidView(
                    factory = {
                        GPUImageView(it).apply {
                            setRenderMode(RENDERMODE_WHEN_DIRTY)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            setBackgroundColor(android.graphics.Color.WHITE)
                        }
                    },
                    update = {
                        frame?.data?.let { bitmap ->
                            it.setImage(bitmap)
                        }
                    }
                )
                Row(
                    Modifier
                        .padding(bottom = 10.dp)
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Button(
                        onClick = {
                            if (currentMode == "Video") isRecording = !isRecording
                            else viewModel.takePhoto()
                        },
                        modifier = Modifier
                            .height(100.dp)
                            .padding(bottom = 50.dp)
                    ) {
                        if (currentMode == "Video") Text(text = if (!isRecording) "녹화 시작" else "녹화 중지")
                        else Text(text = "사진 촬영")
                    }
                    Button(
                        onClick = {
                            currentMode = when (currentMode) {
                                "Photo" -> "Video"
                                else -> "Photo"
                            }
                        },
                        modifier = Modifier
                            .height(100.dp)
                            .padding(bottom = 50.dp)
                    ) {
                        Text(text = currentMode)
                    }
                }
            }
        }
    }
}