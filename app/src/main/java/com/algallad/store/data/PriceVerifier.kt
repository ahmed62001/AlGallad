package com.algallad.store.data

data class ProductPriceInfo(
    val exists: Boolean,
    val priceMinor: Long,
    val available: Boolean,
    val updatedAt: Long
)

enum class LinePriceStatus { OK, PRICE_CHANGED_AFTER_ORDER, MISMATCH, PRODUCT_MISSING, INVALID_QTY }
enum class PriceCheckStatus { VERIFIED, INFO_CHANGED, SUSPICIOUS }

data class LinePriceResult(
    val productId: String,
    val orderedUnitPriceMinor: Long,
    val currentUnitPriceMinor: Long?,
    val qty: Int,
    val status: LinePriceStatus
)

data class PriceCheckResult(
    val status: PriceCheckStatus,
    val diffMinor: Long,
    val orderedLinesTotalMinor: Long?,
    val currentTotalMinor: Long?,
    val lineResults: List<LinePriceResult>
)

object PriceVerifier {
    fun verifyOrder(order: Order, current: Map<String, ProductPriceInfo>): PriceCheckResult {
        val lineResults = order.items.map { line ->
            val info = current[line.productId]
            val verdict = when {
                line.qty !in 1..99 -> LinePriceStatus.INVALID_QTY
                info == null || !info.exists -> LinePriceStatus.PRODUCT_MISSING
                info.priceMinor == line.unitPriceMinor -> LinePriceStatus.OK
                info.updatedAt > order.createdAt -> LinePriceStatus.PRICE_CHANGED_AFTER_ORDER
                else -> LinePriceStatus.MISMATCH
            }
            LinePriceResult(
                productId = line.productId,
                orderedUnitPriceMinor = line.unitPriceMinor,
                currentUnitPriceMinor = info?.takeIf { it.exists }?.priceMinor,
                qty = line.qty,
                status = verdict
            )
        }

        val orderedLinesTotal = safeSum(order.items.mapNotNull { safeLineTotal(it.unitPriceMinor, it.qty) })
            ?.takeIf { order.items.all { line -> safeLineTotal(line.unitPriceMinor, line.qty) != null } }

        val currentTotals = order.items.map { line ->
            val info = current[line.productId]
            if (info == null || !info.exists) null else safeLineTotal(info.priceMinor, line.qty)
        }
        val currentTotal = if (currentTotals.any { it == null }) null else safeSum(currentTotals.filterNotNull())
        val diff = if (currentTotal != null) currentTotal - order.totalMinor else 0L

        val totalMismatch = orderedLinesTotal == null || orderedLinesTotal != order.totalMinor
        val suspicious = totalMismatch || lineResults.any { it.status in setOf(LinePriceStatus.MISMATCH, LinePriceStatus.PRODUCT_MISSING, LinePriceStatus.INVALID_QTY) }
        val informational = lineResults.any { it.status == LinePriceStatus.PRICE_CHANGED_AFTER_ORDER }
        val status = when {
            suspicious -> PriceCheckStatus.SUSPICIOUS
            informational -> PriceCheckStatus.INFO_CHANGED
            else -> PriceCheckStatus.VERIFIED
        }

        return PriceCheckResult(status, diff, orderedLinesTotal, currentTotal, lineResults)
    }
}