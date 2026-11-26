package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentRequest
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentPageRepository
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentPageEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.upload.DirectUploadService
import com.wutsi.ndopify.refdata.dto.StorageType
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.Optional
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

    @Test
    fun `replacePage - a page save that throws deletes the new object, not the old one`() {
        val oldPath = "mandate/100/documents/page-1000-1.png"
        val newPath = "mandate/100/documents/0f8fad5b-d9cb-469f-a165-70867728950e.png"

        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateService).findById(100L)
        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateDao).findByIdForUpdate(100L)
        doReturn(Optional.of(MandateDocumentEntity(id = 1000L, mandateId = 100L))).whenever(documentDao).findById(1000L)
        doReturn(
            Optional.of(
                MandateDocumentPageEntity(
                    id = 2001L,
                    documentId = 1000L,
                    pageIndex = 1,
                    path = oldPath,
                    storageType = StorageType.LOCAL,
                ),
            ),
        ).whenever(pageDao).findById(2001L)
        doReturn(null).whenever(pageDao).findByPath(newPath)
        doReturn(storage).whenever(storageProvider).get()
        doReturn(storage).whenever(storageProvider).get(StorageType.S3)
        doReturn(StorageType.S3).whenever(storage).type()
        doReturn("image/png").whenever(directUpload).verify(newPath, MandateDocumentService.prefix(100L))
        doReturn(mock<TransactionStatus>()).whenever(transactionManager).getTransaction(any())
        doThrow(RuntimeException("boom")).whenever(pageDao).save(any<MandateDocumentPageEntity>())

        assertFailsWith<RuntimeException> {
            service.replacePage(100, 1000, 2001, newPath)
        }

        verify(storage).delete(newPath)
        verify(storage, never()).delete(oldPath)
    }

    @Test
    fun `deletePage - the only page of a document is refused under the lock`() {
        val page = MandateDocumentPageEntity(
            id = 3000L,
            documentId = 2000L,
            pageIndex = 0,
            path = "mandate/100/documents/page-2000-0.png",
            storageType = StorageType.S3,
        )

        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateService).findById(100L)
        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateDao).findByIdForUpdate(100L)
        doReturn(Optional.of(MandateDocumentEntity(id = 2000L, mandateId = 100L))).whenever(documentDao).findById(2000L)
        doReturn(Optional.of(page)).whenever(pageDao).findById(3000L)
        // The document has a single page: the last-page check must refuse, under the lock.
        doReturn(listOf(page)).whenever(pageDao).findByDocumentIdOrderByPageIndex(2000L)
        doReturn(mock<TransactionStatus>()).whenever(transactionManager).getTransaction(any())

        val exception = assertFailsWith<BadRequestException> {
            service.deletePage(100, 2000, 3000)
        }

        assertEquals(ErrorCode.MANDATE_DOCUMENT_LAST_PAGE, exception.error.code)
        verify(pageDao, never()).delete(any())
    }

    @Test
    fun `addPage - a path another page takes is refused under the lock`() {
        val newPath = "mandate/100/documents/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        val otherPage = MandateDocumentPageEntity(
            id = 4000L,
            documentId = 2000L,
            pageIndex = 0,
            path = "mandate/100/documents/page-2000-0.png",
            storageType = StorageType.S3,
        )

        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateService).findById(100L)
        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateDao).findByIdForUpdate(100L)
        doReturn(Optional.of(MandateDocumentEntity(id = 3000L, mandateId = 100L))).whenever(documentDao).findById(3000L)
        // The pre-check sees nothing (so the verify runs), the in-lock check sees another page's path.
        doReturn(null, otherPage).whenever(pageDao).findByPath(newPath)
        doReturn(storage).whenever(storageProvider).get()
        doReturn(StorageType.S3).whenever(storage).type()
        doReturn("image/png").whenever(directUpload).verify(newPath, MandateDocumentService.prefix(100L))
        doReturn(mock<TransactionStatus>()).whenever(transactionManager).getTransaction(any())

        val exception = assertFailsWith<BadRequestException> {
            service.addPage(100, 3000, newPath)
        }

        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, exception.error.code)
        verify(storage, never()).delete(any())
    }

    @Test
    fun `create - a path another page takes is refused under the lock`() {
        val path = "mandate/100/documents/11111111-2222-3333-4444-555555555555.png"
        val otherPage = MandateDocumentPageEntity(
            id = 5000L,
            documentId = 2000L,
            pageIndex = 0,
            path = path,
            storageType = StorageType.S3,
        )

        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateService).findById(100L)
        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateDao).findByIdForUpdate(100L)
        // The pre-lock check sees nothing (so the verify runs), the in-lock check sees another page's path.
        doReturn(null, otherPage).whenever(pageDao).findByPath(path)
        doReturn(storage).whenever(storageProvider).get()
        doReturn(StorageType.S3).whenever(storage).type()
        doReturn("image/png").whenever(directUpload).verify(path, MandateDocumentService.prefix(100L))
        doReturn(mock<TransactionStatus>()).whenever(transactionManager).getTransaction(any())

        val exception = assertFailsWith<BadRequestException> {
            service.create(100, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path)))
        }

        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, exception.error.code)
        verify(storage, never()).delete(any())
        verify(documentDao, never()).save(any<MandateDocumentEntity>())
        verify(pageDao, never()).save(any<MandateDocumentPageEntity>())
    }
}
