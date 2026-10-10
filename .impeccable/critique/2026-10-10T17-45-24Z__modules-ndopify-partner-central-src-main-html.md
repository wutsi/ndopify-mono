---
target: Partner Central pages (post doc update)
total_score: 28
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 4
target_identity: "file:/Users/htchepannou/Perso/ndopify-mono/modules/ndopify-partner-central/src/main/html"
timestamp: 2026-10-10T17-45-24Z
slug: modules-ndopify-partner-central-src-main-html
---
Method: dual-agent (A: design review · B: detector + browser) — run after source-doc update (2026-10-10)

## Design Health Score (pre-fix): 28/40
1 Status 3 · 2 Real world 3 · 3 Control 3 · 4 Consistency 3 · 5 Error prevention 2 · 6 Recognition 3 · 7 Flexibility 2 · 8 Minimalist 3 · 9 Recovery 3 · 10 Help 3

## Priority issues (fixed in cycle 1)
- [P1] OTP attempts reset on every resend (unlimited guesses); resend confirmation invisible → 3-code budget per sign-in, visible "Nouveau code envoyé".
- [P1] maxlength=6 truncated pasted "123 456" → maxlength 12, digits filtered.
- [P1] Preamble alerts on every KYC step; photos lost on reload → preamble on step 1 only; photos persisted in IndexedDB until submit.
- [P1] No drop zone; English native picker text → preview frame is a French-labelled drop zone, native input visually hidden but focusable.
- [P2] Two primary CTAs on index → join CTA outline until unknown-email branch promotes it.
- [P2] Low-res images accepted; contradictory 5 Mo hint → min 1000 px long edge, hint reworded.
- Minor: aria-disabled submit buttons explain on tap; long-email overflow; faux bold; duplicate "5 minutes"; review shows "Photo ajoutée".

## Detector
CLI 7 → 6 (all white on #1d7edf, 4.12:1, DESIGN.md tokens). Browser overlay: index + connexion clean; others low-contrast (same) + overused-font (CLI waiver not honored by overlay).
