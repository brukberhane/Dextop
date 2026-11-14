package moe.n4tsu.dextop.xr

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.util.Log
import java.nio.ByteBuffer

internal class VitureHidPoseProvider(
    override val id: String,
    override val displayName: String,
) : AbstractXrPoseProvider() {
    @Volatile private var running = false
    private var reader: Thread? = null
    private var connection: UsbDeviceConnection? = null
    private var imuInterface: UsbInterface? = null
    private var mcuInterface: UsbInterface? = null
    private var mcuOut: UsbEndpoint? = null
    private var requests = emptyList<UsbRequest>()

    fun open(context: Context, device: UsbDevice) {
        running = true
        publishAvailability(XrAvailability.CONNECTING)
        reader = Thread({
            runCatching { read(context.applicationContext, device) }
                .onFailure { error ->
                    Log.w(TAG, "reader failed", error)
                    publishAvailability(XrAvailability.ERROR)
                }
            if (running) {
                running = false
                releaseUsb()
                VitureHidSession.abandon(this)
            }
        }, "DextopViture").also { it.start() }
    }

    fun close() {
        running = false
        requests.forEach { runCatching { it.cancel() } }
        reader?.join(1500)
        reader = null
        releaseUsb()
    }

    private fun releaseUsb() {
        val current: UsbDeviceConnection?
        val out: UsbEndpoint?
        val imu: UsbInterface?
        val mcu: UsbInterface?
        val pending: List<UsbRequest>
        synchronized(this) {
            if (connection == null && requests.isEmpty()) return
            current = connection
            out = mcuOut
            imu = imuInterface
            mcu = mcuInterface
            pending = requests
            connection = null
            imuInterface = null
            mcuInterface = null
            mcuOut = null
            requests = emptyList()
        }
        if (current != null && out != null) {
            val packet = VitureProtocol.disableImu()
            runCatching { current.bulkTransfer(out, packet, packet.size, 200) }
        }
        if (imu != null) runCatching { current?.releaseInterface(imu) }
        if (mcu != null) runCatching { current?.releaseInterface(mcu) }
        runCatching { current?.close() }
        pending.forEach { runCatching { it.close() } }
    }

    private fun read(context: Context, device: UsbDevice) {
        val usb = context.getSystemService(UsbManager::class.java)
        if (usb == null || !usb.hasPermission(device)) {
            publishAvailability(XrAvailability.UNAVAILABLE)
            return
        }
        val opened = usb.openDevice(device)
        if (opened == null || device.interfaceCount < 2) {
            publishAvailability(XrAvailability.ERROR)
            opened?.close()
            return
        }
        connection = opened
        val imu = device.getInterface(0)
        val mcu = device.getInterface(1)
        imuInterface = imu
        mcuInterface = mcu
        if (!opened.claimInterface(imu, true) || !opened.claimInterface(mcu, true)) {
            Log.w(TAG, "claim failed imu=${imu.id} mcu=${mcu.id}")
            publishAvailability(XrAvailability.ERROR)
            releaseUsb()
            return
        }
        val imuIn = endpoint(imu, UsbConstants.USB_DIR_IN)
        val mcuIn = endpoint(mcu, UsbConstants.USB_DIR_IN)
        val out = endpoint(mcu, UsbConstants.USB_DIR_OUT)
        mcuOut = out
        if (imuIn == null || mcuIn == null || out == null) {
            Log.w(TAG, "missing interrupt endpoint")
            publishAvailability(XrAvailability.ERROR)
            releaseUsb()
            return
        }
        val imuReq = UsbRequest()
        val mcuReq = UsbRequest()
        if (!imuReq.initialize(opened, imuIn) || !mcuReq.initialize(opened, mcuIn)) {
            runCatching { imuReq.close() }
            runCatching { mcuReq.close() }
            publishAvailability(XrAvailability.ERROR)
            releaseUsb()
            return
        }
        requests = listOf(imuReq, mcuReq)
        val imuBuf = ByteBuffer.allocateDirect(64)
        val mcuBuf = ByteBuffer.allocateDirect(64)
        if (!imuReq.queue(imuBuf) || !mcuReq.queue(mcuBuf)) {
            publishAvailability(XrAvailability.ERROR)
            releaseUsb()
            return
        }
        writeEnable(opened, out, VitureProtocol.enableImu(), "xmodem")
        val openedAt = System.nanoTime()
        var retriedEnable = false
        var sawImu = false
        var crcKind: VitureProtocol.CrcKind? = null
        var loggedAck = false
        var previous = floatArrayOf(Float.NaN, Float.NaN, Float.NaN)
        var poseCount = 0
        var streamStart = 0L
        var loggedRate = false
        var stallResent = false
        var lastPacket = openedAt
        var lastPoseLog = 0L
        var loggedDrop = false
        var loggedOther = false
        var loggedWait = false
        while (running) {
            val got = try {
                opened.requestWait(1000L)
            } catch (_: java.util.concurrent.TimeoutException) {
                null
            }
            if (!sawImu && !retriedEnable && System.nanoTime() - openedAt >= 1_000_000_000L) {
                retriedEnable = true
                writeEnable(opened, out, VitureProtocol.enableImuShort(), "short")
            }
            if (got == null) {
                if (!loggedWait) {
                    loggedWait = true
                    Log.i(TAG, "no report yet")
                }
                val quiet = System.nanoTime() - lastPacket >= 2_000_000_000L
                if (sawImu && quiet && !stallResent) {
                    stallResent = true
                    writeEnable(opened, out, VitureProtocol.enableImu(), "xmodem")
                }
                continue
            }
            val buf = if (got == imuReq) imuBuf else mcuBuf
            val size = when {
                buf.position() > 0 -> buf.position()
                buf.limit() in 1 until buf.capacity() -> buf.limit()
                else -> 0
            }
            val bytes = ByteArray(size.coerceAtLeast(0))
            if (size > 0) {
                buf.position(0)
                buf.get(bytes, 0, size)
            }
            buf.clear()
            if (!got.queue(buf)) break
            if (size < 2) continue
            if (VitureProtocol.isMcuAck(bytes, size) && !loggedAck) {
                loggedAck = true
                Log.i(TAG, "mcu ack ${hex(bytes)}")
            }
            if (!VitureProtocol.isImu(bytes, size)) {
                if (!loggedOther && !VitureProtocol.isMcuAck(bytes, size)) {
                    loggedOther = true
                    Log.i(TAG, "other size=$size ${hex(bytes)}")
                }
                continue
            }
            val xmodem = VitureProtocol.crcMatches(bytes, size, VitureProtocol.CrcKind.XMODEM_LE)
            val ccitt = VitureProtocol.crcMatches(bytes, size, VitureProtocol.CrcKind.CCITT_BE)
            val kind = crcKind
            val accept = when {
                kind != null -> if (VitureProtocol.crcMatches(bytes, size, kind)) kind else null
                xmodem -> VitureProtocol.CrcKind.XMODEM_LE.also {
                    crcKind = it
                    Log.i(TAG, "crc=xmodem")
                }
                ccitt -> VitureProtocol.CrcKind.CCITT_BE.also {
                    crcKind = it
                    Log.i(TAG, "crc=ccitt")
                }
                else -> null
            }
            if (accept == null) {
                if (!loggedDrop) {
                    loggedDrop = true
                    Log.i(TAG, "drop size=$size ${hex(bytes)}")
                }
                continue
            }
            val degrees = VitureProtocol.readRawDegrees(bytes, size) ?: continue
            sawImu = true
            lastPacket = System.nanoTime()
            stallResent = false
            val unwrapped = FloatArray(3)
            for (index in 0..2) {
                val prior = previous[index]
                unwrapped[index] = VitureProtocol.unwrapDegrees(if (prior.isNaN()) null else prior, degrees[index])
                previous[index] = unwrapped[index]
            }
            val mapped = VitureProtocol.axisRadians(unwrapped[0], unwrapped[1], unwrapped[2])
            publishRaw(mapped[0], mapped[1], mapped[2], System.nanoTime())
            if (System.nanoTime() - lastPoseLog >= 1_000_000_000L) {
                lastPoseLog = System.nanoTime()
                Log.i(TAG, "pose yaw=${mapped[0]} pitch=${mapped[1]} roll=${mapped[2]}")
            }
            poseCount += 1
            if (availability != XrAvailability.STREAMING) {
                publishAvailability(XrAvailability.STREAMING)
                streamStart = System.nanoTime()
            }
            if (!loggedRate && streamStart != 0L && System.nanoTime() - streamStart >= 5_000_000_000L) {
                loggedRate = true
                Log.i(TAG, "pose-rate=${"%.1f".format(poseCount / 5.0)}Hz")
            }
        }
    }

    private fun writeEnable(connection: UsbDeviceConnection, out: UsbEndpoint, packet: ByteArray, label: String) {
        val wrote = connection.bulkTransfer(out, packet, packet.size, 200)
        Log.i(TAG, "enable $label wrote=$wrote")
    }

    private fun endpoint(usb: UsbInterface, direction: Int): UsbEndpoint? {
        for (index in 0 until usb.endpointCount) {
            val endpoint = usb.getEndpoint(index)
            if (endpoint.direction == direction && endpoint.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                return endpoint
            }
        }
        return null
    }

    private fun hex(bytes: ByteArray): String =
        bytes.take(16).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    private companion object {
        const val TAG = "DextopViture"
    }
}

internal object VitureHidSession {
    private val lock = Any()
    private var provider: VitureHidPoseProvider? = null
    private var deviceKey: String? = null

    fun start(context: Context, device: UsbDevice, id: String, displayName: String) {
        val next = synchronized(lock) {
            if (provider != null && deviceKey == device.deviceName && provider?.id == id) return
            val previous = provider
            provider = null
            deviceKey = null
            previous
        }
        previousClose(next)
        val created = VitureHidPoseProvider(id, displayName)
        synchronized(lock) {
            provider = created
            deviceKey = device.deviceName
        }
        XrPoseProviders.register(created)
        created.open(context.applicationContext, device)
    }

    fun abandon(provider: VitureHidPoseProvider) {
        val dropped = synchronized(lock) {
            if (this.provider !== provider) return
            this.provider = null
            deviceKey = null
            true
        }
        if (dropped) XrPoseProviders.unregister(provider.id)
    }

    fun stop() {
        val current = synchronized(lock) {
            val previous = provider
            provider = null
            deviceKey = null
            previous
        }
        previousClose(current)
    }

    private fun previousClose(current: VitureHidPoseProvider?) {
        if (current == null) return
        current.close()
        XrPoseProviders.unregister(current.id)
    }
}
