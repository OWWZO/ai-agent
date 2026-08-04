package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpResponse;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolObservationSerializer;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.WebFetchTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.util.Map;

/**
 * WebFetch 非 2xx 响应的结构化错误回归。
 */
public class WebFetchStructuredResultTest {

    @Test
    public void shouldKeepHttpFailureDetailsInStructuredPayload() {
        RemoteHttpPort httpPort = new RemoteHttpPort() {
            @Override
            public String execute(RemoteHttpRequest request) {
                return "";
            }

            @Override
            public RemoteHttpResponse executeDetailed(RemoteHttpRequest request) {
                return RemoteHttpResponse.builder()
                        .statusCode(404)
                        .statusText("Not Found")
                        .headers(Map.of("Content-Type", "text/html"))
                        .body("fund page is missing")
                        .finalUrl(request.getUrl())
                        .build();
            }
        };

        WebFetchTool tool = new WebFetchTool();
        tool.setAgentContext(AgentContext.builder()
                .requestId("req-web-fetch-404")
                .sessionId("session-web-fetch-404")
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(new ReactorConfig(), httpPort))
                .build());

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of(
                "url", "https://example.com/missing",
                "prompt", "extract the title"
        ));

        Assert.assertTrue(Boolean.TRUE.equals(payload.getFailed()));
        Assert.assertTrue(payload.getLlmData() instanceof Map<?, ?>);
        Map<?, ?> detail = (Map<?, ?>) payload.getLlmData();
        Assert.assertEquals("web_fetch", detail.get("tool"));
        Assert.assertEquals(404, detail.get("status"));
        Assert.assertEquals("Not Found", detail.get("statusText"));
        Assert.assertEquals("fund page is missing", detail.get("responseBody"));

        String observation = ToolObservationSerializer.serializePayload(payload);
        Assert.assertTrue(observation.contains("\"tool_ok\":false"));
        Assert.assertTrue(observation.contains("\"status\":404"));
    }
}
