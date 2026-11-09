/*
 * Ndopify Partner Central — another agent's profile (static prototype): agent.html.
 * Loaded after partner-central.js (which only initialises the account menu here).
 * PROTOTYPE: the fixtures stand in for GET /v1/agents/{id}; ?id= picks one, ?back= points the back link at the
 * listing the agent came from. Server calls are simulated.
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
                "avec des biens vérifiés et des visites organisées."
        },
        "1002": {
            firstName: "Paul", lastName: "Etame", verified: true,
            agentType: "REAL_ESTATE_AGENT", city: "Yaoundé",
            neighborhoods: ["Bastos", "Biyem-Assi"],
            joinedAt: "2024-09-02", whatsappNumber: null, biography: ""
        }
    };

    var AGENT_TYPE_LABELS = {
        INDEPENDANT: "Indépendant",
        REAL_ESTATE_AGENT: "Rattaché à une agence immobilière"
    };

    var SINCE_FORMAT = new Intl.DateTimeFormat("fr-FR", { month: "long", year: "numeric" });

    function $(selector) {
        return document.querySelector(selector);
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
        var section = $("[data-agent-sectors]");
        if (!section) return;
        var city = agent.city || "";
        var neighborhoods = agent.neighborhoods || [];
        section.hidden = !city && neighborhoods.length === 0;

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

    function renderAgent(agent) {
        renderIdentity(agent);
        renderPhoto(agent);
        renderSectors(agent);
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

        var id = new URLSearchParams(window.location.search).get("id") || "1001";
        var agent = AGENTS[id];
        if (!agent) {
            page.hidden = true;
            show("[data-agent-missing]", true);
            return;
        }
        renderAgent(agent);
    });
})();
