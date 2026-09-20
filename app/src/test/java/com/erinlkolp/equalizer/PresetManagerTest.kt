package com.erinlkolp.equalizer

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PresetManagerTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
    }

    @Test
    fun testDefaultPresets_existAndValid() {
        val defaults = PresetManager.DEFAULT_PRESETS
        assertTrue(defaults.isNotEmpty())

        val flat = defaults.find { it.name == "Flat" }
        assertNotNull(flat)
        assertEquals(31, flat!!.gains.size)
        assertTrue(flat.gains.all { it == 120 })

        for (preset in defaults) {
            assertEquals("Preset ${preset.name} must have 31 bands", 31, preset.gains.size)
            for (gain in preset.gains) {
                assertTrue("Gain $gain in ${preset.name} should be between 0 and 240", gain in 0..240)
            }
        }
    }

    @Test
    fun testSaveAndGetCustomPreset() {
        val customGains = IntArray(31) { 150 }
        val saved = PresetManager.saveCustomPreset(fakePrefs, "My Bass", customGains)
        assertTrue(saved)

        val customPresets = PresetManager.getCustomPresets(fakePrefs)
        assertEquals(1, customPresets.size)
        val myPreset = customPresets[0]
        assertEquals("My Bass", myPreset.name)
        assertTrue(myPreset.isCustom)
        assertTrue(myPreset.gains.all { it == 150 })

        val allPresets = PresetManager.getAllPresets(fakePrefs)
        assertEquals(PresetManager.DEFAULT_PRESETS.size + 1, allPresets.size)
    }

    @Test
    fun testOverwriteCustomPreset() {
        val gains1 = IntArray(31) { 130 }
        PresetManager.saveCustomPreset(fakePrefs, "Profile1", gains1)

        val gains2 = IntArray(31) { 140 }
        PresetManager.saveCustomPreset(fakePrefs, "Profile1", gains2)

        val customPresets = PresetManager.getCustomPresets(fakePrefs)
        assertEquals(1, customPresets.size)
        assertEquals("Profile1", customPresets[0].name)
        assertTrue(customPresets[0].gains.all { it == 140 })
    }

    @Test
    fun testDeleteCustomPreset() {
        val gains = IntArray(31) { 160 }
        PresetManager.saveCustomPreset(fakePrefs, "ToDelete", gains)
        assertEquals(1, PresetManager.getCustomPresets(fakePrefs).size)

        val deleted = PresetManager.deleteCustomPreset(fakePrefs, "ToDelete")
        assertTrue(deleted)
        assertTrue(PresetManager.getCustomPresets(fakePrefs).isEmpty())
    }

    @Test
    fun testRenameCustomPreset() {
        val gains = IntArray(31) { 150 }
        PresetManager.saveCustomPreset(fakePrefs, "Old Title", gains)
        val renamed = PresetManager.renameCustomPreset(fakePrefs, "Old Title", "New Title")
        assertTrue(renamed)

        val customPresets = PresetManager.getCustomPresets(fakePrefs)
        assertEquals(1, customPresets.size)
        assertEquals("New Title", customPresets[0].name)
    }

    @Test
    fun testSaveCustomPreset_invalidInput() {
        assertFalse(PresetManager.saveCustomPreset(fakePrefs, "", IntArray(31) { 120 }))
        assertFalse(PresetManager.saveCustomPreset(fakePrefs, "   ", IntArray(31) { 120 }))
        assertFalse(PresetManager.saveCustomPreset(fakePrefs, "Test", IntArray(10) { 120 }))
    }

    // In-memory fake implementation of SharedPreferences for JVM tests
    class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST") (data[key] as? MutableSet<String> ?: defValues)
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removes = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) removes.add(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                clearAll = true
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) storage.clear()
                removes.forEach { storage.remove(it) }
                storage.putAll(pending)
            }
        }
    }
}
