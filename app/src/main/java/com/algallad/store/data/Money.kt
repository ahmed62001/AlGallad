package com.algallad.store.data

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private const val PIASTERS_PER_EGP = 100L

const val MAX_PRICE_MINOR = 100_000_000L

fun poundsToMinor(input: String): Long? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.startsWith('-')) return null
    val normalized = trimmed
        .map { ch ->
            when (ch) {
                in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                '\u066B' -> '.'
                '\u066C', ',', '\u060C', ' ' -> null
                else -> ch
            }
        }
        .filterNotNull()
        .joinToString("")
    if (normalized.isBlank()) return null
    val parts = normalized.split('.')
    if (parts.size > 2) return null
    val poundsText = parts[0].ifBlank { "0" }
    if (poundsText.any { !it.isDigit() }) return null
    val pounds = poundsText.toLongOrNull() ?: return null
    val fracRaw = parts.getOrNull(1) ?: ""
    if (fracRaw.any { !it.isDigit() }) return null
    if (fracRaw.length > 2) return null
    val piasters = fracRaw.padEnd(2, '0').take(2).toLongOrNull() ?: 0L
    val total = runCatching { Math.addExact(Math.multiplyExact(pounds, PIASTERS_PER_EGP), piasters) }.getOrNull() ?: return null
    if (total < 0 || total > MAX_PRICE_MINOR) return null
    return total
}

fun parseToMinor(input: String): Long? = poundsToMinor(input)

fun minorToPounds(minor: Long): String {
    val whole = minor / PIASTERS_PER_EGP
    val fraction = kotlin.math.abs(minor % PIASTERS_PER_EGP)
    val formattedWhole = NumberFormat.getIntegerInstance(Locale.US).format(whole)
    return if (fraction == 0L) formattedWhole else "$formattedWhole.${fraction.toString().padStart(2, '0')}"
}

fun formatMoney(minor: Long, arabic: Boolean, currencySymbol: String? = null): String {
    val amount = minorToPounds(minor)
    val symbol = currencySymbol ?: if (arabic) "\u062C.\u0645" else "EGP"
    return if (arabic) "$amount $symbol" else "$symbol $amount"
}

fun formatMoneyBigDecimal(pounds: BigDecimal, arabic: Boolean): String {
    val minor = pounds.multiply(BigDecimal(100)).toLong()
    return formatMoney(minor, arabic)
}

fun safeLineTotal(unitPriceMinor: Long, qty: Int): Long? =
    runCatching { Math.multiplyExact(unitPriceMinor, qty.toLong()) }.getOrNull()

fun safeSum(values: Iterable<Long>): Long? =
    values.fold(0L as Long?) { acc, value -> acc?.let { runCatching { Math.addExact(it, value) }.getOrNull() } }

fun clampQty(qty: Int): Int = qty.coerceIn(1, 99)

fun cartTotalMinor(lines: List<Pair<Long, Int>>): Long? {
    val totals = lines.map { (price, qty) -> safeLineTotal(price, clampQty(qty)) }
    if (totals.any { it == null }) return null
    return safeSum(totals.filterNotNull())
}
