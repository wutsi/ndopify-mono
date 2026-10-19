package com.wutsi.ndopify.esignature

import org.springframework.boot.autoconfigure.SpringBootApplication
import kotlin.test.Test
import kotlin.test.assertNotNull

class ApplicationTest {
    @Test
    fun isASpringBootApplication() {
        assertNotNull(Application::class.java.getAnnotation(SpringBootApplication::class.java))
        assertNotNull(Application())
    }
}
