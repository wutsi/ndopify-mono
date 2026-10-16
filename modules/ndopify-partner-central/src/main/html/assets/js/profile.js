/*
 * Ndopify Partner Central — profile pages (static prototype): profile/edit.html and profile/photo.html.
 * The saved profile is kept in sessionStorage ("npc.profile"), which index.html reads for its profile section.
 * Server calls are simulated; every simulation is marked "PROTOTYPE".
 */
(function () {
    "use strict";

    var KEY = "npc.profile";
    var MAX_NEIGHBORHOODS = 3;
    var MAX_BIOGRAPHY = 500;
    var MAX_FILE_BYTES = 5 * 1024 * 1024;
    var ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
    var PHOTO_SIZE = 480; // px, square: shown round, at most 12rem wide

    // PROTOTYPE: the signed-in agent, as GET /v1/agents/{id} would return it (same as index.html's).
    var DEFAULT_PROFILE = {
        firstName: "Serge",
        lastName: "Ngono",
        whatsappNumber: "+237677123456",
        experienceLevel: "SENIOR",
        agentType: "INDEPENDANT",
        cityId: 2232593,
        cityName: "Douala",
        neighborhoodIds: [2007, 2001, 2006],
        neighborhoodNames: ["Bonapriso", "Akwa", "Bonanjo"],
        biography: "Démarcheur à Douala depuis 6 ans, je connais Bonapriso, Akwa et Bonanjo rue par rue. " +
            "J'accompagne familles et jeunes actifs jusqu'à la signature du bail, avec des visites organisées " +
            "et des biens vérifiés.",
        photo: null
    };

    // PROTOTYPE: rows the simulated GET /v1/locations serves (same data as rejoindre.js).
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
        rows.push({ id: city.id, parentId: null, name: city.name, type: "CITY" });
        city.neighborhoods.forEach(function (name, n) {
            rows.push({ id: (c + 1) * 1000 + n + 1, parentId: city.id, name: name, type: "NEIGHBORHOOD" });
        });
        return rows;
    }, []);

    var locationNames = {};

    // PROTOTYPE: stands in for the BFF's JSON proxy to GET /v1/locations.
    function loadLocations(params) {
        var rows = LOCATIONS.filter(function (l) {
            return l.type === params.type && (params.parentId == null || l.parentId === params.parentId);
        }).sort(function (a, b) {
            return a.name.localeCompare(b.name, "fr");
        });
        rows.forEach(function (l) {
            locationNames[l.id] = l.name;
        });
        return new Promise(function (resolve) {
            window.setTimeout(function () {
                resolve(rows);
            }, 300);
        });
    }

    /* ---------- Storage (fails soft: private mode, quota) ---------- */
    function loadProfile() {
        try {
            var saved = JSON.parse(window.sessionStorage.getItem(KEY));
            if (saved) return Object.assign({}, DEFAULT_PROFILE, saved);
        } catch (e) {
            /* ignore */
        }
        return Object.assign({}, DEFAULT_PROFILE);
    }

    function saveProfile(patch) {
        try {
            window.sessionStorage.setItem(KEY, JSON.stringify(Object.assign(loadProfile(), patch)));
            return true;
        } catch (e) {
            return false;
        }
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
        if (message) {
            $("[data-error-text]", errorEl).textContent = message;
            errorEl.hidden = false;
            target.setAttribute("aria-invalid", "true");
        } else {
            errorEl.hidden = true;
            target.removeAttribute("aria-invalid");
        }
    }

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
            input.checked = input.value === value;
        });
    }

    // Accent- and case-insensitive search ("deido" finds "Deïdo").
    function fold(text) {
        return String(text).normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
    }

    function initials(profile) {
        return ((profile.firstName || "").charAt(0) + (profile.lastName || "").charAt(0)).toUpperCase() || "NP";
    }

    function showPhoto(container, src) {
        var img = $("img", container);
        if (!src) return;
        if (!img) {
            img = document.createElement("img");
            img.width = PHOTO_SIZE;
            img.height = PHOTO_SIZE;
            img.alt = container.getAttribute("data-alt") || "";
            container.textContent = "";
            container.appendChild(img);
        }
        img.src = src;
    }

    function renderIdentity(profile) {
        $all("[data-profile-initials]").forEach(function (el) {
            el.textContent = initials(profile);
        });
        $all("[data-profile-photo]").forEach(function (el) {
            showPhoto(el, profile.photo);
        });
    }

    /* ---------- slim-select (styled in rejoindre.css, as on the join wizard) ---------- */
    function slimSelect(select, settings) {
        var slim = new window.SlimSelect({
            select: select,
            settings: Object.assign({
                searchPlaceholder: "Rechercher…",
                searchText: "Aucun résultat",
                searchingText: "Recherche…",
                removeText: "Retirer",
                deselectText: "Effacer",
                maxValuesMessage: "{number} sélectionnés",
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

    var PHONE_ERRORS = {
        1: "Cet indicatif de pays n'existe pas. Choisissez votre pays dans la liste.",
        2: "Ce numéro est trop court. Vérifiez qu'il ne manque pas de chiffre.",
        3: "Ce numéro est trop long. Vérifiez qu'il n'y a pas de chiffre en trop."
    };

    /* ---------- profile/edit.html ---------- */
    function initEdit() {
        var form = $("[data-profile-form]");
        var submit = $("[data-submit]", form);
        var profile = loadProfile();
        var phone = null;
        var phoneFailed = false;

        renderIdentity(profile);
        $("[data-profile-name]").textContent = profile.firstName + " " + profile.lastName;
        $("[data-photo-link]").textContent = profile.photo ? "Changer de photo" : "Ajouter une photo";

        var phoneInput = $("#whatsappNumber");
        var bio = $("#biography");
        var bioCounter = $("[data-biography-counter]");
        var citySelect = $("#cityId");
        var neighborhoodSelect = $("#neighborhoodIds");
        var group = $('[data-group="neighborhoodIds"]');
        var empty = $("[data-neighborhoods-empty]");
        var counter = $("[data-neighborhood-counter]");

        setChecked("experienceLevel", profile.experienceLevel);
        setChecked("agentType", profile.agentType);
        bio.value = profile.biography || "";

        var city = slimSelect(citySelect, { placeholderText: "Choisissez une ville", ariaLabel: "Ville principale d'intervention" });
        var neighborhoods = slimSelect(neighborhoodSelect, {
            placeholderText: "Choisissez vos quartiers",
            ariaLabel: "Quartiers",
            maxSelected: MAX_NEIGHBORHOODS,
            closeOnSelect: false
        });

        function cityId() {
            return Number(citySelect.value) || 0;
        }

        function selected() {
            return neighborhoods.slim.getSelected().map(Number);
        }

        function updateCounters(announceIt) {
            var count = selected().length;
            counter.textContent = count + " sur " + MAX_NEIGHBORHOODS + " sélectionné" + (count > 1 ? "s" : "");
            if (announceIt && count === MAX_NEIGHBORHOODS) {
                announce($("[data-neighborhood-status]"), "Vous avez choisi " + MAX_NEIGHBORHOODS + " quartiers, le maximum.");
            }
            bioCounter.textContent = bio.value.length + " sur " + MAX_BIOGRAPHY + " caractères";
        }

        function toOptions(locations, keep) {
            return locations.map(function (l) {
                return { value: String(l.id), text: l.name, selected: (keep || []).indexOf(l.id) !== -1 };
            });
        }

        var pending = 0;

        function loadNeighborhoods(keep) {
            var id = cityId();
            var request = ++pending;
            empty.hidden = !!id;
            group.hidden = !id;
            neighborhoods.slim.setData([]);
            updateCounters(false);
            if (!id) return;
            neighborhoods.slim.disable();
            loadLocations({ type: "NEIGHBORHOOD", parentId: id }).then(function (locations) {
                if (request !== pending) return; // the city changed again meanwhile
                neighborhoods.slim.setData(toOptions(locations, keep));
                neighborhoods.slim.enable();
                updateCounters(false);
                sync();
            });
        }

        /* Fields: errors appear on blur, not while typing; Enregistrer turns active once every field is valid. */
        var fields = [
            {
                target: phoneInput,
                error: $("#whatsappNumber-error"),
                touched: function () {
                    return true;
                },
                validate: function () {
                    if (phoneInput.value.trim() === "") return "Saisissez votre numéro WhatsApp.";
                    if (!phone) return phoneFailed ? "Nous ne pouvons pas vérifier ce numéro pour l'instant." : "Vérification du numéro en cours de chargement.";
                    if (phone.e164()) return "";
                    return PHONE_ERRORS[phone.validationError()] ||
                        "Ce numéro n'est pas un numéro mobile valide. Exemple : 6 77 12 34 56";
                }
            },
            {
                target: $('[data-group="experienceLevel"]'),
                error: $("#experienceLevel-error"),
                validate: function () {
                    return checkedValue("experienceLevel") ? "" : "Indiquez votre expérience.";
                }
            },
            {
                target: $('[data-group="agentType"]'),
                error: $("#agentType-error"),
                validate: function () {
                    return checkedValue("agentType") ? "" : "Indiquez comment vous exercez.";
                }
            },
            {
                target: city.control,
                source: citySelect,
                error: $("#cityId-error"),
                contains: function (el) {
                    return city.control.contains(el) || city.content.contains(el);
                },
                validate: function () {
                    return cityId() ? "" : "Choisissez votre ville principale d'intervention.";
                }
            },
            {
                target: neighborhoods.control,
                source: neighborhoodSelect,
                error: $("#neighborhoodIds-error"),
                contains: function (el) {
                    return neighborhoods.control.contains(el) || neighborhoods.content.contains(el);
                },
                validate: function () {
                    if (!cityId()) return "";
                    var count = selected().length;
                    if (count === 0) return "Choisissez au moins un quartier.";
                    if (count > MAX_NEIGHBORHOODS) return "Choisissez au plus " + MAX_NEIGHBORHOODS + " quartiers.";
                    return "";
                }
            }
        ];

        function valid() {
            return fields.every(function (field) {
                return field.validate() === "";
            }) && bio.value.length <= MAX_BIOGRAPHY;
        }

        function sync() {
            setActive(submit, valid());
        }

        fields.forEach(function (field) {
            var refresh = function () {
                if (field.target.getAttribute("aria-invalid") === "true") {
                    setFieldError(field.target, field.error, field.validate());
                }
                sync();
            };
            var source = field.source || field.target;
            source.addEventListener("input", refresh);
            source.addEventListener("change", refresh);
            source.addEventListener("countrychange", refresh);
            field.target.addEventListener("focusout", function (event) {
                if ((field.contains || field.target.contains.bind(field.target))(event.relatedTarget)) return;
                setFieldError(field.target, field.error, field.validate());
            });
        });

        bio.addEventListener("input", function () {
            updateCounters(false);
            sync();
        });
        neighborhoodSelect.addEventListener("change", function () {
            updateCounters(true);
        });

        city.slim.disable();
        loadLocations({ type: "CITY" }).then(function (locations) {
            city.slim.setData([{ value: "", text: "Choisissez une ville", placeholder: true }]
                .concat(toOptions(locations, [profile.cityId])));
            city.slim.enable();
            loadNeighborhoods(profile.neighborhoodIds);
            citySelect.addEventListener("change", function () {
                loadNeighborhoods([]);
                setFieldError(neighborhoods.control, $("#neighborhoodIds-error"), "");
            });
        });

        window.NdUtil.phoneReady().then(
            function (i18n) {
                phone = window.NdUtil.bindPhone(phoneInput, { name: "whatsappNumber", value: profile.whatsappNumber, i18n: i18n });
                sync();
            },
            function () {
                phoneFailed = true;
                $("[data-phone-unavailable]").hidden = false;
            }
        );

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (!isActive(submit)) {
                // Show every error at once, and move to the first one.
                var first = null;
                fields.forEach(function (field) {
                    var message = field.validate();
                    setFieldError(field.target, field.error, message);
                    if (message && !first) first = field.target;
                });
                if (first) (first.matches("fieldset") ? $("input", first) : first).focus();
                return;
            }
            setActive(submit, false);
            submit.textContent = "Enregistrement…";
            announce($("[data-saving]"), "Enregistrement de votre profil en cours.");
            // PROTOTYPE: the real page PUTs these fields to /v1/agents/{id} (UpdateAgentRequest).
            window.setTimeout(function () {
                saveProfile({
                    whatsappNumber: phone.e164(),
                    experienceLevel: checkedValue("experienceLevel"),
                    agentType: checkedValue("agentType"),
                    cityId: cityId(),
                    cityName: locationNames[cityId()],
                    neighborhoodIds: selected(),
                    neighborhoodNames: selected().map(function (id) {
                        return locationNames[id];
                    }),
                    biography: bio.value.trim() || null
                });
                window.location.href = "../index.html?saved=profile";
            }, 600);
        });

        updateCounters(false);
        sync();
    }

    /* ---------- profile/photo.html ---------- */

    // Center-crops the photo to a square and shrinks it to PHOTO_SIZE, as a JPEG data URL.
    function toSquare(file, callback) {
        var reader = new FileReader();
        reader.onload = function () {
            var img = new Image();
            img.onload = function () {
                var side = Math.min(img.naturalWidth, img.naturalHeight);
                var canvas = document.createElement("canvas");
                canvas.width = PHOTO_SIZE;
                canvas.height = PHOTO_SIZE;
                canvas.getContext("2d").drawImage(img,
                    (img.naturalWidth - side) / 2, (img.naturalHeight - side) / 2, side, side,
                    0, 0, PHOTO_SIZE, PHOTO_SIZE);
                callback(canvas.toDataURL("image/jpeg", 0.85));
            };
            img.onerror = function () {
                callback(null);
            };
            img.src = reader.result;
        };
        reader.onerror = function () {
            callback(null);
        };
        reader.readAsDataURL(file);
    }

    function initPhoto() {
        var form = $("[data-photo-form]");
        var submit = $("[data-submit]", form);
        var input = $("#photo-file");
        var error = $("#photo-error");
        var label = $("[data-upload-label]");
        var status = $("[data-upload-status]");
        var preview = $("[data-profile-photo]");
        var profile = loadProfile();
        var chosen = null;

        renderIdentity(profile);
        if (profile.photo) label.textContent = "Choisir une autre photo";

        input.addEventListener("change", function () {
            var file = input.files && input.files[0];
            if (!file) return;
            var message = "";
            if (ACCEPTED_TYPES.indexOf(file.type) === -1) {
                message = "Ce format n'est pas accepté. Choisissez une photo JPG, PNG ou WEBP.";
            } else if (file.size > MAX_FILE_BYTES) {
                message = "Cette photo dépasse 5 Mo. Choisissez une photo plus légère.";
            }
            setFieldError(input, error, message);
            input.value = "";
            if (message) return;
            toSquare(file, function (src) {
                if (!src) {
                    setFieldError(input, error, "Cette photo ne peut pas être lue. Choisissez-en une autre.");
                    return;
                }
                chosen = src;
                showPhoto(preview, src);
                label.textContent = "Choisir une autre photo";
                setActive(submit, true);
                announce(status, "Photo choisie. Vérifiez l'aperçu, puis enregistrez.");
            });
        });

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (!isActive(submit) || !chosen) return;
            setActive(submit, false);
            submit.textContent = "Enregistrement…";
            announce($("[data-saving]"), "Enregistrement de votre photo en cours.");
            // PROTOTYPE: the real page uploads the photo to ndopify-server.
            window.setTimeout(function () {
                if (!saveProfile({ photo: chosen })) {
                    submit.textContent = "Enregistrer";
                    setActive(submit, true);
                    setFieldError(input, error, "Cette photo n'a pas pu être enregistrée. Réessayez.");
                    return;
                }
                window.location.href = "../index.html?saved=photo";
            }, 600);
        });
    }

    var pages = {
        "profile-edit": initEdit,
        "profile-photo": initPhoto
    };

    document.addEventListener("DOMContentLoaded", function () {
        var init = pages[document.body.getAttribute("data-page")];
        if (init) init();
    });
})();
