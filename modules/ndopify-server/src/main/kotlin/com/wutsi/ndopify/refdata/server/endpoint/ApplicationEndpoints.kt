package com.wutsi.ndopify.refdata.server.endpoint

import com.wutsi.ndopify.refdata.dto.SearchApplicationResponse
import com.wutsi.ndopify.refdata.server.mapper.ApplicationMapper
import com.wutsi.ndopify.refdata.server.service.ApplicationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/applications")
class ApplicationEndpoints(
    private val service: ApplicationService,
    private val mapper: ApplicationMapper,
) {
    @GetMapping
    fun search(): SearchApplicationResponse {
        val apps = service.search()
        return SearchApplicationResponse(
            applications = apps.map { tenant -> mapper.toApplication(tenant) }
        )
    }
}
