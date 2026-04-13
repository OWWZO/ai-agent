# Quickstart: 对话历史持久化精简重构

## 1. 切换前准备

1. 确认当前 feature 目录为 `specs/004-conversation-history-refactor/`
2. 备份或直接清理旧历史数据
3. 应用新的 `schema.sql` / Mapper 变更

> 本需求不保留旧历史兼容，切换前即可删除旧历史数据。

## 2. 启动服务

```powershell
mvn -pl ai-agent-station-study-app spring-boot:run
```

```powershell
cd ui
npm run dev
```

## 3. 验证历史列表收敛

1. 打开首页，创建一条新对话
2. 发送一条 `PLAN_SOLVE` 请求
3. 请求完成后刷新页面
4. 检查历史侧栏：
   - 能看到会话标题、最后预览、模式
   - 不需要先拉详情也能展示列表
   - 列表只显示当前设备/用户归属的会话

## 4. 验证 `PLAN_SOLVE` 历史回放

1. 打开刚完成的深度思考会话
2. 检查详情接口返回的 `turns + events`
3. 确认页面按事件顺序显示：
   - 思考
   - 计划
   - 任务推进
   - 最终结果
4. 检查 `turns[].events[].payload`：
   - `artifactRefs` 已直接出现在 payload 顶层
   - 若 `payload.messageType = task`，仍可通过 `payload.resultMap.messageType` 识别具体任务节点
4. 再次刷新页面后重复验证，结果应一致

## 5. 验证 `REACT` + Artifact 引用

1. 发送一条包含搜索或报告产出的 `REACT` 请求
2. 检查事件 payload 中存在稳定 artifact 引用
   - `artifactRefs[].resourceKey` 不是工作区临时路径
   - `artifactRefs[].downloadUrl/previewUrl` 指向稳定可访问地址
3. 在历史详情中打开该引用内容
4. 确认前端通过稳定资源地址而不是工作区临时路径读取内容

## 6. 验证缺失引用场景

1. 手工让某个 artifact 引用失效
2. 重新打开对应历史会话
3. 预期结果：
   - 历史主时间线仍可展示
   - 缺失内容显示明确错误/缺失状态，并返回 `missing/missingReason`
   - 页面不会白屏或静默无响应

## 7. 构建与回归

```powershell
mvn -pl ai-agent-station-study-domain,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests=false test
```

```powershell
cd ui
npm run lint
npm run build
```

## 8. 验收结论

以下条件都满足时，可进入 `/speckit.tasks`：

- 会话列表只依赖摘要接口即可展示
- 历史详情只依赖 turn/event 契约即可回放
- 大体量总结内容通过稳定引用访问
- 旧 rich 字段不再作为主要读取来源
- 本地草稿与服务端历史状态已分层
