/*
 * Ndopify e-Signature: enhancement of the sign page (templates/sign.html).
 *
 * The server renders the current step and every step posts without this script. It only:
 * - keeps the code field to digits and enables "Suivant" once the 6 are there,
 * - enables "Signer électroniquement" once the consent box is ticked,
 * - shows a busy state on submit, so that a second press does nothing,
 * - moves focus to the code field / the confirmation's heading, so that the new step is announced.
 */
(function () {
    "use strict";

    var CODE_LENGTH = 6;
    var MESSAGES = {
        sending: "Envoi du code…",
        verifying: "Vérification…",
        signing: "Signature en cours…"
    };

    function $(selector, root) {
        return (root || document).querySelector(selector);
    }

    function setEnabled(button, enabled) {
        button.setAttribute("aria-disabled", enabled ? "false" : "true");
    }

    function isEnabled(button) {
        return button.getAttribute("aria-disabled") !== "true" && button.getAttribute("aria-busy") !== "true";
    }

    // Busy: the label says what's happening (also to a screen reader). The page changes with the response, so the
    // button stays busy until then.
    function setBusy(button, busyLabel) {
        var live = $("[data-live]");
        if (live) live.textContent = busyLabel;
        button.textContent = busyLabel;
        button.setAttribute("aria-busy", "true");
    }

    // A submit that is disabled or already under way does nothing
    function guard(form, button, busyLabel) {
        form.addEventListener("submit", function (event) {
            if (!isEnabled(button)) {
                event.preventDefault();
                return;
            }
            setBusy(button, busyLabel);
        });
    }

    function bindIntro() {
        var form = $("[data-intro-form]");
        if (form) guard(form, $("[data-submit]", form), MESSAGES.sending);
    }

    function bindCode() {
        var form = $("[data-code-form]");
        if (!form) return;
        var input = $("#code", form);
        var submit = $("[data-submit]", form);

        function sync() {
            // Digits only, whatever was typed or pasted ("123 456", "123-456")
            var digits = input.value.replace(/\D/g, "").slice(0, CODE_LENGTH);
            if (digits !== input.value) input.value = digits;
            setEnabled(submit, digits.length === CODE_LENGTH);
        }

        input.addEventListener("input", function () {
            input.removeAttribute("aria-invalid");
            var error = $("#code-error");
            if (error) error.hidden = true;
            sync();
        });
        guard(form, submit, MESSAGES.verifying);

        var resend = $("[data-resend]", form);
        var resendForm = document.getElementById(resend.getAttribute("form"));
        resendForm.addEventListener("submit", function (event) {
            if (resend.getAttribute("aria-busy") === "true") {
                event.preventDefault();
                return;
            }
            setBusy(resend, MESSAGES.sending);
        });

        sync();
        // Straight to the field: the keyboard and the code autofill open at once; the legend is still read on entering
        input.focus();
    }

    function bindSign() {
        var form = $("[data-sign-form]");
        var consent = $("#consent");
        var submit = form && $("[data-submit]", form);
        if (!form || !consent || !submit) return;

        function sync() {
            setEnabled(submit, consent.checked);
        }

        consent.addEventListener("change", sync);
        guard(form, submit, MESSAGES.signing);
        sync();
    }

    function focusConfirmation() {
        var title = $("#done-title");
        if (title) title.focus();
    }

    document.addEventListener("DOMContentLoaded", function () {
        bindIntro();
        bindCode();
        bindSign();
        focusConfirmation();
    });
})();
