package moe.n4tsu.dextop.xr

import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertTrue

class XrOrientationFusionTest {
    @Test fun yawTracksHeadUpGyro() {
        val fusion = XrOrientationFusion()
        var pose = floatArrayOf(0f, 0f, 0f)
        val steps = 100
        val dt = 1f / steps
        repeat(steps) {
            pose = fusion.step(0f, 90f, 0f, 0f, 1f, 0f, dt)
        }
        assertClose((PI / 2.0).toFloat(), pose[0], 0.05f)
        assertClose(0f, pose[1], 0.05f)
        assertClose(0f, pose[2], 0.05f)
    }

    @Test fun restingBiasKillsInjectedDrift() {
        val fusion = XrOrientationFusion()
        var pose = floatArrayOf(0f, 0f, 0f)
        repeat(500) {
            pose = fusion.step(0f, 1f, 0f, 0f, 1f, 0f, 0.01f)
        }
        val before = pose[0]
        repeat(100) {
            pose = fusion.step(0f, 1f, 0f, 0f, 1f, 0f, 0.01f)
        }
        assertTrue(kotlin.math.abs(pose[0] - before) < 0.02f, "late drift ${pose[0] - before}")
    }

    @Test fun levelAccelPullsPitchBack() {
        val fusion = XrOrientationFusion()
        var pose = floatArrayOf(0f, 0f, 0f)
        repeat(20) {
            pose = fusion.step(90f, 0f, 0f, 0f, 1f, 0f, 0.01f)
        }
        val pitched = kotlin.math.abs(pose[1])
        assertTrue(pitched > 0.1f, "pitch did not move: $pitched")
        repeat(500) {
            pose = fusion.step(0f, 0f, 0f, 0f, 1f, 0f, 0.01f)
        }
        assertTrue(kotlin.math.abs(pose[1]) < pitched * 0.7f, "pitch stayed ${pose[1]} from $pitched")
    }

    private fun assertClose(expected: Float, actual: Float, tolerance: Float) {
        assertTrue(kotlin.math.abs(expected - actual) < tolerance, "expected $expected actual $actual")
    }
}
