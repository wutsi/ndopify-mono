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
    const dom = new JSDOM(html, { url: "http://localhost:8081/", runScripts: "dangerously", virtualConsole });
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
    "offer-features-residential-rental", "find-property-residential-sale", "find-budget-building-sale"
];

for (const name of ALL_FIXTURES) {
    test(`${name}: the page scripts load without an error`, async () => {
        assert.deepEqual((await load(name)).errors, []);
    });
}

test("offer-property, terms: the price is grouped by thousands as it is typed", async () => {
    const page = await load("offer-terms-sale");
    assert.equal(type(page, "#priceAmount", "50000000"), "50 000 000");
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
    assert.equal(type(page, "#priceAmount", "300000000"), "300 000 000");
    const rate = page.document.querySelector("[data-price-per-unit]");
    assert.equal(rate.hidden, false);
    assert.match(rate.textContent, /\/unité pour 12 unités$/);
});
