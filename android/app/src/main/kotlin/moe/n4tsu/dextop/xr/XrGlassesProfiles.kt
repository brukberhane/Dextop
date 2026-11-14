package moe.n4tsu.dextop.xr

import android.content.Context

/** Bumped when stored preset names were renamed. Generation 1 used ultrawide=3840 and triple=3 zones. */
internal const val XR_PRESET_GENERATION = 2

/**
 * Old saves: "ultrawide" was the 3840-wide preset (now "wide") and "triple" was
 * three zones (now "ultrawide"). A later read of the new names must not remap them,
 * so the rewrite happens once and stores [XR_PRESET_GENERATION].
 */
internal fun migrateStoredPreset(stored: String?, generation: Int): String {
    if (generation >= XR_PRESET_GENERATION) return stored ?: "single"
    return when (stored) {
        "triple" -> "ultrawide"
        "ultrawide" -> "wide"
        null, "" -> "single"
        else -> stored
    }
}

internal data class XrProfile(
    val mode: String,
    val preset: String,
    val singleScale: Float = 1f,
) {
    companion object {
        val default = XrProfile("body-lock", "single", 1f)
        val modes = setOf("body-lock", "smooth-follow", "world-lock")
        val presets = setOf("single", "wide", "ultrawide")

        fun parse(mode: String, preset: String, singleScale: Float = 1f): XrProfile? {
            if (mode !in modes || preset !in presets) return null
            return XrProfile(mode, preset, nearestSingleScale(singleScale))
        }
    }
}

internal interface XrProfileStore {
    fun read(id: String): XrProfile
    fun save(id: String, mode: String, preset: String, singleScale: Float = 1f): Boolean
}

internal class MemoryXrProfileStore : XrProfileStore {
    private val saved = HashMap<String, XrProfile>()

    override fun read(id: String): XrProfile = saved[id] ?: XrProfile.default

    override fun save(id: String, mode: String, preset: String, singleScale: Float): Boolean {
        val profile = XrProfile.parse(mode, preset, singleScale) ?: return false
        saved[id] = profile
        return true
    }
}

internal class PrefsXrProfileStore(context: Context) : XrProfileStore {
    private val prefs = context.applicationContext.getSharedPreferences("dextop_xr", Context.MODE_PRIVATE)

    override fun read(id: String): XrProfile {
        val generation = prefs.getInt("preset_gen.$id", 1)
        val rawPreset = prefs.getString("preset.$id", null)
        val preset = migrateStoredPreset(rawPreset, generation)
        if (rawPreset != null && generation < XR_PRESET_GENERATION) {
            prefs.edit()
                .putString("preset.$id", preset)
                .putInt("preset_gen.$id", XR_PRESET_GENERATION)
                .apply()
        }
        return XrProfile.parse(
            prefs.getString("mode.$id", null) ?: XrProfile.default.mode,
            if (rawPreset == null) XrProfile.default.preset else preset,
            prefs.getFloat("single_scale.$id", 1f),
        ) ?: XrProfile.default
    }

    override fun save(id: String, mode: String, preset: String, singleScale: Float): Boolean {
        val profile = XrProfile.parse(mode, preset, singleScale) ?: return false
        prefs.edit()
            .putString("mode.$id", profile.mode)
            .putString("preset.$id", profile.preset)
            .putFloat("single_scale.$id", profile.singleScale)
            .putInt("preset_gen.$id", XR_PRESET_GENERATION)
            .apply()
        return true
    }
}
