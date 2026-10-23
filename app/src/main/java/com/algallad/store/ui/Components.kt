package com.algallad.store.ui

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.algallad.store.R
import com.algallad.store.data.formatMoney
import com.algallad.store.ui.theme.Brand
import com.algallad.store.util.fmtMoney
import com.algallad.store.util.isArabic
import com.algallad.store.util.setAppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- Images (Base64) ----------
object ImageCache {
    // Sized in BYTES: 1/8 of the app memory class (spec Part G).
    private val maxBytes: Int = ((Runtime.getRuntime().maxMemory() / 8).coerceAtMost((128 * 1024 * 1024).toLong())).toInt()
    private val cache = object : LruCache<Int, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: Int, bmp: ImageBitmap): Int {
            return (bmp.width * bmp.height * 4).coerceAtLeast(1)
        }
    }
    private fun key(s: String) = s.hashCode() * 31 + s.length
    fun peek(s: String?): ImageBitmap? = s?.let { cache.get(key(it)) }

    // Disk cache in cacheDir/img/ keyed productId_imgVersion_index (sanitized).
    private fun diskFile(ctx: android.content.Context, diskKey: String): java.io.File {
        val safe = diskKey.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        return java.io.File(java.io.File(ctx.cacheDir, "img").apply { mkdirs() }, safe)
    }

    private fun decodeBitmap(bytes: ByteArray): ImageBitmap? = runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()

    suspend fun load(
        ctx: android.content.Context,
        s: String,
        diskKey: String? = null
    ): ImageBitmap? {
        cache.get(key(s))?.let { return it }
        if (diskKey != null) {
            val cached = withContext(Dispatchers.IO) {
                runCatching {
                    val f = diskFile(ctx, diskKey)
                    if (f.exists()) decodeBitmap(f.readBytes()) else null
                }.getOrNull()
            }
            if (cached != null) { cache.put(key(s), cached); return cached }
        }
        val decoded = withContext(Dispatchers.Default) {
            runCatching { Base64.decode(s, Base64.DEFAULT) }.getOrNull()
        } ?: return null
        val bmp = withContext(Dispatchers.Default) { decodeBitmap(decoded) } ?: return null
        cache.put(key(s), bmp)
        if (diskKey != null) {
            withContext(Dispatchers.IO) {
                runCatching { diskFile(ctx, diskKey).writeBytes(decoded) }
            }
        }
        return bmp
    }
}

@Composable
fun Base64Image(
    data: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    diskKey: String? = null
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = ImageCache.peek(data), data, diskKey) {
        value = if (data.isNullOrBlank()) null else ImageCache.load(ctx, data, diskKey)
    }
    val b = bmp
    if (b != null) {
        Image(bitmap = b, contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Image, stringResource(R.string.cd_product_image), tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(32.dp))
        }
    }
}

// ---------- App scaffold ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Brand.Navy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = fab,
        containerColor = MaterialTheme.colorScheme.background,
        // Android 16 enforces edge-to-edge: keep system insets so bars never overlap content.
        content = content
    )
}

@Composable
fun LanguageAction() {
    val ar = isArabic()
    TextButton(onClick = { setAppLanguage(if (ar) "en" else "ar") }) {
        Text(stringResource(R.string.lang_toggle), color = Brand.Gold, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------- Input elements ----------
@Composable
fun AppTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    imeAction: ImeAction = ImeAction.Next,
    // Task 3: field-level inline error + keyboard actions (defaults keep all
    // existing call sites unchanged).
    isError: Boolean = false,
    supportingError: String? = null,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions =
        androidx.compose.foundation.text.KeyboardActions.Default,
) {
    var show by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        // Leading icon is decorative: the field's visible label announces the field.
        leadingIcon = leading?.let { { Icon(it, null) } },
        trailingIcon = if (password) {
            {
                IconButton(onClick = { show = !show }) {
                    Icon(
                        if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        stringResource(if (show) R.string.cd_hide_password else R.string.cd_show_password)
                    )
                }
            }
        } else null,
        visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = keyboardActions,
        isError = isError,
        singleLine = singleLine,
        minLines = minLines,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )
        if (isError && supportingError != null) {
            Text(
                supportingError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Assertive
                }
            )
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = Brand.Navy, contentColor = Color.White),
        modifier = modifier.fillMaxWidth().height(52.dp)
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
        else Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = Brand.Gold, contentColor = Brand.OnGold),
        modifier = modifier.fillMaxWidth().height(52.dp)
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Brand.OnGold, strokeWidth = 2.5.dp)
        else Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun QtyStepper(qty: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onChange(qty - 1) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Remove, stringResource(R.string.cd_decrease_qty))
        }
        Text("$qty", Modifier.widthIn(min = 28.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { onChange(qty + 1) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Add, stringResource(R.string.cd_increase_qty))
        }
    }
}

@Composable
fun priceLabel(v: Double): String = "${fmtMoney(v)} ${stringResource(R.string.currency)}"

@Composable
fun priceLabelMinor(v: Long): String = formatMoney(v, isArabic())

// ---------- Task 5: skeleton shimmer (replaces bare spinners) ----------
/** One shimmering placeholder block. With animator-duration scale 0 the
 *  transition holds its initial value — layout stays correct, just static. */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "shimmerAlpha"
    )
    Box(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

/** Skeleton for a product grid card (thumb + two text lines + button). */
@Composable
fun ProductCardSkeleton(modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        ShimmerBox(Modifier.fillMaxWidth().aspectRatio(1f))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ShimmerBox(Modifier.fillMaxWidth().height(16.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.7f).height(16.dp))
            ShimmerBox(Modifier.fillMaxWidth().height(44.dp))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            // Decorative: the title text below conveys the same meaning to screen readers.
            Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionText != null && onAction != null) {
            Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = Brand.Navy, contentColor = Color.White)) {
                Text(actionText)
            }
        }
    }
}
