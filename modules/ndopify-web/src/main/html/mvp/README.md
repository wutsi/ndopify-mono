# ndopify-web — MVP prototype

The public site's prototype for the MVP. It covers the MVP's three pillars, and only what the platform implements
(ndopify-server, Partner Central, the e-signature portal):

1. **Agent subscription**: `agent.html` → Partner Central's `/join`, then KYC (ID card front and back, selfie, a
   Mobile Money account in the agent's own name, checked automatically).
2. **Matching tenants and owners with an agent**:
   - `index.html` (tenant) → `/find-property` creates a SEARCH mandate.
   - `bailleur.html` (owner) → `/offer-property` creates a LISTING mandate.
   - `annonce.html`, a listing's public page: *Être contacté par un agent* — a SEARCH mandate seeded from the listing,
     or, when the visitor already has one for that city and type, an accepted match the agent is told to call about.
   - `proposition.html` is a property the agent proposes to the tenant (the match).
3. **Visit management**: `visite.html`. The tenant requests a visit from `proposition.html` (on site or by WhatsApp
   video, once the mandate is signed). The agent confirms it, the tenant gets a code, the agent checks the tenant in
   and out, and the tenant gives feedback.

The previous prototype, from before the move to mandates and the MVP cut, is kept as is in `../v1/`. Never serve
either folder, and never delete or "clean up" anything in them.

## Out of the MVP, never promised here

- Pass Visite, and any payment through the platform: visit fees, agent visit pay, commission split or payout.
- Vetting tenants, and files for the owner to review.
- Title-deed checks, lease signature, rent collection.
- Marketplace features: browsing listings, the "Poser une question" AI chat on a listing.
- Promises with no implementation: "l'équipe examine votre dossier", experience-based assignment, listing videos,
  NIU.

The mandate contracts still state a commission of 1 month's rent, due when the lease is signed. The pages say so.
Nothing collects it.

## Changes from v1

| Page | Change |
|---|---|
| `index.html`, `bailleur.html`, `agent.html` | Copy rewritten around the mandate, the agent and the visit, with the real rules: agent dispatch by neighbourhood, availability, response time and client ratings; 1 h to accept an offer; at most 5 mandates at a time; e-signature of the mandate; matching; visit code; reminder the day before. The calls to action point to Partner Central (`https://partner.ndopify.com` stands for the tenant's `partnerCentralUrl`). |
| `rechercher.html`, `louer.html`, `joindre.html` | Not in the MVP prototype: the wizards are Partner Central's (see its own prototype). |
| `annonce.html` | The call to action is now "Être contacté par un agent" (it asks an agent to call, it doesn't book a visit), and the confirmation says "Un agent vous contacte sous 24 h pour organiser la visite", with no steps list. The chat drawer is removed, with its JS and CSS. The SOLD/RENTED state is described in a comment. |
| `proposition.html` | Answers are final: no "Finalement, pas pour moi" and no "changer d'avis". New `ACCEPTED_NO_VISIT` state (`?state=accepted-no-visit`): interested, but the mandate isn't signed yet, so the tenant contacts the agent. The page says the agent is told of the answer. |
| `visite.html` | No exact address or "Itinéraire" link: the agent gives them before the visit, as the e-mails say. The 3-step progress and the "À prévoir" / "Le jour J" checklists are kept. |

## Not built yet

These are in this prototype but not in `src/main/resources/templates/`:

- `listing.html`: the chat drawer is still served (with its canned answers), with the `#visit` popup in the page for
  SOLD/RENTED listings; its call to action, popup and confirmation match this prototype's.
- `match.html`: the flash messages say the agent was told (ndopify-server now notifies it).
- `visit.html`: the 3-step progress, "À prévoir" / "Le jour J", and the line "Serge vous communique l'adresse exacte…".
- `index.html`, `owner.html`, `agent.html`: the rewritten copy.
