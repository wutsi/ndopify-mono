# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Primary: agents, of two kinds.** Démarcheurs and official real estate agents attached to an agency — the sign-up
  and profile record them as `INDEPENDANT` and `REAL_ESTATE_AGENT` ("Rattaché à une agence immobilière"). They work
  in the field, mostly from a phone, in French; they receive customer mandates, give them visibility, run visits and
  close the deal. Ndopify exists to feed them mandates and get those mandates to a closed deal, so their app (Partner
  Central) sets the bar for every other surface.
- **Customers.** The mandate holders: a tenant or buyer searching for a home (*Trouver un bien*, a SEARCH mandate)
  and an owner or seller offering a property (*Proposer un bien*, a LISTING mandate), for rent or for sale. They enter
  their need through a public wizard, sign the mandate contract that approves it, then answer match proposals.
- **Contract parties.** Customers and property owners who read and sign a contract from the e-signature portal.
- **Public visitors.** The audience a mandate's visibility reaches. They browse a tenant's marketing site, a
  published listing or a match proposal — and much of that reach happens in WhatsApp groups and channels, where
  agents/démarcheurs build their audiences and share listing links.

## Product Purpose

Ndopify matches each customer mandate — a search or a property offer, for rent or for sale — with the right
démarcheur or real estate agent, then gives that mandate the maximum visibility: a public listing, reach into other
agents' inventories through co-brokerage, and active match proposals to the customer. The point of that visibility is
concrete: turn the mandate into visits, and the visits into a closed deal — a rental or a sale at a final price — as
quickly as possible.

Success means mandates do not stall. Each one reaches the right agent, is seen by the customers it can serve, produces
visits, and closes at a final price; an agent can carry several mandates at once without losing track of the next step
on any of them.

## Positioning

**Mandate-to-deal speed, on verified ground.** A competitor can copy a listing board or a CRM. It cannot truthfully
copy the chain: a customer mandate is routed exclusively to the best-scoring local agent, then distributed on three
channels at once (public listing, co-brokerage matching, active proposals), over an agent who passed KYC, a property
that passed an on-site checklist, and a mandate contract signed with a publicly verifiable signature. Trust is what
makes the visibility worth acting on; speed is what the visibility is for — and both exist to close the deal, not to
collect a signature.

## Operating Context

- **The mandate is the unit of work.** One mandate follows one chain: intake (public wizard) → exclusive scored
  dispatch to an agent → the customer signs the mandate contract, which approves the mandate → follow-up, listing
  publication and/or match proposals → visits → the deal closed at a final price (a rental or a sale), through a
  match or outside it. Every surface is a step on that chain, not a destination of its own; the chain ends on the
  closing, never on the signature.
- **Visibility has three levers, all required.** (1) Public exposure: a PUBLISHED listing with its public page and the
  tenant's public search. (2) Co-brokerage reach: matches drawn from the agent's own inventory first, then from other
  agents' published listings. (3) Active matching: the platform notifies the agent, proposes matches to the customer,
  and expires unanswered proposals. Lean on one lever and the mandate underperforms.
- **WhatsApp is where the audience already is.** Agents/démarcheurs build their audience in WhatsApp groups and
  channels and share the listing or match link there. WhatsApp is also the contact rail: agents and mandates carry a
  WhatsApp number (`Agent.whatsappNumber`, the mandate's contact), and a VIDEO visit is a WhatsApp video call. The
  public page must arrive as a credible, previewable link (title, image) on a phone, without an app install. There is
  no WhatsApp Business API integration or automated messaging today: the share and the call are the agent's, the
  page is the platform's.
- **Time-boxed dispatch.** An offer is exclusive to one agent and expires; the platform re-dispatches it. Designs
  must show the deadline and the consequence of missing it.
- **Field-first agents.** Agent work happens on a phone, often on mobile data; pages are server-rendered and must
  work without JavaScript, which only enhances. Actions are short, resumable and explicit about their state.
- **Multi-tenant.** Each tenant is a separate brand and market with its own URLs (`partnerCentralUrl`,
  `publicPortalUrl`, `eSignaturePortalUrl`), reference data (cities, neighbourhoods) and formatting (locale, time
  zone, currency). A request is resolved to its tenant by the URL it arrives on. No tenant ever sees another tenant's
  data.
- **Cameroon now, Africa-wide later.** Current evidence is Cameroonian: the +237 phone example, E.164 storage, MTN
  Mobile Money as the only wired gateway. The product targets mobile-money payment integrations across Africa, so
  country, currency, language and payment-gateway assumptions stay tenant-level, never hard-coded.
- **French product surface.** UI, e-mails and contracts are in French; code, comments, logs and docs are in English.

## Capabilities and Constraints

- **Mandate intake:** public five-step *Rejoindre Ndopify* sign-up (records the agent type, independent vs
  agency-attached), the seven-step *Trouver un bien* (SEARCH mandate) and *Proposer un bien* (LISTING mandate)
  wizards. These wizards' rules are the only validation a mandate gets; ndopify-server does not validate mandates
  itself yet. A mandate carries a listing type, rent or sale, and a sale carries its financing and ownership documents;
  the public copy of both wizards is written for a rental today, while the domain already models `SALE`.
- **Agent matching and dispatch:** SEARCH and LISTING mandates with a lifecycle (pending assignment → assigned →
  pending approval → approved → closed / cancelled), where signing the mandate contract is what moves a mandate to
  approved; exclusive scored dispatch with expiry and re-dispatch, agent follow-up.
- **Mandate closing:** only an APPROVED mandate closes. The agent records the deal at a final price, either through a
  match — closing both sides at the same price — or outside any match; the mandate keeps `finalPrice`, the match (or
  `dealOutside`), `closedAt` and its `age` (whole days from approval to closing). Closing cancels the mandate's open
  visits and expires its pending offers. The price is recorded, not collected: the platform holds no transaction or
  payment record of the deal.
- **Visibility — public:** on-site checklist per section with photos, asynchronous verification, LLM-generated
  description and hero image, publication to a public URL, public search of PUBLISHED listings, the listing's public
  page and the tenant's marketing site. The public pages carry Open Graph/Twitter card metadata, so a link pasted
  into a WhatsApp group previews with its title and image.
- **Visibility — co-brokerage:** listings scored against APPROVED SEARCH mandates, the agent's own inventory first,
  then other agents' published listings.
- **Visibility — active matching:** matches notified to the agent, proposed to the customer, accepted or rejected
  with an expiry, and expired when unanswered. `ListingStats` reports matches and visits per listing — the activity
  signal, since listings carry no counters of their own.
- **Visits:** ON_SITE or VIDEO visits with a 4-digit check-in code, scheduled by the agent, checked in/out, marked
  no-show or cancelled, then rated. A VIDEO visit is a WhatsApp video call.
- **Contracts:** numbered per tenant, one type per need — a rental search or listing (`SEARCH`, `LISTING`), a sale
  search or listing (`SALE_SEARCH`, `SALE_LISTING`), and the lease agreement (`LEASE_AGREEMENT`, numbered `BAIL`). The
  mandate contract is signed by the customer only (the agent is a party, not a signer); the last required signature
  makes the contract SIGNED and, in the same transaction, the mandate APPROVED. Only the party itself can sign, from
  the e-signature portal; the signed PDF carries a QR code opening a public verification page. Closing a mandate does
  not create the lease agreement automatically: auto-creating it on closing is planned for a future version, and the
  `LEASE_AGREEMENT` type stays available on its own until then.
- **Payments:** mobile-money gateway abstraction; only MTN is implemented today. It backs the agent's mobile-money
  payment method and the KYC match, not the collection of the deal price: the contract states a per-visit fee and, on a
  sale, a commission rate (1–20 % of the sale price), both settled outside the platform.
- **Verification, and its limit:** agent KYC (identity documents, mobile-money account or manual check, verified
  offline by jobs), listing review and signature verification are real. **Customers are not identity-verified
  today** — their details come from the wizard. Do not present customers as verified; if that changes, record it
  here first.
- **Surfaces today:** agent app (Partner Central), e-signature portal, public marketing site, and the shared
  `ndopify-web-core` library. All web apps are stateless clients of `ndopify-server` over REST, and all
  user-visible dates and amounts go through the tenant's formatter.
- **Not built yet (do not assume):** admin console, SDK, tracking server, messaging broker, a payment/transaction
  record of a closed deal, and auto-creating the lease agreement when a rental mandate closes (planned for a future
  version). Tokens and roles for them may exist in contracts, but no code does.
- **Prototypes:** `src/main/html/` under each web app is the reviewed static prototype. Never serve it, never delete
  or clean it up; implementation follows it.
- **Tenant formatting is a rule, not a detail:** no hard-coded currency symbol, date pattern or time zone anywhere
  users can see it.

## Brand Commitments

- **Name:** Ndopify.
- **Voice:** French, direct, field-appropriate.
- **Tenant identity:** each tenant presents as its own brand on its own URLs; the platform name is not imposed on the
  tenant's customer-facing pages beyond what the tenant's site does.
- **Visual system:** recorded separately in `DESIGN.md` (Ndopify Design System); product truth lives here, visual
  rules live there.

## Evidence on Hand

- Root and per-module `README.md` files: product scope, module responsibilities, per-surface features.
- `CLAUDE.md` files: architecture, tenancy, token trust, formatting and prototype rules; `DESIGN.md` and its
  do/don't guidance.
- The mandate and contract domains: `MandateService.close` / `closeDeal`, `ContractType`, `MandateEntity.age` — the
  closing model this document describes.
- `modules/*/src/main/html/`: the approved static prototypes for Partner Central, the e-signature portal and the
  public site.
- Local seed data (`db/migration/local/`) with tenants, cities and neighbourhoods.
- **Absences future work must not fabricate:** no real customer testimonials, named agencies, benchmarks, press,
  pricing page, production metrics, time-to-visit or time-to-closing figures, or customer counts exist in the
  repository. Do not invent them.

## Product Principles

1. **Every mandate exists to close.** The measure of a mandate is visits and a deal closed at a final price — not a
   listing published, a contract signed or a status advanced. Optimize the chain, end to end.
2. **Match the right agent first.** Exclusive, scored, time-boxed dispatch gets a mandate into capable hands before
   visibility work starts; don't dilute it across agents.
3. **Maximum visibility means all three levers.** Public exposure, co-brokerage reach and active matching together;
   a page, a report or a notification alone is not distribution. Public exposure must travel as a shareable,
   previewable link into the agent's WhatsApp groups.
4. **Speed is the scoreboard — and it must be real.** Collapse the steps between mandate, first visit and closing;
   show the deadline and the true next action, and never imply progress that has not happened.
5. **Verification makes the visibility credible.** KYC on the agent, the on-site checklist, the reviewed listing and
   the verifiable signature are what let a customer act on a fast proposal and sign. Keep the trust chain intact when
   adding speed.
