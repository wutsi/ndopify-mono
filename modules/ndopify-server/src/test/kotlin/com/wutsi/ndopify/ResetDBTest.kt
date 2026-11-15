package com.wutsi.ndopify

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.Test

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("qa")
class ResetDBTest {
    @Test
    fun reset() {
        // Do nothing
    }
}
