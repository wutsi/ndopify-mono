package com.wutsi.ndopify.refdata.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.refdata.server.dao.TenantRepository
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import org.springframework.stereotype.Service

@Service
class TenantService(
    private val dao: TenantRepository
) {
    fun findById(id: Long): TenantEntity {
        val tenant = dao.findById(id)
            .orElseThrow { NotFoundException(Error(ErrorCode.TENANT_NOT_FOUND)) }
        return tenant
    }

    fun search(status: Boolean? = null): List<TenantEntity> {
        return if (status == null) {
            dao.findAll().toList()
        } else {
            dao.findByActive(status)
        }
    }
}
