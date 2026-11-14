package moe.n4tsu.dextop.xr

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VitureProtocolTest {
    @Test fun enablePacketFixture() {
        val packet = VitureProtocol.enableImu()
        assertEquals(64, packet.size)
        assertEquals(
            "FF FE 27 25 0D 00 00 00 00 00 00 00 00 00 15 00 00 00 01",
            hex(packet.copyOf(19)),
        )
        assertTrue(packet.copyOfRange(19, packet.size).all { it == 0.toByte() })
        assertTrue(VitureProtocol.crcMatches(packet, packet.size, VitureProtocol.CrcKind.XMODEM_LE))
    }

    @Test fun enableCommandIsImuOn() {
        val packet = VitureProtocol.enableImu()
        assertEquals(0x15, packet[14].toInt() and 0xFF)
        assertEquals(0x00, packet[15].toInt() and 0xFF)
        assertEquals(0x00, packet[16].toInt() and 0xFF)
    }

    @Test fun xmodemWindowIs2527() {
        val packet = VitureProtocol.enableImu()
        packet[2] = 0
        packet[3] = 0
        assertEquals(0x2527, VitureProtocol.xmodem(packet, 4, 0x0D + 2))
    }

    @Test fun axisMap() {
        val mapped = VitureProtocol.axisRadians(90f, 0f, 0f)
        assertClose((-PI / 2.0).toFloat(), mapped[0])
        assertClose(0f, mapped[1])
        assertClose(0f, mapped[2])
    }

    @Test fun unwrapContinuesPastTheBranchCut() {
        assertClose(190f, VitureProtocol.unwrapDegrees(170f, -170f))
    }

    @Test fun imuDegreesAndCrc() {
        val frame = imuFrame(90f, 0f, 0f)
        val raw = VitureProtocol.readRawDegrees(frame, frame.size)
        assertNotNull(raw)
        assertClose(90f, raw[0])
        assertClose(0f, raw[1])
        assertClose(0f, raw[2])
        assertTrue(VitureProtocol.crcMatches(frame, frame.size, VitureProtocol.CrcKind.XMODEM_LE))
        frame[18] = (frame[18].toInt() xor 0xFF).toByte()
        assertFalse(VitureProtocol.crcMatches(frame, frame.size, VitureProtocol.CrcKind.XMODEM_LE))
        assertNull(VitureProtocol.readRawDegrees(byteArrayOf(0xFF.toByte(), 0xFE.toByte()), 2))
    }

    private fun imuFrame(raw0: Float, raw1: Float, raw2: Float): ByteArray {
        val packet = ByteArray(32)
        packet[0] = 0xFF.toByte()
        packet[1] = 0xFC.toByte()
        val payloadLen = packet.size - 6
        packet[4] = (payloadLen and 0xFF).toByte()
        packet[5] = ((payloadLen shr 8) and 0xFF).toByte()
        putFloatBe(packet, 18, raw0)
        putFloatBe(packet, 22, raw1)
        putFloatBe(packet, 26, raw2)
        val crc = VitureProtocol.xmodem(packet, 4, packet.size - 4)
        packet[2] = (crc and 0xFF).toByte()
        packet[3] = ((crc shr 8) and 0xFF).toByte()
        return packet
    }

    private fun putFloatBe(buffer: ByteArray, offset: Int, value: Float) {
        val bits = value.toBits()
        buffer[offset] = ((bits shr 24) and 0xFF).toByte()
        buffer[offset + 1] = ((bits shr 16) and 0xFF).toByte()
        buffer[offset + 2] = ((bits shr 8) and 0xFF).toByte()
        buffer[offset + 3] = (bits and 0xFF).toByte()
    }

    private fun hex(packet: ByteArray): String =
        packet.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(kotlin.math.abs(expected - actual) < 0.001f, "expected $expected actual $actual")
    }
}
