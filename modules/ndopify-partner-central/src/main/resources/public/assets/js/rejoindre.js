/*
 * Ndopify Partner Central — "Rejoindre Ndopify" wizard (templates/rejoindre.html).
 * The server drives the wizard (JoinController): it renders one step at a time, validates what each step posts and
 * keeps the answers in the HTTP session. This script only enhances the current step's fields, and every step still
 * posts without it:
 * - phone numbers: intl-tel-input formats them and posts E.164 ("+237677123456");
 * - city: a slim-select type-ahead over GET /locations?type=CITY&q=…;
 * - neighbourhoods: the chosen city's, loaded from GET /locations?type=NEIGHBORHOOD&parent-id=…, at most 3;
 * - "Utiliser mon numéro WhatsApp" fills the Mobile Money field and locks it;
 * - the review's submit button can't be pressed twice.
 */
(function () {
    "use strict";

    var MAX_NEIGHBORHOODS = 3;
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
     * A slim-select dropdown over a native <select> (DESIGN.md › Form fields — styled in rejoindre.css).
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

    /* ---------- Phone fields ---------- */
    /*
     * Binds intl-tel-input to every phone field of the step once its utils.js is in. The visible input gives its
     * name to a hidden input carrying E.164, so the form posts exactly one value. Until then — or for good, if
     * utils.js can't load — the visible input keeps its name and posts what was typed: the server validates it.
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
                    var phone = window.NdUtil.bindPhone(input, {
                        name: name,
                        value: input.value.trim(),
                        i18n: i18n,
                        // MTN Mobile Money and Orange Money accounts here are Cameroonian: locked to +237.
                        onlyCountries: name === "mobileMoneyNumber" ? ["cm"] : undefined
                    });
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
        if (field.phone.isEmpty() || field.input.readOnly) return ""; // empty: the server's message
        if (field.phone.e164()) return "";
        return PHONE_ERRORS[field.phone.validationError()] || PHONE_INVALID;
    }

    /* ---------- Step: secteur ---------- */
    function initSector(form) {
        var url = form.getAttribute("data-locations-url");
        var citySelect = $("#cityId", form);
        var neighborhoodSelect = $("#neighborhoodIds", form);
        var group = $('[data-group="neighborhoodIds"]', form);
        var empty = $("[data-neighborhoods-empty]", form);
        var counter = $("[data-neighborhood-counter]", form);
        var status = $("[data-neighborhood-status]", form);
        var neighborhoodError = $("#neighborhoodIds-error", form);

        // The server rendered the chosen neighbourhoods only. Read them before slim-select takes over the <select>.
        var initial = $all("option:checked", neighborhoodSelect).map(function (option) {
            return option.value;
        });

        function toOption(location, selected) {
            return { value: String(location.id), text: location.name, selected: !!selected };
        }

        var city = slimSelect(citySelect, {
            placeholderText: "Choisissez une ville",
            ariaLabel: "Ville principale d'intervention",
            searchPlaceholder: "Tapez le nom de votre ville…"
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
            placeholderText: "Choisissez vos quartiers",
            ariaLabel: "Quartiers",
            maxSelected: MAX_NEIGHBORHOODS,
            closeOnSelect: false
        });

        function selected() {
            return neighborhoods.slim.getSelected();
        }

        // slim-select applies setData() asynchronously: a fresh list passes its own count.
        function updateCounter(announceIt, count) {
            if (count === undefined) count = selected().length;
            counter.textContent = count + " sur " + MAX_NEIGHBORHOODS + " sélectionné" + (count > 1 ? "s" : "");
            if (announceIt && count === MAX_NEIGHBORHOODS) {
                announce(status, "Vous avez choisi " + MAX_NEIGHBORHOODS + " quartiers, le maximum.");
            }
        }

        // Each city change loads that city's neighbourhoods once; typing then filters them in the browser.
        var pending = 0;

        function loadNeighborhoods(keep) {
            var cityId = citySelect.value;
            var request = ++pending;
            empty.hidden = !!cityId;
            group.hidden = !cityId;
            if (!cityId) {
                neighborhoods.slim.setData([]);
                updateCounter(false, 0);
                return;
            }
            neighborhoods.slim.disable();
            searchAllLocations(url, { type: "NEIGHBORHOOD", "parent-id": cityId }).then(
                function (locations) {
                    if (request !== pending) return; // the city changed again meanwhile
                    var options = locations.map(function (location) {
                        return toOption(location, keep.indexOf(String(location.id)) !== -1);
                    });
                    neighborhoods.slim.setData(options);
                    neighborhoods.slim.enable();
                    updateCounter(false, options.filter(function (option) {
                        return option.selected;
                    }).length);
                },
                function () {
                    if (request !== pending) return;
                    neighborhoods.slim.enable();
                    setFieldError(neighborhoods.control, neighborhoodError,
                        "Nous ne pouvons pas charger les quartiers pour l'instant. Rechargez la page.");
                }
            );
        }

        loadNeighborhoods(initial);
        citySelect.addEventListener("change", function () {
            loadNeighborhoods([]);
            setFieldError(neighborhoods.control, neighborhoodError, "");
            setFieldError(city.control, $("#cityId-error", form), "");
        });
        neighborhoodSelect.addEventListener("change", function () {
            updateCounter(true);
            if (selected().length) setFieldError(neighborhoods.control, neighborhoodError, "");
        });
    }

    /* ---------- Step: paiement ---------- */
    function initSameAsWhatsapp(form, phones) {
        var same = $("#sameAsWhatsapp", form);
        var momo = phones.filter(function (field) {
            return field.input.id === "mobileMoneyNumber";
        })[0];
        var whatsapp = $("[data-whatsapp-number]", form);
        if (!same) return;

        function sync(moveFocus) {
            var input = momo ? momo.input : $("#mobileMoneyNumber", form);
            var number = whatsapp.getAttribute("data-e164");
            // Checked, the server takes the WhatsApp number from the session: the field just shows it.
            if (same.checked) {
                if (momo) momo.phone.set(number);
                else input.value = number;
                setFieldError(input, $("#mobileMoneyNumber-error", form), "");
            } else if (moveFocus) {
                input.focus();
            }
            input.readOnly = same.checked;
        }

        same.addEventListener("change", function () {
            sync(true);
        });
        sync(false);
    }

    /* ---------- Page ---------- */
    function initJoin() {
        var form = $("[data-join-form]");
        if (!form) return; // the confirmation

        // Enter in a dropdown's search box picks a result; it mustn't submit the step.
        form.addEventListener("keydown", function (event) {
            if (event.key === "Enter" && event.target.closest(".iti__dropdown-content, .ss-search")) {
                event.preventDefault();
            }
        });

        var phones = [];
        initPhones(form).then(function (fields) {
            phones = fields;
            initSameAsWhatsapp(form, phones);
        });
        // Shown numbers (review, "Utiliser mon numéro WhatsApp") in the international format, once utils.js is in.
        window.NdUtil.phoneReady().then(function () {
            $all("[data-phone], [data-whatsapp-number]").forEach(function (el) {
                el.textContent = window.NdUtil.formatPhone(el.textContent.trim());
            });
        }, function () {
            /* E.164 stays readable */
        });

        if ($("#cityId", form)) initSector(form);

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
                announce($("[data-sending]", form), "Envoi de votre candidature en cours.");
            }
        });
    }

    // Back from the next page restores this one from the cache: the submit button must work again.
    window.addEventListener("pageshow", function (event) {
        var submit = $("[data-submit]");
        if (!event.persisted || !submit) return;
        submit.removeAttribute("aria-disabled");
        submit.textContent = "Envoyer ma candidature";
    });

    document.addEventListener("DOMContentLoaded", function () {
        if (document.body.getAttribute("data-page") === "join") initJoin();
    });
})();
