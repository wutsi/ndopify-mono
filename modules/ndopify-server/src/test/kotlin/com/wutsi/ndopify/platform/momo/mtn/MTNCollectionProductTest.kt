package com.wutsi.ndopify.platform.momo.mtn

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.assertEquals

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MTNCollectionProductTest {
    @Autowired
    private lateinit var product: MtnCollectionProduct

    @Test
    fun userBasicInfo() {
        product.authenticate()
        val info = product.userBasicInfo("237221234100")

        println(info)
        assertEquals("Sand", info.givenName)
        assertEquals("Box", info.familyName)
    }
}
