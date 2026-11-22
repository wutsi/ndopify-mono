/*
 * Ndopify Partner Central — shared behaviour, served with the Thymeleaf pages.
 * Each page sets <body data-page="..."> and gets its own initialiser below.
 * Progressive enhancement only: the server renders and validates every page, and every form posts without this.
 */
(function () {
    "use strict";

    var EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

    var KEYS = {
        email: "npc.email"
    };

    /* ---------- Storage (fails soft: private mode, quota) ---------- */
    var store = {
        get: function (key) {
            try {
                return window.sessionStorage.getItem(key);
            } catch (e) {
                return null;
            }
        },
        set: function (key, value) {
            try {
                window.sessionStorage.setItem(key, value);
                return true;
            } catch (e) {
                return false;
            }
        },
        remove: function (key) {
            try {
                window.sessionStorage.removeItem(key);
            } catch (e) {
                /* ignore */
            }
        }
    };

    /* ---------- Helpers ---------- */
    function $(selector, root) {
        return (root || document).querySelector(selector);
    }

    function $all(selector, root) {
        return Array.prototype.slice.call((root || document).querySelectorAll(selector));
    }

    function announce(region, message) {
        if (!region) return;
        region.textContent = "";
        window.setTimeout(function () {
            region.textContent = message;
        }, 50);
    }

    function setFieldError(input, errorEl, message) {
        if (message) {
            errorEl.querySelector("[data-error-text]").textContent = message;
            errorEl.hidden = false;
            input.setAttribute("aria-invalid", "true");
        } else {
            errorEl.hidden = true;
            input.removeAttribute("aria-invalid");
        }
    }

    // Source doc: submit buttons stay inactive until the form is valid. aria-disabled (not the disabled
    // attribute) keeps them in the tab order so their aria-describedby explanation can still be reached.
    function setActive(button, active) {
        if (active) button.removeAttribute("aria-disabled");
        else button.setAttribute("aria-disabled", "true");
    }

    // One post per page: once a form is on its way, its button says so and every submit of the page does nothing
    // until the next page comes (a second OTP would replace the first one's code). See the pageshow below.
    var busy = false;
    var syncs = [];

    function setBusy(button, label) {
        busy = true;
        button.setAttribute("aria-disabled", "true");
        if (!button.hasAttribute("data-label")) button.setAttribute("data-label", button.textContent);
        button.textContent = label;
    }

    // Back from the next page restores this one from the cache as it was left: the buttons must work again.
    window.addEventListener("pageshow", function (event) {
        if (!event.persisted || !busy) return;
        busy = false;
        $all("[data-label]").forEach(function (button) {
            button.removeAttribute("aria-disabled");
            button.textContent = button.getAttribute("data-label");
        });
        syncs.forEach(function (sync) {
            sync();
        });
    });

    function formatTime(ms) {
        var d = new Date(ms);
        var h = d.getHours();
        var m = String(d.getMinutes()).padStart(2, "0");
        return h + " h " + m;
    }

    function initialsFromEmail(email) {
        if (!email) return "NP";
        var name = email.split("@")[0].replace(/[^a-zA-Z.\-_]/g, "");
        var parts = name.split(/[.\-_]+/).filter(Boolean);
        if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
        return (name.slice(0, 2) || "NP").toUpperCase();
    }

    /* ---------- Header user menu ---------- */
    function initUserMenu() {
        var toggle = $("[data-user-menu-toggle]");
        var list = $("[data-user-menu-list]");
        if (!toggle || !list) return;

        var initialsEl = $("[data-user-initials]");
        // The pages render the agent's initials; without them, fall back to the login's copy of the e-mail.
        if (initialsEl && !initialsEl.textContent.trim()) {
            initialsEl.textContent = initialsFromEmail(store.get(KEYS.email));
        }

        function close(returnFocus) {
            list.hidden = true;
            toggle.setAttribute("aria-expanded", "false");
            if (returnFocus) toggle.focus();
        }

        toggle.addEventListener("click", function () {
            var open = toggle.getAttribute("aria-expanded") === "true";
            if (open) {
                close(false);
            } else {
                list.hidden = false;
                toggle.setAttribute("aria-expanded", "true");
            }
        });

        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape" && toggle.getAttribute("aria-expanded") === "true") close(true);
        });

        document.addEventListener("click", function (event) {
            if (!list.hidden && !list.contains(event.target) && !toggle.contains(event.target)) close(false);
        });

        list.addEventListener("focusout", function (event) {
            if (event.relatedTarget && !list.contains(event.relatedTarget) && event.relatedTarget !== toggle) {
                close(false);
            }
        });

        $all("[data-logout]").forEach(function (form) {
            form.addEventListener("submit", function () {
                Object.keys(KEYS).forEach(function (k) {
                    store.remove(KEYS[k]);
                });
            });
        });
    }

    /* ---------- login.html, e-mail step (LoginController: POST /login) ---------- */
    function initLoginEmail(root) {
        var form = $("[data-login-form]", root);
        var input = $("#email", root);
        var error = $("#email-error", root);
        var submit = $("[data-submit]", root);

        function validity() {
            var value = input.value.trim();
            if (!value) return "Saisissez votre adresse email.";
            if (!EMAIL_PATTERN.test(value)) return "Cette adresse email semble incomplète. Exemple : nom@exemple.com";
            return "";
        }

        function sync() {
            setActive(submit, !busy && validity() === "");
        }

        input.addEventListener("input", function () {
            if (input.getAttribute("aria-invalid") === "true") setFieldError(input, error, validity());
            sync();
        });

        input.addEventListener("blur", function () {
            if (input.value.trim()) setFieldError(input, error, validity());
        });

        // A valid form is submitted to the server as-is, once.
        form.addEventListener("submit", function (event) {
            if (busy) {
                event.preventDefault();
                return;
            }
            var message = validity();
            setFieldError(input, error, message);
            if (message) {
                event.preventDefault();
                input.focus();
                return;
            }
            // Kept for the pages that don't render the agent's email themselves (the header menu falls back to it); kyc.html does.
            store.set(KEYS.email, input.value.trim().toLowerCase());
            setBusy(submit, "Envoi du code…");
        });

        if (!input.value) input.value = store.get(KEYS.email) || "";
        if (input.getAttribute("aria-invalid") === "true") input.focus();
        syncs.push(sync);
        sync();
    }

    /* ---------- login.html, code step (LoginController: POST /login/verify, resend via POST /login) ---------- */
    function initLoginCode(root) {
        var form = $("[data-code-form]", root);
        var input = $("#code", root);
        var error = $("#code-error", root);
        var submit = $("[data-submit]", root);
        var status = $("[data-status]", root);
        var expiryEl = $("[data-expiry]", root);
        var expiresAt = expiryEl ? Number(expiryEl.getAttribute("data-expires-at")) || 0 : 0;

        function isExpired() {
            return expiresAt > 0 && Date.now() >= expiresAt;
        }

        // The server is the authority on expiry; this only tells the user before they try.
        function scheduleExpiry() {
            if (!expiresAt) return;
            expiryEl.textContent = formatTime(expiresAt);
            var remaining = expiresAt - Date.now();
            if (remaining <= 0) return;
            window.setTimeout(function () {
                setFieldError(input, error, "Ce code a expiré. Demandez un nouveau code ci-dessous.");
                announce(status, "Votre code a expiré. Vous pouvez demander un nouveau code.");
                sync();
            }, remaining);
        }

        function validity() {
            var value = input.value.trim();
            if (!value) return "Saisissez le code à 6 chiffres reçu par email.";
            if (!/^\d{6}$/.test(value)) return "Le code contient exactement 6 chiffres.";
            return "";
        }

        function sync() {
            setActive(submit, !busy && validity() === "" && !isExpired());
        }

        input.addEventListener("input", function () {
            var digits = input.value.replace(/\D/g, "").slice(0, 6);
            if (digits !== input.value) input.value = digits;
            if (input.getAttribute("aria-invalid") === "true" && !isExpired()) setFieldError(input, error, "");
            sync();
        });

        form.addEventListener("submit", function (event) {
            if (busy) {
                event.preventDefault();
                return;
            }
            var message = isExpired() ? "Ce code a expiré. Demandez un nouveau code ci-dessous." : validity();
            setFieldError(input, error, message);
            if (message) {
                event.preventDefault();
                input.focus();
                return;
            }
            setBusy(submit, "Connexion…");
        });

        // "Renvoyer le code" posts the hidden resend form: once, and not while the code is being checked.
        var resendForm = document.getElementById("resend-form");
        var resend = $('[form="resend-form"]', root);
        if (resendForm && resend) {
            resendForm.addEventListener("submit", function (event) {
                if (busy) {
                    event.preventDefault();
                    return;
                }
                setBusy(resend, "Envoi du code…");
                sync();
                announce(status, "Envoi d'un nouveau code en cours.");
            });
        }

        scheduleExpiry();
        if (isExpired()) setFieldError(input, error, "Ce code a expiré. Demandez un nouveau code ci-dessous.");
        if (input.getAttribute("aria-invalid") === "true") {
            input.select();
            input.focus();
        }
        syncs.push(sync);
        sync();
    }

    /*
     * login.html: the server renders only the current step (it owns the URLs and the guard), so this just
     * wires up whichever step is on the page.
     */
    function initLogin() {
        var emailStep = $('[data-step="email"]');
        var codeStep = $('[data-step="code"]');
        if (emailStep) initLoginEmail(emailStep);
        if (codeStep) initLoginCode(codeStep);
    }

    /* ---------- Boot ---------- */
    var pages = {
        login: initLogin
    };

    document.addEventListener("DOMContentLoaded", function () {
        initUserMenu();
        var init = pages[document.body.getAttribute("data-page")];
        if (init) init();
    });
})();
