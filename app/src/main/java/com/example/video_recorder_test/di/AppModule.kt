package com.example.video_recorder_test.di

import com.example.video_recorder_test.data.CameraRepositoryImpl
import com.example.video_recorder_test.domain.CameraRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton //싱글톤으로 선언하지 않는 경우, 다른 UseCase에서 호출 시, 레포지토리 내, 멤버 변수의 값을 유지하지 못함.
    abstract fun bindsCameraRepository(
        cameraRepository: CameraRepositoryImpl
    ): CameraRepository
}