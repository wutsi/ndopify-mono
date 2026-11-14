# NPC Published Listing — Design

**Status:** approved (prototype: `modules/ndopify-partner-central/src/main/html/listing.html?id=5002` for the owner,
`?id=5001` for the co-broker).
**Slice:** the published listing page, shared by the listing agent (owner) and a co-broker.

## Intent

Once a listing is published, the checklist stops being the useful view. The listing agent needs a readable property
page with the public link, the activity and the private access details; a co-broker needs the same property page
without the owner-only data, plus the listing agent's contact. The two audiences share one page and one sheet; only
the audience-specific blocks differ.

## Goals

- Make the published listing page (gallery + description + property sheet) the default once a listing is published.
- Let a co-broker agent reach it from a co-brokerage match and see enough detail to propose the property to their
  client.
- Keep the owner's public link, share button, stats, exact position, access directions and verification trail.
- Show the listing agent's remarks to both audiences (owner decision).
- Leave the draft / pending / to-fix checklist flow unchanged.

## Non-goals

- Visits, closing, commission, or the co-brokerage proposal workflow.
- Changing the agent profile page's listing cards.
- Changing the public portal page.
- Changing ndopify-server's access model: the BFF enforces who sees what.

## Anti-goals

- A co-broker page that contains the public URL, the stats, GPS coordinates, access directions, the listing agent's
  mandate id, or the listing agent's customer.
- Sensitive data present in the HTML and merely hidden with CSS or JavaScript.
- A second, duplicated template for the co-broker (the page is one template with a viewer lens).

## Invariants

1. **Projection, not hiding.** The co-broker `ListingView` is built without `publicUrl`, `stats`, `latitude`,
   `longitude`, `accessDirections` or `privateLocation`. The template must not receive them.
2. **Owner extras.** `publicUrl`, `stats`, the private location block and the verification disclosure exist only when
   the signed-in agent owns the listing.
3. **Agent remarks.** `agentRemarks` is rendered for both audiences, as written, with line breaks preserved.
4. **Public place only for the co-broker.** Neighbourhood, city and the public landmark; the note says the exact
   address and itinerary are shared by the agent before the visit.
5. **Owner access is unchanged.** `ListingAccess` still protects the section and photo pages; a co-broker can never
   open those.
6. **`TenantFormatter`** formats every date and amount; no hard-coded `FCFA` or date pattern.
7. **No new colours.** Use the existing tier-2 tokens; the viewer reuses the existing `viewer__*` styles.

## Decisions

### One page, two lenses

`GET /listings/{id}` renders the same `listing.html`. The view model carries a `coBrokerage` flag; the template shows
the shared published blocks for both, and the owner-only blocks only for the owner. The alternative — a dedicated
`/co-brokerage/listings/{id}` template — duplicates the gallery, sheet and CSS, so it was rejected.

### Published statuses

`PUBLISHED`, `RENTED` and `SOLD` get the sheet, as the current page keeps the public link for all three. A co-broker is
only admitted to `PUBLISHED`.

### The details display

The checklist is an audit tool (“À vérifier”, progress, section links) and reads as a to-do list to an external agent.
The published page uses a read-only **sheet** instead:

- a key-facts strip (rooms, bathrooms, area, furnished, parking);
- **Le bien** (type, furnishing, counts, surface);
- **Emplacement** (neighbourhood/city + public landmark; owner: exact GPS + Maps + access directions; co-broker: the
  “shared by the agent” note);
- **Équipements** grouped as water / electricity / road access / security;
- **Loyer et conditions** (rent, deposit and advance with amounts, availability);
- **Remarques de l’agent**.

Alternatives considered and rejected: keeping the checklist (wrong audience, hides detail pages from the co-broker);
an accordion (more taps, less scanning); a dense two-column table (poor on phones, where the agents work).

### Owner-only blocks

- **Public link + share** under the title.
- **Activity** line: views, interested clients, visits.
- **Accès et position** inside *Emplacement*: coordinates, “Ouvrir dans Maps”, “Comment y accéder”, marked as visible
  to the owner only.
- **Vérification de l’annonce**, a native `<details>` at the bottom: closed once published, open (and without its
  summary) while the listing is a draft or in verification. Its inner heading is “Sections vérifiées”.

### Co-broker access

The co-broker reaches the page from a *Biens correspondants* card. The card link becomes
`/listings/{id}?match={matchId}` when the match is co-brokerage. The page loads the listing and:

1. admits the owner as before; otherwise
2. requires an explicit `match` id, loads the match, and requires `match.listingId == listing.id`,
   `match.coBrokerage == true`, `match.status` in the visible statuses (`ACTIVE`, `PROPOSED`, `ACCEPTED`, `EXPIRED`);
3. loads the match's mandate and requires it to belong to the signed-in agent (a search mandate);
4. requires the listing to be `PUBLISHED`.

No match id, another listing, another agent's mandate, a dismissed match or an unpublished listing is the locked view.
The listing fetch and the match/mandate checks are separate calls; a 5xx/network failure is “unavailable”, a
4xx/denied relation is “locked”. The public link is never derived for a co-broker, and the stats are never fetched.

### Photos

The gallery uses the listing's images (non-rejected, non-blank), hero first. The first five are in the grid; every
image is a `[data-photo-link]` slide in the existing `listing.js` viewer, so “Voir les N photos” opens the same viewer
on the first photo. Extra anchors are `display:none` in the grid; the viewer still indexes them and the close returns
focus to the button/anchor that opened it, never a hidden link. The alternative — a second JSON photo list and a new
viewer — duplicates the existing viewer and is rejected.

### Data shape

`ListingView` keeps the owner's checklist fields and gains:

- `coBrokerage: Boolean`
- `published: PublishedListingView?` (null until published)
- `agent: ListingAgentView?` (co-broker only)
- `backUrl` / `backLabel` (the owner goes to the mandate, the co-broker to the match)

`PublishedListingView` holds the description, ordered photos, key facts, property specs, place, co-broker note,
amenity groups, terms, remarks and the owner-only private location. `PublishedListingViews` builds it from the
`Listing`, images, locations and formatter; it never sees the match.

`PublishedListingAccess` owns the owner/co-broker decision. The controller fetches the listing agent's `Agent` only
for a co-broker and builds the contact card with the existing profile/whatsapp helpers; the agent's mandate and customer
are never loaded.

## Out of scope

- The agent profile page's listing cards: they have no `match` id, so they keep their current link and the locked view
  for another agent's listing. A later slice can add a co-brokerage entry point there.
- The match card's copy beyond the link (no new “Bien d’un autre agent” row in this slice).
- Server-side authorization; ndopify-server still does not check listing reads.

## Verification

- `PublishedListingViewsTest`: facts, specs, place, amenities, terms, hero order, private location, no private for a
  co-broker, missing images/GPS.
- `PublishedListingAccessTest`: owner, valid co-broker, no match, wrong listing, non-co-brokerage, other agent's
  mandate, dismissed, unpublished, 404/5xx.
- `ListingViewsTest` / `ListingControllerTest` / `ListingTemplateTest`: the owner lens, the co-broker lens, the
  collapsed verification, the co-broker link and the owner-only blocks.
- `MatchViewsTest` / `MandateTemplateTest`: the co-brokerage card link carries the match id.
- Manual visual check against the approved prototype at 390px and 1024px; no automated CSS/JS test exists.
