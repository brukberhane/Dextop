package moe.n4tsu.dextop.xr

import android.content.Context

internal data class XrProfile(val mode: String, val preset: String) {
    companion object {
        val default = XrProfile("body-lock", "single")
        val modes = setOf("body-lock", "smooth-follow", "world-lock")
        val presets = setOf("single", "ultrawide", "triple")

        fun parse(mode: String, preset: String): XrProfile? {
            if (mode !in modes || preset !in presets) return null
            return XrProfile(mode, preset)
        }
    }
}

internal interface XrProfileStore {
    fun read(id: String): XrProfile
    fun save(id: String, mode: String, preset: String): Boolean
}

internal class MemoryXrProfileStore : XrProfileStore {
    private val saved = HashMap<String, XrProfile>()

    override fun read(id: String): XrProfile = saved[id] ?: XrProfile.default

    override fun save(id: String, mode: String, preset: String): Boolean {
        val profile = XrProfile.parse(mode, preset) ?: return false
        saved[id] = profile
        return true
    }
}

internal class PrefsXrProfileStore(context: Context) : XrProfileStore {
    private val prefs = context.applicationContext.getSharedPreferences("dextop_xr", Context.MODE_PRIVATE)

    override fun read(id: String): XrProfile =
        XrProfile.parse(
            prefs.getString("mode.$id", null) ?: XrProfile.default.mode,
            prefs.getString("preset.$id", null) ?: XrProfile.default.preset,
        ) ?: XrProfile.default

    override fun save(id: String, mode: String, preset: String): Boolean {
        val profile = XrProfile.parse(mode, preset) ?: return false
        prefs.edit().putString("mode.$id", profile.mode).putString("preset.$id", profile.preset).apply()
        return true
    }
}
