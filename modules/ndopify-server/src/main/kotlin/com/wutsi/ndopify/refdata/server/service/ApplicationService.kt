package com.wutsi.ndopify.refdata.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.refdata.server.dao.ApplicationRepository
import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import org.springframework.stereotype.Service

@Service
class ApplicationService(
    private val dao: ApplicationRepository
) {
    fun findById(id: Long): ApplicationEntity {
        return dao.findById(id)
            .orElseThrow { NotFoundException(Error(ErrorCode.APPLICATION_NOT_FOUND)) }
    }

    fun findByCodeOrNull(code: String): ApplicationEntity? {
        return dao.findByCode(code)
            .orElse(null)
    }

    fun search(): List<ApplicationEntity> {
        return dao.findAll().toList()
    }
}
