package jp.co.cyberagent.android.gpuimage.filter

import android.opengl.EGL14
import android.os.ParcelFileDescriptor
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
            /** [mCodecInput] sometimes turns to null.
             * */
            mCodecInput?.let {
                it.makeCurrent()
                super.onDraw(textureId, cubeBuffer, textureBuffer)
                it.swapBuffers()
                mVideoEncoder!!.frameAvailableSoon()
            } ?: run {

                mEGLCore = EglCore(EGL14.eglGetCurrentContext(), EglCore.FLAG_RECORDABLE)
                mCodecInput = WindowSurface(mEGLCore, mVideoEncoder!!.surface, false)
                Timber.e("mCodecInput is null, skipping encoding.")
            }
        }

        // Make screen surface be current surface
        mEGL!!.eglMakeCurrent(mEGLDisplay, mEGLScreenSurface, mEGLScreenSurface, mEGLContext)
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseEncodeSurface()
    }

    fun startRecording(fd: ParcelFileDescriptor?, width: Int, height: Int) {
        runOnDraw {
            if (mIsRecording) {
                return@runOnDraw
            }
            try {
                mMuxer = MediaMuxerWrapper(fd?.fileDescriptor)

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
            if (!mIsRecording) {
                return@runOnDraw
            }

            mMuxer!!.stopRecording()
            mIsRecording = false
            releaseEncodeSurface()
        }
    }

    private fun releaseEncodeSurface() {

        mEGLCore?.run {
            makeNothingCurrent()
            release()
            mEGLCore = null
        }

        mCodecInput?.run {
            release()
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
