package com.wutsi.ndopify.email.server.dao

import com.wutsi.ndopify.email.server.domain.EmailAddressEntity
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.Date

interface EmailAddressRepository : CrudRepository<EmailAddressEntity, Long> {
    fun findByEmailHash(emailHash: String): EmailAddressEntity?

    /**
     * Inserts the address's verdict, or updates the row of the same hash. The single statement makes concurrent
     * submissions of one address update one row instead of failing on the unique key, and `REQUIRES_NEW` commits the
     * verdict on its own, so it survives the caller's rollback (a refused mandate rolls its transaction back).
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO T_EMAIL_ADDRESS(
                email_hash, score, reason, has_valid_syntax, has_valid_dns_records, mailbox_exists,
                is_role_address, is_disposable, is_random_input, checked_at, expires_at
            ) VALUES (
                :emailHash, :score, :reason, :hasValidSyntax, :hasValidDnsRecords, :mailboxExists,
                :isRoleAddress, :isDisposable, :isRandomInput, :checkedAt, :expiresAt
            )
            ON DUPLICATE KEY UPDATE
                score = VALUES(score),
                reason = VALUES(reason),
                has_valid_syntax = VALUES(has_valid_syntax),
                has_valid_dns_records = VALUES(has_valid_dns_records),
                mailbox_exists = VALUES(mailbox_exists),
                is_role_address = VALUES(is_role_address),
                is_disposable = VALUES(is_disposable),
                is_random_input = VALUES(is_random_input),
                checked_at = VALUES(checked_at),
                expires_at = VALUES(expires_at)
        """,
    )
    fun upsert(
        emailHash: String,
        score: Double,
        reason: String,
        hasValidSyntax: String,
        hasValidDnsRecords: String,
        mailboxExists: String,
        isRoleAddress: String,
        isDisposable: String,
        isRandomInput: String,
        checkedAt: Date,
        expiresAt: Date,
    )
}
