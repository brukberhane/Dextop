package moe.n4tsu.dextop.xr

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Gyro integration plus a small accelerometer correction on pitch and roll.
 * Yaw is gyro only. A stationary stretch folds the mean gyro into a bias so
 * a resting head does not crawl.
 */
internal class XrOrientationFusion(
    private val gain: Float = 0.1f,
) {
    private var w = 1f
    private var x = 0f
    private var y = 0f
    private var z = 0f
    private var biasX = 0f
    private var biasY = 0f
    private var biasZ = 0f
    private var stillSeconds = 0f
    private var previous = FloatArray(3) { Float.NaN }

    fun step(
        gyroXDeg: Float,
        gyroYDeg: Float,
        gyroZDeg: Float,
        accelX: Float,
        accelY: Float,
        accelZ: Float,
        dt: Float,
    ): FloatArray {
        if (dt <= 0f) return euler()
        adaptBias(gyroXDeg, gyroYDeg, gyroZDeg, dt)
        val deg = (PI / 180.0).toFloat()
        var wx = (gyroXDeg - biasX) * deg
        var wy = (gyroYDeg - biasY) * deg
        var wz = (gyroZDeg - biasZ) * deg
        val accel = correct(accelX, accelY, accelZ)
        wx += accel[0]
        wy += accel[1]
        wz += accel[2]
        integrate(wx, wy, wz, dt)
        val raw = euler()
        val unwrapped = FloatArray(3)
        for (index in 0..2) {
            val prior = previous[index]
            unwrapped[index] = XrealAirProtocol.unwrapRadians(if (prior.isNaN()) null else prior, raw[index])
            previous[index] = unwrapped[index]
        }
        return unwrapped
    }

    private fun adaptBias(gyroX: Float, gyroY: Float, gyroZ: Float, dt: Float) {
        val magnitude = sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ)
        if (magnitude >= 3f) {
            stillSeconds = 0f
            return
        }
        stillSeconds += dt
        if (stillSeconds < 0.5f) return
        val alpha = (dt / 1f).coerceAtMost(1f)
        biasX += alpha * (gyroX - biasX)
        biasY += alpha * (gyroY - biasY)
        biasZ += alpha * (gyroZ - biasZ)
    }

    /** Extra body rate that pulls pitch and roll toward the measured gravity. */
    private fun correct(accelX: Float, accelY: Float, accelZ: Float): FloatArray {
        val magnitude = sqrt(accelX * accelX + accelY * accelY + accelZ * accelZ)
        if (magnitude < 0.5f || magnitude > 1.5f) return floatArrayOf(0f, 0f, 0f)
        val ax = accelX / magnitude
        val ay = accelY / magnitude
        val az = accelZ / magnitude
        val predicted = bodyUp()
        val ex = ay * predicted[2] - az * predicted[1]
        val ey = az * predicted[0] - ax * predicted[2]
        val ez = ax * predicted[1] - ay * predicted[0]
        return floatArrayOf(gain * ex, gain * ey, gain * ez)
    }

    /** World up (0, 1, 0) expressed in the body frame. */
    private fun bodyUp(): FloatArray = floatArrayOf(
        2f * (x * y + z * w),
        w * w + y * y - x * x - z * z,
        2f * (y * z - w * x),
    )

    private fun integrate(wx: Float, wy: Float, wz: Float, dt: Float) {
        val half = 0.5f * dt
        val dw = 1f
        val dx = wx * half
        val dy = wy * half
        val dz = wz * half
        val nw = w * dw - x * dx - y * dy - z * dz
        val nx = w * dx + x * dw + y * dz - z * dy
        val ny = w * dy - x * dz + y * dw + z * dx
        val nz = w * dz + x * dy - y * dx + z * dw
        val norm = sqrt(nw * nw + nx * nx + ny * ny + nz * nz)
        if (norm == 0f) return
        w = nw / norm
        x = nx / norm
        y = ny / norm
        z = nz / norm
    }

    private fun euler(): FloatArray {
        val yaw = atan2(2f * (w * y + x * z), 1f - 2f * (y * y + x * x))
        val pitchSin = (2f * (w * x - y * z)).coerceIn(-1f, 1f)
        val pitch = asin(pitchSin)
        val roll = atan2(2f * (w * z + x * y), 1f - 2f * (x * x + z * z))
        return floatArrayOf(yaw, pitch, roll)
    }
}
