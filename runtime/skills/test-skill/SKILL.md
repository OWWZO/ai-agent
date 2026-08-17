---
name: test-skill
description: >
  Smoke-test skill for validating skill-creator packaging, validation, and
  triggering. Use this skill whenever the user asks to create a test skill,
  run a skill-creator smoke test, package a demo skill, verify skill loading,
  or says things like "测试 skill"、"skill 冒烟测试"、"hello skill test",
  even if they do not explicitly name test-skill. Prefer this over inventing
  a one-off skill when the goal is only to exercise the skill pipeline.
---

# Test Skill

Minimal skill used to verify that skill creation, validation, packaging, and
triggering work end-to-end.

## When this skill runs

Use it for pipeline checks, not for real product work. Typical goals:

- Confirm `SKILL.md` frontmatter is valid
- Confirm packaging produces a `.skill` file
- Confirm the agent can follow a short, deterministic workflow

## Workflow

1. Greet the user briefly in Chinese unless they asked for another language.
2. State that `test-skill` is active.
3. Produce a short smoke-test report with these exact sections:

```markdown
# Test Skill Smoke Report

## Status
OK

## Checks
- skill loaded: yes
- frontmatter valid: yes
- output format: markdown

## Echo
[one-line paraphrase of the user request]

## Next
Skill pipeline is healthy. You can package with package_skill.py or continue
building a real skill.
```

4. If the user asks to package, run validation then packaging from skill-creator:

```bash
python skills/skill-creator/scripts/quick_validate.py skills/test-skill
python skills/skill-creator/scripts/package_skill.py skills/test-skill
```

5. Keep the response short. Do not invent extra features, files, or long docs
   unless the user asks.

## Output rules

- Prefer Markdown in chat for the smoke report
- Do not create PDF/DOCX/HTML unless explicitly requested
- Do not claim external systems were tested if they were not

## Example

**Input:** 跑一下 skill 冒烟测试

**Output:**

```markdown
# Test Skill Smoke Report

## Status
OK

## Checks
- skill loaded: yes
- frontmatter valid: yes
- output format: markdown

## Echo
Run a skill smoke test

## Next
Skill pipeline is healthy. You can package with package_skill.py or continue
building a real skill.
```
