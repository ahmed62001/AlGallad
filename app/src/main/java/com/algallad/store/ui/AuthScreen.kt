package com.algallad.store.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.algallad.store.AppViewModel
import com.algallad.store.R
import com.algallad.store.data.AuthDefaults
import com.algallad.store.data.AuthErrorScope
import com.algallad.store.data.Validators
import com.algallad.store.secureAuthScreen
import com.algallad.store.ui.theme.Brand

@Composable
fun SplashScreen() {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Brand.NavyDark, Brand.Navy))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LogoBadge(96)
            Text(stringResource(R.string.app_name), color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            CircularProgressIndicator(color = Brand.Gold, strokeWidth = 3.dp)
        }
    }
}

@Composable
fun LogoBadge(size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brand.Gold), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.ShoppingBag, stringResource(R.string.cd_store_logo), tint = Brand.Navy, modifier = Modifier.size((size / 2).dp))
    }
}

@Composable
private fun PasswordChecklist(password: String) {
    val checks = Validators.passwordChecks(password)
    val rows = listOf(
        checks.minLength8 to stringResource(R.string.pw_req_length),
        checks.hasLower to stringResource(R.string.pw_req_lower),
        checks.hasUpper to stringResource(R.string.pw_req_upper),
        checks.hasDigit to stringResource(R.string.pw_req_digit),
        checks.hasSpecial to stringResource(R.string.pw_req_special)
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { row ->
            val ok = row.first
            val label = row.second
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Decorative: the adjacent requirement text announces the state.
                Icon(
                    if (ok) Icons.Filled.Check else Icons.Filled.Close,
                    null,
                    tint = if (ok) Brand.Gold else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(label, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AuthScreen(vm: AppViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(ctx) {
        val act = ctx as? androidx.appcompat.app.AppCompatActivity
        act?.secureAuthScreen()
        onDispose { }
    }
    // Round 6 (Task D): guests open on SIGN-UP (see AuthDefaults); saved state still
    // wins after config change/process restore (rememberSaveable initializer semantics).
    var mode by rememberSaveable { mutableIntStateOf(AuthDefaults.initialMode(vm.session != null)) } // 0 login, 1 register
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var fullName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).imePadding()
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Brand.NavyDark, Brand.Navy)))
                .statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Box(Modifier.align(Alignment.TopEnd)) { LanguageAction() }
            Column(
                Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LogoBadge(84)
                Text(stringResource(R.string.app_name), color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(stringResource(R.string.tagline), color = Brand.Gold, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }

        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (mode == 1) R.string.auth_register else R.string.auth_login),
                style = MaterialTheme.typography.titleLarge
            )

            // Task 3: persistent inline status driven by the dedicated typed authError
            // channel (NOT vm.message — AppRoot consumes that for the snackbar and can
            // null it before a second consumer copies it). Direct state reads: no
            // LaunchedEffect race. Error color + icon, announced via LiveRegion,
            // cleared on the next user edit.
            val authError = vm.authError
            if (authError != null) {
                val announced = stringResource(authError.messageRes) +
                    if (authError.detail.isNotEmpty()) " ${authError.detail}" else ""
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().semantics {
                        liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Assertive
                        contentDescription = announced
                    }
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline, null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            announced,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            val emailFieldError = authError?.takeIf { it.scope == AuthErrorScope.FIELD_EMAIL }
                ?.let { stringResource(it.messageRes) }
            val passwordFieldError = authError?.takeIf { it.scope == AuthErrorScope.FIELD_PASSWORD }
                ?.let { stringResource(it.messageRes) + if (authError.detail.isNotEmpty()) " ${authError.detail}" else "" }

            val focusManager = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            fun submit() {
                keyboard?.hide()
                focusManager.clearFocus()
                when (mode) {
                    1 -> vm.registerCustomer(email.trim(), password, fullName, phone, address)
                    else -> vm.login(email.trim(), password)
                }
            }

            AppTextField(
                email, { email = it; vm.clearAuthError() }, stringResource(R.string.email),
                leading = Icons.Filled.Email, keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next, isError = emailFieldError != null,
                supportingError = emailFieldError,
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )
            AppTextField(
                password, { password = it; vm.clearAuthError() }, stringResource(R.string.password),
                leading = Icons.Filled.Lock,
                keyboardType = KeyboardType.Password, password = true,
                imeAction = if (mode == 0) ImeAction.Done else ImeAction.Next,
                isError = passwordFieldError != null, supportingError = passwordFieldError,
                keyboardActions = KeyboardActions(
                    onDone = { submit() },
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )
            )
            if (mode == 1) {
                PasswordChecklist(password)
                AppTextField(
                    fullName, { fullName = it; vm.clearAuthError() }, stringResource(R.string.full_name),
                    leading = Icons.Filled.Badge, imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )
            }
            if (mode == 1) {
                AppTextField(
                    phone, { phone = it; vm.clearAuthError() }, stringResource(R.string.phone),
                    leading = Icons.Filled.Phone, keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next,
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )
                AppTextField(
                    address, { address = it; vm.clearAuthError() }, stringResource(R.string.address),
                    leading = Icons.Filled.LocationOn, singleLine = false, minLines = 2,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { submit() })
                )
            }
            if (mode != 0) {
                Text(stringResource(R.string.email_rule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = stringResource(if (mode == 1) R.string.auth_register else R.string.auth_login),
                loading = vm.busy,
                onClick = { submit() }
            )

            if (mode == 0) {
                TextButton(onClick = { mode = 1; vm.clearAuthError() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.auth_no_account))
                }
                TextButton(
                    onClick = { keyboard?.hide(); vm.forgotPassword(email.trim()) },
                    enabled = !vm.resetSending,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    if (vm.resetSending) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.reset_sending))
                    } else {
                        Text(stringResource(R.string.forgot_password))
                    }
                }
            } else {
                TextButton(onClick = { mode = 0; vm.clearAuthError() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.auth_have_account))
                }
            }
        }
    }

    // Dedicated post-registration state: stays until dismissed (no auto-dismiss, no timeout).
    val pendingEmail = vm.pendingVerifyEmail
    if (pendingEmail != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissVerifyDialog() },
            title = { Text(stringResource(R.string.verify_dialog_title)) },
            text = { Text(stringResource(R.string.verify_dialog_body, pendingEmail)) },
            confirmButton = {
                TextButton(onClick = { vm.refreshSession(); vm.dismissVerifyDialog() }) {
                    Text(stringResource(R.string.verify_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.resendVerification() }) {
                    Text(stringResource(R.string.verify_resend))
                }
            }
        )
    }
}
