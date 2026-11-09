# Share a Listing from Partner Central — Design

Partner Central's listing page (`templates/listing.html`) already knows the listing's public URL for `PUBLISHED`,
`RENTED` and `SOLD` listings (`ListingView.publicUrl`, built from the tenant's `publicPortalUrl` and the listing's
`url`). It shows it as "Voir l'annonce en ligne". Agents need to send that page to a client from their phone; today
they must copy it by hand.

A "Partager" button sits next to the public link and shares that public URL, never the internal checklist URL.

## Goals

- The button appears only when the listing has a public URL: `PUBLISHED`, `RENTED` or `SOLD`, and a tenant with a
  `publicPortalUrl`.
- On a phone it opens the system share sheet with the listing's title and public URL.
- On a browser without the Share API (desktop) it copies the public URL instead and briefly says "Lien copié".
- The prototype demonstrates the same behavior.

## Non-goals

- No server endpoint, DTO, schema or ndopify-server change.
- No sharing from the Espace agent's cards or the mandate page: only the listing page.
- No custom share menu (WhatsApp, e-mail, …): the system sheet / clipboard is enough.

## Approaches

- **Web Share API with a clipboard fallback (picked).** Matches the public site's pattern, opens the native sheet on
  phones, and stays useful on desktop. The button is server-rendered only when the public URL exists, and revealed by
  JS so a browser without scripts still uses "Voir l'annonce en ligne".
- **Web Share API only, hidden where unsupported.** Simpler and closer to the public site, but the agent-facing app is
  often used on desktop, where the button would silently vanish.
- **A copy-link link rendered server-side.** Works without JS, but cannot open the phone's share sheet and adds a
  second visible control next to the public link.

## Design

- `templates/listing.html`: next to "Voir l'annonce en ligne", a `button.btn.btn--secondary.btn--compact.listing-share`
  (the mandate page's WhatsApp button style) with
  `data-listing-share`, `hidden`, `data-share-title="{listing title}"` and `data-share-url="{publicUrl}"`; rendered
  only inside the existing `th:if="${l.publicUrl}"` block. Label "Partager", with the public site's share icon.
- `public/assets/js/listing.js`: a new `initShare` in the checklist page's init reveals the button when
  `navigator.share` or `navigator.clipboard` exists; on click it shares `{ title, url }` when possible, otherwise
  writes `url` to the clipboard and sets the label to "Lien copié" for two seconds (restored afterwards). A dismissed
  share sheet is ignored. Existing `[data-listing-public-url]` link unchanged.
- `public/assets/css/listing.css`: the public link and the button on one wrapping row (`.listing-public`), aligned,
  wrapping on narrow screens; `.listing-share[hidden] { display: none; }` because `.btn` sets `display`.
- Prototype (`src/main/html/listing.html`, `assets/js/partner-central.js`, `assets/css/listing.css`): same markup and
  behavior. The prototype has no public portal, so `initListing` builds a stub public URL for `PUBLISHED`, `RENTED`
  and `SOLD` and uses it for both the existing link and the share button; add the "Loué" / "Vendu" tags so those
  states render.

## Testing

- `ListingTemplateTest`: the button renders with the public URL and title on a `PUBLISHED` listing, and on `RENTED` /
  `SOLD`; it is absent on a draft and on a published listing without a `publicPortalUrl`.
- No JS test framework: the script follows the existing `initShare` pattern and is verified in the prototype manually.
