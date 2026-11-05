/*
 * visit.html: the visit page's enhancements. Every action is a form that posts; nothing here is required: without
 * JavaScript the server renders the page and validates every action, and the visit still starts and ends without a
 * position.
 */
(function () {
    "use strict";

    if (document.body.getAttribute("data-page") !== "visit") return;

    var WEAK_METERS = 50;
    var COMMENT_MAX = 2000;

    function $(selector, root) {
        return (root || document).querySelector(selector);
    }

    function $all(selector, root) {
        return Array.prototype.slice.call((root || document).querySelectorAll(selector));
    }

    /* ---------- The outcome of the last action ---------- */
    var flash = $("[data-visit-flash]");
    if (flash) flash.focus();

    /* ---------- The customer's code: digits only, four of them ---------- */
    var code = $("[data-checkin-form] input[name=code]");
    if (code) {
        code.addEventListener("input", function () {
            code.value = code.value.replace(/\D/g, "").slice(0, 4);
            var error = $("#code-error");
            if (error) {
                error.hidden = true;
                code.removeAttribute("aria-invalid");
            }
        });
    }

    /* ---------- The type hint follows the chosen type ---------- */
    var scheduleForm = $("[data-schedule-form]");
    if (scheduleForm) {
        var firstName = scheduleForm.getAttribute("data-customer-first-name") || "Le client";
        var typeHint = $("[data-type-hint]");
        scheduleForm.addEventListener("change", function (event) {
            if (event.target.name !== "type" || !typeHint) return;
            typeHint.textContent = event.target.value === "VIDEO"
                ? "Vous appellerez " + firstName + " en vidéo sur WhatsApp, depuis le bien, à l'heure choisie."
                : "Vous retrouvez " + firstName + " au bien, à l'heure choisie.";
        });
    }

    /* ---------- The report's counter ---------- */
    var comment = $("[data-checkout-form] textarea[name=comment]");
    var counter = $("[data-counter]");
    if (comment && counter) {
        var syncCounter = function () {
            counter.textContent = comment.value.length + " / " + COMMENT_MAX;
        };
        comment.addEventListener("input", syncCounter);
        syncCounter();
    }

    /* ---------- The position: taken on load, never required ---------- */
    var positionInputs = $all("[data-position-input]");
    var positionBoxes = $all("[data-position-status]");

    function renderPosition(box, state, accuracy) {
        var label = $("[data-position-text]", box);
        var retry = $("[data-position-retry]", box);
        box.hidden = false;
        box.className = "visit-position visit-position--" + state;
        retry.hidden = state === "pending" || state === "idle" || (state === "done" && accuracy <= WEAK_METERS);
        if (state === "pending") label.textContent = "Relevé de votre position…";
        if (state === "none") label.textContent = box.getAttribute("data-none-text");
        if (state === "done") {
            label.textContent = accuracy > WEAK_METERS
                ? "Position imprécise (précision " + accuracy + " m) : sortez devant le bien et relevez-la à nouveau."
                : "Position relevée · précision " + accuracy + " m";
            if (accuracy > WEAK_METERS) box.className += " visit-position--weak";
        }
    }

    function setPosition(state, coords) {
        var accuracy = coords ? Math.round(coords.accuracy) : null;
        positionInputs.forEach(function (input) {
            input.value = !coords ? "" : input.name === "accuracyMeters" ? accuracy : coords[input.name];
        });
        positionBoxes.forEach(function (box) {
            renderPosition(box, state, accuracy);
        });
    }

    function capture() {
        if (!navigator.geolocation) {
            setPosition("none");
            return;
        }
        setPosition("pending");
        navigator.geolocation.getCurrentPosition(
            function (position) {
                setPosition("done", position.coords);
            },
            function () {
                setPosition("none");
            },
            { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
        );
    }

    $all("[data-position-retry]").forEach(function (button) {
        button.addEventListener("click", capture);
    });

    if (positionInputs.length > 0) capture();

    /* ---------- The locked check-in's link opens the no-show ---------- */
    var noShow = $("#no-show");
    if (noShow && window.location.hash === "#no-show") noShow.open = true;
})();
