package com.wutsi.ndopify.web.config

import com.wutsi.ndopify.web.core.service.security.AccessTokenRenewalFilter
import com.wutsi.ndopify.web.core.service.security.JwtAuthenticationFilter
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.web.DefaultSecurityFilterChain
import org.springframework.security.web.SecurityFilterChain
import kotlin.test.assertTrue

/** The chain's order: the renewal filter must run before the request is authenticated. */
@SpringBootTest
class SecurityFilterChainTest {
    @Autowired
    private lateinit var chain: SecurityFilterChain

    @Test
    fun `renews the access token before authenticating the request`() {
        val filters = (chain as DefaultSecurityFilterChain).filters
        val renewal = filters.indexOfFirst { it is AccessTokenRenewalFilter }
        val jwt = filters.indexOfFirst { it is JwtAuthenticationFilter }

        assertTrue(renewal >= 0, "the renewal filter is in the chain")
        assertTrue(jwt > renewal, "the renewal runs before JwtAuthenticationFilter")
    }
}
