package moe.n4tsu.dextop.xr

import java.util.zip.CRC32
import kotlin.math.PI

/**
 * XREAL Air / Air 2 / Air 2 Pro HID IMU framing. Pure: no Android types.
 * Command CRC is zlib CRC-32 over bytes from offset 5. Sensor reports are
 * raw gyro (deg/s) and accel (g). Mag and the unread checksum are ignored.
 */
internal object XrealAirProtocol {
    const val IMU_INTERFACE = 3
    val KNOWN_PIDS = intArrayOf(0x0424, 0x0428, 0x0432)

    /** Head frame from the sensor body: (−x, z, y). Sensor x is left, y back, z up. */
    private val HEAD_FROM_BODY = intArrayOf(0, 2, 1)
    private val HEAD_SIGN = floatArrayOf(-1f, 1f, 1f)

    fun startStream(): ByteArray = command(0x19, byteArrayOf(0x01))

    fun stopStream(): ByteArray = command(0x19, byteArrayOf(0x00))

    fun command(msgid: Int, data: ByteArray): ByteArray {
        val packet = ByteArray(8 + data.size)
        packet[0] = 0xAA.toByte()
        val length = 3 + data.size
        packet[5] = (length and 0xFF).toByte()
        packet[6] = ((length shr 8) and 0xFF).toByte()
        packet[7] = (msgid and 0xFF).toByte()
        data.copyInto(packet, 8)
        val crc = CRC32()
        crc.update(packet, 5, packet.size - 5)
        val value = crc.value.toInt()
        packet[1] = (value and 0xFF).toByte()
        packet[2] = ((value shr 8) and 0xFF).toByte()
        packet[3] = ((value shr 16) and 0xFF).toByte()
        packet[4] = ((value shr 24) and 0xFF).toByte()
        return packet
    }

    fun classify(buffer: ByteArray, length: Int): XrealReportKind {
        if (length < 2) return XrealReportKind.UNKNOWN
        val head = buffer[0].toInt() and 0xFF
        val second = buffer[1].toInt() and 0xFF
        return when {
            head == 0x01 && second == 0x02 -> XrealReportKind.SENSOR
            head == 0xAA && second == 0x53 -> XrealReportKind.INIT
            head == 0xAA -> XrealReportKind.ECHO
            else -> XrealReportKind.UNKNOWN
        }
    }

    /** Gyro deg/s and accel g, already in the head frame. Null when the buffer is not a sensor report. */
    fun parseSample(buffer: ByteArray, length: Int): XrealSample? {
        if (classify(buffer, length) != XrealReportKind.SENSOR || length < 0x2A) return null
        val gyro = head(scaled(buffer, 0x0C, 0x0E, intArrayOf(0x12, 0x15, 0x18)))
        val accel = head(scaled(buffer, 0x1B, 0x1D, intArrayOf(0x21, 0x24, 0x27)))
        return XrealSample(u64le(buffer, 0x04), gyro, accel)
    }

    fun unwrapRadians(previous: Float?, sample: Float): Float {
        if (previous == null) return sample
        var value = sample
        val turn = (2.0 * PI).toFloat()
        while (value - previous > PI.toFloat()) value -= turn
        while (previous - value > PI.toFloat()) value += turn
        return value
    }

    private fun scaled(buffer: ByteArray, multAt: Int, divAt: Int, axes: IntArray): FloatArray {
        val multiplier = i16le(buffer, multAt).toFloat()
        val divisor = i32le(buffer, divAt).toFloat()
        val scale = if (divisor == 0f) 0f else multiplier / divisor
        return FloatArray(3) { index -> i24le(buffer, axes[index]) * scale }
    }

    private fun head(body: FloatArray): FloatArray = FloatArray(3) { index ->
        HEAD_SIGN[index] * body[HEAD_FROM_BODY[index]]
    }

    private fun i16le(buffer: ByteArray, offset: Int): Int {
        val value = (buffer[offset].toInt() and 0xFF) or ((buffer[offset + 1].toInt() and 0xFF) shl 8)
        return if (value and 0x8000 != 0) value or -0x10000 else value
    }

    private fun i32le(buffer: ByteArray, offset: Int): Int =
        (buffer[offset].toInt() and 0xFF) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 3].toInt() and 0xFF) shl 24)

    private fun i24le(buffer: ByteArray, offset: Int): Int {
        val value = (buffer[offset].toInt() and 0xFF) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 16)
        return if (value and 0x800000 != 0) value or -0x1000000 else value
    }

    private fun u64le(buffer: ByteArray, offset: Int): Long {
        var value = 0L
        for (index in 0..7) value = value or ((buffer[offset + index].toLong() and 0xFF) shl (8 * index))
        return value
    }
}

internal enum class XrealReportKind { SENSOR, INIT, ECHO, UNKNOWN }

internal data class XrealSample(
    val timestamp: Long,
    val gyroDegPerSec: FloatArray,
    val accelG: FloatArray,
)

/**
 * First sane delta picks nanoseconds or microseconds. Later deltas that are
 * not a positive interval under half a second are dropped. A backwards jump
 * that fits a 24-bit counter is treated as rollover.
 */
internal class XrealTimestampClock {
    var unit: String = ""
        private set
    private var scaleToNanos = 0L
    private var previous: Long? = null

    fun dtSeconds(raw: Long): Float? {
        val prior = previous
        previous = raw
        if (prior == null) return null
        var delta = raw - prior
        if (delta < 0) {
            val wrapped = delta + (1L shl 24)
            if (wrapped <= 0) return null
            delta = wrapped
        }
        if (scaleToNanos == 0L) {
            if (delta < 50_000L) {
                scaleToNanos = 1_000L
                unit = "us"
            } else {
                scaleToNanos = 1L
                unit = "ns"
            }
        }
        val seconds = delta.toDouble() * scaleToNanos.toDouble() / 1_000_000_000.0
        if (seconds <= 0.0 || seconds >= 0.5) return null
        return seconds.toFloat()
    }
}
