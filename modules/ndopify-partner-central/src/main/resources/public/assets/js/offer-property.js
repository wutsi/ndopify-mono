/*
 * Ndopify Partner Central — "Proposer un bien" wizard (templates/offer-property.html).
 * The server drives the wizard (OfferPropertyController): it renders one step at a time, validates what each step
 * posts and keeps the answers in the HTTP session. This script only enhances the current step's fields, and every
 * step still posts without it:
 * - address: a slim-select type-ahead over GET /locations?type=CITY&q=…, then the chosen city's neighbourhoods
 *   (GET /locations?type=NEIGHBORHOOD&parent-id=…), a single choice;
 * - terms: the rent as "150 000" as you type (the server reads the digits);
 * - features: shows only the questions that apply to the chosen type (furnishing, rooms, parking, and the number of
 *   places only after "Oui"), disables the hidden ones so they don't post; the surface of a commercial property
 *   is formatted like the rent;
 * - contact: intl-tel-input formats the WhatsApp number and posts E.164 ("+237677123456");
 * - "Suivant" stays inactive until every required field of the step is filled (the server still validates them);
 * - the review's submit button can't be pressed twice.
 */
(function () {
    "use strict";

    var MIN_CITY_SEARCH = 2;
    // LocationController's cap: neighbourhoods are fetched page by page.
    var PAGE_SIZE = 50;
    var MAX_PAGES = 10;

    var PHONE_ERRORS = {
        1: "Cet indicatif de pays n'existe pas. Choisissez votre pays dans la liste.",
        2: "Ce numéro est trop court. Vérifiez qu'il ne manque pas de chiffre.",
        3: "Ce numéro est trop long. Vérifiez qu'il n'y a pas de chiffre en trop."
    };
    var PHONE_INVALID = "Ce numéro n'est pas un numéro mobile valide. Exemple : 6 77 12 34 56";

    // Same rules as OfferPropertyRules (needsFurniture, needsRooms, needsParking, needsArea). The server renders
    // only the chosen category's types, so a residential form has the residential questions and a commercial one
    // the surface (always asked): only the type narrows the residential questions down.
    var WITHOUT_ROOMS = ["ROOM", "STUDIO", "BUILDING"];
    var WITHOUT_FURNITURE = ["BUILDING"];
    var WITHOUT_PARKING = ["ROOM"];

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
        if (!errorEl) return;
        if (message) {
            errorEl.querySelector("[data-error-text]").textContent = message;
            errorEl.hidden = false;
            target.setAttribute("aria-invalid", "true");
        } else {
            errorEl.hidden = true;
            target.removeAttribute("aria-invalid");
        }
    }

    // Accent- and case-insensitive "contains", so "ekelle" finds "Ngoa-Ekellé" and "mbi" finds "Mvog-Mbi".
    function fold(text) {
        return String(text).normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
    }

    /* GET /locations with the server's parameter names (q, id, parent-id, type, limit, offset). */
    function searchLocations(url, params) {
        var query = new URLSearchParams();
        Object.keys(params).forEach(function (key) {
            [].concat(params[key]).forEach(function (value) {
                query.append(key, value);
            });
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

    /* Every location matching params, PAGE_SIZE at a time (a big city has more neighbourhoods than one page). */
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

    /*
     * A slim-select dropdown over a native <select> (DESIGN.md › Form fields — styled in join.css).
     * The <select> stays the source of truth (it's what the form posts) and keeps firing "change"; slim-select's
     * combobox becomes the field's visible, focusable control, so it takes over the error description and state.
     */
    function slimSelect(select, settings, events) {
        var slim = new window.SlimSelect({
            select: select,
            settings: Object.assign({
                searchPlaceholder: "Rechercher…",
                searchText: "Aucun résultat",
                searchingText: "Recherche…",
                resultsText: "{count} résultat(s)",
                removeText: "Retirer",
                deselectText: "Effacer",
                maxValuesMessage: "{number} sélectionnés",
                // The dropdown stays anchored under the field on phones too, instead of a full-screen modal.
                modal: "off",
                openPosition: "down",
                contentLocation: select.parentNode
            }, settings),
            events: Object.assign({
                searchFilter: function (option, search) {
                    return fold(option.text).indexOf(fold(search)) !== -1;
                }
            }, events)
        });
        var control = slim.render.main.main;
        control.setAttribute("aria-describedby", select.getAttribute("aria-describedby"));
        select.removeAttribute("aria-describedby");
        // An error the server rendered on the <select> belongs to the visible control now.
        if (select.getAttribute("aria-invalid") === "true") control.setAttribute("aria-invalid", "true");
        return { slim: slim, control: control };
    }

    /* ---------- "Suivant" ---------- */
    // aria-disabled (not the disabled attribute) keeps the button in the tab order, so its hint can still be reached.
    function setActive(button, active) {
        if (active) button.removeAttribute("aria-disabled");
        else button.setAttribute("aria-disabled", "true");
    }

    function isFilled(form, field) {
        if (field.type === "radio" || field.type === "checkbox") {
            return !!form.querySelector('input[name="' + field.name + '"]:checked');
        }
        if (field.multiple) return field.selectedOptions.length > 0;
        return field.value.trim() !== "";
    }

    /*
     * Keeps the step's [data-next] in sync with its enabled [required] fields — read on every refresh, since the
     * property step makes questions required or not as the type changes — and inactive while a field is
     * [data-loading] (slim-select disables it meanwhile, which would drop it from the required ones). Returns the
     * refresh, for the changes no input/change event reports (a value set by script, slim-select reloading its
     * options).
     */
    function initNext(form) {
        var next = $("[data-next]", form);
        if (!next) return function () {};

        function refresh() {
            setActive(next, !$("[data-loading]", form) &&
                $all("[required]:not(:disabled)", form).every(function (field) {
                    return isFilled(form, field);
                }));
        }

        form.addEventListener("input", refresh);
        form.addEventListener("change", refresh);
        // Enter in a text field submits the form too: not while the step is incomplete.
        form.addEventListener("submit", function (event) {
            if (next.getAttribute("aria-disabled") === "true") event.preventDefault();
        });
        refresh();
        return refresh;
    }

    /* ---------- Whole numbers (budget, surface): "150 000" as you type ---------- */
    function digitsOf(value) {
        return String(value || "").replace(/\D/g, "").replace(/^0+(?=\d)/, "");
    }

    function formatNumber(digits) {
        return digits ? Number(digits).toLocaleString("fr-FR") : "";
    }

    // Regroups the digits by thousands while typing, keeping the caret after the same digit.
    function bindNumber(input, maxDigits) {
        if (!input) return;
        input.addEventListener("input", function () {
            var caretDigits = digitsOf(input.value.slice(0, input.selectionStart)).length;
            var formatted = formatNumber(digitsOf(input.value).slice(0, maxDigits));
            var pos = 0;
            for (var seen = 0; pos < formatted.length && seen < caretDigits; pos++) {
                if (/\d/.test(formatted.charAt(pos))) seen++;
            }
            input.value = formatted;
            input.setSelectionRange(pos, pos);
        });
    }

    /* ---------- Phone field ---------- */
    /*
     * Binds intl-tel-input to the step's phone field once its utils.js is in. The visible input gives its name to a
     * hidden input carrying E.164, so the form posts exactly one value. Until then — or for good, if utils.js can't
     * load — the visible input keeps its name and posts what was typed: the server validates it.
     */
    function initPhones(form) {
        var inputs = $all('input[type="tel"][name]', form);
        if (!inputs.length) return Promise.resolve([]);

        return window.NdUtil.phoneReady().then(
            function (i18n) {
                return inputs.map(function (input) {
                    var name = input.getAttribute("name");
                    var hidden = document.createElement("input");
                    hidden.type = "hidden";
                    hidden.name = name;
                    input.removeAttribute("name");
                    input.insertAdjacentElement("afterend", hidden);
                    var phone = window.NdUtil.bindPhone(input, { name: name, value: input.value.trim(), i18n: i18n });
                    return { input: input, phone: phone, error: $("#" + input.id + "-error") };
                });
            },
            function () {
                $all("[data-phone-unavailable]", form).forEach(function (el) {
                    el.hidden = false;
                });
                return [];
            }
        );
    }

    function phoneError(field) {
        if (field.phone.isEmpty()) return ""; // empty: the server's message
        if (field.phone.e164()) return "";
        return PHONE_ERRORS[field.phone.validationError()] || PHONE_INVALID;
    }

    /* ---------- Step: address ---------- */
    function initAddress(form, refresh) {
        var url = form.getAttribute("data-locations-url");
        var citySelect = $("#cityId", form);
        var neighborhoodSelect = $("#neighborhoodId", form);
        var group = $('[data-group="neighborhoodId"]', form);
        var empty = $("[data-neighborhoods-empty]", form);
        var neighborhoodError = $("#neighborhoodId-error", form);

        // The server rendered the chosen neighbourhood only. Read it before slim-select takes over the <select>.
        var initial = neighborhoodSelect.value;

        function toOption(location, selected) {
            return { value: String(location.id), text: location.name, selected: !!selected };
        }

        var city = slimSelect(citySelect, {
            placeholderText: "Choisissez une ville",
            ariaLabel: "Ville",
            searchPlaceholder: "Tapez le nom de la ville…"
        }, {
            // Thousands of cities: the server searches them (accent-insensitive prefix), not the browser.
            search: function (search) {
                if (search.trim().length < MIN_CITY_SEARCH) {
                    return Promise.reject("Tapez au moins " + MIN_CITY_SEARCH + " lettres.");
                }
                return searchLocations(url, { type: "CITY", q: search.trim(), limit: 20 }).then(
                    function (locations) {
                        return locations.map(function (location) {
                            return toOption(location, String(location.id) === citySelect.value);
                        });
                    },
                    function () {
                        return Promise.reject("Recherche impossible pour le moment. Réessayez.");
                    }
                );
            }
        });
        var neighborhoods = slimSelect(neighborhoodSelect, {
            placeholderText: "Choisissez le quartier",
            ariaLabel: "Quartier"
        });

        // Each city change loads that city's neighbourhoods once; typing then filters them in the browser.
        var pending = 0;

        function loadNeighborhoods(keep) {
            var cityId = citySelect.value;
            var request = ++pending;
            empty.hidden = !!cityId;
            group.hidden = !cityId;
            if (!cityId) {
                neighborhoods.slim.setData([{ text: "Choisissez le quartier", placeholder: true, value: "" }]);
                refresh();
                return;
            }
            neighborhoodSelect.setAttribute("data-loading", "");
            neighborhoods.slim.disable();
            refresh();
            searchAllLocations(url, { type: "NEIGHBORHOOD", "parent-id": cityId }).then(
                function (locations) {
                    if (request !== pending) return; // the city changed again meanwhile
                    neighborhoodSelect.removeAttribute("data-loading");
                    var options = locations.map(function (location) {
                        return toOption(location, String(location.id) === keep);
                    });
                    neighborhoods.slim.setData([{ text: "Choisissez le quartier", placeholder: true, value: "" }]
                        .concat(options));
                    neighborhoods.slim.enable();
                    refresh();
                },
                function () {
                    if (request !== pending) return;
                    neighborhoodSelect.removeAttribute("data-loading");
                    neighborhoods.slim.enable();
                    refresh();
                    setFieldError(neighborhoods.control, neighborhoodError,
                        "Nous ne pouvons pas charger les quartiers pour l'instant. Rechargez la page.");
                }
            );
        }

        loadNeighborhoods(initial);
        citySelect.addEventListener("change", function () {
            loadNeighborhoods("");
            refresh();
            setFieldError(neighborhoods.control, neighborhoodError, "");
            setFieldError(city.control, $("#cityId-error", form), "");
        });
        neighborhoodSelect.addEventListener("change", function () {
            refresh();
            if (neighborhoodSelect.value) setFieldError(neighborhoods.control, neighborhoodError, "");
        });
    }

    /* ---------- Step: features ---------- */
    function initFeatures(form, refresh) {
        var groups = {
            furnitureType: $('[data-group="furnitureType"]', form),
            rooms: $('[data-group="rooms"]', form),
            hasParking: $('[data-group="hasParking"]', form),
            parkings: $('[data-group="parkings"]', form)
        };
        var areaLabel = $("[data-area-label]", form);

        function checkedValue(name) {
            var input = $('input[name="' + name + '"]:checked', form);
            return input ? input.value : "";
        }

        // A hidden question is disabled (it doesn't post) and never in error; a shown one gates "Suivant".
        function show(key, shown) {
            var group = groups[key];
            if (!group) return; // only rendered for the category that asks it
            group.hidden = !shown;
            $all("input", group).forEach(function (input) {
                input.disabled = !shown;
                input.required = shown;
            });
            if (!shown) setFieldError(group, $("#" + key + "-error", form), "");
        }

        function updateQuestions() {
            var type = checkedValue("propertyType");
            show("furnitureType", WITHOUT_FURNITURE.indexOf(type) === -1);
            show("rooms", WITHOUT_ROOMS.indexOf(type) === -1);
            show("hasParking", WITHOUT_PARKING.indexOf(type) === -1);
            // The number of places only after "Oui" (the server reads "Non" as 0 places).
            show("parkings", WITHOUT_PARKING.indexOf(type) === -1 && checkedValue("hasParking") === "YES");
            if (areaLabel) areaLabel.textContent = type === "LAND" ? "Surface du terrain estimée" : "Surface estimée";
            refresh();
        }

        $all('input[name="propertyType"], input[name="hasParking"]', form).forEach(function (input) {
            input.addEventListener("change", updateQuestions);
        });
        updateQuestions();
    }

    /* ---------- Page ---------- */
    function initOfferProperty() {
        // Shown numbers (review, confirmation) in the international format, once utils.js is in.
        window.NdUtil.phoneReady().then(function () {
            $all("[data-phone]").forEach(function (el) {
                el.textContent = window.NdUtil.formatPhone(el.textContent.trim());
            });
        }, function () {
            /* E.164 stays readable */
        });

        var form = $("[data-offer-form]");
        if (!form) return; // the confirmation

        // Enter in a dropdown's search box picks a result; it mustn't submit the step.
        form.addEventListener("keydown", function (event) {
            if (event.key === "Enter" && event.target.closest(".iti__dropdown-content, .ss-search")) {
                event.preventDefault();
            }
        });

        var refresh = initNext(form);
        var phones = [];
        initPhones(form).then(function (fields) {
            phones = fields;
        });

        if ($("#cityId", form)) initAddress(form, refresh);
        if ($('[data-group="propertyType"]', form)) initFeatures(form, refresh);
        bindNumber($("#priceAmount", form), 9);
        bindNumber($("#area", form), 7);

        form.addEventListener("submit", function (event) {
            // A number intl-tel-input can't read would reach the server empty: say why here instead.
            var invalid = null;
            phones.forEach(function (field) {
                var message = phoneError(field);
                setFieldError(field.input, field.error, message);
                if (message && !invalid) invalid = field;
            });
            if (invalid) {
                event.preventDefault();
                invalid.input.focus();
                return;
            }

            var submit = $("[data-submit]", form);
            if (submit) {
                if (submit.getAttribute("aria-disabled") === "true") {
                    event.preventDefault();
                    return;
                }
                submit.setAttribute("aria-disabled", "true");
                submit.textContent = "Envoi en cours…";
                announce($("[data-sending]", form), "Enregistrement de votre bien en cours.");
            }
        });
    }

    // Back from the next page restores this one from the cache: the submit button must work again.
    window.addEventListener("pageshow", function (event) {
        var submit = $("[data-submit]");
        if (!event.persisted || !submit) return;
        submit.removeAttribute("aria-disabled");
        submit.textContent = "Enregistrer mon bien";
    });

    document.addEventListener("DOMContentLoaded", function () {
        if (document.body.getAttribute("data-page") === "offer-property") initOfferProperty();
    });
})();
