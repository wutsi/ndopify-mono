# Floor Plans and Maps as Listing Images Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A listing's floor plans and (for land) parcel maps are accepted by the image verification, every image carries a `PHOTO` / `FLOORPLAN` / `MAP` kind, a listing can no longer be published without at least one photo, and a plan or a map never becomes the listing's cover.

**Architecture:** `ndopify-server` keeps the rule in the domain it already owns: a server-only `ImageKind` on `T_LISTING_IMAGE` (backfilled `PHOTO`), a property-type-aware vision prompt, one new error code from `ndopify-dto` (`LISTING_MISSING_PHOTO`, returned after `LISTING_NOT_ENOUGH_IMAGES`), a stricter `PHOTOS` checklist and a photo-only hero picker. `ndopify-partner-central` only maps the new code to its French reason. No DTO field, no API change, no new configuration.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, Spring AI `ChatClients.VISION` / `ChatClients.TEXT`, Flyway, JUnit 5 + `kotlin.test` + `mockito-kotlin`, `TestRestTemplate` on MySQL 8, Thymeleaf (Partner Central, rules only).

**Spec:** GitHub issue [#315](https://github.com/wutsi/ndopify-mono/issues/315) — the requester's original text, the problem, the decisions settled with them on 2026-11-24 (property-type-aware prompt, the three kinds, the one-photo gate, the photo-only hero), the verbatim prompt draft, the acceptance criteria, the risks and the analyst's implementation calls. `UI: yes` — the only user-facing text is one French rejection reason in Partner Central, plus the photos page (`listing/photos.html`): when the PHOTOS section is still `TO_VERIFY` with at least `MIN_PHOTOS` accepted images and none refused, the page says a photo of the property is still required ("une photo du bien est encore nécessaire") instead of *c'est suffisant* — with five accepted images and none refused, the missing photo is the only possible cause, so no image kind is exposed — and its instruction states the rule (plans and maps are accepted and count toward the five, one photo of the property is required, a plan is judged on legibility rather than lighting). The `src/main/html` prototype is unchanged.

## Before you start

Confirm the landing state (any unexpected result: stop, the story or `main` moved):

```bash
git grep -n "LISTING_MISSING_PHOTO" -- modules/ndopify-dto || echo "error code absent"
git grep -n "kind" -- modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/domain || echo "kind absent"
git grep -n "fun extract(file: File)" -- modules/ndopify-server/src/main/kotlin
git grep -n "Generic maps" -- modules/ndopify-server/src/main/kotlin
ls modules/ndopify-server/src/main/resources/db/migration/common | sort -V | tail -2
grep -n "<ndopify-dto.version>" pom.xml
```

Expected: the error code absent, no `kind` in the listing domain, `fun extract(file: File): ListingImageInfo?`,
`Generic maps` under the prompt's *Disallowed Content*, `V18_2__match-building-scores.sql` as the highest migration,
`<ndopify-dto.version>0.0.105</ndopify-dto.version>` (the last released DTO; the module itself is at
`0.0.106-SNAPSHOT`).

## Global Constraints

- **Three pull requests, in this order, never the reverse.**
  1. `feat/315-dto-listing-missing-photo` — Task 1, `ndopify-dto` only. Its merge runs `ndopify-dto-main.yml`, which
     releases the DTO and tags it `ndopify-dto-0.0.106`.
  2. `feat/315-server-floor-plans-and-maps` — Tasks 2 to 8, `pom.xml` + `ndopify-server`. Only buildable once the
     release of (1) exists: the root `pom.xml` pins the released version, never a `-SNAPSHOT`.
  3. `feat/315-npc-missing-photo-message` — Task 9, `ndopify-partner-central`. Its body says `Closes #315`; the other
     two say `Refs #315`.
- **The prompt is normative.** `ListingImageInfoExtractor.PROMPT` becomes, verbatim, the issue's *Prompt (settled
  draft)* block — same wording, same order, same rules. The only edits: it is a Kotlin raw string (`"""…"""`,
  `.trimIndent()`), and `{{propertyType}}`, `{{listingType}}`, `{{lotArea}}`, `{{city}}` are the four placeholders
  substituted on every call (like `ListingContentGenerator` substitutes its own, with `.replace(...)`).
- **The kinds, verbatim:** `PHOTO` | `FLOORPLAN` | `MAP`, stored by name, never exposed through `ndopify-dto` nor
  the API. The model's `kind` is read as a `String` and coerced **server-side** by `ImageKind.parse`: its name with
  case and blanks ignored, and `PHOTO` for anything else (missing, empty, unknown).
- **The gate, in this order:** below `ndopify.listing.min-images` `VERIFIED` images → `LISTING_NOT_ENOUGH_IMAGES`;
  else no `VERIFIED` image with `kind = PHOTO` → `LISTING_MISSING_PHOTO`; else success. A listing short of images
  never reports the new code.
- **The checklist:** `PHOTOS` stays `MIN_PHOTOS = 5` images, none `REJECTED`, **and at least one `PHOTO` among the
  images that aren't `REJECTED`**. `MIN_PHOTOS` keeps counting plans and maps.
- **The hero:** only a `VERIFIED` `PHOTO`. The content generator is told so, and the publisher never trusts it: a
  `heroImageId` that isn't a `VERIFIED` `PHOTO` is ignored. Only a listing with no `VERIFIED` `PHOTO` at all (a
  legacy one) falls back to today's quality-then-area ordering over all its verified images.
- **Persisted data:** one column, `T_LISTING_IMAGE.kind VARCHAR(30) NOT NULL DEFAULT 'PHOTO'`, in
  `V18_3__listing-image-kind.sql` (the default backfills every existing row, including genuine maps). No
  re-extraction, no data rewrite; `V18_3` is never edited once merged.
- **Nothing else moves:** upload, attach, presign, `ListingImageService.cover`, `MailImages`,
  `ListingTelegramSender`, `ndopify-web`, `ndopify-esignature`, `ndopify-web-core`, the `Listing`/`ListingImage`
  DTOs, `ndopify.listing.min-images`, `src/main/html/`.
- **No new configuration**, no environment variable, no cron change: the vision client and `min-images` are read as
  today.
- UI text in French, code, comments, plan and commits in English. Assertions are `kotlin.test`, mocks are
  `mockito-kotlin` (`mock`, `doReturn(...).whenever(...)`, `argumentCaptor`, `verify`, `never`).
- Every touched module ends green: from the module directory `mvn -s ../../settings.xml verify -Dheadless=true`
  (ktlint + tests + JaCoCo; `ndopify-server` needs the local MySQL of `AGENTS.md`). Commits carry `Refs #315`.

## Review Focus

1. **A land listing with a cadastral extract, a surveyor plan and two photos** — the two maps are `VERIFIED` as
   `MAP`, titled in French, judged on legibility (not "well lit"), their scale, north arrow, parcel number and
   © attribution never a rejection; the photos stay the only hero candidates. Task 4 pins the prompt, Task 5 the
   persisted kind.
2. **Five accepted plans and maps, no photo** — the verification answers `LISTING_MISSING_PHOTO` (never
   `LISTING_NOT_ENOUGH_IMAGES`), the `PHOTOS` section is `TO_VERIFY` so the listing can't be re-sent without a
   photo, and adding one photo clears both. Tasks 5 and 6.
3. **A map that must still be refused** — a city/region map, a single pin on a street, an itinerary, a search-result
   screenshot, or a parcel map on an apartment: `REJECTED`. The prompt keeps them under *Disallowed content*; no
   code-side check contradicts the model. Task 4.
4. **A listing published before this story** — its images read back as `PHOTO` (column default), so it verifies
   and keeps its covers exactly as today; a legacy listing with no photo at all still gets a hero. Tasks 3, 5, 7.
5. **A crisp parcel map scoring 5 next to a quality-3 photo** — the photo is the hero in `Listing.heroImageId`,
   hence in the public page, the e-mail and the Telegram message; a plan or a map is never the cover, even when the
   text model names one. Task 7.

---

### Task 1: The DTO error code — PR 1

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `ErrorCode.LISTING_MISSING_PHOTO = "$PREFIX:listing:missing-photo"`, read by Task 5 (`ndopify-server`)
  and Task 9 (`ndopify-partner-central`).

- [ ] **Step 1: Add the constant**

In the `listing` group of `ErrorCode`, right after `LISTING_NOT_ENOUGH_IMAGES`, add
`val LISTING_MISSING_PHOTO = "$PREFIX:listing:missing-photo"`. Nothing else changes; the DTO has no test
(`modules/ndopify-dto/CLAUDE.md`).

- [ ] **Step 2: Build the DTO locally**

```bash
mvn -q -Dheadless=true -pl modules/ndopify-dto install
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit, open and merge PR 1**

```bash
git switch -c feat/315-dto-listing-missing-photo
git add modules/ndopify-dto
git commit -m "feat(dto): the missing-photo error code" -m "Refs #315"
git push -u origin feat/315-dto-listing-missing-photo
```

PR 1 title `feat(dto): the missing-photo error code`, body: one paragraph on why (issue #315) and `Refs #315`; merge
it once `ndopify-dto-pr` is green.

- [ ] **Step 4: Wait for the release, and read its version**

```bash
gh run list --workflow=ndopify-dto-main.yml --limit 1 --json status,conclusion,headSha
git fetch --tags
git tag --list 'ndopify-dto-*' | sort -V | tail -1
```

Expected: `completed` / `success` for the merge commit, and the newest tag `ndopify-dto-0.0.106`. Do not start PR 2
before the tag exists: the other modules resolve the DTO from GitHub Packages, and a `-SNAPSHOT` never resolves
there.

---

### Task 2: Pin the released DTO — first commit of PR 2

**Files:**
- Modify: `pom.xml` (`<ndopify-dto.version>`)

**Interfaces:**
- Consumes: the release of Task 1.
- Produces: the version every later task builds against.

- [ ] **Step 1: Set the released version and commit it on PR 2's branch**

With the tag read in Task 1 (e.g. `0.0.106`), set `<ndopify-dto.version>0.0.106</ndopify-dto.version>` in the root
`pom.xml` (no `-SNAPSHOT`), then:

```bash
git switch main && git pull --ff-only
git switch -c feat/315-server-floor-plans-and-maps
git add pom.xml
git commit -m "chore(deps): use the released ndopify-dto with the missing-photo code" -m "Refs #315"
```

- [ ] **Step 2: Prove every module resolves it**

```bash
mvn -q -Dheadless=true -DskipTests install
```

Expected: `BUILD SUCCESS` (all six modules compile against the release; the server still compiles because
`LISTING_MISSING_PHOTO` is unused so far).

---

### Task 3: `ImageKind`, the entity column and `V18_3`

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/domain/ImageKind.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/domain/ListingImageEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/util/jpa/EnumConverters.kt`
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V18_3__listing-image-kind.sql`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/domain/ImageKindTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces, for Tasks 4 to 7:
  - `enum class ImageKind { PHOTO, FLOORPLAN, MAP }` with `companion object { fun parse(value: String?): ImageKind }`
  - `ListingImageEntity.kind: ImageKind = ImageKind.PHOTO` (between `status` and `title`)
  - the `kind` column, backfilled `PHOTO`

- [ ] **Step 1: Write the failing test**

`ImageKindTest.kt` (package `com.wutsi.ndopify.listing.server.domain`):

```kotlin
class ImageKindTest {
    @Test
    fun `parse the model's kinds`() {
        assertEquals(ImageKind.PHOTO, ImageKind.parse("PHOTO"))
        assertEquals(ImageKind.FLOORPLAN, ImageKind.parse("FLOORPLAN"))
        assertEquals(ImageKind.MAP, ImageKind.parse("MAP"))
        assertEquals(ImageKind.MAP, ImageKind.parse(" map "))
        assertEquals(ImageKind.FLOORPLAN, ImageKind.parse("floorplan"))
    }

    @Test
    fun `anything else is a photo`() {
        assertEquals(ImageKind.PHOTO, ImageKind.parse(null))
        assertEquals(ImageKind.PHOTO, ImageKind.parse(""))
        assertEquals(ImageKind.PHOTO, ImageKind.parse("PICTURE"))
    }

    @Test
    fun `an image is a photo until the verification says otherwise`() {
        assertEquals(ImageKind.PHOTO, ListingImageEntity(id = 11L).kind)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd modules/ndopify-server && mvn -q test -Dtest=ImageKindTest
```

Expected: compilation failure (`Unresolved reference: ImageKind`, `kind`).

- [ ] **Step 3: Add the enum**

`ImageKind.kt`, with the KDoc on the enum saying what each value is and the companion saying that an answer the
model got wrong never fails the extraction:

```kotlin
enum class ImageKind {
    PHOTO,
    FLOORPLAN,
    MAP,
    ;

    companion object {
        fun parse(value: String?): ImageKind =
            entries.firstOrNull { kind -> kind.name == value?.trim()?.uppercase() } ?: PHOTO
    }
}
```

- [ ] **Step 4: Add the column to the entity and its converter**

`ListingImageEntity`: `val kind: ImageKind = ImageKind.PHOTO,` right after `status` (same package, so no import;
no annotation — the `@Converter(autoApply = true)` applies). `EnumConverters.kt`, in the `// listing` group:
`ImageKindConverter : EnumConverter<ImageKind>(ImageKind::class.java, ImageKind.PHOTO)` with
`import com.wutsi.ndopify.listing.server.domain.ImageKind` — not optional, unlike `PropertyType`/`FurnitureType`,
so a NULL or unreadable column reads back as `PHOTO`.

- [ ] **Step 5: Add the migration**

`V18_3__listing-image-kind.sql`:

```sql
ALTER TABLE T_LISTING_IMAGE
  ADD COLUMN kind VARCHAR(30) NOT NULL DEFAULT 'PHOTO' AFTER status;
```

- [ ] **Step 6: Run the tests**

```bash
cd modules/ndopify-server && mvn -q test -Dtest=ImageKindTest
cd modules/ndopify-server && mvn -q test -Dtest=GetListingEndpointTest
```

Expected: both `BUILD SUCCESS`. The second proves Flyway applied `V18_3` and Hibernate reads the new column (its
fixtures insert no `kind`, so every image reads back `PHOTO`).

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/domain modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/util/jpa/EnumConverters.kt modules/ndopify-server/src/main/resources/db/migration/common/V18_3__listing-image-kind.sql modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/domain
git commit -m "feat(server): a listing image is a photo, a floor plan or a map" -m "Refs #315"
```

---

### Task 4: The prompt learns about the listing, and returns a kind

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingImageInfoExtractor.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierImage.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingImageInfoExtractorTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierImageTest.kt`

**Interfaces:**
- Consumes: `ImageKind` (Task 3), `LocationService.search(SearchLocationRequest)` (`refdata`), `ListingEntity`
  (`propertyType`, `listingType`, `area`, `cityId`).
- Produces, for Tasks 5 and 9:
  - `fun extract(file: File, listing: ListingEntity): ListingImageInfo?`
  - `data class ListingImageInfo(title, description, valid, quality, kind: String? = null, reason)`

- [ ] **Step 1: Write the failing tests**

In `ListingImageInfoExtractorTest`, add the `LocationService` mock and the listing, and turn the single-argument
call into two arguments:

```kotlin
    private val locationService = mock<LocationService>()
    private val extractor = ListingImageInfoExtractor(ChatClient.builder(chatModel).build(), locationService)
    private val listing = ListingEntity(
        id = 1L,
        listingType = ListingType.SALE,
        propertyType = PropertyType.LAND,
        cityId = 100L,
        area = 1200,
    )

    @BeforeEach
    fun setUp() {
        doReturn(ChatOptions.builder().build()).whenever(chatModel).options
        doReturn(listOf(LocationEntity(id = 100L, name = "Douala"))).whenever(locationService).search(any())
        file.writeBytes(byteArrayOf(1, 2, 3))
    }

    private fun prompt(): String {
        val prompt = argumentCaptor<Prompt>()
        verify(chatModel).call(prompt.capture())
        return prompt.firstValue.instructions.filterIsInstance<UserMessage>().last().text!!
    }
```

New cases (the existing ones call `extractor.extract(file, listing)` and keep their assertions):

```kotlin
    @Test
    fun `the prompt describes the listing, and the kind comes back`() {
        mockResponse("""{"title": "Plan du rez-de-chaussée", "description": "3 pièces", "valid": true, "quality": 4, "kind": "FLOORPLAN"}""")

        val info = assertNotNull(extractor.extract(file, listing))

        assertEquals("FLOORPLAN", info.kind)
        assertEquals("Plan du rez-de-chaussée", info.title)
        assertTrue(prompt().contains("The listing describes: LAND (SALE), lot area 1200 m2, in Douala."))
        assertFalse(prompt().contains("{{"), prompt())
    }

    @Test
    fun `the prompt without the listing details`() {
        doReturn(emptyList<LocationEntity>()).whenever(locationService).search(any())
        mockResponse("""{"title": "Salon", "valid": true, "quality": 3}""")

        extractor.extract(file, ListingEntity(id = 1L, listingType = ListingType.RENTAL))

        assertTrue(prompt().contains("The listing describes: - (RENTAL), lot area - m2, in -."))
        assertFalse(prompt().contains("{{"), prompt())
    }

    @Test
    fun `the prompt accepts floor plans and parcel maps, and keeps generic maps out`() {
        mockResponse("""{"valid": true, "quality": 3}""")

        extractor.extract(file, listing)

        val prompt = prompt()
        assertTrue(prompt.contains("FLOORPLAN"))
        assertTrue(prompt.contains("Parcel maps (land listings only)"))
        assertTrue(prompt.contains("Generic maps"))
        assertTrue(prompt.contains("kind: (PHOTO|FLOORPLAN|MAP)"))
    }
```

In `ListingVerifierImageTest`, every `extractor.extract(any())` stub and `verify(extractor...)` call becomes
`extract(any(), any())` (the gate and the kind are Task 5; this task only keeps the module compiling).

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-server && mvn -q test -Dtest=ListingImageInfoExtractorTest
```

Expected: compilation failure (`No value passed for parameter 'listing'`, `kind`).

- [ ] **Step 3: Rewrite the prompt and pass the listing in**

In `ListingImageInfoExtractor`: inject `private val locationService: LocationService`, replace the whole `PROMPT`
body with the issue's *Prompt (settled draft)* block verbatim, add `val kind: String? = null` to
`ListingImageInfo` (after `quality`, before `reason`), and substitute the four placeholders:

```kotlin
    fun extract(file: File, listing: ListingEntity): ListingImageInfo? {
        val instructions = PROMPT
            .replace("{{propertyType}}", listing.propertyType?.name ?: "-")
            .replace("{{listingType}}", listing.listingType.name)
            .replace("{{lotArea}}", listing.area?.toString() ?: "-")
            .replace("{{city}}", city(listing) ?: "-")
        // … the existing ChatClient.prompt()…chain, unchanged but for `instructions`
    }

    private fun city(listing: ListingEntity): String? =
        locationService.search(SearchLocationRequest(ids = listOf(listing.cityId), limit = 1)).firstOrNull()?.name
```

In `ListingVerifierImage`, pass the listing through: `extractor.extract(file, listing)` — `verify(image)` becomes
`verify(listing, image)` with that one call changed (no gate, no kind yet).

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-server && mvn -q test -Dtest=ListingImageInfoExtractorTest,ListingVerifierImageTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/verifier modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/verifier
git commit -m "feat(server): the image prompt knows the listing and answers a kind" -m "Refs #315"
```

---

### Task 5: The verification stores the kind and requires a photo

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierImage.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierImageTest.kt`

**Interfaces:**
- Consumes: `ErrorCode.LISTING_MISSING_PHOTO` (Task 1), `ImageKind.parse` (Task 3), `ListingImageInfo.kind`
  (Task 4).
- Produces: the `LISTING_MISSING_PHOTO` gate that `ListingVerifierService` reports as an `errorCode` (no change
  there), and `ListingImageEntity.kind` persisted for Task 6 and Task 7.

- [ ] **Step 1: Write the failing tests**

Add to `ListingVerifierImageTest` (the fixture `verifier` has `minImages = 2`; add
`import com.nhaarman.mockitokotlin2.eq`):

```kotlin
    @Test
    fun `the kind of an accepted image is stored`() {
        mockImages(
            pending = listOf(image(11)),
            verified = listOf(image(11, status = ImageStatus.VERIFIED), image(12, status = ImageStatus.VERIFIED)),
        )
        doReturn(ListingImageInfo(title = "Plan du rez-de-chaussée", valid = true, quality = 4, kind = "FLOORPLAN"))
            .whenever(extractor).extract(any(), eq(listing))

        assertTrue(verifier.verify(listing).success)

        val image = argumentCaptor<ListingImageEntity>()
        verify(dao).save(image.capture())
        assertEquals(ImageKind.FLOORPLAN, image.firstValue.kind)
        assertEquals(ImageStatus.VERIFIED, image.firstValue.status)
    }

    @Test
    fun `the kind of a rejected image is stored too`() {
        mockImages(pending = listOf(image(11)), verified = emptyList())
        doReturn(ListingImageInfo(valid = false, quality = 2, kind = "MAP", reason = "Generic map"))
            .whenever(extractor).extract(any(), any())

        verifier.verify(listing)

        val image = argumentCaptor<ListingImageEntity>()
        verify(dao).save(image.capture())
        assertEquals(ImageKind.MAP, image.firstValue.kind)
        assertEquals(ImageStatus.REJECTED, image.firstValue.status)
    }

    @Test
    fun `floor plans and maps only - missing photo`() {
        mockImages(
            pending = emptyList(),
            verified = listOf(
                image(11, status = ImageStatus.VERIFIED).copy(kind = ImageKind.MAP),
                image(12, status = ImageStatus.VERIFIED).copy(kind = ImageKind.FLOORPLAN),
            ),
        )

        val result = verifier.verify(listing)

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_MISSING_PHOTO, result.errorCode)
        verify(extractor, never()).extract(any(), any())
    }

    @Test
    fun `too few images wins over the missing photo`() {
        mockImages(
            pending = emptyList(),
            verified = listOf(image(11, status = ImageStatus.VERIFIED).copy(kind = ImageKind.MAP)),
        )

        val result = verifier.verify(listing)

        assertEquals(false, result.success)
        assertEquals(ErrorCode.LISTING_NOT_ENOUGH_IMAGES, result.errorCode)
    }

    @Test
    fun `one photo among the maps is enough`() {
        mockImages(
            pending = emptyList(),
            verified = listOf(
                image(11, status = ImageStatus.VERIFIED).copy(kind = ImageKind.MAP),
                image(12, status = ImageStatus.VERIFIED),
            ),
        )

        val result = verifier.verify(listing)

        assertTrue(result.success)
        assertNull(result.errorCode)
    }
```

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-server && mvn -q test -Dtest=ListingVerifierImageTest
```

Expected: `FAILED` — `expected:<urn:…:listing:missing-photo> but was:<null>` on the maps-only case, and `null` vs
`FLOORPLAN` on the kind cases.

- [ ] **Step 3: Implement the kind and the gate**

In `ListingVerifierImage.verifyImages`, keep the loop, then gate in this order (KDoc: the error codes in the order
they are checked):

```kotlin
        val verified = dao.findByListingIdAndStatus(listing.id, ImageStatus.VERIFIED)
        return when {
            verified.size < minImages ->
                ListingVerification(success = false, errorCode = ErrorCode.LISTING_NOT_ENOUGH_IMAGES)
            verified.none { image -> image.kind == ImageKind.PHOTO } ->
                ListingVerification(success = false, errorCode = ErrorCode.LISTING_MISSING_PHOTO)
            else -> ListingVerification(success = true)
        }
```

In `verify(listing, image)`, parse once (`val kind = ImageKind.parse(info?.kind)`) and store it in **both** copies
(the `VERIFIED` one and the `REJECTED` one).

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-server && mvn -q test -Dtest=ListingVerifierImageTest,VerifyListingsJobTest
```

Expected: `BUILD SUCCESS` (the job test's fixtures read back `PHOTO`, so nothing changes end to end yet).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/verifier modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/verifier
git commit -m "feat(server): a listing needs one verified photo to pass the verification" -m "Refs #315"
```

---

### Task 6: The `PHOTOS` checklist asks for one photo

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingSectionService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingSectionServiceTest.kt` (new)

**Interfaces:**
- Consumes: `ListingImageRepository.findByListingIdOrderById`, `ImageKind` (Task 3).
- Produces: `Listing.sections`' `PHOTOS` row, which Partner Central already reads (`ListingRules.done`) — no client
  change.

- [ ] **Step 1: Write the failing tests**

`ListingSectionServiceTest.kt` (package `com.wutsi.ndopify.listing.server.service`):

```kotlin
class ListingSectionServiceTest {
    private val dao = mock<ListingSectionRepository>()
    private val imageDao = mock<ListingImageRepository>()
    private val service = ListingSectionService(dao, imageDao)

    private val listing = ListingEntity(id = 501L)

    private fun images(vararg kinds: ImageKind) = kinds.mapIndexed { index, kind ->
        ListingImageEntity(id = index + 1L, listingId = 501L, kind = kind, status = ImageStatus.PENDING_VERIFICATION)
    }

    private fun photos(): ListingSectionSummary =
        service.summaries(listing).first { summary -> summary.section == ListingSection.PHOTOS }

    private fun mockImages(images: List<ListingImageEntity>) {
        doReturn(emptyList<ListingSectionEntity>()).whenever(dao).findByListingId(501L)
        doReturn(images).whenever(imageDao).findByListingIdOrderById(501L)
    }

    @Test
    fun `five photos`() {
        mockImages(images(ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO))

        assertEquals(ListingSectionStatus.VERIFIED, photos().status)
    }

    @Test
    fun `five plans and maps - not done`() {
        mockImages(images(ImageKind.MAP, ImageKind.FLOORPLAN, ImageKind.MAP, ImageKind.FLOORPLAN, ImageKind.MAP))

        assertEquals(ListingSectionStatus.TO_VERIFY, photos().status)
    }

    @Test
    fun `one photo among the plans and maps is enough`() {
        mockImages(images(ImageKind.MAP, ImageKind.FLOORPLAN, ImageKind.MAP, ImageKind.FLOORPLAN, ImageKind.PHOTO))

        assertEquals(ListingSectionStatus.VERIFIED, photos().status)
    }

    @Test
    fun `four images are not enough`() {
        mockImages(images(ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO))

        assertEquals(ListingSectionStatus.TO_VERIFY, photos().status)
    }

    @Test
    fun `a rejected image leaves the section to verify`() {
        mockImages(images(ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.PHOTO, ImageKind.MAP).map {
            it.copy(status = ImageStatus.VERIFIED)
        } + ListingImageEntity(id = 9L, listingId = 501L, status = ImageStatus.REJECTED))

        assertEquals(ListingSectionStatus.TO_VERIFY, photos().status)
    }

    @Test
    fun `a rejected photo does not count as the photo`() {
        mockImages(
            images(ImageKind.MAP, ImageKind.MAP, ImageKind.MAP, ImageKind.MAP).map { it.copy(status = ImageStatus.VERIFIED) } +
                ListingImageEntity(id = 9L, listingId = 501L, kind = ImageKind.PHOTO, status = ImageStatus.REJECTED),
        )

        assertEquals(ListingSectionStatus.TO_VERIFY, photos().status)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-server && mvn -q test -Dtest=ListingSectionServiceTest
```

Expected: `FAILED` on `five plans and maps - not done` (it is `VERIFIED` today) and on `a rejected photo…`;
every other case passes already.

- [ ] **Step 3: Implement the rule**

`photosVerified` reads the images once and applies the three conditions (`MIN_PHOTOS` unchanged, no `REJECTED`,
and at least one `PHOTO` among the non-`REJECTED` ones). Update the class KDoc: `PHOTOS` is `VERIFIED` once there
are `MIN_PHOTOS` images, none `REJECTED`, and at least one of them a `PHOTO`.

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-server && mvn -q test -Dtest=ListingSectionServiceTest,UpdateListingEndpointTest,GetListingEndpointTest
```

Expected: `BUILD SUCCESS` (their fixtures' images read back `PHOTO`).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingSectionService.kt modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingSectionServiceTest.kt
git commit -m "feat(server): the photos checklist needs one photo" -m "Refs #315"
```

---

### Task 7: The hero is always a photo

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/publisher/ListingPublisherService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/publisher/ListingContentGenerator.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/publisher/ListingPublisherServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/publisher/ListingContentGeneratorTest.kt`

**Interfaces:**
- Consumes: `ImageKind` (Task 3), `ListingImageRepository.findByListingIdAndStatus`.
- Produces: `ListingPublisherService.heroImageId(listing: ListingEntity, preferredId: Long? = null): Long?` — the
  cover every reader of `Listing.heroImageId` (`ListingImageService.cover`, the public page, `MailImages`,
  `ListingTelegramSender`) gets.

- [ ] **Step 1: Write the failing tests**

In `ListingPublisherServiceTest`, the `published` test now needs the model's pick among the mocked verified images,
and loses its "never queries the images" assertion:

```kotlin
    @Test
    fun published() {
        doReturn(listOf(ListingImageEntity(id = 11L, kind = ImageKind.PHOTO, quality = 4)))
            .whenever(imageDao).findByListingIdAndStatus(1L, ImageStatus.VERIFIED)

        val result = service.publish(listing)

        // …the existing assertions, including assertEquals(content.heroImageId, saved.heroImageId)
    }

    @Test
    fun `the model's hero is a map - the best photo instead`() {
        doReturn(content.copy(heroImageId = 12L)).whenever(generator).generate(any())
        doReturn(
            listOf(
                ListingImageEntity(id = 11L, kind = ImageKind.PHOTO, quality = 3, width = 800, height = 600),
                ListingImageEntity(id = 12L, kind = ImageKind.MAP, quality = 5, width = 2000, height = 2000),
            ),
        ).whenever(imageDao).findByListingIdAndStatus(1L, ImageStatus.VERIFIED)

        service.publish(listing)

        assertEquals(11L, saved().single().heroImageId)
    }

    @Test
    fun `no photo at all - the best verified image, as before`() {
        doReturn(content.copy(heroImageId = null)).whenever(generator).generate(any())
        doReturn(
            listOf(
                ListingImageEntity(id = 21L, kind = ImageKind.MAP, quality = 3),
                ListingImageEntity(id = 22L, kind = ImageKind.FLOORPLAN, quality = 5),
            ),
        ).whenever(imageDao).findByListingIdAndStatus(1L, ImageStatus.VERIFIED)

        service.publish(listing)

        assertEquals(22L, saved().single().heroImageId)
    }
```

The existing "highest quality, then largest" tests keep their listings (default `kind = PHOTO`) and their
assertions. In `ListingContentGeneratorTest`, the images fixture gains kinds (`11L` `PHOTO`, `12L` `MAP`) and the
prompt assertions become:

```kotlin
        assertTrue(
            prompt.contains("## Image #1\n\n- ID: 11\n- Title: Salon\n- Quality: 4\n- Kind: PHOTO\n- Description: Grand salon"),
        )
        assertTrue(prompt.contains("- Kind: MAP"))
        assertTrue(prompt.contains("The hero image must be a PHOTO"))
```

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-server && mvn -q test -Dtest=ListingPublisherServiceTest,ListingContentGeneratorTest
```

Expected: `FAILED` — the map is chosen as hero, and the prompt has no `Kind` line.

- [ ] **Step 3: Implement**

`ListingPublisherService`: `published` calls `heroImageId(listing, content.heroImageId)`; `manual` keeps
`heroImageId(listing)`. The new body loads the `VERIFIED` images once, keeps the `PHOTO`s as candidates (or all of
them when there is no photo), returns `preferredId` only when it is one of the candidates, else the best candidate
by quality then area. The class KDoc's hero sentence becomes the photo-only rule, with the legacy fallback.

`ListingContentGenerator`: the images block adds `- Kind: ${image.kind}` after `- Quality: …`, and the images
instruction line becomes
`- The hero image must be a PHOTO that represents the property the best: never a floor plan (FLOORPLAN) nor a map (MAP).`

- [ ] **Step 4: Run the tests**

```bash
cd modules/ndopify-server && mvn -q test -Dtest=ListingPublisherServiceTest,ListingContentGeneratorTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/publisher modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/publisher
git commit -m "feat(server): a listing's hero image is a photo, never a plan nor a map" -m "Refs #315"
```

---

### Task 8: `ndopify-server` documentation, gates and PR 2

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the **listing** bullet: checklist, verification, publish)

**Interfaces:**
- Consumes: Tasks 2 to 7.
- Produces: PR 2, ready to merge.

- [ ] **Step 1: Update the listing bullet**

Three targeted edits, keeping the surrounding sentences:

1. The checklist (`PHOTOS` is never saved…) becomes: `VERIFIED` once there are `MIN_PHOTOS` (5) images, none
   `REJECTED` and at least one of them a `PHOTO` (a plan or a map doesn't count as the photo; it does count toward
   the five).
2. The **Send** paragraph gains one sentence after the `ListingVerifierService` one: each image is classified
   `PHOTO` / `FLOORPLAN` / `MAP` by the vision model, which is prompted with the listing's property type, listing
   type, lot area and city — a floor plan is valid on any property, a parcel map only on a `LAND` (or when the
   property type is unset and the parcel is identifiable); the verifier answers `LISTING_NOT_ENOUGH_IMAGES` below
   `ndopify.listing.min-images`, then `LISTING_MISSING_PHOTO` without a `VERIFIED` photo, else it passes.
3. The **Publish** paragraph's hero becomes: the model's `heroImageId` when it is a verified photo, else the
   verified photo of highest quality, then largest; a plan or a map is never the hero (only a listing with no
   verified photo at all falls back to all its verified images).

- [ ] **Step 2: Run the module's gates**

```bash
cd modules/ndopify-server && mvn -s ../../settings.xml verify -Dheadless=true
```

Expected: `BUILD SUCCESS` — ktlint, the whole suite (MySQL up) and the JaCoCo thresholds.

- [ ] **Step 3: Commit, push and open PR 2**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): photo, floor plan and map kinds on a listing's images" -m "Refs #315"
git push -u origin feat/315-server-floor-plans-and-maps
```

PR 2 title `feat(server): accept floor plans and maps, require one photo`, body: the new kinds, the gate order,
the checklist, the photo-only hero, the `V18_3` backfill, `Refs #315`. Merge once `ndopify-server-pr` is green.

---

### Task 9: Partner Central's reason for the new code — PR 3

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRulesTest.kt`

**Interfaces:**
- Consumes: `ErrorCode.LISTING_MISSING_PHOTO` from the DTO version pinned in Task 2.
- Produces: the `PHOTOS` row of the *À corriger* panel — no template change.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `rejections - missing photo`() {
        val listing = Listing(status = ListingStatus.DRAFT, errorCodes = listOf(ErrorCode.LISTING_MISSING_PHOTO))

        assertEquals(
            listOf(
                ListingRules.Rejection(
                    "Il faut au moins une photo du bien : un plan ou une carte ne suffisent pas.",
                    ListingSection.PHOTOS,
                ),
            ),
            ListingRules.rejections(listing),
        )
    }
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd modules/ndopify-partner-central && mvn -q test -Dtest=ListingRulesTest
```

Expected: `FAILED` — the generic `La vérification de l'annonce n'a pas abouti.`

- [ ] **Step 3: Add the mapping**

In `ListingRules.REJECTIONS`, right after `LISTING_NOT_ENOUGH_IMAGES`:

```kotlin
        ErrorCode.LISTING_MISSING_PHOTO to
            Rejection("Il faut au moins une photo du bien : un plan ou une carte ne suffisent pas.", ListingSection.PHOTOS),
```

- [ ] **Step 4: Run the tests and the module's gates**

```bash
cd modules/ndopify-partner-central && mvn -s ../../settings.xml verify -Dheadless=true
```

Expected: `BUILD SUCCESS` (ktlint, the whole suite, JaCoCo).

- [ ] **Step 5: Commit, push and open PR 3**

```bash
git switch main && git pull --ff-only
git switch -c feat/315-npc-missing-photo-message
git add modules/ndopify-partner-central
git commit -m "feat(npc): tell the agent why a listing needs a photo" -m "Refs #315"
git push -u origin feat/315-npc-missing-photo-message
```

PR 3 title `feat(npc): the missing-photo rejection reason`, body with `Closes #315`.

---

## Self-review

- **Spec coverage.** AC 1 (floor plans) and AC 2 (parcel maps for land) → Task 4's prompt; AC 3 (maps still
  refused) and AC 4 (unrelated plans) → Task 4's prompt; AC 5 (legibility, not lighting) → Task 4's prompt,
  Task 5's stored quality; AC 6 (the gate and its order) → Task 5; AC 7 (the checklist) → Task 6; AC 8 (the hero) →
  Task 7; AC 9 (no regression) → Tasks 3 (backfill), 5, 6, 7 and their existing tests; AC 10 (tests, ktlint,
  coverage) → every task plus Tasks 8 and 9's `verify`. Scope items 1–7 → Tasks 4, 3, 5, 6, 7, 9 and the three-PR
  rollout in *Global Constraints*.
- **Type consistency.** `ImageKind.parse(String?): ImageKind` is used by Task 5 only; `ListingImageInfo.kind` is a
  `String?` everywhere (Task 4's extractor, Task 5's parse), `ListingImageEntity.kind` an `ImageKind` (Tasks 3, 5,
  6, 7); `extract(file, listing)` has the same two parameters in Task 4's extractor, Task 4's verifier call site
  and every test.
- **Review Focus tests.** Each line names its owner: a land listing's maps and photos (4, 5), plans-only (5, 6),
  the maps that stay refused (4), a legacy listing (3, 5, 7), the parcel map scoring 5 (7).
