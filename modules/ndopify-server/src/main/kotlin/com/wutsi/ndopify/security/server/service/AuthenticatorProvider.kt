package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.server.service.authenticator.GoogleOneTapAuthenticator
import com.wutsi.ndopify.security.server.service.authenticator.OtpAuthenticator
import com.wutsi.ndopify.security.server.service.authenticator.PasswordAuthenticator
import org.springframework.stereotype.Service

@Service
class AuthenticatorProvider(
    private val password: PasswordAuthenticator,
    private val googleOneTap: GoogleOneTapAuthenticator,
    private val otp: OtpAuthenticator,
) {
    fun get(type: AuthType): Authenticator? {
        return when (type) {
            AuthType.PASSWORD -> password
            AuthType.GOOGLE_ONE_TAP -> googleOneTap
            AuthType.OTP -> null // Not supported until we support OneTap verification in GoogleOneTapAuthenticator
            else -> null
        }
    }
}
