package com.example.video_recorder_test.ui

import android.opengl.GLSurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import jp.co.cyberagent.android.gpuimage.GPUImageView

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
    val gpuImageView = remember {
        GPUImageView(context).apply {
            setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initializeCamera(owner)
    }

    LaunchedEffect(isRecording) {


    }



    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { gpuImageView }, update = {
            frame?.run {
                it.setImage(this.data)
            }
        })
        Button(
            onClick = {
                isRecording = !isRecording
            },
            modifier = Modifier
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .padding(bottom = 50.dp)
        ) {
            Text(text = if (!isRecording) "Start Record" else "Stop Recording")
        }
    }
}