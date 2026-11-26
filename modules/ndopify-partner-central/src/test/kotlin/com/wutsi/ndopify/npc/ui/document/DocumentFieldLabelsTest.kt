package com.wutsi.ndopify.npc.ui.document

import org.junit.jupiter.api.Test
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.context.support.StaticMessageSource
import java.util.Locale
import kotlin.test.assertEquals

class DocumentFieldLabelsTest {
    private val labels = DocumentFieldLabels(
        ResourceBundleMessageSource().apply {
            setBasename("messages")
            setDefaultEncoding("UTF-8")
        },
    )

    @Test
    fun `known field keys are labelled`() {
        assertEquals("Numéro du titre", labels.label("titleNumber"))
        assertEquals("Date de naissance", labels.label("dateOfBirth"))
        assertEquals("Propriétaires", labels.label("owners"))
        assertEquals("Compte", labels.label("accountLast4"))
    }

    @Test
    fun `a known financial key is labelled`() {
        assertEquals("Salaire net", labels.label("netSalary"))
        assertEquals("Nature du montant", labels.label("amountKind"))
    }

    @Test
    fun `an unknown key falls back`() {
        assertEquals("Autre donnée", labels.label("position"))
    }

    @Test
    fun `each problem code has its instruction`() {
        assertEquals(
            "Le document ne correspond pas au type choisi. Supprimez-le, puis ajoutez-le avec le bon type.",
            labels.instruction("DOCUMENT_TYPE_MISMATCH"),
        )
        assertEquals(
            "L'IA n'a pas pu lire le document. Remplacez ses pages par des photos plus nettes, droites et sans " +
                "reflet, ou supprimez-le et ajoutez-le de nouveau.",
            labels.instruction("DOCUMENT_UNREADABLE"),
        )
        assertEquals(
            "Il manque une page du document, son verso par exemple. Remplacez la page manquante par une photo, ou " +
                "supprimez le document et ajoutez-le avec toutes ses pages.",
            labels.instruction("DOCUMENT_PAGE_MISSING"),
        )
        assertEquals(
            "L'IA n'est pas sûre d'un champ obligatoire. Vérifiez-le sur la pièce : si la photo est floue, " +
                "remplacez la page ; sinon, supprimez le document et ajoutez-le de nouveau.",
            labels.instruction("DOCUMENT_FIELD_LOW_CONFIDENCE"),
        )
        assertEquals(
            "La date d'expiration de la pièce n'a pas pu être lue. Remplacez la page où elle figure par une photo " +
                "plus nette, ou supprimez le document et ajoutez-le de nouveau.",
            labels.instruction("EXPIRY_DATE_UNREADABLE"),
        )
        assertEquals(
            "La lecture du document a échoué. Remplacez ses pages, ou supprimez le document et ajoutez-le de " +
                "nouveau pour relancer la lecture.",
            labels.instruction("EXTRACTION_FAILED"),
        )
    }

    @Test
    fun `an unknown or missing code falls back`() {
        assertEquals(
            "Un problème a été signalé sur ce document. Supprimez-le et ajoutez-le de nouveau pour relancer la lecture.",
            labels.instruction("DOCUMENT_BACK_MISSING"),
        )
        assertEquals(
            "Un problème a été signalé sur ce document. Supprimez-le et ajoutez-le de nouveau pour relancer la lecture.",
            labels.instruction(null),
        )
        assertEquals(
            "Un problème a été signalé sur ce document. Supprimez-le et ajoutez-le de nouveau pour relancer la lecture.",
            labels.instruction("  "),
        )
    }

    @Test
    fun `the generic texts come from the bundle`() {
        val bundle = StaticMessageSource().apply {
            addMessage("document.field.unknown", Locale.FRENCH, "Donnée inconnue")
            addMessage("document.problem.unknown", Locale.FRENCH, "Problème inconnu")
        }
        val labels = DocumentFieldLabels(bundle)

        assertEquals("Donnée inconnue", labels.label("position"))
        assertEquals("Problème inconnu", labels.instruction("DOCUMENT_BACK_MISSING"))
        assertEquals("Problème inconnu", labels.instruction(null))
    }
}
