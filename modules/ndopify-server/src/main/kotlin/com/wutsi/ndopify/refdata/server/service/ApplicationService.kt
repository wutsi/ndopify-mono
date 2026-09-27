package com.wutsi.ndopify.refdata.server.service

import com.wutsi.ndopify.refdata.server.dao.ApplicationRepository
import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import org.springframework.stereotype.Service

@Service
class ApplicationService(
    private val dao: ApplicationRepository
) {
    fun findByCodeOrNull(code: String): ApplicationEntity? {
        return dao.findByCode(code)
            .orElse(null)
    }

    fun search(): List<ApplicationEntity> {
        return dao.findAll().toList()
    }
}
