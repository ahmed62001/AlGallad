package com.algallad.store.data

import androidx.annotation.StringRes
import com.algallad.store.R

/**
 * Pure, JVM unit-testable mapping of sign-in / registration / password-reset
 * failures to a UI error model (Task 3).
 *
 * Assumption (email-enumeration protection): with the Firebase Console
 * "email enumeration protection" toggle ON, signInWithEmailAndPassword returns
 * the SAME error for a wrong email and a wrong password
 * (ERROR_INVALID_CREDENTIAL), so the UI maps every bad-credential shape to ONE
 * generic message and never distinguishes the two cases.
 *
 * Implementation note: the mapper deliberately avoids direct references to
 * FirebaseAuth*Exception types. The firebase-auth AAR's FirebaseAuthException
 * bytecode references an internal repackaged ApiException that is absent from
 * the published AAR (verified via javap: class
 * com.google.firebase.auth.FirebaseAuthException not loadable from classes.jar
 * alone), so `is FirebaseAuthInvalidCredentialsException` or `new
 * FirebaseAuthInvalidCredentialsException(...)` throws NoClassDefFoundError on
 * a JVM unit test. Instead the mapper reads the public `getErrorCode()`
 * method (declared on FirebaseAuthException) and the class simple name via
 * reflection — both survive R8 because the app's own errorText() already
 * relies on class-name matching for WeakPassword. No PII is ever read: only
 * the error code and the exception class name (never getMessage()).
 */
enum class AuthErrorScope { FIELD_EMAIL, FIELD_PASSWORD, FORM }

data class AuthUiError(
    @StringRes val messageRes: Int,
    val scope: AuthErrorScope,
    /** Extra server detail for weak passwords (already sanitized by the caller). */
    val detail: String = "",
)

/**
 * Maps any [Throwable] from Firebase Auth (or the network stack) to an
 * [AuthUiError]. Pure: no Android framework calls, no Firebase class refs.
 */
fun mapAuthError(t: Throwable): AuthUiError {
    val cls = t.javaClass.simpleName
    val code: String = runCatching {
        t.javaClass.methods.firstOrNull { it.name == "getErrorCode" }
            ?.invoke(t) as? String
    }.getOrNull().orEmpty()

    // Disabled account — typed name survives R8 (same mechanism as WeakPassword).
    if (cls.contains("UserDisabled") || code == "ERROR_USER_DISABLED") {
        return AuthUiError(R.string.err_user_disabled, AuthErrorScope.FORM)
    }
    // Rate limiting (FirebaseTooManyRequestsException has no error code).
    if (cls.contains("TooManyRequests") || code == "ERROR_TOO_MANY_REQUESTS") {
        return AuthUiError(R.string.err_too_many_requests, AuthErrorScope.FORM)
    }
    // Offline.
    if (cls.contains("FirebaseNetworkException")) {
        return AuthUiError(R.string.err_network, AuthErrorScope.FORM)
    }
    // Registration: address already taken.
    if (cls.contains("UserCollision") || code == "ERROR_EMAIL_ALREADY_IN_USE") {
        return AuthUiError(R.string.err_email_taken, AuthErrorScope.FIELD_EMAIL)
    }
    // Weak password (registration): keep any server reason the caller appends.
    if (cls.contains("WeakPassword") || code == "ERROR_WEAK_PASSWORD") {
        val detail = Validators.weakPasswordDetails(t)
        return AuthUiError(R.string.err_password_weak, AuthErrorScope.FIELD_PASSWORD, detail)
    }
    // Bad email format rejected server-side. NOTE: this code check MUST come
    // before the generic InvalidCredentials class-name match below, because a
    // FirebaseAuthInvalidCredentialsException carrying ERROR_INVALID_EMAIL
    // would otherwise be swallowed by the generic branch.
    if (code == "ERROR_INVALID_EMAIL") {
        return AuthUiError(R.string.err_email_invalid, AuthErrorScope.FIELD_EMAIL)
    }
    // Bad credentials: typed invalid-credentials / invalid-user shapes AND the
    // enumeration-protection code all map to ONE generic message.
    if (cls.contains("InvalidCredentials") || cls.contains("InvalidUser") ||
        cls.contains("InvalidEmail") ||
        code == "ERROR_INVALID_CREDENTIAL" || code == "ERROR_WRONG_PASSWORD" ||
        code == "ERROR_USER_NOT_FOUND" || code == "ERROR_USER_TOKEN_EXPIRED" ||
        code == "ERROR_REQUIRES_RECENT_LOGIN"
    ) {
        return AuthUiError(R.string.err_bad_credentials, AuthErrorScope.FORM)
    }
    // Safe generic fallback. Only the class name is logged by callers, never
    // the message (it can contain emails / tokens / phone numbers).
    return AuthUiError(R.string.err_generic, AuthErrorScope.FORM)
}

/** Client-side login validation BEFORE any network call. Null = valid. */
fun validateLoginForm(email: String, password: String): AuthUiError? = when {
    email.isBlank() || !Validators.isEmail(email) ->
        AuthUiError(R.string.err_email_invalid, AuthErrorScope.FIELD_EMAIL)
    password.isEmpty() ->
        AuthUiError(R.string.err_empty_password, AuthErrorScope.FIELD_PASSWORD)
    else -> null
}

/** Client-side registration validation BEFORE any network call. Null = valid. */
fun validateRegisterForm(
    email: String,
    password: String,
    fullName: String,
    phone: String,
    address: String,
): AuthUiError? = when {
    email.isBlank() || !Validators.isEmail(email) ->
        AuthUiError(R.string.err_email_invalid, AuthErrorScope.FIELD_EMAIL)
    !Validators.isStrongPassword(password) ->
        AuthUiError(R.string.err_password_weak, AuthErrorScope.FIELD_PASSWORD)
    fullName.isBlank() || phone.isBlank() || address.isBlank() ->
        AuthUiError(R.string.err_fill_all, AuthErrorScope.FORM)
    !Validators.isEgyptPhone(phone) ->
        AuthUiError(R.string.err_phone_invalid, AuthErrorScope.FORM)
    else -> null
}
