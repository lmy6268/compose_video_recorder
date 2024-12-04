package jp.co.cyberagent.android.gpuimage.filter

import android.opengl.EGL14
import android.util.Log
import jp.co.cyberagent.android.gpuimage.encoder.EglCore
import jp.co.cyberagent.android.gpuimage.encoder.MediaAudioEncoder
import jp.co.cyberagent.android.gpuimage.encoder.MediaEncoder
import jp.co.cyberagent.android.gpuimage.encoder.MediaEncoder.MediaEncoderListener
import jp.co.cyberagent.android.gpuimage.encoder.MediaMuxerWrapper
import jp.co.cyberagent.android.gpuimage.encoder.MediaVideoEncoder
import jp.co.cyberagent.android.gpuimage.encoder.WindowSurface
import timber.log.Timber
import java.io.FileDescriptor
import java.io.IOException
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

class GPUImageMovieWriter : GPUImageFilter() {
    private var mMuxer: MediaMuxerWrapper? = null
    private var mVideoEncoder: MediaVideoEncoder? = null
    private var mAudioEncoder: MediaAudioEncoder? = null
    private var mCodecInput: WindowSurface? = null

    private var mEGLScreenSurface: EGLSurface? = null
    private var mEGL: EGL10? = null
    private var mEGLDisplay: EGLDisplay? = null
    private var mEGLContext: EGLContext? = null
    private var mEGLCore: EglCore? = null

    private var mIsRecording = false

    override fun onInit() {
        super.onInit()
        mEGL = EGLContext.getEGL() as EGL10
        mEGLDisplay = mEGL!!.eglGetCurrentDisplay()
        mEGLContext = mEGL!!.eglGetCurrentContext()
        mEGLScreenSurface = mEGL!!.eglGetCurrentSurface(EGL10.EGL_DRAW)
    }

    override fun onDraw(textureId: Int, cubeBuffer: FloatBuffer, textureBuffer: FloatBuffer) {
        // Draw on screen surface
        super.onDraw(textureId, cubeBuffer, textureBuffer)

        if (mIsRecording) {
            // create encoder surface
            if (mCodecInput == null) {
                mEGLCore = EglCore(EGL14.eglGetCurrentContext(), EglCore.FLAG_RECORDABLE)
                mCodecInput = WindowSurface(mEGLCore, mVideoEncoder!!.surface, false)
            }

            // Draw on encoder surface
            mCodecInput!!.makeCurrent()
            super.onDraw(textureId, cubeBuffer, textureBuffer)
            mCodecInput!!.swapBuffers()
            mVideoEncoder!!.frameAvailableSoon()
        }

        // Make screen surface be current surface
        mEGL!!.eglMakeCurrent(mEGLDisplay, mEGLScreenSurface, mEGLScreenSurface, mEGLContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseEncodeSurface()
    }

    fun startRecording(outputPath: String?, width: Int, height: Int) {
        runOnDraw {
            if (mIsRecording) {
                return@runOnDraw
            }
            try {
                mMuxer = MediaMuxerWrapper(outputPath)

                // for video capturing
                mVideoEncoder = MediaVideoEncoder(mMuxer, mMediaEncoderListener, width, height)
                // for audio capturing
                mAudioEncoder = MediaAudioEncoder(mMuxer, mMediaEncoderListener)

                mMuxer!!.prepare()
                mMuxer!!.startRecording()


                mIsRecording = true
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun startRecording(fd: FileDescriptor?, width: Int, height: Int) {
        Timber.tag(TAG).d("called startRecording")
        runOnDraw {
            Timber.tag(TAG).d("on start")
            if (mIsRecording) {
                return@runOnDraw
            }
            try {
                Timber.tag(TAG).d("on start")
                Log.d(TAG, "on Start")
                mMuxer = MediaMuxerWrapper(fd)

                // for video capturing
                mVideoEncoder = MediaVideoEncoder(mMuxer, mMediaEncoderListener, width, height)
                // for audio capturing
                mAudioEncoder = MediaAudioEncoder(mMuxer, mMediaEncoderListener)

                mMuxer!!.prepare()
                mMuxer!!.startRecording()

                mIsRecording = true
            } catch (e: IOException) {
                Log.e(TAG, e.message!!)
                Timber.tag(TAG).e(e)
            }
        }
    }

    fun stopRecording() {
        runOnDraw {
            Log.d(TAG, "onStop")
            Timber.tag(TAG).d("on stop")
            if (!mIsRecording) {
                return@runOnDraw
            }

            mMuxer!!.stopRecording()
            mIsRecording = false
            releaseEncodeSurface()
        }
    }

    private fun releaseEncodeSurface() {
        if (mEGLCore != null) {
            mEGLCore!!.makeNothingCurrent()
            mEGLCore!!.release()
            mEGLCore = null
        }

        if (mCodecInput != null) {
            mCodecInput!!.release()
            mCodecInput = null
        }
    }

    /**
     * callback methods from encoder
     */
    private val mMediaEncoderListener: MediaEncoderListener = object : MediaEncoderListener {
        override fun onPrepared(encoder: MediaEncoder) {
        }

        override fun onStopped(encoder: MediaEncoder) {
        }

        override fun onMuxerStopped() {
        }
    }

    companion object {
        private const val TAG = "Recording State"
    }
}
