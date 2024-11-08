package jp.co.cyberagent.android.gpuimage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.hardware.Camera
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ProgressBar
import jp.co.cyberagent.android.gpuimage.GPUImage.SURFACE_TYPE_SURFACE_VIEW
import jp.co.cyberagent.android.gpuimage.GPUImage.SURFACE_TYPE_TEXTURE_VIEW
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.util.Rotation
import java.io.File
import java.util.concurrent.Semaphore

class GPUImageView : FrameLayout {

    private var surfaceType = SURFACE_TYPE_SURFACE_VIEW
    private lateinit var surfaceView: View
    private lateinit var gpuImage: GPUImage
    private var isShowLoading = true
    private var filter: GPUImageFilter? = null
    var forceSize: Size? = null
    private var ratio = 0.0f

    companion object {
        const val RENDERMODE_WHEN_DIRTY = 0
        const val RENDERMODE_CONTINUOUSLY = 1
    }

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        if (attrs != null) {
            val a = context.theme.obtainStyledAttributes(attrs, R.styleable.GPUImageView, 0, 0)
            try {
                surfaceType = a.getInt(R.styleable.GPUImageView_gpuimage_surface_type, surfaceType)
                isShowLoading =
                    a.getBoolean(R.styleable.GPUImageView_gpuimage_show_loading, isShowLoading)
            } finally {
                a.recycle()
            }
        }
        gpuImage = GPUImage(context)
        surfaceView = if (surfaceType == SURFACE_TYPE_TEXTURE_VIEW) {
            val textureView = GPUImageGLTextureView(context, attrs)
            gpuImage.setGLTextureView(textureView)
            textureView
        } else {
            val glSurfaceView = GPUImageGLSurfaceView(context, attrs)
            gpuImage.setGLSurfaceView(glSurfaceView)
            glSurfaceView
        }
        addView(surfaceView)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (ratio != 0.0f) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)

            val newHeight: Int
            val newWidth: Int
            if (width / ratio < height) {
                newWidth = width
                newHeight = height
            } else {
                newHeight = height
                newWidth = Math.round(height * ratio)
            }

            val newWidthSpec = MeasureSpec.makeMeasureSpec(newWidth, MeasureSpec.EXACTLY)
            val newHeightSpec = MeasureSpec.makeMeasureSpec(newHeight, MeasureSpec.EXACTLY)
            super.onMeasure(newWidthSpec, newHeightSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    fun getGPUImage(): GPUImage {
        return gpuImage
    }

    @Deprecated("Use updatePreviewFrame() instead.")
    fun setUpCamera(camera: Camera) {
        gpuImage.setUpCamera(camera)
    }

    @Deprecated("Use updatePreviewFrame() instead.")
    fun setUpCamera(camera: Camera, degrees: Int, flipHorizontal: Boolean, flipVertical: Boolean) {
        gpuImage.setUpCamera(camera, degrees, flipHorizontal, flipVertical)
    }

    fun updatePreviewFrame(data: ByteArray, width: Int, height: Int) {
        gpuImage.updatePreviewFrame(data, width, height)
    }


    fun setBackgroundColor(red: Float, green: Float, blue: Float) {
        gpuImage.setBackgroundColor(red, green, blue)
    }

    fun setRenderMode(renderMode: Int) {
        when (surfaceView) {
            is GLSurfaceView -> (surfaceView as GLSurfaceView).setRenderMode(renderMode)
            is GLTextureView -> (surfaceView as GLTextureView).setRenderMode(renderMode)
        }
    }

    fun setRatio(ratio: Float) {
        this.ratio = ratio
        surfaceView.requestLayout()
        gpuImage.deleteImage()
    }

    fun setScaleType(scaleType: GPUImage.ScaleType) {
        gpuImage.setScaleType(scaleType)
    }

    fun setRotation(rotation: Rotation) {
        gpuImage.setRotation(rotation)
        requestRender()
    }

    fun setFilter(filter: GPUImageFilter?) {
        this.filter = filter
        gpuImage.setFilter(filter)
        requestRender()
    }

    fun getFilter(): GPUImageFilter? {
        return filter
    }

    fun setImage(bitmap: Bitmap) {
        gpuImage.setImage(bitmap)
    }

    fun setImage(uri: Uri) {
        gpuImage.setImage(uri)
    }

    fun setImage(file: File) {
        gpuImage.setImage(file)
    }

    private fun requestRender() {
        when (surfaceView) {
            is GLSurfaceView -> (surfaceView as GLSurfaceView).requestRender()
            is GLTextureView -> (surfaceView as GLTextureView).requestRender()
        }
    }


    fun capture(): Bitmap {
        val waiter = Semaphore(0)

        val width = surfaceView.measuredWidth
        val height = surfaceView.measuredHeight

        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        gpuImage.runOnGLThread {
            GPUImageNativeLibrary.adjustBitmap(resultBitmap)
            waiter.release()
        }
        requestRender()
        waiter.acquire()

        return resultBitmap
    }

    fun capture(width: Int, height: Int): Bitmap {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException("Do not call this method from the UI thread!")
        }

        forceSize = Size(width, height)

        val waiter = Semaphore(0)

        getViewTreeObserver().addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    getViewTreeObserver().removeGlobalOnLayoutListener(this)
                } else {
                    getViewTreeObserver().removeOnGlobalLayoutListener(this)
                }
                waiter.release()
            }
        })

        post {
            if (isShowLoading) {
                addView(LoadingView(context))
            }
            surfaceView.requestLayout()
        }

        waiter.acquire()

        gpuImage.runOnGLThread {
            waiter.release()
        }
        requestRender()
        waiter.acquire()
        val bitmap = capture()

        forceSize = null
        post {
            surfaceView.requestLayout()
        }
        requestRender()

        if (isShowLoading) {
            postDelayed({
                removeViewAt(1)
            }, 300)
        }

        return bitmap
    }


    fun onPause() {
        when (surfaceView) {
            is GLSurfaceView -> (surfaceView as GLSurfaceView).onPause()
            is GLTextureView -> (surfaceView as GLTextureView).onPause()
        }
    }

    fun onResume() {
        when (surfaceView) {
            is GLSurfaceView -> (surfaceView as GLSurfaceView).onResume()
            is GLTextureView -> (surfaceView as GLTextureView).onResume()
        }
    }

    data class Size(var width: Int, var height: Int)

    private inner class GPUImageGLSurfaceView : GLSurfaceView {

        constructor(context: Context) : super(context)
        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (forceSize != null) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(forceSize!!.width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(forceSize!!.height, MeasureSpec.EXACTLY)
                )
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    private inner class GPUImageGLTextureView : GLTextureView {

        constructor(context: Context) : super(context)
        constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (forceSize != null) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(forceSize!!.width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(forceSize!!.height, MeasureSpec.EXACTLY)
                )
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
    }

    private class LoadingView : FrameLayout {
        constructor(context: Context?) : super(context!!) {
            init()
        }

        constructor(context: Context?, attrs: AttributeSet?) : super(
            context!!, attrs
        ) {
            init()
        }

        constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
            context!!, attrs, defStyle
        ) {
            init()
        }

        private fun init() {
            val view = ProgressBar(context)
            view.layoutParams =
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            addView(view)
            setBackgroundColor(Color.BLACK)
        }
    }


    interface OnPictureSavedListener {
        fun onPictureSaved(uri: Uri?)
    }
}