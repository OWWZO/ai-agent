# 精品对话案例展示设计

## 1. 背景与已确认边界

当前项目已经具备两类与本需求直接相关的能力：

1. 会话头与执行账本持久化
   - 会话头由 `ai_agent_dialogue_session` 承接
   - 单次 run、工具调用、产物等信息由 execution ledger 承接
2. 会话历史回放
   - 后端通过 `ConversationHistoryReplayService` 聚合历史详情
   - 前端通过 `hydrateConversationFromReplayFrames` 将历史详情还原成现有对话渲染模型

本次需求不是再造一套“案例正文存储”，而是在既有会话体系之外，增加一层“精品公开发布”能力。

### 1.1 用户已确认的产品边界

1. 只有管理员/运营可以标记精品对话
2. 其他用户通过“精品对话”入口进入独立公共页面
3. 精品详情页是只读案例浏览页，不允许在原会话里继续追问
4. 首页只放少量精选卡片，同时提供独立精品列表页
5. 发布时允许管理员编辑展示信息：
   - 标题
   - 摘要
   - 标签
   - 封面
6. 精品正文不做冻结快照，而是实时读取原会话最新内容
7. 原会话在被设为精品后仍可继续使用
8. 公共详情需要展示：
   - 发布时间
   - 内容最近更新

### 1.2 核心结论

本次实现的本质不是“发布物系统”，而是“会话公开入口系统”：

1. 发布层只负责“是否公开”与“如何展示”
2. 正文层继续复用原会话历史回放链路
3. 公共详情看到的是原会话当前最新内容，而不是发布当时的快照

---

## 2. 目标与非目标

## 2.1 目标

1. 支持管理员将指定会话标记为精品案例
2. 支持首页展示少量精选案例卡片
3. 支持独立精品列表页
4. 支持公共只读详情页
5. 详情页实时展示原会话最新内容
6. 不破坏现有 visitor 私有会话历史接口语义

## 2.2 非目标

1. 本期不做案例正文快照
2. 本期不做消息级裁剪、脱敏或隐藏指定消息块
3. 本期不允许普通用户提交/审核精品案例
4. 本期不在公共详情页提供“继续追问原会话”
5. 本期不改现有会话历史恢复主链路语义
6. 本期不强制建设完整运营后台页面，先保证后端 admin API 闭环

---

## 3. 总体方案

## 3.1 方案选择

本期采用“轻发布层”方案：

1. 新增一张精品发布表，只保存发布元数据
2. 公共详情查询时先查发布记录，再实时查询原会话历史详情
3. 对前端暴露公共精品浏览接口与管理员发布接口两套入口

不采用以下方案：

1. 不做正文快照存储
2. 不做独立公共消息投影表
3. 不把精品能力直接并入现有 visitor 私有会话接口

## 3.2 架构职责

### 会话历史回放职责

`ConversationHistoryReplayService` 继续只负责：

1. 输入 `sessionId`
2. 聚合 execution ledger 中的 run / tool / artifact 信息
3. 输出历史详情 `ConversationHistoryDetail`

它不负责：

1. 判断该会话是否为精品
2. 提供公共发布元数据
3. 管理上下线状态、排序、封面、标签

### 精品发布职责

新增独立的精品发布查询/管理服务，负责：

1. 创建或更新精品发布记录
2. 查询首页精选列表
3. 查询公共精品列表
4. 查询指定精品详情的发布头信息
5. 控制上下线状态

---

## 4. 数据模型设计

## 4.1 新增表

新增表：`ai_agent_featured_conversation`

建议字段如下：

| 字段 | 类型建议 | 说明 |
|------|------|------|
| `id` | bigint | 主键 |
| `featured_id` | varchar | 对外公共标识，建议唯一 |
| `session_id` | varchar | 关联原会话 `session_id`，唯一 |
| `title` | varchar | 管理员编辑后的展示标题 |
| `summary` | varchar/text | 管理员编辑后的展示摘要 |
| `cover_resource_key` | varchar | 封面资源 key，优先复用现有文件服务 |
| `cover_url` | varchar | 封面直链或冗余展示链接 |
| `tags_json` | text | 标签数组 JSON |
| `sort_order` | int | 排序值，值越大越靠前或按现有习惯定义 |
| `status` | varchar | `ONLINE` / `OFFLINE` |
| `published_by` | varchar | 发布人 |
| `published_at` | datetime | 发布时间 |
| `updated_by` | varchar | 最后修改发布元数据的人 |
| `updated_at` | datetime | 发布元数据更新时间 |

## 4.2 设计说明

1. `session_id` 唯一
   - 一个原会话同一时刻只对应一条精品发布记录
2. `featured_id` 与 `session_id` 解耦
   - 公共详情路由主键使用 `featured_id`
   - 后续更换绑定会话、引入 slug、做跳转时更稳
3. 第一版不建议加物理外键
   - 与当前仓库持久化风格更一致
   - 避免后续迁移时增加耦合
4. 正文仍来自原会话
   - 表中不存 `runs_json`、`replay_frames_json` 这类正文快照

## 4.3 与现有表的关系

### 只读依赖

1. `ai_agent_dialogue_session`
   - 读取会话头信息与 `lastActiveAt`
2. execution ledger 相关表
   - 通过既有回放服务实时读取正文内容

### 明确不改

1. `ai_agent_dialogue_session`
2. `ai_agent_dialogue_run`
3. `ai_agent_llm_invocation`
4. `ai_agent_tool_invocation`
5. `ai_agent_artifact`
6. 现有 `ai_agent_tool_output_*`

---

## 5. 后端接口设计

## 5.1 公共浏览接口

公共接口不走 visitor 私有会话归属校验，单独暴露：

### 首页精选

`GET /api/agent/featured-conversations/home?limit=6`

返回：

1. `featuredId`
2. `title`
3. `summary`
4. `coverUrl`
5. `tags`
6. `publishedAt`
7. `contentLastActiveAt`

### 精品列表

`GET /api/agent/featured-conversations?page=1&limit=20`

返回分页或列表结果：

1. `featuredId`
2. `title`
3. `summary`
4. `coverUrl`
5. `tags`
6. `publishedAt`
7. `contentLastActiveAt`

### 精品详情

`GET /api/agent/featured-conversations/{featuredId}`

返回两部分数据：

1. 发布元数据
   - `featuredId`
   - `sessionId`
   - `title`
   - `summary`
   - `coverUrl`
   - `tags`
   - `publishedAt`
   - `contentLastActiveAt`
2. 实时正文详情
   - 复用 `ConversationHistoryDetail` 语义

### 详情接口的异常降级字段

由于正文依赖原会话实时读取，详情接口建议增加：

1. `contentAvailable`
2. `contentUnavailableReason`

用于处理：

1. 原会话不存在
2. 原会话回放异常
3. 原会话正文暂不可读

## 5.2 管理员接口

管理接口单独走 admin 路径，遵循现有 `/api/v1/admin/**` 风格：

### 创建发布记录

`POST /api/v1/admin/featured-conversations/create`

请求字段：

1. `sessionId`
2. `title`
3. `summary`
4. `coverResourceKey`
5. `coverUrl`
6. `tags`
7. `sortOrder`

### 更新发布记录

`PUT /api/v1/admin/featured-conversations/update`

请求字段：

1. `featuredId`
2. `title`
3. `summary`
4. `coverResourceKey`
5. `coverUrl`
6. `tags`
7. `sortOrder`

### 上下线

1. `POST /api/v1/admin/featured-conversations/online/{featuredId}`
2. `POST /api/v1/admin/featured-conversations/offline/{featuredId}`

### 查询单条发布记录

1. `GET /api/v1/admin/featured-conversations/query-by-featured-id/{featuredId}`
2. `GET /api/v1/admin/featured-conversations/query-by-session-id/{sessionId}`

### 查询发布列表

`POST /api/v1/admin/featured-conversations/query-list`

支持条件：

1. `status`
2. `sessionId`
3. `title`
4. 分页参数

## 5.3 接口语义约束

1. 只有 `ONLINE` 记录会出现在公共首页和公共列表
2. 公共详情只读，不校验 visitor 归属
3. 原有 `/api/agent/conversation/sessions/**` 保持 owner-only 语义不变
4. 管理员下线后，公共列表和公共详情立即不可访问

---

## 6. 后端服务与模块落点

## 6.1 推荐分层

### trigger 层

新增：

1. 公共精品浏览 controller
2. admin 精品管理 controller

### case 层

新增应用编排服务：

1. `FeaturedConversationPublicQueryApplicationService`
2. `FeaturedConversationAdminApplicationService`

说明：

1. 公共查询应用服务负责拼装“发布头 + 实时正文”
2. 管理应用服务负责发布记录创建、更新、上下线

### domain 层

新增：

1. 发布记录实体
2. 发布记录查询/写入仓储端口
3. 领域服务或查询服务接口

### infrastructure 层

新增：

1. PO
2. DAO
3. Repository 实现
4. Mapper XML

## 6.2 查询链路

### 公共详情链路

`PublicController -> FeaturedConversationPublicQueryApplicationService -> FeaturedConversationRepository -> ConversationHistoryReplayService`

步骤：

1. 根据 `featuredId` 查发布记录
2. 校验记录存在且 `status == ONLINE`
3. 按发布记录中的 `sessionId` 查询实时历史详情
4. 将发布元数据与历史详情拼装为公共详情结果

### 管理发布链路

`AdminController -> FeaturedConversationAdminApplicationService -> FeaturedConversationRepository + ExecutionLedgerQueryService`

步骤：

1. 校验 `sessionId` 对应会话存在
2. 创建或更新发布记录
3. 不触碰原会话正文结构

---

## 7. 前端设计

## 7.1 路由设计

新增两个公共页面：

1. `/featured-conversations`
2. `/featured-conversations/:featuredId`

说明：

1. 精品浏览是公共阅读域，不应继续塞在当前 visitor 私有会话态中
2. 公共详情路由主键使用 `featuredId`，不直接暴露 `sessionId`

## 7.2 首页精选入口

当前 `WelcomeView.tsx` 已有精选案例占位区域，本期直接复用该入口结构，改为真实数据驱动。

首页展示规则：

1. 展示 3-6 个精品卡片
2. 卡片字段：
   - 标题
   - 摘要
   - 标签
   - 封面
3. 提供“查看全部”入口，跳转精品列表页

## 7.3 侧边栏入口

在当前工作台侧边栏增加一级入口“精品对话”：

1. 点击后跳转 `/featured-conversations`
2. 它不属于“最近会话”
3. 它不承载 visitor 私有 session 切换语义

## 7.4 精品列表页

列表页第一版以简洁扫描为主：

1. 顶部标题与说明
2. 卡片列表
3. 支持分页或“加载更多”
4. 卡片点击进入详情页

## 7.5 精品详情页

详情页是只读案例浏览页，采用“只读容器 + 复用现有消息渲染链”的方案。

### 头部区域

展示：

1. 标题
2. 摘要
3. 标签
4. 发布时间
5. 内容最近更新

字段定义：

1. `发布时间`
   - 管理员将该会话公开为精品的时间
   - 来源：`publishedAt`
2. `内容最近更新`
   - 原会话正文最近一次变化时间
   - 来源：原会话 `lastActiveAt`

不建议对外文案使用“最近更新时间”，避免和管理员修改标题/摘要混淆。

### 正文区域

正文复用现有回放数据恢复能力：

1. 后端继续返回与 `ConversationHistoryDetail` 同构的历史详情
2. 前端继续用 `hydrateConversationFromReplayFrames` 还原会话数据
3. 消息展示复用现有 `Dialogue` / `DataDialogue` / 附件展示能力

### 明确不复用 `ChatView` 整体壳

原因：

1. `ChatView` 内聚了输入框、SSE、重试、工作区状态
2. 精品详情页只需要只读展示
3. 直接复用 `ChatView` 会引入多余交互语义

因此应新增只读容器，例如：

`FeaturedConversationTranscriptView`

它负责：

1. 请求公共详情
2. 还原只读会话模型
3. 调用现有 renderer 渲染正文

它不负责：

1. 发送消息
2. 继续追问
3. regenerate
4. 工作区联动操作

---

## 8. 权限与风险控制

## 8.1 权限边界

1. 私有会话接口仍按 visitor 归属校验
2. 公共精品接口只按发布状态校验
3. admin 发布接口只允许管理员调用

## 8.2 方案风险

### 风险 1：精品页内容会随原会话变化

这是本方案的主动选择。

含义：

1. 原会话后续新增消息，精品页会变化
2. 原会话后续补附件、补总结，精品页会变化
3. 精品页不是冻结案例，而是公开入口

### 风险 2：原会话异常会影响公共详情

例如：

1. 原会话不存在
2. 原会话回放失败
3. 部分附件失效

应对方式：

1. 公共详情接口提供正文可用性字段
2. 前端提供降级提示页

### 风险 3：内容治理依赖管理员审核

本期不做：

1. 快照
2. 裁剪
3. 消息级脱敏

因此管理员发布前必须人工确认整个原会话可公开。

---

## 9. 实施顺序

## 9.1 第一阶段：后端发布层闭环

1. 新增表、PO、DAO、Mapper XML、schema
2. 完成发布记录 repository
3. 完成公共接口
4. 完成 admin 接口

## 9.2 第二阶段：公共前端浏览层

1. 首页精选卡片
2. 精品列表页
3. 精品详情页

## 9.3 第三阶段：运营使用闭环

1. 先保证 admin API 可被内部调用
2. 是否补独立管理页，后续再定

---

## 10. 验收标准

1. 管理员可基于已有 `sessionId` 创建精品发布记录
2. 只有 `ONLINE` 记录会出现在首页精选与公共列表
3. 公共详情页能读取管理员配置的标题、摘要、标签、封面
4. 公共详情正文实时展示原会话最新内容
5. 原会话 owner 私有历史接口语义不受影响
6. 管理员下线后，公共详情立即不可访问
7. 详情页明确展示：
   - `发布时间`
   - `内容最近更新`
8. 当原会话正文不可用时，详情页能给出明确降级提示，而不是直接崩溃

---

## 11. 测试建议

## 11.1 后端

1. 公共首页列表只返回 `ONLINE` 记录
2. 公共详情可正确拼装“发布头 + 实时正文”
3. 下线后公共列表与详情不可访问
4. 源 `sessionId` 不存在时创建发布记录失败
5. 源会话正文不可读时返回可降级结果
6. 原有 `/api/agent/conversation/sessions/**` 仍维持 visitor 归属校验

## 11.2 前端

1. 首页精选卡片正常展示与跳转
2. 精品列表页正常加载与翻页
3. 精品详情页只读展示，不出现输入框和继续追问能力
4. 详情页头部正确展示 `发布时间` 与 `内容最近更新`
5. 正文异常时能展示降级提示

---

## 12. 后续演进方向

如果后续要把“公开入口”升级为真正的“发布案例”，再考虑第二阶段能力：

1. 发布快照
2. 指定 run 截断
3. 消息块裁剪
4. 附件脱敏
5. 普通用户投稿 + 审核流

本期不预埋第二套正文存储，只保持发布层和正文层解耦，便于将来平滑演进。
