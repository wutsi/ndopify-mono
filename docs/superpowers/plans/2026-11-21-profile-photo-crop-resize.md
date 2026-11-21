# Profile Photo Crop and Resize Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On the profile photo page only, crop the chosen photo to its centred square and shrink it to at most 400×400, white-filled JPEG at 0.85, before the direct upload or the multipart fallback sends it; the KYC steps keep their 2000 px, no-crop behaviour.

**Architecture:** `kyc.js`'s one `shrink()` gains a profile mode (square crop, `PROFILE_SIDE = 400`, white canvas); `initUpload` passes it `page === "profile-photo"`. The prototype's `toSquare()` (`src/main/html/assets/js/profile.js`) is aligned first — its `PHOTO_SIZE` was 480 and it neither white-filled nor avoided enlarging — and the served code then mirrors it. No DTO, server or web-core change.

**Tech Stack:** Vanilla JS (canvas 2D), Thymeleaf, the repo's existing direct upload (`direct-upload.js`).

**Spec:** GitHub issue #225 (`gh issue view 225`) — the story holds the design; there is no separate spec file.

## Global Constraints

- Code, comments and docs in English; UI text in French.
- This story changes neither `modules/ndopify-dto/` nor `modules/ndopify-web-core/`; the root `pom.xml` version properties stay as they are, and no `-SNAPSHOT`.
- `modules/ndopify-partner-central` only. `mvn verify` there must be green (ktlint + tests + JaCoCo).
- `src/main/html/` stays a prototype: never serve it, never delete or "clean up" anything in it. This story edits only `src/main/html/assets/js/profile.js`.
- KYC behaviour is unchanged: `MAX_SIDE` 2000, no crop, and the selfie camera's 3:4 crop untouched.
- The profile photo page's markup, CSS, copy and 192×192 round preview are unchanged; only the file sent changes.
- No new dependency and no JS test runner: the JS is verified in a browser (see each task).
- Conventional commits, one module per commit.

## Review Focus

- **A landscape or portrait phone photo** (4000×3000 / 3000×4000) must come out exactly 400×400, centred. Task 1 (prototype) and Task 2 (served) browser checks.
- **A photo smaller than 400 px** (ex: 300×200) must come out 200×200, not upscaled. Task 1 and Task 2 browser checks.
- **A transparent PNG** must have white, not black, transparent areas in the JPEG. Task 1 and Task 2 browser checks.
- **A KYC ID card or selfie** must stay capped at 2000 px and uncropped. Task 2 keeps the non-profile branch and its browser check.
- **A photo that can't be decoded, or a canvas that fails** must leave the original in the input so the server's type / 5 MB checks still run. Task 2 keeps `callback(null)` → original.

---

### Task 1: Align the prototype to 400 px, white, no enlargement

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/profile.js:14`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/profile.js:443-467` (`toSquare`)

**Interfaces:**
- Produces: `PHOTO_SIZE = 400`.
- Produces: `toSquare(file, callback)` — unchanged signature; `callback` gets a JPEG data URL of a square whose side is `min(min(naturalWidth, naturalHeight), 400)`, white-filled, or `null` when the file can't be read.

- [ ] **Step 1: Drop `PHOTO_SIZE` to 400**

```javascript
    var PHOTO_SIZE = 400; // px, square: the profile photo is shown round at 192 px at most
```

- [ ] **Step 2: White-fill and stop enlarging in `toSquare`**

Replace the body of `img.onload` (the `side` / `canvas` / `drawImage` / `callback` block) with:

```javascript
            img.onload = function () {
                // Centred square, never enlarged: a photo under PHOTO_SIZE keeps its own side. White first, so a
                // transparent PNG doesn't turn black in the JPEG.
                var side = Math.min(img.naturalWidth, img.naturalHeight);
                var size = Math.min(side, PHOTO_SIZE);
                var canvas = document.createElement("canvas");
                canvas.width = size;
                canvas.height = size;
                var context = canvas.getContext("2d");
                context.fillStyle = "#ffffff";
                context.fillRect(0, 0, size, size);
                context.drawImage(img,
                    (img.naturalWidth - side) / 2, (img.naturalHeight - side) / 2, side, side,
                    0, 0, size, size);
                callback(canvas.toDataURL("image/jpeg", 0.85));
            };
```

Update the function's comment above `toSquare` to say "Center-crops the photo to a square of at most PHOTO_SIZE, on white, as a JPEG data URL; never enlarged."

- [ ] **Step 3: Check it on the prototype page**

Open `modules/ndopify-partner-central/src/main/html/profile/photo.html` in a browser and, for each photo, choose it and open the preview image in a new tab (right-click → *Ouvrir l'image dans un nouvel onglet*) to read its dimensions:

- a 4000×3000 landscape → `400 × 400`;
- a 3000×4000 portrait → `400 × 400`, top and bottom cropped equally;
- a 300×200 image → `200 × 200`, not enlarged;
- a transparent PNG → white where it was transparent, never black.

Expected: all four as listed. (No JS test runner exists in this repo; this is the evidence.)

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html/assets/js/profile.js
git commit -m "feat(npc): prototype the profile photo as a 400 px white square"
```

---

### Task 2: Centre-crop and resize the served profile photo in `kyc.js`

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/kyc.js:12-19` (header comment, constants)
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/kyc.js:98-126` (`shrink`)
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/kyc.js:128-163` (`initUpload`)
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/kyc.js:353-367` (`DOMContentLoaded`)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/profile/photo.html:21-25` (comment)
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (profile pages paragraph)

**Interfaces:**
- Consumes: nothing from Task 1 at runtime; the prototype defines the target geometry.
- Produces: `PROFILE_SIDE = 400`; `shrink(file, square, callback)` where `square: boolean`; `initUpload(step, refresh, square)`.
- Unchanged: `direct-upload.js` still receives the input's file (now the shrunk one), and the multipart fallback still posts it.

- [ ] **Step 1: Add `PROFILE_SIDE`**

After `MAX_SIDE`:

```javascript
    // px, square: the profile photo is shown round at 192 px at most; 400 is 2x that, and the usual profile size.
    var PROFILE_SIDE = 400;
```

- [ ] **Step 2: Give `shrink` a square profile mode**

Replace the `shrink` function with:

```javascript
    // Shrinks a photo in the browser, as a JPEG at 0.85. A profile photo (square) is centre-cropped to a square of at
    // most PROFILE_SIDE, on a white canvas so a transparent PNG doesn't turn black; a KYC photo keeps its shape, capped
    // at MAX_SIDE.
    function shrink(file, square, callback) {
        var url;
        var img = new Image();
        img.onload = function () {
            try {
                URL.revokeObjectURL(url);
                var canvas = document.createElement("canvas");
                var context = canvas.getContext("2d");
                if (square) {
                    var side = Math.min(img.width, img.height);
                    var size = Math.min(side, PROFILE_SIDE);
                    canvas.width = size;
                    canvas.height = size;
                    context.fillStyle = "#ffffff";
                    context.fillRect(0, 0, size, size);
                    context.drawImage(img, (img.width - side) / 2, (img.height - side) / 2, side, side,
                        0, 0, size, size);
                } else {
                    var scale = Math.min(1, MAX_SIDE / Math.max(img.width, img.height));
                    canvas.width = Math.round(img.width * scale);
                    canvas.height = Math.round(img.height * scale);
                    context.drawImage(img, 0, 0, canvas.width, canvas.height);
                }
                canvas.toBlob(function (blob) {
                    callback(blob ? new File([blob], "photo.jpg", { type: "image/jpeg" }) : null);
                }, "image/jpeg", 0.85);
            } catch (e) {
                callback(null); // e.g. out of memory: the original photo is posted
            }
        };
        img.onerror = function () {
            URL.revokeObjectURL(url);
            callback(null);
        };
        try {
            url = URL.createObjectURL(file);
            img.src = url;
        } catch (e) {
            callback(null);
        }
    }
```

- [ ] **Step 3: Thread the flag through `initUpload` and the page**

`initUpload(step, refresh, square)`; its `shrink(file, function (small) { … })` call becomes `shrink(file, square, function (small) { … })`.

In `DOMContentLoaded`, replace:

```javascript
        initUpload(step, refresh);
```

with:

```javascript
        // Only the profile photo is cropped to a square; the KYC steps keep their shape.
        initUpload(step, refresh, page === "profile-photo");
```

- [ ] **Step 4: Update the header comment and the template's comment**

In `kyc.js`'s header, after the first bullet, add:

```javascript
 * - the profile photo is centre-cropped to a 400 px square first; the KYC photos keep their shape, capped at 2000 px;
```

In `templates/profile/photo.html`, change the comment's second line to:

```html
  photo step (type and size checked, photo centre-cropped to a 400 px square, shrunk and previewed in the browser);
```

- [ ] **Step 5: Update `modules/ndopify-partner-central/CLAUDE.md`**

In the profile pages paragraph, where it says `kyc.js` enhances them (`data-page` `profile-edit` / `profile-photo`) and the submit handler sends the shrunk photo through `direct-upload.js`, add that on `profile-photo` the photo is first centre-cropped and shrunk to a 400 px square (`PROFILE_SIDE`), white-filled, never enlarged, while the KYC steps keep their shape capped at 2000 px.

- [ ] **Step 6: Run the module's tests and verify**

```bash
cd modules/ndopify-partner-central
mvn test -Dtest=ProfileTemplateTest
mvn verify
```

Expected: PASS, then GREEN.

- [ ] **Step 7: Check the geometry in a browser**

With the app running (`mvn spring-boot:run`, a signed-in agent), open `/profile/photo` and choose the four photos of Task 1's check. The preview's `<img src>` is a `blob:` URL: open it in a new tab to read `400 × 400` (landscape and portrait), `200 × 200` (the 300×200 image) and white corners (the transparent PNG). Read the upload's size in the network tab — the `profile/photo` multipart post, or the S3 `PUT` when direct upload is available — and expect a few tens of KB, not hundreds.

If ndopify-server / MySQL isn't available, run the same four checks on the prototype page (Task 1's step 3), which uses the same algorithm, and confirm by inspection that `kyc.js`'s `square` branch matches `toSquare` (same `min(side, 400)`, same white fill). Also choose an ID card on `/kyc` (or inspect the `square` flag's `page === "profile-photo"` guard) to confirm the KYC path still outputs up to 2000 px, uncropped, with no white border.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/public/assets/js/kyc.js \
  modules/ndopify-partner-central/src/main/resources/templates/profile/photo.html \
  modules/ndopify-partner-central/CLAUDE.md
git commit -m "feat(npc): centre-crop and resize the profile photo before upload"
```

---

## Verification

- `mvn verify` green in `modules/ndopify-partner-central` (ktlint, tests, JaCoCo gate).
- The four photo shapes checked in a browser, on the profile photo page (or the prototype, per Task 2's fallback): 400×400 for landscape and portrait, 200×200 for a photo under 400, white for transparency.
- The KYC steps unchanged: 2000 px cap, no crop, selfie camera's 3:4 crop untouched.
- `git diff origin/main --stat` touches only `modules/ndopify-partner-central/` (two JS files, one template comment, one `CLAUDE.md`), and the root `pom.xml` versions are untouched.

## Out of scope (do not build)

- Any server-side resize or ImageKit / `ImageService` change.
- A crop UI (drag, pan, zoom).
- Backfilling existing photos.
- `Cache-Control: immutable` on the photo objects.
- A JS test runner or unit tests for `kyc.js` / `profile.js`.
- Any change to the profile photo page's markup, CSS, French copy or its 192 px preview, and any change to the KYC wizard's upload.
