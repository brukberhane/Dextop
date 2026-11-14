package moe.n4tsu.dextop.xr

import kotlin.math.PI

/**
 * Viture Pro Gen1 HID framing. Pure: no Android types.
 *
 * Commands use the decompiled layout that matches on-device Pro control
 * traffic: 64-byte buffer, length field `0x0C + payload`, no counter, no
 * `0x03` end marker, CRC-16/XMODEM (init 0) stored little-endian at offset 2.
 * The CRC window is `lengthField + 2` bytes starting at offset 4.
 * Incoming IMU reports may instead use CCITT-FALSE stored big-endian; both
 * are checked until one locks.
 */
internal object VitureProtocol {
    private val crcTable: IntArray = IntArray(256) { index ->
        var crc = index shl 8
        repeat(8) {
            crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
            crc = crc and 0xFFFF
        }
        crc
    }

    fun crc16(data: ByteArray, start: Int, length: Int, init: Int): Int {
        var crc = init and 0xFFFF
        for (i in start until start + length) {
            val value = data[i].toInt() and 0xFF
            crc = ((crc shl 8) xor crcTable[((crc shr 8) xor value) and 0xFF]) and 0xFFFF
        }
        return crc
    }

    fun ccitt(data: ByteArray, start: Int, length: Int): Int = crc16(data, start, length, 0xFFFF)

    fun xmodem(data: ByteArray, start: Int, length: Int): Int = crc16(data, start, length, 0x0000)

    enum class CrcKind { XMODEM_LE, CCITT_BE }

    fun enableImu(): ByteArray = buildCommand(0x15, byteArrayOf(0x01))

    fun disableImu(): ByteArray = buildCommand(0x15, byteArrayOf(0x00))

    /** WebHID-style enable, counter 1, padded to the 64-byte interrupt report. */
    fun enableImuShort(): ByteArray {
        val packet = ByteArray(64)
        val head = intArrayOf(
            0xFF, 0xFE, 0x97, 0x57, 0x0E, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x15, 0x00,
            0x01, 0x00, 0x01, 0x03,
        )
        head.forEachIndexed { index, value -> packet[index] = value.toByte() }
        return packet
    }

    fun logicalLength(buffer: ByteArray, length: Int): Int {
        if (length < 6) return -1
        val payload = (buffer[4].toInt() and 0xFF) or ((buffer[5].toInt() and 0xFF) shl 8)
        val logical = 6 + payload
        if (payload < 0 || logical > length) return -1
        return logical
    }

    fun crcMatches(buffer: ByteArray, length: Int, kind: CrcKind): Boolean {
        val logical = logicalLength(buffer, length)
        if (logical < 6) return false
        val lo = buffer[2].toInt() and 0xFF
        val hi = buffer[3].toInt() and 0xFF
        val stored = when (kind) {
            CrcKind.XMODEM_LE -> lo or (hi shl 8)
            CrcKind.CCITT_BE -> (lo shl 8) or hi
        }
        val computed = when (kind) {
            CrcKind.XMODEM_LE -> xmodem(buffer, 4, logical - 4)
            CrcKind.CCITT_BE -> ccitt(buffer, 4, logical - 4)
        }
        return computed == stored
    }

    fun isImu(buffer: ByteArray, length: Int): Boolean =
        length >= 2 && buffer[0] == 0xFF.toByte() && buffer[1] == 0xFC.toByte()

    fun isMcuAck(buffer: ByteArray, length: Int): Boolean =
        length >= 2 && buffer[0] == 0xFF.toByte() && buffer[1] == 0xFD.toByte()

    /** Big-endian degrees at offsets 18, 22, 26. Null when the buffer is not an IMU report. */
    fun readRawDegrees(buffer: ByteArray, length: Int): FloatArray? {
        if (!isImu(buffer, length) || length < 30) return null
        return floatArrayOf(floatBe(buffer, 18), floatBe(buffer, 22), floatBe(buffer, 26))
    }

    /**
     * yaw = -raw0, pitch = raw2, roll = -raw1, in radians.
     * raw0 is the float at offset 18.
     */
    fun axisRadians(raw0: Float, raw1: Float, raw2: Float): FloatArray {
        val degrees = (PI / 180.0).toFloat()
        return floatArrayOf(-raw0 * degrees, raw2 * degrees, -raw1 * degrees)
    }

    fun unwrapDegrees(previous: Float?, sample: Float): Float {
        if (previous == null) return sample
        var value = sample
        while (value - previous > 180f) value -= 360f
        while (previous - value > 180f) value += 360f
        return value
    }

    private fun buildCommand(cmdId: Int, data: ByteArray): ByteArray {
        val packet = ByteArray(64)
        packet[0] = 0xFF.toByte()
        packet[1] = 0xFE.toByte()
        val lengthField = 0x0C + data.size
        packet[4] = (lengthField and 0xFF).toByte()
        packet[5] = ((lengthField shr 8) and 0xFF).toByte()
        packet[14] = (cmdId and 0xFF).toByte()
        packet[15] = ((cmdId shr 8) and 0xFF).toByte()
        data.copyInto(packet, 18)
        val crc = xmodem(packet, 4, lengthField + 2)
        packet[2] = (crc and 0xFF).toByte()
        packet[3] = ((crc shr 8) and 0xFF).toByte()
        return packet
    }

    private fun floatBe(buffer: ByteArray, offset: Int): Float {
        val bits = ((buffer[offset].toInt() and 0xFF) shl 24) or
            ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
            ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
            (buffer[offset + 3].toInt() and 0xFF)
        return Float.fromBits(bits)
    }
}
