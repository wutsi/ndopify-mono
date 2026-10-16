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
        accepted: "npc.mandates.accepted"
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

    // PROTOTYPE: shows the profile saved by the profile pages; until then, ?profile=complete or ?profile=empty
    // shows the other states of the section.
    function initProfile() {
        var section = $("[data-profile]");
        if (!section) return;
        var params = new URLSearchParams(window.location.search);
        var saved = savedProfile();
        var state = params.get("profile");
        var photoSrc = saved ? saved.photo : (state === "complete" ? "assets/images/icon.png" : null);
        var hasBio = saved ? !!saved.biography : state !== "empty";

        if (photoSrc) {
            var img = document.createElement("img");
            img.src = photoSrc;
            img.alt = "";
            img.width = 80;
            img.height = 80;
            var photo = $("[data-profile-photo]", section);
            photo.textContent = "";
            photo.appendChild(img);
        }
        if (saved) {
            if (saved.biography) $("[data-profile-bio]", section).textContent = saved.biography;
            if (saved.cityName) {
                $("[data-profile-area-text]", section).textContent =
                    (saved.neighborhoodNames || []).join(", ") + " — " + saved.cityName;
            }
        }
        $("[data-profile-bio]", section).hidden = !hasBio;
        $("[data-profile-bio-missing]", section).hidden = hasBio;
        $("[data-profile-todo=photo]", section).hidden = !!photoSrc;
        $("[data-profile-todo=bio]", section).hidden = hasBio;
        $("[data-profile-incomplete]", section).hidden = !!photoSrc && hasBio;

        // Back from a profile page: confirm the save once, then drop the parameter so a reload doesn't repeat it.
        var message = PROFILE_SAVED[params.get("saved")];
        if (message) {
            $("[data-profile-saved-text]", section).textContent = message;
            $("[data-profile-saved]", section).hidden = false;
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

    function initAssignments() {
        var list = $("[data-assignments]");
        if (!list) return;
        var status = $("[data-assignments-status]");
        var empty = $("[data-assignments-empty]");
        var heading = $("#assignments-title");
        heading.setAttribute("tabindex", "-1");

        function pending() {
            return $all("[data-assignment]:not([data-closed])", list);
        }

        function syncEmpty() {
            var none = $all("[data-assignment]", list).length === 0;
            list.hidden = none;
            empty.hidden = !none;
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
            var target = next ? $("h3", next) : heading;
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
                    var ids = acceptedMandates();
                    ids.push(item.getAttribute("data-mandate-id"));
                    store.set(KEYS.accepted, JSON.stringify(ids));
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
        initAssignments();
    }

    /* ---------- mandate.html — an accepted mandate ---------- */

    // PROTOTYPE: the mandates offered on index.html, as GET /v1/mandates/{id} (+ its customer) would return them.
    var MANDATES = {
        101: {
            title: "Locataire · Appartement",
            party: "client",
            contact: {
                name: "Aïcha Mbarga",
                whatsapp: "+237699123456",
                email: "aicha.mbarga@example.com",
                period: "Matin"
            },
            criteria: [
                ["Secteur", "Bonapriso, Akwa — Douala"],
                ["Budget", "150K FCFA / mois"],
                ["Emménagement", "Dans moins de 1 mois"],
                ["Catégorie", "Résidentiel"],
                ["Type de propriété", "Appartement"],
                ["Meublé", "Non"],
                ["Chambres", "2"],
                ["Parking", "1"],
                ["Surface souhaitée", "80 m²"],
                ["Accès à l'eau", "Compteur dédié, Forage"],
                ["Accès à l'électricité", "Compteur dédié, Groupe électrogène"],
                ["Accès routier", "Route bitumée"],
                ["Sécurité", "Gardien 24h/24h"],
                ["Autres précisions", "Proche d'une école pour mes deux enfants.\nRez-de-chaussée ou premier étage."]
            ]
        },
        102: {
            title: "Bailleur · Studio",
            party: "bailleur",
            contact: {
                name: "Paul Ekwalla",
                whatsapp: "+237677987654",
                email: "paul.ekwalla@example.com",
                period: "Après-midi"
            },
            criteria: [
                ["Secteur", "Bonanjo — Douala"],
                ["Loyer", "75K FCFA / mois"],
                ["Disponibilité", "Dans 1 à 3 mois"],
                ["Catégorie", "Résidentiel"],
                ["Type de propriété", "Studio"],
                ["Meublé", "Oui"],
                ["Chambres", "1"],
                ["Parking", "Aucune"],
                ["Surface", "30 m²"],
                ["Accès à l'eau", "Compteur d'eau"],
                ["Accès à l'électricité", "Compteur électrique"],
                ["Accès routier", "Piste accessible en voiture"],
                ["Sécurité", "Barrière sécurisée"]
            ]
        },
        103: {
            title: "Locataire · Bureau",
            party: "client",
            contact: {
                name: "Marie-Claire Ndjock",
                whatsapp: "+237655443322",
                email: "mc.ndjock@example.com",
                period: "Soirée"
            },
            criteria: [
                ["Secteurs", "Akwa, Bonanjo, Deïdo — Douala"],
                ["Budget", "400K FCFA / mois"],
                ["Emménagement", "Dans plus de 3 mois"],
                ["Catégorie", "Commercial"],
                ["Type de propriété", "Bureau"],
                ["Meublé", "Indifférent"],
                ["Parking", "3+"],
                ["Surface souhaitée", "150 m²"],
                ["Accès à l'eau", "Compteur dédié"],
                ["Accès à l'électricité", "Compteur dédié, Panneau solaire"],
                ["Accès routier", "Route bitumée"],
                ["Sécurité", "Vidéo surveillance, Gardien 24h/24h"]
            ]
        }
    };

    // "+237699123456" → "+237 6 99 12 34 56" (Cameroon numbers; others are shown as they are).
    function displayPhone(e164) {
        var m = /^\+237(\d)(\d{2})(\d{2})(\d{2})(\d{2})$/.exec(e164);
        return m ? "+237 " + m.slice(1).join(" ") : e164;
    }

    function initMandate() {
        var id = new URLSearchParams(window.location.search).get("id");
        var mandate = MANDATES[id];
        // Anti-bypass: the details only unlock once the agent accepted the mandate.
        if (!mandate || acceptedMandates().indexOf(id) === -1) {
            $("[data-mandate-locked]").hidden = false;
            return;
        }
        var owner = mandate.party === "bailleur";
        var c = mandate.contact;
        document.title = mandate.title + " — Ndopify Partner Central";
        $("[data-mandate-title]").textContent = mandate.title;
        $("[data-party-label-cap]").textContent = owner ? "Le bailleur" : "Le client";
        $("[data-contact-title]").textContent = owner ? "Le bailleur" : "Le client";
        $("[data-search-title]").textContent = owner ? "Son bien" : "Sa recherche";

        $('[data-contact="name"]').textContent = c.name;
        var wa = $('[data-contact="whatsapp"]');
        wa.textContent = displayPhone(c.whatsapp);
        wa.href = "https://wa.me/" + c.whatsapp.replace("+", "");
        var email = $('[data-contact="email"]');
        email.textContent = c.email;
        email.href = "mailto:" + c.email;
        $('[data-contact="period"]').textContent = c.period;
        $("[data-contact-whatsapp]").href = wa.href;
        $("[data-contact-call]").href = "tel:" + c.whatsapp;

        var list = $("[data-mandate-criteria]");
        mandate.criteria.forEach(function (row) {
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
        });
        $("[data-mandate]").hidden = false;
    }

    /* ---------- Boot ---------- */
    var pages = {
        login: initLogin,
        "kyc-wizard": initKycWizard,
        "agent-home": initAgentHome,
        mandate: initMandate
    };

    document.addEventListener("DOMContentLoaded", function () {
        initUserMenu();
        var init = pages[document.body.getAttribute("data-page")];
        if (init) init();
    });
})();
