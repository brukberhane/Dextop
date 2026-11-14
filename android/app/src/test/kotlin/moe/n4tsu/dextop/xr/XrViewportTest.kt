package moe.n4tsu.dextop.xr

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XrViewportTest {
    @Test fun zoomedCanvasKeepsOneRadian() {
        val panel = 1920
        val slack = (canvasEdge(panel) - viewEdge(panel)) / 2f
        assertTrue(viewEdge(panel) > panel)
        assertTrue(slack / pixelsPerRadian(panel) >= 1f)
    }

    @Test fun poseZeroCentersTheCrop() {
        val panelW = 1920
        val panelH = 1080
        val canvasW = canvasEdge(panelW)
        val canvasH = canvasEdge(panelH)
        val crop = XrViewport().step("world-lock", pose(0f, 0f, 0L), 0L, canvasW, canvasH, panelW, panelH)
        assertEquals((canvasW - viewEdge(panelW)) / 2, crop.left)
        assertEquals((canvasH - viewEdge(panelH)) / 2, crop.top)
        assertEquals(viewEdge(panelW), crop.width)
        assertEquals(viewEdge(panelH), crop.height)
    }

    @Test fun yawMovesLeftAndPitchMovesUp() {
        val viewport = XrViewport()
        val center = viewport.step("world-lock", pose(0f, 0f, 0L), 0L, 4000, 2000, 1000, 1000)
        val left = viewport.step("world-lock", pose(0.5f, 0f, 1_000_000_000L), 1_000_000_000L, 4000, 2000, 1000, 1000)
        val right = XrViewport().let { fresh ->
            fresh.step("world-lock", pose(0f, 0f, 0L), 0L, 4000, 2000, 1000, 1000)
            fresh.step("world-lock", pose(-0.5f, 0f, 1_000_000_000L), 1_000_000_000L, 4000, 2000, 1000, 1000)
        }
        val up = XrViewport().let { fresh ->
            fresh.step("world-lock", pose(0f, 0f, 0L), 0L, 4000, 2000, 1000, 1000)
            fresh.step("world-lock", pose(0f, 0.5f, 1_000_000_000L), 1_000_000_000L, 4000, 2000, 1000, 1000)
        }
        val down = XrViewport().let { fresh ->
            fresh.step("world-lock", pose(0f, 0f, 0L), 0L, 4000, 2000, 1000, 1000)
            fresh.step("world-lock", pose(0f, -0.5f, 1_000_000_000L), 1_000_000_000L, 4000, 2000, 1000, 1000)
        }
        assertTrue(left.left < center.left)
        assertTrue(right.left > center.left)
        assertTrue(up.top < center.top)
        assertTrue(down.top > center.top)
    }

    @Test fun offsetsClampToTheCanvas() {
        val viewport = XrViewport()
        viewport.step("world-lock", pose(0f, 0f, 0L), 0L, 3840, 2160, 1920, 1080)
        val farLeft = viewport.step("world-lock", pose(10f, 10f, 1_000_000_000L), 1_000_000_000L, 3840, 2160, 1920, 1080)
        val farRight = XrViewport().let { fresh ->
            fresh.step("world-lock", pose(0f, 0f, 0L), 0L, 3840, 2160, 1920, 1080)
            fresh.step("world-lock", pose(-10f, -10f, 1_000_000_000L), 1_000_000_000L, 3840, 2160, 1920, 1080)
        }
        assertEquals(0, farLeft.left)
        assertEquals(0, farLeft.top)
        assertEquals(3840 - viewEdge(1920), farRight.left)
        assertEquals(2160 - viewEdge(1080), farRight.top)
    }

    @Test fun matchingCanvasDoesNotPan() {
        val viewport = XrViewport()
        viewport.step("world-lock", pose(0f, 0f, 0L), 0L, 1920, 1080, 1920, 1080)
        val crop = viewport.step("world-lock", pose(2f, -2f, 1_000_000_000L), 1_000_000_000L, 1920, 1080, 1920, 1080)
        assertEquals(0, crop.left)
        assertEquals(0, crop.top)
    }

    @Test fun resetReturnsTheCenterCrop() {
        val viewport = XrViewport()
        viewport.step("world-lock", pose(0f, 0f, 0L), 0L, 3840, 1080, 1920, 1080)
        val moved = viewport.step("world-lock", pose(0.5f, 0f, 1_000_000_000L), 1_000_000_000L, 3840, 1080, 1920, 1080)
        viewport.reset()
        val again = viewport.step("world-lock", pose(0f, 0f, 0L), 0L, 3840, 1080, 1920, 1080)
        val center = (3840 - viewEdge(1920)) / 2
        assertTrue(moved.left < center)
        assertEquals(center, again.left)
    }

    @Test fun tallCanvasGivesPitchRoom() {
        val viewport = XrViewport()
        val center = viewport.step("world-lock", pose(0f, 0f, 0L), 0L, 3840, 2160, 1920, 1080)
        val up = viewport.step("world-lock", pose(0f, 0.4f, 1_000_000_000L), 1_000_000_000L, 3840, 2160, 1920, 1080)
        assertEquals((3840 - viewEdge(1920)) / 2, center.left)
        assertEquals((2160 - viewEdge(1080)) / 2, center.top)
        assertTrue(up.top < center.top)
    }

    @Test fun worldLockHandsSmoothFollowTheCurrentAngle() {
        val viewport = XrViewport()
        viewport.step("world-lock", pose(0f, 0f, 0L), 0L, 4000, 400, 200, 200)
        val world = viewport.step("world-lock", pose(1f, 0f, 1_000_000_000L), 1_000_000_000L, 4000, 400, 200, 200)
        val follow = viewport.step("smooth-follow", pose(1f, 0f, 1_050_000_000L), 1_050_000_000L, 4000, 400, 200, 200)
        assertTrue(abs(follow.left - world.left) < 40, "follow=${follow.left} world=${world.left}")
    }

    @Test fun bodyLockIgnoresPose() {
        val crop = XrViewport().step("body-lock", pose(1f, 1f, 0L), 0L, 3840, 1080, 1920, 1080)
        assertEquals(0, crop.left)
        assertEquals(0, crop.top)
        assertEquals(3840, crop.width)
        assertEquals(1080, crop.height)
    }

    @Test fun smoothFollowApproachesWithoutPassing() {
        val viewport = XrViewport()
        var left = viewport.step("smooth-follow", pose(0f, 0f, 0L), 0L, 4000, 400, 200, 200).left
        val floor = (4000 - viewEdge(200)) / 2 - 200
        repeat(20) { index ->
            val t = (index + 1) * 50_000_000L
            val next = viewport.step("smooth-follow", pose(1f, 0f, t), t, 4000, 400, 200, 200).left
            assertTrue(next < left, "left=$next previous=$left")
            assertTrue(next > floor, "left=$next passed yaw 1")
            left = next
        }
    }

    @Test fun lowPassDoesNotTakeTheWholeSpike() {
        val viewport = XrViewport()
        val center = viewport.step("world-lock", pose(0f, 0f, 0L), 0L, 1000, 200, 100, 100).left
        val spiked = viewport.step("world-lock", pose(1f, 0f, 1_000_000L), 1_000_000L, 1000, 200, 100, 100).left
        assertTrue(abs(center - spiked) < 50, "moved ${center - spiked}px")
    }

    @Test fun extrapolationLeadsAtMostOneFrame() {
        val held = XrViewport()
        held.step("world-lock", pose(0f, 0f, 0L), 0L, 4000, 200, 1000, 200)
        val atSample = held.step("world-lock", pose(0.1f, 0f, 16_000_000L), 16_000_000L, 4000, 200, 1000, 200)

        val led = XrViewport()
        led.step("world-lock", pose(0f, 0f, 0L), 0L, 4000, 200, 1000, 200)
        val ahead = led.step("world-lock", pose(0.1f, 0f, 16_000_000L), 32_000_000L, 4000, 200, 1000, 200)
        assertTrue(ahead.left <= atSample.left)
        assertTrue(atSample.left - ahead.left <= 100)

        val stale = XrViewport()
        stale.step("world-lock", pose(0f, 0f, 0L), 0L, 4000, 200, 1000, 200)
        val heldLate = stale.step("world-lock", pose(0.1f, 0f, 16_000_000L), 16_000_000L + 5_000_000_000L, 4000, 200, 1000, 200)
        assertEquals(atSample.left, heldLate.left)
    }

    @Test fun widePanClampsAtThePanelWindow() {
        val canvasW = canvasWidth("wide", 1920)
        val windowW = windowWidth("wide", 1920, canvasW)
        val viewport = XrViewport()
        val center = viewport.step("world-lock", pose(0f, 0f, 0L), 0L, canvasW, 1080, 1920, 1080, windowW, 1080)
        val left = viewport.step("world-lock", pose(0.5f, 0.4f, 1_000_000_000L), 1_000_000_000L, canvasW, 1080, 1920, 1080, windowW, 1080)
        assertTrue(left.left < center.left)
        assertEquals(center.top, left.top)
        val far = XrViewport().let { fresh ->
            fresh.step("world-lock", pose(0f, 0f, 0L), 0L, canvasW, 1080, 1920, 1080, windowW, 1080)
            fresh.step("world-lock", pose(1.6f, 0f, 1_000_000_000L), 1_000_000_000L, canvasW, 1080, 1920, 1080, windowW, 1080)
        }
        val other = XrViewport().let { fresh ->
            fresh.step("world-lock", pose(0f, 0f, 0L), 0L, canvasW, 1080, 1920, 1080, windowW, 1080)
            fresh.step("world-lock", pose(-1.6f, 0f, 1_000_000_000L), 1_000_000_000L, canvasW, 1080, 1920, 1080, windowW, 1080)
        }
        assertEquals(0, far.left)
        assertEquals(canvasW - windowW, other.left)
    }

    @Test fun worldLockPitchesAndYawsAcrossThePoseCanvas() {
        val canvasW = canvasWidth("single", 1920, worldLock = true)
        val canvasH = canvasHeight("single", 1080, worldLock = true, panelW = 1920)
        val windowW = windowWidth("single", 1920, canvasW, worldLock = true)
        val windowH = windowHeight("single", 1080, canvasH, worldLock = true)
        val viewport = XrViewport()
        val center = viewport.step(
            "world-lock", pose(0f, 0f, 0L), 0L, canvasW, canvasH, 1920, 1080, windowW, windowH,
        )
        val moved = viewport.step(
            "world-lock", pose(0.5f, 0.5f, 1_000_000_000L), 1_000_000_000L,
            canvasW, canvasH, 1920, 1080, windowW, windowH,
        )
        assertEquals(center.left - 960, moved.left)
        assertEquals(center.top - 960, moved.top)
        assertEquals(6032, center.left)
        assertEquals(3016, center.top)
    }

    @Test fun smoothFollowOnAFullBleedCanvasDoesNotPan() {
        val crop = XrViewport().let { viewport ->
            viewport.step("smooth-follow", pose(0f, 0f, 0L), 0L, 1920, 1080, 1920, 1080, 1920, 1080)
            viewport.step("smooth-follow", pose(1f, 0.5f, 1_000_000_000L), 1_000_000_000L, 1920, 1080, 1920, 1080, 1920, 1080)
        }
        assertEquals(0, crop.left)
        assertEquals(0, crop.top)
        assertEquals(1920, crop.width)
    }

    @Test fun scaledSingleDoesNotPan() {
        val crop = XrViewport().let { viewport ->
            viewport.step("world-lock", pose(0f, 0f, 0L), 0L, 3840, 2160, 1920, 1080, 3840, 2160)
            viewport.step("world-lock", pose(2f, -1f, 1_000_000_000L), 1_000_000_000L, 3840, 2160, 1920, 1080, 3840, 2160)
        }
        assertEquals(0, crop.left)
        assertEquals(0, crop.top)
        assertEquals(3840, crop.width)
        assertEquals(2160, crop.height)
    }

    private fun pose(yaw: Float, pitch: Float, time: Long) = XrPose(time, yaw, pitch, 0f)
}
