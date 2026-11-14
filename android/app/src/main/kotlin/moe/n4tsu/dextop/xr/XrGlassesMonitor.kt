package moe.n4tsu.dextop.xr

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.util.Log
import android.net.LinkAddress
import moe.n4tsu.dextop.ExternalDisplayDetector

internal object XrGlassesMonitor {
    const val USB_PERMISSION = "moe.n4tsu.dextop.xr.USB_PERMISSION"

    private val lock = Any()
    private var started = false
    private var store: XrProfileStore? = null
    private var session: XrIdentity? = null
    private var profile = XrProfile.default
    private var usbPermission = "not_required"
    private val permissionAsked = HashSet<Int>()
    private var receiver: BroadcastReceiver? = null
    private val sessionListeners = ArrayList<() -> Unit>()

    fun currentProfile(): XrProfile = synchronized(lock) { profile }

    fun sessionId(): String? = synchronized(lock) { session?.id }

    fun addSessionListener(listener: () -> Unit) {
        val notify = synchronized(lock) { sessionListeners.add(listener) }
        if (notify) listener()
    }

    fun removeSessionListener(listener: () -> Unit) {
        synchronized(lock) { sessionListeners.remove(listener) }
    }

    private fun notifySession() {
        val listeners = synchronized(lock) { sessionListeners.toList() }
        listeners.forEach { it() }
    }

    fun start(context: Context) {
        val app = context.applicationContext
        synchronized(lock) {
            if (store == null) store = PrefsXrProfileStore(app)
            if (started) return
            started = true
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                addAction(USB_PERMISSION)
            }
            val next = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                        val device = intent.usbDevice()
                        if (device != null) permissionAsked.remove(device.deviceId)
                    }
                    refresh(ctx, requestPermission = intent.action != UsbManager.ACTION_USB_DEVICE_DETACHED)
                }
            }
            receiver = next
            app.registerReceiver(next, filter, Context.RECEIVER_NOT_EXPORTED)
        }
        refresh(app, requestPermission = true)
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        val current = synchronized(lock) {
            started = false
            receiver.also { receiver = null }
        }
        if (current != null) runCatching { app.unregisterReceiver(current) }
        VitureHidSession.stop()
        XrealAirHidSession.stop()
    }

    fun onDisplaysChanged(context: Context) {
        refresh(context.applicationContext, requestPermission = false)
    }

    fun snapshot(context: Context): Map<String, Any?> {
        refresh(context.applicationContext, requestPermission = false)
        val (identity, current, permission) = synchronized(lock) { Triple(session, profile, usbPermission) }
        if (identity == null) {
            return mapOf(
                "detected" to false,
                "id" to "",
                "displayName" to "",
                "mode" to "",
                "preset" to "",
                "singleScale" to 1.0,
                "usbPermission" to permission,
            )
        }
        return mapOf(
            "detected" to true,
            "id" to identity.id,
            "displayName" to identity.displayName,
            "mode" to current.mode,
            "preset" to current.preset,
            "singleScale" to current.singleScale.toDouble(),
            "usbPermission" to permission,
        )
    }

    fun saveProfile(context: Context, id: String, mode: String, preset: String, singleScale: Float = 1f): Boolean {
        if (id.isEmpty()) return false
        val app = context.applicationContext
        val saved = synchronized(lock) {
            val profiles = store ?: PrefsXrProfileStore(app).also { store = it }
            if (!profiles.save(id, mode, preset, singleScale)) return false
            if (session?.id == id) profile = profiles.read(id)
            true
        }
        if (saved) notifySession()
        return saved
    }

    fun requestUsbPermission(context: Context) {
        val app = context.applicationContext
        val usb = app.getSystemService(UsbManager::class.java)
        if (usb != null) {
            val denied = usb.deviceList.values
                .filter { (it.vendorId == 0x35CA || it.vendorId == 0x3318) && !usb.hasPermission(it) }
                .map { it.deviceId }
            synchronized(lock) { permissionAsked.removeAll(denied.toSet()) }
        }
        refresh(app, requestPermission = true)
    }

    fun recenter(): String {
        val providers = XrPoseProviders.all()
        runCatching { Log.i("DextopXr", "recenter providers=${providers.size}") }
        providers.forEach { it.recenter() }
        return "ok"
    }

    private fun refresh(context: Context, requestPermission: Boolean) {
        val usb = context.getSystemService(UsbManager::class.java)
        val devices = usb?.deviceList?.values?.toList().orEmpty()
        val viture = devices.filter { it.vendorId == 0x35CA }
        val xreal = devices.filter { device ->
            device.vendorId == 0x3318 && XrealAirProtocol.KNOWN_PIDS.any { it == device.productId }
        }
        val names = runCatching { ExternalDisplayDetector(context).snapshot().names }.getOrDefault(emptyList())
        val identity = identifyXrGlasses(
            XrSignals(
                usbDevices = devices.map { XrUsbDevice(it.vendorId, it.productId) },
                linkAddresses = linkAddresses(context),
                displayNames = names,
            ),
        )
        val relevant = when (identity?.id) {
            "xreal" -> xreal
            "viture-pro" -> viture.filter { it.productId == 0x101D }
            "viture" -> viture.filter { it.productId != 0x101D }
            else -> emptyList()
        }
        val permission = when {
            relevant.isEmpty() -> "not_required"
            relevant.all { usb?.hasPermission(it) == true } -> "granted"
            else -> "needed"
        }
        if (requestPermission && permission == "needed" && usb != null) {
            for (device in relevant) {
                if (usb.hasPermission(device)) continue
                val ask = synchronized(lock) { permissionAsked.add(device.deviceId) }
                if (!ask) continue
                val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                val pending = PendingIntent.getBroadcast(
                    context,
                    device.deviceId,
                    Intent(USB_PERMISSION).setPackage(context.packageName),
                    flags,
                )
                usb.requestPermission(device, pending)
            }
        }
        synchronized(lock) {
            usbPermission = permission
            session = identity
            profile = if (identity == null) XrProfile.default else {
                val profiles = store ?: PrefsXrProfileStore(context).also { store = it }
                profiles.read(identity.id)
            }
        }
        notifySession()
        val vitureDevice = when (identity?.id) {
            "viture-pro" -> viture.firstOrNull { it.productId == 0x101D }
            "viture" -> viture.firstOrNull { it.productId != 0x101D }
            else -> null
        }
        if (vitureDevice != null && permission == "granted" && identity != null) {
            VitureHidSession.start(context, vitureDevice, identity.id, identity.displayName)
        } else {
            VitureHidSession.stop()
        }
        val xrealDevice = if (identity?.id == "xreal") xreal.firstOrNull() else null
        if (xrealDevice != null && permission == "granted" && identity != null) {
            XrealAirHidSession.start(context, xrealDevice, identity.id, identity.displayName)
        } else {
            XrealAirHidSession.stop()
        }
    }

    private fun linkAddresses(context: Context): List<String> {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        return manager.allNetworks.flatMap { network ->
            manager.getLinkProperties(network)?.linkAddresses.orEmpty().map(LinkAddress::toString)
        }
    }

    private fun Intent.usbDevice(): UsbDevice? =
        getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
}
