/*
 * Copyright (C) 2018 CyberAgent, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jp.co.cyberagent.android.gpuimage.filter

import android.content.Context
import android.graphics.PointF
import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.util.OpenGlUtils
import timber.log.Timber
import java.io.InputStream
import java.nio.FloatBuffer
import java.util.LinkedList
import java.util.Scanner

open class GPUImageFilter @JvmOverloads constructor(
    private val vertexShader: String = NO_FILTER_VERTEX_SHADER,
    private val fragmentShader: String = NO_FILTER_FRAGMENT_SHADER
) {
    private val runOnDraw = LinkedList<Runnable>()
    var program: Int = 0
        private set
    private var attribPosition: Int = 0
        private set
    private var uniformTexture: Int = 0
        private set
    private var attribTextureCoordinate: Int = 0
        private set
    var outputWidth: Int = 0
        private set
    var outputHeight: Int = 0
        private set
    var isInitialized: Boolean = false
        private set

    private fun init() {
        onInit()
        onInitialized()
    }

    open fun onInit() {
        program = OpenGlUtils.loadProgram(vertexShader, fragmentShader)
        attribPosition = GLES20.glGetAttribLocation(program, "position")
        uniformTexture = GLES20.glGetUniformLocation(program, "inputImageTexture")
        attribTextureCoordinate = GLES20.glGetAttribLocation(program, "inputTextureCoordinate")
        isInitialized = true
    }

    open fun onInitialized() {
    }

    fun ifNeedInit() {
        if (!isInitialized) init()
    }

    fun destroy() {
        isInitialized = false
        GLES20.glDeleteProgram(program)
        onDestroy()
    }

    open fun onDestroy() {
    }

    open fun onOutputSizeChanged(width: Int, height: Int) {
        outputWidth = width
        outputHeight = height
    }

    open fun onDraw(
        textureId: Int, cubeBuffer: FloatBuffer,
        textureBuffer: FloatBuffer
    ) {
        GLES20.glUseProgram(program)
        runPendingOnDrawTasks()
        if (!isInitialized) {
            return
        }

        cubeBuffer.position(0)
        GLES20.glVertexAttribPointer(attribPosition, 2, GLES20.GL_FLOAT, false, 0, cubeBuffer)
        GLES20.glEnableVertexAttribArray(attribPosition)
        textureBuffer.position(0)
        GLES20.glVertexAttribPointer(
            attribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
            textureBuffer
        )
        GLES20.glEnableVertexAttribArray(attribTextureCoordinate)
        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glUniform1i(uniformTexture, 0)
        }
        onDrawArraysPre()
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(attribPosition)
        GLES20.glDisableVertexAttribArray(attribTextureCoordinate)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    protected open fun onDrawArraysPre() {
    }

    protected fun runPendingOnDrawTasks() {
        synchronized(runOnDraw) {
//            Timber.tag("runPendingOnDrawTasks()")
//                .d("%s", if (runOnDraw.isEmpty()) "empty" else "not empty")
//            //여기서 로그를 찍으면, runOnDraw에서 입력한 데이터가 보이지 않음.
            while (!runOnDraw.isEmpty()) {
                val item = runOnDraw.removeFirst()
                item.run()
            }
        }
    }

    protected fun setInteger(location: Int, intValue: Int) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniform1i(location, intValue)
        }
    }

    fun setFloat(location: Int, floatValue: Float) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniform1f(location, floatValue)
        }
    }

    protected fun setFloatVec2(location: Int, arrayValue: FloatArray?) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniform2fv(location, 1, FloatBuffer.wrap(arrayValue))
        }
    }

    protected fun setFloatVec3(location: Int, arrayValue: FloatArray?) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniform3fv(location, 1, FloatBuffer.wrap(arrayValue))
        }
    }

    protected fun setFloatVec4(location: Int, arrayValue: FloatArray?) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniform4fv(location, 1, FloatBuffer.wrap(arrayValue))
        }
    }

    protected fun setFloatArray(location: Int, arrayValue: FloatArray) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniform1fv(location, arrayValue.size, FloatBuffer.wrap(arrayValue))
        }
    }

    protected fun setPoint(location: Int, point: PointF) {
        runOnDraw {
            ifNeedInit()
            val vec2 = FloatArray(2)
            vec2[0] = point.x
            vec2[1] = point.y
            GLES20.glUniform2fv(location, 1, vec2, 0)
        }
    }

    protected fun setUniformMatrix3f(location: Int, matrix: FloatArray?) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniformMatrix3fv(location, 1, false, matrix, 0)
        }
    }

    protected fun setUniformMatrix4f(location: Int, matrix: FloatArray?) {
        runOnDraw {
            ifNeedInit()
            GLES20.glUniformMatrix4fv(location, 1, false, matrix, 0)
        }
    }

    protected fun runOnDraw(runnable: Runnable) {
        synchronized(runOnDraw) {
            //여기까지는 진행이 됨.
            runOnDraw.addLast(runnable)
        }
    }

    companion object {
        const val NO_FILTER_VERTEX_SHADER: String = "" +
                "attribute vec4 position;\n" +
                "attribute vec4 inputTextureCoordinate;\n" +
                " \n" +
                "varying vec2 textureCoordinate;\n" +
                " \n" +
                "void main()\n" +
                "{\n" +
                "    gl_Position = position;\n" +
                "    textureCoordinate = inputTextureCoordinate.xy;\n" +
                "}"
        const val NO_FILTER_FRAGMENT_SHADER: String = "" +
                "varying highp vec2 textureCoordinate;\n" +
                " \n" +
                "uniform sampler2D inputImageTexture;\n" +
                " \n" +
                "void main()\n" +
                "{\n" +
                "     gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
                "}"

        fun loadShader(file: String?, context: Context): String {
            try {
                val assetManager = context.assets
                val ims = assetManager.open(file!!)

                val re = convertStreamToString(ims)
                ims.close()
                return re
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return ""
        }

        private fun convertStreamToString(`is`: InputStream?): String {
            val s = Scanner(`is`).useDelimiter("\\A")
            return if (s.hasNext()) s.next() else ""
        }
    }
}
