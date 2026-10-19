package com.wutsi.ndopify.contract.server.service

import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.contract.server.domain.ContractPartyEntity
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.security.SecureRandom

/**
 * SHA-256 fingerprints of contracts, as lowercase hex (64 characters). Stored only: never in a DTO.
 *
 * A signature's hash covers, in this order, one per line (a null as an empty line): the signature's random salt, the
 * contract's id and content hash, the contract party's id, its party id, role, legal name and e-mail, when it signed (epoch millis), from which
 * IP address and user agent. Changing the order or the fields changes every hash computed from then on: the stored
 * ones can't be recomputed. The salt makes a published hash (printed in the signed PDF) unguessable from its other,
 * guessable inputs (IP address, user agent, e-mail).
 */
object ContractHasher {
    private val RANDOM = SecureRandom()

    /** 16 random bytes, as lowercase hex (32 characters). */
    fun newSalt(): String = Hex.encodeHexString(ByteArray(16).also { bytes -> RANDOM.nextBytes(bytes) })

    fun contentHash(content: ByteArray): String = DigestUtils.sha256Hex(content)

    fun signatureHash(contract: ContractEntity, party: ContractPartyEntity): String =
        DigestUtils.sha256Hex(
            listOf(
                party.signatureSalt,
                contract.id,
                contract.contentHash,
                party.id,
                party.partyId.toString(),
                party.role.name,
                party.legalName,
                party.legalEmail,
                party.signedAt?.time?.toString(),
                party.ipAddress,
                party.userAgent,
            ).joinToString("\n") { it ?: "" }
        )
}
