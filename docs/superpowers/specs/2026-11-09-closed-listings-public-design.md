# Sold/Rented Listings Remain Public — Design

A listing whose mandate closes is moved to `SOLD`/`RENTED` by `ListingService.onMandateClosed` and keeps its URL,
photos and content. Today `ListingAccessService` only lets anonymous callers read `PUBLISHED` listings, so ndopify-web
answers 404 once a listing leaves the market. It should stay publicly reachable instead, clearly marked as sold or
rented — and without its price.

## Goals

- ndopify-web serves a `SOLD` or `RENTED` listing at its existing public URL, to anyone, indefinitely.
- The page says it is sold/rented: a warning badge ("Vendu" / "Loué") and a notice under the title
  ("Ce bien a été vendu le …" / "Ce bien a été loué le …", using `closedAt` when set).
- No price appears anywhere on a closed listing: no price card, no mobile bottom bar, no price row in the
  conditions, and no generated summary (which may quote the price) as the meta description.

## Non-goals

- Public search keeps returning `PUBLISHED` listings only: the web has no public search, and Partner Central's
  wizards must not propose closed listings.
- `DRAFT`, `PENDING`, `PENDING_VERIFICATION`, `UNDER_VERIFICATION`, `VERIFIED`, `PUBLISHING`, `EXPIRED` and
  `CANCELLED` stay hidden from anonymous callers.
- The tokenized match and visit pages keep their current behaviour: a listing that is no longer `PUBLISHED` is
  `UNAVAILABLE` ("Ce logement n'est plus disponible.").
- No change to the closing flow, DTOs, schema or data.

## Server

`ListingAccessService.ensureCanRead` accepts `PUBLISHED`, `SOLD` and `RENTED` for a caller who is neither an admin nor
the listing's agent. This covers both `GET /v1/listings/{id}` and `GET /v1/listings/{id}/images` (the latter still
filters to `VERIFIED` images). Every other read rule is unchanged, including `restrict(SearchListingRequest)`.

## Web

`ListingPages.of` keeps building the page from the same listing fields, and adds one field to `ListingPage`:
`closedNotice: String?` (null while the listing is not closed).

- `typeBadge` becomes "Vendu" for `SOLD` and "Loué" for `RENTED`, replacing "À vendre" / "À louer"; the template
  styles it with the warning tokens.
- `closedNotice` is "Ce bien a été vendu/vendu le <date>." (no date when `closedAt` is null).
- `metaDescription` is the notice followed by the title, so no generated price leaks.
- `terms` and `asideTerms` are empty; the conditions section is rendered only when `terms` is not empty.
- The price card, the mobile bottom bar and the visit popup's price are rendered only when `closedNotice` is null.
- Photos, description, specs, amenities, audit note and location stay visible. The closing date appears only in the
  notice.

The listing body fragment is shared with `match.html`, which never receives a closed listing (it is `UNAVAILABLE`),
so its existing price rendering is unaffected.

## Testing

- `GetListingEndpointTest` (ndopify-server): anonymous reads of a `SOLD` and a `RENTED` listing return 200; the
  existing `DRAFT` case still returns 404.
- `ListingPagesTest`: `SOLD` and `RENTED` set `typeBadge`, `closedNotice`, the sanitized meta description, and empty
  terms/aside terms.
- `ListingTemplateTest`: a closed page renders the warning badge and notice, and renders no price card, bottom bar,
  or conditions table.
