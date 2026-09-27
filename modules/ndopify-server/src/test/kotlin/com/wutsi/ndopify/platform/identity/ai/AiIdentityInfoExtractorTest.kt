package com.wutsi.ndopify.platform.identity.ai

import com.wutsi.ndopify.refdata.dto.IdentityStatus
import com.wutsi.ndopify.refdata.dto.IdentityType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.File
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiIdentityInfoExtractorTest {
    @Autowired
    private lateinit var extractor: AiIdentityInfoExtractor

    @Test
    fun `national_id - CM`() {
        val images = listOf(
            AiIdentityInfoExtractorTest::class.java.getResource("/files/identity/cm/cni-front.png")?.file,
            AiIdentityInfoExtractorTest::class.java.getResource("/files/identity/cm/cni-back.png")?.file,
        )
        val info = extractor.extract(images.mapNotNull { image -> File(image) })

        assertEquals("CM", info.countryCode)
        assertEquals("DANIEL CHARLES AUGUSTINE", info.firstName)
        assertEquals("TANDENT YANG AHANDA", info.lastName)
        assertEquals("AA00000000", info.number)
        assertEquals("2032-01-12", info.expiryDate)
        assertEquals(IdentityStatus.INVALID, info.status)
        assertEquals(IdentityType.NATIONAL_ID, info.type)
        assertEquals(true, info.invalidityReason?.contains("specimen"))
    }
}
