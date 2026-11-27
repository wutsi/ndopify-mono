/*
 * Ndopify Partner Central — the owner verification's French wording (#298), shared by the mandate page's
 * *Vérification du propriétaire* section (mandate-ownership.js) and the listing page's publication block (#375,
 * partner-central.js), static prototype.
 *
 * Production words the verdict once, in the server's views (MandateOwnershipViews, reused by ListingViews), so that
 * the two pages cannot drift; this file plays that single source here. Loaded before mandate-ownership.js
 * (mandate.html) and before partner-central.js (listing.html).
 *
 * The shipped verdict (#297, ndopify-dto 0.0.113) is a status and its reasons only:
 * { status, reasons: [{ code, details }] } — no evidence date, no document type, title, page, path nor extracted
 * value. A *_MISSING reason is a piece to add, a decisive reason is a check that failed and the one action it calls
 * for; a *_UNUSABLE reason carries the document's problemCodes as details, worded as the document page words them
 * (#331). A reason never names a document id, page, path, URL, extracted value, type nor agent title.
 */
(function () {
    "use strict";

    /*
     * A *_MISSING reason: the piece the verdict found missing, and what counts for it. No error styling: the check
     * is in progress, and this is what the agent adds.
     */
    var MISSING_PIECES = {
        OWNERSHIP_ID_MISSING: "Il manque la pièce d'identité du propriétaire : une carte nationale d'identité " +
            "(recto et verso) ou un passeport, à son nom.",
        OWNERSHIP_DOCUMENT_MISSING: "Il manque le document de propriété : un titre foncier ou un certificat de " +
            "propriété, à son nom."
    };
    var MISSING_OTHER = "Une pièce manque pour vérifier le propriétaire : vérifiez les documents du mandat.";

    /*
     * A decisive reason: its French wording and the one action it calls for — retake the photo, change the type, or
     * upload a title. The reason carries no document type or title, so the block names the check's kind above this
     * wording (checkKind below); the document's problem codes (*_UNUSABLE only) come under it. The action stays the
     * same whatever the details are, since both the photo and the type are corrected from the same document row.
     */
    var REASON_WORDING = {
        OWNERSHIP_ID_EXPIRED: {
            text: "La pièce est expirée : elle ne peut plus servir à vérifier le propriétaire.",
            action: "Reprenez la photo d'une pièce en cours de validité, puis ajoutez-la dans Documents."
        },
        OWNERSHIP_ID_UNUSABLE: {
            text: "La pièce n'a pas pu être vérifiée.",
            action: "Corrigez la pièce dans Documents : reprenez sa photo, ou changez son type."
        },
        OWNERSHIP_ID_NAME_MISMATCH: {
            text: "La pièce ne porte pas le nom du propriétaire : elle ne peut pas l'identifier.",
            action: "Ajoutez une pièce d'identité au nom du propriétaire."
        },
        OWNERSHIP_DOCUMENT_UNUSABLE: {
            text: "Le document n'a pas pu être vérifié.",
            action: "Corrigez le document dans Documents : reprenez sa photo, ou changez son type."
        },
        OWNERSHIP_DOCUMENT_NAME_MISMATCH: {
            text: "Le document ne désigne pas le propriétaire du bien.",
            action: "Ajoutez un titre foncier ou un certificat de propriété au nom du propriétaire."
        }
    };
    // #298's decision 7: one label per code, and a generic line for a code the page doesn't know yet.
    var REASON_OTHER = {
        text: "La vérification du propriétaire a échoué sur ce document.",
        action: "Vérifiez la pièce dans Documents."
    };

    // The check a reason belongs to: the verdict names no type or title, only the kind of piece it looked at.
    function checkKind(code) {
        code = String(code || "");
        if (code.indexOf("OWNERSHIP_ID_") === 0) return "La pièce d'identité";
        if (code.indexOf("OWNERSHIP_DOCUMENT_") === 0) return "Le document de propriété";
        return "";
    }

    /*
     * #331's problem instructions, verbatim: production words them once in messages.properties
     * (document.problem.<code>), which the document page and the owner verification both read. DOCUMENT_EXPIRED is a
     * warning, never an instruction, and doesn't reach a verdict reason's details.
     */
    var PROBLEM_INSTRUCTIONS = {
        DOCUMENT_TYPE_MISMATCH: "Le document ne correspond pas au type choisi. Supprimez-le, puis ajoutez-le avec " +
            "le bon type.",
        DOCUMENT_UNREADABLE: "Le document n'a pas pu être lu. Remplacez ses pages par des photos plus nettes, " +
            "droites et sans reflet, ou supprimez-le et ajoutez-le de nouveau.",
        DOCUMENT_PAGE_MISSING: "Il manque une page du document, son verso par exemple. Remplacez la page manquante " +
            "par une photo, ou supprimez le document et ajoutez-le avec toutes ses pages.",
        DOCUMENT_FIELD_LOW_CONFIDENCE: "Un champ obligatoire n'a pas pu être lu avec certitude. Vérifiez-le " +
            "sur la pièce : si la photo est floue, remplacez la page ; sinon, supprimez le document et " +
            "ajoutez-le de nouveau.",
        EXPIRY_DATE_UNREADABLE: "La date d'expiration de la pièce n'a pas pu être lue. Remplacez la page où elle " +
            "figure par une photo plus nette, ou supprimez le document et ajoutez-le de nouveau.",
        EXTRACTION_FAILED: "La lecture du document a échoué. Remplacez ses pages, ou supprimez le document et " +
            "ajoutez-le de nouveau pour relancer la lecture."
    };
    var PROBLEM_OTHER = "Un problème a été signalé sur ce document. Supprimez-le et ajoutez-le de nouveau pour " +
        "relancer la lecture.";

    window.NdOwnership = {
        MISSING_PIECES: MISSING_PIECES,
        MISSING_OTHER: MISSING_OTHER,
        REASON_WORDING: REASON_WORDING,
        REASON_OTHER: REASON_OTHER,
        checkKind: checkKind,
        PROBLEM_INSTRUCTIONS: PROBLEM_INSTRUCTIONS,
        PROBLEM_OTHER: PROBLEM_OTHER
    };
})();
