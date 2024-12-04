package com.example.video_recorder_test.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.video_recorder_test.R
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.GPUImageView.Companion.RENDERMODE_WHEN_DIRTY
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageLookupFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageMovieWriter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import java.io.FileDescriptor
import java.util.Locale

fun getFileDescriptor(context: Context, fileName: String): FileDescriptor? {

    // Define content values for the new video entry
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(
            MediaStore.Video.Media.RELATIVE_PATH,
            "Movies/MyAppFolder"
        ) // Save in "Movies/MyAppFolder"
    }

    // Insert the content values to MediaStore and get a Uri
    val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val videoUri: Uri? = context.contentResolver.insert(collection, values)

    return videoUri?.let { uri ->
        // Open a file descriptor from the Uri
        context.contentResolver.openFileDescriptor(uri, "rw")?.fileDescriptor
    }
}

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
    var writer by remember { mutableStateOf(GPUImageMovieWriter()) }
    var cnt by remember {
        mutableIntStateOf(0)
    }
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
    val filter = remember {
        GPUImageLookupFilter().apply {
            bitmap = BitmapFactory.decodeResource(
                context.resources,
                R.drawable.filter_1
            )
        }
    }
    var applied by remember {
        mutableStateOf(GPUImageFilterGroup().apply { GPUImageFilter() })
    }

    var currentVideo by remember {
        mutableStateOf<GPUImageMovieWriter?>(null)
    }

    var gpuImageView by remember {
        mutableStateOf<GPUImageView?>(null)
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

                if (isRecording) RecordTimeIndicator(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 50.dp)
                        .zIndex(1f)
                )


                AndroidView(
                    factory = {
                        GPUImageView(it).apply {
                            setRenderMode(RENDERMODE_WHEN_DIRTY)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setBackgroundColor(android.graphics.Color.WHITE)
                            gpuImageView = this
                        }
                    },
                    update = {
                        it.apply {
                            applied = GPUImageFilterGroup(applied.mergedFilters).apply {
                                addFilter(filter)
                            }
                            frame?.run {
                                setImage(data)
                            }
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
                            if (currentMode == "Video") {
                                if (!isRecording) {
                                    currentVideo = GPUImageMovieWriter().apply {
                                        startRecording(
                                            getFileDescriptor(context, "${System.currentTimeMillis()}.mp4"),
                                            1080,
                                            1920
                                        )
                                    }
                                    applied.addFilter(filter)
                                } else currentVideo?.stopRecording()
                                isRecording = !isRecording
                            } else viewModel.takePhoto()
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
                            viewModel.changeMode(currentMode)
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

@Composable
fun RecordTimeIndicator(
    modifier: Modifier = Modifier,
) {
    val textStyle = remember {
        TextStyle(
            color = Color.White
        )
    }
    var elapsedTime by remember { mutableLongStateOf(0L) }
    // 포맷된 시간 문자열
    val timeText by remember {
        derivedStateOf {
            val hours = (elapsedTime / 3600000) % 24 // 시 계산
            val minutes = (elapsedTime / 60000) % 60 // 분 계산
            val seconds = (elapsedTime / 1000) % 60 // 초 계산

            // 두 자리로 표시
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            elapsedTime += 1000L // 1초씩 더해줌
        }
    }

    val backgroundRed = Color(0xFFeb4d3c)

    Box(
        modifier = modifier
            .background(backgroundRed)
            .clip(RoundedCornerShape(9.dp)),
    ) {
        Text(
            modifier = Modifier.padding(vertical = 3.dp, horizontal = 5.dp),
            text = timeText,
            style = textStyle
        )
    }
}