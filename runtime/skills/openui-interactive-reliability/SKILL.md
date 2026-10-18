---
name: openui-interactive-reliability
description: Build and review reliable openui-lang interactive interfaces. Use whenever a user asks for a quiz, planner, calculator, map, dashboard, form, checklist, or any UI with clickable controls, editable fields, or state changes. Prevent disabled controls, dead inputs, invalid positional arguments, and state updates that do not visibly change the interface.
---

# Reliable OpenUI Interfaces

Design the interface around a small, explicit state model and verify every interactive control before presenting it.

## Core rules

1. Put `root = ...` first for progressive rendering.
2. Every `$variable` must be declared with a sensible default and must affect a rendered component or action.
3. Every input must either change visible UI state or be inside a `Form` whose submit button sends a concise `@ToAssistant(...)` request carrying the form values.
4. For `Button`, respect positional arguments. The optional `disabled` argument comes near the end; do not append `true` casually. If the button should work, omit `disabled` or pass `false` explicitly.
5. Do not use a dead `Input` or `Select`: bind it to a `$variable` that is read by displayed text, computed data, or submit context.
6. Use `Action([...])` for multiple updates and ensure each `@Set` changes declared state. After a click, the visible question, score, selected marker, itinerary, or result should change.
7. For dynamic lists, use `@Each` with stable item fields and ensure generated buttons are not disabled unless there is a real condition.
8. For maps and images, use the extended components only after loading the UI component catalog. Use `MapMarker` coordinates appropriate to the displayed city and `SearchImage` with precise 2–4 word queries.

## Preflight checklist

Before outputting an openui block, inspect it as if clicking every control:

- Can each button receive a click? Check the `disabled` positional argument.
- Does each button action execute a state change or a valid `@ToAssistant`?
- Is every form submit button enabled and connected to the form?
- Are all input bindings declared and used?
- Does a segmented control/select visibly update the content it controls?
- Does a map marker selection visibly update selected text or details?
- Are all referenced names defined and all defined names reachable from `root`?
- Are component arguments positional and in the documented order?

## Common failure patterns

- A final `true` intended as `block` is accidentally interpreted as `disabled`. Prefer named-by-position discipline: supply only arguments needed, or use `false` for disabled and `true` only for block after confirming the signature.
- Quiz answers increment the question but do not update the score because the answer comparison is wrong. Compare the clicked option with the stored answer before `@Set`.
- A submit button is disabled by default “for safety,” leaving no path to completion. Make it enabled unless validation is actually implemented.
- A form collects values but the assistant prompt ignores them. Refer to the form context implicitly and ask for the resulting plan; do not duplicate values in the message.

## Output style

Use Chinese when the user communicates in Chinese. Keep the surrounding explanation brief. For an interactive request, return one valid openui-lang block and no extra pseudo-code. If the interface depends on unknown component signatures, call `get_ui_components` first.
