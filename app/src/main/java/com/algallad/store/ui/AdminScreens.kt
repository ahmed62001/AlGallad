package com.algallad.store.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.algallad.store.AppViewModel
import com.algallad.store.R
import com.algallad.store.data.buildSearchTokens
import com.algallad.store.data.formatMoney
import com.algallad.store.data.StoreSettings
import com.algallad.store.ui.theme.Brand
import com.algallad.store.util.ImageUtils
import com.algallad.store.util.isArabic
import com.algallad.store.util.fmtMoneyAmountMinor
import com.algallad.store.util.parseMoneyMinor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ================ Admin product list ================
@Composable
fun AdminProductsScreen(vm: AppViewModel, onEdit: (String) -> Unit) {
    val ar = isArabic()
    var toDelete by remember { mutableStateOf<String?>(null) }

    AppScaffold(
        title = stringResource(R.string.nav_products),
        actions = { LanguageAction() },
        fab = {
            ExtendedFloatingActionButton(
                onClick = { onEdit("new") },
                containerColor = Brand.Gold, contentColor = Brand.OnGold,
                // Icon is decorative: the FAB's text label announces the action.
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.add_product)) }
            )
        }
    ) { pad ->
        if (vm.products.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Filled.Inventory2, stringResource(R.string.no_products_admin))
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vm.products, key = { it.id }) { p ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Base64Image(p.thumb.ifBlank { null }, Modifier.size(72.dp).clip(MaterialTheme.shapes.medium))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(p.name(ar), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                Text(formatMoney(p.priceMinor, ar), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(checked = p.available, onCheckedChange = { vm.setAvailable(p.id, it) }, modifier = Modifier.height(28.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(if (p.available) R.string.available else R.string.unavailable),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Column {
                                IconButton(onClick = { onEdit(p.id) }) { Icon(Icons.Filled.Edit, stringResource(R.string.cd_edit)) }
                                IconButton(onClick = { toDelete = p.id }) { Icon(Icons.Filled.Delete, stringResource(R.string.cd_delete), tint = Brand.Danger) }
                            }
                        }
                    }
                }
            }
        }
    }

    toDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.delete_product)) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteProduct(id); toDelete = null }) {
                    Text(stringResource(R.string.delete), color = Brand.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

// ================ Add / edit product =================
/** Maps an ImageFailure to a localized message resource. Pure. */
fun imageFailureMessage(f: ImageUtils.ImageFailure): Int = when (f) {
    ImageUtils.ImageFailure.UNREADABLE -> R.string.err_image_unreadable
    ImageUtils.ImageFailure.DECODE_ERROR -> R.string.err_image_decode
    ImageUtils.ImageFailure.OVER_BUDGET -> R.string.err_image_too_big
    ImageUtils.ImageFailure.UNKNOWN -> R.string.err_image_failed
    ImageUtils.ImageFailure.CANCELLED -> R.string.err_image_failed // never shown; scope-cancelled
}
@Composable
fun ProductEditScreen(vm: AppViewModel, id: String, onDone: () -> Unit) {
    val existing = remember(id) { vm.products.find { it.id == id } }
    var nameAr by rememberSaveable { mutableStateOf(existing?.nameAr ?: "") }
    var nameEn by rememberSaveable { mutableStateOf(existing?.nameEn ?: "") }
    var descAr by rememberSaveable { mutableStateOf(existing?.descAr ?: "") }
    var descEn by rememberSaveable { mutableStateOf(existing?.descEn ?: "") }
    var category by rememberSaveable { mutableStateOf(existing?.category ?: "") }
    var price by rememberSaveable { mutableStateOf(existing?.priceMinor?.takeIf { it > 0 }?.let { fmtMoneyAmountMinor(it).replace(",", "") } ?: "") }
    var oldPrice by rememberSaveable { mutableStateOf(existing?.oldPriceMinor?.takeIf { it > 0 }?.let { fmtMoneyAmountMinor(it).replace(",", "") } ?: "") }
    var available by rememberSaveable { mutableStateOf(existing?.available ?: true) }
    // Full images → products/{id}/images/{index} (WebP ≤720px, ≤120KB).
    val images = remember { mutableStateListOf<String>() }
    // Cover thumbnail → product doc `thumb` (WebP ≤240px, ≤15KB), parallel to images.
    val thumbs = remember { mutableStateListOf<String>() }
    // Fix 1: track whether the admin actually changed images this session.
    var imagesDirty by remember(id) { mutableStateOf(existing == null) }
    var processing by remember { mutableStateOf(false) }
    var pendingCount by remember { mutableIntStateOf(0) } // Task 1: per-image progress
    var error by remember { mutableStateOf<String?>(null) }
    // Round 6: when EVERY item of a pick batch fails, offer a visible file-browser fallback.
    var showFilesFallback by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val errName = stringResource(R.string.err_product_name)
    val errPrice = stringResource(R.string.err_product_price)
    val limitMsg = stringResource(R.string.err_image_limit, ImageUtils.MAX_IMAGES)
    // Task 4/5 lint-clean: resolve localized failure strings via stringResource
    // (NOT ctx.getString — LocalContext resource reads go stale on config change).
    val msgUnreadable = stringResource(R.string.err_image_unreadable)
    val msgDecode = stringResource(R.string.err_image_decode)
    val msgTooBig = stringResource(R.string.err_image_too_big)
    val msgFailed = stringResource(R.string.err_image_failed)
    fun photoMsg(f: ImageUtils.ImageFailure): String = when (f) {
        ImageUtils.ImageFailure.UNREADABLE -> msgUnreadable
        ImageUtils.ImageFailure.DECODE_ERROR -> msgDecode
        ImageUtils.ImageFailure.OVER_BUDGET -> msgTooBig
        ImageUtils.ImageFailure.CANCELLED -> msgFailed // never shown; scope-cancelled
        ImageUtils.ImageFailure.UNKNOWN -> msgFailed
    }

    // Task 1 + Round 6: every failure path shows a localized message (inline error
    // text) and logs the stage with Log.e (no PII — scheme + mime only, never the
    // URI or file names; full diagnostics inside ImageUtils, tag AlGalladPhoto).
    // Processing is SEQUENTIAL (bounded parallelism = 1) so low-RAM devices never
    // decode several full-size photos at once. pickAndCompress reads the provider
    // stream exactly once (temp-file copy) and produces full + thumb from the file.
    fun processUris(uris: List<Uri>) {
        val fresh = uris.filter { u -> u != Uri.EMPTY }
        if (fresh.isEmpty()) return
        val room = ImageUtils.MAX_IMAGES - images.size - pendingCount
        if (room <= 0) { error = limitMsg; return }
        val accepted = fresh.take(room)
        if (accepted.size < fresh.size) error = limitMsg
        pendingCount += accepted.size
        processing = true
        var batchFailures = 0
        scope.launch(Dispatchers.Default) {
            for (u in accepted) {
                val pick = ImageUtils.pickAndCompress(ctx, u)
                val full = pick.full
                val thumb = if (full.isSuccess) pick.thumb else full
                withContext(Dispatchers.Main) {
                    pendingCount--
                    val fail = (full.exceptionOrNull() as? ImageUtils.PhotoException)
                        ?: (thumb.exceptionOrNull() as? ImageUtils.PhotoException)
                    if (fail != null) {
                        android.util.Log.e(
                            "AlGalladPhoto",
                            "pick failed: ${fail.failure} stage=${fail.stage} uriScheme=${u.scheme} cause=${fail.cause?.javaClass?.simpleName}"
                        )
                        if (fail.failure != ImageUtils.ImageFailure.CANCELLED) {
                            error = photoMsg(fail.failure)
                            batchFailures++
                        }
                    } else {
                        images.add(full.getOrNull().orEmpty())
                        thumbs.add(thumb.getOrNull() ?: full.getOrNull().orEmpty())
                        imagesDirty = true
                    }
                    if (pendingCount <= 0) {
                        pendingCount = 0
                        processing = false
                        if (accepted.isNotEmpty() && batchFailures >= accepted.size) {
                            showFilesFallback = true
                        }
                    }
                }
            }
        }
    }

    // Modern photo picker, registered unconditionally at the top level of the
    // composition (never inside a conditional/Dialog). Fallback for devices
    // without the system picker: ACTION_GET_CONTENT multi-select.
    val fallbackPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        processUris(uris)
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ImageUtils.MAX_IMAGES)
    ) { uris -> processUris(uris) }
    fun launchPicker() {
        error = null
        showFilesFallback = false
        if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(ctx)) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            fallbackPicker.launch("image/*")
        }
    }

    AppScaffold(
        title = stringResource(if (existing == null) R.string.add_product else R.string.edit_product),
        onBack = onDone,
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.navigationBarsPadding().padding(12.dp)) {
                    PrimaryButton(
                        text = stringResource(R.string.save),
                        loading = vm.busy || processing,
                        onClick = {
                            val pr = parseMoneyMinor(price)
                            when {
                                nameAr.isBlank() && nameEn.isBlank() -> error = errName
                                pr == null || pr <= 0 -> error = errPrice
                                else -> {
                                    error = null
                                    val rewriteImages = imagesDirty || existing == null
                                    // On EDIT with unchanged images, keep existing thumb/imageCount/imgVersion.
                                    val imgVersion = if (rewriteImages) System.currentTimeMillis()
                                        else (existing?.imgVersion ?: System.currentTimeMillis())
                                    val thumb = if (rewriteImages) thumbs.firstOrNull().orEmpty()
                                        else (existing?.thumb ?: "")
                                    val imageCount = if (rewriteImages) images.size
                                        else (existing?.imageCount ?: 0)
                                    vm.saveProduct(
                                        existing?.id,
                                        mapOf(
                                            "nameAr" to nameAr.trim(), "nameEn" to nameEn.trim(),
                                            "descAr" to descAr.trim(), "descEn" to descEn.trim(),
                                            "category" to category.trim(),
                                            "priceMinor" to pr, "oldPriceMinor" to (parseMoneyMinor(oldPrice) ?: 0L),
                                            "available" to available,
                                            "imageCount" to imageCount,
                                            "imgVersion" to imgVersion,
                                            "thumb" to thumb,
                                            "searchTokens" to buildSearchTokens(nameAr, nameEn, category),
                                            "createdAt" to (existing?.createdAt ?: System.currentTimeMillis())
                                        ),
                                        images = images.toList(),
                                        existingImageCount = existing?.imageCount ?: 0,
                                        rewriteImages = rewriteImages,
                                        onDone
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.product_images), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.images_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                images.forEachIndexed { i, b64 ->
                    Box {
                        Base64Image(
                            b64,
                            Modifier.size(96.dp).clip(MaterialTheme.shapes.medium)
                                .border(if (i == 0) 2.dp else 0.dp, if (i == 0) Brand.Gold else Color.Transparent, MaterialTheme.shapes.medium)
                                .clickable {
                                    if (i != 0) {
                                        val full = images[i]
                                        val thumb = thumbs[i]
                                        images.removeAt(i)
                                        thumbs.removeAt(i)
                                        images.add(0, full)
                                        thumbs.add(0, thumb)
                                        imagesDirty = true
                                    }
                                }
                        )
                        IconButton(
                            onClick = { images.removeAt(i); thumbs.removeAt(i); imagesDirty = true },
                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                        ) {
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.67f)) {
                                Icon(Icons.Filled.Close, stringResource(R.string.cd_close), tint = Color.White, modifier = Modifier.padding(2.dp))
                            }
                        }
                    }
                }
                if (images.size + pendingCount < ImageUtils.MAX_IMAGES) {
                    Box(
                        Modifier.size(96.dp).clip(MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                            .clickable { launchPicker() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AddPhotoAlternate, stringResource(R.string.cd_add_image), Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                // Per-image progress: one spinner slot per pending image.
                repeat(pendingCount) {
                    Box(
                        Modifier.size(96.dp).clip(MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }

            // Round 6 fallback: the modern picker returned ONLY failures for the whole
            // batch — offer the classic file browser, which goes through the same
            // temp-file pipeline (so it only helps with provider/picker quirks).
            if (showFilesFallback) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.pick_files_hint),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showFilesFallback = false; fallbackPicker.launch("image/*") }) {
                            Text(stringResource(R.string.pick_from_files), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            AppTextField(nameAr, { nameAr = it }, stringResource(R.string.name_ar))
            AppTextField(nameEn, { nameEn = it }, stringResource(R.string.name_en))
            AppTextField(descAr, { descAr = it }, stringResource(R.string.desc_ar), singleLine = false, minLines = 3, imeAction = ImeAction.Next)
            AppTextField(descEn, { descEn = it }, stringResource(R.string.desc_en), singleLine = false, minLines = 3, imeAction = ImeAction.Next)
            AppTextField(category, { category = it }, stringResource(R.string.category))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTextField(price, { price = it }, stringResource(R.string.price), Modifier.weight(1f), keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                AppTextField(oldPrice, { oldPrice = it }, stringResource(R.string.old_price), Modifier.weight(1f), keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = available, onCheckedChange = { available = it })
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.available), style = MaterialTheme.typography.bodyLarge)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

// ================ Store settings =================
@Composable
fun AdminSettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    var whatsapp by rememberSaveable(s.whatsapp) { mutableStateOf(s.whatsapp) }
    var nameAr by rememberSaveable(s.storeNameAr) { mutableStateOf(s.storeNameAr) }
    var nameEn by rememberSaveable(s.storeNameEn) { mutableStateOf(s.storeNameEn) }
    var address by rememberSaveable(s.address) { mutableStateOf(s.address) }

    AppScaffold(title = stringResource(R.string.nav_settings), actions = { LanguageAction() }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = MaterialTheme.shapes.large) {
                Text(
                    stringResource(R.string.whatsapp_setting_hint),
                    Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            AppTextField(whatsapp, { whatsapp = it }, stringResource(R.string.whatsapp_number), keyboardType = KeyboardType.Phone)
            AppTextField(nameAr, { nameAr = it }, stringResource(R.string.store_name_ar))
            AppTextField(nameEn, { nameEn = it }, stringResource(R.string.store_name_en))
            AppTextField(address, { address = it }, stringResource(R.string.store_address), singleLine = false, minLines = 2)
            PrimaryButton(
                text = stringResource(R.string.save), loading = vm.busy,
                onClick = { vm.saveSettings(StoreSettings(whatsapp.trim(), nameAr.trim(), nameEn.trim(), address.trim())) }
            )
        }
    }
}
