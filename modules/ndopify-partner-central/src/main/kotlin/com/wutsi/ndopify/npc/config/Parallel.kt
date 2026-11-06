package com.wutsi.ndopify.npc.config

import org.springframework.web.context.request.RequestContextHolder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Runs a page's independent REST calls at the same time, one virtual thread each.
 *
 * The REST interceptors (`Authorization`, `X-Tenant-ID`) and `TenantContext` read the incoming request through
 * ThreadLocals: each task re-binds the caller's `RequestAttributes`, or ndopify-server answers 401 and the tenant is
 * lost.
 */
class Parallel(
    private val executor: ExecutorService = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("npc-parallel-", 0).factory(),
    ),
) : AutoCloseable {
    fun <T> async(block: () -> T): CompletableFuture<T> {
        val attributes = RequestContextHolder.getRequestAttributes()
        return CompletableFuture.supplyAsync(
            {
                RequestContextHolder.setRequestAttributes(attributes, true)
                try {
                    block()
                } finally {
                    RequestContextHolder.resetRequestAttributes()
                }
            },
            executor,
        )
    }

    override fun close() = executor.close()
}
