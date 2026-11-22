# V2 — Property sales

Roadmap to take Ndopify from the MVP (rentals) to v2, supporting property sales: buyers (SEARCH mandates with
`listingType = SALE`) and sellers (LISTING mandates with `listingType = SALE`) matched with sale-qualified agents.

Framing: the product is mandate matching, not a listing marketplace. Every story serves the customer → agent match
(who the customer is, what the agent does for them, how dispatch picks the agent), not property browsing.

## Starting point

The data model already carries sales: `ListingType.SALE` on mandates and listings, `ListingStatus.SOLD`, the
`Matcher` rules out a pair whose `listingType` differs, and `MandateMailLabels` switches "Location"/"Vente" and
"Loyer"/"Prix". The gaps are in the flows: the *Trouver un bien* wizard hard-codes `ListingType.RENTAL`
(`PendingMandate`), its rules are rental-only (`MIN_RENT`, `movingPeriod`, LAND commercial-only), the mandate
contracts state a one-month-rent commission, and dispatch doesn't know which agents handle sales.

## Stories

### Epic 1 — Sale customers enter the funnel
1. **Buyer creates a SEARCH mandate for a purchase** — "Trouver un bien" asks *Louer / Acheter*, then sale budget,
   financing and timeline.
2. **Owner creates a LISTING mandate to sell** — "Proposer un bien" asks the same, plus asking price, title status
   (titre foncier, in progress, none) and urgency.
3. **Diaspora buyer/seller** — creates and follows a mandate remotely (video visits, remote signing as defaults).

### Epic 2 — Agents qualified for sales
4. **Agent opts in to sales** — Partner Central profile: rentals, sales, or both.
5. **Sale-specific KYC level** — stronger verification (professional card / license or track record) before
   receiving sale mandates.
6. **Agent sale track record** — closed sales feed a reputation signal used by dispatch.

### Epic 3 — Dispatch & matching for sales
7. **Dispatch picks a sale-qualified agent** — sale mandates only go to opted-in (later: qualified) agents.
8. **Sale match scoring** — price vs budget with a negotiation margin, title status, financing compatibility.
9. **Co-brokerage on sales** — buyer's and seller's agents share the deal, split stated up front.

### Epic 4 — Sale mandate contracts
10. **Sale SEARCH & LISTING contract templates** — commission as % of price, duration, exclusivity; signed in the
    e-signature portal by the party only.
11. **Longer mandate lifecycle** — durations, expiry, renewal and progress reminders on sale timelines (months).

### Epic 5 — Visits & buyer seriousness
12. **Repeat visits** — a buyer comes back, possibly with a relative, technician or surveyor.
13. **Buyer qualification before visits** — agent records proof of funds / financing before visiting a seller's
    property.

### Epic 6 — Offer & negotiation
14. **Buyer makes an offer through the agent** — amount, conditions, validity, recorded on the match.
15. **Seller accepts / counters / refuses** — history on the mandate, visible to both customers on the public portal.

### Epic 7 — Closing a sale
16. **Close the mandate as SOLD** — `finalPrice` is the sale price, listing → `SOLD`, both sides' mandates closed.
17. **Notary / title handoff checklist** — the agent tracks sale promise → notary → title transfer as milestones
    (tracking only, Ndopify doesn't execute them).

### Epic 8 — Site & communications
18. **Sale copy** — marketing pages, e-mails and Telegram in buyer/seller wording, mandate-first.
19. **Sale KPIs** — sale mandates created → assigned → visited → under offer → sold, time-to-sale.

## Milestones

```
M1 ──▶ M2 ──▶ M3
 └──────────▶ M4   (needs M2's trust for diaspora)
```

M3 can run in parallel with M2 if capacity allows; they don't depend on each other.

### M1 — First sale closed
Goal: a buyer and a seller each get matched with a sale agent, sign a mandate and close as SOLD.

Stories: **1, 2, 4, 7, 10, 16, 18** (only the copy M1 shows), **19** (basic funnel counts).

- Why together: without 7, sale mandates reach rental-only agents; without 10, the contract states a
  one-month-rent commission; without 16, there is no outcome to measure.
- Exit signal: first SALE mandate closed with a `finalPrice`.
- Risk: sale agents are self-declared until M2. Mitigate by switching sales on for one tenant only, or by
  approving sale opt-ins by hand.
- To check first: if mandates expire or send reminders on a rental timeline today, move **11** into M1.

### M2 — Trust & match quality
Goal: sellers and buyers meet serious counterparts, and the right agents get the deals.

Stories: **5, 13, 8, 6**.

- Why after M1: 6 and 8 need real closed sales and matches to tune; 5 and 13 replace M1's manual/limited opt-in.
- Exit signal: better visit → sold rate than M1, fewer cancelled sale mandates.

### M3 — Deal flow (visit to sale)
Goal: model the steps between "the buyer likes it" and "sold".

Stories: **12, 14, 15, 11** (unless moved to M1), **17**.

- Why together: offers only make sense with repeat visits before and the notary checklist after; 17 replaces the
  jump from "visited" straight to SOLD.
- Exit signal: time from offer to sale, share of sales closed through a recorded offer.

### M4 — Reach
Goal: more deals per agent and more buyers.

Stories: **3, 9**.

- Why last: both grow a funnel that already works; diaspora buyers need M2's trust since they can't visit in person.
- Exit signal: share of sale mandates from abroad, share of sales closed in co-brokerage.

### Not placed — payments
Payment and commission collection stays out of scope, as in the MVP. Sale commissions are large, so a payments
milestone may be worth adding after M1 once sales are proven. To decide.

## Story 1 — detail

### Who the buyer is, and what the agent does for them
- Stakes 10× to 100× a rental, search lasts months instead of weeks.
- Seriousness is the key signal: the wizard collects the *declared* financing so dispatch can route and the agent
  can qualify; proof is collected later by the agent (story 13).
- Many buyers live abroad (story 3): contact and preferred contact period must work across time zones.
- The agent finds and shows a property, but also checks the title and guides the purchase: the buyer's title
  requirement is part of the brief.

### Wizard changes per step

| Step | Change for a purchase |
|---|---|
| need | New first question *Louer / Acheter*, pre-filled from the origin listing (`originListingId`) when there is one. |
| area | Unchanged (city + up to 3 neighbourhoods). |
| property | LAND also allowed under RESIDENTIAL ("buy land to build a house"); other types unchanged. |
| amenities | Unchanged ("nice to have" marking is out of scope). |
| budget | "Budget maximum" instead of "Loyer mensuel", with a sale-level minimum; new **financing** (cash / bank loan / transfer from abroad / not decided) and **timeline** (< 3 months / 3–6 / 6–12 / just looking), replacing `movingPeriod`. |
| contact | Unchanged (country of residence belongs to story 3). |
| review | Purchase wording: "Achat", "Budget", "Financement". |
| confirmation | Explains the next step for a buyer: a sale-qualified agent will contact them. |

### Acceptance criteria
1. *Acheter* creates a SEARCH mandate with `listingType = SALE`; *Louer* keeps today's flow with no regression.
2. A visitor coming from a SALE listing has *Acheter* pre-selected and can change it.
3. Budget and timeline rules depend on the type: a rent-level amount is refused for a purchase, and the reverse.
4. Financing and timeline are stored on the mandate and shown to the agent in the mandate offer (Partner Central,
   e-mail, Telegram).
5. Every customer-facing label (wizard, review, e-mails, public portal) says *Achat / Budget*, never
   *Location / Loyer*, for a sale mandate.
6. Every wizard step works without JavaScript.
7. A SALE search only matches SALE listings (already enforced by the `Matcher`; add a test).

### Impact
- **Prototype first:** `ndopify-partner-central/src/main/html/` *find-property* gets the buy/rent choice and the
  sale budget step; validated before any DTO / server / Partner Central change.
- **DTO:** financing and timeline on the mandate → DTO release.
- **Server:** new Flyway migration for the new mandate columns; labels in offer e-mails and bot messages.
- **Partner Central:** type-dependent wizard rules; financing and timeline on the mandate detail page.

### Dependencies
Story 1 must not go live alone:
- **Story 7:** otherwise sale searches reach rental-only agents. Minimum fallback: send sale mandates to
  `MANUAL_ASSIGNMENT`.
- **Story 10:** the SEARCH contract states a one-month-rent commission. Ship a sale SEARCH template with it, or keep
  *Acheter* behind a tenant flag until it exists.

### Open questions
1. Financing: information for the agent only, or also a dispatch input (e.g. "bank loan" buyers to experienced
   agents)?
2. Minimum purchase budget: one tenant-level amount, or per property type (land vs villa)?
3. "Just looking" buyers: normal mandate, or a soft gate ("we'll contact you when you're ready")?
4. Release gate: tenant feature flag for *Acheter* (lets story 1 ship before 7 and 10), or release 1, 7 and 10
   together?
