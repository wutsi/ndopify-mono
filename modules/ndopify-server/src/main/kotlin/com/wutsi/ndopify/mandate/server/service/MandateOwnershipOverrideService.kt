package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.logger.KVLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Date

/**
 * Stores and clears an admin's override of a LISTING mandate's owner verdict (#377), with its audit trail: the
 * mandatory reason, the admin's user id and the time, written and cleared together. The verdict reads it back through
 * [OwnershipVerdictService], which only applies it while both kinds of supporting document still exist.
 *
 * Only an admin runs it (the endpoint checks), and the override writes nothing about a customer or a document: the log
 * line carries the mandate id, the action, the admin's user id and, for a set, the admin's own reason, never a name,
 * a document id, a page, a path, a URL or an extracted value.
 */
@Service
class MandateOwnershipOverrideService(
    private val dao: MandateRepository,
    private val documentDao: MandateDocumentRepository,
    private val logger: KVLogger,
    private val clock: Clock,
) {
    /**
     * 404 [ErrorCode.MANDATE_NOT_FOUND] → 400 [ErrorCode.MANDATE_NOT_LISTING] → 409
     * [ErrorCode.MANDATE_OWNERSHIP_OVERRIDE_WITHOUT_DOCUMENTS].
     */
    @Transactional
    fun set(mandateId: Long, reason: String, userId: Long) {
        val mandate = load(mandateId)
        ensureBacked(documentDao.findByMandateIdOrderByIdDesc(mandateId))
        dao.save(
            mandate.copy(
                ownershipOverrideReason = reason,
                ownershipOverrideBy = userId,
                ownershipOverrideAt = Date(clock.millis()),
            ),
        )
        log(mandateId, action = "set", userId = userId, reason = reason)
    }

    /** 404 [ErrorCode.MANDATE_NOT_FOUND] → 400 [ErrorCode.MANDATE_NOT_LISTING]. Idempotent: no override, no write. */
    @Transactional
    fun clear(mandateId: Long, userId: Long) {
        val mandate = load(mandateId)
        if (mandate.ownershipOverrideReason != null) {
            dao.save(
                mandate.copy(
                    ownershipOverrideReason = null,
                    ownershipOverrideBy = null,
                    ownershipOverrideAt = null,
                ),
            )
        }
        log(mandateId, action = "clear", userId = userId, reason = null)
    }

    /** The mandate under a write lock; a SEARCH one has no owner verdict, so no override either. */
    private fun load(id: Long): MandateEntity {
        val mandate = dao.findByIdForUpdate(id)
            ?: throw NotFoundException(error = Error(code = ErrorCode.MANDATE_NOT_FOUND))
        if (mandate.mandateType != MandateType.LISTING) {
            throw BadRequestException(error = Error(code = ErrorCode.MANDATE_NOT_LISTING))
        }
        return mandate
    }

    /** An override is always backed by captured pieces: one identity and one ownership document, whatever their status. */
    private fun ensureBacked(documents: List<MandateDocumentEntity>) {
        val backed = documents.any { it.type in OwnershipDocumentTypes.IDENTITY } &&
            documents.any { it.type in OwnershipDocumentTypes.OWNERSHIP }
        if (!backed) {
            throw ConflictException(error = Error(code = ErrorCode.MANDATE_OWNERSHIP_OVERRIDE_WITHOUT_DOCUMENTS))
        }
    }

    private fun log(mandateId: Long, action: String, userId: Long, reason: String?) {
        logger.add("mandate_id", mandateId)
        logger.add("ownership_override", action)
        logger.add("user_id", userId)
        if (reason != null) {
            logger.add("reason", reason)
        }
        logger.log()
    }
}
