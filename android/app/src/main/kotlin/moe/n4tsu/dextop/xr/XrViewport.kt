package moe.n4tsu.dextop.xr

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

internal const val LOW_PASS_TAU_SECONDS = 0.05f
internal const val FOLLOW_TAU_SECONDS = 0.25f
internal const val MAX_LEAD_SECONDS = 1f / 60f

/** Glasses show this much more desktop than the panel, scaled down into the same lens. */
internal const val VIEW_ZOOM = 1.6f

internal fun pixelsPerRadian(panelWidth: Int): Float = panelWidth.toFloat()

internal fun viewEdge(panel: Int): Int = (panel * VIEW_ZOOM).toInt().coerceAtLeast(1) and -2

/** View plus one radian of travel on each side. */
internal fun canvasEdge(panel: Int): Int = (viewEdge(panel) + pixelsPerRadian(panel).toInt() * 2) and -2

internal data class XrCrop(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Pose to a canvas crop. No Android types. Roll is ignored.
 * Yaw+ (look left) decreases [XrCrop.left]. Pitch+ (look up) decreases [XrCrop.top].
 */
internal class XrViewport {
    private var primed = false
    private var lowYaw = 0f
    private var lowPitch = 0f
    private var followYaw = 0f
    private var followPitch = 0f
    private var last: XrPose? = null
    private var lastNow = 0L

    fun reset() {
        primed = false
        last = null
        lastNow = 0L
    }

    fun step(
        mode: String,
        pose: XrPose,
        nowNanos: Long,
        canvasW: Int,
        canvasH: Int,
        panelW: Int,
        panelH: Int,
    ): XrCrop {
        val (yaw, pitch) = lead(pose, nowNanos)
        val sampleDt = last?.let { (pose.timestampNanos - it.timestampNanos) / 1_000_000_000f } ?: 0f
        val wallDt = if (lastNow > 0L) (nowNanos - lastNow) / 1_000_000_000f else 0f
        val dt = if (sampleDt > 0f) sampleDt else wallDt
        if (!primed) {
            lowYaw = yaw
            lowPitch = pitch
            followYaw = yaw
            followPitch = pitch
            primed = true
        } else if (mode != "body-lock" && dt > 0f) {
            val lowAlpha = 1f - exp(-dt / LOW_PASS_TAU_SECONDS)
            lowYaw += (yaw - lowYaw) * lowAlpha
            lowPitch += (pitch - lowPitch) * lowAlpha
            if (mode == "smooth-follow") {
                val followAlpha = 1f - exp(-dt / FOLLOW_TAU_SECONDS)
                followYaw += (lowYaw - followYaw) * followAlpha
                followPitch += (lowPitch - followPitch) * followAlpha
            } else {
                followYaw = lowYaw
                followPitch = lowPitch
            }
        }
        last = pose
        lastNow = nowNanos
        if (mode == "body-lock") return XrCrop(0, 0, canvasW, canvasH)
        val usedYaw = if (mode == "smooth-follow") followYaw else lowYaw
        val usedPitch = if (mode == "smooth-follow") followPitch else lowPitch
        return crop(usedYaw, usedPitch, canvasW, canvasH, panelW, panelH)
    }

    private fun lead(pose: XrPose, nowNanos: Long): Pair<Float, Float> {
        val prev = last ?: return pose.yawRadians to pose.pitchRadians
        val sampleDt = (pose.timestampNanos - prev.timestampNanos) / 1_000_000_000f
        val since = (nowNanos - pose.timestampNanos) / 1_000_000_000f
        if (sampleDt <= 0f || sampleDt >= 0.5f || since <= 0f || since > MAX_LEAD_SECONDS) {
            return pose.yawRadians to pose.pitchRadians
        }
        val scale = since / sampleDt
        return pose.yawRadians + (pose.yawRadians - prev.yawRadians) * scale to
            pose.pitchRadians + (pose.pitchRadians - prev.pitchRadians) * scale
    }

    private fun crop(
        yaw: Float,
        pitch: Float,
        canvasW: Int,
        canvasH: Int,
        panelW: Int,
        panelH: Int,
    ): XrCrop {
        val width = min(viewEdge(panelW), canvasW)
        val height = min(viewEdge(panelH), canvasH)
        val pixels = pixelsPerRadian(panelW)
        val centerX = (canvasW - width) / 2
        val centerY = (canvasH - height) / 2
        val left = centerX + (-yaw * pixels).toInt()
        val top = centerY + (-pitch * pixels).toInt()
        return XrCrop(
            left.coerceIn(0, max(0, canvasW - width)),
            top.coerceIn(0, max(0, canvasH - height)),
            width,
            height,
        )
    }
}
