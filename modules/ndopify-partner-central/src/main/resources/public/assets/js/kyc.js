/*
 * kyc.html enhancements. Every step works without this file: the form posts its photo to /kyc/{step}.
 * - photos are checked (type, 5 MB) and shrunk in the browser before they're posted, so a 12 MP phone photo
 *   doesn't hit the upload limit, and the preview shows the chosen photo at once;
 * - the selfie step uses the front camera in the page (getUserMedia) when it can, under an oval guide; otherwise
 *   the <input type="file" capture="user"> that the page already shows is kept.
 * Ported from the prototype (src/main/html/assets/js/partner-central.js: kycUpload, kycCamera).
 */
(function () {
    "use strict";

    var ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
    var MAX_FILE_BYTES = 5 * 1024 * 1024;
    // Long enough for the ID card text to stay readable by the verification.
    var MAX_SIDE = 2000;
    var SELFIE_MAX_HEIGHT = 1000;

    function $(selector, root) {
        return (root || document).querySelector(selector);
    }

    function setError(step, message) {
        var input = $(".upload__input", step);
        var error = $(".field__error", step);
        if (!error) return;
        $("[data-error-text]", error).textContent = message;
        error.hidden = !message;
        if (message) input.setAttribute("aria-invalid", "true");
        else input.removeAttribute("aria-invalid");
    }

    function showPreview(step, blob) {
        var preview = $("[data-preview]", step);
        var img = $("img", preview);
        if (!img) {
            img = document.createElement("img");
            img.className = "upload__image";
            img.alt = preview.getAttribute("data-alt");
            preview.insertBefore(img, preview.firstChild);
        }
        img.hidden = false;
        img.src = URL.createObjectURL(blob);
        $("[data-placeholder]", step).hidden = true;
        var label = $("[data-upload-label]", step);
        if (label) label.textContent = "Changer la photo";
        var status = $("[data-upload-status]", step);
        if (status) status.textContent = preview.getAttribute("data-alt").replace("Aperçu", "Photo") + " ajoutée.";
    }

    // Puts a photo in the step's file input, so the normal form post sends it. False when the browser can't.
    function useFile(step, file) {
        try {
            var transfer = new DataTransfer();
            transfer.items.add(file);
            $(".upload__input", step).files = transfer.files;
            return true;
        } catch (e) {
            return false;
        }
    }

    function shrink(file, callback) {
        var url = URL.createObjectURL(file);
        var img = new Image();
        img.onload = function () {
            URL.revokeObjectURL(url);
            var scale = Math.min(1, MAX_SIDE / Math.max(img.width, img.height));
            var canvas = document.createElement("canvas");
            canvas.width = Math.round(img.width * scale);
            canvas.height = Math.round(img.height * scale);
            canvas.getContext("2d").drawImage(img, 0, 0, canvas.width, canvas.height);
            canvas.toBlob(function (blob) {
                callback(blob ? new File([blob], "photo.jpg", { type: "image/jpeg" }) : null);
            }, "image/jpeg", 0.85);
        };
        img.onerror = function () {
            URL.revokeObjectURL(url);
            callback(null);
        };
        img.src = url;
    }

    function initUpload(step) {
        var input = $(".upload__input", step);
        if (!input) return;
        input.addEventListener("change", function () {
            var file = input.files && input.files[0];
            if (!file) return;
            if (ACCEPTED_TYPES.indexOf(file.type) === -1) {
                setError(step, "Format non accepté. Utilisez une photo JPG, PNG ou WEBP.");
                input.value = "";
                return;
            }
            setError(step, "");
            shrink(file, function (small) {
                // Can't shrink (or can't swap the file): post the original and let the server check it.
                var chosen = small && useFile(step, small) ? small : file;
                if (chosen.size > MAX_FILE_BYTES) {
                    var size = (chosen.size / (1024 * 1024)).toFixed(1).replace(".", ",");
                    setError(step, "Cette photo pèse " + size + " Mo. La taille maximale est de 5 Mo.");
                    input.value = "";
                    return;
                }
                showPreview(step, chosen);
            });
        });
    }

    function cameraProblem(error) {
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

    function initCamera(step) {
        var video = $("[data-camera-video]", step);
        if (!video) return;
        var guide = $("[data-camera-guide]", step);
        var start = $("[data-camera-start]", step);
        var shoot = $("[data-camera-shoot]", step);
        var fallback = $("[data-camera-fallback]", step);
        var problem = $("[data-camera-problem]", step);
        var preview = $("[data-preview]", step);
        var stream = null;

        var supported = window.isSecureContext && navigator.mediaDevices && navigator.mediaDevices.getUserMedia &&
            typeof DataTransfer === "function";
        if (!supported) return; // keep the file input the page already shows

        // The in-page camera replaces the file input; the input stays in the form to carry the photo.
        fallback.hidden = true;
        start.hidden = false;
        if ($("img", preview)) start.textContent = "Reprendre la photo";

        function setLive(on) {
            var photo = $("img", preview);
            video.hidden = !on;
            guide.hidden = !on;
            shoot.hidden = !on;
            start.hidden = on;
            if (photo) photo.hidden = on;
            $("[data-placeholder]", step).hidden = on || !!photo;
        }

        function stop() {
            if (stream) stream.getTracks().forEach(function (track) { track.stop(); });
            stream = null;
            video.srcObject = null;
            setLive(false);
        }

        function useFallback(message) {
            stop();
            start.hidden = true;
            fallback.hidden = false;
            $("[data-camera-problem-text]", problem).textContent = message;
            problem.hidden = false;
        }

        start.addEventListener("click", function () {
            problem.hidden = true;
            start.disabled = true;
            navigator.mediaDevices
                .getUserMedia({ video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 960 } }, audio: false })
                .then(function (opened) {
                    start.disabled = false;
                    stream = opened;
                    video.srcObject = opened;
                    setLive(true);
                    video.play().catch(function () { /* autoplay attribute covers it */ });
                    shoot.focus();
                })
                .catch(function (error) {
                    start.disabled = false;
                    useFallback(cameraProblem(error));
                });
        });

        shoot.addEventListener("click", function () {
            var vw = video.videoWidth;
            var vh = video.videoHeight;
            if (!vw || !vh) return;
            // Crop the centre of the frame to the 3:4 portrait of the preview; drawn as the camera sees it (not mirrored).
            var sw = Math.min(vw, vh * 3 / 4);
            var sh = sw * 4 / 3;
            var scale = Math.min(1, SELFIE_MAX_HEIGHT / sh);
            var canvas = document.createElement("canvas");
            canvas.width = Math.round(sw * scale);
            canvas.height = Math.round(sh * scale);
            canvas.getContext("2d").drawImage(video, (vw - sw) / 2, (vh - sh) / 2, sw, sh, 0, 0, canvas.width, canvas.height);
            stop();
            canvas.toBlob(function (blob) {
                var file = blob && new File([blob], "selfie.jpg", { type: "image/jpeg" });
                if (!file || !useFile(step, file)) {
                    useFallback("La photo n'a pas pu être prise. Prenez votre selfie avec l'appareil photo de votre téléphone.");
                    return;
                }
                showPreview(step, file);
                start.textContent = "Reprendre la photo";
                $("[data-next]", step).focus();
            }, "image/jpeg", 0.85);
        });

        // Never leave the camera on when the agent leaves the page.
        window.addEventListener("pagehide", stop);
    }

    function initSubmit(form) {
        var submit = $("[data-submit]", form);
        if (!submit) return;
        form.addEventListener("submit", function () {
            // One submission: a second click would hit an already-submitted case.
            submit.disabled = true;
            submit.textContent = "Envoi en cours…";
            var sending = $("[data-sending]", form);
            if (sending) sending.textContent = "Envoi de votre dossier en cours.";
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (document.body.getAttribute("data-page") !== "kyc") return;
        var form = $("[data-kyc-form]");
        if (!form) return;
        var step = $("[data-step]", form);
        initUpload(step);
        initCamera(step);
        initSubmit(form);
    });
})();
