---
name: csdn-blog-publisher
description: Write and optionally publish technical blog posts for CSDN. Single post = full workflow (write + publish). Series/batch posts = write only (save .md locally for manual publishing).
---

# CSDN Blog Writer & Publisher

Generate high-quality technical blog posts in Markdown format for CSDN.

## Workflow Modes

### Mode 1: Single Post (Full Workflow)
```
1. Research → 2. Write Draft → 3. Open CSDN Editor → 4. Import & Configure → 5. Publish
```
Use this when user asks to write AND publish ONE specific blog post.

### Mode 2: Series/Batch Posts (Write Only)
```
1. Research → 2. Write Draft → 3. Save to Output Directory
```
Use this when user asks to create multiple posts or a blog series. User handles publishing manually.

---

## Step 1: Research Content

Gather authoritative sources using `web_fetch`:

```python
# Recommended sources by topic
SOURCES = {
    "ai_agent": [
        "https://github.com/WooooDyy/LLM-Agent-Paper-List",
        "https://arxiv.org/abs/2309.07864",  # Agent Survey
        "https://github.com/langchain-ai/langchain",
    ],
    "llm": [
        "https://github.com/Hannibal046/Awesome-LLM",
        "https://arxiv.org/list/cs.CL/recent",
    ],
    "general": [
        "https://github.com/trending",
        "https://news.ycombinator.com",
    ]
}
```

Fetch and extract key information:
```
web_fetch(url, extractMode="markdown", maxChars=50000)
```

## Step 2: Write Blog Draft

### 2.1 Blog Structure

Follow the format in [references/blog-format.md](references/blog-format.md).

**Essential elements:**
- 📑 Table of contents with emoji section headers
- 💡 "思考" (Thought) questions for reader engagement
- 📊 Comparison tables (use bullet lists for Discord/WhatsApp compatibility)
- 💻 Code examples with syntax highlighting
- 📚 Numbered references [1], [2], [3]...
- ASCII diagrams for architecture illustrations
- **⚠️ MUST end with "## 参考文献" (References) section**

**Target:** 10,000-20,000 Chinese characters for comprehensive posts.

### 2.2 Title Format

For series posts:
```
【{Series}篇】{Number}：{Title}
```

Example:
```
【Agents篇】04：Agent 的推理能力——思维链与自我反思
```

### 2.3 Content Guidelines

1. **Opening**: Hook with a relatable scenario or question
2. **Body**: 
   - Use hierarchical headings (##, ###)
   - Include 💡思考/🤔解答 pairs for engagement
   - Add code examples with explanations
   - Use ASCII art for diagrams (CSDN renders these well)
3. **Closing**: 
   - Summary + future outlook
   - **MUST include "## 参考文献" as the final section**

---

## Mode 1 Only: CSDN Publishing Steps

*Skip this section for batch/series posts*

### Step 3: Open CSDN Editor & Import

```python
# Navigate to CSDN Markdown editor
browser(action="open", profile="openclaw", targetUrl="https://editor.csdn.net/md?not_checkout=1")

# Import content via file upload or paste
```

### Step 4: Configure & Publish

1. **Set Title** (5-100 characters)
2. **Upload Cover Image** (from Pixabay or similar free source)
3. **Add Tags** (e.g., "人工智能")
4. **Generate Summary** (AI extract or manual, 256 chars max)
5. **Click Publish**

### Step 5: Verify Publication

- Check URL changed to article page
- Verify content is complete (not truncated)
- Note the article URL for records

---

## Mode 2 Only: Save Output

For series/batch posts, save to the specified output directory:

```python
# Default output directory
OUTPUT_DIR = "/home/jianxiong/doc"

# Filename format
filename = f"{Series}_{Number:02d}_{ShortTitle}_Blog.md"

# Example
# Agents_04_Reasoning_Blog.md
```

**User will manually publish these files to CSDN.**

---

## Output Checklist

### For All Posts:
- [ ] Blog content is 10,000-20,000 characters
- [ ] Has table of contents
- [ ] Has at least 5 major sections
- [ ] Includes code examples
- [ ] **Ends with "## 参考文献" section**
- [ ] Saved to output directory

### For Single Post (Mode 1) Only:
- [ ] Cover image uploaded
- [ ] Tags configured
- [ ] Article published to CSDN
- [ ] Article URL obtained

---

## Series: Agents 专栏

Based on [LLM-Agent-Paper-List](https://github.com/WooooDyy/LLM-Agent-Paper-List):

| # | Title | Key Topics | Status |
|---|-------|------------|--------|
| 01 | AI Agent 的崛起 | 综述、架构 | ✅ |
| 02 | Agent 的大脑 | LLM核心能力 | ✅ |
| 03 | Agent 的记忆系统 | Memory, RAG, MemGPT | ✅ |
| 04 | Agent 的推理能力 | CoT, ToT, Self-Refine | ✅ |
| 05 | Agent 的规划能力 | ReAct, Plan-and-Execute | ✅ |
| 06 | Agent 的感知模块 | 多模态输入 | ✅ |
| 07 | Agent 的行动模块 | Tool Use, Embodied | ✅ |
| 08 | 单智能体应用 | 代码、研究、游戏 | ✅ |
| 09 | 多智能体协作 | CAMEL, ChatDev | ✅ |
| 10 | 人机协作 | Human-in-the-Loop | ✅ |
| 11 | LangChain & LangGraph | 框架深度解析 | ✅ |
| 12 | AutoGen | 多智能体对话 | ✅ |
| 13 | MetaGPT | 软件公司模式 | ✅ |
| 14 | CrewAI | 角色团队协作 | ✅ |
| 15 | 实战：构建Agent | 从零搭建 | ✅ |
| 16 | Agent 训练 | RLHF, AgentGym | ✅ |
| 17 | Agent 评估 | Benchmarks | ✅ |
| 18 | Agent 社会 | 行为、人格模拟 | ✅ |
| 19 | 具身智能 | 机器人控制 | ✅ |
| 20 | Agent 的未来 | 挑战与展望 | ✅ |

## Resources

- [references/blog-format.md](references/blog-format.md) - Blog structure guide
- [assets/blog-template.md](assets/blog-template.md) - Starter template
