package moe.n4tsu.dextop.xr

internal data class XrUsbDevice(val vendorId: Int, val productId: Int)

internal data class XrSignals(
    val usbDevices: List<XrUsbDevice> = emptyList(),
    val linkAddresses: List<String> = emptyList(),
    val displayNames: List<String> = emptyList(),
)

internal data class XrIdentity(val id: String, val displayName: String)

private const val VITURE_VID = 0x35CA
private const val VITURE_PRO_PID = 0x101D
private const val XREAL_VID = 0x3318

/**
 * USB vendor id beats a display name. XREAL family match is VID 0x3318
 * (Air 2 Pro EDID is "Air 2 Pro", so the name heuristic misses it), else a
 * 169.254.2.* link (One-series NIC), else an xreal/nreal display name.
 * When Viture USB and an XREAL signal are both present, an xreal/nreal
 * display name breaks the tie; otherwise Viture wins.
 */
internal fun identifyXrGlasses(signals: XrSignals): XrIdentity? {
    val vitureUsb = signals.usbDevices.filter { it.vendorId == VITURE_VID }
    val vitureId = when {
        vitureUsb.any { it.productId == VITURE_PRO_PID } -> "viture-pro"
        vitureUsb.isNotEmpty() -> "viture"
        else -> null
    }
    val xrealLink = signals.linkAddresses.any { address ->
        address.substringBefore('/').substringBefore('%').startsWith("169.254.2.")
    }
    val xrealUsb = signals.usbDevices.any { it.vendorId == XREAL_VID }
    val xrealPresent = xrealLink || xrealUsb
    val xrealNamed = signals.displayNames.firstOrNull { isXrealName(it) }
    val vitureNamed = signals.displayNames.firstOrNull { it.lowercase().contains("viture") }

    val id = when {
        vitureId != null && xrealPresent && xrealNamed != null -> "xreal"
        vitureId != null -> vitureId
        xrealPresent -> "xreal"
        xrealNamed != null -> "xreal"
        vitureNamed != null -> "viture-pro"
        else -> return null
    }
    val label = when (id) {
        "xreal" -> xrealNamed ?: signals.displayNames.firstOrNull() ?: "XREAL"
        "viture" -> vitureNamed ?: "VITURE"
        else -> vitureNamed ?: "VITURE Pro"
    }
    return XrIdentity(id, label)
}

private fun isXrealName(name: String): Boolean {
    val lower = name.lowercase()
    return lower.contains("xreal") || lower.contains("nreal")
}
