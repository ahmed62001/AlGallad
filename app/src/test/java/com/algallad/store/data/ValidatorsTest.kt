package com.algallad.store.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun isEgyptPhone_validNumbers() {
        assertTrue(Validators.isEgyptPhone("01012345678"))
        assertTrue(Validators.isEgyptPhone("01112345678"))
        assertTrue(Validators.isEgyptPhone("01212345678"))
        assertTrue(Validators.isEgyptPhone("01512345678"))
    }

    @Test
    fun isEgyptPhone_invalidNumbers() {
        assertFalse(Validators.isEgyptPhone(""))
        assertFalse(Validators.isEgyptPhone("0101234567")) // 10 digits
        assertFalse(Validators.isEgyptPhone("010123456789")) // 12 digits
        assertFalse(Validators.isEgyptPhone("02012345678")) // starts with 02
        assertFalse(Validators.isEgyptPhone("abc"))
    }

    @Test
    fun isEmail_validAndInvalid() {
        assertTrue(Validators.isEmail("user@example.com"))
        assertTrue(Validators.isEmail("admin@example.com"))
        assertFalse(Validators.isEmail(""))
        assertFalse(Validators.isEmail("not-an-email"))
        assertFalse(Validators.isEmail("@example.com"))
        assertFalse(Validators.isEmail("user@"))
    }

    @Test
    fun isStrongPassword_requirements() {
        assertTrue(Validators.isStrongPassword("Abcdef1!"))
        assertTrue(Validators.isStrongPassword("Pass1234!"))
        assertFalse(Validators.isStrongPassword("short1!")) // < 8
        assertFalse(Validators.isStrongPassword("password")) // no upper/digit/special
        assertFalse(Validators.isStrongPassword("PASSWORD1")) // no lower/special
        assertFalse(Validators.isStrongPassword("Password")) // no digit/special
    }

    @Test
    fun sellerWhatsAppToE164_normalizes() {
        assertEquals("201012345678", Validators.sellerWhatsAppToE164("01012345678") ?: "")
        assertEquals("201012345678", Validators.sellerWhatsAppToE164("201012345678") ?: "")
        assertNull(Validators.sellerWhatsAppToE164("12345"))
    }

    @Test
    fun passwordChecks_breakdown() {
        val checks = Validators.passwordChecks("Abcdef1!")
        assertTrue(checks.minLength8)
        assertTrue(checks.hasLower)
        assertTrue(checks.hasUpper)
        assertTrue(checks.hasDigit)
        assertTrue(checks.hasSpecial)
        assertTrue(checks.all)

        val weak = Validators.passwordChecks("password")
        assertFalse(weak.all)
    }
}