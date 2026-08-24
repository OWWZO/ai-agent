---
name: reactor-authenticated-sources
description: >
  使用 Reactor 内置的登录态只读工具读取 Twitter/X、Reddit 和雪球。
  用户明确要求搜索推文、读取 Reddit 帖子评论、查询雪球行情或社区内容时使用；
  不执行发帖、评论、点赞、投票、收藏、交易或账户修改。
---

# Reactor Authenticated Sources

这组能力由 Reactor 自己提供，不依赖 Agent Reach，也不使用浏览器 Cookie。
Cookie 只由 `reactor-tool` Python 进程从环境变量读取，不能作为工具参数传入。

## 工具

- `twitter`：`search`、`tweet`、`thread`、`article`、`feed`、`timeline`、`user_posts`
- `reddit`：`search`、`subreddit`、`popular`、`read`、`user_posts`
- `xueqiu`：`quote`、`search`、`hot_posts`、`hot_stocks`

## 边界

1. 只读取用户已配置登录态能访问的内容。
2. 不读取浏览器、`rdt-cli` credential 文件或 Agent Reach 配置。
3. 不把 Cookie、环境变量值或上游认证错误原文回显给用户。
4. 搜索结果只是候选来源；需要正文或评论时再调用具体的读取操作。
5. 雪球行情可能延迟，不构成投资建议。

## 参数提示

- Twitter 搜索使用 `operation=search` 和 `query`；推文读取使用 `tweet_id`。
- Reddit 搜索使用 `operation=search` 和 `query`；帖子读取使用 `post_id`。
- 雪球行情使用 `operation=quote` 和 `symbol`，例如 `SH600519`。
- `limit` 保持在较小范围，避免触发平台限流。
