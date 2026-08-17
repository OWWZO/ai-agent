# Waterfall Illustration System

Real waterfall PPTs should use visual assets. This reference defines what kind of illustration/image to use, where it fits, how to generate it, and how to keep it consistent with the selected style preset.

## Core Principle

Choose the image slot before choosing or generating the image. Do not generate a picture first and then force it into a slide. Visual assets are part of the slide system, not decoration. A good waterfall deck should usually answer, before writing HTML: which slides are image-led, which slides are diagram-led, and which slides are intentionally text-only.

## Asset Folder And Naming

- Put each deck in its own folder under `ppt/`, with local images in that deck folder's `images/` directory. Example: `ppt/线性代数入门/线性代数入门.html` and `ppt/线性代数入门/images/`.
- Do not share one top-level `ppt/images/` directory across multiple decks. If there are two decks, create two sibling deck folders, each with its own HTML file and `images/` directory.
- Use stable relative paths such as `images/03-rain-shadow-diagram.png`.
- Name files `{page}-{semantic}.{ext}`, for example `01-desert-cover.jpg`, `04-rain-shadow-diagram.png`, `06-coastal-current-map.svg`.
- Avoid spaces and nonsemantic names like `image1.png`.
- Prefer JPG/WebP for photos, PNG/SVG for diagrams and UI-like graphics.

## When To Use Images

Use real/source-backed images by default when the subject is visual or embodied. Search for suitable source-backed images, use user-provided media, capture screenshots, or generate raster images when appropriate before creating a code-native stand-in:

- People and biographies: portraits, archival photos, workplaces, products, public events, or artifacts from important periods.
- Companies and products: product shots, interface screenshots, stores, factories, launch moments, logos only when they are the subject.
- Places, travel, nature, architecture, culture, and history: documentary photos, maps, site views, archival material, timelines with visual evidence.
- Data, engineering, science, and systems: diagrams, screenshots, maps, process graphics, instrument photos, or labeled schematics.
- Portfolios, brands, art, food, fashion, venues, and objects: image-led cards are expected; text should support inspection, not replace it.

Use fewer or no images when the slide is a thesis, definition, transition, quote, compact metric, legal/sensitive topic without reliable assets, or when available images would be misleading, low quality, unlicensed, or merely decorative. If you choose text-only for a visually rich topic, make that choice intentional and compensate with diagrams or strong visual typography.

## Source Search Vs Generated Images

For real products, companies, people, places, events, news, public interfaces, historical artifacts, films/TV, venues, books, artworks, or named objects, start with source-backed visuals or screenshots that can be named in captions. Use generated raster images when the subject is fictional, unreleased, confidential, unavailable at usable quality, or intentionally conceptual. If generated imagery could be mistaken for documentary evidence, caption it plainly as generated concept imagery.

## Generated Raster Images

Use an available raster image generation or editing capability when a waterfall deck needs AI-created bitmap visuals such as product concepts, fictional brand assets, campaign scenes, lifestyle photography, textures, mockups, or visuals that should look like photos/illustrations rather than code-native shapes.

- This skill does not require a specific image provider. If the host environment provides a dedicated image-generation tool, image-editing tool, or image-generation instruction set, follow that environment's rules. Otherwise use the product's native image-generation flow, an approved external image-generation service, source-backed image search, or user-provided assets according to that environment's capabilities and policies.
- Generate raster assets for fictional or unavailable product imagery when the source material describes the product but does not provide usable photos.
- Do not replace requested or clearly expected raster visuals with SVG, CSS, gradients, HTML diagrams, or placeholder geometry. Use SVG/CSS only for deterministic diagrams, charts, icons, simple marks, or export-safe primitives. If the slide needs a photograph, scene, texture, product concept, place, organism, person, artifact, or other picture-like subject, use a source-backed image or generated raster image instead of drawing it in SVG.
- For each generated image, write a production-oriented prompt that includes intended deck slot, ratio, subject, scene/backdrop, style/medium, composition, lighting, palette/material constraints, and an avoid list such as no text, no watermark, no extra logos.
- Prefer one generated image per distinct slide/slot prompt rather than asking one prompt to produce unrelated assets.
- After generation, copy or export the selected output into the deck folder's `images/` directory using a stable descriptive filename such as `ai-01-product-hero.png`. Never reference a project image directly from a provider cache, temporary directory, external generation URL, or tool-owned output folder.
- Reference generated assets with relative paths such as `images/ai-01-product-hero.png`.
- Captions should identify generated concept imagery plainly, for example `Generated concept image based on source document product description`, and should not imply the image is an existing official product photo.
- Validate generated images like any other local asset with `validate-waterfall-deck.mjs --require-local-images --min-image-bytes=10000`, and do a lightweight browser inspection for full-bleed or high-risk image layouts.

## Image Slot Types

Use these standard slots inside `.slide-stage`. They assume a fixed `1600x900` canvas.

| Slot | Ratio | Best for | Class |
|---|---:|---|---|
| Hero image | 16:9 | cover/photo/scene-setting | `.media-frame.r-16x9` |
| Wide evidence | 21:9 | screenshots, timelines, panoramic evidence | `.media-frame.r-21x9` |
| Main visual | 16:10 | diagrams, UI screenshots, generated infographics | `.media-frame.r-16x10` |
| Side visual | 4:3 | left/right image layouts | `.media-frame.r-4x3` |
| Small visual | 3:2 | cards, examples, thumbnail evidence | `.media-frame.r-3x2` |
| Portrait visual | 3:4 | people, book covers, artifacts | `.media-frame.r-3x4` |
| Square icon/map | 1:1 | symbolic diagrams, maps, logos | `.media-frame.r-1x1` |

Supporting classes:

- `.fit-cover`: fill slot, crop safely. Use for photos and generated visuals.
- `.fit-contain`: preserve all content. Use for screenshots, code, maps, charts, dense UI.
- `.image-grid`: 2-4 images with unified crop behavior.
- `.image-grid.two`, `.image-grid.three`, `.image-grid.four`: explicit column count.

## Default HTML Snippets

### Full-Bleed Background Image

Use when the image itself is the scene-setter: a person, place, product, event, artwork, or strong historical moment. Add a subtle overlay so text remains readable. Keep the slide title short and put source details in the caption.

```html
<section class="slide-frame theme-ink photo-slide" aria-label="Cover slide">
  <div class="page-badge">1/7</div>
  <div class="bleed-photo" aria-hidden="true">
    <img src="images/01-cover.jpg" alt="">
  </div>
  <div class="photo-scrim"></div>
  <div class="slide-stage">
    <div class="slide-kicker">Opening</div>
    <div class="slide-content cover-over-photo">
      <h2 class="slide-title title-lg">Elon Musk 的生平</h2>
      <p class="slide-lead">从南非少年到连续创业者。</p>
    </div>
  </div>
</section>
```

Suggested CSS inside the deck:

```css
.slide-frame.photo-slide .slide-stage { background: transparent; }
.bleed-photo { position: absolute; inset: 0; }
.bleed-photo img { width: 100%; height: 100%; object-fit: cover; }
.photo-scrim { position: absolute; inset: 0; background: linear-gradient(90deg, rgba(0,0,0,.66), rgba(0,0,0,.18)); }
.cover-over-photo { position: relative; z-index: 1; color: #fff; align-content: end; }
```

The transparency rule is required when `.bleed-photo` is a sibling of `.slide-stage`: the base template sets `.slide-stage { background: inherit; }` so ordinary slides have a stable white or dark surface. Without the `.photo-slide` override, that inherited surface sits above the image layer and hides the full-bleed photo.

### Single Main Visual

```html
<div class="slide-content split">
  <div>
    <h2 class="slide-title">Rain shadows create dry basins.</h2>
    <p class="slide-lead">Mountains force moist air upward, leaving the far side dry.</p>
  </div>
  <figure class="media-frame r-4x3 fit-cover">
    <img src="images/04-rain-shadow.jpg" alt="Rain shadow diagram across a mountain range">
  </figure>
</div>
```

### Portrait Or Object Feature

Use for biographies, founders, artists, books, artifacts, or products where the subject should be inspectable. Portraits usually work best as `3:4`; product/object features may use `4:3` or `1:1`.

```html
<div class="slide-content split">
  <div>
    <h2 class="slide-title">The person should be visible, not only named.</h2>
    <p class="slide-lead">Use the caption for source, date, and context.</p>
  </div>
  <figure class="media-frame r-3x4 fit-cover">
    <img src="images/01-portrait.jpg" alt="Portrait of the subject at a public event">
  </figure>
</div>
```

### Evidence Row

```html
<div class="slide-content">
  <div class="image-grid three">
    <figure class="media-frame r-16x10 fit-cover"><img src="images/03-a.jpg" alt="Evidence A"></figure>
    <figure class="media-frame r-16x10 fit-cover"><img src="images/03-b.jpg" alt="Evidence B"></figure>
    <figure class="media-frame r-16x10 fit-cover"><img src="images/03-c.jpg" alt="Evidence C"></figure>
  </div>
  <p class="slide-lead">Keep all images in a row at the same ratio and visual scale.</p>
</div>
```

### Screenshot Or Dense UI

```html
<figure class="media-frame r-16x10 fit-contain media-paper">
  <img src="images/05-dashboard.png" alt="Dashboard screenshot showing regional drought indicators">
</figure>
```

## Illustration Types

### 1. Documentary Photo

Use for context, human stakes, field scenes, product usage, place-based stories.

Prompt pattern:

```text
Create a horizontal editorial documentary photograph about [concept]. Natural light, real environment, low saturation, restrained contrast, subtle film grain, believable scene, no logos, no watermark, no generated text. The image is for a waterfall PPT slide, not a standalone poster. Ratio: [16:9/16:10/4:3]. Keep the subject centered with enough quiet margin for surrounding slide text.
```

Best presets: Ultramarine Ledger, Cartographer Field, Copper Archive, Studio Lacquer.

Avoid: commercial stock-photo poses, fake futuristic dashboards, overdramatic cinematic lighting.

### 2. Editorial Infographic

Use for processes, causal models, systems, comparisons, maps, scientific explainers.

Prompt pattern:

```text
Create a horizontal editorial infographic explaining [concept/process/relationship]. Use [selected preset] style: restrained palette, clear short labels in [Chinese/English], thin rules, generous whitespace, medium information density. No slide title, no page number, no logo, no decorative border, no watermark. Ratio: [16:9/16:10]. The graphic should be readable inside a fixed 1600x900 slide canvas.
```

Best presets: Ultramarine Ledger, Civic Slate, Cartographer Field, Seagrass Lab.

Avoid: tiny labels, colorful clipart, 3D icons, SaaS marketing templates.

### 3. Grid / Systems Diagram

Use when the selected preset is Civic Slate, Carbon Night, Docket Gold, or Cartographer Field.

Prompt pattern:

```text
Create a horizontal systems diagram for [concept]. Use strict alignment, straight hairline rules, rectangular modules, neutral surfaces plus one accent color [accent]. Labels are short and in [Chinese/English]. No gradients, no shadows, no rounded corners, no 3D, no cartoon, no slide chrome. Ratio: [16:9/16:10/21:9].
```

### 4. Screenshot Framing

Use when the user provides screenshots, web pages, code, dashboards, design files, or old PPT captures.

Decision order:

1. If details must remain exact, preserve the screenshot and programmatically fit it into a standard ratio canvas.
2. If screenshot is close to target ratio, place it directly in `.media-frame` with `.fit-contain`.
3. If screenshot is too tall, split it into 2-3 same-ratio panels.
4. If screenshot is messy or conceptual, regenerate as a UI scene/diagram in the chosen preset.

Framing parameters:

| Parameter | Options | Guidance |
|---|---|---|
| ratio | 21:9, 16:10, 16:9, 4:3, 1:1 | follow slide slot, not source image |
| fit | contain, cover | contain for fidelity; cover only when safe |
| padding | compact, standard, spacious | dense UI needs spacious |
| surface | plain, paper, grid, muted accent | follow style preset |
| corners | square | waterfall default is square |
| shadow | none | avoid marketing-card shadows |

### 5. Code-Native Visuals

Use SVG, CSS shapes, grids, maps, timelines, and data blocks when the visual is deterministic, schematic, or export-safe. Do not use code-native visuals as a generic substitute for missing photos, scenes, textures, organisms, people, products, or places.

Good for: section dividers, conceptual contrast, systems diagrams, lightweight maps.

Avoid: decorative blobs, purple gradients, generic orbit/AI imagery.

## Preset Mapping

| Preset | Image language | Preferred assets |
|---|---|---|
| Ultramarine Ledger | white editorial, ultramarine anchors, amber signal | documentary photos, source tables, evidence diagrams |
| Civic Slate | public-service clarity, blue/gold status | policy matrices, decision paths, compliance charts |
| Cartographer Field | field-note, map-like, green-blue | maps, routes, field photos, spatial diagrams |
| Carbon Night | technical, dark interface, cyan signal | architecture diagrams, code/UI screenshots, dependency maps |
| Copper Archive | archival, warm, historical | artifacts, timelines, documentary photos, annotated evidence |
| Docket Gold | operational, urgent, case-file | process charts, risk matrices, incident diagrams |
| Seagrass Lab | calm research, teal/mint | experiment diagrams, method cards, science visuals |
| Riso Pop | spot-color print, youthful | zine-style photos, overprint blocks, event evidence |
| Studio Lacquer | premium dark gallery | full-bleed photos, vertical rules, refined quote visuals |
| Market Quilt | friendly local color blocks | food/place photos, schedule tiles, community imagery |
| Night Arcade | midnight, signal, event energy | stage visuals, scoreboards, digital-art screenshots |
| Coral Bulletin | cheerful announcement | launch checklists, update boards, simple campaign visuals |

## Image Density Rules

- Every deck longer than 5 slides should include at least 2 visual slides unless the user explicitly requests text-only.
- Biography, company, product, film, TV, place, travel, event, portfolio, art, architecture, object, and historical decks require real/source-backed images unless the user explicitly asks for text-only.
- 6-10 slide visual-subject decks need at least 4 local `<img src="images/...">` assets from source-backed photos, screenshots, logos, product visuals, website captures, maps, or documented visual evidence.
- 11+ slide visual-subject decks need at least 5 local `<img src="images/...">` assets or 40% image-bearing slides, whichever is larger.
- A 6-10 slide abstract strategy or technical deck usually needs 2-4 diagrams, screenshots, or data visuals.
- CSS diagrams, geometric marks, icon-only compositions, generic abstract AI art, and charts built entirely in HTML/CSS do not satisfy the real-image requirement for visual-subject decks.
- Company and product decks should normally use a full-bleed source-backed image on the cover unless the available images would be misleading, low-quality, legally risky, or the user asks for another cover direction.
- Fictional, unreleased, confidential, or source-document-only product decks may use generated raster concept images to satisfy image-bearing slide needs. Label these as generated concepts in captions and keep them visually plausible to the source document.
- Film and TV explanation decks should normally include scene-relevant stills or screenshots when discussing visual style, framing, editing, genre references, or specific sequences. Use a small, purposeful number of stills; prefer official trailers, press kits, public-domain/Commons material, or user-provided images; avoid decorative or unsourced frame grabs.
- Captions must name the image source/provenance or the reason an image was chosen.
- Do not place images on every slide unless the deck is image-led.
- One image slide should carry one job: context, evidence, explanation, or emotion.
- If a slide already has a heavy diagram, keep visible text short and move explanation to caption.
- Use text-only slides deliberately: for thesis statements, transitions, dense definitions, or when available images would be misleading, low-quality, off-brand, legally risky, or purely decorative. Do not generate generic decorative AI art.

## Accessibility And Alt Text

- Every informative image needs useful `alt` text.
- Decorative CSS shapes should be `aria-hidden="true"`.
- If an image contains important labels, repeat the essential meaning in caption text.
- Do not rely on color alone in diagrams; use labels, line styles, or position.

## Validation Checklist

Before delivery:

- All images use relative paths.
- Every image path exists.
- Image groups use one ratio and one visual scale.
- Screenshots use `.fit-contain` unless explicitly safe to crop.
- Generated images contain no slide chrome, titles, logos, watermarks, page numbers, or decorative borders.
- The selected style preset is visible in the image language.
- Captions explain source, caveat, or interpretation rather than repeating slide text.
