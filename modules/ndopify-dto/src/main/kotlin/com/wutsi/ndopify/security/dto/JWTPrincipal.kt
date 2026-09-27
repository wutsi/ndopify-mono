package com.wutsi.ndopify.security.dto

import com.auth0.jwt.interfaces.DecodedJWT
import java.security.Principal

open class JWTPrincipal(private val jwt: DecodedJWT) : Principal {
    companion object {
        const val CLAIM_USER_ID = "userId"
        const val CLAIM_TENANT_ID = "tenantId"
        const val CLAIM_APPLICATION = "application"
        const val CLAIM_ROLE = "role"
    }

    override fun getName(): String? {
        return getUserId().toString()
    }

    fun getUserId(): Long {
        return jwt.getClaim(CLAIM_USER_ID).asLong()
    }

    fun getTenantId(): Long? {
        return jwt.getClaim(CLAIM_TENANT_ID).asLong()
    }

    fun getSubject(): String {
        return jwt.subject
    }

    fun getApplication(): String {
        return jwt.getClaim(CLAIM_APPLICATION).`as`(String::class.java)
    }

    fun getRoles(): Array<String> {
        return jwt.getClaim(CLAIM_ROLE).asArray(String::class.java)
    }
}
