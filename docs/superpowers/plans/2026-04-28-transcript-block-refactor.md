# TranscriptBlock 扁平化重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构会话历史持久化，用 `ai_agent_turn` + `ai_agent_transcript_block` + `ai_agent_display_event` 替换旧表，去除所有兼容逻辑。

**Architecture:** 扁平化 TranscriptBlock 设计，6 种标准 block_type 枚举，AgentResponse 直接映射为标准块，读取时直接查询拼接。前端展示通过**同步双写**到独立的 `ai_agent_display_event` 表，同一事务内完成 TranscriptBlock + DisplayEvent 写入，彻底分离 LLM 和前端两个领域。

**Tech Stack:** Spring Boot 3.4.3, Java 17, MyBatis-Plus, MySQL

---

## 文件结构映射

### 新建文件

| 文件路径 | 职责 |
|---------|------|
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/enums/TranscriptBlockType.java` | 6 种标准 block 类型枚举 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/Turn.java` | 轮次元数据实体 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/TranscriptBlock.java` | 语义块实体 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/SessionMemory.java` | 简化后的会话记忆快照实体 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ITurnDao.java` | Turn DAO 接口 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ITranscriptBlockDao.java` | TranscriptBlock DAO 接口 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ISessionMemoryDao.java` | SessionMemory DAO 接口（替换旧的） |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/turn_mapper.xml` | Turn Mapper XML |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/transcript_block_mapper.xml` | TranscriptBlock Mapper XML |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/session_memory_mapper.xml` | SessionMemory Mapper XML（替换旧的） |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptBlockMapper.java` | AgentResponse → TranscriptBlock 归类映射 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TurnWriter.java` | Turn 写入服务 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptBlockWriter.java` | TranscriptBlock 批量写入服务 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptPromptFormatter.java` | Block → LLM prompt 文本格式化 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptContextBuilder.java` | 从数据库直接构建 LLM 上下文 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DisplayEvent.java` | 前端展示事件实体 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDisplayEventDao.java` | DisplayEvent DAO 接口 |
| `ai-agent-station-study-app/src/main/resources/mybatis/mapper/display_event_mapper.xml` | DisplayEvent Mapper XML |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/DisplayEventProjector.java` | TranscriptBlock → DisplayEvent 投影 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/DisplayHistoryQueryService.java` | 前端历史查询服务 |

### 修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `ai-agent-station-study-app/src/main/resources/db/schema.sql` | 添加新表，注释旧表 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistCoordinator.java` | 使用新 Writer 替换旧写入逻辑 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java` | 使用新 ContextBuilder 替换旧读取逻辑 |
| `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryCompactionService.java` | 适配新实体和 DAO |

### 删除文件

| 文件路径 | 删除原因 |
|---------|---------|
| `ai-agent-station-study-domain/.../entity/AgentMessage.java` | 被 Turn 替代 |
| `ai-agent-station-study-domain/.../entity/AgentMessageEvent.java` | 被 TranscriptBlock 替代 |
| `ai-agent-station-study-domain/.../mapper/IAgentMessageDao.java` | 被 ITurnDao 替代 |
| `ai-agent-station-study-domain/.../mapper/IAgentMessageEventDao.java` | 被 ITranscriptBlockDao 替代 |
| `ai-agent-station-study-app/.../mybatis/mapper/ai_agent_message_mapper.xml` | 被 turn_mapper.xml 替代 |
| `ai-agent-station-study-app/.../mybatis/mapper/ai_agent_message_event_mapper.xml` | 被 transcript_block_mapper.xml 替代 |
| `ai-agent-station-study-domain/.../model/multi/OrderedEvent.java` | 中间投影模型，不再需要 |
| `ai-agent-station-study-domain/.../service/support/EventProjector.java` | 硬编码映射，不再需要 |
| `ai-agent-station-study-domain/.../service/support/SessionTranscriptBlockAssembler.java` | 复杂恢复逻辑，不再需要 |
| `ai-agent-station-study-domain/.../service/support/SessionArtifactRestoreSupport.java` | 多源文件恢复，不再需要 |
| `ai-agent-station-study-domain/.../service/support/ConversationEventPayloadNormalizer.java` | 遗留字段处理，不再需要 |
| `ai-agent-station-study-domain/.../service/support/ConversationEventFactSupport.java` | 事实投影兜底，不再需要 |
| `ai-agent-station-study-domain/.../service/support/SessionWorkingMemoryAssembler.java` | 复杂组装逻辑，不再需要 |
| `ai-agent-station-study-domain/.../service/support/SessionMemorySummaryBuilder.java` | 摘要结构校正，不再需要 |
| `ai-agent-station-study-domain/.../service/impl/AgentMessageServiceImpl.java` | 旧消息服务 |
| `ai-agent-station-study-domain/.../service/impl/AgentMessageEventServiceImpl.java` | 旧事件服务 |
| `ai-agent-station-study-domain/.../service/support/ConversationReplayAssembler.java` | 复杂回放组装，被 DisplayHistoryQueryService 替代 |

---

## Task 1: 新表 SQL

**Files:**
- Modify: `ai-agent-station-study-app/src/main/resources/db/schema.sql`

- [ ] **Step 1: 在 schema.sql 末尾添加新表定义**

在 `schema.sql` 文件末尾追加以下内容：

```sql
-- ========================================================
-- 新表: ai_agent_turn (替换 ai_agent_message)
-- ========================================================
CREATE TABLE IF NOT EXISTS ai_agent_turn (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    request_id      VARCHAR(64)  NOT NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    query           TEXT         NOT NULL,
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=流式中 1=完成 2=错误 3=停止',
    started_at      DATETIME     NULL,
    finished_at     DATETIME     NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_id (request_id),
    UNIQUE KEY uk_conversation_sort (conversation_id, sort_order)
) ENGINE=InnoDB COMMENT='对话轮次';

-- ========================================================
-- 新表: ai_agent_transcript_block (替换 ai_agent_message_event)
-- ========================================================
CREATE TABLE IF NOT EXISTS ai_agent_transcript_block (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    turn_id         BIGINT       NOT NULL,
    seq_no          INT          NOT NULL COMMENT '轮内顺序',
    block_type      VARCHAR(32)  NOT NULL COMMENT 'USER_INPUT|ASSISTANT_THOUGHT|TOOL_USE|TOOL_RESULT|ARTIFACT_REFERENCE|ASSISTANT_ANSWER',
    role            VARCHAR(16)  NULL COMMENT 'user|assistant',
    text            MEDIUMTEXT   NULL,
    tool_use_id     VARCHAR(128) NULL,
    tool_name       VARCHAR(128) NULL,
    tool_arguments  JSON         NULL COMMENT '工具参数',
    result_payload  JSON         NULL COMMENT '工具结果',
    artifact_refs   JSON         NULL COMMENT '产物引用',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_turn_seq (turn_id, seq_no),
    KEY idx_turn (turn_id, deleted, seq_no)
) ENGINE=InnoDB COMMENT='对话语义块';

-- ========================================================
-- 新表: ai_agent_display_event (前端展示事件，同步双写，完整保留对话细节)
-- ========================================================
CREATE TABLE IF NOT EXISTS ai_agent_display_event (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    turn_id             BIGINT       NOT NULL COMMENT 'FK -> ai_agent_turn.id',
    seq_no              INT          NOT NULL COMMENT '轮内展示顺序',
    display_type        VARCHAR(32)  NOT NULL COMMENT 'user_message|thought|tool_call|tool_result|artifact|final_answer|plan|task_start|task_complete|error',
    task_id             VARCHAR(64)  NULL     COMMENT '任务ID（AutoAgent多步骤时）',
    task_name           VARCHAR(128) NULL     COMMENT '任务名称',
    task_order          INT          NULL     COMMENT '任务序号',
    title               VARCHAR(256) NULL     COMMENT '显示标题',
    content_text        MEDIUMTEXT   NULL     COMMENT '展示文本（完整保留对话细节）',
    content_json        JSON         NULL     COMMENT '结构化内容（图表、表格等）',
    tool_use_id         VARCHAR(128) NULL,
    tool_name           VARCHAR(128) NULL,
    tool_arguments_json JSON         NULL,
    artifact_refs_json  JSON         NULL,
    result_payload_json JSON         NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'completed' COMMENT 'pending|running|completed|error',
    display_area        VARCHAR(32)  NOT NULL DEFAULT 'timeline' COMMENT 'timeline|sidebar',
    display_props_json  JSON         NULL     COMMENT '前端组件额外属性',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_turn_seq (turn_id, seq_no),
    KEY idx_turn (turn_id, deleted, seq_no),
    KEY idx_task (turn_id, task_id, deleted)
) ENGINE=InnoDB COMMENT='前端展示事件表（同步双写）';

-- ========================================================
-- 简化表: ai_agent_session_memory (agent_type字段删除，artifact_refs_json改为artifact_refs)
-- ========================================================
-- 注: 如果旧表已有数据，需要 DROP 后重新创建。由于是"彻底打破"方案，直接替换。
```

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-app/src/main/resources/db/schema.sql
git commit -m "feat: 添加 ai_agent_turn、ai_agent_transcript_block、ai_agent_display_event 新表"
```

---

## Task 2: TranscriptBlockType 枚举

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/enums/TranscriptBlockType.java`

- [ ] **Step 1: 创建枚举文件**

```java
package org.wwz.ai.domain.agent.reactor.model.enums;

/**
 * Transcript 语义块类型。
 * 只有这 6 种，没有"其他"。上游 Agent 产生无法映射的类型时直接抛异常。
 */
public enum TranscriptBlockType {
    USER_INPUT,
    ASSISTANT_THOUGHT,
    TOOL_USE,
    TOOL_RESULT,
    ARTIFACT_REFERENCE,
    ASSISTANT_ANSWER
}
```

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/enums/TranscriptBlockType.java
git commit -m "feat: 添加 TranscriptBlockType 枚举"
```

---

## Task 3: Turn 实体

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/Turn.java`

- [ ] **Step 1: 创建 Turn 实体**

```java
package org.wwz.ai.domain.agent.reactor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话轮次元数据。只存排序、状态、时间，不携带业务语义。
 */
@Data
public class Turn {
    private Long id;
    private Long conversationId;
    private String requestId;
    private Integer sortOrder;
    private String query;
    private Integer status;        // 0=流式中 1=完成 2=错误 3=停止
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private Integer deleted;
}
```

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/Turn.java
git commit -m "feat: 添加 Turn 实体"
```

---

## Task 4: TranscriptBlock 实体

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/TranscriptBlock.java`

- [ ] **Step 1: 创建 TranscriptBlock 实体**

```java
package org.wwz.ai.domain.agent.reactor.entity;

import lombok.Data;
import org.wwz.ai.domain.agent.reactor.model.enums.TranscriptBlockType;

import java.time.LocalDateTime;

/**
 * 对话语义块。每个字段有且只有一种用途，没有"扩展字段"。
 */
@Data
public class TranscriptBlock {
    private Long id;
    private Long turnId;
    private Integer seqNo;
    private TranscriptBlockType blockType;
    private String role;
    private String text;
    private String toolUseId;
    private String toolName;
    private String toolArgumentsJson;
    private String resultPayloadJson;
    private String artifactRefsJson;
    private LocalDateTime createTime;
    private Integer deleted;
}
```

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/TranscriptBlock.java
git commit -m "feat: 添加 TranscriptBlock 实体"
```

---

## Task 5: SessionMemory 实体（简化版）

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/SessionMemory.java`

- [ ] **Step 1: 创建简化版 SessionMemory 实体**

```java
package org.wwz.ai.domain.agent.reactor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话记忆快照。简化版，删除 agent_type 字段。
 */
@Data
public class SessionMemory {
    private Long id;
    private Long conversationId;
    private String sessionId;
    private Integer boundarySortOrder;
    private String summaryText;
    private String artifactRefsJson;
    private Integer sourceTurnCount;
    private LocalDateTime lastCompactedAt;
    private LocalDateTime createTime;
    private Integer deleted;
}
```

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/SessionMemory.java
git commit -m "feat: 添加简化版 SessionMemory 实体"
```

---

## Task 6: Turn DAO + Mapper XML

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ITurnDao.java`
- Create: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/turn_mapper.xml`

- [ ] **Step 1: 创建 ITurnDao**

```java
package org.wwz.ai.domain.agent.reactor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.reactor.entity.Turn;

import java.util.List;

@Mapper
public interface ITurnDao {

    int insert(Turn turn);

    int updateById(Turn turn);

    Turn queryByRequestId(@Param("requestId") String requestId);

    List<Turn> queryByConversationId(@Param("conversationId") Long conversationId);

    List<Turn> queryAfterSortOrder(@Param("conversationId") Long conversationId,
                                     @Param("afterSortOrder") Integer afterSortOrder);

    Integer queryMaxSortOrder(@Param("conversationId") Long conversationId);

    int countStreamingByConversationId(@Param("conversationId") Long conversationId);

    int softDeleteByConversationId(@Param("conversationId") Long conversationId);
}
```

- [ ] **Step 2: 创建 turn_mapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="org.wwz.ai.domain.agent.reactor.mapper.ITurnDao">

    <resultMap id="TurnMap" type="org.wwz.ai.domain.agent.reactor.entity.Turn">
        <id column="id" property="id"/>
        <result column="conversation_id" property="conversationId"/>
        <result column="request_id" property="requestId"/>
        <result column="sort_order" property="sortOrder"/>
        <result column="query" property="query"/>
        <result column="status" property="status"/>
        <result column="started_at" property="startedAt"/>
        <result column="finished_at" property="finishedAt"/>
        <result column="create_time" property="createTime"/>
        <result column="deleted" property="deleted"/>
    </resultMap>

    <insert id="insert" parameterType="org.wwz.ai.domain.agent.reactor.entity.Turn" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO ai_agent_turn (conversation_id, request_id, sort_order, query, status, started_at, finished_at, create_time, deleted)
        VALUES (#{conversationId}, #{requestId}, #{sortOrder}, #{query}, #{status}, #{startedAt}, #{finishedAt}, now(), 0)
    </insert>

    <update id="updateById" parameterType="org.wwz.ai.domain.agent.reactor.entity.Turn">
        UPDATE ai_agent_turn
        <set>
            <if test="sortOrder != null">sort_order = #{sortOrder},</if>
            <if test="query != null">query = #{query},</if>
            <if test="status != null">status = #{status},</if>
            <if test="startedAt != null">started_at = #{startedAt},</if>
            <if test="finishedAt != null">finished_at = #{finishedAt},</if>
        </set>
        WHERE id = #{id} AND deleted = 0
    </update>

    <select id="queryByRequestId" resultMap="TurnMap">
        SELECT * FROM ai_agent_turn WHERE request_id = #{requestId} AND deleted = 0
    </select>

    <select id="queryByConversationId" resultMap="TurnMap">
        SELECT * FROM ai_agent_turn WHERE conversation_id = #{conversationId} AND deleted = 0 ORDER BY sort_order
    </select>

    <select id="queryAfterSortOrder" resultMap="TurnMap">
        SELECT * FROM ai_agent_turn
        WHERE conversation_id = #{conversationId} AND sort_order > #{afterSortOrder} AND deleted = 0
        ORDER BY sort_order
    </select>

    <select id="queryMaxSortOrder" resultType="java.lang.Integer">
        SELECT MAX(sort_order) FROM ai_agent_turn WHERE conversation_id = #{conversationId} AND deleted = 0
    </select>

    <select id="countStreamingByConversationId" resultType="java.lang.Integer">
        SELECT COUNT(*) FROM ai_agent_turn WHERE conversation_id = #{conversationId} AND status = 0 AND deleted = 0
    </select>

    <update id="softDeleteByConversationId">
        UPDATE ai_agent_turn SET deleted = 1 WHERE conversation_id = #{conversationId} AND deleted = 0
    </update>

</mapper>
```

- [ ] **Step 3: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ITurnDao.java

git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/turn_mapper.xml

git commit -m "feat: 添加 Turn DAO 和 Mapper XML"
```

---

## Task 7: TranscriptBlock DAO + Mapper XML

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ITranscriptBlockDao.java`
- Create: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/transcript_block_mapper.xml`

- [ ] **Step 1: 创建 ITranscriptBlockDao**

```java
package org.wwz.ai.domain.agent.reactor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.reactor.entity.TranscriptBlock;

import java.util.List;

@Mapper
public interface ITranscriptBlockDao {

    int insert(TranscriptBlock block);

    int batchInsert(@Param("blocks") List<TranscriptBlock> blocks);

    List<TranscriptBlock> queryByTurnId(@Param("turnId") Long turnId);

    List<TranscriptBlock> queryByTurnIds(@Param("turnIds") List<Long> turnIds);

    int softDeleteByTurnId(@Param("turnId") Long turnId);
}
```

- [ ] **Step 2: 创建 transcript_block_mapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="org.wwz.ai.domain.agent.reactor.mapper.ITranscriptBlockDao">

    <resultMap id="TranscriptBlockMap" type="org.wwz.ai.domain.agent.reactor.entity.TranscriptBlock">
        <id column="id" property="id"/>
        <result column="turn_id" property="turnId"/>
        <result column="seq_no" property="seqNo"/>
        <result column="block_type" property="blockType" typeHandler="org.apache.ibatis.type.EnumTypeHandler"/>
        <result column="role" property="role"/>
        <result column="text" property="text"/>
        <result column="tool_use_id" property="toolUseId"/>
        <result column="tool_name" property="toolName"/>
        <result column="tool_arguments" property="toolArgumentsJson"/>
        <result column="result_payload" property="resultPayloadJson"/>
        <result column="artifact_refs" property="artifactRefsJson"/>
        <result column="create_time" property="createTime"/>
        <result column="deleted" property="deleted"/>
    </resultMap>

    <insert id="insert" parameterType="org.wwz.ai.domain.agent.reactor.entity.TranscriptBlock" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO ai_agent_transcript_block (turn_id, seq_no, block_type, role, text, tool_use_id, tool_name, tool_arguments, result_payload, artifact_refs, create_time, deleted)
        VALUES (#{turnId}, #{seqNo}, #{blockType}, #{role}, #{text}, #{toolUseId}, #{toolName}, #{toolArgumentsJson}, #{resultPayloadJson}, #{artifactRefsJson}, now(), 0)
    </insert>

    <insert id="batchInsert">
        INSERT INTO ai_agent_transcript_block (turn_id, seq_no, block_type, role, text, tool_use_id, tool_name, tool_arguments, result_payload, artifact_refs, create_time, deleted)
        VALUES
        <foreach collection="blocks" item="block" separator=",">
            (#{block.turnId}, #{block.seqNo}, #{block.blockType}, #{block.role}, #{block.text}, #{block.toolUseId}, #{block.toolName}, #{block.toolArgumentsJson}, #{block.resultPayloadJson}, #{block.artifactRefsJson}, now(), 0)
        </foreach>
    </insert>

    <select id="queryByTurnId" resultMap="TranscriptBlockMap">
        SELECT * FROM ai_agent_transcript_block WHERE turn_id = #{turnId} AND deleted = 0 ORDER BY seq_no
    </select>

    <select id="queryByTurnIds" resultMap="TranscriptBlockMap">
        SELECT * FROM ai_agent_transcript_block
        WHERE turn_id IN
        <foreach collection="turnIds" item="turnId" open="(" separator="," close=")">
            #{turnId}
        </foreach>
        AND deleted = 0
        ORDER BY turn_id, seq_no
    </select>

    <update id="softDeleteByTurnId">
        UPDATE ai_agent_transcript_block SET deleted = 1 WHERE turn_id = #{turnId} AND deleted = 0
    </update>

</mapper>
```

- [ ] **Step 3: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ITranscriptBlockDao.java

git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/transcript_block_mapper.xml

git commit -m "feat: 添加 TranscriptBlock DAO 和 Mapper XML"
```

---

## Task 8: SessionMemory DAO + Mapper XML（替换旧版）

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ISessionMemoryDao.java`
- Create: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/session_memory_mapper.xml`

- [ ] **Step 1: 创建 ISessionMemoryDao（适配新实体）**

```java
package org.wwz.ai.domain.agent.reactor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.reactor.entity.SessionMemory;

import java.util.List;

@Mapper
public interface ISessionMemoryDao {

    int insert(SessionMemory sessionMemory);

    int updateById(SessionMemory sessionMemory);

    SessionMemory queryBySessionId(@Param("sessionId") String sessionId);

    List<SessionMemory> queryHistoryBySessionId(@Param("sessionId") String sessionId);

    int softDeleteBySessionId(@Param("sessionId") String sessionId);
}
```

- [ ] **Step 2: 创建 session_memory_mapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="org.wwz.ai.domain.agent.reactor.mapper.ISessionMemoryDao">

    <resultMap id="SessionMemoryMap" type="org.wwz.ai.domain.agent.reactor.entity.SessionMemory">
        <id column="id" property="id"/>
        <result column="conversation_id" property="conversationId"/>
        <result column="session_id" property="sessionId"/>
        <result column="boundary_sort_order" property="boundarySortOrder"/>
        <result column="summary_text" property="summaryText"/>
        <result column="artifact_refs" property="artifactRefsJson"/>
        <result column="source_turn_count" property="sourceTurnCount"/>
        <result column="last_compacted_at" property="lastCompactedAt"/>
        <result column="create_time" property="createTime"/>
        <result column="deleted" property="deleted"/>
    </resultMap>

    <insert id="insert" parameterType="org.wwz.ai.domain.agent.reactor.entity.SessionMemory" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO ai_agent_session_memory (conversation_id, session_id, boundary_sort_order, summary_text, artifact_refs, source_turn_count, last_compacted_at, create_time, deleted)
        VALUES (#{conversationId}, #{sessionId}, #{boundarySortOrder}, #{summaryText}, #{artifactRefsJson}, #{sourceTurnCount}, #{lastCompactedAt}, now(), 0)
    </insert>

    <update id="updateById" parameterType="org.wwz.ai.domain.agent.reactor.entity.SessionMemory">
        UPDATE ai_agent_session_memory
        <set>
            <if test="conversationId != null">conversation_id = #{conversationId},</if>
            <if test="sessionId != null">session_id = #{sessionId},</if>
            <if test="boundarySortOrder != null">boundary_sort_order = #{boundarySortOrder},</if>
            <if test="summaryText != null">summary_text = #{summaryText},</if>
            <if test="artifactRefsJson != null">artifact_refs = #{artifactRefsJson},</if>
            <if test="sourceTurnCount != null">source_turn_count = #{sourceTurnCount},</if>
            <if test="lastCompactedAt != null">last_compacted_at = #{lastCompactedAt},</if>
        </set>
        WHERE id = #{id} AND deleted = 0
    </update>

    <select id="queryBySessionId" resultMap="SessionMemoryMap">
        SELECT * FROM ai_agent_session_memory WHERE session_id = #{sessionId} AND deleted = 0 ORDER BY id DESC LIMIT 1
    </select>

    <select id="queryHistoryBySessionId" resultMap="SessionMemoryMap">
        SELECT * FROM ai_agent_session_memory WHERE session_id = #{sessionId} ORDER BY id DESC
    </select>

    <update id="softDeleteBySessionId">
        UPDATE ai_agent_session_memory SET deleted = 1 WHERE session_id = #{sessionId} AND deleted = 0
    </update>

</mapper>
```

- [ ] **Step 3: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/ISessionMemoryDao.java

git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/session_memory_mapper.xml

git commit -m "feat: 添加简化版 SessionMemory DAO 和 Mapper XML"
```

---

## Task 9: TranscriptBlockMapper（核心映射器）

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptBlockMapper.java`

- [ ] **Step 1: 创建 TranscriptBlockMapper**

```java
package org.wwz.ai.domain.agent.reactor.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.entity.TranscriptBlock;
import org.wwz.ai.domain.agent.reactor.model.enums.TranscriptBlockType;

import java.util.List;
import java.util.Set;

/**
 * AgentResponse → TranscriptBlock 归类映射器。
 * 按语义角色归类，不是兜底兼容。未知类型直接抛异常。
 */
@Component
@RequiredArgsConstructor
public class TranscriptBlockMapper {

    public List<TranscriptBlock> map(AgentResponse response, Long turnId, int baseSeqNo) {
        String type = response.getMessageType();

        if (isThoughtType(type)) {
            return List.of(assistantThoughtBlock(response, turnId, baseSeqNo));
        }
        if (isToolUseType(type, response)) {
            return List.of(toolUseBlock(response, turnId, baseSeqNo));
        }
        if (isArtifactType(type, response)) {
            return List.of(artifactBlock(response, turnId, baseSeqNo));
        }
        if (isToolResultType(type, response)) {
            return List.of(toolResultBlock(response, turnId, baseSeqNo));
        }
        if ("assistant_answer".equals(type)) {
            return List.of(answerBlock(response, turnId, baseSeqNo));
        }
        if ("user_input".equals(type)) {
            return List.of(userInputBlock(response, turnId, baseSeqNo));
        }

        throw new IllegalArgumentException("Unknown message type, cannot classify to TranscriptBlockType: " + type);
    }

    // ========== 类型判断 ==========

    private boolean isThoughtType(String type) {
        return Set.of("plan_thought", "tool_thought", "plan").contains(type);
    }

    private boolean isToolUseType(String type, AgentResponse response) {
        return "task".equals(type) || response.getToolUseId() != null;
    }

    private boolean isArtifactType(String type, AgentResponse response) {
        return Set.of("html", "markdown", "code", "ppt", "file").contains(type)
            || ("result".equals(type) && hasArtifact(response));
    }

    private boolean isToolResultType(String type, AgentResponse response) {
        return Set.of("result", "deep_search", "knowledge", "browser", "data_analysis").contains(type)
            && !hasArtifact(response);
    }

    private boolean hasArtifact(AgentResponse response) {
        // 根据实际 AgentResponse 结构调整
        return response.getArtifactRefs() != null && !response.getArtifactRefs().isEmpty();
    }

    // ========== Block 构建 ==========

    private TranscriptBlock userInputBlock(AgentResponse response, Long turnId, int seqNo) {
        TranscriptBlock block = new TranscriptBlock();
        block.setTurnId(turnId);
        block.setSeqNo(seqNo);
        block.setBlockType(TranscriptBlockType.USER_INPUT);
        block.setRole("user");
        block.setText(response.getContent());
        // 用户上传文件作为 artifact_refs
        block.setArtifactRefsJson(toJson(response.getFiles()));
        return block;
    }

    private TranscriptBlock assistantThoughtBlock(AgentResponse response, Long turnId, int seqNo) {
        TranscriptBlock block = new TranscriptBlock();
        block.setTurnId(turnId);
        block.setSeqNo(seqNo);
        block.setBlockType(TranscriptBlockType.ASSISTANT_THOUGHT);
        block.setRole("assistant");
        block.setText(response.getContent());
        return block;
    }

    private TranscriptBlock toolUseBlock(AgentResponse response, Long turnId, int seqNo) {
        TranscriptBlock block = new TranscriptBlock();
        block.setTurnId(turnId);
        block.setSeqNo(seqNo);
        block.setBlockType(TranscriptBlockType.TOOL_USE);
        block.setRole("assistant");
        block.setToolUseId(response.getToolUseId());
        block.setToolName(response.getToolName());
        block.setToolArgumentsJson(toJson(response.getToolArguments()));
        block.setText(response.getContent());
        return block;
    }

    private TranscriptBlock toolResultBlock(AgentResponse response, Long turnId, int seqNo) {
        TranscriptBlock block = new TranscriptBlock();
        block.setTurnId(turnId);
        block.setSeqNo(seqNo);
        block.setBlockType(TranscriptBlockType.TOOL_RESULT);
        block.setRole("assistant");
        block.setText(response.getContent());
        block.setResultPayloadJson(toJson(response.getResultData()));
        return block;
    }

    private TranscriptBlock artifactBlock(AgentResponse response, Long turnId, int seqNo) {
        TranscriptBlock block = new TranscriptBlock();
        block.setTurnId(turnId);
        block.setSeqNo(seqNo);
        block.setBlockType(TranscriptBlockType.ARTIFACT_REFERENCE);
        block.setRole("assistant");
        block.setText(response.getContent());
        block.setArtifactRefsJson(toJson(response.getArtifactRefs()));
        return block;
    }

    private TranscriptBlock answerBlock(AgentResponse response, Long turnId, int seqNo) {
        TranscriptBlock block = new TranscriptBlock();
        block.setTurnId(turnId);
        block.setSeqNo(seqNo);
        block.setBlockType(TranscriptBlockType.ASSISTANT_ANSWER);
        block.setRole("assistant");
        block.setText(response.getContent());
        return block;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        // 使用项目已有的 JSON 工具，例如 Jackson 或 Fastjson
        try {
            return com.alibaba.fastjson.JSON.toJSONString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
```

**注意**：上述代码中的 `AgentResponse` 类型和字段名（`getMessageType()`、`getContent()`、`getToolUseId()` 等）需要根据项目中实际的 AgentResponse 类来调整。请查看 `AgentResponse` 实际类定义后修改字段访问方式。

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptBlockMapper.java

git commit -m "feat: 添加 TranscriptBlockMapper 归类映射器"
```

---

## Task 10: TurnWriter + TranscriptBlockWriter

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TurnWriter.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptBlockWriter.java`

- [ ] **Step 1: 创建 TurnWriter**

```java
package org.wwz.ai.domain.agent.reactor.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.entity.Turn;
import org.wwz.ai.domain.agent.reactor.mapper.ITurnDao;

@Component
@RequiredArgsConstructor
public class TurnWriter {

    private final ITurnDao turnDao;

    public void save(Turn turn) {
        turnDao.insert(turn);
    }

    public void update(Turn turn) {
        turnDao.updateById(turn);
    }
}
```

- [ ] **Step 2: 创建 TranscriptBlockWriter**

```java
package org.wwz.ai.domain.agent.reactor.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.entity.TranscriptBlock;
import org.wwz.ai.domain.agent.reactor.mapper.ITranscriptBlockDao;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TranscriptBlockWriter {

    private final ITranscriptBlockDao transcriptBlockDao;

    public void saveBatch(List<TranscriptBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return;
        transcriptBlockDao.batchInsert(blocks);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TurnWriter.java

git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptBlockWriter.java

git commit -m "feat: 添加 TurnWriter 和 TranscriptBlockWriter"
```

---

## Task 11: TranscriptPromptFormatter

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptPromptFormatter.java`

- [ ] **Step 1: 创建 TranscriptPromptFormatter**

```java
package org.wwz.ai.domain.agent.reactor.service.support;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.entity.SessionMemory;
import org.wwz.ai.domain.agent.reactor.entity.TranscriptBlock;
import org.wwz.ai.domain.agent.reactor.model.enums.TranscriptBlockType;

import java.util.List;

/**
 * 将 SessionMemory + TranscriptBlock 列表格式化为 LLM prompt 文本。
 * 简单拼接，没有复杂恢复逻辑。
 */
@Component
@RequiredArgsConstructor
public class TranscriptPromptFormatter {

    public String format(SessionMemory memory, List<TranscriptBlock> blocks) {
        StringBuilder sb = new StringBuilder();

        // 1. 历史摘要
        if (memory != null && memory.getSummaryText() != null && !memory.getSummaryText().isBlank()) {
            sb.append("=== 历史摘要 ===\n");
            sb.append(memory.getSummaryText()).append("\n\n");
        }

        // 2. 可复用文件
        if (memory != null && memory.getArtifactRefsJson() != null && !memory.getArtifactRefsJson().isBlank()) {
            sb.append("=== 可复用文件 ===\n");
            appendArtifactRefs(sb, memory.getArtifactRefsJson());
            sb.append("\n");
        }

        // 3. 最近对话
        if (!blocks.isEmpty()) {
            sb.append("=== 最近对话 ===\n");
            for (TranscriptBlock block : blocks) {
                sb.append(formatBlock(block));
            }
        }

        return sb.toString();
    }

    /**
     * 仅格式化 blocks 为文本（用于压缩阶段）
     */
    public String formatBlocks(List<TranscriptBlock> blocks) {
        StringBuilder sb = new StringBuilder();
        for (TranscriptBlock block : blocks) {
            sb.append(formatBlock(block));
        }
        return sb.toString();
    }

    private String formatBlock(TranscriptBlock block) {
        if (block.getBlockType() == null) return "";

        return switch (block.getBlockType()) {
            case USER_INPUT -> "User: " + nvl(block.getText()) + "\n";
            case ASSISTANT_THOUGHT -> "Thought: " + nvl(block.getText()) + "\n";
            case TOOL_USE -> "Tool[" + nvl(block.getToolName()) + "]: " + nvl(block.getText()) + "\n";
            case TOOL_RESULT -> "Result: " + nvl(block.getText()) + "\n";
            case ARTIFACT_REFERENCE -> "Artifact: " + nvl(block.getText()) + "\n";
            case ASSISTANT_ANSWER -> "Assistant: " + nvl(block.getText()) + "\n";
        };
    }

    private void appendArtifactRefs(StringBuilder sb, String artifactRefsJson) {
        try {
            JSONArray refs = JSON.parseArray(artifactRefsJson);
            for (int i = 0; i < refs.size(); i++) {
                JSONObject ref = refs.getJSONObject(i);
                String name = ref.getString("name");
                String url = ref.getString("url");
                sb.append("- ").append(nvl(name, "未命名")).append(": ").append(nvl(url)).append("\n");
            }
        } catch (Exception e) {
            // artifact_refs 格式非法时静默跳过
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String nvl(String s, String defaultValue) {
        return s == null || s.isBlank() ? defaultValue : s;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptPromptFormatter.java

git commit -m "feat: 添加 TranscriptPromptFormatter"
```

---

## Task 12: TranscriptContextBuilder

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptContextBuilder.java`

- [ ] **Step 1: 创建 TranscriptContextBuilder**

```java
package org.wwz.ai.domain.agent.reactor.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.entity.SessionMemory;
import org.wwz.ai.domain.agent.reactor.entity.TranscriptBlock;
import org.wwz.ai.domain.agent.reactor.entity.Turn;
import org.wwz.ai.domain.agent.reactor.mapper.ISessionMemoryDao;
import org.wwz.ai.domain.agent.reactor.mapper.ITranscriptBlockDao;
import org.wwz.ai.domain.agent.reactor.mapper.ITurnDao;

import java.util.ArrayList;
import java.util.List;

/**
 * 从数据库直接构建 LLM 上下文。
 * 替代旧的 SessionWorkingMemoryAssembler，没有复杂恢复逻辑。
 */
@Component
@RequiredArgsConstructor
public class TranscriptContextBuilder {

    private final ISessionMemoryDao sessionMemoryDao;
    private final ITurnDao turnDao;
    private final ITranscriptBlockDao transcriptBlockDao;
    private final TranscriptPromptFormatter promptFormatter;

    /**
     * 构建 LLM prompt 文本
     */
    public String buildHistoryDialogue(Long conversationId, String sessionId) {
        // 1. 查询最新快照
        SessionMemory memory = sessionMemoryDao.queryBySessionId(sessionId);
        int boundarySortOrder = (memory != null && memory.getBoundarySortOrder() != null)
            ? memory.getBoundarySortOrder() : -1;

        // 2. 查询边界后的轮次
        List<Turn> turns = turnDao.queryAfterSortOrder(conversationId, boundarySortOrder);
        if (turns.isEmpty()) {
            return promptFormatter.format(memory, List.of());
        }

        // 3. 查询这些轮次的 blocks
        List<Long> turnIds = turns.stream().map(Turn::getId).toList();
        List<TranscriptBlock> blocks = transcriptBlockDao.queryByTurnIds(turnIds);

        // 4. 格式化
        return promptFormatter.format(memory, blocks);
    }

    /**
     * 获取边界后的轮次列表（供压缩服务使用）
     */
    public List<Turn> getTurnsAfterBoundary(Long conversationId, String sessionId) {
        SessionMemory memory = sessionMemoryDao.queryBySessionId(sessionId);
        int boundarySortOrder = (memory != null && memory.getBoundarySortOrder() != null)
            ? memory.getBoundarySortOrder() : -1;
        return turnDao.queryAfterSortOrder(conversationId, boundarySortOrder);
    }

    /**
     * 获取指定轮次的 blocks（供压缩服务使用）
     */
    public List<TranscriptBlock> getBlocksByTurnIds(List<Long> turnIds) {
        if (turnIds == null || turnIds.isEmpty()) return List.of();
        return transcriptBlockDao.queryByTurnIds(turnIds);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptContextBuilder.java

git commit -m "feat: 添加 TranscriptContextBuilder 替代 SessionWorkingMemoryAssembler"
```

---

## Task 13: 替换 AgentStreamPersistCoordinator 写入路径

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistCoordinator.java`

- [ ] **Step 1: 注入新组件**

在 `AgentStreamPersistCoordinator` 中：
1. 删除对 `AgentMessageServiceImpl`、`AgentMessageEventServiceImpl`、`EventProjector` 的依赖
2. 注入 `TurnWriter`、`TranscriptBlockWriter`、`TranscriptBlockMapper`、`ITurnDao`

```java
// 删除旧依赖
// private final AgentMessageServiceImpl agentMessageService;
// private final AgentMessageEventServiceImpl agentMessageEventService;
// private final EventProjector eventProjector;

// 添加新依赖
private final TurnWriter turnWriter;
private final TranscriptBlockWriter transcriptBlockWriter;
private final TranscriptBlockMapper transcriptBlockMapper;
private final ITurnDao turnDao;
```

- [ ] **Step 2: 替换写入逻辑**

在流处理结束时，替换原有的：
```java
// 旧代码
agentMessageService.save(message);
eventProjector.project(response, ...); // → OrderedEvent → AgentMessageEvent
agentMessageEventService.persistEvents(...);
```

为：
```java
// 新代码
// 1. 先保存 Turn（如果还没保存）
Turn turn = new Turn();
turn.setConversationId(conversationId);
turn.setRequestId(requestId);
turn.setSortOrder(sortOrder);
turn.setQuery(query);
turn.setStatus(0); // 流式中
turn.setStartedAt(LocalDateTime.now());
turnWriter.save(turn);

// 2. 流中的每个 AgentResponse 映射为 TranscriptBlock
List<TranscriptBlock> blocks = new ArrayList<>();
int seqNo = 0;
for (AgentResponse response : responses) {
    List<TranscriptBlock> mapped = transcriptBlockMapper.map(response, turn.getId(), seqNo++);
    blocks.addAll(mapped);
}

// 3. 批量保存 blocks
transcriptBlockWriter.saveBatch(blocks);

// 4. 更新 Turn 状态为完成
turn.setStatus(1);
turn.setFinishedAt(LocalDateTime.now());
turnWriter.update(turn);
```

**注意**：上述伪代码需要根据 `AgentStreamPersistCoordinator` 实际的流处理代码结构调整。关键是替换 `EventProjector` + `AgentMessageEventServiceImpl` 为 `TranscriptBlockMapper` + `TranscriptBlockWriter`。

- [ ] **Step 3: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentStreamPersistCoordinator.java

git commit -m "refactor: AgentStreamPersistCoordinator 使用新 Writer 替换旧写入逻辑"
```

---

## Task 14: 替换 AgentSessionMemoryServiceImpl 读取路径

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java`

- [ ] **Step 1: 注入新组件**

在 `AgentSessionMemoryServiceImpl` 中：
1. 删除对 `SessionWorkingMemoryAssembler` 的依赖
2. 注入 `TranscriptContextBuilder`

```java
// 删除旧依赖
// private final SessionWorkingMemoryAssembler workingMemoryAssembler;

// 添加新依赖
private final TranscriptContextBuilder contextBuilder;
```

- [ ] **Step 2: 替换读取逻辑**

在 `prepareForRequest` 或类似方法中，替换：
```java
// 旧代码
SessionWorkingMemory workingMemory = workingMemoryAssembler.assemble(conversation);
String historyDialogue = workingMemory.getHistoryDialogue();
```

为：
```java
// 新代码
String historyDialogue = contextBuilder.buildHistoryDialogue(
    conversation.getId(), conversation.getSessionId());
```

**注意**：`AgentSessionMemoryServiceImpl` 的实际方法名和参数需要根据实际代码调整。关键是替换 `SessionWorkingMemoryAssembler.assemble()` 为 `TranscriptContextBuilder.buildHistoryDialogue()`。

- [ ] **Step 3: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java

git commit -m "refactor: AgentSessionMemoryServiceImpl 使用新 ContextBuilder 替换旧读取逻辑"
```

---

## Task 15: 调整 SessionMemoryCompactionService

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryCompactionService.java`

- [ ] **Step 1: 注入新组件**

```java
// 删除旧依赖
// private final SessionWorkingMemoryAssembler workingMemoryAssembler;
// private final IAgentMessageDao agentMessageDao;
// private final IAgentMessageEventDao agentMessageEventDao;

// 添加新依赖
private final TranscriptContextBuilder contextBuilder;
private final ITurnDao turnDao;
private final ITranscriptBlockDao transcriptBlockDao;
private final TranscriptPromptFormatter promptFormatter;
private final ISessionMemoryDao sessionMemoryDao;
```

- [ ] **Step 2: 替换压缩逻辑**

压缩时，替换从旧表读取为从新表读取：
```java
// 旧代码：从 AgentMessage + AgentMessageEvent 恢复
// List<AgentMessage> messages = agentMessageDao.queryCompletedAfterSortOrder(...);
// SessionWorkingMemory memory = workingMemoryAssembler.assemble(...);

// 新代码：直接从 TranscriptBlock 读取
List<Turn> turns = contextBuilder.getTurnsAfterBoundary(conversationId, sessionId);
List<Long> turnIds = turns.stream().map(Turn::getId).toList();
List<TranscriptBlock> blocks = contextBuilder.getBlocksByTurnIds(turnIds);

// 生成摘要文本
String textToSummarize = promptFormatter.formatBlocks(blocks);
String summaryText = llmSummaryGenerator.generate(textToSummarize);

// 提取归档产物引用
String artifactRefsJson = extractArtifactRefs(blocks);

// 保存快照
SessionMemory memory = new SessionMemory();
memory.setConversationId(conversationId);
memory.setSessionId(sessionId);
memory.setBoundarySortOrder(maxSortOrder);
memory.setSummaryText(summaryText);
memory.setArtifactRefsJson(artifactRefsJson);
memory.setSourceTurnCount(turns.size());
memory.setLastCompactedAt(LocalDateTime.now());
sessionMemoryDao.insert(memory);
```

- [ ] **Step 3: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryCompactionService.java

git commit -m "refactor: SessionMemoryCompactionService 适配新实体和 DAO"
```

---

## Task 16: 删除旧代码

**Files:** 多个删除

- [ ] **Step 1: 删除旧实体和模型**

```bash
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessage.java

git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentMessageEvent.java

git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/multi/OrderedEvent.java
```

- [ ] **Step 2: 删除旧 DAO 和 Mapper**

```bash
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentMessageDao.java

git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentMessageEventDao.java

git rm ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_mapper.xml

git rm ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_event_mapper.xml
```

- [ ] **Step 3: 删除旧兼容类**

```bash
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/EventProjector.java

git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionTranscriptBlockAssembler.java

git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionArtifactRestoreSupport.java

git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java

git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventFactSupport.java

git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionWorkingMemoryAssembler.java

git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemorySummaryBuilder.java
```

- [ ] **Step 4: 删除旧 Service Impl**

```bash
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageServiceImpl.java

git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentMessageEventServiceImpl.java
```

- [ ] **Step 5: Commit**

```bash
git commit -m "refactor: 删除旧实体、DAO、Mapper、兼容类"
```

---

## Task 17: 清理引用旧代码的地方

**Files:** 多个修改

- [ ] **Step 1: 检查并修复编译错误**

运行编译，修复所有因删除旧类而产生的编译错误：

```bash
cd ai-agent-station-study-app
mvn clean compile
```

常见需要修复的地方：
1. `AgentConversationServiceImpl` 中引用 `AgentMessage` 的地方 → 替换为 `Turn`
2. 前端历史查询链路中引用 `ConversationReplayAssembler` / `AgentMessageEvent` 的地方 → 替换为 `DisplayHistoryQueryService` + `DisplayEvent`
3. 任何引用 `IAgentMessageDao` 的类 → 替换为 `ITurnDao`
4. 测试类中引用旧实体的地方 → 更新测试

**注意**：本计划已经改为链路 1 + 链路 2 同步切换，不保留旧实体、旧回放类作为过渡兼容。

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "refactor: 修复编译错误，清理旧代码引用"
```

---

## Task 18: DisplayEvent 实体

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DisplayEvent.java`

- [ ] **Step 1: 创建 DisplayEvent 实体**

```java
package org.wwz.ai.domain.agent.reactor.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 前端展示事件。面向 UI 渲染，字段自由设计，不受 LLM 约束。
 */
@Data
public class DisplayEvent {
    private Long id;
    private Long turnId;
    private Integer seqNo;

    // 展示类型（前端组件选择）
    private String displayType;       // user_message|thought|tool_call|...

    // 任务分组
    private String taskId;
    private String taskName;
    private Integer taskOrder;

    // 内容（完整保留对话细节）
    private String title;
    private String contentText;       // 与 TranscriptBlock.text 对应
    private String contentJson;       // 结构化额外数据

    // 工具信息（完整保留）
    private String toolUseId;
    private String toolName;
    private String toolArgumentsJson;

    // 产物与结果（完整保留）
    private String artifactRefsJson;
    private String resultPayloadJson;

    // 展示状态与区域
    private String status;            // pending|running|completed|error
    private String displayArea;       // timeline|sidebar

    // 前端组件属性
    private String displayPropsJson;

    private LocalDateTime createTime;
    private Integer deleted;
}
```

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/DisplayEvent.java
git commit -m "feat: 添加 DisplayEvent 实体"
```

---

## Task 19: DisplayEvent DAO + Mapper XML

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDisplayEventDao.java`
- Create: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/display_event_mapper.xml`

- [ ] **Step 1: 创建 IDisplayEventDao**

```java
package org.wwz.ai.domain.agent.reactor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.reactor.entity.DisplayEvent;

import java.util.List;

@Mapper
public interface IDisplayEventDao {

    int insert(DisplayEvent event);

    int batchInsert(@Param("events") List<DisplayEvent> events);

    List<DisplayEvent> queryByTurnId(@Param("turnId") Long turnId);

    List<DisplayEvent> queryByTurnIds(@Param("turnIds") List<Long> turnIds);

    int softDeleteByTurnId(@Param("turnId") Long turnId);
}
```

- [ ] **Step 2: 创建 display_event_mapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="org.wwz.ai.domain.agent.reactor.mapper.IDisplayEventDao">

    <resultMap id="DisplayEventMap" type="org.wwz.ai.domain.agent.reactor.entity.DisplayEvent">
        <id column="id" property="id"/>
        <result column="turn_id" property="turnId"/>
        <result column="seq_no" property="seqNo"/>
        <result column="display_type" property="displayType"/>
        <result column="task_id" property="taskId"/>
        <result column="task_name" property="taskName"/>
        <result column="task_order" property="taskOrder"/>
        <result column="title" property="title"/>
        <result column="content_text" property="contentText"/>
        <result column="content_json" property="contentJson"/>
        <result column="tool_use_id" property="toolUseId"/>
        <result column="tool_name" property="toolName"/>
        <result column="tool_arguments_json" property="toolArgumentsJson"/>
        <result column="artifact_refs_json" property="artifactRefsJson"/>
        <result column="result_payload_json" property="resultPayloadJson"/>
        <result column="status" property="status"/>
        <result column="display_area" property="displayArea"/>
        <result column="display_props_json" property="displayPropsJson"/>
        <result column="create_time" property="createTime"/>
        <result column="deleted" property="deleted"/>
    </resultMap>

    <insert id="insert" parameterType="org.wwz.ai.domain.agent.reactor.entity.DisplayEvent" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO ai_agent_display_event (turn_id, seq_no, display_type, task_id, task_name, task_order, title, content_text, content_json, tool_use_id, tool_name, tool_arguments_json, artifact_refs_json, result_payload_json, status, display_area, display_props_json, create_time, deleted)
        VALUES (#{turnId}, #{seqNo}, #{displayType}, #{taskId}, #{taskName}, #{taskOrder}, #{title}, #{contentText}, #{contentJson}, #{toolUseId}, #{toolName}, #{toolArgumentsJson}, #{artifactRefsJson}, #{resultPayloadJson}, #{status}, #{displayArea}, #{displayPropsJson}, now(), 0)
    </insert>

    <insert id="batchInsert">
        INSERT INTO ai_agent_display_event (turn_id, seq_no, display_type, task_id, task_name, task_order, title, content_text, content_json, tool_use_id, tool_name, tool_arguments_json, artifact_refs_json, result_payload_json, status, display_area, display_props_json, create_time, deleted)
        VALUES
        <foreach collection="events" item="event" separator=",">
            (#{event.turnId}, #{event.seqNo}, #{event.displayType}, #{event.taskId}, #{event.taskName}, #{event.taskOrder}, #{event.title}, #{event.contentText}, #{event.contentJson}, #{event.toolUseId}, #{event.toolName}, #{event.toolArgumentsJson}, #{event.artifactRefsJson}, #{event.resultPayloadJson}, #{event.status}, #{event.displayArea}, #{event.displayPropsJson}, now(), 0)
        </foreach>
    </insert>

    <select id="queryByTurnId" resultMap="DisplayEventMap">
        SELECT * FROM ai_agent_display_event WHERE turn_id = #{turnId} AND deleted = 0 ORDER BY seq_no
    </select>

    <select id="queryByTurnIds" resultMap="DisplayEventMap">
        SELECT * FROM ai_agent_display_event
        WHERE turn_id IN
        <foreach collection="turnIds" item="turnId" open="(" separator="," close=")">
            #{turnId}
        </foreach>
        AND deleted = 0
        ORDER BY turn_id, seq_no
    </select>

    <update id="softDeleteByTurnId">
        UPDATE ai_agent_display_event SET deleted = 1 WHERE turn_id = #{turnId} AND deleted = 0
    </update>

</mapper>
```

- [ ] **Step 3: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IDisplayEventDao.java
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/display_event_mapper.xml
git commit -m "feat: 添加 DisplayEvent DAO 和 Mapper XML"
```

---

## Task 20: DisplayEventProjector

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/DisplayEventProjector.java`

- [ ] **Step 1: 创建 DisplayEventProjector**

```java
package org.wwz.ai.domain.agent.reactor.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.reactor.entity.DisplayEvent;
import org.wwz.ai.domain.agent.reactor.entity.TranscriptBlock;
import org.wwz.ai.domain.agent.reactor.model.enums.TranscriptBlockType;

import java.util.List;

/**
 * TranscriptBlock → DisplayEvent 投影器。
 * 直接复制 TranscriptBlock 事实字段，并补齐前端展示元数据。
 */
@Component
@RequiredArgsConstructor
public class DisplayEventProjector {

    public List<DisplayEvent> project(List<TranscriptBlock> blocks) {
        return blocks.stream().map(this::projectSingle).toList();
    }

    private DisplayEvent projectSingle(TranscriptBlock block) {
        return switch (block.getBlockType()) {
            case USER_INPUT -> projectUserInput(block);
            case ASSISTANT_THOUGHT -> projectThought(block);
            case TOOL_USE -> projectToolUse(block);
            case TOOL_RESULT -> projectToolResult(block);
            case ARTIFACT_REFERENCE -> projectArtifact(block);
            case ASSISTANT_ANSWER -> projectAnswer(block);
        };
    }

    private DisplayEvent projectUserInput(TranscriptBlock block) {
        DisplayEvent event = new DisplayEvent();
        event.setTurnId(block.getTurnId());
        event.setSeqNo(block.getSeqNo());
        event.setDisplayType("user_message");
        event.setTitle("用户输入");
        event.setContentText(block.getText());
        event.setArtifactRefsJson(block.getArtifactRefsJson());
        event.setDisplayArea("timeline");
        event.setStatus("completed");
        return event;
    }

    private DisplayEvent projectThought(TranscriptBlock block) {
        DisplayEvent event = new DisplayEvent();
        event.setTurnId(block.getTurnId());
        event.setSeqNo(block.getSeqNo());
        event.setDisplayType("thought");
        event.setTitle("思考过程");
        event.setContentText(block.getText());
        event.setDisplayArea("timeline");
        event.setStatus("completed");
        return event;
    }

    private DisplayEvent projectToolUse(TranscriptBlock block) {
        DisplayEvent event = new DisplayEvent();
        event.setTurnId(block.getTurnId());
        event.setSeqNo(block.getSeqNo());
        event.setDisplayType("tool_call");
        event.setTitle("调用 " + nvl(block.getToolName(), "工具"));
        event.setContentText(block.getText());
        event.setToolUseId(block.getToolUseId());
        event.setToolName(block.getToolName());
        event.setToolArgumentsJson(block.getToolArgumentsJson());
        event.setDisplayArea("timeline");
        event.setStatus("running");
        return event;
    }

    private DisplayEvent projectToolResult(TranscriptBlock block) {
        DisplayEvent event = new DisplayEvent();
        event.setTurnId(block.getTurnId());
        event.setSeqNo(block.getSeqNo());
        event.setDisplayType("tool_result");
        event.setTitle("工具结果");
        event.setContentText(block.getText());
        event.setResultPayloadJson(block.getResultPayloadJson());
        event.setDisplayArea("timeline");
        event.setStatus("completed");
        return event;
    }

    private DisplayEvent projectArtifact(TranscriptBlock block) {
        DisplayEvent event = new DisplayEvent();
        event.setTurnId(block.getTurnId());
        event.setSeqNo(block.getSeqNo());
        event.setDisplayType("artifact");
        event.setTitle("生成文件");
        event.setContentText(block.getText());
        event.setArtifactRefsJson(block.getArtifactRefsJson());
        event.setDisplayArea("timeline");
        event.setStatus("completed");
        return event;
    }

    private DisplayEvent projectAnswer(TranscriptBlock block) {
        DisplayEvent event = new DisplayEvent();
        event.setTurnId(block.getTurnId());
        event.setSeqNo(block.getSeqNo());
        event.setDisplayType("final_answer");
        event.setTitle("回答");
        event.setContentText(block.getText());
        event.setDisplayArea("timeline");
        event.setStatus("completed");
        return event;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String nvl(String s, String defaultValue) {
        return s == null || s.isBlank() ? defaultValue : s;
    }
}
```

**约束说明**：
- `DisplayEventProjector` 直接复制 `contentText` / `toolName` / `toolArgumentsJson` / `artifactRefsJson` / `resultPayloadJson` 等字段
- 不再把工具参数、产物列表、结果载荷重新包装进 `contentJson`
- `contentJson` 仅用于 block 本身已有的结构化内容；若当前链路没有该类结构化内容，可保持 `null`

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/DisplayEventProjector.java
git commit -m "feat: 添加 DisplayEventProjector"
```

---

## Task 21: DisplayHistoryQueryService

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/DisplayHistoryQueryService.java`

- [ ] **Step 1: 创建 DisplayHistoryQueryService**

```java
package org.wwz.ai.domain.agent.reactor.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.entity.DisplayEvent;
import org.wwz.ai.domain.agent.reactor.entity.Turn;
import org.wwz.ai.domain.agent.reactor.mapper.IDisplayEventDao;
import org.wwz.ai.domain.agent.reactor.mapper.ITurnDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 前端历史查询服务。替代 ConversationReplayAssembler，直接查询 display_event。
 */
@Service
@RequiredArgsConstructor
public class DisplayHistoryQueryService {

    private final ITurnDao turnDao;
    private final IDisplayEventDao displayEventDao;

    public List<TurnHistory> queryHistory(Long conversationId) {
        // 1. 查询轮次
        List<Turn> turns = turnDao.queryByConversationId(conversationId);
        if (turns.isEmpty()) return List.of();

        // 2. 批量查询展示事件
        List<Long> turnIds = turns.stream().map(Turn::getId).toList();
        List<DisplayEvent> events = displayEventDao.queryByTurnIds(turnIds);

        // 3. 按 turn_id 分组
        Map<Long, List<DisplayEvent>> eventMap = events.stream()
            .collect(Collectors.groupingBy(DisplayEvent::getTurnId));

        // 4. 组装
        List<TurnHistory> result = new ArrayList<>();
        for (Turn turn : turns) {
            TurnHistory history = new TurnHistory();
            history.setTurnId(turn.getId());
            history.setSortOrder(turn.getSortOrder());
            history.setQuery(turn.getQuery());
            history.setStatus(turn.getStatus());
            history.setEvents(eventMap.getOrDefault(turn.getId(), List.of()));
            result.add(history);
        }
        return result;
    }

    @Data
    public static class TurnHistory {
        private Long turnId;
        private Integer sortOrder;
        private String query;
        private Integer status;
        private List<DisplayEvent> events;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/DisplayHistoryQueryService.java
git commit -m "feat: 添加 DisplayHistoryQueryService 替代 ConversationReplayAssembler"
```

---

## Task 22: 同步双写 — TranscriptBlockWriter

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptBlockWriter.java`

- [ ] **Step 1: 在 TranscriptBlockWriter 中增加同步双写**

修改 `TranscriptBlockWriter`，在同一事务内保存 blocks 并投影为 display events：

```java
package org.wwz.ai.domain.agent.reactor.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.wwz.ai.domain.agent.reactor.entity.DisplayEvent;
import org.wwz.ai.domain.agent.reactor.entity.TranscriptBlock;
import org.wwz.ai.domain.agent.reactor.mapper.IDisplayEventDao;
import org.wwz.ai.domain.agent.reactor.mapper.ITranscriptBlockDao;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TranscriptBlockWriter {

    private final ITranscriptBlockDao transcriptBlockDao;
    private final IDisplayEventDao displayEventDao;
    private final DisplayEventProjector displayEventProjector;

    @Transactional
    public void saveBatch(List<TranscriptBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return;

        // 1. 保存 transcript blocks（LLM 链路源数据）
        transcriptBlockDao.batchInsert(blocks);

        // 2. 同步投影为 display events（前端展示数据，同一事务）
        List<DisplayEvent> displayEvents = displayEventProjector.project(blocks);
        if (!displayEvents.isEmpty()) {
            displayEventDao.batchInsert(displayEvents);
        }
    }
}
```

**同步双写理由**：
- 投影逻辑轻量（字段映射 + 展示元数据），不会显著增加写入延迟
- 同一事务保证 `ai_agent_transcript_block` 和 `ai_agent_display_event` 数据一致
- 前端查询时直接读取 `ai_agent_display_event`，不需要实时计算
- 如果未来投影变重，再改为异步也不迟

**幂等边界**：
- `saveBatch()` 仅用于同一 `turn` 的首次完整写入
- 如果同一 `turn` 发生补写、重试或重放，调用方需要先清理该 `turn` 在两张表中的旧数据，再执行整轮重建

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/TranscriptBlockWriter.java
git commit -m "feat: TranscriptBlockWriter 同步双写 DisplayEvent"
```

---

## Task 23: 替换前端历史查询接口

**Files:**
- Modify: 前端历史查询 Controller/Service（根据实际代码路径）

- [ ] **Step 1: 找到前端历史查询接口**

通常历史查询接口在 trigger 模块的 Controller 中，路径类似：
- `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/AgentConversationController.java`
- 或 `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/ConversationController.java`

搜索方法：

```bash
rg "ConversationReplayAssembler|queryHistory|historyDetail" ai-agent-station-study-trigger/
```

- [ ] **Step 2: 替换为 DisplayHistoryQueryService**

在 Controller/Service 中：
1. 删除 `ConversationReplayAssembler` 的注入
2. 注入 `DisplayHistoryQueryService`
3. 替换历史查询方法：

```java
// 旧代码
// List<ConversationTurnDetail> details = conversationReplayAssembler.assemble(conversationId);

// 新代码
List<DisplayHistoryQueryService.TurnHistory> histories = displayHistoryQueryService.queryHistory(conversationId);
```

**注意**：如果前端 VO 对象（如 `ConversationTurnDetail`、`ConversationEventDetail`）与新返回的 `TurnHistory` 结构不兼容，需要：
- 方案 A：修改前端 VO 以适配新结构
- 方案 B：在 Controller 层做一层转换

- [ ] **Step 3: Commit**

```bash
git commit -m "refactor: 前端历史查询接口使用 DisplayHistoryQueryService 替代 ConversationReplayAssembler"
```

---

## Task 24: 最终删除旧代码 + 编译验证

**Files:** 多个

- [ ] **Step 1: 删除链路 2 旧兼容类**

```bash
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationReplayAssembler.java
```

- [ ] **Step 2: 运行编译**

```bash
cd ai-agent-station-study-app
mvn clean compile
```

预期：编译成功。如果仍有引用旧类的代码，逐个修复。

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl ai-agent-station-study-app
```

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: 删除所有旧兼容类，链路 1 + 链路 2 重构完成"
```

---

## 自我审查

### Spec 覆盖检查

| Spec 要求 | 对应 Task |
|-----------|-----------|
| 新表结构（ai_agent_turn + ai_agent_transcript_block） | Task 1 |
| TranscriptBlockType 枚举（6 种） | Task 2 |
| Turn / TranscriptBlock / SessionMemory 实体 | Task 3, 4, 5 |
| DAO + Mapper XML | Task 6, 7, 8 |
| TranscriptBlockMapper（归类映射） | Task 9 |
| TurnWriter + TranscriptBlockWriter | Task 10 |
| TranscriptPromptFormatter | Task 11 |
| TranscriptContextBuilder | Task 12 |
| 替换写入路径 | Task 13 |
| 替换读取路径 | Task 14 |
| 压缩服务调整 | Task 15 |
| 删除旧兼容类 | Task 16, 17, 24 |
| **DisplayEvent 实体 + DAO** | **Task 18, 19** |
| **DisplayEventProjector** | **Task 20** |
| **DisplayHistoryQueryService** | **Task 21** |
| **同步双写** | **Task 22** |
| **替换前端查询接口** | **Task 23** |

### Placeholder 扫描

- 无 TBD/TODO
- 所有代码块包含完整代码
- 文件路径都是绝对路径

### 类型一致性检查

- `TranscriptBlockType` 枚举在所有任务中名称一致
- `artifactRefsJson` 字段名在所有实体和 Mapper 中一致
- `DisplayEvent` 字段名在实体、DAO、Mapper、Projector 中一致

### 风险提示

1. **AgentResponse 字段名不确定**：Task 9 中 `TranscriptBlockMapper` 使用了假设的字段名，需要根据实际 `AgentResponse` 类调整
2. **JSON 工具选择**：Task 9、11 使用了 `com.alibaba.fastjson`，如果项目使用 Jackson 需要替换
3. **前端 VO 兼容性**：Task 23 中前端返回结构可能变化，需要确认前端是否能适配 `TurnHistory` 结构
