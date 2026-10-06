package com.wutsi.ndopify.party.endpoint

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.GetIdentificationImageUrlResponse
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/party/GetIdentificationImageUrlEndpoint.sql"])
class GetIdentificationImageUrlEndpointTest : TenantAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val storage = mock<StorageService>()

    @BeforeEach
    fun setUpStorage() {
        doReturn(storage).whenever(storageProvider).get(StorageType.LOCAL)
    }

    @Test
    fun url() {
        doReturn(URL("https://storage.example.com/identifications/id-100/images/img-100-front.jpg?sig=abc"))
            .whenever(storage).generatePresignedUrl(eq("identifications/id-100/images/img-100-front.jpg"), eq(3600))

        val response = rest.getForEntity(
            "/v1/identifications/images/img-100-front/url",
            GetIdentificationImageUrlResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(
            "https://storage.example.com/identifications/id-100/images/img-100-front.jpg?sig=abc",
            response.body?.url,
        )
    }

    @Test
    fun `image not found`() {
        val response = rest.getForEntity(
            "/v1/identifications/images/unknown-image/url",
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_IMAGE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `no content`() {
        val response = rest.getForEntity(
            "/v1/identifications/images/img-100-back/url",
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_IMAGE_NO_CONTENT, response.body?.error?.code)
    }

    @Test
    fun `image from another tenant is not found`() {
        val response = rest.getForEntity(
            "/v1/identifications/images/img-900-front/url",
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_IMAGE_NOT_FOUND, response.body?.error?.code)
    }
}
