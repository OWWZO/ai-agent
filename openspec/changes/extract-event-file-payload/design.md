## Context

当前对话历史链路里，`ai_agent_message` 与 `ai_agent_message_event` 的职责边界不够清晰：

- `ai_agent_message.files_json` 只表示用户上传文件，当前插入占位消息时就写入，后续历史详情 `ConversationTurnDetail.files` 也直接透传该字段。
- `ai_agent_message_event.payload_json` 会保存事件级结构化快照，但文件类事件会把 `html/markdown/ppt/code` 等内容直接内联进去，导致单行数据过大。
- 前端历史渲染主要依赖 event payload 中的 `artifactRefs/fileInfo` 来恢复文件展示，但如果要查询“某轮对话生成了哪些文件”，目前仍需从 `payload_json` 反向提取。
- 会话记忆恢复 `SessionArtifactRestoreSupport` 现在只聚合 `files_json` 与 event `artifactRefs`，尚无“本轮生成文件”的消息级稳定索引。
- 旧历史数据允许在上线前直接清理，因此本次设计不需要为旧 payload 结构提供读时兼容。

这次改动同时跨越了数据库字段、消息/事件持久化、历史回放、会话记忆恢复和前端类型定义，属于典型的跨模块数据模型演进。

## Goals / Non-Goals

**Goals:**

- 让 `ai_agent_message_event.payload_json` 对文件类事件只保留路径引用、资源标识和必要渲染元数据，不再内联全文内容。
- 在 `ai_agent_message` 新增独立字段保存“本轮生成文件列表”，并复用现有上传文件 JSON 结构，便于直接查询每轮产物。
- 保持 `files_json` 继续只表达“用户上传文件”，避免上传文件和生成文件语义混杂。
- 让历史详情接口和前端类型同时暴露上传文件与生成文件，事件时间线继续基于 `payload_json` 的引用信息渲染。
- 明确新模型直接生效，不为旧历史数据提供兼容读取或回填逻辑。

**Non-Goals:**

- 不新增专门的文件表，也不改变现有文件服务/OSS/预览 URL 的存储方案。
- 不重做整个事件 payload 协议，只在现有 canonical `artifactRefs` 结构上做“轻量引用化”收敛。
- 不在本期引入新的前端文件预览组件；继续复用现有文件展示/预览链路。
- 不为旧历史数据保留兼容读取、在线回退或离线回填方案。

## Decisions

### Decision 1: 在 `ai_agent_message` 新增 `generated_files_json`，并复用现有文件 JSON 结构

**方案**

- 为 `ai_agent_message` 新增 `generated_files_json JSON NULL` 字段。
- Domain 实体新增 `generatedFilesJson` 属性；Mapper XML、DAO 更新 `resultMap`、`insert/update/select` 映射。
- 该字段复用 `FileInformation` 对应的 JSON 结构，即沿用当前 `files_json` 的字段风格：
  - `fileName`
  - `fileDesc`
  - `ossUrl`
  - `domainUrl`
  - `fileSize`
  - `fileType`
  - `resourceKey`
  - `mimeType`
  - `originFileName/originFileUrl/originOssUrl/originDomainUrl`

**原因**

- 现有 `SessionArtifactRestoreSupport.parseFiles/normalizeFilesToArtifactRefs/toFiles` 已经围绕该结构完成了上传文件解析和 artifact 归一化，复用可以显著减少消息侧新增解析逻辑。
- 生成文件和上传文件虽然语义不同，但底层都落在“稳定文件引用”模型上，复用同一 JSON 结构最利于后续聚合、去重和历史恢复。
- 直接把生成文件单独落在 message 侧，满足“按轮次直接查询生成文件”的诉求，不需要再从 `payload_json` 逆向解析。

**备选方案**

- 复用 `files_json`：拒绝。会混淆上传文件与生成文件语义，后续查询和 UI 展示都需要再加来源判断。
- 新增独立文件表：拒绝。超出本次最小闭环范围，也与“无需专门存文件表”的约束冲突。
- 复用 event `artifactRefs` 结构写到 message：拒绝。会让消息侧再次维护一套与 `parseFiles` 不兼容的结构，增加维护成本。

### Decision 2: 文件类 event 的 `payload_json` 收敛为“引用式 payload”，继续保留 canonical `artifactRefs`

**方案**

- 对 `html/markdown/ppt/code/file/browser/data_analysis` 等文件类或产物类事件，`payload_json` 只保留前端渲染所需的最小字段：
  - 事件基础标识：`messageType/messageId/taskId/taskOrder`
  - `artifactRefs`
  - `referenceOnly`
  - 必要的展示元数据与轻量结果字段
- 去掉或裁剪原先内联的正文内容、长文本结果以及重复的 `fileInfo/fileList` 嵌套结构。
- `content_text` 继续保留简短摘要、标题或说明文案，避免时间线空白。
- `ConversationEventPayloadNormalizer` 继续输出 canonical `artifactRefs`，但增加“文件类 payload 精简”的统一收口，避免写入侧各分支自行裁剪。

**原因**

- 前端当前已经围绕 `artifactRefs -> fileInfo` 做了统一归一化，保留该结构可以最大限度降低前端协议变化。
- `payload_json` 的职责是支撑 event 级渲染，而不是存储文件全文。改为引用式 payload 后，event 表体积下降，历史回放和上下文恢复也不再依赖大字段。
- 继续保留 `artifactRefs` 而不是完全只看 `generated_files_json`，能保证 event 级时间线仍然知道“哪个事件生成了哪个文件”。

**备选方案**

- event 里完全不存文件引用，只让前端从 `generated_files_json` 反查：拒绝。这样会丢失 event 与文件的直接关联，时间线无法精确渲染。
- 继续保留完整 payload：拒绝。无法解决 event 表膨胀问题。

### Decision 3: 生成文件在单轮结束时统一汇总写回 `ai_agent_message`

**方案**

- 以 `AgentStreamPersistServiceImpl.persistTurnAndEvents(...)` 为唯一收口点。
- 该方法在拿到 `finalOrderedEvents` 后，先调用 `messageEventService.persistEvents(...)` 持久化最终 event，再从同一批最终事件中抽取 `artifactRefs`，转换为 `FileInformation` 结构，去重后序列化成 `generated_files_json`。
- `IAgentMessageService.completeMessage/markError/markForceStop` 扩展参数，允许同时更新：
  - `response`
  - `metricsJson`
  - `status/forceStop/finishedAt`
  - `generatedFilesJson`
- 去重逻辑统一复用 `SessionArtifactRestoreSupport` 的文件归一化能力，避免消息侧和会话恢复侧出现两套标准。

**原因**

- `persistTurnAndEvents(...)` 已经是 message 最终状态和 event 最终状态的统一写入边界，最适合同时落地“本轮生成文件索引”。
- 统一在单轮结束时写一次 `generated_files_json`，避免流式阶段频繁 update message 行，降低写放大与中间态不一致风险。
- 由最终 event 反推生成文件，可以保证 message 级文件列表和 event 级引用来自同一事实来源。

**备选方案**

- 流式过程中每次产出文件都即时更新 `ai_agent_message`：拒绝。数据库写放大明显，而且在 partial/error/force stop 场景里更容易留下不完整索引。
- 查询时再从 `payload_json` 动态提取生成文件：拒绝。这正是本次要消除的使用方式。

### Decision 4: 历史详情与前端类型显式拆分“上传文件”和“生成文件”

**方案**

- `ConversationTurnDetail` 新增 `generatedFiles` 字段，`files` 继续表示上传文件。
- `ConversationTurnRespVO` 新增 `generatedFiles` 字段，Controller 组装时分别映射：
  - `files <- files_json`
  - `generatedFiles <- generated_files_json`
- 前端 `ConversationTurnItem` 增加 `generatedFiles?: CHAT.TFile[]`。
- 历史页面和对话侧栏需要区分两类集合：
  - 上传文件：用户输入附件
  - 生成文件：本轮产物列表
- event 时间线中的文件内容渲染继续依赖 `payload_json.artifactRefs`，而不是依赖 turn 级 `generatedFiles` 倒推。

**原因**

- 用户明确要求保留 `files_json` 语义不变，因此接口层也必须同步保留“上传文件”和“生成文件”两个概念，否则前端仍会混淆。
- turn 级 `generatedFiles` 满足快速查询与列表展示；event 级 `artifactRefs` 满足精确回放与渲染，两者职责不同，不应互相替代。

**备选方案**

- 继续只有 `files` 一个字段并把两类文件混在一起：拒绝。与需求相悖，也会影响现有上传文件的业务语义。

### Decision 5: 读取链路直接切换到新字段模型，不保留旧 event fallback

**方案**

- `ConversationReplayAssembler` 读取 message 时：
  - `files = parseJson(message.getFilesJson())`
  - `generatedFiles = parseJson(message.getGeneratedFilesJson())`
- `SessionArtifactRestoreSupport.collectArtifactRefs(...)` 改为聚合三类新模型来源：
  - 上传文件 `files_json`
  - 生成文件 `generated_files_json`
  - 当前轮事件中的 `artifactRefs`
- 会话文件恢复与历史详情都以新字段和新事件模型为唯一事实来源，不再为旧 payload 结构增加回退分支。
- 发布前直接清理旧历史数据，保证新读取模型面对的都是新结构数据。

**原因**

- 用户已经明确旧数据可直接删除，因此保留兼容代码只会增加复杂度并延长收敛周期。
- 直接切换到新模型可以让消息字段、事件字段、前端类型和会话恢复逻辑保持单一语义，不必长期维护双轨解析。

**备选方案**

- 读取链路保留旧 event fallback：拒绝。会让消息级索引与事件级回退长期并存，违背这次“直接改好”的约束。

## Risks / Trade-offs

- [`generated_files_json` 与 event `artifactRefs` 不一致] → 统一从 `finalOrderedEvents` 生成两边数据，并为完成/异常/强停三种结束态补齐测试。
- [旧历史数据未清理就切到新读取模型] → 发布前清理既有对话历史、事件和会话记忆数据，避免新代码解析旧结构。
- [payload 裁剪过度，前端文件渲染缺字段] → 为文件类事件定义 payload 白名单，并补齐 `html/markdown/ppt/code` 的历史详情回放测试。
- [前端同时展示上传文件和生成文件，出现重复或混淆] → UI 与类型层显式拆分字段命名，列表标题与来源文案区分“上传”/“生成”。
- [预览 URL 不是原始内容地址，前端无法直接渲染 markdown/html] → 约束 `artifactRefs.previewUrl/domainUrl` 必须指向可预览资源；无法保证 raw 内容时退化为下载/外链打开。

## Migration Plan

1. 执行增量 DDL，为 `ai_agent_message` 新增 `generated_files_json JSON NULL`。
2. 上线前清理旧历史对话相关数据，至少覆盖旧会话、消息、事件以及依赖旧 artifact 聚合的会话记忆数据。
3. 发布后端新模型代码：
   - 写入新字段
   - 文件类 event payload 改为引用式
   - 历史详情与会话恢复直接读取新字段模型
4. 发布前端类型与页面改造，接入 turn 级 `generatedFiles`，并继续使用 event `artifactRefs` 做时间线文件渲染。
5. 发布后观察：
   - `ai_agent_message_event.payload_json` 平均体积
   - 每轮生成文件查询结果是否完整
   - 历史回放和会话记忆恢复是否出现文件丢失

**Rollback**

- 新字段是增量字段，回滚时可以保留库表不动，仅回退代码。
- 旧历史数据已经按计划清理，回滚后不再要求恢复旧结构读取能力。
- 若发现 payload 裁剪影响前端，可临时恢复“保留更多轻量字段但不内联全文”的折中写法，而不必撤销新增字段。

## Open Questions

- 无。旧历史数据按上线前清理处理。
