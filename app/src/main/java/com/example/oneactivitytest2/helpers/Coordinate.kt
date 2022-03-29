package com.example.oneactivitytest2.helpers

import android.opengl.Matrix
import javax.vecmath.Vector2f
import javax.vecmath.Vector3f

object CoordinateUtils {
    /**
     * @param touchPoint
     * @param screenWidth
     * @param screenHeight
     * @param projectionMatrix
     * @param viewMatrix
     * @return
     */
    fun GetWorldCoords(
        touchPoint: Vector2f,
        screenWidth: Float,
        screenHeight: Float,
        projectionMatrix: FloatArray?,
        viewMatrix: FloatArray?,
        distance: Float
    ): Vector3f {
        val touchRay: Ray =
            projectRay(touchPoint, screenWidth, screenHeight, projectionMatrix, viewMatrix)
        touchRay.direction.scale(distance) // Distance
        touchRay.origin.add(touchRay.direction) // Scale with Distance

        val biquadFilter = BiquadFilter(0.1f.toDouble())
        for (i in 0..1500) {
            biquadFilter.update(touchRay.origin)
        }

        return touchRay.origin
    }

    private fun projectRay(
        touchPoint: Vector2f,
        screenWidth: Float,
        screenHeight: Float,
        projectionMatrix: FloatArray?,
        viewMatrix: FloatArray?
    ): Ray {
        val viewProjMtx = FloatArray(16)
        Matrix.multiplyMM(viewProjMtx, 0, projectionMatrix, 0, viewMatrix, 0)
        return screenPointToRay(touchPoint, Vector2f(screenWidth, screenHeight), viewProjMtx)
    }

    private fun screenPointToRay(_point: Vector2f, viewportSize: Vector2f, viewProjMtx: FloatArray?): Ray {
        var point = Vector2f(_point)
        point.y = viewportSize.y - point.y
        val x: Float = point.x * 2.0f / viewportSize.x - 1.0f
        val y: Float = point.y * 2.0f / viewportSize.y - 1.0f
        val farScreenPoint = floatArrayOf(x, y, 1.0f, 1.0f)
        val nearScreenPoint = floatArrayOf(x, y, -1.0f, 1.0f)
        val farPlanePoint = FloatArray(4)
        val nearPlanePoint = FloatArray(4)
        val invertedProjectionMatrix = FloatArray(16)
        Matrix.setIdentityM(invertedProjectionMatrix, 0)
        Matrix.invertM(invertedProjectionMatrix, 0, viewProjMtx, 0)
        Matrix.multiplyMV(nearPlanePoint, 0, invertedProjectionMatrix, 0, nearScreenPoint, 0)
        Matrix.multiplyMV(farPlanePoint, 0, invertedProjectionMatrix, 0, farScreenPoint, 0)
        val direction = Vector3f(
            farPlanePoint[0] / farPlanePoint[3],
            farPlanePoint[1] / farPlanePoint[3],
            farPlanePoint[2] / farPlanePoint[3]
        )
        val origin = Vector3f(
            Vector3f(
                nearPlanePoint[0] / nearPlanePoint[3],
                nearPlanePoint[1] / nearPlanePoint[3],
                nearPlanePoint[2] / nearPlanePoint[3]
            )
        )
        direction.sub(origin)
        direction.normalize()
        return Ray(origin, direction)
    }
}

class Ray(val origin: Vector3f, val direction: Vector3f) {
}

class BiquadFilter(Fc: Double) {
    private val vector = Vector3f()
    private val inst = arrayOfNulls<BiquadFilterInstance>(3)

    fun update(v: Vector3f): Vector3f? {
        vector.x = inst[0]!!.process(v.x.toDouble()).toFloat()
        vector.y = inst[1]!!.process(v.y.toDouble()).toFloat()
        vector.z = inst[2]!!.process(v.z.toDouble()).toFloat()
        return vector
    }

    fun get(): Vector3f? {
        return Vector3f(vector.x, vector.y, vector.z)
    }

    private class BiquadFilterInstance internal constructor(fc: Double) {
        // https://en.wikipedia.org/wiki/Digital_biquad_filter
        var a0 = 0.0
        var a1 = 0.0
        var a2 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        var Fc = 0.5
        var Q = 0.707
        var peakGain = 0.0
        var z1 = 0.0
        var z2 = 0.0
        fun process(`in`: Double): Double {
            val out = `in` * a0 + z1
            z1 = `in` * a1 + z2 - b1 * out
            z2 = `in` * a2 - b2 * out
            return out
        }

        fun calcBiquad() {
            val norm: Double
            val K = Math.tan(Math.PI * Fc)
            norm = 1 / (1 + K / Q + K * K)
            a0 = K * K * norm
            a1 = 2 * a0
            a2 = a0
            b1 = 2 * (K * K - 1) * norm
            b2 = (1 - K / Q + K * K) * norm
        }

        init {
            Fc = fc
            calcBiquad()
        }
    }

    init {
        for (i in 0..2) {
            inst[i] = BiquadFilterInstance(Fc)
        }
    }
}
