/*
 * mandate/document-new.html and mandate/document.html enhancements (#329, #366). Every action works without this
 * file: the add and each replacement post their multipart form, ?delete=1 opens the confirmation.
 * - the add form: "Prendre une photo" (images) and "Choisir des fichiers" (images and PDFs) replace the native input,
 *   the chosen files are listed in order by name and size (read in the browser, no thumbnail; a PDF shows "PDF", its
 *   pages are the server's to count) and each can be removed; the checks mirror MandateDocumentRules (JPEG, PNG,
 *   WEBP or PDF, 5 Mo, 10 files), the images are shrunk to 2000 px as KYC's and sent straight to the storage
 *   (direct-upload.js: presign → PUT), then "POST <action>/create" records the document and the page opens
 *   "<action>/saved?documentId=". A PDF is never presigned: with one in the list, every image is shrunk first and the
 *   whole multipart form posts, so ndopify-server renders the PDF's pages. Any failure posts the form with its files:
 *   the multipart add stays the path without JS, and a failed page never leaves a half-created document.
 * - the document page: each *Remplacer* form sends its image straight to the storage (upload, attachTimeout: it
 *   replaces), then opens "<action>/saved"; a PDF posts the form (the server renders its one page, or refuses it), and
 *   a failed image upload posts the form. The delete confirmation opens in place.
 * The rules live in MandateDocumentRules; this file only mirrors them.
 */
(function () {
    "use strict";

    var MAX_PAGES = 10;
    // #369: the mandate's cap, all types together, LISTING and SEARCH alike.
    var MAX_DOCUMENTS = 5;
    var MAX_FILE_BYTES = 5 * 1024 * 1024;
    var MAX_TITLE = 100;
    var MAX_SIDE = 2000;
    var IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
    var PDF_TYPE = "application/pdf";
    var ACCEPTED_TYPES = IMAGE_TYPES.concat([PDF_TYPE]);
    // #369: one document is photos or one PDF, never both nor two PDFs.
    var MIXED_SOURCES = "Un document contient soit des photos, soit un PDF, jamais les deux : choisissez de nouveau " +
        "les pages.";

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

    // The rules of MandateDocumentRules, worded as they are there. A PDF's password, pages and readability are the
    // server's to check, not the browser's.
    function isPdf(file) {
        return file.type === PDF_TYPE;
    }

    // #369: a selection holding more than one PDF, or a PDF with any other file.
    function mixedProblem(files) {
        var pdfs = files.filter(isPdf).length;
        return pdfs > 1 || (pdfs === 1 && files.length > 1) ? MIXED_SOURCES : "";
    }

    // #369: the replacement of the other kind, by the document's source (UNKNOWN takes either).
    function mixedReplace(source) {
        if (source === "PDF") {
            return "Ce document a été créé à partir d'un PDF : remplacez cette page par un PDF d'une page, pas par " +
                "une photo.";
        }
        if (source === "IMAGE") {
            return "Ce document a été créé à partir de photos : remplacez cette page par une photo JPEG, PNG ou " +
                "WEBP, pas par un PDF.";
        }
        return "Ce document accepte une photo ou un PDF d'une page : vérifiez le fichier.";
    }

    // #369: the replacement form's no-file error, by the document's source.
    function replaceRequired(source) {
        if (source === "PDF") return "Choisissez un PDF d'une page pour cette page.";
        if (source === "IMAGE") return "Choisissez une photo pour cette page.";
        return "Choisissez la nouvelle page : une photo ou un PDF.";
    }

    function fileError(file, source) {
        if (ACCEPTED_TYPES.indexOf(file.type) === -1) {
            return "« " + file.name + " » n'est ni une photo ni un PDF : choisissez une photo JPEG, PNG ou WEBP, " +
                "ou un PDF.";
        }
        if (file.size > MAX_FILE_BYTES) {
            if (isPdf(file)) {
                // #369: a PDF document refuses a photo, so its PDF refusal never suggests one.
                return source === "PDF"
                    ? "« " + file.name + " » dépasse 5 Mo : choisissez un PDF plus léger."
                    : "« " + file.name + " » dépasse 5 Mo : choisissez un PDF plus léger, ou envoyez ses pages en " +
                        "photos.";
            }
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
        var limitError = $("[data-new-limit-error]");
        // #369: the mandate's stored document count, from the server; a stale tab below the cap still sees it here.
        var documentCount = parseInt(form.getAttribute("data-documents-count"), 10);
        if (isNaN(documentCount)) documentCount = 0;
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

        // The browser can't count a PDF's pages, so with one the line counts files and says when its pages count.
        function countText() {
            if (!chosen.length) return "Aucun fichier choisi.";
            if (!chosen.some(isPdf)) return pageCount(chosen.length) + " sur " + MAX_PAGES + " au plus";
            return chosen.length + " fichier" + (chosen.length > 1 ? "s" : "") + " choisi" +
                (chosen.length > 1 ? "s" : "") + " · " + MAX_PAGES + " pages au plus. Les pages d'un PDF " +
                "comptent à l'envoi.";
        }

        // What the screen reader hears when the list changes (the count line is not a live region).
        function announcement() {
            if (!chosen.length) return "Aucun fichier choisi.";
            if (!chosen.some(isPdf)) return pageCount(chosen.length) + " choisie" + (chosen.length > 1 ? "s" : "") + ".";
            return chosen.length + " fichier" + (chosen.length > 1 ? "s" : "") + " choisi" +
                (chosen.length > 1 ? "s" : "") + ".";
        }

        function render() {
            list.innerHTML = "";
            var images = 0;
            chosen.forEach(function (file, i) {
                var pdf = isPdf(file);
                if (!pdf) images += 1;
                var row = template.content.firstElementChild.cloneNode(true);
                // A PDF isn't "Page N": its pages are the server's to count, so the row says only that it is a PDF.
                $("[data-page-index]", row).textContent = pdf ? "PDF" : "Page " + images;
                // The name and the size, read in the browser from the File: no thumbnail.
                $("[data-page-name]", row).textContent = file.name;
                $("[data-page-size]", row).textContent = formatSize(file.size);
                var remove = $("[data-page-remove]", row);
                remove.setAttribute(
                    "aria-label",
                    pdf ? "Retirer le PDF " + file.name : "Retirer la page " + images + " (" + file.name + ")",
                );
                remove.addEventListener("click", function () {
                    chosen.splice(i, 1);
                    sync();
                    render();
                    // Removing the PDF (or the last photo) lifts the mixed-selection refusal.
                    showError(error, mixedProblem(chosen));
                    inputs[0].focus();
                });
                list.appendChild(row);
            });
            list.hidden = chosen.length === 0;
            countLine.textContent = countText();
            if (!sending) live.textContent = announcement();
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
                        // Every file gives at least one page, so more than 10 files is always more than 10 pages.
                        refused.push(MAX_PAGES + " fichiers au plus par document : « " + file.name +
                            " » n'a pas été ajouté.");
                    } else {
                        chosen.push(file);
                    }
                });
                input.value = "";
                var mixed = mixedProblem(chosen);
                showError(error, refused.concat(mixed ? [mixed] : []).join(" "));
                sync();
                render();
                // A refused or mixed selection keeps the document from being added until a valid one is chosen.
                setActive(submit, !sending && !refused.length && !mixed);
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
            // #369: a tab left open below the cap while another tab added the 5th: the 6th is refused here (the
            // server would answer MANDATE_DOCUMENT_LIMIT_REACHED). The form's attribute is read again on submit, so a
            // count refreshed by the page counts too.
            var count = parseInt(form.getAttribute("data-documents-count"), 10);
            if (isNaN(count)) count = documentCount;
            if (count >= MAX_DOCUMENTS) {
                if (limitError) {
                    limitError.hidden = false;
                    limitError.focus();
                }
                ok = false;
            } else if (limitError) {
                limitError.hidden = true;
            }
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
                var mixed = mixedProblem(chosen);
                showError(error, mixed);
                if (mixed) ok = false;
            }
            return ok;
        }

        form.addEventListener("submit", function (event) {
            // One submission, and none while a page is on its way.
            if (event.defaultPrevented) return;
            event.preventDefault();
            if (sending || submit.getAttribute("aria-disabled") === "true") return;
            if (!validate()) {
                (form.querySelector('[aria-invalid="true"]') || (limitError && !limitError.hidden && limitError) ||
                    inputs[0]).focus();
                return;
            }
            var action = form.getAttribute("action");
            var paths = [];
            sending = true;
            submit.textContent = "Envoi en cours…";
            render();

            function fallback() {
                // Still busy: the form post leaves the page, and a second click would post it twice.
                form.submit();
            }

            // A PDF is never presigned: each image is shrunk, the files go back to the native input, and the whole
            // multipart form posts so that ndopify-server renders the PDF's pages (a page count only it knows).
            function sendAsForm() {
                submit.textContent = "Envoi du document…";
                render();
                progress.hidden = false;
                progressText.textContent = "Envoi du document…";
                progressBar.removeAttribute("value");
                var next = 0;
                (function loop() {
                    if (next >= chosen.length) {
                        sync();
                        form.submit();
                        return;
                    }
                    var index = next;
                    next += 1;
                    if (isPdf(chosen[index])) {
                        loop();
                        return;
                    }
                    shrink(chosen[index], function (page) {
                        chosen[index] = page;
                        loop();
                    });
                })();
            }

            if (chosen.some(isPdf)) {
                sendAsForm();
                return;
            }

            progress.hidden = false;
            progressText.textContent = "Préparation des pages…";
            progressBar.max = String(chosen.length);
            progressBar.value = "0";

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

        // #369: the kind the document was created from, which decides the files its Remplacer takes.
        var documentSource = $("[data-document-source]", documentRoot);
        var source = documentSource ? documentSource.getAttribute("data-source") : "UNKNOWN";

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
                var problem = !file ? replaceRequired(source) : "";
                // #369: the file must be the document's kind (the server's MANDATE_DOCUMENT_MIXED_SOURCES).
                if (!problem && source === "PDF" && !isPdf(file) && ACCEPTED_TYPES.indexOf(file.type) !== -1) {
                    problem = mixedReplace(source);
                }
                if (!problem && source === "IMAGE" && isPdf(file)) problem = mixedReplace(source);
                if (!problem && file) problem = fileError(file, source);
                showError(error, problem);
                setInvalid(input, !!problem);
                if (problem) {
                    event.preventDefault();
                    input.focus();
                    return;
                }
                // A PDF is never presigned: the multipart form posts, and ndopify-server renders its one page or
                // refuses it (a multi-page, protected or unreadable PDF comes back as this field's error).
                if (isPdf(file)) {
                    setActive(button, false);
                    button.textContent = "Envoi du fichier…";
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
