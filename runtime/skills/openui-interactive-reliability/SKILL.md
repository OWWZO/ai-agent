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

## HtmlView 游戏界面

对于可玩的小游戏，优先使用单个 `HtmlView` 包含 canvas、样式和内联 JavaScript；保持游戏状态集中在少量变量中，并让每次输入都产生可见变化（位置、分数、生命、提示或模式）。

- 同时提供触控和键盘输入；触控按钮使用 `pointerdown`/`pointerup`，并在 `pointercancel`、`pointerleave` 与全局 `pointerup` 时释放按键，避免“卡住”。
- 对按住有效的动作（移动、蓄力、连发）使用按键状态；对开始、重置、开火等单次动作使用独立函数，避免同一事件既触发绑定处理又触发额外监听而重复执行。
- 在 `requestAnimationFrame` 循环中限制 `dt`，并在 `mode !== "play"` 时停止游戏逻辑；绘制层仍应显示标题、暂停、失败和胜利遮罩。
- 每个可交互控件都要能在移动端点击，且提供明确的重试/重置路径；不要把按钮标记为 disabled，除非确有可见且可恢复的条件。
- 在交付前静态预检：root 是首行；HTML 字符串中的引号已正确转义；canvas 尺寸与 CSS 比例一致；开始、暂停/重置、移动、跳跃/射击、结束状态均有可达路径；不重复注册同一按钮的同类事件。

## Output style

Use Chinese when the user communicates in Chinese. Keep the surrounding explanation brief. For an interactive request, return one valid openui-lang block and no extra pseudo-code. If the interface depends on unknown component signatures, call `get_ui_components` first.
