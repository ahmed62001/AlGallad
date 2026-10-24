package com.algallad.store.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTokensTest {

    @Test
    fun normalizeSearchText_lowercasesAndCollapses() {
        assertEquals("hello world", normalizeSearchText("Hello   World"))
    }

    @Test
    fun searchWords_filtersShortWords() {
        assertEquals(listOf("hello", "world"), searchWords("a hello world b"))
    }

    @Test
    fun buildSearchTokens_generatesPrefixes() {
        val tokens = buildSearchTokens("apple")
        assertTrue(tokens.contains("ap"))
        assertTrue(tokens.contains("app"))
        assertTrue(tokens.contains("apple"))
    }

    @Test
    fun buildSearchTokens_respectsMaxTokens() {
        val tokens = buildSearchTokens("abcdefghij", maxTokens = 5)
        assertEquals(5, tokens.size)
    }

    @Test
    fun buildSearchTokens_multipleFields() {
        val tokens = buildSearchTokens("red shoes", "blue shoes")
        assertTrue(tokens.contains("shoe"))
        assertTrue(tokens.contains("shoes"))
    }
}
