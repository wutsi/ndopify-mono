/*
 * Ndopify Partner Central — visits (static prototype): visit.html, and the Visites section of index.html.
 * Loaded after partner-central.js, whose helpers it uses (window.NPC: storage, $, field errors, phone display).
 * Exposes window.NpcVisits for partner-central.js: the visit of a match (mandate.html's cards, the home card's next
 * step). Server calls are simulated; every simulation is marked "PROTOTYPE".
 * The rules are the Design section of docs/superpowers/plans/2026-11-02-visit-management.md.
 */
(function () {
    "use strict";

    var MIN = 60 * 1000;
    var HOUR = 60 * MIN;
    var DAY = 24 * HOUR;

    // The design's rules.
    var REQUEST_TTL = 48 * HOUR; // REQUESTED for 48 h → EXPIRED
    var CHECK_IN_FROM = -1 * HOUR; // check-in from scheduledAt − 1 h…
    var CHECK_IN_TO = 4 * HOUR; // …to scheduledAt + 4 h
    var NO_SHOW_FROM = 15 * MIN; // customer no-show from scheduledAt + 15 min
    var MIN_DURATION = 10 * MIN; // check-out at least 10 min after check-in
    var MAX_ATTEMPTS = 5;
    var FLAG_METERS = 300;
    var WEAK_METERS = 50; // like listing/position.html: a weaker position asks to take it again
    var SLOT_DAYS = 7; // the 7 days starting tomorrow
    var FIRST_SLOT = 8 * 60;
    var LAST_SLOT = 20 * 60;
    var SLOT_MINUTES = 30;
    var COMMENT_MAX = 2000;
    var OPEN = ["REQUESTED", "CONFIRMED", "CHECKED_IN"];
    // VisitType: on site, or a WhatsApp video call the agent makes from the property (same code, position, report).
    var TYPE_LABELS = { ON_SITE: "Sur place", VIDEO: "Visite vidéo" };
    var TYPE_ICONS = {
        ON_SITE: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false"><path d="M3 11 12 4l9 7"/><path d="M5 10v10h14V10"/><path d="M10 20v-6h4v6"/></svg>',
        VIDEO: '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true" focusable="false"><rect x="2" y="6" width="14" height="12" rx="2"/><path d="m16 10 6-3v10l-6-3z"/></svg>'
    };

    // Each visit's changes made on visit.html, by id (see visitOf). Also in partner-central.js's KEYS (logout).
    var KEY = "npc.visits";
    // PROTOTYPE: the code every client of this prototype was e-mailed; the agent never sees it in the real app.
    var PROTOTYPE_CODE = "4827";

    var NOW = Date.now();

    /* ---------- Dates, in the tenant's long format with the year ---------- */
    var DAY_FORMAT = new Intl.DateTimeFormat("fr-FR", { weekday: "short", day: "numeric", month: "long", year: "numeric" });

    function two(n) {
        return (n < 10 ? "0" : "") + n;
    }

    function formatTime(ms) {
        var d = new Date(ms);
        return two(d.getHours()) + "h" + two(d.getMinutes());
    }

    // "sam. 7 novembre 2026"
    function formatDay(ms) {
        return DAY_FORMAT.format(new Date(ms));
    }

    // "sam. 7 novembre 2026 à 10h30"
    function formatAt(ms) {
        return formatDay(ms) + " à " + formatTime(ms);
    }

    function sameDay(a, b) {
        return new Date(a).toDateString() === new Date(b).toDateString();
    }

    // The home cards: "Aujourd'hui · 10h30", else the whole date.
    function formatWhen(ms) {
        return sameDay(ms, Date.now()) ? "Aujourd'hui · " + formatTime(ms) : formatAt(ms);
    }

    // "42 h", "45 min"
    function formatLeft(ms) {
        if (ms >= HOUR) return Math.floor(ms / HOUR) + " h";
        return Math.max(1, Math.ceil(ms / MIN)) + " min";
    }

    function formatDuration(ms) {
        var m = Math.round(ms / MIN);
        return m < 60 ? m + " min" : Math.floor(m / 60) + " h " + two(m % 60);
    }

    function formatMeters(m) {
        return m < 1000 ? m + " m" : (m / 1000).toLocaleString("fr-FR", { maximumFractionDigits: 1 }) + " km";
    }

    function currency() {
        return document.body.getAttribute("data-currency") || "FCFA";
    }


    function formatCompactRent(amount) {
        return (amount / 1000).toLocaleString("fr-FR", { maximumFractionDigits: 1 }) + "K " + currency() + " / mois";
    }

    // Local day `days` from today at "HH:MM".
    function at(days, hhmm) {
        var d = new Date(NOW);
        d.setHours(0, 0, 0, 0);
        d.setDate(d.getDate() + days);
        var p = hhmm.split(":");
        d.setHours(Number(p[0]), Number(p[1]), 0, 0);
        return d.getTime();
    }

    // The half hour at or before now + minutes: a slot's boundary.
    function slotFromNow(minutes) {
        var slot = SLOT_MINUTES * MIN;
        return Math.floor((NOW + minutes * MIN) / slot) * slot;
    }

    // A slot 2 h 30 to 3 h from now, before the check-in window opens, within today's 08:00–20:00; else tomorrow
    // 09:00. The window fixtures (7006 to 7009) stay relative to now, at night too: they show the panel.
    function laterToday() {
        var slot = slotFromNow(180);
        return slot <= at(0, "20:00") && slot >= at(0, "08:00") ? slot : at(slot < at(0, "08:00") ? 0 : 1, "09:00");
    }

    // Haversine, like ndopify-server's GeoDistance.
    function meters(lat1, lng1, lat2, lng2) {
        var r = 6371000;
        var rad = Math.PI / 180;
        var dLat = (lat2 - lat1) * rad;
        var dLng = (lng2 - lng1) * rad;
        var a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return Math.round(2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    /* ---------- Fixtures ---------- */

    // PROTOTYPE: the customers of the visits, by mandate. 204 is the prototype's mandate (mandate.html?id=204); the
    // others' mandate pages show the locked view, like LISTING_PROPOSALS' in partner-central.js.
    var CLIENTS = {
        204: { name: "Sandrine Eboa", whatsapp: "+237699887766" },
        207: { name: "Hervé Mbarga", whatsapp: "+237677451230" },
        208: { name: "Carine Ndongo", whatsapp: "+237655302418" },
        209: { name: "Rodrigue Essomba", whatsapp: "+237698114527" },
        210: { name: "Mireille Fotso", whatsapp: "+237670925381" },
        211: { name: "Paul Atangana", whatsapp: "+237694330716" },
        212: { name: "Grace Nkeng", whatsapp: "+237651208844" },
        213: { name: "Yannick Tchoua", whatsapp: "+237678560192" },
        214: { name: "Estelle Manga", whatsapp: "+237699301457" },
        215: { name: "Boris Kamga", whatsapp: "+237677118263" },
        216: { name: "Danielle Owona", whatsapp: "+237655874390" },
        217: { name: "Arnaud Biyong", whatsapp: "+237696427015" },
        218: { name: "Larissa Ekotto", whatsapp: "+237670338921" },
        219: { name: "Cédric Nana", whatsapp: "+237694775108" },
        220: { name: "Nadège Abena", whatsapp: "+237651990274" },
        221: { name: "Franck Mvondo", whatsapp: "+237678014536" },
        222: { name: "Inès Ngo Bassa", whatsapp: "+237699562781" },
        223: { name: "Joël Fouda", whatsapp: "+237677903361" },
        224: { name: "Aline Ebogo", whatsapp: "+237655617240" },
        225: { name: "Stéphane Ndjock", whatsapp: "+237698240573" },
        226: { name: "Clarisse Moukouri", whatsapp: "+237670481926" },
        227: { name: "Thierry Onana", whatsapp: "+237694157308" }
    };

    // PROTOTYPE: the agent's published listings (those of MATCHES in partner-central.js), with the exact address
    // and position the agent took on site (listing/position.html).
    var LISTINGS = {
        3012: { title: "Appartement 3 chambres", neighbourhood: "Bonapriso", price: 240000,
            address: "Rue Koloko, après la pharmacie du rond-point, portail vert", lat: 4.03182, lng: 9.69671 },
        2987: { title: "Appartement 3 chambres", neighbourhood: "Bonapriso", price: 245000,
            address: "Rue Tokoto, immeuble beige face à l'école Les Lauréats, 2e étage", lat: 4.03021, lng: 9.69312 },
        3002: { title: "Appartement 3 chambres", neighbourhood: "Bonanjo", price: 235000,
            address: "Rue Joss, derrière la poste centrale, portail noir", lat: 4.04468, lng: 9.68893 },
        3010: { title: "Appartement 3 chambres", neighbourhood: "Bonapriso", price: 250000,
            address: "Rue Njo-Njo, après la boulangerie, portail bleu", lat: 4.02807, lng: 9.69518 },
        3027: { title: "Appartement 2 chambres", neighbourhood: "Bonapriso", price: 200000,
            address: "Rue Ivy, résidence Les Palmiers, bâtiment B", lat: 4.02955, lng: 9.69804 },
        3041: { title: "Appartement 4 chambres", neighbourhood: "Bonanjo", price: 265000,
            address: "Boulevard de la Liberté, au-dessus de la banque, 3e étage", lat: 4.04712, lng: 9.69155 }
    };

    // PROTOTYPE: GET /v1/visits/{id} as the agent (never the code), one per state. Dates relative to the page load,
    // so that the check-in windows are open now. matchId only where mandate.html shows the match (mandate 204).
    var VISITS = {
        // À confirmer: the visit of match 9001, the one ndopify-web's proposition.html asks for
        7001: { matchId: "9001", mandateId: "204", listingId: "3012", status: "REQUESTED", createdAt: NOW - 6 * HOUR,
            requestedAt: at(5, "10:30"),
            note: "Je viendrai avec mon mari. Le matin nous arrange, mais nous pouvons nous adapter." },
        // À confirmer, expiring in 8 h
        7002: { mandateId: "208", listingId: "3002", status: "REQUESTED", createdAt: NOW - 40 * HOUR,
            requestedAt: at(2, "16:00") },
        // Confirmed 2 h ago at another time than the client asked
        7003: { mandateId: "207", listingId: "3010", status: "CONFIRMED", createdAt: NOW - 26 * HOUR,
            requestedAt: at(3, "09:00"), scheduledAt: at(3, "09:30"), scheduledOn: NOW - 2 * HOUR,
            note: "Je ne peux pas avant 9h." },
        // Confirmed, in 2 days: the visit of match 9004
        7004: { matchId: "9004", mandateId: "204", listingId: "2987", status: "CONFIRMED", createdAt: NOW - 3 * DAY,
            requestedAt: at(2, "10:00"), scheduledAt: at(2, "10:00") },
        // Confirmed, later today: before the check-in window (tomorrow morning when today's slots are over)
        7005: { mandateId: "209", listingId: "3027", status: "CONFIRMED", createdAt: NOW - 2 * DAY,
            scheduledAt: laterToday() },
        // In the check-in window, the time not reached yet: no no-show
        7006: { mandateId: "210", listingId: "3041", status: "CONFIRMED", createdAt: NOW - 2 * DAY,
            scheduledAt: slotFromNow(60) },
        // In the check-in window, 30 min late or more: the no-show is open
        7007: { mandateId: "211", listingId: "3002", status: "CONFIRMED", createdAt: NOW - 3 * DAY,
            scheduledAt: slotFromNow(-30) },
        // Started 25 min ago, at the door: can be ended
        7008: { mandateId: "212", listingId: "3010", status: "CHECKED_IN", createdAt: NOW - 3 * DAY,
            scheduledAt: slotFromNow(-30), checkedInAt: NOW - 25 * MIN, checkInDistanceMeters: 40 },
        // Started 4 min ago, 820 m away: flagged for review, can't be ended before 10 min
        7009: { mandateId: "213", listingId: "3012", status: "CHECKED_IN", createdAt: NOW - 2 * DAY,
            scheduledAt: slotFromNow(0), checkedInAt: NOW - 4 * MIN, checkInDistanceMeters: 820,
            checkInFlagged: true },
        // Completed, the client interested
        7010: { mandateId: "214", listingId: "2987", status: "COMPLETED", createdAt: NOW - 6 * DAY,
            scheduledAt: at(-3, "15:00"), checkedInAt: at(-3, "15:04"), checkedOutAt: at(-3, "15:36"),
            checkInDistanceMeters: 25,
            agentComment: "Cliente très intéressée, a demandé si le bailleur accepte 2 mois d'avance au lieu de 3. " +
                "À voir avec lui avant jeudi.",
            interest: "INTERESTED", feedbackComment: "Très bel appartement, je souhaite déposer mon dossier." },
        // Completed, the client not interested
        7011: { mandateId: "215", listingId: "3041", status: "COMPLETED", createdAt: NOW - 5 * DAY,
            scheduledAt: at(-2, "11:00"), checkedInAt: at(-2, "11:10"), checkedOutAt: at(-2, "11:31"),
            checkInDistanceMeters: 60, agentComment: "A trouvé le loyer élevé avec les charges du groupe électrogène.",
            interest: "NOT_INTERESTED", feedbackReasonCode: "PRICE",
            feedbackComment: "Trop cher pour nous avec les charges." },
        // Completed yesterday, no position at the start, the client hasn't answered yet
        7012: { mandateId: "216", listingId: "3012", status: "COMPLETED", createdAt: NOW - 4 * DAY,
            scheduledAt: at(-1, "17:30"), checkedInAt: at(-1, "17:28"), checkedOutAt: at(-1, "17:52"),
            agentComment: "Visite rapide, le client reviendra avec sa femme." },
        // Cancelled by the client once confirmed
        7013: { mandateId: "217", listingId: "3027", status: "CANCELLED", createdAt: NOW - 3 * DAY,
            scheduledAt: at(4, "14:00"), cancelledBy: "CUSTOMER", cancelledFrom: "CONFIRMED",
            cancelledAt: NOW - 5 * HOUR, cancellationReason: "J'ai trouvé un logement entre-temps." },
        // Cancelled by the agent
        7014: { mandateId: "218", listingId: "3010", status: "CANCELLED", createdAt: NOW - 4 * DAY,
            scheduledAt: at(1, "10:00"), cancelledBy: "AGENT", cancelledFrom: "CONFIRMED",
            cancelledAt: NOW - DAY, cancellationReason: "Le bailleur n'est pas disponible ce jour-là." },
        // Not confirmed within 48 h
        7015: { mandateId: "219", listingId: "3002", status: "EXPIRED", createdAt: NOW - 3 * DAY,
            requestedAt: at(-1, "10:00"), expiredAt: NOW - DAY },
        // Cancelled by the client before the agent confirmed it
        7016: { mandateId: "220", listingId: "3041", status: "CANCELLED", createdAt: NOW - DAY,
            requestedAt: at(1, "15:00"), cancelledBy: "CUSTOMER", cancelledFrom: "REQUESTED",
            cancelledAt: NOW - 6 * HOUR },
        // The client refused to give the code
        7017: { mandateId: "221", listingId: "2987", status: "NO_SHOW", createdAt: NOW - 4 * DAY,
            scheduledAt: at(-1, "09:30"), noShowParty: "CUSTOMER", noShowReason: "CODE_REFUSED",
            noShowAt: at(-1, "09:52"), noShowDistanceMeters: 15 },
        // The agent didn't start the visit within 4 h: counted as the agent's absence (VisitJobs.noShow)
        7018: { mandateId: "222", listingId: "3012", status: "NO_SHOW", createdAt: NOW - 4 * DAY,
            scheduledAt: at(-1, "14:00"), noShowParty: "AGENT", noShowReason: "AGENT_ABSENT",
            noShowAt: at(-1, "18:00") },

        // Video visits (type VIDEO, the client's choice): the agent calls the client on WhatsApp from the property.
        // À confirmer, asked in video: the visit of match 9006 (mandate.html?id=204)
        7019: { matchId: "9006", mandateId: "204", listingId: "3002", type: "VIDEO", status: "REQUESTED",
            createdAt: NOW - 3 * HOUR, requestedAt: at(3, "18:00"),
            note: "Je suis à Yaoundé cette semaine : une visite en vidéo m'arrangerait." },
        // Confirmed, in video, tomorrow
        7020: { mandateId: "223", listingId: "3027", type: "VIDEO", status: "CONFIRMED", createdAt: NOW - 2 * DAY,
            requestedAt: at(1, "12:00"), scheduledAt: at(1, "12:00") },
        // In the check-in window, 30 min late or more: the call to make, the no-show open
        7021: { mandateId: "224", listingId: "3041", type: "VIDEO", status: "CONFIRMED", createdAt: NOW - 2 * DAY,
            scheduledAt: slotFromNow(-30) },
        // Started 15 min ago, during the call, at the property
        7022: { mandateId: "225", listingId: "3010", type: "VIDEO", status: "CHECKED_IN", createdAt: NOW - 3 * DAY,
            scheduledAt: slotFromNow(-30), checkedInAt: NOW - 15 * MIN, checkInDistanceMeters: 20 },
        // Completed in video, the client interested
        7023: { mandateId: "226", listingId: "3012", type: "VIDEO", status: "COMPLETED", createdAt: NOW - 5 * DAY,
            scheduledAt: at(-2, "18:00"), checkedInAt: at(-2, "18:03"), checkedOutAt: at(-2, "18:24"),
            checkInDistanceMeters: 12,
            agentComment: "Visite par appel vidéo, bonne connexion. Le client vit à Yaoundé, il veut visiter sur " +
                "place avant de signer.",
            interest: "INTERESTED", feedbackComment: "La vidéo m'a convaincu, je passe le voir samedi." },
        // The client didn't answer the video call
        7024: { mandateId: "227", listingId: "2987", type: "VIDEO", status: "NO_SHOW", createdAt: NOW - 4 * DAY,
            scheduledAt: at(-1, "11:00"), noShowParty: "CUSTOMER", noShowReason: "CUSTOMER_ABSENT",
            noShowAt: at(-1, "11:20"), noShowDistanceMeters: 18 }
    };

    // MatchRejectionReason, as proposition.html words them.
    var REASON_LABELS = {
        PRICE: "trop cher",
        LOCATION: "le quartier ne convient pas",
        SIZE: "trop petit ou trop grand",
        AMENITIES: "il manque des équipements",
        AVAILABILITY: "disponible trop tard",
        OTHER: "autre raison"
    };

    /* ---------- State ---------- */
    function npc() {
        return window.NPC;
    }

    function savedVisits() {
        try {
            return JSON.parse(npc().store.get(KEY)) || {};
        } catch (e) {
            return {};
        }
    }

    function saveVisit(id, changes) {
        var all = savedVisits();
        all[id] = all[id] || {};
        Object.keys(changes).forEach(function (key) {
            all[id][key] = changes[key];
        });
        npc().store.set(KEY, JSON.stringify(all));
    }

    // The fixture, with what was done on visit.html since.
    function visitOf(id) {
        var fixture = VISITS[id];
        if (!fixture) return null;
        var v = {};
        Object.keys(fixture).forEach(function (key) {
            v[key] = fixture[key];
        });
        var saved = savedVisits()[id] || {};
        Object.keys(saved).forEach(function (key) {
            v[key] = saved[key];
        });
        v.id = String(id);
        v.client = CLIENTS[v.mandateId];
        v.firstName = v.client.name.split(" ")[0];
        v.listing = LISTINGS[v.listingId];
        v.type = v.type || "ON_SITE";
        v.requestedType = v.requestedType || VISITS[id].type || "ON_SITE";
        v.video = v.type === "VIDEO";
        return v;
    }

    function allVisits() {
        return Object.keys(VISITS).map(visitOf);
    }

    function expiresAt(v) {
        return v.createdAt + REQUEST_TTL;
    }

    // The time the visit is for: the agent's slot once scheduled, else the client's.
    function timeOf(v) {
        return v.scheduledAt || v.requestedAt;
    }

    function inWindow(v, now) {
        return v.status === "CONFIRMED" && now >= v.scheduledAt + CHECK_IN_FROM && now <= v.scheduledAt + CHECK_IN_TO;
    }

    // The page's panel: the status, or WINDOW for a confirmed visit that can be started now.
    function panelOf(v, now) {
        return inWindow(v, now) ? "WINDOW" : v.status;
    }

    var TAGS = {
        REQUESTED: ["À confirmer", "warning"],
        CONFIRMED: ["Confirmée", "success"],
        WINDOW: ["À démarrer", "info"],
        CHECKED_IN: ["En cours", "info"],
        COMPLETED: ["Terminée", "success"],
        CANCELLED: ["Annulée", "neutral"],
        EXPIRED: ["Expirée", "neutral"],
        NO_SHOW: ["Absence", "warning"]
    };

    // The card's next step on the Espace agent, one short line (it fits a phone), after the mandate cards' wording.
    function nextStepOf(v, now) {
        if (v.status === "REQUESTED") return "Confirmez la date avec le client.";
        if (v.status === "CHECKED_IN") {
            return now - v.checkedInAt < MIN_DURATION
                ? "Faites visiter le bien, puis terminez la visite."
                : "Terminez la visite avec votre compte rendu.";
        }
        if (inWindow(v, now)) {
            var late = now >= v.scheduledAt + NO_SHOW_FROM;
            if (v.video) {
                return late ? "Le client ne répond pas ? Déclarez son absence."
                    : "Appelez le client, puis démarrez avec son code.";
            }
            return late ? "Démarrez avec son code, ou déclarez son absence."
                : "Démarrez la visite avec le code du client.";
        }
        return v.video ? "Appelez le client en vidéo, depuis le bien." : "Le jour J, demandez son code au client.";
    }

    function setTag(el, panel) {
        el.textContent = TAGS[panel][0];
        el.className = "status-tag status-tag--" + TAGS[panel][1];
    }

    /* ---------- For partner-central.js ---------- */
    window.NpcVisits = {
        // A match's visit, for its card on mandate.html: undefined when the match has no visit in the fixtures (the
        // card keeps its own), null once closed (cancelled, expired, no-show: the client is "intéressé" again).
        forMatch: function (matchId) {
            var id = Object.keys(VISITS).filter(function (key) {
                return VISITS[key].matchId === String(matchId);
            })[0];
            if (!id) return undefined;
            var v = visitOf(id);
            if (OPEN.indexOf(v.status) === -1 && v.status !== "COMPLETED") return null;
            return { id: v.id, status: v.status, at: timeOf(v), time: formatTime(timeOf(v)),
                type: v.video ? "VIDEO" : "IN_PERSON", checkedOutAt: v.checkedOutAt };
        },
        formatAt: formatAt,
        formatWhen: formatWhen
    };

    /* ---------- index.html › Visites ---------- */

    // À confirmer (the soonest to expire first), then the visits to come (soonest first, so today's lead): each card
    // opens visit.html?id=. PROTOTYPE: ?visits=empty shows an agent without visits.
    function initHomeVisits() {
        var section = document.querySelector("[data-visits-section]");
        if (!section) return;
        var $ = npc().$;
        var template = $("[data-visit-card-template]", section);
        var now = Date.now();
        var empty = new URLSearchParams(window.location.search).get("visits") === "empty";
        var open = empty ? [] : allVisits().filter(function (v) {
            return OPEN.indexOf(v.status) !== -1;
        });
        var requested = open.filter(function (v) {
            return v.status === "REQUESTED" && expiresAt(v) > now;
        }).sort(function (a, b) {
            return expiresAt(a) - expiresAt(b);
        });
        var upcoming = open.filter(function (v) {
            return v.status !== "REQUESTED" && (v.status === "CHECKED_IN" || v.scheduledAt + CHECK_IN_TO > now);
        }).sort(function (a, b) {
            return a.scheduledAt - b.scheduledAt;
        });

        function card(v) {
            var item = template.content.firstElementChild.cloneNode(true);
            var titleId = "visit-" + v.id + "-title";
            item.setAttribute("data-visit-id", v.id);
            $("article", item).setAttribute("aria-labelledby", titleId);
            $("h4", item).id = titleId;
            var link = $("[data-visit-link]", item);
            link.href = "visit.html?id=" + encodeURIComponent(v.id);
            link.textContent = v.client.name;
            var tag = $("[data-visit-tag]", item);
            var timer = $("[data-visit-timer]", item);
            if (v.status === "REQUESTED") {
                tag.hidden = true;
                $("[data-visit-when]", item).textContent = "Demandée pour le " + formatAt(v.requestedAt);
                var left = expiresAt(v) - now;
                $("[data-visit-left]", item).textContent = formatLeft(left);
                if (left < 12 * HOUR) item.setAttribute("data-urgent", "");
            } else {
                setTag(tag, v.status === "CHECKED_IN" ? "CHECKED_IN" : panelOf(v, now));
                timer.hidden = true;
                $("[data-visit-when]", item).textContent = v.status === "CHECKED_IN"
                    ? "En cours depuis " + formatTime(v.checkedInAt) : formatWhen(v.scheduledAt);
            }
            // The type first: a video visit is a call to make from the property
            var listing = $("[data-visit-listing]", item);
            listing.innerHTML = TYPE_ICONS[v.type] + "<span></span>";
            $("span", listing).textContent = TYPE_LABELS[v.type] + " · " + v.listing.title + " · " + v.listing.neighbourhood;
            $("[data-visit-next]", item).textContent = nextStepOf(v, now);
            return item;
        }

        function fill(name, visits) {
            var group = $('[data-visit-group="' + name + '"]', section);
            var list = $("[data-visit-list]", group);
            list.textContent = "";
            visits.forEach(function (v) {
                list.appendChild(card(v));
            });
            group.hidden = visits.length === 0;
        }

        fill("requested", requested);
        fill("upcoming", upcoming);
        $("[data-visits-empty]", section).hidden = requested.length + upcoming.length > 0;
    }

    /* ---------- visit.html ---------- */
    function initVisit() {
        var N = npc();
        var $ = N.$;
        var $all = N.$all;
        var params = new URLSearchParams(window.location.search);
        var id = params.get("id") || "7001";
        var v = visitOf(id);
        var content = $("[data-visit]");
        if (!v) {
            content.hidden = true;
            $("[data-visit-locked]").hidden = false;
            return;
        }

        // PROTOTYPE: ?error=invalid shows a wrong code with 3 tries left, ?error=locked the locked check-in.
        if (params.get("error") === "invalid" || params.get("error") === "locked") {
            saveVisit(id, { codeAttempts: params.get("error") === "locked" ? MAX_ATTEMPTS : 2 });
            v = visitOf(id);
        }

        var flash = $("[data-visit-flash]");

        function text(name, value) {
            $all('[data-v="' + name + '"]').forEach(function (el) {
                el.textContent = value;
            });
        }

        function showFlash(message) {
            $("[data-visit-flash-text]", flash).textContent = message;
            flash.hidden = false;
            flash.focus();
        }

        function update(changes) {
            saveVisit(id, changes);
            v = visitOf(id);
            render();
        }

        /* --- Header: client, listing, mandate, status --- */
        function renderHeader(panel) {
            document.title = "Visite de " + v.client.name + " — Ndopify Partner Central";
            text("id", v.id);
            text("clientName", v.client.name);
            text("firstName", v.firstName);
            text("mandateId", v.mandateId);
            text("phone", N.displayPhone(v.client.whatsapp));
            text("listingTitle", v.listing.title + " · " + v.listing.neighbourhood);
            text("listingMeta", "Annonce n° " + v.listingId + " · " + formatCompactRent(v.listing.price));
            text("address", v.listing.address);
            $("[data-mandate-link]").href = "mandate.html?id=" + encodeURIComponent(v.mandateId) + "#matches-title";
            $("[data-listing-link]").href = "listing.html?id=" + encodeURIComponent(v.listingId);
            $("[data-phone-link]").href = "tel:" + v.client.whatsapp;
            $all("[data-whatsapp-link]").forEach(function (a) {
                a.href = "https://wa.me/" + v.client.whatsapp.replace(/\D/g, "");
            });
            // The type: in the header, and the parts of the page that differ (the call, the client's absence)
            text("typeLabel", v.video ? "Visite vidéo, par appel WhatsApp depuis le bien" : "Visite sur place");
            // SVG elements have no hidden property: the attribute itself
            $all("[data-type-icon]").forEach(function (el) {
                if (el.getAttribute("data-type-icon") === v.type) el.removeAttribute("hidden");
                else el.setAttribute("hidden", "");
            });
            $all("[data-for-type]").forEach(function (el) {
                el.hidden = el.getAttribute("data-for-type") !== v.type;
            });
            $all("[data-map-link]").forEach(function (a) {
                a.href = "https://www.openstreetmap.org/?mlat=" + v.listing.lat + "&mlon=" + v.listing.lng +
                    "#map=18/" + v.listing.lat + "/" + v.listing.lng;
            });
            setTag($("[data-visit-tag]"), panel);
        }

        /* --- REQUESTED: the slot and the type to confirm --- */
        function syncTypeHint() {
            var video = scheduleForm.elements.type.value === "VIDEO";
            $("[data-type-hint]").textContent = video
                ? "Vous appellerez " + v.firstName + " en vidéo sur WhatsApp, depuis le bien, à l'heure choisie."
                : "Vous retrouvez " + v.firstName + " au bien, à l'heure choisie.";
        }
        var scheduleForm = $("[data-schedule-form]");
        var daySelect = scheduleForm.elements.date;
        var timeSelect = scheduleForm.elements.time;
        var slotError = $("#slot-error");

        function isoDay(ms) {
            var d = new Date(ms);
            return d.getFullYear() + "-" + two(d.getMonth() + 1) + "-" + two(d.getDate());
        }

        function slotOf() {
            var p = daySelect.value.split("-");
            var t = timeSelect.value.split(":");
            return new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]), Number(t[0]), Number(t[1])).getTime();
        }

        // The 7 days starting tomorrow, 08:00–20:00 every 30 min, prefilled with the client's slot.
        function fillSlots(selected) {
            daySelect.textContent = "";
            for (var i = 1; i <= SLOT_DAYS; i++) {
                var day = at(i, "00:00");
                daySelect.appendChild(new Option(formatDay(day), isoDay(day)));
            }
            timeSelect.textContent = "";
            for (var m = FIRST_SLOT; m <= LAST_SLOT; m += SLOT_MINUTES) {
                timeSelect.appendChild(new Option(two(Math.floor(m / 60)) + "h" + two(m % 60),
                    two(Math.floor(m / 60)) + ":" + two(m % 60)));
            }
            daySelect.value = isoDay(selected);
            if (!daySelect.value) daySelect.selectedIndex = 0;
            timeSelect.value = two(new Date(selected).getHours()) + ":" + two(new Date(selected).getMinutes());
            if (!timeSelect.value) timeSelect.selectedIndex = 0;
            syncSlots();
        }

        // Any slot of the lists is valid (the server re-checks it: 400 VISIT_INVALID_SLOT).
        function syncSlots() {
            N.setFieldError(daySelect, slotError, null);
        }

        daySelect.addEventListener("change", syncSlots);
        scheduleForm.addEventListener("change", function (event) {
            if (event.target.name === "type") syncTypeHint();
        });
        timeSelect.addEventListener("change", syncSlots);

        scheduleForm.addEventListener("submit", function (event) {
            event.preventDefault();
            var slot = slotOf();
            if (isNaN(slot)) {
                N.setFieldError(daySelect, slotError, "Choisissez le jour et l'heure de la visite.");
                return;
            }
            var submit = $("[type=submit]", scheduleForm);
            if (!N.isActive(submit)) return;
            // PROTOTYPE: simulates POST /v1/visits/{id}/schedule: CONFIRMED, the client is e-mailed the date, the
            // address and the code.
            N.setActive(submit, false);
            submit.textContent = "Confirmation…";
            window.setTimeout(function () {
                N.setActive(submit, true);
                submit.textContent = "Confirmer";
                var now = Date.now();
                var type = scheduleForm.elements.type.value || v.type;
                update({ status: "CONFIRMED", scheduledAt: slot, scheduledOn: now, type: type });
                showFlash((type === "VIDEO" ? "Visite vidéo confirmée pour le " : "Visite confirmée pour le ") +
                    formatAt(slot) + " : " + v.firstName + " a reçu par e-mail la date, l'adresse et son code." +
                    (type === "VIDEO" ? " À l'heure prévue, appelez le client en vidéo sur WhatsApp depuis le bien." : ""));
            }, 600);
        });

        /* --- Cancel (agent), before the check-in: REQUESTED (refuse) or CONFIRMED; the client is told by e-mail --- */
        $all("[data-cancel-form]").forEach(function (form) {
            form.addEventListener("submit", function (event) {
                event.preventDefault();
                var refused = v.status === "REQUESTED";
                // PROTOTYPE: simulates POST /v1/visits/{id}/cancel (as the agent)
                update({ status: "CANCELLED", cancelledBy: "AGENT", cancelledFrom: v.status, cancelledAt: Date.now(),
                    cancellationReason: form.elements.reason.value.trim() || null });
                showFlash((refused ? "Demande refusée" : "Visite annulée") + " : le client est prévenu par e-mail.");
            });
        });

        /* --- Position: taken as soon as the visit can be started or ended (never required) --- */
        var position = { state: "idle", coords: null };

        function renderPosition(box) {
            var label = $("[data-position-text]", box);
            var retry = $("[data-position-retry]", box);
            box.hidden = false;
            box.className = "visit-position visit-position--" + position.state;
            retry.hidden = position.state === "pending" || position.state === "idle" ||
                (position.state === "done" && position.coords.accuracy <= WEAK_METERS);
            if (position.state === "pending") label.textContent = "Relevé de votre position…";
            if (position.state === "none") label.textContent = box.getAttribute("data-none-text");
            if (position.state === "done") {
                var accuracy = Math.round(position.coords.accuracy);
                label.textContent = accuracy > WEAK_METERS
                    ? "Position imprécise (précision " + accuracy + " m) : sortez devant le bien et relevez-la à nouveau."
                    : "Position relevée · précision " + accuracy + " m";
                if (accuracy > WEAK_METERS) box.className += " visit-position--weak";
            }
        }

        function setPosition(state, coords) {
            position.state = state;
            position.coords = coords || null;
            $all("[data-position-input]").forEach(function (input) {
                var name = input.name;
                input.value = !coords ? "" : name === "accuracyMeters" ? Math.round(coords.accuracy) : coords[name];
            });
            $all("[data-position-status]").forEach(renderPosition);
        }

        function capture() {
            if (!navigator.geolocation) {
                setPosition("none");
                return;
            }
            setPosition("pending");
            navigator.geolocation.getCurrentPosition(function (p) {
                setPosition("done", p.coords);
            }, function () {
                setPosition("none");
            }, { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 });
        }

        $all("[data-position-retry]").forEach(function (button) {
            button.addEventListener("click", capture);
        });
        // PROTOTYPE: a position 30 m from the listing, for a computer without GPS.
        $all("[data-simulate-gps]").forEach(function (button) {
            button.addEventListener("click", function () {
                setPosition("done", { latitude: v.listing.lat + 0.00027, longitude: v.listing.lng, accuracy: 12 });
            });
        });

        function distance() {
            if (!position.coords) return null;
            return meters(position.coords.latitude, position.coords.longitude, v.listing.lat, v.listing.lng);
        }

        /* --- WINDOW: check-in with the client's code, or the client's no-show --- */
        var checkInForm = $("[data-checkin-form]");
        var codeInput = checkInForm.elements.code;
        var codeError = $("#code-error");

        codeInput.addEventListener("input", function () {
            codeInput.value = codeInput.value.replace(/\D/g, "").slice(0, 4);
            N.setFieldError(codeInput, codeError, null);
        });

        function attemptsLeftText(left) {
            return "Code incorrect. Il vous reste " + left + (left > 1 ? " essais." : " essai.");
        }

        checkInForm.addEventListener("submit", function (event) {
            event.preventDefault();
            if ((v.codeAttempts || 0) >= MAX_ATTEMPTS) return;
            if (!/^\d{4}$/.test(codeInput.value)) {
                N.setFieldError(codeInput, codeError, "Saisissez les 4 chiffres du code du client.");
                codeInput.focus();
                return;
            }
            // PROTOTYPE: simulates POST /v1/visits/{id}/check-in (409 VISIT_CODE_INVALID / VISIT_CODE_LOCKED)
            if (codeInput.value !== PROTOTYPE_CODE) {
                var attempts = (v.codeAttempts || 0) + 1;
                update({ codeAttempts: attempts });
                if (attempts < MAX_ATTEMPTS) {
                    N.setFieldError(codeInput, codeError, attemptsLeftText(MAX_ATTEMPTS - attempts));
                    codeInput.select();
                }
                return;
            }
            var d = distance();
            update({ status: "CHECKED_IN", checkedInAt: Date.now(), checkInDistanceMeters: d,
                checkInFlagged: d !== null && d > FLAG_METERS });
            showFlash("Visite démarrée à " + formatTime(v.checkedInAt) + ". Terminez-la ici une fois la visite finie.");
        });

        var noShowForm = $("[data-noshow-form]");
        var noShowError = $("#noshow-error");
        noShowForm.addEventListener("change", function () {
            N.setFieldError($("fieldset", noShowForm), noShowError, null);
        });
        noShowForm.addEventListener("submit", function (event) {
            event.preventDefault();
            var reason = noShowForm.elements.reason.value;
            if (!reason) {
                N.setFieldError($("fieldset", noShowForm), noShowError, "Choisissez ce qui s'est passé.");
                return;
            }
            // PROTOTYPE: simulates POST /v1/visits/{id}/no-show (noShowParty CUSTOMER, with the position)
            update({ status: "NO_SHOW", noShowParty: "CUSTOMER", noShowReason: reason, noShowAt: Date.now(),
                noShowDistanceMeters: distance() });
            showFlash("Absence du client enregistrée : il est prévenu par e-mail.");
        });

        // "#no-show" (the locked message's link) opens the declaration.
        function openNoShow() {
            var details = $("#no-show");
            if (window.location.hash === "#no-show" && !details.hidden) details.open = true;
        }
        window.addEventListener("hashchange", openNoShow);

        /* --- CHECKED_IN: check-out with the agent's private comment --- */
        var checkOutForm = $("[data-checkout-form]");
        var comment = checkOutForm.elements.comment;
        var commentError = $("#comment-error");
        var checkOutSubmit = $("[data-checkout-submit]");

        comment.addEventListener("input", function () {
            $("[data-counter]", checkOutForm).textContent = comment.value.length + " / " + COMMENT_MAX;
            if (comment.value.trim()) N.setFieldError(comment, commentError, null);
        });

        checkOutForm.addEventListener("submit", function (event) {
            event.preventDefault();
            if (!N.isActive(checkOutSubmit)) {
                $("[data-checkout-wait]").focus();
                return;
            }
            if (!comment.value.trim()) {
                N.setFieldError(comment, commentError, "Écrivez votre compte rendu de la visite.");
                comment.focus();
                return;
            }
            // PROTOTYPE: simulates POST /v1/visits/{id}/check-out: the client is asked if the listing interests them
            update({ status: "COMPLETED", checkedOutAt: Date.now(), agentComment: comment.value.trim(),
                checkOutDistanceMeters: distance() });
            showFlash("Visite terminée. " + v.firstName + " reçoit un e-mail pour dire si le bien l'intéresse.");
        });

        /* --- The time-bound parts: the no-show from +15 min, the check-out from +10 min, the request's expiry --- */
        function tick() {
            var now = Date.now();
            var panel = panelOf(v, now);
            // A window opening or closing while the page is open changes the panel
            if (panel !== $("[data-visit]").getAttribute("data-current")) {
                render();
                return;
            }
            if (panel === "REQUESTED") {
                text("expiresIn", "dans " + formatLeft(Math.max(0, expiresAt(v) - now)));
            }
            if (panel === "WINDOW") {
                var noShowAt = v.scheduledAt + NO_SHOW_FROM;
                $("[data-noshow-later]").hidden = now >= noShowAt;
                $("#no-show").hidden = now < noShowAt;
                text("noShowFrom", formatTime(noShowAt));
                var locked = (v.codeAttempts || 0) >= MAX_ATTEMPTS;
                $("[data-locked-noshow]").hidden = now < noShowAt;
                $("[data-locked-later]").hidden = now >= noShowAt;
                checkInForm.hidden = locked;
                $("[data-code-locked]").hidden = !locked;
                openNoShow();
            }
            if (panel === "CHECKED_IN") {
                var endFrom = v.checkedInAt + MIN_DURATION;
                N.setActive(checkOutSubmit, now >= endFrom);
                $("[data-checkout-wait]").hidden = now >= endFrom;
                text("checkOutFrom", formatTime(endFrom));
            }
        }

        function render() {
            var now = Date.now();
            var panel = panelOf(v, now);
            content.setAttribute("data-current", panel);
            renderHeader(panel);
            $all("[data-panel]").forEach(function (el) {
                el.hidden = el.getAttribute("data-panel") !== panel;
            });

            if (panel === "REQUESTED") {
                text("requestedAt", formatAt(v.requestedAt));
                text("expiresAt", formatAt(expiresAt(v)));
                $("[data-note]").hidden = !v.note;
                text("note", v.note || "");
                fillSlots(v.requestedAt);
                text("requestedType", v.requestedType === "VIDEO" ? "en vidéo, par appel WhatsApp" : "sur place");
                Array.prototype.forEach.call(scheduleForm.elements.type, function (radio) {
                    radio.checked = radio.value === v.type;
                });
                syncTypeHint();
            }
            if (panel === "CONFIRMED") {
                text("scheduledAt", formatAt(v.scheduledAt));
                text("windowFrom", formatAt(v.scheduledAt + CHECK_IN_FROM));
            }
            if (panel === "WINDOW") {
                text("scheduledWhen", formatWhen(v.scheduledAt));
                var attempts = v.codeAttempts || 0;
                N.setFieldError(codeInput, codeError, attempts > 0 && attempts < MAX_ATTEMPTS
                    ? attemptsLeftText(MAX_ATTEMPTS - attempts) : null);
            }
            if (panel === "CHECKED_IN") {
                text("checkedInTime", formatTime(v.checkedInAt));
                var d = v.checkInDistanceMeters;
                $("[data-distance-ok]").hidden = d === null || d === undefined || !!v.checkInFlagged;
                $("[data-distance-far]").hidden = !v.checkInFlagged;
                $("[data-distance-none]").hidden = d !== null && d !== undefined;
                if (d !== null && d !== undefined) text("distance", formatMeters(d));
            }
            if (panel === "COMPLETED") renderCompleted();
            if (panel === "CANCELLED") renderCancelled();
            if (panel === "EXPIRED") renderExpired();
            if (panel === "NO_SHOW") renderNoShow();

            if ((panel === "WINDOW" || panel === "CHECKED_IN") && position.state === "idle") capture();
            tick();
        }

        function renderCompleted() {
            text("checkedInAt", formatAt(v.checkedInAt));
            text("checkedOutAt", formatAt(v.checkedOutAt));
            text("duration", formatDuration(v.checkedOutAt - v.checkedInAt));
            var d = v.checkInDistanceMeters;
            text("startDistance", d === null || d === undefined ? "Non relevée"
                : "À " + formatMeters(d) + " du bien" + (v.checkInFlagged ? " (à vérifier par l'équipe Ndopify)" : ""));
            text("agentComment", v.agentComment || "");
            text("completedType", v.video ? "Visite vidéo (WhatsApp)" : "Sur place");
            var answered = !!v.interest;
            $("[data-feedback-none]").hidden = answered;
            $("[data-feedback]").hidden = !answered;
            if (answered) {
                var interested = v.interest === "INTERESTED";
                var tag = $("[data-feedback-tag]");
                tag.textContent = interested ? "Intéressé" : "Pas intéressé";
                tag.className = "status-tag status-tag--" + (interested ? "success" : "neutral");
                $("[data-feedback-reason]").textContent = !interested && v.feedbackReasonCode
                    ? REASON_LABELS[v.feedbackReasonCode] : "";
                $("[data-feedback-comment]").hidden = !v.feedbackComment;
                text("feedbackComment", v.feedbackComment || "");
            }
        }

        function renderCancelled() {
            var request = v.cancelledFrom === "REQUESTED";
            var byAgent = v.cancelledBy === "AGENT";
            text("cancelTitle", request ? (byAgent ? "Demande refusée" : "Demande annulée") : "Visite annulée");
            text("cancelText", (byAgent ? (request ? "Vous avez refusé la demande" : "Vous avez annulé la visite")
                : v.firstName + (request ? " a annulé sa demande" : " a annulé la visite")) + " le " +
                formatAt(v.cancelledAt) + ".");
            $("[data-cancel-reason]").hidden = !v.cancellationReason;
            text("cancellationReason", v.cancellationReason ? "« " + v.cancellationReason + " »" : "");
            text("plannedAt", formatAt(timeOf(v)));
        }

        function renderExpired() {
            text("expiredText", "Vous n'avez pas confirmé la demande sous 48 h : elle a expiré le " +
                formatAt(v.expiredAt) + ". " + v.firstName + " peut en faire une nouvelle.");
            text("plannedAt", formatAt(timeOf(v)));
        }

        function renderNoShow() {
            var agent = v.noShowParty === "AGENT";
            text("noShowTitle", agent ? "Visite non démarrée"
                : v.noShowReason === "CODE_REFUSED" ? "Code refusé par le client"
                    : v.video ? "Le client ne répond pas" : "Client absent");
            text("noShowText", agent ? "Vous n'avez pas démarré la visite : elle est comptée comme une absence de votre part."
                : "Déclaré par vous le " + formatAt(v.noShowAt) + " : l'absence du client est enregistrée.");
            var d = v.noShowDistanceMeters;
            $("[data-noshow-distance]").hidden = agent;
            text("noShowDistance", d === null || d === undefined ? "Votre position n'a pas pu être relevée."
                : "Vous étiez à " + formatMeters(d) + " du bien.");
            text("plannedAt", formatAt(timeOf(v)));
        }

        // PROTOTYPE: back to the fixtures' states.
        $("[data-visit-reset]").addEventListener("click", function () {
            npc().store.remove(KEY);
            window.location.href = "visit.html?id=" + encodeURIComponent(id);
        });

        content.hidden = false;
        render();
        window.setInterval(tick, 15000);
    }

    document.addEventListener("DOMContentLoaded", function () {
        var page = document.body.getAttribute("data-page");
        if (page === "agent-home") initHomeVisits();
        if (page === "visit") initVisit();
    });
})();
