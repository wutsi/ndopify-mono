/*
 * Ndopify Partner Central — "Trouver un bien" wizard (static prototype).
 * A single page (find-property.html): seven steps as slides — besoin → secteur → bien → equipements → budget →
 * contact → revue — then the confirmation, which has no URL of its own. Same mechanics as rejoindre.js: each slide
 * is a history entry (?etape=…), but the URL is never trusted: a step only opens once every earlier step is
 * complete. Answers live in sessionStorage until the review step submits them as a TENANT mandate
 * (CreateMandateRequest, ndopify-dto — see toRequest). Server calls are simulated; every simulation is marked
 * "PROTOTYPE".
 */
(function () {
    "use strict";

    var STEPS = ["besoin", "secteur", "bien", "equipements", "budget", "contact", "revue"];
    var REVIEW = STEPS.length - 1;
    var MAX_NEIGHBORHOODS = 3;
    var MAX_COMMENTS = 500;
    var CURRENCY = "XAF";
    var EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
    var NAME_PATTERN = /^[\p{L}][\p{L}' .-]*$/u;

    // Only rentals are supported for now: the visitor isn't asked, every mandate is a RENTAL.
    var LISTING_TYPE = "RENTAL";
    // Smallest monthly rent worth a search (FCFA). The BFF must re-check it.
    var MIN_RENT = 5000;

    var KEYS = {
        answers: "npc.find-property",
        done: "npc.find-property.done"
    };

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
        LAND: { label: "Terrain", categories: ["RESIDENTIAL", "COMMERCIAL"] }
    };

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
        return type.categories.indexOf(a.propertyCategory) !== -1;
    }

    function needsRooms(a) {
        return a.propertyCategory === "RESIDENTIAL" && ["APARTMENT", "HOUSE", "VILLA", "DUPLEX"].indexOf(a.propertyType) !== -1;
    }

    function needsFurniture(a) {
        return ["LAND", "BUILDING", "INDUSTRIAL"].indexOf(a.propertyType) === -1;
    }

    function asksParkings(a) {
        return ["LAND", "ROOM"].indexOf(a.propertyType) === -1;
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

    // A price to read, not to type: "150K", "12M", "1,5M" (FCFA has no cents, so K and M are enough).
    function formatPrice(n) {
        n = Number(n) || 0;
        var short = function (value, unit) {
            return value.toLocaleString("fr-FR", { maximumFractionDigits: 2 }) + unit;
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
    function isStepComplete(key, a) {
        switch (key) {
            case "besoin":
                return !!CATEGORY_LABELS[a.propertyCategory];
            case "secteur":
                return a.cityId > 0 && Array.isArray(a.neighborhoodIds) &&
                    a.neighborhoodIds.length >= 1 && a.neighborhoodIds.length <= MAX_NEIGHBORHOODS;
            case "bien":
                return typeFits(a) &&
                    (!needsRooms(a) || !!ROOM_LABELS[a.rooms]) &&
                    (!needsFurniture(a) || !!FURNITURE_LABELS[a.furnitureType]) &&
                    areaValid(a.area);
            case "equipements":
                return true; // every criterion is optional
            case "budget":
                return a.priceAmount >= MIN_RENT &&
                    !!MOVE_IN_LABELS[a.movingPeriod];
            case "contact":
                return !!a.firstName && !!a.lastName && EMAIL_PATTERN.test(a.email || "") &&
                    /^\+[1-9]\d{6,14}$/.test(a.whatsappNumber || "") && !!PERIOD_LABELS[a.preferredContactPeriod];
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
     */
    function toRequest(a) {
        var request = {
            mandateType: "TENANT",
            listingType: LISTING_TYPE,
            propertyCategory: a.propertyCategory,
            cityId: a.cityId,
            neighbourhoodIds: a.neighborhoodIds,
            propertyType: propertyTypeOf(a),
            furnitureType: needsFurniture(a) && a.furnitureType !== "ANY" ? a.furnitureType : null,
            rooms: needsRooms(a) ? a.rooms : null,
            parkings: asksParkings(a) && a.parkings && a.parkings !== "ANY" ? a.parkings : null,
            area: a.area || null,
            price: {
                amount: a.priceAmount,
                currency: CURRENCY,
                periodicity: "MONTHLY"
            },
            // CreateMandateRequest.movingPeriod (MovingPeriod, ndopify-dto).
            movingPeriod: a.movingPeriod,
            firstName: a.firstName,
            lastName: a.lastName,
            email: a.email,
            whatsappNumber: a.whatsappNumber,
            preferredContactPeriod: a.preferredContactPeriod,
            comments: a.comments || null
        };
        AMENITIES.forEach(function (amenity) {
            request[amenity[0]] = !!a[amenity[0]];
        });
        return request;
    }

    /* ---------- Steps: fields, saved values, what they collect ---------- */
    function needStep() {
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
                        if (count === 0) return "Choisissez au moins un quartier.";
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
        var groups = {
            rooms: $('[data-group="rooms"]'),
            furnitureType: $('[data-group="furnitureType"]'),
            parkings: $('[data-group="parkings"]'),
            area: $('[data-group="area"]')
        };

        setChecked("propertyType", answers.propertyType);
        setChecked("rooms", answers.rooms);
        setChecked("furnitureType", answers.furnitureType);
        setChecked("parkings", answers.parkings);
        area.value = formatNumber(answers.area);
        bindNumber(area, 7);

        // The project from step 1 (saved) with the type being chosen now (live).
        function live() {
            var saved = loadAnswers();
            return {
                propertyCategory: saved.propertyCategory,
                propertyType: checkedValue("propertyType")
            };
        }

        function areaValue() {
            return area.value.trim() ? numberValue(area) : null;
        }

        // Shows only the follow-up questions that apply to the chosen type; a hidden question is never in error.
        function updateQuestions() {
            var a = live();
            var shown = {
                rooms: needsRooms(a),
                furnitureType: needsFurniture(a),
                parkings: asksParkings(a),
                area: true
            };
            Object.keys(groups).forEach(function (key) {
                groups[key].hidden = !shown[key];
                if (!shown[key]) setFieldError(groups[key], $("#" + key + "-error"), "");
            });
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
                radioField("rooms", "Indiquez le nombre de chambres.", function () {
                    return needsRooms(live());
                }),
                radioField("furnitureType", "Indiquez si le bien doit être meublé.", function () {
                    return needsFurniture(live());
                }),
                {
                    target: area,
                    error: $("#area-error"),
                    touched: function () {
                        return area.value.trim() !== "";
                    },
                    validate: function () {
                        if (groups.area.hidden || areaValid(areaValue())) return "";
                        return "Indiquez une surface d'au moins 5 m², ou laissez ce champ vide.";
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
                    area: areaValue()
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
        price.value = formatNumber(answers.priceAmount);
        setChecked("movingPeriod", answers.movingPeriod);
        bindNumber(price, 12);

        return {
            onShow: function () {
                // A shop or an office is moved into too, but "s'installer" reads better for an activity.
                $("[data-move-in-label]").textContent = loadAnswers().propertyCategory === "COMMERCIAL"
                    ? "Date d'installation souhaitée" : "Date d'emménagement souhaitée";
            },
            fields: [
                textField(price, "Indiquez votre budget mensuel.", function () {
                    if (numberValue(price) < MIN_RENT) return "Indiquez un loyer d'au moins " + formatPrice(MIN_RENT) + " FCFA par mois.";
                    return "";
                }),
                radioField("movingPeriod", "Indiquez quand vous souhaitez emménager.")
            ],
            collect: function () {
                return { priceAmount: numberValue(price), movingPeriod: checkedValue("movingPeriod") };
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
                project: CATEGORY_LABELS[a.propertyCategory] ? "Location, " + CATEGORY_LABELS[a.propertyCategory] : "",
                sector: locationNames[a.cityId] && (a.neighborhoodIds || []).length
                    ? locationNames[a.cityId] + " — " + a.neighborhoodIds.map(function (id) {
                        return locationNames[id];
                    }).filter(Boolean).join(", ")
                    : "",
                property: describeProperty(a),
                price: isStepComplete("budget", a)
                    ? formatPrice(a.priceAmount) + " FCFA par mois" : "",
                movingPeriod: MOVE_IN_LABELS[a.movingPeriod] || "",
                contact: isStepComplete("contact", a)
                    ? [a.firstName + " " + a.lastName, a.email, formatPhone(a.whatsappNumber),
                        "Contact " + PERIOD_LABELS[a.preferredContactPeriod]].join("\n")
                    : ""
            };
            var complete = firstIncompleteStep(a) === REVIEW;
            $("[data-review-move-in-term]").textContent = a.propertyCategory === "COMMERCIAL"
                ? "Date d'installation" : "Date d'emménagement";
            Object.keys(values).forEach(function (key) {
                var valueEl = $('[data-review-value="' + key + '"]');
                var missing = $('[data-review-missing="' + key + '"]');
                valueEl.textContent = values[key];
                valueEl.hidden = !values[key];
                missing.hidden = !!values[key];
            });
            // Optional answers are never "missing", only empty.
            $('[data-review-value="amenities"]').textContent = AMENITIES.filter(function (amenity) {
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
    function setProgress(index) {
        var bar = $("[data-progress]");
        var text = "Étape " + (index + 1) + " sur " + STEPS.length;
        bar.setAttribute("aria-valuenow", String(index + 1));
        bar.setAttribute("aria-valuetext", text);
        $("[data-progress-label]").textContent = text;
        $("[data-progress-bar]").style.transform = "scaleX(" + (index + 1) / STEPS.length + ")";
    }

    function initFindProperty() {
        var form = $("[data-find-form]");
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
            needStep(),
            sectorStep(resync),
            propertyStep(resync),
            amenitiesStep(),
            budgetStep(),
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
            announce($("[data-sending]"), "Envoi de votre demande en cours.");
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
                submit.textContent = "Envoyer ma demande";
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

        // Enter in a field moves to the next step; on the review step the form submits the request.
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (current === REVIEW) submitRequest();
            else next(current);
        });
        form.addEventListener("keydown", function (event) {
            var target = event.target;
            if (event.key !== "Enter" || !target.matches("input:not([type=checkbox]):not([type=radio])")) return;
            if (target.closest(".iti__dropdown-content")) return; // intl-tel-input's country search
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
        if (document.body.getAttribute("data-page") === "find-property") initFindProperty();
    });
})();
