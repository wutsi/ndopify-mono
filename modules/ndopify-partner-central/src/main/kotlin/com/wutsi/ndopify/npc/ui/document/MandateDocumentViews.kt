package com.wutsi.ndopify.npc.ui.document

import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateDocument
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.ui.findproperty.FindPropertyRules
import com.wutsi.ndopify.npc.ui.mandate.MandateRules
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.time.LocalDate

/**
 * Builds the *Documents* section of the mandate page and a document's page from ndopify-server's DTOs. The documents
 * are listed newest first (the endpoint's order, by `createdAt` to be sure); a page carries no URL nor path, only the
 * Partner Central preview route built from the ids. The text of the empty state says what to add for a landlord's
 * listing or a client's search.
 */
object MandateDocumentViews {
    private const val RENTAL_HINT =
        "Ajoutez la pièce d'identité du bailleur et un document de propriété du bien : titre foncier ou certificat " +
            "de propriété (et sa procuration s'il loue pour un autre)."
    private const val SALE_HINT =
        "Ajoutez la pièce d'identité du vendeur et ses documents de propriété : titre foncier, certificat de " +
            "propriété ou plan de bornage (et sa procuration s'il vend pour un autre)."
    private const val RENTAL_SEARCH_HINT =
        "Ajoutez la pièce d'identité du client et un justificatif de revenus, comme un bulletin de paie."
    private const val SALE_SEARCH_HINT =
        "Ajoutez la pièce d'identité de l'acheteur et un justificatif de son financement : bulletin de paie, accord " +
            "de principe bancaire ou justificatif de fonds."

    /** [documents] null when they can't be read: the section says so, and the rest of the page is unchanged. */
    fun section(
        mandate: Mandate,
        documents: List<MandateDocument>?,
        formatter: TenantFormatter,
        today: LocalDate,
    ): DocumentSectionView {
        val sale = FindPropertyRules.isSale(mandate.listingType)
        val listing = mandate.mandateType == MandateType.LISTING
        val words = MandateRules.words(mandate.mandateType, mandate.listingType)
        val hint = when {
            listing && sale -> SALE_HINT
            listing -> RENTAL_HINT
            sale -> SALE_SEARCH_HINT
            else -> RENTAL_SEARCH_HINT
        }
        val intro = "Les pièces d'identité et " + (if (listing) "de propriété " else "de financement ") +
            "${words.of}. Privées : jamais publiées ni montrées à un autre agent."
        val count = documents?.size ?: 0
        val writable = MandateDocumentRules.writable(mandate)
        return DocumentSectionView(
            rows = documents.orEmpty()
                .sortedByDescending { it.createdAt.time }
                .map { document ->
                    val typeLabel = MandateDocumentLabels.type(document.type)
                    DocumentRowView(
                        id = document.id,
                        typeLabel = typeLabel,
                        title = document.title.trim().takeIf { it.isNotEmpty() && it != typeLabel },
                        status = MandateDocumentLabels.status(document.status),
                        addedOn = formatter.date(document.createdAt),
                        pageCount = document.pages.size,
                        expired = document.expiresAt?.isBefore(today) == true,
                        href = "/mandates/${mandate.id}/documents/${document.id}",
                    )
                },
            unavailable = documents == null,
            writable = writable,
            emptyHint = hint,
            intro = intro,
            count = count,
            atLimit = documents != null && writable && MandateDocumentRules.atLimit(count),
        )
    }

    fun document(mandate: Mandate, document: MandateDocument, formatter: TenantFormatter): DocumentView {
        val typeLabel = MandateDocumentLabels.type(document.type)
        val title = document.title.trim().ifEmpty { typeLabel }
        val source = document.source
        return DocumentView(
            id = document.id,
            mandateId = mandate.id,
            typeLabel = typeLabel.takeIf { it != title },
            title = title,
            status = MandateDocumentLabels.status(document.status),
            addedOn = formatter.date(document.createdAt),
            version = document.version,
            pages = document.pages
                .sortedBy { it.index }
                .mapIndexed { index, page ->
                    DocumentPageView(
                        id = page.id,
                        number = index + 1,
                        href = "/mandates/${mandate.id}/documents/${document.id}/pages/${page.id}/preview",
                    )
                },
            writable = MandateDocumentRules.writable(mandate),
            source = source,
            sourceLabel = MandateDocumentLabels.source(source),
            replaceAccept = MandateDocumentRules.accept(source),
            replaceHint = MandateDocumentRules.replaceHint(source),
            replaceRequired = MandateDocumentRules.replaceRequired(source),
        )
    }
}
