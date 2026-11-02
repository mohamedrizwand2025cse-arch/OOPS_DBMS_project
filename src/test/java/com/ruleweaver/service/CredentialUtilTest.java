package com.ruleweaver.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CredentialUtilTest {

    @Test
    void passwordIsHashedAndVerified() {
        String hash = CredentialUtil.hashPassword("secret123");
        assertFalse(hash.contains("secret123"));
        assertTrue(CredentialUtil.verifyPassword("secret123", hash));
        assertFalse(CredentialUtil.verifyPassword("secret124", hash));
    }

    @Test
    void samePasswordGetsDifferentSalt() {
        assertNotEquals(CredentialUtil.hashPassword("secret123"), CredentialUtil.hashPassword("secret123"));
    }

    @Test
    void badStoredHashNeverMatches() {
        assertFalse(CredentialUtil.verifyPassword("x", "nonsense"));
        assertFalse(CredentialUtil.verifyPassword("x", null));
    }

    @Test
    void validatesSignInForm() {
        assertNotNull(CredentialUtil.validate(true, "A", "a@b.co", "12345678"));
        assertNotNull(CredentialUtil.validate(false, null, "not-an-email", "12345678"));
        assertNotNull(CredentialUtil.validate(false, null, "a@b.co", "1234567"));
        assertNull(CredentialUtil.validate(true, "Rizwan", "a@b.co", "12345678"));
    }

    @Test
    void emailsAreNormalized() {
        assertEquals("rizwan@mail.com", CredentialUtil.normalizeEmail("  Rizwan@Mail.COM "));
    }
}
