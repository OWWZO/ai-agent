# Contract: Bridge Removal

## Goal

定义本轮哪些 legacy bridge 必须被删除、哪些调用链必须改接稳定 seam，以及 bridge 删除完成后的验收信号。

## 1. Mandatory Removal Set

以下对象在满足稳定 seam 替代后必须删除，不能继续作为生产入口存在：

- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IGptProcessService.java`
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IMultiAgentService.java`
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/GptProcessServiceImpl.java`
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/MultiAgentServiceImpl.java`
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/DataAgentService.java`
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/Nl2SqlService.java`
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/AgentHandlerService.java`
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentHandlerFactory.java`
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/PlanSolveHandlerImpl.java`
- `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/ReactHandlerImpl.java`

## 2. Required Replacement Shape

### GPT Query / Multi-Agent

- `case.query` 只能依赖稳定领域语义接口
- 新接口必须表达“查询请求 + 消息流输出”语义
- `trigger` 不得直接触碰 legacy bridge

### DataAgent

- `case.dataquery` 只能依赖稳定的查询编排、召回、元数据与执行语义接口
- chat、preview、schema recall、nl2sql 不能再默认汇总到 `DataAgentService`
- `DataAgentInitRunner` 只能依赖稳定服务和稳定配置契约

## 3. Forbidden Shape

- 通过新增 facade 或重命名同义接口，继续保留原 bridge 的委派关系
- 在 case 中直接实现原本属于领域语义的复杂逻辑，只为了绕开删除 bridge
- 删除旧接口但保留同名/同义实现类在旧目录下继续承载主逻辑

## 4. Acceptance Signals

满足以下信号时，bridge removal contract 视为成立：

1. 生产代码对 mandatory removal set 的依赖数为 0
2. mandatory removal set 的文件残留数为 0
3. `query` / `dataquery` 入口继续可用，且回归通过
4. 控制器、job、auto-configuration 只依赖新的稳定 seam
5. 旧 `reactor/service/impl` 不再保留无主链路依赖的历史 handler 壳

## 5. Temporary Exceptions

- 若某个旧包名暂时仍承载稳定契约，其内容必须在 [subdomain-ownership-contract.md](./subdomain-ownership-contract.md) 中登记
- “稳定契约暂存”不构成保留 bridge 的理由
