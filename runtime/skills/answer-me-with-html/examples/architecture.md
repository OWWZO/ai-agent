---
template: doc
theme: shadcn
title: Answer me with HTML 是怎么工作的
subtitle: 模型只写内容稿，CLI 负责排版、画图和检查
---

## A 一次 render 的全过程
```flow LR
(Markdown 稿件) -> parse: frontmatter + 面板
parse -> STE lint & 组件渲染
STE lint --> 终端警告
组件渲染 -> dagre 布局: flow
组件渲染 & dagre 布局 -> 模板与插槽
模板与插槽 -> *[(单文件 HTML)]
group render.js: parse, STE lint, 组件渲染, dagre 布局, 模板与插槽
```

## B 模型和 CLI 的分工
| 工作 | 谁来做 |
|---|---|
| 决定讲什么、分几个面板 | 模型 |
| 写关系、数据和说明文字 | 模型 |
| 排版、配色、暗黑模式 | CLI |
| 计算图形坐标 | CLI（dagre） |
| 检查写作风格 | CLI（STE lint） |

```callout ok 结果
模型不再输出 CSS、JS 和 SVG 坐标。一次 Bash 调用就能拿到成品页面。
```

## C 目录结构
```tree list
answer-me-with-html
  `bin/am.js` | CLI 入口
  `src/`
    `parse.js` | 稿件 → 面板与块
    `render.js` | 主流程
    `components/` | 8 个组件
    `lint/` | STE 受控写作检查
    `themes/` | blueprint 与 shadcn 两套主题
  `skills/answer-me-with-html/` | Agent Skill
```

## D 出错时怎么办
```sequence
模型 -> am: render 稿件
am --> 模型: ✗ L14 [flow] 错误 + 正确示例
模型 -> 模型: 按示例修正
模型 -> am: 再次 render
am --> 模型: ✓ 页面路径
```
