---
target: Partner Central all pages
total_score: 27
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:/Users/htchepannou/Perso/ndopify-mono/modules/ndopify-partner-central/src/main/html"
timestamp: 2026-10-10T18-55-20Z
slug: modules-ndopify-partner-central-src-main-html
---
Method: dual-agent (A: design review · B: detector+browser)

## Design Health Score — 27/40 (Acceptable)
H1 3 (step-2 scroll hides progress) · H2 3 · H3 2 (no wizard history; menu logout trap) · H4 3 · H5 3 (paste truncation) · H6 3 · H7 2 · H8 3 (unknown-email alert = hint styling) · H9 3 (silent upload/NIU errors) · H10 2 (no support path)

## Design Specificity
Content product-specific; visual language generic onboarding. Detector: 25 findings — 5 real (white on #1d7edf 4.1:1, brand token), 20 false positives (disabled contrast, Montserrat, empty preview img, cramped-padding static). Missed by detector: input border 1.33:1.

## Priority Issues
- [P1] Wizard steps push no history (Android Back exits) — harden
- [P1] Account menu keyboard open focuses Déconnexion — harden
- [P1] Input borders 1.33:1 — polish
- [P2] OTP paste truncated by maxlength — harden
- [P2] Spacing collapse .review/.tracker; silent upload/NIU errors — layout/harden

## Persona Red Flags (Jordan/Sam/Casey)
Disagreement with Stage 1: native disabled buttons unreachable by Tab, so the aria-describedby explanation never reaches Sam. Missed by Stage 1: history, logout trap, 66x19 Modifier targets, unannounced "Envoi en cours", return-to-review Précédent.

## Minor
"(bientôt disponible)" 3.95:1; logo alt; index.html outside column; duplicated warning; off-token clamp minima; unused .review__head; OTP attempts reset on resend.
