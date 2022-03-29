package com.example.oneactivitytest2

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.example.oneactivitytest2.helpers.CameraPermissionHelper
import com.example.oneactivitytest2.helpers.CoordinateUtils
import com.example.testviewpagerwithar.helpers.rotateBetween
import com.google.ar.core.*
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.our.shot.presentation.common.helpers.DisplayRotationHelper
import com.our.shot.presentation.common.helpers.TapHelper
import com.our.shot.presentation.common.rendering.BackgroundRenderer
import com.our.shot.presentation.common.rendering.ImageRenderer
import java.io.IOException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import javax.vecmath.Vector2f
import javax.vecmath.Vector3f

class MainActivity : AppCompatActivity(), GLSurfaceView.Renderer {
    private val backgroundRenderer = BackgroundRenderer()
    private lateinit var customImageRenderer: ImageRenderer
    private var screenWidth = 0.0f
    private var screenHeight = 0.0f
    private lateinit var displayRotationHelper: DisplayRotationHelper
    private lateinit var tapHelper: TapHelper

    private var scaleFactor: Float = 1.0f
    private var anchorPoint: Vector2f = Vector2f(0f, 0f)

    private lateinit var sv: GLSurfaceView
    private var arSession: Session? = null
    private var isARSessionAlive = false
    private var estimatedDistance: Float = 3.0f
    private var customImageAnchor: Anchor? = null
    private val anchorMatrix = FloatArray(16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this)
            return
        }

        initHelper()

        sv = findViewById<GLSurfaceView>(R.id.activity_surface_view)
        sv.apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(this@MainActivity)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setOnTouchListener(tapHelper)
        }
    }

    override fun onResume() {
        super.onResume()

        if (arSession == null) {
            arSession = Session(this)
            if (!setupSession()) {
                return
            }
        }

        try {
            arSession?.resume()
            // auto focus bug in ARCore
            // https://github.com/google-ar/arcore-android-sdk/issues/1300
            // workaround : resume arcore twice
            arSession?.pause()
            arSession?.resume()
        } catch (e: CameraNotAvailableException) {
            arSession = null
            return
        }

        isARSessionAlive = true
        sv.onResume()
        displayRotationHelper.onResume()
    }

    override fun onPause() {
        if (arSession != null) {
            isARSessionAlive = false
            displayRotationHelper.onPause()
            sv.onPause()
            arSession?.pause()
        }

        super.onPause()
    }

    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {
        GLES20.glClearColor(
            0f,
            0f,
            0f,
            1.0f
        )
        createRenderer()
    }

    override fun onSurfaceChanged(p0: GL10?, width: Int, height: Int) {
        GLES20.glViewport(
            0,
            0,
            width,
            height
        )
        displayRotationHelper.onSurfaceChanged(
            width,
            height
        )

        // called when start up
        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
    }

    override fun onDrawFrame(p0: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        displayRotationHelper.updateSessionIfNeeded(
            arSession
        )

        if (arSession == null || !isARSessionAlive) {
            return
        }

        arSession?.setCameraTextureName(backgroundRenderer.textureId)

        // Perform ARCore per-frame update.
        val frame = arSession?.update() ?: return
        val camera = frame.camera

        // Handle screen tap.
        handleTap(camera)

        // If frame is ready, render camera preview image to the GL surface.
        backgroundRenderer.draw(frame)

        // If not tracking, don't draw 3D objects.
        if (camera.trackingState == TrackingState.PAUSED) {
            return
        }

        // Get projection matrix.
        val projectionMatrix = computeProjectionMatrix(camera)
        val viewMatrix = computeViewMatrix(camera)

        if (customImageAnchor?.trackingState == TrackingState.TRACKING) {
            customImageAnchor?.let {
                it.pose.toMatrix(anchorMatrix, 0)

                customImageRenderer.updateModelMatrix(anchorMatrix, scaleFactor)
                customImageRenderer.draw(viewMatrix, projectionMatrix)
            }
        }
    }

    private fun createRenderer() {
        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the camera preview image texture. Used in non-AR and AR mode.
            backgroundRenderer.createOnGlThread(
                this
            )
            customImageRenderer = ImageRenderer(this)
        } catch (e: IOException) {
        }
    }

    private fun initHelper() {
        displayRotationHelper = DisplayRotationHelper(this)
        tapHelper = TapHelper(
            this,
            // update scale func
            { scale ->
                scaleFactor *= scale
            },
            // update coords func
            { dx, dy ->
                anchorPoint.x -= dx
                anchorPoint.y -= dy
            }
        )
    }

    private fun setupSession(): Boolean {
        try {
            arSession?.let { session ->
                val config = Config(session).apply {
                    focusMode = Config.FocusMode.AUTO
                    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        depthMode = Config.DepthMode.AUTOMATIC
                    }
                }
                session.configure(config)
            }
        } catch (exception: Exception) {
            return false
        }
        return true
    }

    private fun handleTap(camera: Camera) {
        val tap = tapHelper.poll()
        if (tap != null &&
            camera.trackingState == TrackingState.TRACKING
        ) {
            val projectionMatrix = computeProjectionMatrix(camera)
            val viewMatrix = computeViewMatrix(camera)

            anchorPoint.x = tap.x
            anchorPoint.y = tap.y

            val point: Vector3f = CoordinateUtils.GetWorldCoords(
                anchorPoint, screenWidth, screenHeight,
                projectionMatrix, viewMatrix, estimatedDistance
            )

            customImageAnchor?.detach()
            customImageAnchor = arSession?.createAnchor(
                Pose.makeTranslation(point.getX(), point.getY(), point.getZ())
            )
        }
    }

    private val MAX_ALLOCATION_SIZE = 16
    private fun computeProjectionMatrix(camera: Camera): FloatArray {
        val projectionMatrix = FloatArray(MAX_ALLOCATION_SIZE)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)

        return projectionMatrix
    }

    fun computeViewMatrix(camera: Camera): FloatArray {
        val viewMatrix = FloatArray(MAX_ALLOCATION_SIZE)
        camera.getViewMatrix(viewMatrix, 0)
        return viewMatrix
    }
}