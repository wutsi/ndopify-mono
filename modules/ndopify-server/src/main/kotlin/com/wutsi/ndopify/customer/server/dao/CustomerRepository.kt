package com.wutsi.ndopify.customer.server.dao

import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository

interface CustomerRepository : CrudRepository<CustomerEntity, Long>, JpaSpecificationExecutor<CustomerEntity>
