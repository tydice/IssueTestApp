package com.our.shot.presentation.common.rendering

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Build
import androidx.annotation.RequiresApi
import com.example.oneactivitytest2.R
import com.example.oneactivitytest2.helpers.ShaderUtil
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.*

class ImageRenderer(
    private val activityContext: Context
) {
    private val vertexShaderCode =
        // This matrix member variable provides a hook to manipulate
        // the coordinates of the objects that use this vertex shader
        "attribute vec2 a_TexCoordinate;" +
            "varying vec2 v_TexCoordinate;" +
            "uniform mat4 uMVPMatrix;" +
            "attribute vec4 vPosition;" +
            "void main() {" +
            // The matrix must be included as a modifier of gl_Position.
            // Note that the uMVPMatrix factor *must be first* in order
            // for the matrix multiplication product to be correct.
            "  gl_Position = uMVPMatrix * vPosition;" +
            "  v_TexCoordinate = a_TexCoordinate;" +
            "}"
    private val fragmentShaderCode = (
        "precision mediump float;" +
            "uniform sampler2D u_Texture;" +
            "varying vec2 v_TexCoordinate;" +
            "uniform vec4 vColor;" +
            "void main() {" +
            // "  gl_FragColor = vColor;" +
            "vec4 texColor = texture2D(u_Texture, v_TexCoordinate);" +
            "gl_FragColor = texColor;" +
            "}"
        )

    // Added for Textures
    private val mCubeTextureCoordinates: FloatBuffer
    private var mTextureUniformHandle = 0
    private var mTextureCoordinateHandle = 0
    private val mTextureCoordinateDataSize = 2
    private val mTextureDataHandle: Int = -1
    private val shaderProgram: Int
    private val vertexBuffer: FloatBuffer
    private val drawListBuffer: ShortBuffer
    private var mPositionHandle = 0
    private var mColorHandle = 0
    private var mMVPMatrixHandle = 0

    private val drawOrder = shortArrayOf(0, 1, 2, 0, 2, 3) // Order to draw vertices
    private val vertexStride = COORDS_PER_VERTEX * 4 // Bytes per vertex

    // Set color with red, green, blue and alpha (opacity) values
    var color = floatArrayOf(0.63671875f, 0.76953125f, 0.22265625f, 1.0f)

    // Added
    private val modelMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val modelViewProjectionMatrix = FloatArray(16)

    /**
     * Updates the object model matrix and applies scaling.
     *
     * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
     * @param scaleFactor A separate scaling factor to apply before the `modelMatrix`.
     * @see Matrix
     */
    fun updateModelMatrix(modelMatrix: FloatArray?, scaleFactor: Float) {
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        scaleMatrix[0] = scaleFactor
        scaleMatrix[5] = scaleFactor
        scaleMatrix[10] = scaleFactor
        Matrix.multiplyMM(this.modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0)
    }

    fun draw(
        cameraView: FloatArray?,
        cameraPerspective: FloatArray?
    ) {
        // colorCorrectionRgba: FloatArray?,
        // objColor: FloatArray? = ImageRenderer.DEFAULT_COLOR ) {

        ShaderUtil.checkGLError(ImageRenderer.TAG, "Before draw")
        // Build the ModelView and ModelViewProjection matrices for calculating object position and light.
        Matrix.multiplyMM(modelViewMatrix, 0, cameraView, 0, modelMatrix, 0)
        Matrix.multiplyMM(
            modelViewProjectionMatrix,
            0,
            cameraPerspective,
            0,
            modelViewMatrix,
            0
        )

        // Add program to OpenGL ES Environment
        GLES30.glUseProgram(shaderProgram)

        // Get handle to vertex shader's vPosition member
        mPositionHandle = GLES30.glGetAttribLocation(shaderProgram, "vPosition")

        // Enable a handle to the triangle vertices
        GLES30.glEnableVertexAttribArray(mPositionHandle)

        // Prepare the triangle coordinate data
        GLES30.glVertexAttribPointer(
            mPositionHandle, COORDS_PER_VERTEX,
            GLES30.GL_FLOAT, false,
            vertexStride, vertexBuffer
        )

        // Get Handle to Fragment Shader's vColor member
        mColorHandle = GLES30.glGetUniformLocation(shaderProgram, "vColor")

        // Set the Color for drawing the triangle
        GLES30.glUniform4fv(mColorHandle, 1, color, 0)

        // Diff Start
        // Set Texture Handles and bind Texture
        mTextureUniformHandle = GLES30.glGetAttribLocation(
            shaderProgram, "u_Texture"
        )
        mTextureCoordinateHandle = GLES30.glGetAttribLocation(
            shaderProgram, "a_TexCoordinate"
        )

        // Set the active texture unit to texture unit 0.
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        // Bind the texture to this unit.
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTextureDataHandle)

        // Tell the texture uniform sampler to use this texture
        // in the shader by binding to texture unit 0.
        GLES30.glUniform1i(mTextureUniformHandle, 0)

        // Pass in the texture coordinate information
        mCubeTextureCoordinates.position(0)
        GLES30.glVertexAttribPointer(
            mTextureCoordinateHandle, mTextureCoordinateDataSize,
            GLES30.GL_FLOAT, false, 0, mCubeTextureCoordinates
        )
        GLES30.glEnableVertexAttribArray(mTextureCoordinateHandle)
        // Diff End

        // Get Handle to Shape's Transformation Matrix
        mMVPMatrixHandle = GLES30.glGetUniformLocation(shaderProgram, "uMVPMatrix")
        ShaderUtil.checkGLError(ImageRenderer.TAG, "glGetUniformLocation")

        // Apply the projection and view transformation
        GLES30.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, modelViewProjectionMatrix, 0)
        mMVPMatrixHandle = GLES30.glGetUniformLocation(shaderProgram, "glUniformMatrix4fv")
        ShaderUtil.checkGLError(ImageRenderer.TAG, "glUniformMatrix4fv")

        // Draw the triangle
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES, drawOrder.size,
            GLES30.GL_UNSIGNED_SHORT, drawListBuffer
        )

        // Disable Vertex Array
        GLES30.glDisableVertexAttribArray(mPositionHandle)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun updateImage(bitmap: Bitmap) {
        if (mTextureDataHandle != 0) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, mTextureDataHandle)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            bitmap.recycle()

        } else {
            throw RuntimeException("Error loading texture: no texture handle")
        }
    }

    // zero point 는 anchoring 기준을 어디로 할 것인지에 대한 y 축값
    fun updateImageCoords(width: Float, height: Float, zeroPoint: Float) {
        // Update spriateCoords
        val halfWidth = width / 2
        var spriteCoords = floatArrayOf(
            -halfWidth, height - zeroPoint, // top left
            -halfWidth, -zeroPoint, // bottom left
            halfWidth, -zeroPoint, // bottom right
            halfWidth, height - zeroPoint // top right
        )

        vertexBuffer.put(spriteCoords)
        vertexBuffer.position(0)
    }

    companion object {
        private val TAG = ImageRenderer::class.java.simpleName

        // number of coordinates per vertex in this array
        val COORDS_PER_VERTEX = 2
        var spriteCoords = floatArrayOf(
            -0.5f, 0.5f, // top left
            -0.5f, -0.5f, // bottom left
            0.5f, -0.5f, // bottom right
            0.5f, 0.5f // top right
        )
        private val DEFAULT_COLOR = floatArrayOf(0f, 0f, 0f, 0f)

        fun loadShader(type: Int, shaderCode: String?): Int {
            // create a vertex shader type (GLES30.GL_VERTEX_SHADER)
            // or a fragment shader type (GLES30.GL_FRAGMENT_SHADER)
            val shader = GLES30.glCreateShader(type)

            // add the source code to the shader and compile it
            GLES30.glShaderSource(shader, shaderCode)
            GLES30.glCompileShader(shader)
            return shader
        }

        fun loadTexture(context: Context, resourceId: Int): Int {
            val textureHandle = IntArray(1)
            GLES30.glGenTextures(1, textureHandle, 0)
            if (textureHandle[0] != 0) {
                val options = BitmapFactory.Options()
                options.inScaled = false // no pre-scaling

                // Read in the resource
                val bitmap = BitmapFactory.decodeResource(
                    context.resources, resourceId, options
                )

                // Bind to the texture in OpenGL
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureHandle[0])

                // Set filtering
                // GL_TEXTURE_MIN_FILTER
                // — This tells OpenGL what type of filtering to apply
                // when drawing the texture smaller than the original size in pixels.

                // GL_TEXTURE_MAG_FILTER
                // — This tells OpenGL what type of filtering to apply
                // when magnifying the texture beyond its original size in pixels.
                GLES30.glTexParameteri(
                    GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_MIN_FILTER,
                    GLES30.GL_LINEAR
                )
                GLES30.glTexParameteri(
                    GLES30.GL_TEXTURE_2D,
                    GLES30.GL_TEXTURE_MAG_FILTER,
                    GLES30.GL_LINEAR
                )

                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)

                // Load the bitmap into the bound texture.
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)

                // Recycle the bitmap, since its data has been loaded into OpenGL.
                bitmap.recycle()
            }
            if (textureHandle[0] == 0) {
                throw RuntimeException("Error loading texture.")
            }
            return textureHandle[0]
        }
    }

    init {
        // Initialize Vertex Byte Buffer for Shape Coordinates / # of coordinate values * 4 bytes per float
        val bb = ByteBuffer.allocateDirect(spriteCoords.size * 4)
        // Use the Device's Native Byte Order
        bb.order(ByteOrder.nativeOrder())
        // Create a floating point buffer from the ByteBuffer
        vertexBuffer = bb.asFloatBuffer()
        // Add the coordinates to the FloatBuffer
        vertexBuffer.put(spriteCoords)
        // Set the Buffer to Read the first coordinate
        vertexBuffer.position(0)

        // S, T (or X, Y)
        // Texture coordinate data.
        // Because images have a Y axis pointing downward (values increase as you move down the image) while
        // OpenGL has a Y axis pointing upward, we adjust for that here by flipping the Y axis.
        // What's more is that the texture coordinates are the same for every face.
        val cubeTextureCoordinateData = floatArrayOf(
            0.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
            1.0f, 0.0f
        )
        mCubeTextureCoordinates = ByteBuffer.allocateDirect(
            cubeTextureCoordinateData.size * 4
        )
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        mCubeTextureCoordinates.put(cubeTextureCoordinateData).position(0)

        // Initialize byte buffer for the draw list
        val dlb = ByteBuffer.allocateDirect(spriteCoords.size * 2)
        dlb.order(ByteOrder.nativeOrder())
        drawListBuffer = dlb.asShortBuffer()
        drawListBuffer.put(drawOrder)
        drawListBuffer.position(0)

        val vertexShader: Int = loadShader(
            GLES30.GL_VERTEX_SHADER,
            vertexShaderCode
        )
        val fragmentShader: Int = loadShader(
            GLES30.GL_FRAGMENT_SHADER,
            fragmentShaderCode
        )
        shaderProgram = GLES30.glCreateProgram()
        GLES30.glAttachShader(shaderProgram, vertexShader)
        GLES30.glAttachShader(shaderProgram, fragmentShader)

        // Texture Code
        GLES30.glBindAttribLocation(shaderProgram, 0, "a_TexCoordinate")
        GLES30.glLinkProgram(shaderProgram)
    }
}
