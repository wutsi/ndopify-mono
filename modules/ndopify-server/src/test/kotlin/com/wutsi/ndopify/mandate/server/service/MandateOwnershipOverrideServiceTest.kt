package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.logger.KVLogger
import java.time.Clock
import java.time.Instant
import java.util.Date
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The override's audit trail: the three fields written and cleared together, and the set/clear log line of AC9. */
class MandateOwnershipOverrideServiceTest {
    private companion object {
        private const val MANDATE_ID = 100L
        private const val USER_ID = 11L
        private const val REASON = "Titre au nom de son père, vérifié en agence"
        private val NOW: Instant = Instant.parse("2026-11-28T10:15:30Z")
    }

    private val dao = mock<MandateRepository>()
    private val documentDao = mock<MandateDocumentRepository>()
    private val logger = CapturingKVLogger()
    private val clock = mock<Clock>()
    private val service = MandateOwnershipOverrideService(dao, documentDao, logger, clock)

    private val mandate = MandateEntity(
        id = MANDATE_ID,
        customerId = 10L,
        mandateType = MandateType.LISTING,
        cityId = 2370201,
    )

    @Test
    fun `set writes the audit fields and logs the action with the reason`() {
        doReturn(mandate).whenever(dao).findByIdForUpdate(MANDATE_ID)
        doReturn(backingDocuments()).whenever(documentDao).findByMandateIdOrderByIdDesc(MANDATE_ID)
        doReturn(NOW.toEpochMilli()).whenever(clock).millis()

        service.set(MANDATE_ID, REASON, USER_ID)

        val saved = argumentCaptor<MandateEntity>()
        verify(dao).save(saved.capture())
        assertEquals(REASON, saved.firstValue.ownershipOverrideReason)
        assertEquals(USER_ID, saved.firstValue.ownershipOverrideBy)
        assertEquals(Date(NOW.toEpochMilli()), saved.firstValue.ownershipOverrideAt)
        val expected: List<Pair<String, String?>> = listOf(
            "mandate_id" to MANDATE_ID.toString(),
            "ownership_override" to "set",
            "user_id" to USER_ID.toString(),
            "reason" to REASON,
        )
        assertEquals(expected, logger.entries)
        assertEquals(1, logger.logs)
    }

    @Test
    fun `clear empties the three audit fields and logs the action without a reason`() {
        val overridden = mandate.copy(
            ownershipOverrideReason = REASON,
            ownershipOverrideBy = USER_ID,
            ownershipOverrideAt = Date(NOW.toEpochMilli()),
        )
        doReturn(overridden).whenever(dao).findByIdForUpdate(MANDATE_ID)

        service.clear(MANDATE_ID, USER_ID)

        val saved = argumentCaptor<MandateEntity>()
        verify(dao).save(saved.capture())
        assertNull(saved.firstValue.ownershipOverrideReason)
        assertNull(saved.firstValue.ownershipOverrideBy)
        assertNull(saved.firstValue.ownershipOverrideAt)
        val expected: List<Pair<String, String?>> = listOf(
            "mandate_id" to MANDATE_ID.toString(),
            "ownership_override" to "clear",
            "user_id" to USER_ID.toString(),
        )
        assertEquals(expected, logger.entries)
        assertEquals(1, logger.logs)
    }

    @Test
    fun `clearing without an override writes nothing and still logs the action`() {
        doReturn(mandate).whenever(dao).findByIdForUpdate(MANDATE_ID)

        service.clear(MANDATE_ID, USER_ID)

        verify(dao, never()).save(any())
        val expected: List<Pair<String, String?>> = listOf(
            "mandate_id" to MANDATE_ID.toString(),
            "ownership_override" to "clear",
            "user_id" to USER_ID.toString(),
        )
        assertEquals(expected, logger.entries)
    }

    private fun backingDocuments(): List<MandateDocumentEntity> =
        listOf(document(DocumentType.NATIONAL_ID), document(DocumentType.LAND_TITLE))

    private fun document(type: DocumentType) = MandateDocumentEntity(id = 1000, mandateId = MANDATE_ID, type = type)

    /** Keeps the log entries in order, so a test can assert the line carries nothing else. */
    private class CapturingKVLogger : KVLogger {
        val entries = mutableListOf<Pair<String, String?>>()
        var logs = 0

        override fun log() {
            logs++
        }

        override fun setException(ex: Throwable) {}

        override fun add(key: String, value: String?) {
            entries.add(key to value)
        }

        override fun add(key: String, value: Long?) {
            entries.add(key to value?.toString())
        }

        override fun add(key: String, value: Double?) {
            entries.add(key to value?.toString())
        }

        override fun add(key: String, value: Optional<*>) {
            entries.add(key to value.orElse(null)?.toString())
        }

        override fun add(key: String, values: Collection<*>?) {
            values?.forEach { entries.add(key to it?.toString()) }
        }

        override fun add(key: String, value: Any?) {
            entries.add(key to value?.toString())
        }
    }
}
