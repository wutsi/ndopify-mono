package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NdKycClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdKycClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdKycClient("http://localhost:8080", builder)
    }

    @Test
    fun current() {
        server.expect(requestTo("http://localhost:8080/v1/kyc/cases?partyId=100&limit=1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"cases":[{"id":"case-1","partyId":100,"status":"PENDING_VERIFICATION","createdAt":0,"updatedAt":0}]}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://localhost:8080/v1/kyc/cases/case-1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"case":{"id":"case-1","partyId":100,"identificationType":"NATIONAL_ID","status":"PENDING_VERIFICATION","createdAt":0,"updatedAt":0,"verifications":[],"images":[{"id":"img-1","caseId":"case-1","identificationType":"NATIONAL_ID","imageType":"FRONT","createdAt":0,"modifiedAt":0}]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val case = client.current(100)

        assertEquals("case-1", case?.id)
        assertEquals(KycStatus.PENDING_VERIFICATION, case?.status)
        assertEquals(listOf(KycImageType.FRONT), case?.images?.map { it.imageType })
        server.verify()
    }

    @Test
    fun `current when the party has no case`() {
        server.expect(requestTo("http://localhost:8080/v1/kyc/cases?partyId=100&limit=1"))
            .andRespond(withSuccess("""{"cases":[]}""", MediaType.APPLICATION_JSON))

        assertNull(client.current(100))
        server.verify()
    }

    @Test
    fun create() {
        server.expect(requestTo("http://localhost:8080/v1/kyc/cases"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"partyId":100,"identificationType":"NATIONAL_ID"}"""))
            .andRespond(withSuccess("""{"caseId":"case-2"}""", MediaType.APPLICATION_JSON))

        assertEquals("case-2", client.create(100))
        server.verify()
    }

    @Test
    fun upload() {
        server.expect(
            requestTo("http://localhost:8080/v1/kyc/cases/case-1/images/upload?identificationType=NATIONAL_ID&imageType=SELFIE"),
        )
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"multipartFile\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Content-Type: image/jpeg")))
            .andRespond(withSuccess())

        client.upload("case-1", KycImageType.SELFIE, byteArrayOf(1, 2, 3), "image/jpeg")
        server.verify()
    }

    @Test
    fun `upload on a case that is not pending`() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://localhost:8080/v1/kyc/cases/case-1/images/upload")))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":{"code":"urn:wutsi:ndopify:error:kyc-case:not-pending"}}"""),
            )

        assertFailsWith<HttpClientErrorException> {
            client.upload("case-1", KycImageType.FRONT, byteArrayOf(1), "image/png")
        }
    }

    @Test
    fun submit() {
        server.expect(requestTo("http://localhost:8080/v1/kyc/cases/case-1/submit"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess())

        client.submit("case-1")
        server.verify()
    }

    @Test
    fun imageUrl() {
        server.expect(requestTo("http://localhost:8080/v1/kyc/cases/images/img-1/url"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"url":"https://s3/img-1.jpg"}""", MediaType.APPLICATION_JSON))

        assertEquals("https://s3/img-1.jpg", client.imageUrl("img-1"))
        server.verify()
    }
}
