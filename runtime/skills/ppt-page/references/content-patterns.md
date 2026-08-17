# Content Patterns

Use these patterns when a waterfall PPT needs to explain, teach, compare, or argue rather than only make a visual statement. Pick the content pattern before choosing the page layout recipe, decorative treatment, or image slots. These patterns preserve the fixed 1600x900 slide-stage and single-column waterfall frame.

## Selection Heuristic

- Use a **big statement** only for covers, section beats, transitions, and memorable conclusions.
- Use a **content pattern** when the audience must understand a mechanism, compare alternatives, inspect evidence, follow a timeline, or remember a framework.
- Use captions for nuance, citations, and speaker commentary; do not push all substantive content into captions. The slide canvas may contain short body text, matrices, tables, callouts, labels, and structured diagrams when those are the clearest way to teach the point.

This file chooses the reasoning shape. After choosing the pattern, use `layout-patterns.md` for the light HTML skeleton.

Suggested pairing:

| Content pattern | Common layout recipes |
|---|---|
| Argument Card | Argument Grid, Text Explainer |
| Event Anatomy | Timeline / Chain, Comparison / Matrix |
| Causal Chain | Timeline / Chain |
| Comparison Layout | Comparison / Matrix |
| Policy Matrix | Comparison / Matrix, Data Table First when table-like |
| Evidence Reading | Split Text / Image, Image Evidence Row |
| Actor Network | Comparison / Matrix, Argument Grid |
| Dense Reading Slide | Dense Reading / `layout-reading` |
| Timeline With Consequences | Timeline / Chain |
| Map / Spatial Explanation | Split Text / Image, Image Evidence Row |

## 1. Argument Card

Best for: historical interpretations, strategy recommendations, research findings, product decisions.

Structure:

- Claim: one sentence at headline scale.
- Evidence: 2-3 compact proof points, data points, quotes, or examples.
- Interpretation: one short sentence explaining why the evidence matters.
- Takeaway: optional final line or accent block.

Layout guidance:

- Left 45% claim, right 55% evidence stack; or top claim with three evidence cells below.
- Keep each evidence cell to 18-30 Chinese characters or 8-16 English words.
- Put citations and caveats in the caption.

## 2. Event Anatomy

Best for: wars, reforms, launches, crises, incidents, policy turns.

Structure:

- Background: what condition made the event possible.
- Trigger: what changed.
- Decision: what actors did.
- Consequence: immediate and long-term effects.

Layout guidance:

- Use a 2x2 grid for the four parts, or a horizontal chain when sequence matters.
- Keep dates and actors visible in mono or accent type.
- Avoid making the whole slide one large paragraph; use caption for fuller narration.

## 3. Causal Chain

Best for: explaining why something happened, diagnosing a metric, or connecting distant effects.

Structure:

- Cause -> mechanism -> intermediate effect -> outcome -> residue/legacy.

Layout guidance:

- Use 4-5 connected modules with arrows, numbers, or position.
- Use explicit labels so the chain is readable without presenter narration.
- If causal certainty is low, label weaker links as inference or debate in the caption.

## 4. Comparison Layout

Best for: before/after, A vs B, rivals, factions, policy alternatives, historical periods.

Comparison modes:

- Juxtaposition: side-by-side panels. Best for broad comparison.
- Superposition: overlays on one visual. Best when shapes share a coordinate system.
- Explicit encoding: difference markers, deltas, checkmarks, heat cells. Best for showing what changed.

Layout guidance:

- Align rows by comparable dimensions such as goal, tool, cost, risk, actor, and result.
- Use consistent wording and scale across both sides.
- Do not use color alone to signal better/worse; include labels.

## 5. Policy Matrix

Best for: reforms, programs, product bets, legal/regulatory changes, operating models.

Structure:

- Goal: what the policy aims to solve.
- Tool: what it changes.
- Beneficiary: who gains or is protected.
- Cost / side effect: what new burden or risk appears.
- Opposition: why reasonable actors resist it.

Layout guidance:

- Use rows for policies and columns for dimensions.
- For 3-4 policies, use cards; for 5+ policies, use a compact table.
- Keep one column visually emphasized: goal, effect, or trade-off.

## 6. Evidence Reading

Best for: maps, photos, screenshots, artifacts, charts, archival images, paintings.

Structure:

- Evidence visual as the main object.
- 3-5 callouts directly attached to visible features.
- A short interpretive sentence explaining what the evidence proves or complicates.

Layout guidance:

- Prefer `.fit-contain` when details matter.
- Callouts should identify, not decorate. Use fine rules or numbered dots.
- Caption must name source/provenance and any caveat about interpretation.

## 7. Actor Network

Best for: people, factions, institutions, ecosystems, alliances, and conflicts.

Structure:

- Actors as nodes grouped by camp, role, geography, or institution.
- Links labeled by relationship: alliance, patronage, conflict, dependency, succession, veto.
- One takeaway sentence about the network dynamic.

Layout guidance:

- Keep node labels short and legible.
- For 6-10 actors, use a network map; for more, use grouped columns.
- Put biographical detail in captions.

## 8. Dense Reading Slide

Best for: teaching decks, briefing memos, research synthesis, history or policy explanation.

Structure:

- A clear title.
- 80-160 Chinese characters or 60-110 English words of body text.
- A narrow sidebar for dates, terms, source note, or key definitions.

Layout guidance:

- Use body text only when the reader needs the actual explanation on the slide.
- Keep line length readable: roughly 20-28 Chinese characters or 45-65 English characters.
- Avoid tiny type; cut text or move detail to caption before shrinking too far.

## 9. Timeline With Consequences

Best for: historical narratives, project retrospectives, product launches, incident reports.

Structure:

- Date / moment.
- Event.
- Why it mattered.
- Link to next event.

Layout guidance:

- Do not use a timeline only as a decorative row of dates.
- Add consequence labels or inflection markers.
- For 6+ events, use two rows or a vertical ledger inside the fixed slide canvas.

## 10. Map / Spatial Explanation

Best for: geopolitics, travel, supply chains, market geography, operations, infrastructure.

Structure:

- Map or spatial visual.
- Legend or actor labels.
- Movement arrows, zones, chokepoints, or boundary changes.
- One interpretation line.

Layout guidance:

- Make geography serve the argument: route, distance, adjacency, constraint, or change.
- Avoid unlabeled maps.
- Caption should state map source and whether boundaries are approximate.

## Content Pattern Plan

Before writing slide HTML for a content-heavy deck, create a brief plan with these columns:

| Slide | Job | Content pattern | Layout recipe | Visible content | Caption role | Visual/evidence |
|---|---|---|---|---|---|---|
| 1 | Establish subject | Big statement | Full-Bleed Photo Cover | Title + framing line | Source/provenance | Source-backed image |
| 2 | Orient sequence | Timeline with consequences | Timeline / Chain | 4-6 milestones | Narrative context | Optional map or none |
| 3 | Explain mechanism | Causal chain | Timeline / Chain | Cause modules | Caveats | Diagram |

A good deck normally mixes statement pages and content pages. For decks longer than 8 slides, avoid using big-statement, quote, or stat layouts for more than one-third of the deck unless the user explicitly wants a manifesto or keynote style.
