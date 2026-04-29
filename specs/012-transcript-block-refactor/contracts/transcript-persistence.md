# Contract: Transcript Persistence

## Tables

- `ai_agent_turn`
- `ai_agent_transcript_block`
- `ai_agent_display_event`
- `ai_agent_session_memory`

## Write Semantics

### Step 1: Turn Lifecycle

1. 请求被接受后先创建或更新一条 `ai_agent_turn`
2. turn 进入 `STREAMING`
3. 流式结束后 turn 更新为 `COMPLETED / ERROR / STOPPED`

### Step 2: Transcript Blocks

1. 流式事件聚合为标准 `TranscriptBlock`
2. block 类型只允许：
   - `USER_INPUT`
   - `ASSISTANT_THOUGHT`
   - `TOOL_USE`
   - `TOOL_RESULT`
   - `ARTIFACT_REFERENCE`
   - `ASSISTANT_ANSWER`
3. 同一 turn 内通过 `seq_no` 保证顺序
4. `TOOL_USE` 与 `TOOL_RESULT` 通过 `tool_use_id` 关联

### Step 3: Display Events

1. `TranscriptBlock` 写入后立刻同步投影为 `DisplayEvent`
2. `DisplayEvent` 与 `TranscriptBlock` 同事务落库
3. 不允许异步补投影，不允许查询时临时反推展示模型

## Mapping Rules

| TranscriptBlockType | DisplayType |
|---------------------|-------------|
| `USER_INPUT` | `user_message` |
| `ASSISTANT_THOUGHT` | `thought` |
| `TOOL_USE` | `tool_call` |
| `TOOL_RESULT` | `tool_result` |
| `ARTIFACT_REFERENCE` | `artifact` |
| `ASSISTANT_ANSWER` | `final_answer` |

## Atomicity Rules

1. turn 终态更新、block 写入、display event 写入必须以同一业务成功边界完成。
2. 如果同一请求发生重试或整轮重建，调用方必须先清理该 turn 已有的 transcript blocks 和 display events，再执行重建。
3. 不允许存在“turn 已完成但 display events 缺失”的最终态。

## Payload Rules

1. `ai_agent_transcript_block` 保存领域事实：
   - `text`
   - `tool_name`
   - `tool_arguments`
   - `result_payload`
   - `artifact_refs`
2. `ai_agent_display_event` 保存 UI 可直接消费的数据：
   - `content_text`
   - `content_json`
   - `artifact_refs_json`
   - `result_payload_json`
   - `display_props_json`
3. 大体量正文一律不在两张表里重复长文本存储，只保留摘要和稳定引用。

## Final Implementation Notes

1. 旧表 `ai_agent_message`、`ai_agent_message_event` 不再承担任何运行时写入职责。
2. `ai_agent_display_event` 是读模型，不反向驱动 working memory。
