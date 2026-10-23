package com.algallad.store.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.algallad.store.AppViewModel
import com.algallad.store.R
import com.algallad.store.data.Product
import com.algallad.store.data.Repo
import com.algallad.store.data.formatMoney
import com.algallad.store.ui.theme.Brand
import com.algallad.store.ui.theme.goldAccent
import com.algallad.store.util.isArabic

// ================ Shop =================
@Composable
fun ShopScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    // Task 2: keyboard controller for the Search IME action.
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(query) {
        // 350ms debounce, minimum 2 chars (spec).
        kotlinx.coroutines.delay(350)
        debounced = query
    }
    val ar = isArabic()
    val useServerSearch = debounced.trim().length >= 2
    var serverResults by remember { mutableStateOf<List<Product>?>(null) }
    LaunchedEffect(debounced, cat) {
        if (useServerSearch) {
            serverResults = runCatching { Repo.searchProducts(debounced.trim(), cat.ifBlank { null }) }.getOrNull()
            // Fix 3: seed the product cache from search results so detail/cart resolve by id.
            serverResults?.let { vm.cacheSearchResults(it) }
        } else serverResults = null
    }
    LaunchedEffect(vm.products) { if (!useServerSearch) serverResults = null }
    val cats = remember(vm.products) { vm.products.map { it.category.trim() }.filter { it.isNotEmpty() }.distinct() }
    val offers = remember(vm.products) { vm.products.filter { it.available && it.oldPriceMinor > it.priceMinor } }
    val base = serverResults ?: vm.products
    val list = remember(base, debounced, cat, useServerSearch) {
        base.filter {
            (cat.isEmpty() || it.category.trim() == cat) &&
                (!useServerSearch && (debounced.isBlank() ||
                    it.nameAr.contains(debounced, true) || it.nameEn.contains(debounced, true)) || useServerSearch)
        }
    }
    val name = vm.session?.profile?.fullName.orEmpty()

    AppScaffold(title = stringResource(R.string.app_name), actions = { LanguageAction() }) { pad ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = vm.refreshing,
            onRefresh = { vm.refreshCatalog() },
            state = pullState,
            modifier = Modifier.padding(pad).fillMaxSize()
        ) {
            when {
                // Task 5: skeleton shimmer instead of a bare spinner on first load.
                !vm.productsLoaded -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(6, key = { "skeleton$it" }) { ProductCardSkeleton(Modifier.animateItem()) }
                }
                // Error: catalog listener failed and nothing to show → retry via one-shot refresh.
                vm.productsError && vm.products.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        Icons.Filled.Warning,
                        stringResource(R.string.err_catalog),
                        stringResource(R.string.retry)
                    ) { vm.refreshCatalog() }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                            .background(Brush.horizontalGradient(listOf(Brand.NavyDark, Brand.Navy)))
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(stringResource(R.string.shop_greeting, name), color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.shop_sub), color = Brand.Gold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, stringResource(R.string.cd_search)) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.cd_clear_search)) } }
                        } else null,
                        singleLine = true, shape = MaterialTheme.shapes.medium,
                        // Task 2: Search IME action dismisses the keyboard.
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (cats.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { FilterChip(selected = cat.isEmpty(), onClick = { cat = "" }, label = { Text(stringResource(R.string.all)) }) }
                            items(cats) { c -> FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c) }) }
                        }
                    }
                }
            }
            if (!useServerSearch && offers.isNotEmpty() && cat.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Decorative: the "Offers" heading beside it conveys the meaning.
                        Icon(Icons.Filled.LocalOffer, null, tint = goldAccent(), modifier = Modifier.size(20.dp))
                            Text(
                                stringResource(R.string.offers),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(offers.take(10), key = { it.id }) { p ->
                                Box(Modifier.width(150.dp)) {
                                    ProductCard(p, ar, onOpen = { onOpen(p.id) }, onAdd = { vm.addToCart(p.id) }, qty = vm.cart[p.id] ?: 0, onQty = { vm.setQty(p.id, it) })
                                }
                            }
                        }
                    }
                }
            }
            if (useServerSearch && serverResults == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (list.isEmpty() && !(useServerSearch && serverResults == null)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Filled.Search, stringResource(R.string.no_products))
                }
            }
            // Task 5: animateItem on placement changes (list/grid reorder motion).
            items(list, key = { it.id }) { p ->
                Box(Modifier.animateItem()) {
                    ProductCard(p, ar, onOpen = { onOpen(p.id) }, onAdd = { vm.addToCart(p.id) }, qty = vm.cart[p.id] ?: 0, onQty = { vm.setQty(p.id, it) })
                }
            }
            if (!useServerSearch && cat.isEmpty() && vm.hasMoreProducts && list.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedButton(
                        onClick = { vm.loadMoreProducts() },
                        enabled = !vm.loadingMore,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (vm.loadingMore) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.load_more))
                    }
                }
            }
                } // LazyVerticalGrid
            } // when/else
        } // PullToRefreshBox
    } // AppScaffold
}

@Composable
fun ProductCard(p: Product, ar: Boolean, onOpen: () -> Unit, onAdd: () -> Unit, qty: Int = 0, onQty: (Int) -> Unit = {}) {
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            Base64Image(p.thumb.ifBlank { null }, Modifier.fillMaxWidth().aspectRatio(1f).alpha(if (p.available) 1f else 0.45f))
            val d = p.discountPercent()
            if (d > 0 && p.available) {
                // Discount badge: red fill + white text (spec; AA-verified).
                Surface(
                    color = Brand.Danger, shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Text("-$d%", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (!p.available) {
                Surface(
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f), shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(stringResource(R.string.unavailable), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(p.name(ar), maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(formatMoney(p.priceMinor, ar), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                    if (p.oldPriceMinor > p.priceMinor) {
                        Text(formatMoney(p.oldPriceMinor, ar), textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (p.available) {
                    // Quick "+" turns into a stepper after first add (spec); Task 5
                    // AnimatedVisibility gives visible add-to-cart feedback.
                    AnimatedVisibility(visible = qty <= 0, enter = fadeIn(tween(200)), exit = fadeOut(tween(150))) {
                        FilledIconButton(
                            onClick = onAdd, modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Brand.Gold, contentColor = Brand.OnGold)
                        ) { Icon(Icons.Filled.Add, stringResource(R.string.cd_add_to_cart)) }
                    }
                    AnimatedVisibility(
                        visible = qty > 0,
                        enter = expandHorizontally(tween(220)) + fadeIn(tween(220)),
                        exit = shrinkHorizontally(tween(180)) + fadeOut(tween(180))
                    ) {
                        QtyStepper(qty) { onQty(it) }
                    }
                }
            }
        }
    }
}

// ================ Product detail =================
@Composable
fun ProductDetailScreen(vm: AppViewModel, id: String, onBack: () -> Unit, onCart: () -> Unit) {
    val ar = isArabic()
    // Fix 3: resolve from the product cache (works even if the item came only from search).
    // If it's not cached yet, fetch it by id and reflect the result reactively.
    var fetched by remember(id) { mutableStateOf<Product?>(null) }
    var fetchDone by remember(id) { mutableStateOf(false) } // Task 5: skeleton until the attempt finishes
    LaunchedEffect(id) {
        if (vm.productById(id) == null) {
            fetched = runCatching { Repo.fetchProductById(id) }.getOrNull()
        }
        fetchDone = true
    }
    val p = vm.productById(id) ?: fetched ?: vm.products.find { it.id == id }
    var qty by rememberSaveable { mutableIntStateOf(1) }

    AppScaffold(
        title = p?.name(ar) ?: "",
        onBack = onBack,
        bottomBar = {
            if (p != null) {
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier.navigationBarsPadding().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QtyStepper(qty) { qty = it.coerceIn(1, 99) }
                        GoldButton(
                            text = stringResource(R.string.add_to_cart),
                            enabled = p.available,
                            onClick = { vm.addToCart(p.id, qty); onCart() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    ) { pad ->
        if (p == null && !fetchDone) {
            // Task 5: skeleton shimmer while the product resolves (not an error yet).
            Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
                ShimmerBox(Modifier.fillMaxWidth().aspectRatio(1f))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShimmerBox(Modifier.width(120.dp).height(28.dp))
                    ShimmerBox(Modifier.fillMaxWidth().height(40.dp))
                    ShimmerBox(Modifier.fillMaxWidth().height(90.dp))
                }
            }
        } else if (p == null) {
            EmptyState(Icons.Filled.Warning, stringResource(R.string.no_products), stringResource(R.string.back), onBack)
        } else {
            Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
                // Lazily load products/{id}/images/* (first image first); thumb shows until they arrive.
                val full by produceState(initialValue = emptyList<String>(), p.id, p.imgVersion) {
                    value = runCatching { Repo.loadProductImages(p.id, p.imgVersion) }.getOrNull().orEmpty()
                }
                val images = full.ifEmpty { listOf(p.thumb) }.filter { it.isNotBlank() }.ifEmpty { listOf("") }
                val pager = rememberPagerState(pageCount = { images.size })
                Box {
                    HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(1f)) { i ->
                        val diskKey = if (full.isNotEmpty()) "${p.id}_${p.imgVersion}_$i" else null
                        Base64Image(images[i].ifBlank { null }, Modifier.fillMaxSize(), ContentScale.Crop, diskKey)
                    }
                    if (images.size > 1) {
                        Row(Modifier.align(Alignment.BottomCenter).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(images.size) { i ->
                                Box(
                                    Modifier.size(if (pager.currentPage == i) 10.dp else 7.dp).clip(CircleShape)
                                        .background(if (pager.currentPage == i) Brand.Gold else Color.White.copy(alpha = 0.8f))
                                )
                            }
                        }
                    }
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (p.category.isNotBlank()) {
                        SuggestionChip(onClick = {}, label = { Text(p.category) })
                    }
                    Text(p.name(ar), style = MaterialTheme.typography.headlineSmall)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(formatMoney(p.priceMinor, ar), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
                        if (p.oldPriceMinor > p.priceMinor) {
                            Text(formatMoney(p.oldPriceMinor, ar), textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    if (!p.available) {
                        Text(stringResource(R.string.unavailable), color = Brand.Danger, style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider()
                    Text(stringResource(R.string.description), style = MaterialTheme.typography.titleMedium)
                    Text(p.desc(ar).ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ================ Cart =================
@Composable
fun CartScreen(vm: AppViewModel, onBrowse: () -> Unit, onProfile: () -> Unit, onAuth: () -> Unit = onProfile) {
    val ar = isArabic()
    val ctx = LocalContext.current
    val lines = vm.cartLines()
    var notes by rememberSaveable { mutableStateOf("") }
    val profile = vm.session?.profile
    val profileIncomplete = profile != null && (profile.phone.isBlank() || profile.address.isBlank())
    // Task 2: keyboard controller for the notes-field Done action.
    val keyboard = LocalSoftwareKeyboardController.current

    // Price-drift confirm: checkout re-fetched SERVER prices that differ from what the cart showed.
    vm.pendingFreshTotal?.let { fresh ->
        AlertDialog(
            onDismissRequest = { vm.dismissPendingFresh() },
            title = { Text(stringResource(R.string.prices_updated_title)) },
            confirmButton = {
                TextButton(onClick = { vm.checkout(ctx, notes, { notes = "" }, forceTotal = fresh) }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissPendingFresh() }) { Text(stringResource(R.string.cancel)) }
            },
            text = { Text(stringResource(R.string.prices_updated_body, formatMoney(fresh, ar))) }
        )
    }

    AppScaffold(
        title = stringResource(R.string.nav_cart),
        actions = { LanguageAction() },
        bottomBar = {
            if (lines.isNotEmpty()) {
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.total), style = MaterialTheme.typography.titleMedium)
                            Text(priceLabel(vm.cartTotal), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                        }
                        GoldButton(
                            // Guest: auth at checkout, then return to the cart (spec).
                            text = stringResource(if (vm.session == null) R.string.login_to_order else R.string.checkout_whatsapp),
                            loading = vm.busy,
                            onClick = {
                                if (vm.session == null) onAuth()
                                else vm.checkout(ctx, notes, { notes = "" })
                            }
                        )
                    }
                }
            }
        }
    ) { pad ->
        if (lines.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.ShoppingCart, stringResource(R.string.cart_empty), stringResource(R.string.browse_products), onBrowse)
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(lines, key = { it.first.id }) { (p, q) ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Base64Image(p.thumb.ifBlank { null }, Modifier.size(76.dp).clip(MaterialTheme.shapes.medium))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(p.name(ar), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                 Text(formatMoney(p.priceMinor * q, ar), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                                QtyStepper(q) { vm.setQty(p.id, it) }
                            }
                            IconButton(onClick = { vm.setQty(p.id, 0) }) {
                                Icon(Icons.Filled.Delete, stringResource(R.string.cd_remove_from_cart), tint = Brand.Danger)
                            }
                        }
                    }
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.delivery_info), style = MaterialTheme.typography.titleMedium)
                            if (profile != null) {
                                Text(profile.fullName.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
                                Text(profile.phone.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(profile.address.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (profileIncomplete) {
                                TextButton(onClick = onProfile) { Text(stringResource(R.string.complete_profile), color = Brand.Danger) }
                            }
                            OutlinedTextField(
                                value = notes, onValueChange = { notes = it.take(com.algallad.store.data.Limits.NOTES) },
                                label = { Text(stringResource(R.string.order_notes)) },
                                minLines = 2, shape = MaterialTheme.shapes.medium,
                                // Task 2: Done dismisses the keyboard on the notes field.
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Decorative: the WhatsApp explanation text beside it conveys the meaning.
                                Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Brand.Success, modifier = Modifier.size(20.dp))
                                Text(stringResource(R.string.whatsapp_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
