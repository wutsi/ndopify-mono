package com.wutsi.ndopify.listing.server.service.publisher

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.event.ListingPublishedEvent
import com.wutsi.ndopify.platform.util.StringUtils
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.server.service.LocationService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.Date

/**
 * Publishes a listing by generating its content and updating its status to PUBLISHED.
 * If the content generation fails, the publishing will retry up to MAX_RETRIES times, then the listing is published
 * without content (no title, summary nor description: the UI handles it), keeping the last error.
 * Without a hero image in the generated content, the hero image is the verified image with the highest quality, then the largest.
 * The listing's public URL is the path `/<city>/<neighborhood>/listing/<id>/<slug>`, its slug from its title: without
 * neighborhood nor title, those segments are left out. The host isn't part of it: it's the tenant's public portal.
 * A published listing publishes a [ListingPublishedEvent], in the transaction that saves it.
 */
@Service
class ListingPublisherService(
    private val generator: ListingContentGenerator,
    private val dao: ListingRepository,
    private val imageDao: ListingImageRepository,
    private val locationService: LocationService,
    private val tx: TransactionTemplate,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingPublisherService::class.java)
        const val MAX_RETRIES = 3
    }

    fun publish(listing: ListingEntity): ListingEntity {
        if (listing.status != ListingStatus.PUBLISHING) {
            throw IllegalStateException("Listing#${listing.id}.status=${listing.status} - Cannot be published")
        }

        return try {
            if ((listing.retries ?: 0) >= MAX_RETRIES) {
                manual(listing)
            } else {
                val content = generator.generate(listing)
                if (content != null) {
                    published(listing, content)
                } else {
                    failed(listing, null)
                }
            }
        } catch (ex: Exception) {
            LOGGER.warn("Unable to generate content for listing#${listing.id}", ex)
            failed(listing, ex)
        }
    }

    private fun failed(listing: ListingEntity, ex: Exception?): ListingEntity {
        return tx.execute {
            dao.save(
                listing.copy(
                    errorCodes = ex?.let { listOf(ErrorCode.INTERNAL_ERROR) } ?: emptyList(),
                    errorMessage = ex?.message ?: "No content",
                    retries = (listing.retries ?: 0) + 1,
                    modifiedAt = Date(clock.millis())
                )
            )
        }
    }

    private fun manual(listing: ListingEntity): ListingEntity {
        val now = Date(clock.millis())
        val heroImageId = heroImageId(listing)
        val url = url(listing, null)
        return tx.execute {
            val published = dao.save(
                listing.copy(
                    status = ListingStatus.PUBLISHED,
                    title = null,
                    summary = null,
                    description = null,
                    url = url,
                    heroImageId = heroImageId,
                    publishedAt = now,
                    modifiedAt = now,
                )
            )
            eventPublisher.publishEvent(ListingPublishedEvent(listingId = published.id!!, tenantId = published.tenantId!!))
            published
        }
    }

    private fun published(listing: ListingEntity, content: ListingContentInfo): ListingEntity {
        val now = Date(clock.millis())
        val heroImageId = content.heroImageId ?: heroImageId(listing)
        val title = content.title?.take(100)
        val url = url(listing, title)
        return tx.execute {
            val published = dao.save(
                listing.copy(
                    status = ListingStatus.PUBLISHED,
                    title = title,
                    summary = content.summary?.take(255),
                    description = content.description,
                    url = url,
                    heroImageId = heroImageId,
                    retries = 0,
                    errorMessage = null,
                    errorCodes = emptyList(),
                    publishedAt = now,
                    modifiedAt = now,
                )
            )
            eventPublisher.publishEvent(ListingPublishedEvent(listingId = published.id!!, tenantId = published.tenantId!!))
            published
        }
    }

    private fun heroImageId(listing: ListingEntity): Long? =
        imageDao.findByListingIdAndStatus(listing.id ?: -1, ImageStatus.VERIFIED)
            .maxWithOrNull(
                compareBy(
                    { image -> image.quality ?: 0 },
                    { image -> (image.width ?: 0).toLong() * (image.height ?: 0) },
                ),
            )
            ?.id

    private fun url(listing: ListingEntity, title: String?): String {
        val locations = locationService.search(
            SearchLocationRequest(
                ids = listOfNotNull(listing.cityId, listing.neighborhoodId),
                limit = 2,
            )
        )
        val city = locations.find { location -> location.id == listing.cityId }
        val neighborhood = locations.find { location -> location.id == listing.neighborhoodId }

        val prefix = StringUtils.toSlug(StringUtils.toSlug("", city?.name), neighborhood?.name) + "/listing/${listing.id}"
        return StringUtils.toSlug(prefix, title)
    }
}
