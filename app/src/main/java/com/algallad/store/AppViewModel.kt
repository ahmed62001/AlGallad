package com.algallad.store

import android.app.Application
import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.algallad.store.data.*
import com.algallad.store.util.PdfGenerator
import com.algallad.store.util.WhatsAppSender
import com.algallad.store.data.Validators
import com.algallad.store.data.formatMoney
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {

    var booting by mutableStateOf(true); private set
    var busy by mutableStateOf(false); private set
    var session by mutableStateOf<Session?>(null); private set
    var products by mutableStateOf<List<Product>>(emptyList()); private set
    var productsLoaded by mutableStateOf(false); private set   // first snapshot or error arrived
    var productsError by mutableStateOf(false); private set    // catalog failed to load
    var loadingMore by mutableStateOf(false); private set
    var hasMoreProducts by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    private var productsJob: Job? = null
    private var firstPage = emptyList<Product>()
    private var firstPageCursor: com.google.firebase.firestore.DocumentSnapshot? = null
    private var moreProducts = emptyList<Product>()
    private var moreCursor: com.google.firebase.firestore.DocumentSnapshot? = null
    var orders by mutableStateOf<List<Order>>(emptyList()); private set
    var settings by mutableStateOf(StoreSettings()); private set
    var message by mutableStateOf<String?>(null)
    /**
     * Task 3: dedicated auth-error channel. Root cause of the "wrong password
     * shows nothing" bug: AppRoot's LaunchedEffect(vm.message) NULLS the shared
     * one-shot [message] while AuthScreen's LaunchedEffect(vm.message) tries to
     * copy it — whichever runs first wins, so the sticky inline card usually
     * never gets set. This separate, never-nulled-by-AppRoot state carries the
     * TYPED auth error (with field scope); the UI clears it on user edit.
     */
    var authError by mutableStateOf<AuthUiError?>(null); private set
    fun clearAuthError() { authError = null }
    private fun failAuth(t: Throwable) {
        // Log the exception CLASS only — never the message (may contain PII).
        android.util.Log.w("AlGalladAuth", "auth failed: ${t.javaClass.simpleName}")
        authError = mapAuthError(t)
        message = str(mapAuthError(t).messageRes)
    }
    private fun failAuthClient(e: AuthUiError) {
        authError = e
        message = str(e.messageRes) + if (e.detail.isNotEmpty()) " ${e.detail}" else ""
    }
    /** Set after a successful registration; AuthScreen shows the Check-your-email dialog until dismissed. */
    var pendingVerifyEmail by mutableStateOf<String?>(null); private set
    fun dismissVerifyDialog() { pendingVerifyEmail = null }

    val cart = mutableStateMapOf<String, Int>()
    private var ordersJob: Job? = null
    private val cartKey = stringPreferencesKey("cart_v1")

    // Fix 3: cache every Product we ever see (pages + search) so detail/cart resolve by id
    // even when a line came from search results or a later page not currently listed.
    private val productCache = mutableStateMapOf<String, Product>()
    private fun cacheProducts(list: List<Product>) { list.forEach { productCache[it.id] = it } }

    private fun loadCart() {
        viewModelScope.launch {
            runCatching {
                val ctx = getApplication<Application>()
                val raw = ctx.cartStore.data.first()[cartKey].orEmpty()
                if (raw.isNotBlank()) {
                    raw.split(';').forEach {
                        val kv = it.split('=')
                        val q = kv.getOrNull(1)?.toIntOrNull()
                        if (kv.size == 2 && kv[0].isNotBlank() && q != null && q in 1..99) cart[kv[0]] = q
                    }
                }
            }
        }
    }

    private fun persistCart() {
        viewModelScope.launch {
            runCatching {
                val ctx = getApplication<Application>()
                val raw = cart.entries.joinToString(";") { "${it.key}=${it.value}" }
                ctx.cartStore.edit { prefs -> prefs[cartKey] = raw }
            }
        }
    }

    init {
        cleanOldPdfs()
        startProducts()
        viewModelScope.launch { runCatching { Repo.settingsFlow().collect { settings = it } } }
        viewModelScope.launch {
            session = runCatching { Repo.loadSession() }.getOrNull()
            loadCart()
            if (session != null) startOrders()
            booting = false
        }
    }

    // First page is a live listener; pages 2+ are one-shot (free-tier read budget, see DATA_BUDGET.md).
    private fun startProducts() {
        productsJob?.cancel()
        productsJob = viewModelScope.launch {
            Repo.productsFlow().collect { r ->
                r.fold(
                    onSuccess = { (list, last) ->
                        firstPage = list
                        firstPageCursor = last
                        cacheProducts(list)
                        if (moreProducts.isEmpty()) hasMoreProducts = list.size >= 20
                        products = (list + moreProducts).distinctBy { it.id }
                        productsError = false
                        productsLoaded = true
                    },
                    onFailure = {
                        productsError = true
                        productsLoaded = true
                    }
                )
            }
        }
    }

    fun loadMoreProducts() {
        if (loadingMore || !hasMoreProducts) return
        val cursor = moreCursor ?: firstPageCursor ?: return
        loadingMore = true
        viewModelScope.launch {
            try {
                val (page, last) = Repo.loadProductsPage(after = cursor)
                moreProducts = moreProducts + page
                cacheProducts(page)
                moreCursor = last
                hasMoreProducts = page.size >= 20
                products = (firstPage + moreProducts).distinctBy { it.id }
            } catch (e: Exception) {
                message = errorText(e)
            } finally {
                loadingMore = false
            }
        }
    }

    fun refreshCatalog() {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try {
                val (list, last) = Repo.loadProductsPage()
                firstPage = list
                firstPageCursor = last
                moreProducts = emptyList()
                moreCursor = null
                hasMoreProducts = list.size >= 20
                products = list
                cacheProducts(list)
                productsError = false
                productsLoaded = true
            } catch (e: Exception) {
                message = errorText(e)
            } finally {
                refreshing = false
            }
        }
    }

    private val Context.cartStore by preferencesDataStore(name = "cart")
    private fun cleanOldPdfs() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val dir = java.io.File(getApplication<Application>().cacheDir, "pdf")
                val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
                dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
            }
        }
    }

    private fun str(@StringRes id: Int, vararg a: Any): String = getApplication<Application>().getString(id, *a)

    private fun weakPasswordText(e: Exception): String {
        // FirebaseAuthWeakPasswordException carries getReason(); keep it next to the base string.
        val reasons: String = Validators.weakPasswordDetails(e)
        val base = str(R.string.err_password_weak)
        return if (reasons.isNotEmpty()) "$base $reasons" else base
    }

    private fun errorText(e: Exception): String = when {
        // Server-side policy rejections surface here (message contains WEAK_PASSWORD or the typed
        // exception name survives R8); always map to the NEW weak-password string, never err_password_short.
        e.javaClass.name.contains("WeakPassword") || e.message.orEmpty().contains("WEAK_PASSWORD") -> weakPasswordText(e)
        e is FirebaseAuthException -> when (e.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> str(R.string.err_email_taken)
            "ERROR_WEAK_PASSWORD" -> weakPasswordText(e)
            "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD",
            "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_EMAIL" -> str(R.string.err_bad_credentials)
            else -> str(R.string.err_generic)
        }
        e is FirebaseNetworkException -> str(R.string.err_network)
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            str(R.string.err_permission)
        else -> str(R.string.err_generic)
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (e: Exception) {
                message = errorText(e)
            } finally {
                busy = false
            }
        }
    }

    private fun startOrders() {
        val s = session ?: return
        ordersJob?.cancel()
        ordersJob = viewModelScope.launch {
            runCatching {
                Repo.ordersFlow(s.isAdmin, s.uid).collect {
                    orders = it
                    if (s.isAdmin) verifyMissingPriceChecks(it)
                }
            }
        }
    }

    private suspend fun verifyMissingPriceChecks(list: List<Order>) {
        val targets = list.filter { it.priceCheck == null }.take(25)
        if (targets.isEmpty()) return
        val prices = Repo.fetchFreshPrices(targets.flatMap { o -> o.items.map { it.productId } }.distinct())
        targets.forEach { order ->
            runCatching { Repo.setPriceCheck(order.id, PriceVerifier.verifyOrder(order, prices)) }
        }
    }

    private suspend fun afterAuth() {
        session = Repo.loadSession()
        startOrders()
    }

    // ---------- Auth ----------
    // Task 3: client-side validation FIRST (no network call on bad input),
    // then typed server mapping via failAuth (never silent: authError drives
    // the inline UI, message drives the snackbar).
    fun login(email: String, p: String) {
        clearAuthError()
        validateLoginForm(email.trim(), p)?.let { failAuthClient(it); return }
        viewModelScope.launch {
            busy = true
            try {
                Repo.signIn(email.trim(), p); afterAuth()
            } catch (e: Exception) {
                failAuth(e)
            } finally {
                busy = false
            }
        }
    }

    fun registerCustomer(email: String, p: String, name: String, phone: String, address: String) {
        clearAuthError()
        validateRegisterForm(email.trim(), p, name, phone, address)?.let { failAuthClient(it); return }
        val registeredEmail = email.trim()
        viewModelScope.launch {
            busy = true
            try {
                Repo.registerCustomer(email.trim(), p, name, phone, address)
                afterAuth()
                pendingVerifyEmail = registeredEmail
                message = str(R.string.verify_email_banner)
            } catch (e: Exception) {
                failAuth(e)
            } finally {
                busy = false
            }
        }
    }

    fun resendVerification() = launchBusy { Repo.resendVerification(); message = str(R.string.verification_sent) }
    fun refreshSession() = launchBusy { session = Repo.loadSession(); if (session != null) startOrders() }
    /** Task 3: success AND failure both surface visibly; invalid email is a client error. */
    var resetSending by mutableStateOf(false); private set
    fun forgotPassword(email: String) {
        clearAuthError()
        if (!Validators.isEmail(email.trim())) {
            failAuthClient(AuthUiError(R.string.err_email_invalid, AuthErrorScope.FIELD_EMAIL))
            return
        }
        viewModelScope.launch {
            resetSending = true
            busy = true
            try {
                Repo.sendPasswordReset(email.trim())
                message = str(R.string.password_reset_generic)
            } catch (e: Exception) {
                failAuth(e)
            } finally {
                resetSending = false
                busy = false
            }
        }
    }

    fun deleteAccount(password: String, onDone: () -> Unit) = launchBusy {
        // Play policy: in-app deletion. Re-authenticate, delete users/{uid}, delete Auth user, clear local data.
        val s = session ?: return@launchBusy
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        val email = user?.email.orEmpty()
        val cred = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
        if (user != null) user.reauthenticate(cred).await()
        ordersJob?.cancel()
        Repo.deleteAccountCompletely()
        session = null
        orders = emptyList()
        cart.clear()
        persistCart()
        message = str(R.string.account_deleted)
        onDone()
    }

    fun logout() {
        ordersJob?.cancel()
        Repo.signOut()
        session = null
        orders = emptyList()
        cart.clear()
        persistCart()
    }

    fun updateProfile(name: String, phone: String, address: String) {
        val s = session ?: return
        val n = clampTo(name.trim(), Limits.FULL_NAME)
        val p = phone.trim()
        val a = clampTo(address.trim(), Limits.ADDRESS)
        if (n.isBlank()) { message = str(R.string.err_fill_all); return }
        if (!Validators.isEgyptPhone(p)) { message = str(R.string.err_phone_invalid); return }
        launchBusy {
            Repo.updateProfile(s.uid, n, p, a)
            session = Repo.loadSession()
            message = str(R.string.saved)
        }
    }

    // ---------- Cart ----------
    val cartCount: Int get() = cart.values.sum()

    /** Cache search results so a cart line added from search resolves without a reload. */
    fun cacheSearchResults(results: List<Product>) = cacheProducts(results)

    /** Resolve a product by id from cache; returns null if not yet cached. */
    fun productById(id: String): Product? = productCache[id]

    /**
     * Kick off (idempotent, best-effort) fetches for cart ids missing from the cache so the
     * cart never silently drops a line that came from search or a later page.
     */
    private fun prefetchMissing() {
        val missing = cart.keys.filter { it !in productCache }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            missing.forEach { id ->
                if (productCache[id] == null) {
                    runCatching { Repo.fetchProductById(id) }.getOrNull()?.let { productCache[id] = it }
                }
            }
        }
    }

    /**
     * Cart lines resolved from the product cache (never filtered by availability so a line
     * is never silently dropped). Any id missing from the cache triggers a background fetch;
     * those lines appear once the fetch lands (state map change recomposes the cart).
     */
    fun cartLines(): List<Pair<Product, Int>> {
        prefetchMissing()
        return resolveCartLines(cart.toMap(), productCache.toMap()).first
    }

    val cartTotalMinor: Long get() = cartLines().sumOf { it.first.priceMinor * it.second }
    val cartTotal: Double get() = cartTotalMinor / 100.0

    fun addToCart(id: String, qty: Int = 1) {
        cart[id] = minOf((cart[id] ?: 0) + qty, 99)
        persistCart()
        message = str(R.string.added_to_cart)
    }

    fun setQty(id: String, q: Int) {
        if (q <= 0) cart.remove(id) else cart[id] = minOf(q, 99)
        persistCart()
    }

    // ---------- Checkout ----------
    var openCartAfterAuth by mutableStateOf(false)  // guest checked out → after login, return to cart
    var pendingFreshTotal by mutableStateOf<Long?>(null); private set
    fun dismissPendingFresh() { pendingFreshTotal = null }

    var lastOrder by mutableStateOf<Order?>(null); private set
    var showOrderConfirmation by mutableStateOf(false)
    fun dismissOrderConfirmation() { showOrderConfirmation = false }

    private fun orderMessage(order: Order): String =
        "\u0637\u0644\u0628 \u062C\u062F\u064A\u062F #${order.shortId()} \u0645\u0646 ${order.customerName} \u2014 \u0627\u0644\u0625\u062C\u0645\u0627\u0644\u064A ${formatMoney(order.totalMinor, true)}\n" +
            "New order #${order.shortId()} from ${order.customerName} \u2014 Total ${formatMoney(order.totalMinor, false)}"

    /** Confirmation-screen retry: regenerate the PDF and reopen WhatsApp with it. */
    fun openWhatsAppAgain(ctx: Context) {
        val o = lastOrder ?: return
        launchBusy {
            val file = withContext(Dispatchers.IO) { PdfGenerator.create(ctx, o, settings) }
            WhatsAppSender.sendPdfToSeller(ctx, file, settings.whatsapp, orderMessage(o))
        }
    }

    fun checkout(ctx: Context, notes: String, onDone: () -> Unit, forceTotal: Long? = null) {
        val s = session ?: return
        val lines = cartLines()
        if (lines.isEmpty()) return
        val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (authUser?.isEmailVerified != true) { message = str(R.string.err_email_not_verified); return }
        if (settings.whatsapp.isBlank()) { message = str(R.string.err_no_whatsapp); return }
        if (s.profile.phone.isBlank() || s.profile.address.isBlank()) { message = str(R.string.err_complete_profile); return }
        launchBusy {
            val shownTotal = cartTotalMinor
            val fresh = try {
                Repo.fetchFreshPrices(lines.map { it.first.id })
            } catch (e: Exception) {
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                    message = str(R.string.offline_block_order); return@launchBusy
                }
                throw e
            }
            val unavailable = lines.any { (p, _) -> fresh[p.id]?.available != true }
            if (unavailable) { message = str(R.string.err_product_unavailable); return@launchBusy }
            val freshLines = lines.map { (p, q) ->
                val price = fresh[p.id]?.priceMinor ?: error("missing price")
                p.copy(priceMinor = price) to q
            }
            val freshTotal = freshLines.sumOf { it.first.priceMinor * it.second }
            // Customer-side price revalidation: stop + confirm if prices moved since the cart rendered.
            if (forceTotal == null && freshTotal != shownTotal) {
                pendingFreshTotal = freshTotal
                return@launchBusy
            }
            pendingFreshTotal = null
            val order = Order(
                id = Repo.newOrderId(),
                orderNo = Repo.newOrderNo(),
                userId = s.uid,
                customerName = s.profile.fullName.ifBlank { authUser?.email.orEmpty() },
                customerPhone = s.profile.phone,
                address = s.profile.address,
                notes = clampTo(notes.trim(), Limits.NOTES),
                items = freshLines.map { (p, q) ->
                    OrderItem(p.id, p.nameAr.ifBlank { p.nameEn }, p.nameEn.ifBlank { p.nameAr }, p.priceMinor, q)
                },
                totalMinor = freshLines.sumOf { it.first.priceMinor * it.second },
                createdAt = System.currentTimeMillis()
            )
            // Fix 2: rules gate order create on request.auth.token.email_verified == true.
            // Force a fresh ID token right before the write so the claim is current.
            if (!Repo.idTokenEmailVerified()) Repo.refreshIdToken()
            Repo.placeOrder(order)
            val file = withContext(Dispatchers.IO) { PdfGenerator.create(ctx, order, settings) }
            cart.clear()
            persistCart()
            WhatsAppSender.sendPdfToSeller(ctx, file, settings.whatsapp, orderMessage(order))
            lastOrder = order
            showOrderConfirmation = true
            message = str(R.string.order_sent_hint)
            onDone()
        }
    }

    fun sharePdf(ctx: Context, order: Order) {
        launchBusy {
            val file = withContext(Dispatchers.IO) { PdfGenerator.create(ctx, order, settings) }
            WhatsAppSender.sharePdf(ctx, file)
        }
    }

    // ---------- Admin ----------
    fun saveProduct(
        id: String?,
        data: Map<String, Any>,
        images: List<String>,
        existingImageCount: Int = 0,
        rewriteImages: Boolean = true,
        onDone: () -> Unit
    ) {
        launchBusy {
            Repo.saveProduct(id, data, images, existingImageCount, rewriteImages)
            message = str(R.string.saved)
            onDone()
        }
    }

    fun setAvailable(id: String, v: Boolean) = launchBusy { Repo.setAvailable(id, v) }
    fun deleteProduct(id: String) = launchBusy { Repo.deleteProduct(id); cart.remove(id) }
    fun setOrderStatus(id: String, status: String) = launchBusy { Repo.setOrderStatus(id, status) }

    fun reverifyOrder(order: Order) = launchBusy {
        val prices = Repo.fetchFreshPrices(order.items.map { it.productId }.distinct())
        Repo.setPriceCheck(order.id, PriceVerifier.verifyOrder(order, prices))
        message = str(R.string.price_verified)
    }

    fun saveSettings(s: StoreSettings) = launchBusy {
        Repo.saveSettings(s)
        message = str(R.string.saved)
    }
}
