---
name: reactor-authenticated-sources
description: >
  使用 Reactor 的 host_cli 调用 Twitter/X、Reddit CLI，使用 opencli 调用雪球浏览器登录态适配器。
  用户明确要求搜索推文、读取 Reddit 帖子评论、查询雪球行情或社区内容时使用；
  不执行发帖、评论、点赞、投票、收藏、交易或账户修改。
---

# Reactor Authenticated Sources

Twitter/X 和 Reddit 通过 `host_cli` 调用宿主机 CLI；雪球通过浏览器在线时的 `opencli` 调用。
凭据由 CLI 或浏览器会话管理，不把 Cookie、token 或 credential 作为工具参数传入。

## 工具

- `twitter` CLI：`search`、`tweet`、`thread`、`article`、`feed`、`timeline`、`user-posts`
- `rdt` CLI：`search`、`sub`、`popular`、`read`、`user-posts`
- `opencli` 雪球：`stock`、`search`、`hot`、`hot-stock`、`feed`、`comments`、`watchlist`

## 边界

1. 只读取用户已配置登录态能访问的内容。
2. 不把 Cookie、token 或 credential 内容放入 `host_cli.args` 或 `opencli.args`。
3. 不把 Cookie、环境变量值或上游认证错误原文回显给用户。
4. 搜索结果只是候选来源；需要正文或评论时再调用具体的读取操作。
5. 雪球行情可能延迟，不构成投资建议。

## 参数提示

- Twitter 搜索：`host_cli(tool="twitter", args=["search", QUERY, "--json"])`。
- Reddit 搜索：`host_cli(tool="rdt", args=["search", QUERY])`。
- Reddit 帖子：`host_cli(tool="rdt", args=["read", POST_ID])`。
- 雪球行情：`opencli(args=["xueqiu", "stock", SYMBOL])`。
- `limit` 保持在较小范围，避免触发平台限流。
