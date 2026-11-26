/*
 * Ndopify Partner Central — a mandate's documents (#329, #331), static prototype.
 *   - mandate.html (data-page="mandate"): the *Documents* section, after partner-central.js has rendered the page;
 *   - mandate/document-new.html (data-page="mandate-document-new"): *Ajouter un document*;
 *   - mandate/document.html (data-page="mandate-document"): one document, what the AI read on it, its pages,
 *     Remplacer / Renommer / Supprimer.
 * Every page is written to work without JavaScript (multipart forms, ?delete=1 confirmation): this script only
 * renders the prototype's sample data and enhances (the chosen files listed by name and size, shrink, upload
 * progress, the delete confirmation in place). No page image is ever shown inline: a page opens through "Voir". Server calls are simulated; every simulation is marked "PROTOTYPE".
 * #331: the document page shows what the AI read (MandateDocument.fields, in the server's order, its French label
 * from FIELD_LABELS and its value formatted by kind), the hint while it reads, one instruction per problem code of a
 * FAILED document, and the notice of a piece whose expiresAt is before today; the mandate page's row adds the
 * "Expirée" marker. Confidence, the page paths and any JSON stay out of the HTML.
 * Query switches: mandate.html ?docs=none|error, ?documentOutcome=added|deleted|changed; document-new.html
 * ?errors=missing|file|size|server (?errors=1 = missing); document.html ?delete=1, ?outcome=renamed|replaced&page=N,
 * ?errors=blank|long, ?conflict=1, ?replaceError=type|size|empty&page=N; the reading states come from the samples
 * (?id=&document=, see DOCUMENTS). ?docs=reset (any page) forgets the session.
 */
(function () {
    "use strict";

    var MAX_PAGES = 10;
    var MAX_FILE_BYTES = 5 * 1024 * 1024;
    var MAX_TITLE = 100;
    // As KYC: a page is shrunk to 2000 px on its longest side before it is sent (production: direct-upload.js).
    var MAX_SIDE = 2000;
    var ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
    var KEY = "npc.documents";

    // The French names of #328's MandateDocumentLabels, in DocumentType order (UNKNOWN never shown).
    var TYPES = {
        NATIONAL_ID: "Carte nationale d'identité",
        PASSPORT: "Passeport",
        LAND_TITLE: "Titre foncier",
        CERTIFICATE_OF_OWNERSHIP: "Certificat de propriété",
        SURVEY_PLAN: "Plan de bornage",
        POWER_OF_ATTORNEY: "Procuration",
        LEASE_CONTRACT: "Contrat de bail",
        UTILITY_BILL: "Facture d'eau ou d'électricité",
        TAX_RECEIPT: "Quittance d'impôt",
        PAY_SLIP: "Bulletin de paie",
        BANK_PRE_APPROVAL: "Accord de principe bancaire",
        PROOF_OF_FUNDS: "Justificatif de fonds",
        OTHER: "Document"
    };
    var TYPE_ORDER = Object.keys(TYPES);

    // The types the picker lists first, by mandate type; every other type follows under "Autres types".
    var SUGGESTED = {
        LISTING: ["NATIONAL_ID", "PASSPORT", "LAND_TITLE", "CERTIFICATE_OF_OWNERSHIP", "SURVEY_PLAN",
            "POWER_OF_ATTORNEY"],
        SEARCH: ["NATIONAL_ID", "PASSPORT", "PAY_SLIP", "BANK_PRE_APPROVAL", "PROOF_OF_FUNDS"]
    };

    // DocumentStatus → label and pill. No EXPIRED status: an expired piece stays EXTRACTED, marked "Expirée" (#331).
    var STATUSES = {
        UPLOADED: ["Reçu", ""],
        EXTRACTING: ["En cours d'analyse", "status-tag--info"],
        EXTRACTED: ["Analysé", "status-tag--success"],
        FAILED: ["À corriger", "status-tag--warning"]
    };

    // PROTOTYPE: what the pages need of partner-central.js's MANDATES (party, sale, the customer's name and the
    // status the mandate starts at); the status changed on mandate.html is read from the session (npc.mandates.state).
    var MANDATES = {
        101: { party: "client", sale: false, name: "Aïcha Mbarga" },
        102: { party: "bailleur", sale: false, name: "Paul Ekwalla" },
        103: { party: "client", sale: false, name: "Marie-Claire Ndjock" },
        104: { party: "client", sale: true, name: "Hervé Ngando" },
        201: { party: "client", sale: false, name: "Jean-Pierre Fotso", status: "ASSIGNED" },
        203: { party: "bailleur", sale: false, name: "Emmanuel Tchoua", status: "APPROVED" },
        204: { party: "client", sale: false, name: "Sandrine Eboa", status: "APPROVED" },
        301: { party: "bailleur", sale: true, name: "Rodrigue Essomba", status: "APPROVED" },
        302: { party: "bailleur", sale: true, name: "Carine Mbarga", status: "APPROVED" },
        303: { party: "client", sale: true, name: "Mireille Tchoumi", status: "APPROVED" },
        304: { party: "client", sale: true, name: "Patrice Owona", status: "APPROVED" },
        311: { party: "client", sale: true, name: "Blaise Nkoulou", status: "CANCELLED" }
    };

    /*
     * #331: the French label of a DocumentField key — production's message bundle, by key. A key the bundle doesn't
     * know yet shows FIELD_OTHER with its value, so a server field added later is never lost. `currency` is the
     * document's currency, used by the amounts: it is never a row of its own.
     */
    var FIELD_LABELS = {
        number: "Numéro",
        firstName: "Prénom(s)",
        lastName: "Nom",
        dateOfBirth: "Date de naissance",
        issueDate: "Date de délivrance",
        expiryDate: "Date d'expiration",
        countryCode: "Pays",
        titleNumber: "Numéro du titre",
        owners: "Propriétaires",
        department: "Département",
        arrondissement: "Arrondissement",
        locality: "Localité",
        surface: "Surface",
        parcelReference: "Référence de la parcelle",
        coordinates: "Coordonnées",
        grantor: "Mandant",
        attorney: "Mandataire",
        purpose: "Objet",
        notary: "Notaire",
        holderName: "Titulaire",
        address: "Adresse",
        provider: "Fournisseur",
        title: "Titre",
        summary: "Résumé",
        employerName: "Employeur",
        employeeName: "Salarié",
        payMonth: "Mois de paie",
        grossSalary: "Salaire brut",
        netSalary: "Salaire net",
        bankName: "Banque",
        beneficiaries: "Bénéficiaires",
        amount: "Montant",
        validityDate: "Valable jusqu'au",
        amountKind: "Nature du montant",
        accountHolder: "Titulaire du compte",
        statementDate: "Date du relevé",
        accountLast4: "Compte"
    };
    var FIELD_OTHER = "Autre donnée";

    // The two values of DocumentData.AmountKind, in words.
    var AMOUNT_KINDS = { BALANCE: "Solde", CERTIFIED: "Attesté" };

    // The currencies a tenant of this prototype may read; another ISO code shows as sent (production: the tenant's
    // currencySymbol, from TenantFormatter).
    var CURRENCY_SYMBOLS = { XAF: "FCFA", XOF: "FCFA" };

    var MONTHS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre",
        "novembre", "décembre"];

    /*
     * #331: one instruction per problem code, in the server's own order (DocumentProblemCodes). Each says what the
     * agent can actually do from this page — replace a page, or delete the document and add it again; there is no
     * re-analysis and no field to correct. DOCUMENT_EXPIRED is a warning, never an instruction: the expiry notice is
     * what the agent sees. PROBLEM_OTHER answers an unknown code.
     */
    var PROBLEM_INSTRUCTIONS = {
        DOCUMENT_TYPE_MISMATCH: "Le document ne correspond pas au type choisi. Supprimez-le, puis ajoutez-le avec " +
            "le bon type.",
        DOCUMENT_UNREADABLE: "L'IA n'a pas pu lire le document. Remplacez ses pages par des photos plus nettes, " +
            "droites et sans reflet, ou supprimez-le et ajoutez-le de nouveau.",
        DOCUMENT_PAGE_MISSING: "Il manque une page du document, son verso par exemple. Remplacez la page manquante " +
            "par une photo, ou supprimez le document et ajoutez-le avec toutes ses pages.",
        DOCUMENT_FIELD_LOW_CONFIDENCE: "L'IA n'est pas sûre d'un champ obligatoire. Vérifiez-le sur la pièce : si " +
            "la photo est floue, remplacez la page ; sinon, supprimez le document et ajoutez-le de nouveau.",
        EXPIRY_DATE_UNREADABLE: "La date d'expiration de la pièce n'a pas pu être lue. Remplacez la page où elle " +
            "figure par une photo plus nette, ou supprimez le document et ajoutez-le de nouveau.",
        EXTRACTION_FAILED: "La lecture du document a échoué. Remplacez ses pages, ou supprimez le document et " +
            "ajoutez-le de nouveau pour relancer la lecture."
    };
    var PROBLEM_OTHER = "Un problème a été signalé sur ce document. Supprimez-le et ajoutez-le de nouveau pour " +
        "relancer la lecture.";

    /*
     * PROTOTYPE: GET /v1/mandates/{id}/documents, newest first; a page's "Voir" opens a sample asset
     * (assets/images/doc-sample-*), production's is the page's presigned `url` (MandateDocumentPage.url), never a path.
     * #331's fields (`{ key, value, confidence }`, the type's own order, display-ready values), `problemCodes`
     * (server order) and the two dates come with each sample, so every reading state is one URL away. A page's
     * confidence is never rendered. EXPIRED is not a status: an expired piece is EXTRACTED + DOCUMENT_EXPIRED, or
     * EXTRACTED and expired since (no code), and its expiresAt is what marks it.
     */
    var DOCUMENTS = {
        301: [
            { id: 3103, type: "LAND_TITLE", title: "Titre foncier n° 4521 — Bonapriso", status: "EXTRACTED",
                createdAt: "2026-11-25", version: 1, pages: ["page", "page"],
                issuedAt: "2019-06-14", expiresAt: null, problemCodes: [],
                fields: [
                    { key: "titleNumber", value: "4521", confidence: 0.97 },
                    { key: "owners", value: "Rodrigue Essomba, Carine Mbarga", confidence: 0.93 },
                    { key: "department", value: "Wouri", confidence: 0.95 },
                    { key: "arrondissement", value: "Douala 5e", confidence: 0.91 },
                    { key: "locality", value: "Bonapriso", confidence: 0.94 },
                    { key: "surface", value: "500", confidence: 0.88 },
                    { key: "issueDate", value: "2019-06-14", confidence: 0.96 }
                ] },
            { id: 3105, type: "UTILITY_BILL", title: "Facture d'eau — octobre 2026", status: "EXTRACTING",
                createdAt: "2026-11-24", version: 0, pages: ["page"],
                issuedAt: null, expiresAt: null, problemCodes: [], fields: [] },
            { id: 3102, type: "NATIONAL_ID", title: "Carte nationale d'identité", status: "EXTRACTED",
                createdAt: "2026-11-23", version: 0, pages: ["id-front", "id-back"],
                issuedAt: "2015-01-12", expiresAt: "2025-01-12", problemCodes: ["DOCUMENT_EXPIRED"],
                fields: [
                    { key: "number", value: "1038472659", confidence: 0.96 },
                    { key: "firstName", value: "Rodrigue", confidence: 0.97 },
                    { key: "lastName", value: "Essomba", confidence: 0.98 },
                    { key: "dateOfBirth", value: "1979-03-08", confidence: 0.92 },
                    { key: "issueDate", value: "2015-01-12", confidence: 0.95 },
                    { key: "expiryDate", value: "2025-01-12", confidence: 0.97 },
                    { key: "countryCode", value: "CM", confidence: 0.99 }
                ] },
            { id: 3104, type: "POWER_OF_ATTORNEY", title: "Procuration — vente du lot 12", status: "UPLOADED",
                createdAt: "2026-11-21", version: 0, pages: ["page"],
                issuedAt: null, expiresAt: null, problemCodes: [], fields: [] },
            { id: 3101, type: "SURVEY_PLAN", title: "Plan de bornage", status: "FAILED",
                createdAt: "2026-11-20", version: 2, pages: ["plan"],
                issuedAt: null, expiresAt: null,
                problemCodes: ["DOCUMENT_PAGE_MISSING", "DOCUMENT_FIELD_LOW_CONFIDENCE"], fields: [] }
        ],
        303: [
            { id: 3305, type: "POWER_OF_ATTORNEY", title: "Procuration — achat de l'appartement", status: "EXTRACTED",
                createdAt: "2026-11-25", version: 0, pages: ["page"],
                issuedAt: null, expiresAt: null, problemCodes: [], fields: [] },
            { id: 3304, type: "PROOF_OF_FUNDS", title: "Relevé de compte — Afriland First Bank", status: "EXTRACTED",
                createdAt: "2026-11-24", version: 0, pages: ["page"],
                issuedAt: "2026-11-20", expiresAt: null, problemCodes: [],
                fields: [
                    { key: "bankName", value: "Afriland First Bank", confidence: 0.95 },
                    { key: "accountHolder", value: "Mireille Tchoumi", confidence: 0.93 },
                    { key: "amount", value: "12500000", confidence: 0.9 },
                    { key: "amountKind", value: "BALANCE", confidence: 0.87 },
                    { key: "currency", value: "XAF", confidence: 0.98 },
                    { key: "statementDate", value: "2026-11-20", confidence: 0.94 },
                    { key: "accountLast4", value: "0123", confidence: 0.96 }
                ] },
            { id: 3302, type: "PAY_SLIP", title: "Bulletin de paie — octobre 2026", status: "EXTRACTED",
                createdAt: "2026-11-23", version: 0, pages: ["page"],
                issuedAt: "2026-10-01", expiresAt: null, problemCodes: [],
                fields: [
                    { key: "employerName", value: "Orange Cameroun", confidence: 0.94 },
                    { key: "employeeName", value: "Mireille Tchoumi", confidence: 0.96 },
                    { key: "position", value: "Chargée de clientèle", confidence: 0.71 },
                    { key: "payMonth", value: "2026-10", confidence: 0.98 },
                    { key: "grossSalary", value: "412000", confidence: 0.93 },
                    { key: "netSalary", value: "350000", confidence: 0.95 },
                    { key: "currency", value: "XAF", confidence: 0.97 }
                ] },
            { id: 3301, type: "NATIONAL_ID", title: "Carte nationale d'identité", status: "EXTRACTED",
                createdAt: "2026-11-21", version: 0, pages: ["id-front", "id-back"],
                issuedAt: "2022-05-20", expiresAt: "2032-05-20", problemCodes: [],
                fields: [
                    { key: "number", value: "1094738215", confidence: 0.95 },
                    { key: "firstName", value: "Mireille", confidence: 0.97 },
                    { key: "lastName", value: "Tchoumi", confidence: 0.98 },
                    { key: "dateOfBirth", value: "1991-11-02", confidence: 0.94 },
                    { key: "issueDate", value: "2022-05-20", confidence: 0.96 },
                    { key: "expiryDate", value: "2032-05-20", confidence: 0.96 },
                    { key: "countryCode", value: "CM", confidence: 0.99 }
                ] },
            { id: 3303, type: "BANK_PRE_APPROVAL", title: "Accord de principe — prêt immobilier",
                status: "EXTRACTED", createdAt: "2026-11-10", version: 0, pages: ["page", "page"],
                issuedAt: "2026-08-20", expiresAt: "2026-11-20", problemCodes: [],
                fields: [
                    { key: "bankName", value: "Afriland First Bank", confidence: 0.95 },
                    { key: "beneficiaries", value: "Mireille Tchoumi, Patrice Owona", confidence: 0.92 },
                    { key: "amount", value: "45000000", confidence: 0.94 },
                    { key: "currency", value: "XAF", confidence: 0.98 },
                    { key: "issueDate", value: "2026-08-20", confidence: 0.93 },
                    { key: "validityDate", value: "2026-11-20", confidence: 0.95 }
                ] }
        ],
        311: [
            { id: 3113, type: "PROOF_OF_FUNDS", title: "Attestation de solde — Afriland First Bank",
                status: "EXTRACTED", createdAt: "2026-11-12", version: 0, pages: ["page"],
                issuedAt: "2026-11-05", expiresAt: null, problemCodes: [],
                fields: [
                    { key: "bankName", value: "Afriland First Bank", confidence: 0.95 },
                    { key: "accountHolder", value: "Blaise Nkoulou", confidence: 0.96 },
                    { key: "amount", value: "8000000", confidence: 0.92 },
                    { key: "amountKind", value: "CERTIFIED", confidence: 0.89 },
                    { key: "currency", value: "XAF", confidence: 0.98 },
                    { key: "statementDate", value: "2026-11-05", confidence: 0.94 },
                    { key: "accountLast4", value: "4471", confidence: 0.97 }
                ] },
            { id: 3112, type: "BANK_PRE_APPROVAL", title: "Accord de principe — prêt immobilier", status: "EXTRACTED",
                createdAt: "2026-11-10", version: 0, pages: ["page", "page"],
                issuedAt: "2026-03-02", expiresAt: "2026-09-02", problemCodes: ["DOCUMENT_EXPIRED"],
                fields: [
                    { key: "bankName", value: "BICEC", confidence: 0.94 },
                    { key: "beneficiaries", value: "Blaise Nkoulou", confidence: 0.91 },
                    { key: "amount", value: "35000000", confidence: 0.93 },
                    { key: "currency", value: "XAF", confidence: 0.98 },
                    { key: "issueDate", value: "2026-03-02", confidence: 0.92 },
                    { key: "validityDate", value: "2026-09-02", confidence: 0.95 }
                ] },
            { id: 3111, type: "PASSPORT", title: "Passeport", status: "FAILED",
                createdAt: "2026-11-05", version: 0, pages: ["id-front"],
                issuedAt: null, expiresAt: null, problemCodes: ["DOCUMENT_BACK_MISSING"], fields: [] }
        ]
    };

    var SAMPLE_IMAGES = {
        "id-front": "doc-sample-id-front.svg",
        "id-back": "doc-sample-id-back.svg",
        page: "doc-sample-page.svg",
        plan: "doc-sample-plan.svg"
    };

    /* ---------- Helpers ---------- */

    function $(selector, root) {
        return (root || document).querySelector(selector);
    }

    function $all(selector, root) {
        return Array.prototype.slice.call((root || document).querySelectorAll(selector));
    }

    var params = new URLSearchParams(window.location.search);

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

    function readJson(key, fallback) {
        try {
            return JSON.parse(store.get(key)) || fallback;
        } catch (e) {
            return fallback;
        }
    }

    if (params.get("docs") === "reset") store.remove(KEY);

    function mandateOf(id) {
        return MANDATES[id] || { party: "client", sale: false, name: "", status: "ASSIGNED" };
    }

    // The mandate's status: the one changed on mandate.html (the session), else the one it starts at.
    function statusOf(id) {
        var states = readJson("npc.mandates.state", {});
        if (states[id] && states[id].status) return states[id].status;
        return mandateOf(id).status || "ASSIGNED";
    }

    // Anti-bypass, as partner-central.js's ownsMandate: an offer's mandate opens only once accepted.
    function owns(id) {
        var mandate = MANDATES[id];
        if (!mandate) return false;
        if (mandate.status) return true;
        var accepted = readJson("npc.mandates.accepted", []);
        return accepted.indexOf(id) !== -1 || accepted.indexOf(String(id)) !== -1 ||
            accepted.indexOf(Number(id)) !== -1;
    }

    function isClosed(status) {
        return status === "CLOSED" || status === "CANCELLED";
    }

    function kindOf(id) {
        return mandateOf(id).party === "bailleur" ? "LISTING" : "SEARCH";
    }

    // "Vendeur" / "Bailleur" / "Acheteur" / "Client", as the contact block says it.
    function partyLabel(id) {
        var mandate = mandateOf(id);
        if (mandate.party === "bailleur") return mandate.sale ? "Vendeur" : "Bailleur";
        return mandate.sale ? "Acheteur" : "Client";
    }

    function documentsOf(id) {
        var saved = readJson(KEY, {});
        if (saved[id]) return saved[id];
        return JSON.parse(JSON.stringify(DOCUMENTS[id] || []));
    }

    function saveDocuments(id, documents) {
        var saved = readJson(KEY, {});
        saved[id] = documents;
        store.set(KEY, JSON.stringify(saved));
    }

    function findDocument(id, documentId) {
        var found = null;
        documentsOf(id).forEach(function (doc) {
            if (String(doc.id) === String(documentId)) found = doc;
        });
        return found;
    }

    // The tenant's dateFormat, with the year: dd/MM/yyyy here (production: TenantFormatter).
    function formatDate(iso) {
        var parts = String(iso).split("-");
        return parts[2] + "/" + parts[1] + "/" + parts[0];
    }

    function todayIso() {
        var d = new Date();
        return d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" +
            String(d.getDate()).padStart(2, "0");
    }

    /* ---------- #331: what the AI read ---------- */

    // An expiry before today, on the tenant's clock: `expiresAt` is a LocalDate on both sides, so ISO strings compare
    // as dates. A document without one never expires.
    function isExpired(doc) {
        return !!doc.expiresAt && String(doc.expiresAt) < todayIso();
    }

    function fieldOf(fields, key) {
        var value = null;
        (fields || []).forEach(function (field) {
            if (field && field.key === key) value = field.value;
        });
        return value;
    }

    // The document's own currency for its amounts, else the tenant's (the body's data-currency).
    function currencySymbolOf(fields) {
        var code = fieldOf(fields, "currency");
        if (!code) return document.body.getAttribute("data-currency") || "FCFA";
        return CURRENCY_SYMBOLS[code] || code;
    }

    // A displayed amount, short and in the tenant's French form (#292), with the document's currency: "350K FCFA",
    // "45M FCFA", "12,5M FCFA". K keeps one decimal, M and G two, half-up, the unit bumped after rounding.
    function formatMoney(value, symbol) {
        var n = Math.abs(Number(value) || 0);
        function rounded(amount, decimals) {
            var factor = Math.pow(10, decimals);
            return Math.round(amount * factor) / factor;
        }
        function text(amount, decimals) {
            return String(amount.toFixed(decimals)).replace(/\.?0+$/, "").replace(".", ",");
        }
        var short;
        if (n < 1000) {
            short = String(n);
        } else {
            var k = rounded(n / 1000, 1);
            if (k < 1000) {
                short = text(k, 1) + "K";
            } else {
                var m = rounded(n / 1000000, 2);
                short = m < 1000 ? text(m, 2) + "M" : text(rounded(n / 1000000000, 2), 2) + "G";
            }
        }
        return short + "\u00A0" + symbol;
    }

    // A count grouped by thousands, as the app writes a surface: "500", "1 200".
    function formatCount(value) {
        return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, "\u00A0");
    }

    // "2026-10" → "Octobre 2026" (the tenant's locale in production).
    function formatMonth(value) {
        var parts = String(value).split("-");
        var month = MONTHS[Number(parts[1]) - 1];
        return month ? month.charAt(0).toUpperCase() + month.slice(1) + " " + parts[2] : value;
    }

    // One field's value, formatted by its kind. Anything it can't read shows as sent: never an error.
    function readingValue(key, value, fields) {
        var text = String(value);
        if (key === "issueDate" || key === "expiryDate" || key === "validityDate" || key === "statementDate" ||
                key === "dateOfBirth") {
            return /^\d{4}-\d{2}-\d{2}$/.test(text) ? formatDate(text) : text;
        }
        if (key === "payMonth") return /^\d{4}-\d{2}$/.test(text) ? formatMonth(text) : text;
        if (key === "amount" || key === "grossSalary" || key === "netSalary") {
            return /^\d+(\.\d+)?$/.test(text) ? formatMoney(text, currencySymbolOf(fields)) : text;
        }
        if (key === "accountLast4") return /^\d{4}$/.test(text) ? "•••• " + text : text;
        if (key === "surface") return /^\d+$/.test(text) ? formatCount(text) + "\u00A0m²" : text;
        if (key === "amountKind") return AMOUNT_KINDS[text] || text;
        return text;
    }

    // The fields worth a row, in the server's order: `currency` isn't one, and a null or blank value has no row.
    function readingFields(doc) {
        return (doc.fields || []).filter(function (field) {
            if (!field || field.key === "currency") return false;
            return field.value !== null && field.value !== undefined && String(field.value).trim() !== "";
        });
    }

    // A chosen file's size, French style: "850 Ko", "1,8 Mo" (production: the same, read from the File).
    function formatSize(bytes) {
        if (bytes < 1024 * 1024) return Math.max(1, Math.round(bytes / 1024)) + " Ko";
        return (Math.round(bytes / (1024 * 1024) * 10) / 10).toString().replace(".", ",") + " Mo";
    }

    function pageCount(n) {
        return n + (n > 1 ? " pages" : " page");
    }

    function imagesBase() {
        return document.body.getAttribute("data-assets") || "assets/images/";
    }

    function pageSrc(page) {
        return SAMPLE_IMAGES[page] ? imagesBase() + SAMPLE_IMAGES[page] : page;
    }

    function setStatusTag(el, status) {
        var entry = STATUSES[status] || STATUSES.UPLOADED;
        el.className = "status-tag" + (entry[1] ? " " + entry[1] : "");
        el.innerHTML = '<span class="visually-hidden">Statut : </span>';
        el.appendChild(document.createTextNode(entry[0]));
    }

    function showError(el, text) {
        if (!el) return;
        $("[data-error-text]", el).textContent = text;
        el.hidden = !text;
    }

    function setInvalid(input, invalid) {
        if (!input) return;
        if (invalid) input.setAttribute("aria-invalid", "true");
        else input.removeAttribute("aria-invalid");
    }

    // The URL without the one-shot switches, as the other pages do after showing a flash (a reload won't repeat it).
    function dropParams(names) {
        var current = new URLSearchParams(window.location.search);
        names.forEach(function (name) {
            current.delete(name);
        });
        var query = current.toString();
        history.replaceState(null, "", window.location.pathname + (query ? "?" + query : "") + window.location.hash);
    }

    // Shrinks an image to `max` px on its longest side (never enlarged), as KYC does; JPEG.
    function shrink(file, max, quality, callback) {
        var reader = new FileReader();
        reader.onload = function () {
            var img = new Image();
            img.onload = function () {
                var scale = Math.min(1, max / Math.max(img.width, img.height));
                var canvas = document.createElement("canvas");
                canvas.width = Math.round(img.width * scale);
                canvas.height = Math.round(img.height * scale);
                canvas.getContext("2d").drawImage(img, 0, 0, canvas.width, canvas.height);
                callback(canvas.toDataURL("image/jpeg", quality));
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

    // The check the BFF repeats server-side (Tika, 5 Mo): the error of one file, or "".
    function fileError(file) {
        if (ACCEPTED_TYPES.indexOf(file.type) === -1) {
            return "« " + file.name + " » n'est pas une image : choisissez une photo JPEG, PNG ou WEBP.";
        }
        if (file.size > MAX_FILE_BYTES) {
            return "« " + file.name + " » dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère.";
        }
        return "";
    }

    /* ---------- mandate.html › Documents ---------- */

    function initSection() {
        var section = $("[data-documents]");
        if (!section) return;
        var id = params.get("id") || "301";
        var list = $("[data-documents-list]", section);
        var template = $("[data-documents-template]", section);
        var add = $("[data-documents-add]", section);
        var flash = $("[data-documents-flash]", section);

        // The page's own rendering says whether the mandate is closed or cancelled (the cancel form may change it).
        function closed() {
            return $all('[data-panel="CLOSED"], [data-panel="CANCELLED"]').some(function (panel) {
                return !panel.hidden;
            });
        }

        function render() {
            var mandate = mandateOf(id);
            var listing = kindOf(id) === "LISTING";
            var party = partyLabel(id).toLowerCase();
            var readOnly = closed();
            var unavailable = params.get("docs") === "error";
            var documents = params.get("docs") === "none" ? [] : documentsOf(id);

            var whose = listing ? "du " + party : mandate.sale ? "de l'acheteur" : "du client";
            $("[data-documents-intro]", section).textContent = "Les pièces d'identité et " +
                (listing ? "de propriété " : "de financement ") + whose +
                ". Privées : jamais publiées ni montrées à un autre agent.";

            add.hidden = readOnly;
            add.href = "mandate/document-new.html?id=" + id;
            $("[data-documents-readonly]", section).hidden = !readOnly;
            $("[data-documents-readonly-text]", section).textContent =
                (closedPanel() === "CLOSED" ? "Mandat fermé" : "Mandat annulé") +
                " : les documents restent consultables, sans modification.";

            $("[data-documents-unavailable]", section).hidden = !unavailable;
            list.hidden = unavailable || documents.length === 0;
            var empty = $("[data-documents-empty]", section);
            empty.hidden = unavailable || documents.length > 0;
            $("[data-documents-empty-hint]", empty).textContent = readOnly ? "" : emptyHint(id);
            $("[data-documents-empty-hint]", empty).hidden = readOnly;

            list.innerHTML = "";
            documents.forEach(function (doc) {
                var row = template.content.firstElementChild.cloneNode(true);
                var link = $("a", row);
                link.href = "mandate/document.html?id=" + id + "&document=" + doc.id;
                $("[data-doc-type]", row).textContent = TYPES[doc.type] || TYPES.OTHER;
                setStatusTag($("[data-doc-status]", row), doc.status);
                // #331: the statuses don't say a piece expired, so the row does (the document page has the notice).
                $("[data-doc-expired]", row).hidden = !isExpired(doc);
                var title = $("[data-doc-title]", row);
                title.textContent = doc.title;
                title.hidden = !doc.title || doc.title === TYPES[doc.type];
                $("[data-doc-meta]", row).textContent = "Ajouté le " + formatDate(doc.createdAt) + " · " +
                    pageCount(doc.pages.length);
                list.appendChild(row);
            });
        }

        function closedPanel() {
            var panel = $all('[data-panel="CLOSED"], [data-panel="CANCELLED"]').filter(function (p) {
                return !p.hidden;
            })[0];
            return panel ? panel.getAttribute("data-panel") : "";
        }

        function emptyHint(mandateId) {
            var mandate = mandateOf(mandateId);
            if (kindOf(mandateId) === "LISTING") {
                return mandate.sale
                    ? "Ajoutez la pièce d'identité du vendeur et ses documents de propriété : titre foncier, " +
                        "certificat de propriété ou plan de bornage (et sa procuration s'il vend pour un autre)."
                    : "Ajoutez la pièce d'identité du bailleur et un document de propriété du bien : titre foncier " +
                        "ou certificat de propriété (et sa procuration s'il loue pour un autre).";
            }
            return mandate.sale
                ? "Ajoutez la pièce d'identité de l'acheteur et un justificatif de son financement : bulletin de " +
                    "paie, accord de principe bancaire ou justificatif de fonds."
                : "Ajoutez la pièce d'identité du client et un justificatif de revenus, comme un bulletin de paie.";
        }

        render();

        // The cancel form changes the status in place: the section follows it.
        var observer = new MutationObserver(render);
        $all('[data-panel="CLOSED"], [data-panel="CANCELLED"]').forEach(function (panel) {
            observer.observe(panel, { attributes: true, attributeFilter: ["hidden"] });
        });

        // The outcome of an add or a delete (production: the documentOutcome flash attribute).
        var outcome = params.get("documentOutcome");
        var outcomeTitle = params.get("documentTitle") || "";
        var texts = {
            added: "Document ajouté" + (outcomeTitle ? " : " + outcomeTitle : "") + ".",
            deleted: "Document supprimé" + (outcomeTitle ? " : " + outcomeTitle : "") + ".",
            changed: "Ce document n'est plus disponible : il a été supprimé, ou le mandat a changé entre-temps."
        };
        if (texts[outcome]) {
            flash.classList.toggle("alert--success", outcome !== "changed");
            flash.classList.toggle("alert--warning", outcome === "changed");
            $("[data-documents-flash-text]", flash).textContent = texts[outcome];
            flash.hidden = false;
            flash.focus();
            dropParams(["documentOutcome", "documentTitle"]);
        }
    }

    /* ---------- mandate/document-new.html ---------- */

    function fillTypePicker(select, id, selected) {
        var suggested = SUGGESTED[kindOf(id)];
        var first = $('optgroup[data-type-group="suggested"]', select);
        var others = $('optgroup[data-type-group="others"]', select);
        first.innerHTML = "";
        others.innerHTML = "";
        TYPE_ORDER.forEach(function (type) {
            var option = document.createElement("option");
            option.value = type;
            option.textContent = TYPES[type];
            option.selected = type === selected;
            (suggested.indexOf(type) !== -1 ? first : others).appendChild(option);
        });
        // The suggested types in the order of the story, not the enum's.
        suggested.forEach(function (type) {
            first.appendChild($('option[value="' + type + '"]', first));
        });
        // "Document" (OTHER) closes the list.
        var other = $('option[value="OTHER"]', others);
        if (other) others.appendChild(other);
    }

    function initNewDocument() {
        var id = params.get("id") || "301";
        var back = "../mandate.html?id=" + id + "#documents-title";
        $all("[data-back-link]").forEach(function (link) {
            link.href = back;
        });

        if (!owns(id) || isClosed(statusOf(id))) {
            var locked = $("[data-new-locked]");
            locked.hidden = false;
            $("[data-new]").hidden = true;
            if (isClosed(statusOf(id))) {
                $("[data-new-locked-title]").textContent = "Ce mandat est " +
                    (statusOf(id) === "CLOSED" ? "fermé" : "annulé");
                $("[data-new-locked-text]").textContent = "Ses documents restent consultables depuis le mandat, " +
                    "mais vous ne pouvez plus en ajouter.";
            }
            return;
        }
        $("[data-new-meta]").textContent = "Mandat n° " + id + " · " + partyLabel(id) + " : " + mandateOf(id).name;

        var form = $("[data-new-form]");
        var select = $("#documentType");
        var title = $("#documentTitle");
        var counter = $("[data-title-counter]");
        var typeError = $("#documentType-error");
        var titleError = $("#documentTitle-error");
        var pagesError = $("#pages-error");
        var listing = kindOf(id) === "LISTING";

        fillTypePicker(select, id, "");

        function placeholder() {
            title.placeholder = select.value ? TYPES[select.value] : "Le nom du type";
        }

        function count() {
            counter.textContent = title.value.length + " / " + MAX_TITLE;
        }

        select.addEventListener("change", function () {
            placeholder();
            showError(typeError, "");
            setInvalid(select, false);
        });
        title.addEventListener("input", count);

        // The error states of the form as the server renders them again: the type and title kept, pages to choose
        // again (a browser never re-fills a file input).
        var errors = params.get("errors");
        if (errors === "1") errors = "missing";
        var keptType = listing ? "LAND_TITLE" : "PAY_SLIP";
        var keptTitle = listing ? "Titre foncier lot 12" : "Bulletin de paie — octobre 2026";
        if (errors === "missing") {
            title.value = listing ? "Lot 12" : "Octobre 2026";
            showError(typeError, "Choisissez le type du document.");
            setInvalid(select, true);
            showError(pagesError, "Ajoutez au moins une page : une photo ou une image par page.");
        } else if (errors === "file" || errors === "size" || errors === "server") {
            select.value = keptType;
            title.value = keptTitle;
            if (errors === "file") {
                showError(pagesError, "« titre-foncier.pdf » n'est pas une image : choisissez une photo JPEG, " +
                    "PNG ou WEBP. Choisissez de nouveau les pages.");
            } else if (errors === "size") {
                showError(pagesError, "« IMG_2041.jpg » dépasse 5 Mo : reprenez la photo, ou choisissez une " +
                    "image plus légère. Choisissez de nouveau les pages.");
            } else {
                $("[data-new-server-error]").hidden = false;
            }
        }
        placeholder();
        count();

        initPagePicker(form, function (pages) {
            // PROTOTYPE: presign → PUT → POST /mandates/{id}/documents per page; the multipart post without JS.
            var type = select.value;
            var documents = documentsOf(id);
            var doc = {
                id: Date.now(),
                type: type,
                // Server rule: a blank title is the type's French name.
                title: title.value.trim() || TYPES[type],
                status: "UPLOADED",
                createdAt: todayIso(),
                version: 0,
                pages: pages
            };
            documents.unshift(doc);
            saveDocuments(id, documents);
            window.location.href = "../mandate.html?id=" + id + "&documentOutcome=added&documentTitle=" +
                encodeURIComponent(doc.title) + "#documents-title";
        }, function () {
            var ok = true;
            if (!select.value) {
                showError(typeError, "Choisissez le type du document.");
                setInvalid(select, true);
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
            return ok;
        });

        // As the server's error page: the first field in error takes the focus.
        var serverError = $("[data-new-server-error]");
        var firstError = $('[aria-invalid="true"]', form) || (!pagesError.hidden && $("[data-page-input]", form)) ||
            (!serverError.hidden && serverError);
        if (firstError) firstError.focus();
    }

    // The pages chosen, with JS: the native input is replaced by "Prendre une photo" / "Choisir des images" and the
    // ordered thumbnails (each removable); on submit they're shrunk and sent one by one, with the progress.
    function initPagePicker(form, onSent, validateOthers) {
        var native = $("[data-pages-native]", form);
        var enhanced = $("[data-pages-enhanced]", form);
        var list = $("[data-pages-list]", form);
        var template = $("[data-page-template]", form);
        var countLine = $("[data-pages-count]", form);
        var live = $("[data-pages-live]", form);
        var error = $("#pages-error");
        var progress = $("[data-pages-progress]", form);
        var progressText = $("[data-pages-progress-text]", form);
        var progressBar = $("[data-pages-progress-bar]", form);
        var submit = $("[data-new-submit]", form);
        var inputs = $all("[data-page-input]", form);
        var chosen = [];
        var sending = false;

        native.hidden = true;
        $("#pages").disabled = true;
        enhanced.hidden = false;

        function render() {
            list.innerHTML = "";
            chosen.forEach(function (item, i) {
                var li = template.content.firstElementChild.cloneNode(true);
                $("[data-page-index]", li).textContent = "Page " + (i + 1);
                // The name and size, read in the browser from the File: no thumbnail.
                $("[data-page-name]", li).textContent = item.file.name;
                $("[data-page-size]", li).textContent = formatSize(item.file.size);
                var remove = $("[data-page-remove]", li);
                remove.setAttribute("aria-label", "Retirer la page " + (i + 1) + " (" + item.file.name + ")");
                remove.hidden = sending;
                remove.addEventListener("click", function () {
                    chosen.splice(i, 1);
                    live.textContent = "Page " + (i + 1) + " retirée.";
                    render();
                    inputs[0].focus();
                });
                if (sending && i >= item.sentIndex) li.classList.add("doc-file--pending");
                list.appendChild(li);
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
        }

        inputs.forEach(function (input) {
            input.addEventListener("change", function () {
                var refused = [];
                Array.prototype.slice.call(input.files).forEach(function (file) {
                    var problem = fileError(file);
                    if (problem) {
                        refused.push(problem);
                    } else if (chosen.length >= MAX_PAGES) {
                        refused.push("10 pages au plus par document : « " + file.name + " » n'a pas été ajoutée.");
                    } else {
                        chosen.push({ file: file });
                    }
                });
                input.value = "";
                showError(error, refused.join(" "));
                live.textContent = pageCount(chosen.length) + " choisie" + (chosen.length > 1 ? "s" : "") + ".";
                render();
            });
        });

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            if (sending) return;
            var ok = validateOthers();
            if (!chosen.length) {
                showError(error, "Ajoutez au moins une page : une photo ou une image par page.");
                ok = false;
            }
            if (!ok) {
                ($('[aria-invalid="true"]', form) || inputs[0]).focus();
                return;
            }
            showError(error, "");
            sending = true;
            submit.setAttribute("aria-disabled", "true");
            progress.hidden = false;
            progressText.textContent = "Préparation des pages…";
            progressBar.max = chosen.length;
            progressBar.value = 0;
            render();

            var pages = [];
            (function next(i) {
                if (i >= chosen.length) {
                    progressText.textContent = "Document enregistré…";
                    onSent(pages);
                    return;
                }
                progressText.textContent = "Envoi de la page " + (i + 1) + " sur " + chosen.length + "…";
                chosen.forEach(function (item) {
                    item.sentIndex = i;
                });
                render();
                // PROTOTYPE: shrunk to 2000 px and sent straight to the storage; the file isn't kept, "Voir" opens a
                // sample asset.
                shrink(chosen[i].file, MAX_SIDE, 0.85, function () {
                    pages.push("page");
                    setTimeout(function () {
                        progressBar.value = i + 1;
                        next(i + 1);
                    }, 500);
                });
            })(0);
        });

        render();
    }

    /* ---------- mandate/document.html ---------- */

    function initDocument() {
        var id = params.get("id") || "301";
        var documentId = params.get("document") || "3103";
        var back = "../mandate.html?id=" + id + "#documents-title";
        $all("[data-back-link]").forEach(function (link) {
            link.href = back;
        });

        var doc = owns(id) ? findDocument(id, documentId) : null;
        if (!doc) {
            // Production: back to the mandate page with the "changed" outcome (a 404 or 403 never shows an error page).
            $("[data-document-locked]").hidden = false;
            $("[data-document]").hidden = true;
            return;
        }
        var readOnly = isClosed(statusOf(id));
        var typeName = TYPES[doc.type] || TYPES.OTHER;
        var self = "document.html?id=" + id + "&document=" + doc.id;

        $("[data-document-heading]").textContent = doc.title || typeName;
        setStatusTag($("[data-document-status]"), doc.status);
        // One upload date for the whole document; no size, no image.
        $("[data-document-meta]").textContent = (doc.title && doc.title !== typeName ? typeName + " · " : "") +
            "Ajouté le " + formatDate(doc.createdAt);
        $("[data-document-mandate]").textContent = "Mandat n° " + id + " · " + partyLabel(id) + " : " +
            mandateOf(id).name;
        $("[data-document-readonly]").hidden = !readOnly;
        $("[data-document-readonly-text]").textContent = (statusOf(id) === "CLOSED" ? "Mandat fermé" : "Mandat annulé") +
            " : ce document reste consultable, sans modification.";
        $("[data-document-manage]").hidden = readOnly;

        renderReading(id, doc, readOnly);
        renderPages(id, doc, readOnly, self);
        if (!readOnly) {
            initRename(id, doc, self);
            initDelete(id, doc, self);
        }

        var flash = $("[data-document-flash]");
        var outcome = params.get("outcome");
        var page = params.get("page");
        var texts = {
            renamed: "Titre enregistré.",
            replaced: "Page " + page + " remplacée : le document est de nouveau « Reçu »."
        };
        if (texts[outcome]) {
            $("[data-document-flash-text]").textContent = texts[outcome];
            flash.hidden = false;
            flash.focus();
            dropParams(["outcome", "page"]);
        }
    }

    /*
     * #331: what the AI read on the document, above its pages — the expiry notice, the hint while it reads, one
     * instruction per problem code, then the field rows (no confidence, no JSON). Production renders all of it
     * server-side, so the page works without JavaScript; here the section's blocks are switched and its rows built
     * from the sample. A CLOSED / CANCELLED mandate keeps the whole section readable, with no action of its own.
     */
    function renderReading(id, doc, readOnly) {
        var section = $("[data-document-reading]");
        if (!section) return;
        var fields = readingFields(doc);
        var codes = doc.problemCodes || [];
        var waiting = doc.status === "UPLOADED" || doc.status === "EXTRACTING";
        var failed = doc.status === "FAILED";
        var hasFields = doc.status === "EXTRACTED" && fields.length > 0;

        // The notice: expiresAt before today. DOCUMENT_EXPIRED (the server's warning at reading time) tells a piece
        // already expired when the AI read it from one that has expired since.
        var expired = isExpired(doc);
        $("[data-reading-expired]", section).hidden = !expired;
        if (expired) {
            $("[data-expired-title]", section).textContent = "Pièce expirée le " + formatDate(doc.expiresAt);
            $("[data-expired-when]", section).textContent = codes.indexOf("DOCUMENT_EXPIRED") !== -1
                ? "Elle était déjà expirée quand l'IA l'a lue."
                : "Elle a expiré depuis sa lecture.";
            // Read-only: no action of this story's own, and none of replacing on a closed or cancelled mandate.
            $("[data-expired-action]", section).textContent = readOnly
                ? (statusOf(id) === "CLOSED" ? "Mandat fermé" : "Mandat annulé") +
                    " : cette pièce reste consultable telle quelle."
                : "Ajoutez une pièce en cours de validité : remplacez ses pages par celles de la nouvelle pièce, " +
                    "ou supprimez-la et ajoutez la nouvelle.";
        }

        var waitingBox = $("[data-reading-waiting]", section);
        waitingBox.hidden = !waiting;
        if (waiting) {
            $("[data-waiting-text]", section).textContent = doc.status === "UPLOADED"
                ? "Le document est reçu : sa lecture va commencer. Rien à faire, elle se lance d'elle-même ; ses " +
                    "données apparaîtront ici."
                : "L'IA lit le document. Ses données apparaîtront ici dès qu'elle aura terminé : rien à faire.";
        }

        $("[data-reading-empty]", section).hidden = !(doc.status === "EXTRACTED" && !fields.length);

        var problems = $("[data-reading-problems]", section);
        problems.hidden = !failed;
        if (failed) {
            var list = $("[data-reading-problem-list]", section);
            list.innerHTML = "";
            codes.forEach(function (code) {
                var li = document.createElement("li");
                li.textContent = PROBLEM_INSTRUCTIONS[code] || PROBLEM_OTHER;
                list.appendChild(li);
            });
        }

        $("[data-reading-intro]", section).hidden = !hasFields;
        var data = $("[data-reading-fields]", section);
        data.hidden = !hasFields;
        if (hasFields) {
            var template = $("[data-reading-field-template]", section);
            data.innerHTML = "";
            fields.forEach(function (field) {
                var row = template.content.firstElementChild.cloneNode(true);
                $("[data-field-label]", row).textContent = FIELD_LABELS[field.key] || FIELD_OTHER;
                $("[data-field-value]", row).textContent = readingValue(field.key, field.value, doc.fields);
                data.appendChild(row);
            });
        }
    }

    function renderPages(id, doc, readOnly, self) {
        var list = $("[data-document-pages]");
        var template = $("[data-document-page-template]");
        var replaceError = params.get("replaceError");
        var errorPage = params.get("page");
        list.innerHTML = "";
        doc.pages.forEach(function (page, i) {
            var n = i + 1;
            var li = template.content.firstElementChild.cloneNode(true);
            // Nothing is shown inline: "Voir" opens the page in a new tab (production: its presigned url; here, a
            // sample asset).
            var view = $("[data-page-link]", li);
            view.href = pageSrc(page);
            view.innerHTML = "Voir<span class=\"visually-hidden\"> la page " + n + " (nouvel onglet)</span>";
            $("[data-page-caption]", li).textContent = "Page " + n;
            var replace = $("[data-page-replace]", li);
            replace.hidden = readOnly;
            $("[data-page-replace-summary]", li).innerHTML = "Remplacer<span class=\"visually-hidden\"> la page " +
                n + "</span>";
            var input = $("input[type=file]", li);
            var inputId = "page-" + n + "-file";
            input.id = inputId;
            $("label", li).setAttribute("for", inputId);
            $("label", li).textContent = "Nouvelle image de la page " + n;
            var error = $("[data-page-error]", li);
            error.id = inputId + "-error";
            input.setAttribute("aria-describedby", inputId + "-hint " + inputId + "-error");
            $("[data-page-hint]", li).id = inputId + "-hint";
            // Production: POST /mandates/{id}/documents/{documentId}/pages/{pageId} (multipart), a form per page.
            $("form", li).setAttribute("action", "/mandates/" + id + "/documents/" + doc.id + "/pages/" + (i + 1));

            if (replaceError && String(n) === errorPage) {
                replace.open = true;
                var messages = {
                    type: "Ce fichier n'est pas une image : choisissez une photo JPEG, PNG ou WEBP.",
                    size: "Cette image dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère.",
                    empty: "Choisissez la nouvelle image de la page."
                };
                showError(error, messages[replaceError] || messages.empty);
                setInvalid(input, true);
            }

            $("form", li).addEventListener("submit", function (event) {
                event.preventDefault();
                var file = input.files[0];
                var problem = !file ? "Choisissez la nouvelle image de la page." : fileError(file);
                showError(error, problem);
                setInvalid(input, !!problem);
                if (problem) {
                    input.focus();
                    return;
                }
                // PROTOTYPE: shrunk, presign → PUT → POST …/pages/{pageId} (attach that replaces: attachTimeout).
                var button = $("[data-page-submit]", li);
                button.setAttribute("aria-disabled", "true");
                button.textContent = "Envoi de la page " + n + "…";
                shrink(file, MAX_SIDE, 0.85, function () {
                    setTimeout(function () {
                        var documents = documentsOf(id);
                        documents.forEach(function (d) {
                            if (String(d.id) === String(doc.id)) {
                                // PROTOTYPE: the file isn't kept; "Voir" opens a sample asset.
                                d.pages[i] = "page";
                                // The server resets its analysis: the reading's data, dates and problems leave with it
                                // (#331), and the document reads "Reçu" until it is read again.
                                d.status = "UPLOADED";
                                d.fields = [];
                                d.problemCodes = [];
                                d.issuedAt = null;
                                d.expiresAt = null;
                            }
                        });
                        saveDocuments(id, documents);
                        window.location.href = self + "&outcome=replaced&page=" + n;
                    }, 700);
                });
            });
            list.appendChild(li);
        });
        if (replaceError) {
            var invalid = $('[aria-invalid="true"]', list);
            if (invalid) invalid.focus();
        }
    }

    function initRename(id, doc, self) {
        var form = $("[data-rename-form]");
        var box = $("[data-rename]");
        var input = $("#documentTitle");
        var error = $("#documentTitle-error");
        var counter = $("[data-title-counter]");
        var conflict = $("[data-rename-conflict]");
        var typeName = TYPES[doc.type] || TYPES.OTHER;
        input.value = doc.title;
        input.placeholder = typeName;
        $("[name=version]", form).value = doc.version;

        function count() {
            counter.textContent = input.value.length + " / " + MAX_TITLE;
        }

        input.addEventListener("input", count);

        var errors = params.get("errors");
        if (errors === "blank") {
            input.value = "";
            showError(error, "Indiquez le titre du document.");
        } else if (errors === "long") {
            input.value = "Titre foncier n° 4521 — Bonapriso, lot 12, parcelle bornée en 2019 par le géomètre " +
                "Eyidi, à côté de l'école";
            showError(error, "100 caractères au plus : celui-ci en compte " + input.value.length + ".");
        }
        if (errors) {
            setInvalid(input, true);
            box.open = true;
            input.focus();
        }
        if (params.get("conflict") === "1") {
            // 409 MANDATE_DOCUMENT_VERSION_CONFLICT: the form again, with the current title and version.
            conflict.hidden = false;
            box.open = true;
            conflict.focus();
        }
        count();

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            var value = input.value.trim();
            var problem = !value ? "Indiquez le titre du document."
                : value.length > MAX_TITLE ? "100 caractères au plus : celui-ci en compte " + value.length + "." : "";
            showError(error, problem);
            setInvalid(input, !!problem);
            if (problem) {
                input.focus();
                return;
            }
            // PROTOTYPE: POST /mandates/{id}/documents/{documentId} with { title, version }; only the title changes.
            var documents = documentsOf(id);
            documents.forEach(function (d) {
                if (String(d.id) === String(doc.id)) {
                    d.title = value;
                    d.version = (d.version || 0) + 1;
                }
            });
            saveDocuments(id, documents);
            window.location.href = self + "&outcome=renamed";
        });
    }

    function initDelete(id, doc, self) {
        var open = $("[data-delete-open]");
        var confirm = $("[data-delete-confirm]");
        var cancel = $("[data-delete-cancel]");
        var form = $("[data-delete-form]");
        open.href = self + "&delete=1";
        cancel.href = self;
        $("[data-delete-pages]").textContent = doc.pages.length > 1
            ? "Ses " + doc.pages.length + " pages sont supprimées"
            : "Sa page est supprimée";

        function show(visible) {
            confirm.hidden = !visible;
            open.hidden = visible;
            open.setAttribute("aria-expanded", visible ? "true" : "false");
            if (visible) $(".alert__title", confirm).focus();
        }

        // Without JS, ?delete=1 is the confirmation page; with it, the same panel in place.
        if (params.get("delete") === "1") {
            show(true);
            dropParams(["delete"]);
        }
        open.addEventListener("click", function (event) {
            event.preventDefault();
            show(true);
        });
        cancel.addEventListener("click", function (event) {
            event.preventDefault();
            show(false);
            open.focus();
        });
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            // PROTOTYPE: DELETE /v1/mandates/{id}/documents/{documentId} (POST …/delete from the BFF's form).
            saveDocuments(id, documentsOf(id).filter(function (d) {
                return String(d.id) !== String(doc.id);
            }));
            window.location.href = "../mandate.html?id=" + id + "&documentOutcome=deleted&documentTitle=" +
                encodeURIComponent(doc.title) + "#documents-title";
        });
    }

    var pages = {
        mandate: initSection,
        "mandate-document-new": initNewDocument,
        "mandate-document": initDocument
    };
    var init = pages[document.body.getAttribute("data-page")];
    if (init) init();
})();
