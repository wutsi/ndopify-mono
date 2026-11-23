/*
 * Ndopify Partner Central — "Trouver un bien" wizard (static prototype).
 * A single page (find-property.html): seven steps as slides — besoin → secteur → bien → equipements → budget →
 * contact → revue — then the confirmation, which has no URL of its own. Same mechanics as rejoindre.js: each slide
 * is a history entry (?etape=…), but the URL is never trusted: a step only opens once every earlier step is
 * complete. Answers live in sessionStorage until the review step submits them as a TENANT mandate
 * (CreateMandateRequest, ndopify-dto — see toRequest). Server calls are simulated; every simulation is marked
 * "PROTOTYPE".
 * v2 — sales: step 1 asks listingType (Louer = RENTAL, Acheter = SALE), preselected from ?type= when nothing is
 * saved. For a SALE: no room ("Chambre") to buy, land ("Terrain") is offered for both uses, furniture isn't asked,
 * the budget is a total price (periodicity NONE, at least MIN_SALE_PRICE), the moving period reads as the buying
 * horizon (same MovingPeriod values) and a financing answer is required (FinancingType). A rental stays as is and
 * sends no financing.
 *
 * #275 — a plot of land is bought, never rented: a rental offers Terrain under neither use (data-categories is empty
 * in the page), and a posted propertyType=LAND is refused by the rules (the step never completes with it); a purchase
 * keeps it under both. "Indifférent" stays for both projects.
 *
 * #278 — once step 1 is answered, every later step (the review included) and the confirmation carry the project
 * ("Achat" / "Location") as a read-only pill next to the step count / under "Demande envoyée"; step 1 keeps its
 * choice only. The confirmation's pill is part of the confirmation's data-for pair (applyConfirmation).
 *
 * #274 — a building bought as a whole is captured by two counts, its levels and its units (whole numbers), each with
 * an "Indifférent" answer sent as null; no room nor parking is asked, and the amenities step leaves the wizard: six
 * steps for that branch (the progress, its labels, the arrows and ?etape= follow it), seven for every other.
 */
(function () {
    "use strict";

    var STEPS = ["besoin", "secteur", "bien", "equipements", "budget", "contact", "revue"];
    var MAX_NEIGHBORHOODS = 3;
    var MAX_COMMENTS = 500;
    var CURRENCY = "XAF";
    var EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
    var NAME_PATTERN = /^[\p{L}][\p{L}' .-]*$/u;

    // v2: the visitor's project (step 1). The public site's links preselect it with ?type=SALE|RENTAL.
    var LISTING_TYPE_LABELS = { RENTAL: "Location", SALE: "Achat" };
    // Smallest monthly rent worth a search (FCFA). The BFF must re-check it.
    var MIN_RENT = 5000;
    // Smallest total budget worth a purchase search (FCFA). The BFF must re-check it.
    var MIN_SALE_PRICE = 1000000;

    function isSale(a) {
        return a.listingType === "SALE";
    }

    // A building bought as a whole (#274): its two counts replace the rooms and the parking, and the amenities step
    // leaves the wizard. A rental building is untouched.
    function isBuildingSale(a) {
        return isSale(a) && a.propertyType === "BUILDING";
    }

    function requestedListingType() {
        var type = (new URLSearchParams(window.location.search).get("type") || "").toUpperCase();
        return LISTING_TYPE_LABELS[type] ? type : null;
    }

    var KEYS = {
        answers: "npc.find-property",
        done: "npc.find-property.done",
        sample: "npc.find-property.sample"
    };

    // v2 (#274): the bounds of a building's two counts (whole numbers). The BFF must re-check them.
    var MAX_LEVELS = 30;
    var MAX_UNITS = 500;

    // PROTOTYPE: rows the simulated GET /v1/locations serves, shaped like its Location DTO (ndopify-dto).
    // Same data as rejoindre.js: city ids are the GeoNames ids; neighbourhood ids are placeholders.
    var LOCATIONS = [
        {
            id: 2220957,
            name: "Yaoundé",
            neighborhoods: [
                "Bastos", "Biyem-Assi", "Emana", "Ekounou", "Essos", "Etoudi", "Mendong", "Melen", "Mimboman",
                "Mvan", "Mvog-Mbi", "Ngoa-Ekellé", "Nkolbisson", "Nsam", "Odza", "Omnisport", "Santa Barbara"
            ]
        },
        {
            id: 2232593,
            name: "Douala",
            neighborhoods: [
                "Akwa", "Bali", "Bepanda", "Bonabéri", "Bonamoussadi", "Bonanjo", "Bonapriso", "Deïdo", "Kotto",
                "Logbessou", "Logpom", "Makepe", "Ndokoti", "Village"
            ]
        }
    ].reduce(function (rows, city, c) {
        rows.push({ id: city.id, parentId: null, name: city.name, type: "CITY", country: "CM" });
        city.neighborhoods.forEach(function (name, n) {
            rows.push({ id: (c + 1) * 1000 + n + 1, parentId: city.id, name: name, type: "NEIGHBORHOOD", country: "CM" });
        });
        return rows;
    }, []);

    /*
     * PROTOTYPE: stands in for the BFF's JSON proxy to GET /v1/locations — same parameters (type, parent-id, id,
     * limit) and the same SearchLocationResponse shape, so wiring the real page is a fetch() swap.
     */
    function searchLocations(params) {
        var rows = LOCATIONS.filter(function (l) {
            return (!params.type || l.type === params.type) &&
                (params.parentId == null || l.parentId === params.parentId) &&
                (!params.country || l.country === params.country) &&
                (!params.ids || params.ids.indexOf(l.id) !== -1);
        }).sort(function (a, b) {
            return a.name.localeCompare(b.name, "fr");
        }).slice(0, params.limit || 100);
        return new Promise(function (resolve) {
            window.setTimeout(function () {
                resolve({ locations: rows });
            }, 300);
        });
    }

    // Names of every location loaded so far, for the review step.
    var locationNames = {};

    function loadLocations(params) {
        return searchLocations(params).then(function (response) {
            response.locations.forEach(function (l) {
                locationNames[l.id] = l.name;
            });
            return response.locations;
        });
    }

    /* ---------- Labels (enum values from ndopify-dto) ---------- */
    var CATEGORY_LABELS = { RESIDENTIAL: "usage résidentiel", COMMERCIAL: "usage commercial" };

    var PROPERTY_TYPES = {
        ROOM: { label: "Chambre", categories: ["RESIDENTIAL"] },
        STUDIO: { label: "Studio", categories: ["RESIDENTIAL"] },
        APARTMENT: { label: "Appartement", categories: ["RESIDENTIAL"] },
        HOUSE: { label: "Maison", categories: ["RESIDENTIAL"] },
        VILLA: { label: "Villa", categories: ["RESIDENTIAL"] },
        DUPLEX: { label: "Duplex", categories: ["RESIDENTIAL"] },
        RETAIL: { label: "Boutique, local commercial", categories: ["COMMERCIAL"] },
        OFFICE: { label: "Bureau", categories: ["COMMERCIAL"] },
        INDUSTRIAL: { label: "Entrepôt, local industriel", categories: ["COMMERCIAL"] },
        BUILDING: { label: "Immeuble", categories: ["COMMERCIAL"] },
        // A plot of land is bought, never rented (#275): no rental category offers it; a purchase, both.
        LAND: { label: "Terrain", categories: [], saleCategories: ["RESIDENTIAL", "COMMERCIAL"] }
    };
    // Types that can't be bought: a room is only rented.
    var RENTAL_ONLY_TYPES = ["ROOM"];

    var ROOM_LABELS = { "1": "1 chambre", "2": "2 chambres", "3": "3 chambres", "4": "4 chambres", "5+": "5 chambres et +" };
    // ANY ("Indifférent") is only a UI answer: it is sent as furnitureType = null.
    var FURNITURE_LABELS = { FULLY_FURNISHED: "Meublé", UNFURNISHED: "Non meublé", ANY: "Meublé ou non" };
    // ANY ("Indifférent") is only a UI answer, like furniture: it is sent as parkings = null.
    var PARKING_LABELS = {
        "0": "Sans parking",
        "1": "1 place de parking",
        "2": "2 places de parking",
        "3+": "3 places de parking et +",
        ANY: "Parking indifférent"
    };
    var PERIOD_LABELS = { MORNING: "le matin", AFTERNOON: "l'après-midi", EVENING: "le soir" };
    var MOVE_IN_LABELS = {
        WITHIN_1_MONTH: "Dans moins d'1 mois",
        WITHIN_3_MONTHS: "Dans 1 à 3 mois",
        AFTER_3_MONTHS: "Dans plus de 3 mois"
    };
    // How a purchase is financed (FinancingType, ndopify-dto): asked for a sale only, and never sent for a rental.
    var FINANCING_LABELS = {
        CASH: "Comptant",
        BANK_LOAN: "Prêt bancaire",
        TRANSFER_FROM_ABROAD: "Transfert depuis l'étranger",
        UNDECIDED: "Pas encore décidé"
    };
    // The land documents a buyer may specify (optional): asked only for a land purchase, and a rental or a building
    // never asks nor sends them. "Titre foncier" is the land title, "Plan de bornage" the survey plan that delimits
    // the parcel, "Certificat de propriété" the certificate of ownership. The search stays simple — "land title
    // available" is just ticking "Titre foncier".
    var TITLE_DOCUMENT_LABELS = {
        LAND_TITLE: "Titre foncier",
        SURVEY_PLAN: "Plan de bornage",
        CERTIFICATE_OF_OWNERSHIP: "Certificat de propriété"
    };

    // The boolean criteria of CreateMandateRequest, in form order.
    var AMENITIES = [
        ["waterMeter", "Compteur d'eau"],
        ["dedicatedWaterMeter", "Compteur d'eau individuel"],
        ["waterTank", "Citerne"],
        ["waterWell", "Forage"],
        ["electricityMeter", "Compteur électrique"],
        ["dedicatedElectricityMeter", "Compteur électrique individuel"],
        ["generatorSet", "Groupe électrogène"],
        ["solarPanel", "Panneaux solaires"],
        ["roadPaved", "Route bitumée"],
        ["trackAccessibleByCar", "Piste accessible en voiture"],
        ["trackInaccessibleByCar", "Piste accessible à pied seulement"],
        ["securedFenced", "Clôturé et sécurisé"],
        ["securityGuard", "Gardien"],
        ["videoSurveillance", "Vidéosurveillance"]
    ];

    /*
     * Which follow-up questions of the "bien" step apply, given the project and the property type. The type is optional:
     * none, or ANY ("Indifférent", a UI answer sent as propertyType = null), asks every question that fits most types.
     */
    function propertyTypeOf(a) {
        return PROPERTY_TYPES[a.propertyType] ? a.propertyType : null;
    }

    function typeFits(a) {
        var type = PROPERTY_TYPES[a.propertyType];
        if (!type) return !a.propertyType || a.propertyType === "ANY";
        if (isSale(a) && RENTAL_ONLY_TYPES.indexOf(a.propertyType) !== -1) return false;
        var categories = isSale(a) && type.saleCategories ? type.saleCategories : type.categories;
        return categories.indexOf(a.propertyCategory) !== -1;
    }

    function needsRooms(a) {
        // v2: land is residential too when it's bought (a plot to build on), and has no rooms.
        return a.propertyCategory === "RESIDENTIAL" && ["STUDIO", "ROOM", "LAND"].indexOf(a.propertyType) === -1;
    }

    function needsFurniture(a) {
        // A purchase isn't searched by its furniture: the buyer discusses it with the seller.
        if (isSale(a)) return false;
        return ["LAND", "BUILDING", "INDUSTRIAL"].indexOf(a.propertyType) === -1;
    }

    function asksParkings(a) {
        // v2 (#274): a building bought as a whole has no parking to search for; a rental building keeps the question.
        return ["LAND", "ROOM"].indexOf(a.propertyType) === -1 && !isBuildingSale(a);
    }

    // v2 (#274): a building purchase asks its two counts, and no amenities step.
    function needsBuildingCounts(a) {
        return isBuildingSale(a);
    }

    function needsAmenities(a) {
        return !isBuildingSale(a);
    }

    // A plot of land bought (the only property whose surface and documents matter): sale, type LAND.
    function isLandSale(a) {
        return isSale(a) && a.propertyType === "LAND";
    }

    // A plot of land bought is searched by its size: the surface is required, not optional.
    function needsLandArea(a) {
        return isLandSale(a);
    }

    // Land is the only property whose documents a buyer may specify (titre foncier, plan de bornage…): optional,
    // sale only, and never for a rental, a building or an unspecified type.
    function asksTitleDocuments(a) {
        return isLandSale(a);
    }

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

    function loadJson(key) {
        try {
            return JSON.parse(store.get(key)) || null;
        } catch (e) {
            return null;
        }
    }

    function loadAnswers() {
        return loadJson(KEYS.answers) || {};
    }

    function saveAnswers(patch) {
        var answers = loadAnswers();
        Object.keys(patch).forEach(function (key) {
            answers[key] = patch[key];
        });
        store.set(KEYS.answers, JSON.stringify(answers));
        return answers;
    }

    /*
     * PROTOTYPE: ?sample= shortcuts for the states of #274 (README › find-property.html). Each seeds the answers of
     * the steps before the one it opens; the page then opens there (a step only opens once the earlier ones are
     * complete). A sample already seeded isn't seeded again, so the reviewer can keep walking the wizard.
     */
    var SAMPLES = {
        // A purchase of a building, its counts still to answer: the step asks Étages and Unités, six steps in all.
        building: {
            step: "bien",
            answers: { propertyType: "BUILDING", area: 850 }
        },
        // The same with both "Indifférent" answers: Suivant is active, the counts are sent as null.
        "building-any": {
            step: "bien",
            answers: { propertyType: "BUILDING", area: 850, levelsAny: true, unitsAny: true }
        },
        // The review of that purchase: Étages 3, Unités 12, six steps, no amenities line.
        "building-review": {
            step: "revue",
            answers: {
                propertyType: "BUILDING", levels: 3, units: 12, area: 850,
                priceAmount: 250000000, movingPeriod: "WITHIN_3_MONTHS", financingType: "CASH"
            }
        },
        // The same with both counts "Indifférent".
        "building-review-any": {
            step: "revue",
            answers: {
                propertyType: "BUILDING", levelsAny: true, unitsAny: true, area: 850,
                priceAmount: 250000000, movingPeriod: "WITHIN_3_MONTHS", financingType: "CASH"
            }
        },
        // A rental building is unchanged: seven steps, its parking and amenities questions, no count of units.
        "rental-building": {
            step: "revue",
            answers: {
                listingType: "RENTAL", propertyType: "BUILDING", parkings: "ANY",
                priceAmount: 900000, movingPeriod: "WITHIN_3_MONTHS",
                waterMeter: true, electricityMeter: true
            }
        }
    };

    // The answers a sample starts from: a complete purchase in Bonapriso, Douala, at 250 000 000 FCFA.
    function sampleAnswers(name) {
        var chosen = Object.prototype.hasOwnProperty.call(SAMPLES, name) ? SAMPLES[name] : null;
        if (!chosen) return null;
        return Object.assign({
            listingType: "SALE",
            propertyCategory: "COMMERCIAL",
            cityId: 2232593,
            neighborhoodIds: [2007],
            area: 800,
            priceAmount: 250000000,
            movingPeriod: "WITHIN_3_MONTHS",
            financingType: "CASH",
            firstName: "Bertrand",
            lastName: "Fouda",
            email: "b.fouda@example.com",
            whatsappNumber: "+237690334455",
            preferredContactPeriod: "AFTERNOON"
        }, chosen.answers || {});
    }

    // Seeds a ?sample= (once), and returns the step it opens in the branch's own list, or null without a sample.
    function applySample() {
        var name = new URLSearchParams(window.location.search).get("sample");
        var answers = sampleAnswers(name);
        if (!answers) return null;
        if (store.get(KEYS.sample) !== name || !store.get(KEYS.answers)) {
            store.set(KEYS.answers, JSON.stringify(answers));
            store.set(KEYS.sample, name);
            store.remove(KEYS.done);
        }
        return activeSteps(loadAnswers()).indexOf(SAMPLES[name].step);
    }

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

    // aria-disabled (not the disabled attribute) keeps buttons in the tab order so their
    // aria-describedby explanation can still be reached — same convention as rejoindre.js.
    function setActive(button, active) {
        if (active) button.removeAttribute("aria-disabled");
        else button.setAttribute("aria-disabled", "true");
    }

    function isActive(button) {
        return button.getAttribute("aria-disabled") !== "true";
    }

    function checkedValue(name) {
        var input = $('input[name="' + name + '"]:checked');
        return input ? input.value : "";
    }

    // All checked values of a checkbox group, in document order.
    function checkedValues(name) {
        return $all('input[name="' + name + '"]:checked').map(function (input) {
            return input.value;
        });
    }

    function setChecked(name, value) {
        $all('input[name="' + name + '"]').forEach(function (input) {
            input.checked = value != null && input.value === String(value);
        });
    }

    // Re-tick a saved checkbox group (an array of values).
    function setCheckedAll(name, values) {
        $all('input[name="' + name + '"]').forEach(function (input) {
            input.checked = (values || []).indexOf(input.value) !== -1;
        });
    }

    // Accent- and case-insensitive "contains", so "ekelle" finds "Ngoa-Ekellé" and "mbi" finds "Mvog-Mbi".
    function fold(text) {
        return String(text).normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
    }

    /* ---------- Whole numbers (budget, surface): "150 000" as you type, a Number in the answers ---------- */
    function digitsOf(value) {
        return String(value || "").replace(/\D/g, "").replace(/^0+(?=\d)/, "");
    }

    function formatNumber(n) {
        return n ? Number(n).toLocaleString("fr-FR") : "";
    }

    // A price to read, not to type: "800K", "12M", "1.5M" (FCFA has no cents, so K and M are enough).
    function formatPrice(n) {
        n = Number(n) || 0;
        var short = function (value, unit) {
            return value.toLocaleString("en-US", { maximumFractionDigits: 2, useGrouping: false }) + unit;
        };
        if (n >= 1000000) return short(n / 1000000, "M");
        if (n >= 1000) return short(n / 1000, "K");
        return String(n);
    }

    function numberValue(input) {
        var digits = digitsOf(input.value);
        return digits ? Number(digits) : 0;
    }

    // Regroups the digits by thousands while typing, keeping the caret after the same digit.
    function bindNumber(input, maxDigits) {
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

    /*
     * A slim-select dropdown over a native <select> (DESIGN.md › Form fields — styled in rejoindre.css).
     * The <select> stays the source of truth and keeps firing "change"; slim-select's combobox becomes the
     * field's visible, focusable control, so it takes over the error description.
     */
    function slimSelect(select, settings) {
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
            events: {
                searchFilter: function (option, search) {
                    return fold(option.text).indexOf(fold(search)) !== -1;
                }
            }
        });
        var control = slim.render.main.main;
        control.setAttribute("aria-describedby", select.getAttribute("aria-describedby"));
        select.removeAttribute("aria-describedby");
        return { slim: slim, control: control, content: slim.render.content.main };
    }

    function slimField(select, ui, extra) {
        return Object.assign({
            target: ui.control,
            source: select,
            error: $("#" + select.id + "-error"),
            // Moving into the open dropdown (its search box, its options) isn't leaving the field.
            contains: function (el) {
                return ui.control.contains(el) || ui.content.contains(el);
            }
        }, extra);
    }

    /* Phone formatting, validation and E.164 come from intl-tel-input via util.js (loaded first). */
    var formatPhone = window.NdUtil.formatPhone;
    var maskEmail = window.NdUtil.maskEmail;

    var PHONE_ERRORS = {
        1: "Cet indicatif de pays n'existe pas. Choisissez votre pays dans la liste.",
        2: "Ce numéro est trop court. Vérifiez qu'il ne manque pas de chiffre.",
        3: "Ce numéro est trop long. Vérifiez qu'il n'y a pas de chiffre en trop."
    };
    var PHONE_LOADING = "Vérification du numéro en cours de chargement. Réessayez dans un instant.";
    var PHONE_UNAVAILABLE = "Nous ne pouvons pas vérifier ce numéro pour l'instant. Rechargez la page.";

    /*
     * A phone field whose intl-tel-input controller arrives later (utils.js loads asynchronously).
     * Until then — or for good, if it can't load — the number counts as invalid, so its step can't be passed.
     */
    function lazyPhone(input, options) {
        var phone = null;
        var failed = false;
        return {
            bind: function (i18n) {
                options.i18n = i18n;
                // Keep anything typed while utils.js was loading; otherwise restore the saved number.
                if (input.value.trim()) delete options.value;
                phone = window.NdUtil.bindPhone(input, options);
            },
            fail: function () {
                failed = true;
            },
            isEmpty: function () {
                return input.value.trim() === "";
            },
            e164: function () {
                return phone ? phone.e164() : "";
            },
            validity: function (emptyMessage) {
                if (input.value.trim() === "") return emptyMessage;
                if (!phone) return failed ? PHONE_UNAVAILABLE : PHONE_LOADING;
                if (phone.e164()) return "";
                return PHONE_ERRORS[phone.validationError()] ||
                    "Ce numéro n'est pas un numéro mobile valide. Exemple : 6 77 12 34 56";
            }
        };
    }

    function phoneField(input, phone, emptyMessage) {
        return {
            target: input,
            error: $("#" + input.id + "-error"),
            touched: function () {
                return !phone.isEmpty();
            },
            validate: function () {
                return phone.validity(emptyMessage);
            }
        };
    }

    function textField(input, emptyMessage, extra) {
        return {
            target: input,
            error: $("#" + input.id + "-error"),
            touched: function () {
                return input.value.trim() !== "";
            },
            validate: function () {
                var value = input.value.trim();
                if (!value) return emptyMessage;
                return extra ? extra(value) : "";
            }
        };
    }

    // A radio group; when applies() says the question is hidden, it is never in error.
    function radioField(name, emptyMessage, applies) {
        return {
            target: $('[data-group="' + name + '"]'),
            error: $("#" + name + "-error"),
            touched: function () {
                return checkedValue(name) !== "";
            },
            validate: function () {
                if (applies && !applies()) return "";
                return checkedValue(name) ? "" : emptyMessage;
            }
        };
    }

    function nameValidity(value) {
        if (!NAME_PATTERN.test(value)) return "Utilisez uniquement des lettres, des espaces, des tirets ou des apostrophes.";
        return "";
    }

    function emailValidity(value) {
        if (!EMAIL_PATTERN.test(value)) return "Cette adresse email semble incomplète. Exemple : nom@exemple.com";
        return "";
    }

    function areaValid(area) {
        return area == null || (area >= 5 && area <= 1000000);
    }

    /*
     * Whether a step's saved answers are complete. This is what guards the wizard: a step opens only when
     * every step before it is complete, whatever the URL says. The BFF must re-check all of it server side.
     */
    function countValid(value, max) {
        return value != null && Number.isInteger(Number(value)) && Number(value) >= 1 && Number(value) <= max;
    }

    // A count counts once answered: a whole number within its bounds, or "Indifférent" (stored as null).
    function countAnswered(a, name) {
        var max = name === "levels" ? MAX_LEVELS : MAX_UNITS;
        return !!a[name + "Any"] || countValid(a[name], max);
    }

    function isStepComplete(key, a) {
        switch (key) {
            case "besoin":
                return !!LISTING_TYPE_LABELS[a.listingType] && !!CATEGORY_LABELS[a.propertyCategory];
            case "secteur":
                // Neighbourhoods are optional: none means the whole city.
                return a.cityId > 0 && Array.isArray(a.neighborhoodIds) &&
                    a.neighborhoodIds.length <= MAX_NEIGHBORHOODS;
            case "bien":
                return typeFits(a) &&
                    (!needsRooms(a) || !!ROOM_LABELS[a.rooms]) &&
                    (!needsFurniture(a) || !!FURNITURE_LABELS[a.furnitureType]) &&
                    // v2 (#274): a building's two counts are required, each "Indifférent" included.
                    (!needsBuildingCounts(a) || (countAnswered(a, "levels") && countAnswered(a, "units"))) &&
                    (needsLandArea(a) ? a.area != null && areaValid(a.area) : areaValid(a.area));
            case "equipements":
                return true; // every criterion is optional
            case "budget":
                return a.priceAmount >= (isSale(a) ? MIN_SALE_PRICE : MIN_RENT) &&
                    !!MOVE_IN_LABELS[a.movingPeriod] &&
                    (!isSale(a) || !!FINANCING_LABELS[a.financingType]);
            case "contact":
                return !!a.firstName && !!a.lastName && EMAIL_PATTERN.test(a.email || "") &&
                    /^\+[1-9]\d{6,14}$/.test(a.whatsappNumber || "") && !!PERIOD_LABELS[a.preferredContactPeriod];
            default:
                return false;
        }
    }

    /*
     * The steps of the branch being answered: a building purchased skips the amenities step (#274), six steps
     * instead of seven; every other branch keeps the seven. Step 1 has no answer yet, so it reads as seven.
     */
    function activeSteps(a) {
        return needsAmenities(a) ? STEPS.slice() : STEPS.filter(function (key) {
            return key !== "equipements";
        });
    }

    function reviewIndexOf(a) {
        return activeSteps(a).length - 1;
    }

    function firstIncompleteStep(answers) {
        var keys = activeSteps(answers);
        for (var i = 0; i < keys.length - 1; i++) {
            if (!isStepComplete(keys[i], answers)) return i;
        }
        return keys.length - 1;
    }

    /*
     * The saved answers as a CreateMandateRequest (ndopify-dto). Questions that don't apply to the chosen
     * property are sent as null even if an earlier answer is still in the session.
     */
    function toRequest(a) {
        var request = {
            mandateType: "TENANT",
            listingType: a.listingType,
            propertyCategory: a.propertyCategory,
            cityId: a.cityId,
            neighbourhoodIds: a.neighborhoodIds,
            propertyType: propertyTypeOf(a),
            furnitureType: needsFurniture(a) && a.furnitureType !== "ANY" ? a.furnitureType : null,
            rooms: needsRooms(a) ? a.rooms : null,
            parkings: asksParkings(a) && a.parkings && a.parkings !== "ANY" ? a.parkings : null,
            // v2 (#274): a building's two counts; "Indifférent" is sent as null, and no other type sends one.
            levels: needsBuildingCounts(a) && !a.levelsAny ? a.levels : null,
            units: needsBuildingCounts(a) && !a.unitsAny ? a.units : null,
            area: a.area || null,
            // A land sale sends the documents the buyer wants (optional); anything else sends none (null).
            titleDocuments: asksTitleDocuments(a) ? (a.titleDocuments || []) : null,
            price: {
                amount: a.priceAmount,
                currency: CURRENCY,
                periodicity: isSale(a) ? "NONE" : "MONTHLY"
            },
            // CreateMandateRequest.movingPeriod (MovingPeriod, ndopify-dto).
            movingPeriod: a.movingPeriod,
            // A purchase sends how it will be financed; a rental sends none.
            financingType: isSale(a) ? a.financingType : null,
            firstName: a.firstName,
            lastName: a.lastName,
            email: a.email,
            whatsappNumber: a.whatsappNumber,
            preferredContactPeriod: a.preferredContactPeriod,
            comments: a.comments || null
        };
        AMENITIES.forEach(function (amenity) {
            // v2 (#274): a building purchase never asks nor sends an amenity.
            request[amenity[0]] = needsAmenities(a) && !!a[amenity[0]];
        });
        return request;
    }

    /* ---------- Steps: fields, saved values, what they collect ---------- */
    function needStep() {
        var answers = loadAnswers();
        setChecked("listingType", answers.listingType || requestedListingType());
        setChecked("propertyCategory", answers.propertyCategory);
        return {
            fields: [
                radioField("listingType", "Indiquez si vous souhaitez louer ou acheter."),
                radioField("propertyCategory", "Indiquez si le bien est pour y habiter ou pour une activité.")
            ],
            collect: function () {
                return {
                    listingType: checkedValue("listingType"),
                    propertyCategory: checkedValue("propertyCategory")
                };
            }
        };
    }

    function sectorStep(resync) {
        var answers = loadAnswers();
        var citySelect = $("#cityId");
        var neighborhoodSelect = $("#neighborhoodIds");
        var group = $('[data-group="neighborhoodIds"]');
        var empty = $("[data-neighborhoods-empty]");
        var counter = $("[data-neighborhood-counter]");
        var status = $("[data-neighborhood-status]");

        var city = slimSelect(citySelect, {
            placeholderText: "Choisissez une ville",
            ariaLabel: "Ville"
        });
        var neighborhoods = slimSelect(neighborhoodSelect, {
            placeholderText: "Choisissez les quartiers",
            ariaLabel: "Quartiers souhaités",
            maxSelected: MAX_NEIGHBORHOODS,
            closeOnSelect: false
        });

        function cityId() {
            return Number(citySelect.value) || 0;
        }

        function selected() {
            return neighborhoods.slim.getSelected().map(Number);
        }

        function updateCounter(announceIt) {
            var count = selected().length;
            counter.textContent = count + " sur " + MAX_NEIGHBORHOODS + " sélectionné" + (count > 1 ? "s" : "");
            if (announceIt && count === MAX_NEIGHBORHOODS) {
                announce(status, "Vous avez choisi " + MAX_NEIGHBORHOODS + " quartiers, le maximum.");
            }
        }

        function toOptions(locations, keep) {
            return locations.map(function (l) {
                return { value: String(l.id), text: l.name, selected: (keep || []).indexOf(l.id) !== -1 };
            });
        }

        // Each city change loads that city's neighbourhoods once; typing then filters them in the browser.
        var pending = 0;

        function loadNeighborhoods(keep) {
            var id = cityId();
            var request = ++pending;
            empty.hidden = !!id;
            group.hidden = !id;
            neighborhoods.slim.setData([]);
            updateCounter(false);
            if (!id) return;
            neighborhoods.slim.disable();
            loadLocations({ type: "NEIGHBORHOOD", parentId: id }).then(function (locations) {
                if (request !== pending) return; // the city changed again meanwhile
                neighborhoods.slim.setData(toOptions(locations, keep));
                neighborhoods.slim.enable();
                updateCounter(false);
                resync();
            });
        }

        city.slim.disable();
        loadLocations({ type: "CITY", country: "CM" }).then(function (locations) {
            var saved = locationNames[answers.cityId] ? [answers.cityId] : [];
            city.slim.setData([{ value: "", text: "Choisissez une ville", placeholder: true }]
                .concat(toOptions(locations, saved)));
            city.slim.enable();
            loadNeighborhoods(answers.neighborhoodIds);
            citySelect.addEventListener("change", function () {
                loadNeighborhoods([]);
                setFieldError(neighborhoods.control, $("#neighborhoodIds-error"), "");
            });
        });
        neighborhoodSelect.addEventListener("change", function () {
            updateCounter(true);
        });

        return {
            fields: [
                slimField(citySelect, city, {
                    touched: function () {
                        return cityId() > 0;
                    },
                    validate: function () {
                        return cityId() ? "" : "Choisissez la ville.";
                    }
                }),
                slimField(neighborhoodSelect, neighborhoods, {
                    touched: function () {
                        return selected().length > 0;
                    },
                    validate: function () {
                        if (!cityId()) return "";
                        var count = selected().length;
                        if (count > MAX_NEIGHBORHOODS) return "Choisissez au plus " + MAX_NEIGHBORHOODS + " quartiers.";
                        return "";
                    }
                })
            ],
            collect: function () {
                return { cityId: cityId(), neighborhoodIds: selected() };
            }
        };
    }

    function propertyStep(resync) {
        var answers = loadAnswers();
        var area = $("#area");
        var counts = $("[data-building-counts]");
        var levelInput = $("#levels");
        var unitInput = $("#units");
        var levelAny = $('input[name="levelsAny"]');
        var unitAny = $('input[name="unitsAny"]');
        var groups = {
            rooms: $('[data-group="rooms"]'),
            furnitureType: $('[data-group="furnitureType"]'),
            parkings: $('[data-group="parkings"]'),
            area: $('[data-group="area"]'),
            titleDocuments: $('[data-group="titleDocuments"]')
        };

        setChecked("propertyType", answers.propertyType);
        setChecked("rooms", answers.rooms);
        setChecked("furnitureType", answers.furnitureType);
        setChecked("parkings", answers.parkings);
        setCheckedAll("titleDocuments", answers.titleDocuments);
        area.value = formatNumber(answers.area);
        bindNumber(area, 7);
        // v2 (#274): the building's two counts, with their "Indifférent" answers.
        levelInput.value = answers.levels != null ? String(answers.levels) : "";
        unitInput.value = answers.units != null ? String(answers.units) : "";
        levelAny.checked = !!answers.levelsAny;
        unitAny.checked = !!answers.unitsAny;

        // "Indifférent" takes the count's input away, so an answer is never both a number and a blank.
        function syncAny(name) {
            var input = $("#" + name);
            var any = $('input[name="' + name + 'Any"]');
            input.disabled = any.checked;
            if (any.checked) setFieldError(input, $("#" + name + "-error"), "");
        }
        ["levels", "units"].forEach(function (name) {
            $('input[name="' + name + 'Any"]').addEventListener("change", function () {
                syncAny(name);
                resync();
            });
            $("#" + name).addEventListener("input", function () {
                if (this.value.trim()) $('input[name="' + name + 'Any"]').checked = false;
            });
        });
        syncAny("levels");
        syncAny("units");

        // The project from step 1 (saved) with the type being chosen now (live).
        function live() {
            var saved = loadAnswers();
            return {
                listingType: saved.listingType,
                propertyCategory: saved.propertyCategory,
                propertyType: checkedValue("propertyType")
            };
        }

        function areaValue() {
            return area.value.trim() ? numberValue(area) : null;
        }

        // A count: a whole number within its bounds, or "Indifférent" (stored as null).
        function countField(name, noun, max) {
            var input = $("#" + name);
            var any = $('input[name="' + name + 'Any"]');
            return {
                target: input,
                error: $("#" + name + "-error"),
                touched: function () {
                    return input.value.trim() !== "" || any.checked;
                },
                validate: function () {
                    if (!needsBuildingCounts(live())) return "";
                    var raw = input.value.trim();
                    if (any.checked) return "";
                    if (!raw) return "Indiquez le nombre " + noun + ", ou cochez Indifférent.";
                    if (!/^\d+$/.test(raw) || Number(raw) < 1 || Number(raw) > max) {
                        return "Indiquez un nombre entier, de 1 à " + max + ".";
                    }
                    return "";
                }
            };
        }

        // Shows only the follow-up questions that apply to the chosen type; a hidden question is never in error.
        function updateQuestions() {
            var a = live();
            var shown = {
                rooms: needsRooms(a),
                furnitureType: needsFurniture(a),
                parkings: asksParkings(a),
                area: true,
                // The documents are asked for a land purchase only (and optional); never for a rental or a building.
                titleDocuments: asksTitleDocuments(a)
            };
            Object.keys(groups).forEach(function (key) {
                groups[key].hidden = !shown[key];
                if (!shown[key]) setFieldError(groups[key], $("#" + key + "-error"), "");
            });
            // v2 (#274): a building's levels and units replace the rooms and the parking.
            counts.hidden = !needsBuildingCounts(a);
            if (counts.hidden) {
                setFieldError(levelInput, $("#levels-error"), "");
                setFieldError(unitInput, $("#units-error"), "");
            }
            $("[data-area-label]").textContent = a.propertyType === "LAND" ? "Surface du terrain minimale" : "Surface minimale";
        }

        $all('input[name="propertyType"]').forEach(function (input) {
            input.addEventListener("change", function () {
                updateQuestions();
                resync();
            });
        });

        return {
            // Step 1 may have changed the category since this step was last shown: offer only its types.
            onShow: function () {
                var saved = loadAnswers();
                var category = saved.propertyCategory;
                var sale = isSale(saved);
                $all('[data-group="propertyType"] .choice').forEach(function (choice) {
                    var categories = (sale && choice.getAttribute("data-sale-categories")) ||
                        choice.getAttribute("data-categories");
                    var fits = categories.split(" ").indexOf(category) !== -1 &&
                        !(sale && choice.hasAttribute("data-rental-only"));
                    var input = $("input", choice);
                    choice.hidden = !fits;
                    input.disabled = !fits;
                    if (!fits) input.checked = false;
                });
                updateQuestions();
            },
            fields: [
                radioField("rooms", "Indiquez le nombre de chambres.", function () {
                    return needsRooms(live());
                }),
                radioField("furnitureType", "Indiquez si le bien doit être meublé.", function () {
                    return needsFurniture(live());
                }),
                countField("levels", "d'étages", MAX_LEVELS),
                countField("units", "d'unités", MAX_UNITS),
                {
                    target: area,
                    error: $("#area-error"),
                    touched: function () {
                        return area.value.trim() !== "";
                    },
                    validate: function () {
                        if (groups.area.hidden) return "";
                        // A plot of land bought is searched by its size: the surface is required.
                        if (needsLandArea(live())) {
                            if (areaValue() == null) return "Indiquez la surface du terrain.";
                            return areaValid(areaValue()) ? "" : "Indiquez une surface d'au moins 5 m².";
                        }
                        return areaValid(areaValue()) ? "" : "Indiquez une surface d'au moins 5 m², ou laissez ce champ vide.";
                    }
                }
            ],
            collect: function () {
                var a = live();
                return {
                    propertyType: a.propertyType || null,
                    rooms: needsRooms(a) ? checkedValue("rooms") : null,
                    furnitureType: needsFurniture(a) ? checkedValue("furnitureType") : null,
                    parkings: asksParkings(a) ? checkedValue("parkings") || null : null,
                    levels: needsBuildingCounts(a) && !levelAny.checked && levelInput.value.trim()
                        ? Number(levelInput.value.trim()) : null,
                    levelsAny: needsBuildingCounts(a) && levelAny.checked,
                    units: needsBuildingCounts(a) && !unitAny.checked && unitInput.value.trim()
                        ? Number(unitInput.value.trim()) : null,
                    unitsAny: needsBuildingCounts(a) && unitAny.checked,
                    area: areaValue(),
                    titleDocuments: asksTitleDocuments(a) ? checkedValues("titleDocuments") : null
                };
            }
        };
    }

    function amenitiesStep() {
        var answers = loadAnswers();
        AMENITIES.forEach(function (amenity) {
            $('input[name="' + amenity[0] + '"]').checked = !!answers[amenity[0]];
        });
        return {
            fields: [],
            collect: function () {
                var values = {};
                AMENITIES.forEach(function (amenity) {
                    values[amenity[0]] = $('input[name="' + amenity[0] + '"]').checked;
                });
                return values;
            }
        };
    }

    function budgetStep() {
        var answers = loadAnswers();
        var price = $("#priceAmount");
        var financing = $('[data-group="financingType"]');
        var perSqm = $("[data-price-per-sqm]");
        price.value = formatNumber(answers.priceAmount);
        setChecked("movingPeriod", answers.movingPeriod);
        setChecked("financingType", answers.financingType);
        bindNumber(price, 12);

        // The implied price per m², for a land purchase: total budget ÷ surface, rounded to the nearest 100 FCFA.
        // Display only — never stored, never validated. Hidden until both a budget and a valid surface exist.
        function updatePerSqm() {
            var a = loadAnswers();
            var budget = numberValue(price);
            var area = a.area != null ? Number(a.area) : 0;
            var show = isLandSale(a) && areaValid(a.area) && area > 0 && budget > 0;
            if (!show) {
                perSqm.hidden = true;
                perSqm.textContent = "";
                return;
            }
            var rate = Math.round((budget / area) / 100) * 100;
            if (rate <= 0) {
                perSqm.hidden = true;
                perSqm.textContent = "";
                return;
            }
            perSqm.textContent = "Soit ≈ " + formatNumber(rate) + " FCFA/m² pour " + formatNumber(area) + " m²";
            perSqm.hidden = false;
        }
        price.addEventListener("input", updatePerSqm);

        // The rental wording, put back when the visitor goes back to step 1 and switches from Acheter to Louer.
        var rentalTexts = ["[data-price-label]", "[data-price-suffix]", "[data-budget-intro]", "[data-price-hint]"]
            .map(function (selector) {
                return [selector, $(selector).textContent];
            });

        return {
            onShow: function () {
                var a = loadAnswers();
                var sale = isSale(a);
                updatePerSqm();
                // Financing is asked for a purchase only; a rental hides it and never has it in error.
                financing.hidden = !sale;
                if (!sale) setFieldError(financing, $("#financingType-error"), "");
                rentalTexts.forEach(function (text) {
                    $(text[0]).textContent = text[1];
                });
                if (sale) {
                    $("[data-move-in-label]").textContent = "Quand souhaitez-vous acheter\u00a0?";
                    $("[data-price-label]").textContent = "Budget total";
                    $("[data-price-suffix]").textContent = "FCFA";
                    $("[data-budget-intro]").textContent = "Le prix maximal que vous êtes prêt à payer, et " +
                        "quand vous pensez acheter.";
                    $("[data-price-hint]").textContent = "Votre agent ne vous propose que des biens dans ce " +
                        "budget. Le prix se négocie ensuite avec le vendeur.";
                    return;
                }
                // A shop or an office is moved into too, but "s'installer" reads better for an activity.
                $("[data-move-in-label]").textContent = a.propertyCategory === "COMMERCIAL"
                    ? "Date d'installation souhaitée" : "Date d'emménagement souhaitée";
            },
            fields: [
                textField(price, "Indiquez votre budget.", function () {
                    if (isSale(loadAnswers())) {
                        if (numberValue(price) < MIN_SALE_PRICE) return "Indiquez un budget d'au moins " + formatPrice(MIN_SALE_PRICE) + " FCFA.";
                        return "";
                    }
                    if (numberValue(price) < MIN_RENT) return "Indiquez un loyer d'au moins " + formatPrice(MIN_RENT) + " FCFA par mois.";
                    return "";
                }),
                radioField("movingPeriod", "Indiquez quand vous souhaitez emménager ou acheter."),
                radioField("financingType", "Indiquez comment vous financerez votre achat.", function () {
                    return isSale(loadAnswers());
                })
            ],
            collect: function () {
                return {
                    priceAmount: numberValue(price),
                    movingPeriod: checkedValue("movingPeriod"),
                    financingType: checkedValue("financingType")
                };
            }
        };
    }

    function contactStep(phone) {
        var answers = loadAnswers();
        var firstName = $("#firstName");
        var lastName = $("#lastName");
        var email = $("#email");
        var comments = $("#comments");
        var counter = $("[data-comments-counter]");

        firstName.value = answers.firstName || "";
        lastName.value = answers.lastName || "";
        email.value = answers.email || "";
        comments.value = answers.comments || "";
        setChecked("preferredContactPeriod", answers.preferredContactPeriod);

        function updateCounter() {
            counter.textContent = comments.value.length + " sur " + MAX_COMMENTS + " caractères";
        }
        comments.addEventListener("input", updateCounter);
        updateCounter();

        return {
            fields: [
                textField(firstName, "Saisissez votre prénom.", nameValidity),
                textField(lastName, "Saisissez votre nom.", nameValidity),
                textField(email, "Saisissez votre adresse email.", emailValidity),
                phoneField($("#whatsappNumber"), phone, "Saisissez votre numéro WhatsApp."),
                radioField("preferredContactPeriod", "Indiquez quand votre agent peut vous contacter.")
            ],
            collect: function () {
                return {
                    firstName: firstName.value.trim(),
                    lastName: lastName.value.trim(),
                    email: email.value.trim().toLowerCase(),
                    whatsappNumber: phone.e164(),
                    preferredContactPeriod: checkedValue("preferredContactPeriod"),
                    comments: comments.value.trim().slice(0, MAX_COMMENTS)
                };
            }
        };
    }

    function reviewStep() {
        var submit = $("[data-submit]");

        function describeProperty(a) {
            if (!typeFits(a)) return "";
            var parts = [propertyTypeOf(a) ? PROPERTY_TYPES[a.propertyType].label : "Tout type de bien"];
            if (needsRooms(a) && ROOM_LABELS[a.rooms]) parts.push(ROOM_LABELS[a.rooms]);
            if (needsFurniture(a) && FURNITURE_LABELS[a.furnitureType]) parts.push(FURNITURE_LABELS[a.furnitureType]);
            if (asksParkings(a) && PARKING_LABELS[a.parkings]) parts.push(PARKING_LABELS[a.parkings]);
            if (a.area) parts.push(formatNumber(a.area) + " m² minimum");
            return parts.join(" · ");
        }

        function render(a) {
            var values = {
                project: LISTING_TYPE_LABELS[a.listingType] && CATEGORY_LABELS[a.propertyCategory]
                    ? LISTING_TYPE_LABELS[a.listingType] + ", " + CATEGORY_LABELS[a.propertyCategory] : "",
                sector: locationNames[a.cityId]
                    ? locationNames[a.cityId] + " — " + ((a.neighborhoodIds || []).length
                        ? a.neighborhoodIds.map(function (id) {
                            return locationNames[id];
                        }).filter(Boolean).join(", ")
                        : "tous les quartiers")
                    : "",
                property: describeProperty(a),
                // v2 (#274): a building's two counts; "Indifférent" when the visitor left it blank, never a missing line.
                levels: needsBuildingCounts(a)
                    ? (a.levelsAny ? "Indifférent" : a.levels != null ? String(a.levels) : "") : "",
                units: needsBuildingCounts(a)
                    ? (a.unitsAny ? "Indifférent" : a.units != null ? String(a.units) : "") : "",
                titleDocuments: asksTitleDocuments(a)
                    ? ((a.titleDocuments || []).map(function (id) {
                        return TITLE_DOCUMENT_LABELS[id];
                    }).filter(Boolean).join(" · ") || "Aucun document exigé")
                    : "",
                price: isStepComplete("budget", a)
                    ? formatPrice(a.priceAmount) + (isSale(a) ? " FCFA" : " FCFA par mois") : "",
                movingPeriod: MOVE_IN_LABELS[a.movingPeriod] || "",
                financing: FINANCING_LABELS[a.financingType] || "",
                contact: isStepComplete("contact", a)
                    ? [a.firstName + " " + a.lastName, a.email, formatPhone(a.whatsappNumber),
                        "Contact " + PERIOD_LABELS[a.preferredContactPeriod]].join("\n")
                    : ""
            };
            var complete = firstIncompleteStep(a) === reviewIndexOf(a);
            $("[data-review-move-in-term]").textContent = isSale(a) ? "Achat prévu"
                : a.propertyCategory === "COMMERCIAL" ? "Date d'installation" : "Date d'emménagement";
            $("[data-review-price-term]").textContent = isSale(a) ? "Budget total" : "Budget mensuel";
            $("[data-review-titleDocuments]").hidden = !asksTitleDocuments(a);
            $("[data-review-financing]").hidden = !isSale(a);
            // v2 (#274): a building purchase shows its two counts and no amenities line.
            $("[data-review-levels]").hidden = !needsBuildingCounts(a);
            $("[data-review-units]").hidden = !needsBuildingCounts(a);
            $("[data-review-amenities]").hidden = !needsAmenities(a);
            Object.keys(values).forEach(function (key) {
                var valueEl = $('[data-review-value="' + key + '"]');
                var missing = $('[data-review-missing="' + key + '"]');
                valueEl.textContent = values[key];
                valueEl.hidden = !values[key];
                missing.hidden = !!values[key];
            });
            // Optional answers are never "missing", only empty.
            $('[data-review-value="amenities"]').textContent = !needsAmenities(a) ? "" :
                AMENITIES.filter(function (amenity) {
                    return a[amenity[0]];
                }).map(function (amenity) {
                    return amenity[1];
                }).join(", ") || "Aucun critère";
            $('[data-review-value="comments"]').textContent = a.comments || "Aucune";
            setActive(submit, complete);
            $("[data-incomplete]").hidden = complete;
        }

        return {
            onShow: function () {
                var answers = loadAnswers();
                var unknown = [answers.cityId].concat(answers.neighborhoodIds || []).filter(function (id) {
                    return id && !locationNames[id];
                });
                render(answers);
                // After a reload the location names aren't loaded yet: fetch them by id, then render again.
                if (unknown.length) {
                    loadLocations({ ids: unknown }).then(function () {
                        render(answers);
                    });
                }
            },
            fields: [],
            collect: function () {
                return {};
            }
        };
    }

    /* ---------- Wizard ---------- */
    // v2 (#274): the count and the track follow the branch's own steps — six for a building purchase, seven otherwise.
    function setProgress(index, keys) {
        var bar = $("[data-progress]");
        var text = "Étape " + (index + 1) + " sur " + keys.length;
        bar.setAttribute("aria-valuemax", String(keys.length));
        bar.setAttribute("aria-valuenow", String(index + 1));
        bar.setAttribute("aria-valuetext", text);
        $("[data-progress-label]").textContent = text;
        $("[data-progress-bar]").style.transform = "scaleX(" + (index + 1) / keys.length + ")";
        // Each step's legend is its accessible name: it carries the same count.
        $all("[data-step]").forEach(function (slide) {
            var legend = $("legend", slide);
            if (!legend) return;
            var name = legend.getAttribute("data-step-name") ||
                legend.textContent.replace(/^Étape \d+ sur \d+\s*:\s*/, "");
            legend.setAttribute("data-step-name", name);
            var i = keys.indexOf(slide.getAttribute("data-step"));
            if (i !== -1) legend.textContent = "Étape " + (i + 1) + " sur " + keys.length + " : " + name;
        });
    }

    /*
     * Shows the project's word in the progress header's pill (data-for="RENTAL" / "SALE"): "Location" or "Achat",
     * rental when no answer is saved yet. The whole pill stays hidden on step 1, whose choice is the control
     * (#278, AC 2); the confirmation's own pill is filled by applyConfirmation() from the submitted listingType.
     */
    function applyProjectPill(listingType) {
        var project = listingType === "SALE" ? "SALE" : "RENTAL";
        $all("[data-progress-block] [data-project] [data-for]").forEach(function (el) {
            el.hidden = el.getAttribute("data-for") !== project;
        });
    }

    function initFindProperty() {
        var form = $("[data-find-form]");
        var slideOf = {};
        STEPS.forEach(function (key) {
            slideOf[key] = $('[data-step="' + key + '"]', form);
        });
        var confirmation = $("[data-confirmation]");
        var formParts = [$("[data-progress-block]"), $("[data-steps-title]"), form];
        var current = 0;
        // A ?sample= seeds the session before the steps read their answers, and names the step it opens.
        var sampleStep = applySample();
        var whatsapp = lazyPhone($("#whatsappNumber"), { name: "whatsappNumber", value: loadAnswers().whatsappNumber });

        function resync() {
            sync(current);
        }

        var stepOf = {
            besoin: needStep(),
            secteur: sectorStep(resync),
            bien: propertyStep(resync),
            equipements: amenitiesStep(),
            budget: budgetStep(),
            contact: contactStep(whatsapp),
            revue: reviewStep()
        };

        // The branch's steps (a building purchase skips the amenities step, #274): an index always reads in them.
        function active() {
            return activeSteps(loadAnswers());
        }

        function slideAt(index) {
            return slideOf[active()[index]];
        }

        function stepAt(index) {
            return stepOf[active()[index]];
        }

        function stepValid(index) {
            return stepAt(index).fields.every(function (field) {
                return field.validate() === "";
            });
        }

        function sync(index) {
            var next = $("[data-next]", slideAt(index));
            if (next) setActive(next, stepValid(index));
        }

        /* History: each slide is an entry; state = { step, returning, depth }. */
        function state() {
            return window.history.state || {};
        }

        function urlFor(index) {
            return window.location.pathname + (index > 0 ? "?etape=" + active()[index] : "");
        }

        function requestedStep() {
            if (typeof state().step === "number") return state().step;
            var index = active().indexOf(new URLSearchParams(window.location.search).get("etape"));
            return index === -1 ? 0 : index;
        }

        // Never open a step whose predecessors aren't complete, whatever the URL or history entry says.
        function allowed(index) {
            return Math.min(index, firstIncompleteStep(loadAnswers()));
        }

        function show(index, moveFocus) {
            confirmation.hidden = true;
            formParts.forEach(function (el) {
                el.hidden = false;
            });
            var keys = active();
            STEPS.forEach(function (key) {
                slideOf[key].hidden = key !== keys[index];
            });
            current = index;
            setProgress(index, keys);
            // The project pill (Achat / Location) from step 2 on: step 1 carries the choice itself (#278, AC 2).
            $("[data-progress-block] [data-project]").hidden = index === 0;
            applyProjectPill(loadAnswers().listingType);

            var slide = slideAt(index);
            var next = $("[data-next]", slide);
            if (next) next.textContent = state().returning ? "Revenir à la revue" : "Suivant";
            if (stepAt(index).onShow) stepAt(index).onShow();
            sync(index);

            if (moveFocus) {
                slide.focus({ preventScroll: true });
                $("[data-progress-block]").scrollIntoView({ block: "start" });
            }
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

        function validateStep(index) {
            var firstInvalid = null;
            stepAt(index).fields.forEach(function (field) {
                var message = field.validate();
                setFieldError(field.target, field.error, message);
                if (message && !firstInvalid) firstInvalid = field;
            });
            if (firstInvalid) {
                var focusable = firstInvalid.target.matches("input, select, textarea, [tabindex]")
                    ? firstInvalid.target
                    : $("input:not(:disabled)", firstInvalid.target);
                if (focusable) focusable.focus();
                return false;
            }
            return true;
        }

        function next(index) {
            if (!validateStep(index)) return;
            saveAnswers(stepAt(index).collect());
            if (!state().returning) {
                go(index + 1);
                return;
            }
            // Editing from the review goes back to it — unless the change left a later step incomplete (e.g. a new
            // category, whose property type isn't chosen yet). That step replaces this entry, so Back still
            // leads to the review.
            var saved = loadAnswers();
            var gap = firstIncompleteStep(saved);
            if (gap === reviewIndexOf(saved)) {
                back(gap);
            } else {
                window.history.replaceState({ step: gap, returning: true, depth: state().depth || 0 }, "", urlFor(gap));
                show(gap, true);
            }
        }

        // The confirmation's wording follows the project (RENTAL by default): a sale reads as the buyer's next step.
        function applyConfirmation(listingType) {
            var project = listingType === "SALE" ? "SALE" : "RENTAL";
            $all("[data-confirmation] [data-for]").forEach(function (el) {
                el.hidden = el.getAttribute("data-for") !== project;
            });
        }

        function showConfirmation(done) {
            formParts.forEach(function (el) {
                el.hidden = true;
            });
            confirmation.hidden = false;
            applyConfirmation(done.listingType);
            $("[data-done-email]").textContent = maskEmail(done.email);
            $("[data-done-whatsapp]").textContent = formatPhone(done.whatsappNumber);
            $("[data-done-period]").textContent = PERIOD_LABELS[done.preferredContactPeriod] || "au moment choisi";
            confirmation.focus({ preventScroll: true });
            window.scrollTo(0, 0);
        }

        function submitRequest() {
            var submit = $("[data-submit]");
            var saved = loadAnswers();
            if (!isActive(submit) || firstIncompleteStep(saved) !== reviewIndexOf(saved)) return;
            setActive(submit, false);
            submit.textContent = "Envoi en cours…";
            announce($("[data-sending]"), "Envoi de votre demande en cours.");
            // PROTOTYPE: the real page POSTs this to /v1/mandates (CreateMandateRequest).
            window.console.info("PROTOTYPE POST /v1/mandates", toRequest(saved));
            window.setTimeout(function () {
                var done = {
                    listingType: saved.listingType,
                    email: saved.email,
                    whatsappNumber: saved.whatsappNumber,
                    preferredContactPeriod: saved.preferredContactPeriod
                };
                store.set(KEYS.done, JSON.stringify(done));
                store.remove(KEYS.answers);
                submit.textContent = "Envoyer ma demande";
                // The confirmation replaces the review entry: it has no URL, and a reload shows it again.
                window.history.replaceState({ done: true, depth: state().depth || 0 }, "", window.location.pathname);
                showConfirmation(done);
            }, 600);
        }

        /* Field listeners: errors appear on blur / next, not while typing; Next turns active once valid. */
        STEPS.forEach(function (key) {
            var step = stepOf[key];
            var slide = slideOf[key];
            // The branch's index is read when it fires: the step list changes with the answers (#274).
            function indexNow() {
                return active().indexOf(key);
            }

            step.fields.forEach(function (field) {
                var refresh = function () {
                    if (field.target.getAttribute("aria-invalid") === "true") {
                        setFieldError(field.target, field.error, field.validate());
                    }
                    var index = indexNow();
                    if (index !== -1) sync(index);
                };
                // A slim-select field shows its errors on the combobox but listens to the native <select>.
                var source = field.source || field.target;
                source.addEventListener("input", refresh);
                source.addEventListener("change", refresh);
                source.addEventListener("countrychange", refresh);
                field.target.addEventListener("focusout", function (event) {
                    if ((field.contains || field.target.contains.bind(field.target))(event.relatedTarget)) return;
                    if (field.touched ? field.touched() : true) setFieldError(field.target, field.error, field.validate());
                });
            });

            var nextButton = $("[data-next]", slide);
            if (nextButton) {
                nextButton.addEventListener("click", function () {
                    next(current);
                });
            }
            // Step 1 has no "Précédent": the wizard is the start of the page. From the review, Next returns to it.
            var prevControl = $("[data-prev]", slide);
            if (prevControl) {
                prevControl.addEventListener("click", function (event) {
                    if (state().returning) {
                        event.preventDefault();
                        back(reviewIndexOf(loadAnswers()));
                    } else if (prevControl.tagName === "BUTTON") {
                        back(current - 1);
                    }
                });
            }
        });

        $all("[data-edit]").forEach(function (button) {
            button.addEventListener("click", function () {
                var index = active().indexOf(button.getAttribute("data-edit"));
                if (index !== -1) go(index, true);
            });
        });

        // Enter in a field moves to the next step; on the review step the form submits the request.
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (current === reviewIndexOf(loadAnswers())) submitRequest();
            else next(current);
        });
        form.addEventListener("keydown", function (event) {
            var target = event.target;
            if (event.key !== "Enter" || !target.matches("input:not([type=checkbox]):not([type=radio])")) return;
            if (target.closest(".iti__dropdown-content")) return; // intl-tel-input's country search
            event.preventDefault();
            if (current === reviewIndexOf(loadAnswers())) submitRequest();
            else next(current);
        });

        window.addEventListener("popstate", function () {
            var done = loadJson(KEYS.done);
            if (state().done && done) {
                showConfirmation(done);
                return;
            }
            var index = allowed(requestedStep());
            if (index !== requestedStep()) {
                window.history.replaceState({ step: index, returning: false, depth: state().depth || 0 }, "", urlFor(index));
            }
            show(index, true);
        });

        /* First render: reloading the confirmation shows it again; otherwise the allowed step (?sample= names it). */
        var done = loadJson(KEYS.done);
        if (state().done && done) {
            showConfirmation(done);
        } else {
            var target = sampleStep != null ? sampleStep : requestedStep();
            var start = allowed(target);
            var returning = sampleStep == null && start === requestedStep() && !!state().returning;
            window.history.replaceState({ step: start, returning: returning, depth: state().depth || 0 }, "", urlFor(start));
            show(start, false);
        }

        // The WhatsApp field becomes usable once intl-tel-input's utils.js is in; until then its step can't be passed.
        window.NdUtil.phoneReady().then(
            function (i18n) {
                whatsapp.bind(i18n);
                if (confirmation.hidden) show(current, false);
                // formatPhone needs utils.js: the confirmation may have shown the raw E.164 number until now.
                else $("[data-done-whatsapp]").textContent = formatPhone((loadJson(KEYS.done) || {}).whatsappNumber);
            },
            function () {
                whatsapp.fail();
                $all("[data-phone-unavailable]").forEach(function (el) {
                    el.hidden = false;
                });
            }
        );
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (document.body.getAttribute("data-page") === "find-property") initFindProperty();
    });
})();
