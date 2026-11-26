/*
 * Runs the built page scripts (target/classes/public/assets/dist/app.min.js) on pages the Thymeleaf template tests
 * rendered (target/script-fixtures/, see ScriptFixtures.kt), in jsdom: a script that throws while a step loads — as
 * offer-property.js did on every step but the first (#304) — or stops showing the questions of the chosen type fails
 * the build. The CDN libraries (slim-select, intl-tel-input) aren't loaded: the scripts must cope without them, as
 * they do when the CDN is down.
 *
 * Run by Maven after the Kotlin tests (`mvn test`), or `npm test` once they have run.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
import { JSDOM, VirtualConsole } from "jsdom";

const BUNDLE = "target/classes/public/assets/dist/app.min.js";
const FIXTURES = "target/script-fixtures";

/*
 * Loads a fixture with the bundle in place of its <script> tags, and collects every error the scripts raise. Resolves
 * once the page has loaded: jsdom fires DOMContentLoaded, where the page scripts start, after the constructor returns.
 */
async function load(name) {
    const file = `${FIXTURES}/${name}.html`;
    assert.ok(existsSync(file), `${file} is missing: run the Kotlin template tests first (mvn test)`);
    const bundle = readFileSync(BUNDLE, "utf8");
    const html = readFileSync(file, "utf8")
        .replace(/<script\b[^>]*\bsrc=[^>]*>\s*<\/script>/g, "")
        .replace("</body>", () => `<script>${bundle}</script></body>`);
    const errors = [];
    const virtualConsole = new VirtualConsole();
    virtualConsole.on("jsdomError", (error) => errors.push(error.message));
    const dom = new JSDOM(html, {
        url: "http://localhost:8081/",
        runScripts: "dangerously",
        virtualConsole,
        beforeParse(window) {
            // jsdom has no matchMedia, which every browser has (listing.js's viewer reads prefers-reduced-motion)
            window.matchMedia = (query) => ({
                matches: false, media: query, addEventListener() {}, removeEventListener() {}
            });
            // ... nor DataTransfer, which the documents' add form uses to carry its chosen pages into the native input
            window.DataTransfer = function () {
                this.items = { add() {} };
                this.files = null;
            };
        }
    });
    if (dom.window.document.readyState !== "complete") {
        await new Promise((resolve) => dom.window.addEventListener("load", resolve));
    }
    return { window: dom.window, document: dom.window.document, errors };
}

function visible(document, selector) {
    const el = document.querySelector(selector);
    assert.ok(el, `${selector} is not on the page`);
    return !el.closest("[hidden]");
}

function choose(page, name, value) {
    const input = page.document.querySelector(`input[name="${name}"][value="${value}"]`);
    assert.ok(input, `${name}=${value} is not offered`);
    input.checked = true;
    input.dispatchEvent(new page.window.Event("change", { bubbles: true }));
}

/* Types [text] at the end of the field, as the browser reports it: one input event, the caret after the text. */
function type(page, selector, text) {
    const input = page.document.querySelector(selector);
    input.value = text;
    input.setSelectionRange(text.length, text.length);
    input.dispatchEvent(new page.window.Event("input", { bubbles: true }));
    return input.value;
}

const ALL_FIXTURES = [
    "offer-property", "offer-terms-sale", "offer-features-residential-sale", "offer-features-commercial-sale",
    "offer-features-residential-rental", "find-property-residential-sale", "find-budget-building-sale",
    "listing-photos", "mandate-document-new", "mandate-document", "mandate-document-pdf", "mandate-document-image"
];

for (const name of ALL_FIXTURES) {
    test(`${name}: the page scripts load without an error`, async () => {
        assert.deepEqual((await load(name)).errors, []);
    });
}

test("offer-property, terms: the price is grouped by thousands as it is typed", async () => {
    const page = await load("offer-terms-sale");
    assert.equal(type(page, "#priceAmount", "50000000"), "50 000 000");
    assert.equal(type(page, "#priceAmount", "1234567890123"), "123 456 789 012", "12 digits at most");
});

test("offer-property, features: no follow-up question before a type, a sale's documents excepted", async () => {
    const page = await load("offer-features-residential-sale");
    for (const group of ["furnitureType", "rooms", "hasParking", "parkings", "area"]) {
        assert.equal(visible(page.document, `[data-group="${group}"]`), false, group);
    }
    assert.equal(visible(page.document, '[data-group="ownershipDocuments"]'), true);
});

test("offer-property, features: an apartment sale asks its furniture, rooms and parking, no surface", async () => {
    const page = await load("offer-features-residential-sale");
    choose(page, "propertyType", "APARTMENT");
    for (const group of ["furnitureType", "rooms", "hasParking"]) {
        assert.equal(visible(page.document, `[data-group="${group}"]`), true, group);
        assert.ok(page.document.querySelector(`input[name="${group}"]`).required, `${group} required`);
    }
    assert.equal(visible(page.document, '[data-group="area"]'), false);
    choose(page, "hasParking", "YES");
    assert.equal(visible(page.document, '[data-group="parkings"]'), true);
});

test("offer-property, features: a studio sale asks no rooms", async () => {
    const page = await load("offer-features-residential-sale");
    choose(page, "propertyType", "STUDIO");
    assert.equal(visible(page.document, '[data-group="rooms"]'), false);
    assert.equal(visible(page.document, '[data-group="furnitureType"]'), true);
});

test("offer-property, features: a land sale groups its surface and shows its price per m²", async () => {
    const page = await load("offer-features-residential-sale");
    choose(page, "propertyType", "LAND");
    assert.equal(visible(page.document, '[data-group="area"]'), true);
    assert.equal(visible(page.document, '[data-group="rooms"]'), false);
    assert.equal(type(page, "#area", "1200"), "1 200");
    const rate = page.document.querySelector("[data-price-per-sqm]");
    assert.equal(rate.hidden, false);
    assert.match(rate.textContent, /\/m² pour 1 200 m²$/);
});

test("offer-property, features: a building sale asks its counts and shows its price per unit", async () => {
    const page = await load("offer-features-commercial-sale");
    assert.equal(visible(page.document, "[data-building-counts]"), false);
    choose(page, "propertyType", "BUILDING");
    assert.equal(visible(page.document, "[data-building-counts]"), true);
    assert.equal(visible(page.document, '[data-group="area"]'), true);
    type(page, "#units", "8");
    const rate = page.document.querySelector("[data-price-per-unit]");
    assert.equal(rate.hidden, false);
    assert.match(rate.textContent, /\/unité pour 8 unités$/);
});

test("offer-property, features: a rental offers no building", async () => {
    const page = await load("offer-features-residential-rental");
    assert.equal(page.document.querySelector('input[name="propertyType"][value="BUILDING"]'), null);
    choose(page, "propertyType", "ROOM");
    assert.equal(visible(page.document, '[data-group="rooms"]'), false);
    assert.equal(visible(page.document, '[data-group="hasParking"]'), true);
    assert.equal(visible(page.document, '[data-group="ownershipDocuments"]'), false);
});

test("find-property, property: a purchase's questions follow its type", async () => {
    const page = await load("find-property-residential-sale");
    choose(page, "propertyType", "APARTMENT");
    assert.equal(visible(page.document, '[data-group="rooms"]'), true);
    assert.equal(visible(page.document, '[data-group="area"]'), false);
    choose(page, "propertyType", "LAND");
    assert.equal(visible(page.document, '[data-group="rooms"]'), false);
    assert.equal(visible(page.document, '[data-group="area"]'), true);
    assert.ok(page.document.querySelector("#area").required);
    assert.equal(visible(page.document, '[data-group="titleDocuments"]'), true);
});

test("find-property, budget: a building purchase shows its price per unit as the budget is typed", async () => {
    const page = await load("find-budget-building-sale");
    assert.equal(type(page, "#priceAmount", "1234567890123"), "123 456 789 012", "12 digits at most");
    assert.equal(type(page, "#priceAmount", "300000000"), "300 000 000");
    const rate = page.document.querySelector("[data-price-per-unit]");
    assert.equal(rate.hidden, false);
    assert.match(rate.textContent, /\/unité pour 12 unités$/);
});

/* Chooses [count] photos in the gallery input: jsdom decodes no image, so the page stays on "Préparation…". */
function choosePhotos(page, count) {
    page.window.URL.createObjectURL = () => "blob:photo";
    page.window.URL.revokeObjectURL = () => {};
    const input = page.document.querySelectorAll("[data-photo-input]")[1];
    const files = Array.from({ length: count }, (_, i) => new page.window.File(["x"], `p${i}.jpg`, { type: "image/jpeg" }));
    Object.defineProperty(input, "files", { value: files, configurable: true });
    input.dispatchEvent(new page.window.Event("change", { bubbles: true }));
    return input;
}

test("listing photos: choosing photos shows the progress and makes the buttons inactive", async () => {
    const page = await load("listing-photos");
    const progress = page.document.querySelector("[data-photo-progress]");
    assert.equal(progress.hidden, true);
    assert.equal(page.document.querySelector("[data-photo-submit]").hidden, true);

    const input = choosePhotos(page, 3);

    assert.equal(progress.hidden, false);
    assert.equal(page.document.querySelector("[data-photo-progress-text]").textContent, "Préparation des photos…");
    for (const label of page.document.querySelectorAll("[data-photo-actions] label")) {
        assert.equal(label.getAttribute("aria-disabled"), "true");
    }
    // Inactive, not disabled: the multipart fallback posts the input's files
    assert.equal(input.disabled, false);
    const click = new page.window.MouseEvent("click", { bubbles: true, cancelable: true });
    input.closest("label").dispatchEvent(click);
    assert.equal(click.defaultPrevented, true);
    assert.deepEqual(page.errors, []);
});

test("listing photos: a photo refused before sending hides the progress again", async () => {
    const page = await load("listing-photos");
    choosePhotos(page, 21);

    assert.equal(page.document.querySelector("[data-photo-progress]").hidden, true);
    assert.equal(page.document.querySelector("#photos-error").hidden, false);
    for (const label of page.document.querySelectorAll("[data-photo-actions] label")) {
        assert.equal(label.hasAttribute("aria-disabled"), false);
    }
});

/* ---------- #329: a mandate's documents ---------- */

function pageFile(page, name, type = "image/jpeg", size = 2048) {
    return new page.window.File([new Uint8Array(size)], name, { type });
}

/* The second "data-page-input" is "Choisir des fichiers" (several); the first one takes a photo. */
function choosePages(page, files) {
    const input = page.document.querySelectorAll("[data-page-input]")[1];
    Object.defineProperty(input, "files", { value: files, configurable: true });
    input.dispatchEvent(new page.window.Event("change", { bubbles: true }));
    return input;
}

async function eventually(predicate, label) {
    for (let i = 0; i < 200; i++) {
        if (predicate()) return;
        await new Promise((resolve) => setTimeout(resolve, 10));
    }
    assert.fail(label);
}

test("mandate documents, add: the chosen pages are listed by name and size, with no image", async () => {
    const page = await load("mandate-document-new");

    choosePages(page, [pageFile(page, "IMG_2041.jpg"), pageFile(page, "IMG_2042.jpg", "image/png", 2 * 1024 * 1024)]);

    const rows = page.document.querySelectorAll("[data-pages-list] li");
    assert.equal(rows.length, 2);
    assert.equal(rows[0].querySelector("[data-page-index]").textContent, "Page 1");
    assert.equal(rows[0].querySelector("[data-page-name]").textContent, "IMG_2041.jpg");
    assert.equal(rows[0].querySelector("[data-page-size]").textContent, "2 Ko");
    assert.equal(rows[1].querySelector("[data-page-index]").textContent, "Page 2");
    assert.equal(rows[1].querySelector("[data-page-size]").textContent, "2 Mo");
    assert.equal(page.document.querySelectorAll("[data-pages-list] img").length, 0);
    assert.equal(page.document.querySelector("[data-pages-count]").textContent, "2 pages sur 10 au plus");
    assert.equal(page.document.querySelector("[data-new-submit]").hasAttribute("aria-disabled"), false);

    // Removable before sending: the second one leaves
    rows[1].querySelector("[data-page-remove]").dispatchEvent(new page.window.Event("click", { bubbles: true }));
    assert.equal(page.document.querySelectorAll("[data-pages-list] li").length, 1);
    assert.deepEqual(page.errors, []);
});

test("mandate documents, add: a 6 Mo page shows the size error and keeps Ajouter inactive", async () => {
    const page = await load("mandate-document-new");

    choosePages(page, [pageFile(page, "IMG_2041.jpg", "image/jpeg", 6 * 1024 * 1024)]);

    const error = page.document.querySelector("#pages-error");
    assert.equal(error.hidden, false);
    assert.equal(error.querySelector("[data-error-text]").textContent,
        "« IMG_2041.jpg » dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère.");
    assert.equal(page.document.querySelector("[data-pages-list]").hidden, true);
    assert.equal(page.document.querySelector("[data-new-submit]").getAttribute("aria-disabled"), "true");
});

test("mandate documents, add: a page that can't be stored posts the form, never creates a document", async () => {
    const page = await load("mandate-document-new");
    let created = false;
    let submitted = false;
    let put = 0;
    page.window.HTMLFormElement.prototype.submit = function () {
        submitted = true;
    };
    page.window.fetch = (url, options) => {
        if (String(url).endsWith("/presign")) {
            return Promise.resolve({
                ok: true, redirected: false,
                json: () => Promise.resolve({ url: "https://s3/put", path: "mandate/301/documents/page" })
            });
        }
        if (String(url).endsWith("/create")) {
            created = true;
            return Promise.resolve({ ok: true, redirected: false, json: () => Promise.resolve({ documentId: 1 }) });
        }
        put += 1;
        return Promise.resolve({ ok: put === 1, redirected: false });
    };

    choosePages(page, [pageFile(page, "IMG_2041.jpg"), pageFile(page, "IMG_2042.jpg")]);
    page.document.querySelector("#documentType").value = "PASSPORT";
    page.document.querySelector("[data-new-form]")
        .dispatchEvent(new page.window.Event("submit", { bubbles: true, cancelable: true }));

    await eventually(() => submitted, "the form was not posted after a failed page");
    assert.equal(created, false, "no document is created when a page couldn't be stored");
    assert.equal(put, 2, "the second page's PUT failed");
});

test("mandate documents, add: the pages are stored then the document created", async () => {
    const page = await load("mandate-document-new");
    const calls = [];
    page.window.HTMLFormElement.prototype.submit = () => assert.fail("the form must not be posted");
    page.window.fetch = (url, options) => {
        calls.push(String(url));
        if (String(url).endsWith("/presign")) {
            return Promise.resolve({
                ok: true, redirected: false,
                json: () => Promise.resolve({ url: "https://s3/put", path: "mandate/301/documents/page" })
            });
        }
        if (String(url).endsWith("/create")) {
            const body = JSON.parse(options.body);
            assert.deepEqual(body.paths, ["mandate/301/documents/page", "mandate/301/documents/page"]);
            assert.equal(body.type, "LAND_TITLE");
            assert.equal(body.title, null);
            return Promise.resolve({ ok: true, redirected: false, json: () => Promise.resolve({ documentId: 42 }) });
        }
        return Promise.resolve({ ok: true, redirected: false });
    };

    choosePages(page, [pageFile(page, "IMG_2041.jpg"), pageFile(page, "IMG_2042.jpg")]);
    page.document.querySelector("#documentType").value = "LAND_TITLE";
    page.document.querySelector("[data-new-form]")
        .dispatchEvent(new page.window.Event("submit", { bubbles: true, cancelable: true }));

    await eventually(() => calls.some((url) => url.endsWith("/create")), "the document was not created");
    assert.equal(calls.filter((url) => url.endsWith("/create")).length, 1);
});

test("mandate documents, document page: the delete confirmation opens in place", async () => {
    const page = await load("mandate-document");

    const open = page.document.querySelector("[data-delete-open]");
    const confirm = page.document.querySelector("[data-delete-confirm]");
    assert.equal(confirm.hidden, true);
    open.dispatchEvent(new page.window.MouseEvent("click", { bubbles: true, cancelable: true }));
    assert.equal(confirm.hidden, false);
    assert.equal(open.getAttribute("aria-expanded"), "true");
    page.document.querySelector("[data-delete-cancel]")
        .dispatchEvent(new page.window.MouseEvent("click", { bubbles: true, cancelable: true }));
    assert.equal(confirm.hidden, true);
    assert.deepEqual(page.errors, []);
});

test("mandate documents, document page: a replacement goes straight to the storage", async () => {
    const page = await load("mandate-document");
    const calls = [];
    page.window.HTMLFormElement.prototype.submit = () => assert.fail("the form must not be posted");
    page.window.fetch = (url, options) => {
        calls.push(String(url));
        if (String(url).endsWith("/presign")) {
            return Promise.resolve({
                ok: true, redirected: false,
                json: () => Promise.resolve({ url: "https://s3/put", path: "mandate/301/documents/b.png" })
            });
        }
        return Promise.resolve({ ok: true, redirected: false, json: () => Promise.resolve({ pageId: 2002 }) });
    };

    const form = page.document.querySelectorAll("[data-page-replace] form")[1];
    const input = form.querySelector("input[type=file]");
    Object.defineProperty(input, "files", { value: [pageFile(page, "page.jpg")], configurable: true });
    form.dispatchEvent(new page.window.Event("submit", { bubbles: true, cancelable: true }));

    await eventually(() => calls.some((url) => url.endsWith("/attach")), "the page was not attached");
    assert.ok(calls.some((url) => url.endsWith("/pages/2002/presign")), calls.join(", "));
    assert.ok(calls.some((url) => url.endsWith("/pages/2002/attach")), calls.join(", "));
});

test("mandate documents, document page: a replacement without a file shows its error", async () => {
    const page = await load("mandate-document");

    const form = page.document.querySelectorAll("[data-page-replace] form")[0];
    const submitted = form.dispatchEvent(new page.window.Event("submit", { bubbles: true, cancelable: true }));

    assert.equal(submitted, false, "the browser must not post the form");
    assert.equal(form.querySelector(".field__error").hidden, false);
    assert.equal(form.querySelector("[data-error-text]").textContent,
        "Choisissez la nouvelle page : une photo ou un PDF.");
});

/* ---------- #366: a PDF next to the photos ---------- */

test("mandate documents, add: a PDF is a PDF row and the count says its pages count on send", async () => {
    const page = await load("mandate-document-new");

    choosePages(page, [pageFile(page, "scan.pdf", "application/pdf", 512 * 1024)]);

    const rows = page.document.querySelectorAll("[data-pages-list] li");
    assert.equal(rows.length, 1);
    assert.equal(rows[0].querySelector("[data-page-index]").textContent, "PDF");
    assert.equal(rows[0].querySelector("[data-page-name]").textContent, "scan.pdf");
    assert.equal(page.document.querySelector("[data-pages-count]").textContent,
        "1 fichier choisi · 10 pages au plus. Les pages d'un PDF comptent à l'envoi.");
    assert.deepEqual(page.errors, []);
});

test("mandate documents, add: a PDF over 5 Mo shows the PDF size error and keeps Ajouter inactive", async () => {
    const page = await load("mandate-document-new");

    choosePages(page, [pageFile(page, "scan.pdf", "application/pdf", 6 * 1024 * 1024)]);

    const error = page.document.querySelector("#pages-error");
    assert.equal(error.hidden, false);
    assert.equal(error.querySelector("[data-error-text]").textContent,
        "« scan.pdf » dépasse 5 Mo : choisissez un PDF plus léger, ou envoyez ses pages en photos.");
    assert.equal(page.document.querySelector("[data-new-submit]").getAttribute("aria-disabled"), "true");
});

test("mandate documents, add: an add holding a PDF posts the whole form, never a presign", async () => {
    const page = await load("mandate-document-new");
    const calls = [];
    page.window.HTMLFormElement.prototype.submit = function () {
        calls.push("submit");
    };
    page.window.fetch = (url) => {
        calls.push(String(url));
        return Promise.resolve({ ok: true, redirected: false, json: () => Promise.resolve({}) });
    };

    choosePages(page, [pageFile(page, "scan.pdf", "application/pdf")]);
    page.document.querySelector("#documentType").value = "LAND_TITLE";
    page.document.querySelector("[data-new-form]")
        .dispatchEvent(new page.window.Event("submit", { bubbles: true, cancelable: true }));

    await eventually(() => calls.includes("submit"), "the form was not posted");
    assert.equal(calls.filter((call) => call.endsWith("/presign")).length, 0, "a PDF is never presigned");
    assert.equal(calls.filter((call) => call.endsWith("/create")).length, 0, "the form creates the document");
    assert.equal(page.document.querySelector("[data-new-submit]").textContent, "Envoi du document…");
});

test("mandate documents, document page: a PDF replacement posts the form, never a presign", async () => {
    const page = await load("mandate-document");
    const calls = [];
    page.window.fetch = (url) => {
        calls.push(String(url));
        return Promise.resolve({ ok: true, redirected: false, json: () => Promise.resolve({ pageId: 2002 }) });
    };

    const form = page.document.querySelectorAll("[data-page-replace] form")[1];
    const input = form.querySelector("input[type=file]");
    Object.defineProperty(input, "files", { value: [pageFile(page, "scan.pdf", "application/pdf")], configurable: true });
    // Not cancelled: the browser posts the multipart form, the server rendering the PDF's one page
    const posted = form.dispatchEvent(new page.window.Event("submit", { bubbles: true, cancelable: true }));

    assert.equal(posted, true, "the browser must post the form");
    assert.equal(calls.filter((call) => call.endsWith("/presign")).length, 0, "a PDF is never presigned");
    assert.equal(calls.filter((call) => call.endsWith("/attach")).length, 0, "a PDF is never attached");
    assert.equal(form.querySelector(".field__error").hidden, true);
});

/* ---------- #369: the mandate's caps and a document's kind ---------- */

const MIXED_SOURCES = "Un document contient soit des photos, soit un PDF, jamais les deux : choisissez de nouveau " +
    "les pages.";

function submit(form) {
    return form.dispatchEvent(new form.ownerDocument.defaultView.Event("submit", { bubbles: true, cancelable: true }));
}

test("mandate documents, add: photos and a PDF are refused before sending", async () => {
    const page = await load("mandate-document-new");
    page.window.HTMLFormElement.prototype.submit = () => assert.fail("a mixed selection must not be posted");

    choosePages(page, [pageFile(page, "IMG_2041.jpg"), pageFile(page, "scan.pdf", "application/pdf")]);
    page.document.querySelector("#documentType").value = "LAND_TITLE";

    assert.equal(page.document.querySelector("#pages-error [data-error-text]").textContent, MIXED_SOURCES);
    assert.equal(submit(page.document.querySelector("[data-new-form]")), false, "the browser must not post the form");
});

test("mandate documents, add: two PDFs are refused before sending", async () => {
    const page = await load("mandate-document-new");
    page.window.HTMLFormElement.prototype.submit = () => assert.fail("a mixed selection must not be posted");

    choosePages(page, [
        pageFile(page, "a.pdf", "application/pdf"),
        pageFile(page, "b.pdf", "application/pdf")
    ]);
    page.document.querySelector("#documentType").value = "LAND_TITLE";

    assert.equal(page.document.querySelector("#pages-error [data-error-text]").textContent, MIXED_SOURCES);
    assert.equal(submit(page.document.querySelector("[data-new-form]")), false, "the browser must not post the form");
});

test("mandate documents, add: a stale tab at the cap refuses the 6th document", async () => {
    const page = await load("mandate-document-new");
    page.window.HTMLFormElement.prototype.submit = () => assert.fail("the 6th document must not be posted");

    const form = page.document.querySelector("[data-new-form]");
    form.setAttribute("data-documents-count", "5");
    choosePages(page, [pageFile(page, "IMG_2041.jpg")]);
    page.document.querySelector("#documentType").value = "LAND_TITLE";

    assert.equal(submit(form), false, "the browser must not post the form");
    const limit = page.document.querySelector("[data-new-limit-error]");
    assert.equal(limit.hidden, false);
    assert.match(limit.textContent, /Un mandat peut avoir 5 documents au plus\./);
});

test("mandate documents, document page: a PDF document refuses a photo replacement", async () => {
    const page = await load("mandate-document-pdf");
    const form = page.document.querySelectorAll("[data-page-replace] form")[0];
    const input = form.querySelector("input[type=file]");
    Object.defineProperty(input, "files", { value: [pageFile(page, "page.jpg")], configurable: true });

    assert.equal(submit(form), false, "the browser must not post the form");
    assert.equal(
        form.querySelector("[data-error-text]").textContent,
        "Ce document a été créé à partir d'un PDF : remplacez cette page par un PDF d'une page, pas par une photo."
    );
});

test("mandate documents, document page: an image document refuses a PDF replacement", async () => {
    const page = await load("mandate-document-image");
    const form = page.document.querySelectorAll("[data-page-replace] form")[0];
    const input = form.querySelector("input[type=file]");
    Object.defineProperty(input, "files", {
        value: [pageFile(page, "scan.pdf", "application/pdf")],
        configurable: true
    });

    assert.equal(submit(form), false, "the browser must not post the form");
    assert.equal(
        form.querySelector("[data-error-text]").textContent,
        "Ce document a été créé à partir de photos : remplacez cette page par une photo JPEG, PNG ou WEBP, pas " +
            "par un PDF."
    );
});
