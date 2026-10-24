package com.algallad.store.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceVerifierTest {

    @Test
    fun verifyOrder_allPricesMatch() {
        val order = com.algallad.store.data.OrderDoc(
            id = "o1",
            orderNo = "AG-240101-1234",
            userId = "u1",
            customerName = "Customer",
            customerPhone = "+201012345678",
            address = "Cairo",
            items = listOf(
                com.algallad.store.data.OrderItem(productId = "p1", unitPriceMinor = 1000L, qty = 2)
            ),
            totalMinor = 2000L
        )
        val current = mapOf(
            "p1" to com.algallad.store.data.ProductPriceInfo(true, 1000L, true, 0L)
        )
        val result = PriceVerifier.verifyOrder(order, current)
        assertEquals(com.algallad.store.data.PriceCheckStatus.VERIFIED, result.status)
    }

    @Test
    fun verifyOrder_priceChanged() {
        val order = com.algallad.store.data.OrderDoc(
            id = "o1",
            orderNo = "AG-240101-1234",
            userId = "u1",
            customerName = "Customer",
            customerPhone = "+201012345678",
            address = "Cairo",
            items = listOf(
                com.algallad.store.data.OrderItem(productId = "p1", unitPriceMinor = 1000L, qty = 1)
            ),
            totalMinor = 1000L,
            createdAt = 1000L
        )
        val current = mapOf(
            "p1" to com.algallad.store.data.ProductPriceInfo(true, 1500L, true, 2000L) // updated after order
        )
        val result = PriceVerifier.verifyOrder(order, current)
        assertEquals(com.algallad.store.data.PriceCheckStatus.INFO_CHANGED, result.status)
    }

    @Test
    fun verifyOrder_productMissing() {
        val order = com.algallad.store.data.OrderDoc(
            id = "o1",
            orderNo = "AG-240101-1234",
            userId = "u1",
            customerName = "Customer",
            customerPhone = "+201012345678",
            address = "Cairo",
            items = listOf(
                com.algallad.store.data.OrderItem(productId = "p1", unitPriceMinor = 1000L, qty = 1)
            ),
            totalMinor = 1000L
        )
        val current = mapOf<String, com.algallad.store.data.ProductPriceInfo>()
        val result = PriceVerifier.verifyOrder(order, current)
        assertEquals(com.algallad.store.data.PriceCheckStatus.SUSPICIOUS, result.status)
    }
}