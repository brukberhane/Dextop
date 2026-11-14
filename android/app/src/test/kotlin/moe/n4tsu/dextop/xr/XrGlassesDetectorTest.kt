package moe.n4tsu.dextop.xr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XrGlassesDetectorTest {
    @Test fun vitureProUsbWithoutName() {
        val identity = identifyXrGlasses(XrSignals(usbDevices = listOf(XrUsbDevice(0x35CA, 0x101D))))
        assertEquals("viture-pro", identity?.id)
        assertEquals("VITURE Pro", identity?.displayName)
    }

    @Test fun otherViturePid() {
        val identity = identifyXrGlasses(XrSignals(usbDevices = listOf(XrUsbDevice(0x35CA, 0x1100))))
        assertEquals("viture", identity?.id)
    }

    @Test fun xrealLinkAddress() {
        val identity = identifyXrGlasses(XrSignals(linkAddresses = listOf("169.254.2.1/16")))
        assertEquals("xreal", identity?.id)
        assertEquals("XREAL", identity?.displayName)
    }

    @Test fun xrealNameWithoutUsb() {
        val identity = identifyXrGlasses(XrSignals(displayNames = listOf("XREAL One")))
        assertEquals("xreal", identity?.id)
        assertEquals("XREAL One", identity?.displayName)
    }

    @Test fun vitureNameWithoutUsb() {
        val identity = identifyXrGlasses(XrSignals(displayNames = listOf("VITURE Pro")))
        assertEquals("viture-pro", identity?.id)
        assertEquals("VITURE Pro", identity?.displayName)
    }

    @Test fun plainMonitorIsUnknown() {
        assertNull(identifyXrGlasses(XrSignals(displayNames = listOf("Dell U2720Q"))))
    }

    @Test fun usbWinsOverUnrelatedName() {
        val identity = identifyXrGlasses(
            XrSignals(
                usbDevices = listOf(XrUsbDevice(0x35CA, 0x101D)),
                displayNames = listOf("Dell U2720Q"),
            ),
        )
        assertEquals("viture-pro", identity?.id)
    }

    @Test fun nameBreaksTieTowardXreal() {
        val identity = identifyXrGlasses(
            XrSignals(
                usbDevices = listOf(XrUsbDevice(0x35CA, 0x101D)),
                linkAddresses = listOf("169.254.2.1/16"),
                displayNames = listOf("XREAL One"),
            ),
        )
        assertEquals("xreal", identity?.id)
    }

    @Test fun vitureWinsTieWhenNameIsMissing() {
        val identity = identifyXrGlasses(
            XrSignals(
                usbDevices = listOf(XrUsbDevice(0x35CA, 0x101D)),
                linkAddresses = listOf("169.254.2.1/16"),
            ),
        )
        assertEquals("viture-pro", identity?.id)
    }

    @Test fun xrealNameStillWinsWhenAVitureNameIsListedFirst() {
        val identity = identifyXrGlasses(
            XrSignals(
                usbDevices = listOf(XrUsbDevice(0x35CA, 0x101D)),
                linkAddresses = listOf("169.254.2.1/16"),
                displayNames = listOf("VITURE Pro", "XREAL One"),
            ),
        )
        assertEquals("xreal", identity?.id)
        assertEquals("XREAL One", identity?.displayName)
    }
}
