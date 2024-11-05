package com.example.video_recorder_test.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun MainScreen(
    modifier: Modifier
) {
    var showScreen by remember {
        mutableStateOf(false)
    }
    val context = LocalContext.current

    val checked = checkPermissionsAreGranted(context)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        showScreen = it.values.all { value -> value }
    }

    if (checked.isEmpty()) showScreen = true
    else LaunchedEffect(key1 = Unit) {
        permissionLauncher.launch(checked)
    }
    if (showScreen) {
        CameraViewFinder(modifier.fillMaxSize())
    }
}

val permissions = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
).run {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU) this + arrayOf()
    else this
}

private fun checkPermissionsAreGranted(context: Context): Array<String> {
    val needsToRequest = mutableListOf<String>()
    permissions.forEach { perm ->
        if (ContextCompat.checkSelfPermission(
                context,
                perm
            ) != PackageManager.PERMISSION_GRANTED
        ) needsToRequest.add(perm)
    }

    return needsToRequest.toTypedArray()
}