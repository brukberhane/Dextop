package moe.n4tsu.dextop.xr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class XrPoseProvidersTest {
    @Test fun registryStartsEmptyAfterClear() {
        XrPoseProviders.clearForTests()
        assertTrue(XrPoseProviders.all().isEmpty())
        assertNull(XrPoseProviders.byId("fake"))
    }

    @Test fun registerByIdAndUnregister() {
        XrPoseProviders.clearForTests()
        val provider = FakeXrPoseProvider()
        XrPoseProviders.register(provider)
        assertSame(provider, XrPoseProviders.byId("fake"))
        assertEquals(listOf(provider), XrPoseProviders.all())
        XrPoseProviders.unregister("fake")
        assertNull(XrPoseProviders.byId("fake"))
        assertTrue(XrPoseProviders.all().isEmpty())
    }

    @Test fun registerReplacesSameId() {
        XrPoseProviders.clearForTests()
        val first = FakeXrPoseProvider()
        val second = FakeXrPoseProvider()
        XrPoseProviders.register(first)
        XrPoseProviders.register(second)
        assertSame(second, XrPoseProviders.byId("fake"))
        assertEquals(1, XrPoseProviders.all().size)
    }
}
