package com.algallad.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePrefsTest {

    @Test
    fun coerceFontScale_nullAndNaN() {
        assertEquals(DEFAULT_FONT_SCALE, coerceFontScale(null), 0.001f)
        assertEquals(DEFAULT_FONT_SCALE, coerceFontScale(Float.NaN), 0.001f)
    }

    @Test
    fun coerceFontScale_clamps() {
        assertEquals(MIN_FONT_SCALE, coerceFontScale(0.5f), 0.001f)
        assertEquals(MAX_FONT_SCALE, coerceFontScale(2.0f), 0.001f)
        assertEquals(1.25f, coerceFontScale(1.25f), 0.001f)
    }

    @Test
    fun themeModeFromName_mapping() {
        assertEquals(ThemeMode.LIGHT, themeModeFromName("light"))
        assertEquals(ThemeMode.DARK, themeModeFromName("dark"))
        assertEquals(ThemeMode.SYSTEM, themeModeFromName("system"))
        assertEquals(ThemeMode.SYSTEM, themeModeFromName("unknown"))
        assertEquals(ThemeMode.SYSTEM, themeModeFromName(null))
    }

    @Test
    fun themeModeToName_mapping() {
        assertEquals("light", themeModeToName(ThemeMode.LIGHT))
        assertEquals("dark", themeModeToName(ThemeMode.DARK))
        assertEquals("system", themeModeToName(ThemeMode.SYSTEM))
    }

    @Test
    fun isDarkActive_resolution() {
        assertFalse(isDarkActive(ThemeMode.LIGHT, true))
        assertTrue(isDarkActive(ThemeMode.DARK, false))
        assertTrue(isDarkActive(ThemeMode.SYSTEM, true))
        assertFalse(isDarkActive(ThemeMode.SYSTEM, false))
    }
}