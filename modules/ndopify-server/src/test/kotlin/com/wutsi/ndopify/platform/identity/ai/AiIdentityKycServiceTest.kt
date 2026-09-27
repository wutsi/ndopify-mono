package com.wutsi.ndopify.platform.identity.ai

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.identity.model.IdKycMatchRequest
import com.wutsi.ndopify.platform.identity.model.IdentityInfo
import com.wutsi.ndopify.refdata.dto.IdentityStatus
import com.wutsi.ndopify.refdata.dto.IdentityType
import org.mockito.Mockito.mock
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AiIdentityKycServiceTest {
    private val extractor = mock<AiIdentityInfoExtractor>()
    private val service = AiIdentityKycService(extractor)

    @Test
    fun `matching holder name, country and document type`() {
        val image = File("/foo/bar.jpg")
        doReturn(
            IdentityInfo(
                type = IdentityType.PASSPORT,
                firstName = "John",
                lastName = "Doe",
                countryCode = "CM",
                status = IdentityStatus.VALID,
            ),
        ).whenever(extractor).extract(listOf(image))

        val response = service.kycMatch(
            IdKycMatchRequest(
                holderName = "John Doe",
                countryCode = "CM",
                images = listOf(image),
                identityType = IdentityType.PASSPORT,
            ),
        )

        assertEquals("John Doe", response.holderName)
        assertEquals(IdentityStatus.VALID, response.status)
        assertEquals(1.0, response.holderNameScore)
        assertEquals(1.0, response.countryCodeScore)
        assertEquals(1.0, response.documentTypeScore)
    }

    @Test
    fun `mismatching country code`() {
        doReturn(
            IdentityInfo(
                type = IdentityType.PASSPORT,
                firstName = "John",
                lastName = "Doe",
                countryCode = "FR",
                status = IdentityStatus.VALID,
            ),
        ).whenever(extractor).extract(any())

        val response = service.kycMatch(
            IdKycMatchRequest(
                holderName = "John Doe",
                countryCode = "CM",
                identityType = IdentityType.PASSPORT,
            ),
        )

        assertEquals(0.0, response.countryCodeScore)
    }

    @Test
    fun `country code comparison is case-insensitive`() {
        doReturn(
            IdentityInfo(
                type = IdentityType.PASSPORT,
                firstName = "John",
                lastName = "Doe",
                countryCode = "cm",
                status = IdentityStatus.VALID,
            ),
        ).whenever(extractor).extract(any())

        val response = service.kycMatch(
            IdKycMatchRequest(
                holderName = "John Doe",
                countryCode = "CM",
                identityType = IdentityType.PASSPORT,
            ),
        )

        assertEquals(1.0, response.countryCodeScore)
    }

    @Test
    fun `mismatching document type`() {
        doReturn(
            IdentityInfo(
                type = IdentityType.NATIONAL_ID,
                firstName = "John",
                lastName = "Doe",
                countryCode = "CM",
                status = IdentityStatus.VALID,
            ),
        ).whenever(extractor).extract(any())

        val response = service.kycMatch(
            IdKycMatchRequest(
                holderName = "John Doe",
                countryCode = "CM",
                identityType = IdentityType.PASSPORT,
            ),
        )

        assertEquals(0.0, response.documentTypeScore)
    }

    @Test
    fun `holder name does not match`() {
        doReturn(
            IdentityInfo(
                type = IdentityType.PASSPORT,
                firstName = "Xyz",
                lastName = "Qwerty",
                countryCode = "CM",
                status = IdentityStatus.VALID,
            ),
        ).whenever(extractor).extract(any())

        val response = service.kycMatch(
            IdKycMatchRequest(
                holderName = "John Doe",
                countryCode = "CM",
                identityType = IdentityType.PASSPORT,
            ),
        )

        assertTrue(response.holderNameScore < 0.5)
    }

    @Test
    fun `expired document`() {
        doReturn(
            IdentityInfo(
                type = IdentityType.PASSPORT,
                firstName = "John",
                lastName = "Doe",
                countryCode = "CM",
                status = IdentityStatus.EXPIRED,
            ),
        ).whenever(extractor).extract(any())

        val response = service.kycMatch(
            IdKycMatchRequest(
                holderName = "John Doe",
                countryCode = "CM",
                identityType = IdentityType.PASSPORT,
            ),
        )

        assertEquals(IdentityStatus.EXPIRED, response.status)
    }

    @Test
    fun `passes document urls to extractor`() {
        val images = listOf(File("/foo/bar1.jpg"), File("/foo/bar2.jpg"))
        doReturn(IdentityInfo()).whenever(extractor).extract(images)

        service.kycMatch(
            IdKycMatchRequest(
                holderName = "John Doe",
                countryCode = "CM",
                images = images,
            ),
        )

        verify(extractor).extract(images)
    }
}
