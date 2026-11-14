package moe.n4tsu.dextop.xr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrGlassesProfilesTest {
    @Test fun freshStoreIsBodyLockSingle() {
        val store = MemoryXrProfileStore()
        assertEquals(XrProfile.default, store.read("viture-pro"))
    }

    @Test fun saveRoundTripDoesNotClobberOtherGlasses() {
        val store = MemoryXrProfileStore()
        assertTrue(store.save("viture-pro", "world-lock", "ultrawide"))
        assertEquals(XrProfile("world-lock", "ultrawide"), store.read("viture-pro"))
        assertEquals(XrProfile.default, store.read("xreal"))
    }

    @Test fun unknownModeIsRejected() {
        val store = MemoryXrProfileStore()
        assertFalse(store.save("viture-pro", "pinned", "single"))
        assertEquals(XrProfile.default, store.read("viture-pro"))
    }
}
