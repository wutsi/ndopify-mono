/*
 * Ndopify Partner Central — a listing's pages: its checklist (templates/listing.html), its sections
 * (templates/listing/section.html) and its photos (templates/listing/photos.html). They all work without this file:
 * every form posts to ListingController / ListingSectionController / ListingPhotoController, which check everything
 * again. This script only enhances them:
 * - checklist: "Envoyer pour vérification" while inactive moves the focus to what's left instead of posting;
 * - sections: a button that posted is disabled ("Enregistrement…"), a field's error goes once it's changed; "Le bien"
 *   shows the types of the chosen category and the questions of the type only; "Position GPS" takes the phone's
 *   position (the typed latitude / longitude stay the fallback), with its accuracy and a map link;
 * - photos: each photo taken or chosen is checked, shrunk and sent at once, without "Envoyer les photos".
 * Ported from the prototype (src/main/html/assets/js/partner-central.js: initListing, initListingSection,
 * initListingProperty, initListingGps, initListingPhotos).
 */
(function () {
    "use strict";

    // ListingRules
    var MAX_PHOTOS = 20;
    var MAX_PHOTO_BYTES = 5 * 1024 * 1024;
    // The photos posted together: under spring.servlet.multipart.max-request-size (10 Mo), with room for the rest
    var MAX_REQUEST_BYTES = 9 * 1024 * 1024;
    var PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"];
    // A shrunk photo's longest side: sharp enough for a listing, light enough to send many at once
    var MAX_SIDE = 1600;
    // Beyond, the phone guessed (indoors, under a roof): the agent takes the position again outside
    var WEAK_ACCURACY = 50;

    // ListingRules.applies: the questions of "Le bien" asked for a type.
    var APPLIES = {
        furniture: function (a) {
            return a.category === "RESIDENTIAL" && a.type !== "BUILDING";
        },
        rooms: function (a) {
            return a.category === "RESIDENTIAL" && ["ROOM", "STUDIO", "BUILDING"].indexOf(a.type) === -1;
        },
        bathrooms: function (a) {
            return a.type !== "LAND";
        },
        kitchens: function (a) {
            return a.category === "RESIDENTIAL" && ["ROOM", "BUILDING"].indexOf(a.type) === -1;
        },
        parkings: function (a) {
            return a.type !== "ROOM";
        },
        levels: function (a) {
            return ["HOUSE", "DUPLEX", "BUILDING"].indexOf(a.type) !== -1;
        },
        floorLevel: function (a) {
            return ["ROOM", "STUDIO", "APARTMENT", "OFFICE", "RETAIL"].indexOf(a.type) !== -1;
        }
    };

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
        if (!errorEl || !target) return;
        if (message) {
            errorEl.querySelector("[data-error-text]").textContent = message;
            errorEl.hidden = false;
            target.setAttribute("aria-invalid", "true");
        } else {
            errorEl.hidden = true;
            target.removeAttribute("aria-invalid");
        }
    }

    // A form posts once: its button is disabled (with [label], if any) while the next page loads.
    function guardSubmit(form, label) {
        var button = $('[type="submit"]', form);
        if (!button) return;
        form.addEventListener("submit", function (event) {
            if (button.disabled) {
                event.preventDefault();
                return;
            }
            window.setTimeout(function () {
                button.disabled = true;
                if (label) button.textContent = label;
            }, 0);
        });
    }

    function focusFlash(selector) {
        var flash = $(selector);
        if (flash) flash.focus();
    }

    /* ---------- The checklist ---------- */
    function initListing() {
        focusFlash("[data-listing-flash]");
        var form = $("[data-listing-send-form]");
        if (!form) return;
        var submit = $("[data-listing-submit]", form);
        var missing = $("[data-listing-missing]");
        form.addEventListener("submit", function (event) {
            if (submit.getAttribute("aria-disabled") === "true") {
                event.preventDefault();
                missing.focus();
            }
        });
        guardSubmit(form, "Envoi…");
    }

    /* ---------- A section ---------- */
    function initProperty(form) {
        function sync() {
            var category = ($('[name="propertyCategory"]:checked', form) || {}).value;
            $all('[name="propertyType"]', form).forEach(function (radio) {
                var label = radio.closest("[data-categories]");
                var fits = !category || label.getAttribute("data-categories") === category;
                label.hidden = !fits;
                if (!fits) radio.checked = false;
            });
            var answers = {
                category: category,
                type: ($('[name="propertyType"]:checked', form) || {}).value
            };
            $all("[data-applies]", form).forEach(function (el) {
                el.hidden = !answers.type || !APPLIES[el.getAttribute("data-applies")](answers);
            });
        }
        $all('[name="propertyCategory"], [name="propertyType"]', form).forEach(function (radio) {
            radio.addEventListener("change", sync);
        });
        sync();
    }

    function initGps(form) {
        var button = $("[data-gps]", form);
        if (!button || !navigator.geolocation) return;
        var latitude = form.elements.latitude;
        var longitude = form.elements.longitude;
        var accuracy = form.elements.accuracy;
        var result = $("[data-gps-result]", form);
        var weak = $("[data-gps-weak]", form);
        var error = $("#latitude-error", form);
        var manual = $("[data-gps-manual]", form);

        // The phone gives the position: the typed one is only the fallback, back when it can't be had
        manual.hidden = true;
        button.hidden = false;

        function show() {
            var lat = latitude.value.trim();
            var lng = longitude.value.trim();
            result.hidden = !lat || !lng;
            if (result.hidden) return;
            var meters = Number(accuracy.value);
            $("[data-gps-coords]", result).textContent = Number(lat).toFixed(5) + ", " + Number(lng).toFixed(5) +
                (meters ? " (± " + meters + " m)" : "");
            $("[data-gps-map]", result).href = "https://www.openstreetmap.org/?mlat=" + lat + "&mlon=" + lng +
                "#map=18/" + lat + "/" + lng;
            weak.hidden = !(meters > WEAK_ACCURACY);
            button.textContent = "Relever à nouveau";
        }

        button.addEventListener("click", function () {
            var label = button.textContent;
            button.disabled = true;
            button.textContent = "Localisation…";
            navigator.geolocation.getCurrentPosition(function (position) {
                button.disabled = false;
                latitude.value = position.coords.latitude;
                longitude.value = position.coords.longitude;
                accuracy.value = Math.round(position.coords.accuracy);
                setFieldError(button, error, null);
                show();
            }, function (e) {
                button.disabled = false;
                button.textContent = label;
                manual.hidden = false;
                setFieldError(button, error, e.code === 1
                    ? "Autorisez l'accès à votre position dans le navigateur, puis réessayez."
                    : "Position introuvable : sortez devant l'entrée du bien et réessayez.");
            }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 });
        });
        show();
    }

    function initSection() {
        var form = $("[data-section-form]");
        if (!form || !$("[data-section-submit]", form)) return;
        focusFlash("[data-section-error]");
        var section = document.body.getAttribute("data-section");
        if (section === "property") initProperty(form);
        if (section === "gps") initGps(form);

        // A field's error goes once it's changed
        function clear(event) {
            var name = event.target.name;
            var error = name && $("#" + name + "-error", form);
            if (error) setFieldError(event.target, error, null);
        }
        form.addEventListener("input", clear);
        form.addEventListener("change", clear);
        guardSubmit(form, "Enregistrement…");
    }

    /* ---------- The photos ---------- */
    function shrink(file, callback) {
        var url = URL.createObjectURL(file);
        var img = new Image();
        img.onload = function () {
            URL.revokeObjectURL(url);
            var scale = Math.min(1, MAX_SIDE / Math.max(img.width, img.height));
            var canvas = document.createElement("canvas");
            canvas.width = Math.round(img.width * scale);
            canvas.height = Math.round(img.height * scale);
            canvas.getContext("2d").drawImage(img, 0, 0, canvas.width, canvas.height);
            canvas.toBlob(function (blob) {
                var name = file.name.replace(/\.[^.]*$/, "") + ".jpg";
                callback(blob ? new File([blob], name, { type: "image/jpeg" }) : null);
            }, "image/jpeg", 0.85);
        };
        img.onerror = function () {
            URL.revokeObjectURL(url);
            callback(null);
        };
        img.src = url;
    }

    function initPhotos() {
        var form = $("[data-photo-form]");
        if (!form) return;
        var inputs = $all("[data-photo-input]", form);
        var submit = $("[data-photo-submit]", form);
        var error = $("#photos-error");
        var count = $("[data-photo-count]");
        var live = $("[data-photo-live]");
        var existing = $all("[data-photos] > li").length;

        // Each photo is sent as soon as it's chosen: the button is for browsers without JS
        submit.hidden = true;

        function fail(message) {
            setFieldError(inputs[0], error, message);
            inputs.forEach(function (input) {
                input.value = "";
            });
            if (message) announce(live, message);
        }

        // Puts the photos in [input] (the others emptied), so the normal form post sends them
        function send(input, files) {
            try {
                var transfer = new DataTransfer();
                files.forEach(function (file) {
                    transfer.items.add(file);
                });
                inputs.forEach(function (other) {
                    if (other !== input) other.value = "";
                });
                input.files = transfer.files;
            } catch (e) {
                // Can't swap the files: the originals are posted, the server checks them
            }
            inputs.forEach(function (other) {
                other.closest("label").setAttribute("aria-disabled", "true");
            });
            count.textContent = "Envoi…";
            announce(live, "Envoi des photos…");
            form.submit();
        }

        inputs.forEach(function (input) {
            input.addEventListener("change", function () {
                var files = Array.prototype.slice.call(input.files || []);
                if (!files.length) return;
                setFieldError(inputs[0], error, null);
                if (existing + files.length > MAX_PHOTOS) return fail(MAX_PHOTOS + " photos au plus.");
                var wrong = files.filter(function (file) {
                    return PHOTO_TYPES.indexOf(file.type) === -1;
                })[0];
                if (wrong) return fail(wrong.name + " n'est pas une photo JPEG, PNG ou WEBP.");

                // One after another: a phone decoding 20 full-size photos at once runs out of memory
                var shrunk = [];
                var total = 0;
                (function next(i) {
                    if (i === files.length) return send(input, shrunk);
                    shrink(files[i], function (small) {
                        var photo = small || files[i];
                        if (photo.size > MAX_PHOTO_BYTES) return fail(photo.name + " dépasse 5 Mo.");
                        total += photo.size;
                        if (total > MAX_REQUEST_BYTES) {
                            return fail("Ces photos sont trop lourdes ensemble : choisissez-en moins à la fois.");
                        }
                        shrunk.push(photo);
                        next(i + 1);
                    });
                })(0);
            });
        });

        $all("[data-photo-delete-form]").forEach(function (deletion) {
            guardSubmit(deletion);
        });
    }

    var pages = {
        listing: initListing,
        "listing-section": initSection,
        "listing-photos": initPhotos
    };

    document.addEventListener("DOMContentLoaded", function () {
        var init = pages[document.body.getAttribute("data-page")];
        if (init) init();
    });
})();
