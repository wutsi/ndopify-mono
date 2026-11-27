package com.wutsi.ndopify.email.server.dao

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.email.server.domain.Verdict
import com.wutsi.ndopify.email.server.domain.VerificationReason
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class EmailAddressRepositoryTest : BaseEndpointIntegrationTest() {
    companion object {
        private const val DAY = 24L * 60 * 60 * 1000
        private const val HASH = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        private const val OTHER_HASH = "486ea46224d1bb4fb680f34f7c9ad96a8f24ec88be73ea8e5a6c65260e9cb8a7"
    }

    @Autowired
    private lateinit var dao: EmailAddressRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    @Sql("/db/test/clean.sql")
    fun `the upsert inserts once and updates the row of the same hash`() {
        val now = Date()
        dao.upsert(HASH, 1.0, "NONE", "HIGH", "HIGH", "HIGH", "HIGH", "HIGH", "HIGH", now, Date(now.time + DAY))
        dao.upsert(HASH, 0.0, "MAILBOX_UNREACHABLE", "HIGH", "HIGH", "LOW", "HIGH", "HIGH", "HIGH", now, now)

        val row = dao.findByEmailHash(HASH)!!
        assertEquals(0.0, row.score)
        assertEquals(VerificationReason.MAILBOX_UNREACHABLE, row.reason)
        assertEquals(Verdict.LOW, row.mailboxExists)
        assertEquals(1L, count())
    }

    @Test
    @Sql("/db/test/clean.sql")
    fun `the six verdicts and the dates are read back`() {
        val checkedAt = Date(1_700_000_000_000)
        val expiresAt = Date(checkedAt.time + 30 * DAY)
        dao.upsert(
            OTHER_HASH, 0.5, "ROLE_ADDRESS", "HIGH", "MEDIUM", "HIGH", "LOW", "MEDIUM", "HIGH",
            checkedAt, expiresAt,
        )

        val row = dao.findByEmailHash(OTHER_HASH)!!
        assertEquals(0.5, row.score)
        assertEquals(VerificationReason.ROLE_ADDRESS, row.reason)
        assertEquals(Verdict.HIGH, row.hasValidSyntax)
        assertEquals(Verdict.MEDIUM, row.hasValidDnsRecords)
        assertEquals(Verdict.HIGH, row.mailboxExists)
        assertEquals(Verdict.LOW, row.isRoleAddress)
        assertEquals(Verdict.MEDIUM, row.isDisposable)
        assertEquals(Verdict.HIGH, row.isRandomInput)
        assertEquals(checkedAt.time / 1000, row.checkedAt.time / 1000) // DATETIME keeps seconds, not milliseconds
        assertEquals(expiresAt.time / 1000, row.expiresAt.time / 1000)
    }

    @Test
    @Sql("/db/test/clean.sql")
    fun `an unknown enum name reads the converter default`() {
        val now = Date()
        jdbc.update(
            """
                INSERT INTO T_EMAIL_ADDRESS(
                    email_hash, score, reason, has_valid_syntax, has_valid_dns_records, mailbox_exists,
                    is_role_address, is_disposable, is_random_input, checked_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            HASH, 1.0, "ZZZ", "ZZZ", "HIGH", "HIGH", "HIGH", "HIGH", "HIGH", now, now,
        )

        val row = dao.findByEmailHash(HASH)!!
        assertEquals(VerificationReason.NONE, row.reason)
        assertEquals(Verdict.HIGH, row.hasValidSyntax)
    }

    private fun count(): Long =
        jdbc.queryForObject("SELECT COUNT(*) FROM T_EMAIL_ADDRESS", Long::class.java)!!
}
