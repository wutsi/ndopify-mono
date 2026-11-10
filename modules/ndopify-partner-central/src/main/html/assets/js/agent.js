/*
 * Ndopify Partner Central — another agent's profile (static prototype): agent.html.
 * Loaded after partner-central.js (which only initialises the account menu here).
 * PROTOTYPE: the fixtures stand in for GET /v1/agents/{id} and the agent's published listings; ?id= picks one,
 * ?back= points the back link at the listing the agent came from. Server calls are simulated.
 */
(function () {
    "use strict";

    // PROTOTYPE: agents, as GET /v1/agents/{id} would return them.
    var AGENTS = {
        "1001": {
            firstName: "Clarisse", lastName: "Mbarga", verified: true,
            agentType: "INDEPENDANT", city: "Douala",
            neighborhoods: ["Bonapriso", "Akwa", "Bonanjo"],
            joinedAt: "2023-03-14", whatsappNumber: "+237699123456",
            biography: "Démarcheur à Douala, je connais Bonapriso, Akwa et Bonanjo rue par rue. " +
                "J'accompagne les familles et les jeunes actifs de la recherche à la signature du bail, " +
                "avec des biens vérifiés et des visites organisées.",
            // PROTOTYPE: PUBLISHED only, newest first — what GET /v1/listings?agent-id={id} returns for another agent.
            listings: [
                {
                    id: 5001, title: "Appartement 2 chambres", place: "Bonapriso, Douala",
                    facts: "80 m² · Meublé", amount: "250K FCFA", period: "/ mois"
                },
                {
                    id: 5002, title: "Studio", place: "Akwa, Douala",
                    facts: "30 m² · Meublé", amount: "120K FCFA", period: "/ mois"
                },
                {
                    id: 5003, title: "Maison 3 chambres", place: "Bonanjo, Douala",
                    facts: "150 m² · Non meublé", amount: "400K FCFA", period: "/ mois"
                },
                {
                    id: 5004, title: "Appartement 3 chambres", place: "Bonapriso, Douala",
                    facts: "95 m² · Non meublé", amount: "300K FCFA", period: "/ mois"
                },
                {
                    id: 5005, title: "Studio", place: "Akwa, Douala",
                    facts: "28 m² · Meublé", amount: "110K FCFA", period: "/ mois"
                },
                {
                    id: 5006, title: "Maison 4 chambres", place: "Bonanjo, Douala",
                    facts: "200 m² · Non meublé", amount: "450K FCFA", period: "/ mois"
                }
            ]
        },
        "1002": {
            firstName: "Paul", lastName: "Etame", verified: true,
            agentType: "REAL_ESTATE_AGENT", city: "Yaoundé",
            neighborhoods: ["Bastos", "Biyem-Assi"],
            joinedAt: "2024-09-02", whatsappNumber: null, biography: "",
            listings: []
        }
    };

    var AGENT_TYPE_LABELS = {
        INDEPENDANT: "Indépendant",
        REAL_ESTATE_AGENT: "Rattaché à une agence immobilière"
    };

    var SINCE_FORMAT = new Intl.DateTimeFormat("fr-FR", { month: "long", year: "numeric" });

    // PROTOTYPE: no listing photos here; the real card uses the listing's hero image.
    var HOUSE_ICON = '<svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"' +
        ' aria-hidden="true" focusable="false"><path d="M3 11 12 4l9 7"/><path d="M5 10v10h14V10"/>' +
        '<path d="M10 20v-6h4v6"/></svg>';

    var PAGE_SIZE = 3; // what the prototype shows per page

    // The agent and page currently rendered, for the pager.
    var current = { id: "1001", agent: null, page: 1 };

    function $(selector) {
        return document.querySelector(selector);
    }

    function $all(selector) {
        return Array.prototype.slice.call(document.querySelectorAll(selector));
    }

    function setText(selector, value) {
        var element = $(selector);
        if (element) element.textContent = value;
    }

    function show(selector, visible) {
        var element = $(selector);
        if (element) element.hidden = !visible;
    }

    function fullName(agent) {
        return [agent.firstName, agent.lastName].filter(function (part) {
            return part && part.trim();
        }).join(" ");
    }

    function initials(agent) {
        return ((agent.firstName || "").charAt(0) + (agent.lastName || "").charAt(0)).toUpperCase();
    }

    function formatSince(value) {
        if (!value) return "";
        var date = new Date(value + "T12:00:00");
        if (isNaN(date.getTime())) return "";
        return "Membre depuis " + SINCE_FORMAT.format(date);
    }

    function renderIdentity(agent) {
        var name = fullName(agent);
        setText("[data-agent-name]", name);
        setText("[data-agent-initials]", initials(agent));
        if (name) document.title = name + " — Ndopify Partner Central";

        var type = AGENT_TYPE_LABELS[agent.agentType] || "";
        setText("[data-agent-type]", type);
        show("[data-agent-type]", Boolean(type));

        var city = agent.city || "";
        setText("[data-agent-city]", city);
        show("[data-agent-city]", Boolean(city));
        show("[data-agent-place]", Boolean(city));
        show("[data-agent-dot]", Boolean(type) && Boolean(city));

        var since = formatSince(agent.joinedAt);
        setText("[data-agent-since]", since);
        show("[data-agent-since]", Boolean(since));
        show("[data-agent-verified]", Boolean(agent.verified));
    }

    function renderPhoto(agent) {
        var image = $("[data-agent-photo-image]");
        var initialsElement = $("[data-agent-initials]");
        if (!image || !initialsElement) return;
        if (!agent.photoUrl) {
            image.hidden = true;
            image.removeAttribute("src");
            initialsElement.hidden = false;
            return;
        }
        image.src = agent.photoUrl;
        image.alt = "";
        image.hidden = false;
        initialsElement.hidden = true;
    }

    function renderSectors(agent) {
        var sectors = $("[data-agent-sectors]");
        if (!sectors) return;
        var city = agent.city || "";
        var neighborhoods = agent.neighborhoods || [];
        sectors.hidden = !city && neighborhoods.length === 0;

        setText("[data-agent-sectors-city]", city);
        show("[data-agent-sectors-city]", Boolean(city));

        var chips = $("[data-agent-neighborhoods]");
        if (!chips) return;
        chips.textContent = "";
        neighborhoods.forEach(function (name) {
            var chip = document.createElement("span");
            chip.className = "agent-sectors__chip";
            chip.textContent = name;
            chips.appendChild(chip);
        });
    }

    // PROTOTYPE: the card opens NPC's listing page; the BFF will use /listings/{id}.
    function listingUrlOf(listing) {
        return "listing.html?id=" + encodeURIComponent(listing.id);
    }

    function listingLine(className, value) {
        var line = document.createElement("span");
        line.className = className;
        line.textContent = value;
        return line;
    }

    function listingItem(listing) {
        var item = document.createElement("li");
        var link = document.createElement("a");
        link.className = "agent-listing";
        link.href = listingUrlOf(listing);

        var photo = document.createElement("span");
        photo.className = "agent-listing__photo";
        photo.setAttribute("aria-hidden", "true");
        photo.innerHTML = HOUSE_ICON;

        var text = document.createElement("span");
        text.className = "agent-listing__text";
        var title = document.createElement("strong");
        title.className = "agent-listing__title";
        title.textContent = listing.title;
        text.appendChild(title);
        text.appendChild(listingLine("agent-listing__place", listing.place));
        text.appendChild(listingLine("agent-listing__facts", listing.facts));

        var price = document.createElement("span");
        price.className = "agent-listing__price";
        var amount = document.createElement("strong");
        amount.textContent = listing.amount;
        price.appendChild(amount);
        price.appendChild(document.createTextNode(" " + (listing.period || "")));
        text.appendChild(price);

        link.appendChild(photo);
        link.appendChild(text);
        item.appendChild(link);
        return item;
    }

    function listingsUrl(id, page) {
        var params = new URLSearchParams(window.location.search);
        params.set("id", id);
        params.set("page", page);
        return "agent.html?" + params.toString() + "#listings";
    }

    // The URL follows the pager; file:// may refuse history changes, so the page keeps working either way.
    function rememberPage(url, replace) {
        try {
            if (replace) {
                window.history.replaceState({ page: current.page }, "", url);
            } else {
                window.history.pushState({ page: current.page }, "", url);
            }
        } catch (e) {
            /* ignore */
        }
    }

    function setPagerLink(selector, enabled, id, page) {
        var link = $(selector);
        if (!link) return;
        if (!enabled) {
            link.setAttribute("aria-disabled", "true");
            link.removeAttribute("href");
            link.removeAttribute("data-listings-target");
            return;
        }
        link.removeAttribute("aria-disabled");
        link.setAttribute("href", listingsUrl(id, page));
        link.setAttribute("data-listings-target", page);
    }

    function renderListings(agent, id, requestedPage) {
        var section = $("[data-agent-listings]");
        var list = $("[data-agent-listings-list]");
        var pager = $("[data-agent-pagination]");
        if (!section || !list || !pager) return 1;
        var listings = agent.listings || [];
        section.hidden = listings.length === 0;
        list.textContent = "";
        if (!listings.length) {
            pager.hidden = true;
            return 1;
        }
        var pages = Math.ceil(listings.length / PAGE_SIZE);
        var page = Math.min(Math.max(requestedPage || 1, 1), pages);
        var start = (page - 1) * PAGE_SIZE;
        listings.slice(start, start + PAGE_SIZE).forEach(function (listing) {
            list.appendChild(listingItem(listing));
        });
        pager.hidden = pages <= 1;
        setText("[data-listings-page]", page);
        setText("[data-listings-pages]", pages);
        setPagerLink("[data-listings-prev]", page > 1, id, page - 1);
        setPagerLink("[data-listings-next]", page < pages, id, page + 1);
        return page;
    }

    function goToListingsPage(page) {
        if (!current.agent) return;
        current.page = renderListings(current.agent, current.id, page);
        rememberPage(listingsUrl(current.id, current.page), false);
        var section = $("[data-agent-listings]");
        if (section) section.scrollIntoView({ block: "start" });
        var status = $("[data-listings-status]");
        var pages = $("[data-listings-pages]");
        if (status && pages) status.textContent = "Page " + current.page + " sur " + pages.textContent;
    }

    function initListingsPager() {
        $all("[data-listings-prev], [data-listings-next]").forEach(function (link) {
            link.addEventListener("click", function (event) {
                if (link.getAttribute("aria-disabled") === "true") {
                    event.preventDefault();
                    return;
                }
                var target = parseInt(link.getAttribute("data-listings-target"), 10);
                if (!target) return;
                event.preventDefault();
                goToListingsPage(target);
            });
        });
        window.addEventListener("popstate", function () {
            if (!current.agent) return;
            var page = parseInt(new URLSearchParams(window.location.search).get("page"), 10) || 1;
            current.page = renderListings(current.agent, current.id, page);
        });
    }

    function renderAbout(agent) {
        var biography = (agent.biography || "").trim();
        setText("[data-agent-bio]", biography);
        show("[data-agent-about]", Boolean(biography));
    }

    function renderContact(agent) {
        var link = $("[data-agent-whatsapp]");
        if (!link) return;
        var digits = (agent.whatsappNumber || "").replace(/\D/g, "");
        link.hidden = !digits;
        if (!digits) {
            link.removeAttribute("href");
            return;
        }
        link.href = "https://wa.me/" + digits;
    }

    function renderAgent(agent, id, requestedPage) {
        renderIdentity(agent);
        renderPhoto(agent);
        renderSectors(agent);
        current.page = renderListings(agent, id, requestedPage);
        renderAbout(agent);
        renderContact(agent);
    }

    // The link that brought the agent here (a listing, once co-brokerage ships): relative URLs only.
    function initBackLink() {
        var link = $("[data-back-link]");
        if (!link) return;
        var back = new URLSearchParams(window.location.search).get("back");
        if (!back || back.indexOf(":") !== -1 || back.indexOf("//") === 0 || back.indexOf("\\") !== -1) return;
        link.href = back;
        link.textContent = "← Retour";
    }

    document.addEventListener("DOMContentLoaded", function () {
        var page = $("[data-agent]");
        if (!page) return;
        initBackLink();

        var params = new URLSearchParams(window.location.search);
        var id = params.get("id") || "1001";
        var agent = Object.prototype.hasOwnProperty.call(AGENTS, id) ? AGENTS[id] : null;
        if (!agent) {
            page.hidden = true;
            show("[data-agent-missing]", true);
            return;
        }
        current.id = id;
        current.agent = agent;
        var requested = parseInt(params.get("page"), 10) || 1;
        renderAgent(agent, id, requested);
        if (requested !== current.page) {
            rememberPage(listingsUrl(id, current.page), true);
        }
        initListingsPager();
    });
})();
