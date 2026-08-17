---
name: meeting-action-brief
description: >
  Turn messy meeting notes, call transcripts, or chat dumps into a short action
  brief with owners and due dates. Use this skill whenever the user pastes
  meeting notes and wants action items, owners, follow-ups, or a scannable
  recap — including phrases like "会议纪要", "整理一下会议", "action items from
  the call", "提炼待办", "谁负责什么", or "meeting summary with next steps" —
  even if they do not say "skill". Prefer this over free-form prose when the
  goal is a brief people can copy into IM or a ticket. Also useful as a
  skill-creator packaging/validation smoke test.
---

# Meeting Action Brief

Convert messy meeting notes into a short, scannable action brief.

This skill is intentionally small: a real workflow and a reliable smoke test
for skill-creator (validate → package → optional eval).

## Why this shape

Meeting notes are noisy. The value is not a full transcript rewrite — it is:

1. **Surface decisions** so the group agrees what was settled
2. **Extract actions** with owner + due when known
3. **Keep it short** so it can be pasted into IM / tickets

## Workflow

1. **Parse inputs**
   - Accept free text, bullets, or rough transcripts.
   - Split into decisions, actions, and open questions.
   - Do not invent attendees, owners, or deadlines the user did not mention.
   - If owner/due is unclear, leave as `TBD` and flag once.

2. **Normalize actions**
   - Verb-first titles: `发方案草稿`, `Schedule design review`, `修登录超时`
   - Attach owner and due only when present or strongly implied
   - Merge obvious duplicates

3. **Deliver in chat as Markdown** (default)
   - No file unless the user asks for HTML/PDF/DOCX
   - Keep total length tight: title + counts + sections

## Output template

ALWAYS use this structure (omit empty sections except title and actions):

```markdown
## 会议行动简报
- 上下文：（主题/日期，若可知）
- 统计：决策 x · 行动 y · 待决 z

### 决策
- 一句话决策

### 行动项
- [ ] **动词开头任务** — @负责人 · 截止：日期或 TBD

### 待决问题
- 仍未拍板的问题（可选）

### 一句话摘要
用一句说明本场最重要的结论与下一步。
```

Use `- [x]` only for items already marked done in the notes.

## Examples

**Example 1**

Input:
```
今天对齐了 Q3 发布：先做登录重构，小王这周五出方案；埋点先不做。
小李问要不要上灰度，还没定。我负责下周一对齐产品。
```

Output:
```markdown
## 会议行动简报
- 上下文：Q3 发布对齐
- 统计：决策 2 · 行动 2 · 待决 1

### 决策
- Q3 优先做登录重构
- 埋点本期不做

### 行动项
- [ ] **输出登录重构方案** — @小王 · 截止：本周五
- [ ] **与产品对齐发布节奏** — @我 · 截止：下周一

### 待决问题
- 是否上灰度尚未拍板

### 一句话摘要
登录重构先行（小王周五出方案），埋点暂缓；灰度待决，下周一我对齐产品。
```

**Example 2**

Input:
```
standup notes: ship the memory PR today if CI green; blocked on staging key from ops; design review comments due Thu
```

Output:
```markdown
## 会议行动简报
- 上下文：standup
- 统计：决策 0 · 行动 3 · 待决 0

### 行动项
- [ ] **合并 memory PR** — 条件：CI 绿 · 截止：今天
- [ ] **向 ops 申请 staging key** — 当前阻塞
- [ ] **回复 design review 评论** — 截止：周四

### 一句话摘要
今天在 CI 通过后合 memory PR；并行解 staging key 阻塞，周四前清 design review。
```

## Edge cases

- **No clear actions**: still list decisions; say "未识别到明确行动项" once.
- **Single owner for everything**: put owner once in 上下文, avoid repeating on every line unless helpful.
- **Not meeting-related**: if the user is not asking to extract meeting actions, answer normally — do not force this format.
- **User asks for a file**: export the same structure to `.md`; chat stays a short summary.

## Anti-patterns

- Do not rewrite the full transcript.
- Do not invent owners, dates, or decisions.
- Do not expand into a project plan or roadmap unless asked.
