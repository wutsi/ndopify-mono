package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentRequest
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentPageRepository
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.upload.DirectUploadService
import com.wutsi.ndopify.refdata.dto.StorageType
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The unit-level rules of [MandateDocumentService.create], independent of the storage: a mandate closed during the
 * storage I/O is refused under the lock, and the objects just uploaded are deleted.
 */
class MandateDocumentServiceTest {
    private val mandateService = mock<MandateService>()
    private val mandateDao = mock<MandateRepository>()
    private val access = mock<MandateAccessService>()
    private val documentDao = mock<MandateDocumentRepository>()
    private val pageDao = mock<MandateDocumentPageRepository>()
    private val storageProvider = mock<StorageServiceProvider>()
    private val directUpload = mock<DirectUploadService>()
    private val storage = mock<StorageService>()
    private val transactionManager = mock<PlatformTransactionManager>()

    private val tx = TransactionTemplate(transactionManager)
    private val clock = mock<Clock>()

    private val service = MandateDocumentService(
        mandateService = mandateService,
        mandateDao = mandateDao,
        access = access,
        documentDao = documentDao,
        pageDao = pageDao,
        storageProvider = storageProvider,
        directUpload = directUpload,
        tx = tx,
        clock = clock,
        urlTTL = 3600,
    )

    private val path1 = "mandate/100/documents/0f8fad5b-d9cb-469f-a165-70867728950e.png"
    private val path2 = "mandate/100/documents/7c9e6679-7425-40de-944b-e07fc1f90ae7.png"

    @Test
    fun `create - a mandate closed during the upload deletes the new objects`() {
        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateService).findById(100L)
        // Closed meanwhile: findByIdForUpdate re-reads it under the lock
        doReturn(MandateEntity(id = 100L, status = MandateStatus.CLOSED)).whenever(mandateDao).findByIdForUpdate(100L)
        doReturn(storage).whenever(storageProvider).get()
        doReturn(storage).whenever(storageProvider).get(StorageType.S3)
        doReturn(StorageType.S3).whenever(storage).type()
        doReturn("image/png").whenever(directUpload).verify(any(), any())
        doReturn(mock<TransactionStatus>()).whenever(transactionManager).getTransaction(any())

        val exception = assertFailsWith<ConflictException> {
            service.create(
                100,
                CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path1, path2)),
            )
        }

        assertEquals(ErrorCode.MANDATE_ALREADY_CLOSED, exception.error.code)
        verify(storage).delete(path1)
        verify(storage).delete(path2)
    }
}
