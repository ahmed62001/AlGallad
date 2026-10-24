package com.algallad.store.data

import com.algallad.store.util.ImageUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class DefectFixLogicTest {

    @Test
    fun clampTo_clamps() {
        assertEquals("he", clampTo("hello", 2))
        assertEquals("hi", clampTo("hi", 10))
        assertEquals("", clampTo("", 5))
    }

    @Test
    fun imageWritePlan_noRewrite() {
        val (writes, deletes) = imageWritePlan(listOf("a"), 5, false)
        assertEquals(0, writes.size)
        assertEquals(0, deletes.size)
    }

    @Test
    fun imageWritePlan_rewriteFewer() {
        val (writes, deletes) = imageWritePlan(listOf("a", "b"), 5, true)
        assertEquals(listOf(0, 1), writes)
        assertEquals(listOf(2, 3, 4), deletes)
    }

    @Test
    fun imageWritePlan_rewriteMore() {
        val (writes, deletes) = imageWritePlan(listOf("a", "b", "c", "d", "e"), 2, true)
        // capped at MAX_IMAGES (4)
        assertEquals(listOf(0, 1, 2, 3), writes)
        assertEquals(0, deletes.size)
    }

    @Test
    fun resolveCartLines_splitsResolvedAndMissing() {
        val cache = mapOf(
            "p1" to com.algallad.store.data.Product(id = "p1", nameAr = "أ", nameEn = "A"),
            "p2" to com.algallad.store.data.Product(id = "p2", nameAr = "ب", nameEn = "B")
        )
        val cart = mapOf("p1" to 2, "p2" to 1, "p3" to 3)
        val (resolved, missing) = resolveCartLines(cart, cache)
        assertEquals(2, resolved.size)
        assertEquals(listOf("p3"), missing)
    }

    @Test
    fun profileWrite_createdAtOnlyOnCreate() {
        assertEquals(true, profileWrite(false)["createdAt"])
        assertEquals(false, profileWrite(true)["createdAt"])
        assertEquals(true, profileWrite(true)["updatedAt"])
    }
}