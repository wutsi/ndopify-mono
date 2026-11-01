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
        back: "npc.kyc.back",
        // Status of the submitted dossier: attente, verifie or rejete.
        selfie: "npc.kyc.selfie",
        submitted: "npc.kyc.submitted",
        // Set while a rejected dossier's documents are being sent again.
        resubmit: "npc.kyc.resubmit",
        // The agent's profile, as saved by profile/edit.html and profile/photo.html (see profile.js).
        profile: "npc.profile",
        // Ids of the mandates the agent accepted: their details (mandate.html) are unlocked.
        accepted: "npc.mandates.accepted",
        // Status and dates of each of the agent's mandates, as changed on mandate.html (see stateOf).
        mandates: "npc.mandates.state",
        // The listings of the agent's approved LISTING mandates, by id (see createListing).
        listings: "npc.listings",
        // The agent's decisions on the matches of its search mandates, by mandate then match (see matchStatesOf).
        matches: "npc.matches"
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

    /* ---------- login.html — step 1: e-mail ---------- */
    function initLoginEmail(root, onCodeSent) {
        var form = $("[data-login-form]", root);
        var input = $("#email", root);
        var error = $("#email-error", root);
        var submit = $("[data-submit]", root);
        var unknown = $("[data-unknown-email]", root);
        var joinHeading = $("#join-title", root);
        var joinLink = $("[data-join-link]", root);

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
                // rejoindre.html pre-fills the address, so the visitor doesn't type it twice.
                joinLink.setAttribute("href", "rejoindre.html?email=" + encodeURIComponent(email));
                unknown.hidden = false;
                joinHeading.focus();
                return;
            }

            store.set(KEYS.email, email);
            store.set(KEYS.otpExpiry, String(Date.now() + OTP_TTL_MS));
            store.set(KEYS.otpAttempts, "0");
            onCodeSent();
        });

        var remembered = store.get(KEYS.email);
        if (remembered && !input.value) input.value = remembered;
        sync();
    }

    /* ---------- login.html — step 2: code ---------- */
    // Listeners are bound once; enter()/leave() run each time the step is shown or hidden.
    function initLoginCode(root) {
        var form = $("[data-code-form]", root);
        var input = $("#code", root);
        var error = $("#code-error", root);
        var submit = $("[data-submit]", root);
        var status = $("[data-status]", root);
        var expiryEl = $("[data-expiry]", root);
        var resend = $("[data-resend]", root);
        var expiryTimer = null;

        function email() {
            return store.get(KEYS.email) || "";
        }

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
            window.location.href = "kyc.html";
        });

        resend.addEventListener("click", function () {
            // PROTOTYPE: a real resend calls the API, which applies its own rate limit.
            store.set(KEYS.otpExpiry, String(Date.now() + OTP_TTL_MS));
            store.set(KEYS.otpAttempts, "0");
            input.value = "";
            setFieldError(input, error, "");
            scheduleExpiry();
            sync();
            announce(status, "Nouveau code envoyé à " + maskEmail(email()) + ". Il est valable jusqu'à " + formatTime(expiry()) + ".");
            input.focus();
        });

        return {
            enter: function () {
                $("[data-masked-email]", root).textContent = maskEmail(email());
                input.value = "";
                status.textContent = "";
                setFieldError(input, error, isExpired() ? "Ce code a expiré. Demandez un nouveau code ci-dessous." : "");
                scheduleExpiry();
                sync();
            },
            leave: function () {
                window.clearTimeout(expiryTimer);
            }
        };
    }

    /*
     * login.html — both steps on one page, as slides. Each step is a history entry (?etape=code) so the
     * Back button works, but the URL is never trusted: the code step only opens while a code requested in
     * this session is pending; otherwise (direct link, new tab, after logout) the e-mail step is shown.
     */
    function initLogin() {
        var slides = { email: $('[data-step="email"]'), code: $('[data-step="code"]') };
        var code = initLoginCode(slides.code);

        function state() {
            return window.history.state || {};
        }

        function requestedStep() {
            if (state().step) return state().step;
            return new URLSearchParams(window.location.search).get("etape") === "code" ? "code" : "email";
        }

        function allowed(step) {
            return step === "code" && store.get(KEYS.email) && store.get(KEYS.otpExpiry) ? "code" : "email";
        }

        function urlFor(step) {
            return window.location.pathname + (step === "code" ? "?etape=code" : "");
        }

        function show(step, moveFocus) {
            Object.keys(slides).forEach(function (key) {
                slides[key].hidden = key !== step;
            });
            if (step === "code") code.enter();
            else code.leave();
            if (moveFocus) $("h1", slides[step]).focus();
        }

        function settle(moveFocus) {
            var step = allowed(requestedStep());
            window.history.replaceState({ step: step, depth: state().depth || 0 }, "", urlFor(step));
            show(step, moveFocus);
        }

        initLoginEmail(slides.email, function () {
            window.history.pushState({ step: "code", depth: (state().depth || 0) + 1 }, "", urlFor("code"));
            show("code", true);
        });

        $("[data-change-email]", slides.code).addEventListener("click", function () {
            if (state().depth > 0) {
                window.history.back();
            } else {
                window.history.replaceState({ step: "email", depth: 0 }, "", urlFor("email"));
                show("email", true);
            }
        });

        window.addEventListener("popstate", function () {
            settle(true);
        });
        settle(false);
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

    /* ---------- Image downscale for the review slide ---------- */
    // max: the longest side, in pixels (1000 by default).
    function toThumbnail(file, callback, max) {
        var reader = new FileReader();
        reader.onload = function () {
            var img = new Image();
            img.onload = function () {
                max = max || 1000;
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

    /*
     * kyc.html — the whole identity verification on one page, in three views:
     *   - wizard: recto → verso → selfie → revue as slides. Each slide is a history entry (?etape=verso, ?etape=revue)
     *     so the Back button works, but the URL is never trusted: a slide only opens once every earlier photo
     *     is in this session;
     *   - confirmation: right after the dossier is sent; it has no URL of its own, and a reload shows it again;
     *   - status: once a dossier has been submitted, the page shows only its status (en attente, vérifié, rejeté),
     *     whatever the URL says. A rejected dossier lands on its status, whose button reopens the wizard to send
     *     new documents; reloading keeps that wizard, but coming back to the page later shows the status again.
     */
    var KYC_STEPS = ["recto", "verso", "selfie", "revue"];
    var KYC_SELFIE = 2;
    var KYC_REVIEW = 3;
    // Photos of the dossier, in the order of their slides.
    var KYC_PHOTOS = ["front", "back", "selfie"];
    var KYC_STATUSES = ["attente", "verifie", "rejete"];

    // Photo uploader of one slide (front / back / selfie). onChange runs once a photo is stored.
    function kycUpload(step, onChange) {
        var side = step.getAttribute("data-side");
        var input = $(".upload__input", step);
        var error = $(".field__error", step);
        var preview = $("[data-preview]", step);
        var placeholder = $("[data-placeholder]", step);
        var status = $("[data-upload-status]", step);
        var buttonLabel = $("[data-upload-label]", step);
        var dataKey = KEYS[side];

        // The preview shows the photo; no file name is displayed.
        function showPreview(src) {
            placeImage(preview, src);
            placeholder.hidden = true;
            buttonLabel.textContent = "Changer la photo";
        }

        var saved = store.get(dataKey);
        if (saved) showPreview(saved);

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
                use(thumb);
            });
        });

        // Keeps a photo (data URL), whether it comes from a file or from the camera.
        function use(src) {
            setFieldError(input, error, "");
            // If storage is unavailable the preview still shows; the review slide then reports the gap.
            store.set(dataKey, src);
            showPreview(src);
            // Screen readers don't see the preview: say the photo is in.
            announce(status, preview.getAttribute("data-alt").replace("Aperçu", "Photo") + " ajoutée.");
            onChange();
        }

        return {
            input: input,
            use: use,
            done: function () {
                return !!store.get(dataKey);
            }
        };
    }

    /*
     * Selfie: live front camera in the page (getUserMedia), mirrored under an oval guide, captured to a canvas.
     * When the camera can't be used — no support, not HTTPS/localhost, permission refused, no camera — the
     * slide falls back to <input type="file" capture="user">, which opens the phone's own camera app.
     */
    var SELFIE_MAX_HEIGHT = 1000;

    function kycCamera(step, upload) {
        var preview = $("[data-preview]", step);
        var placeholder = $("[data-placeholder]", step);
        var video = $("[data-camera-video]", step);
        var guide = $("[data-camera-guide]", step);
        var start = $("[data-camera-start]", step);
        var shoot = $("[data-camera-shoot]", step);
        var fallback = $("[data-camera-fallback]", step);
        var problem = $("[data-camera-problem]", step);
        var stream = null;
        // Bumped on every stop: a camera that opens after the agent has left the slide is closed at once.
        var session = 0;

        function supported() {
            return !!(window.isSecureContext && navigator.mediaDevices && navigator.mediaDevices.getUserMedia);
        }

        function setLive(on) {
            var photo = $("img", preview);
            video.hidden = !on;
            guide.hidden = !on;
            shoot.hidden = !on;
            start.hidden = on || !fallback.hidden;
            if (photo) photo.hidden = on;
            placeholder.hidden = on || !!photo;
        }

        function stop() {
            session++;
            if (stream) {
                stream.getTracks().forEach(function (track) {
                    track.stop();
                });
            }
            stream = null;
            video.srcObject = null;
            setLive(false);
        }

        function useFallback(message) {
            stop();
            fallback.hidden = false;
            start.hidden = true;
            if (message) {
                $("[data-camera-problem-text]", problem).textContent = message;
                problem.hidden = false;
            }
        }

        function failure(error) {
            var name = error && error.name;
            if (name === "NotAllowedError" || name === "SecurityError") {
                return "Vous n'avez pas autorisé l'accès à la caméra. Autorisez-le dans les réglages de votre " +
                    "navigateur, ou prenez votre selfie avec l'appareil photo de votre téléphone.";
            }
            if (name === "NotFoundError" || name === "OverconstrainedError") {
                return "Aucune caméra n'a été trouvée. Prenez votre selfie avec l'appareil photo de votre téléphone.";
            }
            if (name === "NotReadableError") {
                return "La caméra est utilisée par une autre application. Fermez-la et réessayez, ou prenez votre " +
                    "selfie avec l'appareil photo de votre téléphone.";
            }
            return "La caméra n'a pas pu démarrer. Prenez votre selfie avec l'appareil photo de votre téléphone.";
        }

        start.addEventListener("click", function () {
            if (!isActive(start)) return; // already asking for the camera
            var mine = ++session;
            problem.hidden = true;
            setActive(start, false);
            navigator.mediaDevices
                .getUserMedia({ video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 960 } }, audio: false })
                .then(function (opened) {
                    setActive(start, true);
                    if (mine !== session) {
                        opened.getTracks().forEach(function (track) {
                            track.stop();
                        });
                        return;
                    }
                    stream = opened;
                    video.srcObject = opened;
                    setLive(true);
                    video.play().catch(function () {
                        /* autoplay attribute covers it */
                    });
                    shoot.focus();
                })
                .catch(function (error) {
                    setActive(start, true);
                    if (mine === session) useFallback(failure(error));
                });
        });

        shoot.addEventListener("click", function () {
            var vw = video.videoWidth;
            var vh = video.videoHeight;
            if (!vw || !vh) return;
            // Crop the centre of the frame to the 3:4 portrait shown in the preview (object-fit: cover).
            var sw = Math.min(vw, vh * 3 / 4);
            var sh = sw * 4 / 3;
            var scale = Math.min(1, SELFIE_MAX_HEIGHT / sh);
            var canvas = document.createElement("canvas");
            canvas.width = Math.round(sw * scale);
            canvas.height = Math.round(sh * scale);
            // Drawn as the camera sees it, not mirrored: only the live preview is flipped.
            canvas.getContext("2d").drawImage(video, (vw - sw) / 2, (vh - sh) / 2, sw, sh, 0, 0, canvas.width, canvas.height);
            stop();
            upload.use(canvas.toDataURL("image/jpeg", 0.8));
            start.textContent = "Reprendre la photo";
            $("[data-next]", step).focus();
        });

        if (!supported()) useFallback("");
        else if (upload.done()) start.textContent = "Reprendre la photo";

        // Never leave the camera on when the agent leaves the page.
        window.addEventListener("pagehide", stop);

        return {
            leave: stop,
            // Where to send the agent who presses Suivant without a selfie.
            focus: function () {
                (fallback.hidden ? start : upload.input).focus();
            }
        };
    }

    function initKycWizard() {
        var form = $("[data-kyc-form]");
        var slides = KYC_STEPS.map(function (key) {
            return $('[data-step="' + key + '"]', form);
        });
        var views = $all("[data-view]");
        var confirmation = $('[data-view="confirmation"]');
        var heading = $("h1");
        var submit = $("[data-submit]", form);
        var current = 0;

        var uploads = slides.slice(0, KYC_REVIEW).map(function (slide) {
            return kycUpload(slide, function () {
                sync();
            });
        });
        var camera = kycCamera(slides[KYC_SELFIE], uploads[KYC_SELFIE]);

        // Shows the elements of one view; data-view lists every view an element belongs to.
        function setView(name) {
            if (name !== "wizard") camera.leave();
            views.forEach(function (el) {
                el.hidden = el.getAttribute("data-view").split(" ").indexOf(name) === -1;
            });
        }

        // PROTOTYPE: the real status comes from the API. ?statut= sets the status of this session, so each
        // state can be reviewed (?statut=soumission starts over with no dossier).
        function submittedStatus() {
            var requested = new URLSearchParams(window.location.search).get("statut");
            if (requested === "soumission") {
                store.remove(KEYS.submitted);
                store.remove(KEYS.resubmit);
            } else if (KYC_STATUSES.indexOf(requested) !== -1) {
                store.set(KEYS.submitted, requested);
                store.remove(KEYS.resubmit);
            }
            var saved = store.get(KEYS.submitted);
            return KYC_STATUSES.indexOf(saved) !== -1 ? saved : null;
        }

        function showStatus(key, moveFocus) {
            setView("status");
            $all("[data-status]").forEach(function (panel) {
                panel.hidden = panel.getAttribute("data-status") !== key;
            });
            if (moveFocus) heading.focus();
        }

        function firstIncomplete() {
            for (var i = 0; i < uploads.length; i++) {
                if (!uploads[i].done()) return i;
            }
            return KYC_REVIEW;
        }

        function sync() {
            if (current === KYC_REVIEW) setActive(submit, firstIncomplete() === KYC_REVIEW);
            else setActive($("[data-next]", slides[current]), uploads[current].done());
        }

        /* History: each slide is an entry; state = { step, returning, depth } or { done, depth }. */
        function state() {
            return window.history.state || {};
        }

        function urlFor(index) {
            return window.location.pathname + (index > 0 ? "?etape=" + KYC_STEPS[index] : "");
        }

        function requestedStep() {
            if (typeof state().step === "number") return state().step;
            var index = KYC_STEPS.indexOf(new URLSearchParams(window.location.search).get("etape"));
            return index === -1 ? 0 : index;
        }

        // Never open a slide whose predecessors aren't complete, whatever the URL or history entry says.
        function allowed(index) {
            return Math.min(index, firstIncomplete());
        }

        function renderReview() {
            KYC_PHOTOS.forEach(function (side) {
                var src = store.get(KEYS[side]);
                var slot = $('[data-review-image="' + side + '"]', form);
                if (src) placeImage(slot, src, side === "selfie" ? "review__thumb review__thumb--portrait" : "review__thumb");
                $('[data-review-missing="' + side + '"]', form).hidden = !!src;
            });
            $("[data-incomplete]", form).hidden = firstIncomplete() === KYC_REVIEW;
        }

        function show(index, moveFocus) {
            setView("wizard");
            slides.forEach(function (slide, i) {
                slide.hidden = i !== index;
            });
            current = index;
            if (index !== KYC_SELFIE) camera.leave();
            setProgress(index + 1, KYC_STEPS.length);

            var next = $("[data-next]", slides[index]);
            if (next) next.textContent = state().returning ? "Revenir à la revue" : "Suivant";
            if (index === KYC_REVIEW) renderReview();
            sync();

            if (moveFocus) focusStep(slides[index]);
        }

        function go(index, returning) {
            window.history.pushState(
                { step: index, returning: !!returning, depth: (state().depth || 0) + 1 },
                "",
                urlFor(index)
            );
            show(index, true);
        }

        // Step back one history entry when there is one from this wizard, otherwise replace in place.
        function back(index) {
            if (state().depth > 0) {
                window.history.back();
            } else {
                window.history.replaceState({ step: index, returning: false, depth: 0 }, "", urlFor(index));
                show(index, true);
            }
        }

        function next(index) {
            if (!uploads[index].done()) {
                if (index === KYC_SELFIE) camera.focus();
                else uploads[index].input.focus();
                return;
            }
            if (state().returning) back(KYC_REVIEW);
            else go(index + 1);
        }

        function showConfirmation() {
            setView("confirmation");
            confirmation.focus({ preventScroll: true });
            window.scrollTo(0, 0);
        }

        function submitFile() {
            if (!isActive(submit) || firstIncomplete() !== KYC_REVIEW) return;
            setActive(submit, false);
            submit.textContent = "Envoi en cours…";
            announce($("[data-sending]", form), "Envoi de votre dossier en cours.");
            // PROTOTYPE: the real page uploads the three photos, then shows the confirmation on success.
            window.setTimeout(function () {
                store.set(KEYS.submitted, "attente");
                store.remove(KEYS.resubmit);
                KYC_PHOTOS.forEach(function (k) {
                    store.remove(KEYS[k]);
                });
                submit.textContent = "Envoyer mon dossier";
                // The confirmation replaces the review entry: it has no URL, and a reload shows it again.
                window.history.replaceState({ done: true, depth: state().depth || 0 }, "", window.location.pathname);
                showConfirmation();
            }, 600);
        }

        slides.forEach(function (slide, index) {
            var nextButton = $("[data-next]", slide);
            if (nextButton) {
                nextButton.addEventListener("click", function () {
                    next(index);
                });
            }
            // Step 1 has no "Précédent": the wizard is the start of the page. From the review, Next returns to it.
            var prevButton = $("[data-prev]", slide);
            if (prevButton) {
                prevButton.addEventListener("click", function () {
                    if (state().returning) back(KYC_REVIEW);
                    else back(index - 1);
                });
            }
        });

        $all("[data-edit]", form).forEach(function (button) {
            button.addEventListener("click", function () {
                go(KYC_STEPS.indexOf(button.getAttribute("data-edit")), true);
            });
        });

        // The confirmation's entry becomes the status: Back from the status doesn't bring the confirmation back.
        $("[data-show-status]").addEventListener("click", function () {
            window.history.replaceState({ depth: state().depth || 0 }, "", window.location.pathname);
            showStatus(submittedStatus(), true);
        });

        // A rejected dossier: the status invites the agent to send new documents, which reopens the wizard.
        $("[data-restart]").addEventListener("click", function () {
            store.set(KEYS.resubmit, "1");
            window.history.pushState({ step: 0, returning: false, depth: (state().depth || 0) + 1 }, "", urlFor(0));
            show(0, true);
        });

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (current === KYC_REVIEW) submitFile();
            else next(current);
        });

        function settle(moveFocus) {
            if (state().done && store.get(KEYS.submitted)) {
                showConfirmation();
                return;
            }
            var status = submittedStatus();
            // Only a wizard entry opened from the rejected status (or its reload) shows the wizard again.
            var resubmitting = status === "rejete" && store.get(KEYS.resubmit) && typeof state().step === "number";
            if (status && !resubmitting) {
                // The status is the page itself: drop ?etape= / ?statut= from the URL.
                window.history.replaceState({ status: true, depth: state().depth || 0 }, "", window.location.pathname);
                showStatus(status, moveFocus);
                return;
            }
            var requested = requestedStep();
            var index = allowed(requested);
            var returning = index === requested && !!state().returning;
            window.history.replaceState({ step: index, returning: returning, depth: state().depth || 0 }, "", urlFor(index));
            show(index, moveFocus);
        }

        window.addEventListener("popstate", function () {
            settle(true);
        });
        settle(false);
    }

    /* ---------- index.html — Espace agent ---------- */
    var URGENT_MS = 15 * 60 * 1000;

    var PROFILE_SAVED = {
        profile: "Votre profil a été mis à jour.",
        photo: "Votre photo de profil a été enregistrée."
    };

    function savedProfile() {
        try {
            return JSON.parse(store.get(KEYS.profile));
        } catch (e) {
            return null;
        }
    }

    // The section only shows while the photo or the biography is missing, or to confirm a save. PROTOTYPE: follows
    // the profile saved by the profile pages; until then, ?profile=complete or ?profile=empty shows the other states.
    function initProfile() {
        var section = $("[data-profile]");
        if (!section) return;
        var params = new URLSearchParams(window.location.search);
        var saved = savedProfile();
        var state = params.get("profile");
        var hasPhoto = saved ? !!saved.photo : state === "complete";
        var hasBio = saved ? !!saved.biography : state !== "empty";

        $("[data-profile-todo=photo]", section).hidden = hasPhoto;
        $("[data-profile-todo=bio]", section).hidden = hasBio;
        $("[data-profile-incomplete]", section).hidden = hasPhoto && hasBio;
        section.hidden = hasPhoto && hasBio;

        // Back from a profile page: confirm the save once, then drop the parameter so a reload doesn't repeat it.
        var message = PROFILE_SAVED[params.get("saved")];
        if (message) {
            $("[data-profile-saved-text]", section).textContent = message;
            $("[data-profile-saved]", section).hidden = false;
            section.hidden = false;
            params.delete("saved");
            var query = params.toString();
            window.history.replaceState(null, "", window.location.pathname + (query ? "?" + query : ""));
        }
    }

    function formatRemaining(ms) {
        var total = Math.max(0, Math.floor(ms / 1000));
        var h = Math.floor(total / 3600);
        var m = Math.floor((total % 3600) / 60);
        var s = total % 60;
        if (h > 0) return h + " h " + String(m).padStart(2, "0") + " min";
        if (m > 0) return m + " min " + String(s).padStart(2, "0") + " s";
        return s + " s";
    }

    function acceptedMandates() {
        try {
            return JSON.parse(store.get(KEYS.accepted)) || [];
        } catch (e) {
            return [];
        }
    }

    // mandates: the "Mes mandats" section (initMandates), where an accepted offer is added.
    function initAssignments(mandates) {
        var list = $("[data-assignments]");
        if (!list) return;
        var status = $("[data-assignments-status]");
        var section = $("[data-assignments-section]");
        // Where the focus goes once the last offer is declined: the section is gone.
        var after = $("#mandates-title");
        after.setAttribute("tabindex", "-1");

        // PROTOTYPE: ?assignments=empty shows an agent without offers.
        if (new URLSearchParams(window.location.search).get("assignments") === "empty") {
            $all("[data-assignment]", list).forEach(function (item) {
                item.remove();
            });
        }

        function pending() {
            return $all("[data-assignment]:not([data-closed])", list);
        }

        // No offer: no section at all.
        function syncEmpty() {
            section.hidden = $all("[data-assignment]", list).length === 0;
        }

        // Replaces the card's actions (and the details' timer) by an outcome message, with an optional link.
        function close(item, kind, title, text, link) {
            item.setAttribute("data-closed", kind);
            item.removeAttribute("data-urgent");
            $("[data-actions]", item).hidden = true;
            $("[data-decline-confirm]", item).hidden = true;
            $all("[data-timer]", item).forEach(function (el) {
                el.hidden = true;
            });
            var alert = document.createElement("div");
            alert.className = "alert alert--" + kind;
            var body = document.createElement("div");
            var strong = document.createElement("p");
            strong.className = "alert__title";
            strong.textContent = title;
            var p = document.createElement("p");
            p.textContent = text;
            body.appendChild(strong);
            body.appendChild(p);
            if (link) {
                var a = document.createElement("a");
                a.href = link.href;
                a.textContent = link.text;
                a.className = "alert__link";
                var row = document.createElement("p");
                row.appendChild(a);
                body.appendChild(row);
            }
            alert.appendChild(body);
            $("article", item).appendChild(alert);
        }

        function remove(item) {
            var items = $all("[data-assignment]", list);
            var next = items[items.indexOf(item) + 1] || items[items.indexOf(item) - 1];
            item.remove();
            syncEmpty();
            var target = next ? $("h3", next) : after;
            if (next) target.setAttribute("tabindex", "-1");
            target.focus();
        }

        function closeAccepted(item) {
            var id = item.getAttribute("data-mandate-id");
            close(item, "success", "Mandat accepté",
                "Le client est prévenu. Ses coordonnées et le détail du mandat sont maintenant débloqués : " +
                "contactez-le rapidement.",
                { href: "mandate.html?id=" + id, text: "Voir le mandat et contacter le client" });
        }

        $all("[data-assignment]", list).forEach(function (item) {
            var minutes = parseFloat(item.getAttribute("data-expires-in")) || 0;
            item.expiresAt = Date.now() + minutes * 60 * 1000;
            // PROTOTYPE: an offer accepted earlier in this session stays accepted after a reload.
            if (acceptedMandates().indexOf(item.getAttribute("data-mandate-id")) !== -1) closeAccepted(item);

            var actions = $("[data-actions]", item);
            var confirm = $("[data-decline-confirm]", item);
            var accept = $("[data-accept]", item);

            accept.addEventListener("click", function () {
                if (!isActive(accept)) return;
                setActive(accept, false);
                accept.textContent = "Acceptation…";
                // PROTOTYPE: simulates POST /v1/assignments/{id}/accept.
                window.setTimeout(function () {
                    var id = item.getAttribute("data-mandate-id");
                    var ids = acceptedMandates();
                    ids.push(id);
                    store.set(KEYS.accepted, JSON.stringify(ids));
                    saveState(id, { status: "ASSIGNED", acceptedAt: Date.now() });
                    if (mandates) mandates.add(id);
                    closeAccepted(item);
                    announce(status, "Mandat accepté. Les coordonnées du client sont débloquées.");
                    $("h3", item).setAttribute("tabindex", "-1");
                    $("h3", item).focus();
                }, 600);
            });

            $("[data-decline]", item).addEventListener("click", function () {
                actions.hidden = true;
                confirm.hidden = false;
                $("[data-decline-cancel]", item).focus();
            });

            $("[data-decline-cancel]", item).addEventListener("click", function () {
                confirm.hidden = true;
                actions.hidden = false;
                $("[data-decline]", item).focus();
            });

            $("[data-decline-confirmed]", item).addEventListener("click", function () {
                // PROTOTYPE: simulates POST /v1/assignments/{id}/decline.
                announce(status, "Mandat refusé. Il sera proposé à un autre agent.");
                remove(item);
            });
        });

        function tick() {
            var now = Date.now();
            pending().forEach(function (item) {
                var left = item.expiresAt - now;
                if (left <= 0) {
                    close(item, "warning", "Délai expiré", "Ce mandat a été proposé à un autre agent.");
                    announce(status, "Un mandat a expiré.");
                    return;
                }
                $all("[data-timer-value]", item).forEach(function (el) {
                    el.textContent = formatRemaining(left);
                });
                if (left < URGENT_MS) item.setAttribute("data-urgent", "");
            });
        }

        syncEmpty();
        tick();
        window.setInterval(tick, 1000);
    }

    function initAgentHome() {
        initProfile();
        initAssignments(initMandates());
    }

    /* ---------- The agent's mandates (index.html › Mes mandats, mandate.html) ---------- */

    // PROTOTYPE: the agent's mandates, as GET /v1/mandates/{id} (+ its customer) would return them. 101 to 103 are
    // the offers of index.html, the agent's once accepted; 201 to 203 are already its own, at the status of their
    // initial state (dates in days from now). "fields" is the search, as UpdateMandateRequest names it: once edited
    // (mandate/edit.html), the session's copy replaces it (see fieldsOf). Title and criteria are derived from it.
    var MANDATES = {
        101: {
            party: "client",
            contact: {
                name: "Aïcha Mbarga",
                whatsapp: "+237699123456",
                email: "aicha.mbarga@example.com",
                period: "Matin"
            },
            fields: {
                city: "Douala",
                neighbourhoods: ["Bonapriso", "Akwa"],
                propertyCategory: "RESIDENTIAL",
                propertyType: "APARTMENT",
                furnitureType: "UNFURNISHED",
                rooms: "2",
                parkings: "1",
                area: 80,
                amenities: ["dedicatedWaterMeter", "waterWell", "dedicatedElectricityMeter", "generatorSet",
                    "roadPaved", "securityGuard"],
                price: 150000,
                movingPeriod: "WITHIN_1_MONTH",
                comments: "Proche d'une école pour mes deux enfants.\nRez-de-chaussée ou premier étage."
            }
        },
        102: {
            party: "bailleur",
            contact: {
                name: "Paul Ekwalla",
                whatsapp: "+237677987654",
                email: "paul.ekwalla@example.com",
                period: "Après-midi"
            },
            fields: {
                city: "Douala",
                neighbourhoods: ["Bonanjo"],
                propertyCategory: "RESIDENTIAL",
                propertyType: "STUDIO",
                furnitureType: "FULLY_FURNISHED",
                rooms: "1",
                parkings: "0",
                area: 30,
                amenities: ["waterMeter", "electricityMeter", "trackAccessibleByCar", "securedFenced"],
                price: 75000,
                movingPeriod: "WITHIN_3_MONTHS",
                securityDeposit: 2
            }
        },
        103: {
            party: "client",
            contact: {
                name: "Marie-Claire Ndjock",
                whatsapp: "+237655443322",
                email: "mc.ndjock@example.com",
                period: "Soirée"
            },
            fields: {
                city: "Douala",
                neighbourhoods: ["Akwa", "Bonanjo", "Deïdo"],
                propertyCategory: "COMMERCIAL",
                propertyType: "OFFICE",
                furnitureType: "ANY",
                parkings: "3+",
                area: 150,
                amenities: ["dedicatedWaterMeter", "dedicatedElectricityMeter", "solarPanel", "roadPaved",
                    "securityGuard", "videoSurveillance"],
                price: 400000,
                movingPeriod: "AFTER_3_MONTHS"
            }
        },
        201: {
            party: "client",
            initial: { status: "ASSIGNED", acceptedAt: -1 },
            contact: {
                name: "Jean-Pierre Fotso",
                whatsapp: "+237670112233",
                email: "jp.fotso@example.com",
                period: "Soirée"
            },
            fields: {
                city: "Douala",
                neighbourhoods: ["Kotto", "Logpom"],
                propertyCategory: "RESIDENTIAL",
                propertyType: "HOUSE",
                furnitureType: "UNFURNISHED",
                rooms: "3",
                parkings: "2",
                area: 150,
                amenities: ["dedicatedWaterMeter", "waterTank", "dedicatedElectricityMeter", "roadPaved",
                    "securedFenced"],
                price: 250000,
                movingPeriod: "WITHIN_3_MONTHS",
                comments: "Une cour pour garer deux voitures."
            }
        },
        202: {
            party: "client",
            initial: { status: "PENDING_APPROVAL", acceptedAt: -5, requestedAt: -2 },
            contact: {
                name: "Grâce Nkoulou",
                whatsapp: "+237691554433",
                email: "grace.nkoulou@example.com",
                period: "Après-midi"
            },
            fields: {
                city: "Douala",
                neighbourhoods: ["Bonamoussadi", "Makepe"],
                propertyCategory: "RESIDENTIAL",
                propertyType: "APARTMENT",
                furnitureType: "FULLY_FURNISHED",
                rooms: "1",
                parkings: "0",
                amenities: ["waterMeter", "electricityMeter", "trackAccessibleByCar", "securityGuard"],
                price: 120000,
                movingPeriod: "WITHIN_1_MONTH"
            }
        },
        203: {
            party: "bailleur",
            initial: { status: "APPROVED", acceptedAt: -24, requestedAt: -21, approvedAt: -19 },
            contact: {
                name: "Emmanuel Tchoua",
                whatsapp: "+237677001122",
                email: "e.tchoua@example.com",
                period: "Matin"
            },
            fields: {
                city: "Douala",
                neighbourhoods: ["Bonapriso"],
                propertyCategory: "RESIDENTIAL",
                propertyType: "APARTMENT",
                furnitureType: "UNFURNISHED",
                rooms: "3",
                parkings: "1",
                area: 120,
                amenities: ["dedicatedWaterMeter", "waterWell", "dedicatedElectricityMeter", "generatorSet",
                    "roadPaved", "securityGuard", "videoSurveillance"],
                price: 300000,
                movingPeriod: "WITHIN_1_MONTH",
                // The landlord's terms (offer-property.html), copied into the listing (see createListing)
                securityDeposit: 3,
                advanceRent: 6
            }
        },
        // A signed search: the matcher found listings of the agent's inventory for it (see MATCHES).
        204: {
            party: "client",
            initial: { status: "APPROVED", acceptedAt: -12, requestedAt: -10, approvedAt: -8 },
            contact: {
                name: "Sandrine Eboa",
                whatsapp: "+237699887766",
                email: "sandrine.eboa@example.com",
                period: "Soirée"
            },
            fields: {
                city: "Douala",
                neighbourhoods: ["Bonapriso"],
                propertyCategory: "RESIDENTIAL",
                propertyType: "APARTMENT",
                furnitureType: "UNFURNISHED",
                rooms: "3",
                parkings: "1",
                amenities: ["waterTank", "generatorSet", "securedFenced"],
                price: 250000,
                movingPeriod: "WITHIN_3_MONTHS"
            }
        }
    };

    /* The search's labels, as the find-property wizard words them. "ANY" is the UI's "Indifférent". */
    var CATEGORY_LABELS = { RESIDENTIAL: "Résidentiel", COMMERCIAL: "Commercial" };
    var PROPERTY_TYPE_LABELS = {
        ROOM: "Chambre",
        STUDIO: "Studio",
        APARTMENT: "Appartement",
        HOUSE: "Maison",
        VILLA: "Villa",
        DUPLEX: "Duplex",
        RETAIL: "Boutique, local commercial",
        OFFICE: "Bureau",
        INDUSTRIAL: "Entrepôt, local industriel",
        BUILDING: "Immeuble",
        LAND: "Terrain",
        ANY: "Indifférent"
    };
    // In the title ("Locataire · Boutique"): one word.
    var PROPERTY_TYPE_SHORT = { RETAIL: "Boutique", INDUSTRIAL: "Entrepôt", ANY: "Tout type" };
    var FURNITURE_LABELS = { FULLY_FURNISHED: "Oui", UNFURNISHED: "Non", ANY: "Indifférent" };
    var PARKING_LABELS = { "0": "Aucune", "3+": "3+", ANY: "Indifférent" };
    var MOVING_LABELS = {
        WITHIN_1_MONTH: "Dans moins de 1 mois",
        WITHIN_3_MONTHS: "Dans 1 à 3 mois",
        AFTER_3_MONTHS: "Dans plus de 3 mois"
    };
    var AMENITY_GROUPS = [
        ["Accès à l'eau", [["waterMeter", "Compteur d'eau"], ["dedicatedWaterMeter", "Compteur d'eau individuel"],
            ["waterTank", "Citerne"], ["waterWell", "Forage"]]],
        ["Accès à l'électricité", [["electricityMeter", "Compteur électrique"],
            ["dedicatedElectricityMeter", "Compteur électrique individuel"], ["generatorSet", "Groupe électrogène"],
            ["solarPanel", "Panneaux solaires"]]],
        ["Accès routier", [["roadPaved", "Route bitumée"], ["trackAccessibleByCar", "Piste accessible en voiture"],
            ["trackInaccessibleByCar", "Piste accessible à pied seulement"]]],
        ["Sécurité", [["securedFenced", "Clôturé et sécurisé"], ["securityGuard", "Gardien"],
            ["videoSurveillance", "Vidéosurveillance"]]]
    ];

    // 150000 → "150K FCFA / mois".
    // A match card's rent, in thousands so the facts fit one line on a phone: "245K FCFA / mois", "152,5K FCFA / mois"
    // (the BFF's TenantFormatter.compactMoney).
    function formatCompactRent(amount) {
        var value = amount < 1000 ? String(amount)
            : (amount / 1000).toLocaleString("fr-FR", { maximumFractionDigits: 1 }) + "K";
        return value + " FCFA / mois";
    }

    // The visit's kind, as an icon: a camera for a video call, a house on site. Its words for screen readers.
    var VISIT_ICONS = {
        VIDEO: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false"><rect x="2" y="6" width="14" height="12" rx="2"/><path d="m16 10 6-3v10l-6-3z"/></svg>',
        IN_PERSON: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false"><path d="M3 11 12 4l9 7"/><path d="M5 10v10h14V10"/><path d="M10 20v-6h4v6"/></svg>'
    };

    function formatRent(amount) {
        var value = amount % 1000 === 0 ? amount / 1000 + "K" : amount.toLocaleString("fr-FR");
        return value + " FCFA / mois";
    }

    function fieldsOf(id) {
        var saved = mandateStates()[id];
        return saved && saved.fields ? saved.fields : MANDATES[id].fields;
    }

    function titleOf(id) {
        var f = fieldsOf(id);
        var type = PROPERTY_TYPE_SHORT[f.propertyType] || PROPERTY_TYPE_LABELS[f.propertyType] || "Tout type";
        return (MANDATES[id].party === "bailleur" ? "Bailleur" : "Locataire") + " · " + type;
    }

    // The search as [term, value] rows: sector, amount and date first (a card's three facts), then the rest.
    function criteriaOf(id) {
        var f = fieldsOf(id);
        var owner = MANDATES[id].party === "bailleur";
        var n = f.neighbourhoods.length;
        var rows = [
            [n > 1 ? "Secteurs" : "Secteur", (n ? f.neighbourhoods.join(", ") : "Toute la ville") + " — " + f.city],
            [owner ? "Loyer" : "Budget", formatRent(f.price)],
            [owner ? "Disponibilité" : "Emménagement", MOVING_LABELS[f.movingPeriod]],
            ["Catégorie", CATEGORY_LABELS[f.propertyCategory]],
            ["Type de propriété", PROPERTY_TYPE_LABELS[f.propertyType] || "Indifférent"]
        ];
        if (f.furnitureType) rows.push(["Meublé", FURNITURE_LABELS[f.furnitureType]]);
        if (f.rooms) rows.push(["Chambres", f.rooms]);
        if (f.parkings) rows.push(["Parking", PARKING_LABELS[f.parkings] || f.parkings]);
        if (f.area) rows.push([owner ? "Surface" : "Surface souhaitée", f.area + " m²"]);
        AMENITY_GROUPS.forEach(function (group) {
            var labels = group[1].filter(function (a) {
                return f.amenities.indexOf(a[0]) !== -1;
            }).map(function (a) {
                return a[1];
            });
            if (labels.length) rows.push([group[0], labels.join(", ")]);
        });
        if (f.comments) rows.push(["Autres précisions", f.comments]);
        return rows;
    }

    var DAY_MS = 24 * 60 * 60 * 1000;
    // Assigné, En approbation, Actif: listed in "Mes mandats". CLOSED is "Fermé" (lease signed), CANCELLED "Annulé".
    var ACTIVE_STATUSES = ["ASSIGNED", "PENDING_APPROVAL", "APPROVED"];
    // ndopify.contract.signature.ttl-days and ndopify.mandate.contract.duration-months.
    var SIGNATURE_TTL_DAYS = 7;
    var CONTRACT_MONTHS = 6;

    var STATUS_TAGS = {
        ASSIGNED: ["Assigné", "info"],
        PENDING_APPROVAL: ["En approbation", "warning"],
        APPROVED: ["Actif", "success"],
        CLOSED: ["Fermé", "neutral"],
        CANCELLED: ["Annulé", "neutral"]
    };

    // MandateCancellationCode, as the cancel form words them.
    var CANCELLATION_LABELS = {
        FOUND_INDEPENDENTLY: "trouvé sans votre aide",
        FOUND_VIA_OTHER_AGENT: "trouvé par un autre agent",
        CHANGE_OF_PLANS: "changement de projet",
        UNREALISTIC_CRITERIA: "critères irréalistes",
        UNSATISFACTORY_SERVICE: "insatisfait du service",
        OTHER: "autre raison"
    };

    var DATE_FORMAT = new Intl.DateTimeFormat("fr-FR", { day: "numeric", month: "long", year: "numeric" });
    // Where room is short (a status tag): "26 oct. 2026", as the tenant's dateFormat in the served pages.
    // The cards' dates (Mes mandats, Biens correspondants), as the BFF's tenant dateFormat: "1 nov. 2026".
    var SHORT_DATE_FORMAT = new Intl.DateTimeFormat("fr-FR", { day: "numeric", month: "short", year: "numeric" });
    var CHECK_MARK = '<svg class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false"><circle cx="10" cy="10" r="8.25" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="m6.5 10.25 2.5 2.5 4.5-5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';

    function formatDate(ms) {
        return DATE_FORMAT.format(new Date(ms));
    }

    function formatShortDate(ms) {
        return SHORT_DATE_FORMAT.format(new Date(ms));
    }

    function addMonths(ms, months) {
        var d = new Date(ms);
        d.setMonth(d.getMonth() + months);
        return d.getTime();
    }

    function signatureDeadline(state) {
        return state.requestedAt + SIGNATURE_TTL_DAYS * DAY_MS;
    }

    // Anti-bypass: an agent only sees its own mandates, the offers it accepted included.
    function ownsMandate(id) {
        var mandate = MANDATES[id];
        return !!mandate && (!!mandate.initial || acceptedMandates().indexOf(id) !== -1);
    }

    function mandateStates() {
        try {
            return JSON.parse(store.get(KEYS.mandates)) || {};
        } catch (e) {
            return {};
        }
    }

    // { status, acceptedAt, requestedAt?, approvedAt?, closedAt?, finalPrice?, cancellationCode?, cancellationReason? }
    function stateOf(id) {
        var saved = mandateStates()[id];
        if (saved) return saved;
        var initial = MANDATES[id].initial || { status: "ASSIGNED", acceptedAt: 0 };
        var state = {};
        Object.keys(initial).forEach(function (key) {
            var value = initial[key];
            state[key] = typeof value === "number" ? Date.now() + value * DAY_MS : value;
        });
        return state;
    }

    function saveState(id, state) {
        var states = mandateStates();
        states[id] = state;
        store.set(KEYS.mandates, JSON.stringify(states));
    }

    function partyWords(mandate) {
        var owner = mandate.party === "bailleur";
        return {
            the: owner ? "le bailleur" : "le client",
            The: owner ? "Le bailleur" : "Le client",
            of: owner ? "du bailleur" : "du client",
            search: owner ? "son bien" : "sa recherche",
            Search: owner ? "Son bien" : "Sa recherche"
        };
    }

    // The card's one-line next step on index.html.
    function nextStep(mandate, state, id) {
        var p = partyWords(mandate);
        if (state.status === "ASSIGNED") {
            return "Contactez " + p.the + ", puis envoyez-lui le mandat à approuver.";
        }
        if (state.status === "PENDING_APPROVAL") {
            var deadline = signatureDeadline(state);
            if (deadline < Date.now()) return "Délai de signature dépassé : renvoyez le mandat.";
            return p.The + " a jusqu'au " + formatShortDate(deadline) + " pour signer le mandat.";
        }
        if (state.status === "APPROVED") {
            // A landlord's property is let through its listing: created, completed on site, then published
            var listing = mandate.party === "bailleur" ? cardListingOf(id) : null;
            if (mandate.party === "bailleur" && !listing) return "Créez l'annonce du bien, puis visitez-le pour la compléter.";
            if (listing && listing.status === "DRAFT") {
                return listing.rejection ? "Annonce à corriger : voyez les points signalés." : "Complétez l'annonce, puis envoyez-la pour vérification.";
            }
            if (listing && listing.status === "PENDING") return "Annonce en vérification : elle sera publiée sous peu.";
            // A client's search: a visit to confirm, a listing the client likes, then new matches to send
            if (mandate.party !== "bailleur") {
                var matches = matchesOf(id);
                var visit = matches.filter(function (m) {
                    return answerOf(m) === "VISIT";
                })[0];
                if (visit) return "Confirmez au client la visite du " + formatShortDate(visit.visit.at) + " à " + visit.visit.time + (visit.visit.type === "VIDEO" ? ", en vidéo" : ", sur place") + " (annonce n° " + visit.listingId + ").";
                var liked = matches.filter(function (m) {
                    return answerOf(m) === "ACCEPTED";
                })[0];
                if (liked) return "L'annonce n° " + liked.listingId + " intéresse le client : proposez-lui une visite.";
                if (countMatches(matches, "ACTIVE") || countMatches(matches, "SHORTLISTED")) {
                    return "Envoyez au client les biens qui lui correspondent.";
                }
            }
            // Signed: what's left is the property (a client) or the tenant (a landlord), within the mandate's validity
            var find = mandate.party === "bailleur" ? "Trouvez un locataire" : "Trouvez un bien pour " + p.the;
            return find + " avant le " + formatShortDate(addMonths(state.approvedAt, CONTRACT_MONTHS)) + ".";
        }
        return "";
    }

    function setStatusTag(el, status) {
        var tag = STATUS_TAGS[status];
        el.textContent = tag[0];
        el.className = "status-tag status-tag--" + tag[1];
    }

    /* ---------- Matches: listings of the agent's inventory that fit one of its signed searches ---------- */

    // PROTOTYPE: GET /v1/matches?mandate-id= (best score first), with the listing of each. Only an APPROVED SEARCH
    // mandate has matches (the matcher runs when it's signed): 204 has them now, 201 once its signature is simulated.
    // availableIn: days from now (0: available now). fit: the partial scores of Matcher, as words, true when full.
    var MATCHES = {
        204: [
            {
                id: "9001", listingId: "3012", propertyType: "APARTMENT", rooms: 3, neighbourhood: "Bonapriso",
                price: 240000, availableIn: 31, score: 0.95,
                fit: [[true, "Dans le secteur recherché"], [true, "Dans le budget"], [true, "3 chambres, comme demandé"],
                    [false, "2 équipements sur 3 : Clôturé et sécurisé, Citerne. Manque : Groupe électrogène"],
                    [true, "Disponible avant la date d'emménagement"], [true, "Non meublé, comme demandé"]]
            },
            {
                id: "9002", listingId: "3027", propertyType: "APARTMENT", rooms: 2, neighbourhood: "Bonapriso",
                price: 200000, availableIn: 106, score: 0.745,
                fit: [[true, "Dans le secteur recherché"], [true, "Dans le budget"],
                    [false, "2 chambres, une de moins que demandé"],
                    [false, "1 équipement sur 3 : Clôturé et sécurisé. Manque : Groupe électrogène, Citerne"],
                    [false, "Disponible moins d'un mois après la date d'emménagement"],
                    [true, "Non meublé, comme demandé"]]
            },
            {
                id: "9003", listingId: "3041", propertyType: "APARTMENT", rooms: 4, neighbourhood: "Bonanjo",
                distanceKm: 1.5, price: 265000, availableIn: 0, score: 0.58,
                fit: [[false, "À 1,5 km du secteur recherché"], [false, "6 % au-dessus du budget"],
                    [false, "4 chambres, une de plus que demandé"], [true, "Les 3 équipements demandés"],
                    [true, "Disponible dès maintenant"], [true, "Non meublé, comme demandé"]]
            },
            // Sent to the client 3 days ago, answered on the public portal (ndopify-web's proposition.html).
            {
                id: "9004", listingId: "2987", propertyType: "APARTMENT", rooms: 3, neighbourhood: "Bonapriso",
                price: 245000, availableIn: 15, score: 0.9,
                initial: { status: "PROPOSED", sentAt: -3, answer: "ACCEPTED", answeredAt: -2,
                    visit: { in: 2, time: "10h00", type: "IN_PERSON" } },
                fit: [[true, "Dans le secteur recherché"], [true, "Dans le budget"], [true, "3 chambres, comme demandé"],
                    [false, "2 équipements sur 3. Manque : Groupe électrogène"],
                    [true, "Disponible avant la date d'emménagement"], [true, "Non meublé, comme demandé"]]
            },
            {
                id: "9005", listingId: "2990", propertyType: "APARTMENT", rooms: 3, neighbourhood: "Bonapriso",
                price: 270000, availableIn: 40, score: 0.62,
                initial: { status: "PROPOSED", sentAt: -3, answer: "REJECTED", answeredAt: -3, reason: "PRICE" },
                fit: [[true, "Dans le secteur recherché"], [false, "8 % au-dessus du budget"],
                    [true, "3 chambres, comme demandé"], [true, "Les 3 équipements demandés"],
                    [true, "Disponible avant la date d'emménagement"], [true, "Non meublé, comme demandé"]]
            },
            {
                id: "9006", listingId: "3002", propertyType: "APARTMENT", rooms: 3, neighbourhood: "Bonanjo",
                distanceKm: 0.8, price: 235000, availableIn: 25, score: 0.79,
                initial: { status: "PROPOSED", sentAt: -1 },
                fit: [[false, "À 0,8 km du secteur recherché"], [true, "Dans le budget"],
                    [true, "3 chambres, comme demandé"], [false, "1 équipement sur 3 : Citerne"],
                    [true, "Disponible avant la date d'emménagement"], [true, "Non meublé, comme demandé"]]
            }
        ],
        201: [
            {
                id: "9101", listingId: "3055", propertyType: "HOUSE", rooms: 3, neighbourhood: "Kotto",
                price: 230000, availableIn: 20, score: 0.88,
                fit: [[true, "Dans le secteur recherché"], [true, "Dans le budget"], [true, "3 chambres, comme demandé"],
                    [false, "4 équipements sur 5. Manque : Citerne"],
                    [true, "Disponible avant la date d'emménagement"], [true, "Non meublée, comme demandé"]]
            },
            {
                id: "9102", listingId: "3060", propertyType: "HOUSE", rooms: 4, neighbourhood: "Logpom",
                price: 260000, availableIn: 45, score: 0.67,
                fit: [[true, "Dans le secteur recherché"], [false, "4 % au-dessus du budget"],
                    [false, "4 chambres, une de plus que demandé"], [false, "3 équipements sur 5. Manque : Citerne, Route bitumée"],
                    [true, "Disponible avant la date d'emménagement"], [false, "Meublée : le client la veut vide"]]
            }
        ]
    };

    // ACTIVE: new, to look at. SHORTLISTED: kept by the agent, to send. PROPOSED: sent to the client.
    // DISMISSED: set aside, never proposed again for this mandate (the server's DISMISSED).
    // NOTE: the server has ACTIVE and DISMISSED only; SHORTLISTED and PROPOSED are the next plans' statuses.
    var MATCH_TAGS = {
        ACTIVE: ["Nouveau", "info"],
        SHORTLISTED: ["Retenu", "success"],
        PROPOSED: ["Envoyé", "neutral"]
    };
    // The client's answer to a PROPOSED match, on the public portal: interested (then maybe a visit) or not.
    // NOTE: not on the server yet either (the plan's ACCEPTED / REJECTED, and the visit request).
    var ANSWER_TAGS = {
        ACCEPTED: ["Intéressé", "success"],
        VISIT: ["Visite demandée", "warning"],
        REJECTED: ["Refusé", "neutral"]
    };
    // proposition.html's "Pas pour moi" reasons.
    var REJECTION_LABELS = {
        PRICE: "trop cher",
        LOCATION: "le quartier ne convient pas",
        SIZE: "trop petit ou trop grand",
        AMENITIES: "il manque des équipements",
        AVAILABILITY: "disponible trop tard",
        OTHER: "autre raison"
    };

    function allMatchStates() {
        try {
            return JSON.parse(store.get(KEYS.matches)) || {};
        } catch (e) {
            return {};
        }
    }

    function matchStatesOf(mandateId) {
        return allMatchStates()[mandateId] || {};
    }

    function saveMatchState(mandateId, matchId, state) {
        var all = allMatchStates();
        all[mandateId] = all[mandateId] || {};
        all[mandateId][matchId] = state;
        store.set(KEYS.matches, JSON.stringify(all));
    }

    // Only a client's signed search is matched (SEARCH, APPROVED).
    function hasMatches(mandateId) {
        return MANDATES[mandateId].party !== "bailleur" && stateOf(mandateId).status === "APPROVED";
    }

    // The mandate's matches with their status, best score first.
    function matchesOf(mandateId) {
        if (!hasMatches(mandateId)) return [];
        var states = matchStatesOf(mandateId);
        return (MATCHES[mandateId] || []).map(function (match) {
            var state = states[match.id] || initialMatchState(match);
            var copy = {};
            Object.keys(match).forEach(function (key) {
                copy[key] = match[key];
            });
            ["status", "sentAt", "answer", "answeredAt", "reason", "visit"].forEach(function (key) {
                copy[key] = state[key];
            });
            return copy;
        });
    }

    // PROTOTYPE: a match's initial state, its dates in days from now (the visit's day too).
    function initialMatchState(match) {
        var initial = match.initial;
        if (!initial) return { status: "ACTIVE" };
        var state = {};
        Object.keys(initial).forEach(function (key) {
            var value = initial[key];
            state[key] = typeof value === "number" ? Date.now() + value * DAY_MS : value;
        });
        if (initial.visit) {
            state.visit = { at: Date.now() + initial.visit.in * DAY_MS, time: initial.visit.time,
                type: initial.visit.type };
        }
        return state;
    }

    // ACCEPTED, VISIT (accepted, with a visit asked) or REJECTED; null while the client hasn't answered.
    function answerOf(match) {
        if (match.status !== "PROPOSED" || !match.answer) return null;
        return match.answer === "ACCEPTED" && match.visit ? "VISIT" : match.answer;
    }

    // The visit the client asked for: "mardi 3 novembre à 10h00, sur place".
    function visitLabel(visit) {
        var day = new Intl.DateTimeFormat("fr-FR", { weekday: "long", day: "numeric", month: "long" })
            .format(new Date(visit.at));
        return day + " à " + visit.time + (visit.type === "VIDEO" ? ", en vidéo" : ", sur place");
    }

    // The home card's proposals line, short enough for one line on a phone: what the client answered, "1 visite ·
    // 1 refusé · 1 en attente" (no total: the counts add up to it), or "". The mandate page has the details.
    function proposalsSummary(matches) {
        var sent = matches.filter(function (m) {
            return m.status === "PROPOSED";
        });
        if (!sent.length) return "";
        var counts = { VISIT: 0, ACCEPTED: 0, REJECTED: 0, NONE: 0 };
        sent.forEach(function (m) {
            counts[answerOf(m) || "NONE"]++;
        });
        var parts = [];
        if (counts.VISIT) parts.push(counts.VISIT + (counts.VISIT > 1 ? " visites" : " visite"));
        if (counts.ACCEPTED) parts.push(counts.ACCEPTED + (counts.ACCEPTED > 1 ? " intéressés" : " intéressé"));
        if (counts.REJECTED) parts.push(counts.REJECTED + (counts.REJECTED > 1 ? " refusés" : " refusé"));
        if (counts.NONE) parts.push(counts.NONE + " en attente");
        return parts.join(" · ");
    }

    function countMatches(matches, status) {
        return matches.filter(function (m) {
            return m.status === status;
        }).length;
    }

    // The home card's line: "3 nouveaux biens correspondants · 1 retenu à envoyer", or "" when nothing is to do.
    function matchesSummary(mandateId) {
        var matches = matchesOf(mandateId);
        var fresh = countMatches(matches, "ACTIVE");
        var kept = countMatches(matches, "SHORTLISTED");
        var parts = [];
        if (fresh) parts.push(fresh + (fresh > 1 ? " nouveaux biens correspondants" : " nouveau bien correspondant"));
        if (kept) parts.push(kept + (kept > 1 ? " retenus à envoyer" : " retenu à envoyer"));
        return parts.join(" · ");
    }

    function matchTitle(match) {
        var type = PROPERTY_TYPE_LABELS[match.propertyType];
        return type + " · " + match.rooms + (match.rooms > 1 ? " chambres" : " chambre");
    }

    function matchAvailability(match) {
        return match.availableIn <= 0 ? "Libre maintenant" : "Dès le " + formatShortDate(Date.now() + match.availableIn * DAY_MS);
    }

    // PROTOTYPE: the clients interested by a published listing, by mandate: its matches whose client answered
    // "intéressé" (with or without a visit), on the searches of other agents too.
    var LISTING_INTEREST = { 203: 2 };

    // "2 personnes intéressées", or "Aucune personne intéressée pour l'instant".
    function interestSummary(mandateId) {
        var n = LISTING_INTEREST[mandateId] || 0;
        if (!n) return "Aucune personne intéressée pour l'instant";
        return n + (n > 1 ? " personnes intéressées" : " personne intéressée");
    }

    // PROTOTYPE: ?listing=published shows 203 with a published listing, without going through listing.html.
    function cardListingOf(mandateId) {
        var listing = listingOf(mandateId);
        if (listing || String(mandateId) !== "203" ||
            new URLSearchParams(window.location.search).get("listing") !== "published") return listing;
        return { id: "501", mandateId: "203", status: "PUBLISHED", publishedAt: Date.now() - 5 * DAY_MS };
    }

    function initMandates() {
        var list = $("[data-mandates]");
        if (!list) return null;
        var empty = $("[data-mandates-empty]");
        var template = $("[data-mandate-template]");

        function syncEmpty() {
            var none = $all("[data-mandate-card]", list).length === 0;
            list.hidden = none;
            empty.hidden = !none;
        }

        // Title and facts too: the search may have been edited since the page was written.
        function render(item, id) {
            var state = stateOf(id);
            $("[data-mandate-link]", item).textContent = titleOf(id);
            var facts = $(".assignment__facts", item);
            facts.textContent = "";
            criteriaOf(id).slice(0, 3).forEach(function (row, i) {
                var div = document.createElement("div");
                var dt = document.createElement("dt");
                var dd = document.createElement("dd");
                dt.textContent = row[0];
                // The card names the neighbourhoods only; the city is the agent's.
                dd.textContent = i === 0 ? row[1].split(" — ")[0] : row[1];
                div.appendChild(dt);
                div.appendChild(dd);
                facts.appendChild(div);
            });
            setStatusTag($("[data-status-tag]", item), state.status);
            // A client's static card has no listing line: only 203 (here) and 102 (the template) are landlords'
            var listingLine = $("[data-listing-line]", item);
            if (listingLine) {
                var listing = cardListingOf(id);
                listingLine.hidden = !canList(id);
                if (canList(id)) fillListingLine(listingLine, id, listing, false);
                // Published: how many clients it interested
                var interest = $("[data-listing-interest]", item);
                interest.hidden = !canList(id) || !listing || listing.status !== "PUBLISHED";
                $("[data-listing-interest-text]", interest).textContent = interestSummary(id);
            }
            $("[data-next-step]", item).textContent = nextStep(MANDATES[id], state, id);
            // The matches still to handle, linking straight to the mandate's list.
            var matches = $("[data-matches]", item);
            var summary = matchesSummary(id);
            matches.hidden = !summary;
            $("[data-matches-link]", matches).textContent = summary;
            $("[data-matches-link]", matches).href = "mandate.html?id=" + encodeURIComponent(id) + "#matches-title";
            // What became of the listings sent to the client
            var proposals = $("[data-proposals]", item);
            var sent = proposalsSummary(matchesOf(id));
            proposals.hidden = !sent;
            $("[data-proposals-text]", proposals).textContent = sent;
        }

        function card(id) {
            var item = template.content.firstElementChild.cloneNode(true);
            var titleId = "mandate-" + id + "-title";
            item.setAttribute("data-mandate-id", id);
            $("article", item).setAttribute("aria-labelledby", titleId);
            $("h3", item).id = titleId;
            $("[data-mandate-link]", item).href = "mandate.html?id=" + id;
            return item;
        }

        // An accepted offer: newest first.
        function add(id) {
            if ($('[data-mandate-id="' + id + '"]', list)) return;
            var item = card(id);
            list.insertBefore(item, list.firstElementChild);
            render(item, id);
            syncEmpty();
        }

        // PROTOTYPE: ?mandates=empty shows an agent without mandates.
        if (new URLSearchParams(window.location.search).get("mandates") === "empty") {
            $all("[data-mandate-card]", list).forEach(function (item) {
                item.remove();
            });
        } else {
            acceptedMandates().forEach(add);
        }

        $all("[data-mandate-card]", list).forEach(function (item) {
            var id = item.getAttribute("data-mandate-id");
            if (ACTIVE_STATUSES.indexOf(stateOf(id).status) === -1) item.remove();
            else render(item, id);
        });
        syncEmpty();
        return { add: add };
    }

    // ContactPeriodType, as "à appeler …" ends.
    var CONTACT_PERIOD_PHRASES = { Matin: "le matin", "Après-midi": "l'après-midi", "Soirée": "le soir" };

    // "+237699123456" → "+237 6 99 12 34 56" (Cameroon numbers; others are shown as they are).
    function displayPhone(e164) {
        var m = /^\+237(\d)(\d{2})(\d{2})(\d{2})(\d{2})$/.exec(e164);
        return m ? "+237 " + m.slice(1).join(" ") : e164;
    }

    // The status wizard: Assigné > En approbation > Actif > Fermé.
    var MANDATE_STEPS = ["ASSIGNED", "PENDING_APPROVAL", "APPROVED", "CLOSED"];

    function initMandate() {
        var id = new URLSearchParams(window.location.search).get("id");
        if (!ownsMandate(id)) {
            $("[data-mandate-locked]").hidden = false;
            return;
        }
        var mandate = MANDATES[id];
        var p = partyWords(mandate);
        var c = mandate.contact;
        document.title = titleOf(id) + " — Ndopify Partner Central";
        $("[data-mandate-title]").textContent = titleOf(id);
        $all("[data-edit-link]").forEach(function (a) {
            a.href = "mandate/edit.html?id=" + encodeURIComponent(id);
        });
        $("[data-mandate-number]").textContent = id;
        $("[data-contact-title]").textContent = p.The;
        $("[data-search-title]").textContent = p.Search;
        $all("[data-party-label]").forEach(function (el) {
            el.textContent = p.the;
        });
        $all("[data-party-label-cap]").forEach(function (el) {
            el.textContent = p.The;
        });
        $all("[data-party-of]").forEach(function (el) {
            el.textContent = p.of;
        });
        $all("[data-search-label]").forEach(function (el) {
            el.textContent = p.search;
        });
        $all("[data-party-email]").forEach(function (el) {
            el.textContent = c.email;
        });
        $all("[data-signature-ttl]").forEach(function (el) {
            el.textContent = SIGNATURE_TTL_DAYS;
        });
        $("[data-approved-next]").textContent = mandate.party === "bailleur"
            ? "Créez l'annonce et visitez le bien pour la compléter ; une fois publiée, organisez les visites."
            : "Proposez des biens au client et organisez les visites.";

        $('[data-contact="name"]').textContent = c.name;
        // The number calls; its WhatsApp button writes.
        var phone = $('[data-contact="whatsapp"]');
        $("[data-contact-number]", phone).textContent = displayPhone(c.whatsapp);
        phone.href = "tel:" + c.whatsapp;
        var email = $('[data-contact="email"]');
        email.textContent = c.email;
        email.href = "mailto:" + c.email;
        $('[data-contact="period"]').textContent = CONTACT_PERIOD_PHRASES[c.period] || c.period;
        $("[data-contact-whatsapp]").href = "https://wa.me/" + c.whatsapp.replace("+", "");

        function criterion(list, row) {
            var item = document.createElement("div");
            item.className = "review__item";
            var dt = document.createElement("dt");
            dt.className = "review__term";
            dt.textContent = row[0];
            var dd = document.createElement("dd");
            dd.className = "review__value review__multiline";
            dd.textContent = row[1];
            item.appendChild(dt);
            item.appendChild(dd);
            list.appendChild(item);
        }

        // Visible: sector, amount, date, the type with its rooms ("Maison, 3 chambres"), then the client's comments.
        // Folded: everything else.
        var rows = criteriaOf(id);
        var byTerm = {};
        rows.forEach(function (row) {
            byTerm[row[0]] = row[1];
        });
        var rooms = byTerm["Chambres"];
        var key = rows.slice(0, 3).concat([["Type de bien", byTerm["Type de propriété"] +
            (rooms ? ", " + rooms + (rooms === "1" ? " chambre" : " chambres") : "")]]);
        if (byTerm["Autres précisions"]) key.push(["Autres précisions", byTerm["Autres précisions"]]);
        var rest = rows.slice(3).filter(function (row) {
            return ["Type de propriété", "Chambres", "Autres précisions"].indexOf(row[0]) === -1;
        });
        key.forEach(function (row) {
            criterion($("[data-mandate-key-criteria]"), row);
        });
        rest.forEach(function (row) {
            criterion($("[data-mandate-criteria]"), row);
        });
        var more = $("[data-mandate-more]");
        var moreLabel = $("[data-mandate-more-label]");
        more.hidden = rest.length === 0;
        function syncMore() {
            moreLabel.textContent = more.open ? "Masquer les critères" : "Voir tous les critères (" + rest.length + ")";
        }
        more.addEventListener("toggle", syncMore);
        syncMore();

        var flash = $("[data-mandate-flash]");

        function showFlash(text) {
            $("[data-mandate-flash-text]").textContent = text;
            flash.hidden = false;
            flash.focus();
        }

        function fill(selector, text) {
            $all(selector).forEach(function (el) {
                el.textContent = text;
            });
        }

        function render() {
            var state = stateOf(id);
            var closed = state.status === "CLOSED" || state.status === "CANCELLED";
            var current = closed ? MANDATE_STEPS.length - 1 : MANDATE_STEPS.indexOf(state.status);
            var dates = [state.acceptedAt, state.requestedAt, state.approvedAt, state.closedAt];

            $("[data-mandate-accepted]").textContent = formatDate(state.acceptedAt);
            setStatusTag($("[data-status-tag]"), state.status);
            $all("[data-step]").forEach(function (li, i) {
                // A mandate closed without a lease may skip steps (ex: cancelled before its approval): never done.
                var done = closed ? i === current || !!dates[i] : i < current;
                li.setAttribute("data-state", done ? "done" : i === current ? "current" : "todo");
                $(".tracker__mark", li).innerHTML = done ? CHECK_MARK : "";
                if (i === current) li.setAttribute("aria-current", "step");
                else li.removeAttribute("aria-current");
                $("[data-step-date]", li).textContent = i <= current && dates[i] ? formatDate(dates[i]) : "";
            });
            // The wizard's last step is the mandate's end: Fermé (lease signed) or Annulé.
            var label = STATUS_TAGS[state.status][0];
            $('[data-step="CLOSED"] .mandate-steps__label').textContent = state.status === "CANCELLED" ? "Annulé" : "Fermé";
            var date = dates[current];
            $("[data-step-summary]").textContent = "Étape " + (current + 1) + " sur " + MANDATE_STEPS.length + " · " +
                label + (date ? ", le " + formatDate(date) : "");

            $all("[data-panel]").forEach(function (panel) {
                panel.hidden = panel.getAttribute("data-panel") !== state.status;
            });
            if (state.requestedAt) {
                var deadline = signatureDeadline(state);
                fill("[data-requested-at]", formatDate(state.requestedAt));
                fill("[data-signature-deadline]", formatDate(deadline) + (deadline < Date.now() ? " (dépassé)" : ""));
            }
            // Edited since sent: sending it again becomes the step's main action.
            var edited = state.editedAt > state.requestedAt;
            $("[data-edited-since]").hidden = !edited;
            var resend = $('[data-panel="PENDING_APPROVAL"] [data-request-approval]');
            resend.classList.toggle("btn--primary", edited);
            resend.classList.toggle("btn--outline", !edited);
            if (state.approvedAt) {
                fill("[data-approved-at]", formatDate(state.approvedAt));
                fill("[data-valid-until]", formatDate(addMonths(state.approvedAt, CONTRACT_MONTHS)));
            }
            if (closed) {
                fill("[data-closed-at]", formatDate(state.closedAt));
            }
            if (state.status === "CLOSED") {
                var days = Math.max(0, Math.floor((state.closedAt - state.approvedAt) / DAY_MS));
                fill("[data-final-price]", state.finalPrice.toLocaleString("fr-FR") + " FCFA / mois");
                fill("[data-age]", days + (days > 1 ? " jours" : " jour"));
            }
            if (state.status === "CANCELLED") {
                fill("[data-cancellation-label]", CANCELLATION_LABELS[state.cancellationCode]);
                var reason = $("[data-cancellation-reason]");
                reason.textContent = state.cancellationReason || "";
                reason.hidden = !state.cancellationReason;
            }

            renderListing();
            renderMatches();

            $("[data-close]").hidden = closed;
            // Before its approval a mandate can't be closed with a lease (409 MANDATE_NOT_APPROVED): only cancelled.
            $('[data-close-open="close"]').hidden = state.status !== "APPROVED";
        }

        // The listing of an approved LISTING mandate: "Créer l'annonce", then its status and a link to it.
        var listingBox = $("[data-listing-actions]");
        var listingCreate = $("[data-listing-create]", listingBox);
        var listingLink = $("[data-listing-link]", listingBox);

        function renderListing() {
            var show = canList(id);
            listingBox.hidden = !show;
            if (!show) return;
            var listing = cardListingOf(id);
            var line = $("[data-listing-line]", listingBox);
            fillListingLine(line, id, listing, true);
            // Published: how many clients it interested, like the Espace agent's card
            var interest = $("[data-listing-interest]", listingBox);
            interest.hidden = !listing || listing.status !== "PUBLISHED";
            $("[data-listing-interest-text]", interest).textContent = interestSummary(id);
            listingCreate.hidden = !!listing;
            listingLink.hidden = !listing;
            var text = $("[data-listing-text]", listingBox);
            text.hidden = false;
            if (!listing) {
                // On its own mandate's page, not a link
                $("[data-listing-number]", line).removeAttribute("href");
                text.textContent = "Visitez le bien pour vérifier ses caractéristiques et prendre les photos : " +
                    "l'annonce est publiée une fois vérifiée.";
                return;
            }
            var tag = listingTag(listing);
            // Published: the line says it all, its number is the link
            if (tag === "PUBLISHED") {
                text.hidden = true;
                listingLink.hidden = true;
                return;
            }
            listingLink.href = "listing.html?id=" + encodeURIComponent(listing.id);
            listingLink.textContent = listing.status === "DRAFT"
                ? (listing.rejection ? "Corriger l'annonce" : "Continuer l'annonce") : "Voir l'annonce";
            listingLink.className = "btn " + (listing.status === "DRAFT" ? "btn--primary" : "btn--outline");
            text.textContent = tag === "PUBLISHED" ? "Annonce publiée le " + formatDate(listing.publishedAt) + "."
                : tag === "PENDING" ? "Annonce envoyée le " + formatDate(listing.submittedAt) + ", en vérification."
                    : tag === "REJECTED" ? "Annonce à corriger : " + listing.rejection.reason
                        : "Annonce en brouillon : " + (missingSections(listing).length
                            ? "encore à faire, " + missingSections(listing).join(", ") + "."
                            : "prête à être envoyée.");
        }

        // PROTOTYPE: simulates POST /v1/listings { mandateId }, then opens the listing.
        listingCreate.addEventListener("click", function () {
            if (!isActive(listingCreate)) return;
            setActive(listingCreate, false);
            listingCreate.textContent = "Création…";
            window.setTimeout(function () {
                var listing = listingOf(id) || createListing(id);
                window.location.href = "listing.html?id=" + encodeURIComponent(listing.id);
            }, 500);
        });

        /* Biens correspondants: each match is sent to the client, kept for later (Retenir) or set aside (Écarter). */
        var matchBox = $("[data-matches-section]");
        var matchList = $("[data-match-list]", matchBox);
        var matchTemplate = $("[data-match-template]");
        var matchFlash = $("[data-match-flash]", matchBox);
        var sendKept = $("[data-match-send-kept]", matchBox);

        function showMatchFlash(text) {
            $("[data-match-flash-text]", matchFlash).textContent = text;
            matchFlash.hidden = false;
        }

        function setMatch(matchId, state) {
            saveMatchState(id, matchId, state);
            renderMatches();
        }

        function fact(list, term, value, className) {
            var div = document.createElement("div");
            var dt = document.createElement("dt");
            var dd = document.createElement("dd");
            if (className) dd.className = className;
            dt.className = "visually-hidden";
            dt.textContent = term;
            dd.textContent = value;
            div.appendChild(dt);
            div.appendChild(dd);
            list.appendChild(div);
        }

        function matchItem(match) {
            var item = matchTemplate.content.firstElementChild.cloneNode(true);
            var titleId = "match-" + match.id + "-title";
            item.setAttribute("data-match-id", match.id);
            $("article", item).setAttribute("aria-labelledby", titleId);
            var title = $("[data-match-title]", item);
            title.id = titleId;
            title.textContent = matchTitle(match);
            var tag = $("[data-match-tag]", item);
            var t = MATCH_TAGS[match.status];
            var answer = answerOf(match);
            if (answer) t = ANSWER_TAGS[answer];
            tag.textContent = match.status === "PROPOSED" && !answer ? "En attente" : t[0];
            tag.className = "status-tag status-tag--" + t[1];
            // Sent: one line, the visit asked first, since it's what the agent acts on
            var answerLine = $("[data-match-answer]", item);
            answerLine.hidden = match.status !== "PROPOSED";
            if (match.status === "PROPOSED") {
                var sentText = "Envoyé le " + formatShortDate(match.sentAt);
                answerLine.textContent = answer === "ACCEPTED" ? sentText + " · intéressé le " + formatShortDate(match.answeredAt)
                    : sentText;
                // A visit: its kind as an icon (words for screen readers), then its day and time
                if (answer === "VISIT") {
                    var video = match.visit.type === "VIDEO";
                    answerLine.innerHTML = VISIT_ICONS[video ? "VIDEO" : "IN_PERSON"] +
                        '<span><span class="visually-hidden">' + (video ? "Visite en vidéo, " : "Visite sur place, ") +
                        "</span><strong></strong></span>";
                    $("strong", answerLine).textContent = "Le " + formatShortDate(match.visit.at) + " à " + match.visit.time;
                    $("strong", answerLine).parentNode.appendChild(document.createTextNode(" · " + sentText));
                }
                answerLine.classList.toggle("match__answer--visit", answer === "VISIT");
            }
            $("[data-match-ref]", item).textContent = match.listingId;
            $("[data-match-link]", item).href = "listing.html?id=" + encodeURIComponent(match.listingId);
            // One line: the terms are for screen readers only, the values say it all.
            var facts = $("[data-match-facts]", item);
            fact(facts, "Correspondance", Math.round(match.score * 100) + " %");
            fact(facts, "Quartier", match.neighbourhood + (match.distanceKm
                ? " (à " + String(match.distanceKm).replace(".", ",") + " km)" : ""));
            // In bold: what the agent looks at first
            fact(facts, "Loyer", formatCompactRent(match.price), "match__rent");
            fact(facts, "Disponibilité", matchAvailability(match));
            var fit = $("[data-match-fit]", item);
            match.fit.forEach(function (row) {
                var li = document.createElement("li");
                li.className = "match__fit-item" + (row[0] ? " match__fit-item--full" : "");
                li.innerHTML = row[0] ? CHECK_MARK : '<svg class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false"><circle cx="10" cy="10" r="8.25" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M6.5 10h7" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>';
                var span = document.createElement("span");
                span.innerHTML = '<span class="visually-hidden">' + (row[0] ? "Oui : " : "En partie : ") + "</span>";
                span.appendChild(document.createTextNode(row[1]));
                li.appendChild(span);
                fit.appendChild(li);
            });

            var actions = $("[data-match-actions]", item);
            var keep = $("[data-match-keep]", item);
            var confirm = $("[data-match-confirm]", item);
            // Sent: nothing left to do here; the client answers on the public portal (next plans).
            actions.hidden = match.status === "PROPOSED";
            keep.hidden = match.status !== "ACTIVE";

            var send = $("[data-match-send]", item);
            send.addEventListener("click", function () {
                if (!isActive(send)) return;
                // PROTOTYPE: simulates POST /v1/matches/{id}/propose: the client gets the listing's link by e-mail.
                setActive(send, false);
                send.textContent = "Envoi…";
                window.setTimeout(function () {
                    setMatch(match.id, { status: "PROPOSED", sentAt: Date.now() });
                    showMatchFlash("Annonce n° " + match.listingId + " envoyée à " + c.email + ".");
                }, 600);
            });
            keep.addEventListener("click", function () {
                // PROTOTYPE: simulates POST /v1/matches/{id}/shortlist.
                setMatch(match.id, { status: "SHORTLISTED" });
                showMatchFlash("Annonce n° " + match.listingId + " retenue : envoyez-la quand vous voulez.");
            });
            $("[data-match-dismiss]", item).addEventListener("click", function () {
                actions.hidden = true;
                confirm.hidden = false;
                $("[data-match-dismiss-confirmed]", confirm).focus();
            });
            $("[data-match-dismiss-cancel]", confirm).addEventListener("click", function () {
                confirm.hidden = true;
                actions.hidden = false;
                $("[data-match-dismiss]", item).focus();
            });
            $("[data-match-dismiss-confirmed]", confirm).addEventListener("click", function () {
                // PROTOTYPE: simulates POST /v1/matches/{id}/dismiss.
                setMatch(match.id, { status: "DISMISSED" });
                showMatchFlash("Annonce n° " + match.listingId + " écartée : elle ne vous sera plus proposée pour ce mandat.");
            });
            return item;
        }

        function renderMatches() {
            var show = hasMatches(id);
            matchBox.hidden = !show;
            if (!show) return;
            var matches = matchesOf(id);
            // Neither set aside nor refused by the client: the proposals line counts the refused ones.
            var shown = matches.filter(function (m) {
                return m.status !== "DISMISSED" && answerOf(m) !== "REJECTED";
            });
            // A visit to confirm and a listing the client likes come first, then new, kept, and those still
            // waiting for an answer; best score first within each.
            function rank(m) {
                var answer = answerOf(m);
                if (answer === "VISIT") return 0;
                if (answer === "ACCEPTED") return 1;
                return { ACTIVE: 2, SHORTLISTED: 3, PROPOSED: 4 }[m.status];
            }
            shown.sort(function (a, b) {
                return rank(a) - rank(b) || b.score - a.score;
            });
            matchList.textContent = "";
            shown.forEach(function (match) {
                matchList.appendChild(matchItem(match));
            });
            matchList.hidden = shown.length === 0;
            // None at all: those refused are still a match, the proposals line says so.
            $("[data-match-empty]", matchBox).hidden = shown.length > 0 || countMatches(matches, "PROPOSED") > 0;

            var proposals = proposalsSummary(matches);
            var proposalsLine = $("[data-match-proposals]", matchBox);
            proposalsLine.hidden = !proposals;
            $("[data-match-proposals-text]", proposalsLine).textContent = proposals;

            var kept = countMatches(matches, "SHORTLISTED");
            sendKept.hidden = kept < 2;
            sendKept.textContent = "Envoyer les " + kept + " biens retenus";

            var dismissed = countMatches(matches, "DISMISSED");
            var dismissedNote = $("[data-match-dismissed]", matchBox);
            dismissedNote.hidden = dismissed === 0;
            dismissedNote.textContent = dismissed + (dismissed > 1 ? " biens écartés" : " bien écarté") +
                " : ils ne vous seront plus proposés pour ce mandat.";
        }

        // PROTOTYPE: the kept matches in one e-mail, one POST /v1/matches/{id}/propose each.
        sendKept.addEventListener("click", function () {
            if (!isActive(sendKept)) return;
            setActive(sendKept, false);
            sendKept.textContent = "Envoi…";
            window.setTimeout(function () {
                var kept = matchesOf(id).filter(function (m) {
                    return m.status === "SHORTLISTED";
                });
                kept.forEach(function (m) {
                    saveMatchState(id, m.id, { status: "PROPOSED", sentAt: Date.now() });
                });
                setActive(sendKept, true);
                renderMatches();
                showMatchFlash(kept.length + " annonces envoyées à " + c.email + ".");
            }, 600);
        });

        function update(changes) {
            var state = stateOf(id);
            Object.keys(changes).forEach(function (key) {
                state[key] = changes[key];
            });
            saveState(id, state);
            render();
        }

        // Approuver / Renvoyer: PROTOTYPE, simulates POST /v1/mandates/{id}/request-approval.
        $all("[data-request-approval]").forEach(function (button) {
            var label = button.textContent;
            button.addEventListener("click", function () {
                if (!isActive(button)) return;
                var again = stateOf(id).status === "PENDING_APPROVAL";
                setActive(button, false);
                button.textContent = "Envoi…";
                window.setTimeout(function () {
                    update({ status: "PENDING_APPROVAL", requestedAt: Date.now() });
                    setActive(button, true);
                    button.textContent = label;
                    showFlash((again ? "Mandat renvoyé à " : "Mandat envoyé à ") + c.email + " pour signature.");
                }, 600);
            });
        });

        // PROTOTYPE: the client signs the contract (ContractService.sign → MandateService.onContractSigned).
        $("[data-simulate-signature]").addEventListener("click", function () {
            update({ status: "APPROVED", approvedAt: Date.now() });
            showFlash(p.The + " a signé le mandat : il est maintenant actif.");
        });

        /* Fermer (close, lease signed) and Annuler (cancel): one form, in the mode of the button that opened it. */
        var CLOSE_MODES = {
            close: { title: "Fermer le mandat", submit: "Confirmer la fermeture", style: "btn--primary" },
            cancel: { title: "Annuler le mandat", submit: "Confirmer l'annulation", style: "btn--danger" }
        };
        var closeBox = $("[data-close]");
        var buttons = $("[data-close-buttons]", closeBox);
        var form = $("[data-close-form]", closeBox);
        var submit = $("[data-close-submit]", form);
        var price = $("#finalPrice", form);
        var priceError = $("#finalPrice-error", form);
        var codes = $all('[name="cancellationCode"]', form);
        var codeError = $("#cancellationCode-error", form);
        var mode = null;
        var opener = null;

        function clearErrors() {
            setFieldError(price, priceError, null);
            setFieldError(codes[0], codeError, null);
        }

        function openForm(button) {
            opener = button;
            mode = button.getAttribute("data-close-open");
            var m = CLOSE_MODES[mode];
            $("[data-close-title]", form).textContent = m.title;
            submit.textContent = m.submit;
            submit.className = "btn " + m.style;
            $all("[data-for-mode]", form).forEach(function (el) {
                el.hidden = el.getAttribute("data-for-mode") !== mode;
            });
            buttons.hidden = true;
            button.setAttribute("aria-expanded", "true");
            form.hidden = false;
            $("[data-for-mode]:not([hidden]) input", form).focus();
        }

        function closeForm(returnFocus) {
            form.hidden = true;
            form.reset();
            clearErrors();
            buttons.hidden = false;
            if (opener) opener.setAttribute("aria-expanded", "false");
            if (returnFocus && opener) opener.focus();
            mode = null;
        }

        $all("[data-close-open]", buttons).forEach(function (button) {
            button.addEventListener("click", function () {
                openForm(button);
            });
        });

        $("[data-close-back]", form).addEventListener("click", function () {
            closeForm(true);
        });

        price.addEventListener("input", function () {
            setFieldError(price, priceError, null);
        });

        codes.forEach(function (radio) {
            radio.addEventListener("change", function () {
                setFieldError(codes[0], codeError, null);
            });
        });

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (!isActive(submit)) return;
            var cancel = mode === "cancel";
            var changes = { status: cancel ? "CANCELLED" : "CLOSED", closedAt: Date.now() };

            if (cancel) {
                var code = $('[name="cancellationCode"]:checked', form);
                if (!code) {
                    setFieldError(codes[0], codeError, "Choisissez la raison de l'annulation.");
                    codes[0].focus();
                    return;
                }
                changes.cancellationCode = code.value;
                changes.cancellationReason = $("#cancellationReason", form).value.trim();
            } else {
                var digits = price.value.replace(/[\s.]/g, "");
                var amount = parseInt(digits, 10);
                if (!/^\d+$/.test(digits) || !(amount > 0)) {
                    setFieldError(price, priceError, "Indiquez le loyer convenu, en FCFA par mois.");
                    price.focus();
                    return;
                }
                changes.finalPrice = amount;
            }

            // PROTOTYPE: simulates POST /v1/mandates/{id}/close (finalPrice) or /cancel (cancellationCode, reason).
            setActive(submit, false);
            submit.textContent = cancel ? "Annulation…" : "Fermeture…";
            window.setTimeout(function () {
                setActive(submit, true);
                closeForm(false);
                update(changes);
                showFlash(cancel
                    ? "Mandat annulé. Il ne figure plus dans vos mandats en cours."
                    : "Mandat fermé : félicitations pour ce bail !");
            }, 600);
        });

        render();
        $("[data-mandate]").hidden = false;

        var params = new URLSearchParams(window.location.search);
        if (params.get("saved")) {
            showFlash(stateOf(id).status === "PENDING_APPROVAL"
                ? "Mandat modifié. Renvoyez-le pour que " + p.the + " signe la nouvelle version."
                : "Mandat modifié.");
            params.delete("saved");
            window.history.replaceState(null, "", window.location.pathname + "?" + params.toString());
        }
    }

    /* ---------- mandate/edit.html — edit a mandate before its approval ---------- */

    var EDITABLE_STATUSES = ["ASSIGNED", "PENDING_APPROVAL"];
    var MAX_NEIGHBOURHOODS = 3;
    var AMENITY_KEYS = [];
    AMENITY_GROUPS.forEach(function (group) {
        group[1].forEach(function (a) {
            AMENITY_KEYS.push(a[0]);
        });
    });

    // "150 000" → 150000; null when empty, NaN when not a positive whole number.
    function parseAmount(value) {
        var digits = value.replace(/[\s.]/g, "");
        if (!digits) return null;
        return /^\d+$/.test(digits) && parseInt(digits, 10) > 0 ? parseInt(digits, 10) : NaN;
    }

    function initMandateEdit() {
        var id = new URLSearchParams(window.location.search).get("id");
        $all("[data-back-link]").forEach(function (a) {
            a.href = "../mandate.html?id=" + encodeURIComponent(id || "");
        });
        if (!ownsMandate(id)) {
            $("[data-edit-locked]").hidden = false;
            return;
        }
        var state = stateOf(id);
        // Source doc: no edit once approved.
        if (EDITABLE_STATUSES.indexOf(state.status) === -1) {
            $("[data-edit-locked-title]").textContent = "Ce mandat ne peut plus être modifié";
            $("[data-edit-locked-text]").textContent = "Un mandat approuvé par le client ne se modifie plus.";
            $("[data-edit-locked]").hidden = false;
            return;
        }

        var mandate = MANDATES[id];
        var owner = mandate.party === "bailleur";
        var f = fieldsOf(id);
        var form = $("[data-edit-form]");
        var submit = $("[data-edit-submit]", form);

        document.title = "Modifier le mandat " + id + " — Ndopify Partner Central";
        $("[data-edit-title]").textContent = titleOf(id);
        $("[data-edit-number]").textContent = id;
        $("[data-city]").textContent = f.city;
        $("[data-party-of]").textContent = partyWords(mandate).of;
        $("[data-edit-resend]").hidden = state.status !== "PENDING_APPROVAL";

        // A client's search or a landlord's property: wording, and no "Indifférent" for a property that exists.
        $all("[data-label-tenant]").forEach(function (el) {
            el.textContent = el.getAttribute(owner ? "data-label-owner" : "data-label-tenant");
        });
        if (owner) {
            $all("[data-tenant-only]", form).forEach(function (el) {
                el.remove();
            });
        }

        function field(name) {
            return form.elements[name];
        }

        function checked(name) {
            var input = $('[name="' + name + '"]:checked', form);
            return input ? input.value : null;
        }

        function check(name, value) {
            var input = value == null ? null : $('[name="' + name + '"][value="' + value + '"]', form);
            if (input) input.checked = true;
        }

        /* Pre-fill */
        f.neighbourhoods.forEach(function (n) {
            check("neighbourhoods", n);
        });
        check("propertyCategory", f.propertyCategory);
        check("propertyType", f.propertyType);
        check("rooms", f.rooms);
        check("furnitureType", f.furnitureType);
        check("parkings", f.parkings);
        field("area").value = f.area || "";
        f.amenities.forEach(function (key) {
            field(key).checked = true;
        });
        field("price").value = f.price.toLocaleString("fr-FR");
        check("movingPeriod", f.movingPeriod);
        field("comments").value = f.comments || "";

        /* Dependent fields */
        var counter = $("[data-neighbourhoods-counter]", form);

        // At most 3 neighbourhoods: once 3 are chosen, the others wait for one to be removed.
        function syncNeighbourhoods() {
            var boxes = $all('[name="neighbourhoods"]', form);
            var count = boxes.filter(function (b) {
                return b.checked;
            }).length;
            boxes.forEach(function (b) {
                b.disabled = !b.checked && count >= MAX_NEIGHBOURHOODS;
            });
            counter.textContent = count + " sur " + MAX_NEIGHBOURHOODS + (count > 1 ? " sélectionnés" : " sélectionné");
        }

        // The types of the category; rooms for a home, furniture and parking for anything but a plot of land.
        function syncProperty() {
            var category = checked("propertyCategory");
            $all('[name="propertyType"]', form).forEach(function (input) {
                var label = input.closest("label");
                var fits = label.getAttribute("data-categories").split(" ").indexOf(category) !== -1;
                label.hidden = !fits;
                if (!fits) input.checked = false;
            });
            var type = checked("propertyType");
            var land = type === "LAND";
            $('[data-group="rooms"]', form).hidden = category !== "RESIDENTIAL" || land;
            $('[data-group="furnitureType"]', form).hidden = land;
            $('[data-group="parkings"]', form).hidden = land;
        }

        function visible(name) {
            return !$('[data-group="' + name + '"]', form).hidden;
        }

        /* Each folded group shows its current value. */
        var SUMMARIES = {
            sector: function () {
                var names = $all('[name="neighbourhoods"]:checked', form).map(function (b) {
                    return b.value;
                });
                return names.length ? names.join(", ") : "Toute la ville";
            },
            property: function () {
                var parts = [PROPERTY_TYPE_LABELS[checked("propertyType")] || CATEGORY_LABELS[checked("propertyCategory")]];
                var rooms = visible("rooms") && checked("rooms");
                if (rooms) parts.push(rooms + (rooms === "1" ? " chambre" : " chambres"));
                var area = parseAmount(field("area").value);
                if (area > 0) parts.push(area + " m²");
                return parts.join(" · ");
            },
            amenities: function () {
                var count = AMENITY_KEYS.filter(function (key) {
                    return field(key).checked;
                }).length;
                return count ? count + (count > 1 ? " critères" : " critère") : "Aucun critère";
            },
            budget: function () {
                var price = parseAmount(field("price").value);
                var parts = [price > 0 ? formatRent(price) : "Montant à indiquer"];
                if (checked("movingPeriod")) parts.push(MOVING_LABELS[checked("movingPeriod")]);
                return parts.join(" · ");
            },
            comments: function () {
                var text = field("comments").value.trim();
                return text ? (text.length > 40 ? text.slice(0, 40) + "…" : text) : "Aucune";
            }
        };

        function syncSummaries() {
            $all("[data-edit-group]", form).forEach(function (group) {
                $("[data-group-value]", group).textContent = SUMMARIES[group.getAttribute("data-edit-group")]();
            });
        }

        /* Errors */
        var ERRORS = {
            price: "Indiquez " + (owner ? "le loyer" : "le budget") + ", en FCFA par mois.",
            movingPeriod: owner ? "Indiquez quand le bien est disponible." : "Indiquez la date d'emménagement.",
            rooms: "Indiquez le nombre de chambres.",
            furnitureType: "Indiquez si le bien est meublé.",
            area: "Indiquez la surface en m², ou laissez le champ vide."
        };

        function errorTarget(name) {
            return $('[name="' + name + '"]', form);
        }

        function clearError(name) {
            setFieldError(errorTarget(name), $("#" + name + "-error", form), null);
        }

        function validate() {
            var invalid = [];
            if (!(parseAmount(field("price").value) > 0)) invalid.push("price");
            if (!checked("movingPeriod")) invalid.push("movingPeriod");
            if (visible("rooms") && !checked("rooms")) invalid.push("rooms");
            if (visible("furnitureType") && !checked("furnitureType")) invalid.push("furnitureType");
            if (isNaN(parseAmount(field("area").value))) invalid.push("area");
            Object.keys(ERRORS).forEach(function (name) {
                setFieldError(errorTarget(name), $("#" + name + "-error", form),
                    invalid.indexOf(name) !== -1 ? ERRORS[name] : null);
            });
            return invalid;
        }

        form.addEventListener("change", function (event) {
            var name = event.target.name;
            if (name === "neighbourhoods") syncNeighbourhoods();
            if (name === "propertyCategory" || name === "propertyType") syncProperty();
            if (ERRORS[name]) clearError(name);
            syncSummaries();
        });

        form.addEventListener("input", function (event) {
            if (ERRORS[event.target.name]) clearError(event.target.name);
            syncSummaries();
        });

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (!isActive(submit)) return;
            var invalid = validate();
            if (invalid.length) {
                // The first error's group is unfolded, so the agent sees what to fix.
                var target = errorTarget(invalid[0]);
                target.closest("details").open = true;
                target.focus();
                return;
            }

            var area = parseAmount(field("area").value);
            var fields = {
                city: f.city,
                neighbourhoods: $all('[name="neighbourhoods"]:checked', form).map(function (b) {
                    return b.value;
                }),
                propertyCategory: checked("propertyCategory"),
                propertyType: checked("propertyType"),
                furnitureType: visible("furnitureType") ? checked("furnitureType") : null,
                rooms: visible("rooms") ? checked("rooms") : null,
                parkings: visible("parkings") ? checked("parkings") : null,
                area: area || null,
                amenities: AMENITY_KEYS.filter(function (key) {
                    return field(key).checked;
                }),
                price: parseAmount(field("price").value),
                movingPeriod: checked("movingPeriod"),
                comments: field("comments").value.trim() || null
            };

            // PROTOTYPE: simulates POST /v1/mandates/{id} with the whole search (UpdateMandateRequest).
            setActive(submit, false);
            submit.textContent = "Enregistrement…";
            window.setTimeout(function () {
                var current = stateOf(id);
                current.fields = fields;
                current.editedAt = Date.now();
                saveState(id, current);
                window.location.href = "../mandate.html?id=" + encodeURIComponent(id) + "&saved=1";
            }, 600);
        });

        syncNeighbourhoods();
        syncProperty();
        syncSummaries();
        $("[data-edit]").hidden = false;
    }


    /*
     * ---------- listing.html, listing/*.html — the listing of an approved LISTING mandate ----------
     * The agent creates it from the mandate (POST /v1/listings: a DRAFT, pre-filled from the landlord's answers),
     * visits the property, checks each section of the checklist (POST /v1/listings/{id}) and adds the photos
     * (POST /v1/listings/{id}/images/upload), then sends it to be checked. Once checked, it's published, its title,
     * summary and description written by AI from the audit and the photos: the agent never writes them.
     * PROTOTYPE: ndopify-server has neither a submit nor a publish endpoint yet, nor any check that the mandate is an
     * approved LISTING one; "PENDING" stands for the listing being checked.
     */
    var LISTING_SECTIONS = [
        { key: "location", label: "Emplacement", page: "emplacement.html" },
        { key: "gps", label: "Position GPS", page: "position.html" },
        { key: "property", label: "Le bien", page: "bien.html" },
        { key: "amenities", label: "Eau, électricité, accès", page: "equipements.html" },
        { key: "terms", label: "Loyer et conditions", page: "conditions.html" },
        { key: "photos", label: "Photos", page: "photos.html" },
        { key: "remarks", label: "Vos remarques", page: "remarques.html", optional: true }
    ];
    var MIN_LISTING_PHOTOS = 5;
    var MAX_LISTING_PHOTOS = 20;
    // A DRAFT sent back by the check is "À corriger" (see listingTag).
    var LISTING_TAGS = {
        // No listing yet: the mandate's line only (index.html's card, mandate.html's panel)
        NONE: ["À créer", "neutral"],
        DRAFT: ["Brouillon", "info"],
        REJECTED: ["À corriger", "warning"],
        PENDING: ["En vérification", "warning"],
        PUBLISHED: ["Publiée", "success"]
    };
    var LISTING_FURNITURE_LABELS = { FULLY_FURNISHED: "Meublé", UNFURNISHED: "Non meublé" };
    var ROAD_ACCESS_KEYS = ["roadPaved", "trackAccessibleByCar", "trackInaccessibleByCar"];
    var SECTION_SAVED = {
        location: "Emplacement enregistré.",
        gps: "Position GPS enregistrée.",
        property: "Caractéristiques du bien enregistrées.",
        amenities: "Équipements enregistrés.",
        terms: "Loyer et conditions enregistrés.",
        remarks: "Remarques enregistrées."
    };

    function listings() {
        try {
            return JSON.parse(store.get(KEYS.listings)) || {};
        } catch (e) {
            return {};
        }
    }

    // false when the session is full (photos)
    function saveListing(listing) {
        var all = listings();
        all[listing.id] = listing;
        return store.set(KEYS.listings, JSON.stringify(all));
    }

    // A mandate has one listing at most (409 LISTING_ALREADY_EXISTS).
    function listingOf(mandateId) {
        var all = listings();
        var id = Object.keys(all).filter(function (key) {
            return all[key].mandateId === String(mandateId);
        })[0];
        return id ? all[id] : null;
    }

    // Only an approved LISTING mandate (a landlord's) gets a listing.
    function canList(mandateId) {
        return ownsMandate(mandateId) && MANDATES[mandateId].party === "bailleur" &&
            stateOf(mandateId).status === "APPROVED";
    }

    function listingTag(listing) {
        return listing.status === "DRAFT" && listing.rejection ? "REJECTED" : listing.status;
    }

    // The listing's line of a landlord's approved mandate ([data-listing-line]): "Annonce n° 501" and its status tag,
    // "Annonce" "À créer" before it's created; just "Annonce" on index.html's card (numbered false), so that it fits
    // one line on a phone with its tag. [data-listing-number] is a link: the listing, or the mandate (where it's
    // created) on index.html's card.
    function fillListingLine(line, mandateId, listing, numbered) {
        var tag = listing ? listingTag(listing) : "NONE";
        var number = $("[data-listing-number]", line);
        number.textContent = listing && numbered ? "Annonce n° " + listing.id : "Annonce";
        if (number.tagName === "A") {
            number.href = listing ? "listing.html?id=" + encodeURIComponent(listing.id)
                : "mandate.html?id=" + encodeURIComponent(mandateId);
        }
        var tagEl = $("[data-listing-status-tag]", line);
        tagEl.textContent = LISTING_TAGS[tag][0] +
            (tag === "PUBLISHED" ? " le " + formatShortDate(listing.publishedAt) : "");
        tagEl.className = "status-tag status-tag--" + LISTING_TAGS[tag][1];
    }

    // A mandate's count ("2", or "3+" for 3 or more) as a number, as ListingService.create does: 3
    function countOf(value) {
        var n = parseInt(value, 10);
        return isNaN(n) ? null : n;
    }

    function isoDate(ms) {
        var d = new Date(ms);
        return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" +
            String(d.getDate()).padStart(2, "0");
    }

    function plural(n, one, many) {
        return n + " " + (n > 1 ? many : one);
    }

    // PROTOTYPE: simulates POST /v1/listings { mandateId }. "owner" keeps the landlord's answers, so that each section
    // shows what the agent changed.
    function createListing(mandateId) {
        var f = fieldsOf(mandateId);
        var fields = {
            city: f.city,
            neighbourhood: f.neighbourhoods[0] || "",
            landmark: "",
            accessDirections: "",
            latitude: null,
            longitude: null,
            // Not in UpdateListingRequest: shown next to the position only
            accuracy: null,
            propertyCategory: f.propertyCategory,
            propertyType: f.propertyType,
            furnitureType: f.furnitureType || null,
            rooms: countOf(f.rooms),
            bathrooms: null,
            livingRooms: null,
            kitchens: null,
            parkings: countOf(f.parkings),
            area: f.area || null,
            levels: null,
            floorLevel: null,
            amenities: f.amenities.slice(),
            price: f.price,
            securityDeposit: f.securityDeposit || null,
            advanceRent: f.advanceRent || null,
            leaseTerm: null,
            noticePeriod: null,
            // PROTOTYPE: the mandates of this prototype have no availableFrom (V7_2)
            availableFrom: f.availableFrom || isoDate(Date.now() + 14 * DAY_MS),
            agentRemarks: ""
        };
        var ids = Object.keys(listings()).map(Number);
        var listing = {
            id: ids.length ? Math.max.apply(null, ids) + 1 : 501,
            mandateId: String(mandateId),
            status: "DRAFT",
            createdAt: Date.now(),
            fields: fields,
            owner: JSON.parse(JSON.stringify(fields)),
            // When each section was last saved: a section counts once the agent has checked it on site
            checked: {},
            // { id, src, status: PENDING_VERIFICATION | VERIFIED | REJECTED, errorMessage? }, oldest first
            photos: []
        };
        saveListing(listing);
        return listing;
    }

    function goodPhotos(listing) {
        return listing.photos.filter(function (photo) {
            return photo.status !== "REJECTED";
        }).length;
    }

    function sectionDone(listing, key) {
        // A photo refused by the check is deleted (or replaced) before sending again
        if (key === "photos") return goodPhotos(listing) >= MIN_LISTING_PHOTOS && goodPhotos(listing) === listing.photos.length;
        if (key === "remarks") return true;
        return !!listing.checked[key];
    }

    // What's left before sending: "Emplacement, 2 photos".
    function missingSections(listing) {
        return LISTING_SECTIONS.filter(function (s) {
            return !s.optional && !sectionDone(listing, s.key);
        }).map(function (s) {
            if (s.key !== "photos") return s.label;
            var refused = listing.photos.length - goodPhotos(listing);
            var short = MIN_LISTING_PHOTOS - goodPhotos(listing);
            return [refused ? (refused > 1 ? "les photos refusées" : "la photo refusée") : null,
                short > 0 ? plural(short, "photo", "photos") : null].filter(Boolean).join(", ");
        });
    }

    function listingTitle(listing) {
        var f = listing.fields;
        var type = PROPERTY_TYPE_SHORT[f.propertyType] || PROPERTY_TYPE_LABELS[f.propertyType] || "Bien";
        return type + ", " + (f.neighbourhood || f.city);
    }

    function amenityLabels(keys) {
        var labels = [];
        AMENITY_GROUPS.forEach(function (group) {
            group[1].forEach(function (a) {
                if (keys.indexOf(a[0]) !== -1) labels.push(a[1]);
            });
        });
        return labels;
    }

    // The checklist row's one line: the section's main values.
    function sectionSummary(listing, key) {
        var f = listing.fields;
        switch (key) {
            case "location":
                return [f.neighbourhood, f.landmark || "Point de repère à noter"].join(" · ");
            case "gps":
                return f.latitude != null
                    ? f.latitude.toFixed(5) + ", " + f.longitude.toFixed(5) + (f.accuracy ? " (± " + f.accuracy + " m)" : "")
                    : "À relever devant l'entrée du bien";
            case "property":
                return [PROPERTY_TYPE_LABELS[f.propertyType], f.rooms != null ? plural(f.rooms, "chambre", "chambres") : null,
                    f.area ? f.area + " m²" : null].filter(Boolean).join(" · ");
            case "amenities":
                var road = f.amenities.filter(function (a) {
                    return ROAD_ACCESS_KEYS.indexOf(a) !== -1;
                });
                var others = f.amenities.length - road.length;
                return [road.length ? amenityLabels(road)[0] : "Accès à préciser",
                    others ? plural(others, "équipement", "équipements") : "aucun équipement"].join(" · ");
            case "terms":
                return formatRent(f.price) +
                    (f.securityDeposit ? " · caution " + f.securityDeposit + " mois" : "");
            case "photos":
                var n = goodPhotos(listing);
                return (n ? plural(n, "photo", "photos") : "Aucune photo") +
                    (n < MIN_LISTING_PHOTOS ? " · " + MIN_LISTING_PHOTOS + " au moins" : "");
            default:
                return f.agentRemarks || "Aucune remarque";
        }
    }

    // [label, tag style] of a checklist row.
    function sectionState(listing, key) {
        var done = sectionDone(listing, key);
        if (key === "remarks") return listing.fields.agentRemarks ? ["Ajoutées", "success"] : ["Facultatif", "neutral"];
        if (done) return ["Vérifié", "success"];
        if (listing.rejection && listing.rejection.sections.indexOf(key) !== -1) return ["À corriger", "warning"];
        return [key === "photos" ? "À compléter" : "À vérifier", "warning"];
    }

    function initListing() {
        var params = new URLSearchParams(window.location.search);
        var id = params.get("id");
        var listing = listings()[id];
        if (!listing || !ownsMandate(listing.mandateId)) {
            $("[data-listing-locked]").hidden = false;
            return;
        }
        var mandateLink = "mandate.html?id=" + encodeURIComponent(listing.mandateId);
        $all("[data-mandate-link]").forEach(function (a) {
            a.href = mandateLink;
        });
        $("[data-listing-mandate]").textContent = listing.mandateId;
        $("[data-listing-created]").textContent = formatShortDate(listing.createdAt);

        var flash = $("[data-listing-flash]");
        var list = $("[data-checklist]");
        var template = $("[data-checklist-template]");
        var submit = $("[data-listing-submit]");
        var missing = $("[data-listing-missing]");

        function showFlash(text) {
            $("[data-listing-flash-text]").textContent = text;
            flash.hidden = false;
            flash.focus();
        }

        function render() {
            listing = listings()[id];
            var tag = listingTag(listing);
            var editable = listing.status === "DRAFT";
            document.title = "Annonce · " + listingTitle(listing) + " — Ndopify Partner Central";
            $("[data-listing-title]").textContent = "Annonce · " + listingTitle(listing);
            var tagEl = $("[data-listing-tag]");
            tagEl.textContent = LISTING_TAGS[tag][0] +
                (listing.status === "PUBLISHED" ? " le " + formatShortDate(listing.publishedAt) : "");
            tagEl.className = "status-tag status-tag--" + LISTING_TAGS[tag][1];

            $all("[data-listing-panel]").forEach(function (panel) {
                panel.hidden = panel.getAttribute("data-listing-panel") !== tag;
            });
            if (listing.rejection) $("[data-rejection-reason]").textContent = listing.rejection.reason;
            if (listing.submittedAt) $("[data-submitted-at]").textContent = formatDate(listing.submittedAt);
            // PROTOTYPE: no public portal here, the link goes nowhere
            $("[data-listing-public]").hidden = listing.status !== "PUBLISHED";

            var done = LISTING_SECTIONS.filter(function (s) {
                return !s.optional && sectionDone(listing, s.key);
            }).length;
            $("[data-checklist-progress]").textContent = done + " sections sur " +
                (LISTING_SECTIONS.length - 1) + " vérifiées";

            // Each row opens its section: to edit it while a draft, to view it (read-only) once sent
            list.textContent = "";
            LISTING_SECTIONS.forEach(function (s) {
                var item = template.content.firstElementChild.cloneNode(true);
                var row = document.createElement("a");
                row.className = "checklist__row";
                row.href = "listing/" + s.page + "?id=" + encodeURIComponent(id);
                while (item.firstChild) row.appendChild(item.firstChild);
                item.appendChild(row);
                $("[data-section-title]", item).textContent = s.label;
                $("[data-section-summary]", item).textContent = sectionSummary(listing, s.key);
                var state = sectionState(listing, s.key);
                var stateEl = $("[data-section-state]", item);
                stateEl.textContent = state[0];
                stateEl.className = "status-tag status-tag--" + state[1];
                list.appendChild(item);
            });

            var left = missingSections(listing);
            $("[data-listing-send]").hidden = !editable;
            setActive(submit, left.length === 0);
            missing.textContent = left.length ? "Encore à faire : " + left.join(", ") + "." :
                "Tout est vérifié : vous pouvez envoyer l'annonce.";
        }

        function update(changes) {
            var current = listings()[id];
            Object.keys(changes).forEach(function (key) {
                current[key] = changes[key];
            });
            saveListing(current);
            render();
        }

        // PROTOTYPE: there's no submit endpoint yet (see above).
        submit.addEventListener("click", function () {
            if (!isActive(submit)) {
                missing.focus();
                return;
            }
            setActive(submit, false);
            submit.textContent = "Envoi…";
            window.setTimeout(function () {
                submit.textContent = "Envoyer pour vérification";
                update({ status: "PENDING", submittedAt: Date.now(), rejection: null });
                showFlash("Annonce envoyée. Nous vous préviendrons dès sa publication.");
            }, 600);
        });

        // PROTOTYPE: the check publishes the listing, its texts written by AI.
        $("[data-simulate-publish]").addEventListener("click", function () {
            var photos = listings()[id].photos.map(function (photo) {
                photo.status = "VERIFIED";
                return photo;
            });
            update({ status: "PUBLISHED", publishedAt: Date.now(), photos: photos });
            showFlash("L'annonce est publiée.");
        });

        // PROTOTYPE: the check sends it back, with a photo refused and the landmark to make clearer.
        $("[data-simulate-rejection]").addEventListener("click", function () {
            var current = listings()[id];
            var photo = current.photos.filter(function (p) {
                return p.status !== "REJECTED";
            })[0];
            if (photo) {
                photo.status = "REJECTED";
                photo.errorMessage = "Photo floue : reprenez-la à la lumière du jour.";
            }
            delete current.checked.location;
            current.status = "DRAFT";
            current.rejection = {
                at: Date.now(),
                reason: "Une photo est floue, et le point de repère ne suffit pas pour trouver le bien.",
                sections: ["location", "photos"]
            };
            saveListing(current);
            render();
            showFlash("L'annonce vous a été renvoyée pour correction.");
        });

        render();
        $("[data-listing]").hidden = false;

        var saved = params.get("saved");
        if (saved && SECTION_SAVED[saved]) {
            showFlash(SECTION_SAVED[saved]);
            params.delete("saved");
            window.history.replaceState(null, "", window.location.pathname + "?" + params.toString());
        }
    }

    /* listing/*.html: one section of the checklist. Its form only holds the section's fields. */

    // The questions of "Le bien" that apply to a property type (as offer-property.js: LAND is commercial).
    var LISTING_APPLIES = {
        furniture: function (a) {
            return a.propertyCategory === "RESIDENTIAL" && a.propertyType !== "BUILDING";
        },
        rooms: function (a) {
            return a.propertyCategory === "RESIDENTIAL" && ["ROOM", "STUDIO", "BUILDING"].indexOf(a.propertyType) === -1;
        },
        bathrooms: function (a) {
            return a.propertyType !== "LAND";
        },
        kitchens: function (a) {
            return a.propertyCategory === "RESIDENTIAL" && ["ROOM", "BUILDING"].indexOf(a.propertyType) === -1;
        },
        parkings: function (a) {
            return a.propertyType !== "ROOM";
        },
        levels: function (a) {
            return ["HOUSE", "DUPLEX", "BUILDING"].indexOf(a.propertyType) !== -1;
        },
        floorLevel: function (a) {
            return ["ROOM", "STUDIO", "APARTMENT", "OFFICE", "RETAIL"].indexOf(a.propertyType) !== -1;
        }
    };

    function fillListingForm(form, f) {
        $all("input, select, textarea", form).forEach(function (el) {
            var name = el.name;
            if (!name) return;
            if (el.type === "checkbox") el.checked = f.amenities.indexOf(name) !== -1;
            else if (name === "roadAccess") el.checked = f.amenities.indexOf(el.value) !== -1;
            else if (el.type === "radio") el.checked = String(f[name]) === el.value;
            else if (el.getAttribute("data-kind") === "amount") el.value = f[name] ? f[name].toLocaleString("fr-FR") : "";
            else el.value = f[name] == null ? "" : f[name];
        });
    }

    // The section's values, as UpdateListingRequest names them. A question hidden for the type is saved empty.
    function readListingForm(form) {
        var values = {};
        var amenities = null;
        $all("input, select, textarea", form).forEach(function (el) {
            var name = el.name;
            if (!name) return;
            var hidden = !!el.closest("[hidden]");
            if (el.type === "checkbox" || name === "roadAccess") {
                amenities = amenities || [];
                if (el.checked && !hidden) amenities.push(el.type === "checkbox" ? name : el.value);
                return;
            }
            if (el.type === "radio") {
                if (!(name in values)) values[name] = null;
                if (el.checked && !hidden) values[name] = el.value;
                return;
            }
            var v = hidden ? "" : el.value.trim();
            var kind = el.getAttribute("data-kind");
            if (kind === "amount") values[name] = parseAmount(v);
            else if (kind === "int") values[name] = v === "" ? null : /^\d+$/.test(v) ? parseInt(v, 10) : NaN;
            else if (kind === "number") values[name] = v === "" ? null : Number(v);
            else values[name] = v;
        });
        if (amenities) values.amenities = amenities;
        return values;
    }

    function ownerText(name, value) {
        switch (name) {
            case "propertyCategory":
                return CATEGORY_LABELS[value];
            case "propertyType":
                return PROPERTY_TYPE_LABELS[value];
            case "furnitureType":
                return LISTING_FURNITURE_LABELS[value];
            case "price":
                return value.toLocaleString("fr-FR") + " FCFA / mois";
            case "securityDeposit":
            case "advanceRent":
                return value + " mois";
            case "area":
                return value + " m²";
            case "availableFrom":
                return formatDate(new Date(value + "T00:00").getTime());
            case "amenities":
                return amenityLabels(value).join(", ") || "aucun équipement";
            default:
                return String(value);
        }
    }

    // "Indiqué par le propriétaire : …" under each field the agent changed from the landlord's answer.
    function syncOwnerHints(form, owner) {
        var values = readListingForm(form);
        $all("[data-owner-for]", form).forEach(function (hint) {
            var name = hint.getAttribute("data-owner-for");
            var was = owner[name];
            var now = values[name];
            var same = name === "amenities"
                ? was.slice().sort().join() === (now || []).slice().sort().join()
                : String(was == null ? "" : was) === String(now == null ? "" : now);
            hint.hidden = was == null || was === "" || same;
            hint.textContent = "Indiqué par le propriétaire : " + (was == null ? "" : ownerText(name, was));
        });
    }

    // [name, message] of each invalid field.
    var LISTING_RULES = {
        location: function (v) {
            var errors = [];
            if (!v.neighbourhood) errors.push(["neighbourhood", "Choisissez le quartier du bien."]);
            if (!v.landmark) errors.push(["landmark", "Indiquez un point de repère connu : école, carrefour, commerce…"]);
            return errors;
        },
        gps: function (v) {
            return v.latitude == null ? [["latitude", "Relevez la position GPS depuis le bien."]] : [];
        },
        property: function (v, form) {
            var errors = [];
            function shown(name) {
                var el = $('[name="' + name + '"]', form);
                return el && !el.closest("[hidden]");
            }
            if (!v.propertyCategory) errors.push(["propertyCategory", "Choisissez l'usage du bien."]);
            if (!v.propertyType) errors.push(["propertyType", "Choisissez le type de bien."]);
            if (shown("furnitureType") && !v.furnitureType) {
                errors.push(["furnitureType", "Indiquez si le bien est meublé."]);
            }
            ["rooms", "bathrooms", "livingRooms", "kitchens", "parkings", "levels", "floorLevel", "area"]
                .forEach(function (name) {
                    if (!shown(name)) return;
                    var required = ["rooms", "bathrooms", "area"].indexOf(name) !== -1;
                    if (v[name] == null && required) errors.push([name, "Indiquez ce nombre, compté sur place."]);
                    else if (isNaN(v[name])) errors.push([name, "Indiquez un nombre entier."]);
                    else if (name === "area" && !(v.area > 0)) errors.push([name, "Indiquez la surface en m²."]);
                });
            return errors;
        },
        amenities: function (v) {
            var road = (v.amenities || []).filter(function (a) {
                return ROAD_ACCESS_KEYS.indexOf(a) !== -1;
            });
            return road.length ? [] : [["roadAccess", "Indiquez l'accès routier au bien."]];
        },
        terms: function (v) {
            var errors = [];
            if (v.price === null) errors.push(["price", "Indiquez le loyer mensuel."]);
            else if (isNaN(v.price)) errors.push(["price", "Indiquez le loyer en chiffres, en FCFA par mois."]);
            if (!v.securityDeposit) errors.push(["securityDeposit", "Choisissez la caution demandée."]);
            if (!v.availableFrom) errors.push(["availableFrom", "Indiquez la date à laquelle le bien est libre."]);
            else if (new Date(v.availableFrom + "T00:00").getTime() > Date.now() + 365 * DAY_MS) {
                errors.push(["availableFrom", "Choisissez une date dans les 12 prochains mois."]);
            }
            return errors;
        },
        remarks: function () {
            return [];
        }
    };

    function showListingErrors(form, errors) {
        $all(".field__error", form).forEach(function (el) {
            el.hidden = true;
        });
        $all("[aria-invalid]", form).forEach(function (el) {
            el.removeAttribute("aria-invalid");
        });
        errors.forEach(function (error) {
            var input = $('[data-field="' + error[0] + '"]', form) || $('[name="' + error[0] + '"]', form);
            setFieldError(input, $("#" + error[0] + "-error", form), error[1]);
        });
        if (errors.length) {
            var first = $('[data-field="' + errors[0][0] + '"]', form) || $('[name="' + errors[0][0] + '"]', form);
            first.focus();
        }
    }

    function initListingProperty(form) {
        function sync() {
            var category = ($('[name="propertyCategory"]:checked', form) || {}).value;
            $all('[name="propertyType"]', form).forEach(function (radio) {
                var fits = radio.closest("[data-categories]").getAttribute("data-categories").split(" ")
                    .indexOf(category) !== -1;
                radio.closest(".choice").hidden = !fits;
                if (!fits) radio.checked = false;
            });
            var a = {
                propertyCategory: category,
                propertyType: ($('[name="propertyType"]:checked', form) || {}).value
            };
            $all("[data-applies]", form).forEach(function (el) {
                el.hidden = !a.propertyType || !LISTING_APPLIES[el.getAttribute("data-applies")](a);
            });
        }
        $all('[name="propertyCategory"], [name="propertyType"]', form).forEach(function (radio) {
            radio.addEventListener("change", sync);
        });
        sync();
    }

    function initListingGps(form) {
        var button = $("[data-gps]", form);
        var result = $("[data-gps-result]", form);
        var weak = $("[data-gps-weak]", form);
        var error = $("#latitude-error", form);

        function show() {
            var lat = form.elements.latitude.value;
            var lng = form.elements.longitude.value;
            var accuracy = Number(form.elements.accuracy.value);
            result.hidden = !lat;
            if (!lat) return;
            $("[data-gps-coords]", result).textContent = Number(lat).toFixed(5) + ", " + Number(lng).toFixed(5) +
                (accuracy ? " (± " + accuracy + " m)" : "");
            $("[data-gps-map]", result).href = "https://www.openstreetmap.org/?mlat=" + lat + "&mlon=" + lng +
                "#map=18/" + lat + "/" + lng;
            // Indoors or under a roof the phone guesses: the agent takes it again outside, at the door
            weak.hidden = !(accuracy > 50);
            button.textContent = "Relever à nouveau";
        }

        function set(coords) {
            form.elements.latitude.value = coords.latitude;
            form.elements.longitude.value = coords.longitude;
            form.elements.accuracy.value = Math.round(coords.accuracy);
            setFieldError(button, error, null);
            show();
        }

        button.addEventListener("click", function () {
            if (!navigator.geolocation) {
                setFieldError(button, error, "Ce navigateur ne donne pas la position : utilisez celui de votre téléphone.");
                return;
            }
            var label = button.textContent;
            setActive(button, false);
            button.textContent = "Localisation…";
            navigator.geolocation.getCurrentPosition(function (position) {
                setActive(button, true);
                set(position.coords);
            }, function (e) {
                setActive(button, true);
                button.textContent = label;
                setFieldError(button, error, e.code === 1
                    ? "Autorisez l'accès à votre position dans le navigateur, puis réessayez."
                    : "Position introuvable : sortez devant l'entrée du bien et réessayez.");
            }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 });
        });

        // PROTOTYPE: a position in Bonapriso, for a computer without GPS.
        $("[data-simulate-gps]", form).addEventListener("click", function () {
            set({ latitude: 4.0335 + Math.random() / 1000, longitude: 9.6942 + Math.random() / 1000, accuracy: 8 });
        });

        show();
    }

    function initListingPhotos(listing, readonly) {
        var grid = $("[data-photos]");
        var template = $("[data-photo-template]");
        var count = $("[data-photo-count]");
        var error = $("#photos-error");
        var inputs = $all("[data-photo-input]");
        var live = $("[data-photo-live]");
        var pending = 0;

        function render() {
            listing = listings()[listing.id];
            grid.textContent = "";
            listing.photos.forEach(function (photo, i) {
                var item = template.content.firstElementChild.cloneNode(true);
                var img = $("img", item);
                img.src = photo.src;
                img.alt = "Photo " + (i + 1);
                var rejected = photo.status === "REJECTED";
                item.classList.toggle("photo--rejected", rejected);
                var note = $("[data-photo-note]", item);
                note.textContent = rejected ? photo.errorMessage : photo.status === "VERIFIED" ? "Vérifiée" : "Envoyée";
                $("[data-photo-delete]", item).hidden = !!readonly;
                $("[data-photo-delete]", item).setAttribute("aria-label", "Supprimer la photo " + (i + 1));
                $("[data-photo-delete]", item).addEventListener("click", function () {
                    // PROTOTYPE: ndopify-server can't delete a listing's image yet.
                    var current = listings()[listing.id];
                    current.photos = current.photos.filter(function (p) {
                        return p.id !== photo.id;
                    });
                    saveListing(current);
                    render();
                    announce(live, "Photo " + (i + 1) + " supprimée.");
                });
                grid.appendChild(item);
            });
            for (var n = 0; n < pending; n++) {
                var placeholder = document.createElement("li");
                placeholder.className = "photo photo--pending";
                placeholder.textContent = "Envoi…";
                grid.appendChild(placeholder);
            }
            var good = goodPhotos(listing);
            var refused = listing.photos.length - good;
            count.textContent = plural(good, "photo", "photos") + (readonly ? "" : refused
                ? " · supprimez " + (refused > 1 ? "les photos refusées" : "la photo refusée") + " et reprenez-la"
                : good < MIN_LISTING_PHOTOS ? " · encore " + (MIN_LISTING_PHOTOS - good) + " au moins"
                    : " · c'est suffisant, ajoutez-en si utile");
            var full = listing.photos.length + pending >= MAX_LISTING_PHOTOS;
            inputs.forEach(function (input) {
                input.disabled = full;
            });
        }

        // Each photo is sent as soon as it's taken, so a visit cut short loses nothing.
        function add(file) {
            if (ACCEPTED_TYPES.indexOf(file.type) === -1) {
                setFieldError(inputs[0], error, file.name + " n'est pas une photo JPEG, PNG ou WEBP.");
                return;
            }
            if (file.size > MAX_FILE_BYTES) {
                setFieldError(inputs[0], error, file.name + " dépasse 5 Mo.");
                return;
            }
            if (listings()[listing.id].photos.length + pending >= MAX_LISTING_PHOTOS) {
                setFieldError(inputs[0], error, MAX_LISTING_PHOTOS + " photos au plus.");
                return;
            }
            pending++;
            render();
            // PROTOTYPE: simulates POST /v1/listings/{id}/images/upload, kept small for the session's storage
            toThumbnail(file, function (src) {
                window.setTimeout(function () {
                    pending--;
                    var current = listings()[listing.id];
                    if (src) {
                        current.photos.push({ id: Date.now() + Math.random(), src: src, status: "PENDING_VERIFICATION" });
                        if (!saveListing(current)) {
                            setFieldError(inputs[0], error, "Mémoire du prototype pleine : supprimez des photos.");
                        }
                    } else {
                        setFieldError(inputs[0], error, file.name + " ne peut pas être lue.");
                    }
                    render();
                    announce(live, count.textContent);
                }, 400);
            }, 480);
        }

        if (readonly) {
            $("[data-photo-actions]").hidden = true;
            $(".photo-tips").hidden = true;
            $(".form__actions [data-back-link]").textContent = "Retour à l'annonce";
        }
        inputs.forEach(function (input) {
            input.addEventListener("change", function () {
                setFieldError(inputs[0], error, null);
                Array.prototype.forEach.call(input.files, add);
                input.value = "";
            });
        });
        render();
    }

    // A sent section: its answers only (the choices not taken and the empty fields left out), nothing to act on.
    function showListingReadonly(form, listing) {
        syncOwnerHints(form, listing.owner);
        form.classList.add("listing-readonly");
        $all(".field, fieldset.choices", form).forEach(function (group) {
            var answered = $all("input, select, textarea", group).some(function (el) {
                return el.type === "checkbox" || el.type === "radio" ? el.checked : el.value.trim() !== "";
            });
            if (!answered) group.hidden = true;
        });
        $all("input, select, textarea, button", form).forEach(function (el) {
            el.disabled = true;
        });
        $all("[data-gps], [data-gps-prototype], [data-gps-hint], [data-gps-weak], [data-section-submit]", form)
            .forEach(function (el) {
                el.hidden = true;
            });
        $all(".form__actions [data-back-link]", form).forEach(function (a) {
            a.textContent = "Retour à l'annonce";
            a.className = "btn btn--primary";
        });
        form.addEventListener("submit", function (event) {
            event.preventDefault();
        });
    }

    function initListingSection() {
        var section = document.body.getAttribute("data-section");
        var id = new URLSearchParams(window.location.search).get("id");
        var listing = listings()[id];
        var back = "../listing.html?id=" + encodeURIComponent(id);
        $all("[data-back-link]").forEach(function (a) {
            a.href = back;
        });
        var locked = $("[data-section-locked]");
        if (!listing || !ownsMandate(listing.mandateId)) {
            locked.hidden = false;
            return;
        }
        // Sent: nothing changes during the check nor once published, the section is only shown
        var readonly = listing.status !== "DRAFT";
        if (readonly) {
            $("[data-section-readonly-text]").textContent = (listing.status === "PUBLISHED"
                ? "Annonce publiée le " + formatDate(listing.publishedAt)
                : "Annonce en vérification depuis le " + formatDate(listing.submittedAt)) +
                " : voici ce que vous avez relevé, il ne peut plus être modifié.";
            $("[data-section-readonly]").hidden = false;
            // The instruction is for the visit
            $("[data-section-content] .page-intro > p:last-child").hidden = true;
        }
        document.title = $("[data-section-content] h1").textContent + " · " + listingTitle(listing) + " — Ndopify Partner Central";
        $("[data-section-meta]").textContent = "Annonce · " + listingTitle(listing) + " · mandat n° " +
            listing.mandateId;
        $("[data-section-content]").hidden = false;
        if (section === "photos") {
            initListingPhotos(listing, readonly);
            return;
        }

        var form = $("[data-section-form]");
        fillListingForm(form, listing.fields);
        if (section === "property") initListingProperty(form);
        if (section === "gps") initListingGps(form);
        if (readonly) {
            showListingReadonly(form, listing);
            return;
        }
        if (section === "terms") form.elements.availableFrom.min = isoDate(Date.now());
        function sync(event) {
            syncOwnerHints(form, listing.owner);
            // A field's error goes once it's changed
            var name = event && event.target.name;
            var error = name && $("#" + name + "-error", form);
            if (error) setFieldError(event.target, error, null);
        }
        form.addEventListener("input", sync);
        form.addEventListener("change", sync);
        sync();

        var submit = $("[data-section-submit]", form);
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (!isActive(submit)) return;
            var values = readListingForm(form);
            var errors = LISTING_RULES[section](values, form);
            showListingErrors(form, errors);
            if (errors.length) return;
            // PROTOTYPE: simulates POST /v1/listings/{id} (UpdateListingRequest: every field, with the version)
            setActive(submit, false);
            submit.textContent = "Enregistrement…";
            window.setTimeout(function () {
                var current = listings()[id];
                Object.keys(values).forEach(function (key) {
                    current.fields[key] = values[key];
                });
                current.checked[section] = Date.now();
                saveListing(current);
                window.location.href = back + "&saved=" + section;
            }, 500);
        });
    }

    /* ---------- Boot ---------- */
    var pages = {
        login: initLogin,
        "kyc-wizard": initKycWizard,
        "agent-home": initAgentHome,
        mandate: initMandate,
        "mandate-edit": initMandateEdit,
        listing: initListing,
        "listing-section": initListingSection
    };

    document.addEventListener("DOMContentLoaded", function () {
        initUserMenu();
        var init = pages[document.body.getAttribute("data-page")];
        if (init) init();
    });
})();
