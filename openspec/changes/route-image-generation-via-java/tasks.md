## 1. Java 后端生图链路与持久化基础

- [x] 1.1 在 `ai-agent-station-study-app/src/main/resources/db/schema.sql` 新增图片生成记录表，并补齐对应的 PO、DAO 与 MyBatis Mapper XML
- [x] 1.2 在 `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/gateway/` 新增 Python 生图网关与请求/响应 DTO，使用服务端配置的 `image_generation_url` 调用 `/v1/tool/image_generation`
- [x] 1.3 在 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/` 新增工作台生图应用服务，完成请求校验、Python 响应归一化，以及“每张结果图一条记录”的入库逻辑

## 2. Java 对外接口与历史聚合

- [x] 2.1 在 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/` 新增生图工作台 Controller 与请求/响应 VO，提供 `generate` 与 `history` 接口
- [x] 2.2 在后端历史查询链路中实现按 `requestId` 的两段式分页聚合，确保返回结果按批次组织图片列表而不是单图平铺
- [x] 2.3 统一处理 `X-Device-Id` 解析、Python 错误透传与成功响应包装，保证空历史、上游失败、无图片结果等边界场景行为稳定

## 3. 前端工作台接入 Java API

- [x] 3.1 调整 `ui/src/services/imageGeneration.ts` 与相关类型定义，移除图片模式下的 Python 直连请求，改为调用 Java 生图与历史接口
- [x] 3.2 更新 `ui/src/pages/WorkspaceImageGeneration/index.tsx`、`types.ts`、`utils.ts`，收敛废弃配置项并接入新的生成请求参数与返回结构
- [x] 3.3 在生图工作台页面新增历史列表展示，复用现有图片预览/下载能力，支持查看当前设备之前生成过的图片批次

## 4. 回归验证

- [x] 4.1 为新增表、历史聚合、生成成功/失败分支补充后端测试，重点覆盖多图生成一批次、多设备隔离和失败不落库场景
- [ ] 4.2 运行 `mvn test -pl ai-agent-station-study-app -DskipTests=false`、必要的领域层测试，以及 `cd ui && npm run build` / `npm run lint` 验证前后端改动
