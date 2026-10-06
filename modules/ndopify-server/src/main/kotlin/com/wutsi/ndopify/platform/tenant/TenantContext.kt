package com.wutsi.ndopify.platform.tenant

object TenantContext {
    private val value: ThreadLocal<Long?> = ThreadLocal()

    fun set(tenantId: Long?) {
        value.set(tenantId)
    }

    fun get(): Long? =
        value.get()

    fun remove() {
        value.remove()
    }
}
