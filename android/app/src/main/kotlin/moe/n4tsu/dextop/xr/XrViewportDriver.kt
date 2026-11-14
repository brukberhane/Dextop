package moe.n4tsu.dextop.xr

import android.util.Log
import android.view.Choreographer
import java.util.concurrent.atomic.AtomicReference

/**
 * Stores the latest pose on the reader thread and applies a crop on the
 * choreographer thread. The apply callback owns the SurfaceControl.
 */
internal class XrViewportDriver(
    private val choreographer: Choreographer,
    private val profile: () -> XrProfile,
    private val identity: () -> String?,
    private val panel: () -> Pair<Int, Int>,
    private val canvas: () -> Pair<Int, Int>,
    private val apply: (XrCrop) -> Boolean,
) : XrPoseListener, Choreographer.FrameCallback {
    private val viewport = XrViewport()
    private val latest = AtomicReference<XrPose?>(null)
    private val bound = ArrayList<XrPoseProvider>()
    private var posted = false
    private var loggedAt = 0L
    private var cropWarned = false

    fun start() {
        rebind()
        if (posted) return
        viewport.reset()
        posted = true
        choreographer.postFrameCallback(this)
    }

    fun stop() {
        posted = false
        choreographer.removeFrameCallback(this)
        unbind()
        latest.set(null)
    }

    fun rebind() {
        val live = XrPoseProviders.all()
        val stale = bound.filter { provider -> live.none { it === provider } }
        stale.forEach { provider ->
            provider.removePoseListener(this)
            bound.remove(provider)
        }
        live.forEach { provider ->
            if (bound.none { it === provider }) {
                provider.addPoseListener(this)
                bound.add(provider)
            }
        }
    }

    override fun onPose(pose: XrPose) {
        latest.set(pose)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!posted) return
        rebind()
        val id = identity()
        val xr = id == "xreal" || id == "viture" || id == "viture-pro"
        val streaming = bound.any { it.availability == XrAvailability.STREAMING }
        val mode = if (xr) profile().mode else "body-lock"
        if (!xr || !streaming || mode == "body-lock") {
            choreographer.postFrameCallback(this)
            return
        }
        val pose = latest.get()
        if (pose != null) {
            val (canvasW, canvasH) = canvas()
            val (panelW, panelH) = panel()
            if (canvasW > 0 && panelW > 0) {
                val crop = viewport.step(mode, pose, System.nanoTime(), canvasW, canvasH, panelW, panelH)
                if (!apply(crop) && !cropWarned) {
                    cropWarned = true
                    Log.w(TAG, "updateCrop failed")
                }
                val now = System.nanoTime()
                if (now - loggedAt >= 1_000_000_000L) {
                    loggedAt = now
                    Log.i(TAG, "mode=$mode left=${crop.left} top=${crop.top} yaw=${pose.yawRadians} pitch=${pose.pitchRadians}")
                }
            }
        }
        choreographer.postFrameCallback(this)
    }

    private fun unbind() {
        bound.forEach { it.removePoseListener(this) }
        bound.clear()
    }

    private companion object {
        const val TAG = "DextopXrView"
    }
}
