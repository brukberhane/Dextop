package moe.n4tsu.dextop.xr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XrealAirProtocolTest {
    @Test fun startAndStopFrames() {
        assertEquals("AA C5 D1 21 42 04 00 19 01", hex(XrealAirProtocol.startStream()))
        assertEquals("AA 53 E1 26 35 04 00 19 00", hex(XrealAirProtocol.stopStream()))
    }

    @Test fun sensorReportParsesHeadFrame() {
        val frame = ByteArray(64)
        frame[0] = 0x01
        frame[1] = 0x02
        putI16(frame, 0x0C, 1)
        putI32(frame, 0x0E, 1)
        putI24(frame, 0x12, 10)
        putI24(frame, 0x15, 0)
        putI24(frame, 0x18, 0)
        putI16(frame, 0x1B, 1)
        putI32(frame, 0x1D, 1)
        putI24(frame, 0x21, 0)
        putI24(frame, 0x24, 0)
        putI24(frame, 0x27, 1)
        val sample = XrealAirProtocol.parseSample(frame, frame.size)
        assertNotNull(sample)
        assertClose(-10f, sample.gyroDegPerSec[0])
        assertClose(0f, sample.gyroDegPerSec[1])
        assertClose(0f, sample.gyroDegPerSec[2])
        assertClose(0f, sample.accelG[0])
        assertClose(1f, sample.accelG[1])
        assertClose(0f, sample.accelG[2])
    }

    @Test fun initAndEchoAreNotSamples() {
        val init = byteArrayOf(0xAA.toByte(), 0x53)
        val echo = byteArrayOf(0xAA.toByte(), 0x11)
        assertEquals(XrealReportKind.INIT, XrealAirProtocol.classify(init, init.size))
        assertEquals(XrealReportKind.ECHO, XrealAirProtocol.classify(echo, echo.size))
        assertNull(XrealAirProtocol.parseSample(init, init.size))
        assertNull(XrealAirProtocol.parseSample(echo, echo.size))
        assertNull(XrealAirProtocol.parseSample(byteArrayOf(0x01), 1))
    }

    @Test fun timestampUnitAndRejects() {
        val clock = XrealTimestampClock()
        assertNull(clock.dtSeconds(0))
        assertClose(0.001f, clock.dtSeconds(1_000_000)!!)
        assertEquals("ns", clock.unit)
        assertNull(clock.dtSeconds(1_000_000))
        assertNull(clock.dtSeconds(1_000_000 + 600_000_000))

        val micros = XrealTimestampClock()
        assertNull(micros.dtSeconds(0))
        assertClose(0.001f, micros.dtSeconds(1_000)!!)
        assertEquals("us", micros.unit)
    }

    private fun putI16(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun putI24(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
    }

    private fun putI32(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun hex(packet: ByteArray): String =
        packet.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(kotlin.math.abs(expected - actual) < 0.001f, "expected $expected actual $actual")
    }
}
