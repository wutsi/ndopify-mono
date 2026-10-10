---
name: init-partner-central
---

Build the Ndopify Partner Central website in three stages, using the skills `neo-user-journey`, `power-design`, and
`impeccable` in sequence.

Output location: write all generated HTML (and any page-specific assets) under
`modules/ndopify-partner-central/src/main/html/` — the existing

- `modules/ndopify-partner-central/src/main/html/assets/images/` directory already holds the checked-in logo/icon
  assets.
- `modules/ndopify-partner-central/src/main/html/assets/css/` for CSS files.
- `modules/ndopify-partner-central/src/main/html/assets/js/` for JavaScript files.

Language: all generated page content — copy, labels, microcopy, form field names, error/validation messages,
button text — must be in French. The source docs and the CTA strings below are all French; don't default to
English for anything not explicitly quoted in this file.

# Content Generation Sequence

## Stage 1 — Plan with neo-user-journey:

Create journey maps for the three audiences/flows before any HTML exists.

## Stage 2 — Generate with power-design (website path):

Brand: use this repo's `DESIGN.md` as the token source instead of Firecrawl.

Scope: multi-page, self-contained HTML/CSS, light theme only via semantic tokens.

## Stage 3 — Verify with impeccable critique, then fix:

Run the full dual-agent critique (design review + mechanical detector) against all the generated pages. For the three
wizard pages specifically, apply the "Form-heavy / wizard" persona set (Jordan, Sam, Casey) from impeccable's own
table — cross-check its findings against Stage 1's synthetic-persona results from neo-user-journey; a disagreement
between the two is worth surfacing, not silently resolving.

Treat every skipped-heading, low-contrast, and design-system-color/radius finding as a bug, since power-design's
rulebook already promised those wouldn't exist.

Fix everything found, then re-run impeccable detect to confirm a clean result. If it still finds issues, fix those
and re-run again — repeat until a detect pass comes back clean, up to 3 fix/re-detect cycles total. If issues
remain after the 3rd cycle, stop and report the remaining findings instead of looping further — treat a finding
that survives 3 fix attempts as a sign it may not be fixable without a source-doc or design-token change (e.g. a
contrast ratio baked into the brand's own primary color), not as something to keep forcing.

# Additional Instructions

- The content of the marketing page should be tailored to the audience's needs and concerns, highlighting the benefits
  of using Ndopify for their specific situation.

# Content Sources

- Partner Central
  content: https://docs.google.com/document/d/e/2PACX-1vQcgPTXRGMs0Z-gQMraoPUqBt1jhIqXBZNR0zNNqAeSqhPOmVSu_F8dWmeC6qYQW1Z8xAncLpkIg2Hf/pub
- Platform
  pitch: https://docs.google.com/document/d/e/2PACX-1vQ4T7QF-5feYdO3ks_i43Qj80QTYfflt_PBjCDS7dofdKNE3UBIcBJhtjTHzVMhaeJGnAZhYo7TA2DN/pub
- Platform vision &
  mission : https://docs.google.com/document/d/e/2PACX-1vSzZZ6EU3xv3bM186dJQ00Kg0Nytky74sLSTouyPTFCMqOQ-lgjYEbIPjbEDklmIZedfat_UxeAufdj/pub
