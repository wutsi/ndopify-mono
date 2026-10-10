package com.wutsi.ndopify.npc.service.security

import com.wutsi.ndopify.security.dto.JwtPrincipal
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority

class JwtAuthentication(private val principal: JwtPrincipal) : Authentication {
    private var authenticated: Boolean = true

    override fun getName(): String? {
        return principal.name
    }

    override fun getPrincipal(): Any? {
        return principal
    }

    override fun getCredentials(): Any? {
        return null
    }

    override fun getDetails(): Any? {
        return null
    }

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return emptyList()
    }

    override fun isAuthenticated(): Boolean {
        return authenticated
    }

    override fun setAuthenticated(isAuthenticated: Boolean) {
        authenticated = isAuthenticated
    }
}
