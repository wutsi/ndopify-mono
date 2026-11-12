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

    /* ---------- The closure actions: the trigger opens its form in place, as ?close= does without JS ---------- */
    $all("[data-visit-close]").forEach(function (box) {
        var buttons = $("[data-close-buttons]", box);
        var openers = $all("[data-close-open]", box);
        var opener = null;

        function formOf(mode) {
            return $('[data-close-form="' + mode + '"]', box);
        }

        function open(link) {
            opener = link;
            var mode = link.getAttribute("data-close-open");
            $all("[data-close-form]", box).forEach(function (form) {
                form.hidden = form.getAttribute("data-close-form") !== mode;
            });
            buttons.hidden = true;
            link.setAttribute("aria-expanded", "true");
            var first = $("input, textarea", formOf(mode));
            if (first) first.focus();
        }

        function close() {
            $all("[data-close-form]", box).forEach(function (form) {
                form.hidden = true;
            });
            buttons.hidden = false;
            openers.forEach(function (link) {
                link.setAttribute("aria-expanded", "false");
            });
            if (opener) opener.focus();
        }

        openers.forEach(function (link) {
            var form = formOf(link.getAttribute("data-close-open"));
            link.setAttribute("aria-expanded", form && !form.hidden ? "true" : "false");
            link.addEventListener("click", function (event) {
                event.preventDefault();
                open(link);
            });
        });

        $all("[data-close-back]", box).forEach(function (back) {
            back.addEventListener("click", function (event) {
                event.preventDefault();
                close();
            });
        });

        /* Opened by the server (?close=...): focus its first field. */
        $all("[data-close-form]", box).forEach(function (form) {
            if (form.hidden) return;
            opener = $('[data-close-open="' + form.getAttribute("data-close-form") + '"]', box);
            var first = $("input, textarea", form);
            if (first) first.focus();
        });
    });
})();
