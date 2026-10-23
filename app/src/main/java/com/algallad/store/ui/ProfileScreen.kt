package com.algallad.store.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.algallad.store.AppViewModel
import com.algallad.store.R
import com.algallad.store.data.DEFAULT_FONT_SCALE
import com.algallad.store.data.MAX_FONT_SCALE
import com.algallad.store.data.MIN_FONT_SCALE
import com.algallad.store.data.ThemeMode
import com.algallad.store.data.fontScaleFlow
import com.algallad.store.data.themeModeFlow
import com.algallad.store.data.writeFontScale
import com.algallad.store.data.writeThemeMode
import com.algallad.store.ui.theme.Brand
import com.algallad.store.ui.theme.goldAccent
import com.algallad.store.util.isArabic
import com.algallad.store.util.setAppLanguage
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(vm: AppViewModel) {
    val s = vm.session ?: return
    val ar = isArabic()
    val context = LocalContext.current
    var name by rememberSaveable(s.profile.fullName) { mutableStateOf(s.profile.fullName) }
    var phone by rememberSaveable(s.profile.phone) { mutableStateOf(s.profile.phone) }
    var address by rememberSaveable(s.profile.address) { mutableStateOf(s.profile.address) }
    var showDelete by remember { mutableStateOf(false) }
    var delPassword by remember { mutableStateOf("") }

    AppScaffold(title = stringResource(R.string.nav_profile)) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp)) {
                    Text(s.profile.fullName.ifBlank { stringResource(R.string.nav_profile) }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(if (s.isAdmin) R.string.role_admin else R.string.role_customer),
                        color = goldAccent(), style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            AppTextField(name, { name = it }, stringResource(R.string.full_name), leading = Icons.Filled.Badge)
            AppTextField(phone, { phone = it }, stringResource(R.string.phone), leading = Icons.Filled.Phone, keyboardType = KeyboardType.Phone)
            AppTextField(address, { address = it }, stringResource(R.string.address), leading = Icons.Filled.LocationOn, singleLine = false, minLines = 2)
            PrimaryButton(stringResource(R.string.save), { vm.updateProfile(name, phone, address) }, loading = vm.busy)

            // Email verification: required before ordering (VM blocks unverified checkout).
            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.isEmailVerified == false) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)), shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.verify_email_banner), color = Brand.Danger, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { vm.resendVerification() },
                                enabled = !vm.busy,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.resend_verification)) }
                            OutlinedButton(
                                onClick = { vm.refreshSession() },
                                enabled = !vm.busy,
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.i_verified)) }
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(selected = ar, onClick = { setAppLanguage("ar") }, label = { Text(stringResource(R.string.lang_arabic)) })
                FilterChip(selected = !ar, onClick = { setAppLanguage("en") }, label = { Text(stringResource(R.string.lang_english)) })
            }

            // Round 6 (Task B): appearance — radio rows (matches reference settings design)
            // + app text-size slider. Both persist via the existing settings DataStore
            // (ThemePrefs) and apply instantly (MainActivity collects the flows).
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
            val themeCtx = LocalContext.current
            val themeMode by themeModeFlow(themeCtx).collectAsState(initial = ThemeMode.SYSTEM)
            val themeScope = rememberCoroutineScope()
            ThemeMode.entries.forEach { m ->
                val label = stringResource(
                    when (m) {
                        ThemeMode.SYSTEM -> R.string.theme_system
                        ThemeMode.LIGHT -> R.string.theme_light
                        ThemeMode.DARK -> R.string.theme_dark
                    }
                )
                val select: () -> Unit = { themeScope.launch { writeThemeMode(themeCtx, m) } }
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.RadioButton, onClick = select).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = themeMode == m, onClick = select)
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Text(stringResource(R.string.text_size), style = MaterialTheme.typography.titleMedium)
            val fontScale by fontScaleFlow(themeCtx).collectAsState(initial = DEFAULT_FONT_SCALE)
            var sliderValue by remember(fontScale) { mutableFloatStateOf(fontScale) }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { themeScope.launch { writeFontScale(themeCtx, sliderValue) } },
                valueRange = MIN_FONT_SCALE..MAX_FONT_SCALE,
                steps = 4, // five stops: 90 / 100 / 110 / 120 / 130 / 140 %
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.font_scale_percent, Math.round(sliderValue * 100)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))
            // Privacy policy link — opens the hosted /privacy.html in the browser.
            TextButton(
                onClick = {
                    val url = context.getString(R.string.privacy_policy_url)
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.privacy_policy), color = MaterialTheme.colorScheme.primary)
            }
            OutlinedButton(
                onClick = { vm.logout() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                // Decorative: the adjacent "Log out" button text announces the action.
                Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Brand.Danger)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.logout), color = Brand.Danger)
            }

            // Play policy: in-app account deletion with password re-authentication.
            OutlinedButton(
                onClick = { showDelete = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Brand.Danger)
            ) {
                Text(stringResource(R.string.delete_account))
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.delete_account)) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.delete_account_confirm), style = MaterialTheme.typography.bodyMedium)
                    AppTextField(
                        delPassword, { delPassword = it }, stringResource(R.string.password),
                        password = true, keyboardType = KeyboardType.Password, imeAction = ImeAction.Done
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteAccount(delPassword) { showDelete = false; delPassword = "" } },
                    enabled = !vm.busy && delPassword.isNotEmpty()
                ) { Text(stringResource(R.string.delete), color = Brand.Danger) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
