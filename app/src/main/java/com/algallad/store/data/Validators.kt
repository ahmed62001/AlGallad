package com.algallad.store.data

object Validators {
    private val egyptPhone = Regex("^01[0125]\\d{8}$")
    private val email = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun isEgyptPhone(value: String): Boolean = egyptPhone.matches(value.trim())
    fun isEmail(value: String): Boolean = email.matches(value.trim())

    /**
     * Firebase Console password policy: a password must satisfy ALL five checks.
     * Specials are exactly: ^ $ * . [ ] { } ( ) ? " ! @ # % &amp; / \ , &gt; &lt; ' : ; | _ ~ ` + = -
     * (ASCII ranges on purpose: Arabic-only letters/digits do not count as lower/upper/digit.)
     */
    val passwordSpecials: Set<Char> = setOf(
        '^', '$', '*', '.', '[', ']', '{', '}', '(', ')', '?', '"',
        '!', '@', '#', '%', '&', '/', '\\', ',', '>', '<', '\'', ':', ';', '|',
        '_', '~', '`', '+', '=', '-'
    )

    data class PasswordChecks(
        val minLength8: Boolean,
        val hasLower: Boolean,
        val hasUpper: Boolean,
        val hasDigit: Boolean,
        val hasSpecial: Boolean
    ) {
        val all: Boolean get() = minLength8 && hasLower && hasUpper && hasDigit && hasSpecial
    }

    fun passwordChecks(p: String): PasswordChecks = PasswordChecks(
        minLength8 = p.length >= 8,
        hasLower = p.any { it in 'a'..'z' },
        hasUpper = p.any { it in 'A'..'Z' },
        hasDigit = p.any { it in '0'..'9' },
        hasSpecial = p.any { it in passwordSpecials }
    )

    fun isStrongPassword(value: String): Boolean = passwordChecks(value).all

    /**
     * Server-side detail for a weak-password rejection: reads getReason()/getReasons() when
     * present (FirebaseAuthWeakPasswordException) via reflection so unit tests stay JVM-only.
     */
    fun weakPasswordDetails(e: Throwable): String = runCatching {
        val m = e.javaClass.methods.firstOrNull { it.name == "getReason" || it.name == "getReasons" }
        val v = m?.invoke(e)
        when (v) {
            is Collection<*> -> v.filterIsInstance<String>().joinToString(" ").trim()
            is String -> v.trim()
            else -> ""
        }
    }.getOrNull().orEmpty()

    fun sellerWhatsAppToE164(value: String): String? {
        val digits = value.filter(Char::isDigit)
        val normalized = when {
            digits.startsWith("20") && digits.length == 12 -> digits
            digits.startsWith("0") && digits.length == 11 -> "2$digits"
            digits.length == 10 && digits.startsWith("1") -> "20$digits"
            else -> return null
        }
        return if (Regex("^201[0125]\\d{8}$").matches(normalized)) normalized else null
    }
}