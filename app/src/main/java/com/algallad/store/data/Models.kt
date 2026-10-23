package com.algallad.store.data

import androidx.annotation.Keep
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot

// Firestore key contracts (parity with firebase/firestore.rules).
val PRODUCT_KEYS = setOf(
    "nameAr", "nameEn", "descAr", "descEn", "category", "priceMinor", "oldPriceMinor",
    "available", "imageCount", "imgVersion", "thumb", "searchTokens", "createdAt", "updatedAt"
)

@Keep
data class Product(
    @DocumentId val id: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val descAr: String = "",
    val descEn: String = "",
    val category: String = "",
    val priceMinor: Long = 0L,
    val oldPriceMinor: Long = 0L,
    val available: Boolean = true,
    val imageCount: Int = 0,
    val imgVersion: Long = 0L,
    val thumb: String = "",
    val searchTokens: List<String> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    @Deprecated("Use priceMinor")
    val price: Double get() = priceMinor / 100.0
    @Deprecated("Use oldPriceMinor")
    val oldPrice: Double get() = oldPriceMinor / 100.0
    @Deprecated("Use thumb/images subcollection")
    val images: List<String> get() = if (thumb.isBlank()) emptyList() else listOf(thumb)
    fun name(ar: Boolean): String = if (ar) nameAr.ifBlank { nameEn } else nameEn.ifBlank { nameAr }
    fun desc(ar: Boolean): String = if (ar) descAr.ifBlank { descEn } else descEn.ifBlank { descAr }
    fun discountPercent(): Int =
        if (oldPriceMinor > priceMinor && priceMinor > 0) (((oldPriceMinor - priceMinor) * 100) / oldPriceMinor).toInt() else 0
}

@Keep
data class ProductImage(
    @DocumentId val id: String = "",
    val data: String = "",
    val v: Long = 0L
)

@Keep
data class UserProfile(
    @DocumentId val id: String = "",
    val fullName: String = "",
    val phone: String = "",
    val address: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Keep
data class OrderItem(
    val productId: String = "",
    val nameAr: String = "",
    val nameEn: String = "",
    val unitPriceMinor: Long = 0L,
    val qty: Int = 1
) {
    @Deprecated("Use nameAr")
    val name: String get() = nameAr
    @Deprecated("Use unitPriceMinor")
    val price: Double get() = unitPriceMinor / 100.0
}

data class CartLine(
    val productId: String = "",
    val qty: Int = 1
)

@Keep
data class PriceCheck(
    val status: String = "",
    val diffMinor: Long = 0L,
    val checkedAt: Long = 0L
)

// Backwards-compat alias: codebase references both names.
typealias PriceCheckSummary = PriceCheck

@Keep
data class OrderDoc(
    @DocumentId val id: String = "",
    val orderNo: String = "",
    val userId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val address: String = "",
    val notes: String = "",
    val items: List<OrderItem> = emptyList(),
    val totalMinor: Long = 0L,
    val status: String = "new",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val priceCheck: PriceCheckSummary? = null
) {
    @Deprecated("Use totalMinor")
    val total: Double get() = totalMinor / 100.0
    fun shortId(): String = orderNo.ifBlank { id.take(6).uppercase() }

    fun toMap(): Map<String, Any?> = mapOf(
        "orderNo" to orderNo,
        "userId" to userId,
        "customerName" to customerName,
        "customerPhone" to customerPhone,
        "address" to address,
        "notes" to notes,
        "items" to items.map {
            mapOf(
                "productId" to it.productId,
                "nameAr" to it.nameAr,
                "nameEn" to it.nameEn,
                "unitPriceMinor" to it.unitPriceMinor,
                "qty" to it.qty
            )
        },
        "totalMinor" to totalMinor,
        "status" to status,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        val ALLOWED_KEYS = setOf(
            "orderNo", "userId", "customerName", "customerPhone", "address", "notes",
            "items", "totalMinor", "status", "createdAt", "updatedAt"
        )
    }
}

// Backwards-compat alias: UI/VM reference Order; spec canonical name is OrderDoc.
typealias Order = OrderDoc

@Keep
data class StoreSettings(
    val whatsapp: String = "",
    val storeNameAr: String = "",
    val storeNameEn: String = "",
    val address: String = ""
)

data class Session(
    val uid: String,
    val isAdmin: Boolean,
    val profile: UserProfile
)


private fun tsMillis(doc: DocumentSnapshot, key: String): Long =
    doc.getTimestamp(key)?.toDate()?.time ?: (doc.getLong(key) ?: 0L)

fun DocumentSnapshot.toProduct(): Product = Product(
    id = id,
    nameAr = getString("nameAr").orEmpty(),
    nameEn = getString("nameEn").orEmpty(),
    descAr = getString("descAr").orEmpty(),
    descEn = getString("descEn").orEmpty(),
    category = getString("category").orEmpty(),
    priceMinor = getLong("priceMinor") ?: 0L,
    oldPriceMinor = getLong("oldPriceMinor") ?: 0L,
    available = getBoolean("available") ?: true,
    imageCount = (getLong("imageCount") ?: 0L).toInt(),
    imgVersion = getLong("imgVersion") ?: 0L,
    thumb = getString("thumb").orEmpty(),
    searchTokens = (get("searchTokens") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
    createdAt = getLong("createdAt") ?: 0L,
    updatedAt = tsMillis(this, "updatedAt")
)

fun DocumentSnapshot.toUserProfile(): UserProfile = UserProfile(
    id = id,
    fullName = getString("fullName").orEmpty(),
    phone = getString("phone").orEmpty(),
    address = getString("address").orEmpty(),
    createdAt = tsMillis(this, "createdAt"),
    updatedAt = tsMillis(this, "updatedAt")
)

fun DocumentSnapshot.toStoreSettings(): StoreSettings = StoreSettings(
    whatsapp = getString("whatsapp").orEmpty(),
    storeNameAr = getString("storeNameAr").orEmpty(),
    storeNameEn = getString("storeNameEn").orEmpty(),
    address = getString("address").orEmpty()
)


private fun mapToOrderItem(m: Map<*, *>?): OrderItem = OrderItem(
    productId = m?.get("productId") as? String ?: "",
    nameAr = m?.get("nameAr") as? String ?: "",
    nameEn = m?.get("nameEn") as? String ?: "",
    unitPriceMinor = (m?.get("unitPriceMinor") as? Number)?.toLong() ?: 0L,
    qty = (m?.get("qty") as? Number)?.toInt() ?: 1
)

private fun mapToPriceCheck(m: Map<*, *>?): PriceCheck? {
    if (m == null) return null
    val checked = (m["checkedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
        ?: (m["checkedAt"] as? Number)?.toLong() ?: 0L
    return PriceCheck(
        status = m["status"] as? String ?: "",
        diffMinor = (m["diffMinor"] as? Number)?.toLong() ?: 0L,
        checkedAt = checked
    )
}

fun DocumentSnapshot.toOrderDoc(): OrderDoc {
    val items = (get("items") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let(::mapToOrderItem) } ?: emptyList()
    return OrderDoc(
        id = id,
        orderNo = getString("orderNo").orEmpty(),
        userId = getString("userId").orEmpty(),
        customerName = getString("customerName").orEmpty(),
        customerPhone = getString("customerPhone").orEmpty(),
        address = getString("address").orEmpty(),
        notes = getString("notes").orEmpty(),
        items = items,
        totalMinor = getLong("totalMinor") ?: 0L,
        status = getString("status") ?: "new",
        createdAt = tsMillis(this, "createdAt"),
        updatedAt = tsMillis(this, "updatedAt"),
        priceCheck = mapToPriceCheck(get("priceCheck") as? Map<*, *>)
    )
}
