/*
 * Ndopify e-Signature — prototype behaviour of sign.html (and of verify.html, see renderVerify).
 *
 * One page, four slides (?step=intro|code|sign|done), each a history entry. A slide only opens once the earlier ones
 * are done, whatever the URL says: the code slide once a code was sent, the signing slide once the code was
 * verified, the confirmation once signed. The served page gets that state from the server; the prototype keeps it
 * in localStorage, per contract and signer, so a reload, a return from the mail app or the e-mail's link opened again
 * in another tab keeps the slide.
 */
(function () {
    "use strict";

    var STEPS = ["intro", "code", "sign", "done"];
    var CODE_LENGTH = 6;
    var CODE_TTL_MS = 5 * 60 * 1000;
    var PARAMS = new URL(window.location.href).searchParams;
    var STORAGE_KEY = "ndopify.esignature." + (PARAMS.get("contract-id") || "") + "." + (PARAMS.get("party-id") || "");
    var LATENCY_MS = 600; // Fake network delay: shows the busy state

    // Prototype codes (see the comment at the top of sign.html)
    var EXPIRED_CODE = "000000";
    var WRONG_CODE = "111111";

    var MESSAGES = {
        codeExpired: "Ce code a expiré. Demandez-en un nouveau ci-dessous.",
        codeWrong: "Code incorrect. Vérifiez les 6 chiffres reçus par e-mail.",
        codeSent: "Un nouveau code a été envoyé. Il remplace le précédent.",
        sending: "Envoi du code…",
        verifying: "Vérification…",
        signing: "Signature en cours…"
    };

    function $(selector, root) {
        return (root || document).querySelector(selector);
    }

    function $$(selector, root) {
        return Array.prototype.slice.call((root || document).querySelectorAll(selector));
    }

    // ---------- State ----------

    function load() {
        try {
            return JSON.parse(window.localStorage.getItem(STORAGE_KEY)) || {};
        } catch (e) {
            return {};
        }
    }

    function save(state) {
        try {
            window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
        } catch (e) {
            // Private mode: the page still works, it just forgets on reload
        }
    }

    var state = load();

    function update(changes) {
        Object.keys(changes).forEach(function (key) {
            state[key] = changes[key];
        });
        save(state);
    }

    // The furthest slide the signer may see
    // Once verified, the code steps are behind the signer: Précédent from the signing slide isn't offered.
    function allowedStep(requested) {
        if (state.signedAt) return "done";
        if (state.authenticated) return "sign";
        if (state.codeSent && requested !== "intro") return "code";
        return "intro";
    }

    // ---------- Formatting (the tenant's: French, Africa/Douala) ----------

    function formatDate(date) {
        return new Intl.DateTimeFormat("fr-FR", {
            day: "numeric", month: "long", year: "numeric", timeZone: "Africa/Douala"
        }).format(date);
    }

    function formatTime(date) {
        var parts = new Intl.DateTimeFormat("fr-FR", {
            hour: "2-digit", minute: "2-digit", timeZone: "Africa/Douala"
        }).formatToParts(date);
        var hour = parts.filter(function (p) { return p.type === "hour"; })[0].value;
        var minute = parts.filter(function (p) { return p.type === "minute"; })[0].value;
        return hour + " h " + minute;
    }

    // ---------- Buttons ----------

    function setEnabled(button, enabled) {
        button.setAttribute("aria-disabled", enabled ? "false" : "true");
    }

    function isEnabled(button) {
        return button.getAttribute("aria-disabled") !== "true" && button.getAttribute("aria-busy") !== "true";
    }

    // Busy: the label says what's happening (also to a screen reader), and a second press does nothing
    function setBusy(button, busyLabel) {
        var live = $("[data-live]");
        if (live) live.textContent = busyLabel || "";
        if (busyLabel) {
            button.dataset.label = button.textContent;
            button.textContent = busyLabel;
            button.setAttribute("aria-busy", "true");
        } else {
            if (button.dataset.label) button.textContent = button.dataset.label;
            button.removeAttribute("aria-busy");
        }
    }

    // ---------- Slides ----------

    var progressLabel = $("[data-progress-label]");
    var progress = $("[data-progress]");
    var progressBar = $("[data-progress-bar]");
    var progressBlock = $("[data-progress-block]");
    var stepsTitle = $("[data-steps-title]");
    var column = $("[data-column]");

    function render(step, options) {
        options = options || {};
        $$("[data-step]").forEach(function (slide) {
            slide.hidden = slide.getAttribute("data-step") !== step;
        });

        var index = STEPS.indexOf(step) + 1;
        var inWizard = step !== "done";
        progressBlock.hidden = !inWizard;
        stepsTitle.hidden = !inWizard;
        if (inWizard) {
            var text = "Étape " + index + " sur 3";
            progressLabel.textContent = text;
            progress.setAttribute("aria-valuenow", String(index));
            progress.setAttribute("aria-valuetext", text);
            progressBar.style.transform = "scaleX(" + (index / 3).toFixed(4) + ")";
        }

        // The contract needs room; the short steps read better narrow
        column.classList.toggle("column--wide", step === "sign");
        $("[data-page-intro]").classList.toggle("page-intro--center", step === "done");

        if (step === "code") renderExpiry();
        if (step === "sign") renderSignature();
        if (step === "done") renderDone();

        if (options.focus !== false) {
            // Moves focus to the new slide, so a screen reader announces it (legend, or the confirmation's heading).
            // On the code slide, straight to the field: the keyboard and the code autofill open at once, and the
            // fieldset's legend is still read on entering it.
            var target = step === "done" ? $("#done-title")
                : step === "code" ? codeInput
                : $("[data-step='" + step + "'] .step");
            if (target) target.focus();
        }
    }

    function go(step) {
        var url = new URL(window.location.href);
        url.searchParams.set("step", step);
        window.history.pushState({ step: step }, "", url);
        render(step);
    }

    function requestedStep() {
        var step = new URL(window.location.href).searchParams.get("step");
        return STEPS.indexOf(step) >= 0 ? step : "intro";
    }

    // ---------- Blocked link (prototype: ?state=signed|expired|cancelled) ----------

    function renderBlocked(reason) {
        var blocked = $("[data-blocked]");
        var alert = $("[data-blocked-state='" + reason + "']", blocked);
        if (!alert) return false;
        blocked.hidden = false;
        alert.hidden = false;
        progressBlock.hidden = true;
        stepsTitle.hidden = true;
        $$("[data-step]").forEach(function (slide) {
            slide.hidden = true;
        });
        return true;
    }

    // ---------- Step 1: intro ----------

    function sendCode(button, onSent) {
        setBusy(button, MESSAGES.sending);
        window.setTimeout(function () {
            setBusy(button, null);
            update({ codeSent: true, codeExpiresAt: Date.now() + CODE_TTL_MS });
            onSent();
        }, LATENCY_MS);
    }

    function bindIntro() {
        var form = $("[data-intro-form]");
        var submit = $("[data-submit]", form);
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (!isEnabled(submit)) return;
            sendCode(submit, function () {
                go("code");
            });
        });
    }

    // ---------- Step 2: code ----------

    var codeInput = $("#code");
    var codeError = $("#code-error");
    var codeSubmit = $("[data-code-form] [data-submit]");

    function renderExpiry() {
        if (state.codeExpiresAt) $("[data-expiry]").textContent = formatTime(new Date(state.codeExpiresAt));
    }

    function showCodeError(message) {
        $("[data-error-text]", codeError).textContent = message;
        codeError.hidden = false;
        codeInput.setAttribute("aria-invalid", "true");
        codeInput.focus();
    }

    function clearCodeError() {
        codeError.hidden = true;
        codeInput.removeAttribute("aria-invalid");
    }

    function syncCode() {
        // Digits only, whatever was typed or pasted ("123 456", "123-456")
        var digits = codeInput.value.replace(/\D/g, "").slice(0, CODE_LENGTH);
        if (digits !== codeInput.value) codeInput.value = digits;
        setEnabled(codeSubmit, digits.length === CODE_LENGTH);
    }

    function bindCode() {
        var form = $("[data-code-form]");
        var status = $("[data-status]", form);

        codeInput.addEventListener("input", function () {
            clearCodeError();
            syncCode();
        });

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (!isEnabled(codeSubmit)) return;
            var code = codeInput.value;
            setBusy(codeSubmit, MESSAGES.verifying);
            window.setTimeout(function () {
                setBusy(codeSubmit, null);
                if (code === EXPIRED_CODE || (state.codeExpiresAt && Date.now() > state.codeExpiresAt)) {
                    showCodeError(MESSAGES.codeExpired);
                } else if (code === WRONG_CODE) {
                    showCodeError(MESSAGES.codeWrong);
                } else {
                    update({ authenticated: true });
                    go("sign");
                }
            }, LATENCY_MS);
        });

        $("[data-resend]", form).addEventListener("click", function () {
            var button = this;
            if (button.getAttribute("aria-busy") === "true") return;
            status.textContent = "";
            setBusy(button, MESSAGES.sending);
            window.setTimeout(function () {
                setBusy(button, null);
                update({ codeExpiresAt: Date.now() + CODE_TTL_MS });
                renderExpiry();
                clearCodeError();
                codeInput.value = "";
                syncCode();
                status.textContent = MESSAGES.codeSent;
            }, LATENCY_MS);
        });

        $("[data-previous]", form).addEventListener("click", function () {
            go("intro");
        });

        syncCode();
    }

    // ---------- Step 3: sign ----------

    var consent = $("#consent");
    var signSubmit = $("[data-sign-form] [data-submit]");

    function renderSignature() {
        $("[data-sign-date]").textContent = formatDate(new Date());
    }

    function syncConsent() {
        setEnabled(signSubmit, consent.checked);
    }

    function bindSign() {
        var form = $("[data-sign-form]");
        consent.addEventListener("change", syncConsent);
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (!isEnabled(signSubmit)) return;
            setBusy(signSubmit, MESSAGES.signing);
            window.setTimeout(function () {
                setBusy(signSubmit, null);
                update({ signedAt: Date.now() });
                go("done");
            }, LATENCY_MS);
        });
        syncConsent();
    }

    // ---------- Step 5: done ----------

    function renderDone() {
        if (!state.signedAt) return;
        var date = new Date(state.signedAt);
        $("[data-signed-at]").textContent = formatDate(date) + " à " + formatTime(date);
    }

    // ---------- Start ----------

    // verify.html: one view or the other (prototype: ?state=invalid), no state, no slides.
    function renderVerify() {
        var shown = new URL(window.location.href).searchParams.get("state") === "invalid" ? "invalid" : "valid";
        $$("[data-verify-state]").forEach(function (section) {
            section.hidden = section.getAttribute("data-verify-state") !== shown;
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (document.body.getAttribute("data-page") === "verify") {
            renderVerify();
            return;
        }

        var reason = new URL(window.location.href).searchParams.get("state");
        if (reason && renderBlocked(reason)) return;

        bindIntro();
        bindCode();
        bindSign();

        var requested = requestedStep();
        var step = allowedStep(requested);
        if (step !== requested) {
            var url = new URL(window.location.href);
            url.searchParams.set("step", step);
            window.history.replaceState({ step: step }, "", url);
        }
        render(step, { focus: false });

        // Back / Forward: the slide shown is always the URL's; one that can't be shown any more (ex: the code once
        // verified) is replaced in the history by the one that can, so the page and the URL never disagree.
        window.addEventListener("popstate", function () {
            var requested = requestedStep();
            var target = allowedStep(requested);
            if (target !== requested) {
                var url = new URL(window.location.href);
                url.searchParams.set("step", target);
                window.history.replaceState({ step: target }, "", url);
            }
            render(target);
        });
    });
})();
