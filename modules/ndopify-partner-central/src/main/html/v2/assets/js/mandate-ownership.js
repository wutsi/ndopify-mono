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
 * codes as details, worded as the document page words them (#331; the French wording is shared with the listing
 * page's publication block in ownership-copy.js, and production reads the same document.problem.<code> bundle).
 * UNAVAILABLE is the section's own state when the read fails or answers UNKNOWN.
 * The Documents rows just below the section are what name each piece's type, title and status.
 * #377: a VERIFIED verdict may carry overridden: true (an admin overrode the owner verification). It is still
 * VERIFIED, but it is its own state: the pill reads "Vérifiée par Ndopify", the intro loses its documents clause,
 * and the block says Ndopify verified the owner after review — never that the documents passed; the publication
 * hint and the listing link stay the verified ones. No date, reason, admin identity nor admin reason is shown.
 * The section is absent on a SEARCH mandate and on a CLOSED / CANCELLED one.
 *
 * Query switch (prototype only; production renders all of it server-side):
 *   ?ownership=pending[&reason=both|id-missing|document-missing|in-flight]   (both is the default)
 *   ?ownership=verified                      (301 with &listing=published shows the listing link)
 *   ?ownership=overridden                    (the VERIFIED verdict of an admin's override, #377: its own pill and
 *                                             wording; &listing=published gives it the same listing link)
 *   ?ownership=failed[&reason=mixed|id-expired|id-unusable|id-name|document-unusable|document-name]
 *                                            (id-expired is the default; 301's own verdict)
 *   ?ownership=unavailable                   (the verdict read failed: only the section says so)
 *   ?ownership=none                          (the section as a SEARCH or CLOSED / CANCELLED page renders it: absent)
 * The samples for 301 and 302 are the mandates' own: 301 has an expired identity card, 302 has no document.
 */
(function () {
    "use strict";

    // #375: the French wording is one source, shared with the listing page's publication block (ownership-copy.js,
    // loaded before this file), so that the two pages cannot drift.
    var copy = window.NdOwnership;

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
        // #377: VERIFIED by an admin's override — its own state, with the verdict's overridden flag.
        if (choice === "overridden") return { status: "VERIFIED", overridden: true, reasons: [] };
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
        list.innerHTML = "";
        verdict.reasons.forEach(function (reason) {
            var item = template.content.firstElementChild.cloneNode(true);
            var wording = copy.REASON_WORDING[reason.code] || copy.REASON_OTHER;
            // The check's kind, never a document type, title, id, page, path, URL nor extracted value.
            var check = $("[data-reason-check]", item);
            check.textContent = copy.checkKind(reason.code);
            check.hidden = !check.textContent;
            $("[data-reason-text]", item).textContent = wording.text;
            var problems = $("[data-reason-problems]", item);
            problems.innerHTML = "";
            (reason.details || []).forEach(function (code) {
                var problem = document.createElement("li");
                problem.textContent = copy.PROBLEM_INSTRUCTIONS[code] || copy.PROBLEM_OTHER;
                problems.appendChild(problem);
            });
            problems.hidden = problems.children.length === 0;
            $("[data-reason-action]", item).textContent = wording.action;
            list.appendChild(item);
        });
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

        $("[data-ownership-party]", section).textContent = party === "sale" ? "le vendeur" : "le bailleur";
        // #377: an overridden VERIFIED verdict is its own block, and its intro no longer says the documents decided
        // it — the verdict came from Ndopify's review.
        var overridden = verdict.status === "VERIFIED" && verdict.overridden;
        $("[data-ownership-intro-documents]", section).hidden = overridden;
        setTag(section, verdict);
        var block = showBlock(section, overridden ? "overridden" : verdict.status.toLowerCase());
        if (verdict.status === "PENDING") renderPending(section, verdict);
        if (verdict.status === "FAILED") renderFailed(section, verdict);
        if (verdict.status === "VERIFIED") renderVerified(block);
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
