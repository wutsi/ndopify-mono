/*
 * index.html (Espace agent) enhancements. The page works without this file: "Refuser" opens the confirmation via
 * /?decline={id}, the forms post to /assignments/{id}/accept|decline, and the time left is the one at render.
 * - the time left of each offer counts down; under 15 min the card is marked urgent; at 0 its actions are replaced
 *   by "Délai expiré", so a stale offer can't be posted;
 * - "Refuser" / "Annuler" toggle the confirmation in place instead of reloading the page;
 * - a button that posted is disabled, so a double click doesn't post twice.
 * Ported from the prototype (src/main/html/assets/js/partner-central.js: initAssignments).
 */
(function () {
    "use strict";

    var URGENT_MS = 15 * 60 * 1000;

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

    function formatRemaining(ms) {
        var total = Math.max(0, Math.floor(ms / 1000));
        var h = Math.floor(total / 3600);
        var m = Math.floor((total % 3600) / 60);
        var s = total % 60;
        if (h > 0) return h + " h " + String(m).padStart(2, "0") + " min";
        if (m > 0) return m + " min " + String(s).padStart(2, "0") + " s";
        return s + " s";
    }

    // Replaces the card's actions (and its timer) by "Délai expiré".
    function expire(item) {
        item.setAttribute("data-closed", "warning");
        item.removeAttribute("data-urgent");
        $("[data-actions]", item).hidden = true;
        $("[data-decline-confirm]", item).hidden = true;
        $all("[data-timer]", item).forEach(function (el) {
            el.hidden = true;
        });
        var alert = document.createElement("div");
        alert.className = "alert alert--warning";
        var body = document.createElement("div");
        var title = document.createElement("p");
        title.className = "alert__title";
        title.textContent = "Délai expiré";
        var text = document.createElement("p");
        text.textContent = "Ce mandat a été proposé à un autre agent.";
        body.appendChild(title);
        body.appendChild(text);
        alert.appendChild(body);
        $("article", item).appendChild(alert);
    }

    function initAssignments() {
        var list = $("[data-assignments]");
        if (!list) return;
        var status = $("[data-assignments-status]");
        var items = $all("[data-assignment]", list);

        items.forEach(function (item) {
            var actions = $("[data-actions]", item);
            var confirm = $("[data-decline-confirm]", item);

            $("[data-decline]", item).addEventListener("click", function (event) {
                event.preventDefault();
                actions.hidden = true;
                confirm.hidden = false;
                $("[data-decline-cancel]", item).focus();
            });

            $("[data-decline-cancel]", item).addEventListener("click", function (event) {
                event.preventDefault();
                confirm.hidden = true;
                actions.hidden = false;
                $("[data-decline]", item).focus();
            });

            $all("form", item).forEach(function (form) {
                form.addEventListener("submit", function () {
                    $all("button", form).forEach(function (button) {
                        button.disabled = true;
                    });
                });
            });
        });

        function tick() {
            var now = Date.now();
            items.forEach(function (item) {
                if (item.hasAttribute("data-closed")) return;
                var left = Number(item.getAttribute("data-expires-at")) - now;
                if (left <= 0) {
                    expire(item);
                    announce(status, "Un mandat a expiré.");
                    return;
                }
                $all("[data-timer-value]", item).forEach(function (el) {
                    el.textContent = formatRemaining(left);
                });
                if (left < URGENT_MS) item.setAttribute("data-urgent", "");
            });
        }

        tick();
        window.setInterval(tick, 1000);
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (document.body.getAttribute("data-page") === "agent-home") initAssignments();
    });
})();
