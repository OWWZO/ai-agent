# Story Planning Reference

Use this reference before writing page blocks or choosing visuals. A page should answer the user's real intent, not merely arrange formatted sections.

## Reader Intent

Before building the page, infer and write down:

- who will read it;
- what they need to understand, decide, compare, remember, or reuse;
- what content genre they likely expect: newsletter, weekly digest, memo, research note, case study, data report, analytical readout, operating update, technical note, dashboard-like review, or another familiar format;
- what a real reader would expect that genre to feel like, including its reading flow, density, evidence style, and visual rhythm;
- what depth they need: quick note, explanatory note, research synthesis, strategy memo, technical walkthrough, operating update, data interpretation, or analytical review;
- what evidence or examples would make the page feel trustworthy;
- what would make the page feel too shallow, too generic, or overbuilt.

When the user's query is broad, make a reasonable assumption and state it in the page caveats or source notes when material.

## Story Spine

Draft the page spine before writing HTML:

1. **Context:** why this page exists now.
2. **Core question or promise:** what the page helps the reader understand.
3. **Thesis:** the clearest answer or organizing idea.
4. **Evidence path:** the few sections, examples, sources, charts, images, or diagrams needed to support the thesis.
5. **Implications:** what the reader should do, watch, or remember.
6. **Limits:** missing data, uncertainty, assumptions, or scope boundaries.

For prose-heavy or current-news pages, write the outline first in plain language, then choose the blocks. Do not start by filling a fixed skeleton. Section labels should fit the story the reader needs.

Do not apply a fixed block sequence such as summary -> metrics -> chart -> table. Do not apply a fixed newsletter sequence either. Use the user's requested genre, source material, and reader intent to decide what belongs in the primary reading flow and what belongs in an appendix or source list.

## Opening Shape

Before choosing header components, decide what the top of the page is supposed to do. The opening may need to:

- orient the reader with one precise sentence;
- state a decision or recommendation first;
- set the scene with a short paragraph;
- explain source scope and date because freshness matters;
- foreground a visual or diagram because the subject is hard to grasp in prose;
- lead with a small number of headline values because exact metrics are the point.

Do not treat subtitle, metadata badges, thesis callout, and KPI strip as the natural order of a page. Those components are useful only when they reduce friction for the reader. Metadata such as date range, source scope, report type, meeting participants, or caveats should usually read as a sentence, caption, note, or source entry rather than as pill-shaped labels. If the same information reads naturally in the first paragraph, in a figure caption, in a source note, or in a later section, prefer that calmer placement.

For meeting notes, memos, PRDs, research notes, and technical/product notes, the first screen often works better as a title plus an authored opening paragraph than as a stack of badges and summary boxes. For data reports or operating updates, top metrics can be appropriate, but only when they are the reader's first decision context.

## Depth Modes

Common page modes:

- `briefing`: short context, key takeaways, dated sources, and what changed.
- `newsletter / digest`: a reader-facing roundup whose structure should follow the user's expected publication style. It may use article-like items, source links, short commentary, real images, or no images. It should not automatically become a data report or policy memo.
- `research note`: synthesis of sources, evidence, quotes or examples, and caveats.
- `product note`: product context, screenshots or images, user flow, positioning, and limitations.
- `technical note`: system context, architecture or workflow diagrams, trade-offs, and implementation notes.
- `strategy memo`: decision framing, options, trade-offs, scenarios, and recommendation.
- `data analysis`: metrics, trends, drivers, risks, benchmark context, and next questions.
- `operating update`: goals, progress, blockers, decisions, and next steps.

The same topic can support different formats and depths. For example, AI regulation news can be a newsletter, a policy brief, a research memo, or a dashboard-like tracker. Those should not share the same structure by default.

## Evidence Mix

Choose blocks based on what helps the reader:

- prose for synthesis, framing, and judgment;
- callouts for the main thesis, recommendation, or caveat;
- tables for exact lookup and comparisons;
- charts for shape, movement, comparison, mix, drivers, or relationships;
- image blocks for product, market, venue, hardware, screenshots, logos, model cards, or other visual context;
- diagram blocks for workflows, systems, timelines, taxonomy, architecture, and process;
- source lists for auditability.

When the page names specific external objects such as products, platforms, companies, tools, accounts, creators, places, papers, datasets, communities, or examples, decide whether the reader needs enough context to identify, access, verify, or continue exploring those objects. Depending on the page genre, that context may be a link, source date, official name, favicon or logo, screenshot, short use-case note, citation, or caveat. Do not force every recommendation page into a fixed set of sections; add object-level context only when it helps the reader act on or trust the page.

When examples are used as evidence, make clear what judgment each example supports and avoid over-extending it. If a counterexample, limitation, or boundary condition materially changes the reader's decision, include it in prose, a note, a caption, or a nearby source context rather than adding a formulaic "pros/cons" section.

Components are optional. A table is useful for exact lookup and comparison, but it can be an appendix when the primary format is a news digest. Metrics and charts are useful for data reports, but they can make a newsletter or narrative memo feel stiff. A callout is useful for a thesis or caveat, but not every page needs one.

Do not make every page start with the same four KPIs. Some pages need no KPI strip; others need two, three, five, or a table instead.

Do not make every page start with the same `导读` or `Executive Summary` block. A short opening synthesis is useful only when it advances the reader into the argument. If the page is better served by a scene-setting paragraph, a dated briefing note, or a direct thesis section, use that instead.

## Plan Output

Before writing the page, decide:

- page title;
- reader and goal;
- inferred genre and why that genre fits the user's request;
- chosen opening shape and why it fits this genre;
- page thesis;
- sections in order;
- each section's job;
- evidence or sources per section;
- block type: `text`, `callout`, `metric`, `chart`, `table`, `image`, `diagram`, `note`, or `sources`;
- chart map, if quantitative charts are used;
- key caveats and source freshness notes.

The final page should feel like it was written for the user's question, not generated from a generic template.
