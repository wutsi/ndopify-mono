package com.wutsi.ndopify

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.task.TaskDecorator
import java.util.concurrent.atomic.AtomicInteger

/**
 * Counts the tasks submitted to the application executor. With virtual threads the executor is a
 * `SimpleAsyncTaskExecutor`, which exposes neither a queue nor an active-thread count, so this is how
 * [BaseEndpointIntegrationTest] waits for the @Async e-mails before stopping its SMTP server.
 */
class AsyncTaskTracker {
    private val pending = AtomicInteger()

    fun submitted() {
        pending.incrementAndGet()
    }

    fun completed() {
        pending.decrementAndGet()
    }

    fun count(): Int = pending.get()
}

@TestConfiguration(proxyBeanMethods = false)
class AsyncTaskTrackerConfiguration {
    @Bean
    fun asyncTaskTracker() = AsyncTaskTracker()

    @Bean
    fun asyncTaskDecorator(tracker: AsyncTaskTracker) = TaskDecorator { task ->
        tracker.submitted()
        Runnable {
            try {
                task.run()
            } finally {
                tracker.completed()
            }
        }
    }
}
