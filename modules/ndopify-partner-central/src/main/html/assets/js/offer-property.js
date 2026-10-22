/*
 * Ndopify Partner Central — "Proposer un bien" wizard (static prototype).
 * A single page (offer-property.html): seven steps as slides — bien → adresse → conditions → caracteristiques →
 * equipements → contact → revue — then the confirmation, which has no URL of its own. Same mechanics as
 * find-property.js: each slide is a history entry (?etape=…), but the URL is never trusted: a step only opens once
 * every earlier step is complete. Answers live in sessionStorage until the review step submits them as a LISTING
 * mandate (CreateMandateRequest, ndopify-dto — see toRequest). Server calls are simulated; every simulation is
 * marked "PROTOTYPE".
 */
(function () {
    "use strict";

    var STEPS = ["bien", "adresse", "conditions", "caracteristiques", "equipements", "contact", "revue"];
    var REVIEW = STEPS.length - 1;
    var MAX_ADVANCE_MONTHS = 12;
    var MAX_DEPOSIT_MONTHS = 6;
    // How far ahead the availability date may be (days).
    var MAX_AVAILABILITY_DAYS = 365;
    var CURRENCY = "XAF";
    var EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
    var NAME_PATTERN = /^[\p{L}][\p{L}' .-]*$/u;

    // Only rentals are supported for now: the landlord isn't asked, every mandate is a RENTAL.
    var LISTING_TYPE = "RENTAL";
    // Smallest monthly rent worth a listing (FCFA). The BFF must re-check it.
    var MIN_RENT = 5000;

    var KEYS = {
        answers: "npc.offer-property",
        done: "npc.offer-property.done"
    };

    // PROTOTYPE: rows the simulated GET /v1/locations serves, shaped like its Location DTO (ndopify-dto).
    // Same data as find-property.js: city ids are the GeoNames ids; neighbourhood ids are placeholders.
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

    // "Villa, maison" is one answer, as in the specs: it is sent as HOUSE.
    var PROPERTY_TYPES = {
        APARTMENT: { label: "Appartement", categories: ["RESIDENTIAL"] },
        HOUSE: { label: "Villa, maison", categories: ["RESIDENTIAL"] },
        ROOM: { label: "Chambre", categories: ["RESIDENTIAL"] },
        STUDIO: { label: "Studio", categories: ["RESIDENTIAL"] },
        DUPLEX: { label: "Duplex", categories: ["RESIDENTIAL"] },
        BUILDING: { label: "Immeuble entier", categories: ["RESIDENTIAL"] },
        OFFICE: { label: "Bureau", categories: ["COMMERCIAL"] },
        RETAIL: { label: "Commerce, boutique", categories: ["COMMERCIAL"] },
        INDUSTRIAL: { label: "Entrepôt, local industriel", categories: ["COMMERCIAL"] },
        LAND: { label: "Terrain", categories: ["COMMERCIAL"] }
    };

    var ROOM_LABELS = { "1": "1 chambre", "2": "2 chambres", "3": "3 chambres", "4": "4 chambres", "5+": "5 chambres et +" };
    var FURNITURE_LABELS = { FULLY_FURNISHED: "Meublé", UNFURNISHED: "Non meublé" };
    var PARKING_LABELS = { "1": "1 place de parking", "2": "2 places de parking", "3+": "3 places de parking et +" };
    var PERIOD_LABELS = { MORNING: "le matin", AFTERNOON: "l'après-midi", EVENING: "le soir" };

    // The road access is one answer (a radio), sent as the matching boolean of CreateMandateRequest.
    var ROAD_ACCESS = {
        roadPaved: "Route bitumée",
        trackAccessibleByCar: "Piste accessible en voiture",
        trackInaccessibleByCar: "Piste accessible à pied seulement"
    };

    // The other boolean amenities of CreateMandateRequest, in form order.
    var AMENITIES = [
        ["waterMeter", "Compteur d'eau"],
        ["dedicatedWaterMeter", "Compteur d'eau individuel"],
        ["waterWell", "Forage"],
        ["waterTank", "Château d'eau, citerne"],
        ["electricityMeter", "Compteur électrique"],
        ["dedicatedElectricityMeter", "Compteur électrique individuel"],
        ["generatorSet", "Groupe électrogène"],
        ["solarPanel", "Panneaux solaires"],
        ["videoSurveillance", "Vidéosurveillance"],
        ["securityGuard", "Gardien 24 h/24"],
        ["securedFenced", "Barrière, clôture sécurisée"]
    ];

    /*
     * Which questions of the "caracteristiques" step apply, given the category (step 1) and the property type.
     * A residential property has its furnishing, rooms and parking (a room only its furnishing); a commercial one only its surface.
     */
    function typeFits(a) {
        var type = PROPERTY_TYPES[a.propertyType];
        return !!type && type.categories.indexOf(a.propertyCategory) !== -1;
    }

    function isResidential(a) {
        return a.propertyCategory === "RESIDENTIAL";
    }

    // A whole building is let as it is, furnished or not flat by flat: the agent sees it during the audit.
    function needsFurniture(a) {
        return isResidential(a) && a.propertyType !== "BUILDING";
    }

    function needsRooms(a) {
        return isResidential(a) && ["ROOM", "STUDIO", "BUILDING"].indexOf(a.propertyType) === -1;
    }

    // A room is let in a shared home: its parking, if any, isn't its own.
    function needsParking(a) {
        return isResidential(a) && a.propertyType !== "ROOM";
    }

    function needsArea(a) {
        return a.propertyCategory === "COMMERCIAL";
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

    function setChecked(name, value) {
        $all('input[name="' + name + '"]').forEach(function (input) {
            input.checked = value != null && input.value === String(value);
        });
    }

    // Accent- and case-insensitive "contains", so "ekelle" finds "Ngoa-Ekellé" and "cote" finds "Côte d'Ivoire".
    function fold(text) {
        return String(text).normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
    }

    /* ---------- Whole numbers (rent, surface): "150 000" as you type, a Number in the answers ---------- */
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

    /* ---------- Dates (availability): ISO "YYYY-MM-DD" in the answers, in the visitor's time zone ---------- */
    function isoDate(date) {
        var pad = function (n) {
            return (n < 10 ? "0" : "") + n;
        };
        return date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate());
    }

    function today() {
        var now = new Date();
        return new Date(now.getFullYear(), now.getMonth(), now.getDate());
    }

    function parseIsoDate(value) {
        var match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value || "");
        if (!match) return null;
        var date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
        return isoDate(date) === value ? date : null; // rejects 2026-02-31
    }

    function lastAvailableDate() {
        var date = today();
        date.setDate(date.getDate() + MAX_AVAILABILITY_DAYS);
        return date;
    }

    function availabilityValid(value) {
        var date = parseIsoDate(value);
        return !!date && date >= today() && date <= lastAvailableDate();
    }

    // "Dès maintenant" for today, else "Dès le 3 novembre 2026".
    function formatAvailability(value) {
        var date = parseIsoDate(value);
        if (!date) return "";
        if (date.getTime() === today().getTime()) return "Disponible dès maintenant";
        return "Disponible dès le " + date.toLocaleDateString("fr-FR", { day: "numeric", month: "long", year: "numeric" });
    }

    function months(n) {
        return n + " mois";
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
        return area >= 5 && area <= 1000000;
    }

    function monthsValid(value, min, max) {
        return value == null || (Number.isInteger(value) && value >= min && value <= max);
    }

    /*
     * Whether a step's saved answers are complete. This is what guards the wizard: a step opens only when
     * every step before it is complete, whatever the URL says. The BFF must re-check all of it server side.
     */
    function isStepComplete(key, a) {
        switch (key) {
            case "bien":
                return !!CATEGORY_LABELS[a.propertyCategory];
            case "adresse":
                return a.cityId > 0 && a.neighborhoodId > 0;
            case "conditions":
                return a.priceAmount >= MIN_RENT &&
                    monthsValid(a.advanceRent, 1, MAX_ADVANCE_MONTHS) &&
                    a.securityDeposit != null && monthsValid(a.securityDeposit, 1, MAX_DEPOSIT_MONTHS) &&
                    availabilityValid(a.availableFrom);
            case "caracteristiques":
                return typeFits(a) &&
                    (!needsFurniture(a) || !!FURNITURE_LABELS[a.furnitureType]) &&
                    (!needsRooms(a) || !!ROOM_LABELS[a.rooms]) &&
                    (!needsParking(a) || a.parkings === "0" || !!PARKING_LABELS[a.parkings]) &&
                    (!needsArea(a) || areaValid(a.area));
            case "equipements":
                return !!ROAD_ACCESS[a.roadAccess];
            case "contact":
                return !!a.firstName && !!a.lastName && EMAIL_PATTERN.test(a.email || "") &&
                    /^\+[1-9]\d{6,14}$/.test(a.whatsappNumber || "") &&
                    !!PERIOD_LABELS[a.preferredContactPeriod];
            default:
                return false;
        }
    }

    function firstIncompleteStep(answers) {
        for (var i = 0; i < REVIEW; i++) {
            if (!isStepComplete(STEPS[i], answers)) return i;
        }
        return REVIEW;
    }

    /*
     * The saved answers as a CreateMandateRequest (ndopify-dto). Questions that don't apply to the chosen
     * property are sent as null even if an earlier answer is still in the session.
     * Not in CreateMandateRequest yet: availableFrom.
     */
    function toRequest(a) {
        var request = {
            mandateType: "LISTING",
            listingType: LISTING_TYPE,
            propertyCategory: a.propertyCategory,
            cityId: a.cityId,
            neighbourhoodIds: [a.neighborhoodId],
            propertyType: a.propertyType,
            furnitureType: needsFurniture(a) ? a.furnitureType : null,
            rooms: needsRooms(a) ? a.rooms : null,
            parkings: needsParking(a) ? a.parkings : null,
            area: needsArea(a) ? a.area : null,
            price: {
                amount: a.priceAmount,
                currency: CURRENCY,
                periodicity: "MONTHLY"
            },
            advanceRent: a.advanceRent,
            securityDeposit: a.securityDeposit,
            availableFrom: a.availableFrom,
            firstName: a.firstName,
            lastName: a.lastName,
            email: a.email,
            whatsappNumber: a.whatsappNumber,
            preferredContactPeriod: a.preferredContactPeriod
        };
        Object.keys(ROAD_ACCESS).forEach(function (key) {
            request[key] = a.roadAccess === key;
        });
        AMENITIES.forEach(function (amenity) {
            request[amenity[0]] = !!a[amenity[0]];
        });
        return request;
    }

    /* ---------- Steps: fields, saved values, what they collect ---------- */
    function categoryStep() {
        var answers = loadAnswers();
        setChecked("propertyCategory", answers.propertyCategory);
        return {
            fields: [
                radioField("propertyCategory", "Indiquez si le bien est pour y habiter ou pour une activité.")
            ],
            collect: function () {
                return { propertyCategory: checkedValue("propertyCategory") };
            }
        };
    }

    function addressStep(resync) {
        var answers = loadAnswers();
        var citySelect = $("#cityId");
        var neighborhoodSelect = $("#neighborhoodId");
        var group = $('[data-group="neighborhoodId"]');
        var empty = $("[data-neighborhoods-empty]");

        var city = slimSelect(citySelect, {
            placeholderText: "Choisissez une ville",
            ariaLabel: "Ville"
        });
        var neighborhood = slimSelect(neighborhoodSelect, {
            placeholderText: "Choisissez le quartier",
            ariaLabel: "Quartier"
        });

        function cityId() {
            return Number(citySelect.value) || 0;
        }

        function neighborhoodId() {
            return Number(neighborhoodSelect.value) || 0;
        }

        function toOptions(locations, keep, placeholder) {
            return [{ value: "", text: placeholder, placeholder: true }].concat(locations.map(function (l) {
                return { value: String(l.id), text: l.name, selected: l.id === keep };
            }));
        }

        // Each city change loads that city's neighbourhoods once; typing then filters them in the browser.
        var pending = 0;

        function loadNeighborhoods(keep) {
            var id = cityId();
            var request = ++pending;
            empty.hidden = !!id;
            group.hidden = !id;
            neighborhood.slim.setData(toOptions([], null, "Choisissez le quartier"));
            if (!id) return;
            neighborhood.slim.disable();
            loadLocations({ type: "NEIGHBORHOOD", parentId: id }).then(function (locations) {
                if (request !== pending) return; // the city changed again meanwhile
                neighborhood.slim.setData(toOptions(locations, keep, "Choisissez le quartier"));
                neighborhood.slim.enable();
                resync();
            });
        }

        city.slim.disable();
        loadLocations({ type: "CITY", country: "CM" }).then(function (locations) {
            city.slim.setData(toOptions(locations, answers.cityId, "Choisissez une ville"));
            city.slim.enable();
            loadNeighborhoods(answers.neighborhoodId);
            citySelect.addEventListener("change", function () {
                loadNeighborhoods(null);
                setFieldError(neighborhood.control, $("#neighborhoodId-error"), "");
            });
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
                slimField(neighborhoodSelect, neighborhood, {
                    touched: function () {
                        return neighborhoodId() > 0;
                    },
                    validate: function () {
                        if (!cityId()) return "";
                        return neighborhoodId() ? "" : "Choisissez le quartier.";
                    }
                })
            ],
            collect: function () {
                return {
                    cityId: cityId(),
                    neighborhoodId: neighborhoodId()
                };
            }
        };
    }

    function conditionsStep() {
        var answers = loadAnswers();
        var price = $("#priceAmount");
        var advance = $("#advanceRent");
        var deposit = $("#securityDeposit");
        var available = $("#availableFrom");

        price.value = formatNumber(answers.priceAmount);
        advance.value = answers.advanceRent != null ? String(answers.advanceRent) : "";
        deposit.value = answers.securityDeposit != null ? String(answers.securityDeposit) : "";
        available.value = answers.availableFrom || "";
        bindNumber(price, 12);

        function monthsOf(select) {
            return select.value ? Number(select.value) : null;
        }

        return {
            // The bounds move with the day: set them each time the step is shown.
            onShow: function () {
                available.min = isoDate(today());
                available.max = isoDate(lastAvailableDate());
            },
            fields: [
                textField(price, "Indiquez le loyer mensuel souhaité.", function () {
                    if (numberValue(price) < MIN_RENT) return "Indiquez un loyer d'au moins " + formatPrice(MIN_RENT) + " FCFA par mois.";
                    return "";
                }),
                {
                    target: deposit,
                    error: $("#securityDeposit-error"),
                    touched: function () {
                        return deposit.value !== "";
                    },
                    validate: function () {
                        return deposit.value ? "" : "Indiquez la caution demandée, en mois de loyer.";
                    }
                },
                {
                    target: available,
                    error: $("#availableFrom-error"),
                    touched: function () {
                        return available.value !== "";
                    },
                    validate: function () {
                        if (!available.value) return "Indiquez à partir de quand le bien est libre.";
                        if (!availabilityValid(available.value)) {
                            return "Choisissez une date entre aujourd'hui et dans " + MAX_AVAILABILITY_DAYS + " jours.";
                        }
                        return "";
                    }
                }
            ],
            collect: function () {
                return {
                    priceAmount: numberValue(price),
                    advanceRent: monthsOf(advance),
                    securityDeposit: monthsOf(deposit),
                    availableFrom: available.value
                };
            }
        };
    }

    function propertyStep(resync) {
        var answers = loadAnswers();
        var area = $("#area");
        var groups = {
            furnitureType: $('[data-group="furnitureType"]'),
            rooms: $('[data-group="rooms"]'),
            hasParking: $('[data-group="hasParking"]'),
            parkings: $('[data-group="parkings"]'),
            area: $('[data-group="area"]')
        };

        setChecked("propertyType", answers.propertyType);
        setChecked("furnitureType", answers.furnitureType);
        setChecked("rooms", answers.rooms);
        // parkings is "0" for "Non", else the number of places: the Oui / Non question is UI only.
        setChecked("hasParking", answers.parkings == null ? null : answers.parkings === "0" ? "NO" : "YES");
        setChecked("parkings", answers.parkings === "0" ? null : answers.parkings);
        area.value = formatNumber(answers.area);
        bindNumber(area, 7);

        // The category from step 1 (saved) with the type being chosen now (live).
        function live() {
            var saved = loadAnswers();
            return {
                propertyCategory: saved.propertyCategory,
                propertyType: checkedValue("propertyType")
            };
        }

        function asksPlaces() {
            return needsParking(live()) && checkedValue("hasParking") === "YES";
        }

        function areaValue() {
            return area.value.trim() ? numberValue(area) : null;
        }

        // Shows only the questions that apply; a hidden question is never in error.
        function updateQuestions() {
            var a = live();
            var chosen = typeFits(a);
            var shown = {
                furnitureType: chosen && needsFurniture(a),
                rooms: chosen && needsRooms(a),
                hasParking: chosen && needsParking(a),
                parkings: chosen && asksPlaces(),
                area: chosen && needsArea(a)
            };
            Object.keys(groups).forEach(function (key) {
                groups[key].hidden = !shown[key];
                if (!shown[key]) setFieldError(groups[key], $("#" + key + "-error"), "");
            });
            $("[data-area-label]").textContent = a.propertyType === "LAND" ? "Surface du terrain estimée" : "Surface estimée";
        }

        $all('input[name="propertyType"], input[name="hasParking"]').forEach(function (input) {
            input.addEventListener("change", function () {
                updateQuestions();
                resync();
            });
        });

        return {
            // Step 1 may have changed the category since this step was last shown: offer only its types.
            onShow: function () {
                var category = loadAnswers().propertyCategory;
                $all('[data-group="propertyType"] .choice').forEach(function (choice) {
                    var fits = choice.getAttribute("data-categories").split(" ").indexOf(category) !== -1;
                    var input = $("input", choice);
                    choice.hidden = !fits;
                    input.disabled = !fits;
                    if (!fits) input.checked = false;
                });
                updateQuestions();
            },
            fields: [
                radioField("propertyType", "Choisissez le type de bien."),
                radioField("furnitureType", "Indiquez si le bien est meublé.", function () {
                    return needsFurniture(live());
                }),
                radioField("rooms", "Indiquez le nombre de chambres.", function () {
                    return needsRooms(live());
                }),
                radioField("hasParking", "Indiquez si le bien a un parking ou un garage.", function () {
                    return needsParking(live());
                }),
                radioField("parkings", "Indiquez le nombre de places.", asksPlaces),
                {
                    target: area,
                    error: $("#area-error"),
                    touched: function () {
                        return area.value.trim() !== "";
                    },
                    validate: function () {
                        if (groups.area.hidden) return "";
                        if (!area.value.trim()) return "Indiquez la surface, même approximative.";
                        return areaValid(areaValue()) ? "" : "Indiquez une surface d'au moins 5 m².";
                    }
                }
            ],
            collect: function () {
                var a = live();
                var parkings = null;
                if (needsParking(a)) parkings = checkedValue("hasParking") === "NO" ? "0" : checkedValue("parkings");
                return {
                    propertyType: a.propertyType,
                    furnitureType: needsFurniture(a) ? checkedValue("furnitureType") : null,
                    rooms: needsRooms(a) ? checkedValue("rooms") : null,
                    parkings: parkings,
                    area: needsArea(a) ? areaValue() : null
                };
            }
        };
    }

    function amenitiesStep() {
        var answers = loadAnswers();
        setChecked("roadAccess", answers.roadAccess);
        AMENITIES.forEach(function (amenity) {
            $('input[name="' + amenity[0] + '"]').checked = !!answers[amenity[0]];
        });
        return {
            fields: [
                radioField("roadAccess", "Indiquez comment on accède au bien.")
            ],
            collect: function () {
                var values = { roadAccess: checkedValue("roadAccess") };
                AMENITIES.forEach(function (amenity) {
                    values[amenity[0]] = $('input[name="' + amenity[0] + '"]').checked;
                });
                return values;
            }
        };
    }

    function contactStep(phone) {
        var answers = loadAnswers();
        var firstName = $("#firstName");
        var lastName = $("#lastName");
        var email = $("#email");

        firstName.value = answers.firstName || "";
        lastName.value = answers.lastName || "";
        email.value = answers.email || "";
        setChecked("preferredContactPeriod", answers.preferredContactPeriod);

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
                    preferredContactPeriod: checkedValue("preferredContactPeriod")
                };
            }
        };
    }

    function reviewStep() {
        var submit = $("[data-submit]");

        function describeProperty(a) {
            if (!isStepComplete("caracteristiques", a)) return "";
            var parts = [PROPERTY_TYPES[a.propertyType].label];
            if (needsFurniture(a)) parts.push(FURNITURE_LABELS[a.furnitureType]);
            if (needsRooms(a)) parts.push(ROOM_LABELS[a.rooms]);
            if (needsParking(a)) parts.push(a.parkings === "0" ? "Sans parking" : PARKING_LABELS[a.parkings]);
            if (needsArea(a)) parts.push(formatNumber(a.area) + " m²");
            return parts.join(" · ");
        }

        function describeLocation(a) {
            if (!locationNames[a.cityId] || !locationNames[a.neighborhoodId]) return "";
            return locationNames[a.neighborhoodId] + ", " + locationNames[a.cityId];
        }

        function describeConditions(a) {
            if (!isStepComplete("conditions", a)) return "";
            return [
                formatPrice(a.priceAmount) + " FCFA par mois",
                a.advanceRent ? "Avance de " + months(a.advanceRent) : "Sans avance de loyer",
                "Caution de " + months(a.securityDeposit),
                formatAvailability(a.availableFrom)
            ].join("\n");
        }

        function describeAmenities(a) {
            if (!ROAD_ACCESS[a.roadAccess]) return "";
            return [ROAD_ACCESS[a.roadAccess]].concat(AMENITIES.filter(function (amenity) {
                return a[amenity[0]];
            }).map(function (amenity) {
                return amenity[1];
            })).join(", ");
        }

        function render(a) {
            var values = {
                category: CATEGORY_LABELS[a.propertyCategory] ? "Location, " + CATEGORY_LABELS[a.propertyCategory] : "",
                location: describeLocation(a),
                conditions: describeConditions(a),
                property: describeProperty(a),
                amenities: describeAmenities(a),
                contact: isStepComplete("contact", a)
                    ? [a.firstName + " " + a.lastName, a.email, formatPhone(a.whatsappNumber),
                        "Contact " + PERIOD_LABELS[a.preferredContactPeriod]].join("\n")
                    : ""
            };
            var complete = firstIncompleteStep(a) === REVIEW;
            Object.keys(values).forEach(function (key) {
                var valueEl = $('[data-review-value="' + key + '"]');
                var missing = $('[data-review-missing="' + key + '"]');
                valueEl.textContent = values[key];
                valueEl.hidden = !values[key];
                missing.hidden = !!values[key];
            });
            setActive(submit, complete);
            $("[data-incomplete]").hidden = complete;
        }

        return {
            onShow: function () {
                var answers = loadAnswers();
                var unknown = [answers.cityId, answers.neighborhoodId].filter(function (id) {
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
    function setProgress(index) {
        var bar = $("[data-progress]");
        var text = "Étape " + (index + 1) + " sur " + STEPS.length;
        bar.setAttribute("aria-valuenow", String(index + 1));
        bar.setAttribute("aria-valuetext", text);
        $("[data-progress-label]").textContent = text;
        $("[data-progress-bar]").style.transform = "scaleX(" + (index + 1) / STEPS.length + ")";
    }

    function initOfferProperty() {
        var form = $("[data-offer-form]");
        var slides = STEPS.map(function (key) {
            return $('[data-step="' + key + '"]', form);
        });
        var confirmation = $("[data-confirmation]");
        var formParts = [$("[data-progress-block]"), $("[data-steps-title]"), form];
        var current = 0;
        var whatsapp = lazyPhone($("#whatsappNumber"), { name: "whatsappNumber", value: loadAnswers().whatsappNumber });

        function resync() {
            sync(current);
        }

        var steps = [
            categoryStep(),
            addressStep(resync),
            conditionsStep(),
            propertyStep(resync),
            amenitiesStep(),
            contactStep(whatsapp),
            reviewStep()
        ];

        function stepValid(index) {
            return steps[index].fields.every(function (field) {
                return field.validate() === "";
            });
        }

        function sync(index) {
            var next = $("[data-next]", slides[index]);
            if (next) setActive(next, stepValid(index));
        }

        /* History: each slide is an entry; state = { step, returning, depth }. */
        function state() {
            return window.history.state || {};
        }

        function urlFor(index) {
            return window.location.pathname + (index > 0 ? "?etape=" + STEPS[index] : "");
        }

        function requestedStep() {
            if (typeof state().step === "number") return state().step;
            var index = STEPS.indexOf(new URLSearchParams(window.location.search).get("etape"));
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
            slides.forEach(function (slide, i) {
                slide.hidden = i !== index;
            });
            current = index;
            setProgress(index);

            var next = $("[data-next]", slides[index]);
            if (next) next.textContent = state().returning ? "Revenir à la revue" : "Suivant";
            if (steps[index].onShow) steps[index].onShow();
            sync(index);

            if (moveFocus) {
                slides[index].focus({ preventScroll: true });
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
            steps[index].fields.forEach(function (field) {
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
            saveAnswers(steps[index].collect());
            if (!state().returning) {
                go(index + 1);
                return;
            }
            // Editing from the review goes back to it — unless the change left a later step incomplete (e.g. a new
            // category, whose property type isn't chosen yet). That step replaces this entry, so Back still
            // leads to the review.
            var gap = firstIncompleteStep(loadAnswers());
            if (gap === REVIEW) {
                back(REVIEW);
            } else {
                window.history.replaceState({ step: gap, returning: true, depth: state().depth || 0 }, "", urlFor(gap));
                show(gap, true);
            }
        }

        function showConfirmation(done) {
            formParts.forEach(function (el) {
                el.hidden = true;
            });
            confirmation.hidden = false;
            $("[data-done-email]").textContent = maskEmail(done.email);
            $("[data-done-whatsapp]").textContent = formatPhone(done.whatsappNumber);
            $("[data-done-period]").textContent = PERIOD_LABELS[done.preferredContactPeriod] || "au moment choisi";
            confirmation.focus({ preventScroll: true });
            window.scrollTo(0, 0);
        }

        function submitRequest() {
            var submit = $("[data-submit]");
            var saved = loadAnswers();
            if (!isActive(submit) || firstIncompleteStep(saved) !== REVIEW) return;
            setActive(submit, false);
            submit.textContent = "Envoi en cours…";
            announce($("[data-sending]"), "Enregistrement de votre bien en cours.");
            // PROTOTYPE: the real page POSTs this to /v1/mandates (CreateMandateRequest).
            window.console.info("PROTOTYPE POST /v1/mandates", toRequest(saved));
            window.setTimeout(function () {
                var done = {
                    email: saved.email,
                    whatsappNumber: saved.whatsappNumber,
                    preferredContactPeriod: saved.preferredContactPeriod
                };
                store.set(KEYS.done, JSON.stringify(done));
                store.remove(KEYS.answers);
                submit.textContent = "Enregistrer mon bien";
                // The confirmation replaces the review entry: it has no URL, and a reload shows it again.
                window.history.replaceState({ done: true, depth: state().depth || 0 }, "", window.location.pathname);
                showConfirmation(done);
            }, 600);
        }

        /* Field listeners: errors appear on blur / next, not while typing; Next turns active once valid. */
        steps.forEach(function (step, index) {
            step.fields.forEach(function (field) {
                var refresh = function () {
                    if (field.target.getAttribute("aria-invalid") === "true") {
                        setFieldError(field.target, field.error, field.validate());
                    }
                    sync(index);
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

            var nextButton = $("[data-next]", slides[index]);
            if (nextButton) {
                nextButton.addEventListener("click", function () {
                    next(index);
                });
            }
            // Step 1 has no "Précédent": the wizard is the start of the page. From the review, Next returns to it.
            var prevControl = $("[data-prev]", slides[index]);
            if (prevControl) {
                prevControl.addEventListener("click", function (event) {
                    if (state().returning) {
                        event.preventDefault();
                        back(REVIEW);
                    } else if (prevControl.tagName === "BUTTON") {
                        back(index - 1);
                    }
                });
            }
        });

        $all("[data-edit]").forEach(function (button) {
            button.addEventListener("click", function () {
                go(STEPS.indexOf(button.getAttribute("data-edit")), true);
            });
        });

        // Enter in a field moves to the next step; on the review step the form submits the listing.
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (current === REVIEW) submitRequest();
            else next(current);
        });
        form.addEventListener("keydown", function (event) {
            var target = event.target;
            if (event.key !== "Enter" || !target.matches("input:not([type=checkbox]):not([type=radio])")) return;
            if (target.closest(".iti__dropdown-content")) return; // intl-tel-input's country search
            if (target.closest(".ss-content")) return; // slim-select's search
            event.preventDefault();
            if (current === REVIEW) submitRequest();
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

        /* First render: reloading the confirmation shows it again; otherwise the allowed step. */
        var done = loadJson(KEYS.done);
        if (state().done && done) {
            showConfirmation(done);
        } else {
            var start = allowed(requestedStep());
            var returning = start === requestedStep() && !!state().returning;
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
        if (document.body.getAttribute("data-page") === "offer-property") initOfferProperty();
    });
})();
