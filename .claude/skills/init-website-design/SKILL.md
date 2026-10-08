---
name: init-website-design
description: Use when setting up or refreshing the design system reference for this project — generates DESIGN.md (colors, typography, spacing, components) from the canonical Ndopify design doc, for consistent AI-driven UI generation.
---

This skill generates a `modules/ndopify-website/DESIGN.md` file describing design patterns, colors, typography, spacing,
and components, so an
AI agent can generate consistent UI for this project.

The DESIGN.md must follow the specification defined here:

- https://github.com/google-labs-code/design.md/blob/main/docs/spec.md
- https://stitch.withgoogle.com/docs/design-md/specification

The design system content itself comes from the following source of truth; pull colors, typography, spacing, and
component definitions from it:
https://docs.google.com/document/d/e/2PACX-1vRfGmUYMyIZhBEB7v6srcrwZBB5JY7uLwpvVj6ylKNtRrN03jhTuw8ecu2tlXFnV030LolrfR5FlM07/pub

Write the file to the root of the current module (e.g. `modules/ndopify-website/DESIGN.md`). If DESIGN.md already
exists, regenerate it from the source doc rather than hand-merging — the source doc is authoritative.

### Logo & icon assets

Check `src/main/html/assets/images/` for checked-in logo/icon files (e.g. `logo.png`, `logo-dark.png`, `icon.png`) before
writing the Overview section's asset guidance. If local files exist, reference their actual repo paths and
describe which variant they are (e.g. dark-on-transparent, for light surfaces) rather than just pointing at the
external Canva/Drive links from the source doc. Only fall back to the external links for variants that aren't
checked into the repo yet, and call out explicitly which variants are missing — don't assume a missing variant
exists or invent one.

In addition to the Overview prose, add a frontmatter `assets` block mapping each checked-in variant to its repo
path (relative to the module root, e.g. `src/main/html/assets/images/logo.png`), so the paths are machine-readable and not
just described in prose. Use descriptive keys per variant, e.g. `logo-light`, `logo-dark`, `icon-light`. Leave out
any variant that isn't checked in — don't invent a path for a missing asset, even a placeholder one. Example:

```yaml
assets:
    logo-light: src/main/html/assets/images/logo.png
    logo-dark: src/main/html/assets/images/logo-dark.png
    icon-light: src/main/html/assets/images/icon.png
```

This key isn't part of the DESIGN.md spec's defined token groups, but unknown top-level content is preserved by
consumers per the spec, and it doesn't trigger lint errors or warnings in the official linter — verify this still
holds after regenerating.

### Validation gate (required — do not skip)

Do not consider DESIGN.md done just because it was written. After generating or regenerating it, validate it against
the spec using the official linter, and fix and re-run until it passes clean:

```
npx @google/design.md lint DESIGN.md
```

If `npx` / the package isn't available in this environment, say so explicitly rather than skipping validation
silently — don't just eyeball the spec and assume conformance.

### Additional Instructions

- Before overwriting DESIGN.md, create a backup copy of the existing file (if any) in the same directory, e.g.
  `DESIGN.md.bak`, so you can
  compare changes if needed.
