package com.wutsi.ndopify.security.server.service

import org.apache.commons.codec.digest.DigestUtils
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PasswordEncryptorTest {
    private val encryptor = PasswordEncryptor()

    @Test
    fun hash() {
        val result = encryptor.encrypt("secret", "salt")

        assertEquals(DigestUtils.md5Hex("secret-salt"), result)
    }

    @Test
    fun `hash is deterministic`() {
        assertEquals(
            encryptor.encrypt("secret", "salt"),
            encryptor.encrypt("secret", "salt"),
        )
    }

    @Test
    fun `hash differs with different salt`() {
        assertNotEquals(
            encryptor.encrypt("secret", "salt1"),
            encryptor.encrypt("secret", "salt2")
        )
    }

    @Test
    fun `hash differs with different clear`() {
        assertNotEquals(
            encryptor.encrypt("secret1", "salt"),
            encryptor.encrypt("secret2", "salt")
        )
    }

    @Test
    fun matches() {
        val hashed = encryptor.encrypt("secret", "salt")

        assertTrue(encryptor.matches("secret", hashed, "salt"))
    }

    @Test
    fun `matches with no salt`() {
        val hashed = encryptor.encrypt("secret", null)

        assertTrue(encryptor.matches("secret", hashed, null))
    }

    @Test
    fun `matches with wrong password`() {
        val hashed = encryptor.encrypt("secret", "salt")

        assertFalse(encryptor.matches("wrong", hashed, "salt"))
    }

    @Test
    fun `matches with wrong salt`() {
        val hashed = encryptor.encrypt("secret", "salt")

        assertFalse(encryptor.matches("secret", hashed, "wrong-salt"))
    }
}
