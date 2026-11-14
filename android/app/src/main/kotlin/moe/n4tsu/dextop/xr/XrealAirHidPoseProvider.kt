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

internal class XrealAirHidPoseProvider(
    override val id: String,
    override val displayName: String,
) : AbstractXrPoseProvider() {
    @Volatile private var running = false
    private var reader: Thread? = null
    private var connection: UsbDeviceConnection? = null
    private var imuInterface: UsbInterface? = null
    private var imuOut: UsbEndpoint? = null
    private var request: UsbRequest? = null

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
                XrealAirHidSession.abandon(this)
            }
        }, "DextopXrealAir").also { it.start() }
    }

    fun close() {
        running = false
        runCatching { request?.cancel() }
        reader?.join(1500)
        reader = null
        releaseUsb()
    }

    private fun releaseUsb() {
        val current: UsbDeviceConnection?
        val out: UsbEndpoint?
        val imu: UsbInterface?
        val pending: UsbRequest?
        synchronized(this) {
            if (connection == null && request == null) return
            current = connection
            out = imuOut
            imu = imuInterface
            pending = request
            connection = null
            imuOut = null
            imuInterface = null
            request = null
        }
        if (current != null && out != null) {
            val packet = XrealAirProtocol.stopStream()
            runCatching { current.bulkTransfer(out, packet, packet.size, 200) }
        }
        if (imu != null) runCatching { current?.releaseInterface(imu) }
        runCatching { current?.close() }
        runCatching { pending?.close() }
    }

    private fun read(context: Context, device: UsbDevice) {
        val usb = context.getSystemService(UsbManager::class.java)
        if (usb == null || !usb.hasPermission(device)) {
            publishAvailability(XrAvailability.UNAVAILABLE)
            return
        }
        val opened = usb.openDevice(device) ?: run {
            publishAvailability(XrAvailability.ERROR)
            return
        }
        connection = opened
        val imu = findInterface(device, XrealAirProtocol.IMU_INTERFACE)
        imuInterface = imu
        if (imu == null || !opened.claimInterface(imu, true)) {
            Log.w(TAG, "claim failed iface=${imu?.id}")
            publishAvailability(XrAvailability.ERROR)
            releaseUsb()
            return
        }
        val imuIn = endpoint(imu, UsbConstants.USB_DIR_IN)
        val out = endpoint(imu, UsbConstants.USB_DIR_OUT)
        imuOut = out
        if (imuIn == null || out == null) {
            Log.w(TAG, "missing interrupt endpoint")
            publishAvailability(XrAvailability.ERROR)
            releaseUsb()
            return
        }
        Log.i(TAG, "endpoints in=${imuIn.address} out=${out.address}")
        val queued = UsbRequest()
        if (!queued.initialize(opened, imuIn)) {
            runCatching { queued.close() }
            publishAvailability(XrAvailability.ERROR)
            releaseUsb()
            return
        }
        request = queued
        val buf = ByteBuffer.allocateDirect(64)
        if (!queued.queue(buf)) {
            publishAvailability(XrAvailability.ERROR)
            releaseUsb()
            return
        }
        write(opened, out, XrealAirProtocol.startStream(), "start")
        val fusion = XrOrientationFusion()
        val clock = XrealTimestampClock()
        var retried = false
        var sawSensor = false
        var loggedUnit = false
        var loggedRate = false
        var stallResent = false
        var poseCount = 0
        var deviceNanos = 0.0
        var lastPacket = System.nanoTime()
        var lastPoseLog = 0L
        val openedAt = lastPacket
        while (running) {
            val got = try {
                opened.requestWait(1000L)
            } catch (_: java.util.concurrent.TimeoutException) {
                null
            }
            if (got == null) {
                if (!sawSensor && !retried && System.nanoTime() - openedAt >= 1_000_000_000L) {
                    retried = true
                    Log.i(TAG, "retry start")
                    write(opened, out, XrealAirProtocol.stopStream(), "stop")
                    write(opened, out, XrealAirProtocol.startStream(), "start")
                }
                if (sawSensor && !stallResent && System.nanoTime() - lastPacket >= 2_000_000_000L) {
                    stallResent = true
                    write(opened, out, XrealAirProtocol.startStream(), "start")
                }
                if (!sawSensor && retried && System.nanoTime() - openedAt >= 2_000_000_000L) {
                    Log.w(TAG, "no sensor frame")
                    publishAvailability(XrAvailability.ERROR)
                    dequeue(opened, queued)
                    return
                }
                continue
            }
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
            if (!running || !got.queue(buf)) break
            val sample = XrealAirProtocol.parseSample(bytes, size) ?: continue
            val dt = clock.dtSeconds(sample.timestamp)
            if (!loggedUnit && clock.unit.isNotEmpty()) {
                loggedUnit = true
                Log.i(TAG, "timestamp=${clock.unit}")
            }
            val step = dt ?: ((System.nanoTime() - lastPacket) / 1_000_000_000f)
            lastPacket = System.nanoTime()
            stallResent = false
            val pose = fusion.step(
                sample.gyroDegPerSec[0],
                sample.gyroDegPerSec[1],
                sample.gyroDegPerSec[2],
                sample.accelG[0],
                sample.accelG[1],
                sample.accelG[2],
                step,
            )
            val published = publishRaw(pose[0], pose[1], pose[2], System.nanoTime())
            if (!sawSensor) {
                sawSensor = true
                publishAvailability(XrAvailability.STREAMING)
            }
            poseCount += 1
            if (dt != null) deviceNanos += dt.toDouble() * 1_000_000_000.0
            if (System.nanoTime() - lastPoseLog >= 1_000_000_000L) {
                lastPoseLog = System.nanoTime()
                Log.i(
                    TAG,
                    "pose yaw=${published.yawRadians} pitch=${published.pitchRadians} roll=${published.rollRadians}",
                )
            }
            if (!loggedRate && deviceNanos >= 5_000_000_000.0) {
                loggedRate = true
                val seconds = deviceNanos / 1_000_000_000.0
                Log.i(TAG, "pose-rate=${"%.1f".format(poseCount / seconds)}Hz")
            }
        }
    }

    private fun dequeue(opened: UsbDeviceConnection, queued: UsbRequest) {
        runCatching { queued.cancel() }
        runCatching { opened.requestWait() }
    }

    private fun write(connection: UsbDeviceConnection, out: UsbEndpoint, packet: ByteArray, label: String) {
        val wrote = connection.bulkTransfer(out, packet, packet.size, 200)
        Log.i(TAG, "$label wrote=$wrote")
    }

    private fun findInterface(device: UsbDevice, id: Int): UsbInterface? {
        for (index in 0 until device.interfaceCount) {
            val iface = device.getInterface(index)
            if (iface.id == id) return iface
        }
        return null
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

    private companion object {
        const val TAG = "DextopXrealAir"
    }
}

internal object XrealAirHidSession {
    private val lock = Any()
    private var provider: XrealAirHidPoseProvider? = null
    private var deviceKey: String? = null
    private var failedKey: String? = null

    fun start(context: Context, device: UsbDevice, id: String, displayName: String) {
        val next = synchronized(lock) {
            if (failedKey == device.deviceName) return
            if (provider != null && deviceKey == device.deviceName && provider?.id == id) return
            val previous = provider
            provider = null
            deviceKey = null
            previous
        }
        previousClose(next)
        val created = XrealAirHidPoseProvider(id, displayName)
        synchronized(lock) {
            provider = created
            deviceKey = device.deviceName
        }
        XrPoseProviders.register(created)
        created.open(context.applicationContext, device)
    }

    fun abandon(provider: XrealAirHidPoseProvider) {
        val dropped = synchronized(lock) {
            if (this.provider !== provider) return
            failedKey = deviceKey
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
            failedKey = null
            previous
        }
        previousClose(current)
    }

    private fun previousClose(current: XrealAirHidPoseProvider?) {
        if (current == null) return
        current.close()
        XrPoseProviders.unregister(current.id)
    }
}
