package moe.n4tsu.dextop.xr

/** Scriptable [XrPoseProvider] for tests. Not registered in production. */
internal class FakeXrPoseProvider : AbstractXrPoseProvider() {
    override val id: String = "fake"
    override val displayName: String = "Fake XR glasses"

    fun push(rawYaw: Float, rawPitch: Float, rawRoll: Float, timestampNanos: Long) {
        publishRaw(rawYaw, rawPitch, rawRoll, timestampNanos)
    }

    fun setAvailability(next: XrAvailability) {
        publishAvailability(next)
    }
}
