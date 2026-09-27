package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.server.service.authenticator.GoogleOneTapAuthenticator
import com.wutsi.ndopify.security.server.service.authenticator.PasswordAuthenticator
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import kotlin.test.assertEquals

class AuthenticatorFactoryTest {
    private val password = mock<PasswordAuthenticator>()
    private val googleOneTap = mock<GoogleOneTapAuthenticator>()
    private val factory = AuthenticatorFactory(password, googleOneTap)

    @Test
    fun password() {
        assertEquals(password, factory.getAuthenticator(AuthType.PASSWORD))
    }

    @Test
    fun googleOneTag() {
        assertEquals(googleOneTap, factory.getAuthenticator(AuthType.GOOGLE_ONE_TAP))
    }

    @Test
    fun unknown() {
        assertEquals(null, factory.getAuthenticator(AuthType.UNKNOWN))
    }
}
