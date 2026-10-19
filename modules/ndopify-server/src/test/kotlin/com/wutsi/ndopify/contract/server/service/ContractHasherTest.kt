package com.wutsi.ndopify.contract.server.service

import com.wutsi.ndopify.contract.dto.ContractPartyRole
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.domain.ContractPartyEntity
import org.apache.commons.codec.digest.DigestUtils
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ContractHasherTest {
    private val party = ContractPartyEntity(
        id = "cp-11",
        partyId = 100,
        role = ContractPartyRole.MANDATOR,
        legalName = "Ray Sponsible",
        legalEmail = "ray.sponsible@gmail.com",
        signedAt = Date(1718000000000),
        ipAddress = "192.168.1.10",
        userAgent = "Mozilla/5.0 (iPhone)",
        signatureSalt = "0123456789abcdef0123456789abcdef",
    )

    @Test
    fun `content hash`() {
        val content = "<p>Bail signé à Yaoundé</p>".toByteArray(Charsets.UTF_8)

        val hash = ContractHasher.contentHash(content)

        assertEquals(DigestUtils.sha256Hex(content), hash)
        assertEquals(64, hash.length)
    }

    @Test
    fun `signature hash`() {
        val hash = ContractHasher.signatureHash(ContractEntity(id = "c-1", contentHash = "abc"), party)

        assertEquals(
            DigestUtils.sha256Hex(
                "0123456789abcdef0123456789abcdef\nc-1\nabc\ncp-11\n100\nMANDATOR\nRay Sponsible\nray.sponsible@gmail.com\n1718000000000\n192.168.1.10\nMozilla/5.0 (iPhone)"
            ),
            hash,
        )
    }

    @Test
    fun `signature hash without content hash`() {
        val hash = ContractHasher.signatureHash(ContractEntity(id = "c-1", contentHash = null), party)

        assertEquals(
            DigestUtils.sha256Hex(
                "0123456789abcdef0123456789abcdef\nc-1\n\ncp-11\n100\nMANDATOR\nRay Sponsible\nray.sponsible@gmail.com\n1718000000000\n192.168.1.10\nMozilla/5.0 (iPhone)"
            ),
            hash,
        )
    }

    @Test
    fun `signature hash changes with the content`() {
        assertNotEquals(
            ContractHasher.signatureHash(ContractEntity(id = "c-1", contentHash = "abc"), party),
            ContractHasher.signatureHash(ContractEntity(id = "c-1", contentHash = "abd"), party),
        )
    }

    @Test
    fun `new salt`() {
        val salt = ContractHasher.newSalt()

        assertTrue(Regex("[0-9a-f]{32}").matches(salt))
        assertNotEquals(salt, ContractHasher.newSalt())
    }

    @Test
    fun `signature hash changes with the salt`() {
        val contract = ContractEntity(id = "c-1", contentHash = "abc")

        assertNotEquals(
            ContractHasher.signatureHash(contract, party),
            ContractHasher.signatureHash(contract, party.copy(signatureSalt = "f".repeat(32))),
        )
    }
}
