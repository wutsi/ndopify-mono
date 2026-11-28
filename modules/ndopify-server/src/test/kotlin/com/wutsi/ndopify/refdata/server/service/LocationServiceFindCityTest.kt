package com.wutsi.ndopify.refdata.server.service

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/refdata/LocationServiceFindCity.sql"])
class LocationServiceFindCityTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var service: LocationService

    @Test
    fun `an exact city name matches case and accents`() {
        assertEquals(2370101L, service.findCity("YAOUNDÉ", "CM")?.id)
    }

    @Test
    fun `punctuation and spacing are ignored`() {
        assertEquals(2370301L, service.findCity("Sainte Anne", "CM")?.id)
    }

    @Test
    fun `a substring does not match`() {
        assertNull(service.findCity("Yaound", "CM"))
    }

    @Test
    fun `a neighbourhood does not match`() {
        assertNull(service.findCity("Bastos", "CM"))
    }

    @Test
    fun `a city of another country does not match`() {
        assertNull(service.findCity("Montreal", "CM"))
    }

    @Test
    fun `a blank name or country does not match`() {
        assertNull(service.findCity(" ", "CM"))
        assertNull(service.findCity("Yaounde", null))
    }
}
