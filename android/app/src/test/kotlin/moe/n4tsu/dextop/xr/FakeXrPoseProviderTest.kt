package moe.n4tsu.dextop.xr

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeXrPoseProviderTest {
    @Test fun pushDeliversPoseToListener() {
        val provider = FakeXrPoseProvider()
        val received = mutableListOf<XrPose>()
        provider.addPoseListener { received += it }
        provider.push(0.3f, -0.1f, 0.05f, 1_000L)
        assertEquals(1, received.size)
        val pose = received.single()
        assertEquals(1_000L, pose.timestampNanos)
        assertClose(0.3f, pose.yawRadians)
        assertClose(-0.1f, pose.pitchRadians)
        assertClose(0.05f, pose.rollRadians)
    }

    @Test fun twoListenersBothNotified() {
        val provider = FakeXrPoseProvider()
        var first = 0
        var second = 0
        provider.addPoseListener { first += 1 }
        provider.addPoseListener { second += 1 }
        provider.push(0f, 0f, 0f, 1L)
        assertEquals(1, first)
        assertEquals(1, second)
    }

    @Test fun removedListenerStopsReceiving() {
        val provider = FakeXrPoseProvider()
        var count = 0
        val listener = XrPoseListener { count += 1 }
        provider.addPoseListener(listener)
        provider.push(0f, 0f, 0f, 1L)
        provider.removePoseListener(listener)
        provider.push(0f, 0f, 0f, 2L)
        assertEquals(1, count)
    }

    @Test fun recenterBeforeAnyPoseIsANoOp() {
        val provider = FakeXrPoseProvider()
        val received = mutableListOf<XrPose>()
        provider.addPoseListener { received += it }
        provider.recenter()
        provider.push(0.3f, 0f, 0f, 1L)
        assertClose(0.3f, received.single().yawRadians)
    }

    @Test fun recenterZerosTheCurrentYaw() {
        val provider = FakeXrPoseProvider()
        val received = mutableListOf<XrPose>()
        provider.addPoseListener { received += it }
        provider.push(0.3f, 0f, 0f, 1L)
        provider.recenter()
        provider.push(0.3f, 0f, 0f, 2L)
        assertClose(0f, received.last().yawRadians)
    }

    @Test fun recenterResetsPitchAndRollToo() {
        val provider = FakeXrPoseProvider()
        val received = mutableListOf<XrPose>()
        provider.addPoseListener { received += it }
        provider.push(0.2f, -0.4f, 0.15f, 1L)
        provider.recenter()
        provider.push(0.2f, -0.4f, 0.15f, 2L)
        val pose = received.last()
        assertClose(0f, pose.yawRadians)
        assertClose(0f, pose.pitchRadians)
        assertClose(0f, pose.rollRadians)
    }

    @Test fun availabilityTransitionNotifiesListener() {
        val provider = FakeXrPoseProvider()
        val seen = mutableListOf<XrAvailability>()
        provider.addAvailabilityListener { seen += it }
        assertEquals(XrAvailability.UNAVAILABLE, provider.availability)
        provider.setAvailability(XrAvailability.STREAMING)
        assertEquals(XrAvailability.STREAMING, provider.availability)
        assertEquals(listOf(XrAvailability.STREAMING), seen)
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertTrue(abs(expected - actual) < 1e-4f, "expected $expected, was $actual")
    }
}
