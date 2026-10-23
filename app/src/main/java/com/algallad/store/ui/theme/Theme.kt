package com.algallad.store.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algallad.store.R
import com.algallad.store.data.ThemeMode
import com.algallad.store.data.isDarkActive

// Cairo (OFL) bundled in res/font: Arabic + Latin.
val CairoFamily = FontFamily(Font(R.font.cairo, FontWeight.Normal))

/**
 * Round 6 (Task B): brand tokens sourced from docs/design/reference.png —
 * navy #0B2545 + gold #C99A2E, light bg #F5F7FA. Every text/UI pairing below is
 * proven WCAG AA by BOTH scripts/wcag_contrast.py and ContrastTest (keep in sync).
 */
object Brand {
    /** Primary brand navy (headers, primary buttons, light-theme primary). */
    val Navy = Color(0xFF0B2545)
    /** Deep navy for dark-theme backgrounds (reference dark screens). */
    val NavyDark = Color(0xFF081A31)
    /** Brand gold (CTA fills, accents). */
    val Gold = Color(0xFFC99A2E)
    /** Gold used as TEXT on light backgrounds only — passes WCAG AA (see ContrastTest). */
    val GoldOnLightText = Color(0xFF7A5B0E)
    /** Content color ON gold fills (7.1:1 on Gold). */
    val OnGold = Color(0xFF1B1400)
    /** Reference light app background (14.6:1 with TextLight). */
    val SurfaceLight = Color(0xFFF5F7FA)
    /** Light body text (14.6:1 on SurfaceLight, 15.7:1 on white). */
    val TextLight = Color(0xFF1B2430)
    /** Light secondary text (5.1:1 on SurfaceLight). */
    val MutedLight = Color(0xFF5F6B7A)
    /** Dark surface (13.2:1 with TextDark). */
    val SurfaceDark = Color(0xFF102441)
    /** Dark body text. */
    val TextDark = Color(0xFFE8EDF3)
    /** Dark secondary text (7.2:1 on SurfaceDark). */
    val MutedDark = Color(0xFF9FB2CC)
    /** Gold text accent on dark surfaces (8.3:1 on SurfaceDark). */
    val GoldDarkText = Color(0xFFE2B857)
    val Success = Color(0xFF1E9E6A)
    val Warning = Color(0xFFE08A1E)
    val Danger = Color(0xFFC93B3B)
    val Info = Color(0xFF2B6CB0)
}

/**
 * Gold for TEXT/icons on regular surfaces — AA in BOTH themes:
 * light -> GoldOnLightText on white/#F5F7FA (6.3:1 / 5.9:1),
 * dark  -> Brand.Gold on navy surfaces (6.1:1 on surface, 6.9:1 on background).
 * Use only for accents ON surfaces; on navy hero/headers keep plain [Brand.Gold]
 * (5.6:1 on navy) and on gold fills use [Brand.OnGold].
 */
@Composable
fun goldAccent(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Brand.Gold else Brand.GoldOnLightText

private val LightColors = lightColorScheme(
    primary = Brand.Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F5),
    onPrimaryContainer = Brand.Navy,
    secondary = Brand.Gold,
    onSecondary = Brand.OnGold,
    secondaryContainer = Color(0xFFFBEFD0),
    onSecondaryContainer = Color(0xFF3A2A00),
    tertiary = Brand.Info,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCE9F9),
    onTertiaryContainer = Color(0xFF10315A),
    background = Brand.SurfaceLight,
    onBackground = Brand.TextLight,
    surface = Color.White,
    onSurface = Brand.TextLight,
    surfaceVariant = Color(0xFFEEF1F5),
    onSurfaceVariant = Brand.MutedLight,
    outline = Color(0xFF7E8798),        // interactive borders: >= 3:1 vs bg+surface (WCAG 1.4.11)
    outlineVariant = Color(0xFFE7EBF0), // decorative dividers
    error = Brand.Danger,
    onError = Color.White,
    errorContainer = Color(0xFFFCE4E4),
    onErrorContainer = Color(0xFF5C1414),
    inverseSurface = Brand.TextLight,
    inverseOnSurface = Brand.SurfaceLight,
    inversePrimary = Color(0xFFA9C0E8),
    scrim = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE2B857),
    onPrimary = Brand.OnGold,
    primaryContainer = Color(0xFF243752),
    onPrimaryContainer = Brand.TextDark,
    secondary = Brand.Gold,
    onSecondary = Brand.OnGold,
    secondaryContainer = Color(0xFF3A2D10),
    onSecondaryContainer = Color(0xFFFBEFD0),
    tertiary = Color(0xFF7FB0E6),
    onTertiary = Color(0xFF06203B),
    tertiaryContainer = Color(0xFF24405F),
    onTertiaryContainer = Color(0xFFD6E7FB),
    background = Brand.NavyDark,
    onBackground = Brand.TextDark,
    surface = Brand.SurfaceDark,
    onSurface = Brand.TextDark,
    surfaceVariant = Color(0xFF17314F),
    onSurfaceVariant = Brand.MutedDark,
    outline = Color(0xFF5177AE),        // >= 3:1 vs surface+background
    outlineVariant = Color(0xFF24406B), // decorative dividers
    error = Color(0xFFFF7B7B),
    onError = Color(0xFF5A0E0E),
    errorContainer = Color(0xFF5C1414),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Brand.TextDark,
    inverseOnSurface = Brand.SurfaceDark,
    inversePrimary = Brand.Navy,
    scrim = Color.Black
)

// Slightly larger fonts with 1.5x line height for Arabic readability (Cairo supports Arabic + Latin)
private fun appType(defaultFamily: androidx.compose.ui.text.font.FontFamily) = Typography(
    headlineMedium = TextStyle(fontFamily = defaultFamily, fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = defaultFamily, fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = defaultFamily, fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = defaultFamily, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = defaultFamily, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = defaultFamily, fontSize = 16.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = defaultFamily, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = defaultFamily, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = defaultFamily, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = defaultFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = defaultFamily, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp)
)

/** Round 6 (Task B): app-level text-size preference multiplies every typestyle.
 *  Runs on 1.0 unchanged; system font scaling still applies on top (sp semantics). */
private fun scaleTextStyle(s: TextStyle, factor: Float): TextStyle =
    if (factor == 1f) s else s.copy(fontSize = s.fontSize * factor, lineHeight = s.lineHeight * factor)

fun scaleTypography(t: Typography, factor: Float): Typography {
    if (factor == 1f) return t
    fun sc(s: TextStyle) = scaleTextStyle(s, factor)
    return Typography(
        displayLarge = sc(t.displayLarge), displayMedium = sc(t.displayMedium), displaySmall = sc(t.displaySmall),
        headlineLarge = sc(t.headlineLarge), headlineMedium = sc(t.headlineMedium), headlineSmall = sc(t.headlineSmall),
        titleLarge = sc(t.titleLarge), titleMedium = sc(t.titleMedium), titleSmall = sc(t.titleSmall),
        bodyLarge = sc(t.bodyLarge), bodyMedium = sc(t.bodyMedium), bodySmall = sc(t.bodySmall),
        labelLarge = sc(t.labelLarge), labelMedium = sc(t.labelMedium), labelSmall = sc(t.labelSmall)
    )
}

@Composable
fun AlGalladTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    // Cairo default font is applied via Typography defaultFontFamily below.
    val systemDark = isSystemInDarkTheme()
    val dark = isDarkActive(mode, systemDark)
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = scaleTypography(appType(CairoFamily), fontScale),
        shapes = AppShapes,
        content = content
    )
}

