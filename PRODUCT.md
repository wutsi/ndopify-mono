# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Primary: agents, of two kinds.** Mobile-money agents (*démarcheurs*) and official real estate agents attached to an
  agency — the sign-up and profile record them as `INDEPENDANT` and `REAL_ESTATE_AGENT` ("Rattaché à une agence
  immobilière"). Both work in the field, mostly from a phone, in French, and do the same job: sign up, pass KYC,
  receive mandates, check properties on site, publish listings, propose matches and run visits. Ndopify is built on
  their networks, so their app (Partner Central) sets the bar for every other surface.
- **Customers.** Two kinds, both served by public wizards: tenants searching for a rental (*Trouver un bien*,
  creating a SEARCH mandate) and owners offering a property (*Proposer un bien*, creating a LISTING mandate). They
  also answer match proposals and sign contracts.
- **Contract parties.** Customers and landlords who read and sign a contract from the e-signature portal.
- **Public visitors.** People browsing a tenant's marketing site, a published listing or a match proposal.

## Product Purpose

Ndopify turns a rental request or offer into a signed contract through its local agent networks — mobile-money agents
and official real estate agents. A customer submits a mandate; the platform offers it exclusively to the
best-scoring agent for its tenant; that agent follows it up, produces and verifies the listing (or matches existing
listings), arranges visits, and the contract is signed online. Success means the customer reaches a signed contract
with minimal back-and-forth, the agent can run several mandates from a phone, and mobile-money payments settle the
money side.

## Positioning

**Trust through verification.** KYC on the agent, a mandatory on-site checklist and photos on every listing,
asynchronous review before publication, e-signed contracts, and a public QR verification page for each signature.
A competitor can copy a listing board; it cannot truthfully claim that the property, the agent and the signature were
each verified inside the same chain.

## Operating Context

- **Multi-tenant.** Each tenant is a separate brand and market with its own URLs (`partnerCentralUrl`,
  `publicPortalUrl`, `eSignaturePortalUrl`), reference data (cities, neighbourhoods) and formatting (locale, time
  zone, currency). A request is resolved to its tenant by the URL it arrives on. No tenant ever sees another tenant's
  data.
- **Field-first agents.** Agent work happens on a phone, often on mobile data; pages are server-rendered and must
  work without JavaScript, which only enhances. Actions are short, resumable and explicit about their state.
- **Cameroon now, Africa-wide later.** Current evidence is Cameroonian: the +237 phone example, E.164 storage, MTN
  Mobile Money as the only wired gateway. The product targets mobile-money payment integrations across Africa, so
  country, currency, language and payment-gateway assumptions stay tenant-level, never hard-coded.
- **French product surface.** UI, e-mails and contracts are in French; code, comments, logs and docs are in English.
- **Agents' mandates are time-boxed.** An offer is exclusive to one agent and expires; the platform re-dispatches it.
  Designs must show the deadline and the consequence of missing it.

## Capabilities and Constraints

- **Agent lifecycle:** public five-step sign-up wizard (*Rejoindre Ndopify*) that records the agent type
  (independent vs attached to an agency), e-mail OTP sign-in, KYC (identity documents, mobile-money account or manual
  check, verified offline by jobs), agent profile and coverage area.
- **Mandates:** SEARCH and LISTING mandates with a lifecycle (pending assignment → assigned → pending approval →
  approved → closed / cancelled), exclusive scored dispatch with expiry and re-dispatch, agent follow-up.
- **Listings:** on-site checklist per section with photos, asynchronous verification, LLM-generated description and
  hero image, publication to a public URL, public search of PUBLISHED listings.
- **Matching and visits:** listings scored against APPROVED SEARCH mandates (an agent's own inventory first, then
  other agents' published listings — co-brokerage), proposal to the customer, accept/reject with expiry, ON_SITE or
  VIDEO visits with a 4-digit check-in code, no-show handling and rating.
- **Contracts:** numbered per tenant, signature requested per party, last required signature makes the contract
  SIGNED; only the party itself can sign, from the e-signature portal; the signed PDF carries a QR code opening a
  public verification page.
- **Payments:** mobile-money gateway abstraction; only MTN is implemented today.
- **Surfaces today:** agent app (Partner Central), e-signature portal, public marketing site, and the shared
  `ndopify-web-core` library. All web apps are stateless clients of `ndopify-server` over REST, and all
  user-visible dates and amounts go through the tenant's formatter.
- **Not built yet (do not assume):** admin console, SDK, tracking server, messaging broker. Tokens and roles for
  them may exist in contracts, but no code does.
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
- `modules/*/src/main/html/`: the approved static prototypes for Partner Central, the e-signature portal and the
  public site.
- Local seed data (`db/migration/local/`) with tenants, cities and neighbourhoods.
- **Absences future work must not fabricate:** no real customer testimonials, named agencies, benchmarks, press,
  pricing page, production metrics or customer counts exist in the repository. Do not invent them.

## Product Principles

1. **The agent carries the relationship.** Every decision protects a field agent's (mobile-money or estate agency)
   ability to move a mandate forward from a phone, in French, on a weak connection.
2. **Trust is verified, not claimed.** Show the verification step (KYC, checklist, signature, QR check) instead of
   asserting reliability.
3. **One demand, one owner at a time.** Exclusivity plus a visible deadline is what makes dispatch fair; don't hide
   timing or ownership.
4. **Every surface belongs to a tenant.** Brand, data, locale, currency and market are the tenant's, resolved from
   the URL; no cross-tenant leakage or shared assumptions.
5. **State honesty over optimism.** Wizards, statuses and blocked views reflect the server's real state; never let a
   page imply progress that has not happened.
