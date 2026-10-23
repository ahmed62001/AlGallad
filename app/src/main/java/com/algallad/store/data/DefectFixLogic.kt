package com.algallad.store.data

import com.algallad.store.util.ImageUtils

/**
 * Pure, unit-testable helpers backing the verified-defect fixes. Kept free of Firebase
 * calls so the behavioural logic can be proven on the JVM (see DefectFixTests.kt).
 */

// Length caps mirrored from firebase/firestore.rules (NOT the rules file itself).
object Limits {
    const val FULL_NAME = 120
    const val ADDRESS = 300
    const val NOTES = 500
    const val PHONE_MAX = 20
}

/** Hard clamp a value to a maximum length (rules require exact caps). */
fun clampTo(value: String, max: Int): String = if (value.length <= max) value else value.take(max)

/**
 * Which image subcollection docs to write and which stale docs to delete.
 * - write : indices [0, newCount) -> set products/{id}/images/{i}
 * - delete: indices [newCount, existingCount) -> delete products/{id}/images/{i}
 * When the caller did not change images (rewriteImages=false) this is (empty, empty):
 * the existing images/{i} docs and the doc's thumb/imageCount/imgVersion are preserved.
 */
fun imageWritePlan(newImages: List<String>, existingCount: Int, rewriteImages: Boolean): Pair<List<Int>, List<Int>> {
    if (!rewriteImages) return emptyList<Int>() to emptyList<Int>()
    val newCount = newImages.take(ImageUtils.MAX_IMAGES).size
    val writes = (0 until newCount).toList()
    val deletes = (newCount until existingCount.coerceAtLeast(newCount)).toList()
    return writes to deletes
}

/**
 * Resolve cart lines from a product cache without silently dropping any line.
 * Returns (resolvedLines, missingIds). Missing ids must be fetched by id and re-resolved
 * so the cart never hides a line the customer added.
 */
fun resolveCartLines(
    cart: Map<String, Int>,
    cache: Map<String, Product>
): Pair<List<Pair<Product, Int>>, List<String>> {
    val resolved = ArrayList<Pair<Product, Int>>()
    val missing = ArrayList<String>()
    for ((id, q) in cart) {
        val p = cache[id]
        if (p != null) resolved.add(p to q) else missing.add(id)
    }
    return resolved to missing
}

/**
 * Which fields a profile write touches. `createdAt` is written ONLY on create so that
 * the create rule (createdAt == request.time) is satisfied and the update rule
 * (createdAt == resource.data.createdAt) is not violated.
 */
fun profileWrite(exists: Boolean): Map<String, Boolean> = mapOf(
    "fullName" to true,
    "phone" to true,
    "address" to true,
    "createdAt" to !exists,
    "updatedAt" to true
)
