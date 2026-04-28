package org.wwz.ai.test.domain.sessionmemory;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessageEvent;
import org.wwz.ai.domain.agent.reactor.entity.AgentSessionMemory;
import org.wwz.ai.domain.agent.reactor.model.dto.FileInformation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话记忆测试公共夹具
 */
public final class SessionMemoryTestSupport {

    public static final String SESSION_ID = "sess-memory-001";
    public static final String REQUEST_ID = "req-memory-001";
    public static final String DEVICE_ID = "dev-memory-001";
    public static final Long CONVERSATION_ID = 1001L;

    private SessionMemoryTestSupport() {
    }

    public static AgentMessage completedMessage(Long messageId,
                                                String requestId,
                                                int sortOrder,
                                                String query,
                                                String response,
                                                String filesJson) {
        return AgentMessage.builder()
                .id(messageId)
                .conversationId(CONVERSATION_ID)
                .requestId(requestId)
                .sortOrder(sortOrder)
                .query(query)
                .response(response)
                .filesJson(filesJson)
                .status(1)
                .forceStop(0)
                .agentType(2)
                .build();
    }

    public static AgentMessageEvent artifactEvent(Long messageId, int seqNo, String displayName, String url) {
        return AgentMessageEvent.builder()
                .messageId(messageId)
                .seqNo(seqNo)
                .eventType("tool_result")
                .eventSubType("html.page")
                .displayArea("workspace")
                .title(displayName)
                .contentText("已生成稳定引用：" + displayName)
                .referenceOnly(true)
                .artifactRefsJson("""
                        [
                          {
                            "displayName": "%s",
                            "resourceKey": "%s",
                            "downloadUrl": "%s",
                            "previewUrl": "%s",
                            "missing": false
                          }
                        ]
                        """.formatted(displayName, displayName, url, url))
                .structuredDataJson("""
                        {
                          "messageType": "html",
                          "answer": "已生成稳定引用：%s",
                          "isFinal": true
                        }
                        """.formatted(displayName))
                .status("completed")
                .build();
    }

    public static AgentSessionMemory snapshot(String summaryText,
                                              int boundarySortOrder,
                                              String artifactRefsJson) {
        return AgentSessionMemory.builder()
                .id(3001L)
                .conversationId(CONVERSATION_ID)
                .sessionId(SESSION_ID)
                .agentType(2)
                .summaryText(summaryText)
                .artifactRefsJson(artifactRefsJson)
                .boundarySortOrder(boundarySortOrder)
                .sourceTurnCount(boundarySortOrder + 1)
                .deleted(0)
                .build();
    }

    public static String filesJson(FileInformation... files) {
        return JSON.toJSONString(List.of(files));
    }

    public static FileInformation file(String fileName, String description, String url) {
        return FileInformation.builder()
                .fileName(fileName)
                .fileDesc(description)
                .ossUrl(url)
                .domainUrl(url)
                .build();
    }

    public static void assertFileNames(List<FileInformation> files, String... expectedFileNames) {
        List<String> actualNames = files.stream()
                .map(FileInformation::getFileName)
                .collect(Collectors.toList());
        Assert.assertEquals(List.of(expectedFileNames), actualNames);
    }
}
