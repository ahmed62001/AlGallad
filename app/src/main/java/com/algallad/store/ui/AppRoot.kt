package com.algallad.store.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.algallad.store.AppViewModel
import com.algallad.store.R

private data class NavTab(val route: String, val label: Int, val icon: ImageVector)

private val customerTabs = listOf(
    NavTab("shop", R.string.nav_shop, Icons.Filled.Storefront),
    NavTab("cart", R.string.nav_cart, Icons.Filled.ShoppingCart),
    NavTab("orders", R.string.nav_orders, Icons.Filled.Receipt),
    NavTab("profile", R.string.nav_profile, Icons.Filled.Person)
)
// Guest (not signed in): browse + cart only; the profile tab hosts the auth screen.
private val guestTabs = listOf(
    NavTab("shop", R.string.nav_shop, Icons.Filled.Storefront),
    NavTab("cart", R.string.nav_cart, Icons.Filled.ShoppingCart),
    NavTab("profile", R.string.nav_profile, Icons.Filled.Person)
)
private val adminTabs = listOf(
    NavTab("a_products", R.string.nav_products, Icons.Filled.Inventory2),
    NavTab("a_orders", R.string.nav_orders_admin, Icons.Filled.Receipt),
    NavTab("a_settings", R.string.nav_settings, Icons.Filled.Settings),
    NavTab("profile", R.string.nav_profile, Icons.Filled.Person)
)

@Composable
fun AppRoot(vm: AppViewModel) {
    val snack = remember { SnackbarHostState() }
    // Task 4: status/navigation icon appearance follows the ACTIVE Compose theme
    // (light or dark), updated instantly on switch without an app restart.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val view = LocalView.current
    DisposableEffect(dark) {
        val window = (view.context as? android.app.Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
        onDispose { }
    }
    // ON_RESUME recheck: refresh the session (cheap, at most once per resume) when signed in
    // but still unverified, so a verified admin stops looking like a customer.
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (user != null && !user.isEmailVerified) vm.refreshSession()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            vm.message = null
            snack.showSnackbar(it)
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        // Task 2: same single-insets rule as MainScaffold — systemBars only
        // (safeDrawing includes IME → ghost panel above the keyboard).
        contentWindowInsets = WindowInsets.systemBars
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                vm.booting -> SplashScreen()
                // Guest browsing: no session → shop + cart + auth tabs (auth at checkout, spec).
                else -> MainScaffold(vm)
            }
        }
    }
}

@Composable
private fun MainScaffold(vm: AppViewModel) {
    val nav = rememberNavController()
    val admin = vm.session?.isAdmin == true
    val tabs = when {
        admin -> adminTabs
        vm.session == null -> guestTabs
        else -> customerTabs
    }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    // Task 5: one back stack per tab (saveState/restoreState/launchSingleTop +
    // popUpTo start). Scroll positions + search text persist across tab switches
    // because each tab's state is saved, not destroyed. Re-tapping the active tab
    // pops its stack to the tab root (scroll-to-top happens naturally as the
    // root recomposes at its saved position; lists use rememberSaveable state).
    fun go(r: String) {
        if (route == r) {
            nav.popBackStack(r, inclusive = false)
            return
        }
        nav.navigate(r) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Guest tapped checkout → signed in → return to the cart.
    LaunchedEffect(vm.session) {
        if (vm.session != null && vm.openCartAfterAuth) {
            vm.openCartAfterAuth = false
            go("cart")
        }
    }

    // Part E confirmation screen: order number + what happens next + Open WhatsApp + My orders.
    val ctx = LocalContext.current
    if (vm.showOrderConfirmation) {
        vm.lastOrder?.let { o ->
            AlertDialog(
                onDismissRequest = { vm.dismissOrderConfirmation() },
                title = { Text(stringResource(R.string.confirm_order_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("#${o.shortId()}", style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.confirm_order_next), style = MaterialTheme.typography.bodyMedium)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { vm.openWhatsAppAgain(ctx) }) {
                        Text(stringResource(R.string.open_whatsapp))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { vm.dismissOrderConfirmation(); go("orders") }) {
                        Text(stringResource(R.string.nav_orders))
                    }
                }
            )
        }
    }

    Scaffold(
        // Task 2 — ONE insets strategy for the whole app (see docs/DECISIONS_V5.md):
        // windowSoftInputMode=adjustResize resizes the window; the IME inset is then
        // applied EXACTLY ONCE via imePadding() on each scrolling form container.
        // WindowInsets.safeDrawing INCLUDES the IME, so using it here would
        // double-apply the inset (ghost panel above the keyboard). systemBars only.
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            // Task 5: bottom bar hides on non-tab destinations (product detail,
            // edit) with an expand/shrink animation; tab switches fade through.
            val onTab = tabs.any { it.route == route }
            AnimatedVisibility(
                visible = onTab,
                enter = fadeIn(animationSpec = tween(250)) + expandVertically(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { go(t.route) },
                            icon = {
                                // Icon is decorative: NavigationBarItem's label announces the tab.
                                if (t.route == "cart" && vm.cartCount > 0) {
                                    // Task 5: badge animates on count change.
                                    BadgedBox(
                                        badge = {
                                            Badge {
                                                Text(
                                                    "${vm.cartCount}",
                                                    modifier = Modifier.animateContentSize(animationSpec = tween(200))
                                                )
                                            }
                                        }
                                    ) { Icon(t.icon, null) }
                                } else Icon(t.icon, null)
                            },
                            label = { Text(stringResource(t.label)) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        // Task 5: purposeful motion. Tab switches fade through (~250ms);
        // forward/detail navigation slides on the horizontal axis respecting RTL
        // (slide direction flips automatically with layout direction).
        // All are stock transition APIs: with animator duration scale 0 they
        // complete instantly and navigation still works.
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val fwdIn: EnterTransition = slideInHorizontally(animationSpec = tween(300)) { if (rtl) -it else it } + fadeIn(tween(300))
        val fwdOut: ExitTransition = slideOutHorizontally(animationSpec = tween(300)) { if (rtl) it else -it } + fadeOut(tween(300))
        val backIn: EnterTransition = slideInHorizontally(animationSpec = tween(300)) { if (rtl) it else -it } + fadeIn(tween(300))
        val backOut: ExitTransition = slideOutHorizontally(animationSpec = tween(300)) { if (rtl) -it else it } + fadeOut(tween(300))
        NavHost(
            nav, startDestination = tabs.first().route, modifier = Modifier.padding(pad),
            enterTransition = { fadeIn(animationSpec = tween(250)) },
            exitTransition = { fadeOut(animationSpec = tween(250)) }
        ) {
            composable("shop") { ShopScreen(vm) { nav.navigate("product/$it") } }
            composable("cart") {
                CartScreen(
                    vm,
                    onBrowse = { go("shop") },
                    onProfile = { go("profile") },
                    onAuth = {
                        vm.openCartAfterAuth = true
                        go("profile")
                    }
                )
            }
            composable("orders") { OrdersScreen(vm, onAuth = { go("profile") }) }
            composable("profile") {
                if (vm.session == null) AuthScreen(vm) else ProfileScreen(vm)
            }
            composable("a_products") { AdminProductsScreen(vm) { nav.navigate("edit/$it") } }
            composable("a_orders") { OrdersScreen(vm, onAuth = { go("profile") }) }
            composable("a_settings") { AdminSettingsScreen(vm) }
            composable(
                "product/{id}",
                enterTransition = { fwdIn }, exitTransition = { fwdOut },
                popEnterTransition = { backIn }, popExitTransition = { backOut }
            ) { e ->
                ProductDetailScreen(vm, e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() }, onCart = { go("cart") })
            }
            composable(
                "edit/{id}",
                enterTransition = { fwdIn }, exitTransition = { fwdOut },
                popEnterTransition = { backIn }, popExitTransition = { backOut }
            ) { e ->
                ProductEditScreen(vm, e.arguments?.getString("id").orEmpty(), onDone = { nav.popBackStack() })
            }
        }
    }
}
