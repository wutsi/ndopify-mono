/*
 * Ndopify Partner Central — the mandate page (templates/mandate.html) and its edit page (templates/mandate/edit.html).
 * Both work without this file: the forms post to MandateController, "Fermer" / "Annuler" open their form through
 * ?end=close|cancel, and the edit page shows every follow-up question. This script only enhances them:
 * - mandate page: "Fermer le mandat" / "Annuler le mandat" open their form in place, "Retour" closes it; the form is
 *   checked before posting, with the server's messages; a button that posted is disabled ("Envoi…", "Fermeture…",
 *   "Annulation…"), so a double click doesn't post twice; the outcome of the last action gets the focus;
 * - edit page: the types of the chosen category only, the follow-up questions of the type only, each folded group's
 *   summary kept up to date, the neighbourhoods a slim-select over the mandate's city (GET /locations, at most 3),
 *   amounts formatted as typed.
 * Ported from the prototype (src/main/html/assets/js/partner-central.js: initMandate, initMandateEdit).
 */
(function () {
    "use strict";

    var MAX_NEIGHBORHOODS = 3;
    // LocationController's cap: neighbourhoods are fetched page by page.
    var PAGE_SIZE = 50;
    var MAX_PAGES = 10;
    var SUMMARY_LENGTH = 40;

    // FindPropertyRules: the follow-up questions of each type.
    var WITHOUT_ROOMS = ["STUDIO", "ROOM"];
    var WITHOUT_FURNITURE = ["LAND", "BUILDING", "INDUSTRIAL"];
    var WITHOUT_PARKINGS = ["LAND", "ROOM"];

    // MandateLabels.
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
    var MOVING_LABELS = {
        WITHIN_1_MONTH: "Dans moins d'1 mois",
        WITHIN_3_MONTHS: "Dans 1 à 3 mois",
        AFTER_3_MONTHS: "Dans plus de 3 mois"
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

    function setFieldError(target, errorEl, message) {
        if (!errorEl || !target) return;
        if (message) {
            errorEl.querySelector("[data-error-text]").textContent = message;
            errorEl.hidden = false;
            target.setAttribute("aria-invalid", "true");
        } else {
            errorEl.hidden = true;
            target.removeAttribute("aria-invalid");
        }
    }

    // "150 000" → 150000; null when empty, NaN when not a positive whole number (spaces and dots as separators).
    function parseAmount(value) {
        var digits = String(value).replace(/[\s.  ]/g, "");
        if (!digits) return null;
        return /^\d+$/.test(digits) && parseInt(digits, 10) > 0 ? parseInt(digits, 10) : NaN;
    }

    function formatNumber(amount) {
        return amount.toLocaleString("fr-FR");
    }

    // The tenant's currency ("FCFA"), from the page's body (data-currency).
    function currency() {
        return document.body.getAttribute("data-currency") || "";
    }

    // 150000 → "150 000 FCFA / mois", close to MandateLabels.price (the tenant's monetaryFormat is server-side only).
    function formatRent(amount) {
        return formatNumber(amount) + "\u00a0" + currency() + " / mois";
    }

    // A form's submit button, once pressed: disabled and relabelled, so the form can't be posted twice.
    function guardSubmit(form, label) {
        var button = $('[type="submit"]', form);
        if (!button) return;
        form.addEventListener("submit", function (event) {
            if (event.defaultPrevented) return;
            if (button.getAttribute("aria-disabled") === "true") {
                event.preventDefault();
                return;
            }
            button.setAttribute("aria-disabled", "true");
            button.textContent = label;
        });
    }

    /* ---------- mandate.html ---------- */
    function initMandate() {
        var flash = $("[data-mandate-flash]");
        if (flash) flash.focus();

        $all("[data-request-approval-form]").forEach(function (form) {
            guardSubmit(form, "Envoi…");
        });

        // "Voir tous les critères (n)" / "Masquer les critères".
        var more = $("[data-mandate-more]");
        if (more) {
            var moreLabel = $("[data-mandate-more-label]", more);
            var count = moreLabel.getAttribute("data-count");
            more.addEventListener("toggle", function () {
                moreLabel.textContent = more.open ? "Masquer les critères" : "Voir tous les critères (" + count + ")";
            });
        }

        initEnd();
    }

    /* Fermer (lease signed) and Annuler: each button opens its form in place, as ?end= does without JS. */
    function initEnd() {
        var end = $("[data-end]");
        if (!end) return;
        var buttons = $("[data-end-buttons]", end);
        var openers = $all("[data-end-open]", end);
        var opener = null;

        function formOf(mode) {
            return $('[data-end-form="' + mode + '"]', end);
        }

        function open(link) {
            opener = link;
            var mode = link.getAttribute("data-end-open");
            $all("[data-end-form]", end).forEach(function (form) {
                form.hidden = form.getAttribute("data-end-form") !== mode;
            });
            buttons.hidden = true;
            link.setAttribute("aria-expanded", "true");
            var first = $("input, textarea", formOf(mode));
            if (first) first.focus();
        }

        function close() {
            $all("[data-end-form]", end).forEach(function (form) {
                form.hidden = true;
            });
            buttons.hidden = false;
            openers.forEach(function (link) {
                link.setAttribute("aria-expanded", "false");
            });
            if (opener) opener.focus();
        }

        openers.forEach(function (link) {
            var form = formOf(link.getAttribute("data-end-open"));
            link.setAttribute("aria-expanded", form && !form.hidden ? "true" : "false");
            link.setAttribute("role", "button");
            link.addEventListener("click", function (event) {
                event.preventDefault();
                open(link);
            });
        });

        $all("[data-end-back]", end).forEach(function (back) {
            back.addEventListener("click", function (event) {
                event.preventDefault();
                close();
            });
        });

        // Opened by the server (?end=, or an error): focus its first invalid field, else its first field.
        $all("[data-end-form]", end).forEach(function (form) {
            if (form.hidden) return;
            opener = $('[data-end-open="' + form.getAttribute("data-end-form") + '"]', end);
            var invalid = $('[aria-invalid="true"] input, input[aria-invalid="true"], textarea[aria-invalid="true"]', form);
            (invalid || $("input, textarea", form)).focus();
        });

        var closeForm = formOf("close");
        if (closeForm) {
            var price = $("#finalPrice", closeForm);
            var priceError = $("#finalPrice-error", closeForm);
            price.addEventListener("input", function () {
                setFieldError(price, priceError, null);
            });
            closeForm.addEventListener("submit", function (event) {
                if (!(parseAmount(price.value) > 0)) {
                    event.preventDefault();
                    setFieldError(price, priceError, "Indiquez le loyer convenu, en " + currency() + " par mois.");
                    price.focus();
                }
            });
            guardSubmit(closeForm, "Fermeture…");
        }

        var cancelForm = formOf("cancel");
        if (cancelForm) {
            var codes = $all('[name="cancellationCode"]', cancelForm);
            var fieldset = codes[0].closest("fieldset");
            var codeError = $("#cancellationCode-error", cancelForm);
            codes.forEach(function (radio) {
                radio.addEventListener("change", function () {
                    setFieldError(fieldset, codeError, null);
                });
            });
            cancelForm.addEventListener("submit", function (event) {
                if (!$('[name="cancellationCode"]:checked', cancelForm)) {
                    event.preventDefault();
                    setFieldError(fieldset, codeError, "Choisissez la raison de l'annulation.");
                    codes[0].focus();
                }
            });
            guardSubmit(cancelForm, "Annulation…");
        }
    }

    /* ---------- mandate/edit.html ---------- */
    function initMandateEdit() {
        var form = $("[data-edit-form]");
        if (!form) return;

        function checked(name) {
            var input = $('[name="' + name + '"]:checked', form);
            return input ? input.value : null;
        }

        function group(name) {
            return $('[data-group="' + name + '"]', form);
        }

        // The types of the category; rooms for a home, furniture and parking as FindPropertyRules asks them.
        function syncProperty() {
            var category = checked("propertyCategory");
            $all('[name="propertyType"]', form).forEach(function (input) {
                var label = input.closest("label");
                var fits = !category || label.getAttribute("data-categories").split(" ").indexOf(category) !== -1;
                label.hidden = !fits;
                if (!fits) input.checked = false;
            });
            var type = checked("propertyType");
            if (type === "ANY") type = null;
            group("rooms").hidden = category !== "RESIDENTIAL" || WITHOUT_ROOMS.indexOf(type) !== -1;
            group("furnitureType").hidden = WITHOUT_FURNITURE.indexOf(type) !== -1;
            group("parkings").hidden = WITHOUT_PARKINGS.indexOf(type) !== -1;
        }

        var neighborhoods = initNeighborhoods(form, syncSummaries);

        /* Each folded group shows its current value (MandateEditRules.summaries). */
        var SUMMARIES = {
            sector: function () {
                var names = neighborhoods.names();
                return names.length ? names.join(", ") : "Toute la ville";
            },
            property: function () {
                var parts = [PROPERTY_TYPE_LABELS[checked("propertyType")] || CATEGORY_LABELS[checked("propertyCategory")]];
                var rooms = !group("rooms").hidden && checked("rooms");
                if (rooms) parts.push(rooms + (rooms === "1" ? " chambre" : " chambres"));
                var area = parseAmount(form.elements.area.value);
                if (area > 0) parts.push(area + " m²");
                return parts.filter(Boolean).join(" · ");
            },
            amenities: function () {
                var count = $all('[name="amenities"]:checked', form).length;
                if (!count) return "Aucun critère";
                return count + (count > 1 ? " critères" : " critère");
            },
            budget: function () {
                var price = parseAmount(form.elements.priceAmount.value);
                var parts = [price > 0 ? formatRent(price) : "Montant à indiquer"];
                if (checked("movingPeriod")) parts.push(MOVING_LABELS[checked("movingPeriod")]);
                return parts.join(" · ");
            },
            comments: function () {
                var text = form.elements.comments.value.trim();
                if (!text) return "Aucune";
                return text.length > SUMMARY_LENGTH ? text.slice(0, SUMMARY_LENGTH) + "…" : text;
            }
        };

        function syncSummaries() {
            $all("[data-edit-group]", form).forEach(function (details) {
                var summary = SUMMARIES[details.getAttribute("data-edit-group")];
                if (summary) $("[data-group-value]", details).textContent = summary();
            });
        }

        // "150000" → "150 000" as it's typed; anything else is left for the server to refuse.
        var price = form.elements.priceAmount;
        price.addEventListener("input", function () {
            var amount = parseAmount(price.value);
            if (amount > 0) price.value = formatNumber(amount);
        });

        form.addEventListener("change", function (event) {
            var name = event.target.name;
            if (name === "propertyCategory" || name === "propertyType") syncProperty();
            var error = $("#" + name + "-error", form);
            if (error) setFieldError(event.target.closest("fieldset") || event.target, error, null);
            syncSummaries();
        });
        form.addEventListener("input", syncSummaries);
        guardSubmit(form, "Enregistrement…");

        syncProperty();
        syncSummaries();

        // A group with an error was opened by the server: focus its first invalid field.
        var invalid = $('[aria-invalid="true"]', form);
        if (invalid) {
            var input = invalid.matches("input, textarea, select") ? invalid : $("input, textarea, select", invalid);
            if (input) input.focus();
        }
    }

    /* GET /locations with the server's parameter names (parent-id, type, limit, offset). */
    function searchLocations(url, params) {
        var query = new URLSearchParams();
        Object.keys(params).forEach(function (key) {
            query.append(key, params[key]);
        });
        return window.fetch(url + "?" + query.toString(), {
            headers: { Accept: "application/json" },
            credentials: "same-origin"
        }).then(function (response) {
            if (!response.ok) throw new Error("GET /locations: " + response.status);
            return response.json();
        }).then(function (body) {
            return body.locations || [];
        });
    }

    function searchAllLocations(url, params) {
        var all = [];

        function page(index) {
            return searchLocations(url, Object.assign({ limit: PAGE_SIZE, offset: index * PAGE_SIZE }, params))
                .then(function (locations) {
                    all = all.concat(locations);
                    return locations.length === PAGE_SIZE && index + 1 < MAX_PAGES ? page(index + 1) : all;
                });
        }

        return page(0);
    }

    // Accent- and case-insensitive "contains", so "ekelle" finds "Ngoa-Ekellé".
    function fold(text) {
        return String(text).normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
    }

    /*
     * The mandate's neighbourhoods: without slim-select (or until its city's list loads) the <select> holds the
     * chosen ones only, which can be kept or removed. Returns the chosen names, for the sector's summary.
     */
    function initNeighborhoods(form, onChange) {
        var select = $("#neighborhoodIds", form);
        var counter = $("[data-neighborhood-counter]", form);
        var status = $("[data-neighborhood-status]", form);
        var error = $("#neighborhoodIds-error", form);

        function chosen() {
            return $all("option:checked", select).map(function (option) {
                return option.textContent.trim();
            });
        }

        function updateCounter(announceIt) {
            var count = chosen().length;
            counter.textContent = count + " sur " + MAX_NEIGHBORHOODS + " sélectionné" + (count > 1 ? "s" : "");
            if (announceIt && count === MAX_NEIGHBORHOODS) {
                announce(status, "Vous avez choisi " + MAX_NEIGHBORHOODS + " quartiers, le maximum.");
            }
        }

        select.addEventListener("change", function () {
            updateCounter(true);
            onChange();
        });

        var cityId = select.getAttribute("data-city-id");
        var url = form.getAttribute("data-locations-url");
        if (!window.SlimSelect || !cityId || !url) return { names: chosen };

        var initial = $all("option:checked", select).map(function (option) {
            return option.value;
        });
        var slim = new window.SlimSelect({
            select: select,
            settings: {
                placeholderText: "Toute la ville",
                ariaLabel: "Quartiers",
                searchPlaceholder: "Rechercher…",
                searchText: "Aucun résultat",
                searchingText: "Recherche…",
                removeText: "Retirer",
                deselectText: "Effacer",
                maxValuesMessage: "{number} sélectionnés",
                maxSelected: MAX_NEIGHBORHOODS,
                closeOnSelect: false,
                modal: "off",
                openPosition: "down",
                contentLocation: select.parentNode
            },
            events: {
                searchFilter: function (option, search) {
                    return fold(option.text).indexOf(fold(search)) !== -1;
                }
            }
        });
        var control = slim.render.main.main;
        control.setAttribute("aria-describedby", select.getAttribute("aria-describedby"));
        select.removeAttribute("aria-describedby");
        if (select.getAttribute("aria-invalid") === "true") control.setAttribute("aria-invalid", "true");

        slim.disable();
        searchAllLocations(url, { type: "NEIGHBORHOOD", "parent-id": cityId }).then(
            function (locations) {
                slim.setData(locations.map(function (location) {
                    var value = String(location.id);
                    return { value: value, text: location.name, selected: initial.indexOf(value) !== -1 };
                }));
                slim.enable();
            },
            function () {
                slim.enable();
                setFieldError(control, error, "Nous ne pouvons pas charger les quartiers pour l'instant. Rechargez la page.");
            }
        );
        select.addEventListener("change", function () {
            setFieldError(control, error, null);
        });

        return { names: chosen };
    }

    document.addEventListener("DOMContentLoaded", function () {
        var page = document.body.getAttribute("data-page");
        if (page === "mandate") initMandate();
        if (page === "mandate-edit") initMandateEdit();
    });
})();
