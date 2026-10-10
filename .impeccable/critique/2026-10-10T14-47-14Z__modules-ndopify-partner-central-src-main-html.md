---
target: Partner Central pages
total_score: 28
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
target_identity: "file:/Users/htchepannou/Perso/ndopify-mono/modules/ndopify-partner-central/src/main/html"
timestamp: 2026-10-10T14-47-14Z
slug: modules-ndopify-partner-central-src-main-html
---
Method: dual-agent (A: design review · B: detector + browser)

## Design Health Score (pre-fix): 28/40
1 Status 3 · 2 Real world 3 · 3 Control 3 · 4 Consistency 3 · 5 Error prevention 3 · 6 Recognition 3 · 7 Flexibility 2 · 8 Minimalist 3 · 9 Recovery 3 · 10 Help 2

## Design specificity
Copy is product-specific (sautage, 70/30, 35/35, CNI↔MoMo holder check); layout is a generic SaaS landing spine. Agent vérifié badge under-used.

## Priority issues (all fixed in cycle 1 unless noted)
- [P1] Unknown-email notice had no CTA → CTA added in notice.
- [P1] Login field below fold on mobile → form moved directly under H1/lead (y=641 @390).
- [P1] NIU hard-blocks agents without one; no support channel → "Je n'ai pas de NIU" help added; support channel NOT added (no contact info in sources).
- [P2] Wizard preamble repeated on every step incl. confirmation → lead only on step 1, preamble hidden on confirmation.
- [P2] Silent Enter / unannounced steps / silent disabled submit → role=alert errors, polite step live region, Enter = Suivant, visible hints for disabled buttons.
- [P2] Pay table "Pour Ndopify" column off-screen @390 → min-width removed.

## Detector (B)
58 findings → 11 after 2 fix cycles. Remaining: low-contrast #fff on #1d7edf 4.12:1 (DESIGN.md primary + button tokens). overused-font waived inline (Montserrat mandated by DESIGN.md).
