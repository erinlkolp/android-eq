package com.erinlkolp.equalizer

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class EqPreset(
    val name: String,
    val gains: IntArray, // 31 values, 0 to 240 (120 = 0 dB)
    val isCustom: Boolean = false
) {
    init {
        require(gains.size == 31) { "Preset must have exactly 31 gain values" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EqPreset
        if (name != other.name) return false
        if (!gains.contentEquals(other.gains)) return false
        if (isCustom != other.isCustom) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + gains.contentHashCode()
        result = 31 * result + isCustom.hashCode()
        return result
    }
}

object PresetManager {
    const val PREF_KEY_CUSTOM_PRESETS = "custom_presets"
    const val PREF_KEY_CURRENT_PRESET = "current_preset"

    val DEFAULT_PRESETS: List<EqPreset> = listOf(
        EqPreset(
            name = "Flat",
            gains = IntArray(31) { 120 },
            isCustom = false
        ),
        EqPreset(
            name = "Bass Boost",
            gains = intArrayOf(
                170, 175, 180, 180, 175, 170, 160, 150, 140, 130, 125, 120, 120, 120, 120, 120,
                120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120
            ),
            isCustom = false
        ),
        EqPreset(
            name = "Treble Boost",
            gains = intArrayOf(
                120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120, 120,
                120, 120, 125, 130, 135, 140, 145, 150, 155, 160, 165, 170, 175, 180, 180
            ),
            isCustom = false
        ),
        EqPreset(
            name = "Rock",
            gains = intArrayOf(
                160, 160, 155, 150, 145, 135, 125, 120, 115, 110, 110, 110, 115, 120, 125, 130,
                130, 130, 135, 140, 145, 150, 155, 160, 160, 160, 155, 155, 150, 150, 150
            ),
            isCustom = false
        ),
        EqPreset(
            name = "Vocal",
            gains = intArrayOf(
                105, 110, 115, 120, 120, 120, 120, 120, 120, 120, 125, 130, 135, 140, 145, 150,
                155, 155, 150, 145, 140, 135, 130, 125, 120, 120, 120, 115, 115, 110, 110
            ),
            isCustom = false
        ),
        EqPreset(
            name = "Electronic",
            gains = intArrayOf(
                170, 170, 165, 160, 155, 145, 135, 125, 120, 115, 115, 115, 115, 120, 120, 125,
                130, 130, 135, 140, 145, 150, 155, 160, 165, 165, 160, 160, 160, 160, 160
            ),
            isCustom = false
        ),
        EqPreset(
            name = "Acoustic",
            gains = intArrayOf(
                120, 120, 125, 130, 135, 135, 130, 125, 125, 120, 120, 120, 125, 125, 125, 130,
                130, 135, 135, 140, 140, 140, 145, 145, 140, 140, 135, 135, 130, 125, 120
            ),
            isCustom = false
        )
    )

    fun getCustomPresets(prefs: SharedPreferences): List<EqPreset> {
        val jsonStr = prefs.getString(PREF_KEY_CUSTOM_PRESETS, null) ?: return emptyList()
        val list = mutableListOf<EqPreset>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val gainsArray = obj.getJSONArray("gains")
                if (gainsArray.length() == 31) {
                    val gains = IntArray(31) { gainsArray.getInt(it) }
                    list.add(EqPreset(name = name, gains = gains, isCustom = true))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getAllPresets(prefs: SharedPreferences): List<EqPreset> {
        return DEFAULT_PRESETS + getCustomPresets(prefs)
    }

    fun saveCustomPreset(prefs: SharedPreferences, name: String, gains: IntArray): Boolean {
        if (name.isBlank() || gains.size != 31) return false
        val trimmedName = name.trim()
        val currentCustom = getCustomPresets(prefs).toMutableList()
        val existingIndex = currentCustom.indexOfFirst { it.name.equals(trimmedName, ignoreCase = true) }
        val newPreset = EqPreset(trimmedName, gains.clone(), isCustom = true)
        if (existingIndex >= 0) {
            currentCustom[existingIndex] = newPreset
        } else {
            currentCustom.add(newPreset)
        }
        saveCustomPresetsList(prefs, currentCustom)
        return true
    }

    fun deleteCustomPreset(prefs: SharedPreferences, name: String): Boolean {
        val currentCustom = getCustomPresets(prefs).toMutableList()
        val removed = currentCustom.removeAll { it.name.equals(name.trim(), ignoreCase = true) }
        if (removed) {
            saveCustomPresetsList(prefs, currentCustom)
        }
        return removed
    }

    fun renameCustomPreset(prefs: SharedPreferences, oldName: String, newName: String): Boolean {
        if (newName.isBlank()) return false
        val trimmedNew = newName.trim()
        val currentCustom = getCustomPresets(prefs).toMutableList()
        val index = currentCustom.indexOfFirst { it.name.equals(oldName.trim(), ignoreCase = true) }
        if (index < 0) return false

        currentCustom[index] = currentCustom[index].copy(name = trimmedNew)
        saveCustomPresetsList(prefs, currentCustom)

        val currentActive = prefs.getString(PREF_KEY_CURRENT_PRESET, null)
        if (currentActive.equals(oldName.trim(), ignoreCase = true)) {
            prefs.edit().putString(PREF_KEY_CURRENT_PRESET, trimmedNew).apply()
        }
        return true
    }

    private fun saveCustomPresetsList(prefs: SharedPreferences, presets: List<EqPreset>) {
        val jsonArray = JSONArray()
        for (preset in presets) {
            val obj = JSONObject()
            obj.put("name", preset.name)
            val gainsArray = JSONArray()
            for (g in preset.gains) {
                gainsArray.put(g)
            }
            obj.put("gains", gainsArray)
            jsonArray.put(obj)
        }
        prefs.edit().putString(PREF_KEY_CUSTOM_PRESETS, jsonArray.toString()).apply()
    }
}
