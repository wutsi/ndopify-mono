/*
 * Ndopify Partner Central — "Rejoindre Ndopify" wizard (static prototype).
 * A single page (rejoindre.html): five steps as slides — identite → experience → secteur → paiement →
 * revue — then the confirmation, which has no URL of its own. Each slide is a history entry (?etape=…) so the
 * Back button works, but the URL is never trusted: a step only opens once every earlier step is complete.
 * Answers live in sessionStorage until the review step submits them; field names match CreateAgentRequest
 * (ndopify-dto). Server calls are simulated; every simulation is marked "PROTOTYPE".
 */
(function () {
    "use strict";

    var STEPS = ["identite", "experience", "secteur", "paiement", "revue"];
    var REVIEW = STEPS.length - 1;
    var MAX_NEIGHBORHOODS = 3;
    var EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
    var NAME_PATTERN = /^[\p{L}][\p{L}' .-]*$/u;

    var KEYS = {
        join: "npc.join",
        joined: "npc.join.done"
    };

    // PROTOTYPE: accounts the simulated API "knows" (same list as partner-central.js).
    var KNOWN_EMAILS = ["agent@ndopify.com", "serge@ndopify.com"];

    // PROTOTYPE: the real page loads cities and neighbourhoods from ndopify-server (/v1/locations).
    // City ids are the GeoNames ids; neighbourhood ids are placeholders.
    var CITIES = [
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
    ].map(function (city, c) {
        return {
            id: city.id,
            name: city.name,
            neighborhoods: city.neighborhoods.map(function (name, n) {
                return { id: (c + 1) * 1000 + n + 1, name: name };
            })
        };
    });

    var EXPERIENCE_LABELS = {
        NOVICE: "Moins d'un an",
        JUNIOR: "1 à 3 ans",
        INTERMEDIATE: "3 à 5 ans",
        SENIOR: "Plus de 5 ans"
    };

    var AGENT_TYPE_LABELS = {
        INDEPENDANT: "Indépendant",
        REAL_ESTATE_AGENT: "Rattaché à une agence immobilière"
    };

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

    function loadAnswers() {
        try {
            return JSON.parse(store.get(KEYS.join)) || {};
        } catch (e) {
            return {};
        }
    }

    function saveAnswers(patch) {
        var answers = loadAnswers();
        Object.keys(patch).forEach(function (key) {
            answers[key] = patch[key];
        });
        store.set(KEYS.join, JSON.stringify(answers));
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
    // aria-describedby explanation can still be reached — same convention as partner-central.js.
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
            input.checked = input.value === String(value);
        });
    }

    function findCity(id) {
        id = Number(id);
        for (var i = 0; i < CITIES.length; i++) {
            if (CITIES[i].id === id) return CITIES[i];
        }
        return null;
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
            set: function (value) {
                if (phone) phone.set(value);
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

    function radioField(name, emptyMessage) {
        return {
            target: $('[data-group="' + name + '"]'),
            error: $("#" + name + "-error"),
            touched: function () {
                return checkedValue(name) !== "";
            },
            validate: function () {
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

    function isKnownEmail(email) {
        return KNOWN_EMAILS.indexOf(String(email || "").trim().toLowerCase()) !== -1;
    }

    /*
     * Whether a step's saved answers are complete. This is what guards the wizard: a step opens only when
     * every step before it is complete, whatever the URL says. The BFF must re-check all of it server side.
     */
    function isStepComplete(key, a) {
        switch (key) {
            case "identite":
                return !!a.firstName && !!a.lastName && EMAIL_PATTERN.test(a.email || "") && !isKnownEmail(a.email) &&
                    /^\+[1-9]\d{6,14}$/.test(a.whatsappNumber || "");
            case "experience":
                return !!EXPERIENCE_LABELS[a.experienceLevel] && !!AGENT_TYPE_LABELS[a.agentType];
            case "secteur":
                return !!findCity(a.cityId) && Array.isArray(a.neighborhoodIds) &&
                    a.neighborhoodIds.length >= 1 && a.neighborhoodIds.length <= MAX_NEIGHBORHOODS;
            case "paiement":
                return /^\+237\d{9}$/.test(a.mobileMoneyNumber || "");
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

    /* ---------- Steps: fields, saved values, what they collect ---------- */
    function identityStep(phones) {
        var answers = loadAnswers();
        var firstName = $("#firstName");
        var lastName = $("#lastName");
        var email = $("#email");
        var existing = $("[data-existing-account]");
        // The login page (or the BFF) may hand over the address the visitor already typed.
        var handedOver = new URLSearchParams(window.location.search).get("email");

        firstName.value = answers.firstName || "";
        lastName.value = answers.lastName || "";
        email.value = answers.email || handedOver || "";

        function syncExisting() {
            existing.hidden = !(EMAIL_PATTERN.test(email.value.trim()) && isKnownEmail(email.value));
        }
        email.addEventListener("input", syncExisting);
        email.addEventListener("focusout", syncExisting);
        syncExisting();

        return {
            blocked: function () {
                return isKnownEmail(email.value);
            },
            fields: [
                textField(firstName, "Saisissez votre prénom.", nameValidity),
                textField(lastName, "Saisissez votre nom.", nameValidity),
                textField(email, "Saisissez votre adresse email.", emailValidity),
                phoneField($("#whatsappNumber"), phones.whatsapp, "Saisissez votre numéro WhatsApp.")
            ],
            collect: function () {
                return {
                    firstName: firstName.value.trim(),
                    lastName: lastName.value.trim(),
                    email: email.value.trim().toLowerCase(),
                    whatsappNumber: phones.whatsapp.e164()
                };
            }
        };
    }

    function experienceStep() {
        var answers = loadAnswers();
        setChecked("experienceLevel", answers.experienceLevel);
        setChecked("agentType", answers.agentType);
        return {
            fields: [
                radioField("experienceLevel", "Indiquez depuis combien de temps vous exercez."),
                radioField("agentType", "Indiquez si vous êtes indépendant ou rattaché à une agence.")
            ],
            collect: function () {
                return { experienceLevel: checkedValue("experienceLevel"), agentType: checkedValue("agentType") };
            }
        };
    }

    function sectorStep(resync) {
        var answers = loadAnswers();
        var city = $("#cityId");
        var group = $('[data-group="neighborhoodIds"]');
        var list = $("[data-neighborhoods]");
        var empty = $("[data-neighborhoods-empty]");
        var counter = $("[data-neighborhood-counter]");
        var status = $("[data-neighborhood-status]");

        CITIES.forEach(function (c) {
            var option = document.createElement("option");
            option.value = String(c.id);
            option.textContent = c.name;
            city.appendChild(option);
        });

        function selected() {
            return $all('input[name="neighborhoodIds"]:checked', list).map(function (input) {
                return Number(input.value);
            });
        }

        function updateLimit(announceIt) {
            var count = selected().length;
            counter.textContent = count + " sur " + MAX_NEIGHBORHOODS + " sélectionné" + (count > 1 ? "s" : "");
            $all('input[name="neighborhoodIds"]', list).forEach(function (input) {
                input.disabled = !input.checked && count >= MAX_NEIGHBORHOODS;
            });
            if (announceIt && count === MAX_NEIGHBORHOODS) {
                announce(status, "Vous avez choisi " + MAX_NEIGHBORHOODS + " quartiers, le maximum.");
            }
        }

        function renderNeighborhoods(keep) {
            list.textContent = "";
            var c = findCity(city.value);
            empty.hidden = !!c;
            group.hidden = !c;
            if (!c) return;
            c.neighborhoods.forEach(function (n) {
                var label = document.createElement("label");
                label.className = "choice";
                var input = document.createElement("input");
                input.type = "checkbox";
                input.name = "neighborhoodIds";
                input.value = String(n.id);
                input.checked = (keep || []).indexOf(n.id) !== -1;
                label.appendChild(input);
                label.appendChild(document.createTextNode(" " + n.name));
                list.appendChild(label);
            });
            updateLimit(false);
        }

        if (findCity(answers.cityId)) city.value = String(answers.cityId);
        renderNeighborhoods(answers.neighborhoodIds);

        city.addEventListener("change", function () {
            renderNeighborhoods([]);
            setFieldError(group, $("#neighborhoodIds-error"), "");
            resync();
        });
        list.addEventListener("change", function () {
            updateLimit(true);
        });

        return {
            fields: [
                {
                    target: city,
                    error: $("#cityId-error"),
                    validate: function () {
                        return findCity(city.value) ? "" : "Choisissez votre ville principale d'intervention.";
                    }
                },
                {
                    target: group,
                    error: $("#neighborhoodIds-error"),
                    touched: function () {
                        return selected().length > 0;
                    },
                    validate: function () {
                        if (!findCity(city.value)) return "";
                        var count = selected().length;
                        if (count === 0) return "Choisissez au moins un quartier.";
                        if (count > MAX_NEIGHBORHOODS) return "Choisissez au plus " + MAX_NEIGHBORHOODS + " quartiers.";
                        return "";
                    }
                }
            ],
            collect: function () {
                return { cityId: Number(city.value), neighborhoodIds: selected() };
            }
        };
    }

    function paymentStep(phones, resync) {
        var momo = $("#mobileMoneyNumber");
        var same = $("#sameAsWhatsapp");
        var shortcut = $("[data-same-as-whatsapp]");

        same.addEventListener("change", function () {
            if (same.checked) phones.momo.set(loadAnswers().whatsappNumber);
            momo.readOnly = same.checked;
            if (same.checked) setFieldError(momo, $("#mobileMoneyNumber-error"), "");
            else momo.focus();
            resync();
        });

        return {
            // Step 1 may have changed the WhatsApp number since this step was last shown.
            onShow: function () {
                var whatsapp = loadAnswers().whatsappNumber || "";
                // A foreign WhatsApp number can't be a Cameroonian Mobile Money number.
                shortcut.hidden = whatsapp.indexOf("+237") !== 0;
                $("[data-whatsapp-number]").textContent = formatPhone(whatsapp);
                same.checked = !shortcut.hidden && phones.momo.e164() === whatsapp;
                momo.readOnly = same.checked;
            },
            fields: [phoneField(momo, phones.momo, "Saisissez votre numéro Mobile Money.")],
            collect: function () {
                return { mobileMoneyNumber: phones.momo.e164() };
            }
        };
    }

    function reviewStep() {
        var submit = $("[data-submit]");
        return {
            onShow: function () {
                var answers = loadAnswers();
                var city = findCity(answers.cityId);
                var values = {
                    name: answers.firstName && answers.lastName ? answers.firstName + " " + answers.lastName : "",
                    email: answers.email || "",
                    whatsappNumber: answers.whatsappNumber ? formatPhone(answers.whatsappNumber) : "",
                    experienceLevel: EXPERIENCE_LABELS[answers.experienceLevel] || "",
                    agentType: AGENT_TYPE_LABELS[answers.agentType] || "",
                    cityId: city ? city.name : "",
                    neighborhoodIds: city && answers.neighborhoodIds && answers.neighborhoodIds.length
                        ? city.neighborhoods
                            .filter(function (n) {
                                return answers.neighborhoodIds.indexOf(n.id) !== -1;
                            })
                            .map(function (n) {
                                return n.name;
                            })
                            .join(", ")
                        : "",
                    mobileMoneyNumber: answers.mobileMoneyNumber ? formatPhone(answers.mobileMoneyNumber) : ""
                };
                var complete = firstIncompleteStep(answers) === REVIEW;
                Object.keys(values).forEach(function (key) {
                    var valueEl = $('[data-review-value="' + key + '"]');
                    var missing = $('[data-review-missing="' + key + '"]');
                    valueEl.textContent = values[key];
                    valueEl.hidden = !values[key];
                    missing.hidden = !!values[key];
                });
                setActive(submit, complete);
                $("[data-incomplete]").hidden = complete;
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

    function initJoin() {
        var form = $("[data-join-form]");
        var slides = STEPS.map(function (key) {
            return $('[data-step="' + key + '"]', form);
        });
        var confirmation = $("[data-confirmation]");
        var formParts = [$("[data-progress-block]"), $("[data-steps-title]"), form];
        var current = 0;
        var answers = loadAnswers();

        var phones = {
            whatsapp: lazyPhone($("#whatsappNumber"), { name: "whatsappNumber", value: answers.whatsappNumber }),
            momo: lazyPhone($("#mobileMoneyNumber"), {
                name: "mobileMoneyNumber",
                value: answers.mobileMoneyNumber,
                // MTN Mobile Money and Orange Money accounts here are Cameroonian: the country is locked to +237.
                onlyCountries: ["cm"]
            })
        };

        function resync() {
            sync(current);
        }

        var steps = [
            identityStep(phones),
            experienceStep(),
            sectorStep(resync),
            paymentStep(phones, resync),
            reviewStep()
        ];

        function stepValid(index) {
            var step = steps[index];
            return step.fields.every(function (field) {
                return field.validate() === "";
            }) && (!step.blocked || !step.blocked());
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
                var focusable = firstInvalid.target.matches("input, select, textarea")
                    ? firstInvalid.target
                    : $("input:not(:disabled)", firstInvalid.target);
                if (focusable) focusable.focus();
                return false;
            }
            return !(steps[index].blocked && steps[index].blocked());
        }

        function next(index) {
            if (!validateStep(index)) return;
            saveAnswers(steps[index].collect());
            if (state().returning) back(REVIEW);
            else go(index + 1);
        }

        function showConfirmation(email) {
            formParts.forEach(function (el) {
                el.hidden = true;
            });
            confirmation.hidden = false;
            $("[data-joined-email]").textContent = maskEmail(email);
            confirmation.focus({ preventScroll: true });
            window.scrollTo(0, 0);
        }

        function submitApplication() {
            var submit = $("[data-submit]");
            var saved = loadAnswers();
            if (!isActive(submit) || firstIncompleteStep(saved) !== REVIEW) return;
            setActive(submit, false);
            submit.textContent = "Envoi en cours…";
            announce($("[data-sending]"), "Envoi de votre candidature en cours.");
            // PROTOTYPE: the real page POSTs the answers to /v1/agents (CreateAgentRequest).
            window.setTimeout(function () {
                store.set(KEYS.joined, saved.email);
                store.remove(KEYS.join);
                submit.textContent = "Envoyer ma candidature";
                // The confirmation replaces the review entry: it has no URL, and a reload shows it again.
                window.history.replaceState({ done: true, depth: state().depth || 0 }, "", window.location.pathname);
                showConfirmation(saved.email);
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
                field.target.addEventListener("input", refresh);
                field.target.addEventListener("change", refresh);
                field.target.addEventListener("countrychange", refresh);
                field.target.addEventListener("focusout", function (event) {
                    if (field.target.contains(event.relatedTarget)) return;
                    if (field.touched ? field.touched() : true) setFieldError(field.target, field.error, field.validate());
                });
            });

            var nextButton = $("[data-next]", slides[index]);
            if (nextButton) {
                nextButton.addEventListener("click", function () {
                    next(index);
                });
            }
            // Step 1's "Précédent" is a link out to the login page — except when editing from the review.
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

        // Enter in a field moves to the next step; on the review step the form submits the application.
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (current === REVIEW) submitApplication();
            else next(current);
        });
        form.addEventListener("keydown", function (event) {
            var target = event.target;
            if (event.key !== "Enter" || !target.matches("input:not([type=checkbox]):not([type=radio])")) return;
            if (target.closest(".iti__dropdown-content")) return; // intl-tel-input's country search
            event.preventDefault();
            if (current === REVIEW) submitApplication();
            else next(current);
        });

        window.addEventListener("popstate", function () {
            if (state().done && store.get(KEYS.joined)) {
                showConfirmation(store.get(KEYS.joined));
                return;
            }
            var index = allowed(requestedStep());
            if (index !== requestedStep()) {
                window.history.replaceState({ step: index, returning: false, depth: state().depth || 0 }, "", urlFor(index));
            }
            show(index, true);
        });

        /* First render: reloading the confirmation shows it again; otherwise the allowed step. */
        if (state().done && store.get(KEYS.joined)) {
            showConfirmation(store.get(KEYS.joined));
        } else {
            var start = allowed(requestedStep());
            var returning = start === requestedStep() && !!state().returning;
            window.history.replaceState({ step: start, returning: returning, depth: state().depth || 0 }, "", urlFor(start));
            show(start, false);
        }

        // Phone fields become usable once intl-tel-input's utils.js is in; until then their steps can't be passed.
        window.NdUtil.phoneReady().then(
            function (i18n) {
                phones.whatsapp.bind(i18n);
                phones.momo.bind(i18n);
                if (confirmation.hidden) show(current, false);
            },
            function () {
                phones.whatsapp.fail();
                phones.momo.fail();
                $all("[data-phone-unavailable]").forEach(function (el) {
                    el.hidden = false;
                });
            }
        );
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (document.body.getAttribute("data-page") === "join") initJoin();
    });
})();
