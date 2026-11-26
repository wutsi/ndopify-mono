package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentRequest
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.UpdateMandateDocumentRequest
import com.wutsi.ndopify.npc.client.ndopify.NdMandateDocumentClient.UploadedPage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NdMandateDocumentClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdMandateDocumentClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdMandateDocumentClient("http://localhost:8080", builder)
    }

    @Test
    fun search() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/100/documents"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"documents":[
                        {"id":12,"mandateId":100,"type":"LAND_TITLE","title":"Titre foncier","status":"UPLOADED","problemCodes":[],"fields":[],"pages":[],"version":0,"createdAt":0,"modifiedAt":0},
                        {"id":11,"mandateId":100,"type":"NATIONAL_ID","title":"CNI","status":"EXTRACTED","problemCodes":[],"fields":[],"pages":[],"version":1,"createdAt":0,"modifiedAt":0}
                    ]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val documents = client.search(100)

        assertEquals(listOf(12L, 11L), documents.map { it.id })
        assertEquals(listOf(DocumentType.LAND_TITLE, DocumentType.NATIONAL_ID), documents.map { it.type })
        assertEquals(listOf(DocumentStatus.UPLOADED, DocumentStatus.EXTRACTED), documents.map { it.status })
        server.verify()
    }

    @Test
    fun `search without document`() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/100/documents"))
            .andRespond(withSuccess("""{"documents":[]}""", MediaType.APPLICATION_JSON))

        assertTrue(client.search(100).isEmpty())
        server.verify()
    }

    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/100/documents/12"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"document":{"id":12,"mandateId":100,"type":"LAND_TITLE","title":"Titre foncier","status":"EXTRACTED","problemCodes":[],"fields":[],
                        "version":3,"createdAt":0,"modifiedAt":0,
                        "pages":[{"id":21,"index":0,"contentType":"image/png","url":"https://s3/page-1","createdAt":0,"modifiedAt":0}]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val document = client.get(100, 12)

        assertEquals(12, document.id)
        assertEquals(DocumentType.LAND_TITLE, document.type)
        assertEquals(3, document.version)
        assertEquals("https://s3/page-1", document.pages.single().url)
        server.verify()
    }

    @Test
    fun presign() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/100/documents/presign"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"contentType":"image/png"}"""))
            .andRespond(withSuccess("""{"url":"https://s3/put","path":"mandate/100/documents/a.png"}""", MediaType.APPLICATION_JSON))

        val presigned = client.presign(100, "image/png")

        assertEquals("https://s3/put", presigned.url)
        assertEquals("mandate/100/documents/a.png", presigned.path)
        server.verify()
    }

    @Test
    fun create() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/100/documents"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"type":"LAND_TITLE","title":null,"paths":["a","b"]}"""))
            .andRespond(withSuccess("""{"documentId":42}""", MediaType.APPLICATION_JSON))

        assertEquals(42, client.create(100, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf("a", "b"))))
        server.verify()
    }

    @Test
    fun upload() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/100/documents/upload"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"type\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("LAND_TITLE")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"title\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"files\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("filename=\"recto.png\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Content-Type: image/png")))
            .andRespond(withSuccess("""{"documentId":42}""", MediaType.APPLICATION_JSON))

        val id = client.upload(
            100,
            DocumentType.LAND_TITLE,
            "Titre foncier",
            listOf(UploadedPage(byteArrayOf(1, 2, 3), "image/png", "recto.png")),
        )

        assertEquals(42, id)
        server.verify()
    }

    @Test
    fun `upload without a title`() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/100/documents/upload"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"title\""))))
            .andRespond(withSuccess("""{"documentId":42}""", MediaType.APPLICATION_JSON))

        client.upload(100, DocumentType.LAND_TITLE, null, listOf(UploadedPage(byteArrayOf(1), "image/png", "a.png")))
        server.verify()
    }

    @Test
    fun `upload - no document id in the answer`() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/100/documents/upload"))
            .andRespond(withSuccess())

        assertEquals(-1, client.upload(100, DocumentType.LAND_TITLE, null, listOf(UploadedPage(byteArrayOf(1), "image/png", "a.png"))))
        server.verify()
    }

    @Test
    fun `upload a replacement page - no page id in the answer`() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/100/documents/12/pages/21/upload"))
            .andRespond(withSuccess())

        assertEquals(-1, client.uploadReplacementPage(100, 12, 21, UploadedPage(byteArrayOf(1), "image/jpeg", "page.jpg")))
        server.verify()
    }

    @Test
    fun rename() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/100/documents/12"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"title":"Titre foncier lot 12","version":3}"""))
            .andRespond(withSuccess())

        client.rename(100, 12, UpdateMandateDocumentRequest(title = "Titre foncier lot 12", version = 3))
        server.verify()
    }

    @Test
    fun `replace a page`() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/100/documents/12/pages/21"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"path":"mandate/100/documents/b.png"}"""))
            .andRespond(withSuccess("""{"pageId":21}""", MediaType.APPLICATION_JSON))

        assertEquals(21, client.replacePage(100, 12, 21, "mandate/100/documents/b.png"))
        server.verify()
    }

    @Test
    fun `upload a replacement page`() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/100/documents/12/pages/21/upload"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"file\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("filename=\"page.jpg\"")))
            .andRespond(withSuccess("""{"pageId":21}""", MediaType.APPLICATION_JSON))

        val id = client.uploadReplacementPage(100, 12, 21, UploadedPage(byteArrayOf(1), "image/jpeg", "page.jpg"))

        assertEquals(21, id)
        server.verify()
    }

    @Test
    fun delete() {
        server.expect(requestTo("http://localhost:8080/v1/mandates/100/documents/12"))
            .andExpect(method(HttpMethod.DELETE))
            .andRespond(withSuccess())

        client.delete(100, 12)
        server.verify()
    }
}
