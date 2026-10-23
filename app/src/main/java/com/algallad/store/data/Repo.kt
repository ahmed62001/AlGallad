package com.algallad.store.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Repo {
    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    private fun now() = System.currentTimeMillis()
    private val adminEmails: Set<String> = com.algallad.store.BuildConfig.ADMIN_EMAILS
        .split(',')
        .map { it.trim().lowercase(Locale.US) }
        .filter { it.isNotBlank() }
        .toSet()
    fun isAdminEmail(email: String?): Boolean = email?.trim()?.lowercase(Locale.US) in adminEmails

    // ---------- Auth ----------
    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    fun signOut() = auth.signOut()

    suspend fun deleteAccountCompletely() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        runCatching { db.collection("users").document(uid).delete().await() }
        user.delete().await()
        signOut()
    }

    suspend fun registerCustomer(email: String, password: String, fullName: String, phone: String, address: String) {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("no user")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(fullName.trim()).build()).await()
        user.sendEmailVerification().await()
        db.collection("users").document(user.uid).set(profileMap(fullName, phone, address)).await()
    }

    private fun profileMap(fullName: String, phone: String, address: String) = mapOf(
        "fullName" to fullName.trim(),
        "phone" to phone.trim(),
        "address" to address.trim(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    suspend fun loadSession(): Session? {
        val user = auth.currentUser ?: return null
        user.reload().await()
        // Force a fresh ID token so email_verified / custom claims are current (fixes stale-token defects).
        user.getIdToken(true).await()
        val isAdmin = isAdminEmail(user.email) && user.isEmailVerified
        val doc = db.collection("users").document(user.uid).get().await()
        val profile = runCatching { doc.toUserProfile() }.getOrNull()
            ?: UserProfile(id = user.uid, fullName = user.displayName.orEmpty())
        return Session(user.uid, isAdmin, profile)
    }

    suspend fun updateProfile(uid: String, fullName: String, phone: String, address: String) {
        val ref = db.collection("users").document(uid)
        val common = mapOf(
            "fullName" to fullName.trim(),
            "phone" to phone.trim(),
            "address" to address.trim()
        )
        val exists = ref.get().await().exists()
        if (exists) {
            // Update rule requires createdAt unchanged, so never write createdAt here.
            ref.update(common + mapOf("updatedAt" to FieldValue.serverTimestamp())).await()
        } else {
            // Create rule requires createdAt == request.time; serverTimestamp satisfies it.
            ref.set(common + mapOf("createdAt" to FieldValue.serverTimestamp(), "updatedAt" to FieldValue.serverTimestamp())).await()
        }
    }

    /** Force-refresh the current ID token (no-op when signed out). */
    suspend fun refreshIdToken() {
        auth.currentUser?.getIdToken(true)?.await()
    }

    /** True when the current user's fresh ID token carries email_verified == true. */
    suspend fun idTokenEmailVerified(): Boolean {
        val token = auth.currentUser?.getIdToken(false)?.await() ?: return false
        return token.claims["email_verified"] == true
    }

    suspend fun resendVerification() { auth.currentUser?.sendEmailVerification()?.await() }
    suspend fun sendPasswordReset(email: String) { auth.sendPasswordResetEmail(email.trim()).await() }

    // ---------- Data ----------
    fun productsFlow(): Flow<Result<Pair<List<Product>, DocumentSnapshot?>>> = callbackFlow {
        // Only listen to the first small page; subsequent pages are one-shot via loadProductsPage.
        val reg = db.collection("products").orderBy("createdAt", Query.Direction.DESCENDING).limit(20)
            .addSnapshotListener { s, e ->
                if (e != null) trySend(Result.failure(e))
                else if (s != null) trySend(Result.success(s.documents.map { it.toProduct() } to s.documents.lastOrNull()))
            }
        awaitClose { reg.remove() }
    }

    suspend fun loadProductsPage(category: String? = null, after: DocumentSnapshot? = null, limit: Long = 20): Pair<List<Product>, DocumentSnapshot?> {
        var q: Query = db.collection("products").orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
        if (!category.isNullOrBlank()) q = db.collection("products").whereEqualTo("category", category).orderBy("createdAt", Query.Direction.DESCENDING).limit(limit)
        if (after != null) q = q.startAfter(after)
        val snap = q.get(Source.SERVER).await()
        return snap.documents.map { it.toProduct() } to snap.documents.lastOrNull()
    }

    suspend fun searchProducts(query: String, category: String? = null): List<Product> {
        val words = com.algallad.store.data.searchWords(query)
        if (words.isEmpty()) return emptyList()
        val q: Query = db.collection("products")
            .whereArrayContains("searchTokens", words.first())
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(20)
        val snap = q.get(Source.SERVER).await()
        var list = snap.documents.map { it.toProduct() }
        if (!category.isNullOrBlank()) list = list.filter { it.category.trim() == category }
        if (words.size > 1) {
            list = list.filter { p ->
                val hay = com.algallad.store.data.normalizeSearchText("${p.nameAr} ${p.nameEn} ${p.category}")
                words.drop(1).all { hay.contains(it) }
            }
        }
        return list
    }

    fun settingsFlow(): Flow<StoreSettings> = callbackFlow {
        val reg = db.collection("settings").document("store").addSnapshotListener { s, _ ->
            if (s != null) trySend(runCatching { s.toStoreSettings() }.getOrNull() ?: StoreSettings())
        }
        awaitClose { reg.remove() }
    }

    fun ordersFlow(isAdmin: Boolean, uid: String): Flow<List<Order>> = callbackFlow {
        val base = db.collection("orders")
        val query = (if (isAdmin) base else base.whereEqualTo("userId", uid))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(if (isAdmin) 25 else 15)
        val reg = query.addSnapshotListener { s, _ ->
            if (s != null) trySend(s.documents.map { it.toOrderDoc() }.sortedByDescending { it.createdAt })
        }
        awaitClose { reg.remove() }
    }

    // ---------- Products (admin) ----------
    /**
     * Save a product and, atomically, its images subcollection.
     * @param images full Base64 WebP images (max 4). Written to products/{id}/images/{i} with v = imgVersion.
     * @param existingImageCount how many images/{i} docs already exist (so we can delete stale ones).
     * @param rewriteImages false on an EDIT where the admin did not touch images: keeps existing
     *        images/{i}, and the caller must keep the existing thumb/imageCount/imgVersion.
     */
    suspend fun saveProduct(
        id: String?,
        data: Map<String, Any>,
        images: List<String>,
        existingImageCount: Int = 0,
        rewriteImages: Boolean = true
    ) {
        val ref = if (id.isNullOrEmpty()) db.collection("products").document()
        else db.collection("products").document(id)
        val imgVersion = (data["imgVersion"] as? Number)?.toLong() ?: now()
        val (writes, deletes) = imageWritePlan(images, existingImageCount, rewriteImages)
        db.runBatch { batch ->
            batch.set(ref, data + mapOf("updatedAt" to FieldValue.serverTimestamp()))
            writes.forEach { index ->
                batch.set(
                    ref.collection("images").document(index.toString()),
                    mapOf("data" to images[index], "v" to imgVersion)
                )
            }
            deletes.forEach { index ->
                batch.delete(ref.collection("images").document(index.toString()))
            }
        }.await()
    }

    /** One-shot fetch of a single product by id (used to repair cache misses for detail/cart). */
    suspend fun fetchProductById(id: String): Product? {
        val doc = db.collection("products").document(id).get(Source.SERVER).await()
        return if (doc.exists()) doc.toProduct() else null
    }

    suspend fun setAvailable(id: String, available: Boolean) {
        db.collection("products").document(id).update(mapOf("available" to available, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun deleteProduct(id: String) {
        val ref = db.collection("products").document(id)
        val imgs = ref.collection("images").get().await()
        db.runBatch { batch ->
            imgs.documents.forEach { batch.delete(it.reference) }
            batch.delete(ref)
        }.await()
    }

    /** One-shot read of the product's images subcollection (cover index first), only current imgVersion. */
    suspend fun loadProductImages(productId: String, imgVersion: Long): List<String> {
        val snap = db.collection("products").document(productId).collection("images").get().await()
        return snap.documents
            .sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
            .filter { (it.getLong("v") ?: -1L) == imgVersion }
            .mapNotNull { it.getString("data") }
            .filter { it.isNotBlank() }
    }

    // ---------- Orders ----------
    fun newOrderId(): String = db.collection("orders").document().id

    fun newOrderNo(): String = "AG-${SimpleDateFormat("yyMMdd", Locale.US).format(Date())}-${(1000..9999).random()}"

    suspend fun fetchFreshPrices(productIds: Collection<String>): Map<String, ProductPriceInfo> {
        val result = mutableMapOf<String, ProductPriceInfo>()
        productIds.chunked(10).forEach { chunk ->
            val snap = db.collection("products")
                .whereIn(FieldPath.documentId(), chunk)
                .get(Source.SERVER)
                .await()
            snap.documents.forEach { doc ->
                result[doc.id] = ProductPriceInfo(
                    exists = true,
                    priceMinor = doc.getLong("priceMinor") ?: 0L,
                    available = doc.getBoolean("available") ?: false,
                    updatedAt = doc.getTimestamp("updatedAt")?.toDate()?.time ?: (doc.getLong("updatedAt") ?: 0L)
                )
            }
        }
        productIds.forEach { result.putIfAbsent(it, ProductPriceInfo(false, 0L, false, 0L)) }
        return result
    }

    suspend fun placeOrder(o: Order) {
        val orderRef = db.collection("orders").document(o.id)
        db.runBatch { batch ->
            batch.set(orderRef, o.toMap() + mapOf(
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    suspend fun setOrderStatus(id: String, status: String) {
        db.collection("orders").document(id).update(mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp())).await()
    }

    suspend fun setPriceCheck(id: String, result: PriceCheckResult) {
        db.collection("orders").document(id).update(
            mapOf(
                "priceCheck" to mapOf("status" to result.status.name, "diffMinor" to result.diffMinor, "checkedAt" to FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    // ---------- Settings (admin) ----------
    suspend fun saveSettings(s: StoreSettings) {
        db.collection("settings").document("store").set(s).await()
    }
}
