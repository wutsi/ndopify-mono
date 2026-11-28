/*
 * Ndopify Partner Central — the owner verification's French wording (#298, #402), for the mandate page's
 * *Vérification du propriétaire* section (mandate-ownership.js), static prototype.
 *
 * Production words the verdict once, in the server's views (MandateOwnershipViews); this file plays that single
 * source here. Loaded before mandate-ownership.js (mandate.html).
 * The shipped verdict (#297, ndopify-dto 0.0.113) is a status and its reasons only:
 * { status, reasons: [{ code, details }] } — no evidence date, no document type, title, page, path nor extracted
 * value. A *_MISSING reason is a piece to add; #402 cuts a decisive reason to ONE short sentence naming the cause,
 * with the piece it looked at in bold and no action of its own (the section's intro links to the Documents section,
 * the document page carries the how). A reason never names a document id, page, path, URL, extracted value, type nor
 * agent title.
 * #376 adds the city comparison (OwnershipLocation: mandateCity, documentLocality, status) and its reason,
 * OWNERSHIP_PROPERTY_MISMATCH: the two places are the verdict's only property data, and #402 keeps them inline in
 * that reason's sentence.
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

    // A place the comparison carries as blank reads "non précisée" — never an empty cell.
    var UNKNOWN_PLACE = "non précisée";

    /*
     * #402: a decisive reason, cut to the cause alone — the piece it looked at (bold on the page) and the rest of the
     * sentence. No action, no instruction list: the section's intro links to the Documents section and the document
     * page says what to correct. The verdict carries no document type or title, so the piece is named by its check,
     * never by a title. #376's mismatch keeps its two places in the sentence (the verdict's only property data); a
     * blank place reads "non précisée".
     */
    var REASONS = {
        OWNERSHIP_ID_UNUSABLE: {
            piece: "La pièce d'identité",
            text: "n'a pas pu être vérifiée."
        },
        OWNERSHIP_ID_EXPIRED: {
            piece: "La pièce d'identité",
            text: "est expirée."
        },
        OWNERSHIP_ID_NAME_MISMATCH: {
            piece: "La pièce d'identité",
            text: "n'est pas au nom du propriétaire."
        },
        OWNERSHIP_DOCUMENT_UNUSABLE: {
            piece: "Le document de propriété",
            text: "n'a pas pu être vérifié."
        },
        OWNERSHIP_DOCUMENT_NAME_MISMATCH: {
            piece: "Le document de propriété",
            text: "ne désigne pas le propriétaire."
        },
        OWNERSHIP_PROPERTY_MISMATCH: {
            piece: "Le titre",
            text: function (places) {
                return "désigne un bien à " + (places.locality || UNKNOWN_PLACE) + ", le mandat à " +
                    (places.city || UNKNOWN_PLACE) + ".";
            }
        }
    };
    // A code the page doesn't know yet (#298's decision 7): one generic sentence, never an error.
    var REASON_OTHER = {
        piece: "Un document",
        text: "n'a pas pu être vérifié."
    };

    /*
     * The piece and the rest of the sentence of one reason. The mismatch's sentence is built from the verdict's two
     * places, so the caller passes them (the section's location data).
     */
    function reasonOf(code, location) {
        var reason = REASONS[String(code || "")] || REASON_OTHER;
        var places = location || {};
        return {
            piece: reason.piece,
            text: typeof reason.text === "function"
                ? reason.text({ locality: places.documentLocality, city: places.mandateCity })
                : reason.text
        };
    }

    window.NdOwnership = {
        MISSING_PIECES: MISSING_PIECES,
        MISSING_OTHER: MISSING_OTHER,
        REASON_OTHER: REASON_OTHER,
        reasonOf: reasonOf
    };
})();
