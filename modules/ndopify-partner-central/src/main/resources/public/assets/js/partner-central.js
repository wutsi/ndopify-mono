/*
 * Ndopify Partner Central — shared behaviour (static prototype).
 * Each page sets <body data-page="..."> and gets its own initialiser below.
 * Server calls are simulated; every simulation is marked "PROTOTYPE".
 */
(function () {
    "use strict";

    var OTP_TTL_MS = 5 * 60 * 1000;
    var OTP_MAX_ATTEMPTS = 5;
    var MAX_FILE_BYTES = 5 * 1024 * 1024;
    var ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
    var NIU_PATTERN = /^[A-Z0-9]{14}$/;
    var EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

    // PROTOTYPE: accounts the simulated API "knows". Any other address takes the "Rejoindre" branch.
    var KNOWN_EMAILS = ["agent@ndopify.com", "serge@ndopify.com"];
    // PROTOTYPE: this code simulates a wrong code; any other 6-digit code is accepted.
    var WRONG_CODE = "000000";

    var KEYS = {
        email: "npc.email",
        otpExpiry: "npc.otpExpiry",
        otpAttempts: "npc.otpAttempts",
        front: "npc.kyc.front",
        frontName: "npc.kyc.frontName",
        back: "npc.kyc.back",
        backName: "npc.kyc.backName",
        niu: "npc.kyc.niu",
        submitted: "npc.kyc.submitted"
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

    function isActive(button) {
        return button.getAttribute("aria-disabled") !== "true";
    }

    // Images are created only once there is a photo, so the markup never carries an empty <img>.
    function placeImage(container, src, className) {
        var img = $("img", container);
        if (!img) {
            img = document.createElement("img");
            img.width = 856;
            img.height = 540;
            img.alt = container.getAttribute("data-alt") || "";
            if (className) img.className = className;
            container.appendChild(img);
        }
        img.src = src;
        return img;
    }

    function maskEmail(email) {
        var parts = email.split("@");
        if (parts.length !== 2) return email;
        var name = parts[0];
        var visible = name.length <= 2 ? name.charAt(0) : name.slice(0, 2);
        return visible + "•••@" + parts[1];
    }

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

        var email = store.get(KEYS.email);
        var initials = initialsFromEmail(email);
        $("[data-user-initials]").textContent = initials;
        var emailEl = $("[data-user-email]");
        if (emailEl) {
            if (email) {
                emailEl.textContent = email;
            } else {
                emailEl.hidden = true;
            }
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

        $all("[data-logout]").forEach(function (link) {
            link.addEventListener("click", function () {
                Object.keys(KEYS).forEach(function (k) {
                    store.remove(KEYS[k]);
                });
            });
        });
    }

    /* ---------- login/index.html ---------- */
    function initLoginEmail() {
        var form = $("[data-login-form]");
        var input = $("#email");
        var error = $("#email-error");
        var submit = $("[data-submit]");
        var unknown = $("[data-unknown-email]");
        var joinHeading = $("#join-title");

        function validity() {
            var value = input.value.trim();
            if (!value) return "Saisissez votre adresse email.";
            if (!EMAIL_PATTERN.test(value)) return "Cette adresse email semble incomplète. Exemple : nom@exemple.com";
            return "";
        }

        function sync() {
            setActive(submit, validity() === "");
        }

        input.addEventListener("input", function () {
            unknown.hidden = true;
            if (input.getAttribute("aria-invalid") === "true") setFieldError(input, error, validity());
            sync();
        });

        input.addEventListener("blur", function () {
            if (input.value.trim()) setFieldError(input, error, validity());
        });

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            var message = validity();
            setFieldError(input, error, message);
            if (message) {
                input.focus();
                return;
            }
            var email = input.value.trim().toLowerCase();

            if (KNOWN_EMAILS.indexOf(email) === -1) {
                unknown.hidden = false;
                joinHeading.focus();
                return;
            }

            store.set(KEYS.email, email);
            store.set(KEYS.otpExpiry, String(Date.now() + OTP_TTL_MS));
            store.set(KEYS.otpAttempts, "0");
            window.location.href = "connection.html";
        });

        var remembered = store.get(KEYS.email);
        if (remembered && !input.value) input.value = remembered;
        sync();
    }

    /* ---------- login/connection.html ---------- */
    function initLoginCode() {
        var form = $("[data-code-form]");
        var input = $("#code");
        var error = $("#code-error");
        var submit = $("[data-submit]");
        var status = $("[data-status]");
        var expiryEl = $("[data-expiry]");
        var resend = $("[data-resend]");
        var missing = $("[data-missing-session]");
        var email = store.get(KEYS.email);
        var expiryTimer = null;

        if (!email) {
            form.hidden = true;
            $("[data-sent-to]").hidden = true;
            missing.hidden = false;
            return;
        }

        $("[data-masked-email]").textContent = maskEmail(email);

        function expiry() {
            return Number(store.get(KEYS.otpExpiry)) || 0;
        }

        function attempts() {
            return Number(store.get(KEYS.otpAttempts)) || 0;
        }

        function isExpired() {
            return Date.now() >= expiry();
        }

        function scheduleExpiry() {
            window.clearTimeout(expiryTimer);
            expiryEl.textContent = formatTime(expiry());
            var remaining = expiry() - Date.now();
            if (remaining <= 0) return;
            expiryTimer = window.setTimeout(function () {
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
            setActive(submit, validity() === "" && !isExpired() && attempts() < OTP_MAX_ATTEMPTS);
        }

        input.addEventListener("input", function () {
            var digits = input.value.replace(/\D/g, "").slice(0, 6);
            if (digits !== input.value) input.value = digits;
            if (input.getAttribute("aria-invalid") === "true" && !isExpired()) setFieldError(input, error, "");
            sync();
        });

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (attempts() >= OTP_MAX_ATTEMPTS) {
                setFieldError(input, error, "Trop d'essais pour ce code. Demandez un nouveau code ci-dessous.");
                input.focus();
                return;
            }
            if (isExpired()) {
                setFieldError(input, error, "Ce code a expiré. Demandez un nouveau code ci-dessous.");
                input.focus();
                return;
            }
            var message = validity();
            setFieldError(input, error, message);
            if (message) {
                input.focus();
                return;
            }

            if (input.value === WRONG_CODE) {
                var used = attempts() + 1;
                store.set(KEYS.otpAttempts, String(used));
                var left = OTP_MAX_ATTEMPTS - used;
                setFieldError(
                    input,
                    error,
                    left > 0
                        ? "Code incorrect. Il vous reste " + left + (left > 1 ? " essais." : " essai.")
                        : "Trop d'essais pour ce code. Demandez un nouveau code ci-dessous."
                );
                input.select();
                input.focus();
                sync();
                return;
            }

            store.remove(KEYS.otpExpiry);
            store.remove(KEYS.otpAttempts);
            window.location.href = "../kyc/index.html";
        });

        resend.addEventListener("click", function () {
            // PROTOTYPE: a real resend calls the API, which applies its own rate limit.
            store.set(KEYS.otpExpiry, String(Date.now() + OTP_TTL_MS));
            store.set(KEYS.otpAttempts, "0");
            input.value = "";
            setFieldError(input, error, "");
            scheduleExpiry();
            sync();
            announce(status, "Nouveau code envoyé à " + maskEmail(email) + ". Il est valable jusqu'à " + formatTime(expiry()) + ".");
            input.focus();
        });

        scheduleExpiry();
        if (isExpired()) setFieldError(input, error, "Ce code a expiré. Demandez un nouveau code ci-dessous.");
        sync();
    }

    /* ---------- kyc/index.html ---------- */
    function initKycStatus() {
        var params = new URLSearchParams(window.location.search);
        // PROTOTYPE: the real status comes from the API. ?statut= lets each state be reviewed.
        var statusKey = params.get("statut") || (store.get(KEYS.submitted) ? "attente" : "soumission");
        var panel = $('[data-status="' + statusKey + '"]') || $('[data-status="soumission"]');
        panel.hidden = false;
    }

    /* ---------- Wizard progress ---------- */
    function focusStep(step) {
        step.focus({ preventScroll: true });
        var progress = $(".progress");
        if (progress) progress.scrollIntoView({ block: "start" });
    }

    function setProgress(step, total) {
        var bar = $("[data-progress]");
        if (!bar) return;
        var text = "Étape " + step + " sur " + total;
        bar.setAttribute("aria-valuenow", String(step));
        bar.setAttribute("aria-valuetext", text);
        $("[data-progress-label]").textContent = text;
        $("[data-progress-bar]").style.transform = "scaleX(" + step / total + ")";
    }

    /* ---------- Image downscale for the review page ---------- */
    function toThumbnail(file, callback) {
        var reader = new FileReader();
        reader.onload = function () {
            var img = new Image();
            img.onload = function () {
                var max = 1000;
                var scale = Math.min(1, max / Math.max(img.width, img.height));
                var canvas = document.createElement("canvas");
                canvas.width = Math.round(img.width * scale);
                canvas.height = Math.round(img.height * scale);
                canvas.getContext("2d").drawImage(img, 0, 0, canvas.width, canvas.height);
                callback(canvas.toDataURL("image/jpeg", 0.8));
            };
            img.onerror = function () {
                callback(null);
            };
            img.src = reader.result;
        };
        reader.onerror = function () {
            callback(null);
        };
        reader.readAsDataURL(file);
    }

    /* ---------- kyc/verification.html ---------- */
    function initKycDocuments() {
        var params = new URLSearchParams(window.location.search);
        var returnToReview = params.get("retour") === "revue";
        var steps = $all("[data-step]");
        var current = params.get("etape") === "verso" ? 1 : 0;

        function setupUpload(step) {
            var side = step.getAttribute("data-step");
            var input = $(".upload__input", step);
            var error = $(".field__error", step);
            var preview = $("[data-preview]", step);
            var placeholder = $("[data-placeholder]", step);
            var filename = $("[data-filename]", step);
            var next = $("[data-next]", step);
            var buttonLabel = $("[data-upload-label]", step);
            var dataKey = KEYS[side];
            var nameKey = KEYS[side + "Name"];

            function showPreview(src, name) {
                placeImage(preview, src);
                placeholder.hidden = true;
                filename.textContent = name ? "Photo ajoutée : " + name : "Photo ajoutée.";
                filename.hidden = false;
                buttonLabel.textContent = "Changer la photo";
                setActive(next, true);
            }

            var saved = store.get(dataKey);
            if (saved) showPreview(saved, store.get(nameKey));
            else setActive(next, false);

            input.addEventListener("change", function () {
                var file = input.files && input.files[0];
                if (!file) return;

                if (ACCEPTED_TYPES.indexOf(file.type) === -1) {
                    setFieldError(input, error, "Format non accepté. Utilisez une photo JPG, PNG ou WEBP.");
                    input.value = "";
                    return;
                }
                if (file.size > MAX_FILE_BYTES) {
                    var size = (file.size / (1024 * 1024)).toFixed(1).replace(".", ",");
                    setFieldError(input, error, "Cette photo pèse " + size + " Mo. La taille maximale est de 5 Mo.");
                    input.value = "";
                    return;
                }

                setFieldError(input, error, "");
                toThumbnail(file, function (thumb) {
                    if (!thumb) {
                        setFieldError(input, error, "Impossible de lire cette photo. Essayez avec une autre image.");
                        return;
                    }
                    // If storage is unavailable the preview still shows; the review page then reports the gap.
                    store.set(dataKey, thumb);
                    store.set(nameKey, file.name);
                    showPreview(thumb, file.name);
                });
            });
        }

        function stepUrl(index) {
            var query = [];
            if (index === 1) query.push("etape=verso");
            if (returnToReview) query.push("retour=revue");
            return window.location.pathname + (query.length ? "?" + query.join("&") : "");
        }

        function show(index, moveFocus) {
            steps.forEach(function (step, i) {
                step.hidden = i !== index;
            });
            current = index;
            setProgress(index + 1, 4);
            if (moveFocus) focusStep(steps[index]);
        }

        // Each slide is a history entry, so the Android Back button moves between recto and verso.
        function go(index) {
            window.history.pushState({ step: index }, "", stepUrl(index));
            show(index, true);
        }

        window.addEventListener("popstate", function (event) {
            var index = event.state && typeof event.state.step === "number"
                ? event.state.step
                : new URLSearchParams(window.location.search).get("etape") === "verso" ? 1 : 0;
            show(index, true);
        });

        steps.forEach(function (step, index) {
            setupUpload(step);
            var next = $("[data-next]", step);
            var prev = $("[data-prev]", step);
            if (returnToReview) next.textContent = "Revenir à la revue";

            next.addEventListener("click", function () {
                if (!isActive(next)) {
                    $(".upload__input", step).focus();
                    return;
                }
                if (returnToReview) {
                    window.location.href = "revue.html";
                } else if (index < steps.length - 1) {
                    go(index + 1);
                } else {
                    window.location.href = "niu.html";
                }
            });

            if (prev) {
                prev.addEventListener("click", function () {
                    if (returnToReview) window.location.href = "revue.html";
                    else if (index > 0) window.history.back();
                    else window.location.href = "index.html";
                });
            }
        });

        window.history.replaceState({ step: current }, "", stepUrl(current));
        show(current, false);
    }

    /* ---------- kyc/niu.html ---------- */
    function initKycNiu() {
        var params = new URLSearchParams(window.location.search);
        var returnToReview = params.get("retour") === "revue";
        var input = $("#niu");
        var error = $("#niu-error");
        var next = $("[data-next]");
        var prev = $("[data-prev]");
        var counter = $("[data-counter]");

        setProgress(3, 4);
        if (returnToReview) {
            next.textContent = "Revenir à la revue";
            prev.href = "revue.html";
        }

        var saved = store.get(KEYS.niu);
        if (saved) input.value = saved;

        function validity() {
            var value = input.value.trim();
            if (!value) return "";
            if (!NIU_PATTERN.test(value)) return "Le NIU contient 14 caractères (lettres et chiffres). Vérifiez votre saisie ou laissez le champ vide.";
            return "";
        }

        function sync() {
            setActive(next, validity() === "");
            var length = input.value.trim().length;
            counter.textContent = length + " / 14 caractères" + (length > 0 && length < 14 ? " — il en manque " + (14 - length) : "");
        }

        input.addEventListener("input", function () {
            var cleaned = input.value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 14);
            if (cleaned !== input.value) input.value = cleaned;
            if (input.getAttribute("aria-invalid") === "true") setFieldError(input, error, validity());
            sync();
        });

        input.addEventListener("blur", function () {
            setFieldError(input, error, validity());
        });

        $("[data-niu-form]").addEventListener("submit", function (event) {
            event.preventDefault();
            var message = validity();
            setFieldError(input, error, message);
            if (message) {
                input.focus();
                return;
            }
            if (input.value.trim()) store.set(KEYS.niu, input.value.trim());
            else store.remove(KEYS.niu);
            window.location.href = "revue.html";
        });

        sync();
    }

    /* ---------- kyc/revue.html ---------- */
    function initKycReview() {
        setProgress(4, 4);
        var submit = $("[data-submit]");
        var complete = true;

        ["front", "back"].forEach(function (side) {
            var src = store.get(KEYS[side]);
            var slot = $('[data-review-image="' + side + '"]');
            var missing = $('[data-review-missing="' + side + '"]');
            if (src) {
                placeImage(slot, src, "review__thumb");
                missing.hidden = true;
            } else {
                missing.hidden = false;
                complete = false;
            }
        });

        var niu = store.get(KEYS.niu);
        $("[data-review-niu]").textContent = niu || "Non renseigné (optionnel)";

        setActive(submit, complete);
        $("[data-incomplete]").hidden = complete;

        $("[data-review-form]").addEventListener("submit", function (event) {
            event.preventDefault();
            if (!complete || !isActive(submit)) return;
            setActive(submit, false);
            submit.textContent = "Envoi en cours…";
            announce($("[data-sending]"), "Envoi de votre dossier en cours.");
            // PROTOTYPE: the real page uploads both images + NIU, then redirects on success.
            window.setTimeout(function () {
                store.set(KEYS.submitted, "1");
                ["front", "frontName", "back", "backName", "niu"].forEach(function (k) {
                    store.remove(KEYS[k]);
                });
                window.location.href = "confirmation.html";
            }, 600);
        });
    }

    /* ---------- Boot ---------- */
    var pages = {
        "login-email": initLoginEmail,
        "login-code": initLoginCode,
        "kyc-status": initKycStatus,
        "kyc-documents": initKycDocuments,
        "kyc-niu": initKycNiu,
        "kyc-review": initKycReview
    };

    document.addEventListener("DOMContentLoaded", function () {
        initUserMenu();
        var init = pages[document.body.getAttribute("data-page")];
        if (init) init();
    });
})();
