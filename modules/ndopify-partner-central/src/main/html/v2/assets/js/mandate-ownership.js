/*
 * Ndopify Partner Central — the owner verification of a LISTING mandate (#298), static prototype.
 *   - mandate.html (data-page="mandate"): the *Vérification du propriétaire* section, before the Documents section.
 * Production: GET /v1/mandates/{id}/ownership, read on every page load and never cached, on an ASSIGNED,
 * PENDING_APPROVAL or APPROVED LISTING mandate only. The BFF renders the verdict and its reasons server-side, so
 * every state works without JavaScript; this script only renders the prototype's sample verdicts, and the static
 * markup is 301's server render (FAILED, its identity card expired).
 *
 * The shipped verdict (#297, ndopify-dto 0.0.112) is a status and its reasons only:
 * { status, reasons: [{ code, details }] } — no evidence date, no document type, no agent title. PENDING carries one
 * reason per missing piece (none while a piece is still UPLOADED or EXTRACTING); VERIFIED has no reason and no date;
 * FAILED carries one reason per failed check, named by its kind (*la pièce d'identité* / *le document de propriété*)
 * because a reason doesn't say which type or title it was, and a *_UNUSABLE reason carries the document's problem
 * codes as details, worded here as the document page words them (#331; production reads the same
 * document.problem.<code> bundle). UNAVAILABLE is the section's own state when the read fails or answers UNKNOWN.
 * The Documents rows just below the section are what name each piece's type, title and status.
 * The section is absent on a SEARCH mandate and on a CLOSED / CANCELLED one.
 *
 * Query switch (prototype only; production renders all of it server-side):
 *   ?ownership=pending[&reason=both|id-missing|document-missing|in-flight]   (both is the default)
 *   ?ownership=verified                      (301 with &listing=published shows the listing link)
 *   ?ownership=failed[&reason=id-expired|id-unusable|id-name|document-unusable|document-name]
 *                                            (id-expired is the default; 301's own verdict)
 *   ?ownership=unavailable                   (the verdict read failed: only the section says so)
 *   ?ownership=none                          (the section as a SEARCH or CLOSED / CANCELLED page renders it: absent)
 * The samples for 301 and 302 are the mandates' own: 301 has an expired identity card, 302 has no document.
 */
(function () {
    "use strict";

    /*
     * A *_MISSING reason: the piece the verdict found missing, and what counts for it. No error styling: the check
     * is in progress, and this is what the agent adds (AC 2).
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
     * (document.problem.<code>), which both the document page and this section read. DOCUMENT_EXPIRED is a warning,
     * never an instruction, and doesn't reach a verdict reason's details.
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

    /*
     * PROTOTYPE: the sample verdicts, as GET /v1/mandates/{id}/ownership would send them — a status and reasons, no
     * date, no type nor title; details are the failed document's problemCodes, in the server's order. The default is
     * FAILED for 301 (its expired identity card) and PENDING with both pieces missing for any other LISTING mandate
     * (302 has no document yet).
     */
    var PENDING_SAMPLES = {
        both: [
            { code: "OWNERSHIP_ID_MISSING" },
            { code: "OWNERSHIP_DOCUMENT_MISSING" }
        ],
        "id-missing": [{ code: "OWNERSHIP_ID_MISSING" }],
        "document-missing": [{ code: "OWNERSHIP_DOCUMENT_MISSING" }],
        // A piece still UPLOADED or EXTRACTING: no reason, the check is in progress (AC 2).
        "in-flight": []
    };

    var FAILED_SAMPLES = {
        "id-expired": [{ code: "OWNERSHIP_ID_EXPIRED" }],
        "id-unusable": [{ code: "OWNERSHIP_ID_UNUSABLE", details: ["DOCUMENT_UNREADABLE"] }],
        "id-name": [{ code: "OWNERSHIP_ID_NAME_MISMATCH" }],
        "document-unusable": [{
            code: "OWNERSHIP_DOCUMENT_UNUSABLE",
            details: ["DOCUMENT_TYPE_MISMATCH", "DOCUMENT_PAGE_MISSING"]
        }],
        "document-name": [{ code: "OWNERSHIP_DOCUMENT_NAME_MISMATCH" }]
    };

    var params = new URLSearchParams(window.location.search);

    function $(selector, root) {
        return (root || document).querySelector(selector);
    }

    function $all(selector, root) {
        return Array.prototype.slice.call((root || document).querySelectorAll(selector));
    }

    // The mandate's status, from the one step panel partner-central.js left visible.
    function statusOfPage() {
        var panel = $all("[data-panel]").filter(function (el) {
            return !el.hidden;
        })[0];
        return panel ? panel.getAttribute("data-panel") : "";
    }

    // The contact's role names the kind: a Vendeur / Bailleur mandate is a LISTING one.
    function partyOfPage() {
        var role = $("[data-contact-role]");
        var text = role ? role.textContent.trim() : "";
        if (text === "Vendeur") return "sale";
        if (text === "Bailleur") return "rental";
        return "";
    }

    // The mandate's listing, if the *Actif* panel rendered one: its number is then a link to it.
    function listingHref() {
        var number = $("[data-listing-number]");
        var href = number ? number.getAttribute("href") : "";
        return href && href.indexOf("listing.html") === 0 ? href : "";
    }

    // PROTOTYPE: the verdict the URL asks for; no switch means the mandate's own sample (301 / any other).
    function verdictOf(id) {
        var choice = params.get("ownership");
        if (choice === "none") return null;
        if (choice === "unavailable") return { status: "UNAVAILABLE", reasons: [] };
        if (choice === "verified") return { status: "VERIFIED", reasons: [] };
        if (choice === "failed") {
            return { status: "FAILED", reasons: FAILED_SAMPLES[params.get("reason")] || FAILED_SAMPLES["id-expired"] };
        }
        if (choice === "pending") {
            return { status: "PENDING", reasons: PENDING_SAMPLES[params.get("reason")] || PENDING_SAMPLES.both };
        }
        return id === "301"
            ? { status: "FAILED", reasons: FAILED_SAMPLES["id-expired"] }
            : { status: "PENDING", reasons: PENDING_SAMPLES.both };
    }

    function showBlock(section, name) {
        ["unavailable", "pending", "failed", "verified"].forEach(function (state) {
            $('[data-ownership-' + state + ']', section).hidden = state !== name;
        });
    }

    function setTag(section, status) {
        var tag = $("[data-ownership-tag]", section);
        var entry = {
            PENDING: ["info", "En cours"],
            VERIFIED: ["success", "Vérifié"],
            FAILED: ["warning", "Non vérifié"]
        }[status];
        tag.hidden = !entry;
        if (!entry) return;
        tag.className = "status-tag status-tag--" + entry[0];
        tag.textContent = entry[1];
    }

    function renderPending(section, verdict) {
        var list = $("[data-ownership-pending-reasons]", section);
        list.innerHTML = "";
        verdict.reasons.forEach(function (reason) {
            var item = document.createElement("li");
            item.textContent = MISSING_PIECES[reason.code] || MISSING_OTHER;
            list.appendChild(item);
        });
        // Without a reason, the piece is being read: the check runs on its own, and there is nothing to add.
        var missing = verdict.reasons.length > 0;
        list.hidden = !missing;
        $("[data-ownership-pending-text]", section).hidden = missing;
        var link = $("[data-ownership-pending-link]", section);
        link.textContent = missing ? "Ajouter ces pièces dans Documents" : "Voir les documents";
        link.href = "#documents-title";
    }

    function renderFailed(section, verdict) {
        var list = $("[data-ownership-reasons]", section);
        var template = $("[data-ownership-reason-template]", section);
        list.innerHTML = "";
        verdict.reasons.forEach(function (reason) {
            var item = template.content.firstElementChild.cloneNode(true);
            var wording = REASON_WORDING[reason.code] || REASON_OTHER;
            // The check's kind, never a document type, title, id, page, path, URL nor extracted value.
            var check = $("[data-reason-check]", item);
            check.textContent = checkKind(reason.code);
            check.hidden = !check.textContent;
            $("[data-reason-text]", item).textContent = wording.text;
            var problems = $("[data-reason-problems]", item);
            problems.innerHTML = "";
            (reason.details || []).forEach(function (code) {
                var problem = document.createElement("li");
                problem.textContent = PROBLEM_INSTRUCTIONS[code] || PROBLEM_OTHER;
                problems.appendChild(problem);
            });
            problems.hidden = problems.children.length === 0;
            $("[data-reason-action]", item).textContent = wording.action;
            list.appendChild(item);
        });
    }

    // VERIFIED has no date (the verdict carries none) and no reason: the hint, then the listing when there is one.
    function renderVerified(section) {
        var actions = $("[data-ownership-listing-actions]", section);
        var link = $("[data-ownership-listing]", section);
        var href = listingHref();
        actions.hidden = !href;
        if (href) link.href = href;
    }

    function render() {
        var section = $("[data-ownership]");
        if (!section) return;
        var id = params.get("id") || "301";
        var party = partyOfPage();
        var status = statusOfPage();
        var shown = !!party && (status === "ASSIGNED" || status === "PENDING_APPROVAL" || status === "APPROVED");
        var verdict = shown ? verdictOf(id) : null;

        // A SEARCH mandate, a CLOSED / CANCELLED one and ?ownership=none render no section at all.
        section.hidden = !verdict;
        if (!verdict) return;

        $("[data-ownership-party]", section).textContent = party === "sale" ? "le vendeur" : "le bailleur";
        setTag(section, verdict.status);
        showBlock(section, verdict.status.toLowerCase());
        if (verdict.status === "PENDING") renderPending(section, verdict);
        if (verdict.status === "FAILED") renderFailed(section, verdict);
        if (verdict.status === "VERIFIED") renderVerified(section);
    }

    /*
     * The page's own state changes in place: cancelling or closing the mandate below hides the section, and the
     * *Actif* panel's listing (once the mandate is approved) gives the VERIFIED state its link.
     */
    function watch() {
        var observer = new MutationObserver(render);
        $all('[data-panel="CLOSED"], [data-panel="CANCELLED"], [data-listing-actions]').forEach(function (el) {
            observer.observe(el, { attributes: true, attributeFilter: ["hidden"] });
        });
    }

    /*
     * partner-central.js's own DOMContentLoaded handler runs first (it is loaded first): by the time this runs, the
     * page's status, contact and listing are rendered, which is what the section reads.
     */
    document.addEventListener("DOMContentLoaded", function () {
        render();
        watch();
    });
})();
