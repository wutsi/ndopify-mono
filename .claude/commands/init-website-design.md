---
name: init-website-design
---

This skill generates a `DESIGN.md` file describing design patterns, colors, typography, spacing, and components, so an
AI agent can generate consistent UI for this project.

The DESIGN.md must follow the specification defined here:

- https://stitch.withgoogle.com/docs/design-md/specification

The design system content itself comes from the following source of truth; pull colors, typography, spacing, and
component definitions from it:
https://docs.google.com/document/d/e/2PACX-1vRfGmUYMyIZhBEB7v6srcrwZBB5JY7uLwpvVj6ylKNtRrN03jhTuw8ecu2tlXFnV030LolrfR5FlM07/pub

If DESIGN.md already exists:

- Create a backup copy of the existing file (if any) in the same directory, e.g. `DESIGN.md.bak`, so you can compare
  changes if needed.
- Update the existing DESIGN.md file with any new or changed content from the source doc, but do not delete any existing
  content that is still valid. If there are conflicts between the existing DESIGN.md and the source doc, resolve them by
  following the source doc's guidance, and if necessary, add comments in the DESIGN.md to explain any deviations. If the
  existing DESIGN.md is missing sections that are present in the source doc, add those sections to the DESIGN.md file.

### Logo & icon assets

Here are the logo and icon assets for this project. The following variants are checked into the repo:

- logo-light: src/main/html/assets/images/logo.png
- logo-dark: src/main/html/assets/images/logo-dark.png
- icon-light: src/main/html/assets/images/icon.png
- icon-dark: src/main/html/assets/images/icon-dark.png

Make sure to include these assets in the DESIGN.md file under the "Assets" section.

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
