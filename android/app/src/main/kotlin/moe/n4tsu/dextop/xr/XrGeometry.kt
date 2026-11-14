package moe.n4tsu.dextop.xr

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/** Black strip between zoned monitors, in canvas pixels. */
internal const val ZONE_GAP_PX = 48

/** 1080p, 1440p, 2160p at a 1080-tall panel. */
internal fun singleScaleSteps(): FloatArray = floatArrayOf(1f, 4f / 3f, 2f)

internal fun nearestSingleScale(value: Float): Float {
    if (!value.isFinite() || value <= 0f) return 1f
    return singleScaleSteps().minBy { abs(it - value) }
}

private fun even(px: Int): Int = px.coerceAtLeast(2) and -2

/** Integer edges for the three steps so 4/3 does not land one pixel short. */
internal fun scaledPanel(panel: Int, scale: Float): Int {
    val step = nearestSingleScale(scale)
    val px = when {
        step > 1.5f -> panel * 2
        step > 1.1f -> panel * 4 / 3
        else -> panel
    }
    return even(px)
}

/**
 * World-lock only. The canvas covers every pose the fusion can emit
 * (yaw ±π, pitch ±π/2) plus one lens, so the crop still has a full window at
 * the pose limit. px/rad is the lens width. Single upscale multiplies that,
 * so 2160p is about 28000×14000. That framebuffer is the world-lock cost.
 * Body-lock and smooth-follow pass [worldLock] false and stay on a canvas
 * the same size as the window.
 */
private fun poseSpan(pxPerRad: Int, radians: Double, window: Int): Int =
    even((pxPerRad * radians).roundToInt() + window)

private fun lensWidth(preset: String, panelW: Int, singleScale: Float): Int =
    if (preset == "single") scaledPanel(panelW, singleScale) else even(panelW)

private fun lensHeight(preset: String, panelH: Int, singleScale: Float): Int =
    if (preset == "single") scaledPanel(panelH, singleScale) else even(panelH)

internal fun canvasWidth(
    preset: String,
    panelW: Int,
    singleScale: Float = 1f,
    worldLock: Boolean = false,
): Int {
    if (worldLock) return poseSpan(lensWidth(preset, panelW, singleScale), 2 * PI, lensWidth(preset, panelW, singleScale))
    return when (preset) {
        "wide" -> even(panelW * 2 + ZONE_GAP_PX)
        "ultrawide" -> even(panelW * 3 + 2 * ZONE_GAP_PX)
        "single" -> scaledPanel(panelW, singleScale)
        else -> even(panelW)
    }
}

internal fun canvasHeight(
    preset: String,
    panelH: Int,
    singleScale: Float = 1f,
    worldLock: Boolean = false,
    panelW: Int = panelH,
): Int {
    if (worldLock) {
        val px = lensWidth(preset, panelW, singleScale)
        return poseSpan(px, PI, lensHeight(preset, panelH, singleScale))
    }
    return when (preset) {
        "single" -> scaledPanel(panelH, singleScale)
        else -> even(panelH)
    }
}

internal fun canvasSize(
    preset: String,
    panelW: Int,
    panelH: Int,
    singleScale: Float = 1f,
    worldLock: Boolean = false,
): Pair<Int, Int> = canvasWidth(preset, panelW, singleScale, worldLock) to
    canvasHeight(preset, panelH, singleScale, worldLock, panelW)

internal fun windowWidth(
    preset: String,
    panelW: Int,
    canvasW: Int,
    singleScale: Float = 1f,
    worldLock: Boolean = false,
): Int {
    val lens = if (worldLock || preset != "single") lensWidth(preset, panelW, singleScale) else canvasW
    return minOf(lens, canvasW)
}

internal fun windowHeight(
    preset: String,
    panelH: Int,
    canvasH: Int,
    singleScale: Float = 1f,
    worldLock: Boolean = false,
): Int {
    val lens = if (worldLock || preset != "single") lensHeight(preset, panelH, singleScale) else canvasH
    return minOf(lens, canvasH)
}

internal data class XrZoneRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

internal fun zoneRects(
    preset: String,
    panelW: Int,
    panelH: Int,
    singleScale: Float = 1f,
    worldLock: Boolean = false,
): List<XrZoneRect> {
    if (!worldLock) {
        val count = when (preset) {
            "wide" -> 2
            "ultrawide" -> 3
            else -> return listOf(XrZoneRect(0, 0, canvasWidth(preset, panelW, singleScale), canvasHeight(preset, panelH, singleScale)))
        }
        val h = even(panelH)
        val w = even(panelW)
        return List(count) { index ->
            val x = index * (w + ZONE_GAP_PX)
            XrZoneRect(x, 0, x + w, h)
        }
    }
    val canvasW = canvasWidth(preset, panelW, singleScale, worldLock = true)
    val canvasH = canvasHeight(preset, panelH, singleScale, worldLock = true, panelW = panelW)
    val count = when (preset) {
        "wide" -> 2
        "ultrawide" -> 3
        else -> 1
    }
    val w = lensWidth(preset, panelW, singleScale)
    val h = lensHeight(preset, panelH, singleScale)
    val groupW = count * w + (count - 1) * ZONE_GAP_PX
    val x0 = (canvasW - groupW) / 2
    val y0 = (canvasH - h) / 2
    return List(count) { index ->
        val x = x0 + index * (w + ZONE_GAP_PX)
        XrZoneRect(x, y0, x + w, y0 + h)
    }
}
