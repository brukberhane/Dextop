package moe.n4tsu.dextop.xr

import android.util.Log

/**
 * Origin-relative 3DoF head orientation.
 *
 * Yaw positive = head turns left (counter-clockwise from above).
 * Pitch positive = head tilts up.
 * Roll positive = head rolls right (clockwise for the wearer).
 *
 * Values are unwrapped and continuous: providers must not wrap angles into
 * [-π, π]. Viewport math depends on that continuity.
 *
 * [yawRadians], [pitchRadians], and [rollRadians] are relative to the last
 * [XrPoseProvider.recenter] call, so the pose at recenter reads approximately 0.
 */
data class XrPose(
    val timestampNanos: Long,
    val yawRadians: Float,
    val pitchRadians: Float,
    val rollRadians: Float,
)

enum class XrAvailability { UNAVAILABLE, CONNECTING, STREAMING, ERROR }

fun interface XrPoseListener {
    fun onPose(pose: XrPose)
}

fun interface XrAvailabilityListener {
    fun onAvailabilityChanged(availability: XrAvailability)
}

/**
 * 3DoF orientation source for one pair of XR glasses.
 *
 * Contract:
 * 1. Poses are origin-relative. [recenter] shifts future poses so the current
 *    head direction reads approximately 0. Consumers never do origin math.
 * 2. Angles are unwrapped and continuous. Do not wrap to [-π, π].
 * 3. Vendor frames are converted to this contract inside adapters, never by consumers.
 * 4. Listener callbacks fire from a provider-owned thread. Consumers must not block.
 *    Poses arrive in nondecreasing [XrPose.timestampNanos] order.
 * 5. [recenter] is synchronous and idempotent, and a no-op before the first pose.
 */
internal interface XrPoseProvider {
    val id: String
    val displayName: String
    val availability: XrAvailability

    fun addPoseListener(listener: XrPoseListener)
    fun removePoseListener(listener: XrPoseListener)
    fun addAvailabilityListener(listener: XrAvailabilityListener)
    fun recenter()
}

internal abstract class AbstractXrPoseProvider : XrPoseProvider {
    private val lock = Any()
    private val poseListeners = LinkedHashSet<XrPoseListener>()
    private val availabilityListeners = LinkedHashSet<XrAvailabilityListener>()
    private var lastRaw: RawPose? = null
    private var origin = RawPose(0f, 0f, 0f)

    @Volatile
    private var currentAvailability: XrAvailability = XrAvailability.UNAVAILABLE
    override val availability: XrAvailability get() = currentAvailability

    protected fun publishRaw(rawYaw: Float, rawPitch: Float, rawRoll: Float, timestampNanos: Long): XrPose {
        val pose: XrPose
        val listeners: List<XrPoseListener>
        synchronized(lock) {
            lastRaw = RawPose(rawYaw, rawPitch, rawRoll)
            pose = XrPose(
                timestampNanos,
                rawYaw - origin.yaw,
                rawPitch - origin.pitch,
                rawRoll - origin.roll,
            )
            listeners = poseListeners.toList()
        }
        for (listener in listeners) listener.onPose(pose)
        return pose
    }

    protected fun publishAvailability(next: XrAvailability) {
        val listeners: List<XrAvailabilityListener>
        synchronized(lock) {
            if (currentAvailability == next) return
            currentAvailability = next
            listeners = availabilityListeners.toList()
        }
        for (listener in listeners) listener.onAvailabilityChanged(next)
    }

    override fun addPoseListener(listener: XrPoseListener) {
        synchronized(lock) { poseListeners.add(listener) }
    }

    override fun removePoseListener(listener: XrPoseListener) {
        synchronized(lock) { poseListeners.remove(listener) }
    }

    override fun addAvailabilityListener(listener: XrAvailabilityListener) {
        synchronized(lock) { availabilityListeners.add(listener) }
    }

    override fun recenter() {
        synchronized(lock) {
            val raw = lastRaw
            if (raw == null) {
                runCatching { Log.i(TAG, "recenter before first pose") }
                return
            }
            origin = raw
            runCatching { Log.i(TAG, "recenter yaw=${raw.yaw} pitch=${raw.pitch} roll=${raw.roll}") }
        }
    }

    private companion object {
        const val TAG = "DextopXr"
    }

    private data class RawPose(val yaw: Float, val pitch: Float, val roll: Float)
}
