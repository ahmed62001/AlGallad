package com.algallad.store.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Task 4: persisted light/dark/system choice. Zero new dependencies — the app
 * already uses DataStore (cart); this is a second preferences file. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.settingsStore by preferencesDataStore(name = "settings")
private val THEME_KEY = stringPreferencesKey("theme_mode")
private val FONT_SCALE_KEY = floatPreferencesKey("font_scale")

/** App-level text-size multiplier bounds (Round 6, Task B). System font scaling still
 *  applies ON TOP (sp units), so this is an extra user preference, never a replacement. */
const val MIN_FONT_SCALE = 0.90f
const val MAX_FONT_SCALE = 1.40f
const val DEFAULT_FONT_SCALE = 1.00f

/** Pure (unit-tested): stored font scale -> valid value. Missing/NaN/out-of-range -> 1.0. */
fun coerceFontScale(raw: Float?): Float = when {
    raw == null || raw.isNaN() -> DEFAULT_FONT_SCALE
    else -> raw.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
}

/** Pure mapping (unit-tested): stored string <-> ThemeMode. Unknown -> SYSTEM. */
fun themeModeFromName(raw: String?): ThemeMode = when (raw?.lowercase()) {
    "light" -> ThemeMode.LIGHT
    "dark" -> ThemeMode.DARK
    else -> ThemeMode.SYSTEM
}

fun themeModeToName(mode: ThemeMode): String = when (mode) {
    ThemeMode.LIGHT -> "light"
    ThemeMode.DARK -> "dark"
    ThemeMode.SYSTEM -> "system"
}

fun themeModeFlow(ctx: Context): Flow<ThemeMode> =
    ctx.settingsStore.data.map { themeModeFromName(it[THEME_KEY]) }

suspend fun readThemeMode(ctx: Context): ThemeMode =
    ctx.settingsStore.data.map { themeModeFromName(it[THEME_KEY]) }.first()

suspend fun writeThemeMode(ctx: Context, mode: ThemeMode) {
    ctx.settingsStore.edit { it[THEME_KEY] = themeModeToName(mode) }
}

fun fontScaleFlow(ctx: Context): Flow<Float> =
    ctx.settingsStore.data.map { coerceFontScale(it[FONT_SCALE_KEY]) }

suspend fun readFontScale(ctx: Context): Float =
    ctx.settingsStore.data.map { coerceFontScale(it[FONT_SCALE_KEY]) }.first()

suspend fun writeFontScale(ctx: Context, scale: Float) {
    ctx.settingsStore.edit { it[FONT_SCALE_KEY] = coerceFontScale(scale) }
}

/** Pure resolution (unit-tested): effective dark flag from mode + system state. */
fun isDarkActive(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> systemDark
}
