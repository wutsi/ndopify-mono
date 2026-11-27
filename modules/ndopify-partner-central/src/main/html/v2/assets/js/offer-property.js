/*
 * Ndopify Partner Central — "Proposer un bien" wizard (static prototype).
 * A single page (offer-property.html): seven steps as slides — bien → adresse → conditions → caracteristiques →
 * equipements → contact → revue — then the confirmation, which has no URL of its own. Same mechanics as
 * find-property.js: each slide is a history entry (?etape=…), but the URL is never trusted: a step only opens once
 * every earlier step is complete. Answers live in sessionStorage until the review step submits them as a LISTING
 * mandate (CreateMandateRequest, ndopify-dto — see toRequest). Server calls are simulated; every simulation is
 * marked "PROTOTYPE".
 *
 * v2 — sales. Step 1 asks the project, listingType: RENTAL ("Mettre en location") or SALE ("Vendre"), preselected
 * from ?type=SALE|RENTAL (the public site's links) while no answer is saved. A sale:
 *  - asks a "Prix de vente" in FCFA, sent with periodicity NONE, of at least MIN_SALE_PRICE;
 *  - has no advance nor deposit (hidden, sent as null); availableFrom is kept ("Disponible à partir du" reads as
 *    well for a sale);
 *  - offers no room (ROOM); a plot (LAND) and a building (BUILDING) are sold only (#304), a plot under Résidentiel
 *    and Commercial, a building under Commercial (the shared rule table says so, see util.js);
 *  - asks its ownership documents (ownershipDocuments) whatever its type (Titre foncier / Plan de bornage /
 *    Certificat de propriété, optional and several, none being a valid answer), shown on the review as "Document de
 *    propriété" with "Aucun document déclaré" when none is ticked (#304);
 *  - shows the page's data-for="SALE" texts instead of the data-for="RENTAL" ones (applyProject), and its
 *    Quitter / confirmation links go to {publicPortalUrl}/owner/sell instead of /owner.
 *
 * #273 — a sale's follow-up questions and a plot's rate:
 *  - #304 removed the old preview: a follow-up question shows only once its type is chosen, a sale and a rental
 *    alike (the ownership documents excepted, asked by every sale from the step's first render);
 *  - the ownership documents are visible for every sale, from the step's first render (#304);
 *  - a sold plot shows its implied rate under its surface, short ("Soit ≈ 56,3K FCFA/m² pour 800 m²", display only)
 *    and the review repeats it right after the total asking price, before the availability;
 *  - the review of a sale reads its total price short like every displayed amount ("45M FCFA", #292 overriding
 *    #273's full form); inputs keep their exact digits.
 *  - once step 1 is answered, every later step — the review included — and the confirmation carry the project
 *    ("Vente" / "Location") as a read-only pill next to the step count / under "Bien enregistré" (#278, AC 1, 3).
 *    Step 1 keeps its choice only; the word is the page's data-for pair, so a land sale reads "Vente" like any
 *    other.
 *  - ?sample= shortcuts seed the answers of these states (README › offer-property.html).
 *
 * #275 — a plot of land is sold, never let:
 *  - a rental offers Terrain under neither category (the rule table's rental categories are empty): a posted
 *    propertyType=LAND is refused by the rules ("Choisissez le type de bien."), as the BFF does;
 *  - a sale offers it under Résidentiel and Commercial (#293), with its documents, surface and rate; it is one
 *    choice, kept when the use changes.
 *
 * #274 — a building sold as a whole is captured by two counts, its levels and its units (whole numbers, both
 * required), in place of the rooms and the parking, and without the amenities step: six steps for that branch (the
 * progress, its labels, the arrows and ?etape= follow it), seven otherwise.
 *
 * #304 — one rule set with Trouver un bien and mandate/edit.html, read from NdUtil.propertyRules (util.js):
 *  - a building and a plot of land are sold only: a rental offers neither;
 *  - a follow-up question shows only once a type is chosen, a sale and a rental alike (the ownership documents
 *    excepted, every sale);
 *  - a sale asks its ownership documents whatever the type (optional, several or none, "Aucun document déclaré"
 *    on the review when none is declared), a rental never asks nor sends them;
 *  - a residential type asks its furniture on a sale too (and a room its parking), and no surface at all;
 *  - a sold building shows its price per unit under its unités ("Soit ≈ 12,5M FCFA/unité pour 8 unités"), a sold plot
 *    its price per m² under its surface; the review repeats the same line after the price. Display only, rounded to
 *    the nearest 100 FCFA, never sent nor stored;
 *  - the price and the surface are grouped by thousands as typed, with the caret kept after the same digit.
 */
(function () {
    "use strict";

    var STEPS = ["bien", "adresse", "conditions", "caracteristiques", "equipements", "contact", "revue"];
    var MAX_LEVELS = 30;
    var MAX_UNITS = 500;
    var MAX_ADVANCE_MONTHS = 12;
    var MAX_DEPOSIT_MONTHS = 6;
    // How far ahead the availability date may be (days).
    var MAX_AVAILABILITY_DAYS = 365;
    var CURRENCY = "XAF";
    var EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
    // #380: the one sentence the three public forms show when ndopify-server refuses the contact e-mail.
    var EMAIL_INVALID = "Cette adresse email n'est pas valide.";
    var NAME_PATTERN = /^[\p{L}][\p{L}' .-]*$/u;

    // The project of step 1 (ListingType, ndopify-dto).
    var LISTING_TYPES = { RENTAL: "Location", SALE: "Vente" };
    // Smallest monthly rent worth a listing (FCFA). The BFF must re-check it.
    var MIN_RENT = 5000;
    // Smallest sale price worth a listing (FCFA). The BFF must re-check it.
    var MIN_SALE_PRICE = 1000000;
    // PROTOTYPE: the public site's owner pages ({publicPortalUrl}/owner and /owner/sell).
    var OWNER_URLS = {
        RENTAL: "https://www.ndopify.com/owner",
        SALE: "https://www.ndopify.com/owner/sell"
    };

    var KEYS = {
        answers: "npc.offer-property",
        done: "npc.offer-property.done",
        sample: "npc.offer-property.sample",
        // #380: set while the contact step shows the server's e-mail refusal, cleared on the first edit.
        emailInvalid: "npc.offer-property.email-invalid"
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

    // The property-type rules shared by the three pages (#304): categories per project, and the questions of a type.
    var RULES = window.NdUtil.propertyRules;

    // "Villa, maison" is one answer, as in the specs: it is sent as HOUSE.
    var PROPERTY_TYPES = {
        APARTMENT: { label: "Appartement" },
        HOUSE: { label: "Villa, maison" },
        ROOM: { label: "Chambre" },
        STUDIO: { label: "Studio" },
        DUPLEX: { label: "Duplex" },
        BUILDING: { label: "Immeuble entier" },
        OFFICE: { label: "Bureau" },
        RETAIL: { label: "Commerce, boutique" },
        INDUSTRIAL: { label: "Entrepôt, local industriel" },
        LAND: { label: "Terrain" }
    };

    var ROOM_LABELS = { "1": "1 chambre", "2": "2 chambres", "3": "3 chambres", "4": "4 chambres", "5+": "5 chambres et +" };
    var FURNITURE_LABELS = { FULLY_FURNISHED: "Meublé", UNFURNISHED: "Non meublé" };
    var PARKING_LABELS = { "1": "1 place de parking", "2": "2 places de parking", "3+": "3 places de parking et +" };
    var PERIOD_LABELS = { MORNING: "le matin", AFTERNOON: "l'après-midi", EVENING: "le soir" };

    // The documents a seller may declare (optional, several): asked for every sale (#304). An owner with none can
    // continue: the agent checks the documents at the audit.
    var OWNERSHIP_DOCUMENT_LABELS = RULES.DOCUMENTS.reduce(function (labels, document) {
        labels[document.value] = document.label;
        return labels;
    }, {});

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

    // The categories of a type come from the shared table (#304): a sale offers a plot of land under Résidentiel
    // and Commercial, a building under Commercial only; a rental offers neither.
    function isSale(a) {
        return a.listingType === "SALE";
    }

    // A building sold as a whole (#274): its two counts replace the rooms and the parking, and the amenities step
    // leaves the wizard. A rental building is untouched.
    function isBuildingSale(a) {
        return isSale(a) && a.propertyType === "BUILDING";
    }

    function minPrice(a) {
        return isSale(a) ? MIN_SALE_PRICE : MIN_RENT;
    }

    /*
     * Which questions of the "caracteristiques" step apply, given the project and category (step 1) and the property
     * type. The shared table (#304) says what each type asks; a type offered under neither category of the step fits
     * none. A sale no longer asks a surface to a residential type, and asks its furniture like a rental.
     */
    function categoriesOf(a) {
        return RULES.categories(a.propertyType, a.listingType);
    }

    function typeFits(a) {
        return categoriesOf(a).indexOf(a.propertyCategory) !== -1;
    }

    /*
     * Whether the type being chosen is chosen and offered by the step-1 category. A follow-up question shows only
     * once it is (#304, replacing #273's preview).
     */
    function isKnown(a) {
        return !!PROPERTY_TYPES[a.propertyType] && typeFits(a);
    }

    // A whole building is counted by its units: it asks no furniture, no room nor parking.
    function needsFurniture(a) {
        return isKnown(a) && RULES.asks(a.propertyType, a.listingType, "furniture");
    }

    function needsRooms(a) {
        return isKnown(a) && RULES.asks(a.propertyType, a.listingType, "rooms");
    }

    // A room asks its parking like every residential type (#304, settled 2026-11-24). A plot and a building have none.
    function needsParking(a) {
        return isKnown(a) && RULES.asks(a.propertyType, a.listingType, "parkings");
    }

    // v2 (#274): a building sold asks its two counts, and no amenities step.
    function needsBuildingCounts(a) {
        return isKnown(a) && RULES.asks(a.propertyType, a.listingType, "counts");
    }

    function needsAmenities(a) {
        return !isBuildingSale(a);
    }

    // A plot, a commercial type and a sold building have a surface; a residential type has none (#304).
    function needsArea(a) {
        return isKnown(a) && RULES.asks(a.propertyType, a.listingType, "area");
    }

    // A sale asks its ownership documents whatever its type (optional, several or none); a rental never asks them.
    function asksDocuments(a) {
        return RULES.asksDocuments(a.listingType);
    }

    function isLandSale(a) {
        return isSale(a) && a.propertyType === "LAND";
    }

    /*
     * Shows the page's texts of the project (data-for="RENTAL" / "SALE", rental when none is chosen yet) and points
     * the owner links (Quitter, the confirmation's CTA) at the project's public page.
     */
    function applyProject(listingType) {
        var project = listingType === "SALE" ? "SALE" : "RENTAL";
        $all("[data-for]").forEach(function (el) {
            el.hidden = el.getAttribute("data-for") !== project;
        });
        $all("[data-owner-link]").forEach(function (link) {
            link.href = OWNER_URLS[project];
        });
    }

    // The project the public site asked for (?type=SALE|RENTAL), if any.
    function requestedProject() {
        var type = new URLSearchParams(window.location.search).get("type");
        return LISTING_TYPES[type] ? type : null;
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
     * PROTOTYPE: ?sample= shortcuts for the states of #273 and #275 (README › offer-property.html). Each seeds the
     * answers of the steps before the one it opens; the page then opens there (a step only opens once the earlier
     * ones are complete). A sample already seeded isn't seeded again, so the reviewer can keep walking the wizard.
     */
    var SAMPLES = {
        // A sale whose type isn't chosen yet: the type choices and the *Document de propriété* only — no follow-up
        // question before a type is chosen (#304).
        sale: { step: "caracteristiques" },
        // A rental whose type isn't chosen yet: the type choices only — no follow-up question, no documents, no rate.
        "rental-pending": {
            step: "caracteristiques",
            answers: {
                listingType: "RENTAL",
                propertyType: null,
                priceAmount: 250000,
                advanceRent: 2,
                securityDeposit: 2
            }
        },
        // A sold plot of land: no rooms nor parking, the documents, its surface and the implied rate per m².
        land: { step: "caracteristiques", answers: { propertyType: "LAND", area: 800 } },
        // The same without its surface: the step refuses it ("Indiquez la surface du terrain.").
        "land-no-area": { step: "caracteristiques", answers: { propertyType: "LAND" } },
        // A villa sold: furniture, rooms and parking, its documents (optional, none here), no surface nor rate.
        house: {
            step: "caracteristiques",
            answers: { propertyType: "HOUSE", rooms: "3", parkings: "2", furnitureType: "UNFURNISHED" }
        },
        // The review of the sold plot, its two documents declared.
        "land-review": {
            step: "revue",
            answers: { propertyType: "LAND", area: 800, ownershipDocuments: ["LAND_TITLE", "SURVEY_PLAN"] }
        },
        // The same with no document declared: "Aucun document déclaré".
        "land-review-none": {
            step: "revue",
            answers: { propertyType: "LAND", area: 800, ownershipDocuments: [] }
        },
        // A villa sold with its three documents: the review lists them all ("Titre foncier · Plan de bornage ·
        // Certificat de propriété"); the same without any reads "Aucun document déclaré".
        "house-review-docs": {
            step: "revue",
            answers: {
                propertyType: "HOUSE",
                rooms: "4",
                parkings: "2",
                furnitureType: "FULLY_FURNISHED",
                ownershipDocuments: ["LAND_TITLE", "SURVEY_PLAN", "CERTIFICATE_OF_OWNERSHIP"]
            }
        },
        "house-review-none": {
            step: "revue",
            answers: {
                propertyType: "HOUSE",
                rooms: "4",
                parkings: "2",
                furnitureType: "FULLY_FURNISHED",
                ownershipDocuments: []
            }
        },
        // A rental, complete: its review (and each earlier step, walking back) reads "Location" next to the step
        // count, and the confirmation under "Bien enregistré" — where the sale samples read "Vente" (#278, AC 1, 3).
        rental: {
            step: "revue",
            answers: {
                listingType: "RENTAL",
                propertyType: "APARTMENT",
                furnitureType: "UNFURNISHED",
                rooms: "3",
                parkings: "1",
                priceAmount: 250000,
                advanceRent: 2,
                securityDeposit: 2
            }
        },
        // #304: a room let — no rooms asked, but its parking and its furniture are; no surface, no documents.
        "rental-room": {
            step: "caracteristiques",
            answers: {
                listingType: "RENTAL",
                propertyType: "ROOM",
                furnitureType: "UNFURNISHED",
                parkings: "1",
                priceAmount: 60000,
                advanceRent: 1,
                securityDeposit: 2
            }
        },
        // #304: a studio let — no rooms asked, its parking (none) and its furniture are; no surface, no documents.
        "rental-studio": {
            step: "caracteristiques",
            answers: {
                listingType: "RENTAL",
                propertyType: "STUDIO",
                furnitureType: "FULLY_FURNISHED",
                parkings: "0",
                priceAmount: 90000,
                advanceRent: 1,
                securityDeposit: 1
            }
        },
        // #275: a rental posted with a plot of land. Terrain isn't offered for a rental any more: the step shows
        // the commercial types alone and the answer is refused ("Choisissez le type de bien.", on *Suivant*), as
        // the BFF's form re-rendered after the POST does. #304 keeps a building out of a rental too.
        "rental-land": {
            step: "caracteristiques",
            answers: {
                listingType: "RENTAL",
                propertyCategory: "COMMERCIAL",
                propertyType: "LAND",
                advanceRent: 1,
                securityDeposit: 2
            }
        },
        // #304: a rental posted with a building, now sale-only: the type isn't offered, the answer dropped; the
        // step shows no type checked and no count.
        "rental-building": {
            step: "caracteristiques",
            answers: {
                listingType: "RENTAL",
                propertyCategory: "COMMERCIAL",
                propertyType: "BUILDING",
                advanceRent: 1,
                securityDeposit: 2
            }
        },
        // #274: a building sold as a whole, its two counts still to fill: six steps, "Immeuble entier" under
        // Commercial, no parking, and Suivant answers for the missing counts.
        building: {
            step: "caracteristiques",
            answers: {
                listingType: "SALE",
                propertyCategory: "COMMERCIAL",
                propertyType: "BUILDING",
                area: 850,
                priceAmount: 250000000
            }
        },
        // #304: the same building with its 3 étages and 12 unités, and the price per unit under them.
        "building-filled": {
            step: "caracteristiques",
            answers: {
                listingType: "SALE",
                propertyCategory: "COMMERCIAL",
                propertyType: "BUILDING",
                levels: 3,
                units: 12,
                area: 850,
                priceAmount: 250000000
            }
        },
        // The review of that building sale: Étages 3, Unités 12, the price per unit, no equipment line, six steps.
        "building-review": {
            step: "revue",
            answers: {
                listingType: "SALE",
                propertyCategory: "COMMERCIAL",
                propertyType: "BUILDING",
                levels: 3,
                units: 12,
                area: 850,
                priceAmount: 250000000
            }
        },
        // #380: the review's POST refused the contact e-mail (400 EMAIL_ADDRESS_INVALID). The visitor comes back to
        // the contact step with every answer kept, the e-mail flagged; "Suivant" reads "Revenir à la revue"
        // (returning) and "Précédent" opens the review, so the wizard never posts from here.
        "email-invalid": {
            step: "contact",
            returning: true,
            emailInvalid: true,
            // A complete villa sale, so the earlier steps stay answered and only the contact step is shown.
            answers: {
                propertyType: "HOUSE", rooms: "3", furnitureType: "UNFURNISHED", parkings: "1",
                email: "aicha.ngobassong@gmial.com"
            }
        },
        // The same refusal on a contact step whose other fields are blank (the visitor only typed the e-mail): the
        // sentence wraps under the field at 320 px without pushing the layout.
        "email-invalid-empty": {
            step: "contact",
            returning: true,
            emailInvalid: true,
            answers: {
                propertyType: "HOUSE", rooms: "3", furnitureType: "UNFURNISHED", parkings: "1",
                email: "aicha.ngobassong@gmial.com", firstName: "", lastName: "", whatsappNumber: null,
                preferredContactPeriod: null
            }
        }
    };

    // The answers a sample starts from: a complete sale in Bastos, Yaoundé, at 45M FCFA for 800 m².
    function sampleAnswers(name) {
        var chosen = Object.prototype.hasOwnProperty.call(SAMPLES, name) ? SAMPLES[name] : null;
        if (!chosen) return null;
        return Object.assign({
            listingType: "SALE",
            propertyCategory: "RESIDENTIAL",
            cityId: 2220957,
            neighborhoodId: 1001,
            priceAmount: 45000000,
            availableFrom: isoDate(today()),
            firstName: "Aïcha",
            lastName: "Ngo Bassong",
            email: "aicha.ngobassong@example.com",
            whatsappNumber: "+237677123456",
            preferredContactPeriod: "EVENING",
            roadAccess: "roadPaved",
            waterMeter: true,
            electricityMeter: true,
            securedFenced: true
        }, chosen.answers || {});
    }

    // Seeds a ?sample= (once), and returns the step it opens plus whether it is reached from the review (#380), or
    // null without a known sample.
    function applySample() {
        var name = new URLSearchParams(window.location.search).get("sample");
        var answers = sampleAnswers(name);
        if (!answers) return null;
        if (store.get(KEYS.sample) !== name || !store.get(KEYS.answers)) {
            store.set(KEYS.answers, JSON.stringify(answers));
            store.set(KEYS.sample, name);
            store.remove(KEYS.done);
        }
        // #380: the refusal stays on screen until the address itself is corrected and saved.
        if (SAMPLES[name].emailInvalid && loadAnswers().email === answers.email) {
            store.set(KEYS.emailInvalid, "1");
        } else {
            store.remove(KEYS.emailInvalid);
        }
        return {
            index: activeSteps(loadAnswers()).indexOf(SAMPLES[name].step),
            returning: !!SAMPLES[name].returning
        };
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

    // All checked values of a checkbox group, in document order.
    function checkedValues(name) {
        return $all('input[name="' + name + '"]:checked').map(function (input) {
            return input.value;
        });
    }

    // Re-tick a saved checkbox group (an array of values).
    function setCheckedAll(name, values) {
        $all('input[name="' + name + '"]').forEach(function (input) {
            input.checked = (values || []).indexOf(input.value) !== -1;
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

    // A price to read, not to type (#292): "800K", "12M", "1,25M", "1,5G" (FCFA has no cents). K keeps at most
    // one decimal, M and G two, half-up, trailing zeros dropped, and the unit is bumped after rounding
    // (999 950 → "1M", never "1000K"). The French comma matches the rest of the page.
    function formatPrice(n) {
        n = Number(n) || 0;
        var abs = Math.abs(n);
        var sign = n < 0 ? "-" : "";
        function rounded(value, decimals) {
            var factor = Math.pow(10, decimals);
            return Math.round(value * factor) / factor;
        }
        function text(value, decimals) {
            return String(value.toFixed(decimals)).replace(/\.?0+$/, "").replace(".", ",");
        }
        if (abs < 1000) return sign + String(abs);
        var k = rounded(abs / 1000, 1);
        if (k < 1000) return sign + text(k, 1) + "K";
        var m = rounded(abs / 1000000, 2);
        if (m < 1000) return sign + text(m, 2) + "M";
        return sign + text(rounded(abs / 1000000000, 2), 2) + "G";
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

    /*
     * The implied rate of a sold plot of land: asking price ÷ surface, rounded to the nearest 100 FCFA (#273). 0
     * when the answers aren't a land sale, or when the price or the surface is missing or unusable. Display only:
     * it is never stored, never sent.
     */
    function rateOf(a, price, surface) {
        if (!isLandSale(a) || !(Number(price) > 0) || !areaValid(surface)) return 0;
        return Math.round((Number(price) / surface) / 100) * 100;
    }

    /*
     * "≈ 56,3K FCFA/m² pour 800 m²" (#292): the rate reads short like every displayed amount, the surface stays a
     * plain grouped count. The prototype hard-codes the French comma and FCFA; the production page formats both with
     * the tenant's formatter (the tenant's locale and currency symbol).
     */
    function rateText(rate, surface) {
        return "≈ " + formatPrice(rate) + "\u00A0FCFA/m² pour " + formatNumber(surface) + " m²";
    }

    /*
     * The implied price of a building's unit: sale price ÷ unités, rounded to the nearest 100 FCFA (#304). 0 when
     * the answers aren't a building sale, or when the price or the count is missing or unusable. Display only: it is
     * never stored, never sent.
     */
    function ratePerUnitOf(a, price, units) {
        if (!isBuildingSale(a) || !(Number(price) > 0) || !countValid(units, MAX_UNITS)) return 0;
        return Math.round((Number(price) / units) / 100) * 100;
    }

    // "≈ 20,83M FCFA/unité pour 12 unités": the rate reads short like every displayed amount (#292); the unit count
    // stays a plain count. The prototype hard-codes FCFA; production uses the tenant's currency symbol.
    function ratePerUnitText(rate, units) {
        return "≈ " + formatPrice(rate) + "\u00A0FCFA/unité pour " + units + (Number(units) > 1 ? " unités" : " unité");
    }

    // A question is required only while it applies: the page marks the normal state with `required`, and the script
    // drops it from a question a sale shows before its type is saved (#273).
    function setRequired(name, required) {
        $all('input[name="' + name + '"]').forEach(function (input) {
            input.required = required;
        });
    }

    function monthsValid(value, min, max) {
        return value == null || (Number.isInteger(value) && value >= min && value <= max);
    }

    /*
     * Whether a step's saved answers are complete. This is what guards the wizard: a step opens only when
     * every step before it is complete, whatever the URL says. The BFF must re-check all of it server side.
     */
    // A count counts once answered: a whole number within its bounds (a building's two counts are required).
    function countValid(value, max) {
        return value != null && Number.isInteger(Number(value)) && Number(value) >= 1 && Number(value) <= max;
    }

    function isStepComplete(key, a) {
        switch (key) {
            case "bien":
                return !!LISTING_TYPES[a.listingType] && !!CATEGORY_LABELS[a.propertyCategory];
            case "adresse":
                return a.cityId > 0 && a.neighborhoodId > 0;
            case "conditions":
                return a.priceAmount >= minPrice(a) &&
                    (isSale(a) || (monthsValid(a.advanceRent, 1, MAX_ADVANCE_MONTHS) &&
                        a.securityDeposit != null && monthsValid(a.securityDeposit, 1, MAX_DEPOSIT_MONTHS))) &&
                    availabilityValid(a.availableFrom);
            case "caracteristiques":
                return typeFits(a) &&
                    (!needsFurniture(a) || !!FURNITURE_LABELS[a.furnitureType]) &&
                    (!needsRooms(a) || !!ROOM_LABELS[a.rooms]) &&
                    (!needsParking(a) || a.parkings === "0" || !!PARKING_LABELS[a.parkings]) &&
                    // v2 (#274): a building's levels and units, both required.
                    (!needsBuildingCounts(a) || (countValid(a.levels, MAX_LEVELS) && countValid(a.units, MAX_UNITS))) &&
                    (!needsArea(a) || areaValid(a.area));
            case "equipements":
                // The amenities step leaves a building sale's wizard (#274).
                return !needsAmenities(a) || !!ROAD_ACCESS[a.roadAccess];
            case "contact":
                return !!a.firstName && !!a.lastName && EMAIL_PATTERN.test(a.email || "") &&
                    /^\+[1-9]\d{6,14}$/.test(a.whatsappNumber || "") &&
                    !!PERIOD_LABELS[a.preferredContactPeriod];
            default:
                return false;
        }
    }

    /*
     * The steps of the branch being answered: a building sale skips the amenities step (#274), six steps instead of
     * seven. Step 1 has no answer yet, so it reads as seven.
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
     * Not in CreateMandateRequest yet: availableFrom.
     */
    function toRequest(a) {
        var request = {
            mandateType: "LISTING",
            listingType: a.listingType,
            propertyCategory: a.propertyCategory,
            cityId: a.cityId,
            neighbourhoodIds: [a.neighborhoodId],
            propertyType: a.propertyType,
            furnitureType: needsFurniture(a) ? a.furnitureType : null,
            rooms: needsRooms(a) ? a.rooms : null,
            parkings: needsParking(a) ? a.parkings : null,
            // v2 (#274): a building's two counts; no other type sends one.
            levels: needsBuildingCounts(a) ? a.levels : null,
            units: needsBuildingCounts(a) ? a.units : null,
            area: needsArea(a) ? a.area : null,
            // A sale keeps its ownership documents whatever its type; a rental sends none (#304).
            ownershipDocuments: asksDocuments(a) ? (a.ownershipDocuments || []) : null,
            price: {
                amount: a.priceAmount,
                currency: CURRENCY,
                periodicity: isSale(a) ? "NONE" : "MONTHLY"
            },
            advanceRent: isSale(a) ? null : a.advanceRent,
            securityDeposit: isSale(a) ? null : a.securityDeposit,
            availableFrom: a.availableFrom,
            firstName: a.firstName,
            lastName: a.lastName,
            email: a.email,
            whatsappNumber: a.whatsappNumber,
            preferredContactPeriod: a.preferredContactPeriod
        };
        Object.keys(ROAD_ACCESS).forEach(function (key) {
            // v2 (#274): a building sale sends no amenity at all.
            request[key] = needsAmenities(a) && a.roadAccess === key;
        });
        AMENITIES.forEach(function (amenity) {
            request[amenity[0]] = needsAmenities(a) && !!a[amenity[0]];
        });
        return request;
    }

    /* ---------- Steps: fields, saved values, what they collect ---------- */
    function categoryStep() {
        var answers = loadAnswers();
        setChecked("listingType", answers.listingType || requestedProject());
        setChecked("propertyCategory", answers.propertyCategory);
        applyProject(checkedValue("listingType"));
        $all('input[name="listingType"]').forEach(function (input) {
            input.addEventListener("change", function () {
                applyProject(checkedValue("listingType"));
            });
        });
        return {
            // Back from a later step: the texts follow the saved project again.
            onShow: function () {
                applyProject(checkedValue("listingType"));
            },
            fields: [
                radioField("listingType", "Indiquez si vous souhaitez mettre votre bien en location ou le vendre."),
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
                {
                    target: price,
                    error: $("#priceAmount-error"),
                    touched: function () {
                        return price.value.trim() !== "";
                    },
                    validate: function () {
                        var sale = isSale(loadAnswers());
                        if (!price.value.trim()) {
                            return sale ? "Indiquez le prix de vente souhaité." : "Indiquez le loyer mensuel souhaité.";
                        }
                        if (numberValue(price) >= (sale ? MIN_SALE_PRICE : MIN_RENT)) return "";
                        return sale
                            ? "Indiquez un prix d'au moins " + formatPrice(MIN_SALE_PRICE) + "\u00A0FCFA."
                            : "Indiquez un loyer d'au moins " + formatPrice(MIN_RENT) + "\u00A0FCFA par mois.";
                    }
                },
                {
                    target: deposit,
                    error: $("#securityDeposit-error"),
                    touched: function () {
                        return deposit.value !== "";
                    },
                    validate: function () {
                        if (isSale(loadAnswers())) return "";
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
                var sale = isSale(loadAnswers());
                return {
                    priceAmount: numberValue(price),
                    advanceRent: sale ? null : monthsOf(advance),
                    securityDeposit: sale ? null : monthsOf(deposit),
                    availableFrom: available.value
                };
            }
        };
    }

    function propertyStep(resync) {
        var answers = loadAnswers();
        var area = $("#area");
        var perSqm = $("[data-price-per-sqm]");
        var perUnit = $("[data-price-per-unit]");
        var counts = $("[data-building-counts]");
        var levelInput = $("#levels");
        var unitInput = $("#units");
        var groups = {
            furnitureType: $('[data-group="furnitureType"]'),
            rooms: $('[data-group="rooms"]'),
            hasParking: $('[data-group="hasParking"]'),
            parkings: $('[data-group="parkings"]'),
            area: $('[data-group="area"]'),
            ownershipDocuments: $('[data-group="ownershipDocuments"]')
        };

        setChecked("propertyType", answers.propertyType);
        setChecked("furnitureType", answers.furnitureType);
        setChecked("rooms", answers.rooms);
        // parkings is "0" for "Non", else the number of places: the Oui / Non question is UI only.
        setChecked("hasParking", answers.parkings == null ? null : answers.parkings === "0" ? "NO" : "YES");
        setChecked("parkings", answers.parkings === "0" ? null : answers.parkings);
        setCheckedAll("ownershipDocuments", answers.ownershipDocuments);
        area.value = formatNumber(answers.area);
        bindNumber(area, 7);
        area.addEventListener("input", updatePricePerSqm);
        // v2 (#274): the building's two counts (whole numbers, both required).
        levelInput.value = answers.levels != null ? String(answers.levels) : "";
        unitInput.value = answers.units != null ? String(answers.units) : "";
        unitInput.addEventListener("input", updatePricePerUnit);

        // The category from step 1 (saved) with the type being chosen now (live).
        function live() {
            var saved = loadAnswers();
            return {
                listingType: saved.listingType,
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

        // A plot of land sold (#273) or a building sold (#304): its implied price per m² or per unit, rounded to the
        // nearest 100 FCFA. Display only — never stored, never sent. Hidden until both a valid asking price
        // (conditions step) and a valid surface / unit count exist; also hidden for a rental or another type. The
        // price is read from the saved answers, the surface and the count as typed.
        function updatePricePerSqm() {
            var surface = areaValue();
            var rate = rateOf(live(), loadAnswers().priceAmount, surface);
            if (rate <= 0) {
                perSqm.hidden = true;
                perSqm.textContent = "";
                return;
            }
            perSqm.textContent = "Soit " + rateText(rate, surface);
            perSqm.hidden = false;
        }

        function updatePricePerUnit() {
            var units = unitInput.value.trim() ? Number(unitInput.value) : null;
            var rate = ratePerUnitOf(live(), loadAnswers().priceAmount, units);
            if (rate <= 0) {
                perUnit.hidden = true;
                perUnit.textContent = "";
                return;
            }
            perUnit.textContent = "Soit " + ratePerUnitText(rate, units);
            perUnit.hidden = false;
        }

        // A count: a whole number within its bounds; both of a building's counts are required (#274).
        function countField(name, noun, max) {
            var input = $("#" + name);
            return {
                target: input,
                error: $("#" + name + "-error"),
                touched: function () {
                    return input.value.trim() !== "";
                },
                validate: function () {
                    if (!needsBuildingCounts(live())) return "";
                    var raw = input.value.trim();
                    if (!raw) return "Indiquez le nombre " + noun + ".";
                    if (!/^\d+$/.test(raw) || Number(raw) < 1 || Number(raw) > max) {
                        return "Indiquez un nombre entier, de 1 à " + max + ".";
                    }
                    return "";
                }
            };
        }

        // Shows only the questions of the chosen type; nothing follows while no type is chosen (#304 removed #273's
        // seller preview). A hidden question is never in error; the ownership documents excepted — every sale asks
        // them, optional, before and after the type.
        function updateQuestions() {
            var a = live();
            var shown = {
                furnitureType: needsFurniture(a),
                rooms: needsRooms(a),
                hasParking: needsParking(a),
                parkings: asksPlaces(),
                area: needsArea(a),
                // Every sale asks its ownership documents, optional; a rental never shows them (#304).
                ownershipDocuments: asksDocuments(a)
            };
            Object.keys(groups).forEach(function (key) {
                groups[key].hidden = !shown[key];
                if (!shown[key]) setFieldError(groups[key], $("#" + key + "-error"), "");
            });
            setRequired("furnitureType", needsFurniture(a));
            setRequired("rooms", needsRooms(a));
            setRequired("hasParking", needsParking(a));
            setRequired("parkings", asksPlaces());
            setRequired("area", needsArea(a));
            // v2 (#274): a building's levels and units replace the rooms and the parking.
            counts.hidden = !needsBuildingCounts(a);
            if (counts.hidden) {
                setFieldError(levelInput, $("#levels-error"), "");
                setFieldError(unitInput, $("#units-error"), "");
            }
            $("[data-area-label]").textContent = a.propertyType === "LAND" ? "Surface du terrain estimée" : "Surface estimée";
            updatePricePerSqm();
            updatePricePerUnit();
        }

        $all('input[name="propertyType"], input[name="hasParking"]').forEach(function (input) {
            input.addEventListener("change", function () {
                updateQuestions();
                resync();
            });
        });

        return {
            // Step 1 may have changed the project or category since this step was last shown: offer only its types.
            // The shared rule table says which ones (#304): a building and a plot of land are sold only.
            onShow: function () {
                var saved = loadAnswers();
                var category = saved.propertyCategory;
                $all('[data-group="propertyType"] .choice').forEach(function (choice) {
                    var input = $("input", choice);
                    var fits = RULES.categories(input.value, saved.listingType).indexOf(category) !== -1;
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
                        if (!area.value.trim()) {
                            // A plot of land is sold by its surface, and its error names it — the wording of
                            // OfferPropertyRules.
                            return live().propertyType === "LAND"
                                ? "Indiquez la surface du terrain."
                                : "Indiquez la surface, même approximative.";
                        }
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
                    // v2 (#274): a building's two counts; no other type carries one.
                    levels: needsBuildingCounts(a) && levelInput.value.trim()
                        ? Number(levelInput.value.trim()) : null,
                    units: needsBuildingCounts(a) && unitInput.value.trim()
                        ? Number(unitInput.value.trim()) : null,
                    area: needsArea(a) ? areaValue() : null,
                    // Every sale keeps its ownership documents, optional (#304); a rental sends none.
                    ownershipDocuments: asksDocuments(a) ? checkedValues("ownershipDocuments") : null
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

        // #380: the review's POST was refused on the e-mail (400 EMAIL_ADDRESS_INVALID): the step comes back with
        // the field flagged and the one shared sentence. The first edit hands the field back to its own check.
        function showServerEmailError() {
            if (store.get(KEYS.emailInvalid) !== "1") return;
            setFieldError(email, $("#email-error"), EMAIL_INVALID);
        }
        email.addEventListener("input", function () {
            store.remove(KEYS.emailInvalid);
        });

        return {
            onShow: showServerEmailError,
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
            if (needsBuildingCounts(a)) {
                parts.push(a.levels + (a.levels > 1 ? " étages" : " étage"));
                parts.push(a.units + (a.units > 1 ? " unités" : " unité"));
            }
            if (needsArea(a)) parts.push(formatNumber(a.area) + " m²");
            return parts.join(" · ");
        }

        function describeLocation(a) {
            if (!locationNames[a.cityId] || !locationNames[a.neighborhoodId]) return "";
            return locationNames[a.neighborhoodId] + ", " + locationNames[a.cityId];
        }

        function describeConditions(a) {
            if (!isStepComplete("conditions", a)) return "";
            var availability = formatAvailability(a.availableFrom);
            if (isSale(a)) {
                // The total asking price, short like every displayed amount (#292, overriding #273's full amount),
                // then its implied rate right after it, before the availability: per m² for a sold plot (#273), per
                // unit for a sold building (#304). Computed server-side in production.
                var lines = [formatPrice(a.priceAmount) + "\u00A0FCFA"];
                var rate = rateOf(a, a.priceAmount, a.area);
                if (rate > 0) lines.push(rateText(rate, a.area));
                var unitRate = ratePerUnitOf(a, a.priceAmount, a.units);
                if (unitRate > 0) lines.push(ratePerUnitText(unitRate, a.units));
                lines.push(availability);
                return lines.join("\n");
            }
            return [
                formatPrice(a.priceAmount) + "\u00A0FCFA par mois",
                a.advanceRent ? "Avance de " + months(a.advanceRent) : "Sans avance de loyer",
                "Caution de " + months(a.securityDeposit),
                availability
            ].join("\n");
        }

        function describeAmenities(a) {
            // A building sale asks no amenities at all (#274).
            if (!needsAmenities(a) || !ROAD_ACCESS[a.roadAccess]) return "";
            return [ROAD_ACCESS[a.roadAccess]].concat(AMENITIES.filter(function (amenity) {
                return a[amenity[0]];
            }).map(function (amenity) {
                return amenity[1];
            })).join(", ");
        }

        function render(a) {
            var values = {
                category: LISTING_TYPES[a.listingType] && CATEGORY_LABELS[a.propertyCategory]
                    ? LISTING_TYPES[a.listingType] + ", " + CATEGORY_LABELS[a.propertyCategory] : "",
                location: describeLocation(a),
                conditions: describeConditions(a),
                property: describeProperty(a),
                amenities: describeAmenities(a),
                // A sale shows its ownership documents whatever its type; none declared reads "Aucun document
                // déclaré" (#304). A rental has no row.
                ownershipDocuments: asksDocuments(a)
                    ? ((a.ownershipDocuments || []).map(function (id) {
                        return OWNERSHIP_DOCUMENT_LABELS[id];
                    }).filter(Boolean).join(" · ") || "Aucun document déclaré")
                    : "",
                contact: isStepComplete("contact", a)
                    ? [a.firstName + " " + a.lastName, a.email, formatPhone(a.whatsappNumber),
                        "Contact " + PERIOD_LABELS[a.preferredContactPeriod]].join("\n")
                    : ""
            };
            var complete = firstIncompleteStep(a) === reviewIndexOf(a);
            // Every sale shows its documents on the review as "Document de propriété"; a rental never does (#304).
            $("[data-review-ownershipDocuments]").hidden = !asksDocuments(a);
            // v2 (#274): a building sale shows no equipment line.
            $("[data-review-amenities]").hidden = !needsAmenities(a);
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
    // v2 (#274): the count and the track follow the branch's own steps — six for a building sale, seven otherwise.
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

    function initOfferProperty() {
        var form = $("[data-offer-form]");
        var slideOf = {};
        STEPS.forEach(function (key) {
            slideOf[key] = $('[data-step="' + key + '"]', form);
        });
        var confirmation = $("[data-confirmation]");
        var formParts = [$("[data-progress-block]"), $("[data-steps-title]"), form];
        var current = 0;
        // A ?sample= seeds the session before the steps read their answers, and names the step it opens.
        var sample = applySample();
        var sampleStep = sample ? sample.index : null;
        var whatsapp = lazyPhone($("#whatsappNumber"), { name: "whatsappNumber", value: loadAnswers().whatsappNumber });

        function resync() {
            sync(current);
        }

        var stepOf = {
            bien: categoryStep(),
            adresse: addressStep(resync),
            conditions: conditionsStep(),
            caracteristiques: propertyStep(resync),
            equipements: amenitiesStep(),
            contact: contactStep(whatsapp),
            revue: reviewStep()
        };

        // The branch's steps (a building sale skips the amenities step, #274): an index always reads in them.
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
            // The project pill (Vente / Location) from step 2 on: step 1 carries the choice itself (#278, AC 1).
            // The confirmation's own pill is not [data-project]; applyProject() fills both from the listingType.
            $("[data-progress-block] [data-project]").hidden = index === 0;

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

        function showConfirmation(done) {
            formParts.forEach(function (el) {
                el.hidden = true;
            });
            confirmation.hidden = false;
            applyProject(done.listingType);
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
            announce($("[data-sending]"), "Enregistrement de votre bien en cours.");
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
                submit.textContent = "Enregistrer mon bien";
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

        // Enter in a field moves to the next step; on the review step the form submits the listing.
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (current === reviewIndexOf(loadAnswers())) submitRequest();
            else next(current);
        });
        form.addEventListener("keydown", function (event) {
            var target = event.target;
            if (event.key !== "Enter" || !target.matches("input:not([type=checkbox]):not([type=radio])")) return;
            if (target.closest(".iti__dropdown-content")) return; // intl-tel-input's country search
            if (target.closest(".ss-content")) return; // slim-select's search
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
            // #380: a sample may say it is reached from the review, so Back and "Revenir à la revue" lead there.
            var returning = sample ? sample.returning : (start === requestedStep() && !!state().returning);
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
