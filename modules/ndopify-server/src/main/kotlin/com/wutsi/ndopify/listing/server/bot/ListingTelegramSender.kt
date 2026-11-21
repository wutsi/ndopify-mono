package com.wutsi.ndopify.listing.server.bot

import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.platform.image.Crop
import com.wutsi.ndopify.platform.image.Format
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.Transformation
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.platform.util.MimeUtils
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.awt.Dimension
import java.io.ByteArrayOutputStream

/**
 * Sends one Telegram message about a listing: the listing's cover as a photo with [text] as its caption, or [text] alone.
 *
 * The photo is the listing's cover (`ListingImageService.cover`) resized to 1280×1280 at quality 80
 * ([PHOTO_TRANSFORMATION]) through the [ImageService], and given to Telegram as an ImageKit URL it fetches itself. When
 * the URL can't be resized (local, or a URL outside the ImageKit origin) the cover is read from the storage and
 * uploaded instead. The photo is used only when the text fits Telegram's caption limit ([CAPTION_MAX_LENGTH] code
 * points); a longer text is sent as a text message, never truncated, without reading the image. A failed resize, read
 * or photo only drops the photo: the text is sent anyway, and the original is never sent when a resized URL exists, so
 * the notification is never lost. Telegram refusing the text throws
 * ([com.wutsi.ndopify.platform.telegram.TelegramException]), which the notifiers turn into their e-mail fallback.
 *
 * [listingImageService] is lazy: `ListingImageService` depends on `ListingService`, which owns the publication notifier
 * and this chain. Neither the bot token nor the image URL is ever logged.
 */
@Service
class ListingTelegramSender(
    @Lazy private val listingImageService: ListingImageService,
    private val storageProvider: StorageServiceProvider,
    private val client: TelegramClient,
    private val imageService: ImageService,
) {
    companion object {
        const val CAPTION_MAX_LENGTH = 1024

        /** The most Telegram displays, capped on both sides so portrait photos fit too; JPEG at a quality invisible on a phone. */
        val PHOTO_TRANSFORMATION = Transformation(
            dimension = Dimension(1280, 1280),
            format = Format.JPG,
            crop = Crop.AT_MAX,
            quality = 80,
        )

        private val LOGGER = LoggerFactory.getLogger(ListingTelegramSender::class.java)
    }

    fun send(chatId: Long, listing: ListingEntity, text: String, buttons: List<TelegramButton> = emptyList()) {
        if (text.codePointCount(0, text.length) <= CAPTION_MAX_LENGTH) {
            val cover = listingImageService.cover(listing)
            if (cover != null && sendPhoto(chatId, listing, cover, text, buttons)) {
                return
            }
        }
        client.sendMessage(chatId, text, buttons)
    }

    // Resizes the cover and gives Telegram its URL; any failure only drops the photo, never the message
    private fun sendPhoto(
        chatId: Long,
        listing: ListingEntity,
        cover: ListingImageEntity,
        text: String,
        buttons: List<TelegramButton>,
    ): Boolean {
        return try {
            val url = imageService.transform(cover.url, PHOTO_TRANSFORMATION)
            if (url == cover.url) {
                uploadOriginal(chatId, listing, cover, text, buttons)
            } else {
                client.sendPhoto(chatId, url, text, buttons)
            }
            true
        } catch (ex: Exception) {
            LOGGER.warn("Telegram photo failed for Listing#${listing.id} (image ${cover.id}), sending the text only", ex)
            false
        }
    }

    // Local, or a URL the image service can't resize: upload the stored file, as before
    private fun uploadOriginal(
        chatId: Long,
        listing: ListingEntity,
        cover: ListingImageEntity,
        text: String,
        buttons: List<TelegramButton>,
    ) {
        val out = ByteArrayOutputStream()
        storageProvider.get().get(cover.path, out)
        client.sendPhoto(
            chatId,
            out.toByteArray(),
            "${listing.id}.${MimeUtils.getExtensionFromMimeType(cover.mimeType)}",
            cover.mimeType ?: "image/jpeg",
            text,
            buttons,
        )
    }
}
