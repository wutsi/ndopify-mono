package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.server.service.authenticator.GoogleOneTapAuthenticator
import com.wutsi.ndopify.security.server.service.authenticator.PasswordAuthenticator
import org.springframework.stereotype.Service

@Service
class AuthenticatorFactory(
    private val password: PasswordAuthenticator,
    private val googleOneTap: GoogleOneTapAuthenticator,
) {
    fun getAuthenticator(type: AuthType): Authenticator? {
        return when (type) {
            AuthType.PASSWORD -> password
            AuthType.GOOGLE_ONE_TAP -> googleOneTap
            else -> null
        }
    }
}
