package com.wutsi.ndopify.platform.identity

import com.wutsi.ndopify.platform.identity.ai.AiIdentityKycService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.mockito.Mockito.mock

class IdentityKycServiceProviderTest {
    val ai = mock<AiIdentityKycService>()
    val provider = IdentityKycServiceProvider(ai)

    @Test
    fun get() {
        assertNull(provider.get(com.wutsi.ndopify.refdata.dto.IdentityType.UNKNOWN))
        assertEquals(ai, provider.get(com.wutsi.ndopify.refdata.dto.IdentityType.NATIONAL_ID))
        assertEquals(ai, provider.get(com.wutsi.ndopify.refdata.dto.IdentityType.PASSPORT))
        assertEquals(ai, provider.get(com.wutsi.ndopify.refdata.dto.IdentityType.DRIVER_LICENSE))
    }
}
