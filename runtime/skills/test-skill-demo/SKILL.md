---
name: test-skill-demo
description: >
  Turn a messy task list, todo dump, or standup bullets into a clean prioritized
  action checklist in Markdown. Use this skill whenever the user asks to organize
  todos, prioritize a task list, clean up a checklist, format standup action items,
  or says things like "整理一下待办", "帮我排优先级", "做成 checklist", "action items",
  or wants a structured P0/P1/P2 task list — even if they do not say "skill". Prefer
  this over free-form prose when the goal is a scannable checklist. This is also the
  smoke-test skill for skill-creator packaging and validation.
---

# Test Skill Demo

Convert messy task notes into a short, prioritized Markdown action checklist.

This skill is intentionally small: it is a real workflow and a reliable smoke test
for skill-creator (validate → package → optional eval).

## Why this shape

People paste half-formed bullets. The value is not more prose — it is:

1. **Normalize** vague items into clear actions
2. **Prioritize** so the next step is obvious
3. **Keep the reply short** so it can be copied into notes/IM

## Workflow

1. **Parse inputs**
   - Accept free text, bullets, or numbered lists.
   - Split into discrete tasks. Merge obvious duplicates.
   - Do not invent work the user did not mention.
   - If priority is unclear, default to `P1` and mark uncertainty briefly.

2. **Assign priority**
   - `P0`: blocking, deadline today/tomorrow, or user-marked urgent
   - `P1`: important this week / main path
   - `P2`: nice-to-have / later
   - Keep at most 3 items in `P0` unless the user already listed more urgents.

3. **Write each item as an action**
   - Prefer verb-first titles: `修登录超时`, `发周报草稿`, `Review PR #12`
   - Optional one-line note for context, owner, or dependency
   - Status if known: `todo` | `doing` | `blocked` | `done`

4. **Deliver in chat as Markdown** (default)
   - No file unless the user asks for HTML/PDF/DOCX
   - Keep total length tight: title + counts + checklist + optional next focus

## Output template

ALWAYS use this structure (empty sections may be omitted except title and checklist):

```markdown
## 行动清单
- 周期/上下文：（若可知）
- 统计：P0 x · P1 y · P2 z

### P0
- [ ] **动词开头任务** — 可选说明

### P1
- [ ] **动词开头任务** — 可选说明

### P2
- [ ] **动词开头任务** — 可选说明

### 下一步
一句话：现在最该先做哪一件、为什么。
```

Use `- [x]` only for items the user already marked done.

## Examples

**Example 1**

Input:
```
明天要交周报，登录还有超时，顺便看看文档，有空再整理桌面
```

Output:
```markdown
## 行动清单
- 上下文：明日截止相关
- 统计：P0 2 · P1 1 · P2 1

### P0
- [ ] **写并提交周报** — 明日截止
- [ ] **排查登录超时** — 影响使用

### P1
- [ ] **过一遍文档** — 范围未指定，先扫关键页

### P2
- [ ] **整理桌面** — 有空再做

### 下一步
先写周报草稿（有硬截止），并行记下登录超时复现步骤。
```

**Example 2**

Input:
```
standup: still on agent memory; blocked on API key; need to reply design review
```

Output:
```markdown
## 行动清单
- 上下文：standup
- 统计：P0 2 · P1 1 · P2 0

### P0
- [ ] **回复 design review** — 阻塞协作
- [ ] **申请/配置 API key** — 当前 blocked 根因

### P1
- [ ] **继续 agent memory 实现** — 解阻后推进

### 下一步
先处理 API key 与 design review 回复，再回到 memory 开发。
```

## Edge cases

- **Only one item**: still use the template; skip empty priority sections.
- **All done**: show checked items and a short "本批已完成" note; ask if they want a next-week list only if useful.
- **Non-task chat**: if the user is not asking to organize tasks, do not force this format — answer normally.
- **User asks for a file**: then export the same structure to `.md` / checklist tool; chat stays a short summary.

## Anti-patterns

- Do not pad with motivational fluff.
- Do not create a huge project plan from three bullets.
- Do not rename the skill or expand scope into full project management unless asked.
