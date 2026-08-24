package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.common.social.RedditTool;
import org.wwz.ai.domain.agent.runtime.tool.common.social.TwitterTool;
import org.wwz.ai.domain.agent.runtime.tool.common.social.XueqiuTool;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.test.domain.support.ReactorRuntimeTestSupport;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SocialToolTest {

    @Test
    public void shouldForwardReadRequestWithoutCredentialFields() {
        AtomicReference<RemoteHttpRequest> captured = new AtomicReference<>();
        RemoteHttpPort httpPort = request -> {
            captured.set(request);
            return """
                    {"code":200,"data":{"ok":true,"platform":"reddit","operation":"popular",
                    "items":[{"id":"abc","title":"A post"}],"warnings":[]}}
                    """;
        };
        RedditTool tool = new RedditTool();
        tool.setAgentContext(context(httpPort));

        ToolResultPayload payload = (ToolResultPayload) tool.execute(Map.of("operation", "popular"));

        Assert.assertFalse(payload.getFailed());
        Assert.assertTrue(String.valueOf(payload.getLlmData()).contains("A post"));
        Assert.assertTrue(captured.get().getUrl().endsWith("/v1/tool/reddit"));
        Assert.assertFalse(captured.get().getBody().toLowerCase().contains("cookie"));
    }

    @Test
    public void shouldExposeIndependentSchemasForAllPlatforms() {
        Assert.assertTrue(new TwitterTool().toParams().toString().contains("tweet_id"));
        Assert.assertTrue(new RedditTool().toParams().toString().contains("post_id"));
        Assert.assertTrue(new XueqiuTool().toParams().toString().contains("symbol"));
    }

    private AgentContext context(RemoteHttpPort httpPort) {
        ReactorConfig config = new ReactorConfig();
        ReflectionTestUtils.setField(config, "codeInterpreterUrl", "http://reactor-tool");
        return AgentContext.builder()
                .requestId("req-social-001")
                .sessionId("session-social-001")
                .runtimeDependencies(ReactorRuntimeTestSupport.runtimeDependencies(config, httpPort))
                .build();
    }
}
