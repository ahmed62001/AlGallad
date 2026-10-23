package com.algallad.store.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.algallad.store.AppViewModel
import com.algallad.store.R
import com.algallad.store.data.Order
import com.algallad.store.data.formatMoney
import com.algallad.store.ui.theme.Brand
import com.algallad.store.util.WhatsAppSender
import com.algallad.store.util.fmtDate
import com.algallad.store.util.isArabic

private val STATUSES = listOf("new", "confirmed", "delivered", "cancelled")

@StringRes
private fun statusRes(s: String) = when (s) {
    "confirmed" -> R.string.status_confirmed
    "delivered" -> R.string.status_delivered
    "cancelled" -> R.string.status_cancelled
    else -> R.string.status_new
}

private fun statusColor(s: String) = when (s) {
    "confirmed" -> Brand.Info
    "delivered" -> Brand.Success
    "cancelled" -> Brand.Danger
    else -> Brand.Warning
}

@Composable
fun OrdersScreen(vm: AppViewModel, onAuth: () -> Unit = {}) {
    val admin = vm.session?.isAdmin == true
    AppScaffold(
        title = stringResource(if (admin) R.string.nav_orders_admin else R.string.nav_orders),
        actions = { LanguageAction() }
    ) { pad ->
        // Task 5: guests get a friendly sign-in prompt, not a dead end.
        if (vm.session == null && !admin) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Filled.Receipt,
                    stringResource(R.string.login_to_order),
                    stringResource(R.string.auth_login),
                    onAction = onAuth
                )
            }
            return@AppScaffold
        }
        if (vm.orders.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.Receipt, stringResource(R.string.no_orders))
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vm.orders, key = { it.id }) { o ->
                    // Task 5: placement changes animate.
                    Box(Modifier.animateItem()) { OrderCard(o, admin, vm) }
                }
            }
        }
    }
}

@Composable
private fun OrderCard(o: Order, admin: Boolean, vm: AppViewModel) {
    val ctx = LocalContext.current
    val ar = isArabic()
    var expanded by rememberSaveable(o.id) { mutableStateOf(false) }
    var showLines by rememberSaveable(o.id) { mutableStateOf(false) }
    var confirmStatus by remember { mutableStateOf<String?>(null) }
    var confirmCancel by remember { mutableStateOf(false) }
    val c = statusColor(o.status)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("#${o.shortId()}", style = MaterialTheme.typography.titleMedium)
                    Text(fmtDate(o.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (admin) Text(o.customerName, style = MaterialTheme.typography.bodyMedium)
                }
                Surface(color = c.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
                    Text(stringResource(statusRes(o.status)), Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = c, style = MaterialTheme.typography.labelMedium)
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    stringResource(if (expanded) R.string.cd_collapse_details else R.string.cd_expand_details)
                )
            }
            PriceCheckBadge(check = o.priceCheck, onTap = { showLines = true })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.total), style = MaterialTheme.typography.bodyMedium)
                Text(formatMoney(o.totalMinor, ar), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider()
                    o.items.forEach {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${it.qty} × " + if (ar) it.nameAr.ifBlank { it.nameEn } else it.nameEn.ifBlank { it.nameAr }, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(formatMoney(it.unitPriceMinor * it.qty, ar), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (admin) {
                        HorizontalDivider()
                        Text("${stringResource(R.string.phone)}: ${o.customerPhone}", style = MaterialTheme.typography.bodyMedium)
                        Text("${stringResource(R.string.address)}: ${o.address}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (o.notes.isNotBlank()) {
                        Text("${stringResource(R.string.order_notes)}: ${o.notes}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (admin) {
                        OutlinedButton(onClick = { vm.reverifyOrder(o) }, shape = MaterialTheme.shapes.medium) {
                            Text(stringResource(R.string.reverify))
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            STATUSES.forEach { s ->
                                FilterChip(
                                    selected = o.status == s,
                                    onClick = {
                                        val needConfirm = s == "cancelled" || s == "delivered" ||
                                            (s == "confirmed" && o.priceCheck?.status == "SUSPICIOUS")
                                        if (needConfirm) confirmStatus = s else vm.setOrderStatus(o.id, s)
                                    },
                                    label = { Text(stringResource(statusRes(s))) }
                                )
                            }
                        }
                    } else if (o.status == "new") {
                        OutlinedButton(onClick = { confirmCancel = true }, shape = MaterialTheme.shapes.medium) {
                            Text(stringResource(R.string.cancel_order))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.sharePdf(ctx, o) }, shape = MaterialTheme.shapes.medium) {
                            // Decorative: the button's "PDF" text announces the action.
                            Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.share_pdf))
                        }
                        if (admin) {
                            OutlinedButton(
                                onClick = {
                                    val corrected = o.priceCheck?.let { " — ${formatMoney(o.totalMinor + it.diffMinor, true)}" }.orEmpty()
                                    WhatsAppSender.openChat(ctx, o.customerPhone, "\u0637\u0644\u0628\u0643 #${o.shortId()}${corrected}")
                                },
                                shape = MaterialTheme.shapes.medium
                            ) {
                                // Decorative: the button's label announces the WhatsApp action.
                                Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp), tint = Brand.Success)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.whatsapp_customer))
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; vm.setOrderStatus(o.id, "cancelled") }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.cancel)) } },
            text = { Text(stringResource(R.string.cancel_order_confirm)) }
        )
    }
    confirmStatus?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmStatus = null },
            confirmButton = {
                TextButton(onClick = { confirmStatus = null; vm.setOrderStatus(o.id, target) }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = { TextButton(onClick = { confirmStatus = null }) { Text(stringResource(R.string.cancel)) } },
            text = { Text(stringResource(statusRes(target))) }
        )
    }
    if (showLines) {
        PriceLinesSheet(order = o, ar = ar, onClose = { showLines = false })
    }
}

@Composable
private fun PriceCheckBadge(check: com.algallad.store.data.PriceCheckSummary?, onTap: () -> Unit) {
    val (label, color) = when (check?.status) {
        "VERIFIED" -> stringResource(R.string.price_check_ok) to Brand.Success
        "INFO_CHANGED" -> stringResource(R.string.price_check_changed) to Brand.Info
        "SUSPICIOUS" -> stringResource(R.string.price_check_warn) to Brand.Danger
        else -> return
    }
    Surface(
        color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50),
        modifier = Modifier.clickable { onTap() }
    ) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PriceLinesSheet(order: Order, ar: Boolean, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.confirm)) } },
        title = { Text("#${order.shortId()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                order.items.forEach {
                    val nm = if (ar) it.nameAr.ifBlank { it.nameEn } else it.nameEn.ifBlank { it.nameAr }
                    Text("${it.qty} × $nm — " + formatMoney(it.unitPriceMinor, ar), style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
                val diff = order.priceCheck?.diffMinor ?: 0L
                Text(
                    "${stringResource(R.string.total)}: ${formatMoney(order.totalMinor, ar)}" +
                        if (diff != 0L) " → ${formatMoney(order.totalMinor + diff, ar)}" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    )
}
