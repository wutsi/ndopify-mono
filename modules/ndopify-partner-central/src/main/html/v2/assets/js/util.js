/*
 * Ndopify Partner Central — shared helpers, exposed as window.NdUtil.
 * Load after intlTelInput.min.js and before the page scripts that use it (e.g. joindre.js).
 *
 * Phone numbers (DESIGN.md › Form fields › Phone number): intl-tel-input does the work. Its utils.js
 * (libphonenumber) formats as you type, validates, and produces E.164 ("+237677123456") — the only format
 * phone numbers are stored or sent in. Nothing here re-implements that logic.
 */
(function () {
    "use strict";

    // Same version as ndopify-website. jsDelivr, not cdnjs: cdnjs lists 25.11.2 but serves none of its files.
    var ITI_BUILD = "https://cdn.jsdelivr.net/npm/intl-tel-input@25.11.2/build/js/";
    var DEFAULT_COUNTRY = "cm";
    // Cameroon first, then its CEMAC neighbours — same order as ndopify-website.
    var COUNTRY_ORDER = ["cm", "ga", "cg", "td", "cf", "gq"];

    var ready = null;

    /*
     * Loads intl-tel-input's utils.js and its French translations, once per page.
     * Resolves with the translations; formatting, validation and E.164 are available after that.
     * If utils.js cannot be loaded the promise rejects, and phone fields can't be validated.
     */
    function phoneReady() {
        if (!window.intlTelInput) return Promise.reject(new Error("intl-tel-input is not loaded"));
        if (!ready) {
            var utils = window.intlTelInput.attachUtils(function () {
                return import(ITI_BUILD + "utils.js");
            });
            var i18n = import(ITI_BUILD + "i18n/fr/index.js")
                .then(function (module) {
                    return module.default;
                })
                .catch(function () {
                    return {}; // English country names are an acceptable fallback.
                });
            ready = Promise.all([utils || Promise.resolve(), i18n]).then(function (results) {
                return results[1];
            });
        }
        return ready;
    }

    function utils() {
        return window.intlTelInput && window.intlTelInput.utils;
    }

    /* Any spelling of a number ("6 77 12 34 56", "+237 677-12-34-56", "00237…") → E.164, or "" if invalid. */
    function toE164(value, country) {
        var u = utils();
        if (!u || !value) return "";
        if (!u.isValidNumber(value, country || DEFAULT_COUNTRY)) return "";
        return u.formatNumber(value, country || DEFAULT_COUNTRY, u.numberFormat.E164);
    }

    /* E.164 → "+237 6 77 12 34 56" for display. Returns the input unchanged if utils.js isn't loaded. */
    function formatPhone(e164) {
        var u = utils();
        if (!u || !e164) return e164 || "";
        return u.formatNumber(e164, null, u.numberFormat.INTERNATIONAL);
    }

    /*
     * Turns a type="tel" input into an intl-tel-input field. Call after phoneReady() has resolved.
     *   options.name           name of the hidden input that carries the E.164 value (the visible input has no name)
     *   options.value          initial number, in E.164
     *   options.onlyCountries  e.g. ["cm"] to lock the country (the dropdown is then hidden)
     *   options.i18n           translations resolved by phoneReady()
     * The hidden input is kept in sync on every change, not only on native submit, so script-driven flows read it too.
     */
    function bindPhone(input, options) {
        options = options || {};
        var locked = options.onlyCountries && options.onlyCountries.length === 1;
        var iti = window.intlTelInput(input, {
            initialCountry: locked ? options.onlyCountries[0] : DEFAULT_COUNTRY,
            onlyCountries: options.onlyCountries || [],
            countryOrder: COUNTRY_ORDER,
            allowDropdown: !locked,
            separateDialCode: true,
            strictMode: true,
            validationNumberTypes: ["MOBILE"],
            i18n: options.i18n || {},
            hiddenInput: function () {
                return { phone: options.name };
            }
        });
        var hidden = input.form && input.form.querySelector('input[type="hidden"][name="' + options.name + '"]');

        function e164() {
            var u = utils();
            if (!u || !iti.isValidNumberPrecise()) return "";
            return iti.getNumber(u.numberFormat.E164);
        }

        function sync() {
            if (hidden) hidden.value = e164();
        }

        input.addEventListener("input", sync);
        input.addEventListener("countrychange", sync);

        // intl-tel-input keeps the last search text when the country list reopens, so a second search is
        // typed after it ("FranceCameroun") and finds nothing. Start each opening with an empty search.
        input.addEventListener("open:countrydropdown", function () {
            var search = iti.ui && iti.ui.searchInput;
            if (!search || !search.value) return;
            search.value = "";
            search.dispatchEvent(new Event("input"));
        });
        if (options.value) iti.setNumber(options.value);
        sync();

        return {
            iti: iti,
            e164: e164,
            isEmpty: function () {
                return input.value.trim() === "";
            },
            // libphonenumber's reason for an invalid number, as a utils.validationError code (TOO_SHORT, …).
            validationError: function () {
                return iti.getValidationError();
            },
            set: function (value) {
                iti.setNumber(value || "");
                sync();
            }
        };
    }

    function maskEmail(email) {
        var parts = String(email || "").split("@");
        if (parts.length !== 2) return email;
        var name = parts[0];
        var visible = name.length <= 2 ? name.charAt(0) : name.slice(0, 2);
        return visible + "•••@" + parts[1];
    }

    /*
     * The property-type rules the three pages that ask them share (offer-property.html, find-property.html,
     * mandate/edit.html). The server's rules are the source of truth (OfferPropertyRules, FindPropertyRules,
     * MandateEditRules); these tables only mirror them, so no script keeps a fourth copy.
     *
     * Per type: the categories it is offered under for a rental and for a sale ([]: never offered), and the
     * follow-up questions it asks — rooms, parkings, furniture, area, counts (a building's étages and unités).
     * - A residential type asks no surface; rooms unless it is a studio or a room; parkings (a room included);
     *   furniture on both projects; its documents on a sale.
     * - A commercial type asks its surface and, on a sale, its documents.
     * - A building and a plot of land are sold only. A building asks its étages, its unités and its surface; a plot
     *   of land its surface (required on a sale).
     * Documents: every sale asks them, optional, several or none; a rental never asks them.
     */
    var PROPERTY_RULES = {
        ROOM: {
            rental: ["RESIDENTIAL"], sale: [],
            rooms: false, parkings: true, furniture: true, area: false, counts: false
        },
        STUDIO: {
            rental: ["RESIDENTIAL"], sale: ["RESIDENTIAL"],
            rooms: false, parkings: true, furniture: true, area: false, counts: false
        },
        APARTMENT: {
            rental: ["RESIDENTIAL"], sale: ["RESIDENTIAL"],
            rooms: true, parkings: true, furniture: true, area: false, counts: false
        },
        HOUSE: {
            rental: ["RESIDENTIAL"], sale: ["RESIDENTIAL"],
            rooms: true, parkings: true, furniture: true, area: false, counts: false
        },
        VILLA: {
            rental: ["RESIDENTIAL"], sale: ["RESIDENTIAL"],
            rooms: true, parkings: true, furniture: true, area: false, counts: false
        },
        DUPLEX: {
            rental: ["RESIDENTIAL"], sale: ["RESIDENTIAL"],
            rooms: true, parkings: true, furniture: true, area: false, counts: false
        },
        RETAIL: {
            rental: ["COMMERCIAL"], sale: ["COMMERCIAL"],
            rooms: false, parkings: false, furniture: false, area: true, counts: false
        },
        OFFICE: {
            rental: ["COMMERCIAL"], sale: ["COMMERCIAL"],
            rooms: false, parkings: false, furniture: false, area: true, counts: false
        },
        INDUSTRIAL: {
            rental: ["COMMERCIAL"], sale: ["COMMERCIAL"],
            rooms: false, parkings: false, furniture: false, area: true, counts: false
        },
        BUILDING: {
            rental: [], sale: ["COMMERCIAL"],
            rooms: false, parkings: false, furniture: false, area: true, counts: true
        },
        LAND: {
            rental: [], sale: ["RESIDENTIAL", "COMMERCIAL"],
            rooms: false, parkings: false, furniture: false, area: true, counts: false
        }
    };

    // The three documents a sale may declare (TitleDocumentType, ndopify-dto), in form order.
    var PROPERTY_DOCUMENTS = [
        { value: "LAND_TITLE", label: "Titre foncier" },
        { value: "SURVEY_PLAN", label: "Plan de bornage" },
        { value: "CERTIFICATE_OF_OWNERSHIP", label: "Certificat de propriété" }
    ];

    function propertyRule(type) {
        return Object.prototype.hasOwnProperty.call(PROPERTY_RULES, type) ? PROPERTY_RULES[type] : null;
    }

    // The categories a type is offered under, for a sale when listingType is SALE, for a rental otherwise.
    function typeCategories(type, listingType) {
        var rule = propertyRule(type);
        if (!rule) return [];
        return (listingType === "SALE" ? rule.sale : rule.rental).slice();
    }

    // Whether the type is offered at all for the project (a building and a plot of land are sold only).
    function typeOffered(type, listingType) {
        return typeCategories(type, listingType).length > 0;
    }

    // Whether [field] is asked for this type: rooms, parkings, furniture, area, counts. A building's counts are
    // asked on a sale only; every other question is the same on both projects.
    function typeAsks(type, listingType, field) {
        var rule = propertyRule(type);
        if (!rule) return false;
        if (field === "counts") return rule.counts && listingType === "SALE";
        return !!rule[field];
    }

    // A sale asks its ownership documents (optional, several or none); a rental never asks any.
    function asksDocuments(listingType) {
        return listingType === "SALE";
    }

    window.NdUtil = {
        phoneReady: phoneReady,
        bindPhone: bindPhone,
        toE164: toE164,
        formatPhone: formatPhone,
        maskEmail: maskEmail,
        propertyRules: {
            TYPES: PROPERTY_RULES,
            DOCUMENTS: PROPERTY_DOCUMENTS,
            categories: typeCategories,
            offers: typeOffered,
            asks: typeAsks,
            asksDocuments: asksDocuments
        }
    };
})();
