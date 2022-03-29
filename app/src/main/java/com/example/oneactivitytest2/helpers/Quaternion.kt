package com.example.testviewpagerwithar.helpers

import com.google.ar.core.Pose
import kotlin.math.*

// refer to
// https://android.googlesource.com/platform/external/jmonkeyengine/+/
// 59b2e6871c65f58fdad78cd7229c292f6a177578/engine/src/core/com/jme3/math/Quaternion.java
fun multipleQuaternion(q1: FloatArray, q2: FloatArray): FloatArray {
    val ret = FloatArray(4)

    // x = q1[0], y = q1[1], z = q1[2], w = q1[3]
    // qx = q2[0], qy = q2[1], qz = q2[2], qw = q2[3]

    // res.x = x * qw + y * qz - z * qy + w * qx;
    ret[0] = q1[0] * q2[3] + q1[1] * q2[2] - q1[2] * q2[1] + q1[3] * q2[0]
    // res.y = -x * qz + y * qw + z * qx + w * qy;
    ret[1] = -q1[0] * q2[2] + q1[1] * q2[3] + q1[2] * q2[0] + q1[3] * q2[1]
    // res.z = x * qy - y * qx + z * qw + w * qz
    ret[2] = q1[0] * q2[1] - q1[1] * q2[0] + q1[2] * q2[3] + q1[3] * q2[2]
    // res.w = -x * qx - y * qy - z * qz + w * qw
    ret[3] = -q1[0] * q2[0] - q1[1] * q2[1] - q1[2] * q2[2] + q1[3] * q2[3]

    return ret
}

// angle ordering : pitch, yaw, roll
fun toAngles(quaternion: FloatArray): FloatArray {
    require(quaternion.size == 4) { "Quaternion array must have four elements" }

    val angles = FloatArray(4)
    val x = quaternion[0]
    val y = quaternion[1]
    val z = quaternion[2]
    val w = quaternion[3]

    val sqw = w * w
    val sqx = x * x
    val sqy = y * y
    val sqz = z * z
    val unit = sqx + sqy + sqz + sqw // if normalized is one, otherwise
    // is correction factor
    val test: Float = x * y + z * w
    if (test > 0.499 * unit) { // singularity at north pole
        angles[1] = 2 * atan2(x, w)
        angles[2] = ((PI / 2.0f).toFloat())
        angles[0] = 0f
    } else if (test < -0.499 * unit) { // singularity at south pole
        angles[1] = -2 * atan2(x, w)
        angles[2] = (-(PI / 2.0f)).toFloat()
        angles[0] = 0F
    } else {
        angles[1] =
            atan2(2 * y * w - 2 * x * z, sqx - sqy - sqz + sqw) // roll or heading
        angles[2] = asin(2 * test / unit) // pitch or attitude
        angles[0] = atan2(2 * x * w - 2 * y * z, -sqx + sqy - sqz + sqw) // yaw or bank
    }
    return angles
}

// real ordering with sensor value : pitch(x), yaw(y), roll(z)
fun toQuaternion(yaw: Float, roll: Float, pitch: Float): FloatArray {
    val quaternion = FloatArray(4)

    var angle: Float = pitch * 0.5f
    val sinPitch: Float = sin(angle)
    val cosPitch: Float = cos(angle)

    angle = roll * 0.5f
    val sinRoll: Float = sin(angle)
    val cosRoll: Float = cos(angle)

    angle = yaw * 0.5f
    val sinYaw: Float = sin(angle)
    val cosYaw: Float = cos(angle)

    // variables used to reduce multiplication calls.
    val cosRollXcosPitch = cosRoll * cosPitch
    val sinRollXsinPitch = sinRoll * sinPitch
    val cosRollXsinPitch = cosRoll * sinPitch
    val sinRollXcosPitch = sinRoll * cosPitch

    quaternion[3] = cosRollXcosPitch * cosYaw - sinRollXsinPitch * sinYaw
    quaternion[0] = cosRollXcosPitch * sinYaw + sinRollXsinPitch * cosYaw
    quaternion[1] = sinRollXcosPitch * cosYaw + cosRollXsinPitch * sinYaw
    quaternion[2] = cosRollXsinPitch * cosYaw - sinRollXcosPitch * sinYaw

    normalizeQuaternion(quaternion)

    return quaternion
}

fun invSqrt(_x: Float): Float {
    var x = _x
    val xhalf = 0.5f * x
    var i = java.lang.Float.floatToIntBits(x)
    i = 0x5f3759df - (i shr 1)
    x = java.lang.Float.intBitsToFloat(i)
    x *= 1.5f - xhalf * x * x

    return x
}

private fun normalizeQuaternion(q: FloatArray) {
    val norm = q[3] * q[3] + q[0] * q[0] + q[1] * q[1] + q[2] * q[2]
    val n = invSqrt(norm)

    q[0] *= n
    q[1] *= n
    q[2] *= n
    q[3] *= n
}
/** Returns the 2-norm of the input array.  */
fun norm(floats: FloatArray): Float {
    var sum = 0f
    for (float in floats) {
        sum += float * float
    }
    return sqrt(sum.toDouble()).toFloat()
}

/** Normalizes the input array in-place. */
fun normalize(floats: FloatArray) {
    val scale: Float = 1 / norm(floats)
    for (i in floats.indices) {
        floats[i] *= scale
    }
}

fun rotateBetween(fromRaw: FloatArray, toRaw: FloatArray): Pose {
    val from = fromRaw.copyOf(3)
    normalize(from)
    val to = toRaw.copyOf(3)
    normalize(to)
    val cross = FloatArray(3)
    cross[0] = from[1] * to[2] - from[2] * to[1]
    cross[1] = from[2] * to[0] - from[0] * to[2]
    cross[2] = from[0] * to[1] - from[1] * to[0]
    val dot = from[0] * to[0] + from[1] * to[1] + from[2] * to[2]
    val angle = atan2(norm(cross).toDouble(), dot.toDouble()).toFloat()
    normalize(cross)
    val sinHalf = sin((angle / 2.0f).toDouble()).toFloat()
    val cosHalf = cos((angle / 2.0f).toDouble()).toFloat()
    return Pose.makeRotation(
        cross[0] * sinHalf,
        cross[1] * sinHalf,
        cross[2] * sinHalf,
        cosHalf
    )
}
