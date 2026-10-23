package com.algallad.store.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.LocaleListCompat
import com.algallad.store.data.formatMoney
import com.algallad.store.data.minorToPounds
import com.algallad.store.data.poundsToMinor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun isArabic(): Boolean = LocalConfiguration.current.locales[0].language == "ar"

fun setAppLanguage(tag: String) {
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
}

fun fmtMoney(v: Double): String =
    if (v % 1.0 == 0.0) String.format(Locale.US, "%,.0f", v) else String.format(Locale.US, "%,.2f", v)

fun fmtMoneyMinor(v: Long, arabic: Boolean = false): String = formatMoney(v, arabic)

fun fmtMoneyAmountMinor(v: Long): String = minorToPounds(v)

fun fmtDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(ms))

/** Accepts Arabic-Indic digits and the Arabic decimal separator, returns a plain number. */
fun parseNumber(s: String): Double? {
    val sb = StringBuilder()
    for (ch in s.trim()) {
        when (ch) {
            in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
            in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
            '\u066B', '\u066C', ',', '\u060C' -> sb.append('.')
            else -> sb.append(ch)
        }
    }
    return sb.toString().toDoubleOrNull()
}

fun parseMoneyMinor(s: String): Long? = poundsToMinor(s)
