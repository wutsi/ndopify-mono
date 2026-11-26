/*
 * mandate/document-new.html and mandate/document.html enhancements (#329). Every action works without this file: the
 * add and each replacement post their multipart form, ?delete=1 opens the confirmation.
 * - the add form: "Prendre une photo" / "Choisir des images" replace the native input, the chosen files are listed in
 *   order by name and size (read in the browser, no thumbnail) and each can be removed; the checks mirror
 *   MandateDocumentRules (JPEG, PNG or WEBP, 5 Mo, 10 pages), the pages are shrunk to 2000 px as KYC's and sent straight
 *   to the storage (direct-upload.js: presign → PUT), then "POST <action>/create" records the document and the page
 *   opens "<action>/saved?documentId=". Any failure posts the form with its files: the multipart add stays the path
 *   without JS, and a failed page never leaves a half-created document.
 * - the document page: each *Remplacer* form sends its page straight to the storage (upload, attachTimeout: it
 *   replaces), then opens "<action>/saved"; a failure posts the form. The delete confirmation opens in place.
 * The rules live in MandateDocumentRules; this file only mirrors them.
 */
(function () {
    "use strict";

    var MAX_PAGES = 10;
    var MAX_FILE_BYTES = 5 * 1024 * 1024;
    var MAX_TITLE = 100;
    var MAX_SIDE = 2000;
    var ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];

    function $(selector, root) {
        return (root || document).querySelector(selector);
    }

    function $all(selector, root) {
        return Array.prototype.slice.call((root || document).querySelectorAll(selector));
    }

    function showError(element, message) {
        if (!element) return;
        $("[data-error-text]", element).textContent = message;
        element.hidden = !message;
    }

    function setInvalid(input, invalid) {
        if (!input) return;
        if (invalid) input.setAttribute("aria-invalid", "true");
        else input.removeAttribute("aria-invalid");
    }

    // aria-disabled, not disabled: an inactive button stays reachable, and a disabled input would post no file.
    function setActive(button, active) {
        if (!button) return;
        if (active) button.removeAttribute("aria-disabled");
        else button.setAttribute("aria-disabled", "true");
    }

    function formatSize(bytes) {
        if (bytes < 1024 * 1024) return Math.max(1, Math.round(bytes / 1024)) + " Ko";
        return (Math.round(bytes / (1024 * 1024) * 10) / 10).toString().replace(".", ",") + " Mo";
    }

    function pageCount(count) {
        return count + (count > 1 ? " pages" : " page");
    }

    // The rules of MandateDocumentRules, worded as they are there.
    function fileError(file) {
        if (ACCEPTED_TYPES.indexOf(file.type) === -1) {
            return "« " + file.name + " » n'est pas une image JPEG, PNG ou WEBP.";
        }
        if (file.size > MAX_FILE_BYTES) {
            return "« " + file.name + " » dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère.";
        }
        return "";
    }

    // Shrinks a page as KYC photos: at most MAX_SIDE on its longest side, as a JPEG at 0.85. False when the browser
    // can't (no canvas, a photo it can't decode): the original file is sent, and the server checks it.
    function shrink(file, callback) {
        var canvas = document.createElement("canvas");
        if (!canvas.getContext || !canvas.getContext("2d") || typeof Image !== "function") {
            callback(file);
            return;
        }
        try {
            var context = canvas.getContext("2d");
            var img = new Image();
            img.onload = function () {
                try {
                    var scale = Math.min(1, MAX_SIDE / Math.max(img.width, img.height));
                    canvas.width = Math.round(img.width * scale);
                    canvas.height = Math.round(img.height * scale);
                    context.drawImage(img, 0, 0, canvas.width, canvas.height);
                    canvas.toBlob(function (blob) {
                        callback(blob ? new File([blob], "page.jpg", { type: "image/jpeg" }) : file);
                    }, "image/jpeg", 0.85);
                } catch (e) {
                    callback(file);
                }
            };
            img.onerror = function () {
                callback(file);
            };
            img.src = URL.createObjectURL(file);
        } catch (e) {
            callback(file);
        }
    }

    /* ---------- mandate/document-new.html ---------- */

    function initNew(form) {
        var native = $("[data-pages-native]", form);
        var nativeInput = $("input[name=files]", form);
        var enhanced = $("[data-pages-enhanced]", form);
        var list = $("[data-pages-list]", form);
        var template = $("[data-page-template]", form);
        var countLine = $("[data-pages-count]", form);
        var live = $("[data-pages-live]", form);
        var error = $("#pages-error", form);
        var progress = $("[data-pages-progress]", form);
        var progressText = $("[data-pages-progress-text]", form);
        var progressBar = $("[data-pages-progress-bar]", form);
        var submit = $("[data-new-submit]", form);
        var inputs = $all("[data-page-input]", form);
        var type = $("#documentType", form);
        var title = $("#documentTitle", form);
        var titleError = $("#documentTitle-error", form);
        var titleCounter = $("[data-title-counter]", form);
        var chosen = [];
        var sending = false;

        // The chosen files are what the form posts when the direct upload can't run: browsers without DataTransfer
        // keep the native input, and no enhancement.
        if (typeof DataTransfer !== "function" || !nativeInput) return;
        native.hidden = true;
        enhanced.hidden = false;

        function sync() {
            try {
                var transfer = new DataTransfer();
                chosen.forEach(function (file) {
                    transfer.items.add(file);
                });
                nativeInput.files = transfer.files;
            } catch (e) {
                nativeInput.files = null; // the fallback posts no page: the server asks for one again
            }
        }

        function render() {
            list.innerHTML = "";
            chosen.forEach(function (file, i) {
                var row = template.content.firstElementChild.cloneNode(true);
                $("[data-page-index]", row).textContent = "Page " + (i + 1);
                // The name and the size, read in the browser from the File: no thumbnail.
                $("[data-page-name]", row).textContent = file.name;
                $("[data-page-size]", row).textContent = formatSize(file.size);
                var remove = $("[data-page-remove]", row);
                remove.setAttribute("aria-label", "Retirer la page " + (i + 1) + " (" + file.name + ")");
                remove.addEventListener("click", function () {
                    chosen.splice(i, 1);
                    live.textContent = "Page " + (i + 1) + " retirée.";
                    sync();
                    render();
                    inputs[0].focus();
                });
                list.appendChild(row);
            });
            list.hidden = chosen.length === 0;
            countLine.textContent = chosen.length
                ? pageCount(chosen.length) + " sur " + MAX_PAGES + " au plus"
                : "Aucune page choisie.";
            var full = chosen.length >= MAX_PAGES;
            inputs.forEach(function (input) {
                input.disabled = full || sending;
                input.closest("label").setAttribute("aria-disabled", full || sending ? "true" : "false");
            });
            setActive(submit, !sending);
        }

        inputs.forEach(function (input) {
            input.addEventListener("change", function () {
                var refused = [];
                Array.prototype.slice.call(input.files).forEach(function (file) {
                    var problem = fileError(file);
                    if (problem) {
                        refused.push(problem);
                    } else if (chosen.length >= MAX_PAGES) {
                        refused.push(MAX_PAGES + " pages au plus.");
                    } else {
                        chosen.push(file);
                    }
                });
                input.value = "";
                showError(error, refused.join(" "));
                live.textContent = pageCount(chosen.length) + " choisie" + (chosen.length > 1 ? "s" : "") + ".";
                sync();
                render();
                // A refused file keeps the document from being added until a valid one is chosen.
                setActive(submit, !sending && !refused.length);
            });
        });

        function count() {
            titleCounter.textContent = title.value.length + " / " + MAX_TITLE;
        }
        title.addEventListener("input", count);
        type.addEventListener("change", function () {
            showError($("#documentType-error", form), "");
            setInvalid(type, false);
        });
        count();

        /** Every error of the form at once; false when it can't be sent. */
        function validate() {
            var ok = true;
            if (!type.value) {
                showError($("#documentType-error", form), "Choisissez le type de document.");
                setInvalid(type, true);
                ok = false;
            }
            if (title.value.trim().length > MAX_TITLE) {
                showError(titleError, "100 caractères au plus.");
                setInvalid(title, true);
                ok = false;
            } else {
                showError(titleError, "");
                setInvalid(title, false);
            }
            if (!chosen.length) {
                showError(error, "Ajoutez au moins une page.");
                ok = false;
            } else {
                showError(error, "");
            }
            return ok;
        }

        form.addEventListener("submit", function (event) {
            // One submission, and none while a page is on its way.
            if (event.defaultPrevented) return;
            event.preventDefault();
            if (sending || submit.getAttribute("aria-disabled") === "true") return;
            if (!validate()) {
                (form.querySelector('[aria-invalid="true"]') || inputs[0]).focus();
                return;
            }
            var action = form.getAttribute("action");
            var paths = [];
            sending = true;
            submit.textContent = "Envoi en cours…";
            render();
            progress.hidden = false;
            progressText.textContent = "Préparation des pages…";
            progressBar.max = String(chosen.length);
            progressBar.value = "0";

            function fallback() {
                // Still busy: the form post leaves the page, and a second click would post it twice.
                form.submit();
            }

            function create() {
                progressText.textContent = "Document enregistré…";
                fetch(action + "/create", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        type: type.value,
                        title: title.value.trim() || null,
                        paths: paths
                    })
                }).then(function (response) {
                    if (!response.ok || response.redirected) return fallback();
                    return response.json().then(function (saved) {
                        if (!saved || !saved.documentId || saved.documentId < 0) return fallback();
                        location.assign(action + "/saved?documentId=" + saved.documentId);
                    });
                }).catch(fallback);
            }

            (function next(i) {
                if (i >= chosen.length) {
                    create();
                    return;
                }
                progressText.textContent = "Envoi de la page " + (i + 1) + " sur " + chosen.length + "…";
                shrink(chosen[i], function (page) {
                    window.NdDirectUpload.store(action, page).then(function (path) {
                        // No path: a page couldn't be stored. The whole form is posted, never a partial document.
                        if (!path) {
                            fallback();
                            return;
                        }
                        paths.push(path);
                        progressBar.value = String(i + 1);
                        next(i + 1);
                    });
                });
            })(0);
        });

        render();
    }

    /* ---------- mandate/document.html ---------- */

    function initDocument() {
        var documentRoot = $("[data-document]");
        if (!documentRoot) return;

        var title = $("#documentTitle", documentRoot);
        var counter = $("[data-title-counter]", documentRoot);
        if (title && counter) {
            function count() {
                counter.textContent = title.value.length + " / " + MAX_TITLE;
            }
            title.addEventListener("input", count);
            count();
        }

        $all("[data-page-replace] form", documentRoot).forEach(function (form) {
            var input = $("input[type=file]", form);
            var error = $(".field__error", form);
            var button = $("button[type=submit]", form);
            if (!input || !button) return;
            var label = button.textContent;
            var caption = $(".doc-page__caption", form.closest(".doc-page"));
            var number = caption ? caption.textContent.replace("Page ", "") : "";
            var sending = false;

            form.addEventListener("submit", function (event) {
                if (event.defaultPrevented) return;
                var file = input.files && input.files[0];
                var problem = !file ? "Choisissez une image." : fileError(file);
                showError(error, problem);
                setInvalid(input, !!problem);
                if (problem) {
                    event.preventDefault();
                    input.focus();
                    return;
                }
                if (!window.NdDirectUpload || sending) return;
                event.preventDefault();
                sending = true;
                setActive(button, false);
                button.textContent = number ? "Envoi de la page " + number + "…" : label;
                shrink(file, function (page) {
                    window.NdDirectUpload.upload(form.getAttribute("action"), page, { attachTimeout: true })
                        .then(function (uploaded) {
                            if (uploaded) {
                                location.assign(form.getAttribute("action") + "/saved");
                                return;
                            }
                            // The file is still in the input: the form post replaces the page as without JS.
                            form.submit();
                        });
                });
            });
        });

        var open = $("[data-delete-open]", documentRoot);
        var confirm = $("[data-delete-confirm]", documentRoot);
        var cancel = $("[data-delete-cancel]", documentRoot);
        if (!open || !confirm) return;

        function show(visible) {
            confirm.hidden = !visible;
            open.hidden = visible;
            open.setAttribute("aria-expanded", visible ? "true" : "false");
            if (visible) {
                var heading = $(".alert__title", confirm);
                if (heading) heading.focus();
            }
        }

        // Without JS, ?delete=1 is the confirmation open; with it, the same panel in place.
        if (!confirm.hidden) show(true);
        open.addEventListener("click", function (event) {
            event.preventDefault();
            show(true);
        });
        if (cancel) {
            cancel.addEventListener("click", function (event) {
                event.preventDefault();
                show(false);
                open.focus();
            });
        }
    }

    document.addEventListener("DOMContentLoaded", function () {
        var page = document.body.getAttribute("data-page");
        if (page === "mandate-document-new") {
            var form = $("[data-new-form]");
            if (form) initNew(form);
        } else if (page === "mandate-document") {
            initDocument();
        }
    });
})();
