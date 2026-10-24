package com.algallad.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun poundsToMinor_wholePounds() {
        assertEquals(10000L, poundsToMinor("100") ?: -1L)
        assertEquals(500L, poundsToMinor("5") ?: -1L)
    }

    @Test
    fun poundsToMinor_withFraction() {
        assertEquals(10050L, poundsToMinor("100.50") ?: -1L)
        assertEquals(10005L, poundsToMinor("100.05") ?: -1L)
        assertEquals(10050L, poundsToMinor("100.5") ?: -1L) // 100.5 -> 100.50
    }

    @Test
    fun poundsToMinor_arabicIndicDigits() {
        // \u0661\u0662\u0663 = "123" in Arabic-Indic
        assertEquals(12300L, poundsToMinor("\u0661\u0662\u0663") ?: -1L)
    }

    @Test
    fun poundsToMinor_invalidInput() {
        assertNull(poundsToMinor(""))
        assertNull(poundsToMinor("-5"))
        assertNull(poundsToMinor("abc"))
        assertNull(poundsToMinor("1.2.3"))
        assertNull(poundsToMinor("1.234")) // more than 2 decimal places
    }

    @Test
    fun minorToPounds_formatting() {
        assertEquals("100", minorToPounds(10000L))
        assertEquals("100.50", minorToPounds(10050L))
        assertEquals("100.05", minorToPounds(10005L))
        assertEquals("0", minorToPounds(0L))
    }

    @Test
    fun formatMoney_english() {
        assertEquals("EGP 100.50", formatMoney(10050L, false))
        assertEquals("EGP 100", formatMoney(10000L, false))
    }

    @Test
    fun formatMoney_arabic() {
        val result = formatMoney(10050L, true)
        assertEquals("ج.م", result.substring(result.lastIndexOf(' ') + 1))
    }

    @Test
    fun safeLineTotal_multiply() {
        assertEquals(5000L, safeLineTotal(1000L, 5) ?: -1L)
    }

    @Test
    fun cartTotalMinor_sum() {
        val lines = listOf(1000L to 2, 2500L to 1)
        assertEquals(4500L, cartTotalMinor(lines) ?: -1L)
    }

    @Test
    fun clampQty_bounds() {
        assertEquals(1, clampQty(0))
        assertEquals(5, clampQty(5))
        assertEquals(99, clampQty(100))
    }
}