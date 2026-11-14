package moe.n4tsu.dextop.plasma

/** A Dextop-owned desktop shell running on the Dextop display (Plasma v1 or v2). */
internal interface DesktopShell {
    val displayId: Int
    fun start()
    fun stop(restoreWindows: Boolean = true)
    fun setAutoViewport(left: Float, top: Float, right: Float, bottom: Float) {}
    /** Where a car swipe without a start position should begin, in display pixels. */
    fun autoGestureAnchor(): android.graphics.PointF? = null
    fun showHome(): Boolean = false
    fun onVolumeChanged() {}
    fun handleKey(event: android.view.KeyEvent): Boolean
    fun onWindowsChanged()
    fun onDisplayResized(newWidth: Int, newHeight: Int)
    fun reloadSettings()
    /**
     * The screens of an extended desktop, as parts of this display: the first one carries the
     * panel. Empty: the display is one screen.
     */
    fun setOutputs(outputs: List<android.graphics.Rect>) {}

    /**
     * Phone role is "phone" during look mode. The shell must not take input
     * focus and must not be a touch target, or the phone touchscreen is
     * delivered to this display instead of the app on the phone.
     * Touchpad role never calls this.
     */
    fun setPhoneTouchscreenFree(free: Boolean) {}
}
