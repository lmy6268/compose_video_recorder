package com.example.video_recorder_test

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        System.loadLibrary("opencv_java4") //openCV를 사용하기 위함.
    }
}