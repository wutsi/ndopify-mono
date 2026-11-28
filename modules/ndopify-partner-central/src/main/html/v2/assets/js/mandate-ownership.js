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
 * FAILED carries one reason per failed check (#402: one short sentence, the piece it looked at in bold, and only the
 * cause — no action, no instruction list, no button; the section's intro links to the Documents section and the
 * document page says what to correct). A reason still carries the document's problem codes as `details`, but this
 * section no longer shows them; it never names a document id, type, title, page, path, value nor agent title.
 * UNAVAILABLE is the section's own state when the read fails or answers UNKNOWN.
 * The Documents rows just below the section are what name each piece's type, title and status.
 * #376: the verdict also carries location { mandateCity, documentLocality, status }, the city comparison of the
 * title's locality against the mandate's city. UNKNOWN (nothing to compare) and MATCH show nothing; UNRESOLVED
 * shows the neutral hint under the verdict, whatever the status, with the two places and a link to the Documents
 * section; MISMATCH is the FAILED OWNERSHIP_PROPERTY_MISMATCH reason, with the two places inline in its sentence
 * (#402). The comparison never decides a verdict by itself beyond that one reason, and a missing place reads
 * "non précisée".
 * #377: a VERIFIED verdict may carry overridden: true (an admin overrode the owner verification). It is still
 * VERIFIED, but it is its own state: the pill reads "Vérifiée par Ndopify", its intro is the party sentence alone
 * ("Ndopify vérifie que le vendeur est bien le propriétaire du bien.", no documents clause — #402 makes that clause
 * the intro of the other states), and the block says Ndopify verified the owner after review — never that the
 * documents passed; the publication hint and the listing link stay the verified ones. No date, reason, admin identity
 * nor admin reason is shown. The section is absent on a SEARCH mandate and on a CLOSED / CANCELLED one.
 *
 * Query switch (prototype only; production renders all of it server-side):
 *   ?ownership=pending[&reason=both|id-missing|document-missing|in-flight]   (both is the default)
 *   ?ownership=verified                      (a MATCH comparison — the title is in the mandate's city, so no hint;
 *                                             301 with &listing=published shows the listing link)
 *   ?ownership=overridden                    (the VERIFIED verdict of an admin's override, #377: its own pill and
 *                                             wording; &listing=published gives it the same listing link)
 *   ?ownership=hint[&state=verified|pending|failed][&locality=none][&city=none]
 *                                            (#376's UNRESOLVED comparison, on any status: the default is a VERIFIED
 *                                             verdict of 301 with the title's locality "Bonabéri"; &locality=none blanks
 *                                             it, &city=none blanks the mandate's city — both blank shows no hint)
 *   ?ownership=failed[&reason=mixed|id-expired|id-unusable|id-name|document-unusable|document-name|property-mismatch|unknown]
 *                                            (id-expired is the default; 301's own verdict; property-mismatch is
 *                                             #376's OWNERSHIP_PROPERTY_MISMATCH, Douala against Yaoundé; unknown is a
 *                                             code this page doesn't know yet, #402)
 *   ?ownership=unavailable                   (the verdict read failed: only the section says so)
 *   ?ownership=none                          (the section as a SEARCH or CLOSED / CANCELLED page renders it: absent)
 * The samples for 301 and 302 are the mandates' own: 301 has an expired identity card, 302 has no document.
 */
(function () {
    "use strict";

    // The French wording is one source (ownership-copy.js, loaded before this file), so every reason of the section
    // reads the same words.
    var copy = window.NdOwnership;

    /*
     * PROTOTYPE: the sample verdicts, as GET /v1/mandates/{id}/ownership would send them — a status and reasons, no
     * date, no type nor title; details are the failed document's problemCodes, in the server's order (carried by the
     * verdict, no longer shown here since #402). The default is FAILED for 301 (its expired identity card) and
     * PENDING with both pieces missing for any other LISTING mandate (302 has no document yet).
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
        // #375: the listing page's multi-reason sample (its default FAILED state), so that the link from a blocked
        // listing opens the same verdict here.
        mixed: [
            { code: "OWNERSHIP_ID_EXPIRED" },
            { code: "OWNERSHIP_DOCUMENT_UNUSABLE", details: ["DOCUMENT_TYPE_MISMATCH", "DOCUMENT_PAGE_MISSING"] },
            { code: "OWNERSHIP_DOCUMENT_NAME_MISMATCH" }
        ],
        "id-expired": [{ code: "OWNERSHIP_ID_EXPIRED" }],
        "id-unusable": [{ code: "OWNERSHIP_ID_UNUSABLE", details: ["DOCUMENT_UNREADABLE"] }],
        "id-name": [{ code: "OWNERSHIP_ID_NAME_MISMATCH" }],
        "document-unusable": [{
            code: "OWNERSHIP_DOCUMENT_UNUSABLE",
            details: ["DOCUMENT_TYPE_MISMATCH", "DOCUMENT_PAGE_MISSING"]
        }],
        "document-name": [{ code: "OWNERSHIP_DOCUMENT_NAME_MISMATCH" }],
        // #402: a code this page doesn't know yet (a server code added later) reads the generic sentence.
        "unknown": [{ code: "OWNERSHIP_UNKNOWN_CHECK" }],
        // #376: the title's locality is a known city other than the mandate's (301 is in Douala, the title says
        // Yaoundé). One reason only, and the two places are the comparison's only data.
        "property-mismatch": [{ code: "OWNERSHIP_PROPERTY_MISMATCH" }]
    };

    // #376: the city comparison of a MISMATCH verdict; every other sample has nothing to compare (UNKNOWN) but the
    // ?ownership=hint ones, set below.
    var MISMATCH_LOCATION = { mandateCity: "Douala", documentLocality: "Yaoundé", status: "MISMATCH" };

    // A place the comparison carries as blank reads "non précisée" — never an empty cell.
    var UNKNOWN_PLACE = "non précisée";

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
        // #376: a verified owner whose title resolves to the mandate's own city (MATCH): no hint, as today.
        if (choice === "verified") {
            return {
                status: "VERIFIED",
                reasons: [],
                location: { mandateCity: "Douala", documentLocality: "Douala", status: "MATCH" }
            };
        }
        // #377: VERIFIED by an admin's override — its own state, with the verdict's overridden flag.
        if (choice === "overridden") return { status: "VERIFIED", overridden: true, reasons: [] };
        // #376: the UNRESOLVED comparison on any of the section's statuses — the hint never changes the status.
        if (choice === "hint") {
            var state = params.get("state");
            var verdict = state === "pending"
                ? { status: "PENDING", reasons: [{ code: "OWNERSHIP_ID_MISSING" }] }
                : state === "failed"
                    ? { status: "FAILED", reasons: FAILED_SAMPLES["id-expired"] }
                    : { status: "VERIFIED", reasons: [] };
            verdict.location = {
                mandateCity: params.get("city") === "none" ? "" : "Douala",
                documentLocality: params.get("locality") === "none" ? "" : "Bonabéri",
                status: "UNRESOLVED"
            };
            return verdict;
        }
        if (choice === "failed") {
            var reason = params.get("reason");
            return {
                status: "FAILED",
                reasons: FAILED_SAMPLES[reason] || FAILED_SAMPLES["id-expired"],
                location: reason === "property-mismatch" ? MISMATCH_LOCATION :
                    { mandateCity: "", documentLocality: "", status: "UNKNOWN" }
            };
        }
        if (choice === "pending") {
            return { status: "PENDING", reasons: PENDING_SAMPLES[params.get("reason")] || PENDING_SAMPLES.both };
        }
        return id === "301"
            ? { status: "FAILED", reasons: FAILED_SAMPLES["id-expired"] }
            : { status: "PENDING", reasons: PENDING_SAMPLES.both };
    }

    // #377: the verified and overridden-verified verdicts are two blocks of the same section; both are shown one at a
    // time, so the active one is what carries the listing link.
    var BLOCKS = ["unavailable", "pending", "failed", "verified", "overridden"];

    function showBlock(section, name) {
        BLOCKS.forEach(function (state) {
            $('[data-ownership-' + state + ']', section).hidden = state !== name;
        });
        return $('[data-ownership-' + name + ']', section);
    }

    function setTag(section, verdict) {
        var tag = $("[data-ownership-tag]", section);
        var entry = verdict.overridden
            ? ["success", "Vérifiée par Ndopify"]
            : {
                PENDING: ["info", "En cours"],
                VERIFIED: ["success", "Vérifié"],
                FAILED: ["warning", "Non vérifié"]
            }[verdict.status];
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
            item.textContent = copy.MISSING_PIECES[reason.code] || copy.MISSING_OTHER;
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
        var location = verdict.location || {};
        list.innerHTML = "";
        verdict.reasons.forEach(function (reason) {
            var item = template.content.firstElementChild.cloneNode(true);
            // #402: one short sentence per reason — the piece it looked at, in bold, then the cause. No action, no
            // instruction list and no places list: the intro links to the Documents section, the document page says
            // what to correct.
            var wording = copy.reasonOf(reason.code, location);
            $("[data-reason-check]", item).textContent = wording.piece;
            $("[data-reason-text]", item).textContent = wording.text;
            list.appendChild(item);
        });
    }

    /*
     * #376: the city comparison, a neutral hint under the verdict. UNRESOLVED only — MATCH and UNKNOWN show nothing —
     * and nothing either when the verdict carries no place at all. It never changes the section's status: the block
     * above keeps its own styling.
     */
    function renderLocation(section, verdict) {
        var block = $("[data-ownership-location]", section);
        var location = verdict.location || {};
        var city = location.mandateCity || "";
        var locality = location.documentLocality || "";
        block.hidden = location.status !== "UNRESOLVED" || (!city && !locality);
        if (block.hidden) return;
        $("[data-location-mandate-city]", block).textContent = city || UNKNOWN_PLACE;
        $("[data-location-document-locality]", block).textContent = locality || UNKNOWN_PLACE;
    }

    // VERIFIED has no date (the verdict carries none) and no reason: the hint, then the listing when there is one.
    // #377: scoped to the block the state shows, so the overridden state gets its own listing link.
    function renderVerified(block) {
        var actions = $("[data-ownership-listing-actions]", block);
        var link = $("[data-ownership-listing]", block);
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

        // #402: the intro is the section's short way to the fix, whatever the status. #377's overridden verdict keeps
        // its own sentence instead, without the documents clause — the documents didn't decide it.
        var overridden = verdict.status === "VERIFIED" && verdict.overridden;
        $("[data-ownership-intro-documents]", section).hidden = overridden;
        $("[data-ownership-intro-overridden]", section).hidden = !overridden;
        $("[data-ownership-party]", section).textContent = party === "sale" ? "le vendeur" : "le bailleur";
        setTag(section, verdict);
        var block = showBlock(section, overridden ? "overridden" : verdict.status.toLowerCase());
        if (verdict.status === "PENDING") renderPending(section, verdict);
        if (verdict.status === "FAILED") renderFailed(section, verdict);
        if (verdict.status === "VERIFIED") renderVerified(block);
        renderLocation(section, verdict);
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
