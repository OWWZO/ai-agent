# Media And Diagrams Reference

Use this reference when the report needs visual context beyond statistical charts.

## When To Use Images

Add an image block when seeing the subject helps the reader understand the report:

- product or app screenshots;
- hardware, venue, factory, lab, retail, or physical context;
- company/product logos when identity matters;
- market maps, ecosystem visuals, or public model cards;
- paper figures or architecture images when rights and attribution allow;
- generated explanatory images when no real image exists and the image is clearly illustrative.

Do not add decorative stock-like images. The image should provide evidence, orientation, or concrete context.

For news reports, images are usually expected unless the user asks for text only. Prefer the article's own image, an official product/company image, a clearly attributed logo, or another source-backed visual. If an article has no usable image, say so in the source/image index and choose an honest fallback such as an official logo.

For weekly digests, entertainment roundups, and other reader-facing lists, repeated item cards often need images even when the surrounding page is document-like. Examples include what-to-watch cards, film/music/book picks, event listings, product comparisons, venue lists, and people profiles. For these blocks, try to provide one relevant image per repeated item from the item's own article, official page, Open Graph/Twitter metadata, or another reviewed source. Keep a stable aspect ratio across cards, store the files locally when practical, and add a compact caption or source note for the card group. Do not use one unrelated collage or decorative stock image when separate item-level images are available.

## Image Source Rules

- Prefer user-provided images, local screenshots, public official assets, paper figures, or clearly attributed sources.
- For current companies, products, papers, and market topics, verify source date and URL before using external images.
- Store report-specific image files in `report-slug/images/` when practical.
- For item-card grids, store each card image with a descriptive local filename and verify each downloaded file is a real image, not an HTML error page or blocked hotlink response.
- For products, platforms, companies, tools, and other named resources, prefer real traceable identity assets when visual recognition helps: official favicon, official logo, product screenshot, Open Graph image, or another reviewed source image. Do not draw or generate approximate brand marks. If a favicon can be fetched reliably, store it locally; if the site blocks direct fetching, a favicon service may be used as a lightweight fallback. If no reliable identity asset exists, use a text link rather than a decorative substitute.
- For offline single-file reports, prefer converting small local images to data URIs or otherwise ensure the image remains available beside `index.html`.
- Every image figure needs a caption with what it shows, date/source when relevant, and why it matters.
- Keep a compact image index for news/current-research reports when multiple external visuals are used. The index should connect each local file to the original source URL and explain whether the image came from the article, an official asset, or a fallback visual.

## When To Use Diagrams

Add a diagram block when the report needs to explain structure, flow, causality, or architecture:

- technology architecture;
- product/user workflow;
- research method or evaluation pipeline;
- market ecosystem or value chain;
- causal loop or driver tree;
- decision tree or option map;
- data pipeline or model lifecycle.

Use diagrams to clarify relationships, not to decorate. If a diagram would repeat simple prose, skip it.

## Mermaid Diagrams

Mermaid is the preferred default for workflow, architecture, sequence, state, decision, dependency, and taxonomy diagrams when the diagram can be expressed clearly in Mermaid syntax. It usually produces a better editable document than hand-authored SVG for system flows and process maps.

For final local/offline HTML pages:

- keep Mermaid source directly in `index.html` inside a visible `<pre class="mermaid">` block so the page remains easy to edit;
- wrap Mermaid figures in a `doc-diagram` section and a `.diagram-frame.mermaid-frame` container;
- copy `assets/mermaid.min.js` into the page's `assets/` folder whenever Mermaid diagrams are present;
- load local Mermaid with `<script src="assets/mermaid.min.js"></script>` before `assets/doc.js`;
- initialize Mermaid through `assets/doc.js`; do not add page-specific initialization unless the diagram needs a materially different theme or layout;
- rely on the shared `assets/doc.js` Mermaid zoom behavior for complex diagrams instead of hand-adding page-specific zoom buttons;
- do not rely on a remote Mermaid CDN for default deliverables; use a CDN only for a quick preview or when the user explicitly accepts a network dependency;
- if Mermaid rendering is unavailable, use a simple hand-authored HTML/SVG diagram or a clearly formatted table instead and say so in the final note.

Use hand-authored inline SVG instead of Mermaid when the diagram requires precise custom layout, branded visual design, dense annotations, or shapes that Mermaid cannot express cleanly.

Recommended Mermaid figure shape:

```html
<section class="doc-diagram" id="workflow">
  <h2>Workflow</h2>
  <p>Short setup for why this diagram matters.</p>
  <div class="diagram-frame table-scroll mermaid-frame">
    <pre class="mermaid">
flowchart TD
  A["User goal"] --> B["Coordinator"]
  B --> C["Research agent"]
  B --> D["Execution agent"]
  C --> E["Shared state"]
  D --> E
  E --> F["Verified output"]
    </pre>
  </div>
  <p class="figure-caption"><strong>Figure 1: Workflow.</strong><span>Caption with the key takeaway.</span></p>
</section>
```

## Figure Quality

For image and diagram blocks:

- keep the outer width aligned with the document column unless the user asks for a wide visual;
- use the same 16px rounded figure container language as charts and tables;
- include a concise caption below the figure;
- avoid text overlap at mobile widths;
- keep labels large enough to read in the final report;
- cite source and date when the image or diagram is evidence.

## Block Fields

Use `image` blocks for bitmap or externally sourced visuals:

- `id`
- `title`
- `body_html`
- `src`
- `alt`
- `caption_title`
- `caption`
- optional `width`

Use `diagram` blocks for rendered diagrams:

- `id`
- `title`
- `body_html`
- `diagram_html` or `diagram_svg`
- `caption_title`
- `caption`
- optional `mermaid_source` for audit notes
- optional `width`
