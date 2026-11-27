package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.customer.server.dao.CustomerRepository
import com.wutsi.ndopify.email.server.dao.EmailAddressRepository
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.listing.dto.ContactPeriodType
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.party.server.dao.PartyRepository
import com.wutsi.ndopify.platform.email.EmailVerification
import com.wutsi.ndopify.platform.email.EmailVerifier
import com.wutsi.ndopify.platform.email.VerificationReason
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.time.LocalDate
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestPropertySource(
    properties = [
        "ndopify.email.verification.provider=ses",
        "ndopify.email.verification.throttle.per-ip-per-hour=1000",
        "ndopify.email.verification.throttle.daily-cap=1000",
    ],
)
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/CreateMandateEndpoint.sql"])
class CreateMandateEmailEndpointTest : TenantAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var verifier: EmailVerifier

    @Autowired
    private lateinit var mandateDao: MandateRepository

    @Autowired
    private lateinit var customerDao: CustomerRepository

    @Autowired
    private lateinit var partyDao: PartyRepository

    @Autowired
    private lateinit var emailDao: EmailAddressRepository

    @Autowired
    private lateinit var ds: DataSource

    private val request = CreateMandateRequest(
        propertyCategory = PropertyCategory.RESIDENTIAL,
        mandateType = MandateType.LISTING,
        listingType = ListingType.RENTAL,
        cityId = 2370201L,
        neighbourhoodIds = listOf(111L, 222L),
        propertyType = PropertyType.APARTMENT,
        furnitureType = FurnitureType.FULLY_FURNISHED,
        rooms = "2",
        parkings = "1",
        area = 75,
        waterMeter = true,
        dedicatedWaterMeter = true,
        waterTank = true,
        waterWell = true,
        electricityMeter = true,
        dedicatedElectricityMeter = false,
        generatorSet = true,
        solarPanel = true,
        roadPaved = true,
        trackAccessibleByCar = false,
        trackInaccessibleByCar = false,
        securedFenced = true,
        securityGuard = true,
        videoSurveillance = true,
        price = Money(amount = 150000, "XAF", PeriodicityType.MONTHLY),
        securityDeposit = 2,
        advanceRent = 3,
        leaseTerm = 12,
        movingPeriod = MovingPeriod.WITHIN_1_MONTH,
        availableFrom = LocalDate.of(2026, 11, 3),
        noticePeriod = 1,
        firstName = "Ray",
        lastName = "Sponsible",
        email = "Ray.Sponsible@gmail.com",
        whatsappNumber = "+237671234567",
        preferredContactPeriod = ContactPeriodType.MORNING,
        comments = "Close to the market",
    )

    @Test
    fun `a LOW verdict is refused, writes nothing and is cached`() {
        doReturn(EmailVerification(0.0, VerificationReason.SYNTAX_INVALID)).whenever(verifier).verify(any())
        val mandates = mandateDao.count()
        val customers = customerDao.count()
        val parties = partyDao.count()

        val response = rest.postForEntity("/v1/mandates", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.EMAIL_ADDRESS_INVALID, response.body!!.error.code)
        assertEquals("email", response.body!!.error.parameter?.name)
        assertEquals(ParameterType.PARAMETER_TYPE_PAYLOAD, response.body!!.error.parameter?.type)
        assertNull(response.body!!.error.message)
        assertNull(response.body!!.error.data)
        assertEquals(mandates, mandateDao.count())
        assertEquals(customers, customerDao.count())
        assertEquals(parties, partyDao.count())
        assertEquals(1L, emailDao.count())
        verify(verifier, times(1)).verify(any())

        rest.postForEntity("/v1/mandates", request, ErrorResponse::class.java)
        verify(verifier, times(1)).verify(any()) // The second refusal comes from the cache
    }

    @Test
    fun `a valid verdict creates the mandate and caches it`() {
        doReturn(EmailVerification(1.0, VerificationReason.NONE)).whenever(verifier).verify(any())
        val mandates = mandateDao.count()

        val response = rest.postForEntity("/v1/mandates", request, CreateMandateResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mandates + 1, mandateDao.count())
        assertEquals(1L, emailDao.count())
        assertEquals(1.0, emailDao.findByEmailHash(hashOf("ray.sponsible@gmail.com"))!!.score)
    }

    @Test
    fun `the same address twice inside its TTL is verified once`() {
        doReturn(EmailVerification(1.0, VerificationReason.NONE)).whenever(verifier).verify(any())

        assertEquals(HttpStatus.OK, rest.postForEntity("/v1/mandates", request, CreateMandateResponse::class.java).statusCode)
        assertEquals(HttpStatus.OK, rest.postForEntity("/v1/mandates", request, CreateMandateResponse::class.java).statusCode)

        verify(verifier, times(1)).verify(any())
        assertEquals(1L, emailDao.count())
    }

    @Test
    fun `an expired row is verified again and updated`() {
        doReturn(EmailVerification(1.0, VerificationReason.NONE)).whenever(verifier).verify(any())
        rest.postForEntity("/v1/mandates", request, CreateMandateResponse::class.java)
        ds.connection.use { cnn ->
            cnn.createStatement().use { it.executeUpdate("UPDATE T_EMAIL_ADDRESS SET expires_at = '2020-01-01 00:00:00'") }
        }

        rest.postForEntity("/v1/mandates", request, CreateMandateResponse::class.java)

        verify(verifier, times(2)).verify(any())
        assertEquals(1L, emailDao.count())
    }

    @Test
    fun `a failing verifier fails open, writes the mandate and caches nothing`() {
        doThrow(RuntimeException("boom ray.sponsible@gmail.com")).whenever(verifier).verify(any())
        val mandates = mandateDao.count()

        val response = rest.postForEntity("/v1/mandates", request, CreateMandateResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(mandates + 1, mandateDao.count())
        assertEquals(0L, emailDao.count())
    }

    @Test
    fun `a malformed address is refused without a verifier call nor a row`() {
        val mandates = mandateDao.count()

        val response = rest.postForEntity("/v1/mandates", request.copy(email = "ray@"), ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.EMAIL_ADDRESS_INVALID, response.body!!.error.code)
        verify(verifier, never()).verify(any())
        assertEquals(mandates, mandateDao.count())
        assertEquals(0L, emailDao.count())
    }

    @Test
    fun `the e-mail is checked before an unknown origin listing`() {
        doReturn(EmailVerification(0.0, VerificationReason.MAILBOX_UNREACHABLE)).whenever(verifier).verify(any())
        val mandates = mandateDao.count()

        val response = rest.postForEntity(
            "/v1/mandates",
            request.copy(originListingId = 999_999),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.EMAIL_ADDRESS_INVALID, response.body!!.error.code)
        assertEquals(mandates, mandateDao.count()) // Not LISTING_NOT_FOUND: the e-mail is checked first
        assertEquals(1L, emailDao.count()) // The refused verdict is cached; nothing else is written
    }

    private fun hashOf(email: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(email.toByteArray(Charsets.UTF_8))
        return java.math.BigInteger(1, digest).toString(16).padStart(64, '0')
    }
}
