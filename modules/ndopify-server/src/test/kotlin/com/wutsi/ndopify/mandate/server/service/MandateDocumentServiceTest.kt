package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentRequest
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentPageRepository
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentPageEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.platform.image.PdfRasterizationException
import com.wutsi.ndopify.platform.image.PdfRasterizer
import com.wutsi.ndopify.platform.image.UnprocessableImageException
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.upload.DirectUploadService
import com.wutsi.ndopify.refdata.dto.StorageType
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.springframework.mock.web.MockMultipartFile
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.util.Optional
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The unit-level rules of [MandateDocumentService], independent of the storage: the mandate's lock, and the page
 * previews generated when a page is written and deleted with it.
 */
class MandateDocumentServiceTest {
    private val mandateService = mock<MandateService>()
    private val mandateDao = mock<MandateRepository>()
    private val access = mock<MandateAccessService>()
    private val documentDao = mock<MandateDocumentRepository>()
    private val pageDao = mock<MandateDocumentPageRepository>()
    private val storageProvider = mock<StorageServiceProvider>()
    private val directUpload = mock<DirectUploadService>()
    private val pdfRasterizer = mock<PdfRasterizer>()
    private val storage = mock<StorageService>()
    private val previewService = mock<MandateDocumentPreviewService>()
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
        pdfRasterizer = pdfRasterizer,
        previewService = previewService,
        tx = tx,
        clock = clock,
    )

    private val path1 = "mandate/100/documents/0f8fad5b-d9cb-469f-a165-70867728950e.png"
    private val path2 = "mandate/100/documents/7c9e6679-7425-40de-944b-e07fc1f90ae7.png"
    private val preview = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())

    /** A real PNG: the service sniffs the bytes, so the fixture must be detected as an image. */
    private fun png(): ByteArray = ByteArrayOutputStream()
        .also { ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", it) }
        .toByteArray()

    /** Real PDF bytes (detected as application/pdf); the rasterizer itself is mocked. */
    private fun pdf(): ByteArray = PDDocument().use { document ->
        document.addPage(PDPage(PDRectangle(200f, 100f)))
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }

    private fun imageFile(name: String = "page.png") = MockMultipartFile("files", name, "image/png", png())

    private fun pdfFile(name: String = "scan.pdf") = MockMultipartFile("files", name, "application/pdf", pdf())

    /** The mandate is writable and the storage is S3; the transaction opens. */
    private fun writable() {
        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateService).findById(100L)
        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateDao).findByIdForUpdate(100L)
        doReturn(storage).whenever(storageProvider).get()
        doReturn(storage).whenever(storageProvider).get(StorageType.S3)
        doReturn(storage).whenever(storageProvider).get(StorageType.LOCAL)
        doReturn(StorageType.S3).whenever(storage).type()
        doReturn(mock<TransactionStatus>()).whenever(transactionManager).getTransaction(any())
    }

    @Test
    fun `create - a mandate closed during the upload deletes the new objects`() {
        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateService).findById(100L)
        // Closed meanwhile: findByIdForUpdate re-reads it under the lock
        doReturn(MandateEntity(id = 100L, status = MandateStatus.CLOSED)).whenever(mandateDao).findByIdForUpdate(100L)
        doReturn(storage).whenever(storageProvider).get()
        doReturn(storage).whenever(storageProvider).get(StorageType.S3)
        doReturn(StorageType.S3).whenever(storage).type()
        doReturn("image/png").whenever(directUpload).verify(any(), any())
        doReturn(preview).whenever(previewService).generate(any(), any(), any(), anyOrNull(), anyOrNull())
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
        verify(previewService).deleteQuietly(100, path1, StorageType.S3)
        verify(previewService).deleteQuietly(100, path2, StorageType.S3)
    }

    @Test
    fun `create - an image that cannot be previewed deletes the new objects and is a 400`() {
        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateService).findById(100L)
        doReturn(storage).whenever(storageProvider).get()
        doReturn(storage).whenever(storageProvider).get(StorageType.S3)
        doReturn(StorageType.S3).whenever(storage).type()
        doReturn("image/png").whenever(directUpload).verify(any(), any())
        doThrow(UnprocessableImageException("boom"))
            .whenever(previewService).generate(any(), any(), any(), anyOrNull(), anyOrNull())
        doReturn(null).whenever(pageDao).findByPath(any())

        val exception = assertFailsWith<BadRequestException> {
            service.create(
                100,
                CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path1, path2)),
            )
        }

        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, exception.error.code)
        assertEquals("path", exception.error.parameter?.name)
        assertNull(exception.error.parameter?.value)
        verify(storage).delete(path1)
        verify(storage).delete(path2)
        verify(previewService).deleteQuietly(100, path1, StorageType.S3)
        verify(previewService).deleteQuietly(100, path2, StorageType.S3)
        verify(documentDao, never()).save(any<MandateDocumentEntity>())
        verify(pageDao, never()).save(any<MandateDocumentPageEntity>())
    }

    @Test
    fun `replacePage - a page save that throws deletes the new preview, not the old one`() {
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
        verify(previewService).deleteQuietly(100, newPath, StorageType.S3)
        verify(previewService, never()).deleteQuietly(eq(100), eq(oldPath), any())
    }

    @Test
    fun `replacePage - the replaced page's preview is deleted after the save`() {
        val oldPath = "mandate/100/documents/page-1000-1.png"
        val newPath = "mandate/100/documents/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        val page = MandateDocumentPageEntity(
            id = 2001L,
            documentId = 1000L,
            pageIndex = 1,
            path = oldPath,
            storageType = StorageType.LOCAL,
        )

        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateService).findById(100L)
        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateDao).findByIdForUpdate(100L)
        doReturn(Optional.of(MandateDocumentEntity(id = 1000L, mandateId = 100L))).whenever(documentDao).findById(1000L)
        doReturn(Optional.of(page)).whenever(pageDao).findById(2001L)
        doReturn(null).whenever(pageDao).findByPath(newPath)
        doReturn(storage).whenever(storageProvider).get()
        doReturn(storage).whenever(storageProvider).get(StorageType.LOCAL)
        doReturn(StorageType.S3).whenever(storage).type()
        doReturn("image/png").whenever(directUpload).verify(newPath, MandateDocumentService.prefix(100L))
        doReturn(page.copy(path = newPath)).whenever(pageDao).save(any<MandateDocumentPageEntity>())
        doReturn(mock<TransactionStatus>()).whenever(transactionManager).getTransaction(any())

        service.replacePage(100, 1000, 2001, newPath)

        verify(storage).delete(oldPath)
        verify(previewService).deleteQuietly(100, oldPath, StorageType.LOCAL)
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
        verify(previewService, never()).deleteQuietly(any(), any(), any())
    }

    @Test
    fun `deletePage - the deleted page's preview is deleted with it`() {
        val page = MandateDocumentPageEntity(
            id = 3000L,
            documentId = 2000L,
            pageIndex = 0,
            path = "mandate/100/documents/page-2000-0.png",
            storageType = StorageType.S3,
        )
        val other = page.copy(id = 3001L, pageIndex = 1, path = "mandate/100/documents/page-2000-1.png")

        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateService).findById(100L)
        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateDao).findByIdForUpdate(100L)
        doReturn(Optional.of(MandateDocumentEntity(id = 2000L, mandateId = 100L))).whenever(documentDao).findById(2000L)
        doReturn(Optional.of(page)).whenever(pageDao).findById(3000L)
        doReturn(listOf(page, other)).whenever(pageDao).findByDocumentIdOrderByPageIndex(2000L)
        doReturn(MandateDocumentEntity(id = 2000L, mandateId = 100L)).whenever(documentDao).save(any())
        doReturn(storage).whenever(storageProvider).get(StorageType.S3)
        doReturn(mock<TransactionStatus>()).whenever(transactionManager).getTransaction(any())

        service.deletePage(100, 2000, 3000)

        verify(storage).delete(page.path)
        verify(previewService).deleteQuietly(100, page.path, StorageType.S3)
    }

    @Test
    fun `delete - every page's preview is deleted with the document`() {
        val page1 = MandateDocumentPageEntity(
            id = 3000L,
            documentId = 2000L,
            pageIndex = 0,
            path = "mandate/100/documents/page-2000-0.png",
            storageType = StorageType.S3,
        )
        val page2 = page1.copy(id = 3001L, pageIndex = 1, path = "mandate/100/documents/page-2000-1.png")

        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateService).findById(100L)
        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateDao).findByIdForUpdate(100L)
        doReturn(Optional.of(MandateDocumentEntity(id = 2000L, mandateId = 100L))).whenever(documentDao).findById(2000L)
        doReturn(listOf(page1, page2)).whenever(pageDao).findByDocumentIdOrderByPageIndex(2000L)
        doReturn(storage).whenever(storageProvider).get(StorageType.S3)
        doReturn(mock<TransactionStatus>()).whenever(transactionManager).getTransaction(any())

        service.delete(100, 2000)

        verify(storage, times(2)).delete(any())
        verify(previewService).deleteQuietly(100, page1.path, StorageType.S3)
        verify(previewService).deleteQuietly(100, page2.path, StorageType.S3)
    }

    @Test
    fun `uploadPage - an image that cannot be previewed deletes the new object and is a 400`() {
        val file = MockMultipartFile("file", "page.png", "image/png", png())

        doReturn(MandateEntity(id = 100L, status = MandateStatus.ASSIGNED)).whenever(mandateService).findById(100L)
        doReturn(Optional.of(MandateDocumentEntity(id = 2000L, mandateId = 100L))).whenever(documentDao).findById(2000L)
        doReturn(storage).whenever(storageProvider).get()
        doReturn(storage).whenever(storageProvider).get(StorageType.S3)
        doReturn(StorageType.S3).whenever(storage).type()
        doReturn("image/png").whenever(directUpload).check(file)
        doThrow(UnprocessableImageException("boom"))
            .whenever(previewService).generate(any(), any(), any(), anyOrNull(), anyOrNull())

        val exception = assertFailsWith<BadRequestException> {
            service.uploadPage(100, 2000, file)
        }

        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, exception.error.code)
        assertEquals("file", exception.error.parameter?.name)
        verify(storage).delete(any())
        verify(previewService).deleteQuietly(eq(100), any(), eq(StorageType.S3))
        verify(pageDao, never()).save(any<MandateDocumentPageEntity>())
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
        verify(previewService, never()).deleteQuietly(any(), any(), any())
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
        verify(previewService, never()).deleteQuietly(any(), any(), any())
        verify(documentDao, never()).save(any<MandateDocumentEntity>())
        verify(pageDao, never()).save(any<MandateDocumentPageEntity>())
    }

    @Test
    fun `upload - a pdf becomes its rendered pages in the pdf's order`() {
        val first = preview + 1
        val second = preview + 2
        writable()
        doReturn(listOf(first, second)).whenever(pdfRasterizer).rasterize(any(), eq(MandateDocumentService.MAX_PAGES))
        doReturn(MandateDocumentEntity(id = 2000L, mandateId = 100L)).whenever(documentDao).save(any())
        doAnswer { it.getArgument<MandateDocumentPageEntity>(0) }.whenever(pageDao).save(any())

        service.upload(100, DocumentType.LAND_TITLE, null, listOf(pdfFile()))

        val pages = argumentCaptor<MandateDocumentPageEntity>()
        verify(pageDao, times(2)).save(pages.capture())
        assertEquals(listOf(0, 1), pages.allValues.map { it.pageIndex })
        assertTrue(pages.allValues.all { it.mimeType == "image/jpeg" }, "${pages.allValues.map { it.mimeType }}")
        assertTrue(pages.allValues.all { it.path.endsWith(".jpg") }, "${pages.allValues.map { it.path }}")
        val paths = argumentCaptor<String>()
        verify(storage, times(2)).store(paths.capture(), any(), eq("image/jpeg"))
        assertTrue(paths.allValues.all { it.endsWith(".jpg") }, "${paths.allValues}")
        // The preview of each rendered page, from the rendered bytes (the PDF itself is never stored)
        verify(previewService).generate(eq(100L), eq(storage), any(), eq("image/jpeg"), eq(first))
        verify(previewService).generate(eq(100L), eq(storage), any(), eq("image/jpeg"), eq(second))
        verify(storage, never()).store(argThat { endsWith(".pdf") }, any(), any())
    }

    @Test
    fun `upload - images and pdfs keep the order`() {
        writable()
        doReturn("image/png").whenever(directUpload).check(any())
        doReturn(listOf(preview, preview)).whenever(pdfRasterizer).rasterize(any(), eq(MandateDocumentService.MAX_PAGES))
        doReturn(MandateDocumentEntity(id = 2000L, mandateId = 100L)).whenever(documentDao).save(any())
        doAnswer { it.getArgument<MandateDocumentPageEntity>(0) }.whenever(pageDao).save(any())

        service.upload(100, DocumentType.LAND_TITLE, null, listOf(imageFile(), pdfFile(), imageFile()))

        val pages = argumentCaptor<MandateDocumentPageEntity>()
        verify(pageDao, times(4)).save(pages.capture())
        assertEquals(listOf(0, 1, 2, 3), pages.allValues.map { it.pageIndex })
        assertEquals(
            listOf("image/png", "image/jpeg", "image/jpeg", "image/png"),
            pages.allValues.map { it.mimeType },
        )
        val stored = argumentCaptor<String>()
        verify(storage, times(4)).store(stored.capture(), any(), any())
        // The pages reference the stored objects in the order they were written
        assertEquals(stored.allValues, pages.allValues.map { it.path })
    }

    @Test
    fun `upload - a pdf add over 10 pages is refused, and the pages already stored are deleted`() {
        writable()
        doReturn("image/png").whenever(directUpload).check(any())
        doReturn(List(MandateDocumentService.MAX_PAGES) { preview }).whenever(pdfRasterizer)
            .rasterize(any(), eq(MandateDocumentService.MAX_PAGES))

        val exception = assertFailsWith<BadRequestException> {
            service.upload(100, DocumentType.LAND_TITLE, null, listOf(imageFile(), pdfFile()))
        }

        assertEquals(ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES, exception.error.code)
        assertEquals("files", exception.error.parameter?.name)
        val stored = argumentCaptor<String>()
        verify(storage).store(stored.capture(), any(), eq("image/png"))
        verify(storage).delete(stored.firstValue)
        verify(previewService).deleteQuietly(100, stored.firstValue, StorageType.S3)
        verify(documentDao, never()).save(any<MandateDocumentEntity>())
        verify(pageDao, never()).save(any<MandateDocumentPageEntity>())
    }

    @Test
    fun `upload - an encrypted pdf is refused as file and nothing is stored`() {
        writable()
        doThrow(PdfRasterizationException(PdfRasterizationException.Reason.ENCRYPTED, "encrypted"))
            .whenever(pdfRasterizer).rasterize(any(), any())

        val exception = assertFailsWith<BadRequestException> {
            service.upload(100, DocumentType.LAND_TITLE, null, listOf(pdfFile()))
        }

        assertEquals(ErrorCode.MANDATE_DOCUMENT_PDF_ENCRYPTED, exception.error.code)
        assertEquals("file", exception.error.parameter?.name)
        assertNull(exception.error.parameter?.value)
        verify(storage, never()).store(any(), any(), any())
        verify(documentDao, never()).save(any<MandateDocumentEntity>())
    }

    @Test
    fun `uploadPage - a pdf appends after the last page index`() {
        val document = MandateDocumentEntity(
            id = 2000L,
            mandateId = 100L,
            status = DocumentStatus.EXTRACTED,
            dataVersion = 1,
        )
        writable()
        doReturn(Optional.of(document)).whenever(documentDao).findById(2000L)
        doReturn(1).whenever(pageDao).findMaxPageIndex(2000L)
        doReturn(listOf(preview, preview)).whenever(pdfRasterizer).rasterize(any(), eq(MandateDocumentService.MAX_PAGES))
        doAnswer { it.getArgument<MandateDocumentPageEntity>(0) }.whenever(pageDao).save(any())
        doReturn(document).whenever(documentDao).save(any())

        service.uploadPage(100, 2000, pdfFile("page.pdf"))

        val pages = argumentCaptor<MandateDocumentPageEntity>()
        verify(pageDao, times(2)).save(pages.capture())
        assertEquals(listOf(2, 3), pages.allValues.map { it.pageIndex })
        assertTrue(pages.allValues.all { it.path.endsWith(".jpg") })
        verify(documentDao).save(argThat<MandateDocumentEntity> { status == DocumentStatus.UPLOADED && dataVersion == null })
    }

    @Test
    fun `uploadReplacementPage - a one-page pdf replaces the page and resets the extraction`() {
        val oldPath = "mandate/100/documents/page-2000-1.png"
        val page = MandateDocumentPageEntity(
            id = 2001L,
            documentId = 2000L,
            pageIndex = 1,
            path = oldPath,
            storageType = StorageType.LOCAL,
        )
        val document = MandateDocumentEntity(
            id = 2000L,
            mandateId = 100L,
            status = DocumentStatus.EXTRACTED,
            dataVersion = 1,
        )
        writable()
        doReturn(Optional.of(document)).whenever(documentDao).findById(2000L)
        doReturn(Optional.of(page)).whenever(pageDao).findById(2001L)
        doReturn(listOf(preview)).whenever(pdfRasterizer).rasterize(any(), eq(1))
        doAnswer { it.getArgument<MandateDocumentPageEntity>(0) }.whenever(pageDao).save(any())
        doReturn(document).whenever(documentDao).save(any())

        val replaced = service.uploadReplacementPage(100, 2000, 2001, pdfFile("page.pdf"))

        assertEquals(page.id, replaced.id)
        assertEquals(1, replaced.pageIndex)
        assertEquals("image/jpeg", replaced.mimeType)
        assertTrue(replaced.path.endsWith(".jpg"), replaced.path)
        assertEquals(StorageType.S3, replaced.storageType)
        verify(storage).delete(oldPath)
        verify(previewService).deleteQuietly(100, oldPath, StorageType.LOCAL)
        verify(documentDao).save(argThat<MandateDocumentEntity> { status == DocumentStatus.UPLOADED && dataVersion == null })
    }

    @Test
    fun `uploadReplacementPage - a multi-page pdf is refused and the page is unchanged`() {
        val page = MandateDocumentPageEntity(
            id = 2001L,
            documentId = 2000L,
            pageIndex = 1,
            path = "mandate/100/documents/page-2000-1.png",
            storageType = StorageType.LOCAL,
        )
        writable()
        doReturn(Optional.of(MandateDocumentEntity(id = 2000L, mandateId = 100L))).whenever(documentDao).findById(2000L)
        doReturn(Optional.of(page)).whenever(pageDao).findById(2001L)
        doThrow(PdfRasterizationException(PdfRasterizationException.Reason.TOO_MANY_PAGES, "many pages"))
            .whenever(pdfRasterizer).rasterize(any(), eq(1))

        val exception = assertFailsWith<BadRequestException> {
            service.uploadReplacementPage(100, 2000, 2001, pdfFile("page.pdf"))
        }

        assertEquals(ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES, exception.error.code)
        assertEquals("file", exception.error.parameter?.name)
        verify(storage, never()).store(any(), any(), any())
        verify(storage, never()).delete(any())
        verify(previewService, never()).deleteQuietly(any(), any(), any())
        verify(pageDao, never()).save(any<MandateDocumentPageEntity>())
        verify(documentDao, never()).save(any<MandateDocumentEntity>())
    }
}
