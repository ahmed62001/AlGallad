package com.algallad.store.data

/**
 * Round 6, Task D: which auth mode the Account tab opens on.
 * Guests (no session) see SIGN-UP by default — creating an account is the primary
 * journey for a customer landing on the Account tab; login stays one tap away via
 * the "Already have an account?" toggle. Pure so the rule is unit-testable without
 * Compose or Firebase.
 */
object AuthDefaults {
    /** Login index (AuthScreen convention: 0 = login, 1 = register). */
    const val MODE_LOGIN = 0
    /** Register/sign-up index (AuthScreen convention). */
    const val MODE_REGISTER = 1

    /** Initial mode: guests sign up first, signed-in users land on login. */
    fun initialMode(hasSession: Boolean): Int = if (hasSession) MODE_LOGIN else MODE_REGISTER
}