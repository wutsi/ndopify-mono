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
        val result = encryptor.hash("secret", "salt")

        assertEquals(DigestUtils.md5Hex("secret-salt"), result)
    }

    @Test
    fun `hash is deterministic`() {
        assertEquals(
            encryptor.hash("secret", "salt"),
            encryptor.hash("secret", "salt"),
        )
    }

    @Test
    fun `hash differs with different salt`() {
        assertNotEquals(
            encryptor.hash("secret", "salt1"),
            encryptor.hash("secret", "salt2")
        )
    }

    @Test
    fun `hash differs with different clear`() {
        assertNotEquals(
            encryptor.hash("secret1", "salt"),
            encryptor.hash("secret2", "salt")
        )
    }

    @Test
    fun matches() {
        val hashed = encryptor.hash("secret", "salt")

        assertTrue(encryptor.matches("secret", hashed, "salt"))
    }

    @Test
    fun `matches with no salt`() {
        val hashed = encryptor.hash("secret", null)

        assertTrue(encryptor.matches("secret", hashed, null))
    }

    @Test
    fun `matches with wrong password`() {
        val hashed = encryptor.hash("secret", "salt")

        assertFalse(encryptor.matches("wrong", hashed, "salt"))
    }

    @Test
    fun `matches with wrong salt`() {
        val hashed = encryptor.hash("secret", "salt")

        assertFalse(encryptor.matches("secret", hashed, "wrong-salt"))
    }
}
