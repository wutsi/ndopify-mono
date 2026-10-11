/*
 * Ndopify Partner Central — shared helpers, exposed as window.NdUtil.
 * Load after intlTelInput.min.js and before the page scripts that use it (e.g. rejoindre.js).
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
     *   options.name           name of the hidden input (in the input's form) that carries the E.164 value; the caller
     *                          creates it, and the visible input must have no name, so the form posts one value only
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
            i18n: options.i18n || {}
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

    window.NdUtil = {
        phoneReady: phoneReady,
        bindPhone: bindPhone,
        toE164: toE164,
        formatPhone: formatPhone,
        maskEmail: maskEmail
    };
})();
