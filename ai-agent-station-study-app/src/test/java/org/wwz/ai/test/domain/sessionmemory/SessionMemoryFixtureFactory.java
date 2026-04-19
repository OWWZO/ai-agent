package org.wwz.ai.test.domain.sessionmemory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;
import org.wwz.ai.domain.agent.reactor.model.memory.SessionMemoryFact;

import java.util.List;

/**
 * 会话记忆场景夹具工厂
 */
public final class SessionMemoryFixtureFactory {

    private SessionMemoryFixtureFactory() {
    }

    public static AgentSessionMemory buildSnapshot() {
        String artifactRefsJson = JSON.toJSONString(List.of(new JSONObject() {{
            put("displayName", "existing-report.html");
            put("resourceKey", "existing-report");
            put("downloadUrl", "https://file.example.com/existing-report");
            put("previewUrl", "https://file.example.com/existing-report");
            put("missing", false);
        }}));
        return SessionMemoryTestSupport.snapshot(
                "用户要求后续输出都使用中文表格。",
                2,
                102L,
                List.of(
                        SessionMemoryTestSupport.fact("constraint", "后续输出都使用中文表格"),
                        SessionMemoryTestSupport.fact("goal", "继续沿用上一轮分析背景")),
                artifactRefsJson);
    }

    public static List<AgentMessage> buildRecentWindowMessages() {
        FileInformation uploadFile = SessionMemoryTestSupport.file(
                "uploaded-spec.pdf",
                "用户补充的规格说明",
                "https://file.example.com/uploaded-spec");
        return List.of(
                SessionMemoryTestSupport.completedMessage(
                        103L,
                        "req-memory-002",
                        3,
                        "继续补充框架对比，只保留最重要的三类",
                        "我补充了三类框架，并保持中文表格输出。",
                        SessionMemoryTestSupport.filesJson(uploadFile)),
                SessionMemoryTestSupport.completedMessage(
                        104L,
                        "req-memory-003",
                        4,
                        "把最后结论再精简一点",
                        "已把最终建议压缩成三条核心结论。",
                        null)
        );
    }

    public static List<AgentMessageEvent> buildRecentWindowEvents() {
        return List.of(
                SessionMemoryTestSupport.artifactEvent(
                        104L,
                        1,
                        "summary-report.html",
                        "https://file.example.com/summary-report")
        );
    }

    public static List<SessionMemoryFact> buildExpectedFacts() {
        return List.of(
                SessionMemoryTestSupport.fact("constraint", "后续输出都使用中文表格"),
                SessionMemoryTestSupport.fact("goal", "继续沿用上一轮分析背景"));
    }
}
