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
        mandates: "npc.mandates.state"
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

    // mandates: the "Mes mandats" section (initMandates), where an accepted offer is added.
    function initAssignments(mandates) {
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
                movingPeriod: "WITHIN_3_MONTHS"
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
                movingPeriod: "WITHIN_1_MONTH"
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
    var CHECK_MARK = '<svg class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false"><circle cx="10" cy="10" r="8.25" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="m6.5 10.25 2.5 2.5 4.5-5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';

    function formatDate(ms) {
        return DATE_FORMAT.format(new Date(ms));
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
    function nextStep(mandate, state) {
        var p = partyWords(mandate);
        if (state.status === "ASSIGNED") {
            return "Contactez " + p.the + ", puis envoyez-lui le mandat à approuver.";
        }
        if (state.status === "PENDING_APPROVAL") {
            var deadline = signatureDeadline(state);
            if (deadline < Date.now()) return "Délai de signature dépassé : renvoyez le mandat.";
            return p.The + " a jusqu'au " + formatDate(deadline) + " pour signer le mandat.";
        }
        if (state.status === "APPROVED") {
            return "Mandat signé, valable jusqu'au " + formatDate(addMonths(state.approvedAt, CONTRACT_MONTHS)) + ".";
        }
        return "";
    }

    function setStatusTag(el, status) {
        var tag = STATUS_TAGS[status];
        el.textContent = tag[0];
        el.className = "status-tag status-tag--" + tag[1];
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
            $("[data-next-step]", item).textContent = nextStep(MANDATES[id], state);
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
            ? "Présentez le bien aux locataires et organisez les visites."
            : "Proposez des biens au client et organisez les visites.";

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

        var criteria = $("[data-mandate-criteria]");
        criteriaOf(id).forEach(function (row) {
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
            criteria.appendChild(item);
        });

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

            $("[data-close]").hidden = closed;
            // Before its approval a mandate can't be closed with a lease (409 MANDATE_NOT_APPROVED): only cancelled.
            $('[data-close-open="close"]').hidden = state.status !== "APPROVED";
        }

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


    /* ---------- Boot ---------- */
    var pages = {
        login: initLogin,
        "kyc-wizard": initKycWizard,
        "agent-home": initAgentHome,
        mandate: initMandate,
        "mandate-edit": initMandateEdit
    };

    document.addEventListener("DOMContentLoaded", function () {
        initUserMenu();
        var init = pages[document.body.getAttribute("data-page")];
        if (init) init();
    });
})();
